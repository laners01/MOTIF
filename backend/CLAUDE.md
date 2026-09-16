# 작업 컨벤션 (backend)

아키텍처·모듈 구성·로컬 실행 3모드·팀 코딩 표준은 [README.md](README.md)와
[docs/adr/](docs/adr/)에 있다. 이 문서는 **Jira·브랜치·커밋 규칙**만 다룬다.

## Jira

- 프로젝트 키: `S15P21D104` → 이슈 키는 `S15P21D104-<번호>`
- 이슈 키는 **브랜치명**·**MR 제목**·**커밋 푸터** 세 곳에 넣는다.
- GitLab ↔ Jira 연동이 티켓에 커밋·MR을 링크하는 근거는 **MR 제목**이다.
  브랜치명은 스캔되지 않고, 커밋 푸터만으로는 `develop/*` 머지에서 링크가
  붙지 않는다. 푸터는 이슈 추적용 기록으로 계속 넣는다.

## 브랜치

```
<type>/S15P21D104-<이슈번호>-<kebab-슬러그>
```

예: `feature/S15P21D104-23-google-signin`, `chore/S15P21D104-511-log-timezone`

| type | 용도 |
|---|---|
| `feature/` | 기능 개발 |
| `chore/` | 빌드·CI·인프라·설정 등 기능 외 작업 |
| `fix/` | 버그 수정 |

슬러그는 영어 소문자 kebab-case.

- **단일 `develop` 브랜치는 없다.** 파트별 라인: `develop/backend`,
  `develop/infra`, `develop/android`
- 백엔드 작업은 `develop/backend`에서 분기하고 `develop/backend`로 MR을 낸다.
- `develop/backend`에 push되면 Jenkins가 자동 빌드·배포한다 (`Jenkinsfile`).
  통합 브랜치에 직접 커밋하지 않는다.

## 커밋

```
<type>: <한국어 설명>

- <변경 내역>            ← 본문 (선택)
- <변경 내역>

S15P21D104-<이슈번호>    ← 푸터
```

예 — 안드로이드 파트와 동일 형식이다:

```
refactor: 프로젝트 이름을 modif에서 motif로 변경

- 전체 패키지 경로 수정 (com.ssafy.modif -> com.ssafy.motif)
- Convention Plugin ID 및 build-logic 내 그룹명/패키지 업데이트
- namespace, applicationId 및 rootProject.name 설정 변경

S15P21D104-458
```

```
chore: 트리거를 SCM 폴링에서 GitLab 웹훅으로 전환

S15P21D104-181
```

| type | 용도 |
|---|---|
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `chore` | 빌드·CI·인프라·설정 |
| `docs` | 문서·ADR |
| `test` | 테스트 |
| `init` | 모듈·뼈대 최초 구성 |

- prefix는 영어 소문자, 설명은 **한국어**. 끝에 마침표 없음.
- 제목·본문·푸터 사이에 각각 **빈 줄**을 둔다.
- 푸터는 **이슈 키만** 적는다 — `Refs:` 같은 접두어를 붙이지 않는다 (안드로이드 파트와 통일).
- 이슈가 여럿이면 줄을 나눠 적는다.
- 이슈 없는 작업(오타·포맷 등)은 푸터를 생략한다.
- 본문은 변경이 여러 갈래일 때만 쓴다. 한 줄로 설명되면 제목만.
- 한 커밋 = 한 관심사.

## MR (GitLab)

제목:

```
[S15P21D104-<이슈번호>] <type>: <한국어 설명>
```

예: `[S15P21D104-193] feat: MOTIF 스키마를 Liquibase changelog로 작성`

- **제목에 이슈 키를 반드시 넣는다.** Jira 연동이 이 위치에서 키를 읽는다.
  빠지면 머지해도 티켓에 아무것도 남지 않는다 — 브랜치명과 커밋 푸터에 키가
  있어도 그렇다 (백엔드 MR !6·!7·!11·!12·!16 전부 제목 누락으로 미반영).
- `<type>`은 커밋 prefix와 같은 값을 쓴다.
- 머지 커밋은 GitLab 기본 형식을 쓴다: `Merge branch '<브랜치>' into '<대상>'`.
  본문에 MR 제목이 그대로 들어가므로, 제목에 키가 있으면 머지 커밋도 키를 갖는다.
