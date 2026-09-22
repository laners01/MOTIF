# §11 모티프 (MOTIF) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §11 · DB 명세서 v0.15 §24~§30.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 11.1 | GET | `/api/v1/motif/assets` | 필요 | **내 보유함** — 구매한 에셋 전량 |
| 11.2 | GET | `/api/v1/motif/assets/{assetSeq}` | 필요 | 에셋 상세 |
| 11.3 | GET | `/api/v1/motif/shop` | 필요 | **숍 전량** — 구독 아티스트 × 에셋 *(부활)* |
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

## 11.1 GET /api/v1/motif/assets — 내 보유함 (구매한 에셋)

> **(변경) 숍 목록 → 보유함** (S15P21D104-219)
>
> 이 API 는 두 번 바뀌었다.
>
> | 시점 | 대상 | 페이지 |
> |---|---|---|
> | 원본 v0.11 | 전체 아티스트 에셋 | offset |
> | S15P21D104-224 | 내 구독 아티스트 에셋 | offset |
> | **지금** | **내가 구매한 에셋** | **없음 (비페이지 전량)** |
>
> 숍 첫 화면을 그리던 자리는 **§11.3 `GET /api/v1/motif/shop`** 이 가져갔다. 구독 수만큼
> 이 API 를 부르는 왕복을 없애려고 되살린 API 다.
>
> **깨지는 변경이다.** 봉투가 `PageResponse` → `ListResponse` 로 바뀌어
> `page`·`size`·`totalElements`·`hasNext` 가 사라지고, 요청의 `page`·`size` 를 받지 않으며,
> 항목에서 `myAccess` 가 빠졌다. 안드로이드가 함께 고쳐야 한다.
>
> 원본 명세서 파일은 수정하지 않았다.

### 요청

```http
GET /api/v1/motif/assets?artistSeq=3
Authorization: Bearer {accessToken}
```

`artistSeq` 를 생략하면 내가 산 것 전부, 주면 그 아티스트에게서 산 것만이다.
**정렬은 구매 최신순 고정**이고 파라미터를 받지 않는다.

### 대상 — 산 것은 산 것이다

`asset_purchases.status='ACTIVE'` 인 내 구매만 나온다. 세 가지를 **보지 않는다**.

| 보지 않는 것 | 이유 |
|---|---|
| `is_active` (판매 중단) | 판매가 끊겨도 내 보유는 남는다 (§4.7 `purchasedAssetCount` 와 같은 규칙) |
| 구독 | 구독을 끊어도 산 것은 남는다 |
| `artists.is_deleted` | 같은 이유. 이름·프로필은 행이 남아 있어 계속 나온다 |

**패스로만 열리는 에셋은 나오지 않는다** — 산 것이 아니다. 패스로 무엇이 열리는지는
§11.3 숍의 `myAccess` 가 답한다.

### 성공 응답 (200) — 비페이지 전량

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
        "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93/thumb.jpg",
        "description": "9월 신규 에셋 소개 문구",
        "durationMs": 9800,
        "assetType": "NORMAL"
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `title` | 에셋 표시명 — 전용 컬럼 `motif_assets.title` |
| `description` | 소개 문구. `title` 과 **다른 필드**이며 `null` 일 수 있다 |
| `thumbnailUrl` | 공개 CDN URL |
| `artist` | README §3.5 공통 아티스트 요약. 여러 아티스트에게서 산 것이 섞이므로 항목마다 붙는다 |

**가격이 없다** — §11.2 상세의 몫이다.
**`myAccess` 가 없다** — 전부 개별 구매라 값이 `OWNED` 하나뿐인 칸이 된다.

### 실패 응답

**도메인 분기 없음.** 구매가 0건이거나 없는 `artistSeq` 를 넘기면 빈 목록 200 이다 —
**아티스트 존재를 검증하지 않는다**.

### 구현 메모 (S15P21D104-219)

