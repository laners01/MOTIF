# §5 아티스트 (ARTIST) — 상세 명세

공통 규약(봉투·에러 코드·시각 포맷·공통 실패 분기·비노출 규칙)은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §5 · DB 명세서 v0.15 §9·§10.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 5.1 | GET | `/api/v1/artists/ranking` | 필요 | §3.1 온보딩·§6.2 홈 Who's On 공용 |
| 5.2 | GET | `/api/v1/artists/search` | 필요 | §3.2 온보딩 공용 |
| 5.3 | GET | `/api/v1/artists/{artistSeq}` | 필요 | |
| 5.4 | GET | `/api/v1/artists/{artistSeq}/challenges` | 필요 | |
| 5.5 | GET | `/api/v1/artists/{artistSeq}/notices` | 필요 | |
| 5.6 | POST | `/api/v1/artists/{artistSeq}/subscription` | 필요 | |
| 5.7 | DELETE | `/api/v1/artists/{artistSeq}/subscription` | 필요 | |
| 5.8 | GET | `/api/v1/users/me/motif-results?artistSeq=` | 필요 | §11.5 와 같은 API — [`11-motif.md`](11-motif.md) 참조 |
| 5.9 | GET | `/api/v1/artists/autocomplete` | 필요 | |

## 이 절 전체에 걸리는 스키마 사실

**아티스트 이름은 `artists` 테이블에 없다.** `artists` 는 `users.role='ARTIST'` 사용자의 도메인 확장이라(`uq_artists_user`), 이름·프로필 이미지는 `users` 에서 온다.

| 응답 필드 | 출처 |
|---|---|
| `name` | `artists.user_seq → users.nickname` |
| `profileImageUrl` | `users.profile_image_key` → 공개 CDN URL |
| `groupName` | `artists.group_seq → artist_groups.group_name`. **`group_seq` 는 NULL 허용**이라 솔로면 `null` |
| `bio` | `artists.bio` |

검색·자동완성의 대상 컬럼도 같다 — `users.nickname` 과 `artist_groups.group_name` 이며, 둘 다 pg_trgm GIN 인덱스가 있다(`gin_users_nickname`, `gin_groups_name`).

---

## 5.1 GET /api/v1/artists/ranking — 아티스트 랭킹

Redis 스냅샷(`rank:artist:current`) 기반의 **서버 고정 정렬**이다. `sort` 파라미터를 받지 않는다.

홈의 "Who's On TOP4"(§6.2)와 온보딩의 인기 아티스트 목록(§3.1)이 **같은 API 를 재사용**한다 — 필드도 캐시 정책도 같아서 나눌 실익이 없다.

### 요청

```http
GET /api/v1/artists/ranking?filter=ALL&page=0&size=20
Authorization: Bearer {accessToken}
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `filter` | `ALL` | `ALL` \| `SUBSCRIBED`(내가 구독) \| `UNSUBSCRIBED`(미구독) |
| `page` | 0 | 0-base |
| `size` | 20 | 최대 100. **홈 TOP4 는 `size=4`** |

### 성공 응답 (200) — offset 페이지

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
        "artistSeq": 3,
        "name": "카리나",
        "groupName": "aespa",
        "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
        "isSubscribed": false
      },
      {
        "rank": 2,
        "rankDelta": null,
        "artistSeq": 7,
        "name": "윈터",
        "groupName": "aespa",
        "profileImageUrl": null,
        "isSubscribed": true
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 58,
    "totalPages": 3,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `rank` | 스냅샷 순위(1-base). **`filter` 로 걸러도 다시 매기지 않는다** — 전체 스냅샷 기준이다. "내 구독 아티스트 중 3위"가 아니라 "전체 3위"를 보여주는 것이 랭킹 화면의 의미다 |
| `rankDelta` | **직전 스냅샷 순위 − 현재 순위**. 상승이 양수, 하락이 음수(5위→3위 = `+2`, 3위→5위 = `-2`). **신규 진입은 `null`** |
| `isSubscribed` | 요청자의 구독 여부 |
| `totalElements` | **`filter` 를 적용한 뒤**의 개수다. `ALL` 이면 스냅샷 ZSET 크기와 같다 |

### 점수 공식 **(기본안 — 원본 미기재)**

```
score_artist = Σ_c ( view_count_c × Wv + participant_count_c × Wp ) × 0.5 ^ (age_c / H)