- 대상은 해당 파트의 `develop/*`.
- **MR 제목·설명에 도구 서명을 남기지 않는다.** 머지 커밋 본문은 MR 제목·설명으로
  만들어지므로, 서명이 섞이면 통합 브랜치 이력까지 따라온다.
  `.claude/settings.json` 의 `attribution.commit`·`attribution.pr` 이 둘 다 빈 값이라
  Claude 가 쓰는 커밋·MR 어디에도 서명이 붙지 않는다.

## 구현 시 주의 — 이미 있는 것을 다시 만들지 않는다

여러 세션이 병렬로 돌면 같은 것을 두 번 만들기 쉽다. 아래는 **이미 머지된 것**이다.

| 있는 것 | 위치 | 티켓 |
|---|---|---|
| `MediaJobRequestedEvent` | `module-event/.../contract/` | S15P21D104-603 |
| `MediaJobPublisher` · `MediaJobEventRelay` · `MediaKeys` | `app-core-api/.../domain/media/` | 603 |
| `app-media` 모듈 전체 (렌디션·HLS·썸네일·S3·Dockerfile) | `app-media/` | 603 |
| **어드민 §5 에러 코드 전량** `AdminErrorCode` | `module-common/.../error/` | S15P21D104-588 |
| `BusinessException` 의 `data` — 실패 상세를 응답 `data` 로 | `module-common/.../exception/` | 588 |
| `AdminIpFilter`(404) · `AdminAuthFilter`(쿠키+Bearer) · `AdminJwtProvider` · `AdminSecurityConfig`(@Order(1)) | `app-core-api/.../admin/security/` | 588 |
| `AdminPaths` — 어드민 경로 상수 | `app-core-api/.../admin/` | 588 |
| presign API `AdminMediaController`·`AdminMediaService`·`AdminMediaKind` | `app-core-api/.../admin/media/` | 588 |
| 영상 검증 `VideoRules` (폼이 보낸 길이·해상도 대조) | `app-core-api/.../admin/media/` | 589 |
| 감사 로그 `AdminAuditLogger` + logback `ADMIN_AUDIT` 어펜더 | `app-core-api/.../admin/audit/` · `logback-spring.xml` | 555 |
| 챌린지 엔티티·리포지토리 `Challenge`·`ChallengeParticipant`·`EditedChallenge`·`Song` | `domain-core/.../challenge/` · `song/` | S15P21D104-559 |
| 슬롯 마스크·2ⁿ−1 완결성 `SlotRules` (순수 계산) | `app-core-api/.../admin/challenge/` | 559 |
| 챌린지 §3.7.2·3·4·5·6·8 전량 `AdminChallengeService` | `app-core-api/.../admin/challenge/` | 559·560·561 |
| S3 HEAD `AdminMediaService.requireUploaded` (부재 키 모아 409) | `app-core-api/.../admin/media/` | 559 |
| `CHALLENGE_NOT_FOUND`·`EDITED_CHALLENGE_NOT_FOUND` `ChallengeErrorCode` | `app-core-api/.../domain/challenge/` | 559 |
| `ChallengeCreatedEvent` · `ChallengeEventRelay`(AFTER_COMMIT) | `module-event/.../contract/` · `app-core-api/.../domain/challenge/` | 562 |
| 어드민 로그인 `AdminAuthController`(`/auth/login`·`logout`·`me`) · `AdminAuthService` | `app-core-api/.../admin/auth/` | S15P21D104-608 |
| `motif_admin` 쿠키 조립 `AdminSessionCookie` · `PasswordEncoder`(BCrypt cost 10) 빈 | `app-core-api/.../admin/auth/` · `admin/security/AdminSecurityConfig` | 608 |
| `Admin` 엔티티·리포지토리 (가입 경로 없음 — 생성자 없음) | `domain-core/.../admin/` | 608 |
| `admins.user_seq` NOT NULL UNIQUE FK · 어드민 시드 | `db/changelog/sql/0014`·`0015` | 593 · 608 |
| `/admin/login` 화면 (스타일·스크립트 인라인 — layout 을 쓰지 않는다) | `templates/admin/login.html` | 608 |
| **offset 페이지 응답** `PageResponse`(record, spring-data 의존 없음) | `module-common/.../response/` | S15P21D104-389 |
| **`Page` → `PageResponse` 어댑터 `PageResponses`** (`of`·`of(page, mapper)`·`pageable()` — size 를 100 으로 자른다) | `app-core-api/.../global/response/` | 389·385·609 |
| 곡 API `AdminSongController`·`AdminSongService` (§3.6 목록·등록) | `app-core-api/.../admin/song/` | 389 |
| `Song` 엔티티 전량 매핑 + `SongRepository.search`·`countChallenges` | `domain-core/.../song/` | 389 |
| 아티스트 API `AdminArtistController`·`AdminArtistService` (§3.5 목록·등록·상세·수정) | `app-core-api/.../admin/artist/` | 385 |
| `ArtistGroup`·`Company` 엔티티 · `Artist` §3.5 컬럼 확장 · `ArtistCount` 집계 프로젝션 | `domain-core/.../artist/` | 385 |
| 공지 §3.10.1·2·3 `AdminNoticeService.list`·`create`·`detail` · `NoticeEventRelay` | `app-core-api/.../admin/notice/` · `domain/notice/` | 611·402·403 |
| 챌린지 §3.7.1 목록 `AdminChallengeService.list` · `ChallengeRepository.search` | `app-core-api/.../admin/challenge/` · `domain-core/.../challenge/` | 609 |
| 에셋 §3.8.1 목록 `AdminAssetService.list` · `MotifAssetRepository.search` | `app-core-api/.../admin/asset/` · `domain-core/.../asset/` | 610 |
| **api-docs 그룹 분리** `GroupedOpenApi` user·admin + `springdoc.enable-default-api-docs=false` | `app-core-api/.../global/docs/OpenApiConfig` · `application.yml` | S15P21D104-612 |
| `AdminPaths.API_DOCS` — `/v3/api-docs/admin` 도 어드민 경로다(공인망 404, 어드민 토큰 200) | `app-core-api/.../admin/AdminPaths` | 612 |
| 소속사 §3.3 · 그룹 §3.4 CRUD `AdminCompanyController`·`AdminCompanyService`·`AdminGroupController`·`AdminGroupService` | `app-core-api/.../admin/org/` | S15P21D104-383 · 384 |
| `CompanyListRow`·`GroupListRow` 프로젝션 · 참조수 상관 서브쿼리 · `ArtistRepository.countByCompanySeq…`·`countByGroupSeq…` | `domain-core/.../artist/` | 383 · 384 |
| **커서 페이지 봉투** `CursorResponse`(§1.9) · 비페이지 봉투 `ListResponse`(§5.4) · 토큰 코덱 `Cursors` | `module-common/.../response/` · `app-core-api/.../global/response/` | S15P21D104-79 |
| 팬 조회 API `ArtistController`(§5.3·5.4·5.5) · `ChallengeController`(§7.2) + `…QueryService` | `app-core-api/.../domain/artist/` · `domain/challenge/` | 67 · 76 · 79 · 138 |
| 어드민 화면 `orgs`·`artists`·`songs`·`challenges`·`assets` (전부 `layout.html` 프래그먼트) | `templates/admin/` · `static/admin/` | 387 · 388 · 395 · 401 · 613 |
| **보호 등급 재생목록 API** `MediaController`(`/api/v1/media/{jobUuid}/master.m3u8`·`/{rendition}/index.m3u8`) · `MediaPlaylistQueryService`(인가) | `app-core-api/.../domain/media/` | S15P21D104-278 |
| **등급 판정 단일 지점** `MediaUrlResolver` · `MediaKeys.isProtected`·`prefixOf` | `app-core-api/.../global/media/` · `domain/media/` | 278 |
| `CompositeJob`·`JobType`·`JobStatus` · `ParticipationVideo`·`Visibility` · `MotifResult` 엔티티·리포지토리 (전부 **읽기 전용 — 생성자 없음**) | `domain-core/.../job/` · `video/` · `motif/` | 278 |
| 마이페이지 원콜 `UserController`·`UserQueryService` (§4.1) · 내 챌린지 그리드(§4.8) · 그리드 항목 `VideoPreviewItem`(§4.1·§4.8 공용) · `ParticipationVideoRepository.findMine`(건수는 `Pageable` 로) | `app-core-api/.../domain/user/` · `domain-core/.../video/` | 278 |
| **닉네임 제약 `@Nickname`** (§3.3·§4.2 공용 — 패턴·메시지를 각자 두지 않는다) | `app-core-api/.../global/validation/` | S15P21D104-282 |
| 닉네임 수정 `UserCommandService` (§4.2) | `app-core-api/.../domain/user/` | 282 |
| 타 사용자 프로필 §4.6 `UserQueryService.profile` · `UserErrorCode`(`USER_NOT_FOUND`) · `SubscriptionRepository.findSubscribed`(삭제 아티스트 제외·구독 최신순) | `app-core-api/.../domain/user/` · `domain-core/.../artist/` | S15P21D104-158 |
| **S3 presign·HEAD `MediaObjectStore`** · 이미지 업로드 한도 `MediaUploadProperties`(`motif.media.upload`) | `app-core-api/.../global/media/` | S15P21D104-280 |
| 프로필 이미지 §4.3·§4.4 `UserProfileImageService` · `ProfileImagePresignRegistry`(Redis, ADR-0011) | `app-core-api/.../domain/user/` | 280 |

