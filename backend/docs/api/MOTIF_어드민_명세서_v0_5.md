# MOTIF 어드민 명세서 — v0.6

| 항목 | 내용 |
|---|---|
| 목적 | 아티스트는 로그인하지 않는 콘텐츠 주체다. 운영팀이 **아티스트 계정을 만들고 → 콘텐츠(챌린지·에셋·공지)를 채우고 → 아티스트로서 팬과 상호작용(대행)하고 → 신고·회원·결제를 운영**하기 위한 백오피스 계약 |
| 사용자 | 운영팀(= 프로젝트 팀원). 팬 앱 사용자·아티스트와 완전 분리 |
| 형태 | **core-api 내 모듈** — Thymeleaf 서버 렌더 화면(`/admin/**`) + JSON API(`/api/v1/admin/**`). 별도 프로세스·별도 배포 없음 *(회의 확정 — BE 구현, Thymeleaf)* |
| 원천 문서 | API 명세서 v0.13 · DB 명세서 v0.17 · 아키텍처 설계서 v0.2(→ v0.3 파급) · 기능 명세서(2026-09-02) · 프로젝트 지침 v2 |
| 표기 | **(회의 확정)** = 확정 / **(기본안 — 확인 필요)** = Claude 기본안, 회의 확정 필요 |
| 비밀값 | 어드민 비밀번호·JWT 서명키·IAM 키는 본 문서에 적지 않는다 — "팀 비밀 저장소 참조" |

> 개정 이력
> - v0.1 (2026-08): 초안 — ERD v0.1 기준(`reg_admin_seq`·`HIDDEN` 상태 등).
> - **v0.2 (2026-09-12): API v0.11 · DB v0.15 기준 전면 재작성.** ① Thymeleaf+JSON API 형태 확정 ② 영상 메타 추출 = **어드민 요청 내 동기 ffmpeg**(인제스트 워커·EC2 워커 노드 컷) ③ 편집본·bbox = AI팀 S3 직접 업로드, 어드민은 키·좌표 입력 + 서버 완결성 검증 ④ 감사 주체 = `admins.user_seq → users(ADMIN)` (DB v0.16 요청) ⑤ 대행 = 사용자 API 서비스 재사용(전 상호작용·동일 파급) ⑥ API §16.1 어드민 반영 대기 3건(전조합 편집본·배너·title/description) 종결.
> - **v0.3 (2026-09-12 · 팀 리뷰 R1 반영):** ① **SCG 제거** — Traefik 단일 앞단, IP 제한·JWT 검증은 core-api 필터, 레이트리밋은 Traefik 미들웨어 ② 비허용 IP **404 확정** ③ 감사 로그 **파일+볼륨 확정**(관측 도구 도입 전제) ④ 챌린지 **원본 영상 교체 금지 확정** ⑤ 곡 **불변**(수정 API·`songs` mod 컬럼 추가 **금지**) ⑥ §1.6 이벤트 발행 설명 보강 ⑦ §1.4 영상 검증 기준의 근거 보강(기준 자체는 미결 유지).
> - **v0.6 (2026-09-20 · [ADR-0014](../adr/0014-artist-alias-search.md) 반영):** **아티스트·그룹 별칭 CRUD 신설**(§3.4.5~8 · §3.5.6~9). 팬 앱 다국어 검색(API §5.2)이 훑는 `artist_aliases`·`group_aliases`(DB v0.17)를 채우는 경로다 — 별칭을 넣을 수단이 없으면 검색 결과가 항상 0건이라 검색 API 와 짝이다. **시연 편의용이라 수정·삭제까지 열고 삭제는 하드 삭제**이며, §3.5.5 아티스트 소프트 삭제는 별칭을 건드리지 않는다(조회가 조인에서 거른다). §5 에 `ALIAS_NOT_FOUND` 추가.

---

## 0-2. v0.4 → v0.5 변경 요약 — ffmpeg 를 app-media 한 곳으로

| 영역 | v0.4 | v0.5 (현재 기준) | 근거 |
|---|---|---|---|
| core-api ffmpeg | 포함(+573MB), 동기 길이·썸네일 추출 | **제거.** ffmpeg 를 쓰는 일은 **전부 app-media** | §1.4 |
| 영상 길이·해상도 | 서버가 `ffprobe` 로 추출 | **업로드 화면(JS)이 읽어 폼에 함께 보낸다** | §1.4 · §3.7.2 |
| 썸네일 | core-api 동기 생성 | **app-media 가 생성.** 등록 시에는 경로만 적는다 | §1.4 |
| 세마포어 1 · ffmpeg 메모리 피크 | core-api 운영 제약 | **불필요** — core-api 에 ffmpeg 가 없다 | §1.4 |
| 티켓 590 (core-api Dockerfile ffmpeg) | 필요 | **삭제** | 부록 C |

**왜 바꾸나** — 인코딩 파라미터(GOP·비트레이트)가 두 프로세스에 나뉘어 있으면 한쪽만
고쳤을 때 **예외도 로그도 없이 화질 전환만** 안 된다(ADR-0010 D4). ffmpeg 를 부르는 곳이
하나면 이 사고가 구조적으로 불가능하다. core-api 이미지도 +573MB 를 피한다.

**동기 추출이 필요했던 유일한 이유는 `duration_ms` NOT NULL 이었다.** 그 값은 파일을
열어야 알 수 있어 서버가 ffprobe 를 돌렸는데, **업로드하는 브라우저는 이미 알고 있다** —
`<video>` 에 파일을 물리면 재생하지 않아도 길이·해상도가 나온다. 어드민이 손으로 입력하는
것이 아니라 화면 JS 가 자동으로 읽어 폼에 싣는다.

`thumbnail_key` 는 애초에 문제가 아니다 — 경로가 규약으로 정해지므로(§1.4 산출물 프리픽스)
파일이 생기기 전에도 적을 수 있다. app-media 가 몇 초 뒤 그 자리에 만든다.

---

## 0-1. v0.3 → v0.4 변경 요약 — ADR-0010 반영

| 영역 | v0.3 | v0.4 (현재 기준) | 근거 |
|---|---|---|---|
| 영상 서빙 형태 | 단일 MP4 | **HLS 다중 렌디션(720p/480p) + 재생 중 화질 전환(ABR)** | ADR-0010, API v0.12 |
| 미디어 처리 분담 | 전부 어드민 요청 내 동기 ffmpeg | 가벼운 것만 동기 / 무거운 것은 비동기 → **v0.5 에서 ffmpeg 전량 app-media 로** | §1.4 |
| `job.media` | 삭제(인제스트 워커 컷) | **부활 — 단 용도가 다르다.** 인제스트(길이·썸네일)가 아니라 **렌디션·HLS 전용** | §1.6, §4 |
| 인코딩 파라미터 | 없음 | GOP 2초 고정·화질 간 정렬, 비트레이트 상한 **필수** | ADR-0010 D4 |
| S3 산출물 프리픽스 | 없음(원본만) | `challenges/{uuid}/` · `results/{challenge\|motif}/{jobUuid}/` | §1.4 |

**왜 v0.3 의 `job.media` 컷을 되돌리나** — 컷 결정의 대상은 **인제스트 워커**(길이·썸네일을
비동기로 뽑는 것)였고, 그 판단은 여전히 유효하다(§1.4 "왜 동기인가"). ADR-0010 이 그 뒤에
**렌디션 인코딩**이라는 다른 작업을 추가했는데, 이것은 성격이 다르다 — 실측으로 썸네일
추출이 **86ms** 인 반면 렌디션 2종 인코딩은 **6.7초 동안 CPU 를 포화**시킨다. 사용자 API 와
같은 프로세스에서 돌리면 응답이 밀린다. 큐 이름을 재사용할 뿐 v0.3 이 컷한 그 워커가
아니다.

---

## 0. v0.1 → v0.2 변경 요약

| 영역 | v0.1 | v0.2 (현재 기준) | 근거 |
|---|---|---|---|
| 감사 컬럼 | `reg_admin_seq`, `processed_admin_seq` | `reg_usr_seq` / `mod_usr_seq` / `processed_usr_seq` → `users.role='ADMIN'` 의 `user_seq`. 어드민 로그인은 `admins`, 이름표는 `users` — 둘을 `admins.user_seq` 로 1:1 연결 | DB v0.15 §5·§7~ / **DB v0.16 요청 #1** |
| 솔로 아티스트 | 1인 그룹으로 등록 | `artists.group_seq = NULL` | DB §9 |
| 챌린지 | 챌린지명, 앨범·발매일, `ACTIVE/HIDDEN` 숨김·재노출 | `title`·`status` 삭제 → **삭제(`is_deleted`)만 존재**, 곡은 `title`·`singer_name` | DB v0.6·v0.10 |
| 챌린지 슬롯 | 출연 멤버 태그 | **인물 슬롯**(bbox 4좌표 + `participant_mask`) + **전(全)조합 편집본 필수 등록**(2ⁿ−1) + 서버 완결성 검증 | DB §13·§14, API R8 |
| 에셋 | 소개·해시태그·단일 가격·제목 제거 | `title`(R6) + `description`, 가격은 `motif_asset_prices`(에셋×국가, 현재 KR), 타입 `NORMAL/PREMIUM/SECRET` | DB §30·§31 |
| 공지 | 제목·대표 이미지(0번) | `content` 만, 이미지는 `display_order` 전체 배열(대표 개념 없음) | DB §27·§28 |
| 대행 기록 | `video_likes.artist_seq`, `comments.artist_seq` | 컬럼 없음 → **아티스트 `user_seq` 로 행 생성**, `has_artist_like/comment` 재계산·정렬 자동 반영. 수행 어드민은 앱 로그 | DB §15·§21 |
| 미디어 처리 | 업로드 → 인제스트 워커가 길이·썸네일 추출 | **어드민 요청 안에서 동기 ffprobe/ffmpeg** → 같은 트랜잭션에 INSERT. 인제스트 워커·EC2 워커 노드 **컷** | *(회의 확정)* §1.4 · **v0.5 에서 ffmpeg 를 app-media 로 일원화 → §0-2** |
| 신설 | — | 숍 배너(`shop_banners`, R7) · 패스 가격 · 회원 정지 · 환불(API §12.3 어드민 범위) · 잡 모니터링 | DB v0.12, API v0.11 |

---

## 1. 공통 규약

### 1.1 배치·접근 경로

- **모듈 위치**: `backend/app-core-api` 내 `admin` 패키지(컨트롤러·뷰·어드민 서비스). 도메인 로직은 사용자 API와 **같은 서비스 빈**을 호출한다(소유권·카운터·이벤트 규칙을 한 곳에만 둔다). 램 증가 0, 이미지 1개 유지 *(설계서 v0.2 ⑪)*.
- **경로 2종**

| 경로 | 형태 | 용도 |
|---|---|---|
| `/admin/**` | Thymeleaf HTML | 화면. 로그인 페이지 외 전부 어드민 인증 필요 |
| `/api/v1/admin/**` | JSON(envelope) | 화면의 `fetch` 호출 + Postman/테스트 + 향후 SPA 전환 시 그대로 재사용 |

- **앞단 구성** *(회의 확정 R1 — SCG 제거)*: **Traefik 만 최전단**(TLS 종료·라우팅·`rateLimit` 미들웨어·`X-Forwarded-For` 부여) → core-api 직행. 기존에 SCG 가 맡던 사용자 JWT 검증은 core-api Spring Security 필터로, `/api/v1/notifications/**` 분리 라우팅은 Traefik 라우터(프로세스 분리 시)로 이관한다. Traefik 의 `forwardedHeaders.trustedIPs` 는 k3s 파드/노드 CIDR 로 한정해 헤더 위조를 막는다.
- **네트워크 제한** *(회의 확정 R1 — 404)*: core-api `AdminIpFilter` 가 `/admin/**`·`/api/v1/admin/**` 요청의 클라이언트 IP(Traefik 가 넣은 `X-Forwarded-For` 첫 값)를 **테일넷 CIDR(100.64.0.0/10) + 팀 고정 IP** 허용 목록과 대조하고, 비허용은 **404**(경로 존재 자체 비노출). Traefik 에서 막지 않는 이유: Traefik `ipAllowList` 는 403 을 반환하고 `errors` 미들웨어도 원래 상태코드를 유지하므로 404 요건은 앱 필터가 맡아야 한다. 허용 목록은 설정값(`admin.allowed-cidrs`)으로 두고 팀 IP 변경 시 재배포 없이 configmap 갱신.
- 발표 어휘: **Citadel 패턴**(운영 기능을 신뢰 네트워크 안에만 노출), **Valet Key**(presigned URL), **Claim-Check**(파일 대신 키만 전달).

### 1.2 인증 — 어드민 JWT

- 로그인 주체는 `admins`(login_id + BCrypt `password_hash`). 성공 시 **어드민 JWT** 발급 — 팬 앱 JWT 와 **서명키 분리**(`ADMIN_JWT_SECRET`, 팀 비밀 저장소), 클레임 `typ=ADMIN` 으로 교차 사용 차단(팬 토큰으로 어드민 경로 접근 시 401, 반대도 동일).
- 클레임: `sub=adminSeq`, `usr=userSeq`(감사 주체 — §1.3), `name`, `typ=ADMIN`, `exp`.
- 전달 방식 2종(둘 다 허용): ① 화면용 **HttpOnly 쿠키** `motif_admin` (`Secure; SameSite=Strict; Path=/`) ② API 도구용 `Authorization: Bearer`. 쿠키가 Strict 이므로 타 사이트에서 위조 요청(CSRF)은 쿠키가 실리지 않는다 — 별도 CSRF 토큰은 두지 않는다 **(기본안 — 테일넷 한정 접근이 전제)**.
- 만료 **12시간**, refresh 없음(재로그인) **(기본안 — 확인 필요)**. 로그아웃은 쿠키 삭제만(서버 측 폐기 목록 없음 — stateless).
- 계정 생성은 **시드로만**(가입 화면 없음) — 절차는 부록 B. `admins.is_active=FALSE` 면 로그인 거부.
- 로그인 실패 잠금(5회)은 **선택**(§8 미결) — 테일넷 한정이라 우선순위 낮음.

### 1.3 감사(Audit) 규약

| 행위 | 기록 위치 | 값 |
|---|---|---|
| 콘텐츠 등록·수정·삭제·가격·배너·신고 처리·회원 정지 | 각 테이블 `reg_usr_seq` / `mod_usr_seq` / `processed_usr_seq` | JWT `usr`(= `admins.user_seq` → `users.role='ADMIN'`) |
| **대행 행위**(좋아요·댓글·답글·댓글 좋아요) | 행위 행의 `user_seq` = **아티스트 유저** | 팬 앱이 "아티스트 행위"로 인식하는 데 필요한 값 |
| 대행을 실제 수행한 어드민 | **애플리케이션 구조화 로그** *(회의 확정 — DB 미기록, `admin_action_logs` 미도입)* | `ADMIN_ACT_AS admin={adminSeq} usr={userSeq} artist={artistSeq} action={LIKE_VIDEO…} target={seq} result={OK|code}` |

