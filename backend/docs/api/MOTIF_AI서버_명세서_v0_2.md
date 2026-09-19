# MOTIF AI 서버 명세서 — v0.2

**개정 2026-09-19 (v0.1 2026-09-18) · 대상: AI 팀 · 근거: API 명세 v0.11 §13 · 어드민 명세 v0.5 §1.4·§3.7 · DB 명세 v0.15 §37 · ADR-0005·0010**

AI 서버가 **무엇을 받고, 무엇을 만들고, 무엇을 돌려주는지** 적는다.
백엔드 쪽 할 일은 [`detail/합성-파이프라인-작업명세.md`](detail/합성-파이프라인-작업명세.md) 에 따로 있다.
두 문서가 어긋나면 이 문서가 계약이다 — 백엔드가 여기에 맞춘다.

> **v0.2 도 제안이다.** §3 필드·§6 실패 코드·§11 미결은 AI 팀 확인 후 확정한다.

---

## 0-1. v0.1 → v0.2 변경 요약 — 큐 구독에서 HTTP 양방향으로

AI 팀이 상주 GPU 서버 대신 **GCP Cloud Run Jobs** 를 쓰기로 했다. Cloud Run Job 은 상주하지 않는다 —
누가 실행을 시켜야 돌고, 끝나면 사라진다. 그래서 큐를 구독할 수 없고, 연동의 양 끝이 HTTP 로 바뀐다.

| 항목 | v0.1 | v0.2 |
|---|---|---|
| 실행 형태 | 상주 워커(대여 GPU 서버) | **Cloud Run Job** — 실행 1회 = 잡 1개 |
| 잡 받기 | RabbitMQ `job.composite` 구독 | 백엔드가 **AI 접수 URL 로 HTTP POST** → 접수 서비스가 Job 실행(JSON 은 환경변수로) |
| 결과 보고 | `composite.done` / `composite.failed` 발행 | **백엔드 콜백 HTTP POST** `/done` · `/failed` |
| 네트워크 | Tailscale 조인 | **불필요.** 인터넷 HTTPS 양방향 |
| 인증 | MQ 계정 `gpu-worker` | 공유 토큰 헤더 `X-Motif-Worker-Token` — 양방향 같은 값 |
| 잡 JSON | snake_case | **동일.** 실리는 곳만 큐 → 환경변수 |
| 결과 저장 | S3 `results/…` | **동일** |
| 실패 코드 · 편집본 사전 작업 | §6 · §7 | **동일** |
| 재시도 | 브로커 재전달 | 없음. **Cloud Run 자체 재시도도 0 으로** 둔다(§2.3) |

---

## 0. 한 장 요약

```
앱 ─(1) 촬영본 업로드─▶ S3  raw/{jobUuid}.mp4
앱 ─(2) 합성 요청────▶ core-api ─(3) HTTPS POST {잡 JSON} ─▶ 접수 서비스(Cloud Run Service, CPU)
                                                              │ (4) Job 실행 — JSON 을 env MOTIF_JOB 으로
                                                              ▼
                                                    ┌───── 합성 Job(Cloud Run Job, GPU) ─────┐
                                                    │ (5) S3 에서 촬영본·베이스 받기           │
                                                    │ (6) 합성 → MP4 한 개                     │
                                                    │ (7) S3  results/…/{jobUuid}.mp4          │
core-api ◀─(8) HTTPS POST /done | /failed ──────────┴─────────────────────────────────────────┘
core-api ─(9) HLS 가공(app-media) → DONE 확정 → 푸시(FCM) ─▶ 앱
```

AI 팀이 만드는 것은 **둘**이다.

| 구성물 | 종류 | 하는 일 |
|---|---|---|
| **접수 서비스** | Cloud Run Service (CPU, 항상 켜둘 필요 없음) | 백엔드의 POST 를 받아 토큰을 확인하고 합성 Job 을 실행한다. 30줄짜리다(부록 B) |
| **합성 Job** | Cloud Run Job (GPU) | env 의 JSON 을 읽어 S3 입력 두 개를 받고, 합성해 MP4 를 S3 에 올리고, 백엔드에 콜백 한 번 하고 끝난다(부록 A) |

DB·HLS·썸네일·푸시는 전부 백엔드 몫이다.

---

## 1. 하지 않는 것

| 안 하는 것 | 누가 하나 |
|---|---|
| DB 접속 | core-api. AI 는 DB 자격증명을 받지 않는다 |
| 큐(RabbitMQ) 접속 | 없다. 통신 수단은 **HTTPS 와 S3 둘**뿐 |
| 화질별 인코딩(HLS)·썸네일 | app-media. 결과는 MP4 한 개로 끝낸다 — 인코딩 파라미터는 한 곳에만 둔다(ADR-0010 D1) |
| 푸시 알림 | app-notification |
| 잡 상태 바꾸기(QUEUED → DONE 등) | core-api 가 콜백을 받아서 한다 |
| 실패한 잡 다시 돌리기 | 사용자(앱 "다시 시도")와 백엔드 스윕이 결정한다. Job 은 실패를 **보고만** 한다 |