**§5.3 `myMotifVideos` 는 지금 항상 빈 배열이다.** §1.7 보호 등급이라 CloudFront signed
쿠키가 필요한데 레포에 서명 발급 수단이 없다(키페어·만료 설정 전무). 공개 URL 로 대신
내보내면 유료 결과물이 무기명 링크로 샌다 — 서명이 붙는 티켓에서 채운다(`ponytail:` 주석 있음).

```java
// 후처리를 걸 때 — 새로 짜지 말고 이걸 부른다
mediaJobPublisher.publish(
    MediaJobRequestedEvent.SOURCE_CHALLENGE_ORIGIN,
    challengeSeq,
    videoKey,                                    // challenges/{uuid}.mp4
    MediaKeys.forChallengeOrigin(uuid),
    thumbnailAtMs);                              // 선택 — null 이면 워커 기본 1.5초 (§4.8)
```

```java
// 챌린지 슬롯을 다룰 때 — 마스크는 서버가 계산한다. 비트를 직접 쓰지 않는다
SlotRules.checkParticipants(mainArtistSeq, participants);       // 메인 포함·중복·bbox
List<Integer> masks = SlotRules.editedMasks(n, editedList);     // 2ⁿ−1 완결성 + 마스크
adminMediaService.requireUploaded(keys);                        // HEAD, 부재 키를 data 에
```

