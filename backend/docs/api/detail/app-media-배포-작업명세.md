# app-media 배포 — 작업 명세

**작성 2026-09-14 · 기준 브랜치 `develop/backend` · 운영 `core-api:49`**

새 세션이 이 문서 하나로 시작할 수 있게 쓴다. 서버 상태는 전부 실측이다.

---

## 1. 왜 지금 해야 하나

안드로이드가 **숏폼 HLS 재생**을 붙이려면 `master.m3u8` 이 있어야 한다. 그걸 만드는
유일한 주체가 `app-media` 인데 **배포돼 있지 않다.** 썸네일도 같은 워커가 같은 잡에서
만들므로 따로 해결할 수 없다.

지금 챌린지를 등록하면 등록은 성공하지만 — 등록 API 는 산출물 존재를 검증하지 않는다 —
`job.media` 메시지가 쌓이기만 하고 `challenges.video_key` 는 원본 MP4 인 채로 남는다.
안드로이드는 HLS 가 아니라 MP4 를 받는다.

---

## 2. 현재 상태 (2026-09-14 서버 실측)

```
kubectl get deploy
core-api   1/1     registry.local/motif/core-api:49
postgres   1/1
rabbitmq   1/1
redis      1/1
```

**앱 단위는 core-api 하나뿐이다.** `app-media`·`app-notification` 둘 다 없다.

큐는 전부 선언돼 있고 비어 있다 — `job.media` 는 **messages 0, consumers 0**.
토폴로지는 core-api 가 선언하므로 워커가 뜨면 바로 붙는다.

노드 여유는 충분하다:

| | 값 |
|---|---|
| 노드 | 15Gi RAM / 4 vCPU / 309G disk |
| 현재 사용 | mem 3.2Gi (20%) · cpu 266m (6%) · disk 20G (7%) |
| 요청 합계 | cpu 850m (21%) · mem 1676Mi (10%) |

서버 접속: `ssh -i J15D104T.pem ubuntu@j15D104.p.ssafy.io` (pem 은 `~/Desktop/motif/`).
`kubectl` 은 서버에서 sudo 없이 된다.

---

## 3. app-media 가 하는 일

결정문은 [ADR-0010](../../adr/0010-video-delivery.md), 모듈 설명은
[app-media/README.md](../../../app-media/README.md).

```
S3 원본(MP4) → 렌디션 2종(720p/480p) → HLS(fMP4) → 썸네일 → S3 산출물 → DB 키 기록
```

### 입력 — `job.media` 큐

`MediaJobRequestedEvent` (`module-event/.../contract/`). 직렬화는 snake_case / ISO-8601 UTC.

| 필드 | 의미 |
|---|---|
| `mediaJobUuid` | 전 구간 멱등 키 |
| `sourceType` | `CHALLENGE_ORIGIN`(어드민 업로드) \| `COMPOSITE_RESULT`(합성 결과) |
| `sourceSeq` | 갱신할 행 PK — `challenges` 또는 `composite_jobs` |
| `sourceKey` | S3 원본 키. 재생용이 아니라 **입력**이라 MP4 그대로 |
| `outputPrefix` | 산출물 프리픽스. 이 아래에 `master.m3u8`·`thumb.jpg`·화질별 디렉터리 |

**두 경로가 같은 이벤트로 들어온다.** 인코딩 파라미터를 한 곳에서만 관리하기 위한
구조다(ADR-0010 D1). GPU 워커가 없어 **지금은 `CHALLENGE_ORIGIN` 만 실제로 흐른다** —
안드로이드가 HLS 를 붙여볼 영상이 여기서 나온다.

### 처리 — `MediaJobProcessor.process`

1. **멱등 가드** — DB 의 키가 이미 `master.m3u8` 이면 건너뛴다. 10초짜리 인코딩을
   재전달마다 다시 돌릴 이유가 없다
