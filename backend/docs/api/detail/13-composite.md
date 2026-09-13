# §13 합성 파이프라인 (COMPOSITE) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §13 · DB 명세서 v0.15 §29.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 13.1 | POST | `/api/v1/composite-jobs` | 필요 | 잡 생성 + 업로드 URL 발급 |
| 13.2 | POST | `/api/v1/composite-jobs/{jobUuid}/request` | 필요 | 업로드 완료 → 합성 요청 |
| 13.3 | GET | `/api/v1/composite-jobs/{jobUuid}` | 필요 | 잡 상태·결과 조회 |
| 13.4 | POST | `/api/v1/composite-jobs/{jobUuid}/retry` | 필요 | 실패 잡 재시도 |

## 챌린지와 모티프가 같은 파이프라인을 쓴다

| `jobType` | 무엇을 합성하나 | 결과의 행선지 |
|---|---|---|
| `CHALLENGE` | 챌린지 원본 위에 내 촬영본 | 프리뷰 확인 후 **§8.8 게시** |
| `MOTIF` | 구매/패스로 연 에셋 + 내 촬영본 | **서버가 `motif_results` 자동 생성** → 보관함(§11.6) |

**잡 식별자만 UUID 다** — FCM 푸시 페이로드에 실리는 외부 잔존 식별자이기 때문이다(§1.1).

## 전체 흐름

```
① POST /composite-jobs            잡 생성(UPLOADED) + presigned PUT URL 발급
② PUT {uploadUrl}                 클라이언트가 S3 로 원본 직행 업로드 (동기·로딩 UI)
③ POST /{jobUuid}/request         S3 원본 확인 → QUEUED + 큐 발행
④ (워커)                          PROCESSING → DONE | FAILED, 완료·실패 시 FCM 발송
⑤ GET /{jobUuid}                  FCM 수신 후 결과 화면 진입 시 1회 조회
```

**폴링하지 않는다**(§1.6). 상태 확인은 ④의 FCM 을 받고 ⑤를 **한 번** 호출하는 것뿐이다.

---

## 13.1 POST /api/v1/composite-jobs — 잡 생성

### 요청 — MOTIF

```json
{ "jobType": "MOTIF", "assetSeq": 21 }
```

### 요청 — CHALLENGE

```json
{ "jobType": "CHALLENGE", "challengeSeq": 11, "participantMask": 3 }
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `jobType` | Y | `CHALLENGE` \| `MOTIF` |
| `challengeSeq` | CHALLENGE | 대상 챌린지 |
| `assetSeq` | MOTIF | 대상 에셋 |
| `participantMask` | N (CHALLENGE) | 교체할 슬롯 조합. **`0` 또는 생략 = 무교체**(원본 위 합성) |

### `participantMask` — 슬롯 조합

§7.2 의 `participants[].participantMask` 는 슬롯마다 **단일 비트**(1, 2, 4 …)다. 사용자가 여러 슬롯을 고르면 **비트 OR 로 합산**해서 보낸다 — 윈터(1) + 닝닝(2) = `3`.

| 값 | 서버 동작 |
|---|---|
| `0` 또는 생략 | 무교체 — `edited_challenge_seq = NULL` 로 원본 위에 합성 |
| `> 0` | `(challengeSeq, participantMask)` 로 **편집본을 조회**해 잡에 `edited_challenge_seq` 기록. **없으면 404** |

### 검증

- **CHALLENGE** — 챌린지가 비삭제인지 + 위 편집본 매칭
- **MOTIF** — **이용 권한 필수**(개별 구매 ACTIVE ∪ 유효 PASS). 선결제 원칙이라 권한 없이는 촬영을 시작할 수 없다. `participantMask` 는 쓰지 않는다

### 성공 응답 (201)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "uploadUrl": "https://motif-media.s3.ap-northeast-2.amazonaws.com/raw/3fa85f64.mp4?X-Amz-Algorithm=...&X-Amz-Signature=...",
    "expiresIn": 600
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

잡은 `UPLOADED` 상태로 만들어지고, 클라이언트는 `uploadUrl` 로 **원본을 S3 에 직행 업로드**한다(동기·로딩 UI). 영상 바이트는 API 서버를 통과하지 않는다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` / `ASSET_NOT_FOUND` | 대상 없음 |
| 404 | `EDITED_CHALLENGE_NOT_FOUND` | 선택한 슬롯 조합의 편집본이 없음(CHALLENGE, `participantMask > 0`) |
| 403 | `ASSET_ACCESS_DENIED` | MOTIF 이용 권한 없음 — 클라이언트는 **구매 화면으로 유도** |

