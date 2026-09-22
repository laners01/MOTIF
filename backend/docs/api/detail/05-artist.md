# §5 아티스트 (ARTIST) — 상세 명세

공통 규약(봉투·에러 코드·시각 포맷·공통 실패 분기·비노출 규칙)은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §5 (문서 내 버전 v0.13) · DB 명세서 v0.17 §9·§10·§41·§42.

| # | Method | Path | 인증 | 비고 |
|---|---|---|---|---|
| 5.1 | GET | `/api/v1/artists/ranking` | 필요 | §3.1 온보딩·§6.2 홈 Who's On 공용 |
| 5.2 | GET | `/api/v1/artists/search` | 필요 | §3.2 온보딩 공용 · **자동완성 겸용**(구 §5.9 통합) |
| 5.3 | GET | `/api/v1/artists/{artistSeq}` | 필요 | |
| 5.4 | GET | `/api/v1/artists/{artistSeq}/challenges` | 필요 | |
| 5.5 | GET | `/api/v1/artists/{artistSeq}/notices` | 필요 | |
| 5.6 | POST | `/api/v1/artists/{artistSeq}/subscription` | 필요 | |
| 5.7 | DELETE | `/api/v1/artists/{artistSeq}/subscription` | 필요 | |
| 5.8 | GET | `/api/v1/users/me/motif-results?artistSeq=` | 필요 | §11.5 와 같은 API — [`11-motif.md`](11-motif.md) 참조 |
| ~~5.9~~ | ~~GET~~ | ~~`/api/v1/artists/autocomplete`~~ | — | **삭제** — §5.2 에 통합 |
| 5.10 | GET | `/api/v1/artists` | 필요 | 전량·구독자 수순 **(신설)** |

## 이 절 전체에 걸리는 스키마 사실

**아티스트 이름은 `artists` 테이블에 없다.** `artists` 는 `users.role='ARTIST'` 사용자의 도메인 확장이라(`uq_artists_user`), 이름·프로필 이미지는 `users` 에서 온다.

| 응답 필드 | 출처 |
|---|---|
| `name` | `artists.user_seq → users.nickname` |
| `profileImageUrl` | `users.profile_image_key` → 공개 CDN URL |
| `groupName` | `artists.group_seq → artist_groups.group_name`. **`group_seq` 는 NULL 허용**이라 솔로면 `null` |
| `bio` | `artists.bio` |

**검색 대상은 이 컬럼들이 아니다.** §5.2 검색은 별칭 테이블(`artist_aliases`·`group_aliases`)의 `alias` 를 본다 — 다국어 검색을 위해 언어별 별칭을 따로 저장한다(DB v0.17 §41·§42).

두 갈래를 섞지 않는다:

| 용도 | 출처 |
|---|---|
| **찾기** (§5.2 매칭) | `artist_aliases.alias` · `group_aliases.alias` |
| **보여주기** (모든 응답의 `name`) | `users.nickname` — **고정** |

그래서 "Karina" 로 검색해도 응답의 `name` 은 `카리나` 다. 별칭을 표시에 쓰면 같은 아티스트가 질의에 따라 다른 이름으로 보이고, 이 표(README §3.5 공통 아티스트 요약)를 쓰는 15개 엔드포인트가 전부 흔들린다.

어드민 §3.5.1 아티스트 목록 검색은 여전히 `users.nickname` 대상이다(`gin_users_nickname`) — 운영자는 등록한 이름으로 찾는다. 별칭 검색은 팬 앱 전용이다.

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

### 점수 공식 *(2026-09-21 재정의 — [ADR-0016](../../adr/0016-challenge-view-and-artist-ranking.md))*

구 공식은 `Σ_c (view×Wv + participant×Wp) × 0.5^(age_c/H)` 였다. **시간 감쇠를
걷어냈다** — 감쇠 기준이 `challenges.reg_dttm` 이었던 것은 누적 카운터뿐이라 걸 수 있는
시간축이 그것뿐이었기 때문인데, §7.4 가 20분 버킷을 만들면서 "지금 얼마나 보고 있는가" 를
직접 알 수 있게 됐다. 그리고 감쇠를 남기면 **오래된 챌린지는 지금 아무리 돌아도 올라올
수 없다.**

