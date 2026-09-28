# 영상 전달 PoC — S3 + CloudFront (최종 수치용)

로컬 스윕(`backend/local-infra/poc/`)으로 좁힌 후보를 실제 전달 경로에서
재확인하기 위한 버킷·배포다. 로컬 nginx 는 CDN 캐시·엣지 TTFB·HTTP/2 를
재현하지 못한다. 판정 기준은 `backend/docs/poc/video-delivery.md`(develop/backend).

**이 버킷·배포는 팀 계정이 아니라 개인 프리티어 계정에 있다.** 팀 인프라에는
AWS 콘솔이 없으므로(`infra/README.md`) 다른 선택지가 없었다. 트래픽이 곧
개인 비용이라는 뜻이므로 **§1 을 먼저 하고 나머지를 한다.**

## 전제

- [x] S3 버킷 + CloudFront 배포 생성 (2026-09-10)
- [ ] 월 예산 알람 — §1
- [ ] 영상 30개 — 없으면 §5 는 뒤로. §3 검증은 더미 파일 하나로 된다

---

## 1. 비용 상한 알람 — 제일 먼저

개인 계정에 상한이 없으면 사고가 그대로 청구된다. 영상은 요청 하나가 MB
단위라 스크립트 실수 한 번이 GB 를 만든다. 측정을 시작하기 전에 건다.

AWS Budgets → Create budget → Cost budget

| 항목 | 값 |
|---|---|
| 주기·금액 | 월 $5 — 프리티어를 넘기는 즉시 알림이 오게 하는 값 |
| 알림 | 실제 비용 80%, **예측 비용 100%** |
| 수신 | 계정 이메일 |

예측 비용 알림이 중요하다. 실제 비용은 이미 쓴 뒤에 오지만, 예측은 추세를
보고 미리 온다.

**예상 사용량** — 영상 30개 ≈ 150MB 저장, 세션 시뮬레이션 1회 ≈ 140MB 전송.
티켓 5 의 CloudFront 2차 측정(상위 2~3 조합 × 프로파일별 3회) ≈ 1~2GB.
프리티어(CloudFront 1TB/월) 안이지만, 알람이 없으면 "안이었다"를 사후에 안다.

## 2. 설정 확인

생성은 끝났으므로 값만 맞는지 본다. 콘솔 경로를 적어둔다 — 이 PoC 를 위해
`aws` CLI 를 깔 필요는 없다(§3 검증은 HTTP 만 쓴다).

| 항목 | 확인 방법 | 왜 |
|---|---|---|
| 리전 `ap-northeast-2` | S3 → 버킷 → Properties | 오리진이 멀면 캐시 미스 때 엣지가 원본을 끌어오는 시간이 늘어난다 |
| 버킷 비공개 | S3 객체 URL 직접 접근 → **403** | 공개면 CDN 을 우회해 원본에 직접 붙는 경로가 남는다 |
| OAC | CloudFront → Origins → Origin access = Origin access control | 배포만 버킷을 읽게 하는 표준 방식 |
| **캐시 키에서 쿼리스트링·쿠키 제외** | CloudFront → Behaviors → Cache policy | 사용자별 파라미터가 캐시 키에 들어가면 **적중률이 0% 가 된다** — 사람마다 다른 키가 되어 매번 원본을 때린다. 검증은 §3 |
| HTTP/2 | CloudFront → General → Settings | 기본 ON |
| HTTP/3 | 같은 화면 | 기본 OFF 인 경우가 있다 |

## 3. 검증 — 하네스에서 쏜다

호스트에서 직접 쏘지 않는다. 티켓 2 의 하네스 컨테이너에서 쏘면 `netem`
프로파일이 로컬 origin 과 **동일하게** 걸리므로 두 경로를 같은 조건에서
비교할 수 있다. 이게 CloudFront 수치를 로컬 수치와 나란히 놓을 수 있는
유일한 방법이다.

```bash
cd backend/local-infra/poc          # develop/backend
docker compose up -d
D=d______.cloudfront.net            # 배포 도메인
F=/파일이름                          # 올려둔 오브젝트
```

