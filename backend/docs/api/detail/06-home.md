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

**커서 페이지네이션**(§1.9). Redis 랭킹 스냅샷의 **순서 고정**이며 `sort` 를 받지 않는다.

홈의 가로 캐러셀과 클릭 후 세로 뷰어가 **이 API 하나를 공유**한다.

### 요청

```http
GET /api/v1/home/hot-videos?cursor={opaque}&size=10
Authorization: Bearer {accessToken}
```

| 파라미터 | 기본 | 설명 |
|---|---|---|
| `cursor` | 없음 | opaque 토큰. 없으면 최신 스냅샷의 1위부터 |
| `size` | 10 | 1~100 으로 자른다(§1.9 커서 목록 공통 상한) |

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
        "videoUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8",
        "thumbnailUrl": "https://cdn.motif.io/public/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
        "durationMs": 15000,
        "song": { "title": "bailar toda la noche", "singerName": "mochico" },
        "author": { "userSeq": 5, "nickname": "팬123" },
        "viewCount": 3400
      }
    ],
    "prevCursor": null,
    "nextCursor": "MTc4ODk5ODQwMDoxMA",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `rank` | 스냅샷 순위(1-base). 커서로 이어 받아도 이어지는 번호다 |
| `videoUrl` | **공개 CDN URL**(무서명). 프리픽스가 `public/challenge/{jobUuid}/` 다 — 스냅샷에 공개 사본이 있는 영상만 올리기 때문이다(아래 "랭킹") |
| `thumbnailUrl` | 같은 프리픽스의 `thumb.jpg` |
| `song` | 카드 하단 표기용 — 화면에는 "mochico · bailar toda la noche" 로 나온다 |
| `author` | 작성자 요약. `profileImageUrl` 은 싣지 않는다(카드에 쓰지 않음) |

**경량 항목이다**(회의 확정 R2 — §8.2 전체에서 축소). 좋아요·댓글 수, `isLiked` 같은 오버레이 정보는 **뷰어 진입 시 §8.2 단건 조회 1회**로 받는다. 클릭 즉시 `videoUrl` 로 재생을 시작할 수 있는 최소 정보만 담는 것이 목적이다.

### 랭킹 (기본안)

원본 명세는 "`rank:video:current` 스냅샷 순서 고정" 이라고만 적고 점수 산출을 정하지 않았다. 아래는 이 티켓에서 정한 **기본안**이다 — §5.1 아티스트 랭킹과 같은 구조다.

```
score_video = view_count × 0.5 ^ (age / H)
```

- **조회수와 게시 후 경과 시간만** 쓴다. 좋아요는 점수에 넣지 않는다.
- `age` 의 기준은 `participation_videos.reg_dttm` = **게시 시각**. `view_count` 가 누적 카운터라 개별 조회의 발생 시각이 DB 에 없어, 걸 수 있는 시간축이 이것뿐이다.
- **가중치 설정이 없다.** 항이 하나라 상수배는 순위를 바꾸지 않는다. 설정은 반감기 `H` 와 갱신 주기·버전 보존 기간뿐이다.
- **미래 시각은 감쇠 계수를 1.0 으로 상한한다.** `reg_dttm > now` 면 지수가 음수가 되어 계수가 1을 넘고 조회수가 부풀려진다(앞선 정도가 반감기와 같으면 2배). 시계 차이로 이 일이 생기면 예외도 로그도 없이 순위만 어긋난다.

**모집단** — `visibility = PUBLIC` · `is_deleted = FALSE` · **`video_key` 가 공개 등급 프리픽스**인 참여 영상.

세 번째 조건이 필요한 이유는 §1.7 의 복사 규약이다. PUBLIC 게시는 산출물 트리를 `public/` 으로 복사할 뿐 보호 사본을 지우지 않으므로 **`visibility=PUBLIC` 인데 키가 아직 `private/` 인 구간이 존재**한다. 그 키로 무서명 URL 을 만들면 403 이고, 반대로 보호 등급 재생목록 주소(`/api/v1/media/{jobUuid}/master.m3u8`)로 내보내면 그쪽 인가가 소유자 전용이라 **남의 영상은 404** 다. HOT 은 정의상 남의 영상이므로 **공개 사본이 준비된 것만 올린다.** 복사 중인 영상은 다음 갱신 틱에 들어온다.

PUBLIC → PRIVATE 전환은 `visibility` 조건이 다음 틱에서 걸러낸다. 이미 CDN 에 캐시된 객체는 이 API 의 범위가 아니다 — 무효화는 §8.2 소관이고 비동기다.

**스냅샷과 커서** — 갱신 주기는 5분(§1.6 이 홈 캐시 TTL 을 "랭킹 배치 주기와 동일" 로 묶어 놨다). 순서는 배치가 `score DESC → videoSeq ASC` 로 **확정해서** 저장하고, 조회는 정렬에 관여하지 않는다.

커서 토큰은 `(스냅샷 버전, 마지막 rank)` 를 담는다. 버전이 박혀 있어 배치가 스냅샷을 교체해도 진행 중인 시퀀스는 같은 순서를 계속 페이지한다. 지난 버전은 **15분**간 보존하며, 그보다 오래된 커서로 요청하면 **빈 목록 + `hasNext: false`** 다 — 현재 스냅샷의 1위로 되돌리면 무한 스크롤이 원인 없이 맨 위로 점프한다.

