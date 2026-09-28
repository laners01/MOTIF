# MOTIF ↔ AI 서버 연동 명세 — v0.3

**2026-09-19 · 대상: AI 팀**

**백엔드는 다 만들어졌고 동작을 확인했다.** 2026-09-19 운영 서버에서 AI 를 제외한 전
구간을 손으로 돌렸다 — 콜백 수신 → HLS 가공 → 잡 완료 → 푸시 → 게시.
**접수 URL 하나만 주면 연동된다.**

이 문서는 **AI 팀이 구현할 것만** 적는다. 백엔드 내부 동작은
[`detail/합성-파이프라인-작업명세.md`](detail/합성-파이프라인-작업명세.md) 에 있다.

---

## 1. 전체 그림

```
앱 ─(1) 촬영본 업로드─▶ S3  raw/{job_uuid}.mp4
앱 ─(2) 합성 요청────▶ 백엔드 ─(3) POST {접수 URL} ─▶ 접수 서비스 (AI, CPU)
                                                        │ (4) Job 실행
                                                        ▼
                                              ┌── 합성 Job (AI, GPU) ──┐
                                              │ (5) S3 에서 입력 2개    │
                                              │ (6) 합성 → MP4 1개      │
                                              │ (7) S3 에 결과 PUT      │
백엔드 ◀─(8) POST /done | /failed ────────────┴─────────────────────────┘
백엔드 ─(9) HLS 가공 → 완료 확정 → 푸시 ─▶ 앱
```

AI 팀이 만드는 것은 **둘**이다.

| 만들 것 | 종류 | 하는 일 |
|---|---|---|
| **접수 서비스** | Cloud Run Service (CPU) | 백엔드 POST 를 받아 토큰 확인 → 합성 Job 실행 → 202 응답. 기다리지 않는다 |
| **합성 Job** | Cloud Run Job (GPU) | env 의 JSON 을 읽어 S3 입력 2개 받기 → 합성 → S3 PUT → 콜백 1회 → exit 0 |

**AI 가 하지 않는 것**: DB 접속 · 큐 접속 · HLS 인코딩 · 썸네일 · 푸시 · 잡 상태 변경 · 재시도 판단. 전부 백엔드 몫이다.

---

## 2. 주고받을 것

### AI 팀 → 백엔드

| # | 넘길 것 | 없으면 |
|---|---|---|
| 1 | **접수 서비스 URL** | **연동이 시작되지 않는다. 이것만 차단 요인이다** |
| 2 | `--task-timeout` 값 | 백엔드가 기본 30분으로 둔다. 실제보다 짧으면 정상 잡을 실패 처리한다 |
| 3 | 콜드스타트 실측(첫 프레임까지 N초) | 앱 "약 N 분" 문구를 못 정한다 |
| 4 | `--max-retries 0` 적용 확인 | 기본값 3이면 OOM 한 번에 같은 잡이 네 번 돈다 |
| 5 | 실패 코드 확정(§6) | 앱이 모르는 코드를 받는다 |

**1번만 오면 시작된다.** 나머지는 병행해도 된다.

### 백엔드 → AI 팀

| 것 | 값 |
|---|---|
| 콜백 주소 | `https://j15d104.p.ssafy.io` |
| 인증 토큰 | `X-Motif-Worker-Token` — 64자. **비밀 저장소로 전달**(문서·메신저 금지) |
| S3 버킷 · 리전 | `motif-media` · `ap-northeast-2` |
| S3 자격증명 | IAM `motif-gpu-worker` 액세스 키 — 비밀 저장소로 전달 |

---

## 3. 공통 규약

- 본문은 **JSON · snake_case**. 모르는 필드는 무시한다
- 인증: 헤더 `X-Motif-Worker-Token` — **양방향 같은 값**. 없거나 다르면 401
- 추적: 헤더 `X-Request-Id` — 백엔드가 접수에 실어 보낸다. **Job 은 같은 값을 콜백 헤더에 그대로 싣고 모든 로그 줄에 찍는다**
- 백엔드 응답은 공통 봉투(`result`·`code`·`message`·`data`·`timestamp`)다. **Job 은 HTTP 상태 코드만 보면 된다**

---

## 4. 접수 — 백엔드 → AI

```http
POST {접수 URL}
X-Motif-Worker-Token: …
X-Request-Id: 7c1e…
Content-Type: application/json
```

