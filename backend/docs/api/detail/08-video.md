# §8 참여 영상 (VIDEO) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §8 · DB 명세서 v0.15 §15~§18.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 8.1 | GET | `/api/v1/challenges/{challengeSeq}/videos` | 필요 | 챌린지별 참여 영상 피드 |
| 8.2 | GET | `/api/v1/videos/{videoSeq}` | 필요 | **참여 영상 공통 항목의 정의** |
| 8.3 | POST · DELETE | `/api/v1/videos/{videoSeq}/like` | 필요 | **204 No Content** |
| 8.4 | — | (삭제) 좋아요 사용자 목록 | — | v0.8 에서 기능 제거 |
| 8.5 | POST | `/api/v1/videos/{videoSeq}/view` | 필요 | 조회 이벤트 |
| 8.6 | POST | `/api/v1/videos/{videoSeq}/hide` | 필요 | 관심 없음 |
| 8.7 | POST | `/api/v1/videos/{videoSeq}/report` | 필요 | 영상 신고 |
| 8.8 | POST | `/api/v1/videos` | 필요 | 게시(업로드 완료) |
| 8.9 | PATCH | `/api/v1/videos/{videoSeq}` | 필요 | 게시 정보 수정 |
| 8.10 | DELETE | `/api/v1/videos/{videoSeq}` | 필요 | 게시 영상 삭제 |

**`title` 이 없다** — `participation_videos.title` 컬럼이 삭제됐다(R3). 게시 시에도 받지 않는다.

---

## 8.2 GET /api/v1/videos/{videoSeq} — 참여 영상 단건

**이 응답이 "참여 영상 항목"의 정의다.** §8.1 피드가 같은 형태를 배열로 내려주고, 다른 화면(§6.1 HOT 등)은 경량 항목을 쓰다가 뷰어 진입 시 이 API 를 1회 호출해 오버레이 정보를 채운다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "videoSeq": 88,
    "challengeSeq": 11,
    "description": "재밌었다!",
    "videoUrl": "https://cdn.motif.io/results/challenge/uuid.mp4",
    "thumbnailUrl": "https://cdn.motif.io/videos/88.jpg",
    "durationMs": 15000,
    "author": {
      "userSeq": 5,
      "nickname": "팬123",
      "profileImageUrl": "https://cdn.motif.io/profiles/5.jpg"
    },
    "viewCount": 3400,
    "likeCount": 120,
    "commentCount": 15,
    "hasArtistLike": true,
    "hasArtistComment": false,
    "isLiked": false,
    "commentEnabled": true,
    "visibility": "PUBLIC",
    "isMine": false,
    "regDttm": "2026-09-01T10:00:00.000Z"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `videoUrl` | `PUBLIC` 이면 공개 CDN URL, **본인의 `PRIVATE` 이면 signed URL**(§1.7) |
| `hasArtistLike` · `hasArtistComment` | 해당 챌린지의 아티스트가 좋아요·댓글을 남겼는지. 화면 뱃지이자 §8.1 추천 정렬의 강가중 요소다 |
| `isLiked` | 요청자의 좋아요 여부 |
| `commentEnabled` | `false` 면 댓글이 **동결**된다 — 목록 조회까지 차단이다(§9) |
| `isMine` | `true` 면 클라이언트가 Bottom Sheet 에 수정·삭제를 노출한다 |
| `description` | 게시 시 입력한 문구. DB 상 NOT NULL 이라 빈 문자열일 수는 있어도 `null` 은 아니다 |

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 미존재 · 삭제 영상 · 삭제 사용자의 콘텐츠 · **타인의 `PRIVATE`** · 삭제된 챌린지 소속 |