---

## 2. GCP 세팅

### 2.1 접수 서비스 (Cloud Run Service)

- 공개 URL 하나. 예: `https://motif-submit-xxxx.a.run.app/submit`. 백엔드에 알려준다 — 바뀌면 백엔드 설정을 바꿔야 한다
- `--allow-unauthenticated` 로 열고 **앱에서 토큰을 검사**한다(백엔드가 GCP 자격증명 없이 부르기 위해서다)
- 하는 일: 헤더 `X-Motif-Worker-Token` 대조 → 본문 JSON 을 그대로 `MOTIF_JOB` 환경변수 override 로 넣어 Job 실행(`jobs.run`) → **202** 와 실행 이름 응답
- 실행에 실패하면(쿼터·권한) **503** 을 돌려준다. 백엔드가 나중에 다시 부른다
- 서비스 계정에 Job 실행 권한 — `roles/run.developer` (`run.jobs.run` · `run.jobs.runWithOverrides`). 부족하면 로그에 찍히는 권한 이름대로 더한다
- 리소스는 최소(CPU 1 · 512Mi · min-instances 0). 첫 호출 콜드스타트 1~2초는 백엔드가 감안한다

### 2.2 합성 Job (Cloud Run Job)

```bash
gcloud beta run jobs deploy motif-compose \
  --image {리전}-docker.pkg.dev/{프로젝트}/motif/compose:{태그} \
  --region {GPU 되는 리전} \
  --gpu 1 --gpu-type nvidia-l4 --cpu 8 --memory 32Gi \
  --tasks 1 --max-retries 0 --task-timeout 30m \
  --set-env-vars MOTIF_CALLBACK_BASE=https://j15d104.p.ssafy.io,S3_BUCKET=motif-media,AWS_REGION=ap-northeast-2 \
  --set-secrets MOTIF_WORKER_TOKEN=motif-worker-token:latest,AWS_ACCESS_KEY_ID=motif-gpu-worker-key-id:latest,AWS_SECRET_ACCESS_KEY=motif-gpu-worker-secret:latest
```

옵션 이름은 gcloud 버전에 따라 다를 수 있다. 값이 중요하다:

| 옵션 | 값 | 왜 |
|---|---|---|
| `--max-retries` | **0** | 컨테이너가 죽으면 Cloud Run 이 같은 잡을 다시 돈다. 그러면 콜백이 두 번 가거나 GPU 를 두 번 쓴다. 재시도는 백엔드가 정한다 |
| `--task-timeout` | 처리 시간 + 여유 | 넘으면 강제 종료돼 **콜백 없이** 죽는다. 백엔드 스윕이 이 값 + 5분 뒤에 실패로 닫는다 — 값을 백엔드에 알려준다 |
| `--tasks` | 1 | 잡 1개 = 실행 1개 = 태스크 1개 |
| 리전 | GPU 지원 리전 | 서울(asia-northeast3)엔 GPU 가 없을 수 있다. S3(서울)와 리전이 달라도 배치 작업이라 상관없다 |

### 2.3 환경변수

**배포 시 고정** (Job 설정)

```
MOTIF_CALLBACK_BASE=https://j15d104.p.ssafy.io     # 콜백 주소 앞부분
MOTIF_WORKER_TOKEN=…                                # Secret Manager. 백엔드와 같은 값
AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY           # Secret Manager. IAM motif-gpu-worker
AWS_REGION=ap-northeast-2
S3_BUCKET=motif-media
WORK_DIR=/tmp/motif                                 # Cloud Run 의 /tmp 는 메모리다 — 메모리 산정에 넣는다
```

**실행마다 override** (접수 서비스가 넣는다)

```
MOTIF_JOB={ …§3.2 의 JSON 문자열… }
MOTIF_REQUEST_ID=…                                  # 백엔드가 보낸 X-Request-Id. 로그·콜백에 되돌린다
```

Cloud Run 이 자동으로 주는 `CLOUD_RUN_EXECUTION` 을 `worker_id` 로 쓴다 — 콜백과 Cloud Logging 을 이어 볼 수 있다.

### 2.4 알아둘 것

