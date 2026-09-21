# §7 챌린지 원본 (CHALLENGE) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §7 · DB 명세서 v0.15 §12·§13·§14.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 7.1 | GET | `/api/v1/challenges/feed` | 필요 | 원본 챌린지 숏폼 피드 |
| 7.2 | GET | `/api/v1/challenges/{challengeSeq}` | 필요 | 상세 Bottom Sheet |
| 7.3 | POST · DELETE | `/api/v1/challenges/{challengeSeq}/like` | 필요 | **204 No Content** |
| 7.4 | POST | `/api/v1/challenges/{challengeSeq}/view` | 필요 | 조회 이벤트 |
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
        "participantMask": 1,
        "profileImageUrl": "https://cdn.motif.io/profiles/7a1b.jpg",
        "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 }
      },
      {
        "artistSeq": 9,
        "name": "닝닝",
        "participantMask": 2,
        "profileImageUrl": "https://cdn.motif.io/profiles/9c2d.jpg",
        "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 }
      }
    ],
    "editedChallenges": [
      { "participantMask": 1, "previewImageUrl": "https://cdn.motif.io/edited/11/p1.jpg" },
      { "participantMask": 2, "previewImageUrl": "https://cdn.motif.io/edited/11/p2.jpg" },
      { "participantMask": 3, "previewImageUrl": "https://cdn.motif.io/edited/11/p3.jpg" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### `participants` — 교체 가능한 인물 슬롯

사용자가 **자기 얼굴로 바꿔 넣을 수 있는 자리**다. 추가 참여 아티스트 목록이며, 슬롯 항목은 아티스트 요약이 아니라 `artistSeq`·`name`·`participantMask`·`profileImageUrl`·`anchor` 로 구성된 전용 형태다. **챌린지 주인(`artist`)은 슬롯이 아니다** — 원이 뜨지 않고 교체되지 않는다.

| 필드 | 설명 |
|---|---|
| `participantMask` | 그 슬롯을 가리키는 **단일 비트**(1, 2, 4, 8 …). DB CHECK 로 2의 거듭제곱이 강제된다. **0 은 예약값**이라 발급되지 않는다 |
| `anchor` | **촬영 전 선택 화면(10-1~3)에서 프로필 원이 앉는 자리** *(2026-09-18 — bbox 대체)*. **썸네일 대비 0~1 비율**: `x`·`r` 은 가로, `y` 는 세로 기준. 뷰 크기 `W×H` 로 그리면 중심 `(x·W, y·H)`, 반지름 `r·W`. 시안(402 폭, 지름 80)이면 `r ≈ 0.0995`. 어드민이 아직 찍지 않은 옛 슬롯은 **`null`** — 원을 숨긴다 |

`bbox` 는 **삭제됐다**(2026-09-18, S15P21D104-618). 사각형 중심은 몸통이라 원 자리로 쓸 수 없었고, 촬영 가이드 오버레이도 쓰이지 않았다.

사용자가 여러 슬롯을 고르면 클라이언트가 **비트 OR 로 합산**해 §13.1 의 `participantMask` 로 보낸다 — 예를 들어 윈터(1)와 닝닝(2)을 고르면 `3` 이다. 무교체(원본 위 합성)는 `0` 또는 생략이다.

### `editedChallenges` — 조합별 선택 화면 이미지 *(2026-09-18)*

원을 눌러 조합을 고를 때마다 **썸네일 자리 이미지가 바뀐다**(10-2·10-3 — 배경 교체 개념). 조합 `mask` 당 한 장이고 어드민이 편집본과 함께 등록한다. 클라이언트는 고른 슬롯의 `participantMask` 를 OR 한 값과 같은 항목의 `previewImageUrl` 을 띄운다.

| 상황 | 표시 |
|---|---|
| 아무 것도 안 고름(mask 0) | `thumbnailUrl` — 항목이 따로 없다 |
| 항목이 있고 `previewImageUrl` 이 있다 | 그 이미지 |
| `previewImageUrl` 이 `null`(옛 편집본, 어드민 미업로드) 또는 항목 없음(편집본 0개 챌린지) | `thumbnailUrl` 로 폴백 |

편집본이 있으면 항목은 항상 **2ⁿ−1 개 전조합**이다(어드민 등록 규칙). 마스크 오름차순.

`availableMasks` 필드는 **삭제됐다**(R4). 대신 **운영 전제**가 있다 — 어드민이 **슬롯 조합의 편집본을 전부 등록**한다(슬롯 1~2개면 조합이 1~3개라 현실적이다). 이 전제가 깨지면 조합 유효성은 §13.1 의 404 `EDITED_CHALLENGE_NOT_FOUND` 로만 사후에 드러나, 사용자가 촬영 직전에 실패를 겪는다.

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

## 7.4 POST /api/v1/challenges/{challengeSeq}/view — 조회 이벤트

영상이 재생되면 호출한다. Redis 조회수 버퍼에 가산하고 **1분 주기로 DB 에 플러시**한다.

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

**202 Accepted** — 집계는 비동기이므로 이 응답은 "접수했다"는 뜻이다. `viewCount` 가 즉시 반영되지 않는다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` | 미존재·삭제 챌린지 |

존재 검증은 Redis 적재 전 **경량 캐시**로 한다(기본안) — 조회 이벤트마다 DB 를 때리면 집계를 Redis 로 뺀 의미가 없다.

### 미결

**1회 인정 기준이 정해지지 않았다**(원본 §16 #3) — 같은 사용자의 중복 조회, 최소 시청 시간, 본인 영상 조회 포함 여부. 정책이 확정되면 **서버 내부에서만 반영**하며 이 API 계약은 바뀌지 않는다.

**§8.5(참여 영상 조회 이벤트)는 더 이상 같은 규약이 아니다** *(2026-09-21 — [ADR-0015](../../adr/0015-redis-view-aggregation.md))*. 영상 쪽은 경로에서 `{seq}` 가 빠진 **묶음 수신**으로 바뀌었고, 존재 검증과 `404` 가 없어졌으며, 플러시 주기가 20분이다. 챌린지 조회 이벤트를 같은 구조로 옮기는 것은 **후속 티켓**이고 그때까지 이 절은 구 규약 그대로다 — 즉 지금은 **양쪽이 다르다**. §7.4 를 구현할 때 이 문서가 아니라 §8.5 를 보고 맞춘다.
