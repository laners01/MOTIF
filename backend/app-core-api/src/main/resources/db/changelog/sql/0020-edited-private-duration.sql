--liquibase formatted sql
--
-- 0020 편집본을 보호 등급으로 올리고 길이를 남긴다
-- 명세: backend/docs/erd/MOTIF_DB_SPEC_v0_15.md §13 (v0.19 변경)
--
-- 앱이 편집본 영상을 직접 받는 API 가 생긴다(§7.6). 지금까지 편집본은 AI 워커만 S3 에서
-- 내려받았고 앱에는 주소가 나간 적이 없어 공개 프리픽스(edited/)로 충분했다. 앱에 주는
-- 순간 무기명 CDN 링크가 되므로 서명이 필요하다.
--
-- **서명은 프리픽스가 정한다.** MediaKeys.isProtected 는 private/ 하나만 보고,
-- CloudFront 의 "Restrict viewer access" 도 캐시 동작(private/*) 단위다. 그래서 키를
-- 옮기지 않고 코드에서 등급만 올리면 서버는 서명을 붙이는데 CloudFront 가 검증하지 않아
-- 무서명 요청이 그대로 통과한다 — 서명이 장식이 된다. 키를 옮기는 것이 유일한 방법이다.
--
-- 길이는 원래 받고도 버리던 값이다. 어드민 등록 폼이 durationMs 를 실어 보내고 서버는
-- 원본 ±500ms 대조에만 쓰고 있었다. 앱 촬영 타이머가 편집본 길이에 맞아야 해서 남긴다 —
-- 챌린지 원본 길이로 갈음하면 최대 0.5초가 어긋나 녹화 끝이 잘린다.
--
-- (주석 줄을 "changeset" 으로 시작하지 않는다 — formatted SQL 파서가 선언으로 읽는다.)

--changeset claude:0020-01-edited-duration
--comment 등록 폼이 이미 보내는 값을 저장만 한다. 편집본이 없는 슬롯은 NULL
ALTER TABLE challenge_participants
    ADD COLUMN edited_duration_ms INTEGER NULL,
    ADD CONSTRAINT ck_challenge_participants_edited_duration CHECK (
        edited_duration_ms IS NULL OR edited_duration_ms > 0
    );
--rollback ALTER TABLE challenge_participants DROP CONSTRAINT ck_challenge_participants_edited_duration, DROP COLUMN edited_duration_ms;

--changeset claude:0020-02-edited-keys-to-private
--comment edited/ → private/edited/. CloudFront 캐시 동작 private/* 가 서명을 강제하는 자리다
UPDATE challenge_participants
SET edited_challenge_key = 'private/' || edited_challenge_key,
    initial_scene_key    = 'private/' || initial_scene_key
WHERE edited_challenge_key LIKE 'edited/%';
UPDATE challenge_participants
SET preview_image_key = 'private/' || preview_image_key
WHERE preview_image_key LIKE 'edited/%';
--rollback UPDATE challenge_participants SET edited_challenge_key = substring(edited_challenge_key from 9), initial_scene_key = substring(initial_scene_key from 9) WHERE edited_challenge_key LIKE 'private/edited/%';
--rollback UPDATE challenge_participants SET preview_image_key = substring(preview_image_key from 9) WHERE preview_image_key LIKE 'private/edited/%';
