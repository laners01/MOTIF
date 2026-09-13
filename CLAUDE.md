# MOTIF

백엔드 작업 규약은 **[backend/CLAUDE.md](backend/CLAUDE.md)** 에 있다 — Jira·브랜치·커밋·MR
규칙과 "이미 있는 것을 다시 만들지 않는다" 목록이 거기 있다. 백엔드를 건드린다면 먼저 읽는다.

## 레포 구조

| 경로 | 내용 |
|---|---|
| `backend/` | 서버 모노레포 (Gradle 멀티모듈) |
| `backend/docs/adr/` | 결정 기록 |
| `backend/docs/api/` | API 명세 · 어드민 명세 |
| `backend/docs/poc/` | PoC 판정 기준·측정 결과·인계 문서 |
| `backend/docs/erd/` · `db/` | DB 명세 |

안드로이드·인프라는 각 파트 브랜치(`develop/android`, `develop/infra`)에 있다.