- **실행마다 콜드스타트다.** 이미지 pull + 모델 로딩이 매번 든다. 모델 가중치는 이미지에 굽거나 GCS 볼륨으로 붙여 **첫 프레임까지 걸리는 시간을 재서 백엔드에 알려준다**(앱 문구 "약 N 분" 과 스윕 시간의 근거)
- **대기열이 없다.** 접수되는 대로 다 실행된다. GPU 쿼터를 넘으면 실행 요청이 실패하고 백엔드가 나중에 다시 접수한다. 유료(모티프) 우선순위는 이 구조에선 없다
- **`/tmp` 는 메모리 파일시스템**이다. 촬영본 + 베이스 + 결과(각 수십 MB)가 메모리를 먹는다
- 실행 하나에 한 잡이다. Job 안에서 여러 잡을 돌리지 않는다

---

## 3. HTTP 계약

### 3.1 공통 규약

- 본문은 **JSON · snake_case**. 모르는 필드는 무시한다 — 나중에 늘어도 깨지지 않게
- `Content-Type: application/json`
- 인증: 헤더 **`X-Motif-Worker-Token: {토큰}`**. 양방향 같은 값. 없거나 다르면 401
- 추적: 헤더 **`X-Request-Id`**. 백엔드가 접수에 실어 보낸다. Job 은 같은 값을 콜백 헤더에 그대로 실고 모든 로그 줄에 찍는다 — 앱 → 백엔드 → AI → 백엔드 → 알림을 한 ID 로 grep 한다
- 백엔드 응답은 API 공통 봉투다(`result` · `code` · `message` · `data` · `timestamp`). Job 은 **HTTP 상태 코드만** 보면 된다

### 3.2 접수 — 백엔드 → AI

```http
POST {접수 URL}
X-Motif-Worker-Token: …
X-Request-Id: 7c1e…
Content-Type: application/json
```

**CHALLENGE** (베이스 = 슬롯 인물이 지워진 편집본, 무교체면 원본)

```json
{
  "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
  "job_type": "CHALLENGE",
  "attempt": 1,
  "raw_video_key": "raw/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
  "base_video_key": "edited/11/7d2f…e1.mp4",
  "output_video_key": "results/challenge/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
  "correction_enabled": true,
  "participant_mask": 3,
  "slots": [
    { "participant_mask": 1, "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 } },
    { "participant_mask": 2, "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 } }
  ],
  "base_duration_ms": 15000
}
```

**MOTIF** (베이스 = 구매한 에셋 영상)

```json
{
  "job_uuid": "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
  "job_type": "MOTIF",
  "attempt": 1,
  "raw_video_key": "raw/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.mp4",
  "base_video_key": "assets/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071.mp4",
  "output_video_key": "results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f.mp4",
  "correction_enabled": false,
  "participant_mask": 0,
  "slots": [],
  "base_duration_ms": 9800
}
```

| 필드 | 타입 | 뜻 |
|---|---|---|
| `job_uuid` | string | 잡 식별자. 콜백 URL·로그에 그대로 쓴다 |
| `job_type` | `CHALLENGE` \| `MOTIF` | 합성 종류 |
| `attempt` | int | 몇 번째 시도인지. 로그용 |
| `raw_video_key` | string | 사용자 촬영본 S3 키. MP4, 세로(폰 촬영). 회전 메타데이터가 붙어 있을 수 있다 |
| `base_video_key` | string | 합성 베이스 S3 키. **항상 MP4** — CHALLENGE 는 편집본(`edited/…`) 또는 원본(`challenges/{uuid}.mp4`), MOTIF 는 에셋(`assets/{uuid}.mp4`) |
| `output_video_key` | string | 결과를 올릴 S3 키. **이 키에 그대로 PUT**. 재시도면 같은 키를 덮어쓴다 |
| `correction_enabled` | bool | 사용자가 고른 보정 여부. MOTIF 는 항상 `false`. 뜻은 AI 팀 정의(§11) |
| `participant_mask` | int | 교체하는 슬롯의 비트 합. `0` = 무교체(`slots` 빈 배열) |
| `slots[]` | array | 교체 대상 슬롯만. `participant_mask` 는 단일 비트(1·2·4…). `anchor` 는 촬영 전 선택 화면에서 그 슬롯의 프로필 원이 앉는 자리 — **썸네일 대비 0~1 비율**(`x`·`r` 은 가로, `y` 는 세로 기준). 베이스 영상에 곱하면 대략의 인물 자리다. **위치 힌트**이고 픽셀 bbox 는 없다(2026-09-18 삭제, S15P21D104-618). 옛 챌린지 행은 `null` 일 수 있다 |
| `base_duration_ms` | int | 베이스 길이(ms). 결과 길이의 기준 |

**접수 서비스의 응답**

| HTTP | 뜻 | 백엔드 동작 |
|---|---|---|
| **202** `{ "execution": "projects/…/executions/motif-compose-abc12" }` | Job 실행 시작됨 | 잡을 `PROCESSING` 으로 |
| 401 | 토큰 불일치 | 설정 오류 — 사람이 본다 |
| 503 `{ "error": "…" }` | 실행 못 함(쿼터·권한·일시 오류) | 잡은 `QUEUED` 로 남고 2분 뒤 다시 접수 |

