--liquibase formatted sql
--
-- 0002 device_tokens 쓰기 권한을 core_app 에 부여 — §14.1·§14.2
--
-- 토큰 등록·해제는 사용자 API(/api/v1/notifications/device-tokens)라 core-api 가
-- 받는다. 그런데 device_tokens 는 motif_notif 소유이고 init 스크립트가
-- REVOKE ALL ON SCHEMA motif_notif FROM core_app 을 걸어둬 손을 댈 수 없다.
--
-- motif_core.subscriptions 를 notif_app 에 읽기로 연 것(0003-03)과 정확히 대칭인
-- 예외다. 방향만 반대고, 여는 범위도 테이블 하나로 같다.
--
-- ** init 스크립트(configmap-init.yaml)가 아니라 여기서 여는 이유 **
-- 그 스크립트는 데이터 디렉터리가 비어 있을 때 한 번만 돈다. 이미 초기화된
-- 운영 DB 에는 고쳐도 반영되지 않는다. 소유자(notif_app)가 실행하는 이 changelog
-- 만이 지금 살아 있는 DB 에 권한을 줄 수 있다.

--changeset claude:0002-01-grant-schema-usage-core
--comment 스키마 출입 자격. USAGE 만으로는 어떤 테이블도 못 읽어 격리는 유지된다
GRANT USAGE ON SCHEMA motif_notif TO core_app;
--rollback REVOKE USAGE ON SCHEMA motif_notif FROM core_app;

--changeset claude:0002-02-grant-device-tokens-core
--comment 등록(INSERT)·재할당/해제(UPDATE)·존재 확인(SELECT). DELETE 는 주지 않는다 — 해제는 is_active 다
GRANT SELECT, INSERT, UPDATE ON device_tokens TO core_app;
--rollback REVOKE SELECT, INSERT, UPDATE ON device_tokens FROM core_app;

--changeset claude:0002-03-grant-device-tokens-sequence
--comment IDENTITY 컬럼의 암묵 시퀀스. 이게 없으면 INSERT 가 permission denied for sequence 로 떨어진다
GRANT USAGE, SELECT ON SEQUENCE device_tokens_token_seq_seq TO core_app;
--rollback REVOKE USAGE, SELECT ON SEQUENCE device_tokens_token_seq_seq FROM core_app;
