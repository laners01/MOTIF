# §4 사용자·마이페이지 (USER) — 상세 명세

공통 규약(봉투·에러 코드·시각 포맷·공통 실패 분기·비노출 규칙)은 [`README.md`](README.md)에 있다. 이 문서는 **도메인 분기만** 적는다.

원본: [`MOTIF_API_명세서_v0_11.md`](../MOTIF_API_명세서_v0_11.md) §4 · DB 명세서 v0.15.

| # | Method | Path | 인증 |
|---|---|---|---|
| 4.1 | GET | `/api/v1/users/me` | 필요 |
| 4.2 | PATCH | `/api/v1/users/me/nickname` | 필요 |
| 4.3 | POST | `/api/v1/users/me/profile-image/presign` | 필요 |
| 4.4 | PATCH | `/api/v1/users/me/profile-image` | 필요 |
| 4.5 | — | (삭제) 대표 모티프 설정 → §11.8 PATCH 로 통합 | — |
| 4.6 | GET | `/api/v1/users/{userSeq}/profile` | 필요 |
| 4.7 | GET | `/api/v1/users/me/subscriptions` | 필요 |
| 4.8 | GET | `/api/v1/users/me/videos` | 필요 |
| 4.9 | GET | `/api/v1/users/me/activities` | 필요 |

---

## 4.1 GET /api/v1/users/me — 마이페이지 원콜

마이페이지 첫 화면에 필요한 데이터를 **한 번의 호출**로 내려준다(회의 확정 개편). 프로필 + 대표 모티프 + 내 영상 3×3 프리뷰.

### 요청

