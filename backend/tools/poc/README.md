# 영상 전달 PoC 계측 도구

판정 기준·지표 정의: [../../docs/poc/video-delivery.md](../../docs/poc/video-delivery.md)
실행 환경(nginx origin + netem): [../../local-infra/poc/](../../local-infra/poc/)

커스텀 MP4 파서 없이 ffprobe/ffmpeg/curl 조합으로만 잰다. 파이썬은 표준
라이브러리만 쓴다 — 하네스 이미지에 pip 이 없다.

## 빠른 시작

```bash
cd backend/local-infra/poc && docker compose up -d --build   # 1. 환경
sh backend/tools/poc/selfcheck.sh                            # 2. 도구 자체검사 (영상 불필요)
sh backend/tools/poc/run-all.sh                              # 3. 스윕 전체
```

`selfcheck.sh`·`run-all.sh` 는 **호스트에서** 돌린다. 측정은 여전히 하네스
컨테이너 안에서 하고, 호스트는 프로파일 전환만 맡는다 — 셰이핑을 거는
컨테이너가 커널에 따라 달라지기 때문이다(아래 참조).

## 도구

| 스크립트 | 실행 위치 | 하는 일 |
|---|---|---|
| `probe.sh <dir> [태그] [출력]` | 컨테이너 | `inventory.csv`(길이·비트레이트·해상도·GOP·moov 앞뒤·moov 크기) + `offsets.csv`(T초 바이트) |
| `transcode.sh --rendition 480 <src>` | 컨테이너 | 저화질 렌디션 사본 (ABR 판정용, 재인코딩) |
| `transcode.sh --faststart <src>` | 컨테이너 | moov 가 뒤인 파일만 앞으로 (`-c copy`, 화질 무변경) |
| `ttff.sh <url> [반복] [net] [tag]` | 컨테이너 | 첫 프레임까지 벽시계 p50/p95 + HTTP 요청 수 |
| `session.py` | 컨테이너 | 세션 시뮬레이터 — 아이템당 CSV 한 줄 |
| `report.py` | 컨테이너 | CSV → `report.md` |
| `run-all.sh` | **호스트** | 스윕 전체 + 리포트 |
| `selfcheck.sh` | **호스트** | 영상 없이 파이프라인 전체 검사 |

원본은 절대 건드리지 않는다. `transcode.sh` 는 `/srv/renditions/<태그>/` 에
사본만 만들고, origin 이 `/renditions/<태그>/` 로 원본과 나란히 서빙한다.

## 셰이핑 지점 — 커널에 따라 달라진다

| 커널 | 지점 | 왜 |
|---|---|---|
| ifb 있음 (리눅스) | 하네스 ingress | 원칙. CloudFront 처럼 남의 origin 도 조인다 |
| ifb 없음 (Docker Desktop macOS) | origin egress | origin 의 나가는 방향이 곧 다운로드 경로다. 로컬 origin 만 가능 |

`run-all.sh`·`selfcheck.sh` 가 자동으로 판별한다. 수신측에서 egress 를 걸면
요청·ACK 만 조여지고 다운로드는 그대로 빠르므로, `netem.sh` 는 자동으로
egress 로 강등하지 않는다 — 호출자가 `MODE=egress` 를 명시해야 한다.

macOS 에서 `local-infra/poc/verify.sh`(티켓 541) 는 ifb 가 없어 실패한다.
같은 것을 `selfcheck.sh` 의 2b 단계가 origin egress 로 검사한다.

## 자체검사가 보는 것

영상이 없어도 파이프라인 전체가 돈다 — ffmpeg 로 8초짜리 H.264 클립을
합성해서 쓴다. 영상 문제와 코드 문제를 분리하기 위해서다.

1. probe 가 6행을 내고 moov 를 전부 `front` 로 읽는가 (`+faststart` 로 만들었으므로)
2. 프로파일을 바꾸면 시간이 실제로 변하는가 (512KiB, wifi vs 3g, 5배 이상)
3. 이탈한 세션에서 미시청 프리페치 바이트가 0 이 아닌가
4. **시뮬레이터 합계와 nginx 로그 합계가 ±5% 안인가** ← 완료 조건

4번이 어긋나면 나머지 수치는 전부 무효다. 대조는 요청 URL 에 붙는
`?s=<runid>` 로 실행을 구분한다 — 정적 서빙은 쿼리스트링을 무시하므로
응답에는 영향이 없다.

## 확정이 필요한 값

- **`--exit-prob` (기본 0.05)** — 스와이프당 이탈 확률. 설계문서의 확정
  시청 모델에는 이탈이 없는데, 이탈이 없으면 피드를 끝까지 소비해서
  프리페치 낭비가 **구조적으로 0** 이 된다. 그러면 N·T 를 키울수록 유리하기만
  해서 주 실험이 성립하지 않는다(최적값이 N=∞ 로 나온다). 0.05 는 제안값이다.
- **`PROFILES` 에 `lte_poor` 포함** — 확정 프로파일은 wifi·lte_good 2종인데,
  2.5Mbps 영상에 20Mbps 는 8배 여유라 재버퍼가 나올 수 없다. ABR 판정 게이트가
  열리지 않는다. 기본값에 `lte_poor`(4Mbps·100ms)를 넣어 뒀다.
- **트래픽 모델 식** — 설계문서 식이 프리페치분을 이중계산한다. `report.py`
  3절이 오차와 원인을 같이 낸다.