- `asset_purchases` 가 FROM 절이다(`MotifAssetRepository.findPurchased`). 에셋에서
  출발해 구매를 `exists` 로 거르면 정렬 기준인 `reg_dttm` 이 서브쿼리에 갇혀
  **구매 최신순을 낼 수 없다**. 동률은 `purchase_seq` 로 끊는다.
- 페이지가 없어진 이유는 대상이 "한 사람이 산 것" 이라 카탈로그처럼 늘어나지 않고,
  화면도 한 번에 펼쳐 놓기 때문이다.

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
    "previewVideoUrl": "https://cdn.motif.io/assets/2b7f1c93/master.m3u8",
    "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93/thumb.jpg",
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
| `purchasable` | 지금 구매할 수 있는가. 아래 세 조건을 모두 만족할 때만 `true` |

### previewVideoUrl — 키를 조립하지 않는다

`motif_assets.video_key` 를 그대로 URL 로 바꾼 값이다.

- 가공이 끝나면 app-media 가 `video_key` 를 **마스터 재생목록으로 덮어쓴다**
  (`MediaJobProcessor.recordKeys`). 서버가 `master.m3u8` 을 붙일 이유가 없다.
- 가공 전에는 업로드 MP4 키(`assets/{uuid}.mp4`)가 그대로 들어 있고 썸네일은 404 다 —
  어드민 등록 직후의 짧은 구간이다.
- **서명을 붙이지 않는다.** 에셋 미리보기는
  `assets/` 프리픽스라 공개 등급이고 조각도 무서명이다. 보호 경로로 돌리면 CDN 캐시를
  버린다.

### purchasable *(기본안 — 원본은 "가격이 없으면 false" 만 정했다)*

| 조건 | 이유 |
|---|---|
| `is_active=TRUE` | 내려간 상품은 살 수 없다 |
| KR 활성 가격 존재 | 값이 없으면 결제를 시작할 수 없다 |
| `myAccess != OWNED` | 이미 산 것은 다시 못 산다 — `uq_asset_purchases_active` 가 (회원, 에셋) `ACTIVE` 구매를 하나로 막아 `true` 를 주면 눌렀을 때 실패한다 |

**`myAccess=PASS` 는 `true` 다.** 패스는 만료되므로 지금 열려 있어도 영구 보유를 따로
사는 선택이 남아 있어야 한다 — 패스가 끝나는 순간 구매 버튼이 갑자기 생기는 화면이 더
이상하다.

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
**패스만 보유한 사용자도 404 다** — §11.1 노출 규칙과 같은 판정이다. 403 을 주면 "그
에셋은 존재한다" 가 응답에서 읽히므로 미존재와 같은 코드를 쓴다(§1.8).

### 구현 메모 (S15P21D104-221)

- 노출 필터·`owned`·`passActive` 는 §11.1 과 같은 판정이고, 여기에 KR 활성 가격
  left join 이 붙는 단건 쿼리다(`MotifAssetRepository.findDetail`). **쿼리가 빈 결과면
  곧 404** 라 "미존재"와 "판매 중단 + 비구매자"를 서비스가 구분하지 않는다.
- §11.1 과 SQL 이 닮았지만 합치지 않는다 — 목록은 페이지·정렬이 있고 상세는 가격
  조인이 붙는다. 한 쿼리로 묶으면 목록이 항목마다 가격을 조인하게 된다.
- 가격 국가는 KR 고정(`AssetQueryService.PRICE_COUNTRY_CODE`). `AdminAssetService` 의
  같은 값과 공유하지 않는다 — 그쪽은 어드민이 가격을 넣을 때의 기본 국가이고 여기는
  팬에게 보여 줄 국가라, 나라가 늘면 정해지는 방식이 갈린다.