- `video_likes`·`*_likes` 는 (대상, 유저, 시각) 3칸이고 댓글 테이블에도 등록자 칸이 없어 **대행 수행자를 DB 에 남길 수 없다** — 위 로그로 갈음한다. **보존은 파일 + 볼륨** *(회의 확정 R1)*: logback **JSON 파일 appender**(`/var/log/motif/admin-audit.log`, 일 단위 롤링, 보존 90일 **(기본안)**)를 stdout 과 병행 출력하고, 파일 경로는 k3s **PVC(local-path)** 로 마운트해 파드 재시작에도 남긴다. 추후 관측 도구(Loki/Promtail 등) 도입 시 이 파일을 수집 대상으로 붙인다 — 도입 전까지는 파일이 원본. 파드 2개 환경이라 파일명에 파드명(`HOSTNAME`)을 포함해 충돌을 피한다.
- **DB v0.16 요청 #1**: `admins.user_seq INTEGER NOT NULL UNIQUE FK→users` 추가. 시드는 `users(role='ADMIN')` → `admins` 순서(부록 B). 배포 환경에 기존 `admins` 행이 있으면 백필 후 `NOT NULL` 적용.

### 1.4 미디어 업로드 규약 (어드민)

**원칙**: 이미지·영상 바이트는 브라우저 → S3 presigned PUT 직행(원칙 ①). 서버는 키만 받고(Claim-Check) 존재를 HEAD 로 확인한다.

**ffmpeg 를 쓰는 일은 전부 app-media 에 둔다** *(v0.5 — 원칙 ① 의 예외를 없앤다)*.
**core-api 에는 ffmpeg 바이너리가 없다.**

| 값 | 어디서 나오나 |
|---|---|
| 영상 길이 · 해상도 | **업로드 화면(JS)** 이 읽어 폼에 함께 보낸다 |
| 썸네일 | **app-media** 가 만든다. 등록 시에는 경로만 적는다 |
| 렌디션 · HLS | **app-media** |

**왜 한 곳에 모으나** — 인코딩 파라미터(GOP·비트레이트)가 두 프로세스에 나뉘면 한쪽만
고쳤을 때 **예외도 로그도 없이 화질 전환만** 안 된다(ADR-0010 D4). 부르는 곳이 하나면
그 사고가 구조적으로 불가능하다. 렌디션 인코딩은 60초 영상 기준 **6.7초 CPU 포화**라
사용자 API 와 같은 프로세스에 둘 수도 없다.

**화면이 길이·해상도를 읽는 방법** — 어드민이 입력하는 것이 아니라 JS 가 자동으로 읽는다.
어드민 조작은 **파일 선택 하나**뿐이고, 화면 흐름은 v0.4 와 같다(폼에 값 3개가 더 실릴 뿐).

```js
const v = document.createElement('video');
v.src = URL.createObjectURL(file);
v.onloadedmetadata = () => {
  // v.duration(초) · v.videoWidth · v.videoHeight 를 폼 hidden 필드에 넣는다
};
```

**검증**(서버, 등록 API 트랜잭션 진입 전)