```
reaction_total  = Σ_c ( view_count_c + Wp × participant_count_c )
reaction_recent = Σ_c ( 최근 3버킷 조회수_c )
reaction_prev   = Σ_c ( 그 이전 3버킷 조회수_c )

tScore = log10(1 + reaction_total)  / log10(1 + T0)
rScore = log10(1 + reaction_recent) / log10(1 + R0)
dRel   = (reaction_recent − reaction_prev) / (reaction_prev + K)
dScore = 0.5 + 0.5 × clamp(dRel / D0, −1, +1)

score_artist = Wt·tScore + Wr·rScore + Wd·dScore      (기본 0.5 / 0.35 / 0.15)
```

- 대상은 **해당 아티스트의 비삭제 챌린지**뿐이다. **챌린지 등록 자체에는 점수를 주지
  않는다** — 반응 없는 챌린지를 여러 개 올리는 것만으로 순위가 오르면 인기 랭킹이 아니라
  활동량 랭킹이 된다.
- **참여 영상 수는 누적 항에만 들어간다.** `Wp = 100`("참여 1건 = 조회 100회")은 누적끼리
  비교하려고 정한 값이라 1시간 창에서는 환산비가 아니라 **on/off 스위치**가 된다 —
  1시간 조회 50·참여 0 인 아티스트가 조회 20·참여 1 인 아티스트에게 진다. 우리 규모에서
  1시간 참여는 0 아니면 1 이라 순위가 동전 던지기가 된다.
- **`K`(완충 상수)가 없으면 작은 분모에서 비율이 발산한다** — `0 → 3` 이 `1000 → 2000` 을
  이긴다. IMDb 가중 평점이 표본이 작을 때 전체 평균으로 끌어당기는 것과 같은 장치다.
- **정규화 기준은 그 틱의 최대값이 아니라 고정 상수 `T0`·`R0`·`D0` 다.** 최대값으로
  나누면 다른 아티스트의 등락이 내 점수를 바꿔 `rankDelta` 에 거짓 변동이 뜬다.
- **`tScore`·`rScore` 는 1로 자르지 않는다** — 기준을 넘는 아티스트가 전부 같은 값이 되면
  최상위권의 변별이 사라진다.

### 가중치가 §6.1 과 다르다 — 이 스냅샷은 온보딩도 쓴다

| | 누적 | 최근 | 증감 |
|---|---|---|---|
| §6.1 영상 HOT | 0.3 | 0.5 | 0.2 |
| **§5.1 아티스트** | **0.5** | **0.35** | **0.15** |

이 스냅샷을 §6.2 Who's On TOP4 와 **§3.1 온보딩 인기 아티스트**가 함께 쓴다. 온보딩은
앱을 처음 켠 사람에게 "인기" 를 제시하는 자리라 **무명이 1위로 뜨면 곤란하다.** §6.1
가중치를 그대로 쓰면 검산에서 신인이 대형 아티스트를 앞선다.

| 아티스트 | 누적(조회+참여×100) | 최근1h | 이전1h | 0.3/0.5/0.2 | **0.5/0.35/0.15** |
|---|---|---|---|---|---|
| 지금 뜨는 중형 | 120,000 | 3,000 | 500 | 1위 | **1위** |
| 대형인데 조용 | 700,000 | 200 | 250 | 3위 | **2위** |
| 신인 급상승 | 3,000 | 800 | 100 | 2위 | **3위** |

`Wp`·`T0`·`R0`·`K`·`D0`·가중치는 전부 설정이다(`motif.ranking.*`). **실트래픽 없이 자릿수만
맞춘 추정값**이라, 배치가 상위 10건의 항별 점수를 로그에 남긴다 — 하루치를 보고 설정만
고치면 된다.

### 스냅샷 (서버)

§6.1 과 **같은 버전 구조**다.

| 키 | 내용 |
|---|---|
| `rank:artist:current` | 현재 버전 포인터(STRING) |
| `rank:artist:{version}` | ZSET — `score = rank`(1-base), member = `artistSeq` |
| `rank:artist:{version}:delta` | HASH — `artistSeq` → `rankDelta` |
| `rank:artist:versions` | LIST — 버전 이력. `rankDelta` 대비 대상을 여기서 찾는다 |