**CHALLENGE** — 베이스는 슬롯 인물이 지워진 편집본(무교체면 원본)

```json
{
  "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
  "job_type": "CHALLENGE",
  "attempt": 1,
  "raw_video_key": "raw/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
  "base_video_key": "private/edited/11/7d2f…e1.mp4",
  "output_video_key": "results/challenge/b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c.mp4",
  "correction_enabled": true,
  "participant_mask": 2,
  "slots": [
    { "participant_mask": 2, "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 } }
  ],
  "base_duration_ms": 15000
}
```

**MOTIF** — 베이스는 구매한 에셋 영상

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
| `raw_video_key` | string | 사용자 촬영본. MP4, 세로. 회전 메타데이터가 붙어 있을 수 있다 |
| `base_video_key` | string | 합성 베이스. **항상 MP4**. 챌린지 편집본은 **2026-09-21 부터 `private/edited/…`** — 앱이 이 영상을 직접 받게 되면서 CloudFront 서명이 필요해져 프리픽스를 옮겼다. 워커는 S3 로 직접 받으므로 서명과 무관하지만 **읽기 권한 프리픽스를 함께 옮겨야 한다**(옛 `edited/*` 로 한정돼 있으면 403) |
| `output_video_key` | string | 결과를 올릴 키. **이 키에 그대로 PUT.** 재시도면 덮어쓴다 |
| `correction_enabled` | bool | 사용자가 고른 보정 여부. MOTIF 는 항상 `false`. 뜻은 AI 팀 정의(§9 #1) |
| `participant_mask` | int | 교체 슬롯 비트. `0` = 무교체(`slots` 빈 배열). **2026-09-21 이후 언제나 단일 비트**(1·2·4·8)다 — 편집본이 슬롯 1개당 1개로 정리돼 2명 이상을 한 번에 교체하는 조합이 없어졌다. 필드·타입은 그대로이므로 파싱은 바뀌지 않는다 |
| `slots[]` | array | 교체 대상만 — 위 변경으로 **0개 또는 1개**다(배열 형태는 유지). `anchor` 는 **썸네일 대비 0~1 비율**(`x`·`r` 가로, `y` 세로)로 베이스에 곱하면 대략의 인물 자리다. **위치 힌트**이고 픽셀 bbox 는 없다. 옛 챌린지는 `null` 일 수 있다 |
| `base_duration_ms` | int | 베이스 길이(ms). 결과 길이의 기준 |

**접수 서비스가 줄 응답**

| HTTP | 본문 | 백엔드 동작 |
|---|---|---|
| **202** | `{ "execution": "projects/…/executions/motif-compose-abc12" }` | 잡을 처리 중으로 표시 |
| 401 | — | 설정 오류. 사람이 본다 |
| 503 | `{ "error": "…" }` | 2분 뒤 다시 접수. 3회 실패하면 잡을 닫는다 |

**Job 을 띄우기만 하고 기다리지 않는다.** 응답은 1~3초 안에.

---

## 5. 콜백 성공 — AI → 백엔드

```http
POST https://j15d104.p.ssafy.io/api/worker/v1/composite-jobs/{job_uuid}/done
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

| 필드 | 필수 | 뜻 |
|---|:---:|---|
| `result_video_key` | Y | 받은 `output_video_key` **그대로**. 백엔드가 다시 계산해 대조한다 |
| `processing_ms` | N | 컨테이너 시작부터 업로드 끝까지 |
| `attempt` | N | 받은 값 그대로 |
| `worker_id` | N | `CLOUD_RUN_EXECUTION`. 없어도 처리된다 |

**S3 PUT 이 끝난 뒤에** 부른다.

---

## 6. 콜백 실패 — AI → 백엔드

```http
POST https://j15d104.p.ssafy.io/api/worker/v1/composite-jobs/{job_uuid}/failed
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

| 필드 | 필수 | 뜻 |
|---|:---:|---|
| `fail_code` | Y | 아래 표의 값. **30자 이내 대문자·밑줄** |
| `fail_message` | N | 로그용 자유 문장(500자 이내). 사용자에게 보이지 않는다 |

**실패 코드** — AI 팀이 실제로 구분 가능한 것만 남기고 확정한다(§9 #3)

| `fail_code` | 언제 | 앱 안내 |
|---|---|---|
| `FACE_NOT_DETECTED` | 촬영본에서 얼굴을 찾지 못함 | 재촬영 |
| `PERSON_NOT_DETECTED` | 인물(전신)을 찾지 못함 | 재촬영 |
| `MULTIPLE_PERSONS` | 촬영본에 사람이 둘 이상 | 재촬영 |
| `RAW_TOO_SHORT` | 촬영본이 베이스보다 짧음 | 재촬영 |
| `RAW_UNREADABLE` | 촬영본 디코딩 실패 | 재촬영 |
| `INPUT_NOT_FOUND` | S3 에 입력 키가 없음 | 다시 시도 |
| `UPLOAD_FAILED` | 결과 PUT 실패 | 다시 시도 |
| `GPU_ERROR` | OOM 등 GPU 오류 | 다시 시도 |
| `TIMEOUT` | Job 시간 제한 초과 | 다시 시도 |
| `INTERNAL_ERROR` | 그 외 전부 | 다시 시도 |

백엔드가 직접 내는 코드(`CHALLENGE_DELETED`·`STUCK`)는 Job 이 쓰지 않는다.

---

## 7. 콜백에 대한 백엔드 응답

| HTTP | 뜻 | Job 동작 |
|---|---|---|
| **200** | 받았다. 이미 처리된 잡이어도 200(멱등) | 끝. exit 0 |
| **400** | **`result_video_key` 가 백엔드 계산값과 다르다** | 재시도하지 않는다. 구현 오류다 |
| 401 | 토큰 불일치 | 재시도하지 않는다 |
| 404 | 모르는 `job_uuid` | 재시도하지 않는다 |
| 5xx · 연결 실패 · 타임아웃 | 백엔드 장애 | **3회 재시도**(1s → 4s → 16s). 그래도 실패면 exit 0 |

**4xx 는 재시도하지 않는다.** 설정이나 구현이 틀린 것이라 다시 보내도 같은 답이 온다.

---

## 8. S3 규약

**읽는 키** — 메시지의 키를 그대로 GET 한다. 조립하거나 추측하지 않는다

| 프리픽스 | 내용 |
|---|---|
| `raw/{job_uuid}.mp4` | 사용자 촬영본 |
| `edited/…/{uuid}.mp4` | 슬롯 인물이 지워진 편집본 |
| `challenges/{uuid}.mp4` | 챌린지 원본(무교체 베이스) |
| `assets/{uuid}.mp4` | 모티프 에셋 원본 |

`raw/` 는 7일 뒤 삭제될 수 있다 — 없으면 `INPUT_NOT_FOUND` 로 보고한다.

**쓰는 키** — `output_video_key` 그대로. **여기 한 곳에만** 쓴다

| 프리픽스 | 내용 |
|---|---|
| `results/challenge/{job_uuid}.mp4` | CHALLENGE 결과 |
| `results/motif/{job_uuid}.mp4` | MOTIF 결과 |

`private/…` 는 백엔드가 만드는 재생용 산출물이다. 건드리지 않는다.
IAM 권한: GET `raw/*` `edited/*` `challenges/*` `assets/*` · PUT `results/*` · ListBucket.

**결과 MP4 사양**

| 항목 | 값 | 이유 |
|---|---|---|
| 컨테이너·코덱 | MP4 · H.264(yuv420p) + AAC | 백엔드가 다시 인코딩한다. 이것만 지키면 나머지는 자유 |
| 해상도·방향 | **베이스와 같게** | HLS 사다리가 원본 해상도 기준이다. 업스케일하지 않는다 |
| 길이 | 베이스 ±500ms | 앱이 길이 기준으로 UI 를 그린다 |
| 회전 | **픽셀이 바로 서 있게** — rotate 메타데이터로 세우지 않는다 | 태그로만 세로면 가공에서 눕는다 |
| fps | 고정(30 권장) | 가변 fps 는 음성 싱크가 어긋난다 |
| 오디오 | 베이스 음원 | 촬영본 오디오는 버리는 것이 기본안(§9 #2) |

---

## 9. Cloud Run 설정에서 꼭 맞출 것

| 옵션 | 값 | 왜 |
|---|---|---|
| `--max-retries` | **0** | 컨테이너가 죽으면 Cloud Run 이 같은 잡을 다시 돈다. 콜백이 두 번 가거나 GPU 를 두 번 쓴다. 재시도는 백엔드가 정한다 |
| `--task-timeout` | 처리 시간 + 여유 | 넘으면 **콜백 없이** 죽는다. 백엔드가 이 값 + 5분 뒤 실패로 닫는다 — **값을 알려준다** |
| `--tasks` | 1 | 잡 1개 = 실행 1개 |

Job 에 넣을 환경변수:

```
MOTIF_CALLBACK_BASE=https://j15d104.p.ssafy.io
MOTIF_WORKER_TOKEN=…            # Secret Manager. 백엔드와 같은 값
AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY   # Secret Manager. IAM motif-gpu-worker
AWS_REGION=ap-northeast-2
S3_BUCKET=motif-media
```

실행마다 접수 서비스가 넣는 값: `MOTIF_JOB`(§4 의 JSON 문자열) · `MOTIF_REQUEST_ID`(받은 `X-Request-Id`).

---

## 10. 틀리기 쉬운 것

2026-09-19 백엔드 실측에서 확인된 것만.

| 항목 | 내용 |
|---|---|
| **`result_video_key`** | 받은 `output_video_key` 를 **글자 그대로**. 백엔드가 `results/{challenge\|motif}/{job_uuid}.mp4` 로 다시 계산해 대조하고 다르면 **400** |
| **`base_video_key`** | 항상 MP4 다. 재생목록(`.m3u8`)이 올 일은 없다 |
| **콜백 시점** | S3 PUT 이 **끝난 뒤**. 먼저 부르면 백엔드가 빈 키를 읽는다 |
| **접수 202 유실** | 백엔드가 202 를 못 받아도 Job 은 그대로 진행한다. 콜백은 정상 처리된다 |
| **중복 `/done`** | 200 이다. 재시도해도 안전하다 |
| **같은 잡 두 번 실행** | 있을 수 있다(백엔드 재접수). 그냥 다시 처리한다. 결과 키를 덮어써도 된다 |
| **표기법** | 전부 snake_case. camelCase 로 보내면 필드가 전부 비어 보인다 |
| **접수 지연** | 백엔드가 앱 요청 후 **2~3분** 뒤에 POST 한다(폴링 구조). 즉시 오지 않는 것은 정상이다 |
| **exit code** | 실패를 보고했어도 **0 으로 끝낸다.** 0 이 아니면 모니터링만 더러워진다 |

---

## 11. 막혔을 때

| 증상 | 원인 |
|---|---|
| 접수가 아예 안 온다 | 백엔드에 접수 URL 미반영 |
| 접수 요청이 401 | 토큰 불일치 — 양쪽 값 대조 |
| 콜백이 401 | 같은 토큰 문제 |
| 콜백이 404 | `job_uuid` 오타, 또는 그 잡이 백엔드에 없다 |
| 콜백이 400 | `result_video_key` 불일치(§10) |
| 콜백 200 인데 앱에 결과가 없다 | 백엔드 구간(HLS 가공·푸시). **AI 는 할 일을 마쳤다** |

로그에 `[request_id] job_uuid=…` 를 넣으면 백엔드 로그와 한 ID 로 맞춰볼 수 있다.

---

## 12. 확정이 필요한 것

| # | 질문 | 기본안 |
|---|---|---|
| 1 | `correction_enabled` 가 켜지면 무엇을 하나 | AI 팀 정의. 백엔드는 값만 전달 |
| 2 | 결과 오디오 — 베이스 음원만? 촬영본 음성도? | 베이스 음원만 |
| 3 | 실패 코드(§6) 중 실제로 구분 가능한 것 | 표 그대로. 구분 못 하는 것은 뺀다 |
| 4 | 슬롯을 둘 이상 고른 경우 촬영본 1개를 어떻게 넣나 | AI 팀 정의. 못 하면 앱에서 단일 선택으로 막는다 |
| 5 | 합성 위치 — `anchor` 힌트로 충분한가, 편집본 옆 사이드카(마스크)가 필요한가 | 사이드카가 필요하면 AI 팀 규약으로 둔다. 백엔드 변경 없음 |
| 6 | MOTIF 결과 방향 — 에셋(가로) + 촬영본(세로) | 베이스(에셋)를 따른다 |
| 7 | 잡 1개 벽시계 시간 · `--task-timeout` | 30분. 실측 후 조정 |

> 챌린지 편집본·앵커 산출(**슬롯 n개당 n개** — 2026-09-21 에 조합이 폐기됐다)은 합성 연동과 별개 트랙이다.
> 상세는 [`MOTIF_AI서버_명세서_v0_2.md`](MOTIF_AI서버_명세서_v0_2.md) §7 을 본다.
