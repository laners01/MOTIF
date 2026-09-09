# DDL 검증 리포트

기준 명세: [docs/erd/MOTIF_DB_SPEC_v0_15.md](erd/MOTIF_DB_SPEC_v0_15.md)
질문 목록: [DDL_QUESTIONS.md](DDL_QUESTIONS.md)
검증 일시: 2026-09-09 · 브랜치 `feature/S15P21D104-193-backend-db-liquibase`
검증 회차: 1차 v0.14 기준 → **2차 v0.15 기준 재검증(최종)** — 아래 "v0.15 재검증" 절 참조

## 결과 요약

| 항목 | 결과 |
|---|---|
| 빈 DB 에서 core update | ✅ 50 changeSet, 에러 0 |
| 빈 DB 에서 notif update | ✅ 1 changeSet, 에러 0 |
| 재실행 멱등성 | ✅ core `Run: 0 / Previously run: 50`, notif `Run: 0 / Previously run: 1` |
| v0.15 `motif_results.mod_dttm` 반영 | ✅ 11번 컬럼 `TIMESTAMPTZ NULL` (기본값 없음) |
| 테이블 수 | ✅ `motif_core` 38, `motif_notif` 1 (명세 39개 전수) |
| 컬럼·타입·NULL 여부 | ✅ 38개 테이블 전수 일치 (아래 표) |
| 제약 동작 | ✅ 위반 16건 전부 차단 확인 |
| 스키마 권한 격리 | ✅ 양방향 차단 + `subscriptions` SELECT 예외만 통과 |
| 교차 스키마 FK | ✅ `motif_notif` FK 개수 0 |
| Liquibase 이력 테이블 배치 | ✅ 각 소유 스키마에 생성 |

**검증 중 결함 1건 발견·수정** — `manual-prereq.sql` 의 `pg_trgm` search_path 누락.
아래 "발견한 결함" 절 참조.

---

## 검증 환경

| 항목 | 값 |
|---|---|
| PostgreSQL | 16.15 (Debian) — `postgres:16` 컨테이너 |
| Liquibase | `liquibase/liquibase:4.31` (CLI, 프로젝트 파일 무수정) |
| 실행 계정 | core changelog = `core_app`, notif changelog = `notif_app` (소유 계정 마이그레이션) |

Spring Boot 를 거치지 않고 Liquibase CLI 로 검증했다. `application.yml` 을 수정하지
않기 위한 선택이며, changelog 자체는 동일하게 해석된다.

### 재현 절차

```bash
# 1. 격리된 검증용 DB
docker network create motif-ddl-verify
docker run -d --name motif-ddl-pg --network motif-ddl-verify \
  -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=motif postgres:16

# 2. 수동 선행 SQL (스키마·계정·GRANT USAGE·pg_trgm·search_path)
docker cp backend/docs/db/manual-prereq.sql motif-ddl-pg:/tmp/
docker exec motif-ddl-pg psql -v ON_ERROR_STOP=1 -U postgres -d motif -f /tmp/manual-prereq.sql

# 3. core → notif 순서로 update (계정·스키마를 바꿔가며)
docker run --rm --network motif-ddl-verify \
  -v "$PWD/backend/app-core-api/src/main/resources:/cl" liquibase/liquibase:4.31 \
  --url=jdbc:postgresql://motif-ddl-pg:5432/motif \
  --username=core_app --password=core_app \
  --default-schema-name=motif_core --liquibase-schema-name=motif_core \
  --search-path=/cl --changelog-file=db/changelog/db.changelog-master.yaml update

docker run --rm --network motif-ddl-verify \
  -v "$PWD/backend/app-notification/src/main/resources:/cl" liquibase/liquibase:4.31 \
  --url=jdbc:postgresql://motif-ddl-pg:5432/motif \
  --username=notif_app --password=notif_app \
  --default-schema-name=motif_notif --liquibase-schema-name=motif_notif \
  --search-path=/cl --changelog-file=db/changelog/db.changelog-master.yaml update
```

정리: `docker rm -f motif-ddl-pg && docker network rm motif-ddl-verify`

---

## (a) 테이블별 명세 대조

컬럼 수·타입·NULL 여부·기본값을 `information_schema.columns` 로 덤프해 명세 표와
1:1 대조했다. **38개 테이블 전부 일치**하며 불일치는 없다.