접수 서비스는 Job 을 **띄우기만** 하고 기다리지 않는다. 응답은 1~3초 안에 온다.

### 3.3 콜백 성공 — AI → 백엔드

```http
POST {MOTIF_CALLBACK_BASE}/api/worker/v1/composite-jobs/{job_uuid}/done
X-Motif-Worker-Token: …
X-Request-Id: 7c1e…          ← 받은 값 그대로
Content-Type: application/json
```

```json
{
  "result_video_key": "results/challenge/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
  "processing_ms": 48210,
  "attempt": 1,
  "worker_id": "motif-compose-abc12"
}
```

- `result_video_key` = 받은 `output_video_key` 그대로. **S3 PUT 이 끝난 뒤에** 부른다
- `processing_ms` = 컨테이너 시작부터 업로드 끝까지
- `worker_id` = `CLOUD_RUN_EXECUTION`. 없어도 처리된다

### 3.4 콜백 실패 — AI → 백엔드

```http
POST {MOTIF_CALLBACK_BASE}/api/worker/v1/composite-jobs/{job_uuid}/failed
```

```json
{
  "fail_code": "FACE_NOT_DETECTED",
  "fail_message": "no face in 0~450 frames",
  "processing_ms": 3120,
  "attempt": 1,
  "worker_id": "motif-compose-abc12"
}
```

- `fail_code` 는 §6 표의 값만. **30자 이내 대문자·밑줄**(DB 컬럼 `VARCHAR(30)`)
- `fail_message` 는 로그용 자유 문장(500자 이내). 사용자에게 보이지 않는다 — 사용자 문구는 앱이 `fail_code` 로 고른다

### 3.5 콜백에 대한 백엔드 응답

| HTTP | 뜻 | Job 동작 |
|---|---|---|
| **200** | 받았다. 이미 처리된 잡이어도 200(멱등) | 끝. exit 0 |
| 401 | 토큰 불일치 | 재시도하지 않는다. 로그 남기고 exit 0 |
| 404 | 모르는 `job_uuid` | 같음 |
| 5xx · 연결 실패 · 타임아웃 | 백엔드 장애 | **3회 재시도**(1s → 4s → 16s). 그래도 실패면 로그 남기고 exit 0 — 백엔드 스윕이 시간 초과로 닫는다 |

---

## 4. 잡 처리 순서

```
env 읽기 ─▶ 다운로드 ─▶ 합성 ─▶ 업로드 ─▶ 콜백(done|failed) ─▶ exit 0
```

1. **읽기** — `MOTIF_JOB` 을 JSON 으로 푼다. `MOTIF_REQUEST_ID`·`job_uuid` 를 로그 컨텍스트에 넣는다
2. **다운로드** — `raw_video_key` · `base_video_key` 를 `WORK_DIR/` 로
3. **합성** — GPU 작업. 결과는 MP4(§5.3 사양)
4. **업로드** — `output_video_key` 에 PUT, `ContentType=video/mp4`
5. **콜백** — 성공 `/done` · 실패 `/failed`. 잡 하나에 **정확히 한 번**
6. **exit 0** — 실패 보고를 했어도 0 으로 끝낸다. 0 이 아니면 Cloud Run 이 실행을 "실패" 로 표시하고(재시도 0 이라 다시 돌지는 않는다) 모니터링만 더러워진다

규칙:

- **어느 단계에서 실패해도 `/failed` 를 부르고 정상 종료한다.** Job 이 스스로 다시 돌지 않는다 — 재시도는 사용자와 백엔드가 정한다(API §13.4)
- **콜백 없이 죽는 경우**(task-timeout·OOM)는 백엔드 스윕이 `task-timeout + 5분` 뒤에 실패로 닫는다. 그래서 timeout 값을 백엔드가 알아야 한다
- **같은 `job_uuid` 가 두 번 실행될 수 있다**(백엔드 재접수·재시도). 그냥 다시 처리한다. 결과 키를 덮어써도 되고, 중복 콜백은 백엔드가 200 으로 받고 무시한다
- 처리 시간이 `--task-timeout` 을 넘길 것 같으면 값을 올리되 백엔드에 알린다

---

## 5. S3 규약

### 5.1 읽는 키

| 프리픽스 | 내용 | 누가 올렸나 |
|---|---|---|
| `raw/{jobUuid}.mp4` | 사용자 촬영본. 앱이 presigned PUT 으로 직접 올림 | 앱 |
| `edited/…/{uuid}.mp4` | 슬롯 인물이 지워진 편집본 | **AI 팀**(§7) |
| `challenges/{uuid}.mp4` | 챌린지 원본(무교체 합성 베이스) | 어드민 |
| `assets/{uuid}.mp4` | 모티프 에셋 원본 | 어드민 |

