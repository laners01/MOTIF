# §3 온보딩 (ONBOARDING) — 상세 명세

공통 규약(봉투·에러 코드·시각 포맷·공통 실패 분기)은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §3 · DB 명세서 v0.15 §9·§10.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 3.1 | GET | `/api/v1/artists/ranking` | 필요 | §5.1 과 같은 API — [`05-artist.md`](05-artist.md) 참조 |
| 3.2 | GET | `/api/v1/artists/search` | 필요 | §5.2 와 같은 API — 〃 |
| 3.3 | POST | `/api/v1/onboarding` | 필요 | |

§3.1·§3.2 는 별도 엔드포인트가 아니라 아티스트 API 를 온보딩 화면에서 그대로 호출하는 것이다. 온보딩에서 신규 구현하는 API 는 §3.3 하나뿐이다.

---

## 3.3 POST /api/v1/onboarding

신규 가입자가 **아티스트 구독과 닉네임을 한 번에 확정**한다.

화면은 2단계(아티스트 선택 → 닉네임 입력)지만 **제출은 마지막 1회**이고, 서버는 **하나의 원자 트랜잭션**으로 처리한다 — 구독만 되고 닉네임이 안 바뀌는 중간 상태가 생기면 "온보딩 완료" 판정(구독 1건 이상 존재)이 사실과 어긋난다.

온보딩 화면을 띄울지는 **§2.1 로그인 응답의 `isNewUser`** 로만 판정한다. 이 API 는 그 판정에 관여하지 않는다.

### 요청

```http
POST /api/v1/onboarding
Authorization: Bearer {accessToken}
Content-Type: application/json; charset=utf-8
```

```json
{
  "artistSeqs": [3, 7, 12],
  "nickname": "모티프러버"
}
```

| 필드 | 타입 | 필수 | 제약 |
|---|---|:---:|---|
| `artistSeqs` | number[] | Y | **최소 1개.** 전부 비삭제 아티스트여야 한다. 중복은 서버가 제거한다 |
| `nickname` | string | Y | 영문·한글·**숫자**·`.`·`_`, **2~20자**. 공백·기타 기호 불가. **중복 검사 없음** |

닉네임 중복을 허용하는 것은 회의 확정 사항이다(C1). 표시 이름일 뿐 식별자가 아니라서 `users.nickname` 에 UNIQUE 가 없다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "subscribedCount": 3,
    "nickname": "모티프러버"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `subscribedCount` | number | 실제로 생성된 구독 수. 요청에 중복이 있었다면 **중복 제거 후의 개수** |
| `nickname` | string | 확정된 닉네임 (요청 값 그대로) |

### 서버 처리 순서 (한 트랜잭션)

1. 요청자에게 구독이 **1건이라도 있으면** → 409 `ALREADY_ONBOARDED`
2. `artistSeqs` 중복 제거
3. 해당 아티스트를 조회해 **하나라도 없거나 `is_deleted=TRUE` 면** → 404 `ARTIST_NOT_FOUND` (아무것도 저장하지 않음)
4. `subscriptions` 일괄 INSERT — 복합 PK `(user_seq, artist_seq)`
5. `artists.subscriber_count` 를 **원자적 UPDATE** 로 증가 (`count = count + 1`)
6. `users.nickname` 갱신

**전부 아니면 전무**다. 3번에서 걸리면 닉네임도 바뀌지 않는다.

5번을 읽고-더하고-쓰기로 하면 여러 사용자가 동시에 구독할 때 증가분이 유실된다. 쿼리 한 방으로 더한다.

### 실패 응답

공통 분기(400 형식 오류, 401 미인증, 429, 500)는 [`README.md`](README.md) 참조.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | `artistSeqs` 에 미존재·삭제 아티스트가 하나라도 포함 |
| 409 | `ALREADY_ONBOARDED` | 이미 온보딩을 마친(구독 1건 이상) 사용자의 재호출 |

**404 — `ARTIST_NOT_FOUND`**

```json
{
  "result": "FAIL",
  "code": "ARTIST_NOT_FOUND",
  "message": "존재하지 않는 아티스트입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**409 — `ALREADY_ONBOARDED`**

```json
{
  "result": "FAIL",
  "code": "ALREADY_ONBOARDED",
  "message": "이미 온보딩을 완료한 계정입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**400 — `INVALID_PARAMETER`** (빈 배열·닉네임 형식 위반)

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [
    { "field": "artistSeqs", "message": "아티스트를 1명 이상 선택해야 합니다" },
    { "field": "nickname", "message": "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다." }
  ],
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 클라이언트 처리 규칙

- **온보딩 완료(이 API 200) 전에는 홈 진입을 막는다.** 온보딩 도중 이탈 후 재로그인하면 `isNewUser=false` 라 서버가 온보딩을 다시 띄워주지 않는다(원본 §2.1 한계·가드). 뒤로가기는 앱 종료 확인으로 처리한다.
- 409 를 받으면 이미 완료된 계정이므로 **홈으로 보낸다** — 재시도하지 않는다.
- 404 는 목록을 받은 뒤 아티스트가 삭제된 경우다. 목록을 새로 받아 다시 고르게 한다.

### 이 문서에서 정한 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| `artistSeqs` 중복 | **서버가 제거**, `subscribedCount` 는 distinct 개수 | 거르지 않으면 복합 PK 충돌로 500 이 난다. 클라 실수를 400 으로 되돌려줄 실익이 없다 |
| `artistSeqs` 개수 상한 | **없음** | 명세에 없고 아티스트 총수가 수백 규모다 |
| 닉네임 정규식 | `^[a-zA-Z0-9가-힣._]{2,20}$` | 명세 열거(영문·한글·`.`·`_`)에 **숫자를 추가**(2026-09-12 팀 확정). 아래 참조 |
| `subscriber_count` 갱신 | 원자적 UPDATE | 동시 구독에서 갱신 유실 방지. `ck_artists_subscriber_count(>= 0)` 존재 |
| 계정 상태 검사 | 하지 않음 | 인증 통과로 충분하다. 전 API 공통 규칙을 따른다 |

### 원본 명세 반영 대기

**닉네임에 숫자를 허용한다 (2026-09-12 팀 확정).** 원본 §3.3·§4.2 는 "영문·한글·`.`·`_`" 만 열거해 숫자가 빠져 있는데, 그대로 두면 §2.1 이 가입 시 발급하는 임시 닉네임 `user_{seq}`(예: `user_1`)가 **서버 자신의 검증을 통과하지 못하는 모순**이 생긴다. 원본 명세서의 §3.3·§4.2 문구와 §1.2 검증 오류 예시 메시지에 숫자를 추가해야 한다.

### 미결

- **온보딩 완료 판정이 "구독 1건 이상"** 이라, 나중에 구독을 모두 해제한 사용자는 이 API 를 다시 호출할 수 있다. 원본 §16 #16(온보딩 완료 플래그 컬럼) 이 도입되면 정리된다.
