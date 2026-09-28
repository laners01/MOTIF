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
        "rankDelta": 2,
        "videoSeq": 88,
        "videoUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
        "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
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
| `rankDelta` | **직전 순위 − 현재 순위**. 상승이 양수, 하락이 음수(5위→3위 = `+2`, 3위→5위 = `-2`). **신규 진입은 `null`** — §5.1 과 같은 규약이다. 대비 구간은 **1시간**(배치 3틱) |
| `videoUrl` | CDN URL + **프리픽스 서명**. 참여 영상은 `visibility` 와 무관하게 보호 등급이라 남의 `PUBLIC` 영상에도 서명이 붙는다(§1.7 · ADR-0013). 프리픽스는 `private/challenge/{jobUuid}/` 다 |
| `thumbnailUrl` | 같은 프리픽스의 `thumb.jpg` — 단일 파일이라 URL 하나짜리 서명이다 |

**캐시 TTL(5분)보다 서명 만료(10분)가 길다.** 정상 경로에서는 캐시된 응답의 서명이 아직
유효하다. 다만 §1.6 이 **stale-while-revalidate** 를 허용하므로 갱신이 늦어지면 만료된
파라미터를 쥔 채 재생을 시작할 수 있다 — 그때는 조각이 403 이고, 클라이언트가
`POST /media/{jobUuid}/signature` 로 새로 받아 재시도한다(ADR-0012). **응답을 그대로
오래 보관하지 말 것.**
| `song` | 카드 하단 표기용 — 화면에는 "mochico · bailar toda la noche" 로 나온다 |
| `author` | 작성자 요약. `profileImageUrl` 은 싣지 않는다(카드에 쓰지 않음) |

**경량 항목이다**(회의 확정 R2 — §8.2 전체에서 축소). 좋아요·댓글 수, `isLiked` 같은 오버레이 정보는 **뷰어 진입 시 §8.2 단건 조회 1회**로 받는다. 클릭 즉시 `videoUrl` 로 재생을 시작할 수 있는 최소 정보만 담는 것이 목적이다.

### 랭킹 *(2026-09-21 재정의 — [ADR-0015](../../adr/0015-redis-view-aggregation.md))*

구 공식은 `view_count × 0.5^(age/H)` 였다. **시간 감쇠를 걷어냈다** — `view_count` 가 누적
카운터라 개별 조회의 발생 시각이 DB 에 없었고, 그래서 걸 수 있는 시간축이 **게시 시각**
하나뿐이었기 때문에 감쇠를 썼다. 20분 버킷이 생기면서 "지금 얼마나 보고 있는가" 를 직접
알 수 있게 됐고, 그와 함께 **게시가 오래됐다는 이유만으로 영원히 감점되는 문제**가 사라진다.
오래된 영상이 지금 뜨면 1위에 올라와야 한다.

```
tScore = log10(1 + totalViews)    / log10(1 + T0)
rScore = log10(1 + recentViews1h) / log10(1 + R0)
dRel   = (recentViews1h − previousViews1h) / (previousViews1h + K)
dScore = 0.5 + 0.5 × clamp(dRel / D0, −1, +1)

score  = Wt·tScore + Wr·rScore + Wd·dScore        (기본 0.3 / 0.5 / 0.2)
```

| 값 | 출처 |
|---|---|
| `totalViews` | `participation_videos.view_count` — **DB 가 누적 조회수의 정본**이다 |
| `recentViews1h` | 최근 **3개** 버킷의 합 |
| `previousViews1h` | 그 이전 **3개** 버킷의 합 |

- **세 항이 서로 다른 것을 잰다** — 누적 규모(절대) · 최근 규모(절대) · 모멘텀(상대). 절대
  증감을 따로 넣지 않는 것은 `recentViews1h` 가 이미 최근의 절대량이기 때문이다. 넣으면
  규모가 큰 영상에 같은 신호가 두 번 가산된다.