1. `HEAD s3://motif-media/{videoKey}` → 없으면 409 `MEDIA_NOT_UPLOADED`.
2. 폼이 보낸 `durationMs`·`width`·`height` 규칙 검증 **(기본안 — 확인 필요, §8 #3)**:
   챌린지 원본 **세로**(h > w)·≤ 60초, 에셋 **가로**(w > h)·≤ 20초, 편집본 길이 = 원본 ±500ms.
   위반 시 400 `MEDIA_INVALID`(`data` 에 사유). 수치는 설정값(`admin.media.*`).
3. `thumbnail_key` 는 산출물 규약으로 **계산해 넣는다** — 챌린지 원본이면
   `challenges/{uuid}/thumb.jpg`. 파일은 app-media 가 만든다.

   > **`thumbnails/public/{uuid}.jpg` 가 아니다** *(v0.5 정정)*. v0.4 까지는 core-api 가
   > 동기로 뽑아 그 프리픽스에 올렸지만, v0.5 에서 썸네일 생성이 app-media 로 옮겨가면서
   > **원본과 같은 uuid 아래**로 모인다. `thumbnails/public/` 프리픽스는 §1.4 표에서
   > 서버 생성 항목으로 남지만 **영상 썸네일에는 쓰지 않는다**.
   >
   > **등록 직후 ~ app-media 완료 사이에 썸네일 URL 은 404 다**(수 초). 어드민 목록에서
   > 깨진 이미지로 보인다 — 사용자 노출 경로는 그 시점에 아직 없다(§8 #19).

**값의 출처가 클라이언트인 점** — 개발자 도구로 조작할 수 있다. 다만 어드민은 테일넷
한정(§1.1) · 시드 계정만(§1.2) · 팀원 수 명이라 위험이 낮고, **app-media 가 실제 영상을
읽으므로 실측값으로 정정할 수 있다**(§8 #20). 코덱 이상은 app-media 인코딩에서 걸러진다.

**등록 직후 상태** — `video_key` 는 원본 MP4, `thumbnail_key` 는 아직 없는 파일을 가리킨다.
app-media 가 끝나면 `video_key` 가 `master.m3u8` 로 바뀌고 썸네일 파일이 생긴다.
**그 사이에도 영상은 MP4 로 재생된다**(ABR 만 없다). 스테이징 테이블·nullable 전환·상태
컬럼은 **여전히 필요 없다**.

**S3 프리픽스**(신설 4종 포함) — 오브젝트 키는 전부 UUID 기반, 원본 파일명 미사용:

| kind (presign 요청값) | 프리픽스 | 등급 | 비고 |
|---|---|---|---|
| `CHALLENGE_VIDEO` | `challenges/{uuid}.mp4` | 공개 | **신설.** `raw/` 는 사용자 원본 전용(7일 삭제 후보)이라 분리 |
| `EDITED_VIDEO` / `EDITED_SCENE` / `EDITED_PREVIEW` | `edited/{challengeSeq}/{uuid}.mp4` / `.jpg` / `.jpg` | 공개(첫 장면·선택 화면)·워커 읽기(영상) | **신설.** AI팀 직접 업로드 또는 **어드민 매트릭스 화면에서 파일 업로드**(presign) — 둘 다 키만 등록 API 로 간다. `EDITED_PREVIEW` 는 조합 선택 화면 이미지 *(2026-09-18, 618)* |
| `ASSET_VIDEO` | `assets/{uuid}.mp4` | 공개(미리보기) | 기존 |
| `ARTIST_PROFILE` | `profiles/{uuid}.jpg` | 공개 | 기존(사용자 프로필과 공유) |
| `ARTIST_MOTIF_BG` | `artists/{uuid}.jpg` | 공개 | **신설**(API §11.4 `bannerImageUrl`) |
| `NOTICE_IMAGE` | `notices/{uuid}.jpg` | 공개 | 기존 |
| `BANNER_IMAGE` | `banners/{uuid}.jpg` | 공개 | **신설**(DB §40) |
| (서버 생성) | `thumbnails/public/{uuid}.jpg` | 공개 | 영상 외 추출 썸네일. **영상 썸네일은 산출물 프리픽스 아래**(`{프리픽스}/thumb.jpg`) *(v0.5)* |

**산출물 프리픽스** *(v0.4 신설 — app-media 가 만든다, ADR-0010 D4)*

| 대상 | 원본(입력) | 산출물 |
|---|---|---|
| 챌린지 원본 | `challenges/{uuid}.mp4` | `challenges/{uuid}/` |
| 합성 결과 | `raw/{uuid}.mp4` | `results/{challenge\|motif}/{jobUuid}/` |

산출물 프리픽스 아래에 `master.m3u8` · `480p/` · `720p/` 가 깔린다. 원본과 **같은 uuid**
아래에 놓여 짝이 눈에 보인다. DB 에는 **마스터 재생목록 경로 하나만** 저장하고 나머지는
이 규약으로 찾는다 — **스키마 변경 없음**.

- **seq 가 아니라 uuid 다.** 공개 CDN 경로에 순차 번호를 쓰면 열거가 가능해 콘텐츠의
  존재와 개수가 드러난다.
- 화질당 파일은 **2개**(`data.m4s` + `index.m3u8`). fMP4 + `EXT-X-BYTERANGE` 로 조각을
  한 파일 안의 바이트 구간으로 지정하므로 세그먼트 파일이 폭증하지 않는다
  (영상 1개 2화질 = 총 5개. 옛 TS 조각 방식이면 60개 이상).
- **IAM 파급 추가**: `motif-api` 가 `job.media` 를 발행하고 **app-media** 가 버킷
  `challenges/*`·`results/*`·`raw/*` 에 GET·PUT 권한이 필요하다(신설 `motif-media` 주체).

- presigned PUT 만료: 영상 600초 / 이미지 300초. 허용 Content-Type: 영상 `video/mp4`·`video/quicktime`, 이미지 `image/jpeg`·`image/png`·`image/webp`. 크기 상한 영상 300MB·이미지 10MB **(기본안)** — presign 조건(`content-length-range`)으로 강제.
- **IAM 파급**(인프라): ① `motif-gpu-worker` GET 에 `challenges/*`·`edited/*` 추가(합성 베이스를 읽어야 함) ② AI팀 편집본 업로드용 **`motif-ai-editor`** 신설 — `edited/*` PUT·GET 만 **(기본안)** ③ `motif-api` 는 버킷 전체 RW 유지. ④ 향후 `raw/` 7일 수명주기 도입 시 어드민 프리픽스는 규칙 대상이 아님(프리픽스 분리로 자동 충족).

### 1.5 응답·페이지네이션·에러

- API 명세 §1.2 envelope(`result/code/message/data/timestamp`)·§1.3 규약을 그대로 준용한다. 어드민 목록은 전부 **offset**(`page/size/sort`) — 실시간 유입 밀림이 문제되지 않는다. `size` 기본 20·최대 100.
- 생성 201 / 비동기 접수 202 / 그 외 200(`data:null` 허용). **좋아요 대행 POST/DELETE 는 사용자 API 와 동일하게 204** (같은 서비스 코드).
- 공통 실패(400 `INVALID_PARAMETER`, 401 `UNAUTHORIZED`, 500 `INTERNAL_ERROR`)는 각 API 표에서 생략. 어드민 전용 코드는 §5.
- 어드민 응답의 미디어 URL: 공개 콘텐츠 = CloudFront 일반 URL, 보호 콘텐츠(신고된 PRIVATE 영상·잡 원본/결과) = **signed URL 10분**(API §1.7 준용).

### 1.6 이벤트 발행 (어드민 → RabbitMQ)

**무엇인가** — 어드민이 무언가를 등록·변경했을 때 core-api 가 RabbitMQ 에 "이런 일이 있었다"는 **쪽지(이벤트)** 를 넣는 것이다. 알림 서비스(app-notification)가 그 쪽지를 받아 구독자에게 FCM 을 보낸다(팬아웃). 코어와 알림 서비스는 서로 HTTP 로 부르지 않고 **이 쪽지로만 통신**한다(설계 원칙 — 한쪽이 죽어도 다른 쪽이 멈추지 않고, 쪽지는 큐에 남아 나중에 처리된다). 어드민 화면·API 구현자는 이 표를 몰라도 된다 — 등록 서비스가 트랜잭션 커밋 후 자동으로 발행하며, **구현 위치는 등록 서비스 안**(`@TransactionalEventListener(AFTER_COMMIT)` → `RabbitTemplate.convertAndSend`)이다. 페이로드에는 `eventId(UUID)` 를 넣어 컨슈머가 중복 처리를 막는다. 상세 계약은 §4.

| 행위 | routing key | 컨슈머 | 효과 |
|---|---|---|---|
| 챌린지 등록 | `challenge.created` | evt.notification | 구독자 팬아웃 FCM(`NEW_CHALLENGE`) |
| 공지 등록 | `notice.created` | evt.notification | 구독자 팬아웃 FCM(`NEW_NOTICE`) |
| 대행 댓글·답글 | `comment.created` / `reply.created` | evt.notification · evt.ranking | 사용자 API 와 동일(서비스 재사용) |
| 대행 좋아요 | `video.liked` 등 | evt.ranking | 카운터·랭킹만. **FCM 없음**(좋아요 알림 미발송 — DB §39 회의 확정) |
| 회원 정지 | `user.suspended` **(신설)** | evt.notification | `device_tokens.is_active=FALSE` — 토큰 소유권이 알림 서비스라 코어가 직접 UPDATE 불가(코어↔알림 HTTP 금지 → 이벤트) |
| 에셋 등록 | — | — | 푸시 없음. 홈 6.3 활동 피드에 자연 노출 **(기본안)** |
| **영상 등록 후처리** *(v0.4)* | `job.media` | app-media | 렌디션 2종 + HLS 패키징 → `video_key` 를 `master.m3u8` 로 교체. **챌린지 등록(3.7.2)·에셋 등록(3.8.2)·합성 완료 셋이 같은 큐로 보낸다** *(에셋은 2026-09-17 추가)* |

### 1.7 화면 목록 (Thymeleaf)

| 메뉴 | 화면 | 호출 API(§3) |
|---|---|---|
| 로그인 | `/admin/login` | 3.1 |
| 아티스트 | 소속사 · 그룹 · 아티스트 목록/등록/상세 | 3.3 · 3.4 · 3.5 |
| 곡 | 목록/등록/수정 | 3.6 |
| 챌린지 | 목록 · 등록(원본 업로드 → 슬롯 입력 → 편집본 키 매트릭스) · 상세(카운터·슬롯·편집본) | 3.2 · 3.7 |
| 모티프 에셋 | 목록 · 등록(영상 업로드 + 가격) · 활성/가격 | 3.8 |
| 패스 가격 | 아티스트별 개월 수 가격표 | 3.9 |
| 공지 | 목록 · 작성(이미지 다중) · 수정 | 3.10 |
| 숍 배너 | 목록 · 정렬(드래그 또는 위/아래) · 활성 | 3.11 |
| **활동 대행** | 상단 "이 아티스트로 활동" 선택 → 챌린지/참여영상/공지 탐색 → 좋아요·댓글 | 3.12 |
| 신고 | 목록(상태 필터) · 상세(미리보기) · 인용/기각 | 3.13 |
| 회원 | 검색 · 상세 · 정지/해제 | 3.14 |
| 주문(후반) | 목록 · 상세 · 환불 | 3.15 |
| 합성 잡(후반) | 목록 · 상세 · 재큐 · 배치 수동 실행 | 3.16 |

화면은 관리 효율 목적의 최소 구현(디자인 불요). 업로드는 화면의 JS 가 3.2 presign → S3 PUT → 폼 제출(키) 3단계로 처리한다.

---
## 2. 기능 명세 (ADMIN-xx)

각 항목은 **동작 → 검증 → DB 반영 → 이벤트/파급** 순서다. 상세 계약은 §3 API 를 따른다.

### ADMIN-01. 로그인·세션

- `admins` ID/PW 로그인 → 어드민 JWT(쿠키 + Bearer). `is_active=FALSE` 거부, `last_login_dttm` 갱신.
- 헤더에 로그인 어드민 이름과 현재 "활동 중 아티스트" 컨텍스트(ADMIN-11) 표시.

### ADMIN-02. 소속사 관리

- 등록·수정(회사명)·삭제(소프트). 활성 회사명 중복 불가(`uq_companies_name_active`).
- 삭제 차단: 비삭제 그룹/아티스트가 참조 중이면 409 — 먼저 이동시켜야 한다(DB §7).

### ADMIN-03. 그룹 관리

- 등록(소속사·그룹명)·수정·삭제(소프트). 그룹명 중복 허용(pg_trgm 검색 대상).
- **소속사 변경 시** 해당 그룹의 모든 비삭제 멤버 `artists.company_seq` 를 같은 트랜잭션에서 갱신(DB §8).
- 삭제 차단: 비삭제 멤버가 있으면 409.

### ADMIN-04. 아티스트 관리 ★

**등록** = 한 트랜잭션에서 `users(role='ARTIST', nickname=아티스트명, profile_image_key)` + `artists(user_seq, company_seq, group_seq, bio, motif_bg_image_key, reg_usr_seq)` 생성. **`user_accounts`·`user_oauth_accounts` 는 만들지 않는다**(로그인 없음 — *회의 확정*).

- 아티스트명 = `users.nickname`(검색 GIN 대상). 형식: **1~50자 자유**(공백·특수문자 허용 — "NCT 127") **(기본안 — USER 닉네임 2~20자 규칙 미적용)**. 중복 허용(DB 규칙).
- 그룹을 지정하면 소속사는 **그룹의 소속사로 강제**(요청 `companySeq` 불일치 시 400). 솔로는 `groupSeq=null` + `companySeq` 필수.
- 프로필 이미지·모티프 배너 배경은 presign(3.2) 후 키 전달, 서버 HEAD 검증.
- **수정**: 이름·소개·그룹(솔로↔그룹 전환 포함)·소속사(그룹 없을 때만 직접 변경)·이미지 2종. 그룹 소속 아티스트의 소속사 단독 변경은 거부(DB §9 불변식).
- **삭제**(소프트, 복구 없음) — DB §9 연쇄를 같은 트랜잭션에서: `artists.is_deleted=TRUE`, `users.is_deleted=TRUE`, 해당 아티스트 `motif_assets.is_active=FALSE`·`motif_asset_prices.is_active=FALSE`·`motif_pass_prices.is_active=FALSE`. ACTIVE PASS 는 만료까지 유지, 과거 좋아요·댓글 불변. `subscriptions` 행은 유지하고 조회 시 필터 **(기본안)**. 화면은 파급을 나열한 확인 대화상자 후 실행.
- 삭제 아티스트는 대행(ADMIN-11)·콘텐츠 등록 대상에서 제외.

### ADMIN-05. 곡 관리

- **등록만 — 수정·삭제 없음(불변)** *(회의 확정 R1 — `songs` mod 컬럼 추가 금지)*. `songs` 에 `is_deleted`·`mod_dttm` 이 없고 챌린지가 FK RESTRICT 로 참조한다. 중복 허용(리믹스 등).
- 오타·정보 오류는 **새 곡을 등록하고 챌린지의 곡을 재연결**(3.7.4 `songSeq`)한다. 잘못 등록된 곡 행은 참조가 없어지면 그대로 남는다(운영상 무해). 화면은 등록 전 동일 곡명·가수명 존재 시 경고.

### ADMIN-06. 챌린지 등록·관리 ★★

**등록은 원본 + 슬롯 + 편집본을 한 요청으로 받아 전부 유효할 때만 생성한다(all-or-nothing).** 그래야 "편집본이 덜 올라간 미공개 챌린지" 상태가 필요 없다(`challenges` 에 상태 컬럼이 없음).

흐름(화면):

1. 원본 영상 presign → 브라우저 PUT → `videoKey`.
2. 메인 아티스트·곡(기존 선택 또는 즉시 신규 등록)·소개글 입력.
3. **슬롯 입력** — 교체 가능한 추가 참여 아티스트를 **화면 좌→우 순서**로 추가하고 bbox 4좌표(원본 px, 좌상단 원점)를 입력. 좌표·편집본은 **AI팀이 산출해 S3 `edited/{challengeSeq}/…` 에 올려둔 값** *(회의 확정)* — 어드민은 옮겨 적는다. 서버가 `participant_mask` 를 순서대로 1·2·4… 부여(수기 비트 입력 없음 → 오류 차단).
4. **편집본 매트릭스** — 슬롯 n개면 서버가 2ⁿ−1 조합을 표로 보여주고, 필요한 조합만 채운다. **채운 조합만 등록된다**(2026-09-21 — 메인 아티스트는 지워지지 않으므로 전조합을 만들 이유가 없다). 한 행은 파일 3개를 다 올리거나 비운다.
5. 제출 → 서버 검증 → 동기 ffmpeg(§1.4) → 트랜잭션 INSERT → `challenge.created` 발행.

서버 검증(§3.7 참조): 아티스트·곡 비삭제, 슬롯 아티스트 중복 없음·비삭제(메인도 슬롯 가능), bbox CHECK(우하단 > 좌상단, ≥0), 편집본 집합은 부분 허용(중복·범위 밖만 409 `EDITED_SET_INCOMPLETE`), 모든 키 HEAD, 편집본 길이 = 원본 ±500ms **(기본안)**.

- **슬롯 0개**(솔로 챌린지) 허용 → 편집본 0개, 참여는 원본 위 합성(`composite_jobs.edited_challenge_seq NULL` 경로). ⚠ 이 경우 NULL 경로가 **유일한 참여 방식**이므로 DB 미결 #8(NULL 허용 여부)은 "허용 유지"가 필요하다 — 근거로 제출(§6 #5).
- 슬롯 수 운영 권고 ≤ 3(편집본 7개), 하드 상한 4(15개) **(기본안)**.
- **수정**: 소개글·곡만. **원본 영상 교체 금지**(교체하면 bbox·편집본 전부 무효 — 삭제 후 재등록) *(회의 확정 R1)*. 메인 아티스트 불변(DB).
- **편집본 키 교체**: 같은 행 키 갱신(`mod_dttm`), 항상 가능(DB §14).
- **슬롯 구성 전체 교체**: 참여자·편집본 하드 삭제/재생성(DB §13). `composite_jobs` 가 참조 중인 편집본은 삭제 불가(FK RESTRICT) → 409 `EDITED_IN_USE` — 즉 **참여가 시작된 챌린지의 슬롯 구성은 사실상 고정**. 화면은 참여 잡 0건일 때만 버튼 활성.
- **삭제**(소프트): 자식 참여영상·댓글·좋아요 연쇄 없음, API 노출만 차단(DB §12). 진행 중 CHALLENGE 잡은 결과 최종화 직전 재검증으로 FAILED 처리(DB §37).

### ADMIN-07. 모티프 에셋 등록·관리 ★

- 등록: 아티스트·`title`(표시명)·`description`(소개, 선택)·`assetType`·영상(presign → 동기 ffmpeg) · **KR 가격**(`amount`, `currency=KRW`)을 한 트랜잭션에 — `motif_assets` + `motif_asset_prices(KR, is_active=TRUE)`. 가격 없이 등록도 허용(카탈로그 노출되되 `purchasable:false`) **(기본안)**.
- 등록 즉시 홈 활동 피드(API §6.3 `MOTIF_ASSET`)에 노출. 푸시 없음.
- 수정: `title`·`description`·`assetType`·영상 교체(재추출; 기존 결과물 키는 별도라 영향 없음) **(기본안)**.
- **활성/비활성**(`is_active`): FALSE 전이 시 가격 전부 FALSE(DB §30). TRUE 재전이 시 가격은 **자동 재활성되지 않음** → 화면 경고 + 가격 재활성 버튼. 개별 구매자는 비활성 후에도 이용 가능(권한 규칙).
- **가격**: 에셋×국가 upsert. `is_active=TRUE` 전환은 에셋 활성 AND 국가 활성일 때만(409). 가격 변경은 기존 구매·PENDING 주문 스냅샷에 영향 없음.
- 삭제 없음(`is_active` 로 종료 표현).

### ADMIN-08. 패스 가격 관리

- 아티스트×KR×`durationMonths` upsert(`amount`·`currency`·`is_active`). 초기 1/3/6/12 개월 자유(DB CHECK 미고정).
- `is_active=TRUE` 전환 조건: 국가 활성 AND 아티스트 비삭제(DB §32). 변경은 기존 ACTIVE PASS 에 영향 없음.
- 아티스트 상세(API §5.3 `pass`)·패스 화면(§11.4 `prices`)이 이 표를 읽는다.

### ADMIN-09. 공지 작성·관리

- 작성: 아티스트·본문·이미지 0~N장(presign 다중 → 키 배열, 배열 순서 = `display_order`). 제목 없음.
- 등록 시 `notice.created` → 구독자 팬아웃 FCM. **수정·삭제는 재발송 없음**.
- 수정: 본문 / 이미지 배열 전체 교체(`notice_images` 하드 삭제 후 재생성 — DB §28).
- 삭제(소프트): 댓글·좋아요·이미지 연쇄 없음.

### ADMIN-10. 숍 공용 배너 관리

- 등록(이미지 키 + 순서, 생략 시 말단)·이미지 교체·활성 토글·**전체 재정렬**·하드 삭제(참조 테이블 없음 — **기본안**).
- `display_order UNIQUE` 라 스왑 중 충돌 → 재정렬은 트랜잭션에서 **전부 +10000 오프셋 후 0..n 재부여** (구현 노트).
- 노출은 API §11.11(`is_active` · `display_order ASC`).

### ADMIN-11. 아티스트 활동 대행 ★★

**정의**: 어드민이 "이 아티스트로 활동" 컨텍스트를 선택한 뒤 수행하는 상호작용. DB 에는 **아티스트 유저(`users.role='ARTIST'`)의 행위**로 기록되어 팬 앱의 아티스트 표시·정렬·카운터·알림이 별도 작업 없이 작동한다.

**범위** *(회의 확정 — 상호작용 전부)*:

| 행위 | 대상 | 파급(사용자 API 와 동일) |
|---|---|---|
| 좋아요/취소 | 챌린지 · 참여 영상 · 공지 | `like_count` ±1, 참여영상은 `has_artist_like` 재계산, `evt.ranking`. **FCM 없음**(좋아요 알림 미발송 규칙) |
| 댓글 작성 | 챌린지 · 참여 영상 · 공지 | `comment_count +1`, 참여영상 `has_artist_comment`, 정렬 1순위(대상 아티스트 댓글), `comment.created` → 게시물 소유자 FCM(본인 제외) |
| 답글 작성 | 위 3종의 루트 댓글 | `reply.created` → 부모 댓글 작성자 FCM(NOTI-05) |
| 댓글·답글 삭제 | 아티스트 본인 댓글만 | 댓글 공통 삭제 정책(답글 연쇄·좋아요 하드 삭제·카운터) |
| 댓글 좋아요/취소 | 3종 댓글 | `like_count` ±1 |

**구현 원칙**: 대행 API 는 사용자 API 컨트롤러가 호출하는 **같은 서비스 메서드**를 `actorUserSeq = 아티스트 user_seq` 로 호출한다. 응답 규약도 동일(좋아요 204, 댓글 201 등). 어드민 전용 분기는 ① 액터 치환 ② 아티스트 비삭제 검증 ③ 구조화 로그(§1.3) 세 가지뿐이다.

**제약**

- 참여 영상 게시·관심없음·신고는 대행 대상이 아니다(ARTIST 계정 게시 금지 — API R5; 나머지는 팬 기능).
- 대행 대상은 **PUBLIC 참여 영상만** — PRIVATE 는 타인에게 존재 은닉 규칙 **(기본안 — 확인 필요, §8)**.
- 삭제된 대상·삭제 챌린지 소속 영상·`comment_enabled=FALSE` 영상의 댓글은 사용자 API 와 같은 404/403.
- 아티스트가 **자기 공지·자기 챌린지에 댓글**을 남기면 소유자=본인이라 FCM 미발송(자기 행위 규칙) — 팬 답글에 대한 답글만 팬에게 발송된다.

**탐색 화면**(대행용): 챌린지 목록 → 참여 영상 목록(추천순/최신순, PUBLIC) → 영상 재생 + 댓글 스레드 / 공지 목록 → 댓글 스레드. 각 항목에 "♥ / 댓글 / 답글" 버튼.

### ADMIN-12. 신고 처리

- 목록: 상태(`RECEIVED/ACCEPTED/REJECTED`)·대상 종류 필터, 접수순. 항목에 대상 미리보기(영상: 썸네일·signed URL / 댓글: 내용·작성자), 신고자, `reason_code`(코드표 미결 — 값 그대로 표시), 대상 삭제 여부.
- 처리: **인용(ACCEPTED)** = 대상 소프트 삭제 + 후처리(참여영상 최초 삭제 시 `participant_count −1`, 댓글은 공통 삭제 정책) 같은 트랜잭션, 멱등(이미 삭제된 대상이면 상태만 전이) / **기각(REJECTED)** = 유지. `processed_usr_seq`·`processed_dttm` 기록(DB §26 CHECK).
- 동일 대상의 다른 신고는 자동 처리하지 않는다 — 목록에 "같은 대상 신고 n건" 표시로 일괄 판단 지원 **(기본안)**.

### ADMIN-13. 회원 관리

- 검색(닉네임 trgm·`userSeq`)·상세(계정 상태·참여 영상 수·구독·구매·ACTIVE 패스).
- **정지**: `user_accounts.account_status='SUSPENDED'` + 해당 사용자 활성 `refresh_tokens` 전부 revoke(코어) + `user.suspended` 이벤트 → 알림 서비스가 `device_tokens.is_active=FALSE`. 정지 사유는 컬럼이 없어 로그만 **(기본안)**. ARTIST/ADMIN 은 정지 대상 아님(아티스트는 삭제 사용).
- **해제**: `ACTIVE` 전이. 토큰은 복구하지 않고 재로그인 시 재발급·토큰 재등록(DB §6·§38).
- 회원 삭제(`is_deleted`)·탈퇴는 **범위 외**(DB "회원 탈퇴 프로젝트 범위 밖") — 정지로 갈음 **(기본안, §8)**.

### ADMIN-14. 주문 조회·환불 *(Phase 후반 — 컷 가능)*

- 조회: 사용자·상태·상품·기간 필터(읽기 전용).
- **환불**(`PAID → REFUNDED`, 전액만): 토스 결제 취소 API 호출 성공 후 같은 트랜잭션에서 `orders.status='REFUNDED'` + `asset_purchases.status='REFUNDED'` 또는 `motif_passes.status='REFUNDED'`(즉시 권한 종료). 토스 실패 시 상태 불변 + 502. 결과물이 이미 생성된 에셋 환불 허용 여부는 운영 정책(§8).

### ADMIN-15. 합성 잡 모니터링·배치 운영 *(Phase 후반 — 컷 가능)*

- 잡 목록(상태·타입·사용자·기간)·상세(원본/결과 signed URL, `attempt`, `fail_code`, `processing_ms`).
- **재큐**: FAILED 잡을 API §13.4 와 같은 로직으로(소유자 검증 대신 어드민 권한). 사용자 재시도와 동일하게 `attempt+1`·`fail_code=NULL`.
- **배치 수동 실행**: 랭킹 스냅샷·조회수 플러시·카운터 대사·패스/주문 만료·잡 스윕 — ShedLock 락 획득 실패 시 409(이미 실행 중). 시연 직전 데이터 정합 맞추는 용도.

---
## 3. API 명세 — `/api/v1/admin/**`

표기: 🔓 = 어드민 인증 불요. 그 외 전부 어드민 JWT 필수. 실패 표에는 도메인 분기만 적는다(공통 400/401/500 생략). 성공 응답은 envelope 껍데기를 생략하고 HTTP 코드와 `data` 만 적는다(API 명세 §1.2 규약).

### 3.1 인증

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.1.1 | POST 🔓 | `/auth/login` | 어드민 로그인 — 쿠키 + 토큰 |
| 3.1.2 | POST | `/auth/logout` | 쿠키 삭제 |
| 3.1.3 | GET | `/auth/me` | 현재 어드민 |

**3.1.1 요청** `{ "loginId": "ops_kim", "password": "…" }`

**성공 (200)** — `Set-Cookie: motif_admin=…; HttpOnly; Secure; SameSite=Strict; Path=/` + `data`:

```json
{ "adminSeq": 1, "name": "김운영", "userSeq": 2, "accessToken": "eyJ…", "expiresIn": 43200 }
```

`accessToken` 은 API 도구용(화면은 쿠키만 사용). `last_login_dttm` 갱신.

| HTTP | code | 조건 |
|---|---|---|
| 401 | `ADMIN_LOGIN_FAILED` | ID 또는 PW 불일치(구분하지 않음) |
| 403 | `ADMIN_INACTIVE` | `admins.is_active=FALSE` |

**3.1.3 성공 (200)** `data`: `{ "adminSeq": 1, "name": "김운영", "userSeq": 2 }`

### 3.2 미디어 presign

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.2.1 | POST | `/media/presign` | 업로드 URL 발급(kind 별 프리픽스 — §1.4) |

**요청** `{ "kind": "CHALLENGE_VIDEO", "contentType": "video/mp4", "contentLength": 48213377, "challengeSeq": 11 }`

- `kind` ∈ `CHALLENGE_VIDEO | EDITED_VIDEO | EDITED_SCENE | EDITED_PREVIEW | ASSET_VIDEO | ARTIST_PROFILE | ARTIST_MOTIF_BG | NOTICE_IMAGE | BANNER_IMAGE`. `challengeSeq` 는 `EDITED_*` 에서만(프리픽스 경로 구성 — 신규 등록 중이면 생략, 서버가 `edited/pending/{uuid}` 발급 후 등록 시 경로 그대로 사용 **(기본안)**).
- 서버가 `contentType` 허용 목록·`contentLength` 상한을 검증하고 presigned PUT 조건에 박는다.

**성공 (200)** `data`:

```json
{ "uploadUrl": "https://s3…presigned-put", "objectKey": "challenges/3f9c….mp4", "expiresIn": 600 }
```

| HTTP | code | 조건 |
|---|---|---|
| 400 | `MEDIA_TYPE_NOT_ALLOWED` | kind 에 맞지 않는 Content-Type 또는 크기 초과 |

### 3.3 소속사 `companies`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.3.1 | GET | `/companies?q&includeDeleted&page&size` | 목록 |
| 3.3.2 | POST | `/companies` | 등록 |
| 3.3.3 | PATCH | `/companies/{companySeq}` | 회사명 수정 |
| 3.3.4 | DELETE | `/companies/{companySeq}` | 소프트 삭제 |

**3.3.1 성공 (200)** `data.list[]`:

```json
{ "companySeq": 1, "companyName": "SM엔터테인먼트", "groupCount": 4, "artistCount": 21, "isDeleted": false, "regDttm": "2026-09-12T03:00:00.000Z" }
```

**3.3.2 요청** `{ "companyName": "SM엔터테인먼트" }` → **201** `data`: `{ "companySeq": 1 }`

| HTTP | code | 조건 |
|---|---|---|
| 409 | `COMPANY_NAME_DUPLICATE` | 활성 회사명 중복(`uq_companies_name_active`) |
| 404 | `COMPANY_NOT_FOUND` | 3.3.3/4 — 미존재·삭제 |
| 409 | `COMPANY_IN_USE` | 3.3.4 — 비삭제 그룹/아티스트 참조 중(`data` 에 참조 건수) |

### 3.4 그룹 `artist_groups`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.4.1 | GET | `/groups?companySeq&q&includeDeleted&page&size` | 목록 |
| 3.4.2 | POST | `/groups` | 등록 |
| 3.4.3 | PATCH | `/groups/{groupSeq}` | 그룹명·소속사 수정 |
| 3.4.4 | DELETE | `/groups/{groupSeq}` | 소프트 삭제 |
| 3.4.5 | GET | `/groups/{groupSeq}/aliases` | 별칭 목록 **(v0.6 신설)** |
| 3.4.6 | POST | `/groups/{groupSeq}/aliases` | 별칭 등록 **(v0.6 신설)** |
| 3.4.7 | PUT | `/groups/{groupSeq}/aliases/{aliasSeq}` | 별칭 수정 **(v0.6 신설)** |
| 3.4.8 | DELETE | `/groups/{groupSeq}/aliases/{aliasSeq}` | 별칭 **하드** 삭제 **(v0.6 신설)** |

**3.4.1 항목** `{ "groupSeq": 2, "groupName": "aespa", "company": { "companySeq": 1, "companyName": "SM엔터테인먼트" }, "memberCount": 4, "isDeleted": false, "regDttm": "…" }`

**3.4.2 요청** `{ "companySeq": 1, "groupName": "aespa" }` → **201** `{ "groupSeq": 2 }`

**3.4.3 요청** `{ "groupName": "aespa", "companySeq": 3 }` — 부분 수정. `companySeq` 변경 시 비삭제 멤버 전원의 `artists.company_seq` 를 같은 트랜잭션에서 갱신(DB §8). **200** `data` = 3.4.1 항목.

**3.4.5~3.4.8 그룹 별칭** — 계약은 §3.5.6~3.5.9 아티스트 별칭과 **완전히 같다**(경로의 `artists/{artistSeq}` 자리가 `groups/{groupSeq}` 일 뿐). 대상이 없으면 `GROUP_NOT_FOUND`. 상세 설명은 §3.5.6 아래 한 곳에만 적는다.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `GROUP_NOT_FOUND` / `COMPANY_NOT_FOUND` | 대상 없음·삭제 |
| 404 | `ALIAS_NOT_FOUND` | 3.4.7·3.4.8 — 별칭 없음, 또는 그 그룹의 별칭이 아님 |
| 409 | `GROUP_IN_USE` | 3.4.4 — 비삭제 멤버 존재 |

### 3.5 아티스트 `artists` + `users(ARTIST)`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.5.1 | GET | `/artists?q&groupSeq&companySeq&includeDeleted&page&size` | 목록 |
| 3.5.2 | POST | `/artists` | 등록(users + artists 원자 생성) |
| 3.5.3 | GET | `/artists/{artistSeq}` | 상세 |
| 3.5.4 | PATCH | `/artists/{artistSeq}` | 수정 |
| 3.5.5 | DELETE | `/artists/{artistSeq}` | 소프트 삭제 + 연쇄 |
| 3.5.6 | GET | `/artists/{artistSeq}/aliases` | 별칭 목록 **(v0.6 신설)** |
| 3.5.7 | POST | `/artists/{artistSeq}/aliases` | 별칭 등록 **(v0.6 신설)** |
| 3.5.8 | PUT | `/artists/{artistSeq}/aliases/{aliasSeq}` | 별칭 수정 **(v0.6 신설)** |
| 3.5.9 | DELETE | `/artists/{artistSeq}/aliases/{aliasSeq}` | 별칭 **하드** 삭제 **(v0.6 신설)** |

**3.5.1 항목**

```json
{
  "artistSeq": 3, "userSeq": 40, "name": "카리나",
  "group": { "groupSeq": 2, "groupName": "aespa" }, "company": { "companySeq": 1, "companyName": "SM엔터테인먼트" },
  "profileImageUrl": "https://cdn…/profiles/….jpg",
  "subscriberCount": 15230, "challengeCount": 12, "assetCount": 5, "noticeCount": 3,
  "isDeleted": false, "regDttm": "…"
}
```

**3.5.2 요청**

```json
{
  "name": "카리나", "groupSeq": 2, "companySeq": 1,
  "bio": "aespa의 카리나입니다.",
  "profileImageKey": "profiles/3f9c….jpg", "motifBgImageKey": "artists/a1b2….jpg"
}
```

- 검증: `name` 1~50자(공백 제거 후 비어있지 않음), `groupSeq` 지정 시 그룹 비삭제 **및 `companySeq` = 그룹 소속사**(불일치 400 `INVALID_PARAMETER` — 화면은 그룹 선택 시 소속사를 자동 채움·잠금), 미지정 시 `companySeq` 필수·비삭제. 이미지 키 2종은 선택, 지정 시 HEAD.
- 처리(트랜잭션): `users(nickname=name, profile_image_key, role='ARTIST')` INSERT → `artists(user_seq, group_seq, company_seq, bio, motif_bg_image_key, reg_usr_seq=JWT.usr)` INSERT. `user_accounts` 미생성 *(회의 확정)*.

**성공 (201)** `data`: `{ "artistSeq": 3, "userSeq": 40 }`

**3.5.3 성공 (200)** `data` = 3.5.1 항목 + `bio`, `motifBgImageUrl`, `passPrices[]`(3.9), `recentChallenges[]`(최신 5, 경량).

**3.5.4 요청** — 부분 수정:

```json
{ "name": "카리나", "bio": "…", "groupSeq": null, "companySeq": 1, "profileImageKey": "profiles/….jpg", "motifBgImageKey": "artists/….jpg" }
```

- `groupSeq: null` = 솔로 전환(이때 `companySeq` 로 소속사 유지/변경). 그룹 소속 상태에서 `companySeq` 만 바꾸는 요청은 409 `ARTIST_COMPANY_LOCKED`(그룹 변경 흐름을 타야 함 — DB §9). `name` 변경은 `users.nickname` 갱신 + `users.mod_usr_seq`.
- **200** `data` = 3.5.3.

**3.5.5** — 확인 대화상자 뒤 호출. 같은 트랜잭션: `artists.is_deleted`, `users.is_deleted`, 에셋·에셋 가격·패스 가격 `is_active=FALSE`. **200** `data`: `{ "deactivatedAssets": 5, "deactivatedAssetPrices": 5, "deactivatedPassPrices": 2 }`

**별칭 행은 건드리지 않는다.** 팬 앱 검색이 조회 시 `artists.is_deleted = FALSE` 조인으로 거르므로(API §5.2), 지우면 복구 경로만 잃는다.

**3.5.1~3.5.5 실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` / `GROUP_NOT_FOUND` / `COMPANY_NOT_FOUND` | 대상 없음·삭제 |
| 409 | `IMAGE_NOT_UPLOADED` | 이미지 키 S3 부재(API §4.4 와 동일 코드) |
| 409 | `ARTIST_COMPANY_LOCKED` | 그룹 소속 아티스트의 소속사 단독 변경 |
| 409 | `ARTIST_ALREADY_DELETED` | 3.5.5 — 이미 삭제 |

#### 3.5.6~3.5.9 아티스트 별칭 `artist_aliases` *(v0.6 신설)*

팬 앱 다국어 검색(API §5.2)이 훑는 별칭을 운영자가 채워 넣는 경로다. **별칭을 넣을 수단이 없으면 검색 결과가 항상 0건**이라 검색 API 와 짝이다.

> **이 4개 API 는 프로젝트 기능 범위가 아니라 시연 편의용이다.** 그래서 팬 앱에 없는 수정·삭제까지 열어 두고, 삭제도 소프트가 아니라 **하드 삭제**다. 프로젝트 도메인의 소프트 삭제 규약(§3.5.5)과 혼동하지 않는다 — 그쪽은 별칭을 건드리지 않는다.

**3.5.6 목록** — 비페이지 전량(`ListResponse`). 한 아티스트당 몇 개 수준이라 페이지를 두지 않는다.

```json
{ "list": [ { "aliasSeq": 11, "languageCode": "ko", "alias": "카리나", "regDttm": "…" },
            { "aliasSeq": 12, "languageCode": "en", "alias": "Karina", "regDttm": "…" } ] }
```

**3.5.7 등록 요청** `{ "languageCode": "en", "alias": "Karina" }` → **201** `{ "aliasSeq": 12 }`

**3.5.8 수정 요청** — 본문은 등록과 같다(부분 수정이 아니라 전체 치환이라 `PATCH` 가 아니라 `PUT`). **200** `data` = 3.5.6 항목.

**3.5.9 삭제** — **200** `data: null`.

- 검증: `languageCode` 필수·ISO 639-1 소문자 2자(`^[a-z]{2}$`), `alias` 필수·공백 제거 후 1~100자.
- **중복을 막지 않는다.** 같은 언어에 별칭 여러 개가 정상이고(`aespa`·`æspa`, 활동명·본명), 같은 값이 두 번 들어가도 409 를 내지 않는다 — DB 에 UNIQUE 가 없다(DB §41). 팬 앱 검색은 `artistSeq` 로 중복을 제거하므로 결과가 겹치지 않는다.
- 3.5.8·3.5.9 는 `aliasSeq` 가 **경로의 아티스트 소유인지** 확인한다. 남의 별칭을 `aliasSeq` 만으로 고치는 경로를 열지 않는다 — 아니면 `ALIAS_NOT_FOUND`.
- 삭제된 아티스트(`is_deleted=TRUE`)에는 별칭을 달지 않는다 — `ARTIST_NOT_FOUND`.
- 감사 로그: `CREATE_ARTIST_ALIAS`·`UPDATE_ARTIST_ALIAS`·`DELETE_ARTIST_ALIAS` (그룹 쪽은 `..._GROUP_ALIAS`). 하드 삭제라 지워진 기록은 감사 로그에만 남는다.

**3.5.6~3.5.9 실패 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | 대상 아티스트 없음·삭제됨 |
| 404 | `ALIAS_NOT_FOUND` | 3.5.8·3.5.9 — 별칭 없음, 또는 그 아티스트의 별칭이 아님 |
| 400 | `INVALID_PARAMETER` | `languageCode` 형식 위반, `alias` 공백뿐 또는 100자 초과 |

### 3.6 곡 `songs`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.6.1 | GET | `/songs?q&page&size` | 목록(`q` 는 곡명·가수명 ILIKE — trgm 인덱스 없음, 규모 소) |
| 3.6.2 | POST | `/songs` | 등록 |
| ~~3.6.3~~ | — | (삭제 — R1) | 곡 수정 API 없음 — 곡은 불변, 오류는 새 곡 등록 + 챌린지 재연결(3.7.4) *(회의 확정)* |

**3.6.2 요청** `{ "title": "Supernova", "singerName": "aespa" }` → **201** `{ "songSeq": 11 }`. 중복 곡 허용(DB). 화면은 등록 전 동일 곡명 존재 시 경고만.

**3.6.1 항목** `{ "songSeq": 11, "title": "Supernova", "singerName": "aespa", "challengeCount": 3, "regDttm": "…" }`

**실패 분기** — 공통 외 없음. (`SONG_NOT_FOUND` 는 3.7.2/3.7.4 에서 곡 참조 시 사용.)

### 3.7 챌린지 `challenges` · `challenge_participants` · `edited_challenges` ★★

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.7.1 | GET | `/challenges?artistSeq&songSeq&includeDeleted&page&size` | 목록 |
| 3.7.2 | POST | `/challenges` | **등록(원본+슬롯+편집본 원자)** |
| 3.7.3 | GET | `/challenges/{challengeSeq}` | 상세 |
| 3.7.4 | PATCH | `/challenges/{challengeSeq}` | 소개글·곡 수정 |
| 3.7.5 | PUT | `/challenges/{challengeSeq}/participants` | 슬롯 구성 전체 교체(잡 참조 없을 때만) |
| 3.7.6 | PATCH | `/edited-challenges/{editedChallengeSeq}` | 편집본 키 교체 |
| 3.7.7 | DELETE | `/challenges/{challengeSeq}` | 소프트 삭제 |
| 3.7.8 | POST | `/challenges/validate` | 등록 전 사전 검증(dry-run) **(기본안 — 화면 편의)** |
| 3.7.9 | PATCH | `/challenges/{challengeSeq}/participants/{artistSeq}` | 슬롯 앵커 단독 수정 *(2026-09-18, 618)* |

> **2026-09-18 (S15P21D104-618) — bbox → 앵커, 편집본에 선택 화면 이미지.** 촬영 전 "함께 챌린지하고 싶은 아티스트를 선택" 화면(시안 10-1~3)이 슬롯마다 **썸네일 위에 프로필 원**을 띄우고, 고른 조합에 따라 **썸네일 자리 이미지가 바뀐다**(배경 교체 개념 — 모든 부분집합이 있어야 한다). 사각형 bbox 는 이 자리를 주지 못해 버렸다(중심이 몸통이고, 원본 해상도를 저장하지 않아 비율로 바꿀 수도 없다). 챌린지 주인(`artistSeq`)은 슬롯이 아니다 — 원이 없고 교체되지 않는다(현행 유지). **기존 행은 살린다** — 앵커·선택 화면 키가 `null` 이고 3.7.9 · 3.7.6 으로 채운다(changeSet 0017).

**3.7.1 항목**

```json
{
  "challengeSeq": 11, "artist": { "artistSeq": 3, "name": "카리나" }, "song": { "songSeq": 11, "title": "Supernova", "singerName": "aespa" },
  "thumbnailUrl": "…", "durationMs": 15000, "slotCount": 2, "editedCount": 3,
  "viewCount": 120000, "participantCount": 340, "likeCount": 5600, "commentCount": 210,
  "isDeleted": false, "regDttm": "…"
}
```

**3.7.2 요청**

```json
{
  "artistSeq": 3, "songSeq": 11, "description": "Supernova 챌린지",
  "videoKey": "challenges/3f9c….mp4",
  "durationMs": 15000, "width": 1080, "height": 1920,
  "participants": [
    { "artistSeq": 7, "anchor": { "x": 0.22, "y": 0.36, "r": 0.0995 } },
    { "artistSeq": 9, "anchor": { "x": 0.72, "y": 0.34, "r": 0.0995 } }
  ],
  "editedChallenges": [
    { "removedSlots": [0],    "videoKey": "edited/11/e1.mp4", "initialSceneKey": "edited/11/e1.jpg", "previewImageKey": "edited/11/p1.jpg", "durationMs": 15020 },
    { "removedSlots": [1],    "videoKey": "edited/11/e2.mp4", "initialSceneKey": "edited/11/e2.jpg", "previewImageKey": "edited/11/p2.jpg" },
    { "removedSlots": [0, 1], "videoKey": "edited/11/e3.mp4", "initialSceneKey": "edited/11/e3.jpg", "previewImageKey": "edited/11/p3.jpg" }
  ]
}
```

- `participants` 배열 순서 = **화면 좌→우**. 서버가 `participant_mask = 1 << index` 부여(1, 2, 4 …). `removedSlots` 는 이 배열의 인덱스 집합 → 서버가 `mask = Σ 2^idx` 계산. 어드민은 비트값을 직접 다루지 않는다.
- `anchor` *(618)* = 선택 화면에서 이 슬롯의 **프로필 원이 앉는 자리**. **썸네일 대비 0~1 비율** — `x`·`r` 은 가로, `y` 는 세로 기준. 시안(402×874, 지름 80)의 반지름이 기본값 `40/402 ≈ 0.0995`. 어드민 화면은 원본 프레임(썸네일 시각으로 스크럽한 것)을 **클릭**해 찍고 반지름은 슬라이더. 클라이언트는 렌더 크기를 곱한다 — 중심 `(x·W, y·H)`, 반지름 `r·W`.
- `previewImageKey` *(618)* = 이 조합을 골랐을 때 **썸네일 자리에 보이는 이미지**(AI팀 산출, 배경 교체 개념). mask 당 1장이라 편집본 행에 붙는다. mask 0(무선택)은 행이 없고 챌린지 썸네일이 그 화면이다. 어드민 매트릭스는 영상·첫 장면·선택 화면 **셋을 파일로 올리거나**(presign `EDITED_*`) AI팀이 준 키를 붙인다.
- 검증 순서(전부 통과해야 트랜잭션 진입):
  1. `artistSeq`·`songSeq` 비삭제, `description` 비어있지 않음(DB NOT NULL).
  2. `participants`: 0~4개 **(하드 상한 4 — 기본안)**, 각 `artistSeq` ≠ 메인·서로 다름·비삭제, anchor `0 ≤ x,y ≤ 1 · 0 < r ≤ 0.5`(`ANCHOR_INVALID[i]`).
  3. `editedChallenges` 집합: 개수 = 2ⁿ−1, 각 `removedSlots` 비어있지 않고 인덱스 범위 내·중복 없음, 계산된 마스크 집합 = {1 … 2ⁿ−1} 전체. 부족·중복·초과 → 409 `EDITED_SET_INCOMPLETE`, `data`: `{ "expected": 3, "missingMasks": [3], "duplicateMasks": [], "unknownMasks": [] }`.
  4. `videoKey`·모든 편집본 키·첫 장면 키·선택 화면 키 HEAD → 부재 시 409 `MEDIA_NOT_UPLOADED`, `data`: 부재 키 목록.
  5. *(v0.5)* 폼이 보낸 `durationMs`·`width`·`height` 규칙 검증(§1.4) — **서버 ffmpeg 없음**. 편집본 각 `durationMs` = 원본 ±500ms → 위반 409 `EDITED_DURATION_MISMATCH`. `thumbnail_key` 는 산출물 규약으로 계산해 넣는다(파일은 app-media 가 만든다).
- 처리(트랜잭션): `challenges` INSERT(`video_key` = 업로드한 원본 MP4 키) → `challenge_participants` n행 → `edited_challenges` 2ⁿ−1행 → 커밋 후 **`challenge.created` + `job.media` 발행** *(v0.4)*.
- *(v0.4)* `job.media` 가 처리되면 `video_key` 가 `challenges/{uuid}/master.m3u8` 로 바뀐다.
  **그 전에도 원본 MP4 로 재생된다** — ABR 만 없다(§1.4). 응답의 `videoUrl` 은 그 시점의
  `video_key` 를 그대로 반영하므로 등록 직후에는 MP4 주소가 나올 수 있다.
- n = 0(솔로): `editedChallenges` 는 빈 배열이어야 하며 편집본 없이 등록된다.

**성공 (201)** `data`:

```json
{
  "challengeSeq": 11, "durationMs": 15000, "thumbnailUrl": "https://cdn…/challenges/{uuid}/thumb.jpg",
  "participants": [ { "artistSeq": 7, "participantMask": 1 }, { "artistSeq": 9, "participantMask": 2 } ],
  "editedChallenges": [ { "editedChallengeSeq": 31, "participantMask": 1 }, { "editedChallengeSeq": 32, "participantMask": 2 }, { "editedChallengeSeq": 33, "participantMask": 3 } ]
}
```

**3.7.3 성공 (200)** `data` = 3.7.1 항목 + `description`, `videoUrl`(공개 CDN), `participants[]`(artist 요약(`artistSeq`·`name`·`profileImageUrl` — 상세 화면이 슬롯 원 안에 넣는다)·mask·`anchor{x,y,r}` — 0017 이전 행은 `null`), `editedChallenges[]`(seq·mask·`videoUrl`·`initialSceneUrl`·`previewImageUrl`(0017 이전 행 `null`)·`jobRefCount`), `activeJobCount`(QUEUED/PROCESSING).

**3.7.4 요청** `{ "description": "…", "songSeq": 12 }` — 부분 수정. 원본 영상·메인 아티스트는 불변(요청에 포함되면 400) *(회의 확정 R1 — 원본 교체 금지)*.

**3.7.5 요청** = 3.7.2 의 `participants` + `editedChallenges` — 기존 슬롯·편집본을 하드 삭제하고 재생성(DB §13 규칙). 편집본 중 하나라도 `composite_jobs` 가 참조하면 409 `EDITED_IN_USE`(`data`: 참조 편집본 seq 목록). 검증·응답은 3.7.2 와 동일(원본 재추출 없음).

**3.7.6 요청** `{ "videoKey": "edited/11/e1b.mp4", "initialSceneKey": "edited/11/e1b.jpg", "previewImageKey": "edited/11/p1b.jpg" }` — 부분 수정, 같은 행 키 갱신(`mod_dttm`). HEAD + 길이 검증. **200** `data`: `{ "editedChallengeSeq": 31, "participantMask": 1, "videoUrl": "…", "initialSceneUrl": "…", "previewImageUrl": "…" }`. 이미 발급된 잡은 큐 메시지를 잡 행에서 재구성하므로 다음 합성부터 새 키를 쓴다. `previewImageKey` 는 0017 이전 행(`null`)을 채우는 통로이기도 하다 — 상세 화면의 편집본 행에서 파일을 고르면 presign → 이 API 로 이어진다.

**3.7.7** — 소프트 삭제. **200** `data: null`. 확인 대화상자에 "참여 영상 n건이 앱에서 비노출됨" 안내.

**3.7.8** — 3.7.2 와 같은 본문으로 1~4단계 검증만 수행(ffmpeg 미실행). **200** `data`: `{ "valid": true, "computedMasks": [1,2,3], "issues": [] }` — 화면이 제출 전 매트릭스 완결성을 표시하는 용도.

**3.7.9 요청** *(618)* `{ "x": 0.22, "y": 0.36, "r": 0.0995 }` — 슬롯 하나의 앵커만 같은 행에서 고친다. 마스크·편집본을 건드리지 않으므로 **합성 잡이 참조 중이어도 허용**된다(3.7.5 는 편집본까지 다시 보내야 하고 `EDITED_IN_USE` 로 막혀 0017 이전 행을 채울 수 없다). `artistSeq` 가 그 챌린지의 슬롯이 아니면 400 `PARTICIPANT_INVALID`(`data: ["SLOT_NOT_FOUND"]`). **200** `data` = 3.7.3 상세. 상세 화면은 슬롯 행을 고른 뒤 썸네일을 클릭하면 이 API 를 부른다.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` / `SONG_NOT_FOUND` / `CHALLENGE_NOT_FOUND` / `EDITED_CHALLENGE_NOT_FOUND` | 대상 없음·삭제 |
| 400 | `PARTICIPANT_INVALID` | 중복·anchor 규칙 위반·3.7.9 슬롯 아님(`data` 필드 배열) |
| 409 | `EDITED_SET_INCOMPLETE` | 편집본 조합 누락·중복·초과 |
| 409 | `MEDIA_NOT_UPLOADED` | 키 S3 부재 |
| 400 | `MEDIA_INVALID` | 원본 형식·방향·길이 위반 |
| 409 | `EDITED_DURATION_MISMATCH` | 편집본 길이 ≠ 원본 |
| ~~500~~ | ~~`MEDIA_PROBE_FAILED`~~ | **v0.5 삭제** — core-api 가 ffmpeg 를 돌리지 않는다. 가공 실패는 `dlq.job.media` 로 간다(§8 #19) |
| 409 | `EDITED_IN_USE` | 3.7.5 — 잡 참조 편집본 삭제 불가 |

---
### 3.8 모티프 에셋 `motif_assets` · `motif_asset_prices`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.8.1 | GET | `/motif-assets?artistSeq&isActive&assetType&page&size` | 목록 |
| 3.8.2 | POST | `/motif-assets` | 등록(영상 추출 + KR 가격 원자) |
| 3.8.3 | GET | `/motif-assets/{assetSeq}` | 상세 |
| 3.8.4 | PATCH | `/motif-assets/{assetSeq}` | 표시명·소개·타입·영상 교체 |
| 3.8.5 | PATCH | `/motif-assets/{assetSeq}/active` | 활성/비활성(+가격 연쇄) |
| 3.8.6 | PUT | `/motif-assets/{assetSeq}/prices/{countryCode}` | 국가 가격 upsert(현재 `KR`) |

**3.8.1 항목**

```json
{
  "assetSeq": 21, "artist": { "artistSeq": 3, "name": "카리나", "groupName": "aespa" },
  "title": "9월 신규 에셋", "thumbnailUrl": "…", "durationMs": 9800, "assetType": "NORMAL", "isActive": true,
  "price": { "countryCode": "KR", "amount": "3900.00", "currency": "KRW", "isActive": true },
  "purchaseCount": 42, "resultCount": 131, "regDttm": "…"
}
```

`price` 는 KR 행이 없으면 `null`. `purchaseCount` = `asset_purchases.status='ACTIVE'` 수, `resultCount` = 비삭제 `motif_results` 수(운영 지표).

**3.8.2 요청**

```json
{
  "artistSeq": 3, "title": "9월 신규 에셋", "description": "소개 문구", "assetType": "NORMAL",
  "videoKey": "assets/9c1d….mp4",
  "price": { "amount": "3900.00", "currency": "KRW" }
}
```

- 검증: 아티스트 비삭제, `title` 1~100자, `assetType ∈ NORMAL|PREMIUM|SECRET`, `videoKey` HEAD → ffprobe/ffmpeg(가로·≤20초 **기본안**), `price` 선택 — 있으면 `amount > 0`(`NUMERIC(10,2)`, 문자열 `"3900.00"` → `BigDecimal`), `currency` 3자 대문자(현재 `KRW` 만 허용 **기본안**).
- 처리(트랜잭션): `motif_assets` INSERT(`thumbnail_key`=추출값, `duration_ms`) → `price` 있으면 `motif_asset_prices(asset, KR, amount, currency, is_active=TRUE, reg_usr_seq)` INSERT → 커밋 후 **`job.media` 발행** *(2026-09-17)*. 알림 이벤트는 없다(홈 6.3 은 `motif_assets.reg_dttm` 조회 기반). `job.media` 가 처리되면 `video_key` 가 `assets/{uuid}/master.m3u8` 로 바뀌고 썸네일 파일이 생긴다.

**성공 (201)** `data`: `{ "assetSeq": 21, "durationMs": 9800, "thumbnailUrl": "…" }`

**3.8.4 요청** `{ "title": "…", "description": "…", "assetType": "PREMIUM", "videoKey": "assets/new….mp4" }` — 부분 수정. `videoKey` 포함 시 **`job.media` 재발행** — 교체본은 새 uuid 라 프리픽스가 통째로 새것이고, 산출물 1년 `immutable` 과 부딪히지 않는다(기존 `motif_results`·`composite_jobs` 키는 독립 — 영향 없음). `mod_dttm` 갱신. **200** `data` = 3.8.3.

**3.8.5 요청** `{ "isActive": false }` → 같은 트랜잭션에서 해당 에셋 `motif_asset_prices.is_active=FALSE` 전부(DB §30). `{ "isActive": true }` → 에셋만 활성, 가격은 **자동 재활성 없음** — 응답 `data`: `{ "assetSeq": 21, "isActive": true, "inactivePriceCount": 1 }` 로 화면이 경고 표시.

**3.8.6 요청** `{ "amount": "4900.00", "currency": "KRW", "isActive": true }` — `(asset_seq, country_seq)` UNIQUE upsert. `isActive:true` 전환은 `motif_assets.is_active=TRUE AND countries.is_active=TRUE` 일 때만(DB §31). 변경은 기존 구매·PENDING 스냅샷 불변. **200** `data` = price 객체.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ASSET_NOT_FOUND` / `ARTIST_NOT_FOUND` / `COUNTRY_NOT_FOUND` | 대상 없음 |
| 409 | `MEDIA_NOT_UPLOADED` / 400 `MEDIA_INVALID` | §1.4 *(v0.5 — `MEDIA_PROBE_FAILED` 삭제)* |
| 409 | `PRICE_ACTIVATION_BLOCKED` | 에셋 비활성 또는 국가 비활성 상태에서 가격 활성 시도 |
| 409 | `ASSET_STATE_UNCHANGED` | 3.8.5 — 이미 같은 상태 **(기본안 — 멱등 200 으로 바꿔도 무방)** |

### 3.9 패스 가격 `motif_pass_prices`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.9.1 | GET | `/artists/{artistSeq}/pass-prices` | 아티스트 가격표(전 국가·기간) |
| 3.9.2 | PUT | `/artists/{artistSeq}/pass-prices/{countryCode}/{durationMonths}` | upsert |

**3.9.1 성공 (200)** `data.list[]`: `{ "passPriceSeq": 5, "countryCode": "KR", "durationMonths": 1, "amount": "9900.00", "currency": "KRW", "isActive": true, "activePassCount": 120, "modDttm": null }`

**3.9.2 요청** `{ "amount": "9900.00", "currency": "KRW", "isActive": true }` — `(artist, country, duration)` UNIQUE upsert. `isActive:true` 는 국가 활성 AND 아티스트 비삭제일 때만(DB §32). 기존 ACTIVE PASS 불변. **200** `data` = 항목.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` / `COUNTRY_NOT_FOUND` | — |
| 400 | `INVALID_PARAMETER` | `durationMonths ≤ 0`, `amount ≤ 0` |
| 409 | `PRICE_ACTIVATION_BLOCKED` | 국가 비활성·아티스트 삭제 |

### 3.10 공지 `notices` · `notice_images`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.10.1 | GET | `/notices?artistSeq&includeDeleted&page&size` | 목록 |
| 3.10.2 | POST | `/notices` | 작성 → `notice.created` |
| 3.10.3 | GET | `/notices/{noticeSeq}` | 상세 |
| 3.10.4 | PATCH | `/notices/{noticeSeq}` | 본문·이미지 교체(재발송 없음) |
| 3.10.5 | DELETE | `/notices/{noticeSeq}` | 소프트 삭제 |

**3.10.2 요청** `{ "artistSeq": 3, "content": "컴백 소식 전체 본문…", "imageKeys": ["notices/a.jpg", "notices/b.jpg"] }`

- `content` 비어있지 않음(길이 상한 앱 `@Size(max=5000)` **기본안**), `imageKeys` 0~10장 **기본안**·전부 HEAD. 배열 인덱스 = `display_order`.
- 처리(트랜잭션): `notices` INSERT → `notice_images` n행 → 커밋 후 `notice.created`.

**성공 (201)** `data`: `{ "noticeSeq": 5 }`

**3.10.1 항목** `{ "noticeSeq": 5, "artist": {…}, "contentPreview": "앞 80자…", "imageCount": 2, "likeCount": 200, "commentCount": 14, "isDeleted": false, "regDttm": "…" }` — 상세(3.10.3)는 `content` 전체 + `images[{imageUrl, displayOrder}]`.

**3.10.4 요청** `{ "content": "…", "imageKeys": ["notices/c.jpg"] }` — `imageKeys` 가 오면 **전체 교체**(기존 행 하드 삭제 → 재생성, DB §28), 생략하면 이미지 불변. `mod_dttm` 갱신, 이벤트 없음.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `NOTICE_NOT_FOUND` / `ARTIST_NOT_FOUND` | — |
| 409 | `IMAGE_NOT_UPLOADED` | 이미지 키 부재(`data`: 키 목록) |

### 3.11 숍 배너 `shop_banners`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.11.1 | GET | `/shop-banners` | 전량(비활성 포함), `display_order ASC` |
| 3.11.2 | POST | `/shop-banners` | 등록 |
| 3.11.3 | PATCH | `/shop-banners/{shopBannerSeq}` | 이미지 교체·활성 |
| 3.11.4 | PUT | `/shop-banners/order` | 전체 재정렬 |
| 3.11.5 | DELETE | `/shop-banners/{shopBannerSeq}` | 하드 삭제 **(기본안 — 참조 없음)** |

**3.11.2 요청** `{ "imageKey": "banners/….jpg", "displayOrder": 3 }` — `displayOrder` 생략 시 `MAX+1`. HEAD 검증. **201** `{ "shopBannerSeq": 1 }`. 중복 순서 → 409 `BANNER_ORDER_DUPLICATE`.

**3.11.4 요청** `{ "orderedBannerSeqs": [3, 1, 2] }` — 전체 목록을 새 순서로. 구현: 트랜잭션에서 `display_order += 10000` 일괄 → 배열 순서대로 0..n−1 재부여(UNIQUE 충돌 회피). 누락·미존재 seq → 400. **200** `data` = 3.11.1 목록.

| HTTP | code | 조건 |
|---|---|---|
| 404 | `BANNER_NOT_FOUND` | — |
| 409 | `BANNER_ORDER_DUPLICATE` | 3.11.2 |
| 409 | `IMAGE_NOT_UPLOADED` | — |

### 3.12 아티스트 활동 대행 — `/as-artist/{artistSeq}/**` ★★

경로 접두 `/api/v1/admin/as-artist/{artistSeq}` 뒤에 **사용자 API 와 같은 하위 경로**를 붙인다. 컨트롤러는 ① `artistSeq` → 비삭제 아티스트의 `user_seq` 조회 ② 사용자 API 와 동일한 서비스 메서드를 그 `user_seq` 를 액터로 호출 ③ 구조화 로그. **요청·응답·실패 규약은 대응 사용자 API 와 동일**(API 명세 §7·§8·§9·§10) — 아래는 매핑표만 둔다.

| 대행 경로(접두 뒤) | 대응 사용자 API | 비고 |
|---|---|---|
| `POST/DELETE /challenges/{seq}/like` | §7.3 | 204 |
| `POST/DELETE /videos/{seq}/like` | §8.3 | 204, `has_artist_like` 재계산 |
| `POST/DELETE /notices/{seq}/like` | §10.2 | 204 |
| `GET /challenges/{seq}/comments` `GET /videos/{seq}/comments` `GET /notices/{seq}/comments` | §9.1 | 정렬의 "내 댓글" = 아티스트 본인 댓글 |
| `GET /comments/{type}/{commentSeq}/replies` | §9.2 | — |
| `POST /challenges/{seq}/comments` `POST /videos/{seq}/comments` `POST /notices/{seq}/comments` | §9.3 | 201, `comment.created`/`reply.created` → FCM(본인 제외) |
| `DELETE /comments/{type}/{commentSeq}` | §9.4 | 아티스트 본인 댓글만(타인 댓글은 403 — 타인 댓글 삭제는 신고 처리 3.13 로) |
| `POST/DELETE /comments/{type}/{commentSeq}/like` | §9.5 | 204 |

**어드민 전용 분기**

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ARTIST_NOT_FOUND` | `artistSeq` 미존재·삭제 → 대행 불가 |
| 404 | `VIDEO_NOT_FOUND` | PRIVATE 참여 영상 — 대행 대상 아님 **(기본안 — 사용자 API 존재 은닉 규칙 그대로)** |

**탐색용 조회**(대행 컨텍스트 무관, 어드민 관점):

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.12.1 | GET | `/videos?challengeSeq&userSeq&visibility&includeDeleted&sort=recommended|latest&page&size` | 참여 영상 목록(어드민은 PRIVATE·삭제도 열람 — 신고·회원 상세용). 항목 = API §8.2 + `visibility`·`isDeleted`·`reportCount` |
| 3.12.2 | GET | `/videos/{videoSeq}` | 단건(PRIVATE 는 signed URL) |

### 3.13 신고 `reports`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.13.1 | GET | `/reports?status&targetType&page&size` | 목록(접수순) |
| 3.13.2 | GET | `/reports/{reportSeq}` | 상세 |
| 3.13.3 | POST | `/reports/{reportSeq}/process` | 인용/기각 |

**3.13.1 항목**

```json
{
  "reportSeq": 501, "targetType": "VIDEO", "reasonCode": "SPAM", "status": "RECEIVED",
  "reporter": { "userSeq": 12, "nickname": "팬123" },
  "target": {
    "videoSeq": 88, "thumbnailUrl": "signed…", "videoUrl": "signed…", "author": { "userSeq": 5, "nickname": "abc" },
    "isDeleted": false, "sameTargetReportCount": 3
  },
  "regDttm": "…", "processedBy": null, "processedDttm": null
}
```

`targetType ∈ VIDEO | CHALLENGE_COMMENT | VIDEO_COMMENT | NOTICE_COMMENT`(reports 4대상 컬럼과 1:1). 댓글 대상은 `target = { commentSeq, content, author, parentContent?, isDeleted, sameTargetReportCount }`. `reasonCode` 는 코드표 미결(API §16 #4) — 값 그대로.

**3.13.3 요청** `{ "decision": "ACCEPTED" }` 또는 `{ "decision": "REJECTED" }`

- 처리(트랜잭션): `status`·`processed_usr_seq=JWT.usr`·`processed_dttm=now()`·`mod_dttm`. ACCEPTED 면 대상 소프트 삭제 + 후처리(참여영상: 최초 삭제 시 `participant_count −1` / 댓글: 공통 삭제 정책 — 답글 연쇄·좋아요 하드 삭제·`comment_count` 실감소분) — 대상이 이미 삭제되어 있으면 상태만 전이(멱등, DB §26).
- 참여영상 ACCEPTED 시 `has_artist_*` 는 영상 자체가 비노출이라 재계산 불요.

**성공 (200)** `data`: `{ "reportSeq": 501, "status": "ACCEPTED", "targetDeleted": true, "sideEffects": { "participantCountDelta": -1, "deletedCommentCount": 0 } }`

| HTTP | code | 조건 |
|---|---|---|
| 404 | `REPORT_NOT_FOUND` | — |
| 409 | `REPORT_ALREADY_PROCESSED` | `RECEIVED` 아님 |

### 3.14 회원 `users` · `user_accounts`

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.14.1 | GET | `/users?q&userSeq&role&accountStatus&page&size` | 검색(`q` = 닉네임 부분 일치) |
| 3.14.2 | GET | `/users/{userSeq}` | 상세 |
| 3.14.3 | POST | `/users/{userSeq}/suspend` | 정지 |
| 3.14.4 | POST | `/users/{userSeq}/unsuspend` | 해제 |

**3.14.2 성공 (200)** `data`:

```json
{
  "userSeq": 5, "nickname": "팬123", "role": "USER", "profileImageUrl": "…",
  "accountStatus": "ACTIVE", "isDeleted": false, "regDttm": "…",
  "stats": { "videoCount": 12, "subscriptionCount": 4, "activePurchaseCount": 2, "activePassCount": 1, "reportedCount": 1, "reportCount": 0 },
  "activePasses": [ { "artistSeq": 3, "artistName": "카리나", "expiresDttm": "…" } ]
}
```

`reportedCount` = 이 사용자의 콘텐츠가 신고된 건수, `reportCount` = 이 사용자가 신고한 건수.

**3.14.3 요청** `{ "reason": "욕설 반복" }` — `reason` 은 로그 전용(컬럼 없음).

- 처리(트랜잭션): `user_accounts.account_status='SUSPENDED'`·`mod_dttm` → 해당 사용자 `refresh_tokens WHERE revoked_dttm IS NULL` 전부 `revoked_dttm=now()` → 커밋 후 `user.suspended` 발행(알림 서비스가 토큰 비활성). 이후 로그인·리프레시는 403 `ACCOUNT_SUSPENDED`(API §1.5).
- `role ∈ ARTIST|ADMIN` 은 400 `INVALID_PARAMETER`(정지 대상 아님).

**성공 (200)** `data`: `{ "userSeq": 5, "accountStatus": "SUSPENDED", "revokedTokenCount": 2 }`

**3.14.4** — `ACTIVE` 전이만. 토큰 복구 없음. **200** `data`: `{ "userSeq": 5, "accountStatus": "ACTIVE" }`

| HTTP | code | 조건 |
|---|---|---|
| 404 | `USER_NOT_FOUND` | 미존재(삭제 사용자는 어드민에게는 보임 — `isDeleted` 로 표시) |
| 409 | `USER_ALREADY_SUSPENDED` / `USER_NOT_SUSPENDED` | 상태 충돌 |

### 3.15 주문·환불 `orders` *(Phase 후반)*

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.15.1 | GET | `/orders?userSeq&status&productType&from&to&page&size` | 목록 |
| 3.15.2 | GET | `/orders/{orderUuid}` | 상세(토스 응답 요약 포함) |
| 3.15.3 | POST | `/orders/{orderUuid}/refund` | 전액 환불 |

**3.15.1 항목** `{ "orderUuid": "…", "user": {…}, "productType": "ASSET", "product": { "assetSeq": 21, "title": "…" } | { "artistSeq": 3, "passDurationMonths": 1 }, "amount": "3900.00", "currency": "KRW", "status": "PAID", "paymentProvider": "TOSS", "approvedDttm": "…", "regDttm": "…" }`

**3.15.3 요청** `{ "reason": "고객 요청" }`

- 처리: ① `status='PAID'` 확인 ② 토스 결제 취소 API(`POST /v1/payments/{paymentKey}/cancel`, `cancelReason`) 호출 — 실패 시 상태 불변, 502 `PAYMENT_CANCEL_FAILED`(`data` 에 토스 사유) ③ 성공 시 트랜잭션: `orders.status='REFUNDED'`·`mod_dttm` + ASSET 이면 `asset_purchases.status='REFUNDED'`, PASS 면 `motif_passes.status='REFUNDED'`(권한 즉시 종료, DB §34·§35). 부분 환불 없음.
- 환불 후 사용자의 기존 `motif_results` 는 유지 **(기본안 — 운영 정책 §8)**.

**성공 (200)** `data`: `{ "orderUuid": "…", "status": "REFUNDED", "refundedDttm": "…" }`

| HTTP | code | 조건 |
|---|---|---|
| 404 | `ORDER_NOT_FOUND` | — |
| 409 | `ORDER_NOT_REFUNDABLE` | `PAID` 아님 |
| 502 | `PAYMENT_CANCEL_FAILED` | 토스 취소 실패 |

### 3.16 합성 잡·배치 운영 *(Phase 후반)*

| # | Method | Path | 설명 |
|---|---|---|---|
| 3.16.1 | GET | `/composite-jobs?status&jobType&userSeq&from&to&page&size` | 목록 |
| 3.16.2 | GET | `/composite-jobs/{jobUuid}` | 상세(원본·결과 signed URL, 편집본 참조) |
| 3.16.3 | POST | `/composite-jobs/{jobUuid}/requeue` | FAILED 재큐(= API §13.4 로직, 어드민 권한) |
| 3.16.4 | POST | `/batch/{batchName}/run` | 배치 즉시 실행 |
| 3.16.5 | GET | `/batch/status` | 배치별 마지막 실행·락 상태(ShedLock 테이블 조회) |

**3.16.1 항목** `{ "jobUuid": "…", "jobType": "CHALLENGE", "status": "FAILED", "attempt": 2, "failCode": "SEGMENT_FAIL", "user": {…}, "target": { "challengeSeq": 11 } | { "assetSeq": 21 }, "editedChallengeSeq": 31, "processingMs": 48210, "regDttm": "…", "modDttm": "…" }`

**3.16.3** — `FAILED → QUEUED`, `fail_code=NULL`, `attempt+1`, 잡 행에서 메시지 재구성·`job.composite` 발행(priority 잡 타입 기준). 원본 `raw/` 부재 시 410 `RAW_VIDEO_EXPIRED`. **202** `data`: `{ "jobUuid": "…", "status": "QUEUED", "attempt": 3 }`

**3.16.4** `batchName ∈ ranking-snapshot | view-flush | counter-reconcile | pass-order-expire | job-sweep`. 락 획득 실패 → 409 `BATCH_RUNNING`. **202** `data`: `{ "batchName": "counter-reconcile", "startedDttm": "…" }`

| HTTP | code | 조건 |
|---|---|---|
| 404 | `JOB_NOT_FOUND` / `BATCH_NOT_FOUND` | — |
| 409 | `JOB_NOT_RETRYABLE` | `FAILED` 아님 |
| 410 | `RAW_VIDEO_EXPIRED` | 원본 삭제 |
| 409 | `BATCH_RUNNING` | 락 보유 중 |

---
## 4. 이벤트 계약 (module-event 추가·변경분)

공통 봉투 `{ "eventId": "uuid", "occurredDttm": "…", "type": "…", "payload": {…} }`. 발행은 `AFTER_COMMIT`. 컨슈머 멱등 키 = `eventId` — `notifications` 테이블이 삭제되어 로그 테이블이 없으므로 **Redis `SETNX evt:{eventId}` TTL 24h** 로 중복 소비를 막는다 **(기본안 — 설계서 "notification_log unique" 문구 대체)**.

| routing key | 발행 | payload | 컨슈머 → 동작 |
|---|---|---|---|
| `challenge.created` | 3.7.2 커밋 후 | `{ challengeSeq, artistSeq, artistName, songTitle, thumbnailKey }` | `evt.notification`: `subscriptions WHERE artist_seq=?` 조회(읽기 전용 GRANT) → 활성 `device_tokens` 에 FCM data-only `{ type:"NEW_CHALLENGE", title:"{artistName}의 새 챌린지", body:"{songTitle}", challengeSeq, sentDttm }` |
| `notice.created` | 3.10.2 커밋 후 | `{ noticeSeq, artistSeq, artistName, contentPreview(60자) }` | `evt.notification`: 팬아웃 → `{ type:"NEW_NOTICE", title:"{artistName} 공지", body:contentPreview, noticeSeq, sentDttm }` |
| `user.suspended` **(신설)** | 3.14.3 커밋 후 | `{ userSeq }` | `evt.notification`: `device_tokens SET is_active=FALSE WHERE user_seq=?` |
| `comment.created` / `reply.created` | 3.12 대행(사용자 API 와 동일 발행점) | 기존 계약 | 기존 동작 — 액터가 아티스트라는 차이만 |
| `video.liked` 등 랭킹 이벤트 | 3.12 대행 | 기존 계약 | `evt.ranking` 만. FCM 없음 |
| `job.media` *(v0.4 부활)* | 3.7.2 커밋 후 · 합성 완료 후 | `{ mediaJobUuid, sourceType, sourceSeq, sourceKey, outputPrefix }` | **app-media**: S3 원본 내려받기 → 렌디션 2종(720p/480p) → HLS(fMP4 + 바이트레인지) → 썸네일 → S3 업로드 → `video_key`(또는 `result_video_key`)를 `master.m3u8` 경로로 갱신. 멱등 키는 대상 행의 키가 이미 마스터 재생목록을 가리키는지로 본다 |

`job.media` 주석 *(v0.4)* — `sourceType` 은 `CHALLENGE_ORIGIN`(어드민 등록) | `COMPOSITE_RESULT`(합성 결과) | `ASSET_PREVIEW`(모티프 에셋, 2026-09-17 추가 — 계약이 additive-only 라 상수만 늘렸다)
이고 `sourceSeq` 는 갱신 대상 행의 PK(`challenges.challenge_seq` 또는 `composite_jobs.job_seq`)다.
`outputPrefix` 는 §1.4 산출물 프리픽스 규약으로 만든다. **v0.3 에서 컷한 인제스트 워커와는
다른 작업이다** — 길이·썸네일은 여전히 동기다(§1.4).

팬아웃 구현 노트: 구독자 수천 명 × 기기 토큰이면 FCM 멀티캐스트(500개 단위 배치)로 발송하고, 프로세스 분리 전(동일 배포)에는 `@Async` 스레드풀 1개로 API 응답을 막지 않는다. 지침 §7 미결 #1(팬아웃 조회 방식)은 DB v0.15 §39 "알림 서비스가 `subscriptions` 직접 조회"로 **종결된 것으로 간주**하고 본 문서는 그 방식을 따른다.

---

## 5. 어드민 에러 코드 총괄

공통(API §1.4) + 사용자 도메인 코드(대행에서 그대로 사용) + 아래 어드민 전용. `module-common` 에러 enum 에 추가한다.

| code | HTTP | 사용처 |
|---|---|---|
| `ADMIN_LOGIN_FAILED` | 401 | 3.1.1 |
| `ADMIN_INACTIVE` | 403 | 3.1.1 |
| `MEDIA_TYPE_NOT_ALLOWED` | 400 | 3.2 |
| `MEDIA_NOT_UPLOADED` | 409 | 영상 키 S3 부재(3.7·3.8) — 이미지는 기존 `IMAGE_NOT_UPLOADED` |
| `MEDIA_INVALID` | 400 | 형식·방향·길이 위반 |
| ~~`MEDIA_PROBE_FAILED`~~ | ~~500~~ | **v0.5 삭제** — core-api 가 ffmpeg 를 돌리지 않는다 |
| `COMPANY_NOT_FOUND` / `GROUP_NOT_FOUND` / `SONG_NOT_FOUND` / `COUNTRY_NOT_FOUND` / `BANNER_NOT_FOUND` / `REPORT_NOT_FOUND` / `BATCH_NOT_FOUND` | 404 | 각 리소스 |
| `COMPANY_NAME_DUPLICATE` | 409 | 3.3.2/3 |
| `COMPANY_IN_USE` / `GROUP_IN_USE` | 409 | 3.3.4 / 3.4.4 |
| `ARTIST_COMPANY_LOCKED` | 409 | 3.5.4 |
| `ARTIST_ALREADY_DELETED` | 409 | 3.5.5 |
| `ALIAS_NOT_FOUND` | 404 | 3.4.7/8 · 3.5.8/9 — 별칭 없음, 또는 경로의 아티스트·그룹 소유가 아님 **(v0.6 신설)** |
| `PARTICIPANT_INVALID` | 400 | 3.7.2/5 |
| `EDITED_SET_INCOMPLETE` | 409 | 3.7.2/5 |
| `EDITED_DURATION_MISMATCH` | 409 | 3.7.2/5/6 |
| `EDITED_IN_USE` | 409 | 3.7.5 |
| `PRICE_ACTIVATION_BLOCKED` | 409 | 3.8.6 / 3.9.2 |
| `ASSET_STATE_UNCHANGED` | 409 | 3.8.5 |
| `BANNER_ORDER_DUPLICATE` | 409 | 3.11.2 |
| `REPORT_ALREADY_PROCESSED` | 409 | 3.13.3 |
| `USER_ALREADY_SUSPENDED` / `USER_NOT_SUSPENDED` | 409 | 3.14.3/4 |
| `ORDER_NOT_REFUNDABLE` | 409 | 3.15.3 |
| `PAYMENT_CANCEL_FAILED` | 502 | 3.15.3 |
| `BATCH_RUNNING` | 409 | 3.16.4 |

---

## 6. DB v0.16 변경 요청 (본 문서 파급)

| # | 대상 | 변경 | 근거 | 상태 |
|---|---|---|---|---|
| 1 | `admins.user_seq` | `INTEGER NOT NULL UNIQUE FK → users` 추가. 기존 행 있으면 백필 후 NOT NULL. §5 서술 "관계를 두지 않는다" → "1:1 연결" 정정 | 감사 주체 확정(§1.3) | **(회의 확정 — v0.16 반영 요청)** |
| 2 | ~~`songs.mod_dttm`, `songs.mod_usr_seq`~~ | **철회** — 컬럼 추가 금지, 곡은 불변(등록만). DB 변경 없음 | ADMIN-05 | **(회의 확정 R1 — 추가 금지)** |
| 3 | `shop_banners` | 하드 삭제 허용 서술 추가(참조 테이블 없음) | 3.11.5 | (기본안) |
| 4 | `admin_action_logs` | **미도입**(대행 수행자는 앱 로그) — 기록만 | §1.3 | (회의 확정) |
| 5 | `composite_jobs.edited_challenge_seq` NULL 정책(구현 단계 확정 사항 #8) | **NULL 허용 유지 권고** — 슬롯 0개(솔로) 챌린지는 NULL 경로가 유일한 참여 방식 | ADMIN-06 | 팀 논의 근거 제출 |
| 6 | 구현 단계 확정 사항 #9(bbox·mask 산출 주체) | **종결**: AI팀 산출·S3 직접 업로드, 어드민 수기 입력, 서버 완결성 검증 | ADMIN-06 | (회의 확정) |
| 7 | `users.nickname` CHECK | 아티스트명 1~50자 자유 형식은 현행 CHECK(`1~50`)로 충족 — 변경 없음. USER 2~20자 규칙은 앱 검증 유지 | ADMIN-04 | 확인 |

---

## 7. 타 문서 파급 (정합 노트)

| 문서 | 반영 내용 |
|---|---|
| **아키텍처 설계서 v0.2 → v0.3** | ⓪ **SCG 제거** — Traefik 단일 앞단(TLS·라우팅·rateLimit), JWT 검증·어드민 IP 제한은 core-api 필터, 구성도 v0.4 의 SCG 상자 삭제 ① 인제스트 워커·EC2 워커 노드(`workload=media`) **삭제**(컷). **`job.media` 는 v0.4 에서 렌디션·HLS 전용으로 부활** ② **v0.5: 원칙 ① 의 예외 삭제** — core-api 에 ffmpeg 없음, 세마포어·메모리 피크 반영 불필요. 길이·해상도는 업로드 화면이 보내고 썸네일·렌디션·HLS 는 전부 app-media ③ 어드민 = Thymeleaf + `/api/v1/admin` JSON, 쿠키+Bearer, `typ=ADMIN` 클레임 ④ `user.suspended` 이벤트 추가, `notice.created`/`challenge.created` payload 확정 ⑤ 컨슈머 멱등 = Redis SETNX(notification_log 문구 대체) ⑥ §5.4 표 `subscriber_replica` 잔존 문구 삭제 ⑦ 감사 로그 파일+PVC(관측 도구 전 단계) |
| **인프라(지침 §5·§6)** | S3 프리픽스 신설 `challenges/`·`edited/`·`artists/`·`banners/` · IAM `motif-gpu-worker` GET 에 `challenges/*`·`edited/*` 추가 · `motif-ai-editor`(PUT/GET `edited/*`) 신설 · Traefik `forwardedHeaders.trustedIPs`·`rateLimit` 미들웨어(SCG 역할 이관) · 어드민 허용 CIDR configmap · 감사 로그 **PVC(local-path)** 마운트 · EC2 워커 노드 조인 단계(§6 9번) 삭제 · k8s core-api 메모리 limit 에 ffmpeg 피크 반영 |
| **API 명세 v0.11 §16.1** | 어드민 반영 대기 3건 종결 — ① 전조합 편집본(3.7.2) ② `shop_banners` 관리(3.11) ③ 에셋 `title/description` 분리(3.8.2). §12.3 "환불은 어드민 범위" → 3.15.3. **§1.1 정정**: "Traefik → SCG → Core API" → "Traefik → Core API"(JWT 검증·레이트리밋 주체 변경), `RATE_LIMITED` 429 발원지 = Traefik |
| **ADR-0010 (영상 전달)** *(v0.4 신설)* | 렌디션 2종(720p/480p)·HLS·재생 중 화질 전환 채택. 워커 인코딩 요구사항(GOP 2초 고정·화질 간 정렬, 비트레이트 상한 `-maxrate`/`-bufsize` 필수, faststart 유지). 비공개 영상 URL = CloudFront **서명 쿠키**(서명 URL 은 HLS 조각 요청에 서명이 따라붙지 않아 403) |
| **API 명세 v0.11 → v0.12** *(v0.4 신설)* | `videoUrl` 이 단일 MP4 → HLS 마스터 재생목록. §1.7 보호 등급을 **signed URL + core-api 재생목록 리라이팅** 으로 확정. 산출물 키 규약 표 신설 |
| **모듈 구성** *(v0.4 신설 · v0.5 갱신)* | `app-media` 신설(`job.media` 소비, 포트 8083/관리 9083). Jenkinsfile 빌드·이미지 추가, k8s 매니페스트 신설 필요. **core-api Dockerfile 은 그대로 — ffmpeg 를 넣지 않는다**. 배치는 GPU 서버 대여 전까지 SSAFY 노드 — `resources.limits.cpu: 1` 권장 |
| **DB 명세 v0.15 → v0.16** | §6 표 1~3 |
| **DB 명세 → v0.17** *(v0.6 신설)* | `artist_aliases`·`group_aliases` 신설(§41·§42) + GIN 2개. 반영 완료 — 본 문서가 요청하는 잔여 변경 없음 |
| **API 명세 v0.12 → v0.13** *(v0.6 신설)* | §5.2 를 별칭 검색으로 재정의하고 §5.9 자동완성 삭제. 어드민 §3.4.5~8·§3.5.6~9 가 그 별칭을 채우는 유일한 경로다 — 둘은 같이 나간다 |
| **기능 명세서** | 어드민 대행의 좋아요는 알림 미발송(기존 규칙 재확인), 챌린지 숨김 개념 없음(삭제만) |
| **구현 계획서 v0.1** | 어드민 최소 세트(3.1~3.10)는 **Phase 1 말까지** 필요 — 스텁 워커 시연 전에 챌린지·에셋 실데이터가 있어야 함. 3.11~3.14 Phase 2~3, 3.15~3.16 Phase 4(컷 라인 뒤) |
| **모노레포** | `app-core-api/src/main/java/…/admin/{web,api,service}` + `resources/templates/admin/**`. 어드민 서비스는 `domain-core` 리포지토리와 기존 도메인 서비스만 의존 — `domain-notif` 의존 금지(토큰 비활성은 이벤트) |

---

## 8. 확인 필요 / 미결 안건 (다음 회의)

| # | 항목 | 관련 | 기본안 |
|---|---|---|---|
| 1 | **아티스트 좋아요 FCM** — 현재 확정 규칙은 "좋아요 알림 없음". 대행 좋아요("카리나가 내 영상에 ♥")를 푸시할지 | ADMIN-11, DB §39 | 현행 유지(없음). 도입 시 `video.liked` 에 `actorIsArtist` 추가 → evt.notification 구독 |
| 2 | 대행 대상에 PRIVATE 참여 영상 포함 여부 | 3.12 | 제외(존재 은닉 규칙) |
| 3 | 영상 검증 기준 — 챌린지 세로·≤60초, 에셋 가로·≤20초, 편집본 ±500ms 검증 포함 여부(근거는 §1.4 3항 "왜 두는가") | §1.4, 3.7 | 포함(수치는 설정값) |
| 4 | 슬롯 하드 상한 4(편집본 15개) | 3.7.2 | 4 |
| 5 | ~~챌린지 원본 영상 교체 금지~~ — **종결(R1)**: 금지 확정, 삭제 후 재등록 | 3.7.4 | 확정 |
| 6 | ~~곡 수정 허용 + `songs` mod 컬럼 추가~~ — **종결(R1)**: 컬럼 추가 금지 → 곡 불변(수정 API 삭제), 오류는 새 곡 등록 + 재연결 | ADMIN-05, §6 #2 | 확정 |
| 7 | 에셋 가격 없이 등록 허용(`purchasable:false` 노출) | 3.8.2 | 허용 |
| 8 | 어드민 세션 12h·refresh 없음·로그인 실패 잠금 미도입 | §1.2 | 그대로 |
| 9 | ~~어드민 경로 비허용 IP 응답~~ — **종결(R1)**: 404, core-api `AdminIpFilter` 가 담당(Traefik 는 403 만 가능) | §1.1 | 확정 |
| 10 | ~~대행 로그 보존~~ — **종결(R1)**: JSON 파일 appender + PVC, 관측 도구 도입 시 수집 대상. 보존일(90일)만 미확정 | §1.3 | 확정(보존일 기본안) |
| 11 | 회원 삭제(`is_deleted`) 어드민 기능 — 현재 정지만 | ADMIN-13 | 미도입 |
| 12 | 환불 시 기존 결과물(`motif_results`) 유지 여부 | 3.15.3 | 유지 |
| 13 | `reports.reason_code` 코드표 | 3.13 | API §16 #4 와 공동 대기 |
| 14 | 삭제 아티스트의 `subscriptions` 행 처리(유지+필터 vs 하드 삭제) | ADMIN-04 | 유지+필터 |
| 15 | 에셋 활성/비활성 멱등 처리(409 vs 200) | 3.8.5 | 409 |
| 16 | 지침 §7 미결 #1(팬아웃 조회 방식) — DB §39 "직접 조회"로 종결 처리 확인 | §4 | 종결 |
| 17 | **모티프 에셋 미리보기(`previewVideoUrl`)도 HLS 로 할지** — 가로 ≤20초 짧은 영상이고 스와이프 피드가 아니라 숍 화면에서 재생된다 *(v0.4 신설)* | 3.8, API §11.1 | ~~MP4 유지~~ → **HLS**. *(2026-09-17 뒤집음, S15P21D104-398)* 팀 결정이 "앱에서 재생되는 모든 영상은 HLS" 로 정리됐다. 뒤집은 결정적 이유는 비용이 아니라 **구멍**이다 — MP4 유지는 곧 `job.media` 를 발행하지 않는다는 뜻이었고, 그래서 에셋 썸네일이 영영 404 였다. 사다리를 짧은 변 기준으로 일반화해 가로도 같은 파라미터로 탄다(ADR-0010 D1) |
| 18 | **`app-media` 배치 위치** — GPU 서버 대여 후 그쪽으로 옮길지, SSAFY 노드에 둘지 *(v0.4 신설)* | §1.4 | GPU 서버로 이전. 합성 결과가 그 머신에 있어 S3 왕복(잡당 ~60MB)이 사라지고 core-api 와 CPU 경합도 없다 |
| 20 | **길이·해상도를 업로드 화면이 보내는 것** — 개발자 도구로 조작 가능하다 *(v0.5 신설)* | §1.4 | 허용. 어드민은 테일넷 한정·시드 계정·팀원 수 명이고, app-media 가 실제 영상을 읽으므로 **실측값으로 정정하는 후속 갱신**을 넣을 수 있다(기본안: 넣지 않음, 필요해지면 추가) |
| 19 | **챌린지 원본 HLS 가공 실패 시 처리** — 재큐만 할지, 어드민 화면에 상태를 노출할지 *(v0.4 신설)* | 3.7, §1.4 | 재큐(DLQ `dlq.job.media`). 가공 전에도 MP4 로 재생되므로 사용자 영향이 없어 화면 노출은 후순위 |

---

## 9. 발표 Q&A 방어 멘트

| 예상 질문 | 답변 골자 | 패턴 어휘 |
|---|---|---|
| 아티스트가 직접 안 쓰는데 왜 계정을 만드나 | 팬 앱의 "아티스트 댓글 상단 고정·♥ 표시·정렬 가중"이 전부 `users.role='ARTIST'` 행 기준으로 동작 — 어드민이 그 계정으로 **대행**하면 특별 코드 없이 자동 반영. 로그인 수단(OAuth·계정)은 만들지 않아 탈취 표면 0 | Act-As(대행) + Audit Log |
| 어드민이 서버에 영상을 올리면 원칙 ①(바이트 미통과) 위반 아닌가 | 업로드는 여전히 브라우저→S3 presigned. 서버는 메타 추출을 위해 시킹으로 일부만 읽음. 어드민 트래픽은 총 수십 건이라 비동기 워커+스테이징 테이블보다 **동기가 단순·안전** — 문서에 의도적 예외로 명시 | Valet Key, Claim-Check |
| 편집본이 빠지면 사용자는 어떻게 되나 | 등록 API 가 2ⁿ−1 조합 완결성을 검증하고 **전부 유효할 때만** 커밋(all-or-nothing) — 불완전한 챌린지가 존재할 수 없음. 사용자 API 의 404 는 방어선 | 원자 트랜잭션, Fail-fast validation |
| 어드민 페이지 보안은 | Traefik 뒤 core-api `AdminIpFilter` 로 테일넷/팀 IP 한정 + 어드민 전용 JWT(서명키·`typ` 분리) + HttpOnly·SameSite=Strict 쿠키 + 감사 컬럼·감사 로그 파일. 공개 인터넷에서는 경로 자체가 404 | Citadel, Defense in depth |
| 회원 정지 시 알림 토큰은 왜 이벤트로 끄나 | `device_tokens` 소유권이 알림 서비스(스키마 격리) — 코어는 HTTP 도 직접 UPDATE 도 못 함. 이벤트로 통보해 소유권 경계를 지킴 | 소유권 경계(멀티모듈), EDA |
| 어드민이 잘못 눌러 대행하면 | 아티스트 행위로 기록되지만 감사 로그 파일에 어드민·시각·대상이 남고, 댓글 삭제·좋아요 취소로 즉시 되돌릴 수 있음 | Compensating action |
| 게이트웨이(SCG) 없이 인증은 어디서 하나 | Traefik 는 TLS·라우팅·레이트리밋만, JWT 검증·IP 제한은 core-api 필터 — 서비스가 코어 하나라 게이트웨이 계층은 램만 쓰는 중복이었음. 알림 서비스 분리 시에도 Traefik 경로 라우팅으로 충분 | YAGNI, 단일 진입점 = Traefik |

---

## 부록 A. 챌린지 등록 화면 시퀀스 (Thymeleaf + JS)

```
[어드민 브라우저]                    [core-api /api/v1/admin]            [S3]
 1. 원본 파일 선택 ─── POST /media/presign {CHALLENGE_VIDEO} ─▶ uploadUrl, objectKey
 2. PUT 파일 ──────────────────────────────────────────────────────▶ challenges/{uuid}.mp4
 3. 슬롯 n개 입력(좌→우, bbox) → JS 가 2ⁿ−1 조합 표 생성
 4. 조합마다 AI팀이 준 edited/{…}.mp4 · .jpg 키 입력
 5. POST /challenges/validate ─▶ computedMasks, issues (표에 누락 표시)
 6. POST /challenges ─▶ HEAD ×(1+2·(2ⁿ−1)) → ffprobe/ffmpeg → INSERT ×(1+n+2ⁿ−1) → 201
 7. AFTER_COMMIT challenge.created ─▶ RabbitMQ ─▶ 알림 서비스 팬아웃 FCM
```

소요: 6단계가 원본 1회 + 편집본 2ⁿ−1회 ffprobe 로 n=2 기준 약 5~8초 — 화면은 진행 표시(스피너) 후 상세로 이동.

## 부록 B. 어드민 계정 시드 절차

1. 오프라인에서 BCrypt 해시 생성(`htpasswd -bnBC 10 "" 'pw' | tr -d ':\n'` 또는 Spring `BCryptPasswordEncoder`). 평문·해시 모두 **팀 비밀 저장소**에만.
2. Liquibase `<sql>` changeSet(모듈 `db/changelog-core/`, PK 명시 금지 — IDENTITY 규칙):

```sql
INSERT INTO motif_core.users (nickname, role) VALUES ('admin_kim', 'ADMIN');
INSERT INTO motif_core.admins (login_id, password_hash, name, user_seq)
SELECT 'ops_kim', '${ADMIN_KIM_HASH}', '김운영', u.user_seq
FROM motif_core.users u WHERE u.nickname = 'admin_kim' AND u.role = 'ADMIN';
```

3. `${ADMIN_KIM_HASH}` 는 Liquibase property(환경변수 주입) — changelog 파일에 해시를 직접 쓰지 않는다.
4. 어드민 추가·퇴장은 같은 방식의 신규 changeSet(`is_active=FALSE` 전이). ADMIN `users` 행은 감사 참조가 남으므로 삭제하지 않는다.
5. 검증: `SELECT a.login_id, u.role FROM admins a JOIN users u USING (user_seq)` → 전 행 `ADMIN`.

## 부록 C. 구현 체크리스트 (BE)

- [x] `AdminAuthFilter`(쿠키·Bearer, `typ=ADMIN`, 별도 시크릿) · `AdminIpFilter`(X-Forwarded-For 허용 CIDR, 비허용 404) *(588)* — [ ] Traefik `forwardedHeaders.trustedIPs`/`rateLimit` 미들웨어는 인프라 몫
- [x] 감사 로그 JSON 파일 appender(롤링·HOSTNAME 포함) + `AdminAuditLogger` *(555)* — [ ] PVC 마운트는 인프라 몫
- [ ] ~~`MediaProbeService`~~ **v0.5 삭제** — core-api 는 ffmpeg 를 돌리지 않는다. 대신 업로드 화면 JS 가 길이·해상도를 읽어 폼에 싣는다(§1.4)
- [x] `AdminMediaController.presign`(kind→prefix·Content-Type·크기 조건) *(588)*
- [x] 영상 검증 `VideoRules`(방향·길이·편집본 ±500ms, 수치는 `motif.admin.media.rules`) *(589)*
- [x] §5 어드민 에러 코드 전량 등록 `module-common/.../error/AdminErrorCode` *(588)*
- [ ] 소속사·그룹·아티스트 CRUD(연쇄 규칙 트랜잭션) — [x] 곡 §3.6 목록·등록 *(389)*
- [x] `AdminChallengeService` §3.7.2·3·4·5·6·8(마스크 계산·완결성 검증·원자 INSERT·이벤트) + `validate` *(559·560·561)*
- [ ] 에셋·에셋 가격·패스 가격 upsert(활성 전환 조건)
- [ ] 공지(+이미지 교체)·배너(재정렬 오프셋)
- [ ] `ActAsArtistController` — 사용자 서비스 위임 + 구조화 로그
- [ ] 신고 처리(멱등 후처리) · 회원 정지(`user.suspended`)
- [ ] module-event: ~~`ChallengeCreated`~~ *(562)*, `NoticeCreated`, `UserSuspended` DTO · 알림 서비스 컨슈머 3종 + Redis 멱등
- [x] *(v0.4·562)* `AdminChallengeService.create` 커밋 후 **`job.media` 발행** — `MediaJobPublisher.publish(CHALLENGE_ORIGIN, challengeSeq, videoKey, MediaKeys.forChallengeOrigin(uuid))`
- [ ] *(v0.5)* 업로드 화면 JS 에 길이·해상도 추출 추가(`<video>` onloadedmetadata → hidden 필드)
- [ ] *(v0.4)* Jenkinsfile 에 `app-media` 빌드·이미지 추가 · k8s 매니페스트(`resources.limits.cpu: 1`)
- [ ] ~~S15P21D104-590 core-api Dockerfile ffmpeg~~ **v0.5 삭제**
- [ ] Thymeleaf 템플릿 12 화면(§1.7) · 어드민 시드 changeSet · DB v0.16 changeSet(admins.user_seq 만)