```json
{
  "result": "FAIL",
  "code": "ASSET_ACCESS_DENIED",
  "message": "구매 후 이용할 수 있습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`EDITED_CHALLENGE_NOT_FOUND` 는 **운영 전제가 깨졌을 때만** 나온다 — 어드민이 슬롯 전 조합의 편집본을 등록하기로 했기 때문이다(§7.2). 이 오류를 사용자가 보면 촬영 직전에 막히는 것이므로, 어드민 등록 시 완결성 검증이 필요하다.

---

## 13.2 POST /api/v1/composite-jobs/{jobUuid}/request — 합성 요청

업로드를 마친 뒤 호출한다. 여기서 잡이 큐에 들어간다.

### 요청 — CHALLENGE

```json
{ "correctionEnabled": true }
```

### 요청 — MOTIF

```json
{}
```

**MOTIF 는 `correctionEnabled` 를 받지 않는다.** 보정을 쓰지 않으며 서버 내부 값은 `FALSE` 고정이다.

`correctionEnabled` 는 촬영 후 사용자가 보정 여부를 고르는 값이다(CHALLENGE 전용).

### 서버 처리

1. **S3 원본 존재 확인(HEAD)** — 업로드가 실제로 끝났는지 본다
2. `UPLOADED → QUEUED` 전이
3. `job.composite` 큐 발행 — **우선순위: 모티프 5 / 챌린지 0**(유료 경로를 먼저 처리)

이후 `PROCESSING → DONE | FAILED` 는 워커와 이벤트가 처리하며, 완료·실패 시 FCM 을 발송한다.

### 성공 응답 (202)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "status": "QUEUED" },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**202 Accepted** — 접수했다는 뜻이며 합성은 아직 끝나지 않았다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` | 잡 없음·타인 잡 |
| 409 | `RAW_NOT_UPLOADED` | **S3 원본 부재** — 업로드 미완료 또는 URL 만료 |
| 409 | `JOB_NOT_REQUESTABLE` | 상태가 `UPLOADED` 가 아님(중복 요청 포함) |

---

## 13.3 GET /api/v1/composite-jobs/{jobUuid} — 잡 상태·결과 조회

FCM 을 받고 결과 화면에 진입할 때 **1회** 호출한다. **폴링 금지.**

응답은 **공통부 + `jobType` 별 분기 필드**로 구성된다.

### 성공 응답 (200) — `jobType=CHALLENGE`

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "jobType": "CHALLENGE",
    "status": "DONE",
    "attempt": 1,
    "failCode": null,
    "resultVideoUrl": "https://cdn.motif.io/results/challenge/3fa85f64.mp4?Expires=...&Signature=...",
    "resultThumbnailUrl": "https://cdn.motif.io/results/challenge/3fa85f64.jpg?Expires=...&Signature=..."
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**게시 전 프리뷰라 signed URL** 이다(§1.7). 이 화면에서 확인 후 **§8.8 로 게시**한다.

### 성공 응답 (200) — `jobType=MOTIF`

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "jobUuid": "9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
    "jobType": "MOTIF",
    "status": "DONE",
    "attempt": 1,
    "failCode": null,
    "resultSeq": 10
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**URL 을 싣지 않는다.** 재생과 문구 입력은 §11.6·§11.8 이 담당하므로, `resultSeq` 로 **보관함 상세로 이동**한다(R8 확정).