```json
{
  "result": "FAIL",
  "code": "VIDEO_NOT_FOUND",
  "message": "존재하지 않는 영상입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**타인의 PRIVATE 영상은 403 이 아니라 404 다** — 권한 없음을 알려주면 "그 영상이 존재한다"는 사실이 드러난다. §1.8 의 존재 은닉 규칙이다.

---

## 8.1 GET /api/v1/challenges/{challengeSeq}/videos — 참여 영상 피드

**커서 페이지네이션**(§1.9). **추천 정렬**이며 서버 고정이다 — 좋아요·댓글·조회수에 `has_artist_like`·`has_artist_comment` 를 강하게 가중한다(아티스트가 반응한 영상을 위로).

### 요청

```http
GET /api/v1/challenges/{challengeSeq}/videos?cursor={opaque}&size=20
Authorization: Bearer {accessToken}
```

### 성공 응답 (200) — 커서 페이지

`data.list[]` 의 각 항목은 **§8.2 와 같은 형태**다.

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "videoSeq": 88,
        "challengeSeq": 11,
        "description": "재밌었다!",
        "videoUrl": "https://cdn.motif.io/results/challenge/uuid.mp4",
        "thumbnailUrl": "https://cdn.motif.io/videos/88.jpg",
        "durationMs": 15000,
        "author": { "userSeq": 5, "nickname": "팬123", "profileImageUrl": "https://cdn.motif.io/profiles/5.jpg" },
        "viewCount": 3400,
        "likeCount": 120,
        "commentCount": 15,
        "hasArtistLike": true,
        "hasArtistComment": false,
        "isLiked": false,
        "commentEnabled": true,
        "visibility": "PUBLIC",
        "isMine": false,
        "regDttm": "2026-09-01T10:00:00.000Z"
      }
    ],
    "prevCursor": null,
    "nextCursor": "eyJ2Ijo4OH0",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 목록에서 제외하는 것

- 타인의 `PRIVATE` 영상 · 삭제 영상 · 삭제 사용자의 영상 (§1.8)
- **요청자가 "관심 없음"(§8.6) 한 영상** — `video_hides` 행이 있으면 뺀다

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` | 미존재·삭제 챌린지 |

---

## 8.3 POST · DELETE /api/v1/videos/{videoSeq}/like — 좋아요 토글

**성공은 `204 No Content`**, 본문 없음(§1.2 예외).

```http
HTTP/1.1 204 No Content
```

`video_likes` 생성/삭제 + `participation_videos.like_count` 원자적 증감. 멱등이며 해제는 하드 삭제다. 클라이언트는 낙관적 UI + 실패 시 롤백.

아티스트 계정이 좋아요를 누르면 서비스가 `has_artist_like` 를 재계산한다.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음(비노출 포함) |

---

## 8.5 POST /api/v1/videos/{videoSeq}/view — 조회 이벤트

**§7.4 와 같은 규약**이다 — Redis 버퍼 가산, 1분 플러시, **202 Accepted**.

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 |

1회 인정 기준은 미결이다(§7.4 참조).

---

## 8.6 POST /api/v1/videos/{videoSeq}/hide — 관심 없음

이후 §8.1 피드에서 그 영상을 빼달라는 요청이다.

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

- `video_hides` 는 **행 존재 자체가 상태**다(복합 PK). 이미 숨긴 영상을 다시 호출해도 **200**이다(멱등).
- **해제 API 를 제공하지 않는다**(기본안). 한 번 숨기면 되돌리는 화면이 없다.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 |

---

## 8.7 POST /api/v1/videos/{videoSeq}/report — 영상 신고

### 요청