`chk`/`fk`/`uq` 는 제약 개수, `idx` 는 제약 백킹이 아닌 순수 인덱스 개수다.

| 테이블 | 컬럼 | 명세 | chk | fk | uq | idx | PK 타입 |
|---|---:|---:|---:|---:|---:|---:|---|
| `admins` | 7 | ✅ | 0 | 0 | 1 | 0 | INTEGER |
| `artist_groups` | 8 | ✅ | 0 | 3 | 0 | 1 | INTEGER |
| `artists` | 11 | ✅ | 1 | 4 | 1 | 2 | INTEGER |
| `asset_purchases` | 7 | ✅ | 1 | 3 | 1 | 1 | **BIGINT** |
| `challenge_comment_likes` | 3 | ✅ | 0 | 2 | 0 | 0 | 복합 |
| `challenge_comments` | 10 | ✅ | 2 | 4 | 0 | 3 | **BIGINT** |
| `challenge_likes` | 3 | ✅ | 0 | 2 | 0 | 0 | 복합 |
| `challenge_participants` | 7 | ✅ | 2 | 2 | 1 | 0 | 복합 |
| `challenges` | 14 | ✅ | 5 | 2 | 0 | 2 | INTEGER |
| `companies` | 7 | ✅ | 0 | 2 | 0 | 1 | INTEGER |
| `composite_jobs` | 17 | ✅ | 5 | 4 | 1 | 2 | **BIGINT** |
| `countries` | 8 | ✅ | 1 | 2 | 1 | 0 | INTEGER |
| `edited_challenges` | 7 | ✅ | 1 | 1 | 1 | 0 | INTEGER |
| `motif_asset_prices` | 10 | ✅ | 1 | 4 | 1 | 0 | INTEGER |
| `motif_assets` | 11 | ✅ | 2 | 1 | 0 | 1 | INTEGER |
| `motif_pass_prices` | 11 | ✅ | 1 | 4 | 1 | 0 | INTEGER |
| `motif_passes` | 11 | ✅ | 1 | 3 | 2 | 2 | INTEGER |
| `motif_results` | 12 | ✅ | 1 | 4 | 1 | 3 | **BIGINT** |
| `notice_comment_likes` | 3 | ✅ | 0 | 2 | 0 | 0 | 복합 |
| `notice_comments` | 10 | ✅ | 2 | 4 | 0 | 3 | **BIGINT** |
| `notice_images` | 4 | ✅ | 1 | 1 | 1 | 0 | INTEGER |
| `notice_likes` | 3 | ✅ | 0 | 2 | 0 | 0 | 복합 |
| `notices` | 8 | ✅ | 2 | 1 | 0 | 1 | INTEGER |
| `oauth_providers` | 3 | ✅ | 1 | 0 | 1 | 0 | INTEGER |
| `orders` | 18 | ✅ | 6 | 3 | 2 | 2 | **BIGINT** |
| `participation_videos` | 20 | ✅ | 5 | 5 | 1 | 4 | **BIGINT** |
| `refresh_tokens` | 7 | ✅ | 1 | 1 | 1 | 2 | **BIGINT** |
| `reports` | 12 | ✅ | 3 | 6 | 4 | 1 | **BIGINT** |
| `shop_banners` | 6 | ✅ | 1 | 0 | 1 | 0 | INTEGER |
| `songs` | 5 | ✅ | 0 | 1 | 0 | 0 | INTEGER |
| `subscriptions` | 3 | ✅ | 0 | 2 | 0 | 1 | 복합 |
| `user_accounts` | 5 | ✅ | 1 | 1 | 1 | 0 | INTEGER |
| `user_oauth_accounts` | 5 | ✅ | 0 | 2 | 2 | 0 | INTEGER |
| `users` | 9 | ✅ | 2 | 1 | 0 | 1 | INTEGER |
| `video_comment_likes` | 3 | ✅ | 0 | 2 | 0 | 0 | 복합 |
| `video_comments` | 10 | ✅ | 2 | 4 | 0 | 3 | **BIGINT** |
| `video_hides` | 3 | ✅ | 0 | 2 | 0 | 0 | 복합 |
| `video_likes` | 3 | ✅ | 0 | 2 | 0 | 1 | 복합 |
| `device_tokens` (notif) | 8 | ✅ | 1 | **0** | 1 | 1 | INTEGER |

