--liquibase formatted sql
--
-- 0021 CHALLENGE 합성은 언제나 슬롯 한 자리를 대체한다
-- 명세: backend/docs/erd/MOTIF_DB_SPEC_v0_15.md (v0.20) · API 명세 13.1 · AI 서버 명세 v0.5
--
-- 무교체(원본 위 합성)를 폐기한다. 0019 로 편집본이 슬롯 1개당 1개가 되면서 사용자가
-- 고르는 것은 "함께할 아티스트 한 명" 으로 정리됐는데, challenge_participant_seq 가
-- NULL 인 잡이 그 아래에 남아 있었다 — 그 경로는 편집본이 아니라 챌린지 원본을 베이스로
-- 써서 아무도 지워지지 않은 영상 위에 합성이 얹힌다.
--
-- API 명세 16 잔여 3번("무교체 허용 여부는 팀 논의 예정")을 불허로 닫는 changeset 이다.
--
-- 기존 CHECK 는 CHALLENGE 에 challenge_seq 만 요구했다. 여기에
-- challenge_participant_seq IS NOT NULL 을 더한다. MOTIF 쪽 조건은 그대로다 —
-- 그쪽은 여전히 NULL 이어야 한다(교체할 인물이라는 개념이 챌린지의 것이다).
--
-- 칼럼은 NULL 을 허용한 채로 둔다. MOTIF 행이 NULL 을 쓰므로 NOT NULL 로 올릴 수 없고,
-- 타입별 요구는 CHECK 가 표현한다.
--
-- ⚠ **참조가 남은 무교체 잡이 있으면 이 changeset 은 멈춘다.** 01 이 지우는 것은
-- 아무도 가리키지 않는 잡뿐이다 — participation_videos.job_seq 와 motif_results.job_seq
-- 가 ON DELETE RESTRICT 라 참여 영상이 딸린 잡은 지울 수 없다. 그런 잡이 남아 있으면
-- 02 의 ALTER 가 CHECK 위반으로 실패하는데, **그게 맞다**: 이미 게시된 참여 영상의
-- 베이스가 챌린지 원본이라 나중에 슬롯으로 고쳐 붙일 수가 없고, 그 영상을 어떻게 할지는
-- 사람이 정해야 한다. 조용히 통과시키면(NOT VALID) 디스패처가 그 잡을 집었을 때
-- challenge_participant_seq 가 NULL 이라 터진다.
-- (2026-09-22 기준 로컬·운영 모두 해당 잡이 없다.)
--
-- (주석 줄을 "changeset" 으로 시작하지 않는다 — formatted SQL 파서가 선언으로 읽는다.)

--changeset claude:0021-01-drop-no-replacement-jobs
--comment 폐기된 경로의 잡을 지운다. 참조가 있는 것은 남고, 그러면 다음 changeset 이 멈춘다
DELETE
FROM composite_jobs j
WHERE j.job_type = 'CHALLENGE'
  AND j.challenge_participant_seq IS NULL
  AND NOT EXISTS (SELECT 1 FROM participation_videos v WHERE v.job_seq = j.job_seq)
  AND NOT EXISTS (SELECT 1 FROM motif_results r WHERE r.job_seq = j.job_seq);
--rollback SELECT 1;

--changeset claude:0021-02-composite-require-slot
--comment CHALLENGE 는 교체 슬롯이 필수다. MOTIF 는 그대로 NULL
ALTER TABLE composite_jobs
    DROP CONSTRAINT ck_composite_jobs_type_shape,
    ADD CONSTRAINT ck_composite_jobs_type_shape CHECK (
        (job_type = 'CHALLENGE' AND challenge_seq IS NOT NULL AND asset_seq IS NULL
            AND challenge_participant_seq IS NOT NULL)
        OR (job_type = 'MOTIF' AND asset_seq IS NOT NULL AND challenge_seq IS NULL
            AND challenge_participant_seq IS NULL)
    );
--rollback ALTER TABLE composite_jobs DROP CONSTRAINT ck_composite_jobs_type_shape, ADD CONSTRAINT ck_composite_jobs_type_shape CHECK ((job_type = 'CHALLENGE' AND challenge_seq IS NOT NULL AND asset_seq IS NULL) OR (job_type = 'MOTIF' AND asset_seq IS NOT NULL AND challenge_seq IS NULL AND challenge_participant_seq IS NULL));
