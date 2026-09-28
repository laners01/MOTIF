# §14 알림 (NOTIFICATION) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §14 · DB 명세서(`motif_notif` 스키마) `device_tokens`.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 14.1 | POST | `/api/v1/notifications/device-tokens` | 필요 | FCM 토큰 등록·재할당 |
| 14.2 | DELETE | `/api/v1/notifications/device-tokens` | 필요 | 현재 기기 토큰 비활성 |
| 14.3 | — | (서버 API 없음) | — | 알림 목록·읽음·삭제·설정은 **Android 로컬** |

## 서버는 알림을 저장하지 않는다

**`notifications` 테이블도, 알림 목록 API 도 없다.** 서버는 **FCM 발송만** 하고, 수신한 알림의 목록·읽음·삭제·정렬은 전부 Android 로컬 저장소(Room 등)가 맡는다.

그래서 이런 성질이 따라온다.

- **API 로 알림을 재동기화할 수 없다.** 재설치·앱 데이터 삭제·기기 변경 시 알림 이력과 설정이 초기화된다
- **FCM 자체를 수신하지 못한 알림은 복구 불가**다
- 레드닷(미읽음 배지)도 클라이언트가 로컬 데이터로 계산한다

## 라우팅

`/api/v1/notifications/**` 는 알림 서비스(`app-notification`)가 담당한다. 나머지는 전부 Core API 다.

> **확인 필요**: 원본 §1.1 은 "SCG 가 알림 서비스로 라우팅한다"고 적었지만 **SCG 는 ADR-0009 로 제거**됐다. Traefik 이 이 경로를 `app-notification`(:8082)으로 보내도록 인프라 설정이 필요한지, 아니면 Core API 가 받아 처리할지 정해야 한다.

---

## 14.1 POST /api/v1/notifications/device-tokens — 토큰 등록·재할당

로그인 직후와 FCM 토큰 갱신 시 호출한다.

### 요청

```json
{ "fcmToken": "c9yT...FCM-registration-token...", "platform": "ANDROID" }
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `fcmToken` | Y | FCM 등록 토큰. **전역 UNIQUE** (`uq_device_tokens_fcm_token`) |
| `platform` | Y | `ANDROID` \| `IOS` \| `WEB` (DB CHECK). 현재는 `ANDROID` |

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

### 서버 처리 — upsert + 재할당

`fcm_token` 이 전역 UNIQUE 이므로 **행 하나가 기기 하나**다.

| 상황 | 동작 |
|---|---|
| 처음 보는 토큰 | 새 행 생성, `is_active=TRUE` |
| 같은 사용자의 기존 토큰 | `is_active=TRUE` 로 갱신(멱등) |
| **다른 사용자가 쓰던 토큰** | **현재 사용자로 재할당** + `is_active=TRUE` |

재할당이 필요한 이유는 **기기 하나를 여러 계정이 쓰는 경우** 때문이다. A 가 로그아웃하고 B 가 로그인하면 같은 FCM 토큰이 B 의 것이 돼야 한다 — 그러지 않으면 B 의 알림이 A 에게 간다.

### 실패 응답

**도메인 분기 없음.**

---

## 14.2 DELETE /api/v1/notifications/device-tokens — 토큰 비활성

로그아웃할 때 호출한다.

### 요청

```json
{ "fcmToken": "c9yT...FCM-registration-token..." }
```

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

**멱등하다** — 등록되지 않은 토큰을 보내도 200 이다.

`is_active=FALSE` 로 바꿀 뿐 행을 지우지 않는다 — 같은 기기로 다시 로그인하면 §14.1 이 되살린다.

### 실패 응답

**도메인 분기 없음.**

### 로그아웃은 2콜이다

**§2.3 로그아웃이 이 API 를 대신 호출해 주지 않는다.** 코어 API 가 알림 서비스를 HTTP 로 부르지 않는다는 원칙 때문이며, 클라이언트가 순서대로 두 번 호출해야 한다.

```
① POST /api/v1/auth/logout                    refresh token 폐기
② DELETE /api/v1/notifications/device-tokens  푸시 중단
③ 로컬 토큰 폐기
```

**②를 빠뜨리면 로그아웃한 기기로 푸시가 계속 간다.**

---

## 14.3 알림 발송·로컬 저장 규약

### 전부 data-only 메시지다

모든 FCM 은 **data-only** 로 발송한다. 클라이언트가 `onMessageReceived` 에서 **로컬 설정을 확인한 뒤** 시스템 알림 표시 여부를 결정한다.

notification 페이로드를 쓰면 앱이 백그라운드일 때 시스템이 바로 알림을 띄워 **사용자의 OFF 설정을 무시하게 되기 때문**이다.

### 페이로드 골격

```json
{
  "type": "COMPOSITE_DONE",
  "title": "모티프가 완성됐어요",
  "body": "지금 확인해 보세요",
  "sentDttm": "2026-09-10T00:00:00.000Z",
  "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

마지막 키는 **대상 식별자**이며 `type` 에 따라 달라진다 — `challengeSeq` \| `videoSeq` \| `noticeSeq` \| `jobUuid`.

합성 완료·실패 알림(MOTIF·CHALLENGE)은 **`jobUuid`** 를 싣고, 앱은 진입 시 §13.3 을 1회 조회한다.

### 알림 종류

| `type` | 발송 시점 | 수신자 | 대상 키 | 추가 키 |
|---|---|---|---|---|
| `NEW_CHALLENGE` | 아티스트 신규 챌린지 등록 | 그 아티스트 구독자 전원 | `challengeSeq` | — |
| `NEW_NOTICE` | 아티스트 신규 공지 등록 | 그 아티스트 구독자 전원 | `noticeSeq` | — |
| `NEW_COMMENT` | 내 게시물에 댓글 | 게시물 주인 | `challengeSeq` \| `videoSeq` \| `noticeSeq` | `commentSeq` |
| `NEW_REPLY` | 내 댓글에 답글 | 원댓글 작성자 | `challengeSeq` \| `videoSeq` \| `noticeSeq` | `commentSeq`, `parentCommentSeq` |
| `COMPOSITE_DONE` | 합성 완료 | 잡 주인 | `jobUuid` | `jobType` |
| `COMPOSITE_FAILED` | 합성 실패 | 잡 주인 | `jobUuid` | `jobType`, `failCode` |

- 댓글·답글의 대상 키는 댓글이 달린 게시물 종류를 따른다. `commentSeq` 는 스레드 안 해당 댓글 위치로 이동할 때 쓴다
- `NEW_REPLY` 의 `commentSeq` 는 답글 ID, `parentCommentSeq` 는 원댓글 ID
- `jobType` 은 `MOTIF` \| `CHALLENGE`
- 수신자의 활성 기기가 여럿이면 전부 발송한다
- data 메시지라 **모든 값이 문자열**이다. `challengeSeq` 등 숫자도 문자열로 온다
- 좋아요 알림은 없다

### 알림 설정도 로컬이다

SETTING-02(전체 / 아티스트 / 게시물 / 서비스 ON·OFF)는 **Android DataStore 등 로컬에만** 저장한다. **서버에 설정 테이블도 설정 API 도 없다.**

서버는 사용자별 ON/OFF 를 알지 못하므로 **정책대로 전부 발송**하고, 최종 표시 여부는 클라이언트가 거른다.

### 본인 행위는 발송하지 않는다

내 게시물에 내가 댓글을 달거나 좋아요를 눌러도 나에게 알림이 가지 않는다(§9.3).
