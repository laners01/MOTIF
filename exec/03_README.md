# DB 덤프 안내

이 폴더(`exec/db/`)에는 제출용 DB 덤프 최신본을 둔다. 파일명 규칙: `db_dump_YYYYMMDD.sql.gz`

## 구성

운영 DB 는 k3s 클러스터 안의 PostgreSQL 16 단일 인스턴스(`deployment/postgres`, 이미지 `postgres:16-alpine`, 실측 16.15)다.
DB 1개(`motif`) 안에 스키마 2개와 계정 2개로 소유권을 나눈다. 매니페스트: `infra/k8s/10-postgres/`.

| 스키마 | 소유 계정 | 사용 서버 | 스키마 관리 |
|---|---|---|---|
| `motif_core` | `core_app` | core-api, app-media | Liquibase (app-core-api 기동 시) |
| `motif_notif` | `notif_app` | app-notification | Liquibase (app-notification 기동 시) |

슈퍼유저는 `postgres`, 확장은 `pg_trgm` 1.6(public 스키마, 아티스트·곡 검색용) 하나다. 2026-09-28 실측: 테이블 44개(motif_core 41, motif_notif 3), DB 크기 11MB.
계정·스키마·확장은 최초 기동 시 `postgres-init` ConfigMap 의 `init.sh` 가 만들고, 테이블은 각 앱의 Liquibase 가 만든다.

## 덤프 생성

DB 는 하나지만 계정(`core_app`, `notif_app`)의 비밀번호 해시와 권한까지 함께 떠야 하므로 `pg_dumpall` 을 쓴다.

```bash
# 서버에서
sudo k3s kubectl exec deploy/postgres -- pg_dumpall -U postgres | gzip > db_dump_$(date +%Y%m%d).sql.gz

# 로컬에서 한 번에 (SSH 키는 팀 비밀 저장소)
ssh -i J15D104T.pem ubuntu@j15d104.p.ssafy.io \
  "sudo k3s kubectl exec deploy/postgres -- pg_dumpall -U postgres | gzip" > db_dump_$(date +%Y%m%d).sql.gz
```

스키마별로 따로 필요하면 custom format 으로 뜬다.

```bash
sudo k3s kubectl exec deploy/postgres -- pg_dump -U postgres -d motif -n motif_core  -F c > motif_core.dump
sudo k3s kubectl exec deploy/postgres -- pg_dump -U postgres -d motif -n motif_notif -F c > motif_notif.dump
```

## 검증

```bash
# 헤더 확인 (PostgreSQL database cluster dump 문구)
zcat db_dump_*.sql.gz | head -5

# 롤·스키마·확장 포함 확인: core_app, notif_app / motif_core, motif_notif / pg_trgm 이 보여야 한다
zcat db_dump_*.sql.gz | grep -E "^CREATE (ROLE|SCHEMA|EXTENSION)"

# 테이블 수 확인 (2026-09-28 기준 44개. Liquibase 관리 테이블 databasechangelog 포함)
zcat db_dump_*.sql.gz | grep -c "^CREATE TABLE"
```

## 복원 (신규 환경)

`infra/k8s/10-postgres/` 를 apply 해 PostgreSQL 을 기동하면 빈 PVC 에서 `init.sh` 가 먼저 돌아 롤·스키마·확장을 만든다.
그 뒤 덤프를 흘려 넣는다.

```bash
# 서버에서 (덤프 파일을 서버로 복사한 뒤)
zcat db_dump_YYYYMMDD.sql.gz | sudo k3s kubectl exec -i deploy/postgres -- psql -U postgres -d postgres

# custom 방식 (스키마 단위)
sudo k3s kubectl exec -i deploy/postgres -- pg_restore -U postgres -d motif --clean --if-exists < motif_core.dump
```

- `init.sh` 가 이미 만든 롤·DB·스키마·확장에 대한 `already exists` 오류는 정상이며 무시한다. 그 외 오류는 없어야 한다.
- 덤프에 Liquibase 이력 테이블(`databasechangelog`)이 포함되므로 앱을 다시 기동해도 마이그레이션이 재실행되지 않는다.
- 복원되는 계정 비밀번호는 덤프 시점 서버 값(SCRAM 해시) 기준이다. 신규 환경의 `postgres-secret` 값과 다르면 복원 후 `ALTER ROLE core_app PASSWORD '...'` 로 맞춘다.
