# MOTIF API 명세서 — v0.13

> 원천 문서: 기능 명세서(2026-09-02) · DB 명세서 v0.8 · 아키텍처 설계서 v0.2 · 팀 공통 응답 규약(2026-09-02).

> 본 문서는 **클라이언트(Android) 앱 API**를 다룬다. 어드민 API(`/admin/**`)는 별도 문서에서 정의한다.

> 표기 규약: **(회의 확정)** = 확정 사항 / **(기본안 — 확인 필요)** = Claude 기본안, 회의 확정 필요.

>

> 개정 이력

> - v0.2 (2026-09-02): 충돌 C1~C6 전건 확정 반영, 패스 취소 API(§11.10) 신설.

> - **v0.3 (2026-09-02): 팀 공통 응답 envelope 전면 채택**(§16 구 안건 #1 종결). 페이지네이션 커서 → **offset(page/size/sort)** 전환, `204 No Content` 전면 폐지(→ `200` + `data:null` — envelope 일관성), `INVALID_INPUT`→`INVALID_PARAMETER` 개명, 시각 밀리초 포맷 통일, size 최대 100. **전 엔드포인트 성공/실패 응답 분기 상세화.**

> - **v0.4 (2026-09-02): 팀 리뷰 반영** — 온보딩 판정 isNewUser 일원화(2.1), 4.1 마이페이지 원콜 개편, 내 활동 4.9 통합(구 4.10~4.13 삭제), 4.7 비페이지·카운트 재확정(**v0.7 정의 회귀** — DB v0.9 전파 대기), 4.8 그리드 경량화, 5.4 조회수, 5.5·6.3 공지 content 전체·이미지 전체, 7.1/7.2 필드 정리(isSubscribed 이동), 좋아요·구독 연타 규약.

> - **v0.5 (2026-09-02 · 리뷰 R2): ① 전 영상 그리드 `viewCount` 필수 ② 영상 목록 전부 커서 페이지네이션 전환**(4.8/4.9/6.1/7.1/8.1/11.5 — §1.9 정식 승격) ③ 4.9 화면 기준 재설계(영상 단위 카드 + 하트 + 내 댓글 + 답글 프리뷰) ④ 5.3 subscribedDays·challengeCount·상단 모티프 자동재생 목록 ⑤ 5.4/5.5 비페이지 전량 ⑥ 6.1 경량화 ⑦ 6.3 MOTIF_ASSET 항목 확정 ⑧ 7.1 thumbnailUrl 부활 ⑨ 4.1 videoCount 철회, 4.6 필드 정리. 카드 카운트 **최종 확정**(DB v0.9 반영 완료). 상세는 부록 A.

> - **v0.6 (2026-09-03 · 리뷰 R3, DB v0.10 정합): ① 챌린지·참여 영상 `title` 전면 삭제** ② **알림 목록 스코프 아웃**(notifications 테이블 삭제 — 14.3~14.7 API 제거, 레드닷 없음, 전 알림 푸시 전용) ③ **편집본·슬롯 반영**(7.2 slots/availableMasks, 13.1 participantMask) **(기본안)**. 상세는 부록 A R3.

> - **v0.7 (2026-09-03 · 리뷰 R4): ① 구 4.5(rep-motif)를 §11.8 PATCH 로 통합**(결과물 문구 수정 + 대표 설정 단일 API) ② 4.8 비페이지 전량 전환 ③ **검색/자동완성 API 분리**(5.2 확정 검색 · 5.9 자동완성 신설) ④ 6.1 캐러셀→뷰어 커서 공유 구현 노트 ⑤ 7.2 `availableMasks` 삭제. 상세는 부록 A R4.

> - **v0.8 (2026-09-09 · 리뷰 R5): ① 좋아요 POST/DELETE 성공만 `204 No Content` 예외 적용(챌린지·참여영상·공지·댓글) ② 참여영상 좋아요 사용자 목록(구 8.4) 삭제 및 ARTIST 참여영상 게시 금지 ③ 댓글 정렬을 대상 아티스트→내 댓글→일반 댓글, 각 그룹 최신순으로 확정 ④ 5.5 공지 목록을 커서 기반·본문/이미지 전체 응답으로 확장하고 공지 상세(구 10.1) 삭제 ⑤ 모티프 에셋 전체 카탈로그+`artistSeq` 필터+`myAccess`로 개편, 숍 집계 API(구 11.3) 삭제, 아티스트 패스 API REST 경로 변경 ⑥ MOTIF 결과는 잡 완료 시 자동 생성(`description=null`)하고 구 11.7 저장 API 삭제 ⑦ MOTIF 보정 비활성, 업로드 미완료 오류 409, FCM 수신 알림을 Android 로컬 목록으로 저장. 상세는 부록 A R5.**
> - **v0.9 (2026-09-09 · 리뷰 R6): ① 6.3 공지 항목에 `isLiked` 추가**(공지 상세 삭제로 6.3 이 진입 데이터가 됨) **② `motif_assets.title` 컬럼 신설 확정**(DB v0.11) — 11.1/11.2 응답에 `title` 추가, 6.3 의 description 매핑 주석 해제 ③ SECRET = NORMAL 동일 취급을 **PASS 커버 포함(ⓐ)** 으로 확인, DB 전파. + 원본 파일 마크다운 이스케이프 정규화. 상세는 부록 A R6.
> - **v0.10 (2026-09-09 · 리뷰 R7 — 팀 회의 결정 반영): ① 커서 응답에 `prevCursor`/`hasPrevious` 추가**(§1.9) **② 재시도 = 동일 잡 재큐 + `attempt` 증가 확정**(§13.4 계약 확정) **③ 숍 공용 배너 테이블·API 신설**(§11.11, DB `shop_banners`) ④ 종결: signed 10분, 토스 현행, §4.7 유지, 온보딩 Android 가드 확정, `participantMask >= 0` 확정(0=미발급 예약값), `recent_search_terms` 컬럼 제거(DB v0.12). 상세는 부록 A R7.
> - **v0.12 (2026-09-13 · [ADR-0010](../adr/0010-video-delivery.md) 반영): ① 합성 결과 영상의 `videoUrl` 이 단일 MP4 에서 **HLS 마스터 재생목록(`master.m3u8`)** 주소로 바뀐다**(§4.1·§5.8/11.5·§6.1·§8.2·§11.6) — 화질 2종(720p/480p) + 재생 중 자동 전환(ABR). **② §1.7 보호 등급 전달 방식 확정 — signed URL + core-api 재생목록 리라이팅**(2026-09-15). HLS 는 재생목록 안에서 조각을 추가 요청하는데 URL 서명이 그 요청에 따라붙지 않는다. 한때 signed 쿠키로 적었으나 CDN 도메인이 API 도메인과 달라 쿠키가 실리지 않고, 쿠키는 도메인당 한 벌이라 영상 단위 권한을 못 낸다 — core-api 가 재생목록을 다시 써서 조각 주소에 서명을 박는다. 산출물 프리픽스도 `private/`·`public/` 으로 갈랐다. ③ 챌린지 원본(§7.1·§7.2)의 HLS 전환 여부는 **미정**(§1.7 하단 메모). 근거 측정은 [인계 문서](../poc/video-delivery-handoff.md).
> - **v0.11 (2026-09-09 · 리뷰 R8): ① 13.3 응답을 jobType 별로 분기 확정** — CHALLENGE 는 결과 프리뷰 URL, **MOTIF 는 `resultSeq`**(보관함 상세 이동) **② 편집본 전(全)조합 등록 운영 전제 확정**(어드민 필수 등록 + 완결성 검증 — 어드민 명세 반영 대기). NULL(무교체 원본 합성) 정책은 팀 논의 예정. 상세는 부록 A R8.
> - **v0.13 (2026-09-20 · [ADR-0014](../adr/0014-artist-alias-search.md) 반영): ① 검색과 자동완성을 §5.2 하나로 통합**하고 **§5.9 를 삭제**한다 — v0.7 에서 나눈 것을 되돌린다. 경로는 `/artists/search` 를 남긴다(§3.2 온보딩이 재사용). **② 검색 대상을 `users.nickname`·`artist_groups.group_name` 에서 별칭 테이블(`artist_aliases`·`group_aliases`)로 옮긴다** — 언어별 별칭을 저장해 "Karina"·"카리나" 어느 쪽으로 쳐도 찾히게 한다(DB v0.17). **③ 매칭을 pg_trgm 유사도에서 `ILIKE` 부분 일치로 바꾼다 — 오타 보정이 없어진다.** 대신 정렬 규칙을 결정적으로 명문화했다(완전 일치 → 접두 → 별칭 길이 → 구독자 수 → `artistSeq`). ④ 표시 이름(`name`)은 **`users.nickname` 고정**이다 — 별칭은 검색에만 쓰고 응답에 싣지 않는다. ⑤ §16 #18(자동완성 프로필 이미지·2단 전략) 종결.
---

## 0. v0.3에서 결정·변경된 사항 요약

| 항목 | v0.2 | v0.3 확정 |
|---|---|---|
| 응답 형식 | 도메인 본문 직접 반환 **(기본안)** | **`{result, code, message, data, timestamp}` envelope 고정** **(팀 규약 확정)** |
| 성공 code | — | `SUCCESS` 고정 (팀 규약 1.2 채택 — 규약 예시의 `A001` 은 자리표시자로 판단, `SUCCESS` 로 통일) |
| 페이지네이션 | 커서(`items`/`nextCursor`) | **offset**: `page`(0-base)/`size`(기본 20, 최대 100)/`sort` + `data` 내부 구조 고정 |
| 삭제·토글 성공 | 204 No Content | **200 + `data: null`** — envelope 은 본문을 요구하므로 204 폐지 |
| 검증 오류 코드 | `INVALID_INPUT` | `INVALID_PARAMETER` (팀 공통 코드) |
| 시각 | `2026-09-02T04:00:00Z` | `2026-09-02T04:00:00.000Z` (밀리초 포함) |

커서 페이지네이션은 폐기하되, 실시간 유입 피드의 **페이지 밀림**(스크롤 중 새 콘텐츠 유입 → 중복/누락 노출) 리스크가 있어 §1.9 에 예비 스펙과 함께 안건으로 남긴다(§16 #1).

> **v0.8 현재 규약:** 위 표는 v0.3 당시 변경 기록이다. 현재는 §1.9 대상 목록에 커서 페이지네이션을 사용하며, 좋아요 POST/DELETE 4종은 `204 No Content` 예외를 적용한다.

---

## 1. 공통 규약

### 1.1 기본

- Base URL: `https://{domain}/api/v1` — Traefik(TLS) → SCG(JWT 검증·레이트리밋·라우팅) → Core API.

- `/api/v1/notifications/**` 는 SCG가 **알림 서비스(app-notification)** 로 라우팅한다(프로세스 분리 전에는 동일 배포 내 모듈). 그 외 전부 Core API.

- 요청·응답 본문은 JSON(camelCase). 식별자는 서버 발급 숫자 ID(DB `*_seq`)를 그대로 노출한다. 단 **주문 `orderUuid`, 합성 잡 `jobUuid`** 만 UUID(외부 잔존 식별자 원칙 — 토스 orderId·푸시 페이로드).

### 1.2 공통 응답 형식 (envelope)** **(팀 규약 확정)

모든 응답은 아래 envelope 로 통일한다. HTTP 상태코드는 별도로 정확히 사용한다(200/201/202, 4xx/5xx). 단, **좋아요 생성/취소 POST·DELETE 성공은 204 No Content 예외**로 하며 응답 body를 반환하지 않는다(§7.3, §8.3, §9.5, §10.2).

**성공 — 단건/일반**

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { },
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

**성공 — 페이지 응답** (`data` 내부 구조 고정)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [ { } ],
    "page": 0,
    "size": 20,
    "totalElements": 135,
    "totalPages": 7,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

**실패 — 일반** (HTTP 4xx/5xx 와 함께, `data` 에는 상세 문자열 또는 null)

```json
{
  "result": "FAIL",
  "code": "VIDEO_NOT_FOUND",
  "message": "존재하지 않는 영상입니다.",
  "data": "videoSeq: 88",
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

**실패 — 검증 오류(400)** (`data` 에 필드 단위 오류 배열 허용)

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [
    { "field": "nickname", "message": "2~20자의 영문·한글·마침표·언더바만 허용합니다." }
  ],
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

규칙:

- 성공은 `result=SUCCESS`, `code=SUCCESS` 고정. `message` 는 고정 문구("요청이 성공했습니다.") 또는 상황 문구 — 클라 분기는 **`result`·HTTP 상태로만** 하고 message 는 표시용으로만 쓴다.

- 실패는 `result=FAIL` + 도메인/공통 `code`(§1.4). 본문 없는 성공은 원칙적으로 `data: null` 로 envelope 를 유지한다. **예외: 좋아요 POST/DELETE 성공은 204 No Content이며 envelope 를 반환하지 않는다.**

- 생성은 201, 비동기 접수는 202, 좋아요 생성/취소 성공은 204, 그 외 성공은 200. 이하 각 API 의 "성공 응답" 은 `data` 내용과 HTTP 코드만 표기하고 envelope 껍데기는 생략한다.

### 1.3 규약

| 항목 | 규칙 |
|---|---|
| 인증 | `Authorization: Bearer {accessToken}` — 🔓 표시 API 외 전부 필수 |
| Content-Type | 기본 `application/json; charset=utf-8`. 예외: presigned URL 을 이용한 S3 바이너리 업로드(PUT)는 해당 미디어 타입 |
| 시간 | ISO-8601 UTC, 밀리초 포함 (`2026-09-02T06:00:00.000Z`) |
| 페이지네이션 | **이중 규약** — ① 일반 목록: offset `?page=0&size=20&sort=...`(`page` 0-base, `size` 기본 20·최대 100, `sort` 는 화이트리스트 키만). ② 실시간 유입/무한 스크롤 대상 목록은 **커서** `?cursor={opaque}&size=`(§1.9). 현재 대상에 영상 목록과 **5.5 아티스트 공지 목록**이 포함된다. 서버 고정 정렬 엔드포인트는 `sort` 무시 |
| ID | 서버 발급 숫자 ID(`*Seq`). 예외: `orderUuid`, `jobUuid` |
| 코드 | 성공 `SUCCESS` 고정, 실패는 §1.4 공통 코드 또는 각 API 도메인 코드 |

### 1.4 공통 에러 코드

| code | HTTP | 비고 |
|---|---|---|
| `INVALID_PARAMETER` | 400 | 형식·필수값 검증 실패(필드 오류 배열) |
| `UNAUTHORIZED` | 401 | 토큰 없음·만료·위조 |
| `FORBIDDEN` | 403 | 소유권·권한 없음(일반) |
| `NOT_FOUND` | 404 | 리소스 없음(일반) — 도메인 코드(`*_NOT_FOUND`)가 우선 |
| `CONFLICT` | 409 | 상태 충돌(일반) — 도메인 코드가 우선 |
| `INTERNAL_ERROR` | 500 | 서버 오류 |

공통 확장(도메인 공용):

| code | HTTP | 비고 |
|---|---|---|
| `ACCOUNT_SUSPENDED` / `ACCOUNT_DELETED` | 403 | 정지·삭제 계정 — 전 API 공통 |
| `PAYMENT_FAILED` | 402 | 토스 승인 실패 |


| `RATE_LIMITED` | 429 | SCG 레이트리밋 |

**전 API 공통 실패 분기(이하 각 API 실패 표에서 생략):** 400 `INVALID_PARAMETER`(요청 검증), 401 `UNAUTHORIZED`(🔓 제외), 403 `ACCOUNT_SUSPENDED`/`ACCOUNT_DELETED`, 429 `RATE_LIMITED`, 500 `INTERNAL_ERROR`. 각 API 의 실패 표에는 **도메인 분기만** 적는다.

### 1.5 인증 상세

- 인증 실패 401 `UNAUTHORIZED`. `user_accounts.account_status='SUSPENDED'` 또는 `users.is_deleted=TRUE` 는 403(`ACCOUNT_SUSPENDED` / `ACCOUNT_DELETED`) — 로그인·리프레시 시점에 모두 검증.

- refresh token 은 rotation: 재발급 시 기존 행 revoke 후 새 행 발급. 폐기 토큰 재사용 감지 시 401 `INVALID_REFRESH_TOKEN`.

### 1.6 캐싱 규약 (클라이언트)

- 홈 3섹션(§6)·아티스트 랭킹(§5.1): **TTL 5분**(랭킹 배치 주기와 동일) + stale-while-revalidate. 탭 복귀 시 TTL 내 요청 0회, 경과 시 기존 화면 유지 + 조용한 갱신. 당겨서 새로고침만 강제 갱신. **폴링·소켓 금지.**

- 합성 잡 상태는 폴링하지 않는다 — FCM 알림 수신 → 결과 화면 진입 시 1회 조회(§13.3).

### 1.7 미디어 URL 정책

| 등급 | 대상 | 반환 형태 |
|---|---|---|
| 공개 | 챌린지 원본·에셋 **미리보기**·썸네일·프로필·공지 이미지 | CloudFront 일반 URL (캐시 극대화) |
| 보호 | **모티프 결과물**, **참여 영상 전체**(`visibility` 무관), 게시·저장 전 합성 결과 프리뷰 | CloudFront **signed URL** (만료 기본 10분). 엣지 캐싱 자체는 동작 — 서명 파라미터는 CloudFront 예약값이라 캐시 키에 들어가지 않는다. 사용자마다 서명이 달라도 같은 오브젝트는 한 번만 캐시되므로, 서명한다고 히트율이 떨어지지 않는다 |

**참여 영상은 `visibility` 와 무관하게 전부 보호 등급이다** *(2026-09-19 —
[ADR-0013](../adr/0013-signed-only-media.md))*. `visibility` 는 **앱 안에서 누가 볼
자격이 있는가**만 뜻하고, 그 자격을 CDN 에 전달하는 수단이 서명이다. 둘을 같은 값으로
표현하면 "정책은 바뀌었는데 오브젝트는 아직 안 옮겨졌다" 는 구간이 필연적으로 생긴다.

무기명 URL 을 쓰는 자리는 어드민이 올린 **공개 카탈로그**(챌린지 원본·에셋 미리보기)와
이미지뿐이다. 앱 밖으로 콘텐츠를 가지고 나가는 기능(공유 링크·OG 프리뷰)은 제품
정책상 넣지 않으므로, 사용자 생성 콘텐츠에 무기명 URL 이 필요한 자리가 없다.

- 업로드는 전부 S3 presigned PUT 직행(영상·이미지 바이트는 API 서버 미통과 — 설계 원칙 ①).

**영상은 HLS 다.** `videoUrl` 은 단일 MP4 가 아니라 **마스터 재생목록(`master.m3u8`)** 주소다
([ADR-0010](../adr/0010-video-delivery.md) D2). 플레이어가 그 재생목록을 읽고 화질별
재생목록·조각을 **추가로 요청**한다.

```
master.m3u8                 ← videoUrl 이 가리키는 것
└── 720/index.m3u8 → 720/data.m4s   ← 플레이어가 이어서 요청
└── 480/index.m3u8 → 480/data.m4s
```

- **재생목록에 서명 URL 을 그대로 쓸 수 없다 — 다시 써서 내린다.** `master.m3u8` 에
  서명을 붙여도 그 안의 상대 경로(`720p/index.m3u8` → `data.m4s`)에는 따라붙지 않아
  조각 요청이 403 이 된다. 그래서 **core-api 가 재생목록을 읽어 조각 주소를 서명된
  절대 URL 로 바꿔 내린다**. `videoUrl` 은 CDN 이 아니라 core-api 를 가리킨다:

  ```
  GET /api/v1/media/{jobUuid}/master.m3u8       → 자식 재생목록 주소를 core-api 로
  GET /api/v1/media/{jobUuid}/{720p}/index.m3u8 → data.m4s 를 서명된 CDN 절대 URL 로
  ```

  오가는 것은 **텍스트 4개**(수백 바이트~수 KB)뿐이고 영상 바이트는 여전히 CDN 이
  낸다 — 설계 원칙 ① 유지. `single_file` + `EXT-X-BYTERANGE` 구조라 화질당 서명이
  1개면 되는 것이 전제다(조각마다 파일이면 영상 하나에 서명 수십 개가 된다).
- **signed 쿠키를 쓰지 않는다.** ① CDN 도메인이 API 도메인과 달라 `Set-Cookie` 가
  CDN 요청에 실리지 않는다 ② 쿠키는 도메인당 한 벌이라 "이 프리픽스 전체"밖에 표현하지
  못해, 여러 사람의 영상이 섞인 피드에서 영상 단위 권한을 낼 수 없다
- **만료는 짧아도 된다.** 조각 URL 이 만료돼 403 이면 앱이 재생목록을 다시 받는다 —
  core-api 가 새 서명으로 내려준다. 재시도는 클라이언트 규약이다(안드로이드)
- `thumb.jpg` 처럼 **그 자체로 끝나는 단일 파일**은 리라이팅 없이 서명 URL 을 바로 낸다
- 공개 등급은 서명이 없으므로 영향 없다
- S3 presigned 는 어느 등급에도 쓰지 않는다. 서명 쿼리스트링이 오리진(S3)까지 전달돼야
  해서 **CDN 캐시 적중률을 0% 로 만든다**. CloudFront 서명은 엣지에서 소비되므로 다르다(ADR-0010 D5)

**어디까지 HLS 인가 — 재생되는 것만이다.**

| | 형태 | 이유 |
|---|---|---|
| 참여 영상 (§6.1·§8.2) | **HLS** | 피드에서 재생 |
| 모티프 결과 (§4.1·§5.8/11.5·§11.6) | **HLS** | 보관함에서 재생 |
| **챌린지 원본 (§7.1·§7.2)** | **HLS** | 피드에서 참여 영상과 **같이 스와이프**된다. 한쪽만 MP4 면 그 구간에서 ABR 이 동작하지 않는다 |
| **모티프 에셋 미리보기 (§11.1·§11.2)** | **HLS** | 숍 화면에서 재생된다. **가로** 영상이지만 사다리가 짧은 변 기준이라 같은 파라미터로 간다 *(2026-09-17 — 어드민 명세 v0.5 §8 #17 "MP4 유지" 를 뒤집었다)* |
| 사용자 원본 업로드 (`raw_video_key`) | MP4 | 재생하지 않는다. 합성 워커의 **입력**이다 |
| 편집본 소스 (`edited_challenges.video_key`) | MP4 | 같음 — 워커 입력 |

**산출물 키 규약 — 최상위 프리픽스가 곧 보호 등급이다**

| 대상 | 원본(입력) | 산출물 | 서명 |
|---|---|---|---|
| 합성 결과 (참여 영상·모티프 결과) | `raw/{uuid}.mp4` | `private/{challenge\|motif}/{jobUuid}/` | **필요** |
| 챌린지 원본 | `challenges/{uuid}.mp4` (어드민 명세 §1.4) | `challenges/{uuid}/` | 불필요 |
| 에셋 미리보기 | `assets/{uuid}.mp4` | `assets/{uuid}/` (마스터·화질별·썸네일) | 불필요 |

CloudFront 의 "Restrict viewer access" 는 **캐시 동작 단위**이고 캐시 동작은 path
pattern 으로 고른다. 보호 여부가 경로 최상단에 드러나 있어야 동작 하나(`private/*`)로
가른다 — 같은 프리픽스에 섞으면 나눌 방법이 없다.

**합성 결과는 게시 여부와 무관하게 `private/` 한 곳에 머문다** *(2026-09-19 —
[ADR-0013](../adr/0013-signed-only-media.md))*. 예전에는 PUBLIC 게시 시
`public/challenge/{jobUuid}/` 로 복사하는 규약이 있었으나 폐기했다. 공개 사본의 존재
근거는 "서명 없이 내보내 캐시를 극대화한다" 하나였는데, 서명 파라미터가 캐시 키에
들어가지 않아 그 이점이 없다. 대신 복사기·삭제기·CloudFront 무효화가 필요했고,
`visibility` 와 키 프리픽스가 어긋나는 구간을 계속 특별 취급해야 했다.

**`visibility` 전환은 DB 값 하나를 바꾸는 일이다**(§8.9). 키를 움직이지 않고 S3 도
CDN 도 건드리지 않는다. `PUBLIC → PRIVATE` 은 **다음 서명 갱신(기본 10분) 안에**
닫힌다 — 재생 중에는 CDN 직통이지만 갱신은 반드시 core-api 를 지나고, 거기서 인가를
다시 하기 때문이다(ADR-0012). 무효화에 기대지 않으므로 "수 분 뒤 아마도" 가 아니라
TTL 안에 확실히 닫힌다. 다만 **이미 발급된 서명 파라미터는 만료까지 유효하다** —
즉시 차단을 약속하지 않는 것은 그대로다.

산출물 프리픽스 아래에 `master.m3u8`·`thumb.jpg`·화질별 디렉터리가 깔린다.
DB 에는 **마스터 재생목록 경로 하나만** 저장한다(ADR-0010 D4).
**seq 가 아니라 uuid 다** — 공개 CDN 경로에 순차 번호를 쓰면 열거가 가능해
콘텐츠의 존재와 개수가 드러난다. 같은 이유로 **경로에 `user_seq` 를 넣지 않는다**:
signed URL 은 URL 단위 서명이라 소유자를 경로에 새길 필요가 없고(인가는 서명을
내주기 전에 core-api 가 한다), 넣으면 순차 정수가 CDN 네임스페이스에 드러난다.
사용자별 정리가 필요하면 `composite_jobs(user_seq, job_uuid)` 로 프리픽스를 뽑는다.

챌린지 원본은 어드민 업로드라 합성 워커를 거치지 않는다. **인코딩·패키징 컴포넌트를
두 경로가 공유하도록** 만든다 — 같은 파라미터를 두 곳에 복사하면 GOP 정렬이 어긋나는
순간 조용히 ABR 이 깨진다.

### 1.8 공통 비노출 규칙 (모든 조회 API 적용)

- `users.is_deleted=TRUE` 사용자가 소유한 참여 영상·모티프 결과물·댓글은 노출하지 않는다. 루트 댓글 작성자가 삭제 사용자면 **답글 스레드 전체** 비노출.

- 삭제된 챌린지에 속한 참여 영상은 자체 `is_deleted=FALSE` 여도 비노출.

- `PRIVATE` 참여 영상은 작성자 본인 외 **존재 자체를 숨긴다**(타인 조회 시 404).

- 위 비노출과 `commentCount`·`participantCount` 등 카운터의 표시 불일치는 감수한다 **(회의 확정)**.

### 1.9 커서 페이지네이션** **(회의 확정 R5)

실시간 유입으로 offset 페이지 밀림 위험이 크거나 무한 스크롤 UX를 사용하는 목록은 **커서 기반**이다 — 대상: 4.9 내 활동, **5.5 아티스트 공지 목록**, 6.1 HOT, 7.1 챌린지 피드, 8.1 참여 영상 피드, 11.5 보관함. (4.8 내 영상은 비페이지 전량.)

- 요청: `?cursor={opaque}&size=20` — 최초 호출은 `cursor` 생략. `size` 기본 20·최대 100.

- 응답(envelope 유지, `data` 내부만 상이):

```json
"data": { "list": [ { } ], "prevCursor": null, "nextCursor": "opaque-base64", "hasPrevious": false, "hasNext": true }
```

- `nextCursor: null`(= `hasNext: false`) 이면 끝. 커서는 서버 생성 opaque 토큰(base64 — 정렬 키 조합)으로, 클라이언트는 해석·조작하지 않는다.
- **역방향 조회** *(회의 확정 R7)*: 응답에 `prevCursor`/`hasPrevious` 를 함께 내린다 — 목록 첫 항목보다 앞(더 최신/상위) 방향. 최초 호출(맨 앞부터)은 `prevCursor: null`. **방향은 커서 토큰에 인코딩**되므로 요청 파라미터는 `?cursor=` 하나로 동일하다 — 클라는 받은 `prevCursor` 를 그대로 넣으면 된다. 서버 구현: 역방향은 정렬 반전 조회 후 결과를 다시 뒤집어 반환.

- **역방향 조회용 `prevCursor` 제공 여부는 §16 미결 안건으로 둔다.** 현재 계약은 `nextCursor` 기준 순방향 조회만 확정한다.

- 스냅샷 순서 목록(6.1, 랭킹 파생)은 커서에 스냅샷 버전을 포함해 배치 교체 후에도 순서가 꼬이지 않게 한다 **(구현 노트)**.

- 비커서 목록(랭킹·댓글·6.3 활동 피드·11.1 에셋 카탈로그 등)은 offset 또는 비페이지 규약을 사용한다.

---

## 2. 인증 (AUTH)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 2.1 | POST 🔓 | `/auth/login/google` | 구글 로그인·자동 회원가입 | AUTH-01 |
| 2.2 | POST 🔓 | `/auth/refresh` | 토큰 재발급 (rotation) | refresh_tokens |
| 2.3 | POST | `/auth/logout` | 현재 기기 로그아웃 | AUTH-02, SETTING-04 |

### 2.1 POST /auth/login/google 🔓

구글 ID 토큰을 검증하고, 최초 로그인이면 `users`(+`user_accounts`, `user_oauth_accounts`)를 생성한다.

**요청**

```json
{ "idToken": "google-id-token" }
```

**성공 응답 (200)** — `data`:

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "isNewUser": true,
  "user": { "userSeq": 1, "nickname": "user_1", "profileImageUrl": null }
}
```

- 최초 가입 시 `nickname` 은 임시값(`user_{seq}`)으로 생성하고 온보딩 완료 API에서 확정한다 **(기본안 — users.nickname NOT NULL 대응)**.

- **온보딩 노출 판정 = `isNewUser`** **(회의 확정 2026-09-02 — 구 `needsOnboarding`(구독 0건 파생) 필드 삭제)**. `isNewUser=TRUE` = 이번 로그인 트랜잭션에서 `users` 행이 신규 생성됨(구글 계정 최초 연결).

- 한계와 가드: 온보딩 도중 이탈 후 재로그인하면 `isNewUser=FALSE` 라 서버는 온보딩을 다시 노출하지 않는다 → **클라이언트가 온보딩 완료(3.3 성공) 전 홈 진입을 막는다**(뒤로가기 시 종료 확인). 재설치 등으로 복구가 필요해지면 `users` 에 온보딩 완료 플래그 컬럼을 추가하는 안으로 승격(§16 #16).

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 401 | `INVALID_GOOGLE_TOKEN` | 구글 ID 토큰 검증 실패(만료·위조·aud 불일치) |
| 403 | `ACCOUNT_SUSPENDED` | 정지 계정 로그인 시도 |

```json
{
  "result": "FAIL",
  "code": "INVALID_GOOGLE_TOKEN",
  "message": "구글 인증에 실패했습니다. 다시 로그인해 주세요.",
  "data": null,
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

### 2.2 POST /auth/refresh 🔓

**요청** `{ "refreshToken": "..." }`

**성공 응답 (200)** — `data` 는 2.1 과 동일한 토큰 쌍 + `user` 요약.

**rotation 동작(설명)** — refresh token 은 1회용이다:

1. 클라가 access 만료(401) 감지 → 보관 중인 refresh 로 2.2 호출.

2. 서버는 해당 refresh 행을 잠그고 유효성 검증(만료·revoke 여부) → 기존 행 `revoked_dttm` 기록 → **새 refresh 행 INSERT** → 새 access+refresh 쌍 반환.

3. 클라는 받은 쌍으로 **저장소를 교체**한다(이전 refresh 는 즉시 무효).

4. 이미 revoke 된 refresh 가 다시 들어오면(**재사용 감지** — 탈취 신호) 401 `INVALID_REFRESH_TOKEN`.

클라 처리 규칙: 2.2 가 401 을 주면 로컬 토큰 전부 폐기 후 로그인 화면으로. 동시 요청으로 인한 이중 재발급 경합은 클라가 재발급을 단일 큐(mutex)로 직렬화해 방지한다.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | 만료·폐기·**재사용 감지**(rotation 위반) |
| 403 | `ACCOUNT_SUSPENDED` / `ACCOUNT_DELETED` | 재발급 시점 계정 상태 재검증 |

### 2.3 POST /auth/logout

**요청** `{ "refreshToken": "..." }` — 현재 기기 refresh token 만 revoke.

**성공 응답 (200)** — `data: null`. 이미 폐기된 토큰이어도 200(멱등). 디바이스 토큰 비활성화는 클라이언트가 별도로 §14.2 를 호출한다 **(코어↔알림 HTTP 호출 금지 원칙 — 클라 2콜)**.

**실패 분기** — 공통 외 없음.

---

## 3. 온보딩 (ONBOARDING)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 3.1 | GET | `/artists/ranking` | 인기 아티스트 목록(§5.1 재사용) | ONBOARDING-01 |
| 3.2 | GET | `/artists/search` | 아티스트 검색(§5.2 재사용) | ONBOARDING-02 |
| 3.3 | POST | `/onboarding` | 온보딩 완료(구독 일괄 + 닉네임 확정) | ONBOARDING-03/04 |

### 3.3 POST /onboarding

아티스트 선택과 닉네임 설정을 **하나의 원자 트랜잭션**으로 처리한다 **(기본안 — 화면은 2단계지만 제출은 마지막 1회. 중간 이탈 시 needsOnboarding 재판정이 깔끔)**.

**요청**

```json
{ "artistSeqs": [3, 7, 12], "nickname": "모티프러버" }
```

- 검증: `artistSeqs` 최소 1개·전부 비삭제 아티스트, 닉네임 형식(영문·한글·`.`·`_`, 2~20자). **중복 검사 없음(C1 — 회의 확정)**.

- 처리: `subscriptions` 일괄 생성 + `subscriber_count` 증가(같은 트랜잭션), `users.nickname` 갱신.

**성공 응답 (200)** — `data`:

```json
{ "subscribedCount": 3, "nickname": "모티프러버" }
```

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | `artistSeqs` 에 삭제·미존재 아티스트 포함 |
| 409 | `ALREADY_ONBOARDED` | 이미 온보딩 완료 사용자의 재호출(구독 1건 이상 존재) |

```json
{
  "result": "FAIL",
  "code": "ALREADY_ONBOARDED",
  "message": "이미 온보딩을 완료한 계정입니다.",
  "data": null,
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

---

## 4. 사용자·마이페이지 (USER)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 4.1 | GET | `/users/me` | 내 프로필 | MY-01, MY-07 |
| 4.2 | PATCH | `/users/me/nickname` | 닉네임 수정 | MY-01 |
| 4.3 | POST | `/users/me/profile-image/presign` | 프로필 이미지 업로드 URL 발급 | MY-01, MY-08 |
| 4.4 | PATCH | `/users/me/profile-image` | 프로필 이미지 반영 | MY-01, MY-08 |
| 4.6 | GET | `/users/{userSeq}/profile` | 타 사용자 정보 Bottom Sheet | CHALLENGE-07 |
| 4.7 | GET | `/users/me/subscriptions` | 구독 아티스트 카드 목록 | MY-02 |
| 4.8 | GET | `/users/me/videos` | 내 챌린지(그리드·뷰어 공용) | MY-04 |
| 4.9 | GET | `/users/me/activities` | 내 활동(영상 단위 카드 — 하트·내 댓글·답글) | MY-03, ACTIVITY-01/02 |

### 4.1 GET /users/me

마이페이지 첫 화면 **원콜** 응답 **(회의 확정 2026-09-02 개편)**.

**성공 응답 (200)** — `data`:

```json
{
  "userSeq": 1,
  "nickname": "모티프러버",
  "profileImageUrl": "https://cdn.../profiles/uuid.jpg",
  "repMotif": { "resultSeq": 10, "videoUrl": "signed-cookie 로 접근하는 master.m3u8", "thumbnailUrl": "signed" },
  "videos": [ { "videoSeq": 88, "thumbnailUrl": "...", "viewCount": 3400, "visibility": "PUBLIC" } ]
}
```

- `repMotif` 미설정 시 `null`. 영상은 signed URL(마이페이지 진입 시 자동재생·음소거). 설정·해제·문구 수정은 **§11.8 PATCH** **(R4 — 구 4.5 통합)**.

- `videos` = 내 참여 영상 **최신 9개 경량 프리뷰**(3×3 첫 화면 **(기본안 — 개수 확인)**), 없으면 `null`. 이후 스크롤·전체 그리드는 4.8. `viewCount` 포함(전 영상 그리드 공통 규칙 **(회의 확정 R2)**).

- `videoCount` 는 불요로 철회 **(R2)**.

**실패 분기** — 공통 외 없음.

### 4.2 PATCH /users/me/nickname

**요청** `{ "nickname": "새닉네임" }` — 형식 검증만(영문·한글·`.`·`_`, 2~20자). **중복 확인 없음(C1 — 회의 확정)**.

**성공 응답 (200)** — `data`: `{ "nickname": "새닉네임" }`

**실패 분기** — 400 `INVALID_PARAMETER`(형식 위반, 필드 오류 배열)만.

### 4.3 POST /users/me/profile-image/presign

**성공 응답 (200)** — `data`:

```json
{ "uploadUrl": "https://s3...presigned-put", "imageKey": "profiles/3f9c...jpg", "expiresIn": 300 }
```

### 4.4 PATCH /users/me/profile-image

클라이언트가 presigned PUT 업로드를 마친 뒤 호출.

**요청** `{ "imageKey": "profiles/3f9c...jpg" }` → **성공 응답 (200)** `data`: `{ "profileImageUrl": "https://cdn.../profiles/3f9c...jpg" }`

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 409 | `IMAGE_NOT_UPLOADED` | 해당 키의 S3 오브젝트 부재(업로드 미완료) — 발급된 업로드 흐름과 현재 S3 상태 충돌 |
| 403 | `FORBIDDEN` | 본인 발급 키가 아님(프리픽스·발급 기록 불일치) |

- MY-08 모티프 프레임 프로필: **클라이언트가 재생 중 프레임을 캡처해 동일 플로우(4.3→PUT→4.4)로 업로드** **(기본안 — 서버 측 프레임 추출은 인제스트 워커 부하·범위 초과로 비권장)**.

### 4.5 (삭제 — R4) 대표 모티프 설정 → §11.8 PATCH 통합

보관함의 "결과물 문구 수정"과 "대표 모티프 설정"이 같은 대상(모티프 결과물)에 대한 수정이라 **§11.8 `PATCH /motif/results/{resultSeq}`** 하나로 통합했다 **(회의 확정 R4)**.

### 4.6 GET /users/{userSeq}/profile

**성공 응답 (200)** — `data`:

```json
{
  "userSeq": 5,
  "nickname": "팬123",
  "profileImageUrl": "https://cdn.../profiles/..jpg",
  "subscribedArtists": [
    { "artistSeq": 3, "name": "카리나", "profileImageUrl": "..." }
  ]
}
```

- `participationCount`·`subscribedArtists[].groupName` 제거 **(회의 확정 R2)**.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `USER_NOT_FOUND` | 미존재 또는 `is_deleted=TRUE`(삭제 사용자 비노출) |

### 4.7 GET /users/me/subscriptions

**비페이지** — 구독 아티스트 전량 반환 **(회의 확정 2026-09-02 — 규모상 페이지 불요)**. 정렬 구독 최신순. 카드 앞면+뒷면 데이터를 한 번에 내린다.

**성공 응답 (200)** — `data`:

```json
{
  "list": [ {
    "artistSeq": 3, "name": "카리나", "profileImageUrl": "...",
    "passActive": true, "passExpiresDttm": "2026-10-01T00:00:00.000Z",
    "subscribedDays": 41,
    "challengeCount": 5,
    "purchasedAssetCount": 2, "totalAssetCount": 12
  } ]
}
```

- `groupName` 제거 **(회의 확정)**.

- `challengeCount` = `idx_pv_user_artist` 기반 비삭제 참여 영상 수.

- 모티프 카운트 **(2026-09-02 재확정 —* **v0.7 정의로 회귀***, 직전 C5 확정 폐기)**: 카드 `n/m` = `purchasedAssetCount`/`totalAssetCount`.

  - `purchasedAssetCount` = 해당 아티스트 에셋의 **개별 구매(`asset_purchases.status='ACTIVE'`) 수만**. **패스로 열린 에셋은 포함하지 않는다**(패스만 있으면 `0/m`).

  - `totalAssetCount` = 해당 아티스트 `is_active=TRUE` 에셋 수. **SECRET 도 별도 예외 없이 동일 집계한다.**

  - 패스 상태는 `passActive` 뱃지가 별도 표현. **최종 확정(R2) — DB 명세 v0.9 반영 완료.** 스키마(ERD) 변경은 없다 — 카운트는 집계 쿼리라 컬럼이 없고, 바뀐 것은 DB 명세서의 집계 규칙 서술뿐.

- 본 API 는 마이페이지 카드뿐 아니라 숍 화면에서 **구독 아티스트 목록 + 아티스트별 패스 보유 여부**를 구성하는 데 재사용한다. 아티스트별 에셋은 §11.1 `artistSeq` 필터로 별도 조회한다.

**실패 분기** — 공통 외 없음.

### 4.8 GET /users/me/videos

**비페이지 — 전량 반환** **(회의 확정 R4 — 커서에서 전환. 본인 영상 수가 크지 않다는 전제, 수백 건 누적 시 재검토)**, 최신순. 본인 영상이므로 **PUBLIC·PRIVATE 전부 포함**하고 항목마다 `visibility` 로 구분해 내린다.

**성공 응답 (200)** — `data`:

```json
{ "list": [ { "videoSeq": 88, "thumbnailUrl": "...", "viewCount": 3400, "visibility": "PRIVATE" } ] }
```

- `viewCount` 포함(전 영상 그리드 공통 규칙 **(R2)**). 그리드에서 항목 선택 시 §8.2 단건 조회로 뷰어 진입. 연속 스와이프 재생 요구 시 `?view=FULL` 확장 예약(§16 #18).

- 4.1 `videos`(최신 9개 프리뷰)와의 관계: 첫 화면은 4.1 로 그리고, "더보기/전체" 진입 시 본 API 1회 호출.

**실패 분기** — 공통 외 없음.

### 4.9 GET /users/me/activities** **(회의 확정 R2 — 마이페이지 "내 활동" 화면 기준 재설계)

**커서** `?cursor&size` — **영상 단위 카드**, 마지막 상호작용 최신순. 내가 좋아요 또는 댓글을 남긴 영상 1개가 카드 1장(상단 캐러셀)이고, 카드 아래에 하트 표시·내 댓글·**내 댓글에 달린 답글**이 붙는 구조(화면 시안 기준). 본인 게시물 제외. 탭 분리 없음(구 `type` 필터 삭제).

**성공 응답 (200)** — `data.list[]`:

```json
{
  "video": { "videoSeq": 88, "thumbnailUrl": "...", "viewCount": 3400, "likeCount": 12000 },
  "liked": true,
  "myComment": {
    "commentSeq": 501, "content": "이 챌린지 예지랑 너무 잘 어울린다 🔥",
    "likeCount": 742, "replyCount": 8, "regDttm": "2026-08-28T12:00:00.000Z",
    "parentComment": null
  },
  "replies": [
    { "commentSeq": 530, "author": { "userSeq": 9, "nickname": "dancing_mandu", "profileImageUrl": "..." }, "content": "😂😂😂", "regDttm": "..." },
    { "commentSeq": 531, "author": { "userSeq": 1, "nickname": "kkongdance", "profileImageUrl": "..." }, "content": "이거 보고 바로 챌린지 찍으러 갑니다", "regDttm": "..." }
  ],
  "lastInteractedDttm": "2026-08-28T12:00:00.000Z"
}
```

- `liked` = 하트 표시 여부(내가 좋아요 누름). `video.likeCount` 가 화면의 "♥ 1.2만". `viewCount` 포함(그리드 공통 규칙).

- `myComment` = 내가 단 댓글(없으면 `null` — 좋아요만 한 영상). 같은 영상에 내 댓글이 여러 개면 **최신 1개** **(기본안)**.

- `replies` = **내 댓글에 달린 답글** 최신 2개 프리뷰 **(기본안 — 개수 확인, §16 #22)**. 전체는 `myComment.replyCount` 와 함께 게시물 댓글로 이동. 내 댓글이 답글이면(`parentComment` 존재) depth 2 규칙상 `replies` 는 항상 빈 배열.

- 대상 삭제·비노출·댓글 사용 중지 시 카드 제외(ACTIVITY-02 정책). 좋아요·댓글 둘 다 있으면 카드 1장에 병합.

- 최근 검색어는 **서버 API·DB 없이 Android 로컬에서만 처리**한다. 서버 검색(§5.2)과 최근 검색어 저장은 분리한다.

---

## 5. 아티스트 (ARTIST)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 5.1 | GET | `/artists/ranking` | 아티스트 랭킹(전체·필터) | ARTIST-01, HOME-02 |
| 5.2 | GET | `/artists/search` | 검색 — 자동완성 겸용 **(v0.13 통합)** | ARTIST-02 |
| 5.3 | GET | `/artists/{artistSeq}` | 아티스트 상세 | ARTIST-DETAIL-01/02 |
| 5.4 | GET | `/artists/{artistSeq}/challenges` | 아티스트 챌린지 그리드 | ARTIST-DETAIL-05 |
| 5.5 | GET | `/artists/{artistSeq}/notices` | 아티스트 공지 목록 | ARTIST-DETAIL-06, NOTICE-01 |
| 5.6 | POST | `/artists/{artistSeq}/subscription` | 구독 | ARTIST-03 |
| 5.7 | DELETE | `/artists/{artistSeq}/subscription` | 구독 취소 | ARTIST-DETAIL-01 |
| 5.8 | GET | `/users/me/motif-results?artistSeq=` | 해당 아티스트와 만든 내 모티프(§11.5 재사용) | ARTIST-DETAIL-03 |
| ~~5.9~~ | ~~GET~~ | ~~`/artists/autocomplete`~~ | **삭제 (v0.13)** — §5.2 에 통합 | — |
| 5.10 | GET | `/artists` | 전체 아티스트 목록(구독자 수순·전량) **(신설)** | 아티스트 탭 |

### 5.1 GET /artists/ranking

`?filter=ALL|SUBSCRIBED|UNSUBSCRIBED&page&size` — Redis `rank:artist:current` 스냅샷 기반(서버 고정 정렬, `sort` 무시). TTL 5분. Who's On TOP4 는 `size=4` 재사용.

**성공 응답 (200)** — `data`:

```json
{
  "list": [ {
    "rank": 1, "rankDelta": 2,
    "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "...",
    "isSubscribed": false
  } ],
  "page": 0, "size": 20, "totalElements": 58, "totalPages": 3,
  "hasNext": true, "hasPrevious": false
}
```

- `rankDelta` = 직전 스냅샷(`:prev`) 순위 − 현재 순위. **상승 양수·하락 음수**(5위→3위 = `+2`, 3위→5위 = `-2`), 신규 진입 `null`. `totalElements` 는 스냅샷 ZSET 크기.

- 홈 Who's On(TOP4)과 랭킹 페이지는 **같은 API 재사용**(`size=4`) — 필드·캐시(TTL 5분)가 동일해 분리 실익 없음.

**실패 분기** — 공통 외 없음.

### 5.2 GET /artists/search — 검색 **(v0.13 — 자동완성 통합)**

`?q={검색어}&size` — **타이핑 중이든 엔터 실행이든 같은 API 를 쓴다.** v0.7(R4)에서 나눴던 §5.9 자동완성을 되돌려 합쳤다. 페이지 없이 상위 `size`(기본 20).

**검색 대상은 별칭 테이블이다** — `artist_aliases`·`group_aliases` 의 `alias` 를 `ILIKE '%q%'` 로 훑는다(DB v0.17). 언어별로 별칭을 저장하므로 "Karina" 로 치든 "카리나" 로 치든 같은 아티스트가 나온다. `language_code` 는 저장 구분용일 뿐 **검색에 쓰지 않는다** — 한국어 UI 사용자가 영문으로 치는 일이 흔해서, 언어로 거르면 오히려 안 나온다.

**응답의 `name` 은 `users.nickname` 고정이다.** 별칭은 찾는 데만 쓰고 표시하지 않는다 — 어느 질의로 걸렸든 화면에는 같은 이름이 나온다.

**성공 응답 (200)** — `data`:

```json
{ "list": [ { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "...", "isSubscribed": true } ] }
```

**그룹 매치** — 검색어가 그룹 별칭에 걸리면 **그 그룹의 비삭제 멤버 전원을 결과 목록에 포함**한다. 결과는 항상 멤버 단위이며, 그룹으로 묶은 중첩 구조를 만들지 않는다.

**정렬** — 본인 별칭으로 걸린 아티스트가 그룹 확장으로 딸려온 멤버보다 **항상 앞**이다. 각 덩어리 안에서는:

| 순위 | 기준 |
|---|---|
| 1 | 완전 일치(대소문자 무시) |
| 2 | 접두 일치 |
| 3 | 별칭 길이 ASC |
| 4 | `subscriber_count` DESC |
| 5 | `artistSeq` ASC |

- 한 아티스트가 별칭 여러 개로 걸리면 `artistSeq` 기준 **1건으로 합치고, 가장 높은 매치**를 정렬 키로 쓴다.

- `size` 는 정렬·중복 제거를 마친 뒤의 **단순 상한**이다. 그룹 멤버가 경계에서 잘려 나가는 것을 허용한다 — 그룹을 통째로 넣으면 `size` 가 상한이 아니게 된다.

- 삭제된 아티스트·그룹은 제외한다. 별칭 행은 소프트 삭제를 따라가지 않으므로 **조회 시 조인에서 거른다**(DB v0.17).

- **오타 보정이 없다.** v0.12 까지의 pg_trgm 유사도 검색을 `ILIKE` 로 바꾸면서 "카리다" 같은 오타는 0건이 된다. 유사도 정렬을 버린 대신 위 규칙이 결정적이라 같은 질의가 항상 같은 순서를 준다.

- 결과 0건도 200 + 빈 배열. (최근 검색어 적재는 기능 제외로 삭제 — §16 #19.)

- 클라 구현 규약(구 §5.9 승계): 입력 **디바운스 300ms** + **직전 요청 취소 후 최신 질의만 반영**(코루틴 `Flow.debounce` + `collectLatest`/`flatMapLatest` — 응답 역전으로 옛 결과가 덮어쓰는 것 방지). 한글 IME 조합 중 텍스트도 그대로 전송한다. 빈 문자열이면 호출하지 않고 목록을 비운다.

- 아티스트가 수백 규모라 DB 직조회로 충분하다 — 캐시를 두지 않는다.

**실패 분기**

| 조건 | 코드 |
|---|---|
| `q` 가 비었거나 공백뿐 | 400 `INVALID_PARAMETER` — `'%%'` 가 전체 조회가 되므로 서버가 막는다 |

### ~~5.9 GET /artists/autocomplete — 자동완성~~ — **삭제 (v0.13)**

**§5.2 에 통합됐다.** v0.7(R4)에서 "응답을 최소화" 하려고 나눴으나, 두 응답의 차이가 `isSubscribed` 한 칸뿐이었다. 경로가 둘이면 온보딩 §3.2 가 어느 쪽을 부르는지도 갈린다.

구 §5.9 의 **1~2자 접두 / 3자 이상 pg_trgm 2단 전략도 함께 폐기**한다 — 단일 `ILIKE` 다. 클라 디바운스 규약은 §5.2 로 옮겼다.

### 5.8 GET /users/me/motif-results?artistSeq={artistSeq}

별도 구현 없음 — **§11.5(보관함 목록)의 `artistSeq` 필터를 그대로 재사용**하는 진입점이다(커서·항목 동일, signed 썸네일). 용도: 아티스트 상세에서 상단 자동재생(§5.3 `myMotifVideos`, 최신 5개) 너머의 전체 결과물 열람.

### 5.3 GET /artists/{artistSeq}

**성공 응답 (200)** — `data`:

```json
{
  "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "...",
  "bio": "aespa의 카리나입니다.", "subscriberCount": 15230,
  "challengeCount": 121,
  "isSubscribed": true, "subscribedDays": 492,
  "pass": { "active": true, "expiresDttm": "2026-10-01T00:00:00.000Z" },
  "myMotifVideos": [
    { "resultSeq": 10, "videoUrl": "signed-cookie 로 접근하는 master.m3u8", "thumbnailUrl": "signed", "durationMs": 9800 }
  ]
}
```

- `challengeCount` = 해당 아티스트의 비삭제 챌린지 수, `subscribedDays` = 구독 경과일("WITH n days" — 미구독 시 `null`) **(회의 확정 R2)**.

- `myMotifVideos` = **화면 상단 자동재생용, 내가 이 아티스트와 만든 모티프 결과물 최신 5개** **(기본안 — 개수·만료 §16 #23)**, signed URL. 없으면 빈 배열(상단 영역 미노출). 구 `hasMyMotifResults` 대체. 전체 목록·더보기는 §5.8 재사용.

- **연속 재생 방식(제안)**: 클라가 배열 순서대로 플레이어 재생목록(ExoPlayer `ConcatenatingMediaSource`)에 넣어 하나 끝나면 자동으로 다음 재생. signed URL 만료(10분)는 "재생 시작" 기준이라 진입 시 일괄 발급으로 충분하고, 오래 머물다 만료로 로드 실패하면 해당 건만 §11.6 단건 재조회로 URL 갱신 — 서버 추가 API 불요.

- `pass.active=false` 면 클라는 "모티프 패스 알아보기" 노출(→ §11.4).

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트 |

### 5.4 GET /artists/{artistSeq}/challenges · 5.5 /notices

- 5.4: **비페이지 — 전량 반환** **(회의 확정 R2)**, 최신순. `data`: `{ "list": [ { "challengeSeq": 11, "thumbnailUrl": "...", "viewCount": 120000 } ] }` (그리드 경량 + 조회수. `title` 없음).

- 5.5: **커서** `?cursor&size` — 해당 아티스트의 공지를 **최신순 고정**으로 반환한다. 별도 공지 상세 API(구 §10.1)는 사용하지 않으며, 공지 화면에 필요한 내용을 목록 항목에서 모두 제공한다.

**5.5 성공 응답 (200)** — `data.list[]`:

```json
{
  "noticeSeq": 5,
  "artist": { "artistSeq": 3, "name": "카리나", "profileImageUrl": "..." },
  "content": "전체 본문...",
  "images": [
    { "imageUrl": "https://cdn.../notices/1.jpg", "displayOrder": 0 },
    { "imageUrl": "https://cdn.../notices/2.jpg", "displayOrder": 1 }
  ],
  "likeCount": 200,
  "commentCount": 14,
  "isLiked": true,
  "regDttm": "2026-09-01T09:00:00.000Z"
}
```

- `images` 는 `displayOrder` 오름차순 **전체 배열**이며 대표 이미지 전용 `repImageUrl` 은 사용하지 않는다. `title` 은 없다.

- `artist` 는 `{artistSeq, name, profileImageUrl}` 까지만 반환하고 `groupName` 은 제외한다.

- 공통 실패: 404 `ARTIST_NOT_FOUND`.

### 5.6 POST · 5.7 DELETE /artists/{artistSeq}/subscription

생성/삭제 + `subscriber_count` 원자 증감(같은 트랜잭션). **멱등** **(기본안)**.

**연타 처리 규약**: 서버는 멱등이라 정합성은 항상 유지 — 방어는 클라가 담당한다. 버튼 즉시 낙관적 UI 반영 → **디바운스(약 500ms) 후 최종 상태만 1회 전송**, 응답의 `isSubscribed`/`subscriberCount` 로 UI 보정. 요청 진행 중 중복 탭 무시. 좋아요 토글은 §1.2의 204 예외 규약을 따른다.

**성공 응답 (200)** — `data`: `{ "isSubscribed": true, "subscriberCount": 15231 }` (5.7 은 `false`). 이미 구독 중 POST·미구독 DELETE 도 200(카운터 불변).

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 미존재·삭제 아티스트(구독 신규 생성 시) |

- 구독 취소 UI 는 아티스트 상세에서만 노출(기능 명세)이나 API 는 공통. **패스 취소(§11.10)와 별개** — 이쪽은 무료 팔로우.

### 5.10 GET /artists — 전체 아티스트 목록 **(신설)**

파라미터 없음. **비페이지 — 전량 반환**, `artists.subscriber_count` **내림차순**(동점은 `artistSeq` ASC 고정). 비삭제만.

**성공 응답 (200)** — `data`:

```json
{ "list": [ { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "...", "isSubscribed": true } ] }
```

- 항목은 §5.2 검색과 **같은 모양**(아티스트 요약 + `isSubscribed`)이다. 정렬 기준인 `subscriberCount` 는 싣지 않는다 — 개별 값은 §5.3 상세가 준다.

- **§5.1 랭킹으로 대체할 수 없다.** 랭킹은 배치가 만든 Redis 스냅샷이 1차 출처이고 정렬이 챌린지 반응 점수라 구독자 수순이 아니다(구독은 점수에 들어가지 않는다 — §5.6). 이쪽은 DB 직조회다.

- 아티스트가 수백 규모라 전량이 싸다(§5.2 와 같은 근거). 캐시를 두지 않는다. 0명이면 빈 배열 200.

**실패 분기** — 공통 외 없음.

---

## 6. 홈 (HOME)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 6.1 | GET | `/home/hot-videos` | 실시간 인기 참여 영상(HOT) | HOME-01 |
| 6.2 | GET | `/artists/ranking?size=4` | Who's On TOP4 (§5.1 재사용) | HOME-02 |
| 6.3 | GET | `/home/activities` | 구독 아티스트 최신 활동(모티프+공지 통합) | HOME-03 |

### 6.1 GET /home/hot-videos

**커서** `?cursor&size` **(R2 — 영상 목록 커서 규약)** — Redis `rank:video:current` 스냅샷 순서 고정. 홈 카드·세로 뷰어 이어보기 공용. TTL 5분. **경량 항목** **(회의 확정 R2 — §8.2 전체에서 축소)**.

**성공 응답 (200)** — `data`:

```json
{ "list": [ {
  "rank": 1, "rankDelta": 2, "videoSeq": 88, "videoUrl": "https://cdn.../private/challenge/{jobUuid}/master.m3u8",
  "thumbnailUrl": "...", "durationMs": 15000,
  "song": { "title": "bailar toda la noche", "singerName": "mochico" },
  "author": { "userSeq": 5, "nickname": "팬123" },
  "viewCount": 3400
} ], "prevCursor": null, "nextCursor": "...", "hasPrevious": false, "hasNext": true }
```

- **`rankDelta`** *(2026-09-21 신설 — ADR-0015)*: 직전 순위 − 현재 순위. 상승이 양수, **신규 진입은 `null`**(§5.1 과 같은 규약). 대비 구간은 1시간이다.

- **랭킹 점수** *(2026-09-21 재정의 — ADR-0015)*: 구 기본안 `view_count × 0.5^(age/H)` 를 폐기하고 **누적·최근 1시간·증감** 세 항의 가중합으로 바꿨다. 시간 감쇠가 빠져서 오래된 영상도 지금 뜨면 1위에 오른다. 조회수는 §8.5 가 Redis 에 모아 20분 배치가 집계한다. 공식·모집단·상한은 [`detail/06-home.md`](detail/06-home.md) §6.1.

- 카드 하단 표기("mochico · bailar toda la noche")용 `song`, 작성자 `author` 요약 포함 **(기본안 — 표기가 가수·곡명이면 author 제거 가능)**. 클릭 즉시 `videoUrl` 재생, 좋아요·댓글 등 상세 오버레이는 뷰어 진입 시 §8.2 단건 1회 조회.

- **캐러셀 → 뷰어 구현 노트** **(R4)**: 홈 가로 캐러셀과 클릭 후 세로 뷰어는 **같은 6.1 커서 시퀀스 하나를 공유**한다 — 별도 API 없음.

  1. 홈 진입: `size=10` 첫 호출 → rank 1부터 가로 슬라이드. 끝에 접근하면 `nextCursor` 로 이어 로드.

  2. n번째 카드 클릭: 이미 받은 목록·`nextCursor`·클릭 인덱스를 뷰어에 전달(공유 ViewModel) — 뷰어는 n번째부터 시작해 스와이프로 목록을 순회하고, 소진 시 같은 커서로 이어 받는다. 재조회·중복 로드 없음.

  3. 순서 일관성: 커서에 스냅샷 버전이 박혀 있어(§1.9 구현 노트) 배치가 랭킹을 갈아도 진행 중인 시퀀스는 같은 스냅샷을 계속 페이지한다. 홈 재진입(당겨서 새로고침) 시에만 새 스냅샷 시작.

**실패 분기** — 공통 외 없음.

### 6.3 GET /home/activities

`?page&size` — 구독 아티스트의 **신규 모티프 에셋 등록 + 공지** 를 등록 시각 통합 최신순(서버 고정 정렬).

**성공 응답 (200)** — `data.list[]`:

```json
[
  { "type": "MOTIF_ASSET", "regDttm": "2026-09-02T05:00:00.000Z",
    "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "..." },
    "asset": { "assetSeq": 21, "title": "9월 신규 에셋", "thumbnailUrl": "...", "durationMs": 9800 } },
  { "type": "NOTICE", "regDttm": "2026-09-02T04:00:00.000Z",
    "artist": { "artistSeq": 7, "name": "윈터", "profileImageUrl": "..." },
    "notice": { "noticeSeq": 5, "content": "컴백 소식 전체 본문...",
      "images": [ { "imageUrl": "...", "displayOrder": 0 } ],
      "likeCount": 200, "commentCount": 14, "isLiked": false } }
]
```

- NOTICE 항목 **(회의 확정 2026-09-02)**: `content` 전체 + `images` 전체 배열(`displayOrder` 순, 구 repImageUrl 폐기) + `likeCount`/`commentCount` + `isLiked` *(R6 — 공지 상세 API 삭제로 6.3 항목이 공지 화면의 진입 데이터가 되므로 좋아요 초기 상태를 함께 내린다. 5.5 와 동일 필드)*.

- MOTIF_ASSET 항목 확정 **(R2)**: `artist{artistSeq, name, groupName, profileImageUrl}` + `asset{assetSeq, title, thumbnailUrl, durationMs}` — 좋아요·댓글 카운트 없음(기능 부재 확정). `title` 은 **전용 컬럼 `motif_assets.title`** 값이다 *(R6 — DB v0.11 신설, 구 description 매핑 해제. description 은 소개 문구로 별도 유지)*. 클릭 시 §11.2 에셋 상세 이동.

- 이종 통합 정렬 키 `(regDttm, type, seq)` **(기본안)**. 홈에는 상위 일부, 전체는 페이지 스크롤. 구독 0건이면 빈 목록 200.

**실패 분기** — 공통 외 없음.

---

## 7. 챌린지 원본 (CHALLENGE)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 7.1 | GET | `/challenges/feed` | 원본 챌린지 숏폼 피드 | CHALLENGE-01 |
| 7.2 | GET | `/challenges/{challengeSeq}` | 챌린지 상세 Bottom Sheet | CHALLENGE-02 |
| 7.3 | POST / DELETE | `/challenges/{seq}/like` | 원본 좋아요 토글 | CHALLENGE-01 오버레이 |
| 7.4 | POST | `/challenges/{seq}/view` | 조회 이벤트 | HOME/랭킹 집계 |
| 7.5 | — | 댓글 | §9 공통 댓글(`type=challenge`) | CHALLENGE-06 |

### 7.1 GET /challenges/feed

**커서** `?cursor&size` **(R2 — 영상 목록 커서 규약)** — 구독 아티스트 + 인기·관심 기반 추천 종합(서버 내부 로직, `rank:challenge` 활용).

**성공 응답 (200)** — `data.list[]`:

```json
{
  "challengeSeq": 11, "videoUrl": "https://cdn.../challenges/{uuid}/master.m3u8",
  "thumbnailUrl": "...", "durationMs": 15000,
  "artist": { "artistSeq": 3, "name": "카리나", "profileImageUrl": "..." },
  "song": { "title": "Supernova", "singerName": "aespa" },
  "viewCount": 120000, "participantCount": 340, "likeCount": 5600, "commentCount": 210,
  "isLiked": false, "isSubscribed": true
}
```

- `thumbnailUrl` **부활**(첫 프레임 로딩 전 플레이스홀더 **(회의 확정 R2)**), `artist.groupName` 제거 유지, `isSubscribed`(피드 오버레이 구독 버튼용)는 7.1 항목 소속. **`title` 삭제** **(R3 — challenges.title 컬럼 삭제)**: 화면 식별은 곡명(`song.title`)·아티스트로.

- `participantCount` 는 챌린지의 게시 참여 수이며 **PUBLIC·PRIVATE를 모두 포함**한다. PRIVATE 영상 자체의 조회 비노출 정책과 카운트 집계는 분리한다.

- **구현 현황 (2026-09-14)** — 추천 로직은 아직 없다. `rank:challenge` 랭킹이 만들어지지 않아 **최신순**으로 내려간다. 커서는 §1.9 대로 서버가 만든 opaque 토큰이라 랭킹이 붙어도 클라이언트 계약은 바뀌지 않는다. `prevCursor`·`hasPrevious` 는 순방향 구현이라 항상 `null`·`false` 다(§1.9 — 역방향은 §16 미결).

**실패 분기** — 공통 외 없음.

### 7.2 GET /challenges/{challengeSeq}

**성공 응답 (200)** — `data` = 7.1 항목(이미 `isSubscribed` 포함) + `description` + 슬롯 정보 **(기본안 — DB v0.10 편집본 도입 파급, 확인 필요)**:

```json
{
  "participants": [
    { "artistSeq": 7, "name": "윈터", "participantMask": 1,
      "profileImageUrl": "https://cdn.example/profiles/uuid.jpg",
      "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 } }
  ],
  "editedChallenges": [
    { "participantMask": 1, "previewImageUrl": "https://cdn.example/edited/11/p1.jpg" }
  ]
}
```

- `participants` = 교체 가능한 인물 슬롯(추가 참여 아티스트 — `groupName` 제거 **(회의 확정)**). 챌린지 주인은 슬롯이 아니다(원 없음·교체 불가).
- `anchor` **(2026-09-18, S15P21D104-618 — `bbox` 대체)** = 촬영 전 선택 화면(10-1~3)에서 프로필 원이 앉는 자리. **썸네일 대비 0~1 비율**(`x`·`r` 가로, `y` 세로) — 뷰 `W×H` 에 중심 `(x·W, y·H)`, 반지름 `r·W`. 시안(402 폭·지름 80)이면 `r ≈ 0.0995`. 어드민이 아직 찍지 않은 옛 슬롯은 `null` → 원 숨김. `bbox` 는 삭제.
- `editedChallenges` **(2026-09-18)** = 조합(`mask`)별 **선택 화면 이미지**. 고른 슬롯 마스크를 OR 한 값과 같은 항목의 `previewImageUrl` 을 썸네일 자리에 띄운다(배경 교체 개념). mask 0(무선택)은 항목이 없고 `thumbnailUrl` 이 그 화면. `previewImageUrl` 이 `null`(옛 편집본)이거나 항목이 없으면 썸네일로 폴백. 편집본이 있으면 항상 2ⁿ−1 전조합, 마스크 오름차순. 상세는 [`detail/07-challenge.md`](detail/07-challenge.md).
- `profileImageUrl` 추가 **(2026-09-14)** — 설명 시트(화면 3-2)가 참여자를 아바타로 그린다. 값은 `users.profile_image_key` 를 응답 조립 시점에 CDN URL 로 바꾼 것이며 저장 컬럼이 아니다. 미등록이면 `null`.
- **편집본이 0개인 챌린지가 있을 수 있다** **(2026-09-14)** — 슬롯은 등록됐지만 AI 편집본이 아직 없는 상태다. 재생·표시는 정상이고 촬영만 §13.1 의 `EDITED_CHALLENGE_NOT_FOUND` 로 막힌다. 어드민 등록이 **0개 또는 정확히 2ⁿ−1개**만 허용하므로 부분 집합은 존재하지 않는다.

- `availableMasks` 삭제 **(회의 확정 R4)** — **운영 전제: 슬롯 조합의 편집본은 어드민이 전부 등록**한다(슬롯 1~2개 = 조합 1~3개 수준이라 현실적). 이 전제가 깨지면 조합 유효성은 13.1 의 404(`EDITED_CHALLENGE_NOT_FOUND`)로만 사후 판별되어 사용자가 촬영 직전에 실패를 겪는다 — 전제 유지 여부는 §16 #28.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` | 미존재·삭제 챌린지 |

### 7.3 POST / DELETE /challenges/{seq}/like

멱등 토글 — `challenge_likes` 생성/삭제 + `like_count` 원자 증감. 클라이언트는 현재 상태를 낙관적으로 반전하고 카운트를 ±1 처리하며, 서버 실패 시 로컬 상태를 롤백한다.

**성공 응답: 204 No Content** — `isLiked`·`likeCount` 응답 없음. **실패**: 404 `CHALLENGE_NOT_FOUND`.

### 7.4 POST /challenges/{seq}/view

Redis 조회수 버퍼 가산(1분 플러시). **1회 인정 기준(중복·최소 시청·본인 포함)은 미결** — 정책 확정 시 서버 내부에서만 반영, 계약 불변.

**성공 응답 (202)** — `data: null`. **실패**: 404 `CHALLENGE_NOT_FOUND` **(기본안 — 존재 검증은 Redis 적재 전 경량 캐시로)**.

**§8.5 와 더 이상 같은 규약이 아니다** *(2026-09-21 — ADR-0015)*. 영상 쪽은 묶음 수신 + 20분 집계로 바뀌었고 존재 검증·`404` 가 없어졌다. 챌린지 조회 이벤트를 같은 구조로 옮기는 것은 후속 티켓이며, 그때까지 이 절은 구 규약 그대로다.

---

## 8. 참여 영상 (VIDEO)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 8.1 | GET | `/challenges/{seq}/videos` | 챌린지별 참여 영상 피드 | CHALLENGE-03 |
| 8.2 | GET | `/videos/{videoSeq}` | 참여 영상 단건 | 공통 뷰어 |
| 8.3 | POST / DELETE | `/videos/{seq}/like` | 좋아요 토글 | CHALLENGE-05 |
| 8.5 | POST | `/videos/view` | 조회 이벤트 | **영상별 묶음 수신** (2026-09-21 재정의) |
| 8.6 | POST | `/videos/{seq}/hide` | 관심 없음 | CHALLENGE-09 |
| 8.7 | POST | `/videos/{seq}/report` | 영상 신고 | CHALLENGE-08 |
| 8.8 | POST | `/videos` | 참여 영상 게시(업로드 완료) | CHALLENGE-UPLOAD-01/02 |
| 8.9 | PATCH | `/videos/{seq}` | 게시 정보 수정 | CHALLENGE-UPLOAD-06 |
| 8.10 | DELETE | `/videos/{seq}` | 게시 영상 삭제 | CHALLENGE-UPLOAD-07 |

### 8.1 GET /challenges/{seq}/videos

**커서** `?cursor&size` **(R2 — 영상 목록 커서 규약)** — 추천 정렬(좋아요·댓글·조회수 + `has_artist_like`/`has_artist_comment` 강가중, 서버 내부 고정). 제외: PRIVATE(타인)·삭제 영상·삭제 사용자·**요청자의 video_hides**.

**성공 응답 (200)** — `data.list[]` = 8.2 공통 항목. **실패**: 404 `CHALLENGE_NOT_FOUND`.

### 8.2 GET /videos/{videoSeq} — 참여 영상 공통 응답 항목

**성공 응답 (200)** — `data` (이하 모든 "참여 영상 항목"이 이 형태):

```json
{
  "videoSeq": 88, "challengeSeq": 11, "description": "재밌었다!",
  "videoUrl": "https://cdn.../results/challenge/{jobUuid}/master.m3u8", "thumbnailUrl": "...", "durationMs": 15000,
  "author": { "userSeq": 5, "nickname": "팬123", "profileImageUrl": "..." },
  "viewCount": 3400, "likeCount": 120, "commentCount": 15,
  "hasArtistLike": true, "hasArtistComment": false,
  "isLiked": false, "commentEnabled": true, "visibility": "PUBLIC",
  "isMine": false, "regDttm": "2026-09-01T10:00:00.000Z"
}
```

- `videoUrl`: **HLS 마스터 재생목록**(`master.m3u8`) 주소다 — 단일 MP4 가 아니다(§1.7).
  PUBLIC=공개 CDN, 본인 PRIVATE=core-api 가 서명해 다시 쓴 재생목록. `isMine=true` 면 클라가 Bottom Sheet 에 수정·삭제 노출.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 미존재·삭제 영상·삭제 사용자 콘텐츠·**타인의 PRIVATE**(존재 은닉)·삭제 챌린지 소속 |

### 8.3 POST / DELETE /videos/{seq}/like

멱등 토글. 클라이언트는 낙관적 UI로 상태/카운트를 반영하고 실패 시 롤백한다.

**성공 응답: 204 No Content** — `isLiked`·`likeCount` 응답 없음. **실패**: 404 `VIDEO_NOT_FOUND`.

### 8.4 (삭제 — v0.8) 참여 영상 좋아요 사용자 목록

`GET /videos/{seq}/likes` 기능은 제거한다.

### 8.5 POST /videos/view → **202** (`data: null`)

*2026-09-21 재정의 — ADR-0015.* 경로에서 `{seq}` 가 빠지고 **여러 영상의 조회수를 묶어** 받는다: `{ "views": { "101": 3, "102": 1 } }`. 한 요청 최대 50개 영상 · 영상당 최대 count 100 이며 하나라도 어기면 **전체 400** 이다(부분 수용 없음).

**`VIDEO_NOT_FOUND` 가 없어졌다** — 존재 검증을 하지 않는다(집계를 Redis 로 뺀 의미가 사라진다). 없는 키는 20분 배치의 `UPDATE` 가 0행으로 버린다. Redis 장애도 202 다(fail-open). 상세는 [`detail/08-video.md`](detail/08-video.md) §8.5.

**§7.4 는 아직 구 규약이다** — 같은 구조로 바꾸는 것은 후속 티켓이다.

### 8.6 POST /videos/{seq}/hide

행 존재 자체가 상태(해제 API 미제공 **(기본안)**). 이후 8.1 피드에서 제외.

**성공 (200)** `data: null` — 이미 숨김 상태도 200(멱등). **실패**: 404 `VIDEO_NOT_FOUND`.

### 8.7 POST /videos/{seq}/report

**요청** `{ "reasonCode": "SPAM" }` — **코드표 미결(§16 #10)**.

**성공 (200)** `data: null` → 클라는 다음 영상으로 이동.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `VIDEO_NOT_FOUND` | 대상 없음 |
| 409 | `ALREADY_REPORTED` | 1인 1대상 1회 — 처리 결과 무관 재신고 불가 |

### 8.8 POST /videos (게시)

**요청**

```json
{ "jobUuid": "3fa8...", "description": "재밌었다!", "visibility": "PUBLIC", "commentEnabled": true }
```

- 검증: **요청 사용자 `role=ARTIST` 면 게시 불가(403 `FORBIDDEN`)**. 그 외 잡 `CHALLENGE`·`DONE`·본인 소유·미게시(`uq_participation_videos_job`), 원본 챌린지 비삭제 재확인.

- 처리: `participation_videos` 생성(영상·썸네일 키는 잡 결과 복사, `artist_seq` 는 챌린지에서 복사) + `participant_count +1` 같은 트랜잭션. `title` 없음 **(R3 — 컬럼 삭제, 기능 명세서 CHALLENGE-UPLOAD-01 정정 필요)**.

**성공 응답 (201)** — `data`: `{ "videoSeq": 88 }` → 전체 화면 뷰어 이동(CHALLENGE-UPLOAD-05).

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` | 잡 미존재·타인 잡 |
| 409 | `JOB_NOT_DONE` | 잡 상태가 DONE 아님(진행 중·실패) |
| 409 | `ALREADY_PUBLISHED` | 이미 게시된 잡 |
| 409 | `CHALLENGE_DELETED` | 게시 시점 원본 챌린지 삭제 — 클라는 "삭제된 챌린지" 안내 |
| 403 | `FORBIDDEN` | ARTIST 계정의 참여 영상 게시 시도 |

### 8.9 PATCH /videos/{seq}

수정 가능: `description`, `visibility`, `commentEnabled`. (제목은 컬럼 삭제로 개념 자체가 없음 **(R3)**.) 부분 수정(보낸 필드만 반영).

**성공 (200)** `data` = 8.2 항목(수정 반영본). **실패**: 404 `VIDEO_NOT_FOUND` / 403 `FORBIDDEN`(본인 아님).

### 8.10 DELETE /videos/{seq}

소프트 삭제 + 최초 삭제 시 `participant_count -1`.

**성공 (200)** `data: null`. **실패**: 404 `VIDEO_NOT_FOUND` / 403 `FORBIDDEN`(본인 아님).

---

## 9. 댓글 공통 (COMMENT) — 챌린지·참여 영상·공지 3종

경로 변수 `{type}` ∈ `challenge | video | notice` (댓글 테이블 3종과 1:1). 목록·작성은 대상 리소스 하위, 개별 조작은 `/comments/{type}/{commentSeq}`.

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 9.1 | GET | `/challenges/{seq}/comments` `/videos/{seq}/comments` `/notices/{seq}/comments` | 루트 댓글 목록 | CHALLENGE-06, NOTICE-02 |
| 9.2 | GET | `/comments/{type}/{commentSeq}/replies` | 답글 펼치기 | 대댓글 |
| 9.3 | POST | (9.1과 동일 경로) | 댓글·답글 작성 | 〃 |
| 9.4 | DELETE | `/comments/{type}/{commentSeq}` | 내 댓글 삭제 | CHALLENGE-06, ACTIVITY-02 |
| 9.5 | POST / DELETE | `/comments/{type}/{commentSeq}/like` | 댓글 좋아요 토글 | CHALLENGE-06 |
| 9.6 | POST | `/comments/{type}/{commentSeq}/report` | 댓글 신고 | CHALLENGE-08, NOTICE-02 |

이하 실패 분기의 `*_NOT_FOUND` 는 대상 종류에 따라 `CHALLENGE_NOT_FOUND` / `VIDEO_NOT_FOUND` / `NOTICE_NOT_FOUND` / `COMMENT_NOT_FOUND`.

### 9.1 루트 댓글 목록

`?page&size` — 전체 루트 댓글을 아래 우선순위로 정렬한 뒤 **요청한 `size` 만큼** 반환한다.

1. **대상 아티스트의 댓글** — 최신순
2. **내 댓글** — 최신순
3. **일반 댓글** — 최신순

- "대상 아티스트"는 단순 `role=ARTIST` 전체가 아니라 **해당 콘텐츠에 연결된 아티스트**만 의미한다. 챌린지는 `challenge.artist_seq`, 참여영상은 해당 영상의 `artist_seq`(원본 챌린지에서 복사), 공지는 `notice.artist_seq` 와 댓글 작성 아티스트가 일치할 때만 1순위다. 다른 ARTIST 계정의 댓글은 일반 댓글로 취급한다.

- 우선 그룹의 댓글이 여러 개면 모두 정렬 대상에 포함한다. 예: `size=20`, 대상 아티스트 댓글 15개, 내 댓글 10개라면 첫 페이지는 **아티스트 15개 + 내 댓글 최신 5개**를 반환한다.

**성공 응답 (200)** — `data.list[]`:

```json
{
  "commentSeq": 501, "content": "잘 봤어요!",
  "author": { "userSeq": 5, "nickname": "팬123", "profileImageUrl": "...", "isArtist": false },
  "likeCount": 3, "isLiked": false, "replyCount": 2,
  "isMine": false, "regDttm": "2026-09-01T12:00:00.000Z"
}
```

- `regDttm` 은 원시각 제공 — "n시간 전" 표기는 클라 처리. 답글은 접힌 상태(`replyCount`만) → 9.2로 펼침.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `*_NOT_FOUND` | 대상 리소스 없음 |
| 403 | `COMMENT_DISABLED` | (video) `comment_enabled=FALSE` — 데이터 동결, 목록도 차단 |

### 9.2 GET /comments/{type}/{commentSeq}/replies

`?page&size` — 등록순 고정. `data.list[]` = 9.1 항목(단 `replyCount` 없음).

**실패**: 404 `COMMENT_NOT_FOUND`(루트 아님·삭제 포함) / 403 `COMMENT_DISABLED`.

### 9.3 작성

**요청** `{ "content": "잘 봤어요!", "parentCommentSeq": 501 }` (루트는 parent 생략).

- 검증: 대상 비삭제, (video) `comment_enabled=TRUE`, parent 는 **루트 댓글**이며 동일 대상 소속.

- 처리: `comment_count +1`(같은 트랜잭션) + **FCM 발송 이벤트** 생성(NOTI-04/05 — 본인 행위는 발송하지 않음; 서버 notifications 테이블 미저장).

**성공 응답 (201)** — `data`: `{ "commentSeq": 502 }`

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 400 | `REPLY_DEPTH_EXCEEDED` | 답글에 답글(Depth 2 초과) |
| 400 | `PARENT_TARGET_MISMATCH` | parent 가 다른 게시물 소속 |
| 404 | `*_NOT_FOUND` | 대상·parent 없음 |
| 403 | `COMMENT_DISABLED` | (video) 댓글 사용 중지 |

### 9.4 삭제

본인만. 루트 삭제 시 직계 답글 소프트 삭제 + 대상 댓글들의 좋아요 하드 삭제 + **실제 삭제된 행 수만큼** `comment_count` 감소(DB 규칙).

**성공 (200)** `data`: `{ "deletedCount": 3 }` **실패**: 404 `COMMENT_NOT_FOUND` / 403 `FORBIDDEN`(본인 아님).

### 9.5 좋아요 토글

클라이언트는 낙관적 UI로 상태/카운트를 반영하고 실패 시 롤백한다.

**성공 응답: 204 No Content** — `isLiked`·`likeCount` 응답 없음. **실패**: 404 `COMMENT_NOT_FOUND` / 403 `COMMENT_DISABLED`(video, 생성·해제 모두).

### 9.6 신고

**요청** `{ "reasonCode": "SPAM" }` — reports 4대상 중 대응 컬럼.

**성공 (200)** `data: null` **실패**: 404 `COMMENT_NOT_FOUND` / 409 `ALREADY_REPORTED`.

---

## 10. 공지 (NOTICE)

공지 조회는 **§5.5 `GET /artists/{artistSeq}/notices`** 하나로 처리한다. 별도 공지 상세 API는 두지 않는다.

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 10.1 | — | (삭제) | 구 `GET /notices/{noticeSeq}` 공지 상세 제거 — §5.5로 통합 | NOTICE-02 |
| 10.2 | POST / DELETE | `/notices/{seq}/like` | 공지 좋아요 토글 | NOTICE-02 |
| 10.3 | — | 댓글 | §9 (`type=notice`) | NOTICE-02 |

### 10.1 (삭제 — v0.8) 공지 상세

공지 본문·이미지·좋아요/댓글 상태는 §5.5 목록 항목에서 모두 반환한다.

### 10.2 POST / DELETE /notices/{seq}/like

클라이언트는 낙관적 UI로 상태/카운트를 반영하고 실패 시 롤백한다.

**성공 응답: 204 No Content** — `isLiked`·`likeCount` 응답 없음. **실패**: 404 `NOTICE_NOT_FOUND`.

---

## 11. 모티프 (MOTIF)

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 11.1 | GET | `/motif/assets` | 에셋 카탈로그 전체 목록 + 아티스트 필터 | MOTIF-02/09 |
| 11.2 | GET | `/motif/assets/{assetSeq}` | 에셋 상세(미리보기·가격·권한) | MOTIF-03, PAYMENT-02 |
| 11.3 | — | (삭제) | 구 `/motif/shop` 집계 API 제거 — 클라가 4.7 + 11.1 조합 | MOTIF-09 |
| 11.4 | GET | `/artists/{artistSeq}/motif-pass` | 아티스트별 모티프 패스 정보 | ARTIST-DETAIL-02, MOTIF-07 |
| 11.5 | GET | `/motif/results` | 보관함 목록(최신순) | MOTIF-10 |
| 11.6 | GET | `/motif/results/{resultSeq}` | 결과물 상세(재생) | MOTIF-10 |
| 11.7 | — | (삭제) | 구 결과 저장 API 제거 — MOTIF 잡 완료 시 자동 생성 | MOTIF-CAPTURE-08 |
| 11.8 | PATCH / DELETE | `/motif/results/{resultSeq}` | 결과물 문구·대표 설정 수정 / 삭제 | MOTIF-10, MY-07 |
| 11.9 | GET | `/users/me/motif-passes` | 내 패스 상태 일람 | MOTIF-07, PAYMENT-06 |
| 11.10 | — | `/users/me/motif-passes/{artistSeq}/cancel` | 패스 취소 — 기획 유지, **현재 구현 범위 제외** | C4 |
| 11.11 | GET | `/shop/banners` | 숍 공용 배너 목록 *(R7 신설)* | 숍 화면 |

### 11.1 GET /motif/assets

`?page&size&artistSeq=` — **전체 에셋 카탈로그**를 최신순 고정으로 offset 페이지네이션한다. `artistSeq` 는 선택 필터이며, 생략하면 전체 아티스트 에셋을 동일 응답 형식으로 반환한다.

노출 규칙:

- `is_active=TRUE` 에셋: 모든 사용자에게 노출.

- `is_active=FALSE` 에셋: **개별 구매(`asset_purchases.status='ACTIVE'`) 보유 사용자에게만 노출**. PASS만 보유하거나 권한이 없는 사용자에게는 비노출.

- `SECRET` 은 별도 숨김·집계 예외가 없으며 NORMAL과 동일한 노출/구매/**PASS 커버** 판정을 적용한다 *(R6 확인 — PREMIUM 만 개별 구매 전용)*.
- `title` = 에셋 표시명 — 전용 컬럼(`motif_assets.title`, DB v0.11 신설 *(R6)*). `description` 은 소개 문구로 별도 유지.

**성공 응답 (200)** — `data.list[]`:

```json
{
  "assetSeq": 21,
  "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa" },
  "title": "9월 신규 에셋",
  "thumbnailUrl": "...",
  "description": "9월 신규 에셋 소개 문구",
  "durationMs": 9800,
  "assetType": "NORMAL",
  "myAccess": "PASS"
}
```

- `myAccess` ∈ `OWNED | PASS | NONE`, 우선순위는 **OWNED > PASS > NONE**. 개별 구매와 PASS가 동시에 유효하면 `OWNED`를 반환한다.

- **목록에서는 가격을 반환하지 않는다.** 가격·구매 가능 여부는 §11.2 상세에서 조회한다.

- 숍 화면은 §4.7 구독 목록으로 아티스트 섹션을 구성하고, 각 섹션의 에셋은 본 API의 `artistSeq` 필터로 조회한다. 구 11.3 집계 API는 사용하지 않는다.

**실패 분기** — 공통 외 없음(노출 가능 0건 → 빈 목록 200).

### 11.2 GET /motif/assets/{assetSeq}

**성공 응답 (200)** — `data`:

```json
{
  "assetSeq": 21,
  "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa" },
  "title": "9월 신규 에셋",
  "previewVideoUrl": "https://cdn.../assets/uuid/master.m3u8",
  "thumbnailUrl": "...",
  "description": "...",
  "durationMs": 9800,
  "assetType": "NORMAL",
  "price": { "amount": "3900.00", "currency": "KRW" },
  "purchasable": true,
  "myAccess": "NONE"
}
```

- `previewVideoUrl` 은 공개 CDN 의 **HLS 마스터 재생목록**이다(§1.7). 가공이 끝나기 전에는 업로드 MP4 키가 그대로 들어 있고 썸네일은 404 다 — 어드민 등록 직후의 짧은 구간이다.
- `price` 는 KR 활성 가격 — 없으면 `null` + `purchasable:false`.

- `myAccess` ∈ `OWNED | PASS | NONE`, 우선순위 `OWNED > PASS > NONE`.

- 판매 중단(`is_active=FALSE`) 에셋은 개별 구매자만 단건 조회할 수 있으며, 그 외 사용자는 404로 존재를 숨긴다.

- `SECRET` 은 NORMAL과 동일 규칙을 적용한다.

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ASSET_NOT_FOUND` | 미존재 또는 판매 중단 에셋에 대한 비구매자 조회 |

### 11.3 (삭제 — v0.8) GET /motif/shop

숍 전체 집계 API는 제거한다. Android는 §4.7에서 구독 아티스트와 패스 상태를 받고, §11.1 `artistSeq` 필터로 각 아티스트의 에셋을 조회해 렌더링한다.

숍 전체 공용 배너는 아티스트별 패스 배너와 별개이며, **URL 관리 위치(앱 고정/서버 설정 등)는 §16 미결 안건**으로 둔다.

### 11.4 GET /artists/{artistSeq}/motif-pass

특정 아티스트의 패스 화면용 정보. 기존 `/motif/shop/artists/{artistSeq}` 경로를 REST 관점에 맞게 변경한다.

**성공 응답 (200)** — `data`:

```json
{
  "artistSeq": 3,
  "bannerImageUrl": "https://cdn.../artists/3/motif-bg.jpg",
  "passActive": false,
  "expiresDttm": null,
  "prices": [
    { "durationMonths": 1, "amount": "9900.00", "currency": "KRW" },
    { "durationMonths": 3, "amount": "27900.00", "currency": "KRW" }
  ]
}
```

- `bannerImageUrl` 은 `artists.motif_bg_image_key` 를 공개 CDN URL로 변환해 반환한다. **숍 전체 공용 배너와는 별개**다.

- `passActive=true` 면 `expiresDttm` 을 반환하고, 현재 구현에서는 만료 전 연장 구매를 지원하지 않으므로 `prices: []` 를 반환한다.

- `passActive=false` 면 `expiresDttm:null`, `prices` 에 현재 판매 중인 개월 수별 활성 가격을 반환한다. 필드는 항상 존재하며 판매 가격이 없으면 빈 배열.

**실패**: 404 `ARTIST_NOT_FOUND`.

### 11.5 GET /motif/results · 11.6 /motif/results/{resultSeq}

- 11.5: **커서** `?cursor&size&artistSeq=` — **최신순만 지원**하며 정렬 파라미터를 받지 않는다. §5.8 은 `artistSeq` 필터를 재사용한다. `data.list[]`: `{ "resultSeq": 10, "thumbnailUrl": "signed", "artist": {요약}, "regDttm": "...", "isRep": true }`.

- 11.6 **성공 (200)** `data`: `{ "resultSeq": 10, "videoUrl": "signed-cookie 로 접근하는 master.m3u8", "thumbnailUrl": "signed", "description": null, "durationMs": 9800, "asset": {요약}, "artist": {요약}, "isRep": false, "regDttm": "..." }`

- `description` 은 선택 사항이며 `null` 일 수 있다. `resultSeq` 로 결과물을 직접 조회하는 단건 API가 11.6이다.

- 실패: 404 `MOTIF_RESULT_NOT_FOUND`(타인 소유 포함 — 존재 은닉).

### 11.7 (삭제 — v0.8) POST /motif/results

클라이언트가 결과물을 저장하는 API는 제거한다. **MOTIF 합성 잡이 완료될 때 서버가 `motif_results` 를 자동 생성**한다.

- 자동 생성 초기값: `description=NULL`, 대표 모티프 아님(`isRep=false`).

- 결과물 생성은 잡 완료 처리의 일부이며, `motif_results` 생성까지 성공한 뒤에만 MOTIF 잡을 `DONE` 으로 확정하고 FCM을 발송한다. 중복 완료 이벤트는 `job_uuid` 유일성으로 중복 결과 생성을 방지한다.

- 사용자가 합성 후 편집 화면에서 멘트를 입력하면 §11.8 PATCH로 별도 수정한다. 멘트를 입력하지 않고 나가면 `description=NULL` 상태로 정상 보관된다.

### 11.8 PATCH / DELETE /motif/results/{resultSeq}

**PATCH — 결과물 수정.** 부분 수정(보낸 필드만 반영):

```json
{ "description": "새 문구", "isRep": true }
```

- `description`: 잡 완료와 별개의 편집 작업으로, 최초 멘트 입력과 이후 수정 모두 본 API를 사용한다.

- `isRep:true`: 이 결과물을 대표 모티프로 지정. 서버는 `users.rep_motif_result_seq` 를 교체하며 기존 대표는 자동 해제한다. `isRep:false`: 현재 대표일 때만 해제.

- **성공 (200)** `data` = §11.6 항목(수정 반영본).

**DELETE — 소프트 삭제.** 대표 모티프였다면 같은 트랜잭션에서 `users.rep_motif_result_seq=NULL`.

- **성공 (200)** `data:null`.

**실패 분기(공통)**: 404 `MOTIF_RESULT_NOT_FOUND`(타인 소유 포함) / 403 `FORBIDDEN`.

### 11.9 GET /users/me/motif-passes

**성공 (200)** — `data.list[]`: `{ "artistSeq": 3, "artist": {요약}, "status": "ACTIVE", "startedDttm": "...", "expiresDttm": "...", "durationMonths": 1, "canceledDttm": null }`. 이용 판정은 `ACTIVE && expiresDttm > now` 이고, `status` 는 그 판정을 반영한 **실효 상태**다 — 기간이 지난 패스는 DB 가 `ACTIVE` 여도 `EXPIRED` 로 내려간다 *(S15P21D104-269)*.

### 11.10 패스 취소 — 기획 유지 / 현재 구현 범위 제외

패스 취소 기능과 `canceled_dttm` 개념은 **기획상 유지**한다. 다만 이번 프로젝트 구현 범위에서는 API를 구현하지 않는다. 기존 C4 정책(자동결제 재도입 시 갱신 차단 판정으로 활용 가능)은 후속 구현 시 재사용한다.

---

### 11.11 GET /shop/banners *(회의 확정 R7 — `shop_banners` 테이블 신설, DB v0.12)*

모티프 숍 상단 공용 배너. 아티스트별 `motif_bg_image_key` 와 별개이며 어드민이 관리한다.

**성공 응답 (200)** — `data` (비페이지 전량, `is_active=TRUE` 만, `displayOrder ASC`):

```json
{ "list": [ { "bannerSeq": 1, "imageUrl": "https://cdn.../banners/uuid.jpg", "displayOrder": 0 } ] }
```

- 공개 콘텐츠 — CloudFront 일반 URL. 클릭 액션(링크)은 미도입 — 필요 시 `linkType/linkTarget` 확장 *(기본안 — §16)*.

**실패 분기** — 공통 외 없음(배너 0건도 빈 배열 200).

---

## 12. 결제 (ORDER) — 토스 승인형, 웹훅 없음

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 12.1 | POST | `/orders` | 주문 생성(가격 스냅샷) | PAYMENT-03 |
| 12.2 | POST | `/orders/{orderUuid}/confirm` | 결제 승인(성공 콜백 후) | PAYMENT-05 |
| 12.3 | POST | `/orders/{orderUuid}/cancel` | 승인 전 주문 취소(위젯 이탈) | PAYMENT-05 |

### 12.1 POST /orders

**요청** `{ "productType": "ASSET", "assetSeq": 21 }` 또는 `{ "productType": "PASS", "artistSeq": 3, "passDurationMonths": 1 }`

- 서버가 KR 활성 가격 행을 조회해 `amount·currency·countryCode(·passDuration)` **스냅샷 저장** — 클라이언트는 금액을 결정하지 않는다.

**성공 응답 (201)** — `data` (토스 위젯 파라미터):

```json
{
  "orderUuid": "e3b0c442-98fc-...",
  "orderName": "카리나 모티프 에셋",
  "amount": "3900.00", "currency": "KRW",
  "expiresDttm": "2026-09-02T06:30:00.000Z"
}
```

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ASSET_NOT_FOUND` / `ARTIST_NOT_FOUND` | 대상 없음 |
| 409 | `PRODUCT_NOT_ON_SALE` | `is_active=FALSE` 또는 활성 가격 없음(국가 포함) |
| 409 | `ALREADY_OWNED` | 이미 ACTIVE 구매 보유(ASSET) |
| 409 | `COVERED_BY_PASS` | 유효 PASS 로 이용 가능한 에셋 **(기본안 — "패스 구독 중이면 구매된 상태와 같이 취급" 준수)** |
| 409 | `PASS_ALREADY_ACTIVE` | 기존 ACTIVE PASS 존재(PASS) — 만료·환불 후 재구매는 새 주문 허용 |

### 12.2 POST /orders/{orderUuid}/confirm

토스 successUrl 콜백 파라미터를 그대로 전달:

**요청** `{ "paymentKey": "toss-key", "orderId": "e3b0c442-...", "amount": "3900.00" }`

서버 처리 순서(전부 필수):

1. `orderId=orderUuid` 주문 조회(본인), **`amount` 를 `BigDecimal.compareTo` 로 대조** — 불일치 시 승인 호출 없이 400 `AMOUNT_MISMATCH` **(컷 불가)**.

2. 판매 가능 상태 재검증 — 중단이면 `PENDING→EXPIRED`, 409 `PRODUCT_NOT_ON_SALE`.

3. 토스 승인 API 호출 — 실패 시 `FAILED`(+fail_reason) 기록.

4. 조건부 UPDATE `status='PAID' WHERE status='PENDING' AND expires_dttm > now()` — 영향 0행이면 만료·중복 판정.

5. **같은 트랜잭션**에서 `asset_purchases(ACTIVE)` 생성 또는 `motif_passes` 생성/재활성(`started=now, expires=now+n개월, canceled_dttm=NULL`).

**성공 응답 (200)** — `data`:

```json
{ "orderUuid": "e3b0...", "status": "PAID", "productType": "PASS", "pass": { "expiresDttm": "2026-10-02T06:00:00.000Z" } }
```

(ASSET 이면 `"asset": { "assetSeq": 21 }`.)

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 400 | `AMOUNT_MISMATCH` | 콜백 amount ≠ 주문 스냅샷(승인 호출 전 차단) |
| 402 | `PAYMENT_FAILED` | 토스 승인 거절 — `data` 에 토스 실패 사유. 재시도는 **새 주문**(12.1) |
| 404 | `ORDER_NOT_FOUND` | 주문 없음·타인 주문 |
| 409 | `ORDER_EXPIRED` | 30분 만료(조건부 UPDATE 0행) |
| 409 | `ORDER_ALREADY_PAID` | 중복 승인 시도 |
| 409 | `PRODUCT_NOT_ON_SALE` | 승인 직전 판매 중단 감지 |

```json
{
  "result": "FAIL",
  "code": "PAYMENT_FAILED",
  "message": "결제 승인에 실패했습니다.",
  "data": "toss: REJECT_CARD_COMPANY",
  "timestamp": "2026-09-02T06:00:00.000Z"
}
```

### 12.3 POST /orders/{orderUuid}/cancel

`PENDING → CANCELED`(본인 주문만). 위젯 이탈 시 클라 호출. 미호출 방치 시 30분 후 배치가 `EXPIRED` — 기능 동일, 표기만 차이.

**성공 (200)** `data`: `{ "orderUuid": "...", "status": "CANCELED" }`

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | 주문 없음·타인 주문 |
| 409 | `ORDER_NOT_CANCELABLE` | PENDING 아님(PAID·EXPIRED 등) |

- 환불(`PAID→REFUNDED` + 권한 종료)은 어드민 API 범위 — 본 문서 제외.

---

## 13. 합성 파이프라인 (COMPOSITE) — 챌린지·모티프 공통

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 13.1 | POST | `/composite-jobs` | 잡 생성 + 원본 업로드 presigned 발급 | CAPTURE 공통 |
| 13.2 | POST | `/composite-jobs/{jobUuid}/request` | 업로드 완료 → 합성 요청(QUEUED) | CAPTURE-05/06 |
| 13.3 | GET | `/composite-jobs/{jobUuid}` | 잡 상태·결과 조회(알림 진입 1회) | CAPTURE-06/07, NOTI-06 |
| 13.4 | — | `/composite-jobs/{jobUuid}/retry` | 실패 잡 재시도 — 계약 미결(§16) | 실패 "다시 시도" |

### 13.1 POST /composite-jobs

**요청** `{ "jobType": "MOTIF", "assetSeq": 21 }` 또는 `{ "jobType": "CHALLENGE", "challengeSeq": 11, "participantMask": 1 }`

- CHALLENGE 의 `participantMask` **(기본안 — R3 편집본 파급)**: 사용자가 선택한 교체 슬롯 조합. `0` 또는 생략 = 무교체(원본 위 합성, `edited_challenge_seq=NULL`). `> 0` 이면 서버가 `(challengeSeq, participantMask)` 편집본을 조회해 잡에 `edited_challenge_seq` 로 기록 — 없으면 404 `EDITED_CHALLENGE_NOT_FOUND`.

- 검증: CHALLENGE — 챌린지 비삭제 (+ 위 편집본 매칭). MOTIF — **이용 권한(개별 구매 ACTIVE ∪ 유효 PASS) 필수(C3 선결제 — 회의 확정)**, `participantMask` 미사용.

- 처리: `UPLOADED` 잡 생성 + `raw/{uuid}.mp4` presigned PUT 발급. 클라는 **동기·로딩 UI**로 원본 S3 직행 업로드.

**성공 응답 (201)** — `data`:

```json
{ "jobUuid": "3fa8...", "uploadUrl": "https://s3...presigned-put", "expiresIn": 600 }
```

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `CHALLENGE_NOT_FOUND` / `ASSET_NOT_FOUND` | 대상 없음 |
| 404 | `EDITED_CHALLENGE_NOT_FOUND` | 선택 슬롯 조합의 편집본 없음(CHALLENGE, `participantMask > 0`) |
| 403 | `ASSET_ACCESS_DENIED` | MOTIF 이용 권한 없음 → 클라는 구매 화면 유도 |

### 13.2 POST /composite-jobs/{jobUuid}/request

**요청**

- CHALLENGE: `{ "correctionEnabled": true }` 또는 `{ "correctionEnabled": false }` — 촬영 후 사용자가 보정 여부 선택.

- MOTIF: `correctionEnabled` 필드를 받지 않는다. **MOTIF 보정은 사용하지 않으며 서버 내부 값은 `FALSE` 고정**.

- 처리: S3 원본 존재 확인(HEAD) → `UPLOADED→QUEUED` + `job.composite` 발행(priority: 모티프 5 / 챌린지 0). 이후 `PROCESSING→DONE|FAILED` 는 워커·이벤트가 처리하며 완료·실패 시 FCM을 발송한다.

**성공 응답 (202)** — `data`: `{ "jobUuid": "3fa8...", "status": "QUEUED" }`

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` | 잡 없음·타인 잡 |
| 409 | `RAW_NOT_UPLOADED` | S3 원본 부재(업로드 미완료·URL 만료) |
| 409 | `JOB_NOT_REQUESTABLE` | 상태가 UPLOADED 아님(중복 요청 포함) |

### 13.3 GET /composite-jobs/{jobUuid} *(회의 확정 R8 — jobType 별 응답 분기)*

응답은 공통부(`jobUuid`, `jobType`, `status`, `attempt`, `failCode`) + **jobType 분기 필드**로 구성한다.

**성공 응답 (200) — `jobType=CHALLENGE`** (결과 프리뷰 → 게시 화면 §8.8 로):

```json
{
  "jobUuid": "3fa8...",
  "jobType": "CHALLENGE",
  "status": "DONE",
  "attempt": 1,
  "failCode": null,
  "resultVideoUrl": "signed-url",
  "resultThumbnailUrl": "signed-url"
}
```

**성공 응답 (200) — `jobType=MOTIF`** (결과 행 자동 생성 완료 → `resultSeq` 로 보관함 상세 §11.6 이동):

```json
{
  "jobUuid": "9c1d...",
  "jobType": "MOTIF",
  "status": "DONE",
  "attempt": 1,
  "failCode": null,
  "resultSeq": 10
}
```

- CHALLENGE 분기는 signed 프리뷰 URL(게시 전이므로 보호 등급), MOTIF 분기는 `resultSeq` — 영상 재생·문구 입력은 §11.6/§11.8 이 담당하므로 본 응답에 URL 을 중복으로 싣지 않는다 *(회의 확정 R8)*.
- `QUEUED/PROCESSING` 이면 분기 필드 전부 `null`. `FAILED` 면 `failCode`(코드표 미결 §16) + 클라 "다시 시도"(§13.4) 노출.
- **MOTIF 잡은 합성 결과 업로드 → `motif_results(description=NULL, isRep=false)` 자동 생성 → 잡 `DONE` 확정 → FCM 발송 순서**로 처리한다. 결과 행 생성에 실패하면 DONE/FCM까지 진행하지 않는다 — 따라서 `status=DONE` 인 MOTIF 응답에서 `resultSeq` 는 항상 존재한다.
- FCM에는 `jobUuid`만 전달하고, 앱은 FCM 진입 시 본 API를 1회 조회한다. **폴링 금지.**

**실패**: 404 `JOB_NOT_FOUND`(타인 잡 포함).

### 13.4 POST /composite-jobs/{jobUuid}/retry *(회의 확정 R7 — 동일 잡 재큐 + attempt 모델)*

대상: 본인 `FAILED` 잡. **같은 잡 행을 재큐한다** — 새 job 을 만들지 않고 `jobUuid` 가 유지된다.

- 처리: `status FAILED→QUEUED`, `fail_code=NULL` 초기화(상태별 CHECK 유지), `attempt+1`, 잡 행에서 큐 메시지 재구성 후 `job.composite` 재발행. 결과 키는 `results/{type}/{jobUuid}.mp4` 멱등 규칙이라 재합성이 같은 키를 덮어쓴다.
- 직전 실패 사유(`fail_code`)는 행에서 소거된다 — 실패 이력이 필요하면 로그/이벤트로 남긴다(행 보존 아님) *(트레이드오프 — 회의 확정)*.
- 수동 재시도의 시도 상한은 두지 않는다 *(기본안 — 확인 필요)*. 스윕 자동 재큐의 `attempt < 3` 상한은 무한 루프 방지용으로 별개.

**성공 응답 (202)** — `data`:

```json
{ "jobUuid": "3fa8...", "status": "QUEUED", "attempt": 2 }
```

**실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` | 잡 없음·타인 잡 |
| 409 | `JOB_NOT_RETRYABLE` | `FAILED` 아님 |
| 410 | `RAW_VIDEO_EXPIRED` | 원본 수명주기 삭제 → 재촬영 유도 |

---

## 14. 알림 (NOTIFICATION) — FCM + Android 로컬 목록

서버는 `notifications` 테이블과 알림 목록/읽음 API를 두지 않는다. **FCM만 발송**하고, 수신한 알림의 목록·읽음·삭제·정렬은 Android 로컬 저장소에서 처리한다.

| # | Method | Path | 설명 | 근거 |
|---|---|---|---|---|
| 14.1 | POST | `/notifications/device-tokens` | FCM 토큰 등록/재할당 | device_tokens |
| 14.2 | DELETE | `/notifications/device-tokens` | 현재 기기 토큰 비활성(로그아웃) | 〃 |
| 14.3 | — | (서버 API 없음) | 인앱 알림 목록·읽음·삭제·설정은 Android 로컬 처리 | SETTING-02 |

### 14.1 POST /notifications/device-tokens

**요청** `{ "fcmToken": "...", "platform": "ANDROID" }` — 동일 토큰이 타 사용자 소유였다면 현재 사용자로 재할당 + `is_active=TRUE`(upsert). 로그인 직후·토큰 갱신 시 호출.

**성공 (200)** `data:null`. **14.2 성공 (200)** `data:null`(미등록 토큰도 200 — 멱등).

### 14.3 알림 발송·로컬 저장 규약

SETTING-02(전체/아티스트/게시물/서비스 ON·OFF)는 **Android DataStore 등 로컬에만 저장**한다. 서버 설정 테이블·설정 API 없음.

- 모든 FCM은 **data-only 메시지**로 발송한다. 클라이언트가 `onMessageReceived`에서 로컬 설정을 확인한 뒤 시스템 알림 표시 여부를 결정한다.

- FCM data 페이로드 기본 골격: `{ "type", "title", "body", "sentDttm", target 식별자(challengeSeq|videoSeq|noticeSeq|jobUuid) }`. MOTIF/CHALLENGE 합성 완료·실패 알림은 `jobUuid`를 전달한다.

- Android는 수신한 FCM을 **Room 등 로컬 DB에 저장해 앱 내 알림 목록을 구성**할 수 있다. `read/unread`, 로컬 삭제, 정렬, 알림 채널·트레이 그룹핑은 전부 클라이언트 책임이다.

- 서버에는 알림 이력을 저장하지 않으므로 API로 목록을 재동기화할 수 없다. 재설치·앱 데이터 삭제·기기 변경 시 로컬 알림 이력 및 설정이 초기화될 수 있고, FCM 자체를 수신하지 못한 알림은 복구할 수 없다.

- 서버는 사용자별 로컬 ON/OFF 설정을 알지 못하므로 발송 대상 이벤트는 서버 정책대로 전송하고, 최종 표시 여부는 클라이언트가 필터링한다.

---

## 15. 에러 코드 총괄

공통(§1.4) + 도메인:

| code | HTTP | 사용처 |
|---|---|---|
| `INVALID_GOOGLE_TOKEN` | 401 | 2.1 |
| `INVALID_REFRESH_TOKEN` | 401 | 2.2 |
| `ACCOUNT_SUSPENDED` / `ACCOUNT_DELETED` | 403 | 전 API |
| `ALREADY_ONBOARDED` | 409 | 3.3 |
| `IMAGE_NOT_UPLOADED` | 409 | 4.4 |
| `USER_NOT_FOUND` / `ARTIST_NOT_FOUND` / `CHALLENGE_NOT_FOUND` / `VIDEO_NOT_FOUND` / `NOTICE_NOT_FOUND` / `COMMENT_NOT_FOUND` / `ASSET_NOT_FOUND` / `MOTIF_RESULT_NOT_FOUND` / `ORDER_NOT_FOUND` / `JOB_NOT_FOUND` / `EDITED_CHALLENGE_NOT_FOUND` | 404 | 각 리소스 |
| `COMMENT_DISABLED` | 403 | 9.1/2/3/5 |
| `REPLY_DEPTH_EXCEEDED` / `PARENT_TARGET_MISMATCH` | 400 | 9.3 |
| `ALREADY_REPORTED` | 409 | 8.7, 9.6 |
| `JOB_NOT_DONE` / `ALREADY_PUBLISHED` / `CHALLENGE_DELETED` | 409 | 8.8 |



| `PRODUCT_NOT_ON_SALE` / `ALREADY_OWNED` / `COVERED_BY_PASS` / `PASS_ALREADY_ACTIVE` | 409 | 12.1/12.2 |
| `AMOUNT_MISMATCH` | 400 | 12.2 |
| `PAYMENT_FAILED` | 402 | 12.2 |
| `ORDER_EXPIRED` / `ORDER_ALREADY_PAID` / `ORDER_NOT_CANCELABLE` | 409 | 12.2/12.3 |
| `RAW_NOT_UPLOADED` | 409 | 13.2 |
| `JOB_NOT_REQUESTABLE` | 409 | 13.2 |
| `ASSET_ACCESS_DENIED` | 403 | 13.1 |


| `RATE_LIMITED` | 429 | SCG |

module-common 의 에러 enum 은 이 표와 1:1 로 구현한다(`code`, `httpStatus`, `defaultMessage`).

---

## 16. 확인 필요 / 미결 안건 (다음 회의)

| # | 항목 | 관련 | 상태 |
|---|---|---|---|
| 1 | ~~커서 역방향~~ — **종결(R7)**: `prevCursor`/`hasPrevious` 채택, 요청은 `?cursor=` 단일 | §1.9 | 확정 |
| 2 | ~~signed URL 만료~~ — **종결(R7)**: 10분 확정 | §1.7 | 확정 |
| 3 | 조회수 1회 인정 기준(중복·최소 시청 시간·본인 조회 포함 여부) | §7.4, 8.5 | **미결(계약 불변)** — 2026-09-21 이후 §6.1 랭킹이 조회수로 굴러가므로 무게가 올라갔다. 현재 방어는 영상당 count 상한뿐이다(ADR-0015) |
| 4 | `reports.reason_code` / `composite_jobs.fail_code` 코드표 | §8.7, 9.6, 13.3 | **추후 결정** |
| 5 | ~~재시도 모델~~ — **종결(R7)**: 동일 잡 재큐 + `attempt` 증가, `fail_code` 는 재큐 시 소거(이력은 로그). 수동 상한 없음 *(기본안)* | §13.4 | 확정 |
| 6 | 카드 카운트 `purchasedAssetCount > totalAssetCount` 경계 표시 — 판매 중단 구매 에셋 때문에 발생 가능 | §4.7 | 클라 협의 |
| 7 | ~~§4.7 필드 범위~~ — **종결(R7)**: 현행 전부 유지 | §4.7 | 확정 |
| 8 | ~~13.3 resultSeq 형태~~ — **종결(R8)**: jobType 별 응답 분기, MOTIF 분기에 `resultSeq` | §13.3 | 확정 |
| 9 | ~~숍 공용 배너 관리~~ — **종결(R7)**: `shop_banners` 테이블 신설(DB v0.12) + §11.11 API. 잔여 소안건: 배너 클릭 링크 도입 여부 | §11.11 | 확정 |
| 10 | ~~토스 세부 계약~~ — **종결(R7)**: 현행 §12 유지, 실연동 중 어긋나는 지점만 미세조정 | §12 | 확정 |
| 11 | ~~온보딩 이탈 복구~~ — **종결(R7)**: Android 가드로 확정(플래그 컬럼 미도입) | §2.1 | 확정 |
| 12 | 4.8 뷰어 연속재생 확장(`?view=FULL`) 필요 여부 | §4.8 | 클라 확인 |
| 13 | 4.9 답글 프리뷰 개수(기본 2)·내 댓글 다건 시 최신 1개 | §4.9 | 클라 확인 |
| 14 | 5.3 상단 모티프 자동재생 목록 개수(기본 5)·signed URL 갱신 UX | §5.3 | 클라 확인 |
| 15 | ~~에셋 표시명~~ — **종결(R6)**: `motif_assets.title` 컬럼 신설(DB v0.11), 11.1/11.2/6.3 응답 반영 | §6.3, 11.1/2 | 확정 |
| 16 | `participantMask` — **부분 종결(R7)**: CHECK `>= 0` 확정, **0 = 현재 발급하지 않는 예약값**. 편집본/슬롯 최종 형태는 #17·NULL 정책과 함께 잔여 | §7.2, 13.1 | 부분 확정 |
| 17 | ~~편집본 전조합 등록 전제~~ — **종결(R8)**: 어드민이 슬롯 전 조합 편집본을 필수 등록(+서버 완결성 검증). 어드민 명세 반영 대기 | §7.2, 13.1 | 확정 |
| 18 | ~~자동완성 응답 프로필 이미지 포함 여부 · 1~2자 prefix/3자+ trgm 전략~~ — **종결(v0.13)**: §5.9 를 §5.2 에 통합하며 소멸. 프로필 이미지는 포함, 2단 전략은 단일 `ILIKE` 로 폐기 | §5.2 | 확정 |

**이번 v0.8에서 종결된 기존 안건**

- 참여영상/챌린지 피드는 커서 유지. 5.5 공지도 커서로 전환.

- 닉네임 중복 허용. 최근 검색어는 서버 기능 없이 로컬 처리.

- 챌린지 `participantCount` 는 PUBLIC·PRIVATE 모두 포함.

- 댓글 정렬: 대상 아티스트 최신순 → 내 댓글 최신순 → 일반 댓글 최신순.

- MOTIF 보정 없음(`FALSE`), SECRET 특별 규칙 없음.

- `motif_results.description` 은 자동 생성 시 `NULL`, 이후 PATCH로 선택 입력.

- 숍 전체 집계 API(구 11.3) 제거, 4.7 + 11.1 `artistSeq` 필터 조합으로 렌더링.

### 16.1 문서 반영 대기 (확정분의 타 문서 전파)

| 대상 | 내용 |
|---|---|
| ERD/DB 명세 | `notifications` 서버 저장 미사용 확인, `artists.motif_bg_image_key` 공개 CDN 변환 규칙 확인, MOTIF DONE 시 `motif_results` 자동 생성 흐름/유일성 명시. `motif_passes.canceled_dttm` 은 기획 유지. |
| 기능 명세서 | 8.4 좋아요 목록 삭제, ARTIST 참여영상 게시 금지, 공지 상세 제거·5.5 목록 확장, 좋아요 204, 최근 검색어/알림 목록 로컬 처리, 11.3/11.7 삭제 및 패스 API 경로 변경, 패스 취소 현재 구현 제외 반영. |
| 공통 응답 규약 | 좋아요 POST/DELETE 4종(챌린지·참여영상·공지·댓글)의 `204 No Content` 예외 추가. |
| 어드민 명세서 | ① 챌린지 슬롯 등록 시 **전 조합 편집본 필수 등록 + 완결성 검증**(R8 확정) ② `shop_banners` 관리 화면(등록·정렬·비활성, R7) ③ 에셋 등록 폼 `title`/`description` 분리(R6). **④ v0.13: 별칭 CRUD — 반영 완료(어드민 v0.6 §3.4.5~8·§3.5.6~9). §5.2 가 읽는 별칭을 채우는 유일한 경로다.** |

---

## 부록 A. 리뷰 라운드 변경 기록 (누적)

### R1 → v0.4 (2026-09-02)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 온보딩 판정 `isNewUser` 일원화(`needsOnboarding` 삭제) + 클라 가드 | 2.1 |
| 2 | refresh rotation 동작·클라 처리 설명 추가 | 2.2 |
| 3 | 마이페이지 원콜 개편(repMotif + videos 프리뷰) | 4.1 |
| 4 | 구독 카드: 비페이지·groupName 제거·카운트 `purchasedAssetCount/totalAssetCount`(v0.7 회귀) | 4.7 |
| 5 | 내 영상 그리드 경량화 | 4.8 |
| 6 | 내 활동 통합(구 4.10~4.13 삭제, 행위 단위) | 4.9 |
| 7 | rankDelta 음수 정의·홈 재사용 명시 | 5.1 |
| 8 | 아티스트 챌린지 그리드 viewCount | 5.4 |
| 9 | 공지 content 전체·repImageUrl=0번째 | 5.5, 10.1 |
| 10 | 구독·좋아요 연타 규약(클라 디바운스+서버 멱등) | 5.6, 7.3 |
| 11 | 홈 활동 NOTICE 항목 확장(content·images·counts), 에셋 카운트 불가 명시 | 6.3 |
| 12 | 챌린지 피드 필드 정리(isSubscribed 이동, thumbnail·groupName 제거) | 7.1, 7.2 |
| 13 | signed URL "캐싱 안 함" 오해 해소 문구 | 1.7 |

### R2 → v0.5 (2026-09-02)

| # | 변경 | 위치 |
|---|---|---|
| 1 | **전 영상 그리드 `viewCount` 필수** (4.1 videos·4.8·5.4·6.1 — 11.5 는 카운터 부재로 예외 명시) | 공통 |
| 2 | **영상 목록 전부 커서 페이지네이션** (4.8/4.9/6.1/7.1/8.1/11.5), §1.9 정식 규약 승격, 비영상은 offset 유지 | 공통 |
| 3 | `videoCount` 철회 | 4.1 |
| 4 | `participationCount`·`subscribedArtists[].groupName` 제거 | 4.6 |
| 5 | 카드 카운트 **최종 확정**(개별 구매/전체) — DB v0.9 반영, 스키마 무변 | 4.7 |
| 6 | 그리드 size 3의 배수(기본 9)·visibility 전체 포함·videoSeq/thumbnail/viewCount | 4.8 |
| 7 | 내 활동 화면 기준 재설계: 영상 카드 + `liked` 하트 + `myComment` + **내 댓글의 답글 프리뷰 2개** | 4.9 |
| 8 | `subscribedDays`·`challengeCount` 추가, `myMotifVideos` 상단 자동재생 목록(연속재생 가이드), `hasMyMotifResults` 대체 | 5.3 |
| 9 | 비페이지 전량 반환 | 5.4, 5.5 |
| 10 | HOT 경량화(videoSeq·thumbnail·videoUrl·song·author·viewCount) + 상세는 뷰어 진입 시 §8.2 1회 | 6.1 |
| 11 | MOTIF_ASSET 항목 확정(artist+asset{title←description, thumbnail, duration}) — title 컬럼 부재 지적 | 6.3 |
| 12 | `thumbnailUrl` 부활(플레이스홀더) | 7.1 |
| 13 | 디바운싱 규약 확정 유지 | 5.6/5.7/7.3 |



### R3 → v0.6 (2026-09-03, DB v0.10 정합)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 챌린지·참여 영상 `title` 전면 삭제(컬럼 삭제 파급) — 게시 요청·수정·전 응답에서 제거, 화면 식별은 곡명·아티스트·썸네일 | 7.1/7.2/5.4/8.2/8.8/8.9 |
| 2 | **알림 목록 스코프 아웃** — 구 14.3~14.7 삭제, 레드닷 없음, 전 알림 푸시 전용(딥링크 직행), `NOTIFICATION_NOT_FOUND` 제거 | §14, §15 |
| 3 | 편집본·슬롯 반영 **(기본안)**: 7.2 `participants(bbox·mask)`+`availableMasks`, 13.1 `participantMask`(0/생략=원본), `EDITED_CHALLENGE_NOT_FOUND` | 7.2, 13.1, §15 |
| 4 | `participant_mask >= 0` 완화에 따른 0 의미 안건 등재 | §16 #25 |



### R4 → v0.7 (2026-09-03)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 구 4.5(rep-motif) 삭제 → §11.8 `PATCH /motif/results/{resultSeq}` 통합(문구 수정 + 대표 설정, 대표 단일성 트랜잭션) | 4.5, 11.8 |
| 2 | 내 영상 그리드 비페이지 전량 전환(커서 →), §1.9 대상에서 제외 | 4.8, 1.9 |
| 3 | 검색/자동완성 분리 — 5.2 확정 검색, 5.9 자동완성 신설(1~2자 prefix / 3자+ trgm, 디바운스 300ms + 최신 질의만 반영) | 5.2, 5.9 |
| 4 | 5.8 본문 소절 명시(§11.5 재사용 진입점) | 5.8 |
| 5 | HOT 캐러셀→뷰어 커서 시퀀스 공유 구현 노트 | 6.1 |
| 6 | `availableMasks` 삭제 — 편집본 전조합 등록 운영 전제(§16 #28) | 7.2 |

### R5 → v0.8 (2026-09-09)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 좋아요 POST/DELETE 성공을 204 No Content 예외로 통일, 응답 `isLiked/likeCount` 제거·클라 낙관적 UI/실패 롤백 | 1.2, 7.3, 8.3, 9.5, 10.2 |
| 2 | 참여영상 좋아요 사용자 목록 삭제, ARTIST 참여영상 게시 금지(`FORBIDDEN`) | 8.4, 8.8 |
| 3 | 루트 댓글 정렬을 대상 아티스트→내 댓글→일반 댓글, 각 그룹 최신순으로 확정 | 9.1 |
| 4 | 5.5를 커서 공지 목록으로 전환, 본문·이미지 전체·artist 요약·좋아요 상태 반환, 구 10.1 상세 삭제 | 5.5, 10.1 |
| 5 | 11.1 전체 에셋 카탈로그 + `artistSeq` 필터 + `myAccess`, 가격 목록 제외, 판매중단 구매자는 계속 조회 | 11.1/2 |
| 6 | SECRET을 NORMAL과 동일 규칙으로 처리, 개별 구매 우선 `OWNED > PASS > NONE` | 4.7, 11.1/2, 12.x, 13.1 |
| 7 | 구 11.3 숍 집계 API 삭제, 아티스트 패스 API를 `/artists/{artistSeq}/motif-pass`로 변경 | 11.3/4 |
| 8 | MOTIF 결과를 잡 완료 시 자동 생성(`description=NULL`, `isRep=false`), 구 11.7 저장 API 삭제 | 11.5~8, 13.3 |
| 9 | MOTIF 보정 비활성/파라미터 미노출, `IMAGE_NOT_UPLOADED`·`RAW_NOT_UPLOADED` 409로 변경 | 4.4, 13.2, 15 |
| 10 | FCM 수신 알림을 Android 로컬 DB에 저장해 인앱 알림 목록 구성, 알림 설정도 로컬 유지 | 14 |
| 11 | 챌린지 참여수에 PRIVATE 포함, 최근 검색어 서버 저장 제거 | 7.1, 4.9 |
| 12 | 패스 취소는 기획상 유지하되 현재 구현 범위 제외, 재시도·결제·배너 관리·prevCursor·resultSeq 응답 형태 등 미결 안건 재정리 | 11.10, 13.4, 16 |


### R6 → v0.9 (2026-09-09)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 6.3 공지 항목에 `isLiked` 추가 — 공지 상세 삭제(R5)로 6.3 이 공지 화면 진입 데이터가 되어 좋아요 초기 상태 필요 | 6.3 |
| 2 | `motif_assets.title` 컬럼 신설 확정(DB v0.11) — 11.1/11.2 응답 `title` 추가, 6.3 description 매핑 주석 해제, §16 #15 종결 | 11.1/2, 6.3 |
| 3 | SECRET = NORMAL 동일 취급을 **PASS 커버 포함(해석 ⓐ)** 으로 확인 — DB v0.11 전파(카드 집계 제외 해제 포함) | 11.1, DB |
| 4 | MOTIF 결과 자동 생성 주체 확정: `composite.done` 코어 컨슈머, `ON CONFLICT (job_seq) DO NOTHING` 멱등 — DB v0.11 규칙 명시 | 13.x, DB |
| 5 | 원본 파일 마크다운 이스케이프(내보내기 손상) 정규화 — 이 클린본이 이후 패치 원본 | 전체 |


### R7 → v0.10 (2026-09-09 · 팀 회의 결정)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 커서 응답 `prevCursor`/`hasPrevious` 추가 — 방향은 토큰 인코딩, 요청 파라미터 단일 | §1.9, 6.1 |
| 2 | 재시도 확정: 동일 잡 재큐 + `attempt+1`, `fail_code` 소거(이력은 로그), 202 응답, 수동 상한 없음 *(기본안)* | §13.4 |
| 3 | `shop_banners` 테이블·`GET /shop/banners` 신설(비페이지, 공개 CDN, 링크 없음) | §11.11, DB v0.12 |
| 4 | 종결 일괄: signed 10분 · 토스 현행 · §4.7 유지 · 온보딩 Android 가드 · `participantMask >= 0`(0=예약값) | §16 #2/7/10/11/16 |
| 5 | `users.recent_search_terms` 컬럼 제거(DB v0.12 — 죽은 설계 정리) | DB |


### R8 → v0.11 (2026-09-09)

| # | 변경 | 위치 |
|---|---|---|
| 1 | 13.3 응답 jobType 분기 확정 — CHALLENGE: 프리뷰 URL / MOTIF: `resultSeq`(URL 미포함, §11.6 재생 담당). 공통부에 `attempt` 추가 | §13.3 |
| 2 | 편집본 **전 조합 등록** 운영 전제 확정 — 어드민 필수 등록 + 서버 완결성 검증(어드민 명세 반영 대기), DB 미결 #10 종결 | §7.2, 13.1 |
| 3 | CHALLENGE 잡 `edited_challenge_seq NULL`(무교체 원본 합성) 허용 여부는 **팀 논의 예정**으로 유지 | DB #8 |

— 끝 (v0.11) —
