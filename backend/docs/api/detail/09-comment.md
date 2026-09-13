# §9 댓글 공통 (COMMENT) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §9 · DB 명세서 v0.15 §19~§21.

## 3종 대상을 한 계약으로

댓글은 **챌린지·참여 영상·공지** 세 대상에 붙고, DB 테이블도 3종이다(`challenge_comments`, `video_comments`, `notice_comments`). API 는 **하나의 계약**으로 다룬다.

- **목록·작성**은 대상 리소스 하위 경로를 쓴다.
- **개별 조작**(답글·삭제·좋아요·신고)은 `/comments/{type}/{commentSeq}` 를 쓴다. 경로 변수 `{type}` ∈ `challenge` | `video` | `notice` 이며 테이블 3종과 1:1 이다.

| # | Method | Path | 인증 |
|---|---|---|---|
| 9.1 | GET | `/api/v1/challenges/{seq}/comments`<br>`/api/v1/videos/{seq}/comments`<br>`/api/v1/notices/{seq}/comments` | 필요 |
| 9.2 | GET | `/api/v1/comments/{type}/{commentSeq}/replies` | 필요 |
| 9.3 | POST | (9.1 과 같은 경로) | 필요 |
| 9.4 | DELETE | `/api/v1/comments/{type}/{commentSeq}` | 필요 |
| 9.5 | POST · DELETE | `/api/v1/comments/{type}/{commentSeq}/like` | 필요 |
| 9.6 | POST | `/api/v1/comments/{type}/{commentSeq}/report` | 필요 |

이하 실패 표의 `*_NOT_FOUND` 는 대상 종류에 따라 `CHALLENGE_NOT_FOUND` / `VIDEO_NOT_FOUND` / `NOTICE_NOT_FOUND` / `COMMENT_NOT_FOUND` 로 읽는다.

## 깊이는 2단까지다

루트 댓글과 그 답글까지만 있다. **답글에 답글을 달 수 없다** — `parent_comment_seq` 가 가리키는 것은 반드시 루트 댓글이어야 한다.

---

## 9.1 루트 댓글 목록

`GET /api/v1/{challenges|videos|notices}/{seq}/comments?page=0&size=20`

offset 페이지네이션이다.

### 정렬 — 세 그룹을 순서대로

전체 루트 댓글을 **아래 우선순위로 정렬한 뒤** 요청한 `size` 만큼 자른다.

1. **대상 아티스트의 댓글** — 최신순
2. **내 댓글** — 최신순
3. **일반 댓글** — 최신순

"대상 아티스트"는 `role=ARTIST` 전체가 아니라 **그 콘텐츠에 연결된 아티스트**만이다.

| 대상 | 판정 기준 |
|---|---|
| 챌린지 | `challenges.artist_seq` 와 작성자가 일치 |
| 참여 영상 | 그 영상의 `artist_seq`(원본 챌린지에서 복사)와 일치 |
| 공지 | `notices.artist_seq` 와 일치 |

**다른 ARTIST 계정의 댓글은 일반 댓글로 취급**한다.

우선 그룹의 댓글이 여러 개면 전부 정렬 대상이다. 예를 들어 `size=20` 인데 대상 아티스트 댓글이 15개, 내 댓글이 10개라면 첫 페이지는 **아티스트 15개 + 내 댓글 최신 5개**가 된다.

### 성공 응답 (200) — offset 페이지

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "commentSeq": 501,
        "content": "잘 봤어요!",
        "author": {
          "userSeq": 5,
          "nickname": "팬123",
          "profileImageUrl": "https://cdn.motif.io/profiles/8b2d4e1a.jpg",
          "isArtist": false
        },
        "likeCount": 3,
        "isLiked": false,
        "replyCount": 2,
        "isMine": false,
        "regDttm": "2026-09-01T12:00:00.000Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 210,
    "totalPages": 11,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `author.isArtist` | **대상 아티스트인지** — 화면의 아티스트 뱃지. 정렬 1순위와 같은 판정이다 |
| `replyCount` | 답글 수. 답글은 **접힌 상태**로 시작하며 §9.2 로 펼친다 |
| `isMine` | 내 댓글이면 삭제 메뉴를 노출한다 |
| `regDttm` | **원시각을 준다** — "n시간 전" 표기는 클라이언트가 만든다 |

삭제된 댓글은 목록에서 제외한다. 삭제 사용자가 쓴 루트 댓글은 **답글 스레드 전체**가 비노출이다(§1.8).

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `*_NOT_FOUND` | 대상 리소스 없음 |
| 403 | `COMMENT_DISABLED` | (참여 영상) `comment_enabled=FALSE` — **목록 조회까지 차단**한다 |

