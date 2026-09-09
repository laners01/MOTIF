# 작업 컨벤션 (backend)

아키텍처·모듈 구성·로컬 실행 3모드·팀 코딩 표준은 [README.md](README.md)와
[docs/adr/](docs/adr/)에 있다. 이 문서는 **Jira·브랜치·커밋 규칙**만 다룬다.

## Jira

- 프로젝트 키: `S15P21D104` → 이슈 키는 `S15P21D104-<번호>`
- 이슈 키는 **브랜치명**과 **커밋 푸터** 두 곳에 넣는다.
- GitLab ↔ Jira 연동이 커밋 메시지에서 키를 찾아 해당 이슈에 커밋·MR을 링크한다.

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

- 머지 커밋은 GitLab 기본 형식을 쓴다: `Merge branch '<브랜치>' into '<대상>'`
- 대상은 해당 파트의 `develop/*`.

## ADR

규칙·스택·구조를 바꾸거나 대안을 검토했다면 `docs/adr/`에 ADR을 추가한다
(ADR-0001). 커밋 type은 `docs`.