메시지의 키를 그대로 GET 한다. 키를 조립하거나 추측하지 않는다.
`raw/` 는 나중에 7일 뒤 삭제될 수 있다 — 없으면 `INPUT_NOT_FOUND` 로 실패 보고한다.

### 5.2 쓰는 키

| 프리픽스 | 내용 |
|---|---|
| `results/challenge/{jobUuid}.mp4` | CHALLENGE 결과 |
| `results/motif/{jobUuid}.mp4` | MOTIF 결과 |

`output_video_key` 그대로다. 결과는 **여기 한 곳**에만 쓴다.
`private/…` 아래는 app-media 가 만드는 재생용 산출물이다 — Job 은 건드리지 않는다.

IAM `motif-gpu-worker` 권한: GET `raw/*` `edited/*` `challenges/*` `assets/*` · PUT `results/*` · ListBucket.
그 밖의 키를 만지면 `AccessDenied` 다 — 권한 문제가 아니라 규약 위반이다.
GCP → S3 는 인터넷 경로다. 잡당 수십 MB 왕복이라 비용은 무시할 수준이다.

### 5.3 결과 MP4 사양

| 항목 | 값 | 이유 |
|---|---|---|
| 컨테이너 · 코덱 | MP4 · H.264(yuv420p) + AAC | app-media 가 ffmpeg 로 다시 인코딩한다. 이것만 지키면 나머지는 자유 |
| 해상도 · 방향 | **베이스와 같게** | HLS 사다리가 원본 해상도를 기준으로 짜인다. 업스케일하지 않는다 |
| 길이 | 베이스 ±500ms | 앱은 챌린지 길이를 기준으로 UI 를 그린다 |
| 회전 | **픽셀이 바로 서 있게** — rotate 메타데이터로 세우지 않는다 | 폰 촬영본은 태그로만 세로인 경우가 많다. 그대로 두면 HLS 가공에서 눕는다 |
| fps | 고정(30 권장) | 가변 fps 는 음성 싱크가 어긋난다 |
| 오디오 | 베이스의 음원 | 촬영본 오디오는 버리는 것이 기본안(§11) |
| faststart | 불필요 | 재생용이 아니라 입력이다 |

---

## 6. 실패 코드 (제안)

API 명세 §16 #4 가 "코드표 추후 결정" 으로 열어둔 자리다. **AI 팀이 실제로 구분할 수 있는 것만** 남기고 확정한다.

| `fail_code` | 언제 | 앱 안내(제안) |
|---|---|---|
| `FACE_NOT_DETECTED` | 촬영본에서 얼굴을 찾지 못함 | 재촬영 |
| `PERSON_NOT_DETECTED` | 인물(전신)을 찾지 못함 | 재촬영 |
| `MULTIPLE_PERSONS` | 촬영본에 사람이 둘 이상 | 재촬영 |
| `RAW_TOO_SHORT` | 촬영본이 베이스보다 짧음 | 재촬영 |
| `RAW_UNREADABLE` | 촬영본 디코딩 실패(손상·미지원 코덱) | 재촬영 |
| `INPUT_NOT_FOUND` | S3 에 입력 키가 없음 | 다시 시도 (백엔드가 원인 확인) |
| `UPLOAD_FAILED` | 결과 PUT 실패 | 다시 시도 |
| `GPU_ERROR` | OOM 등 GPU 오류 | 다시 시도 |
| `TIMEOUT` | Job 자체 시간 제한 초과 | 다시 시도 |
| `INTERNAL_ERROR` | 그 외 전부 | 다시 시도 |

- "재촬영" 계열은 다시 돌려도 같은 결과다. 앱이 재시도 버튼 대신 재촬영을 안내한다
- 백엔드가 직접 내는 코드도 있다(`CHALLENGE_DELETED` · `PACKAGING_FAILED` · `STUCK`). Job 은 쓰지 않는다

---

## 7. 사전 작업 — 챌린지 편집본·bbox

챌린지 하나를 등록하려면 **AI 팀 산출물이 먼저** 있어야 한다(어드민 명세 §1.4·§3.7, 회의 확정). 어드민은 키와 좌표를 옮겨 적기만 한다.

슬롯 = 사용자가 자기 얼굴로 바꿔 넣을 수 있는 인물 자리. 메인 아티스트는 슬롯이 아니다.

슬롯 n 개짜리 챌린지 하나에 필요한 것:

