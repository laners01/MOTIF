-- 로컬 격리 모드 DB 초기화 (ADR-0003).
-- 원본: infra/k8s/10-postgres/configmap-init.yaml — 스키마·계정·권한 구조는 거울이며,
-- 비밀번호만 로컬 전용 더미(motif123, 비밀 아님)로 고정한다.
-- 데이터 디렉터리가 비어 있을 때 1회만 실행된다. 재초기화: docker compose down -v

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE SCHEMA motif_core;
CREATE SCHEMA motif_notif;

CREATE USER core_app  WITH PASSWORD 'motif123';   -- 로컬 전용 더미
CREATE USER notif_app WITH PASSWORD 'motif123';   -- 로컬 전용 더미

-- 마이그레이션(Liquibase)을 소유 계정으로 실행하기 위한 스키마 소유권 이전
ALTER SCHEMA motif_core  OWNER TO core_app;
ALTER SCHEMA motif_notif OWNER TO notif_app;

-- 교차 스키마 접근 차단 — 멀티모듈이 컴파일 타임 방어라면 이 GRANT는 런타임 2차 방어
REVOKE ALL ON SCHEMA public       FROM PUBLIC;
REVOKE ALL ON SCHEMA motif_core   FROM notif_app;
REVOKE ALL ON SCHEMA motif_notif  FROM core_app;

-- pg_trgm이 public에 설치되므로 함수 호출을 위한 최소 권한만 부여한다.
-- USAGE만 주면 similarity() 등은 쓰되 테이블 생성은 여전히 막힌다.
GRANT USAGE ON SCHEMA public TO core_app, notif_app;

-- 팬아웃(DB 명세 v0.5 §구독자 팬아웃): 알림 서비스는 motif_core.subscriptions를
-- 읽기 전용으로 조회해 수신자를 얻는다. 여기서는 스키마 USAGE(출입 자격)만 부여한다.
-- 테이블 SELECT(방 열쇠)는 Liquibase가 subscriptions 생성 직후 changeSet으로 부여한다.
GRANT USAGE ON SCHEMA motif_core TO notif_app;

-- 자기 스키마가 앞. public은 pg_trgm 함수(similarity, gin_trgm_ops) 조회용으로만
-- 뒤에 둔다. 순서가 바뀌면 테이블이 public에 생긴다.
ALTER ROLE core_app  SET search_path TO motif_core, public;
ALTER ROLE notif_app SET search_path TO motif_notif, public;

GRANT CONNECT ON DATABASE motif TO core_app, notif_app;
