# MOTIF API 상세 명세

원본은 [`docs/api/MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md)다. **원본은 팀이 관리하며 여기서 고치지 않는다.**

이 디렉터리는 원본을 **구현 가능한 수준까지 내린 문서**다. 원본이 "2.1 과 동일한 토큰 쌍 + `user` 요약" 처럼 참조로 적은 부분을, 중복을 감수하고 **완전한 JSON 으로 다시 쓴다** — 안드로이드가 화면을 만들 때 문서 두 곳을 오가지 않게 하는 것이 목적이다.

- 원본과 어긋나는 내용을 쓰지 않는다. 원본이 비워둔 지점만 채우고, 채운 것은 **(기본안)** 으로 표시한다.
- 원본 개정으로 계약이 바뀌면 이 문서를 따라 고친다. 판단이 갈리면 **원본이 우선**이다.
- 각 문서의 JSON 예시는 해당 API 의 Swagger `@ExampleObject` 와 **같은 값**을 쓴다. 문서와 Swagger 가 다른 값을 보여주면 둘 다 신뢰를 잃는다.

## 문서 구성

| 문서 | 원본 절 | 내용 |
|---|---|---|
| 이 문서 | §1, §15 | 공통 규약·에러 코드 |
| [`02-auth.md`](02-auth.md) | §2 | 구글 로그인 · 토큰 재발급 · 로그아웃 |
| [`03-onboarding.md`](03-onboarding.md) | §3 | 온보딩 완료 |
| [`04-user.md`](04-user.md) | §4 | 마이페이지 8종 |
| [`05-artist.md`](05-artist.md) | §5 | 랭킹 · 검색(자동완성 겸용) · 상세 · 챌린지 · 공지 · 구독 |
| [`06-home.md`](06-home.md) | §6 | HOT 피드 · 구독 아티스트 활동 |
| [`07-challenge.md`](07-challenge.md) | §7 | 챌린지 피드 · 상세 · 좋아요 · 조회 |
| [`08-video.md`](08-video.md) | §8 | 참여 영상 피드 · 단건 · 게시 · 수정 · 삭제 · 신고 |
| [`09-comment.md`](09-comment.md) | §9 | 댓글 3종 공통 6개 |
| [`10-notice.md`](10-notice.md) | §10 | 공지 좋아요 (조회는 §5.5) |
| [`11-motif.md`](11-motif.md) | §11 | 에셋 카탈로그 · 패스 · 보관함 · 배너 |
| [`12-order.md`](12-order.md) | §12 | 주문 생성 · 결제 승인 · 취소 |
| [`13-composite.md`](13-composite.md) | §13 | 합성 잡 생성 · 요청 · 조회 · 재시도 |
| [`14-notification.md`](14-notification.md) | §14 | FCM 디바이스 토큰 (알림 목록은 로컬) |

**이 디렉터리는 구현 가이드라인이다.** 무엇을 만들어야 하는지를 적고, 무엇이 만들어졌는지는 적지 않는다 — 진행 상태는 Jira 가 갖는다. 구현은 API 하나가 이슈 하나·브랜치 하나다.

---

## 1. 공통 응답 봉투 (원본 §1.2)

모든 응답은 아래 봉투로 감싼다. HTTP 상태 코드는 봉투와 **별개로 정확히** 쓴다(200/201/202, 4xx/5xx).

### 성공 — 단건·일반

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {},
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

- `result`·`code`·`message` 는 성공에서 **위 값으로 고정**이다. 클라이언트 분기는 `result` 와 HTTP 상태로만 하고 `message` 는 표시용으로만 쓴다.
- 본문 없는 성공도 봉투를 유지한다 — `"data": null`.

### 성공 — offset 페이지 응답

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [],
    "page": 0,
    "size": 20,
    "totalElements": 135,
    "totalPages": 7,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

### 성공 — 커서 페이지 응답 (원본 §1.9)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [],
    "prevCursor": null,
    "nextCursor": "MTAwMDAwMDAwMzQwMDo4OA",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

- `nextCursor: null`(= `hasNext: false`)이면 끝이다. 커서는 서버가 만든 opaque 토큰이며 **클라이언트는 해석·조작하지 않는다**.
- 방향은 커서 토큰에 인코딩된다 — 요청 파라미터는 `?cursor=` 하나다. 받은 `prevCursor` 를 그대로 넣으면 역방향이다.

### 실패 — 일반

```json
{
  "result": "FAIL",
  "code": "VIDEO_NOT_FOUND",
  "message": "존재하지 않는 영상입니다.",
  "data": "videoSeq: 88",
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

### 실패 — 검증 오류(400)

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [
    { "field": "nickname", "message": "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다." }
  ],
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

### 봉투를 쓰지 않는 예외

**좋아요 생성/취소 POST·DELETE 성공은 `204 No Content`** 이며 본문이 없다(원본 §1.2 · §7.3 · §8.3 · §9.5 · §10.2). `isLiked`·`likeCount` 를 돌려주지 않으므로 클라이언트가 낙관적으로 반영하고 실패 시 롤백한다.

### HTTP 상태 코드 사용

| 상황 | 코드 |
|---|---|
| 조회·수정·삭제·토글 성공 | 200 |
| 생성 성공 | 201 |
| 비동기 접수(합성 요청·조회 이벤트) | 202 |
| 좋아요 생성/취소 성공 | 204 |
| 그 밖 | 4xx·5xx + 실패 봉투 |

---

## 2. 공통 규약 (원본 §1.1 · §1.3)

| 항목 | 규칙 |
|---|---|
| Base URL | `/api/v1` — 예: `POST /api/v1/auth/refresh` |
| 인증 | `Authorization: Bearer {accessToken}`. 🔓 표시 API 외 전부 필수 |
| Content-Type | `application/json; charset=utf-8`. 예외는 presigned URL 로 S3 에 직접 올리는 PUT(해당 미디어 타입) |
| 본문 표기 | JSON, camelCase |
| 식별자 | 서버 발급 숫자 ID(`*Seq`). 예외는 주문 `orderUuid`, 합성 잡 `jobUuid` (UUID) |
| 시각 | ISO-8601 UTC, **밀리초 3자리 고정** — `2026-09-02T06:00:00.000Z` |

시각 3자리 고정은 구현에서 강제된다 — `coreapi.global.config.JacksonConfig` 가 `Instant` 전역 직렬화를 바꾼다. Jackson 기본값은 소수부가 0이면 생략하고 나노초가 있으면 9자리까지 쓰기 때문에, 그대로 두면 자릿수가 요청마다 달라진다.

---

## 3. 공통 실패 분기 (원본 §1.4)

아래는 **전 API 공통**이라 각 API 문서의 실패 표에서는 생략한다. 각 문서에는 도메인 분기만 적는다.

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | 형식·필수값 검증 실패 (`data` 에 필드 오류 배열) |
| 401 | `UNAUTHORIZED` | 토큰 없음·만료·위조 (🔓 API 제외) |
| 403 | `ACCOUNT_SUSPENDED` | `user_accounts.account_status='SUSPENDED'` |
| 403 | `ACCOUNT_DELETED` | `users.is_deleted=TRUE` |
| 429 | `RATE_LIMITED` | 레이트리밋 초과 |
| 500 | `INTERNAL_ERROR` | 서버 오류 — 사유는 응답에 싣지 않는다 |

일반형 `FORBIDDEN`(403) · `NOT_FOUND`(404) · `CONFLICT`(409) 도 공통이지만, 도메인 코드(`VIDEO_NOT_FOUND` 등)가 있으면 **도메인 코드가 우선**한다.

### 에러 코드 형식

`code` 는 **enum 상수와 같은 이름 문자열**이다(`INVALID_PARAMETER`, `VIDEO_NOT_FOUND`). 숫자 코드(`C001` 계열)는 쓰지 않는다 — 원본 §15 가 "module-common 의 에러 enum 은 이 표와 1:1 로 구현"한다고 규정하고, 안드로이드가 이 문자열로 분기한다.

- 공통 코드는 `module-common` 의 `CommonErrorCode` 가 소유한다. 도메인 enum 에 같은 코드를 다시 만들지 않는다.
- 도메인 코드는 `coreapi.domain.<도메인>.<도메인>ErrorCode` 가 갖는다(예: `AuthErrorCode.INVALID_GOOGLE_TOKEN`).
- `code` 문자열은 **전역 유일**하다. 새 코드가 필요하면 원본 §15 표에 먼저 추가한다.

---

## 3.5 공통 객체 — 아티스트 요약

아티스트를 참조하는 모든 응답은 **같은 모양**을 쓴다. 필드를 상황마다 빼지 않는다.

```json
{
  "artistSeq": 3,
  "name": "카리나",
  "groupName": "aespa",
  "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `artistSeq` | number | 아티스트 식별자 |
| `name` | string | `artists.user_seq → users.nickname` |
| `groupName` | string \| **null** | `artists.group_seq → artist_groups.group_name`. **`group_seq` 가 NULL 허용**이라 솔로 아티스트는 `null` |
| `profileImageUrl` | string \| null | `users.profile_image_key` → 공개 CDN URL. 미설정이면 `null` |

**왜 통일하나.** 원본은 화면마다 `groupName`·`profileImageUrl` 을 빼고 넣었지만(§4.6·§5.5·§7.1·§7.2 에서 제거), 그러면 클라이언트가 **같은 개념에 대해 응답 타입을 여러 개** 들고 있어야 한다. 빠진 필드는 대부분 `null` 하나 크기이므로 payload 이득보다 타입 단일화 이득이 크다.

적용 대상: §4.6 `subscribedArtists[]` · §4.7 카드 항목 · §5.1 랭킹 · §5.2 검색(자동완성 겸용) · §5.3 상세 · §5.10 전체 목록 · §6.3 두 타입의 `artist` · §11.1·§11.2 `artist` · §11.5·§11.6 `artist` · §11.9 `artist`.

**예외 — `groupName` 이 빠지는 곳** *(2026-09-22 정정)*: **§7.1·§7.2 챌린지**는 원본의 제거 결정(회의 확정)을 따르고 구현도 그렇다. **§5.5 공지**의 `artist` 도 구현이 빼고 있다(`ArtistNoticeResponse` — "명세 명시"). 이 세 곳을 통일 대상으로 적어 두었던 탓에 문서 예시와 실제 응답이 어긋나 있었다 — 예시를 응답에 맞췄다. `profileImageUrl` 은 세 곳 모두 실린다.

**`name` 은 언제나 `users.nickname` 이다.** §5.2 검색이 별칭 테이블을 훑더라도 응답에 싣는 이름은 바뀌지 않는다 — 별칭을 표시에 쓰면 같은 아티스트가 질의에 따라 다른 이름으로 보이고, 이 요약을 쓰는 위 전부가 흔들린다([`05-artist.md`](05-artist.md) §5.2).

> **원본 명세 반영 대기** — 이 통일은 원본의 개별 제거 결정(§4.6 R2, §5.5, §7.1·§7.2 회의 확정)을 덮는다. 팀 확인이 필요하다.

각 API 문서에서는 이 객체를 **`artist` 요약**이라고 부르고 필드 표를 반복하지 않는다.

---

## 4. 공통 비노출 규칙 (원본 §1.8)

조회 API 전부에 적용된다. 각 API 문서에서 반복하지 않는다.

- `users.is_deleted=TRUE` 사용자가 소유한 참여 영상·모티프 결과물·댓글은 노출하지 않는다. 루트 댓글 작성자가 삭제 사용자면 **답글 스레드 전체**를 감춘다.
- 삭제된 챌린지에 속한 참여 영상은 자체 `is_deleted=FALSE` 여도 노출하지 않는다.
- `PRIVATE` 참여 영상은 작성자 본인 외에는 **존재 자체를 숨긴다**(타인 조회 시 404).
- 위 비노출과 `commentCount`·`participantCount` 등 카운터의 표시 불일치는 감수한다(원본 §1.8 회의 확정).

---

## 5. 미디어 URL 등급 (원본 §1.7)

| 등급 | 대상 | 형태 |
|---|---|---|
| 공개 | 챌린지 원본, 에셋 미리보기, 프로필, 공지 이미지, 숍 배너 | CloudFront 일반 URL |
| 보호 | 모티프 결과물, **참여 영상 전량**(`visibility` 무관), 게시·저장 전 합성 결과 프리뷰, 편집본(`private/edited/`) | CloudFront **프리픽스 서명**(만료 **2시간**) — 단일 영상 파일은 URL 하나짜리 서명(10분), 이미지도 URL 하나짜리 서명(6시간) |

> **원본 명세 반영 대기** — 원본 §1.7 은 보호 등급 만료를 "기본 10분" 한 값으로 적었다. 자동재생이 들어오면서 재생목록 서명만 2시간으로 올렸고([ADR-0017](../../adr/0017-playlist-signature-lifetime.md)), 그에 따라 §8.9 `PUBLIC → PRIVATE`·§8.10 삭제의 "최대 지연" 문장도 함께 움직인다. 팀 확인이 필요하다.

**참여 영상은 `PUBLIC` 이어도 보호 등급이다** *(2026-09-22 정정)*. [ADR-0013](../../adr/0013-signed-only-media.md) 이 `public/` 공개 사본 트리를 폐기하면서 산출물이 `private/` 한 곳에만 남았다 — 표에 "PUBLIC 참여 영상"을 공개 줄에 적어 두었던 탓에, 그 값으로 등급을 가르면 `PUBLIC` 영상에 무서명 URL 이 나가 403 이 된다. **등급은 키의 최상위 프리픽스 하나로만 판정한다.**

### 영상은 HLS 다

`videoUrl` 은 단일 MP4 가 아니라 **마스터 재생목록(`master.m3u8`)** 주소다(ADR-0010 D2). 플레이어가 그것을 읽고 화질별 재생목록·조각을 **추가로 요청**한다.

```
master.m3u8                      ← videoUrl 이 가리키는 것
└── 720/index.m3u8 → 720/data.m4s
└── 480/index.m3u8 → 480/data.m4s
```

재생되는 것만 HLS 다 — **참여 영상 · 모티프 결과 · 챌린지 원본**. 사용자 원본 업로드(`raw_video_key`)와 편집본 소스는 워커의 입력일 뿐이라 MP4 다.

### 보호 등급은 프리픽스 서명이고, 파라미터는 클라이언트가 붙인다 *(ADR-0012)*

**재생목록에만 서명하면 그 안에서 참조하는 조각 요청에 서명이 따라붙지 않아 403 이 난다.** 상대 경로는 쿼리스트링을 상속하지 않기 때문이다. 그래서 **영상 하나의 프리픽스 전체(`private/{kind}/{jobUuid}/*`)에 통하는 서명을 한 벌 발급**하고, 클라이언트(OkHttp 인터셉터)가 그 파라미터를 모든 미디어 요청에 붙인다.

```
videoUrl = https://{cdn}/private/motif/{jobUuid}/master.m3u8?Policy=…&Signature=…&Key-Pair-Id=…
           ↑ 재생목록·조각 전부 CDN 직통. 클라이언트가 자식 요청에 같은 파라미터를 붙인다
```

재생 중 core-api 요청은 **0회**이고 엣지 캐시가 그대로 산다 — 서명 파라미터는 CloudFront 예약값이라 캐시 키에 들어가지 않는다.

**만료(2시간) 복구는 재발급 엔드포인트다.**

```
POST /api/v1/media/{jobUuid}/signature   → { "query": "Policy=…&Signature=…&Key-Pair-Id=…", "expiresDttm": "…" }
```

조각이 403 이면 인터셉터가 이걸 불러 새 `query` 로 재시도한다. **여기가 인가 지점**이라 환불·정지·비공개 전환이 다음 갱신에서 반영된다.

- **signed 쿠키를 쓰지 않는다.** 쿠키는 도메인당 한 벌이라 여러 사람의 영상이 섞인 피드에서 영상 단위 권한을 낼 수 없다.
- `thumb.jpg` 처럼 **그 자체로 끝나는 단일 파일**은 URL 하나짜리 서명(canned)을 바로 낸다 — 만료도 6시간으로 따로다.
- 공개 등급은 서명이 없으므로 영향이 없다.
- 구 방식(core-api 가 재생목록을 다시 써서 내리던 `GET /api/v1/media/{jobUuid}/…`)은 **응답에서 더 이상 가리키지 않지만 엔드포인트는 남아 있다** — 안드로이드 전환 기간의 안전망이다.
- **S3 presigned 는 재생에 쓰지 않는다.** 서명 쿼리스트링이 오리진(S3)까지 전달돼야 해서 CDN 캐시 적중률을 0% 로 만든다. CloudFront 서명은 엣지에서 소비되므로 캐시가 유지된다(ADR-0010 D5). presigned 는 **업로드(PUT)** 전용이다.

각 API 문서의 보호 등급 URL 예시는 **서명 파라미터를 붙여 적는다** *(2026-09-22 정리 — 이전에는 "쿼리스트링을 생략한다" 였다)*. 프리픽스 서명이면 `?Policy=…&Signature=…&Key-Pair-Id=…`, 단일 파일 서명이면 `?Expires=…&Signature=…&Key-Pair-Id=…` 다. 값 자체는 자리표시자이고 **있다는 사실이 계약**이라 생략하면 안 된다 — 생략된 예시를 보고 파라미터 없이 요청하면 403 이고, 그게 실제로 §8.1·§8.2 예시에서 일어났다.

**키가 `private/` 로 시작하면 `visibility` 와 무관하게 언제나 서명이 붙는다.** 그래서 `PUBLIC` 참여 영상의 썸네일 예시에도 파라미터가 있다(§4.1·§4.8·§4.9).

### 산출물 키 규약

| 대상 | 원본(입력) | 산출물 | 서명 |
|---|---|---|---|
| 합성 결과 (참여 영상·모티프 결과) | `raw/{uuid}.mp4` | `private/{challenge\|motif}/{jobUuid}/` | **필요** |
| 챌린지 원본 | `challenges/{uuid}.mp4` (어드민 명세 §1.4) | `challenges/{uuid}/` | 불필요 |

**최상위 프리픽스가 곧 보호 등급이다.** CloudFront 의 "Restrict viewer access" 는 캐시 동작 단위이고 캐시 동작은 path pattern 으로 고르므로, 보호 여부가 경로 최상단에 드러나야 동작 하나(`private/*`)로 가른다.

**합성 결과는 게시 여부와 무관하게 `private/` 한 곳에 머문다**(2026-09-19 — [ADR-0013](../../adr/0013-signed-only-media.md)). PUBLIC 게시 시 `public/challenge/` 로 복사하던 규약은 폐기했다. `visibility` 는 앱 내 공개 범위만 뜻하고, 전송 등급은 서명이 담당한다.

산출물 프리픽스 아래에 `master.m3u8` · `thumb.jpg` · 화질별 디렉터리가 깔린다. DB 에는 **마스터 재생목록 경로 하나만** 저장한다(ADR-0010 D4).

**seq 가 아니라 uuid 다** — 공개 CDN 경로에 순차 번호를 쓰면 열거가 가능해 콘텐츠의 존재와 개수가 드러난다. 이 디렉터리의 예시 URL 도 전부 uuid 를 쓴다.

업로드는 전부 S3 presigned PUT 직행이다 — 영상·이미지 바이트는 API 서버를 통과하지 않는다.

### 만료 재시도 규약 *(ADR-0012 로 확정)*

서명 만료(2시간)가 **재생 중에** 올 수 있다. 조각 요청이 403 이면 플레이어는 그냥 멈춘다.

- **복구는 `POST /api/v1/media/{jobUuid}/signature`** 다. 인터셉터가 403 을 잡아 새 `query` 를 받아 붙이고 재시도한다. 재생목록을 다시 받는 것은 복구가 되지 않는다 — 재생목록도 CDN 에서 오고 같은 만료가 걸려 있다.
- **자동재생 목록에서는 403 을 기다리지 않는다** *(2026-09-23 — [ADR-0017](../../adr/0017-playlist-signature-lifetime.md))*. 서명 범위가 영상 하나라 재발급도 영상당 1회이고, 자동재생에서는 그 왕복이 카드마다 보이는 스톨이 된다. 만료를 목록 수명(2시간)까지 늘려 둔 것이 1차 방어이고, 응답의 `expiresDttm` 은 **서명에 실제로 박힌 값**이라 그보다 앞서 미리 갱신하면 스톨이 없다.
- **미디어 전용 OkHttpClient 를 쓴다.** API 호출과 같은 인스턴스를 쓰면 서명 파라미터가 우리 API 요청에도 붙는다.
- 남은 것은 **재시도 횟수**뿐이다 — 갱신 직후에도 403 이면 그 영상은 권한이 없는 것이므로 무한 재시도하지 않는다.