- `amount` 는 `BigDecimal.toPlainString()` 이다. `toString()` 은 지수 표기를 낼 수 있다.

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
| `bannerImageUrl` | `artists.motif_bg_image_key` → 공개 CDN URL. 미설정이면 `null`. **숍 공용 배너(§11.11)와는 별개**다 |
| `prices` | 현재 판매 중인 **개월 수별 활성 가격**, **개월 오름차순**. **필드는 항상 존재**하며 없으면 빈 배열 |
| `prices[].amount` | **문자열**이다 — `NUMERIC(10,2)` 를 부동소수로 보내지 않는다(§11.2 와 같은 계약) |

**보유 중이면 `prices` 가 빈 배열이다** — 현재 구현은 만료 전 연장 구매를 지원하지 않는다.

가격이 0건이어도 200 이다(빈 배열). 판매 준비 중인 아티스트가 그렇다. 꺼진 가격
(`is_active=FALSE`)은 나오지 않는다 — 내보내면 눌러도 결제가 시작되지 않는 버튼이 생긴다.

### 유효 판정

`status='ACTIVE' AND expires_dttm > now` 이고 **비교는 DB 가 한다**. `status` 만 보면
만료 배치가 돌기 전 구간에서 틀린다. `uq_motif_passes_user_artist` 로 (회원, 아티스트)당
한 행이라 결과는 0 또는 1건이다.

**§5.3 아티스트 상세의 `pass` 칸과 같은 판정이다** — `findActivePassExpiry` 하나를 두
API 가 쓴다. 같은 질문에 두 곳이 답하면 상세 화면과 구매 화면이 서로 다른 말을 하는
순간이 생긴다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트 |

### 구현 메모

- **매핑은 `ArtistController` 에 있다.** §11 도메인이지만 경로가 `/artists/{artistSeq}`
  아래이고, 이 레포는 경로가 컨트롤러를 정한다. 서비스만
  `ArtistMotifPassQueryService` 로 따로 뒀다 — 랭킹·구독이 이미 각자 서비스를 갖는
  방식과 같다.
- 보유 중이면 **가격을 조회하지 않는다**. 응답이 빈 배열로 정해져 있는데 쿼리를 쏠
  이유가 없다.
- 가격 국가는 KR 고정(`ArtistMotifPassQueryService.PRICE_COUNTRY_CODE`). 시드도 KR
  1행뿐이다(changeSet 0013-02). §11.2·어드민의 같은 값과 공유하지 않는다.

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
        "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1790000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
        "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
        "regDttm": "2026-09-01T10:00:00.000Z",
        "isRep": true
      }
    ],
    "prevCursor": null,
    "nextCursor": "MTA",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- `thumbnailUrl` 은 **보호 등급**이라 signed URL 이다 — 모티프 결과물 전체가 그렇다(§1.7).
  서명 만료는 **6시간**(`motif.media.signing.image-ttl`)이다. 재생목록(10분)보다 긴 이유는
  `<img>` 가 403 이 되면 되받아 복구할 경로가 없기 때문이다.
- `isRep` 은 대표 모티프 여부다(§4.1 의 `repMotif`). 사용자당 하나뿐이다.
- **`viewCount` 가 없다** — 모티프 결과물에는 조회수 카운터가 없다(R2 예외 명시).
- 본인 것만 나온다. 삭제한 결과물은 제외한다.
- **삭제된 아티스트의 결과물도 나온다** — 내가 만든 것이고 아티스트 삭제와 무관하다
  (§11.1 보유함과 같은 규칙). 이름·프로필은 행이 남아 있어 계속 조인된다.

### 커서

| 항목 | 값 |
|---|---|
| 정렬 키 | `result_seq` — `reg_dttm` 은 같은 초에 두 건이 만들어지면 페이지 경계가 흔들린다 |
| 방향 | 앞으로만. `prevCursor` 는 `null`, `hasPrevious` 는 `false` (§5.5 와 같다) |
| `size` | 기본 20, **상한 100** |