- 갱신 주기는 **20분**이다 — 조회수 집계 배치의 마지막 단계로 돈다(ADR-0016). 주기를
  따로 두면 버킷이 저장되기 전에 랭킹이 돌아 한 틱 낡은 값으로 순위를 매긴다.
- **`rankDelta` 대비는 3틱 전(=1시간)** 이다. 1틱(20분) 대비로 두면 하루 72번 값이 바뀌어
  앱의 변동 표시가 깜빡인다. 구 `rank:artist:prev` 와 `prev-interval` 설정은 폐기했다.
- **순서를 적재 시점에 확정한다**(`score DESC → artistSeq ASC`). 조회는 rank 순으로 읽기만
  한다 — 예전처럼 전량을 꺼내 애플리케이션에서 정렬하지 않는다. `ZREVRANGE` 는 동점일 때
  member 를 역사전순으로 주므로 ZSET 정렬에 기대면 `artistSeq` DESC 가 나온다.
- **비삭제 아티스트는 챌린지가 0개여도 넣는다**(score 0). 빼면 온보딩 인기 목록(§3.1)과
  `UNSUBSCRIBED` 필터에서 신인이 통째로 사라진다. **§6.1 의 `view_count > 0` 모집단 필터를
  여기에 복사하면 안 된다.**
- **적재 개수 상한(`keep`)을 두지 않는다.** §5.1 은 필터링과 `totalElements` 때문에 전량이
  필요하다 — 자르면 페이지 수가 틀어진다.
- 버전 키에 TTL(`retain`, 기본 2시간)이 있다. 커서가 없는 offset 페이지라 TTL 의 역할은
  **`rankDelta` 대비 버전의 보존**이다 — `interval × rank-delta-buckets`(=1시간)보다 짧으면
  변동값이 조용히 전원 `null` 이 된다.
- 랭킹이 처음 만들어진 틱은 **전원 `0`** 으로 심는다. 그래야 `null` 이 **진짜 신규
  아티스트** 전용으로 남는다.

### 서버 처리

1. 포인터가 가리키는 버전의 ZSET 에서 **전량**을 rank 순으로 꺼낸다. 아티스트가 수백 규모라 전량이 싸고, 구간만 꺼내면 4번 필터에서 걸러진 만큼 페이지가 비어 버린다
2. `rank` 는 ZSET score 에 그대로 들어 있다 — 배치가 확정해 둔 값이라 조회가 정렬에 관여하지 않는다
3. 같은 버전의 `:delta` HASH 에서 `rankDelta` 를 읽는다 — 값이 없으면 `null`(신규 진입)
4. `filter` 가 `SUBSCRIBED`/`UNSUBSCRIBED` 면 요청자의 구독 아티스트를 **1회 조회**해 거른다. 항목마다 조회하면 그대로 N+1 이다
5. `page`·`size` 로 자른 뒤, **그 페이지 분량만** 아티스트 메타(`name`·`groupName`·`profileImageUrl`)를 DB 에서 채운다
6. `profileImageUrl` 은 공개 등급이다 — 서명하지 않는다(§1.7)

**`filter` 는 Redis 가 아니라 애플리케이션 단계에서 적용한다** (미결 해소). 구독 정보는 Redis 에 없고, 자르기 전에 걸러야 페이지 크기가 일정하다.

### 캐싱 (클라이언트 규약 §1.6)

- **TTL 5분** + stale-while-revalidate. **배치 주기(20분)와 더 이상 같지 않다**
  *(2026-09-21 — ADR-0016)*. TTL 도 20분으로 올리면 최대 지연이 배치 20분 + 캐시 20분 =
  **40분**이 된다. 5분을 유지하면 지연 상한이 배치 주기 하나로 묶인다.
- 탭 복귀 시 TTL 안이면 요청하지 않는다. 지났으면 **기존 화면을 유지한 채 조용히 갱신**한다.
- 당겨서 새로고침만 강제 갱신이다. **폴링·소켓 금지.**
- **서버는 갱신을 push 하지 않는다.** SSE·WebSocket 을 쓰지 않으며 FCM 은 §14 전용이라 랭킹과 무관하다. 스냅샷이 갈려도 서버는 아무에게도 알리지 않고, 앱이 **화면 진입·포그라운드 복귀·당겨서 새로고침** 시점에 당겨 간다.
- 이 규약은 **클라이언트 책임**이다(§1.6 제목). 서버는 강제하지 않는다 — "5분마다 갱신된다"를 타이머 폴링으로 구현하지 않도록 계약을 넘길 때 함께 짚는다.