- **증감은 절대값이 아니라 기존 값 대비다.** `+100` 이라도 직전이 50이었으면 탄력이고
  5,000이었으면 관성이다. 분모의 완충 상수 `K` 가 없으면 **`0 → 3` 같은 노이즈가
  `1000 → 2000` 을 이긴다** — 비율 방식의 고전적 함정이다.
- **정규화 기준은 그 틱의 최대값이 아니라 고정 상수 `T0`·`R0`·`D0` 다.** 최대값으로 나누면
  다른 영상이 떡상했다는 이유만으로 내 점수가 내려가고, 1위 영상이 삭제되면 전원의 점수가
  움직인다. `rankDelta` 를 내보내는 이상 그것은 **아무도 아무 것도 하지 않았는데 뜨는 거짓
  변동**이 된다. 고정 기준이면 점수가 절대값이라 시간에 걸쳐 재현·검증된다.
- **`tScore`·`rScore` 는 1로 자르지 않는다.** 기준을 넘는 영상이 전부 같은 값이 되면
  최상위권의 변별이 사라진다. 순위만 쓰므로 범위가 `[0,1]` 일 필요가 없다.
- **좋아요·댓글 수는 점수에 넣지 않는다**(구 명세와 같다). 응답에도 싣지 않는다.

**모집단** — `visibility = PUBLIC` · `is_deleted = FALSE` · **`view_count > 0`** 인 참여 영상
(삭제된 챌린지 소속·탈퇴 사용자 제외 — §1.8).

`view_count > 0` 은 **조회가 한 번이라도 발생한 뒤부터 랭킹에 든다**는 뜻이다. 한 번 들어온
영상은 조용해져도 목록에 남고, 최근·증감 항(70%)이 떨어지면서 자연히 뒤로 밀린다. 게시 직후
상세 화면으로 이어지는 흐름이라 실제로는 게시와 거의 동시에 진입한다.

**적재 상한이 있다** — 점수 순으로 상위 `keep`(기본 500)개만 스냅샷에 담는다. 점수 임계로
자르지 않는다: 점수가 고정 기준 정규화라 임계를 둘 수는 있지만, 트래픽이 적은 시간대에
목록이 통째로 비는 편이 순위를 덜 보여 주는 것보다 나쁘다. 개수 상한은 결과가 예측된다.

**키 프리픽스는 보지 않는다** *(2026-09-19 — [ADR-0013](../../adr/0013-signed-only-media.md))*. 예전에는 "`video_key` 가 공개 등급 프리픽스" 라는 세 번째 조건이 있었다. §1.7 의 복사 규약 때문에 `visibility=PUBLIC` 인데 키가 아직 `private/` 인 구간이 존재했고, 그 영상은 남이 재생할 수 없다고 봤기 때문이다. 공개 사본 트리가 폐기되면서 **그 구간 자체가 사라졌고**, 참여 영상은 전부 보호 등급이라 프리픽스로 거를 것이 없다. 남의 `PUBLIC` 영상도 `MediaPlaylistQueryService` 가 소유자 검사 없이 통과시키므로 정상적으로 재생된다.

PUBLIC → PRIVATE 전환은 `visibility` 조건이 다음 틱에서 걸러낸다. 재생 쪽은 서명 갱신 TTL 안에 닫힌다(§8.9).

**읽을 때 한 번 더 거른다.** 스냅샷은 최대 20분 전 상태라 그 사이 삭제·비공개로 바뀐 영상이
남아 있다. 조회는 `videoSeq` 묶음을 DB 에서 다시 읽으면서 §1.8 비노출과 **요청자의
`video_hides`(관심 없음 — §8.6)** 를 건다. 후자는 예전에 §8.1 피드에만 걸려 있어 관심 없음을
누른 영상이 홈 HOT 에는 계속 떴다.