**offset 이 아닌 이유**는 보관함이 앞쪽에서 늘어나는 목록이기 때문이다 — 합성이 끝날
때마다 새 결과물이 맨 위에 붙어 페이지가 밀린다.

### 실패 응답

**도메인 분기 없음.** 결과물이 0건이면 빈 목록 200 이고 `nextCursor` 도 `null` 이다.

**망가진 커서는 400 `INVALID_PARAMETER`** 다 — 조용히 첫 페이지로 되돌리면 무한 스크롤이
원인 없이 맨 위로 점프한다.

### 구현 메모 (S15P21D104-227)

- 조회 **3번 고정** — 목록 · 아티스트 요약 배치(`ArtistRepository.findCards`) · 내 대표
  모티프 seq(`users.rep_motif_result_seq`). 항목마다 읽으면 그대로 N+1 이다.
- 대표 여부를 결과물 쪽 칼럼으로 두지 않는다 — 사용자당 하나라 `users` 가 갖는다.
- 리포지토리는 **파생 메서드 4개**(커서 유무 × `artistSeq` 유무)다. 한 쿼리에 "null 이면
  전체" 분기를 넣으면 타입 없는 `null` 바인딩이 운영 500 이 된다(§5.5 와 같은 근거).
- 커서 토큰은 `Cursors` 의 Long 오버로드를 쓴다 — `result_seq` 가 `BIGINT` 다. 형식은
  Integer 쪽과 같아 클라이언트는 여전히 커서를 불투명하게 다루면 된다.

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
    "videoUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
    "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1790000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
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
- `asset` 은 `assetSeq`·`title` **둘뿐이다**. 썸네일·가격은 §11.2 상세다.

### 두 URL 의 서명이 다르다 *(ADR-0012)*

| 필드 | 서명 | 이유 |
|---|---|---|
| `videoUrl` | **프리픽스 서명**(`Policy`) | HLS 는 재생목록 안에서 조각을 더 요청한다. 클라이언트가 그 요청에도 **같은 파라미터**를 붙여야 한다 |
| `thumbnailUrl` | **단일 파일 서명**(`Expires`) | 그 자체로 끝나는 주소다. 만료 6시간 |

서명이 만료(10분)돼 조각 요청이 403 이면 **`POST /api/v1/media/{jobUuid}/signature`** 로
새 파라미터를 받는다 — 이 API 를 다시 부를 필요가 없다.

**`videoUrl` 은 키를 조립해 만들지 않는다.** `motif_results.video_key` 가 이미 마스터
재생목록 키이고(app-media 가 가공 후 덮어쓴다), `MediaUrlResolver.resolvePlaylist` 가
그 값을 그대로 받는다.

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

403 으로 가르지 않는다 — `resultSeq` 는 찍어 볼 수 있는 값이라 "그건 남의 것" 이
응답에서 읽히면 안 된다(§1.8).

**삭제된 아티스트·판매 중단 에셋의 결과물은 열린다.** §11.5 목록에 나오는 것이 여기서
404 면 목록과 상세가 어긋난다.

### 구현 메모 (S15P21D104-228)

- `findByResultSeqAndUserSeqAndDeletedFalse` 가 **이미 `userSeq` 를 걸어** 타인 소유가
  자동으로 빈 결과가 된다. 서비스가 소유자 분기를 따로 두지 않는다.
- **`CompositeJob` 을 읽지 않는다.** ADR-0012 이후 재생 주소는 `video_key` 로 만든다.
- `MOTIF_RESULT_NOT_FOUND` 는 `MotifErrorCode` 가 갖는다 — §11.8 의 404 도 같은 코드다.

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
    "videoUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
    "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1790000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
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