Wv = 1     조회 1회
Wp = 100   참여 1건
H  = 14일  반감기
age_c = 기준 시각 − challenges.reg_dttm
```

- 대상은 **해당 아티스트의 비삭제 챌린지**뿐이다.
- **챌린지 등록 자체에는 점수를 주지 않는다.** 점수의 출처는 챌린지가 실제로 받은 반응이고, 그 반응을 업로드 시각 기준으로 깎는다. 등록 행위에 점수를 주면 반응 없는 챌린지를 여러 개 올리는 것만으로 순위가 올라 인기 랭킹이 아니게 된다.
- 참여 1건을 조회 100회로 보는 것은 **참여가 촬영·업로드를 요구하는 행위**이기 때문이다.
- **감쇠 기준이 `challenges.reg_dttm` 인 이유**: `view_count`·`participant_count` 가 누적 카운터라 개별 조회·참여의 발생 시각이 DB 에 없다. 걸 수 있는 시간축이 챌린지 등록 시각 하나뿐이다.

숫자로 보면 의도가 드러난다 — 조회수가 3배 넘게 많은 옛날 챌린지가 최근 챌린지에 밀린다.

| 챌린지 | 조회수 | 나이 | 감쇠 계수 | 기여 점수 |
|---|---|---|---|---|
| A | 10,000 | 30일 | 0.2264 | 2,264 |
| B | 3,000 | 2일 | 0.9057 | **2,717** |

`Wv`·`Wp`·`H` 는 원본 명세에 근거가 없는 수라 설정으로 뺀다 — `motif.ranking.weight.view` · `.participant` · `motif.ranking.half-life-days`.

### 선행 티켓 전까지 전원 0점이다 — 버그가 아니다

점수의 두 입력을 올리는 경로가 아직 없다.

| 입력 | 증가 경로 | 상태 |
|---|---|---|
| `challenges.view_count` | §7.4 `POST /challenges/{seq}/view` | 미구현 |
| `challenges.participant_count` | 참여 영상 업로드 | 미구현 |

따라서 현재 랭킹은 **전 아티스트 0점 동점 → `artistSeq` ASC 고정 목록**이고 `rankDelta` 는 전원 `null` 이다. 두 선행 티켓이 머지되면 **코드 변경 없이** 순위가 갈리기 시작한다.

### 스냅샷 (서버)

| 키 | 갱신 주기 | 내용 |
|---|---|---|
| `rank:artist:current` | **5분** | 현재 순위 ZSET — §1.6 클라 TTL 5분과 같은 주기다 |
| `rank:artist:prev` | **10분** | `rankDelta` 의 기준이 되는 직전 순위 ZSET |

- **비삭제 아티스트는 챌린지가 0개여도 넣는다**(score 0). 빼면 온보딩 인기 목록(§3.1)과 `UNSUBSCRIBED` 필터에서 신인이 통째로 사라진다.
- **동점 정렬은 `artistSeq` ASC 고정이고, 애플리케이션에서 확정한다.** ZSET member 사전순에 기대면 안 된다 — `ZREVRANGE` 는 동점일 때 member 를 역사전순으로 주므로 `artistSeq` DESC 가 나온다.
- 적재는 임시 키에 쓴 뒤 `RENAME` 한다. 아티스트가 0명이면 ZSET 이 비어 임시 키 자체가 생기지 않으므로(Redis 에 빈 키는 없다) 그때는 대상 키를 `DELETE` 한다.
- **키에 TTL 을 걸지 않는다.** 배치가 매번 덮는다. TTL 을 걸면 배치가 멈췄을 때 랭킹이 통째로 사라진다 — 낡은 랭킹이 없는 랭킹보다 낫다. §1.6 의 "TTL 5분"은 **클라이언트 캐시** 규약이지 Redis 키 만료가 아니다.
- 최초 배포로 `:prev` 가 없으면 첫 `:current` 적재 때 같은 내용으로 함께 심는다 → 전원 `rankDelta = 0`. 그래야 `null` 이 **진짜 신규 아티스트** 전용으로 남는다.

### 서버 처리

1. `rank:artist:current`(ZSET)에서 **전량**을 꺼낸다. 아티스트가 수백 규모라 전량이 싸고, 구간만 꺼내면 4번 필터에서 걸러진 만큼 페이지가 비어 버린다
2. `score DESC → artistSeq ASC` 로 정렬을 확정하고 순서대로 `rank` 를 매긴다(1-base)
3. `rank:artist:prev` 에서 같은 아티스트의 직전 순위를 찾아 `rankDelta` 를 계산한다 — 없으면 `null`
4. `filter` 가 `SUBSCRIBED`/`UNSUBSCRIBED` 면 요청자의 구독 아티스트를 **1회 조회**해 거른다. 항목마다 조회하면 그대로 N+1 이다
5. `page`·`size` 로 자른 뒤, **그 페이지 분량만** 아티스트 메타(`name`·`groupName`·`profileImageUrl`)를 DB 에서 채운다
6. `profileImageUrl` 은 공개 등급이다 — 서명하지 않는다(§1.7)

**`filter` 는 Redis 가 아니라 애플리케이션 단계에서 적용한다** (미결 해소). 구독 정보는 Redis 에 없고, 자르기 전에 걸러야 페이지 크기가 일정하다.

### 캐싱 (클라이언트 규약 §1.6)

- **TTL 5분** + stale-while-revalidate. 랭킹 배치 주기와 같다.
- 탭 복귀 시 TTL 안이면 요청하지 않는다. 지났으면 **기존 화면을 유지한 채 조용히 갱신**한다.
- 당겨서 새로고침만 강제 갱신이다. **폴링·소켓 금지.**
- **서버는 갱신을 push 하지 않는다.** SSE·WebSocket 을 쓰지 않으며 FCM 은 §14 전용이라 랭킹과 무관하다. 스냅샷이 갈려도 서버는 아무에게도 알리지 않고, 앱이 **화면 진입·포그라운드 복귀·당겨서 새로고침** 시점에 당겨 간다.
- 이 규약은 **클라이언트 책임**이다(§1.6 제목). 서버는 강제하지 않는다 — "5분마다 갱신된다"를 타이머 폴링으로 구현하지 않도록 계약을 넘길 때 함께 짚는다.

### 실패 응답

**도메인 분기 없음.** 스냅샷이 비어 있으면 빈 목록 200.

Redis 가 죽으면 §1.4 공통 500 이다 — 랭킹은 캐시가 아니라 **데이터 출처**라 DB 폴백을 두지 않는다.

---

## 5.2 GET /api/v1/artists/search — 검색 (확정 실행)

사용자가 **엔터를 치거나 서제스트 항목을 고른 순간** 1회 호출한다. 타이핑 중 호출은 §5.9 자동완성이다.

pg_trgm 유사도 검색이라 오타가 보정된다. **페이지네이션이 없고** 상위 `size` 건만 준다.

### 요청

```http
GET /api/v1/artists/search?q=카리나&size=20
Authorization: Bearer {accessToken}
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `q` | — | 검색어 |
| `size` | 20 | 상위 N 건 |

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "artistSeq": 3,
        "name": "카리나",
        "groupName": "aespa",
        "groupName": "aespa",
        "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
        "isSubscribed": true
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- **결과는 항상 멤버 단위**다. 그룹명이 매치되면 **소속 멤버 전원**을 반환한다.
- 0건도 200 + 빈 배열이다.
- 최근 검색어는 서버가 저장하지 않는다 — **Android 로컬 처리**다(원본 §16 #19 로 기능 제외).

### 실패 응답

**도메인 분기 없음.**

---

## 5.9 GET /api/v1/artists/autocomplete — 자동완성

**타이핑 중 글자가 바뀔 때마다** 호출되는 경량 서제스트다. 응답을 최소화하려고 §5.2 와 분리했다.

### 요청

```http
GET /api/v1/artists/autocomplete?q=카리&size=10
Authorization: Bearer {accessToken}
```

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**공통 아티스트 요약**(README §3.5)을 그대로 쓴다. 원본 §16 #18 의 "자동완성 응답에 프로필 이미지를 포함할지"는 **포함으로 확정**했다 — 호출 빈도가 높긴 하지만 URL 문자열 하나 차이이고, 이것 때문에 응답 타입을 따로 만드는 비용이 더 크다.

### 서버 매칭 전략 (기본안)

| `q` 길이 | 방식 | 이유 |
|---|---|---|
| 1~2자 | **접두 `ILIKE 'q%'`** | trigram 은 3글자 조각 기반이라 짧은 질의에 약하다 |
| 3자 이상 | pg_trgm 유사도(`%`, `word_similarity`) | 오타 보정 |

둘 다 기존 GIN 인덱스로 커버된다(`gin_trgm_ops` 는 LIKE 가속을 포함한다). 유사도 내림차순, 상위 10건 고정.

아티스트가 수백 규모라 **DB 직조회로 충분**하다 — 캐시를 두지 않는다.

### 클라이언트 구현 규약

- **입력 디바운스 300ms** + **직전 요청 취소 후 최신 질의만 반영** (코루틴 `Flow.debounce` + `collectLatest`/`flatMapLatest`). 응답 역전으로 옛 결과가 새 결과를 덮어쓰는 것을 막는다.
- 한글 IME 조합 중 텍스트도 그대로 보낸다 — 부분 음절도 접두 매칭된다.
- 빈 문자열이면 **호출하지 않고** 목록을 비운다.

### 실패 응답

**도메인 분기 없음.**

---

## 5.3 GET /api/v1/artists/{artistSeq} — 아티스트 상세

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "artistSeq": 3,
    "name": "카리나",
    "groupName": "aespa",
    "groupName": "aespa",
    "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
    "bio": "aespa의 카리나입니다.",
    "subscriberCount": 15230,
    "challengeCount": 121,
    "isSubscribed": true,
    "subscribedDays": 492,
    "pass": { "active": true, "expiresDttm": "2026-10-01T00:00:00.000Z" },
    "myMotifVideos": [
      {
        "resultSeq": 10,
        "videoUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8",
        "thumbnailUrl": "https://cdn.motif.io/results/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg",
        "durationMs": 9800
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 산출 규칙 |
|---|---|
| `subscriberCount` | `artists.subscriber_count` (거울 카운터) |
| `challengeCount` | 그 아티스트의 **비삭제 챌린지 수** |
| `subscribedDays` | 구독 경과일 — 화면의 "WITH n days". **미구독이면 `null`** |
| `pass.active` | `motif_passes.status='ACTIVE' AND expires_dttm > now` |
| `pass.expiresDttm` | 위가 참일 때만 값. 아니면 `null` |
| `myMotifVideos` | **내가 이 아티스트와 만든 모티프 결과물 최신 5개**. `videoUrl` 은 HLS 마스터 재생목록이고 보호 등급이라 core-api 가 서명해 다시 쓴 재생목록을 가리킨다. 없으면 **빈 배열**(상단 영역 미노출) |

- `myMotifVideos` 는 화면 상단 **자동재생**용이다. 구 `hasMyMotifResults` 를 대체했다. 전체 목록·더보기는 §5.8(= §11.5 의 `artistSeq` 필터).
- `pass.active=false` 면 클라이언트는 "모티프 패스 알아보기"를 노출한다(→ §11.4).

### 연속 재생 (클라이언트 제안)

배열 순서대로 플레이어 재생목록(ExoPlayer `ConcatenatingMediaSource`)에 넣어 하나가 끝나면 다음으로 넘어간다. 보호 등급이라 각 `videoUrl` 은 core-api 가 서명해 다시 쓴 재생목록을 가리킨다. 서명이 만료(10분)돼 조각 로드가 403 이면 **그 재생목록을 다시 받으면 된다** — core-api 가 새 서명으로 내려주므로 서버에 추가 API 가 필요 없다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트 |

```json
{
  "result": "FAIL",
  "code": "ARTIST_NOT_FOUND",
  "message": "존재하지 않는 아티스트입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| `myMotifVideos` 개수 | 최신 5개 | 원본 §5.3 기본안 (§16 #14 클라 확인 대기) |

---

## 5.4 GET /api/v1/artists/{artistSeq}/challenges — 챌린지 그리드

**비페이지 — 전량 반환**, 최신순. 그리드용 경량 항목이다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      { "challengeSeq": 11, "thumbnailUrl": "https://cdn.motif.io/challenges/7e2a1b9c-3d4e-5f60-8a1b-2c3d4e5f6071/thumb.jpg", "viewCount": 120000 }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- **`title` 은 없다** — `challenges.title` 컬럼 자체가 삭제됐다(R3). 화면 식별은 썸네일과 곡명(§7.1)으로 한다.
- `viewCount` 는 전 영상 그리드 공통 규칙이다.
- 삭제 챌린지는 제외한다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트 |

---

## 5.5 GET /api/v1/artists/{artistSeq}/notices — 공지 목록

**커서 페이지네이션**(§1.9), **최신순 고정**.

**별도의 공지 상세 API 가 없다**(구 §10.1 삭제). 공지 화면에 필요한 것을 목록 항목이 전부 갖는다 — 본문 전체, 이미지 전체, 좋아요 상태까지.

### 요청

```http
GET /api/v1/artists/{artistSeq}/notices?cursor={opaque}&size=20
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
        "noticeSeq": 5,
        "artist": {
          "artistSeq": 3,
          "name": "카리나",
          "groupName": "aespa",
          "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg"
        },
        "content": "전체 본문...",
        "images": [
          { "imageUrl": "https://cdn.motif.io/notices/9f3b2a71.jpg", "displayOrder": 0 },
          { "imageUrl": "https://cdn.motif.io/notices/4c8d5e02.jpg", "displayOrder": 1 }
        ],
        "likeCount": 200,
        "commentCount": 14,
        "isLiked": true,
        "regDttm": "2026-09-01T09:00:00.000Z"
      }
    ],
    "prevCursor": null,
    "nextCursor": "eyJ0IjoiMjAyNi0wOS0wMVQwOTowMDowMFoiLCJuIjo1fQ",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `content` | **본문 전체**. 잘라 보내지 않는다 |
| `images` | `displayOrder` 오름차순 **전체 배열**. 대표 이미지 전용 `repImageUrl` 은 쓰지 않는다. 이미지가 없으면 `[]` |
| `isLiked` | 요청자의 좋아요 여부 — 공지 화면 진입 시 하트 초기 상태 |
| `artist` | **공통 아티스트 요약**(README §3.5) |

- `title` 은 없다(`notices` 에 컬럼 자체가 없다).
- 좋아요 토글은 §10.2, 댓글은 §9(`type=notice`)다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트 |

---

## 5.6 POST · 5.7 DELETE /api/v1/artists/{artistSeq}/subscription — 구독 / 구독 취소

무료 팔로우다. **패스 구매(§11.4)와는 완전히 별개**다.

### 요청

본문 없음. 경로의 `artistSeq` 가 대상이다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "isSubscribed": true, "subscriberCount": 15231 },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