### 캐러셀 → 뷰어 구현 노트 (R4)

**별도 API 가 없다. 같은 커서 시퀀스를 이어 쓴다.**

1. 홈 진입 — `size=10` 으로 첫 호출, `rank` 1부터 가로 슬라이드
2. 캐러셀 끝에 닿으면 `nextCursor` 로 이어 로드
3. n번째 카드 클릭 — **이미 받은 목록 + `nextCursor` + 클릭 인덱스**를 뷰어에 전달한다(공유 ViewModel). 뷰어는 n번째부터 시작해 스와이프로 순회하고, 목록이 소진되면 같은 커서로 이어 받는다. **재조회·중복 로드가 없다**
4. 순서 일관성 — 커서에 **스냅샷 버전**이 박혀 있어(§1.9 구현 노트) 5분 배치로 랭킹이 갈려도 진행 중인 시퀀스는 같은 스냅샷을 계속 페이지한다. 새 스냅샷은 **홈 재진입(당겨서 새로고침)** 때만 시작된다

### 실패 응답

**도메인 분기 없음.** 스냅샷이 비어 있으면 빈 목록 200이고, 보존 기간이 지난 버전의 커서도 **실패가 아니라 빈 목록 200** 이다. 형식이 깨진 커서만 공통 `INVALID_PARAMETER`(400) 다 — 조용히 첫 페이지로 되돌리면 클라이언트가 원인 없이 목록 점프를 겪는다.

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
          "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
        },
        "asset": {
          "assetSeq": 21,
          "title": "9월 신규 에셋",
          "thumbnailUrl": "https://cdn.motif.io/assets/2b7f1c93.jpg",
          "durationMs": 9800
        }
      },
      {
        "type": "NOTICE",
        "regDttm": "2026-09-02T04:00:00.000Z",
        "artist": {
          "artistSeq": 7,
          "name": "윈터",
          "groupName": "aespa",
          "profileImageUrl": "https://cdn.motif.io/profiles/c4a7e9d2.jpg"
        },
        "notice": {
          "noticeSeq": 5,
          "content": "컴백 소식 전체 본문...",
          "images": [
            { "imageUrl": "https://cdn.motif.io/notices/9f3b2a71.jpg", "displayOrder": 0 }
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

**`MOTIF_ASSET`** — **공통 아티스트 요약**(README §3.5) + `asset{assetSeq, title, thumbnailUrl, durationMs}`

- **좋아요·댓글 카운트가 없다.** 에셋에는 그 기능 자체가 없다(확정).
- `title` 은 **전용 컬럼 `motif_assets.title`** 값이다(R6 — DB v0.11 신설). `description`(소개 문구)과 다른 필드다.
- 클릭하면 §11.2 에셋 상세로 이동한다.

**`NOTICE`** — `artist{artistSeq, name, profileImageUrl}` + `notice{noticeSeq, content, images, likeCount, commentCount, isLiked}`

- **본문 전체 + 이미지 전체 배열**이다(`displayOrder` 순). 구 `repImageUrl` 은 폐기됐다.
- `isLiked` 를 함께 내린다 — 공지 상세 API 가 없어서(구 §10.1 삭제) **이 항목이 공지 화면의 진입 데이터**이기 때문이다. §5.5 목록 항목과 같은 필드 구성이다.
- `artist` 는 `MOTIF_ASSET` 과 **같은 공통 아티스트 요약**이다 — 원본 예시는 NOTICE 쪽에서 `groupName` 을 빠뜨렸지만 타입을 통일했다.

### 정렬

이종 통합 정렬 키는 **`(regDttm, type, seq)`** 다(기본안). `regDttm` 이 같을 때 순서를 고정하기 위한 보조 키다.

구독이 0건이면 **빈 목록 200**이다.

### 실패 응답

**도메인 분기 없음.**

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **§6.3 의 `artist` 를 두 타입 모두 공통 아티스트 요약으로 통일했다** — 원본 예시는 `MOTIF_ASSET` 에만 `groupName` 이 있고 `NOTICE` 에는 없었다. 같은 화면의 같은 위치에 쓰이는 값이라 응답 타입을 나눌 이유가 없어 맞췄다(README §3.5).
2. **§6.1 영상 랭킹의 점수·모집단·스냅샷 규약을 기본안으로 채웠다** — 원본은 `rank:video:current` 라는 키 이름과 "순서 고정" 만 적고 무엇으로 점수를 내는지, 무엇이 모집단인지, 버전을 얼마나 보존하는지를 정하지 않았다. 위 "랭킹 (기본안)" 절이 그 지점이다. §5.1 아티스트 랭킹의 공식도 같은 사정으로 구현에서 정한 기본안이다.
3. **§6.1 의 URL 예시 프리픽스를 `results/challenge/` → `public/challenge/` 로 고쳤다** — 원본 예시가 README §5 산출물 키 규약(2026-09-15 확정)보다 앞서 쓰였다. §4.1 그리드 예시는 이미 새 규약을 따르고 있다. **같은 낡은 프리픽스가 §8.1·§8.2·§11.5·§11.6·§13 예시에 남아 있다** — 각 API 티켓에서 같이 고쳐야 한다.
