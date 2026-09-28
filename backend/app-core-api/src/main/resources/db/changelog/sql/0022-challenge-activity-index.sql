--liquibase formatted sql
--
-- 0022 내 활동(§4.9) 챌린지 쪽 조회 인덱스
-- 명세: backend/docs/api/detail/04-user.md §4.9
--
-- §4.9 가 챌린지 원본까지 포함하게 되면서 challenge_likes·challenge_comments 도
-- user_seq 로 들어온다. 댓글 쪽은 0006 의 idx_challenge_comments_user
-- (user_seq, reg_dttm DESC) 가 이미 받는데, 좋아요 쪽은 PK 가
-- (challenge_seq, user_seq) 라 user_seq 가 선행 칼럼이 아니어서 이 조회만
-- challenge_likes 전체를 훑는다. 짝을 맞춰 준다.
--
-- 참여 영상 쪽에서 같은 이유로 0016 이 idx_video_comments_user_dttm 을 넣었다 —
-- 그때는 좋아요에 인덱스가 있고 댓글에 없었고, 여기는 반대다.
--
-- 0012-indexes.sql 에 넣지 않는 이유: 그 파일은 이미 운영 DB 에 적용됐고, 적용이 끝난
-- 변경 단위를 고치면 Liquibase 가 체크섬 불일치로 기동을 막는다.
--
-- (주석 줄을 "changeset" 으로 시작하지 않는다 — formatted SQL 파서가 선언으로 읽는다.)

--changeset claude:0022-01-create-ix-challenge-likes-user-dttm
--comment §4.9 내 활동 — 내가 누른 챌린지 좋아요를 최신순으로. 댓글 쪽 인덱스와 같은 모양
CREATE INDEX idx_challenge_likes_user_dttm
    ON challenge_likes (user_seq, reg_dttm DESC);
--rollback DROP INDEX idx_challenge_likes_user_dttm;