### 명세 규칙 전역 확인

| 규칙 | 확인 방법 | 결과 |
|---|---|---|
| BIGINT PK 는 명세 지정 9종만 | 위 표 PK 타입 열 | ✅ `refresh_tokens`, `participation_videos`, 댓글 3종, `reports`, `orders`, `asset_purchases`, `motif_results`, `composite_jobs` |
| PK 는 `GENERATED BY DEFAULT AS IDENTITY` | `is_identity='YES'` 집계 | ✅ 29개 전부 `BY DEFAULT` (38 − 복합PK 9 = 29) |
| 시간은 전부 TIMESTAMPTZ | 컬럼 덤프 | ✅ `timestamp without time zone` 0건 |
| `mod_dttm` 은 기본값 없이 NULL | 컬럼 덤프 | ✅ 전 테이블 default 없음 |
| 금액은 NUMERIC(10,2), FLOAT 금지 | 컬럼 덤프 | ✅ `amount` 4곳 전부 NUMERIC(10,2), `double precision` 0건 |
| FK 삭제 정책 기본 RESTRICT | `pg_constraint.confdeltype` 집계 | ✅ core 89건 전부 `r`(RESTRICT), CASCADE 0건 |
| 교차 스키마 FK 금지 | `motif_notif` FK 집계 | ✅ 0건 |
| 복합 PK 9종 | 위 표 | ✅ 명세 "복합 PK" 목록과 일치 |
| UNIQUE / 부분 UNIQUE | 제약 26 + 부분 UNIQUE 인덱스 2 | ✅ 명세 목록 전수 (`uq_shop_banners_order` 포함) |
| 일반 인덱스 | `idx_` 접두 집계 | ✅ core 29 + notif 1 = 30, 명세 목록과 일치 |
| 부분/표현식 인덱스 4종 | 인덱스 정의 | ✅ `ix_orders_pending_expiry`, `ix_motif_passes_active_expiry`, `ix_refresh_tokens_active`, `ix_composite_jobs_stuck` |
| pg_trgm GIN 2종 | 인덱스 정의 | ✅ `gin_users_nickname`(부분), `gin_groups_name` |
| Liquibase 이력을 소유 스키마에 | `information_schema.tables` | ✅ `motif_core`·`motif_notif` 각각 `databasechangelog`, `databasechangeloglock` |
| 스키마명 하드코딩 없음 | changelog 전문 grep | ✅ 실행 SQL 에 `motif_core`/`motif_notif` 0건 (헤더 주석 3건만) — `search_path` + `--default-schema-name` 위임 |
| ⚠️ 롤명 하드코딩 1건 | 같은 grep | `0003-03` 의 `GRANT SELECT ON subscriptions TO notif_app`. 지시서 8번은 스키마 하드코딩만 금지하므로 규약 위반은 아니지만, 계정명을 바꾸면 이 changeSet 이 깨진다 — DDL_QUESTIONS Q5 |

### v0.13 → v0.15 누적 결정 체크리스트

| 항목 | 결과 |
|---|---|
| `notifications` 테이블 없음, `motif_notif` 는 `device_tokens` 만 | ✅ |
| `challenges`·`participation_videos` 에 `title` 없음 | ✅ |
| `motif_assets.title VARCHAR(100) NOT NULL` 있음 (`description` 과 별개) | ✅ |
| `users.recent_search_terms` 없음 | ✅ |
| `countries.reg_usr_seq` **NULL 허용** (v0.14 R9) | ✅ nullable |
| `motif_results.mod_dttm TIMESTAMPTZ NULL` **추가** (v0.15 R9 · Q2) | ✅ 11번 컬럼, 기본값 없음 |
| `users` 빈 CHECK 블록 제거 (v0.15 R9 · Q3) | ✅ 문서 정리분 — changelog 는 처음부터 실재 CHECK 2개만 구현 |
| 시드 `KR` 이 `reg_usr_seq=NULL`, `country_name='대한민국'` (v0.15 확정) | ✅ 실측 `KR / 대한민국 / reg_usr_seq=NULL` |
| 시드 `oauth_providers` = `GOOGLE` | ✅ |
| `motif_passes.canceled_dttm` NULL + `>= started_dttm` CHECK | ✅ T10 |
| `challenge_participants` 복합 PK·bbox 4컬럼·단일비트 mask·mask UNIQUE | ✅ T1~T3, T13 |
| `edited_challenges` mask UNIQUE + `>= 0` CHECK | ✅ T12 |
| `composite_jobs.attempt SMALLINT DEFAULT 1 CHECK >= 1` | ✅ |
| `composite_jobs` MOTIF 이면 `edited_challenge_seq` NULL 강제 | ✅ T5 |
| `shop_banners.display_order` UNIQUE | ✅ |
| 거울 카운터 INT, `view_count` 만 BIGINT, 전부 `DEFAULT 0 CHECK >= 0` | ✅ |
| `reports` 4대상 XOR CHECK | ✅ T7 |
| 댓글 Depth 2 구조 (self FK + 자기참조 금지 CHECK) | ✅ |
| `orders` 상태별 CHECK | ✅ T9 |