2. `workDir/{mediaJobUuid}/` 생성 → S3 에서 `source.mp4` 다운로드
3. `HlsPackager.packageForPlayback` — 렌디션 → HLS → 썸네일
4. 산출물 트리를 `outputPrefix` 아래로 업로드
5. **DB 키 갱신** — 마스터 재생목록 경로 **하나만** 기록한다. 나머지는 같은 디렉터리
   규약으로 찾으므로 스키마 변경이 없다(ADR-0010 D4)
   ```sql
   UPDATE challenges SET video_key = ?, thumbnail_key = ?, mod_dttm = now()
    WHERE challenge_seq = ?
   ```
   `JdbcTemplate` 이다. `challenges`·`composite_jobs` 엔티티는 각 도메인 티켓 소유이고
   여기서 만들면 중복이 난다
6. `finally` 로 작업 디렉터리 삭제

갱신 행이 1이 아니면 예외 → 메시지는 `dlq.job.media` 로 간다.

### 인코딩 사양 (실측)

| | 값 |
|---|---|
| 사다리 | **1080p / 720p / 480p 3단** — 2026-09-14 확정, §4.7. 코드는 아직 2단이다 |
| 컨테이너 | fMP4 + `EXT-X-BYTERANGE` |
| 썸네일 | `-ss {선택 시각} -frames:v 1 -vf scale=-2:1280 -q:v 5` → `thumb.jpg`. 시각은 어드민이 고른다 — 2026-09-14 확정, §4.8. 코드는 아직 1.5초 고정이다 |
| 실측(60초 1080p) | 가공 6.7초, 원본 25.8MB → 산출물 12.4MB (48%) |
| 처리 배속 | 영상 길이의 0.1배 (1코어 제한 시 ~0.3배) |

**핵심 테스트는 `화질_간_키프레임이_정확히_일치한다` 다.** 깨지면 예외도 로그도 없이
화질 전환만 안 된다 — 사람이 눈으로 보기 전엔 모른다.

**인코딩 파라미터(GOP·비트레이트·faststart)를 core-api 에 복사하지 마라.** core-api 에는
ffmpeg 자체가 없다(어드민 명세 §1.4).

---

## 4. 해야 할 작업

### 4.1 `application-prod.yml` 작성 — **없어서 지금 띄우면 기동 실패한다**

`app-media/src/main/resources/` 에 `application.yml` **하나뿐**이다. DB·MQ 접속 설정이
아예 없는데 `MediaJobProcessor` 는 `JdbcTemplate` 을 쓴다. DataSource 가 없어 기동에서
죽는다. 로컬에서도 실제로 돌린 적이 없다 — compose 에 파이썬 `stub-worker` 가
대신 들어 있다.

`app-notification/src/main/resources/application-prod.yml` 을 그대로 본떠라:

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://postgres:5432/motif}
    username: ${DB_USERNAME:core_app}      # challenges 를 UPDATE 한다 — core 스키마
    password: ${DB_PASSWORD}
  rabbitmq:
    host: ${MQ_HOST:rabbitmq}
    port: 5672
    username: ${MQ_USERNAME:media}
    password: ${MQ_PASSWORD}
    virtual-host: /motif
