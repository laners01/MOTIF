# §2 인증 (AUTH) — 상세 명세

공통 규약(봉투·에러 코드·시각 포맷·공통 실패 분기)은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §2 · DB 명세서 v0.15 §6.

| # | Method | Path | 인증 | 상태 |
|---|---|---|---|---|
| 2.1 | POST | `/api/v1/auth/login/google` | 🔓 | 구현됨 (상세 명세 미작성 — Swagger 참조) |
| 2.2 | POST | `/api/v1/auth/refresh` | 🔓 | **이 문서** |
| 2.3 | POST | `/api/v1/auth/logout` | 필요 | 예정 |

---

## 2.2 POST /api/v1/auth/refresh 🔓

access token 이 만료되면 refresh token 으로 새 토큰 쌍을 받는다.

**refresh token 은 1회용이다.** 재발급할 때마다 기존 토큰을 폐기하고 새 토큰을 발급한다(rotation). 이미 폐기된 토큰이 다시 들어오면 **탈취 신호**로 보고 거부한다.

인증이 필요 없다(🔓) — access token 이 만료된 상태에서 호출하는 API 이기 때문이다. `Authorization` 헤더를 보내도 무시한다.

### 요청

```http
POST /api/v1/auth/refresh
Content-Type: application/json; charset=utf-8
```

