# §7 챌린지 원본 (CHALLENGE) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §7 · DB 명세서 v0.15 §12·§13·§14.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 7.1 | GET | `/api/v1/challenges/feed` | 필요 | 원본 챌린지 숏폼 피드 |
| 7.2 | GET | `/api/v1/challenges/{challengeSeq}` | 필요 | 상세 Bottom Sheet |
| 7.3 | POST · DELETE | `/api/v1/challenges/{challengeSeq}/like` | 필요 | **204 No Content** |
| 7.4 | POST | `/api/v1/challenges/view` | 필요 | 조회 이벤트 — **챌린지별 묶음 수신** |
| 7.5 | — | 댓글 | 필요 | §9 공통 댓글(`type=challenge`) — [`09-comment.md`](09-comment.md) 참조 |

**`title` 이 없다.** `challenges.title` 컬럼이 삭제됐다(R3). 화면 식별은 **곡명(`song.title`)·아티스트·썸네일**로 한다.

---

## 7.1 GET /api/v1/challenges/feed — 챌린지 피드

**커서 페이지네이션**(§1.9). 구독 아티스트 + 인기·관심 기반 추천을 종합한 **서버 내부 정렬**이다(`rank:challenge` 활용). `sort` 를 받지 않는다.

### 요청

```http
GET /api/v1/challenges/feed?cursor={opaque}&size=20
Authorization: Bearer {accessToken}
```

### 성공 응답 (200) — 커서 페이지

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "challengeSeq": 11,
        "videoUrl": "https://cdn.motif.io/challenges/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/master.m3u8",
        "thumbnailUrl": "https://cdn.motif.io/challenges/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/thumb.jpg",
        "durationMs": 15000,
        "artist": {
          "artistSeq": 3,
          "name": "카리나",
          "groupName": "aespa",
          "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
        },
        "song": { "title": "Supernova", "singerName": "aespa" },
        "viewCount": 120000,
        "participantCount": 340,
        "likeCount": 5600,
        "commentCount": 210,
        "isLiked": false,
        "isSubscribed": true
      }
    ],
    "prevCursor": null,
    "nextCursor": "eyJjIjoxMX0",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `videoUrl` | 챌린지 원본은 **공개 등급** — 일반 CDN URL |
| `thumbnailUrl` | 첫 프레임이 로드되기 전 **플레이스홀더**용(R2 부활) |
| `artist` | **공통 아티스트 요약**(README §3.5) |
| `isSubscribed` | 피드 오버레이의 구독 버튼 상태 — 이 항목에 속한다 |
| `participantCount` | **PUBLIC·PRIVATE 를 모두 포함**한 게시 참여 수 |

`participantCount` 가 PRIVATE 를 포함하는 것은 의도된 결정이다 — **PRIVATE 영상의 조회 비노출(§1.8)과 카운트 집계는 분리**한다. 그래서 참여 수가 340 인데 목록에는 그보다 적게 보일 수 있다.

### 실패 응답

**도메인 분기 없음.**

---

## 7.2 GET /api/v1/challenges/{challengeSeq} — 챌린지 상세

