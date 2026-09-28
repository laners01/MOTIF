-- =============================================================================
-- MOTIF PostgreSQL 수동 선행 SQL (superuser 1회 실행)
--
-- ** 운영 원본은 infra 라인(`develop/infra`)의 configmap-init.yaml 이다. **
-- 본 파일은 로컬 검증·문서용 사본이며 배포에 사용하지 않는다.
-- 내용이 configmap-init.yaml 과 어긋나면 backend/docs/DDL_QUESTIONS.md 에 기록한다.
--
-- 근거 명세: backend/docs/erd/MOTIF_DB_SPEC_v0_15.md
--   - "적용 기준" — 스키마 소유 분리, 교차 스키마 FK 금지, 팬아웃 SELECT 권한
--   - "Liquibase 운영 기준" — pg_trgm 은 관리자 계정으로 1회 설치(changelog 밖)
--
-- 실행:
--   psql -v ON_ERROR_STOP=1 -U postgres -d motif -f manual-prereq.sql
-- =============================================================================

-- 데이터베이스는 선행 생성되어 있다고 가정한다. 없으면 아래를 먼저 실행한다.
--   CREATE DATABASE motif ENCODING 'UTF8' LC_COLLATE 'C' LC_CTYPE 'C' TEMPLATE template0;

-- -----------------------------------------------------------------------------
-- 1. 확장 — superuser 전용. changelog 에 넣지 않는다(명세 Liquibase 운영 기준).
--    trgm GIN 인덱스 changeSet 이 precondition(onFail:HALT)으로 존재를 확인한다.
-- -----------------------------------------------------------------------------
-- 확장은 public 에 설치한다. 두 앱 스키마 어느 쪽에도 두지 않는 이유:
-- motif_core 에 두면 스키마 재생성 시 확장이 함께 사라지고, 인덱스 정의에
-- 스키마를 하드코딩해야 한다(changelog 규약 위반).
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;

-- -----------------------------------------------------------------------------
-- 2. 애플리케이션 계정
--    비밀번호는 로컬 검증용 더미다. 운영은 k8s Secret 주입값을 사용하며
--    비밀값 placeholder fallback 을 두지 않는다(ADR-0004 설정 규율).
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'core_app') THEN
        CREATE ROLE core_app LOGIN PASSWORD 'core_app';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notif_app') THEN
        CREATE ROLE notif_app LOGIN PASSWORD 'notif_app';
    END IF;
END
$$;

-- -----------------------------------------------------------------------------
-- 3. 스키마 — 소유권 분리. 각 계정이 자기 스키마만 소유한다.
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS motif_core  AUTHORIZATION core_app;
CREATE SCHEMA IF NOT EXISTS motif_notif AUTHORIZATION notif_app;

-- -----------------------------------------------------------------------------
-- 4. search_path — changelog 가 스키마명을 하드코딩하지 않는다.
--    Liquibase 의 default-schema 와 이중으로 보장한다.
--    public 을 뒤에 붙이는 이유: pg_trgm 의 gin_trgm_ops 연산자 클래스가
--    search_path 에 없으면 GIN 인덱스 생성이 "operator class does not exist"
--    로 실패한다. 확장 존재만 확인하는 precondition 은 이 경우를 못 잡는다.
--    생성 대상 스키마는 첫 항목이므로 앱 스키마가 앞에 온다.
-- -----------------------------------------------------------------------------
ALTER ROLE core_app  SET search_path = motif_core, public;
ALTER ROLE notif_app SET search_path = motif_notif, public;

-- -----------------------------------------------------------------------------
-- 5. 격리 — 서로의 스키마에 접근하지 못한다.
--    PUBLIC 은 신규 스키마에 기본 권한이 없으나 명시적으로 회수한다.
-- -----------------------------------------------------------------------------
REVOKE ALL ON SCHEMA motif_core  FROM PUBLIC;
REVOKE ALL ON SCHEMA motif_notif FROM PUBLIC;

-- 알림 팬아웃 예외: notif_app 이 motif_core 스키마를 탐색할 수 있어야 한다.
-- 테이블 단위 SELECT 권한은 subscriptions 생성 직후 changeSet 이 부여한다
-- (0003-artists.sql / changeset 0003-04). 여기서는 스키마 USAGE 까지만 연다.
GRANT USAGE ON SCHEMA motif_core TO notif_app;

-- -----------------------------------------------------------------------------
-- 6. 격리 검증 쿼리 — changelog 적용 이후 수동 확인용
-- -----------------------------------------------------------------------------
-- (a) 스키마 소유자
--   SELECT nspname, pg_get_userbyid(nspowner) AS owner
--     FROM pg_namespace WHERE nspname IN ('motif_core','motif_notif');
--   기대: motif_core=core_app, motif_notif=notif_app
--
-- (b) 확장 설치 확인
--   SELECT extname FROM pg_extension WHERE extname = 'pg_trgm';
--   기대: 1행
--
-- (c) notif_app 은 motif_core.users 를 읽을 수 없다
--   \c motif notif_app
--   SELECT * FROM motif_core.users LIMIT 1;          --> ERROR: permission denied
--
-- (d) notif_app 은 motif_core.subscriptions 만 읽을 수 있다
--   SELECT * FROM motif_core.subscriptions LIMIT 1;  --> 성공(0행)
--
-- (e) 권한 매트릭스
--   SELECT table_schema, table_name, privilege_type
--     FROM information_schema.table_privileges
--    WHERE grantee = 'notif_app' AND table_schema = 'motif_core';
--   기대: subscriptions / SELECT 한 행만