§5.7(취소)은 `"isSubscribed": false` 에 감소된 카운트다.

### 서버 처리

- `subscriptions` 행 생성/삭제 + `artists.subscriber_count` **원자적 증감**을 같은 트랜잭션에서 한다.
- **멱등하다** — 이미 구독 중인데 POST 하거나 구독하지 않았는데 DELETE 해도 **200 이고 카운터는 변하지 않는다**.
- 구독 해제는 **하드 삭제**다(복합 PK 관계 테이블).

### 연타 처리 규약

서버가 멱등이라 정합성은 항상 유지된다 — **방어는 클라이언트가 한다**.

1. 버튼을 누르면 **즉시 낙관적으로 UI 를 반전**한다
2. **디바운스(약 500ms) 후 최종 상태만 1회 전송**한다
3. 응답의 `isSubscribed`·`subscriberCount` 로 UI 를 보정한다
4. 요청이 진행 중이면 중복 탭을 무시한다

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트(구독 신규 생성 시) |

> 구독 취소 버튼은 기능 명세상 아티스트 상세에서만 노출되지만 **API 는 공통**이다.

---

## 5.8 GET /api/v1/users/me/motif-results?artistSeq={artistSeq}

**별도 구현이 없다.** §11.5(보관함 목록)의 `artistSeq` 필터를 그대로 쓰는 진입점이다 — 커서·항목·보호 등급 썸네일이 모두 동일하다.

