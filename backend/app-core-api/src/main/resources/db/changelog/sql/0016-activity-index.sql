--liquibase formatted sql
--
-- 0016 내 활동(§4.9) 조회 인덱스
-- 명세: backend/docs/api/detail/04-user.md §4.9
--
-- §4.9 는 "내가 좋아요했거나 댓글을 단 영상"을 합쳐 마지막 상호작용 최신순으로 낸다.
-- 좋아요 쪽은 0005 의 idx_video_likes_user_dttm (user_seq, reg_dttm DESC) 이 이미 받는데,
-- 댓글 쪽은 대상 기준 인덱스(idx_video_comments_target)뿐이라 user_seq 로 들어오는 이
-- 조회가 video_comments 전체를 훑는다. 짝을 맞춰 준다.
--
-- 0012-indexes.sql 에 넣지 않는 이유: 그 파일은 이미 운영 DB 에 적용됐고, 적용이 끝난
-- 변경 단위를 고치면 Liquibase 가 체크섬 불일치로 기동을 막는다.
--
-- (주석 줄을 "changeset" 으로 시작하지 않는다 — formatted SQL 파서가 선언으로 읽는다.)

--changeset claude:0016-01-create-ix-video-comments-user-dttm
--comment §4.9 내 활동 — 내 댓글을 최신순으로. video_likes 쪽 인덱스와 같은 모양
CREATE INDEX idx_video_comments_user_dttm
    ON video_comments (user_seq, reg_dttm DESC);
--rollback DROP INDEX idx_video_comments_user_dttm;