### 실패 응답

**도메인 분기 없음.** 스냅샷이 비어 있으면 빈 목록 200.

Redis 가 죽으면 §1.4 공통 500 이다 — 랭킹은 캐시가 아니라 **데이터 출처**라 DB 폴백을 두지 않는다.

---

## 5.2 GET /api/v1/artists/search — 검색 (자동완성 겸용)

**타이핑 중이든 엔터 실행이든 같은 API 를 부른다.** 원본 v0.7(R4)이 §5.2 확정 검색과 §5.9 자동완성으로 나눴던 것을 v0.13 에서 합쳤다 — 두 응답의 차이가 `isSubscribed` 한 칸뿐이었고, 경로가 둘이면 온보딩 §3.2 가 어느 쪽을 부르는지도 갈린다.

**페이지네이션이 없고** 상위 `size` 건만 준다.

### 요청

```http
GET /api/v1/artists/search?q=카리나&size=20
Authorization: Bearer {accessToken}
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `q` | — | 검색어. **필수** — 비었거나 공백뿐이면 400 |
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
        "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
        "isSubscribed": true
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

**공통 아티스트 요약**(README §3.5) + `isSubscribed` 다 — §5.10 항목과 같은 모양이다. 0건도 200 + 빈 배열이다.

**결과는 항상 멤버 단위**다. 그룹으로 묶은 중첩 구조를 만들지 않는다.

### 매칭

`artist_aliases.alias` 와 `group_aliases.alias` 를 각각 `ILIKE '%q%'` 로 훑는다.

| 단계 | 내용 |
|---|---|
| 1 | `artist_aliases` 매치 → **본인 매치** |
| 2 | `group_aliases` 매치 → 그 그룹의 **비삭제 멤버 전원**을 결과에 추가 (**그룹 확장**) |
| 3 | `artistSeq` 기준 중복 제거 — 가장 높은 매치를 정렬 키로 남긴다 |
| 4 | 정렬 후 상위 `size` 로 자른다 |

- **`language_code` 로 거르지 않는다.** 한국어 UI 사용자도 "aespa" 를 영문으로 치는 일이 흔해서, 언어로 필터링하면 오히려 안 나온다. `language_code` 는 어드민이 별칭을 언어별로 정리해 넣기 위한 저장 구분일 뿐이다.

- **`ILIKE` 라 오타 보정이 없다.** 원본 v0.12 까지의 pg_trgm 유사도 검색을 버렸다 — "카리다" 는 0건이다. 근거는 [ADR-0014](../../adr/0014-artist-alias-search.md).

- **`q` 의 LIKE 메타문자(`%`·`_`·`\`)는 이스케이프해 글자 그대로 찾는다.** 검색어를 `'%' || q || '%'` 한가운데에 끼우므로, 그대로 두면 `%` 한 글자가 모든 별칭에 걸려 **검색어와 무관한 상위 `size` 건**이 나온다(`_` 는 아무 글자 1개가 된다). Postgres `ILIKE` 의 기본 이스케이프 문자가 `\` 라 별도 `ESCAPE` 절은 두지 않는다.

- 삭제된 아티스트·그룹은 제외한다. **별칭 행은 아티스트·그룹 소프트 삭제를 따라가지 않으므로** 조회 시 조인에서 `is_deleted = false` 로 거른다.

### 정렬

**본인 매치 전부가 그룹 확장 멤버보다 앞**이다. 각 덩어리 안에서:

| 순위 | 기준 | 비고 |
|---|---|---|
| 1 | 완전 일치 | 대소문자 무시 |
| 2 | 접두 일치 | |
| 3 | 별칭 길이 ASC | 짧을수록 질의에 가깝다 |
| 4 | `artists.subscriber_count` DESC | |
| 5 | `artistSeq` ASC | 동점 tie-break — 없으면 새로고침마다 순서가 바뀐다 |

그룹 확장으로 들어온 멤버는 1~3 이 의미 없으므로(걸린 별칭이 그룹 것이다) 사실상 4·5 로 정렬된다.

`size` 는 **정렬·중복 제거를 마친 뒤의 단순 상한**이다. 경계에서 그룹 멤버 일부가 잘려 나가는 것을 허용한다 — 그룹을 통째로 넣으면 `size` 가 상한이 아니게 된다.

### 클라이언트 구현 규약

- **입력 디바운스 300ms** + **직전 요청 취소 후 최신 질의만 반영** (코루틴 `Flow.debounce` + `collectLatest`/`flatMapLatest`). 응답 역전으로 옛 결과가 새 결과를 덮어쓰는 것을 막는다.
- 한글 IME 조합 중 텍스트도 그대로 보낸다.
- 빈 문자열이면 **호출하지 않고** 목록을 비운다 — 서버도 400 으로 막지만 왕복을 낭비하지 않는다.
- 최근 검색어는 서버가 저장하지 않는다 — **Android 로컬 처리**다(원본 §16 #19 로 기능 제외).

### 실패 응답

| 조건 | HTTP | `code` |
|---|---|---|
| `q` 가 비었거나 공백뿐 | 400 | `INVALID_PARAMETER` |

빈 `q` 를 통과시키면 `ILIKE '%%'` 가 되어 **전체 아티스트 조회**가 된다. 그건 §5.10 의 일이다.

---

## ~~5.9 GET /api/v1/artists/autocomplete — 자동완성~~ — 삭제

**§5.2 에 통합됐다**(v0.13). 구 §5.9 의 "1~2자 접두 / 3자 이상 pg_trgm" 2단 전략도 함께 폐기한다 — 단일 `ILIKE` 다. 클라이언트 디바운스 규약은 §5.2 로 옮겼다.

원본 §16 #18("자동완성 응답에 프로필 이미지를 포함할지")은 **포함으로 종결**됐다 — 통합된 §5.2 가 공통 아티스트 요약을 그대로 쓴다.

아티스트가 수백 규모라 **DB 직조회로 충분**하다 — 캐시를 두지 않는다(이 근거는 §5.2·§5.10 이 승계).

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
        "videoUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
        "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1790000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
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
| `myMotifVideos` | **내가 이 아티스트와 만든 모티프 결과물 최신 5개**. `videoUrl` 은 HLS 마스터 재생목록이고 보호 등급이라 **CDN 절대 주소 + 프리픽스 서명**이다(ADR-0012). 없으면 **빈 배열**(상단 영역 미노출) |

- `myMotifVideos` 는 화면 상단 **자동재생**용이다. 구 `hasMyMotifResults` 를 대체했다. 전체 목록·더보기는 §5.8(= §11.5 의 `artistSeq` 필터).
- `pass.active=false` 면 클라이언트는 "모티프 패스 알아보기"를 노출한다(→ §11.4).

### 연속 재생 (클라이언트 제안)

배열 순서대로 플레이어 재생목록(ExoPlayer `ConcatenatingMediaSource`)에 넣어 하나가 끝나면 다음으로 넘어간다. 보호 등급이라 각 `videoUrl` 에 **프리픽스 서명 파라미터**가 붙어 있고, 조각·자식 재생목록 요청에 같은 값을 붙이는 것은 클라이언트(OkHttp 인터셉터) 몫이다. 서명이 만료(10분)돼 조각 로드가 403 이면 **`POST /api/v1/media/{jobUuid}/signature`** 로 새 파라미터를 받아 재시도한다 — 재생목록을 다시 받는 것은 복구가 되지 않는다. 재생목록도 CDN 에서 오고 같은 만료가 걸려 있기 때문이다(ADR-0012).

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
    "nextCursor": "NQ",
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

## 5.10 GET /api/v1/artists — 전체 아티스트 목록 (구독자 수순) **(신설)**

아티스트 탭의 전체 목록이다. **비페이지 — 전량 반환**, `artists.subscriber_count` **내림차순**.

### §5.1 랭킹과 다른 API 다

경로도 항목도 비슷해 보이지만 **데이터 출처와 정렬 기준이 다르다.**

| | §5.1 랭킹 | §5.10 목록 |
|---|---|---|
| 1차 출처 | Redis 스냅샷(배치가 5분마다 적재) | **DB 직조회** |
| 정렬 | 점수 — 챌린지 조회·참여를 등록 시각으로 감쇠 | **`subscriber_count` DESC** |
| 페이지 | offset (`page`·`size`) | **없음 — 전량** |
| 항목 | `rank`·`rankDelta` 가 붙는다 | 요약 + `isSubscribed` |

§5.1 을 `size` 만 키워 재사용하면 **화면이 요구하는 순서가 나오지 않는다.** 랭킹은 구독을 점수에 넣지 않으므로(§5.6 문서: "구독해도 순위는 그대로") 구독자 수순 목록이 될 수 없다.

### 요청

```http
GET /api/v1/artists
Authorization: Bearer {accessToken}
```

파라미터가 **없다.** 전량 반환이라 `page`·`size`·`cursor` 가 없고, 정렬도 서버 고정이라 `sort` 를 받지 않는다(§5.1 과 같은 근거 — 열면 정렬 칼럼 화이트리스트가 필요해진다).

### 성공 응답 (200) — 비페이지 전량

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
        "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
        "isSubscribed": true
      },
      {
        "artistSeq": 7,
        "name": "윈터",
        "groupName": "aespa",
        "profileImageUrl": null,
        "isSubscribed": false
      }
    ]
  },
  "timestamp": "2026-09-18T00:00:00.000Z"
}
```