---

## (b) 제약 동작 검증 — 위반 INSERT 16건

전체를 하나의 트랜잭션에서 실행하고 `ROLLBACK` 으로 정리했다. 각 위반 문장은
PL/pgSQL `EXCEPTION` 블록(하위 트랜잭션)으로 감싸 실패 문장만 되돌린다.
먼저 **명세상 유효한 픽스처 행 20여 개를 전부 성공적으로 삽입**해
스키마가 정상 사용을 막지 않는 것도 함께 확인했다.

| # | 시나리오 | 차단한 제약 | 결과 |
|---|---|---|---|
| T1 | `challenge_participants.participant_mask = 3` (다중 비트) | `ck_challenge_participants_mask` | ✅ |
| T2 | `challenge_participants.participant_mask = -1` (음수) | `ck_challenge_participants_mask` | ✅ |
| T3 | bbox 좌표 역전 (`right(50) <= left(100)`) | `ck_challenge_participants_bbox` | ✅ |
| T4 | `composite_jobs` `status='FAILED'` + `fail_code` NULL | `ck_composite_jobs_status_shape` | ✅ |
| T5 | `composite_jobs` `job_type='MOTIF'` + `edited_challenge_seq` 지정 | `ck_composite_jobs_type_shape` | ✅ |
| T6 | `motif_asset_prices.amount = -100.00` (금액 음수) | `ck_motif_asset_prices_amount` | ✅ |
| T7 | `reports` 대상 2개 동시 지정 (`video_seq` + `challenge_comment_seq`) | `ck_reports_exactly_one_target` | ✅ |
| T8 | `reports` `status='ACCEPTED'` + `processed_usr_seq` NULL | `ck_reports_processed` | ✅ |
| T9 | `orders` `product_type='ASSET'` + `artist_seq` 동시 지정 | `ck_orders_product_shape` | ✅ |
| T10 | `motif_passes.canceled_dttm < started_dttm` | `ck_motif_passes_values` | ✅ |
| T11 | `users.role = 'SUPERUSER'` (허용값 밖) | `ck_users_role` | ✅ |
| T12 | `edited_challenges.participant_mask = -1` | `ck_edited_challenges_mask` | ✅ |
| T13 | 같은 챌린지 안에서 mask 중복 배정 | `uq_challenge_participants_mask` | ✅ |
| T14a | 동일 (user, asset) 두 번째 ACTIVE 구매 | `uq_asset_purchases_active` (부분 UNIQUE) | ✅ |
| T14b | 환불(REFUNDED) 후 재구매 ACTIVE — **허용되어야 함** | 부분 UNIQUE 의 의도된 통과 | ✅ 2행 공존 |
| T15 | 동일 신고자 × 동일 영상 재신고 | `uq_reports_reporter_video` | ✅ |
| T16 | `device_tokens.user_seq = 999999` (없는 사용자) — **허용되어야 함** | 교차 스키마 FK 부재 확인 | ✅ 삽입 성공 |

> T14 는 1차 실행에서 `pk_asset_purchases` 가 먼저 걸려 부분 UNIQUE 를 실제로 겨냥하지
> 못했다(픽스처가 `purchase_seq` 를 명시했고 `GENERATED BY DEFAULT` 는 명시값으로
> 시퀀스가 전진하지 않는다). PK 를 명시적으로 분리해 재실행한 결과가 위 표의 값이다.

