# 작업 컨벤션 (backend)

아키텍처·모듈 구성·로컬 실행 3모드·팀 코딩 표준은 [README.md](README.md)와
[docs/adr/](docs/adr/)에 있다. 이 문서는 **Jira·브랜치·커밋 규칙**만 다룬다.

## Jira

- 프로젝트 키: `S15P21D104`
- 이슈 번호는 **브랜치명에만** 넣는다. 커밋 메시지에는 넣지 않는다 —
  이슈 연결은 브랜치명과 GitLab MR이 담당한다.

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
```

예:

```
feat: 빌드 상태를 GitLab 커밋·MR 화면에 보고
chore: 트리거를 SCM 폴링에서 GitLab 웹훅으로 전환
docs: SCG 제거 결정 ADR 0009 추가 및 게이트웨이 모듈 삭제
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
- 한 커밋 = 한 관심사.

## MR (GitLab)

- 머지 커밋은 GitLab 기본 형식을 쓴다: `Merge branch '<브랜치>' into '<대상>'`
- 대상은 해당 파트의 `develop/*`.

## ADR

규칙·스택·구조를 바꾸거나 대안을 검토했다면 `docs/adr/`에 ADR을 추가한다
(ADR-0001). 커밋 type은 `docs`.
