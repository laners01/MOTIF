# MOTIF

**좋아하는 아티스트의 영상 속에 내가 들어간다.**
아티스트가 올린 챌린지·모티프 영상에서 한 사람의 자리를 비우고, 그 자리에 사용자를 AI 로 합성해 넣는 안드로이드 앱이다.
SSAFY 15기 프로젝트.

## 서비스 소개

팬은 아티스트의 챌린지를 따라 추고 공유하지만, 아티스트와 **같은 화면** 안에 있을 수는 없었다.
MOTIF 는 아티스트 원본 영상에서 참여할 위치(슬롯)를 고르면, 그 자리의 인물이 지워진 편집본 위에
사용자를 촬영해 넣고, 서버의 AI 가 색감·조명·춤 동작까지 맞춰 한 편의 영상으로 합성한다.

- **챌린지** — 다른 팬들과 함께 참여하고 공유하는 콘텐츠. 합성 결과를 게시하면 피드·랭킹에 오른다.
- **모티프** — "인생네컷의 영상 버전". 아티스트가 제공하는 짧은 영상에 들어가 나만의 추억을 만들고 개인 보관함에 간직한다. 유료(개별 구매·아티스트 패스).
- **촬영이 쉽다** — 촬영 중 온디바이스에서 내 실루엣을 실시간으로 분리해 아티스트 영상 위에 겹쳐 보여 주므로, 실제 영상 안에 들어간 모습을 보면서 위치와 동작을 맞춘다. 5초 카운트다운 뒤 원본이 끝날 때까지 원테이크.
- **기다리지 않는다** — 합성은 비동기로 돌고, 완료되면 FCM 푸시로 결과 화면에 바로 간다.

### 주요 기능