> **(변경) 403 `FORBIDDEN` 은 두지 않는다.** 원본 명세 표에는 있지만 이 설계에서 도달할
> 수 없다 — 조회가 `user_seq` 를 걸고 있어 타인 소유가 이미 404 다. §8.9 가 403 을 갖는
> 이유는 남의 `PUBLIC` 영상이 조회는 되기 때문인데, 모티프 결과물은 남에게 아예 보이지
> 않는다. 404 하나로 합친다.

**이미 삭제한 결과물에 DELETE 를 다시 보내면 404 다.** 멱등으로 200 을 주지 않는다 —
조회가 삭제분을 빼고 있어 "이미 지웠다" 와 "없다" 를 구분할 방법이 없다.

### 구현 메모

- **대표 해제를 "이전 대표 조회 → 끄기" 로 짜지 않는다.** `users` 칸 하나를 덮어쓰면
  끝이다(`User.changeRepMotif`). 조회해서 끄면 그 사이에 다른 요청이 대표를 바꿨을 때
  남의 지정을 되돌린다.
- 쓰기라 서비스를 나눴다(`MotifResultCommandService`). 조회 서비스는
  `@Transactional(readOnly = true)` 라 쓰기를 섞으면 그 표시가 거짓이 된다.
- PATCH 응답은 §11.6 상세를 **다시 호출해** 만든다 — 조립을 한 벌 더 쓰면 수정 직후
  화면과 상세 화면이 갈린다.
- `description` 상한은 **1000자**(§8.9 와 같은 값). DB 는 TEXT 지만 상한이 없으면 본문
  크기 제한이 서버 전역 설정에만 걸린다.
- 삭제는 **소프트 삭제만** 한다. S3 산출물·`composite_jobs` 는 건드리지 않는다.

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
| `status` | `ACTIVE` \| `EXPIRED` \| `REFUNDED` — **실효 상태**(DB 원시값이 아니다) |
| `canceledDttm` | 취소 의사 기록. **이용 판정에는 참여하지 않는다** |

회원×아티스트당 1행이다(`uq_motif_passes_user_artist`) — 같은 아티스트의 패스를 다시 사면 기존 행을 재활성한다.

### status 는 실효 상태다

이용 가능 판정은 **`status='ACTIVE' && expiresDttm > now`** 다. `ACTIVE → EXPIRED` 전이
배치가 없어 **기간이 지난 `ACTIVE` 행**이 DB 에 정상적으로 존재하는데(DB 명세 v0.15 §35),
이 API 는 그 행을 **`EXPIRED` 로** 내린다. 원시값을 그대로 내보내면 클라이언트가 못 쓰는
패스를 활성으로 읽고, 실제 접근(§11.2·§11.3)이 이미 거부하므로 화면 둘이 갈린다.

판정은 이 API 가 따로 계산하지 않는다 — **§11.4**(아티스트별 패스)·**§4.7**(구독 카드)의
`passActive` 와 **같은 조회**(`MotifPassRepository.findActivePasses`)가 DB 시각으로 답한
것을 그대로 쓴다. 세 화면이 같은 패스를 두고 다른 말을 하지 않는다.

`expiresDttm`·`startedDttm`·`durationMonths`·`canceledDttm` 은 원시값 그대로다. 실효 상태를
덧입히는 것은 `status` 하나뿐이다.

> **DB 행은 고치지 않는다.** 조회가 `EXPIRED` 로 UPDATE 하면 이 화면을 연 사람의 행만
> 정리돼 정합성은 여전히 보장되지 않고, 권한은 이미 `expires_dttm` 이 책임진다. DB 명세
> §35 가 예정한 전이 배치는 만료 알림·통계 같은 요구가 생길 때 별도로 만든다.

### 정렬 · 범위

| 항목 | 값 |
|---|---|
| 정렬 | **만료 늦은 순**(`expires_dttm DESC`) — 유효한 패스가 자연히 위로 온다. 동률은 `pass_seq DESC` |
| 상태 | `ACTIVE`·`EXPIRED`·`REFUNDED` **전부**. 결제 이력 성격이라 거르지 않는다 |
| 삭제 아티스트 | **포함한다**. §4.6·§4.7 은 빼지만 그쪽은 "지금 갈 수 있는 곳" 목록이고 여기는 돈을 낸 기록이다(§11.1 보유함과 같은 규칙) |