```bash
# Range 통과 — 완료 조건. 206 이어야 한다
docker compose exec poc-harness curl -sI -r 0-1023 "https://$D$F" | head -5
```

```bash
# 캐시 동작 — 두 번 쏘면 두 번째가 Hit
docker compose exec poc-harness sh -c "curl -sI https://$D$F | grep -i x-cache; curl -sI https://$D$F | grep -i x-cache"
```

```bash
# 쿼리스트링이 캐시 키에서 빠졌는지 — ?a=1 과 ?a=2 가 둘 다 Hit 이면 제외된 것
docker compose exec poc-harness sh -c "curl -sI 'https://$D$F?a=1' | grep -i x-cache; curl -sI 'https://$D$F?a=2' | grep -i x-cache"
```

```bash
# 프로파일별 엣지 이득 — 같은 크기를 로컬 origin 과 CloudFront 에서 각각
docker compose exec poc-harness sh -c "sh /work/profiles/netem.sh lte_good; curl -s -r 0-131071 -o /dev/null -w 'cdn   %{time_total}s\n' https://$D$F; curl -s -r 0-131071 -o /dev/null -w 'local %{time_total}s\n' http://poc-origin/check/2mb.bin; sh /work/profiles/netem.sh off"
```

### 결과 기록

로컬 기준선은 `backend/local-infra/poc/README.md` 에 있다(lte_good 128KiB
0.267초, 그중 고정비 ≈ 200ms). 아래는 CloudFront 측정 후 채운다.

| 확인 | 결과 |
|---|---|
| Range 206 | (미측정) |
| x-cache 2회차 | (미측정) |
| 쿼리스트링 제외 | (미측정) |
| HTTP/2 | (미측정) |
| HTTP/3 | (미측정) |
| lte_good 128KiB — CDN vs 로컬 | (미측정) |

## 4. 표준 로그 ON

CloudFront → Logging → Standard logging → S3 버킷 지정(로그용 별도 접두사).

세션 egress 의 최종 진실이다. 하네스 집계와 ±5% 안에서 맞아야 계측을
믿을 수 있다(티켓 4 의 로그 대조 항목). 로그는 몇 분 지연되어 도착한다.

**완료 조건**: 로그 버킷에 첫 행이 찍힌다.

## 5. 영상 업로드

영상이 준비된 뒤. `aws` CLI 가 필요한 유일한 단계다.

```bash
aws s3 sync ./videos s3://<버킷>/videos/ --exclude "*" --include "*.mp4"
```

로컬 스윕과 같은 파일이어야 두 경로의 수치를 비교할 수 있다. 파일명도 같게 둔다.

## 6. 보호 등급 서명 설정 (2026-09-15 완료)

§결론의 원칙이 실제 설정이 된 부분이다. **signed URL 로 확정**했다 — 쿠키를 접은
이유는 CDN 도메인이 API 도메인과 달라 `Set-Cookie` 가 CDN 요청에 실리지 않고,
쿠키는 도메인당 한 벌이라 영상 단위 권한을 못 내기 때문이다(API 명세 §1.7).

콘솔 작업이라 매니페스트가 없다. 배포를 다시 만들면 이 절차를 반복한다.

**1) 키페어 (로컬)**

```bash
openssl genrsa -out cf_private.pem 2048
openssl rsa -pubout -in cf_private.pem -out cf_public.pem
```

`cf_private.pem` 은 팀 비밀 저장소 + `core-api-secret` 에만 둔다
(`k8s/40-core-api/secret.example.yaml.txt` 의 주입 절차 참조).

**2) CloudFront → Key management → Public keys** — `cf_public.pem` 등록.
생성된 **Public key ID** 가 앱 설정 `motif.media.signing.key-pair-id` 다
(현재 `K27QE85H6DLYDD`). 비밀이 아니라 서명 URL 쿼리에 실려 나간다.

**3) Key groups** — 위 공개키로 키 그룹 생성.

**4) 배포 → Behaviors → Create behavior**

