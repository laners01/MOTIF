# 영상 전달 PoC 로컬 환경

판정 기준·프로파일: [../../docs/poc/video-delivery.md](../../docs/poc/video-delivery.md)

계측 도구는 [../../tools/poc/](../../tools/poc/) 에 있다(티켓 543).

```bash
cp .env.example .env                                        # 1. VIDEO_DIR 을 영상 폴더로 (영상 없으면 그대로 둬도 된다)
docker compose up -d --build                                # 2. 띄우기
docker compose exec poc-harness sh /work/verify.sh          # 3. 완료 조건 자체검사 (영상 불필요)
docker compose exec poc-harness sh /work/profiles/netem.sh lte_good   # 4. 프로파일 걸기 (off 로 해제)
cat logs/access.csv                                         # 5. 로그 회수 — time,uri,status,range,bytes,request_time
```

측정은 **하네스 컨테이너 안에서** 한다. 프로파일이 그 컨테이너의 netns 에만 걸리므로,
호스트에서 직접 쏘면 조여지지 않은 수치가 나온다. origin 주소는 `http://poc-origin/videos/<파일>`.

## 실측 기준선 (2026-09-10, Docker Desktop / Windows)

환경이 정상인지 판단하는 기준. 워밍업 후 재현성은 ±1ms 수준이고, 컨테이너 재시작 직후
첫 요청만 1 RTT 정도 느리다(3g 에서 7.13초).

| 프로파일 | 전송 | 클라이언트 시간 |
|---|---|---|
| 3g (1.5Mbps·300ms) | 1MiB | **6.833초** — 전송 5.59 + 연결 0.3 + 첫바이트 0.3 + slow start ~0.6 |
| 3g | 256KiB | 2.448초 |
| 3g | 64KiB | 1.352초 |
| lte_good (20Mbps·50ms) | 2MiB | **1.093초** |
| 셰이핑 없음(off) | 2MiB | 0.001초 |

- nginx 로그의 `request_time` 은 체감 시간이 아니다. 위 6.833초짜리 요청이 로그엔
  **2.897초**로 찍힌다 — 서버가 소켓에 다 쓴 시점까지라서 그렇다. 수신측에서 대역폭을
  조이는 구성에서는 항상 이만큼 벌어진다. **체감은 하네스 수치, 바이트는 로그**를 쓴다
- 튜닝해도 안 바뀌는 것(실측 확인): ifb 큐 깊이(40~1000), GRO/GSO/TSO on/off.
  둘 다 소수점 4자리까지 같은 값이 나왔다 — 수치가 이상하면 다른 원인을 찾아야 한다

## 메모

- 영상은 레포에 넣지 않는다 — `VIDEO_DIR` 마운트. 목록 확인은 `http://poc-origin/videos/`(autoindex)
- Docker Desktop(Windows/Mac)에서도 컨테이너 안 `tc` 는 동작한다(리눅스 VM). 호스트가 아니라 컨테이너 eth0 에 건다
- Git Bash 에서 돌리면 `/work/...` 경로가 Windows 경로로 바뀐다. `MSYS_NO_PATHCONV=1` 를 앞에 붙이거나 PowerShell 을 쓴다
- `verify.sh` 가 ifb 오류로 실패하면 커널에 ifb 모듈이 없는 것(Docker Desktop macOS 가 그렇다).
  그 환경에서는 origin egress 에 거는 `MODE=egress` 경로를 쓴다 — `tools/poc/run-all.sh`·
  `selfcheck.sh` 가 자동으로 판별하고, `selfcheck.sh` 2b 단계가 같은 검사를 대신한다
- 실제 영상으로는 아직 안 돌려봤다(`/videos/`·`VIDEO_DIR` 미검증). 티켓 4 에서 확인한다