걸러진 자리는 **`rank` 를 그대로 두고 건너뛴다.** 그래서 한 페이지의 항목 수가 요청한 `size`
보다 적을 수 있고 `rank` 에 빠진 번호가 생긴다 — 번호를 다시 매기면 같은 커서로 이어 받은
페이지와 어긋난다.

**스냅샷과 커서** — 갱신 주기는 **20분**이다(조회수 집계 배치와 같은 틱 — ADR-0015). 순서는 배치가 `score DESC → videoSeq ASC` 로 **확정해서** 저장하고, 조회는 정렬에 관여하지 않는다. 동점 보조 키가 `videoSeq ASC` 인 것은 같은 점수일 때 먼저 게시된 영상을 앞에 두려는 것이다.

커서 토큰은 `(스냅샷 버전, 마지막 rank)` 를 담는다. 버전이 박혀 있어 배치가 스냅샷을 교체해도 진행 중인 시퀀스는 같은 순서를 계속 페이지한다. 지난 버전은 **2시간**간 보존하며, 그보다 오래된 커서로 요청하면 **빈 목록 + `hasNext: false`** 다 — 현재 스냅샷의 1위로 되돌리면 무한 스크롤이 원인 없이 맨 위로 점프한다.

보존 기간이 15분에서 2시간으로 늘어난 것은 커서 사정이 아니라 **`rankDelta` 때문**이다. 대비
대상이 3틱 전(=1시간 전) 버전이라 그 키가 살아 있어야 한다. 보존이 대비 구간보다 짧으면
**`rankDelta` 가 조용히 전원 `null` 이 된다** — 예외도 로그도 남지 않는다. 커서 없는 최초
진입은 언제나 최신 버전이라 "낡은 실시간" 문제는 생기지 않는다.

**`rankDelta` 는 배치가 계산해 둔다.** 조회 시점에 이전 버전을 통째로 읽으면 요청마다 스냅샷
전량을 읽게 된다. 배치가 새 순위를 적재하면서 `videoSeq → rankDelta` 를 함께 적어 두고,
조회는 그 페이지분만 꺼낸다. 이전 버전에 없던 영상은 값을 적지 않는다(= `null`, 신규 진입).
**랭킹이 처음 만들어진 틱은 전원 `0` 으로 심는다** — 그래야 `null` 이 진짜 신규 진입 전용으로
남는다(§5.1 과 같은 판단).

### 캐러셀 → 뷰어 구현 노트 (R4)

**별도 API 가 없다. 같은 커서 시퀀스를 이어 쓴다.**

1. 홈 진입 — `size=10` 으로 첫 호출, `rank` 1부터 가로 슬라이드
2. 캐러셀 끝에 닿으면 `nextCursor` 로 이어 로드
3. n번째 카드 클릭 — **이미 받은 목록 + `nextCursor` + 클릭 인덱스**를 뷰어에 전달한다(공유 ViewModel). 뷰어는 n번째부터 시작해 스와이프로 순회하고, 목록이 소진되면 같은 커서로 이어 받는다. **재조회·중복 로드가 없다**
4. 순서 일관성 — 커서에 **스냅샷 버전**이 박혀 있어(§1.9 구현 노트) 배치가 랭킹을 갈아도 진행 중인 시퀀스는 같은 스냅샷을 계속 페이지한다. 새 스냅샷은 **홈 재진입(당겨서 새로고침)** 때만 시작된다

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

**`NOTICE`** — **공통 아티스트 요약** + `notice{noticeSeq, content, images, likeCount, commentCount, isLiked}`

- **본문 전체 + 이미지 전체 배열**이다(`displayOrder` 순). 구 `repImageUrl` 은 폐기됐다.
- `isLiked` 를 함께 내린다 — 공지 상세 API 가 없어서(구 §10.1 삭제) **이 항목이 공지 화면의 진입 데이터**이기 때문이다. §5.5 목록 항목과 같은 필드 구성이다.
- `artist` 는 `MOTIF_ASSET` 과 **같은 공통 아티스트 요약**이다 — 원본 예시는 NOTICE 쪽에서 `groupName` 을 빠뜨렸지만 타입을 통일했다.