```java
// 어드민 인증이 필요할 때 — 로그인은 이미 있다. 토큰을 직접 만들지 않는다
adminJwtProvider.issue(new AuthenticatedAdmin(adminSeq, userSeq, name));   // 발급
// 감사 컬럼(reg_usr_seq 등)에 넣을 값은 admin.userSeq() 다 — adminSeq 가 아니다 (§1.3)
```

```java
// 어드민 API 를 새로 만들 때 — 아래는 이미 있다
@RequestMapping(AdminPaths.API + "/companies")          // 경로 상수
throw new BusinessException(AdminErrorCode.COMPANY_IN_USE, msg, Map.of("refs", n));  // §5 코드 + data
videoRules.checkChallengeOrigin(new VideoRules.Meta(durationMs, width, height));     // 폼 값 검증
adminAuditLogger.write(admin, "CREATE_CHALLENGE", "CHALLENGE", seq, AdminAuditLogger.RESULT_OK);
```

```java
// S3 를 만질 때 — presign·HEAD 는 여기 하나뿐이다. S3Client 를 직접 주입받지 않는다
objectStore.presignPut(key, contentType, contentLength, ttl);   // 조건이 서명에 박힌다
objectStore.exists(key);                                        // 403 을 "없음" 으로 삼키지 않는다
// 한도(타입·크기·TTL)는 motif.media.upload.image — 어드민 아티스트 프로필과 사용자
// 프로필이 같은 profiles/ 프리픽스에 쓰므로 한 곳에 있다. 각자 두지 말 것

// 300초짜리 상태가 필요할 때 — Redis 다 (ADR-0011). 만료가 데이터의 본질이면 Redis,
// 남겨야 할 이력이면 DB. 거의 NULL 인 칼럼을 새로 만들지 않는다
```

