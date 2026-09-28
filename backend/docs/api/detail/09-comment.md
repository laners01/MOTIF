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

정렬은 **자르기 전에 전체에** 걸린다 — 한 페이지를 읽어 와 그 안에서 다시 정렬하는 것이 아니다. 아티스트 댓글이 200번째 행에 있어도 1페이지 맨 위다.

**(기본안) 같은 시각이면 `commentSeq` 내림차순이다.** `regDttm` 만으로 자르면 같은 초에 달린 댓글들이 페이지마다 순서를 바꿔 나와 **1페이지의 댓글이 2페이지에 또 나오고 다른 하나는 영영 안 나온다**. PK 는 유일하고 IDENTITY 라 최신순과 방향도 같다.

**(기본안) `size` 는 최대 100, 기본 20이다**(§1.5 공통 상한). `sort` 파라미터는 받지 않는다 — 정렬은 위 규칙으로 서버가 고정한다.

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
| `replyCount` | 답글 수. 답글은 **접힌 상태**로 시작하며 §9.2 로 펼친다. **(기본안) 노출되는 답글만 센다** — 삭제된 답글과 삭제 사용자가 쓴 답글은 §9.2 에도 나오지 않으므로 빠진다. 즉 **펼쳤을 때 나오는 수와 같다**. `comment_count` 칼럼이 아니다(그쪽은 루트+답글 합계이고 비노출을 모른다) |
| `isMine` | 내 댓글이면 삭제 메뉴를 노출한다 |
| `regDttm` | **원시각을 준다** — "n시간 전" 표기는 클라이언트가 만든다 |

목록에 나오는 것은 **루트 댓글뿐**이다(`parent_comment_seq IS NULL`). 답글은 §9.2 가 따로 낸다.

삭제된 댓글은 목록에서 제외한다. 삭제 사용자가 쓴 루트 댓글은 **답글 스레드 전체**가 비노출이다(§1.8).