```

비밀값은 fallback 금지(ADR-0004 placeholder 규율), 접속 정보만 fallback 허용.

**Liquibase 를 켜지 마라.** core 스키마 마이그레이션은 core-api 소유다.

### 4.2 RabbitMQ `media` 사용자 신설 — **없다**

현재 사용자: `admin`·`core-api`·`notification`·`gpu-worker`. **`media` 가 없다.**

```
core-api      configure ^$  write ^motif\.(topic|dlx)$  read ^evt\.core$
notification  configure ^$  write ^$                    read ^evt\.notification$
gpu-worker    configure ^$  write ^motif\.topic$        read ^job\.composite$
```

같은 최소권한 모양으로 만든다 — `job.media` 를 읽고, 실패 시 DLX 에 쓴다.
`rabbitmq-secret` 에 비밀번호를 넣고 `core-api-secret` 처럼 앱 Secret 으로 옮긴다.

### 4.3 IAM — 워커용 S3 권한

**오늘(2026-09-14) 겪은 크로스 계정 문제가 여기도 그대로 온다.** 운영 서버는
Lightsail 이고 IMDS 가 `AmazonLightsailInstanceRole`(SSAFY 계정) 자격증명을 흘린다.
`motif-media` 버킷은 우리 계정이라 그걸로는 못 쓴다. `backend/CLAUDE.md` 의
"S3 자격증명 — IMDS 를 믿지 마라" 절을 먼저 읽어라.

워커는 core-api 보다 권한이 더 필요하다 — **원본을 읽고 산출물 트리를 쓴다**:

```json
{"Effect":"Allow","Action":["s3:GetObject","s3:PutObject"],"Resource":"arn:aws:s3:::motif-media/*"},
{"Effect":"Allow","Action":["s3:ListBucket"],"Resource":"arn:aws:s3:::motif-media"}
```

IAM 사용자 `motif-app-media` 로 따로 판다. core-api 키를 공유하면 하나 털렸을 때
둘 다 갈아야 하고 CloudTrail 에서 구분이 안 된다.

### 4.4 k8s 매니페스트 — `infra/k8s/45-app-media/`

`40-core-api/deployment.yaml` 을 본뜨되 다른 점:

- **Service·Ingress 없음** — HTTP 엔드포인트가 없는 큐 소비자다
- `imagePullPolicy: Never` — 레지스트리 없이 containerd import (ADR-0007)
- **`emptyDir` 볼륨을 `/var/tmp/motif-media` 에** — 원본 + 렌디션이 동시에 올라간다.
  60초 1080p 기준 원본 25.8MB + 산출물 12.4MB. 넉넉히 잡아도 작다
- `resources.limits.cpu: "1000m"` — **README 권장이다.** 인코딩이 길어질 뿐 core-api 는
  살아 있다. 메모리는 core-api(req 640Mi/lim 1Gi) 수준이면 충분
- 프로브는 관리 포트 **9083** (core-api 는 9081)
- `envFrom` 으로 새 Secret + 필요하면 configmap

`app-notification` 도 매니페스트가 없다 — 같이 만들 거면 `46-app-notification/`.

### 4.5 첫 배포는 수동으로

Jenkinsfile 은 `IMAGE`·`DEPLOYMENT`·`CONTAINER` 가 core-api 로 **하드코딩**돼 있다.
파라미터화는 나중에 하고, 서버에서 한 번 직접 띄워 파이프라인이 도는 걸 먼저 봐라:

```bash
./gradlew :app-media:bootJar :app-media:test
docker build -t registry.local/motif/app-media:1 backend/app-media
docker save registry.local/motif/app-media:1 | sudo k3s ctr images import -
sudo k3s ctr images ls -q | grep -qx registry.local/motif/app-media:1   # import 확인
kubectl apply -f infra/k8s/45-app-media/
```

Dockerfile 은 이미 있다 — alpine + ffmpeg, 441MB. `eclipse-temurin:21-jre-alpine` 을
고른 근거(Ubuntu 대비 2.5배 작다)는 파일 주석에 있다.

### 4.7 사다리 3단 확장 + 업스케일 금지 — **2026-09-14 확정**

**1080p 를 추가한다.** 지금 사다리는 720p/480p 2단이라 1080p 원본이 들어와도
406×720 이 상한이다. 원본 화질로 스트리밍하는 것이 맞고, 대역폭이 모자라면 ABR 이
720p·480p 로 내려가면 된다 — 그게 사다리를 두는 이유다.

```java
// Rendition.java — 낮은 화질부터. 마스터 재생목록 순서와 같다
new Rendition("480p",  480,  700),
new Rendition("720p",  720, 1200),
new Rendition("1080p", 1080, 2500),   // 추가
```

비트레이트 2500kbps 는 9:16 1080×1920 기준 제안값이다. 실측으로 조정해라 —
PoC 가 60초 1080p 원본 25.8MB → 산출물 12.4MB(480p 4.2 + 720p 7.5)였으니
1080p 단이 60초에 ~18MB 붙어 산출물 합계가 원본을 넘어선다. 인코딩 시간도
6.7초 → 15초 남짓으로 는다. 숏폼이라 총량이 작아 감당 가능한 범위다.

**ADR-0010 D1 을 갱신해야 한다** — 2단 사다리가 거기 결정으로 적혀 있다.
README §7 미결 표 1번("720p 상한이 부족하다는 피드백이 나오면")도 닫는다.

#### 업스케일 금지 — 1080p 를 넣으면 선택이 아니라 필수다

`packageForPlayback` 이 원본 해상도를 **보지 않고** 사다리를 통째로 돈다. 720p 원본에
`scale=-2:1080` 을 걸면 **없는 화질을 만들어낸다** — 비트레이트만 쓰고, 마스터에
`RESOLUTION=608x1080` 이 적혀 ABR 이 그걸 최고 화질로 믿는다. 실제로는 720p 를 늘린
흐릿한 영상이다.

**판단은 영상만 보면 된다 — 어드민 입력이 필요 없다.** `Ffmpeg.resolution(Path)` 이
이미 있다(지금은 산출물에만 쓴다). 사다리 루프 전에 원본에 한 번 부르고
`r.height() > sourceHeight` 인 단을 건너뛴다.

주의 두 가지:

- **스킵은 실패가 아니다.** 현재 정책이 "전부 성공하거나 아무것도 남기지 않는다"
  (ADR-0010 D4 — 렌디션 하나만 남으면 ABR 이 조용히 한 단으로 떨어지므로)인데,
  건너뛴 단을 실패로 처리하면 480p 원본이 통째로 DLQ 로 간다. 스킵 경로를 명시적으로
  갈라라
- **모든 단이 스킵되면** (원본이 480p 미만) 최소 한 단은 원본 해상도로 만들어야 한다.
  아니면 재생할 것이 없다

업로드 쪽에서 막는 방법도 있다 — `VideoRules` 에 최소 해상도 규칙 추가. 지금은
세로 영상 여부(`height > width`)와 길이만 본다. 둘 다 해도 되지만 **워커 가드가
먼저다** — 합성 결과(`COMPOSITE_RESULT`)는 업로드 검증을 타지 않는다.

### 4.8 썸네일 시각 선택 — **2026-09-14 확정**

지금은 `THUMBNAIL_AT = 1.5` 고정이고, 워커가 `challenges.thumbnail_key` 를
무조건 덮어쓴다. 어드민이 고를 수단이 없다.

**어드민이 시각을 고르고 ffmpeg 가 원본에서 뽑는다.** 이미지를 따로 업로드하는 방식이
아니다 — 새 S3 키 규약도, presign 종류 추가도, "썸네일이 있으면 덮어쓰지 않는다"는
상태 분기도 필요 없고, 화질이 원본 그대로 나온다.

```
화면(challenge-new)  원본 <video id="frame"> 를 스크럽 → currentTime
                     ※ bbox 그리려고 이미 띄워놓은 그 엘리먼트다
        ↓