**타입에 없는 칸은 응답에서 아예 뺀다.** `MOTIF_ASSET` 항목에 `"notice": null` 이 실리지 않고 그 반대도 마찬가지다 — 클라이언트가 `type` 하나로 분기하면 되도록.

### 모집단과 정렬 (기본안)

원본은 "구독 아티스트의 신규 모티프 에셋 등록 + 공지" 라고만 적고 제외 조건과 보조 정렬을 정하지 않았다. 아래는 이 티켓에서 정한 **기본안**이다.

**모집단** — 구독 중인 **비삭제** 아티스트의

| 타입 | 조건 |
|---|---|
| `MOTIF_ASSET` | `motif_assets.is_active = TRUE` |
| `NOTICE` | `notices.is_deleted = FALSE` |

에셋에 `is_active` 를 거는 것은 판매가 중단된 에셋을 남기면 **눌렀을 때 §11.2 상세가 막히는 카드**가 목록에 생기기 때문이다. 아티스트 삭제는 구독 조회(§4.6·§4.7 공용)가 이미 걸러 준다.

**정렬** — `regDttm DESC, type ASC, seq DESC`

원본의 `(regDttm, type, seq)` 를 최신순으로 편 것이다. 보조 키 둘이 없으면 **같은 초에 등록된 항목이 페이지마다 자리를 바꿔** offset 페이지에서 중복·누락이 난다. `type` 이 먼저인 것은 값이 정해져 있어 결정적이기 때문이고(`MOTIF_ASSET` < `NOTICE`), `seq` 는 최신이 앞이라 DESC 다.

구독이 0건이면 **빈 목록 200**이다 — 목록 쿼리를 아예 돌리지 않는다.

### 실패 응답

**도메인 분기 없음.**

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **§6.3 의 `artist` 를 두 타입 모두 공통 아티스트 요약으로 통일했다** — 원본 예시는 `MOTIF_ASSET` 에만 `groupName` 이 있고 `NOTICE` 에는 없었다. 같은 화면의 같은 위치에 쓰이는 값이라 응답 타입을 나눌 이유가 없어 맞췄다(README §3.5).
2. **§6.1 영상 랭킹의 점수·모집단·스냅샷 규약을 구현에서 정했다** — 원본은 `rank:video:current` 라는 키 이름과 "순서 고정" 만 적고 무엇으로 점수를 내는지, 무엇이 모집단인지, 버전을 얼마나 보존하는지를 정하지 않았다. 위 "랭킹" 절이 그 지점이다. §5.1 아티스트 랭킹의 공식도 같은 사정으로 구현에서 정한 기본안이다.
   **2026-09-21 에 점수 공식과 응답이 한 번 더 바뀌었다**(ADR-0015) — 시간 감쇠를 걷어내고 최근 1시간·증감 항을 넣었으며 `rankDelta` 칸이 생겼다. 안드로이드가 이 칸을 읽어야 한다.
3. **보호 등급 URL 예시를 `private/{kind}/{jobUuid}/` + 서명 파라미터로 통일했다** *(2026-09-22 정리)*. 원본 예시가 README §5 산출물 키 규약보다 앞서 쓰여 `results/challenge/` 를 가리키고 있었고, 그 뒤 잠깐 쓰였던 `public/` 공개 사본 트리는 [ADR-0013](../../adr/0013-signed-only-media.md) 이 폐기했다. 같은 낡은 프리픽스가 §4.9·§5.3·§8.1·§8.2·§8.9·§11.5·§11.8·§13.3 예시에 남아 있던 것을 **이번에 전부 고쳤고, Swagger `@ExampleObject` 도 같은 값으로 맞췄다**(README 머리말 — 문서와 Swagger 가 다른 값을 보여주면 둘 다 신뢰를 잃는다).