```java
// 이미지 URL 을 만들 때 — 공개/보호 판정을 직접 하지 않는다. 키 프리픽스가 등급이다
mediaUrlResolver.resolve(video.getThumbnailKey());   // private/ 면 서명, 아니면 무서명
// visibility 로 가르면 안 된다 — PUBLIC 게시는 public/ 으로 '복사'만 하고 키를 안 바꾼다.
// PublicMediaUrlResolver·ProtectedMediaUrlResolver 를 호출부에서 직접 고르지 말 것.
// S3 presigned GET 은 어느 등급에도 쓰지 않는다 — CDN 캐시 적중률이 0% 가 된다(§1.7)

// 보호 등급 영상 주소 — CDN 이 아니라 core-api 다. 문자열을 직접 조립하지 않는다
MediaPlaylistQueryService.masterPlaylistUrl(job.getJobUuid());
```

```java
// 목록 API 를 새로 만들 때 — 페이지 봉투·상한은 이미 있다. size 를 직접 자르지 않는다
return PageResponses.of(repository.search(q, PageResponses.pageable(page, size)), this::item);
// 응답 타입은 PageResponse<T> (module-common). 어드민 화면 fillSelect 가 data.list 를
// 꺼내 쓰므로 배열을 data 에 바로 싣지 않는다.
//
// PageResponse 는 record 뿐이고 Page 를 받는 변환은 PageResponses 에 있다 —
// module-common 이 spring-data 에 의존하지 않기 위해서다. 둘을 합치지 말 것.
```

**`AdminErrorCode` 에 코드를 새로 추가하지 않는다** — 명세 §5 표는 전량 등록돼 있다.
표에 없는 코드가 필요하면 명세를 먼저 고친다. 여러 세션이 각자 코드를 덧붙이면
같은 파일에서 계속 충돌한다.

**인코딩 파라미터(GOP·비트레이트·faststart)는 `app-media` 에만 둔다 — core-api 에
복사하지 않는다.** 두 곳에 있으면 한쪽만 고쳤을 때 화질 간 키프레임 정렬이 깨지는데,
그러면 **예외도 로그도 없이 화질 전환만** 안 된다(ADR-0010 D4).
**core-api 에는 ffmpeg 가 아예 없다** — 길이·해상도는 업로드 화면 JS 가 읽어 보내고
서버는 `VideoRules` 로 대조만 한다(어드민 명세 v0.5 §1.4).

### S3 자격증명 — IMDS 를 믿지 마라

**운영 서버(Lightsail)의 인스턴스 역할은 SSAFY 계정 것이고, `motif-media` 버킷은 우리
계정에 있다.** AWS SDK 기본 자격증명 체인은 환경변수가 없으면 IMDS 로 내려가
`AmazonLightsailInstanceRole` 의 임시 자격증명(`ASIA…`)을 주워 서명한다 — 크로스 계정이라
서명은 멀쩡히 되고 S3 가 `AccessDenied` 를 준다.

- **presign 이 로그에 찍혔다고 권한이 있는 것이 아니다.** 서명은 권한을 검사하지 않는다.
  권한이 0이어도 URL 은 정상 발급되고 로그도 정상으로 보인다.
- 그 역할은 우리가 고칠 수 없다(남의 계정이고, Lightsail 인스턴스 역할은 편집 불가).
  **우리 계정의 IAM 사용자 키를 `core-api-secret` 에 넣어 IMDS 를 이겨야 한다** —
  체인에서 환경변수가 IMDS 보다 앞이다. 코드 변경은 없다.
- 필요한 권한 3개: `s3:PutObject`·`s3:GetObject` on `arn:aws:s3:::motif-media/*`,
  `s3:ListBucket` on `arn:aws:s3:::motif-media`(**뒤에 `/*` 를 붙이면 조용히 무효다**).
  `GetObject` 가 빠지면 업로드는 되는데 등록이 막히고, `ListBucket` 이 빠지면 없는 키가
  404 대신 403 으로 온다.
- `envFrom` 은 **파드 생성 시점에 한 번** 해석된다. Secret 을 patch 했으면
  `kubectl rollout restart deployment/core-api` 까지 해야 반영된다.
  넣었다고 믿지 말고 `kubectl exec deploy/core-api -- printenv AWS_ACCESS_KEY_ID` 로 확인해라
  (함정 "설정값이 필수라고 믿지 마라" 와 같은 모양이다 — 설정이 없어도 앱은 멀쩡히 뜬다).

## ADR

규칙·스택·구조를 바꾸거나 대안을 검토했다면 `docs/adr/`에 ADR을 추가한다
(ADR-0001). 커밋 type은 `docs`.