| 산출물 | 개수 | 규격 |
|---|---|---|
| 편집본 MP4 | **2ⁿ − 1** — 슬롯 조합마다 하나, 그 조합의 인물만 지운 영상 | 원본과 같은 해상도·세로, 길이 = 원본 ±500ms |
| 첫 장면 JPG | 편집본마다 하나(촬영 가이드 배경) | 편집본 첫 프레임, 원본과 같은 픽셀 크기 |
| 선택 화면 JPG | 편집본(조합)마다 하나 — 그 조합을 골랐을 때 썸네일 자리에 보이는 이미지 | 썸네일과 같은 비율. presign kind `EDITED_PREVIEW` |
| 앵커 | 슬롯마다 하나 — 선택 화면에서 프로필 원이 앉는 자리 | `x`·`y`·`r` **썸네일 대비 0~1 비율**(`x`·`r` 가로, `y` 세로). 기본 `r = 40/402 ≈ 0.0995`. `0 ≤ x,y ≤ 1`, `0 < r ≤ 0.5` |

**bbox 는 없다** (2026-09-18, S15P21D104-618 — 원 자리로 쓸 수 없어 삭제). 합성에 지운 인물의 정확한 픽셀 위치가
필요하면 **AI 팀이 편집본을 만들 때 이미 아는 값**이므로 편집본 옆에 사이드카로 둔다 — 예: `edited/{폴더}/{uuid}.mask.json`
또는 마스크 영상. 서버는 관여하지 않고, Job 이 `base_video_key` 에서 규약으로 찾는다.

- 슬롯 순서는 **화면 좌 → 우**. 서버가 순서대로 1·2·4… 비트를 붙인다. 편집본은 "지운 슬롯 조합" 으로 구분한다 — 슬롯 2개면 {1}·{2}·{1,2} 세 개
- 업로드 위치: `edited/{폴더}/{uuid}.mp4` · `.jpg`. 폴더 이름은 자유(곡명 슬러그 등). 서버는 키의 **존재만** 확인한다
- 업로드 계정: IAM `motif-ai-editor`(PUT·GET `edited/*`) — 없으면 어드민 화면 presign 이 대체 경로다
- 어드민에게 넘길 것: 슬롯별 `artistSeq`·앵커(`x`·`y`·`r`), 조합별 `videoKey`·`initialSceneKey`·`previewImageKey`·길이(ms). 표 하나로. 어드민 화면에서 파일 업로드도 된다(직접 S3 업로드와 병행)
- 운영 권고: 슬롯 ≤ 3(편집본 7개). 하드 상한 4(15개)

이 산출물이 없으면 그 챌린지는 **슬롯 0개(솔로)** 로만 등록된다 — 원본 위 합성만 가능하다.

---

## 8. 로그·운영

- 모든 로그 줄에 `[request_id] job_uuid=…` 를 넣는다. 백엔드 로그(`[%X{requestId}]`)와 같은 ID 로 grep 된다
- 잡 시작·다운로드 완료·합성 완료·업로드 완료·콜백 응답 — 다섯 지점만 INFO. 그 사이는 DEBUG
- Cloud Logging 에서 실행 이름(`CLOUD_RUN_EXECUTION`)으로 묶인다. 콜백의 `worker_id` 가 그 값이라 백엔드 로그에서 바로 찾아갈 수 있다
- 실행 목록(`gcloud run jobs executions list`)이 곧 "지금 도는 잡" 이다. 백엔드 쪽 상태는 DB `composite_jobs.status` 다
- 접수 서비스 로그에 401·503 이 찍히면 각각 토큰·쿼터 문제다

---

## 9. 로컬에서 먼저 돌려보기

GCP 없이 컨테이너만으로 시험한다. 콜백 대상은 로컬 core-api(격리 모드)다 — 토큰은 로컬 더미 `motif-local-worker-token`.

```bash
# 백엔드 (별도 터미널)
cd backend/local-infra && docker compose up -d --wait
cd backend && ./gradlew :app-core-api:bootRun          # http://localhost:8081

# 합성 Job 컨테이너 — 접수 서비스를 거치지 않고 env 를 직접 넣는다
docker run --rm \
  -e MOTIF_JOB="$(cat sample-job.json)" \
  -e MOTIF_REQUEST_ID=local-1 \
  -e MOTIF_CALLBACK_BASE=http://host.docker.internal:8081 \
  -e MOTIF_WORKER_TOKEN=motif-local-worker-token \
  -e AWS_ACCESS_KEY_ID=… -e AWS_SECRET_ACCESS_KEY=… -e AWS_REGION=ap-northeast-2 -e S3_BUCKET=motif-media \
  motif/compose:dev
```