core-api             ChallengeCreateRequest 에 thumbnailAtMs 추가 (선택, 기본 1500)
        ↓
module-event         MediaJobRequestedEvent 에 thumbnail_at_ms 추가
                     ※ 계약이 additive-only 라 필드 추가는 허용된다 (module-event/README)
        ↓
app-media            THUMBNAIL_AT 상수를 이벤트 값으로. 없으면 1.5 기본
```

**DB 스키마 변경 없음.** `challenges.thumbnail_key` 는 지금처럼 `{prefix}/thumb.jpg` 다.

검증할 것: 시각이 영상 길이를 넘으면 ffmpeg 가 빈 프레임을 뱉는다 — core-api 에서
`durationMs` 와 대조해 막아라(폼 값이라 임의의 수를 보낼 수 있다).

별도 이미지를 디자인해 올리고 싶어지면 그때 업로드 방식을 **추가로** 얹으면 된다.
두 방식은 공존할 수 있다.

### 4.6 Jenkins 파이프라인 — 마지막

`IMAGE_REPO`·`DEPLOYMENT`·`CONTAINER` 를 파라미터로 빼거나 두 번째 Jenkinsfile 을 판다.
`./gradlew :app-core-api:bootJar` 부분도 모듈별로 갈라야 한다.

---

## 5. 검증

### 5.1 배포 직후 (워커만)

```bash
kubectl logs -l app=app-media --tail=50          # 기동 로그, MQ 연결
kubectl exec deploy/rabbitmq -- rabbitmqctl list_queues -p /motif name messages consumers
```
`job.media` 의 **consumers 가 1** 이 되어야 한다. 0 이면 MQ 사용자·권한 문제다.

### 5.2 파이프라인 한 바퀴

어드민에서 챌린지를 등록한다(`/admin/challenges/new`). 그러면:

1. core-api 가 `job.media` 발행 (커밋 후 — 롤백되면 0건)
2. 워커 로그에 `후처리 완료: mediaJobUuid=… sourceType=CHALLENGE_ORIGIN prefix=…`
3. S3 `challenges/{uuid}/` 아래에 `master.m3u8`·`thumb.jpg`·화질별 디렉터리
4. DB 확인 — `video_key` 가 `.mp4` 에서 `…/master.m3u8` 로 바뀌어야 한다
   ```sql
   SELECT challenge_seq, video_key, thumbnail_key FROM motif_core.challenges;
   ```
5. `GET /api/v1/challenges/{seq}` (§7.2) 의 `videoUrl` 이 CloudFront 의 `master.m3u8`

### 5.3 CDN

`motif.media.public-base-url` 은 **이미 넣었다**(MR !72) —
`https://d21so2cpi8si9r.cloudfront.net`, origin 이 `s3://motif-media`.
그 전까지는 모든 미디어 URL 이 조용히 `null` 이었다.

