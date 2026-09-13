# §11 모티프 (MOTIF) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §11 · DB 명세서 v0.15 §24~§30.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 11.1 | GET | `/api/v1/motif/assets` | 필요 | 에셋 카탈로그 |
| 11.2 | GET | `/api/v1/motif/assets/{assetSeq}` | 필요 | 에셋 상세 |
| 11.3 | — | (삭제) 숍 집계 API | — | 클라이언트가 §4.7 + §11.1 조합 |
| 11.4 | GET | `/api/v1/artists/{artistSeq}/motif-pass` | 필요 | 아티스트별 패스 정보 |
| 11.5 | GET | `/api/v1/motif/results` | 필요 | 보관함 목록 (§5.8 공용) |
| 11.6 | GET | `/api/v1/motif/results/{resultSeq}` | 필요 | 결과물 상세(재생) |
| 11.7 | — | (삭제) 결과 저장 API | — | 잡 완료 시 서버가 자동 생성 |
| 11.8 | PATCH · DELETE | `/api/v1/motif/results/{resultSeq}` | 필요 | 문구·대표 설정 / 삭제 |
| 11.9 | GET | `/api/v1/users/me/motif-passes` | 필요 | 내 패스 일람 |
| 11.10 | — | 패스 취소 | — | 기획 유지, **현재 구현 범위 제외** |
| 11.11 | GET | `/api/v1/shop/banners` | 필요 | 숍 공용 배너 |

## 이용 권한 판정 — `myAccess`

에셋 하나에 대한 내 권한은 세 값 중 하나다. **우선순위는 `OWNED > PASS > NONE`** 이다.

| 값 | 의미 |
|---|---|
| `OWNED` | 개별 구매 보유 (`asset_purchases.status='ACTIVE'`) |
| `PASS` | 그 아티스트의 유효 패스로 열림 (`motif_passes.status='ACTIVE' AND expires_dttm > now`) |
| `NONE` | 권한 없음 |

개별 구매와 패스가 **동시에 유효하면 `OWNED`** 다.

`asset_type` 별 규칙:

| 타입 | 규칙 |
|---|---|
| `NORMAL` | 개별 구매 또는 패스로 이용 |
| `SECRET` | **`NORMAL` 과 완전히 동일**. 숨김·집계 예외가 없고 **패스 커버도 된다** |
| `PREMIUM` | **개별 구매 전용** — 패스로 열리지 않는다 |

---

## 11.1 GET /api/v1/motif/assets — 에셋 카탈로그

**전체 에셋**을 최신순 고정으로 offset 페이지네이션한다. `artistSeq` 는 선택 필터다.

### 요청

```http
GET /api/v1/motif/assets?page=0&size=20&artistSeq=3
Authorization: Bearer {accessToken}
```

`artistSeq` 를 생략하면 전체 아티스트의 에셋을 같은 형식으로 반환한다.

### 노출 규칙

| 대상 | 노출 |
|---|---|
| `is_active=TRUE` | 모든 사용자 |
| `is_active=FALSE` (판매 중단) | **개별 구매 보유자에게만**. 패스만 있거나 권한이 없으면 보이지 않는다 |

### 성공 응답 (200) — offset 페이지

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "assetSeq": 21,
        "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
        "title": "9월 신규 에셋",
        "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93.jpg",
        "description": "9월 신규 에셋 소개 문구",
        "durationMs": 9800,
        "assetType": "NORMAL",
        "myAccess": "PASS"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `title` | 에셋 표시명 — **전용 컬럼 `motif_assets.title`**(DB v0.11 신설) |
| `description` | 소개 문구. `title` 과 **다른 필드**이며 `null` 일 수 있다 |
| `thumbnailUrl` | 공개 CDN URL |

**목록에는 가격이 없다.** 가격과 구매 가능 여부는 §11.2 상세에서 준다.

### 숍 화면 구성 방법

구 §11.3 집계 API 는 삭제됐다. 클라이언트가 조합한다.

1. **§4.7** 로 구독 아티스트 목록 + 아티스트별 패스 보유 여부를 받아 섹션을 만든다
2. 각 섹션의 에셋은 **이 API 의 `artistSeq` 필터**로 조회한다
3. 상단 공용 배너는 **§11.11**

### 실패 응답

**도메인 분기 없음.** 노출 가능한 에셋이 0건이면 빈 목록 200.

---

## 11.2 GET /api/v1/motif/assets/{assetSeq} — 에셋 상세

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "assetSeq": 21,
    "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
    "title": "9월 신규 에셋",
    "previewVideoUrl": "https://cdn.motif.io/assets/2b7f1c93.mp4",
    "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93.jpg",
    "description": "9월 신규 에셋 소개 문구",
    "durationMs": 9800,
    "assetType": "NORMAL",
    "price": { "amount": "3900.00", "currency": "KRW" },
    "purchasable": true,
    "myAccess": "NONE"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `previewVideoUrl` | **공개 CDN URL** — 미리보기는 공개 등급이다(§1.7) |