- `sample-job.json` 은 §3.2 그대로. `job_uuid` 는 로컬 core-api 에서 13.1·13.2 로 만든 실제 값이어야 콜백이 404 가 아니다. 그 전 단계 시험이면 404 가 정상이다 — 콜백 요청이 **나가는지**만 본다
- S3 는 실제 버킷을 쓰되 테스트 키는 `results/test/…` 같은 별도 프리픽스로. 또는 로컬 파일 모드를 하나 두고 S3 를 건너뛴다
- 샘플 영상: `backend/local-infra/poc/renditions/720p/*.mp4`
- 접수 서비스는 GCP 프로젝트에 붙여야 시험이 된다. dev 프로젝트나 dev Job 이름으로 먼저 돌린다

시험 순서:

1. 없는 `raw_video_key` 로 실행 → `/failed`(`INPUT_NOT_FOUND`) 콜백이 나가는지
2. 실제 키로 실행 → S3 `results/test/…` 에 MP4 → `/done` 콜백
3. 콜백 대상 서버를 끄고 실행 → 3회 재시도 후 exit 0 인지
4. `ffprobe -show_streams` 로 결과 MP4 가 §5.3 인지 — 특히 `rotate` 태그가 없는지

---

## 10. 연동 전 체크리스트

- [ ] 접수 URL 을 백엔드에 전달. `--task-timeout` 값과 콜드스타트 실측(첫 프레임까지 N 초) 도 함께
- [ ] `X-Motif-Worker-Token` 값 공유(팀 비밀 저장소) · Secret Manager 에 등록
- [ ] IAM `motif-gpu-worker` 액세스 키 수령 · Secret Manager 에 등록
- [ ] `--max-retries 0` 확인
- [ ] §9 시험 1~4 통과
- [ ] `X-Request-Id` 가 콜백 헤더에 그대로 실리는지
- [ ] 편집본·bbox 산출 절차(§7) 시연용 챌린지 1개 분량 준비

---

## 11. 확정이 필요한 것 (AI 팀 ↔ 백엔드)

| # | 질문 | 기본안 |
|---|---|---|
| 1 | `correction_enabled` 가 켜지면 무엇을 하나 (피부·조명 보정? 타이밍 보정?) | AI 팀 정의. 서버는 값만 전달 |
| 2 | 슬롯을 **둘 이상** 고른 경우 촬영본 한 개를 어떻게 넣나 | AI 팀 정의. 못 하면 앱에서 다중 선택을 막는다 |
| 2-1 | 합성 위치 — `anchor` 힌트로 충분한가, 편집본 옆 사이드카(마스크)가 필요한가 | 사이드카가 필요하면 AI 팀 규약으로 둔다. 서버 변경 없음 |
| 3 | MOTIF 합성의 입력·출력 형태 — 에셋(가로) + 촬영본(세로) → 결과 방향은? | 결과 방향은 베이스(에셋)를 따른다 |
| 4 | 결과 오디오 — 베이스 음원만? 촬영본 음성 합치나? | 베이스 음원만 |
| 5 | **잡 1개 벽시계 시간** — 콜드스타트 + 합성. `--task-timeout` 값 | 30분. 실측 후 조정 |
| 6 | GPU 리전·쿼터 — L4 몇 개까지 동시에 도나 | 동시 1~2개로 시작 |
| 7 | 실패 코드표(§6) 중 실제로 구분 가능한 것 | 표 그대로. 구분 못 하는 것은 빼서 확정 |
| 8 | 접수 서비스 소유 — AI 팀이 Cloud Run Service 로 두는가, 백엔드가 GCP API 를 직접 부르는가 | **AI 팀 서비스.** 백엔드가 직접 부르면 서비스 계정 키와 OAuth 코드를 백엔드가 들고 가야 한다 |
| 9 | 편집본 업로드 방식 — IAM 직접 vs 어드민 presign | IAM 직접 |
| 10 | GCP 비용 부담·프로젝트 소유 | AI 팀 프로젝트 |

---

## 부록 A. 합성 Job 골격 (Python)

env 를 읽고, 일하고, 콜백 한 번 하고, 0 으로 끝난다. 큐 코드가 없어 v0.1 보다 짧다.

