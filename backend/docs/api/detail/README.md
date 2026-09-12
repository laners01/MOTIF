# MOTIF API 상세 명세

원본은 [`docs/api/MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md)다. **원본은 팀이 관리하며 여기서 고치지 않는다.**

이 디렉터리는 원본을 **구현 가능한 수준까지 내린 문서**다. 원본이 "2.1 과 동일한 토큰 쌍 + `user` 요약" 처럼 참조로 적은 부분을, 중복을 감수하고 **완전한 JSON 으로 다시 쓴다** — 안드로이드가 화면을 만들 때 문서 두 곳을 오가지 않게 하는 것이 목적이다.

- 원본과 어긋나는 내용을 쓰지 않는다. 원본이 비워둔 지점만 채우고, 채운 것은 **(기본안)** 으로 표시한다.
- 원본 개정으로 계약이 바뀌면 이 문서를 따라 고친다. 판단이 갈리면 **원본이 우선**이다.
- 각 문서의 JSON 예시는 해당 API 의 Swagger `@ExampleObject` 와 **같은 값**을 쓴다. 문서와 Swagger 가 다른 값을 보여주면 둘 다 신뢰를 잃는다.

## 문서 구성

| 문서 | 원본 절 | 상태 |
|---|---|---|
| 이 문서 | §1, §15 | 공통 규약·에러 코드 |
| [`02-auth.md`](02-auth.md) | §2 | §2.2 토큰 재발급 · §2.3 로그아웃 |
| [`03-onboarding.md`](03-onboarding.md) | §3 | §3.3 온보딩 완료 |

API 하나가 Jira 이슈 하나·브랜치 하나다. 절 단위 파일에 해당 API 절만 추가하는 방식으로 늘려 간다.

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
    "nextCursor": "eyJrIjoiODgiLCJ2IjoxfQ",
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
| 공개 | 챌린지 원본, PUBLIC 참여 영상, 에셋 미리보기, 썸네일, 프로필, 공지 이미지, 숍 배너 | CloudFront 일반 URL |
| 보호 | 모티프 결과물, 본인 PRIVATE 참여 영상, 게시·저장 전 합성 결과 프리뷰 | CloudFront signed URL (만료 10분) |

업로드는 전부 S3 presigned PUT 직행이다 — 영상·이미지 바이트는 API 서버를 통과하지 않는다.