---

## (c) 스키마 권한 격리

| 검증 | 기대 | 실측 |
|---|---|---|
| 스키마 소유자 | `motif_core`=`core_app`, `motif_notif`=`notif_app` | ✅ 일치 |
| `notif_app` → `motif_core.users` | 거부 | ✅ `ERROR: permission denied for table users` |
| `notif_app` → `motif_core.subscriptions` | 허용 | ✅ 성공 (0행) |
| `notif_app` 의 `motif_core` 권한 전량 | `subscriptions / SELECT` 한 건만 | ✅ 정확히 한 건 |
| `core_app` → `motif_notif.device_tokens` | 거부 | ✅ `ERROR: permission denied for schema motif_notif` |

`notif_app` 은 `motif_core` 스키마 `USAGE`(선행 SQL) + `subscriptions` 테이블
`SELECT`(changeSet `0003-03`) 조합으로만 팬아웃 조회가 가능하다.

---

## (d) 멱등성

```
core  : Run: 0   Previously run: 50   update was executed successfully
notif : Run: 0   Previously run:  1   update was executed successfully
```

---

## v0.15 재검증 (2차 · 최종)

R9 회신으로 기준 명세가 v0.14 → v0.15 로 교체되고 `motif_results.mod_dttm` 이
추가되면서, **로컬 DB 를 완전히 초기화한 뒤 전체를 다시 검증했다.**

### 반영 방식 — 원본 changeSet 수정 (일회성 예외)

`0010-02-create-motif-results` 원본에 컬럼을 넣었다. 신규 changeSet 원칙의 예외이며
**해당 changeSet 이 아직 커밋·배포되지 않았기 때문**이다(R9 승인).
배포 이후에는 `ALTER TABLE ... ADD COLUMN` 신규 changeSet 을 쓴다.

```sql
    reg_dttm      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dttm      TIMESTAMPTZ  NULL,      -- ← v0.15 Q2 보정
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
```

### 재검증 결과

| 항목 | 결과 |
|---|---|
| 컨테이너·볼륨 완전 재생성 후 `manual-prereq.sql` | ✅ 무에러 |
| 빈 DB core update | ✅ `Run: 50 / Previously run: 0`, `Rows affected: 52` |
| 빈 DB notif update | ✅ `Run: 1 / Previously run: 0` |
| 재실행 no-op — core | ✅ `Run: 0 / Previously run: 50` |
| 재실행 no-op — notif | ✅ `Run: 0 / Previously run: 1` |
| `motif_results` 컬럼 순서 | ✅ `… 10 reg_dttm / 11 mod_dttm / 12 is_deleted` — v0.15 §36 표 순서와 동일 |
| `mod_dttm` 속성 | ✅ `TIMESTAMPTZ`, nullable, **기본값 없음** ("적용 기준" 준수) |
| core 테이블 수 | ✅ 38 (변동 없음) |
| 시드 | ✅ `KR / 대한민국 / reg_usr_seq=NULL`, `GOOGLE` |

Q3(빈 CHECK 블록 제거)·Q4(감사 컬럼 부재 의도 확정)·Q8·Q9 는 **문서 확정 사항이라
changelog 변경이 없다.** Q6 도 현행 해법이 v0.15 운영 기준에 명문화되어 변경 없다.
따라서 changeSet 총수는 1차와 동일한 50 + 1 이다.

---

## 발견한 결함 (수정 완료)

### `manual-prereq.sql` — `pg_trgm` 이 search_path 에 없어 GIN 인덱스 생성 실패

**증상.** 1차 검증에서 `0012-05-create-gin-users-nickname` 이 죽었다.

```
ERROR: operator class "gin_trgm_ops" does not exist for access method "gin"
```

**원인.** `CREATE EXTENSION pg_trgm` 이 `public` 에 설치되는데 `core_app` 의
`search_path` 를 `motif_core` 만으로 고정해 연산자 클래스가 보이지 않았다.
`pg_extension` 행만 확인하는 precondition 은 이 상태를 통과시킨다 — 확장이 "존재"는
하기 때문이다.