`artistSeq` 최상위 칸은 `artist.artistSeq` 와 같은 값이다 — 명세 형태를 그대로 유지한다.

### 실패 응답

**도메인 분기 없음.** 패스가 0건이면 빈 배열 200 이고, 그때는 아티스트 조회를 부르지
않는다(빈 `in` 절을 보내지 않는다).

### 구현 메모 (S15P21D104-70 · 269)

- 매핑은 `UserController` 다 — 경로가 `/users/me` 아래이고 이 레포는 경로가 컨트롤러를
  정한다(§11.4 를 `ArtistController` 에 넣은 것과 같은 기준). 서비스만
  `UserMotifPassQueryService` 로 따로 뒀다.
- `MotifPassRepository.findActive` 를 고쳐 쓰지 않는다. 그쪽은 어드민 §3.14.2 용이라
  **유효 패스만** 세 칸으로 주는 투영이고, 여기는 전량 × 전체 칸이다.
- 조회 3번 고정 — 패스 전량 · `ArtistRepository.findCards`(배치) ·
  `MotifPassRepository.findActivePasses`(배치). 마지막 하나가 실효 상태의 출처다.
  항목마다 유효 여부를 물으면 그대로 N+1 이고, 자바에서 `expiresDttm` 을 비교하면 이
  화면만 다른 시계를 쓰게 된다.

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
- 공개 콘텐츠라 **무서명** CloudFront URL 이다 — 서명을 붙이면 사용자마다 URL 이 갈려
  CDN 캐시가 쪼개진다.
- **클릭 액션(링크)이 없다** — 필요해지면 `linkType`/`linkTarget` 을 추가한다(기본안).
  컬럼도 도입하지 않은 상태다(changeSet 0011-01).
- 배너가 0건이면 빈 배열 200.

### 어드민 §3.11.1 과 무엇이 다른가

같은 테이블을 읽지만 **집합도 항목도 다르다**.

| | 어드민 §3.11.1 | 팬 §11.11 |
|---|---|---|
| 대상 | **전량**(비활성 포함) — 내려둔 배너까지 보고 순서를 잡아야 한다 | **활성만** |
| 항목 | `imageKey`·`isActive`·`regDttm` 등 운영 칸 포함 | `bannerSeq`·`imageUrl`·`displayOrder` 셋뿐 |

그래서 조회도 DTO 도 공유하지 않는다 — 운영 칸이 팬 응답에 섞이면 내려둔 배너가 있다는
사실과 S3 키가 그대로 드러난다(§11.1 ↔ §3.8.1 과 같은 규칙).

### 실패 응답

**도메인 분기 없음.**

### 구현 메모

- 활성 필터는 **쿼리에 있다**(`findByActiveTrueOrderByDisplayOrderAsc`). 전량을 읽어
  자바에서 거르면 비활성이 쌓일수록 읽는 양만 늘고, "활성만" 은 응답 계약이라 조회에
  박혀 있어야 한다.
- 컨트롤러가 **principal 을 받지 않는다** — 응답에 "내" 상태가 없다. 인증 자체는
  `SecurityConfig` 의 catch-all 이 건다(§5.4 와 같은 판단).
- 경로가 `/api/v1/shop/banners` 라 §11.3 숍 전량(`/api/v1/motif/shop`)과 컨트롤러가
  갈린다.

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

## 11.3 GET /api/v1/motif/shop — 숍 전량 *(부활)*