### 성공 응답 (200) — 진행 중

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "jobType": "CHALLENGE",
    "status": "PROCESSING",
    "attempt": 1,
    "failCode": null,
    "resultVideoUrl": null,
    "resultThumbnailUrl": null
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`QUEUED`·`PROCESSING` 이면 **분기 필드가 전부 `null`** 이다.

### 성공 응답 (200) — 실패

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "jobType": "CHALLENGE",
    "status": "FAILED",
    "attempt": 2,
    "failCode": "FACE_NOT_DETECTED",
    "resultVideoUrl": null,
    "resultThumbnailUrl": null
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**잡이 실패해도 조회 자체는 성공(200)이다** — `status` 로 판단한다. 클라이언트는 `failCode` 로 안내하고 "다시 시도"(§13.4)를 노출한다.

| 필드 | 설명 |
|---|---|
| `status` | `UPLOADED` \| `QUEUED` \| `PROCESSING` \| `DONE` \| `FAILED` |
| `attempt` | 시도 횟수. 재시도할 때마다 증가한다 |
| `failCode` | 실패 사유. **코드표 미결**(원본 §16 #4) |

### MOTIF 잡의 완료 순서

```
합성 결과 업로드 → motif_results(description=NULL, isRep=false) 자동 생성 → 잡 DONE 확정 → FCM 발송
```

**결과 행 생성에 실패하면 DONE 으로 만들지 않고 FCM 도 보내지 않는다.** 그래서 `status=DONE` 인 MOTIF 응답에는 **`resultSeq` 가 항상 존재한다**.

### FCM 페이로드

**`jobUuid` 만 전달한다.** 앱은 알림을 눌러 진입할 때 이 API 를 1회 호출해 나머지를 받는다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` | 잡 없음·**타인 잡** |

---

## 13.4 POST /api/v1/composite-jobs/{jobUuid}/retry — 재시도

대상은 **본인의 `FAILED` 잡**이다.

**같은 잡 행을 재큐한다** — 새 잡을 만들지 않으므로 `jobUuid` 가 유지된다. 클라이언트가 들고 있던 식별자를 계속 쓸 수 있다.

### 서버 처리

1. `status: FAILED → QUEUED`
2. `fail_code = NULL` 초기화(상태별 CHECK 유지)
3. `attempt + 1`
4. 잡 행에서 큐 메시지를 재구성해 `job.composite` 재발행

결과 키가 `results/{type}/{jobUuid}.mp4` 로 **멱등**하게 정해져 있어, 재합성은 같은 키를 덮어쓴다.

### 성공 응답 (202)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "status": "QUEUED", "attempt": 2 },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` | 잡 없음·타인 잡 |
| 409 | `JOB_NOT_RETRYABLE` | `FAILED` 상태가 아님 |
| 410 | `RAW_VIDEO_EXPIRED` | **원본이 수명주기로 삭제됨** — 재촬영을 유도한다 |

```json
{
  "result": "FAIL",
  "code": "RAW_VIDEO_EXPIRED",
  "message": "원본 영상이 만료되었습니다. 다시 촬영해 주세요.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

410 Gone 을 쓰는 이유는 "있었지만 이제 없다"이기 때문이다 — 클라이언트는 재시도 버튼을 숨기고 재촬영을 안내해야 한다.

### 트레이드오프

**직전 실패 사유(`fail_code`)는 재큐 시 행에서 지워진다.** 실패 이력이 필요하면 로그·이벤트로 남긴다(행 보존이 아니다).

**수동 재시도에는 시도 상한이 없다**(기본안). 스윕 배치의 자동 재큐에 있는 `attempt < 3` 상한은 무한 루프 방지용으로 별개다.