용도: 아티스트 상세 상단의 자동재생 목록(§5.3 `myMotifVideos`, 최신 5개) 너머의 **전체 결과물 열람**.

계약은 [`11-motif.md`](11-motif.md) §11.5 를 본다.

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **아티스트 이름·그룹명의 출처** — 원본은 `name`·`groupName` 을 아티스트 속성처럼 적지만, DB 에서는 `users.nickname` 과 `artist_groups.group_name` 이다. `group_seq` 가 NULL 허용이라 **`groupName` 은 `null` 일 수 있다**(솔로 아티스트). 원본 예시에는 항상 값이 있어 오해의 소지가 있다.
2. **§5.1 점수 공식·갱신 주기** — 원본은 API 계약(`rank:artist:current` 를 읽는다)만 적고 **점수 공식과 스냅샷 생성 주체를 정하지 않았다.** S15P21D104-51 에서 배치와 함께 확정했고 그 내용이 위 §5.1 에 있다. `:prev` 교체 주기(10분)도 원본에 없던 값이다.
3. **§5.1 의 입력이 아직 0 이다** — 점수가 `challenges.view_count`·`participant_count` 에서 나오는데 둘 다 증가 경로(§7.4 조회 이벤트, 참여 영상 업로드)가 미구현이다. 랭킹 API 자체는 완성이지만 **의미 있는 순위는 그 두 티켓이 머지된 뒤**에 나온다.