```python
import json, logging, os, shutil, sys, time
from pathlib import Path

import boto3, requests

log = logging.getLogger("compose")
s3 = boto3.client("s3", region_name=os.environ.get("AWS_REGION", "ap-northeast-2"))
BUCKET = os.environ.get("S3_BUCKET", "motif-media")
BASE = os.environ["MOTIF_CALLBACK_BASE"].rstrip("/")
HEADERS = {"X-Motif-Worker-Token": os.environ["MOTIF_WORKER_TOKEN"],
           "X-Request-Id": os.environ.get("MOTIF_REQUEST_ID", ""),
           "Content-Type": "application/json"}
WORKER_ID = os.environ.get("CLOUD_RUN_EXECUTION", "local")


class JobFailed(Exception):
    def __init__(self, code, msg=""):
        super().__init__(msg)
        self.code = code


def synthesize(job, work):
    """다운로드 → 합성 → 업로드. 결과 키를 돌려준다. 실패는 JobFailed 로 던진다."""
    raw, base, out = work / "raw.mp4", work / "base.mp4", work / "out.mp4"
    try:
        s3.download_file(BUCKET, job["raw_video_key"], str(raw))
        s3.download_file(BUCKET, job["base_video_key"], str(base))
    except s3.exceptions.ClientError as e:
        raise JobFailed("INPUT_NOT_FOUND", str(e))

    run_model(raw, base, out, job)          # ← AI 팀 몫. 얼굴 미검출 등은 JobFailed("FACE_NOT_DETECTED")

    try:
        s3.upload_file(str(out), BUCKET, job["output_video_key"], ExtraArgs={"ContentType": "video/mp4"})
    except Exception as e:
        raise JobFailed("UPLOAD_FAILED", str(e))
    return job["output_video_key"]


def callback(job_uuid, kind, body):
    """5xx·연결 실패만 3회 재시도. 4xx 는 우리 설정 문제라 재시도하지 않는다."""
    url = f"{BASE}/api/worker/v1/composite-jobs/{job_uuid}/{kind}"
    for i, wait in enumerate((1, 4, 16, None)):
        try:
            r = requests.post(url, headers=HEADERS, data=json.dumps(body), timeout=15)
            if r.status_code < 500:
                log.info("콜백 %s → %s", kind, r.status_code)
                return
            log.warning("콜백 %s 5xx: %s", kind, r.status_code)
        except requests.RequestException as e:
            log.warning("콜백 %s 실패: %r", kind, e)
        if wait is None:
            log.error("콜백 포기 — 백엔드 스윕이 닫는다")
            return
        time.sleep(wait)


def main():
    job = json.loads(os.environ["MOTIF_JOB"])
    started = time.monotonic()
    log.info("[%s] 시작 job_uuid=%s type=%s attempt=%s", HEADERS["X-Request-Id"], job["job_uuid"], job["job_type"], job.get("attempt"))
    work = Path(os.environ.get("WORK_DIR", "/tmp/motif")) / job["job_uuid"]
    work.mkdir(parents=True, exist_ok=True)
    try:
        kind, body = "done", {"result_video_key": synthesize(job, work)}
    except JobFailed as e:
        kind, body = "failed", {"fail_code": e.code, "fail_message": str(e)[:500]}
    except Exception as e:                      # 무엇이든 실패로 보고한다 — 조용히 죽지 않는다
        kind, body = "failed", {"fail_code": "INTERNAL_ERROR", "fail_message": repr(e)[:500]}
    finally:
        shutil.rmtree(work, ignore_errors=True)

    body.update(processing_ms=int((time.monotonic() - started) * 1000),
                attempt=job.get("attempt"), worker_id=WORKER_ID)
    callback(job["job_uuid"], kind, body)
    sys.exit(0)                                 # 실패 보고였어도 0 — Cloud Run 이 실행을 실패로 세지 않게


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
    main()
```

## 부록 B. 접수 서비스 골격 (Python · Cloud Run Service)

토큰을 확인하고 Job 을 띄운다. 기다리지 않는다.

```python
import json, os
from http.server import BaseHTTPRequestHandler, HTTPServer

from google.cloud import run_v2

TOKEN = os.environ["MOTIF_WORKER_TOKEN"]
JOB = os.environ["MOTIF_JOB_NAME"]              # projects/{p}/locations/{l}/jobs/motif-compose
client = run_v2.JobsClient()


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.headers.get("X-Motif-Worker-Token") != TOKEN:
            return self.reply(401, {"error": "bad token"})
        payload = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode()
        request_id = self.headers.get("X-Request-Id", "")
        try:
            op = client.run_job(request=run_v2.RunJobRequest(
                name=JOB,
                overrides=run_v2.RunJobRequest.Overrides(container_overrides=[
                    run_v2.RunJobRequest.Overrides.ContainerOverride(env=[
                        run_v2.EnvVar(name="MOTIF_JOB", value=payload),
                        run_v2.EnvVar(name="MOTIF_REQUEST_ID", value=request_id),
                    ])])))
            self.reply(202, {"execution": op.metadata.name})
        except Exception as e:                  # 쿼터·권한 — 백엔드가 나중에 다시 부른다
            self.reply(503, {"error": repr(e)[:300]})

    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    HTTPServer(("", int(os.environ.get("PORT", 8080))), Handler).serve_forever()
```

`op.metadata.name` 은 실행 이름이다. `client.run_job` 은 실행 **시작**만 기다리므로 응답이 1~3초 안에 온다.