| 기능 | 내용 |
|---|---|
| 챌린지 참여 | 숏폼 피드에서 챌린지 선택 → 슬롯(위치) 선택 → 실시간 미리보기 촬영 → 업로드 → AI 합성 → 푸시 → 결과 확인 → 소개글·공개 범위·댓글 허용을 정해 게시 |
| 모티프 | Shop 에서 모티프 영상 개별 구매 또는 아티스트 패스(기간제) 구매 → 촬영·합성 → 보관함 저장 → 대표 모티프 설정. 토스페이먼츠 결제 |
| 소셜 | 홈(인기 참여 영상 · Who's On 아티스트 랭킹 · 구독 아티스트 활동 피드), 챌린지 숏폼 피드, 참여 영상 피드, 좋아요·댓글·답글, 아티스트 구독·상세(챌린지·공지), 마이페이지(내 참여 영상·구독·활동) |
| 알림 | 합성 완료·실패, 새 챌린지·공지·댓글·답글 FCM 푸시. 알림에서 결과 화면으로 딥링크 |
| 어드민 | 소속사·그룹·아티스트·곡·챌린지(슬롯 앵커·편집본)·모티프 에셋·패스 가격·공지·배너 등록, 아티스트 활동 대행, 신고·회원·주문 관리, 합성 잡 모니터링. 테일넷 전용 |

### 챌린지 참여 흐름

```
앱: 슬롯 선택 → 5초 카운트다운 → 촬영 (온디바이스 세그멘테이션으로 실시간 합성 미리보기)
  → S3 presigned 업로드 → 합성 요청
백엔드: 잡 접수 → AI 접수 서비스 호출 ──▶ GPU 합성 Job (GCP Cloud Run)
  ◀── 결과 콜백 → HLS 가공·썸네일 → 잡 완료 → FCM 푸시
앱: 알림 탭 → 결과 재생 → 소개글·공개 설정 → 게시 → 참여 영상 피드
```

### AI 합성

| 단계 | 하는 일 | 모델·기법 |
|---|---|---|
| 사전분석 (챌린지 등록 시 슬롯당 1회) | 배경 인물 탐지·추적, 슬롯 인물 제거와 배경 복원, 기준 춤 관절, 3D 기하·바닥 접지, 장면 깊이, 조명 조건 | RF-DETR + SAM 2, SVOR(Wan2.1-VACE), DWPose, GVHMR, Video-Depth-Anything, RelightVid |
| 합성 (요청마다) | 촬영본·원본 음악 동기화, 사용자 검출·추적·분리, 춤 동작 보정, 3D 배치·접지, 깊이 기반 가림, 재조명, 오디오 합성 → 30fps MP4 | ffmpeg + STFT 상관, RF-DETR + SAM 2, DWPose + DTW, GVHMR, RelightVid |

GPU Job 은 GCP Cloud Run Jobs(NVIDIA RTX PRO 6000)에서 요청당 1회 실행된다. 실측 처리 시간은 챌린지 약 26~28분, 모티프 약 14분이다.

## 시스템 아키텍처

```
사용자(안드로이드 앱)
   │ HTTPS
   ▼
Traefik (k3s 내장 · hostPort 80/443 · TLS 종단, Let's Encrypt)
   │
   ├─ /api/**     ──▶ core-api (Spring :8081) ──▶ PostgreSQL · Redis
   │                    ├─ MQ job.media ───────▶ app-media (ffmpeg → HLS·썸네일 → S3)
   │                    ├─ MQ evt.notification ▶ app-notification ──▶ FCM 푸시
   │                    └─ HTTP 접수 ─────────▶ AI 접수(GCP Cloud Run) ──▶ GPU Job
   │                         ◀─ 콜백 /api/worker/v1/composite-jobs/{uuid}/done ─┘
   ├─ /admin/**   ──▶ core-api (테일넷 대역만 허용, 그 외 404)
   └─ /project/** ──▶ Jenkins (호스트 프로세스, GitLab 웹훅 전용)

미디어: 앱 ──▶ S3 presigned PUT(업로드) · CloudFront(공개 URL, private/* 는 서명 URL) ──▶ 앱
```

- HTTP 를 받는 서비스는 core-api 하나다. app-media·app-notification 은 RabbitMQ 컨슈머다.
- AI 쪽에는 DB·큐·상태가 없다. 잡 상태·재시도·HLS 가공·푸시는 전부 백엔드가 관리한다.
- 인증은 Google ID 토큰을 검증한 뒤 자체 JWT 를 발급한다. 보호 영상은 CloudFront 서명 URL 로만 재생된다.

## 기술 스택

| 파트 | 스택 |
|---|---|
| Android | Kotlin 2.3, Jetpack Compose, Navigation 3, Hilt, CameraX, ML Kit Selfie Segmentation, Media3(ExoPlayer · HLS), Retrofit · OkHttp, DataStore, Firebase Messaging, Credential Manager(Google 로그인), Toss Payments SDK |
| Backend | Java 21, Spring Boot 4.0, Spring Security(Google ID 토큰 · JWT), Data JPA + QueryDSL, Liquibase, Spring AMQP(RabbitMQ), Data Redis, AWS SDK v2(S3 · CloudFront 서명 URL), Firebase Admin(FCM), ffmpeg(HLS) |
| AI | Python 3.12, PyTorch 2.7(CUDA 12.8), RF-DETR, SAM 2, SVOR(Wan2.1-VACE), DWPose, GVHMR, Video-Depth-Anything, RelightVid. GCP Cloud Run Jobs(GPU) · Cloud Build · Artifact Registry · Cloud Storage · Secret Manager |
| Infra | Ubuntu 24.04 단일 서버, k3s(Traefik · cert-manager), PostgreSQL 16, RabbitMQ 3.13, Redis 7, Jenkins(GitLab 웹훅), Tailscale, AWS S3 + CloudFront |

## 구조

| 경로 | 내용 |
|---|---|
| `android/` | 안드로이드 앱 (Kotlin · Jetpack Compose) — `develop/android` 라인 |
| `backend/` | Spring Boot 4 멀티모듈 백엔드. 실행체는 `app-core-api` / `app-media` / `app-notification` — `develop/backend` 라인 |
| `ai_pipeline/` | GPU 합성·사전분석 파이프라인 (Python, GCP Cloud Run Jobs) — `develop/ai` 라인 |
| `infra/` | k8s 매니페스트와 서버 셋업 절차 — `develop/infra` 라인 |
| `exec/` | 포팅 매뉴얼 · 외부 서비스 · 시연 시나리오 · 환경변수 목록 · DB 덤프 |
| `backend/docs/` | ADR(`adr/`), API·어드민·AI 서버 명세(`api/`), DB 명세(`erd/`), PoC(`poc/`) |
| `backend/CLAUDE.md` | 백엔드 작업 규약 — Jira·브랜치·커밋·MR 규칙. 백엔드를 건드린다면 먼저 읽는다 |

## 브랜치

파트별 develop 라인을 쓴다. **단일 `develop` 브랜치는 없다.**

- `develop/backend` — 백엔드. 여기에 머지되면 CI/CD가 돌아 자동 배포된다
- `develop/infra` — 인프라 매니페스트·문서
- `develop/android` — 안드로이드
- `develop/ai` — AI 파이프라인

## 배포

`develop/backend` push → GitLab 웹훅 → Jenkins → 이미지 빌드 →
k3s containerd 직접 임포트 → 롤링 배포 → health 검증.
실패하면 자동으로 직전 리비전으로 되돌린다.

- 파이프라인: `backend/Jenkinsfile`
- 서버 셋업 절차: `infra/setup/jenkins-setup.md`
- 레지스트리를 쓰지 않는 이유: `backend/docs/adr/0007-no-registry-containerd-direct-import.md`

운영 확인:

```bash
curl -s https://j15d104.p.ssafy.io/api/health
```

## 문서

| 문서 | 위치 |
|---|---|
| 빌드·배포 매뉴얼 | [`exec/01_빌드_배포_매뉴얼.md`](exec/01_빌드_배포_매뉴얼.md) |
| 외부 서비스 | [`exec/02_외부_서비스.md`](exec/02_외부_서비스.md) |
| DB 덤프·복원 | [`exec/03_README.md`](exec/03_README.md) |
| 시연 시나리오 | [`exec/04_시연_시나리오.md`](exec/04_시연_시나리오.md) |
| 환경변수 전체 목록 | [`exec/05_환경변수_전체목록.md`](exec/05_환경변수_전체목록.md) |
| API · 어드민 · AI 서버 명세 | `backend/docs/api/` |
| 아키텍처 결정 기록 | `backend/docs/adr/` |
| AI 파이프라인 운영 문서 | `ai_pipeline/docs/` |
