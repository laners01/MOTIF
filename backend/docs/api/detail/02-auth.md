# §2 인증 (AUTH) — 상세 명세

공통 규약(봉투·에러 코드·시각 포맷·공통 실패 분기)은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §2 · DB 명세서 v0.15 §6.

| # | Method | Path | 인증 |
|---|---|---|---|
| 2.1 | POST | `/api/v1/auth/login/google` | 🔓 |
| 2.2 | POST | `/api/v1/auth/refresh` | 🔓 |
| 2.3 | POST | `/api/v1/auth/logout` | 필요 |

---

## 2.1 POST /api/v1/auth/login/google 🔓

구글 ID 토큰을 검증하고, **최초 로그인이면 회원을 자동 생성**한다(`users` + `user_accounts` + `user_oauth_accounts` 3행).

인증이 필요 없다(🔓).

### 요청

```http
POST /api/v1/auth/login/google
Content-Type: application/json; charset=utf-8
```

```json
{ "idToken": "google-id-token" }
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|:---:|---|
| `idToken` | string | Y | 구글 Sign-In 이 발급한 ID 토큰. 공백 불가 |

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIifQ.xxxxx",
    "refreshToken": "s7Fq2vXk9pLmNbVcZxAsDfGhJkLqWeRtYuIoP1234567",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "isNewUser": true,
    "user": { "userSeq": 1, "nickname": "user_1", "profileImageUrl": "https://cdn.motif.io/profiles/default.png" }
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | string | 서비스 access token (JWT, HS256). `Authorization: Bearer {값}` |
| `refreshToken` | string | 재발급용 **불투명 문자열**(JWT 아님). 해석하지 말고 그대로 보관·재전송한다 |
| `tokenType` | string | `Bearer` 고정 |
| `expiresIn` | number | access token 유효 기간(초). 현재 3600 |
| `isNewUser` | boolean | **이번 요청에서 `users` 행이 새로 생겼는지** — 온보딩 노출 판정의 유일한 근거 |
| `user.nickname` | string | 최초 가입은 임시값 `user_{userSeq}`. 확정은 §3.3 |
| `user.profileImageUrl` | string | 공개 CDN URL(§1.7). 미설정이면 기본 프로필 이미지 URL — 신규 가입은 항상 이 경우다 |

### 서버 처리 순서

1. 구글 JWKS 로 ID 토큰 서명·만료·`iss`·`aud`(우리 clientId)를 검증하고 `sub` 를 얻는다
2. `(GOOGLE, sub)` 로 `user_oauth_accounts` 를 조회한다
3. 없으면 **가입** — `users`(임시 닉네임) → `user_accounts`(ACTIVE) → `user_oauth_accounts` 3행 생성. `isNewUser=true`
4. 계정 상태를 검증한다 — 삭제·정지면 403(§1.5)
5. access token 발급 + `refresh_tokens` 새 행 발급(원문이 아니라 SHA-256 해시를 저장)

- 구글 서버 왕복은 **DB 트랜잭션 밖**에서 한다. 같은 구글 계정의 최초 로그인이 동시에 들어와 `uq_user_oauth_subject` 가 충돌하면, 새 트랜잭션에서 재조회해 기존 회원 경로로 흐른다.
- **로그인할 때마다 refresh token 행이 새로 쌓인다** — 기기별 세션이라 기존 행을 폐기하지 않는다.

### 온보딩 판정과 그 한계

`isNewUser` 가 유일한 근거다(회의 확정 — 구 `needsOnboarding` 필드는 삭제됐다).

**한계**: 온보딩 도중 이탈했다가 재로그인하면 `isNewUser=false` 라 서버는 온보딩을 다시 띄워주지 않는다. 그래서 **클라이언트가 §3.3 성공 전까지 홈 진입을 막아야 한다**(뒤로가기는 앱 종료 확인). 재설치 등으로 복구가 필요해지면 `users` 에 온보딩 완료 플래그를 추가하는 안으로 승격한다(원본 §16 #16).

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 401 | `INVALID_GOOGLE_TOKEN` | 구글 ID 토큰 검증 실패 — 만료·위조·`aud` 불일치·`iss` 불일치·`sub` 없음 |
| 403 | `ACCOUNT_SUSPENDED` / `ACCOUNT_DELETED` | 정지·삭제 계정의 로그인 시도 |

**401 — `INVALID_GOOGLE_TOKEN`**

```json
{
  "result": "FAIL",
  "code": "INVALID_GOOGLE_TOKEN",
  "message": "구글 인증에 실패했습니다. 다시 로그인해 주세요.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**400 — `INVALID_PARAMETER`** (`idToken` 누락·빈 문자열)

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [{ "field": "idToken", "message": "idToken은 필수입니다" }],
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 최초 닉네임 | `user_{userSeq}` | `users.nickname` 이 NOT NULL 이라 가입 시점에 값이 필요하다. 확정은 §3.3 |
| refresh token 형식 | **불투명 랜덤 32바이트**(Base64URL), JWT 아님 | `refresh_tokens` 가 원문 대신 해시만 갖는 구조라 어차피 DB 를 조회해야 한다. JWT 의 자기검증 이점이 없고 클레임 노출·키 회전 문제만 남는다 |
| refresh token TTL | 14일 | 원본에 값이 없다(`motif.auth.jwt.refresh-token-ttl`) |

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
| `user.profileImageUrl` | string | 공개 등급 CloudFront 일반 URL(§1.7). 미설정이면 기본 프로필 이미지 URL. 예시의 도메인은 placeholder |

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

---

## 2.3 POST /api/v1/auth/logout

현재 기기에서 로그아웃한다 — 보낸 refresh token 하나만 폐기하고, **다른 기기의 세션은 그대로 둔다**.

**인증이 필요하다.** §2.1·§2.2 와 달리 🔓 가 아니다.

### 요청

```http
POST /api/v1/auth/logout
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=utf-8
```

```json
{
  "refreshToken": "s7Fq2vXk9pLmNbVcZxAsDfGhJkLqWeRtYuIoP1234567"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|:---:|---|
| `refreshToken` | string | Y | 공백 불가. **이 기기가 보관 중인** 값을 그대로 보낸다 |

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 서버 처리 순서

1. access token 에서 인증된 `userSeq` 를 얻는다.
2. 받은 refresh token 을 SHA-256 hex 로 변환해 `refresh_tokens.token_hash` 로 조회한다.
3. **본인 소유이고 아직 폐기되지 않았으면** `revoked_dttm` 을 기록한다.
4. 그 외(미존재 · 이미 폐기 · 만료 · **타인 소유**)는 아무 일도 하지 않고 200 을 준다.

**멱등하다.** 로그아웃은 네트워크 실패로 재시도되거나 중복 호출되기 쉽고, 실패시켜서 얻을 것이 없다. 같은 요청을 몇 번 보내도 결과는 "그 토큰이 폐기된 상태"로 같다.

행을 잠그지 않는다 — §2.2 rotation 과 달리 동시 실행이 계약을 깨지 않는다(둘 다 같은 값으로 폐기할 뿐이다).

### 서버가 하지 않는 일

- **access token 은 무효화되지 않는다.** JWT 라 서버에 상태가 없어 만료(기본 1시간)까지 유효하다 — **클라이언트가 로컬에서 반드시 폐기**해야 한다. 만료 전 무효화를 하려면 Redis 블랙리스트 같은 상태 저장이 필요한데, 명세가 요구하지 않아 도입하지 않았다.
- **FCM 디바이스 토큰 비활성화(§14.2)를 하지 않는다.** 코어 API 가 알림 서비스를 HTTP 로 호출하지 않는다는 원칙(원본 §2.3 "클라 2콜") 때문이며, 클라이언트가 `DELETE /api/v1/notifications/device-tokens` 를 **따로** 호출해야 푸시가 멈춘다.
- **다른 기기의 토큰을 건드리지 않는다.** 전 기기 로그아웃은 이 API 가 아니다.

### 실패 응답

**도메인 분기가 없다.** 공통 분기만 적용된다 — [`README.md`](README.md) 참조.

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

**401 — `UNAUTHORIZED`** (access token 없음·만료·위조)

```json
{
  "result": "FAIL",
  "code": "UNAUTHORIZED",
  "message": "로그인이 필요합니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

> access token 이 만료돼 401 이 나면, 클라이언트는 §2.2 로 재발급받아 다시 로그아웃하거나 — 어차피 로그아웃이 목적이므로 — **로컬 토큰을 버리는 것으로 끝내도 된다**. 서버의 refresh 행은 만료(기본 14일)로 자연 소멸한다.

### 클라이언트 처리 순서

1. `POST /api/v1/auth/logout` (이 API)
2. `DELETE /api/v1/notifications/device-tokens` (§14.2 — 푸시 중단)
3. 로컬의 access·refresh token 폐기

2번을 빠뜨리면 **로그아웃한 기기로 푸시가 계속 간다.**

### 이 문서에서 정한 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 타인 소유 토큰을 보낸 경우 | 폐기하지 않고 **200** | 403·404 를 주면 "그 토큰이 존재한다"는 정보가 샌다. §2.2 의 사유 비노출과 같은 원칙 |
| 계정 정지·삭제 상태 | **검사하지 않음** | 정지된 계정도 로그아웃은 되는 편이 안전하다. 토큰을 없애는 요청을 막을 이유가 없다 |
| `refreshToken` 누락·빈 문자열 | 400 `INVALID_PARAMETER` | 무엇을 폐기할지 알 수 없으므로 멱등 대상이 아니다 |
| 이미 폐기된 토큰 | 200, **폐기 시각을 덮어쓰지 않음** | 최초 폐기 시각이 감사에 더 유용하다 |