**(기본안) `totalElements` 는 비노출을 걷어낸 뒤의 루트 댓글 수다.** 대상의 `comment_count` 칼럼을 쓰지 않는다 — 그 값은 답글을 포함하고 삭제·탈퇴로 감춘 행도 세고 있어서, 그대로 내보내면 `totalPages` 가 실제 페이지 수와 어긋난다(§1.8 이 감수한다고 한 불일치는 **카운터 표시**이지 페이지 계산이 아니다).

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` | 미존재 · 삭제된 챌린지 |
| 404 | `VIDEO_NOT_FOUND` | 미존재 · 삭제 영상 · 삭제 사용자의 콘텐츠 · 삭제된 챌린지 소속 · **타인의 `PRIVATE`** (§8.2 와 같은 판정) |
| 404 | `NOTICE_NOT_FOUND` | 미존재 · 삭제된 공지 |
| 403 | `COMMENT_DISABLED` | (참여 영상) `comment_enabled=FALSE` — **목록 조회까지 차단**한다 |

**404 가 403 보다 먼저다.** 보이지 않는 영상에 `COMMENT_DISABLED` 를 주면 "그 영상은 존재한다" 가 응답에서 읽힌다 — §1.8 존재 은닉이 뚫리는 자리다.

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

**(기본안) 같은 시각이면 `commentSeq` 오름차순**이다 — §9.1 과 같은 이유로 페이지 경계에서 답글이 중복되거나 빠지지 않게 한다. **(기본안) `size` 는 최대 100, 기본 20.**

`{type}` 은 **소문자**다(`challenge`·`video`·`notice`). 댓글 PK 는 테이블마다 따로 도는 시퀀스라 `{type}` 없이는 어느 테이블의 `501` 인지 알 수 없다. **(기본안) 알 수 없는 값은 400 이 아니라 404** — 오타 한 번에 "그런 종류가 있다/없다" 가 드러나지 않게 한다.

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
          "profileImageUrl": "https://cdn.motif.io/profiles/default.png",
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
| 404 | `COMMENT_NOT_FOUND` | 미존재 · 삭제됨 · **루트 댓글이 아님**(답글의 답글을 조회) · **탈퇴 사용자가 쓴 루트**(스레드 전체 비노출, §1.8) · **(기본안) 대상이 안 보임** · **(기본안) 알 수 없는 `{type}`** |
| 403 | `COMMENT_DISABLED` | (참여 영상) 댓글 사용 중지 |

**(기본안) 대상 비노출도 `COMMENT_NOT_FOUND` 다.** 삭제된 챌린지·공지, 타인의 `PRIVATE` 영상에 달린 댓글이 여기 해당한다. §9.1 은 대상을 경로로 직접 지목하니 `VIDEO_NOT_FOUND` 처럼 대상의 코드로 답하지만, 이 경로는 `commentSeq` 만 들고 오므로 대상별 코드를 내보내면 **그 번호가 어느 테이블의 것인지**가 응답에서 읽힌다.

**(기본안) `totalElements` 는 §9.1 항목의 `replyCount` 와 같은 값이다** — 양쪽 다 삭제된 답글과 탈퇴 사용자의 답글을 뺀 수다. 접어 둔 수와 펼친 수가 다르면 화면이 "2개" 라고 써 놓고 3개를 펼친다.

---

## 9.3 댓글·답글 작성

`POST /api/v1/{challenges|videos|notices}/{seq}/comments`

### 요청

```json
{ "content": "잘 봤어요!", "parentCommentSeq": 501 }
```

| 필드 | 필수 | 설명 |
|---|:---:|---|
| `content` | Y | 본문. **(기본안) 최대 1000자**이고 **앞뒤 공백은 떼고 저장**한다 — 그 뒤 빈 문자열이면 400. 명세가 길이를 비워 뒀고 DB 는 `TEXT` 라 상한이 없는데, 없으면 한 건으로 목록 응답을 통째로 부풀릴 수 있다 |
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
| 400 | `INVALID_PARAMETER` | `content` 누락·공백·1000자 초과 |
| 400 | `REPLY_DEPTH_EXCEEDED` | **답글에 답글**(depth 2 초과) |
| 400 | `PARENT_TARGET_MISMATCH` | `parentCommentSeq` 가 다른 게시물의 댓글 |
| 404 | `CHALLENGE_NOT_FOUND` / `VIDEO_NOT_FOUND` / `NOTICE_NOT_FOUND` | 대상 없음·비노출 — 경로가 대상을 직접 지목하므로 대상의 코드를 쓴다(§9.2·§9.4 와 다른 점) |
| 404 | `COMMENT_NOT_FOUND` | 부모 댓글 없음·삭제됨 · **(기본안) 탈퇴 사용자가 쓴 루트** |
| 403 | `COMMENT_DISABLED` | (참여 영상) 댓글 사용 중지 |

**(기본안) 탈퇴 사용자가 쓴 루트에는 답글을 달 수 없다.** 그 스레드는 §9.1·§9.2 에서 통째로 감춰져 있어(§1.8) 달아도 아무도 볼 수 없는데, 대상의 `commentCount` 만 올라간다.

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

1. 루트 삭제면 **직계 답글까지 소프트 삭제**. 손자는 없다(깊이 2단) — **(기본안) 이미 삭제돼 있던 답글은 다시 세지 않는다**
2. 삭제 대상 댓글들의 **좋아요는 하드 삭제**
3. 대상의 `comment_count` 를 **실제 삭제된 행 수만큼** 감소 — `deletedCount` 와 같은 수다. **(기본안) 0 아래로 내려가지 않는다**
4. 참여 영상이면 `has_artist_comment` 를 **다시 센다** — 아티스트 댓글 하나를 지웠다고 무조건 끄면, 남은 아티스트 댓글이 있는데도 §8.1 피드 가산점이 사라진다. 켜는 것은 자명하지만 끄는 것은 세어 봐야 한다

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | 미존재 · 이미 삭제됨 · **(기본안) 대상이 안 보임** · **(기본안) 알 수 없는 `{type}`** |
| 403 | `FORBIDDEN` | 본인 댓글이 아님 |

**멱등이 아니다** — 이미 지운 댓글에 다시 요청하면 404 다.

**(기본안) 댓글 잠금은 삭제를 막지 않는다.** `comment_enabled=FALSE` 인 영상에서도 자기 댓글은 지울 수 있다(§9.4 실패표에 `COMMENT_DISABLED` 가 없다). 잠금은 **새 글을 막는 장치**지 이미 쓴 글을 못 내리게 하는 장치가 아니다 — 막으면 사용자가 자기 댓글을 영영 내릴 수 없다.

**(기본안) 대상 비노출은 `COMMENT_NOT_FOUND`** — §9.2 와 같은 규칙이다(개별 댓글 경로라 대상 코드를 내보내면 그 번호가 어느 테이블 것인지 드러난다).

---

## 9.5 POST · DELETE /api/v1/comments/{type}/{commentSeq}/like — 좋아요 토글

**성공은 `204 No Content`**, 본문 없음(§1.2 예외).

```http
HTTP/1.1 204 No Content
```

댓글 좋아요 테이블 3종(`*_comment_likes`)의 행 생성/삭제 + `like_count` 원자적 증감. 멱등이며 해제는 하드 삭제다. 클라이언트는 낙관적 UI + 실패 시 롤백.

**루트와 답글 모두** 누를 수 있다. **(기본안) 자기 댓글에도 누를 수 있다** — 명세에 제한이 없다.

**멱등의 뜻**: 이미 누른 상태에서 POST 를, 누르지 않은 상태에서 DELETE 를 보내도 204 이고 `likeCount` 는 움직이지 않는다. **(기본안) `like_count` 는 0 아래로 내려가지 않는다** — 해제를 두 번 받아도 `CHECK (like_count >= 0)` 을 건드리지 않는다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `COMMENT_NOT_FOUND` | 미존재 · 삭제된 댓글 · **(기본안) 탈퇴 사용자의 댓글** · **(기본안) 감춰진 스레드의 답글**(루트 작성자가 탈퇴, §1.8) · **(기본안) 대상이 안 보임** · **(기본안) 알 수 없는 `{type}`** |
| 403 | `COMMENT_DISABLED` | (참여 영상) 댓글 사용 중지 — **생성·해제 모두** 막는다 |

**§9.4 삭제와 다른 점**: 잠긴 영상에서 좋아요는 해제까지 막히지만, 자기 댓글 삭제는 허용된다. 좋아요는 **새로 거는 상호작용**이고 삭제는 **이미 쓴 것을 내리는 일**이라 그렇다.

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

`reports` 는 영상·댓글 3종까지 **4개 대상 중 정확히 하나만** 채운다(CHECK). `{type}` 에 따라 대응 컬럼이 정해진다. **타입이 다르면 같은 번호도 다른 대상**이다 — 챌린지 댓글 501 과 공지 댓글 501 은 각각 신고할 수 있다(UNIQUE 4종이 NULL 로 갈린다).

**접수는 접수일 뿐이다.** 신고했다고 댓글이 숨겨지거나 카운터가 바뀌지 않는다 — 판단·조치는 어드민(§3.13)이 한다.

**(기본안) 본인 댓글도 신고할 수 있다.** 막으려면 "왜 막는가" 를 알릴 코드가 필요한데 §15 표에 없다(§8.7 이 같은 판단을 했다). 답글도 신고 대상이다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | `reasonCode` 누락·공백·30자 초과(컬럼 폭) |
| 404 | `COMMENT_NOT_FOUND` | 미존재 · 삭제된 댓글 · **(기본안) 탈퇴 사용자의 댓글** · **(기본안) 감춰진 스레드의 답글**(§1.8) · **(기본안) 대상이 안 보임** · **(기본안) 알 수 없는 `{type}`** |
| 409 | `ALREADY_REPORTED` | 1인 1대상 1회(DB UNIQUE 로 강제) |

**(기본안) 잠긴 영상의 댓글도 신고할 수 있다.** 실패표에 `COMMENT_DISABLED` 가 없다 — 문제가 되어 잠긴 게시물일수록 신고가 필요한데 막으면 어드민에게 알릴 길이 사라진다. §9.5 좋아요만 잠금에 막힌다.

`reasonCode` 코드표는 미결이다(§8.7 과 같은 표를 쓴다). 그래서 **문자열로 받는다** — 지금 열거로 고정하면 표가 정해질 때 이미 쌓인 행이 열거에 없는 값을 갖게 되고, 그 순간부터 역직렬화가 400 을 낸다.