```json
{ "reasonCode": "SPAM" }
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

접수 후 클라이언트는 **다음 영상으로 이동**한다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 |
| 409 | `ALREADY_REPORTED` | **1인 1대상 1회** — 처리 결과와 무관하게 재신고 불가 |

```json
{
  "result": "FAIL",
  "code": "ALREADY_REPORTED",
  "message": "이미 신고한 대상입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

중복 방지는 DB UNIQUE(`uq_reports_reporter_video`)로 강제된다. `reports` 는 영상·댓글 3종까지 **4개 대상 중 정확히 하나만** 채우는 구조다(CHECK).

### 미결

**`reasonCode` 코드표가 정해지지 않았다**(원본 §16 #4). 컬럼은 `VARCHAR(30)` 이고 §9.6 댓글 신고와 같은 표를 쓴다.

---

## 8.8 POST /api/v1/videos — 게시

합성이 끝난 잡(§13)의 결과물을 **참여 영상으로 게시**한다.

### 요청

```json
{
  "jobUuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "description": "재밌었다!",
  "visibility": "PUBLIC",
  "commentEnabled": true
}
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `jobUuid` | Y | `jobType=CHALLENGE` 이고 `DONE` 인 **본인 소유** 잡 |
| `description` | Y | 게시 문구 |
| `visibility` | Y | `PUBLIC` \| `PRIVATE` |
| `commentEnabled` | Y | 댓글 허용 여부 |

**`title` 은 받지 않는다**(R3 — 컬럼 삭제. 기능 명세서 CHALLENGE-UPLOAD-01 정정 필요).

### 성공 응답 (201)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "videoSeq": 88 },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**201 Created.** 클라이언트는 전체 화면 뷰어로 이동한다(CHALLENGE-UPLOAD-05).

### 서버 처리

1. **요청자가 `role=ARTIST` 면 거부**한다 — 아티스트 계정은 참여 영상을 게시할 수 없다
2. 잡 검증 — `CHALLENGE` 타입 · `DONE` 상태 · 본인 소유 · **미게시**(`uq_participation_videos_job`)
3. 원본 챌린지가 삭제되지 않았는지 재확인
4. `participation_videos` 생성 — 영상·썸네일 키는 **잡 결과에서 복사**하고, `artist_seq` 는 **챌린지에서 복사**한다
5. `challenges.participant_count + 1` — 같은 트랜잭션

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 403 | `FORBIDDEN` | **ARTIST 계정의 게시 시도** |
| 404 | `JOB_NOT_FOUND` | 잡 미존재·타인 잡 |
| 409 | `JOB_NOT_DONE` | 잡이 `DONE` 이 아님(진행 중·실패) |
| 409 | `ALREADY_PUBLISHED` | 이미 게시된 잡 |
| 409 | `CHALLENGE_DELETED` | 게시 시점에 원본 챌린지가 삭제됨 — 클라이언트는 "삭제된 챌린지" 안내 |

```json
{
  "result": "FAIL",
  "code": "ALREADY_PUBLISHED",
  "message": "이미 게시된 영상입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

---

## 8.9 PATCH /api/v1/videos/{videoSeq} — 게시 정보 수정

**부분 수정**이다 — 보낸 필드만 반영한다.

### 요청

```json
{ "description": "수정한 문구", "visibility": "PRIVATE", "commentEnabled": false }
```

수정 가능한 것은 `description` · `visibility` · `commentEnabled` 셋뿐이다. 제목은 개념 자체가 없다(R3).

### 성공 응답 (200)

`data` 는 **§8.2 항목(수정 반영본)** 이다.

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "videoSeq": 88,
    "challengeSeq": 11,
    "description": "수정한 문구",
    "videoUrl": "signed-url",
    "thumbnailUrl": "signed-url",
    "durationMs": 15000,
    "author": { "userSeq": 5, "nickname": "팬123", "profileImageUrl": "https://cdn.motif.io/profiles/5.jpg" },
    "viewCount": 3400,
    "likeCount": 120,
    "commentCount": 15,
    "hasArtistLike": true,
    "hasArtistComment": false,
    "isLiked": false,
    "commentEnabled": false,
    "visibility": "PRIVATE",
    "isMine": true,
    "regDttm": "2026-09-01T10:00:00.000Z"
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`PUBLIC → PRIVATE` 로 바꾸면 **`videoUrl` 이 signed URL 로 바뀐다** — 같은 영상이라도 공개 여부에 따라 URL 등급이 달라진다(§1.7).

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 |
| 403 | `FORBIDDEN` | 본인 영상이 아님 |

---

## 8.10 DELETE /api/v1/videos/{videoSeq} — 게시 영상 삭제

**소프트 삭제**다(`is_deleted=TRUE`).

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

**최초 삭제일 때만** `challenges.participant_count - 1` 을 적용한다 — 이미 삭제된 영상을 다시 지워도 카운터가 더 줄지 않는다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 |
| 403 | `FORBIDDEN` | 본인 영상이 아님 |

---

## 8.4 (삭제) 참여 영상 좋아요 사용자 목록

`GET /videos/{videoSeq}/likes` 는 **기능에서 제거됐다**(v0.8). 구현하지 않는다.
