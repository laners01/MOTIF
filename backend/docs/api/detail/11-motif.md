# §11 모티프 (MOTIF) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §11 · DB 명세서 v0.15 §24~§30.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 11.1 | GET | `/api/v1/motif/assets` | 필요 | 에셋 목록 — **기본은 내 구독 아티스트** |
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

## 11.1 GET /api/v1/motif/assets — 에셋 목록

최신순 고정으로 offset 페이지네이션한다. **`artistSeq` 가 두 모드를 가른다.**

> **(변경) 기본 동작 — 전체가 아니라 내 구독 아티스트** (S15P21D104-224)
>
> 원본 `MOTIF_API_명세서_v0_11.md` §11.1 은 "`artistSeq` 를 생략하면 **전체 아티스트**
> 에셋"이라고 적혀 있다. 이 티켓에서 기본 동작을 **내가 구독한 아티스트의 에셋**으로
> 바꿨다.
>
> - **근거** — 숍의 기본 화면이 구독 기반이다. 미구독 아티스트 에셋이 섞이면 §4.7 구독
>   카드로 만든 섹션과 목록의 아티스트 집합이 어긋난다.
> - **전체 카탈로그를 한 번에 주는 경로는 두지 않는다.** 미구독 아티스트의 에셋은
>   아티스트 상세(§5)에서 `artistSeq` 로 들어온다 — 전체 목록이 필요한 화면이 없다.
> - 원본 명세서 파일은 수정하지 않았다.

### 요청

```http
GET /api/v1/motif/assets?page=0&size=20&artistSeq=3
Authorization: Bearer {accessToken}
```

| `artistSeq` | 대상 |
|---|---|
| **생략** | 내가 구독한 아티스트 전체의 에셋. 여러 아티스트가 한 목록에 **섞여** 최신순 |
| **지정** | **구독 여부와 무관하게** 그 아티스트의 에셋만 |

정렬 파라미터를 받지 않는다 — 최신순 고정이다.

### 구독 판정 (`artistSeq` 생략 시에만)

- `subscriptions` 에 내 행이 있는 아티스트만.
- **삭제된 아티스트는 제외한다.** 구독 행은 아티스트가 삭제돼도 남으므로(§4.6·§4.7
  `findSubscribed` 와 같은 규칙) 거르지 않으면 눌러도 404 가 나는 카드가 섞인다.
- 구독이 0건이면 빈 목록 200.

`artistSeq` 를 지정한 경우에는 삭제 아티스트 게이트가 없다 — 삭제 연쇄로 그 아티스트의
에셋이 전부 `is_active=FALSE` 가 되어 개별 구매자에게만 남는데, **산 것은 판매가 끊겨도
산 것이다**(§4.7 `purchasedAssetCount` 와 같은 규칙).

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
| `artist` | README §3.5 공통 아티스트 요약. **항목마다 붙는다** — 기본 모드에서 여러 아티스트가 섞이므로 클라이언트가 이 값으로 섹션을 묶는다 |

**목록에는 가격이 없다.** 가격과 구매 가능 여부는 §11.2 상세에서 준다.

### 숍 화면 구성 방법

구 §11.3 집계 API 는 삭제됐다. 클라이언트가 조합한다.

1. **§4.7** 로 구독 아티스트 목록 + 아티스트별 패스 보유 여부를 받아 섹션을 만든다
2. 각 섹션의 에셋은 **이 API 의 `artistSeq` 필터**로 조회한다
3. 상단 공용 배너는 **§11.11**

쿼리 없이 부른 목록을 `artist.artistSeq` 로 묶어 그려도 같은 화면이 나온다 — 섹션별
조회와 전체 조회 중 무엇을 쓸지는 클라이언트가 정한다.

### 실패 응답

**도메인 분기 없음.** 노출 가능한 에셋이 0건이면 빈 목록 200. 구독이 0건이거나 없는
`artistSeq` 를 넘겨도 마찬가지다 — **아티스트 존재를 검증하지 않는다**. 검증하면 없는
번호로 존재 여부를 떠보는 경로가 열린다.

### 구현 메모 (S15P21D104-224)

- 구독 필터 · 노출 필터 · `myAccess` 판정을 **한 네이티브 쿼리**에서 한다
  (`MotifAssetRepository.searchCatalog`). 셋 다 페이지 대상 집합을 바꾸므로 하나라도
  애플리케이션에서 거르면 `totalElements` 와 페이지 경계가 응답 항목 수와 어긋난다.
- 패스 만료 비교는 DB 의 `current_timestamp` 다 — 자바 시계로 꺼내 비교하면 앱 서버와
  DB 의 시계 차만큼 만료 직전·직후 판정이 갈린다.
- `PREMIUM` 의 패스 미커버만 서비스(`MyAccess.of`)가 판정한다. 쿼리는 "그 아티스트의
  유효 패스가 있는가"까지만 답한다 — DB 없이 고정할 수 있는 규칙이라 그쪽에 둔다.

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
- **보호 등급 재생목록 주소(`/api/v1/media/…`)를 쓰지 않는다.** 에셋 미리보기는
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

- `thumbnailUrl` 은 **보호 등급**이라 signed URL 이다 — 모티프 결과물 전체가 그렇다(§1.7).
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
- 서명이 만료(10분)돼 조각 요청이 403 이면 **재생목록을 다시 받는다** — core-api 가 새 서명으로 내려준다. §5.3 자동재생 목록의 만료 대응 경로와 같다.

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