| 항목 | 값 |
|---|---|
| Path pattern | `private/*` |
| Restrict viewer access | **Yes** → 3) 키 그룹 |
| Cache policy | `CachingOptimized` |
| Viewer protocol policy | Redirect HTTP to HTTPS |

> **`Default (*)` 를 고치는 것이 아니다.** 기본 동작은 path pattern 을 바꿀 수 없고,
> 거기에 Restrict viewer access 를 켜면 `challenges/`·`public/`·`assets/` 까지 전부
> 서명을 요구해 지금 돌아가는 영상·썸네일이 통째로 403 이 된다.

> **캐시 정책에 쿠키·헤더·쿼리스트링을 추가하지 마라.** 넣으면 서명 파라미터가
> 캐시 키에 들어가 적중률이 0 이 된다. 오리진에 전달할 것이 생기면 캐시 정책이
> 아니라 **오리진 요청 정책**에 넣는다.

**검증** — 둘 다 403 이지만 본문이 다르다. 그게 구분점이다.

```bash
CDN=https://d21so2cpi8si9r.cloudfront.net
curl -s "$CDN/private/motif/none/master.m3u8"          # MissingKey  ← 서명 요구 중
curl -s "$CDN/public/challenge/none/master.m3u8"       # AccessDenied ← 기본 동작
curl -s -o /dev/null -w '%{http_code}\n' "$CDN/challenges/<uuid>/master.m3u8"  # 200
```

`MissingKey` 는 CloudFront 가 엣지에서 직접 내는 응답이다 — S3 까지 가지 않았다는
뜻이라 behavior 가 붙은 증거다. `AccessDenied` 는 S3/OAC 가 낸 것이다.

**아직 안 한 것**: `cloudfront:CreateInvalidation` 권한. §8.2 의 `PUBLIC → PRIVATE`
전환을 구현할 때 필요하다(ADR-0010 "남은 배선" 9번).

## 결론에 적을 원칙 — 비공개 영상 URL

ADR-0010 에 들어갈 내용이다.

**비공개(PRIVATE) 영상은 S3 presigned URL 이 아니라 CloudFront 서명
URL/쿠키로 준다.**

S3 presigned 는 두 가지를 동시에 깬다.

1. **CDN 캐시를 깬다** — presigned URL 은 사용자·만료마다 서명 쿼리스트링이
   달라진다. 그 쿼리스트링이 캐시 키에 들어가면 사람마다 다른 객체가 되어
   적중률이 0% 가 된다. 캐시 키에서 빼면 서명이 무의미해진다. 둘 다 안 된다.
2. **원본 egress 를 그대로 물린다** — 캐시가 안 먹으니 모든 요청이 S3 를
   때린다. CDN 을 붙인 이유가 사라지고, S3 데이터 전송 비용이 그대로 나온다.

CloudFront 서명은 엣지에서 검증하므로 캐시가 유지된다 — 서명 파라미터가 예약값이라
캐시 키에 들어가지 않는다.

> **2026-09-15 확정: URL 서명이다(쿠키 아님).** 이 문서를 쓸 당시에는 쿠키가 피드에
> 맞아 보였으나, CDN 도메인이 API 도메인과 달라 `Set-Cookie` 가 CDN 요청에 실리지
> 않는다. 쿠키는 도메인당 한 벌이라 여러 사람의 영상이 섞인 피드에서 영상 단위
> 권한도 못 낸다. 재생목록은 core-api 가 다시 써서 조각 주소마다 서명을 박는다
> (API 명세 §1.7). 설정 절차는 위 §6.

## 막혔을 때

이 티켓이 막히면(계정 정지·비용·권한) PoC 는 로컬 수치로 결론을 낸다.
CDN 항은 문헌값으로 대체하고, 엣지가 가까워지는 효과는 `netem` 의 RTT 를
낮춰 민감도만 본다 — 앞서 잰 대로 lte_good 128KiB 의 267ms 중 200ms 가
고정비이고 그 대부분이 RTT 이므로, RTT 를 줄였을 때의 상한은 로컬에서도
계산된다. 재현 불가능한 것은 엣지 캐시 적중률과 실제 지리적 지연뿐이다.