안드로이드 확인용 실물이 이미 CDN 에 올라가 있다 (PoC 산출물, Media3 의 fMP4 +
`EXT-X-BYTERANGE` 지원 확인용):

```
https://d21so2cpi8si9r.cloudfront.net/poc/hls/master.m3u8
https://d21so2cpi8si9r.cloudfront.net/poc/hls/thumb.jpg
```

**워커 배포를 기다리지 않고 안드로이드가 지금 붙여볼 수 있다.**

---

## 6. 함정

**1. 지금 띄우면 기동 실패한다.** `application-prod.yml` 이 없어 DataSource 가 없다.
4.1 이 먼저다.

**2. IMDS 자격증명을 믿지 마라.** 4.3 참조. presign 이 로그에 찍혀도 권한이 있다는
뜻이 아니다 — 서명은 권한을 검사하지 않는다.

**3. `envFrom` 은 파드 생성 시점에 한 번만 해석된다.** Secret 을 patch 했으면
`kubectl rollout restart` 까지 해야 한다. 넣었다고 믿지 말고
`kubectl exec deploy/app-media -- printenv AWS_ACCESS_KEY_ID` 로 확인해라.

**4. 설정값이 없어도 앱은 멀쩡히 뜬다.** `ADMIN_SEED_HASH` 가 placeholder 문자열째로
DB 에 들어간 적이 있고, `public-base-url` 이 없어 모든 미디어 URL 이 null 이던 것도
서버에서는 아무 증상이 없었다. **빈 DB·빈 설정으로 실제로 한 번 돌려봐라.**

**5. 워커가 core-api 와 같은 노드에서 돈다.** `concurrency: 1` 로 한 번에 한 건만
처리하게 돼 있다 — **큐가 밀리는 편이 API 가 느린 것보다 낫다.** GPU 서버를 빌리면
파드만 옮긴다(코드·이벤트 계약 변경 없음).

**6. `git worktree list` 로 브랜치를 먼저 확인해라.** 낡은 워크트리를 읽고 "파일이
없다"고 오판한 사고가 두 번 있었다.

---

## 7. 범위 밖

- **합성 경로(`COMPOSITE_RESULT`)** — GPU 워커가 없어 안 돈다. 코드는 같은 경로를
  타므로 GPU 가 붙으면 추가 작업 없이 흐른다
- **`app-notification` 배포** — 같은 종류의 작업이지만 별건. FCM 이 필요해질 때
- **숏폼 피드 §7.1 `GET /challenges/feed`** — 미구현. 단일 영상 HLS 재생은 §7.2 로
  지금 테스트 가능하다
- **360p 사다리 확장** — README §7 미결 표 2번. 1080p 는 §4.7 로 범위 안에 들어왔다