**수정.** changelog 는 손대지 않고(스키마 하드코딩 금지 규약 유지) 선행 SQL 만 고쳤다.

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;
ALTER ROLE core_app  SET search_path = motif_core, public;
ALTER ROLE notif_app SET search_path = motif_notif, public;
```

생성 대상 스키마는 `search_path` 첫 항목이므로 앱 스키마가 앞에 온다.
`public` 은 PostgreSQL 15+ 에서 PUBLIC 의 CREATE 권한이 회수된 상태라 쓰기 위험이 없다.

**명세 반영 완료.** R9 회신으로 현행 해법이 그대로 승인되어 v0.15 "Liquibase 운영 기준"에
명문화됐다(대안으로 `public.gin_trgm_ops` 스키마 한정도 허용). changelog 변경은 없다.

**운영 영향 — 여전히 대조 대기.** 운영 원본인 infra 라인 `configmap-init.yaml` 에 이
설정이 없으면 **운영 배포에서 같은 오류로 마이그레이션이 실패한다.**
→ [DDL_QUESTIONS.md Q6](DDL_QUESTIONS.md) 에서 추적한다.

같은 검증에서 나온 또 한 건도 v0.15 운영 기준에 반영됐다 — `GENERATED BY DEFAULT`
컬럼에 **수동 INSERT 로 PK 를 명시하면 시퀀스가 전진하지 않아 이후 자동 발급이
충돌한다**(T14 실측). 시드 changeSet 은 이미 PK 를 명시하지 않는다.

---

## application.yml 제안 (코드로 넣지 않았음)

`app-core-api` 와 `app-notification` 의 `spring.liquibase` 블록은 **이미 필요한 값이
설정되어 있어 변경할 것이 없다.** 현재 상태가 지시서 요구사항(모듈별 스키마,
이력 테이블을 자기 스키마에)을 그대로 만족한다.

```yaml
# app-core-api/src/main/resources/application.yml — 현재 상태, 수정 불필요
spring:
  liquibase:
    change-log: classpath:db/changelog/db.changelog-master.yaml
    default-schema: motif_core
    liquibase-schema: motif_core
```

`app-notification` 은 동일 구조에 `motif_notif` 다. 다만 **한 가지가 빠져 있다.**

### 제안 1. 마이그레이션 실행 계정을 소유 계정으로 분리

명세의 "소유 계정 마이그레이션" 원칙(core changelog 는 `core_app`, notif 는 `notif_app`)이
성립하려면 각 모듈의 `spring.datasource` 사용자가 자기 스키마 소유 계정이어야 한다.
현재 프로파일별 datasource 계정이 이 조건을 만족하는지 확인이 필요하다.
Liquibase 전용 계정을 따로 쓸 경우:

```yaml
spring:
  liquibase:
    user: ${DB_MIGRATION_USER}       # core_app / notif_app
    password: ${DB_MIGRATION_PASSWORD}
```

비밀값은 placeholder fallback 을 두지 않는다(ADR-0004 설정 규율).

### 제안 2. `ddl-auto: validate` 와의 정합

명세는 `ddl-auto: validate` 를 전 프로파일 고정한다. 엔티티 작성 전 단계에서는
검증 대상이 없어 무해하지만, JPA 엔티티가 들어오면 `mod_dttm` 이 INSERT 시
자동 채워지지 않도록 Auditing 설정을 함께 확인해야 한다(명세 "Liquibase 운영 기준").

---

## 산출물

| 경로 | 내용 |
|---|---|
| `backend/app-core-api/src/main/resources/db/changelog/db.changelog-master.yaml` | core master (기존 스텁에 include 13개 추가) |
| `backend/app-core-api/src/main/resources/db/changelog/sql/0001~0013-*.sql` | core changeSet 50개 |
| `backend/app-notification/src/main/resources/db/changelog/db.changelog-master.yaml` | notif master (include 1개 추가) |
| `backend/app-notification/src/main/resources/db/changelog/sql/0001-device-tokens.sql` | notif changeSet 1개 |
| `backend/docs/db/manual-prereq.sql` | 수동 선행 SQL (스키마·계정·GRANT USAGE·pg_trgm·search_path·격리 검증 쿼리) |
| `backend/docs/DDL_QUESTIONS.md` | 질문 9건(종결 6 / infra 대조 대기 2 / 미결 1) + 지시서 대비 편차 8건 |
| `backend/docs/DDL_VERIFICATION.md` | 본 문서 |