```json
{
  "refreshToken": "s7Fq2vXk9pLmNbVcZxAsDfGhJkLqWeRtYuIoP1234567"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|:---:|---|
| `refreshToken` | string | Y | 공백 불가. §2.1·§2.2 응답으로 받은 값을 **가공 없이 그대로** 보낸다 |

> `refreshToken` 은 **불투명 문자열이며 JWT 가 아니다**(Base64URL 랜덤 32바이트). 서버는 원문을 저장하지 않고 SHA-256 해시(`refresh_tokens.token_hash`)만 갖는다. 클라이언트는 값을 해석·분해하지 않는다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIifQ.yyyyy",
    "refreshToken": "Zr3Nk8dQpWxE2uYtA5bHvCmLs9fJgKo0987654321",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "isNewUser": false,
    "user": {
      "userSeq": 1,
      "nickname": "모티프러버",
      "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
    }
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | string | 새 서비스 access token (JWT, HS256). `Authorization: Bearer {값}` |
| `refreshToken` | string | **새 refresh token.** 요청에 쓴 값은 이 응답 시점에 이미 폐기됐다 |
| `tokenType` | string | `Bearer` 고정 |
| `expiresIn` | number | access token 유효 기간(초). 현재 3600 |
| `isNewUser` | boolean | **재발급에서는 항상 `false`** — 아래 (기본안) 참조 |
| `user.userSeq` | number | 사용자 PK |
| `user.nickname` | string | 현재 닉네임 |
| `user.profileImageUrl` | string \| null | 공개 등급 CloudFront 일반 URL(§1.7). 미설정이면 `null`. 예시의 도메인은 placeholder |

- 응답 형태는 §2.1 로그인과 **완전히 동일**하다(원본 §2.2 "2.1 과 동일한 토큰 쌍 + user 요약"). 안드로이드는 같은 DTO·파서를 쓰면 된다.
- **(기본안)** `isNewUser` 는 재발급에서 의미가 없지만 필드를 빼지 않고 `false` 로 내린다. 필드를 빼면 클라이언트가 로그인/재발급용 응답 타입을 둘로 나눠야 한다. 온보딩 판정은 로그인(§2.1) 응답으로만 한다.
- 클라이언트는 **받은 쌍으로 저장소를 교체**한다. 이전 refresh 는 즉시 무효다.

### 서버 처리 순서

한 트랜잭션에서 아래를 수행한다(DB 명세서 v0.15 §6 서비스 규칙).

1. 받은 원문을 SHA-256 hex 로 변환해 `refresh_tokens.token_hash` 로 조회하고 **그 행을 잠근다**(`SELECT ... FOR UPDATE`).
2. 행이 없으면 401.
3. `revoked_dttm IS NOT NULL` 이면 **재사용 감지** → 401.
4. `expires_dttm <= now` 면 만료 → 401.
5. 계정 상태를 재검증한다 — `users.is_deleted=TRUE` → 403 `ACCOUNT_DELETED`, `user_accounts.account_status='SUSPENDED'` → 403 `ACCOUNT_SUSPENDED`(원본 §1.5 "로그인·리프레시 시점에 모두 검증").
6. 기존 행에 `last_used_dttm`·`revoked_dttm` 을 기록한다.
7. 새 `refresh_tokens` 행을 INSERT 하고, 새 access token 을 발급해 반환한다.

동시성:

- 같은 refresh token 으로 두 요청이 동시에 들어오면 1번의 행 잠금이 둘을 직렬화한다. 먼저 들어온 쪽만 성공하고, 뒤따른 쪽은 3번(폐기됨)에 걸려 401 이다.
- **다른 기기의 토큰은 영향을 받지 않는다.** `refresh_tokens` 는 기기별 행이라 rotation 은 그 행 하나만 교체한다.

### 실패 응답

공통 분기(400 `INVALID_PARAMETER` 형식 오류, 429, 500)는 [`README.md`](README.md) 참조. 도메인 분기는 아래 둘이다.

| HTTP | code | 조건 |
|---|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | 미존재 · 만료 · 폐기됨 · **재사용 감지**(rotation 위반) |
| 403 | `ACCOUNT_SUSPENDED` / `ACCOUNT_DELETED` | 재발급 시점 계정 상태 재검증 실패 |

**401 — `INVALID_REFRESH_TOKEN`**

```json
{
  "result": "FAIL",
  "code": "INVALID_REFRESH_TOKEN",
  "message": "다시 로그인해 주세요.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

> **네 가지 조건이 모두 같은 응답이다.** 어느 쪽인지 알려주면 공격자가 "이 토큰은 존재하지만 만료됐다" 같은 정보를 얻는다. 사유는 서버 로그에만 남긴다.

**403 — `ACCOUNT_SUSPENDED`**

```json
{
  "result": "FAIL",
  "code": "ACCOUNT_SUSPENDED",
  "message": "정지된 계정입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**403 — `ACCOUNT_DELETED`**

```json
{
  "result": "FAIL",
  "code": "ACCOUNT_DELETED",
  "message": "삭제된 계정입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**400 — `INVALID_PARAMETER`** (`refreshToken` 누락·빈 문자열)

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [
    { "field": "refreshToken", "message": "refreshToken은 필수입니다" }
  ],
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 클라이언트 처리 규칙 (원본 §2.2)

- access token 만료(401 `UNAUTHORIZED`)를 감지하면 보관 중인 refresh 로 이 API 를 호출한다.
- **재발급은 단일 큐(mutex)로 직렬화한다.** 동시 요청 여러 개가 각자 재발급을 시도하면 하나만 성공하고 나머지는 401 을 받아 불필요한 로그아웃이 일어난다.
- 이 API 가 401 이면 **로컬 토큰을 전부 폐기하고 로그인 화면으로** 보낸다. 재시도하지 않는다.
- 403 은 계정 문제이므로 재발급·재로그인 모두 불가하다. 안내 문구를 띄운다.

### 이 문서에서 정한 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| `isNewUser` 포함 | 포함하고 `false` 고정 | 원본이 "§2.1 과 동일한 토큰 쌍"이라 규정. 클라이언트 DTO 단일화 |
| 재사용 감지 시 폐기 범위 | **해당 요청만 401** | 원본에 "그 사용자의 모든 활성 토큰 폐기"가 없다. DB 명세서 §6 은 정지·삭제 시에만 전량 revoke 를 규정한다. OAuth 2.1 BCP 의 전량 폐기는 미결 안건으로 남긴다 |
| `last_used_dttm` 의미 | 재발급에 성공적으로 사용된 시각 | DB 명세서 §6 "마지막 재발급 사용 시각" |
| 만료 판정 경계 | `expires_dttm <= now` 면 만료 | 경계값은 만료로 본다(보수적) |

### 미결

- **폐기·만료 행의 하드 삭제 주기** — DB 명세서 §6 은 "운영 정책의 보존 기간에 따라 주기적 삭제"라고만 한다. 배치는 별도 티켓이며, 쌓이는 동안에도 `uq_refresh_tokens_hash` 때문에 정확성 문제는 없다.
- **refresh token TTL 14일**은 명세에 값이 없어 `application.yml` 의 기본안이다(`motif.auth.jwt.refresh-token-ttl`).