| `price` | KR 활성 가격. **없으면 `null`** |
| `price.amount` | **문자열**이다 — `NUMERIC(10,2)` 를 부동소수로 보내지 않는다 |
| `purchasable` | 가격이 없으면 `false` |

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ASSET_NOT_FOUND` | 미존재, 또는 **판매 중단 에셋에 대한 비구매자 조회** |

```json
{
  "result": "FAIL",
  "code": "ASSET_NOT_FOUND",
  "message": "존재하지 않는 에셋입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

판매 중단(`is_active=FALSE`) 에셋은 **개별 구매자만** 단건 조회할 수 있다. 그 외에는 404 로 존재를 숨긴다.

---

## 11.4 GET /api/v1/artists/{artistSeq}/motif-pass — 아티스트별 패스

패스 구매 화면의 데이터다. 구 경로 `/motif/shop/artists/{artistSeq}` 를 REST 관점에 맞게 바꾼 것이다.

### 성공 응답 (200) — 미보유

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "artistSeq": 3,
    "bannerImageUrl": "https://cdn.motif.io/artists/3f9c1a2b/motif-bg.jpg",
    "passActive": false,
    "expiresDttm": null,
    "prices": [
      { "durationMonths": 1, "amount": "9900.00", "currency": "KRW" },
      { "durationMonths": 3, "amount": "27900.00", "currency": "KRW" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 성공 응답 (200) — 보유 중

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "artistSeq": 3,
    "bannerImageUrl": "https://cdn.motif.io/artists/3f9c1a2b/motif-bg.jpg",
    "passActive": true,
    "expiresDttm": "2026-10-01T00:00:00.000Z",
    "prices": []
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 규칙 |
|---|---|
| `bannerImageUrl` | `artists.motif_bg_image_key` → 공개 CDN URL. **숍 공용 배너(§11.11)와는 별개**다 |
| `prices` | 현재 판매 중인 **개월 수별 활성 가격**. **필드는 항상 존재**하며 없으면 빈 배열 |

**보유 중이면 `prices` 가 빈 배열이다** — 현재 구현은 만료 전 연장 구매를 지원하지 않는다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트 |

---

## 11.5 GET /api/v1/motif/results — 보관함 목록

**커서 페이지네이션**(§1.9), **최신순만** 지원한다(정렬 파라미터를 받지 않는다).

§5.8(아티스트 상세의 "내 모티프 전체 보기")은 **이 API 의 `artistSeq` 필터**를 그대로 쓰는 진입점이다.

### 요청

```http
GET /api/v1/motif/results?cursor={opaque}&size=20&artistSeq=3
Authorization: Bearer {accessToken}
```

### 성공 응답 (200) — 커서 페이지

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "resultSeq": 10,
        "thumbnailUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg",
        "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
        "regDttm": "2026-09-01T10:00:00.000Z",
        "isRep": true
      }
    ],
    "prevCursor": null,
    "nextCursor": "eyJyIjoxMH0",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- `thumbnailUrl` 은 **보호 등급**이라 signed 쿠키로 접근한다 — 모티프 결과물 전체가 그렇다(§1.7).
- `isRep` 은 대표 모티프 여부다(§4.1 의 `repMotif`).
- **`viewCount` 가 없다** — 모티프 결과물에는 조회수 카운터가 없다(R2 예외 명시).
- 본인 것만 나온다. 삭제한 결과물은 제외한다.

### 실패 응답

**도메인 분기 없음.**

---

## 11.6 GET /api/v1/motif/results/{resultSeq} — 결과물 상세

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "resultSeq": 10,
    "videoUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8",
    "thumbnailUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg",
    "description": null,
    "durationMs": 9800,
    "asset": { "assetSeq": 21, "title": "9월 신규 에셋" },
    "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
    "isRep": false,
    "regDttm": "2026-09-01T10:00:00.000Z"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- `description` 은 선택 사항이라 **`null` 일 수 있다** — 합성 직후 자동 생성 시 `NULL` 이고, 사용자가 §11.8 로 채운다.
- signed 쿠키가 만료(10분)돼 재생이 실패하면 **이 API 를 단건 재조회**해 쿠키를 다시 받는다 — §5.3 자동재생 목록의 만료 대응 경로다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `MOTIF_RESULT_NOT_FOUND` | 미존재·삭제, **타인 소유 포함**(존재 은닉) |

```json
{
  "result": "FAIL",
  "code": "MOTIF_RESULT_NOT_FOUND",
  "message": "존재하지 않는 결과물입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

---

## 11.8 PATCH · DELETE /api/v1/motif/results/{resultSeq}

구 §4.5(대표 모티프 설정)가 여기로 통합됐다(R4) — 문구 수정과 대표 설정이 같은 대상에 대한 수정이기 때문이다.

### PATCH 요청 — 부분 수정

```json
{ "description": "새 문구", "isRep": true }
```

| 필드 | 설명 |
|---|---|
| `description` | 결과물 문구. **최초 입력과 이후 수정 모두 이 API** 를 쓴다 |
| `isRep` | `true` 면 대표 모티프로 지정, `false` 면 해제 |

보낸 필드만 반영한다.

### PATCH 성공 응답 (200)

`data` 는 **§11.6 항목(수정 반영본)** 이다.

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "resultSeq": 10,
    "videoUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8",
    "thumbnailUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg",
    "description": "새 문구",
    "durationMs": 9800,
    "asset": { "assetSeq": 21, "title": "9월 신규 에셋" },
    "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
    "isRep": true,
    "regDttm": "2026-09-01T10:00:00.000Z"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 대표 모티프 단일성

- `isRep: true` — `users.rep_motif_result_seq` 를 이 결과물로 **교체**한다. **기존 대표는 자동으로 해제**된다(사용자당 1개).
- `isRep: false` — **현재 대표일 때만** 해제한다. 대표가 아닌 결과물에 `false` 를 보내면 아무 일도 일어나지 않는다.

### DELETE — 소프트 삭제

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**대표 모티프였다면 같은 트랜잭션에서 `users.rep_motif_result_seq = NULL`** 로 만든다 — FK 가 없는 논리 참조라 서비스가 정리해야 한다.

### 실패 응답 (PATCH·DELETE 공통)

| HTTP | code | 조건 |
|---|---|---|
| 404 | `MOTIF_RESULT_NOT_FOUND` | 미존재·삭제·**타인 소유** |
| 403 | `FORBIDDEN` | 권한 없음 |

---

## 11.9 GET /api/v1/users/me/motif-passes — 내 패스 일람

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "artistSeq": 3,
        "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
        "status": "ACTIVE",
        "startedDttm": "2026-09-01T00:00:00.000Z",
        "expiresDttm": "2026-10-01T00:00:00.000Z",
        "durationMonths": 1,
        "canceledDttm": null
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `status` | `ACTIVE` \| `EXPIRED` \| `REFUNDED` |
| `canceledDttm` | 취소 의사 기록. **이용 판정에는 참여하지 않는다** |

**실제 이용 가능 판정은 `status='ACTIVE' && expiresDttm > now`** 다 — `status` 만 보면 만료 배치가 돌기 전 구간에서 틀린다.

회원×아티스트당 1행이다(`uq_motif_passes_user_artist`) — 같은 아티스트의 패스를 다시 사면 기존 행을 재활성한다.

### 실패 응답

**도메인 분기 없음.**

---

## 11.11 GET /api/v1/shop/banners — 숍 공용 배너

숍 상단의 공용 배너다. 아티스트별 패스 배너(§11.4 `bannerImageUrl`)와 **별개**이며 어드민이 관리한다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      { "bannerSeq": 1, "imageUrl": "https://cdn.motif.io/banners/uuid.jpg", "displayOrder": 0 }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- **비페이지 전량**, `is_active=TRUE` 만, `displayOrder` 오름차순.
- 공개 콘텐츠라 CloudFront 일반 URL 이다.
- **클릭 액션(링크)이 없다** — 필요해지면 `linkType`/`linkTarget` 을 추가한다(기본안).
- 배너가 0건이면 빈 배열 200.

### 실패 응답

**도메인 분기 없음.**

---

## 11.7 (삭제) 결과 저장 API

`POST /motif/results` 는 **제거됐다**(v0.8). 클라이언트가 결과물을 저장하지 않는다.

**MOTIF 합성 잡이 완료될 때 서버가 `motif_results` 를 자동 생성**한다.

- 자동 생성 초기값: `description = NULL`, `isRep = false`
- **`motif_results` 생성까지 성공한 뒤에야** 잡을 `DONE` 으로 확정하고 FCM 을 발송한다 — 그래서 `status=DONE` 인 MOTIF 잡에는 `resultSeq` 가 항상 존재한다(§13.3)
- 중복 완료 이벤트는 `uq_motif_results_job`(= `job_seq` 유일)으로 막는다. 생성 주체는 `composite.done` 코어 컨슈머이며 `ON CONFLICT DO NOTHING` 으로 멱등하다
- 사용자가 편집 화면에서 멘트를 입력하면 §11.8 PATCH 로 따로 수정한다. 입력하지 않고 나가면 `description = NULL` 인 채로 정상 보관된다

## 11.10 패스 취소 — 현재 구현 범위 제외

패스 취소 기능과 `canceled_dttm` 개념은 **기획상 유지**하되 이번 구현 범위에서는 API 를 만들지 않는다. 자동결제를 재도입하면 갱신 차단 판정에 재사용한다.

## 11.3 (삭제) 숍 집계 API

`GET /motif/shop` 은 **제거됐다**(v0.8). §11.1 의 "숍 화면 구성 방법" 참조.
