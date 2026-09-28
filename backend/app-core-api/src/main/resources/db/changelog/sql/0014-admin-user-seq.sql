--liquibase formatted sql
--
-- 0014 admins.user_seq — 감사 주체 확정 (S15P21D104-593)
--
-- 근거: 어드민 명세 v0.5 §1.3 · §6 #1 (회의 확정 — 감사 주체 = users FK)
--
-- 0002-04 는 admins 를 만들 때 "users 와 관계를 두지 않는다" 고 적었다. 어드민
-- 확정으로 그 전제가 뒤집혔다 — 각 테이블의 감사 컬럼(reg_usr_seq·mod_usr_seq·
-- processed_usr_seq)은 users(user_seq) 를 참조하므로, 어드민이 쓴 행을 남기려면
-- 어드민마다 users(role='ADMIN') 행이 하나씩 있어야 한다. admins.admin_seq 로는
-- 그 FK 를 채울 수 없다. 배포된 changeSet 은 수정하지 않으므로 여기서 덧붙인다.
--
-- 토큰이 adminSeq 와 userSeq 를 함께 싣는 이유도 이것이다 — 둘은 다른 값이고,
-- 매 요청 admins 를 조회하지 않기 위해 로그인 때 한 번 읽어 클레임에 넣는다
-- (AuthenticatedAdmin javadoc, §1.2).
--
-- ── 3단계로 쪼갠 이유 ────────────────────────────────────────────────────────
-- 컬럼 추가 → 백필 → 제약. 한 changeSet 에 몰면 배포 환경에서 백필이 실패했을 때
-- 어디까지 됐는지 알 수 없고, Liquibase 가 changeSet 단위로만 기록하므로 재실행도
-- 전부 아니면 전무가 된다. 쪼개 두면 실패 지점부터 이어서 갈 수 있다.
--
-- 빈 DB(로컬·CI)에서는 02 가 0행을 처리하고 넘어간다.

--changeset claude:0014-01-add-admins-user-seq
--comment 백필 전이라 NULL 허용으로 먼저 붙인다
ALTER TABLE admins ADD COLUMN user_seq INTEGER NULL;
--rollback ALTER TABLE admins DROP COLUMN user_seq;

--changeset claude:0014-02-backfill-admins-user-seq
--comment 기존 어드민 행마다 users(ADMIN) 을 만들어 연결한다. 빈 DB 면 0행
-- 닉네임을 'admin_' || login_id 로 두는 이유: login_id 가 UNIQUE 라 이번에 만든
-- 행들 사이에서도 유일하고, 그래서 RETURNING 결과를 admins 로 되돌려 맞출 수 있다.
-- (VARCHAR: 'admin_'(6) + login_id(≤30) = 36 ≤ users.nickname(50))
WITH created AS (
    INSERT INTO users (nickname, role)
    SELECT 'admin_' || a.login_id, 'ADMIN'
    FROM admins a
    WHERE a.user_seq IS NULL
    RETURNING user_seq, nickname
)
UPDATE admins a
SET user_seq = c.user_seq
FROM created c
WHERE a.user_seq IS NULL
  AND c.nickname = 'admin_' || a.login_id;
--rollback DELETE FROM users WHERE role = 'ADMIN' AND nickname LIKE 'admin\_%';

--changeset claude:0014-03-constrain-admins-user-seq
--comment NOT NULL + UNIQUE + FK. 1:1 이므로 UNIQUE 가 함께 간다
ALTER TABLE admins ALTER COLUMN user_seq SET NOT NULL;
ALTER TABLE admins ADD CONSTRAINT uq_admins_user_seq UNIQUE (user_seq);
ALTER TABLE admins ADD CONSTRAINT fk_admins_user FOREIGN KEY (user_seq)
    REFERENCES users (user_seq) ON DELETE RESTRICT;
--rollback ALTER TABLE admins DROP CONSTRAINT fk_admins_user;
--rollback ALTER TABLE admins DROP CONSTRAINT uq_admins_user_seq;
--rollback ALTER TABLE admins ALTER COLUMN user_seq DROP NOT NULL;

--changeset claude:0014-04-check-admins-password-hash
--comment BCrypt 형태만 허용 — 주입 실패가 조용히 지나가지 못하게 한다 (S15P21D104-608)
-- 실측(2026-09-13): 0015 시드는 해시를 Liquibase property 로 받는데,
-- ADMIN_SEED_HASH 가 없으면 Spring Boot 의 Binder 가 해석 못 한 placeholder 를
-- 예외 없이 통과시켜 password_hash 에 '${ADMIN_SEED_HASH}' 문자열이 그대로 들어갔다.
-- 앱은 멀쩡히 떴고, 아무도 로그인할 수 없는 계정만 남았다. 게다가 changeSet 은
-- 적용 완료로 기록돼 값을 고치려면 새 changeSet 이 필요해진다.
--
-- 그래서 앱이 아니라 DB 가 막는다. 이 제약이 있으면 그 INSERT 가 실패하고
-- Liquibase 가 기동을 멈춘다 — 조용한 고장이 시끄러운 고장이 된다.
-- 형식 검사일 뿐 해시의 정당성은 보지 않는다(cost·salt 는 DB 가 알 수 없다).
ALTER TABLE admins ADD CONSTRAINT ck_admins_password_hash_bcrypt
    CHECK (password_hash ~ '^\$2[aby]\$[0-9]{2}\$.{53}$');
--rollback ALTER TABLE admins DROP CONSTRAINT ck_admins_password_hash_bcrypt;