```json
{
  "result": "FAIL",
  "code": "COMMENT_DISABLED",
  "message": "댓글을 사용할 수 없는 게시물입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

댓글 사용 중지는 **데이터 동결**이다 — 기존 댓글을 지우지 않고 접근만 막는다.

---

## 9.2 GET /api/v1/comments/{type}/{commentSeq}/replies — 답글 펼치기

`?page=0&size=20`. **등록순 고정**이다(최신순이 아니다 — 대화 흐름을 유지한다).

### 성공 응답 (200)

`data.list[]` 는 **§9.1 항목과 같되 `replyCount` 가 없다** — 답글에는 답글이 없기 때문이다.

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "commentSeq": 530,
        "content": "😂😂😂",
        "author": {
          "userSeq": 9,
          "nickname": "dancing_mandu",
          "profileImageUrl": null,
          "isArtist": false
        },
        "likeCount": 0,
        "isLiked": false,
        "isMine": false,
        "regDttm": "2026-09-01T13:00:00.000Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | 미존재 · 삭제됨 · **루트 댓글이 아님**(답글의 답글을 조회) |
| 403 | `COMMENT_DISABLED` | (참여 영상) 댓글 사용 중지 |

---

## 9.3 댓글·답글 작성

`POST /api/v1/{challenges|videos|notices}/{seq}/comments`

### 요청

```json
{ "content": "잘 봤어요!", "parentCommentSeq": 501 }
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `content` | Y | 본문 |
| `parentCommentSeq` | N | 답글일 때만. **루트 댓글은 생략**한다 |

### 성공 응답 (201)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "commentSeq": 502 },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**201 Created.** 작성된 댓글 전체가 아니라 식별자만 준다 — 화면은 로컬 상태로 즉시 그리고, 필요하면 목록을 다시 받는다.

### 서버 처리

1. 대상이 비삭제인지, (참여 영상이면) `comment_enabled=TRUE` 인지 확인
2. `parentCommentSeq` 가 있으면 **루트 댓글인지**, **같은 대상 소속인지** 확인
3. 댓글 생성 + 대상의 `comment_count + 1` — 같은 트랜잭션
4. **FCM 발송 이벤트 생성**(NOTI-04/05) — **본인 행위는 발송하지 않는다**. 서버는 `notifications` 테이블에 저장하지 않는다(§14)
5. 아티스트 계정의 댓글이면 참여 영상의 `has_artist_comment` 를 재계산한다

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `REPLY_DEPTH_EXCEEDED` | **답글에 답글**(depth 2 초과) |
| 400 | `PARENT_TARGET_MISMATCH` | `parentCommentSeq` 가 다른 게시물의 댓글 |
| 404 | `*_NOT_FOUND` | 대상 또는 부모 댓글 없음 |
| 403 | `COMMENT_DISABLED` | (참여 영상) 댓글 사용 중지 |

```json
{
  "result": "FAIL",
  "code": "REPLY_DEPTH_EXCEEDED",
  "message": "답글에는 답글을 달 수 없습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

---

## 9.4 DELETE /api/v1/comments/{type}/{commentSeq} — 삭제

**본인 댓글만** 지울 수 있다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "deletedCount": 3 },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`deletedCount` 는 **실제로 삭제된 행 수**다 — 루트 댓글을 지우면 **직계 답글도 함께 소프트 삭제**되므로 1보다 클 수 있다. 위 예시는 루트 1 + 답글 2 다.

### 서버 처리

1. 루트 삭제면 **직계 답글까지 소프트 삭제**
2. 삭제 대상 댓글들의 **좋아요는 하드 삭제**
3. 대상의 `comment_count` 를 **실제 삭제된 행 수만큼** 감소

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | 미존재·이미 삭제됨 |
| 403 | `FORBIDDEN` | 본인 댓글이 아님 |

---

## 9.5 POST · DELETE /api/v1/comments/{type}/{commentSeq}/like — 좋아요 토글

**성공은 `204 No Content`**, 본문 없음(§1.2 예외).

```http
HTTP/1.1 204 No Content
```

댓글 좋아요 테이블 3종(`*_comment_likes`)의 행 생성/삭제 + `like_count` 원자적 증감. 멱등이며 해제는 하드 삭제다. 클라이언트는 낙관적 UI + 실패 시 롤백.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | 대상 없음 |
| 403 | `COMMENT_DISABLED` | (참여 영상) 댓글 사용 중지 — **생성·해제 모두** 막는다 |

---

## 9.6 POST /api/v1/comments/{type}/{commentSeq}/report — 신고

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

`reports` 는 영상·댓글 3종까지 **4개 대상 중 정확히 하나만** 채운다(CHECK). `{type}` 에 따라 대응 컬럼이 정해진다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | 대상 없음 |
| 409 | `ALREADY_REPORTED` | 1인 1대상 1회(DB UNIQUE 로 강제) |

`reasonCode` 코드표는 미결이다(§8.7 과 같은 표를 쓴다).