```http
GET /api/v1/users/me
Authorization: Bearer {accessToken}
```

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userSeq": 1,
    "nickname": "모티프러버",
    "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
    "repMotif": {
      "resultSeq": 10,
      "videoUrl": "https://cdn.motif.io/results/motif/9c1d.mp4?Expires=...&Signature=...&Key-Pair-Id=...",
      "thumbnailUrl": "https://cdn.motif.io/results/motif/9c1d.jpg?Expires=...&Signature=..."
    },
    "videos": [
      { "videoSeq": 88, "thumbnailUrl": "https://cdn.motif.io/videos/88.jpg", "viewCount": 3400, "visibility": "PUBLIC" },
      { "videoSeq": 87, "thumbnailUrl": "https://cdn.motif.io/videos/87.jpg", "viewCount": 120, "visibility": "PRIVATE" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `profileImageUrl` | string \| null | 공개 CDN URL. 미설정이면 `null` |
| `repMotif` | object \| null | 대표 모티프. **미설정이면 `null`** |
| `repMotif.videoUrl`·`thumbnailUrl` | string | **signed URL**(만료 10분) — 모티프 결과물은 보호 등급(§1.7). 진입 시 자동재생·음소거 |
| `videos` | array \| **null** | 내 참여 영상 **최신 9개** 경량 프리뷰(3×3). **없으면 `null`** — 빈 배열이 아니다 |
| `videos[].visibility` | string | `PUBLIC` \| `PRIVATE`. 본인 화면이라 둘 다 포함한다 |
| `videos[].viewCount` | number | 전 영상 그리드 공통 규칙 |

- `videoCount` 는 불요로 철회됐다(R2). 전체 목록·더보기는 §4.8.
- 대표 모티프의 설정·해제·문구 수정은 이 API 가 아니라 **§11.8 `PATCH /motif/results/{resultSeq}`** 다(R4 통합).

### 서버 처리

1. 인증된 `userSeq` 로 `users` 조회 → 닉네임·`profile_image_key`
2. `users.rep_motif_result_seq` 가 있으면 `motif_results` 조회 → signed URL 2개 생성
3. `participation_videos` 에서 본인 비삭제 영상을 `reg_dttm DESC` 로 9건 조회
4. 썸네일을 `visibility` 에 따라 공개 URL / signed URL 로 변환

### 실패 응답

**도메인 분기 없음.** 공통 분기만 적용된다.

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 프리뷰 개수 | 9개 (3×3) | 원본 §4.1 기본안. 상수로 두고 조정 가능하게 |
| 영상 0건 | `"videos": null` | 원본이 "없으면 null" 이라고 명시. 빈 배열과 구분하는 이유는 불명이나 계약을 따른다 |
| `rep_motif_result_seq` 가 삭제된 결과물을 가리킬 때 | `repMotif: null` | FK 없는 **논리 참조**(순환 참조 방지)라 방어가 필요하다 |

---

## 4.2 PATCH /api/v1/users/me/nickname — 닉네임 수정

### 요청

```json
{ "nickname": "새닉네임" }
```

**형식 검증만 한다. 중복 확인은 하지 않는다**(C1 회의 확정 — `users.nickname` 에 UNIQUE 가 없다).

| 필드 | 규칙 |
|---|---|
| `nickname` | 영문·한글·**숫자**·`.`·`_`, 2~20자 — §3.3 과 **같은 규칙·같은 상수**를 쓴다 |

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "nickname": "새닉네임" },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 실패 응답

**400 `INVALID_PARAMETER` 하나뿐이다.**

```json
{
  "result": "FAIL",
  "code": "INVALID_PARAMETER",
  "message": "요청 값이 올바르지 않습니다.",
  "data": [
    { "field": "nickname", "message": "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다." }
  ],
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

> 닉네임 규칙 상수는 §3.3 구현의 `OnboardingRequest.NICKNAME_PATTERN` 을 공용으로 승격해 쓴다 — 두 곳에 각자 두면 규칙이 갈라진다.

---

## 4.3 POST /api/v1/users/me/profile-image/presign — 업로드 URL 발급

프로필 이미지를 **S3 에 직접 올리기 위한** presigned PUT URL 을 받는다. 이미지 바이트는 API 서버를 통과하지 않는다(설계 원칙 ①).

### 요청

본문 없음.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "uploadUrl": "https://motif-media.s3.ap-northeast-2.amazonaws.com/profiles/3f9c1a2b.jpg?X-Amz-Algorithm=...&X-Amz-Signature=...",
    "imageKey": "profiles/3f9c1a2b.jpg",
    "expiresIn": 300
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `uploadUrl` | S3 presigned **PUT** URL. 이 URL 로 이미지 바이너리를 직접 올린다 |
| `imageKey` | 서버가 발급한 키. 업로드 후 **§4.4 에 그대로 전달**한다 |
| `expiresIn` | 업로드 URL 유효 시간(초). 300 |

### 클라이언트 순서

1. 이 API 로 `uploadUrl`·`imageKey` 수령
2. `PUT {uploadUrl}` — 본문은 이미지 바이트, `Content-Type` 은 해당 미디어 타입(JSON 아님)
3. **§4.4** 로 `imageKey` 를 보내 반영

### 실패 응답

**도메인 분기 없음.**

### 참고 — MY-08 모티프 프레임 프로필

"모티프 영상의 한 프레임을 프로필로" 기능은 **클라이언트가 재생 중 프레임을 캡처해 같은 흐름(4.3 → PUT → 4.4)으로 업로드**한다. 서버가 영상에서 프레임을 뽑지 않는다 — 인제스트 워커 부하이고 범위를 넘는다.

---

## 4.4 PATCH /api/v1/users/me/profile-image — 업로드 반영

presigned PUT 업로드를 **마친 뒤** 호출한다.

### 요청

```json
{ "imageKey": "profiles/3f9c1a2b.jpg" }
```

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": { "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

### 서버 처리

1. `imageKey` 가 **본인에게 발급된 키**인지 확인(프리픽스·발급 기록)
2. **S3 에 해당 오브젝트가 실제로 있는지 확인**(HEAD) — 이 API 만 스토리지를 실제로 호출한다
3. `users.profile_image_key` 갱신 → 공개 CDN URL 반환

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 409 | `IMAGE_NOT_UPLOADED` | 해당 키의 S3 오브젝트 부재(업로드 미완료·URL 만료) |
| 403 | `FORBIDDEN` | 본인 발급 키가 아님 |

```json
{
  "result": "FAIL",
  "code": "IMAGE_NOT_UPLOADED",
  "message": "이미지 업로드가 완료되지 않았습니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

---

## 4.6 GET /api/v1/users/{userSeq}/profile — 타 사용자 정보

참여 영상 뷰어에서 작성자를 눌렀을 때 뜨는 Bottom Sheet 데이터다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "userSeq": 5,
    "nickname": "팬123",
    "profileImageUrl": "https://cdn.motif.io/profiles/aa11bb22.jpg",
    "subscribedArtists": [
      { "artistSeq": 3, "name": "카리나", "profileImageUrl": "https://cdn.motif.io/artists/3.jpg" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- `participationCount` 와 `subscribedArtists[].groupName` 은 **제거됐다**(회의 확정 R2).
- 구독이 0건이면 `subscribedArtists: []`.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 404 | `USER_NOT_FOUND` | 미존재 또는 `is_deleted=TRUE` — **삭제 사용자는 존재를 숨긴다**(§1.8) |

```json
{
  "result": "FAIL",
  "code": "USER_NOT_FOUND",
  "message": "존재하지 않는 사용자입니다.",
  "data": null,
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

> **스키마 주의**: 아티스트 이름은 `artists` 에 없다. `artists.user_seq → users.nickname` 조인으로 얻는다(`artists` 는 `role='ARTIST'` 사용자의 도메인 확장).

---

## 4.7 GET /api/v1/users/me/subscriptions — 구독 아티스트 카드

**비페이지 — 전량 반환**(회의 확정, 규모상 페이지 불요). 정렬은 **구독 최신순**. 카드 앞면·뒷면 데이터를 한 번에 내린다.

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
        "profileImageUrl": "https://cdn.motif.io/artists/3.jpg",
        "passActive": true,
        "passExpiresDttm": "2026-10-01T00:00:00.000Z",
        "subscribedDays": 41,
        "challengeCount": 5,
        "purchasedAssetCount": 2,
        "totalAssetCount": 12
      }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 산출 규칙 |
|---|---|
| `passActive` | `motif_passes.status='ACTIVE' AND expires_dttm > now` |
| `passExpiresDttm` | 위가 참일 때의 만료 시각. 아니면 `null` |
| `subscribedDays` | `now − subscriptions.reg_dttm` (일) |
| `challengeCount` | 그 아티스트에 올린 **내 비삭제 참여 영상 수** (`idx_pv_user_artist`) |
| `purchasedAssetCount` | 그 아티스트 에셋 중 **개별 구매(`asset_purchases.status='ACTIVE'`) 수만**. **패스로 열린 것은 제외** — 패스만 있으면 `0/m` |
| `totalAssetCount` | 그 아티스트의 `is_active=TRUE` 에셋 수. **SECRET 도 동일 집계** |

- 카드의 `n/m` 표기가 `purchasedAssetCount`/`totalAssetCount` 다(2026-09-02 재확정 — v0.7 정의로 회귀).
- `groupName` 은 제거됐다(회의 확정).
- 이 API 는 마이페이지 카드뿐 아니라 **숍 화면의 아티스트 섹션**을 구성하는 데 재사용한다. 아티스트별 에셋은 §11.1 의 `artistSeq` 필터로 따로 조회한다(구 §11.3 집계 API 는 삭제됨).

### 실패 응답

**도메인 분기 없음.** 구독 0건이면 `"list": []` 로 200.

### 미결

- `purchasedAssetCount > totalAssetCount` 가 될 수 있다 — **판매 중단된 에셋을 구매한 경우**(구매는 남고 `is_active=FALSE` 라 분모에서 빠진다). 표시 방법은 클라 협의 대상(원본 §16 #6).

---

## 4.8 GET /api/v1/users/me/videos — 내 챌린지 그리드

**비페이지 — 전량 반환**(회의 확정 R4, 커서에서 전환). 최신순. 본인 영상이므로 **PUBLIC·PRIVATE 전부 포함**하고 항목마다 `visibility` 로 구분한다.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      { "videoSeq": 88, "thumbnailUrl": "https://cdn.motif.io/videos/88.jpg", "viewCount": 3400, "visibility": "PUBLIC" },
      { "videoSeq": 87, "thumbnailUrl": "signed-url", "viewCount": 120, "visibility": "PRIVATE" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- 삭제 영상은 제외한다.
- 그리드에서 항목을 고르면 **§8.2 단건 조회**로 뷰어에 진입한다. 연속 스와이프 재생이 필요해지면 `?view=FULL` 확장을 예약해 뒀다(원본 §16 #12).
- §4.1 의 `videos`(최신 9개)와의 관계: 첫 화면은 §4.1 로 그리고, "더보기/전체" 진입 시 이 API 를 1회 호출한다.
- 전량 반환의 전제는 "본인 영상 수가 크지 않다"이다. 수백 건이 쌓이면 재검토 대상이다.

### 실패 응답

**도메인 분기 없음.**

---

## 4.9 GET /api/v1/users/me/activities — 내 활동

**커서 페이지네이션**(§1.9). **영상 단위 카드**이며 정렬은 **마지막 상호작용 최신순**이다.

내가 좋아요를 눌렀거나 댓글을 단 영상 1개가 카드 1장이고, 카드에 하트 표시·내 댓글·**내 댓글에 달린 답글**이 붙는다(화면 시안 기준). **본인 게시물은 제외**하고, 탭 분리는 없다.

### 요청

```http
GET /api/v1/users/me/activities?cursor={opaque}&size=20
Authorization: Bearer {accessToken}
```

최초 호출은 `cursor` 를 생략한다. `size` 기본 20 · 최대 100.

### 성공 응답 (200)

```json
{
  "result": "SUCCESS",
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {
    "list": [
      {
        "video": {
          "videoSeq": 88,
          "thumbnailUrl": "https://cdn.motif.io/videos/88.jpg",
          "viewCount": 3400,
          "likeCount": 12000
        },
        "liked": true,
        "myComment": {
          "commentSeq": 501,
          "content": "이 챌린지 예지랑 너무 잘 어울린다 🔥",
          "likeCount": 742,
          "replyCount": 8,
          "regDttm": "2026-08-28T12:00:00.000Z",
          "parentComment": null
        },
        "replies": [
          {
            "commentSeq": 530,
            "author": { "userSeq": 9, "nickname": "dancing_mandu", "profileImageUrl": "https://cdn.motif.io/profiles/9.jpg" },
            "content": "😂😂😂",
            "regDttm": "2026-08-28T13:00:00.000Z"
          },
          {
            "commentSeq": 531,
            "author": { "userSeq": 1, "nickname": "kkongdance", "profileImageUrl": null },
            "content": "이거 보고 바로 챌린지 찍으러 갑니다",
            "regDttm": "2026-08-28T14:00:00.000Z"
          }
        ],
        "lastInteractedDttm": "2026-08-28T12:00:00.000Z"
      }
    ],
    "prevCursor": null,
    "nextCursor": "eyJ0IjoiMjAyNi0wOC0yOFQxMjowMDowMFoiLCJ2Ijo4OH0",
    "hasPrevious": false,
    "hasNext": true
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 설명 |
|---|---|
| `liked` | 내가 좋아요를 눌렀는지 = 카드의 하트 표시 |
| `video.likeCount` | 화면의 "♥ 1.2만" |
| `myComment` | 내가 단 댓글. **좋아요만 한 영상이면 `null`** |
| `myComment.parentComment` | 내 댓글이 답글이면 부모 요약, 루트면 `null` |
| `replies` | **내 댓글에 달린 답글 최신 2개** 프리뷰. 전체는 `myComment.replyCount` 와 함께 게시물 댓글 화면으로 이동 |
| `lastInteractedDttm` | 정렬 기준. 좋아요·댓글 중 **마지막 상호작용 시각** |

- 좋아요와 댓글이 **둘 다 있으면 카드 1장으로 병합**한다.
- 같은 영상에 내 댓글이 여러 개면 **최신 1개**만 싣는다.
- 내 댓글이 답글이면(depth 2 규칙) `replies` 는 **항상 빈 배열**이다 — 답글에는 답글을 달 수 없다.
- 대상이 삭제·비노출되었거나 **댓글 사용 중지**된 영상의 카드는 목록에서 제외한다(ACTIVITY-02).
- **최근 검색어는 이 API 와 무관하다** — 서버 API·DB 없이 Android 로컬에서만 처리한다.

### 실패 응답

**도메인 분기 없음.**

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 답글 프리뷰 개수 | 2개 | 원본 §4.9 기본안 (§16 #13 클라 확인 대기) |
| 내 댓글 다건일 때 | 최신 1개 | 〃 |

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **아티스트 이름 출처** — `artists` 테이블에 `name` 컬럼이 없다. 이름은 `users.nickname` 이며 `artists.user_seq` 로 조인해야 한다. §4.6·§4.7 을 포함해 아티스트 이름을 내리는 모든 API 가 해당한다.
2. **§4.1 `videos: null`** — 빈 배열이 아니라 `null` 이라는 규칙은 클라이언트가 분기를 하나 더 갖게 만든다. 빈 배열로 통일할지 확인이 필요하다(현재는 원본대로 `null`).
