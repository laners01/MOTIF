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
      "videoUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/master.m3u8?Policy=eyJTdGF0ZW1lbnQiOlt7...&Signature=...&Key-Pair-Id=K27QE85H6DLYDD",
      "thumbnailUrl": "https://cdn.motif.io/private/motif/9c1d2e3f-4a5b-6c7d-8e9f-0a1b2c3d4e5f/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD"
    },
    "videos": [
      { "videoSeq": 88, "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg", "viewCount": 3400, "visibility": "PUBLIC" },
      { "videoSeq": 87, "thumbnailUrl": "https://cdn.motif.io/private/challenge/7c9e1d20-8b3a-4f51-9c6d-0e2f4a6b8c10/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD", "viewCount": 120, "visibility": "PRIVATE" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `profileImageUrl` | string \| null | **무서명 공개 CDN URL.** 미설정이면 `null` |
| `repMotif` | object \| null | 대표 모티프. **미설정이면 `null`** |
| `repMotif.videoUrl` | string | **HLS 마스터 재생목록** 주소. 모티프 결과물은 보호 등급이라 **CDN 절대 주소 + 프리픽스 서명 파라미터**다(ADR-0012). 클라이언트가 조각·자식 재생목록 요청에 같은 파라미터를 붙인다. 진입 시 자동재생·음소거 |
| `repMotif.thumbnailUrl` | string | 같은 프리픽스의 `thumb.jpg`. 단일 파일이라 **URL 하나짜리 signed URL**(만료 6시간)을 바로 낸다 |
| `videos` | array | 내 참여 영상 **최신 9개** 경량 프리뷰(3×3). **없으면 빈 배열 `[]`** |
| `videos[].visibility` | string | `PUBLIC` \| `PRIVATE`. 본인 화면이라 둘 다 포함한다 |
| `videos[].viewCount` | number | 전 영상 그리드 공통 규칙 |

- `videoCount` 는 불요로 철회됐다(R2). 전체 목록·더보기는 §4.8.
- 대표 모티프의 설정·해제·문구 수정은 이 API 가 아니라 **§11.8 `PATCH /motif/results/{resultSeq}`** 다(R4 통합).

### 서버 처리

1. 인증된 `userSeq` 로 `users` 조회 → 닉네임·`profile_image_key`
2. `users.rep_motif_result_seq` 가 있으면 `motif_results` 조회 → 마스터 재생목록은 core-api 경로로(`composite_jobs.job_uuid` 로 조립), 썸네일은 signed URL 로 생성
3. `participation_videos` 에서 본인 비삭제 영상을 최신순 9건 조회. **삭제된 챌린지에 속한 영상은 뺀다**(§1.8)
4. 이미지 URL 을 **키의 최상위 프리픽스**로 변환 — `private/` 면 signed URL, 아니면 무서명 CDN URL

> **4번은 `visibility` 로 판정하지 않는다.** 둘은 다른 층이다 — `visibility` 는 앱 안에서
> 누가 볼 자격이 있는가이고, 등급은 그 자격을 CDN 에 어떻게 전달하는가다(§1.7 ·
> [ADR-0013](../../adr/0013-signed-only-media.md)). 참여 영상은 `visibility` 와 무관하게
> 전부 `private/` 에 있으므로, 그 값으로 가르면 `PUBLIC` 영상에 무서명 URL 이 나가 403 이
> 된다. **키 하나만 보면 언제나 맞는다.**
>
> 정렬은 `reg_dttm DESC` 대신 **PK 역순**이다 — IDENTITY 라 결과가 같으면서, 같은 초에
> 올라온 영상들이 호출마다 순서를 바꾸는 일이 없다.

### 실패 응답

**도메인 분기 없음.** 공통 분기만 적용된다.

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 프리뷰 개수 | 9개 (3×3) | 원본 §4.1 기본안. 상수로 두고 조정 가능하게 |
| 영상 0건 | `"videos": []` | 원본은 `null` 로 적었지만 **빈 배열로 바꿨다**(2026-09-13 확정). null 과 빈 배열을 구분하면 클라이언트가 같은 "없음"에 분기를 둘로 갖는다 |
| `rep_motif_result_seq` 가 삭제된 결과물을 가리킬 때 | `repMotif: null` | FK 없는 **논리 참조**(순환 참조 방지)라 방어가 필요하다. 남의 결과물 seq 가 들어가 있어도 막히지 않으므로 조회에 `user_seq` 를 함께 건다 |
| 이미지 전달 | **전부 CloudFront 경유.** S3 presigned GET 은 쓰지 않는다 | §1.7 · ADR-0010 D5 — presigned 는 서명 쿼리스트링이 오리진(S3)까지 전달돼야 해서 **CDN 캐시 적중률이 0% 가 된다.** CloudFront 서명은 엣지에서 소비되므로 캐시 키에 들어가지 않는다. presigned 가 맞는 자리는 §4.3 업로드(PUT)다 |
| 보호 이미지 서명 만료 | **6시간** (재생목록·조각은 10분) | 조각 URL 은 만료돼도 앱이 재생목록을 다시 받아 복구되지만 **`<img>` 에는 그 경로가 없다** — 403 이면 깨진 채로 남는다. 10분이면 마이페이지를 열어둔 채 잠깐 다른 앱을 보고 돌아왔을 때 3×3 이 통째로 깨진다. 길게 줘도 노출 범위는 서명된 그 한 장뿐이다(URL 단위 서명) |

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

**오류는 언제나 1건이다.** `null` 이면 필수 오류, 그 밖(빈 문자열·공백·길이·허용 외 문자)은
형식 오류로 갈린다. `@NotBlank` 를 쓰면 빈 문자열이 필수·형식 양쪽에 걸려 2건이 나가므로
**`@NotNull` 을 쓴다** — 위 예시가 1건인 것이 그 이유다. 같은 규칙이 §3.3 온보딩에도
적용되므로, 온보딩에 빈 문자열을 보냈을 때의 오류도 2건에서 1건으로 바뀐다.

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 규칙을 두는 곳 | **`global/validation/Nickname` 합성 애너테이션 하나** | 상세 명세가 "상수를 공용으로 승격"이라 적었지만, 패턴만 옮기면 `@NotNull` 짝이 갈라진다. 한쪽이 빠뜨리면 `{"nickname": null}` 이 검증을 통과해 엔티티까지 내려가고 DB CHECK 위반으로 **400 이어야 할 것이 500 이 된다.** 제약 전체를 묶어야 그 틈이 없다 |
| 앞뒤 공백 | **다듬지 않고 그대로 검증** | 패턴에 공백이 없어 `" 닉"` 은 400 이다. 서버가 조용히 고쳐 주면 사용자가 보낸 값과 저장된 값이 달라진다 |
| `mod_usr_seq` | **§4.2 에서만 본인 seq 를 기록한다. 온보딩(§3.3)은 NULL 로 둔다** | 규칙은 "이 행을 마지막으로 수정한 주체. **최초 설정은 수정이 아니다**". 온보딩도 §2.1 이 만든 기존 행을 UPDATE 하므로 `mod_dttm` 은 어차피 찍히고, 그래서 시각만으로는 "온보딩에서 정해진 이름"과 "사용자가 바꾼 이름"이 구분되지 않는다 — 이 칸의 NULL 여부가 그 둘을 가른다. 어드민 경로(§3.5.4)는 기존대로 기록한다 |
| 같은 값으로 변경 | **그대로 200** | 변경 없음을 409 로 돌려줄 실익이 없다 |
| 응답 필드 | `nickname` 하나 | 마이페이지 전체를 다시 그려야 하면 §4.1 을 부른다. 수정 응답에 화면 전체를 실으면 같은 것을 두 군데서 조립하게 된다 |

---

## 4.3 POST /api/v1/users/me/profile-image/presign — 업로드 URL 발급

프로필 이미지를 **S3 에 직접 올리기 위한** presigned PUT URL 을 받는다. 이미지 바이트는 API 서버를 통과하지 않는다(설계 원칙 ①).

### 요청

```json
{ "contentType": "image/jpeg", "contentLength": 204800 }
```

**원본 명세는 "본문 없음"이다.** 조건을 받도록 바꿨다 — 근거는 아래 [기본안](#기본안-1).

| 필드 | 규칙 |
|---|---|
| `contentType` | `image/jpeg` · `image/png` · `image/webp` |
| `contentLength` | 1 이상, 최대 10MB |

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
2. `PUT {uploadUrl}` — 본문은 이미지 바이트, `Content-Type`·`Content-Length` 는 **1번에 보낸 값과 같아야 한다**
3. **§4.4** 로 `imageKey` 를 보내 반영

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 400 | `INVALID_PARAMETER` | 허용 외 `contentType`(`data.allowedContentTypes`) · 상한 초과 `contentLength`(`data.maxBytes`) |

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 요청 본문 | **`contentType`·`contentLength` 를 받는다** (원본은 본문 없음) | 받은 값이 **서명 대상 헤더**가 되어 다른 값으로 PUT 하면 S3 가 거부한다. 없으면 발급된 URL 로 **아무 타입이든 아무 크기든 `profiles/` 에 올릴 수 있고**, 바이트가 서버를 지나가지 않으므로 사후 검증도 불가능하다. 어드민 §1.4 가 같은 근거로 이미 그렇게 한다 |
| 한도 | `image/jpeg`·`png`·`webp` · 10MB · TTL 300초 | 어드민 이미지 한도와 **같은 설정**을 본다(`motif.media.upload.image`). 어드민 아티스트 프로필과 사용자 프로필이 같은 `profiles/` 프리픽스에 쓰므로 갈리면 한 자리에 규칙이 둘이 된다 |
| 확장자 | `.jpg` 고정 | `contentType` 이 png/webp 여도 키는 `.jpg` 다. 실제 타입은 PUT 시 서명된 `Content-Type` 으로 S3 에 기록되므로 서빙에 문제가 없다(어드민 `ARTIST_PROFILE` 과 같은 규약) |
| 발급 기록 | **Redis, TTL = presign 만료** | §4.4 의 403 을 판정할 근거다. 상세는 §4.4 기본안과 [ADR-0011](../../adr/0011-redis-short-lived-state.md) |

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

1. `imageKey` 가 **본인에게 발급된 키**인지 확인 — Redis 발급 기록 대조
2. **S3 에 해당 오브젝트가 실제로 있는지 확인**(HEAD) — 이 API 만 스토리지를 실제로 호출한다
3. `users.profile_image_key` 갱신 + `mod_usr_seq` 기록 → 공개 CDN URL 반환
4. 발급 기록 삭제

> **1번이 2번보다 먼저인 것은 의도다.** 뒤집으면 남의 키를 보냈을 때 **오브젝트의 존재
> 여부가 응답 시간에 드러난다** — 있으면 HEAD 왕복만큼 느리고 없으면 빠르다. 어차피
> 거부할 요청에 스토리지를 두드리는 것도 아깝다.

### 실패 응답

| HTTP | code | 조건 |
|---|---|---|
| 403 | `FORBIDDEN` | 본인 발급 키가 아님 — **발급 기록 만료(300초 경과)도 여기다** |
| 409 | `IMAGE_NOT_UPLOADED` | 해당 키의 S3 오브젝트 부재(업로드 미완료·PUT 실패) |

403 과 409 를 합치지 않는 이유는 **사용자에게 요구하는 행동이 다르기 때문**이다 —
409 는 "PUT 부터 다시", 403 은 "§4.3 부터 다시"다. 여기서 키의 존재는 비밀이 아니다:
프로필 이미지는 공개 등급이고 §4.6 이 남의 것도 URL 로 내보낸다.

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
      { "artistSeq": 3, "name": "카리나", "groupName": "aespa", "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- `participationCount` 는 제거됐다(회의 확정 R2). `subscribedArtists[]` 는 **공통 아티스트 요약**(README §3.5)을 그대로 쓴다 — 원본은 여기서 `groupName` 을 뺐지만 타입 통일을 위해 되살렸다.
- 구독이 0건이면 `subscribedArtists: []`.

### 서버 처리

1. `userSeq` 로 `users` 조회 — **`is_deleted=TRUE` 면 없는 것으로 답한다**(404)
2. `subscriptions` → `artists` → `users`(이름·프로필) → `artist_groups`(그룹명, left join) 조인
3. 프로필 이미지 키를 **무서명 공개 CDN URL** 로 변환

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

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| **삭제된 아티스트** | `subscribedArtists` 에서 **뺀다** | 명세에 없다. §1.8 이 삭제 아티스트를 "없는 것으로 답한다"고 정했으므로(§5.3 은 404), 목록에 남기면 **눌러도 404 가 나는 카드**가 된다. 구독 행 자체는 지우지 않고 조회에서 거른다 |
| 정렬 | **구독 최신순**(`subscriptions.reg_dttm DESC`) | 명세 미지정. §4.7 이 "구독 최신순"으로 못 박았으니 **같은 목록을 두 API 가 다른 순서로 주지 않게** 맞춘다 |
| 건수 상한 | 없음(전량) | 구독 수는 사람당 수십 건 규모다. §4.7 도 전량이다 |
| 본인 `userSeq` 로 호출 | 그대로 응답 | 막을 이유가 없다. 굳이 막으면 클라이언트가 "내 seq 인지" 분기를 하나 더 갖는다. 마이페이지는 §4.1 이다 |

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
        "groupName": "aespa",
        "profileImageUrl": "https://cdn.motif.io/profiles/3f9c1a2b.jpg",
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
| `challengeCount` | 그 아티스트에 올린 **내 비삭제 참여 영상 수**. **§4.8 그리드와 같은 필터** — 삭제된 챌린지에 속한 영상도 뺀다(아래 기본안) |
| `purchasedAssetCount` | 그 아티스트 에셋 중 **개별 구매(`asset_purchases.status='ACTIVE'`) 수만**. **패스로 열린 것은 제외** — 패스만 있으면 `0/m` |
| `totalAssetCount` | 그 아티스트의 `is_active=TRUE` 에셋 수. **SECRET 도 동일 집계** |

- 카드의 `n/m` 표기가 `purchasedAssetCount`/`totalAssetCount` 다(2026-09-02 재확정 — v0.7 정의로 회귀).
- 카드 항목은 **공통 아티스트 요약**(README §3.5)에 아래 집계 필드를 더한 형태다. 원본은 `groupName` 을 뺐지만 타입 통일을 위해 되살렸다.
- 이 API 는 마이페이지 카드뿐 아니라 **숍 화면의 아티스트 섹션**을 구성하는 데 재사용한다. 아티스트별 에셋은 §11.1 의 `artistSeq` 필터로 따로 조회한다(구 §11.3 집계 API 는 삭제됨).

### 실패 응답

**도메인 분기 없음.** 구독 0건이면 `"list": []` 로 200.

### 서버 처리

1. §4.6 과 **같은 구독 조회**로 아티스트 목록을 얻는다(구독 최신순, 삭제 아티스트 제외)
2. 아티스트 seq 를 모아 집계 4종을 **각 한 번씩** 돌린다
3. 조립해 내린다

**집계를 카드마다 부르지 않는다.** 구독이 n 건이면 카드별 조회는 그대로 4n 쿼리가 된다.
구독 수와 무관하게 **쿼리는 5개로 고정**이다(구독 1 + 집계 4). 구독이 0건이면 집계를
아예 돌리지 않는다.

`group by` 결과에는 건수 0 인 아티스트가 아예 없다 — **없으면 0 으로 읽는다.**

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| **`challengeCount` 에 §1.8 적용** | **삭제된 챌린지에 속한 영상은 뺀다** | 명세는 "내 비삭제 참여 영상 수"라고만 적었다. 안 빼면 **§4.8 그리드에는 안 보이는 영상이 이 숫자에는 잡힌다** — "5개라는데 4개만 보인다"가 되고 사용자는 어느 쪽이 맞는지 알 수 없다. 대신 명세가 언급한 `idx_pv_user_artist` 만으로는 안 되고 챌린지 확인이 붙는다 |
| `motif_passes`·`asset_purchases` | **엔티티 없이 네이티브로 센다** | 필요한 건 숫자·시각뿐이고 그 테이블들의 상태 규칙(환불·취소)은 구매 도메인(§12)이 소유한다. `MotifPassPriceRepository.countActivePasses`·`findActivePassExpiry` 가 같은 판단을 했다 — 그쪽 티켓이 엔티티를 만들면 함께 옮긴다 |
| 패스 유효 판정 위치 | **DB 쪽**(`expires_dttm > now()`) | 행을 꺼내 자바에서 비교하면 앱 서버 시계가 DB 와 어긋난 만큼 만료 직전·직후 판정이 갈린다 |
| `totalAssetCount` 분모 | `is_active=TRUE` 만 | 내려간 에셋을 분모에 넣으면 "12개 중 2개"가 영원히 채워지지 않는다. 어드민 지표용 `countByArtists`(비활성 포함)와 **일부러 다른 쿼리**다 |

### 미결

- `purchasedAssetCount > totalAssetCount` 가 될 수 있다 — **판매 중단된 에셋을 구매한 경우**(구매는 남고 `is_active=FALSE` 라 분모에서 빠진다). **산 것은 판매가 중단돼도 산 것이라 서버는 보정하지 않는다.** 표시 방법은 클라 협의 대상(원본 §16 #6).

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
      { "videoSeq": 88, "thumbnailUrl": "https://cdn.motif.io/private/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg", "viewCount": 3400, "visibility": "PUBLIC" },
      { "videoSeq": 87, "thumbnailUrl": "https://cdn.motif.io/private/challenge/7c9e1d20-8b3a-4f51-9c6d-0e2f4a6b8c10/thumb.jpg?Expires=1789000000&Signature=...&Key-Pair-Id=K27QE85H6DLYDD", "viewCount": 120, "visibility": "PRIVATE" }
    ]
  },
  "timestamp": "2026-09-10T00:00:00.000Z"
}
```

- 삭제 영상은 제외한다. **삭제된 챌린지에 속한 영상도 뺀다**(§1.8) — 영상 자체가 살아 있어도 그렇다.
- 그리드에서 항목을 고르면 **§8.2 단건 조회**로 뷰어에 진입한다. 연속 스와이프 재생이 필요해지면 `?view=FULL` 확장을 예약해 뒀다(원본 §16 #12).
- §4.1 의 `videos`(최신 9개)와의 관계: 첫 화면은 §4.1 로 그리고, "더보기/전체" 진입 시 이 API 를 1회 호출한다.
- 0건이면 `"list": []`.

**항목은 §4.1 `videos[]` 와 완전히 같은 형태다** — 같은 그리드를 그리는 카드라 서버도 같은 쿼리·같은 변환을 쓴다. 나누면 §1.8 비노출이나 썸네일 등급 판정이 한쪽에서만 고쳐질 수 있고, 그때 증상은 "첫 화면과 더보기가 다른 목록을 보여 준다"인데 어느 쪽이 맞는지 화면만 봐서는 알 수 없다.

### 실패 응답

**도메인 분기 없음.**

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 건수 상한 | **두지 않는다(명세대로 전량)** | 조용히 자르면 사용자에게는 **영상이 사라진 것**으로 보인다. 자를 거면 커서를 다시 넣는 게 맞고, 그건 R4 에서 뒤집은 결정이다 |
| 대신 **임계 초과 시 `warn` 로그** | 200건 초과 | 명세가 *"수백 건이 쌓이면 재검토 대상"* 이라고 적어 뒀는데 **재검토할 시점을 알 방법이 없다.** 자르지 않고 시끄럽게만 만든다 — 느려진 뒤에 아는 것보다 낫다(액세스 로그의 300ms 기준과 같은 발상) |
| 정렬 | **PK 역순** | §4.1 과 같은 근거 — IDENTITY 라 최신순과 결과가 같으면서 같은 초에 올라온 영상의 순서가 호출마다 흔들리지 않는다 |

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
          "thumbnailUrl": "https://cdn.motif.io/results/challenge/3fa85f64-5717-4562-b3fc-2c963f66afa6/thumb.jpg",
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
            "author": { "userSeq": 9, "nickname": "dancing_mandu", "profileImageUrl": "https://cdn.motif.io/profiles/1f6b8c0e.jpg" },
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
    "nextCursor": "MTc4NzkxODQwMDAwMDo4OA",
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
| `myComment.replyCount` | **노출되는** 답글 수(삭제된 답글·삭제 사용자의 답글 제외 — §9.1 과 같은 집합) |
| `myComment.parentComment` | 내 댓글이 답글이면 부모 요약, 루트면 `null` |
| `replies` | **내 댓글에 달린 답글 최신 2개** 프리뷰. 전체는 `myComment.replyCount` 와 함께 게시물 댓글 화면으로 이동 |
| `lastInteractedDttm` | 정렬 기준. 좋아요·댓글 중 **마지막 상호작용 시각**. 영상의 등록 시각이 아니다 |

**`parentComment` 의 모양** — 원본·이 문서 모두 `null` 예시만 있어 여기서 채운다 **(기본안)**.

```json
{
  "commentSeq": 610,
  "author": { "userSeq": 7, "nickname": "motif_fan", "profileImageUrl": "https://cdn.motif.io/profiles/9a8b7c6d.jpg" },
  "content": "이 구간 안무가 진짜 어렵던데"
}
```

`author` 는 `replies[].author` 와 **같은 요약**이다(`userSeq`·`nickname`·`profileImageUrl`). §9 의 작성자 요약과 달리 `isArtist` 가 없다 — 아티스트 뱃지는 댓글 목록의 정렬 1순위와 짝인 표시이고, 활동 카드에는 그 정렬도 뱃지도 없다.

- 좋아요와 댓글이 **둘 다 있으면 카드 1장으로 병합**한다.
- 같은 영상에 내 댓글이 여러 개면 **최신 1개**만 싣는다.
- `replies` 는 **최신 2건을 고르고 등록순으로 싣는다.** 고르는 기준이 최신이어야 새 답글이 보이고, 싣는 순서가 대화 순서여야 읽힌다 — 위 예시의 13:00 → 14:00 이 그 결과다.
- 내 댓글이 답글이면(depth 2 규칙) `replies` 는 **항상 빈 배열**이다 — 답글에는 답글을 달 수 없다.
- 대상이 삭제·비노출되었거나 **댓글 사용 중지**된 영상의 카드는 목록에서 제외한다(ACTIVITY-02). 제외 조건 전량은 아래 기본안 표에 있다.
- **내 댓글만 삭제된 경우는 제외가 아니다** — 좋아요가 남아 있으면 카드는 남고 `myComment` 만 `null` 이 된다.
- **최근 검색어는 이 API 와 무관하다** — 서버 API·DB 없이 Android 로컬에서만 처리한다.

**나머지 두 모양** — 좋아요만 한 영상(`myComment=null`)과 내 댓글이 답글인 영상(`parentComment` 채움 · `replies=[]`). `data.list[]` 만 적는다.

```json
[
  {
    "video": { "videoSeq": 90, "thumbnailUrl": "https://cdn.motif.io/private/challenge/8b2c1d40-5e6f-4a71-9c8d-1e3f5a7b9c20/thumb.jpg", "viewCount": 120, "likeCount": 8 },
    "liked": true,
    "myComment": null,
    "replies": [],
    "lastInteractedDttm": "2026-08-27T09:00:00.000Z"
  },
  {
    "video": { "videoSeq": 89, "thumbnailUrl": "https://cdn.motif.io/private/challenge/5d1e2f30-7a8b-4c91-9d2e-3f4a5b6c7d80/thumb.jpg", "viewCount": 940, "likeCount": 77 },
    "liked": false,
    "myComment": {
      "commentSeq": 612,
      "content": "저도 그렇게 생각해요",
      "likeCount": 3,
      "replyCount": 0,
      "regDttm": "2026-08-26T18:00:00.000Z",
      "parentComment": {
        "commentSeq": 610,
        "author": { "userSeq": 7, "nickname": "motif_fan", "profileImageUrl": "https://cdn.motif.io/profiles/9a8b7c6d.jpg" },
        "content": "이 구간 안무가 진짜 어렵던데"
      }
    },
    "replies": [],
    "lastInteractedDttm": "2026-08-26T18:00:00.000Z"
  }
]
```

### 실패 응답

**도메인 분기 없음.** 공통 분기 외에는 망가진 `cursor` 의 `400 INVALID_PARAMETER` 뿐이다 — 조용히 첫 페이지로 되돌리지 않는다(무한 스크롤이 원인 없이 맨 위로 점프한다). 커서는 서버가 발급한 opaque 토큰이라 클라이언트가 만들거나 고치지 않는다(§1.9).

### 기본안

| 항목 | 기본안 | 근거 |
|---|---|---|
| 답글 프리뷰 개수 | 2개 | 원본 §4.9 기본안 (§16 #13 클라 확인 대기) |
| 내 댓글 다건일 때 | 최신 1개 | 〃 |
| 답글 프리뷰 표시 순서 | 최신 2건을 고르고 **등록순**으로 | 원본 예시가 이미 오름차순이다. 고르는 기준과 싣는 순서는 다른 질문이고, 둘을 최신순으로 맞추면 대화가 거꾸로 읽힌다 |
| 커서 구성 | `(lastInteractedDttm, videoSeq)` | 보조 키가 없으면 같은 밀리초에 상호작용한 카드가 호출마다 자리를 바꿔 중복·누락이 난다. 한 번의 요청으로 좋아요와 댓글이 같이 들어오는 자리라 실제로 겹친다 |
| "비노출" 의 범위 | 삭제 · 삭제 사용자/챌린지 소속 · **타인의 `PRIVATE`** 까지. `video_hides`(관심 없음)는 **제외하지 않는다** | 관심 없음은 §8.1 피드를 덜 보기 위한 신호지 "내가 한 일" 을 지우는 조작이 아니다. 좋아요를 눌러 둔 영상이 활동에서 사라지면 되돌릴 경로가 없다 |
| `comment_enabled=FALSE` | **좋아요만 눌렀어도 카드째 제외** | 명세 문구 그대로다. 카드의 본체가 댓글 스레드라 열어도 보여 줄 것이 없다 |
| 내 댓글이 삭제됐을 때 | 카드는 남기고 `myComment=null` | 카드까지 지우면 "좋아요는 그대로인데 활동에서 없어졌다" 가 된다 |
| 부모가 삭제/탈퇴했을 때 | `parentComment=null`, 카드는 남는다 | 내가 단 답글은 그대로 있다. 사라진 것은 그 위 맥락뿐이다 |
| `video_comments` 인덱스 | `(user_seq, reg_dttm DESC)` 를 **이 티켓에서 추가**(`0016`) | 좋아요 쪽은 `idx_video_likes_user_dttm` 이 이미 있는데 댓글 쪽은 대상 기준 인덱스뿐이라, 이 조회만 테이블 전체를 훑는다. §4.9 전용이라 별도 이슈로 뺄 이유가 없다 |

---

## 이 문서에서 발견한 원본 명세 반영 대기

1. **아티스트 이름 출처** — `artists` 테이블에 `name` 컬럼이 없다. 이름은 `users.nickname` 이며 `artists.user_seq` 로 조인해야 한다. §4.6·§4.7 을 포함해 아티스트 이름을 내리는 모든 API 가 해당한다.
2. **§4.1 `videos` 를 빈 배열로 바꿨다** — 원본은 "없으면 null" 이라고 적었지만, 같은 "없음"을 `null` 과 `[]` 로 나누면 클라이언트가 분기를 둘 갖게 된다. 목록 필드는 전부 빈 배열로 통일한다(2026-09-13 확정).
3. **§4.3 에 요청 본문을 추가했다** — 원본은 "본문 없음"인데, 그러면 발급된 presigned URL 로 **아무 타입이든 아무 크기든 `profiles/` 에 올릴 수 있다**(2GB mp4 든 실행 파일이든). 바이트가 서버를 지나가지 않아 사후 검증도 불가능하다. `contentType`·`contentLength` 를 받아 **서명 대상 헤더**로 넣으면 S3 가 대신 거부한다 — 어드민 §1.4 가 같은 근거로 이미 그렇게 하고 있어 규약을 맞춘 것이기도 하다(2026-09-16).
4. **§4.3 의 `Content-Type` 서술 보강** — 원본 클라이언트 순서 2번이 "`Content-Type` 은 해당 미디어 타입(JSON 아님)" 이라고만 적었는데, **§4.3 에 보낸 값과 같아야 한다.** 다르면 S3 가 서명 불일치로 거부한다.
