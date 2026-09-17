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
    "videoUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
    "thumbnailUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
    "durationMs": 15000,
    "author": {
      "userSeq": 5,
      "nickname": "팬123",
      "profileImageUrl": "https://cdn.motif.io/profiles/8b2d4e1a.jpg"
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
| `videoUrl` | **HLS 마스터 재생목록**(`master.m3u8`) 주소. `PUBLIC` 이면 서명 없이 열리고, **본인의 `PRIVATE` 이면 core-api 가 서명해 다시 쓴 재생목록**을 받는다(§1.7) |
| `hasArtistLike` · `hasArtistComment` | 해당 챌린지의 아티스트가 좋아요·댓글을 남겼는지. 화면 뱃지이자 §8.1 추천 정렬의 강가중 요소다 |
| `isLiked` | 요청자의 좋아요 여부 |
| `commentEnabled` | `false` 면 댓글이 **동결**된다 — 목록 조회까지 차단이다(§9) |
| `isMine` | `true` 면 클라이언트가 Bottom Sheet 에 수정·삭제를 노출한다 |
| `description` | 게시 시 입력한 문구. DB 상 NOT NULL 이라 빈 문자열일 수는 있어도 `null` 은 아니다 |

`videoUrl` 의 등급 판정은 **키 프리픽스**가 한다 — `visibility` 로 가르지 않는다(§8.1 과
같은 지점). 게시가 공개 사본을 `public/` 으로 복사할 뿐 키를 바꾸지 않아, `PUBLIC` 인데
키는 아직 `private/` 인 행이 정상적으로 존재하기 때문이다.

**조회수는 이 API 로 올라가지 않는다 (기본안)** — 조회 이벤트는 §8.5 다. 읽기 API 가
쓰기를 겸하면 재시도·프리페치가 그대로 조회수가 된다.

**"관심 없음"(§8.6) 한 영상도 200 이다 (기본안)** — `video_hides` 는 §8.1 피드에서 빼는
장치지 접근 차단이 아니다. 명세가 제외를 적은 자리는 §8.1 뿐이고, 링크·공유로 직접
들어온 경우까지 막을 근거가 없다.

**본인의 `PRIVATE` 은 200 이다** — 아래 404 목록의 "타인의 `PRIVATE`" 과 갈린다.

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

**커서 페이지네이션**(§1.9). **추천 정렬**이며 서버 고정이다 — `sort` 파라미터가 없다.

### 정렬 **(기본안)**

```
score = (has_artist_like + has_artist_comment) × 10¹² + view_count
order by score desc, video_seq desc
```

- **아티스트가 반응한 영상이 사실상 다른 층이다.** 원본 명세는 "좋아요·댓글·조회수 +
  `has_artist_like`/`has_artist_comment` 강가중" 이라고 적었는데, 강가중을 조회수와 같은
  자릿수로 주면 조회수가 큰 영상이 그 위로 올라가 요구가 무너진다. 10¹² 는 `view_count`
  (BIGINT)가 실제로 닿지 않는 자리다.
- **좋아요·댓글 수는 점수에 넣지 않는다 (기본안)** — §6.1 HOT 랭킹이 같은 이유로
  조회수만 쓴다. 두 수는 응답 필드로는 그대로 내려간다.
- `video_seq` 보조 키가 없으면 점수가 같은 영상들이 호출마다 자리를 바꿔 커서 페이지에
  중복·누락이 생긴다. 점수 0(아무도 안 본 영상)이 흔해 실제로 일어난다.
- 점수를 **조회 시점에** 매기므로, 스크롤 중 조회수가 오른 영상이 커서를 앞질러 다시
  나오거나 뒤로 밀려 건너뛸 수 있다. keyset 페이지에서는 피할 수 없고, §6.1 처럼 스냅샷을
  뜨면 챌린지마다 키가 생겨 이 목록에는 과하다. 커서가 opaque 토큰이라 계약은 안 깨진다.

**커서 형식**: `base64("{score}:{videoSeq}")` — §5.5·§7.1 의 단일 seq 토큰, §6.1 의
스냅샷 토큰과 **다른 세 번째 형식**이다(`Cursors.encodeScore`).

### 요청

```http
GET /api/v1/challenges/{challengeSeq}/videos?cursor={opaque}&size=20
Authorization: Bearer {accessToken}
```

`size` 기본 20, 최대 100(§1.9 공통 상한).

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
        "videoUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
        "thumbnailUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
        "durationMs": 15000,
        "author": { "userSeq": 5, "nickname": "팬123", "profileImageUrl": "https://cdn.motif.io/profiles/8b2d4e1a.jpg" },
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
    "nextCursor": "MTAwMDAwMDAwMzQwMDo4OA",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

`videoUrl` 의 등급은 **키 프리픽스가 정한다** — `visibility` 로 가르지 않는다(§1.7).
게시는 공개 사본을 `public/` 으로 복사할 뿐 키를 바꾸지 않아 `PUBLIC` 인데 키는 아직
`private/` 인 행이 정상적으로 존재한다. 보호 등급이면 CDN 이 아니라 **core-api**
(`/api/v1/media/{jobUuid}/master.m3u8`)를 가리킨다.

### 목록에서 제외하는 것

- **타인의** `PRIVATE` 영상 · 삭제 영상 · 삭제 사용자의 영상 (§1.8)
- **요청자가 "관심 없음"(§8.6) 한 영상** — `video_hides` 행이 있으면 뺀다

**내 `PRIVATE` 영상은 보인다 (기본안)** — 명세가 뺀다고 적은 것은 "타인의 `PRIVATE`" 이고,
§4.8 내 챌린지 그리드도 같은 취급이다. 항목의 `visibility` 로 클라이언트가 자물쇠를 그린다.

영상이 0건이면 **빈 목록 200** 이다(`hasNext:false`).

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | 망가진 `cursor` (§1.9 공통 — 조용히 첫 페이지로 되돌리지 않는다) |
| 404 | `CHALLENGE_NOT_FOUND` | 미존재·삭제 챌린지 |

챌린지가 없을 때 빈 목록으로 답하지 않는다 — "영상 0건" 과 구분할 수 없게 된다.

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
- 요청 본문이 없다.

**멱등을 DB 가 보장한다** — `INSERT … ON CONFLICT DO NOTHING` 을 쓰되 **영향 행 수를 보지 않는다**. 같은 구문을 쓰는 §8.7 신고가 0행을 409 로 바꾸는 것과 정반대다 **(기본안)**: 신고는 "1인 1대상 1회" 가 규칙이지만, 숨김은 두 번 눌러도 사용자가 원하는 상태가 같고 해제 수단조차 없어 두 번째만 실패로 보이면 클라이언트가 되돌릴 길 없는 오류를 그리게 된다.

**숨김은 접근 차단이 아니다** — §8.2 단건 조회는 그대로 열린다(§8.2 기본안). **본인 영상도 숨길 수 있다**(명세에 없고 막을 근거가 없다 — §8.7 과 같은 판단).

### 404 는 §8.2 와 같은 규칙이다 **(기본안)**

미존재 · 삭제 영상 · 삭제 사용자의 콘텐츠 · 삭제된 챌린지 소속 · **타인의 `PRIVATE`**. 숨김만 느슨하면 그 응답이 "타인의 `PRIVATE` 영상이 존재한다"를 알려 주는 탐지 수단이 된다(§8.7 과 같은 이유).

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 (§8.2 가시성 규칙과 동일) |

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

**409 는 애플리케이션이 아니라 DB 가 가른다** — "이미 신고했는가"를 먼저 묻고 갈라지면 묻는 시점과 쓰는 시점이 벌어져 동시 요청 둘이 다 통과하고, UNIQUE 위반이 500 으로 터진다. `INSERT … ON CONFLICT DO NOTHING` 의 영향 행 수가 곧 판정이다(§5.6 구독과 같은 방식).

### 404 는 §8.2 와 같은 규칙이다 **(기본안)**

"대상 없음" 의 범위를 §8.2 와 같게 본다 — 미존재 · 삭제 영상 · 삭제 사용자의 콘텐츠 · 삭제된 챌린지 소속 · **타인의 `PRIVATE`**. 신고만 느슨하면 그 응답이 "타인의 `PRIVATE` 영상이 존재한다"를 알려 주는 탐지 수단이 된다.

### 접수는 제재가 아니다 **(기본안)**

신고해도 영상은 그대로 보인다. 자동 숨김·차단은 어드민이 `status` 를 옮기며 하는 일이다 — 접수가 곧 제재면 신고 하나로 남의 영상을 내릴 수 있다. **본인 영상 신고도 막지 않는다** (명세에 없고, 막을 때 쓸 에러 코드가 §15 표에 없다).

### `reasonCode` 는 문자열로 받는다 **(기본안)**

**코드표는 여전히 미결이다**(원본 §16 #4 — §8.7·§9.6·§13.3 공통). 서버는 **비어 있지 않은 30자 이내 문자열**인지만 본다(`VARCHAR(30)`).

지금 enum 으로 고정하지 않는 이유는, 회의가 표를 정하는 순간 **이미 쌓인 행이 열거에 없는 값을 갖게 되고** 그때부터 역직렬화가 400 을 내기 때문이다. 코드는 사유 문구에 가깝게 쓰고, 표가 정해지면 그때 좁힌다. §9.6 댓글 신고도 같은 컬럼·같은 취급이다.

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

**셋 다 선택이고 `null`(= 생략)은 "안 바꿈" 이다 (기본안).** 세 필드 모두 `null` 을 유효한 값으로
쓰지 않으므로 — `description` 은 NOT NULL 이고 **빈 문자열**이 "지움" 이다 — `JsonNullable` 같은
장치 없이 생략과 값을 구분할 수 있다. **셋 다 생략하면 아무것도 바꾸지 않고 현재 상태를 200 으로
돌려준다** (이걸 막을 코드가 §15 표에 없다).

`description` 상한은 **1000자 (기본안)** 다. DB 는 TEXT 라 무제한이고, 상한이 없으면 본문 크기
제한이 서버 전역 설정에만 걸린다. §8.8 게시가 같은 값을 써야 한다.

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
    "videoUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
    "thumbnailUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
    "durationMs": 15000,
    "author": { "userSeq": 5, "nickname": "팬123", "profileImageUrl": "https://cdn.motif.io/profiles/8b2d4e1a.jpg" },
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

**`visibility` 는 양방향으로 바꿀 수 있다. `PUBLIC → PRIVATE` 은 CDN 무효화를 동반한다.**

산출물이 `private/` 와 `public/` 으로 갈려 있어(§1.7) 방향마다 비용이 다르다.

| 방향 | 처리 | 반영 |
|---|---|---|
| `PRIVATE → PUBLIC` | `private/` 트리를 `public/` 으로 복사 | 즉시 |
| `PUBLIC → PRIVATE` | `public/` 트리 삭제 + **CloudFront 무효화** | 수십 초~수 분 |

산출물에 1년 `immutable` 이 붙어 있어 **S3 에서 지우는 것만으로는 엣지가 계속 서빙한다**.
무효화(`create-invalidation /public/challenge/{jobUuid}/*`)를 함께 호출해야 실제로 닫힌다.
비동기라 응답은 즉시 돌려주되, 그 사이 이미 URL 을 쥔 클라이언트는 잠시 더 볼 수 있다 —
**"즉시 차단"을 약속하지 않는다**(월 1,000 path 까지 무료라 비용은 실질 0).

`result_video_key` 는 방향과 무관하게 `private/` 를 계속 가리킨다. 재생 중인
클라이언트가 404 를 만나지 않게 하기 위해서다 — 공개 트리는 사본이고 정본이 아니다.

### 지금 구현된 범위 **(기본안)**

**S3 사본 복사·삭제와 CloudFront 무효화는 아직 없다.** `MediaObjectStore` 에 있는 것은
presign·HEAD 뿐이고 무효화 클라이언트도 없다. 무엇보다 `public/` 트리를 만드는 주체가
**§8.8 게시**인데 그 API 가 없어, 현재 산출물은 전부 `private/` 에만 있다. 복사기는 §8.8 이
소유해야 한다 — 여기서 만들면 그쪽이 같은 것을 다시 만든다.

그래서 **키(`participation_videos.video_key`·`thumbnail_key`)만 비대칭으로 움직인다.**

| 방향 | 키 | 이유 |
|---|---|---|
| `PUBLIC → PRIVATE` | **정본(`private/`)으로 되돌린다** | 이 값만 두면 비공개로 내린 영상이 계속 무서명 CDN URL 로 나간다. 정본 위치는 계산하지 않고 `composite_jobs.result_video_key`·`result_thumbnail_key` 에서 읽는다 — 워커가 실제로 쓴 자리다 |
| `PRIVATE → PUBLIC` | **그대로 둔다** | 공개 사본이 없어 가리킬 자리가 없다. 키만 바꾸면 CDN 이 404 다. 보호 경로로 두면 적어도 본인은 재생된다 |

`PRIVATE → PUBLIC` 이 남기는 "`visibility=PUBLIC` 인데 키는 `private/`" 는 **비정상이 아니다** —
`MediaKeys.isProtected` 가 "정상적으로 존재한다" 고 적어 둔 바로 그 상태다. 그동안 **타인은
재생할 수 없다**(재생목록 인가가 소유자 기준). 복사가 붙는 티켓에서 닫힌다.

남는 한계는 하나다 — **`PUBLIC → PRIVATE` 이후에도 이미 발급된 무서명 URL 은 살아 있다.**
공개 URL 에는 만료가 없고(서명이 없다) 객체도 그대로다. 보호 등급으로 이미 발급된 것들은
서명 TTL(재생목록 10분·이미지 6시간)로 알아서 죽고, core-api 재생 경로는 요청마다 인가를
다시 하므로 **즉시** 닫힌다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | `description` 1000자 초과 · `visibility` 가 `PUBLIC`\|`PRIVATE` 이 아님 |
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 + **남의 `PRIVATE`** |
| 403 | `FORBIDDEN` | 보이는데 본인 영상이 아님 (= 남의 `PUBLIC`) |

**404 가 403 보다 앞선다 (기본안).** 남의 `PRIVATE` 영상에 PATCH 하면 403 이 아니라 404 다 —
403 을 주면 "그 영상이 존재한다"가 응답에서 읽힌다(§1.8 존재 은닉). 403 은 이미 보이는 영상,
즉 남의 `PUBLIC` 에만 나간다.

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
