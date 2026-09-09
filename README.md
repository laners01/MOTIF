# MOTIF

SSAFY 15기 공통 프로젝트. Spring Boot 4 멀티모듈 백엔드를 k3s 단일 노드에
배포한다.

## 구조

| 경로 | 내용 |
|---|---|
| `backend/` | Gradle 멀티모듈. 실행체는 `app-core-api` / `app-notification` |
| `infra/` | k8s 매니페스트와 서버 셋업 절차 (`develop/infra` 라인) |
| `backend/docs/adr/` | 아키텍처 결정 기록. 왜 그렇게 했는지는 여기에 있다 |

## 브랜치

파트별 develop 라인을 쓴다. **단일 `develop` 브랜치는 없다.**

- `develop/backend` — 백엔드. 여기에 머지되면 CI/CD가 돌아 자동 배포된다
- `develop/infra` — 인프라 매니페스트·문서
- `develop/android` — 안드로이드

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