**`artist` 요약**(README §3.5) + `isSubscribed` 다 — §5.2 검색 항목과 같은 모양이다.

| 필드 | 설명 |
|---|---|
| `isSubscribed` | 요청자의 구독 여부. 이 목록에서 바로 구독 버튼을 그릴 수 있게 싣는다 |

**`subscriberCount` 를 싣지 않는다.** 정렬 기준일 뿐 화면이 숫자를 쓰지 않는다 — 필요해지면 필드 추가만 하면 된다(카운트는 `artists.subscriber_count` 에 이미 있다). 개별 아티스트의 구독자 수는 §5.3 상세가 준다.

### 서버 처리

1. 비삭제 아티스트를 **`subscriber_count` DESC → `artistSeq` ASC** 로 정렬해 전량 읽는다 — 정렬을 DB 에 맡긴다
2. 요청자의 구독 아티스트를 **1회 조회**해 `isSubscribed` 를 채운다. 항목마다 조회하면 N+1 이다
3. `profileImageUrl` 은 공개 등급이다 — 서명하지 않는다(§1.7)

**DB 조회는 2회로 고정이다** (목록 1회 + 구독 여부 1회).

- **동점 정렬이 `artistSeq` ASC 인 이유**: 구독자 0인 신인이 여러 명이면 전원 동점인데, tie-break 가 없으면 Postgres 가 주는 순서가 요청마다 달라져 같은 화면이 새로고침마다 뒤바뀐다. §5.1 스냅샷의 동점 규칙과 같은 값을 쓴다.
- **비삭제만 낸다.** 삭제 아티스트는 §1.8 대로 없는 것으로 본다 — 목록에 남기면 눌렀을 때 §5.3 이 404 다.
- 아티스트가 **수백 규모**라 전량이 싸다(§5.2 가 캐시를 두지 않는 것과 같은 근거). 캐시를 두지 않는다.

### 실패 응답

**도메인 분기 없음.** 아티스트가 0명이면 빈 배열 200 이다.

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **아티스트 이름·그룹명의 출처** — 원본은 `name`·`groupName` 을 아티스트 속성처럼 적지만, DB 에서는 `users.nickname` 과 `artist_groups.group_name` 이다. `group_seq` 가 NULL 허용이라 **`groupName` 은 `null` 일 수 있다**(솔로 아티스트). 원본 예시에는 항상 값이 있어 오해의 소지가 있다.
2. **§5.1 점수 공식·갱신 주기** — 원본은 API 계약(`rank:artist:current` 를 읽는다)만 적고 **점수 공식과 스냅샷 생성 주체를 정하지 않았다.** S15P21D104-51 에서 배치와 함께 확정했고 그 내용이 위 §5.1 에 있다. `:prev` 교체 주기(10분)도 원본에 없던 값이다.
3. **§5.1 의 입력이 아직 0 이다** — 점수가 `challenges.view_count`·`participant_count` 에서 나오는데 둘 다 증가 경로(§7.4 조회 이벤트, 참여 영상 업로드)가 미구현이다. 랭킹 API 자체는 완성이지만 **의미 있는 순위는 그 두 티켓이 머지된 뒤**에 나온다.
