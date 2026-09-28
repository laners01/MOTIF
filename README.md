# MOTIF

SSAFY 15기 프로젝트. 좋아하는 아티스트의 챌린지·모티프 영상 속에 사용자가 직접 들어가
함께 영상을 만드는 안드로이드 앱이다. Spring Boot 4 멀티모듈 백엔드를 k3s 단일 노드에,
GPU 합성 파이프라인을 GCP Cloud Run 에 배포한다.

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
- 전체 빌드·배포 절차와 환경변수: `exec/01_빌드_배포_매뉴얼.md`, `exec/05_환경변수_전체목록.md`

운영 확인:

```bash
curl -s https://j15d104.p.ssafy.io/api/health
```