피드에서 정보를 눌렀을 때 뜨는 Bottom Sheet 데이터다. **§7.1 항목 + `description` + 슬롯 정보**로 구성된다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "challengeSeq": 11,
    "videoUrl": "https://cdn.motif.io/challenges/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/master.m3u8",
    "thumbnailUrl": "https://cdn.motif.io/challenges/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/thumb.jpg",
    "durationMs": 15000,
    "description": "이번 주 챌린지입니다. 같이 춰요!",
    "artist": {
      "artistSeq": 3,
      "name": "카리나",
      "groupName": "aespa",
      "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
    },
    "song": { "title": "Supernova", "singerName": "aespa" },
    "viewCount": 120000,
    "participantCount": 340,
    "likeCount": 5600,
    "commentCount": 210,
    "isLiked": false,
    "isSubscribed": true,
    "participants": [
      {
        "artistSeq": 7,
        "name": "윈터",
        "slotOrder": 0,
        "participantMask": 1,
        "profileImageUrl": "https://cdn.motif.io/profiles/7a1b.jpg",
        "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 },
        "selectable": true,
        "previewImageUrl": "https://cdn.motif.io/edited/11/p1.jpg"
      },
      {
        "artistSeq": 9,
        "name": "닝닝",
        "slotOrder": 1,
        "participantMask": 2,
        "profileImageUrl": "https://cdn.motif.io/profiles/9c2d.jpg",
        "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 },
        "selectable": false,
        "previewImageUrl": null
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### `participants` — 교체 가능한 인물 슬롯

사용자가 **자기 얼굴로 바꿔 넣을 수 있는 자리**다. 추가 참여 아티스트 목록이며, 슬롯 항목은 아티스트 요약이 아니라 `artistSeq`·`name`·`slotOrder`·`participantMask`·`profileImageUrl`·`anchor`·`selectable`·`previewImageUrl` 로 구성된 전용 형태다. **챌린지 주인(`artist`)은 슬롯이 아니다** — 원이 뜨지 않고 교체되지 않는다.

배열은 `slotOrder` 오름차순 = **화면 좌→우**다.

| 필드 | 설명 |
|---|---|
| `slotOrder` | 화면 좌→우 순서, **0부터** *(2026-09-21)* |
| `participantMask` | 그 슬롯을 가리키는 **단일 비트**(`1 << slotOrder` = 1, 2, 4, 8). §13.1 에 **그대로** 실어 보낸다 |
| `anchor` | **촬영 전 선택 화면(10-1~3)에서 프로필 원이 앉는 자리** *(2026-09-18 — bbox 대체)*. **썸네일 대비 0~1 비율**: `x`·`r` 은 가로, `y` 는 세로 기준. 뷰 크기 `W×H` 로 그리면 중심 `(x·W, y·H)`, 반지름 `r·W`. 시안(402 폭, 지름 80)이면 `r ≈ 0.0995`. 어드민이 아직 찍지 않은 옛 슬롯은 **`null`** — 원을 숨긴다 |
| `selectable` | 이 슬롯을 지운 편집본이 있는가 *(2026-09-21)*. `false` 면 **화면에서 잠근다** — 보내 봐야 §13.1 이 404 다 |
| `previewImageUrl` | 이 슬롯을 골랐을 때 썸네일 자리에 보이는 이미지 *(2026-09-18 · 2026-09-21 슬롯으로 이전)*. `null` 이면 `thumbnailUrl` 로 폴백 |

`bbox` 는 **삭제됐다**(2026-09-18, S15P21D104-618). 사각형 중심은 몸통이라 원 자리로 쓸 수 없었고, 촬영 가이드 오버레이도 쓰이지 않았다.

### 슬롯은 **하나만** 고른다 *(2026-09-21)*

편집본이 슬롯 1개당 1개로 정리되면서 **여러 명을 한 번에 교체하는 조합이 없어졌다.** 사용자가 고르는 것은 "함께할 아티스트 한 명"이다.

- 고른 슬롯의 `participantMask` 를 **그대로** §13.1 에 보낸다. **OR 로 합산하지 않는다** — 조합 값을 보내면 400 `INVALID_PARAMETER` 다.
- 무교체(원본 위 합성)는 `0` 또는 생략이다.
- 원을 누를 때 **썸네일 자리 이미지가 바뀐다**(10-2·10-3 — 배경 교체 개념). 그 슬롯 항목의 `previewImageUrl` 을 띄운다.

| 상황 | 표시 |
|---|---|
| 아무 것도 안 고름 | `thumbnailUrl` |
| 고른 슬롯에 `previewImageUrl` 이 있다 | 그 이미지 |
| `previewImageUrl` 이 `null`(어드민 미업로드) | `thumbnailUrl` 로 폴백 |

**`editedChallenges[]` 배열은 삭제됐다** *(2026-09-21)* — 조합 `mask` 를 OR 해 맞춰 보던 대조가 필요 없어졌다. 슬롯 항목이 자기 이미지를 들고 온다.

`availableMasks` 필드도 **삭제된 상태**다(R4). 고를 수 있는지는 항목마다 `selectable` 이 답하고 *(2026-09-21)*, 그래도 없는 슬롯을 보내면 §13.1 의 404 `EDITED_CHALLENGE_NOT_FOUND` 가 사후 판별한다. **전 조합 등록 운영 전제는 폐기됐다** — 편집본이 빈 슬롯은 정상이고, 그래서 `selectable` 이 화면 단 차단으로 들어왔다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` | 미존재·삭제 챌린지 |

```json
{
  "result": "FAIL",
  "code": "CHALLENGE_NOT_FOUND",
  "message": "존재하지 않는 챌린지입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

---

## 7.3 POST · DELETE /api/v1/challenges/{challengeSeq}/like — 좋아요 토글

**봉투를 반환하지 않는 예외다** — 성공은 `204 No Content` 이고 본문이 없다(§1.2).

### 성공 응답

```http
HTTP/1.1 204 No Content
```

**`isLiked`·`likeCount` 를 돌려주지 않는다.** 클라이언트는 현재 상태를 **낙관적으로 반전**하고 카운트를 ±1 한 뒤, 서버가 실패하면 로컬 상태를 롤백한다.

### 서버 처리

`challenge_likes` 행 생성/삭제 + `challenges.like_count` **원자적 증감**을 같은 트랜잭션에서 한다. **멱등하다** — 이미 누른 상태로 POST 하거나 누르지 않은 상태로 DELETE 해도 204 이고 카운터는 변하지 않는다. 해제는 하드 삭제다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` | 미존재·삭제 챌린지 |

실패일 때는 봉투를 반환한다(204 예외는 **성공에만** 적용된다).

---

## 7.4 POST /api/v1/challenges/view — 조회 이벤트

**경로에서 `{challengeSeq}` 가 빠졌다** *(2026-09-21 재정의 — [ADR-0016](../../adr/0016-challenge-view-and-artist-ranking.md))*.
§8.5 와 **같은 구조·같은 계약**이다 — 챌린지별 조회수를 묶어 보내고, Redis 에 쌓아
**20분 주기 배치**가 DB·버킷·랭킹에 반영한다.

### 요청

```http
POST /api/v1/challenges/view
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "views": {
    "11": 3,
    "12": 1
  }
}
```

`challengeSeq → 증가 조회수` 의 맵이다. JSON 객체 키라 `challengeSeq` 는 문자열로 온다.

| 제약 | 값 | 위반 시 |
|---|---|---|
| `views` 항목 수 | 1 ~ **50** | 400 `INVALID_PARAMETER` |
| 대상당 `count` | 1 ~ **100** | 400 `INVALID_PARAMETER` |

**부분 수용하지 않는다** — 항목 하나가 어긋나면 요청 전체를 400 으로 거절한다(§8.5 와
같은 근거).

### 성공 응답 (202)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**202 Accepted** — 집계는 비동기이므로 이 응답은 "접수했다"는 뜻이다. `viewCount` 가 즉시 반영되지 않는다. DB 반영은 **20분 주기 배치**가 한다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | 위 제약 위반 · 본문 형식 오류 |

**`CHALLENGE_NOT_FOUND` 가 없어졌다** — 존재하지 않는 `challengeSeq` 를 보내도 202 다.
조회 이벤트마다 DB 를 확인하면 집계를 Redis 로 뺀 의미가 없다. 없는 키는 배치의
`UPDATE` 가 0행으로 버리고, 랭킹 모집단은 DB 에서 나오므로 순위에도 영향이 없다.
구 명세의 *"존재 검증은 Redis 적재 전 경량 캐시로"* 라는 기본안은 이 결정으로 폐기한다.

**Redis 장애도 202 다(fail-open)** — §8.5 와 같다.

### 미결

**1회 인정 기준이 정해지지 않았다**(원본 §16 #3) — 같은 사용자의 중복 조회, 최소 시청 시간, 본인 영상 조회 포함 여부. 정책이 확정되면 **서버 내부에서만 반영**하며 이 API 계약은 바뀌지 않는다.

§5.1 아티스트 랭킹이 이 조회수를 먹으므로 이 미결의 무게가 전보다 무겁다 — 조작하면
순위가 움직인다. 지금 막는 것은 대상당 `count` 상한 하나뿐이다.

**§8.5(참여 영상 조회 이벤트)와 다시 같은 규약이다** *(2026-09-21 — [ADR-0016](../../adr/0016-challenge-view-and-artist-ranking.md))*.
ADR-0015 가 영상 쪽만 먼저 바꿔 한동안 양쪽이 갈려 있었는데, 이 티켓에서 맞췄다.
버퍼·집계 배치·재처리 로직은 **한 벌을 공유**하고 Redis 키만 대상 종류로 갈린다.