> **(변경) 삭제 철회** (S15P21D104-219)
>
> 원본 `MOTIF_API_명세서_v0_11.md` §11.3 은 이 API 를 **제거**하고 "클라가 §4.7 + §11.1
> 을 조합한다"로 정했다. 되살린다.
>
> - **근거** — 숍 첫 화면에서 구독 수만큼 §11.1 을 호출하게 되어 왕복이 아티스트 수에
>   비례한다. 조합 비용이 클라이언트가 아니라 네트워크에 붙는다.
> - 서버는 조회 3번으로 끝난다(구독·에셋·패스). 아티스트 수와 무관하다.
> - 원본 명세서 파일은 수정하지 않았다.

내가 구독한 아티스트가 섹션이고, 각 섹션에 그 아티스트의 에셋이 **전량** 들어 있다.

### 요청

```http
GET /api/v1/motif/shop
Authorization: Bearer {accessToken}
```

**파라미터가 없고 페이지네이션도 없다.** 봉투는 `ListResponse`(§5.4)다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
        "passActive": true,
        "expiresDttm": "2026-10-01T00:00:00.000Z",
        "assets": [
          {
            "assetSeq": 21,
            "title": "9월 신규 에셋",
            "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93/thumb.jpg",
            "description": "9월 신규 에셋 소개 문구",
            "durationMs": 9800,
            "assetType": "NORMAL",
            "myAccess": "OWNED"
          }
        ]
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `artist` | README §3.5 공통 아티스트 요약 |
| `passActive` · `expiresDttm` | 그 아티스트의 패스 보유 여부. 미보유면 `false` / `null` |
| `assets[].myAccess` | **구매 여부가 여기 있다** — `OWNED` 가 개별 구매 |

**섹션 안의 에셋에는 `artist` 가 없다** — 섹션이 이미 아티스트를 말하고 있다(§11.1 과 다른 점).
**가격도 없다** — 가격과 구매 가능 여부는 §11.2 상세다.

### 정렬

| 축 | 기준 |
|---|---|
| 아티스트 | **구독 최신순** — §4.6·§4.7 과 같은 기준이다. 같은 목록이 화면마다 다른 순서면 안 된다 |
| 에셋 | **최신순**(`asset_seq DESC`) — §11.1 과 같은 정렬 |

### 노출 규칙 · myAccess

§11.1 과 같다.

| 대상 | 노출 |
|---|---|
| `is_active=TRUE` | 모든 사용자 |
| `is_active=FALSE` (판매 중단) | **개별 구매 보유자에게만**. 패스만 있으면 보이지 않는다 |

우선순위 `OWNED > PASS > NONE`, `PREMIUM` 은 패스로 열리지 않고 `SECRET` 은 `NORMAL` 과
동일하다. 삭제된 아티스트는 구독 조회 단계에서 빠진다.

### 실패 응답

**도메인 분기 없음.** 구독이 0건이면 빈 배열 200.

**에셋이 0건인 구독 아티스트는 섹션이 나오고 `assets` 가 빈 배열이다.** 섹션을 빼면
"구독했는데 숍에 없는 아티스트" 가 생겨 §4.7 구독 목록과 어긋난다.

### 구현 메모

- 조회 3번 고정 — `SubscriptionRepository.findSubscribed`(순서의 출처) ·
  `MotifAssetRepository.searchShop`(에셋 한 번에) ·
  `MotifPassPriceRepository.findActivePasses`(아티스트별 패스). 앞 둘은 §4.6·§4.7 이
  이미 쓰던 메서드다.
- 에셋 쿼리에 아티스트·패스를 **조인하지 않는다**. 이름·프로필은 구독 조회가 이미
  갖고 있고 패스는 아티스트당 하나라, 에셋 행마다 다시 판정하면 같은 답을 에셋 수만큼
  계산하게 된다. `myAccess` 는 행의 `owned` 와 패스 맵을 서비스가 합쳐 정한다.
- 구독이 0건이면 뒤 두 조회를 **부르지 않는다** — Postgres 에서 `in ()` 은 문법 오류다.
