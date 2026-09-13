# §6 홈 (HOME) — 상세 명세

공통 규약은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §6.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 6.1 | GET | `/api/v1/home/hot-videos` | 필요 | 실시간 인기 참여 영상 |
| 6.2 | GET | `/api/v1/artists/ranking?size=4` | 필요 | §5.1 과 같은 API — [`05-artist.md`](05-artist.md) 참조 |
| 6.3 | GET | `/api/v1/home/activities` | 필요 | 구독 아티스트 최신 활동 |

홈 화면은 세 섹션이고 그중 신규 구현은 6.1·6.3 둘이다. 세 섹션 모두 **TTL 5분 + stale-while-revalidate** 캐싱 규약을 따르며 **폴링·소켓을 쓰지 않는다**(§1.6).

---

## 6.1 GET /api/v1/home/hot-videos — 실시간 인기 참여 영상(HOT)

**커서 페이지네이션**(§1.9). Redis 스냅샷(`rank:video:current`)의 **순서 고정**이며 `sort` 를 받지 않는다.

홈의 가로 캐러셀과 클릭 후 세로 뷰어가 **이 API 하나를 공유**한다.

### 요청

```http
GET /api/v1/home/hot-videos?cursor={opaque}&size=10
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
        "rank": 1,
        "videoSeq": 88,
        "videoUrl": "https://cdn.motif.io/results/challenge/uuid.mp4",
        "thumbnailUrl": "https://cdn.motif.io/videos/88.jpg",
        "durationMs": 15000,
        "song": { "title": "bailar toda la noche", "singerName": "mochico" },
        "author": { "userSeq": 5, "nickname": "팬123" },
        "viewCount": 3400
      }
    ],
    "prevCursor": null,
    "nextCursor": "eyJzIjoxLCJ2Ijo4OH0",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `rank` | 스냅샷 순위 |
| `videoUrl` | **공개 CDN URL**. HOT 은 PUBLIC 영상만 오르므로 서명하지 않는다 |
| `song` | 카드 하단 표기용 — 화면에는 "mochico · bailar toda la noche" 로 나온다 |
| `author` | 작성자 요약. `profileImageUrl` 은 싣지 않는다(카드에 쓰지 않음) |

**경량 항목이다**(회의 확정 R2 — §8.2 전체에서 축소). 좋아요·댓글 수, `isLiked` 같은 오버레이 정보는 **뷰어 진입 시 §8.2 단건 조회 1회**로 받는다. 클릭 즉시 `videoUrl` 로 재생을 시작할 수 있는 최소 정보만 담는 것이 목적이다.

### 캐러셀 → 뷰어 구현 노트 (R4)

**별도 API 가 없다. 같은 커서 시퀀스를 이어 쓴다.**

1. 홈 진입 — `size=10` 으로 첫 호출, `rank` 1부터 가로 슬라이드
2. 캐러셀 끝에 닿으면 `nextCursor` 로 이어 로드
3. n번째 카드 클릭 — **이미 받은 목록 + `nextCursor` + 클릭 인덱스**를 뷰어에 전달한다(공유 ViewModel). 뷰어는 n번째부터 시작해 스와이프로 순회하고, 목록이 소진되면 같은 커서로 이어 받는다. **재조회·중복 로드가 없다**
4. 순서 일관성 — 커서에 **스냅샷 버전**이 박혀 있어(§1.9 구현 노트) 5분 배치로 랭킹이 갈려도 진행 중인 시퀀스는 같은 스냅샷을 계속 페이지한다. 새 스냅샷은 **홈 재진입(당겨서 새로고침)** 때만 시작된다

### 실패 응답

**도메인 분기 없음.** 스냅샷이 비어 있으면 빈 목록 200.

### 미결

- **영상 랭킹 배치가 선행 작업이다**(§5.1 아티스트 랭킹과 동일). 배치 없이는 항상 빈 목록이다.

---

## 6.3 GET /api/v1/home/activities — 구독 아티스트 최신 활동

구독 중인 아티스트의 **신규 모티프 에셋 등록**과 **공지**를 등록 시각 기준으로 **통합 최신순** 정렬한다. 서버 고정 정렬이라 `sort` 를 받지 않는다.

### 요청

```http
GET /api/v1/home/activities?page=0&size=20
Authorization: Bearer {accessToken}
```

offset 페이지네이션이다(커서 대상이 아니다). 홈에는 상위 일부만 보이고 전체는 스크롤로 본다.

### 성공 응답 (200) — offset 페이지

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "type": "MOTIF_ASSET",
        "regDttm": "2026-09-02T05:00:00.000Z",
        "artist": {
          "artistSeq": 3,
          "name": "카리나",
          "groupName": "aespa",
          "profileImageUrl": "https://cdn.motif.io/profiles/3.jpg"
        },
        "asset": {
          "assetSeq": 21,
          "title": "9월 신규 에셋",
          "thumbnailUrl": "https://cdn.motif.io/assets/21.jpg",
          "durationMs": 9800
        }
      },
      {
        "type": "NOTICE",
        "regDttm": "2026-09-02T04:00:00.000Z",
        "artist": {
          "artistSeq": 7,
          "name": "윈터",
          "profileImageUrl": "https://cdn.motif.io/profiles/7.jpg"
        },
        "notice": {
          "noticeSeq": 5,
          "content": "컴백 소식 전체 본문...",
          "images": [
            { "imageUrl": "https://cdn.motif.io/notices/1.jpg", "displayOrder": 0 }
          ],
          "likeCount": 200,
          "commentCount": 14,
          "isLiked": false
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 37,
    "totalPages": 2,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 항목 타입

`type` 으로 분기한다. **두 타입의 필드 구성이 다르다.**

**`MOTIF_ASSET`** — `artist{artistSeq, name, groupName, profileImageUrl}` + `asset{assetSeq, title, thumbnailUrl, durationMs}`

- **좋아요·댓글 카운트가 없다.** 에셋에는 그 기능 자체가 없다(확정).
- `title` 은 **전용 컬럼 `motif_assets.title`** 값이다(R6 — DB v0.11 신설). `description`(소개 문구)과 다른 필드다.
- 클릭하면 §11.2 에셋 상세로 이동한다.

**`NOTICE`** — `artist{artistSeq, name, profileImageUrl}` + `notice{noticeSeq, content, images, likeCount, commentCount, isLiked}`

- **본문 전체 + 이미지 전체 배열**이다(`displayOrder` 순). 구 `repImageUrl` 은 폐기됐다.
- `isLiked` 를 함께 내린다 — 공지 상세 API 가 없어서(구 §10.1 삭제) **이 항목이 공지 화면의 진입 데이터**이기 때문이다. §5.5 목록 항목과 같은 필드 구성이다.
- `artist` 에 **`groupName` 이 없다** — NOTICE 쪽만 빠져 있는 것은 원본 예시 그대로다(아래 반영 대기 참조).

### 정렬

이종 통합 정렬 키는 **`(regDttm, type, seq)`** 다(기본안). `regDttm` 이 같을 때 순서를 고정하기 위한 보조 키다.

구독이 0건이면 **빈 목록 200**이다.

### 실패 응답

**도메인 분기 없음.**

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **§6.3 의 `artist` 필드 구성이 타입마다 다르다** — `MOTIF_ASSET` 에는 `groupName` 이 있고 `NOTICE` 에는 없다. 원본 예시가 그렇게 적혀 있어 그대로 옮겼지만, 같은 화면의 같은 위치에 쓰이는 값이라 **한쪽으로 통일하는 편이 자연스럽다**. 클라이언트 확인이 필요하다.
2. **§6.1·§5.1 의 랭킹 스냅샷 배치**가 명세에 없다 — API 계약만 있고 생성 주체가 정해지지 않았다.
