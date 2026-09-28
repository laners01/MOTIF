--liquibase formatted sql
--
-- 0017 챌린지 슬롯 앵커 · 편집본 선택 화면 이미지 — S15P21D104-618
-- 명세: backend/docs/erd/MOTIF_DB_SPEC_v0_15.md §13·§14 (v0.16 변경)
--
-- 촬영 전 "함께 챌린지하고 싶은 아티스트를 선택" 화면(시안 10-1~3)이 슬롯마다 썸네일 위에
-- 프로필 원을 띄운다. bbox(원본 px 사각형)는 이 자리를 주지 못한다 — 사각형 중심은 몸통
-- 가운데지 머리 옆이 아니고, 원본 width/height 를 저장하지 않아 비율로 바꿀 수도 없다.
-- 그래서 bbox 를 버리고 앵커(x·y·r, 썸네일 대비 0~1 비율)로 바꾼다.
--
-- 기존 행은 살린다 — 앵커·선택 화면 키를 NULL 로 두고 어드민 상세에서 채운다
-- (PATCH /challenges/{seq}/participants/{artistSeq} · PATCH /edited-challenges/{seq}).
-- 신규 등록은 서비스가 필수로 받는다. NULL 은 "0017 이전 행" 이라는 뜻이다.
--
-- (주석 줄을 "changeset" 으로 시작하지 않는다 — formatted SQL 파서가 선언으로 읽는다.)

--changeset claude:0017-01-participants-anchor
--comment bbox 4컬럼 → 앵커 3컬럼. 비율이라 썸네일이 몇 px 로 그려지든 곱하면 자리가 맞는다
ALTER TABLE challenge_participants DROP CONSTRAINT ck_challenge_participants_bbox;
ALTER TABLE challenge_participants
    DROP COLUMN bbox_left_top_x,
    DROP COLUMN bbox_left_top_y,
    DROP COLUMN bbox_right_bottom_x,
    DROP COLUMN bbox_right_bottom_y;
ALTER TABLE challenge_participants
    ADD COLUMN anchor_x DOUBLE PRECISION NULL,
    ADD COLUMN anchor_y DOUBLE PRECISION NULL,
    ADD COLUMN anchor_r DOUBLE PRECISION NULL,
    ADD CONSTRAINT ck_challenge_participants_anchor CHECK (
        (anchor_x IS NULL AND anchor_y IS NULL AND anchor_r IS NULL)
        OR (anchor_x BETWEEN 0 AND 1 AND anchor_y BETWEEN 0 AND 1 AND anchor_r > 0 AND anchor_r <= 0.5)
    );
--rollback ALTER TABLE challenge_participants DROP CONSTRAINT ck_challenge_participants_anchor, DROP COLUMN anchor_x, DROP COLUMN anchor_y, DROP COLUMN anchor_r, ADD COLUMN bbox_left_top_x INTEGER NOT NULL DEFAULT 0, ADD COLUMN bbox_left_top_y INTEGER NOT NULL DEFAULT 0, ADD COLUMN bbox_right_bottom_x INTEGER NOT NULL DEFAULT 1, ADD COLUMN bbox_right_bottom_y INTEGER NOT NULL DEFAULT 1;

--changeset claude:0017-02-edited-preview-image
--comment mask 당 선택 상태 이미지 1장(AI팀 산출). mask 0(무선택)은 행이 없고 썸네일이 그 화면이다
ALTER TABLE edited_challenges
    ADD COLUMN preview_image_key VARCHAR(255) NULL;
--rollback ALTER TABLE edited_challenges DROP COLUMN preview_image_key;
