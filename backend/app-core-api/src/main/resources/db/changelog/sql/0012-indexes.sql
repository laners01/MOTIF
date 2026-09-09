--liquibase formatted sql
--
-- 0012 부분·표현식 인덱스와 pg_trgm GIN
-- 명세: backend/docs/erd/MOTIF_DB_SPEC_v0_15.md "부분/표현식 인덱스", "pg_trgm GIN"
--
-- 일반 인덱스와 부분 UNIQUE 는 각 테이블 파일에 함께 두었다(테이블 = 하나의 논리 단위).
-- 이 파일에는 표현식/조건 인덱스와 확장 의존 인덱스만 모은다.
--
-- 0012-01 ~ 0012-02 는 1시간 주기 ShedLock 배치(주문 만료 정리 + PASS 만료 전이) 전용이다.
-- 0012-05 ~ 0012-06 은 pg_trgm 확장을 요구한다. 확장은 changelog 밖(superuser)에서
-- 설치되므로 precondition(onFail:HALT)으로 존재를 확인하고 안내 메시지를 남긴다.

--changeset claude:0012-01-create-ix-orders-pending-expiry
--comment PENDING 주문 만료 정리 배치 전용 부분 인덱스
CREATE INDEX ix_orders_pending_expiry
    ON orders (expires_dttm)
 WHERE status = 'PENDING';
--rollback DROP INDEX ix_orders_pending_expiry;

--changeset claude:0012-02-create-ix-motif-passes-active-expiry
--comment ACTIVE PASS 만료 전이 배치 전용 부분 인덱스
CREATE INDEX ix_motif_passes_active_expiry
    ON motif_passes (expires_dttm)
 WHERE status = 'ACTIVE';
--rollback DROP INDEX ix_motif_passes_active_expiry;

--changeset claude:0012-03-create-ix-refresh-tokens-active
--comment 미폐기 refresh token 조회용 부분 인덱스
CREATE INDEX ix_refresh_tokens_active
    ON refresh_tokens (user_seq, expires_dttm)
 WHERE revoked_dttm IS NULL;
--rollback DROP INDEX ix_refresh_tokens_active;

--changeset claude:0012-04-create-ix-composite-jobs-stuck
--comment 정체 잡 스윕용 표현식+부분 인덱스. COALESCE(mod_dttm, reg_dttm) 기준 15분 초과 탐색
CREATE INDEX ix_composite_jobs_stuck
    ON composite_jobs (status, COALESCE(mod_dttm, reg_dttm))
 WHERE status IN ('QUEUED','PROCESSING');
--rollback DROP INDEX ix_composite_jobs_stuck;

--changeset claude:0012-05-create-gin-users-nickname
--preconditions onFail:HALT onError:HALT onFailMessage:pg_trgm 확장이 설치되지 않았다. superuser 로 backend/docs/db/manual-prereq.sql 의 CREATE EXTENSION pg_trgm 을 먼저 실행한 뒤 재시도하라 (운영 원본은 infra 라인 configmap-init.yaml).
--precondition-sql-check expectedResult:1 SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'
--comment 닉네임 검색은 아티스트만 대상이므로 부분 GIN
CREATE INDEX gin_users_nickname
    ON users USING gin (nickname gin_trgm_ops)
 WHERE role = 'ARTIST' AND is_deleted = FALSE;
--rollback DROP INDEX gin_users_nickname;

--changeset claude:0012-06-create-gin-groups-name
--preconditions onFail:HALT onError:HALT onFailMessage:pg_trgm 확장이 설치되지 않았다. superuser 로 backend/docs/db/manual-prereq.sql 의 CREATE EXTENSION pg_trgm 을 먼저 실행한 뒤 재시도하라 (운영 원본은 infra 라인 configmap-init.yaml).
--precondition-sql-check expectedResult:1 SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'
--comment 그룹명 검색 GIN. 곡 검색용 GIN 은 두지 않는다
CREATE INDEX gin_groups_name
    ON artist_groups USING gin (group_name gin_trgm_ops);
--rollback DROP INDEX gin_groups_name;
