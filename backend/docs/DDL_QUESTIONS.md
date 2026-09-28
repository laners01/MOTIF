# DDL 작성 중 발견한 질문·모호 지점

기준 명세: [docs/erd/MOTIF_DB_SPEC_v0_15.md](erd/MOTIF_DB_SPEC_v0_15.md)
작성: R9 DDL 작성(2026-09-09) · 갱신: R9 회신 반영 — v0.15 기준 종결 처리

| 상태 | 항목 |
|---|---|
| ✅ 종결 | Q1, Q2, Q3, Q4, Q8, Q9 |
| ⏳ 대기 | Q5, Q6 — infra 라인 `configmap-init.yaml` 대조 |
| 🔶 미결 | Q7 — 명세 "구현 단계에서 별도 확정할 사항" #8 |

배포 이후에는 적용된 changeSet 을 수정하지 않고 신규 changeSet 으로 정정한다.
Q2 만 예외적으로 원본 changeSet 을 고쳤다 — 사유는 해당 항목 참조.

---

## ✅ 종결

### Q1. `countries.country_name` 시드 표시명 — **`'대한민국'` 확정 (v0.15)**

v0.14 까지 명세가 값을 지정하지 않아 임의로 넣었던 항목이다.
v0.15 §29 가 시드 행을 `reg_usr_seq=NULL`, `country_name='대한민국'` 으로 명문화했다.

```sql
-- 0013-seed.sql / changeset 0013-02
INSERT INTO countries (country_code, country_name, is_active, reg_usr_seq)
VALUES ('KR', '대한민국', TRUE, NULL);
```

현재 이 값의 노출처는 없다. 화면에 뜨는 경로가 생기면 표시명 언어 정책을 정하고
**신규 `UPDATE` changeSet** 으로 정정한다(기존 시드 changeSet 은 손대지 않는다).

### Q2. `motif_results.mod_dttm` 누락 — **명세 결함 확정, v0.15 에서 컬럼 추가**

v0.14 §36 컬럼 표에 `mod_dttm` 이 없는데 같은 절의 서비스 규칙은 `description` 을
PATCH 로 수정한다고 적었다. "적용 기준"의 "모든 테이블에서 `mod_dttm`" 규정과도
어긋났다 — 소프트 삭제 대상 11개 중 `motif_results` 만 없었다.

**R9 회신: 누락 확정.** v0.15 §36 이 `mod_dttm TIMESTAMPTZ NULL` 을 추가했다.

**반영 방식 — 신규 changeSet 이 아니라 `0010-02` 원본을 수정했다.** 해당 changeSet 이
아직 커밋·배포되지 않았기 때문이며, 이는 이번 최초 작성분에 한정된 예외다.
배포 이후에는 `ALTER TABLE ... ADD COLUMN` 신규 changeSet 을 쓴다.

```sql
-- 0010-motif-results-jobs.sql / changeset 0010-02 (원본에 반영)
    reg_dttm      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    mod_dttm      TIMESTAMPTZ  NULL,      -- ← v0.15 Q2 보정
    is_deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
```

빈 DB 재초기화 후 update + 재실행 no-op 을 재검증했다
([DDL_VERIFICATION.md](DDL_VERIFICATION.md)).

### Q3. `users` 빈 CHECK 블록 — **v0.12 편집 잔재 확정, v0.15 에서 제거**

v0.14 §1 "주요 CHECK" 에 내용 없는 세 번째 ` ```sql ` 블록이 남아 있었다.
삭제된 `recent_search_terms` CHECK 의 잔재이며 **실재 규칙이 아니다**.
v0.15 에서 문서를 정리했다.

changelog 는 처음부터 실재하는 두 CHECK 만 구현했으므로 변경 없다.

```sql
CONSTRAINT ck_users_role            CHECK (role IN ('USER','ARTIST','ADMIN')),
CONSTRAINT ck_users_nickname_length CHECK (char_length(nickname) BETWEEN 1 AND 50)
```

### Q4. `challenge_participants` 감사 컬럼 부재 — **의도 확정**

`reg_dttm`/`mod_dttm` 을 두지 않는 것은 의도다. 교체 = 하드 삭제/재생성이라 행
이력이 무의미하며 `notice_images` 도 동일하다. v0.15 §13 에 명문화됐다.
changelog 변경 없다.

### Q8. `reports` 4종 UNIQUE — **일반 UNIQUE 로 승인**

PostgreSQL 기본 `NULLS DISTINCT` 동작 덕에 타깃이 NULL 인 행끼리 충돌하지 않으므로
부분 UNIQUE 가 필요 없다. v0.15 에 구현 형태가 기록됐다. 검증 T15 로 재신고 차단 확인.

### Q9. `orders.payment_provider` DB CHECK 없음 — **승인**

허용 코드 검증은 애플리케이션 책임(현재 `TOSS` 만), provider 마스터 테이블은 두지
않는다. DB 는 `payment_key IS NULL OR payment_provider IS NOT NULL` 만 강제한다.

---

## ⏳ infra 대조 대기

### Q5. `subscriptions` SELECT GRANT 소유권

**결정(R9): changeSet 단독 소유.** `0003-03-grant-subscriptions-select-notif` 가
`GRANT SELECT ON subscriptions TO notif_app` 을 수행한다. 수동 선행 SQL 시점에는
테이블이 없어 GRANT 가 성립하지 않는다. `manual-prereq.sql` 은 스키마 `USAGE` 까지만 연다.

**대기 중**: infra 라인 `configmap-init.yaml` 에 동일한 테이블 GRANT 가 있으면
**제거 대상**이다(인프라 라인에 요청됨). 대조 결과를 여기에 기록한다.

> 참고 — 이 changeSet 은 롤명 `notif_app` 을 하드코딩한다. 계정명은 명세 확정 상수로
> 수용하며, 변경 시 신규 changeSet 을 전제한다.

### Q6. `pg_trgm` search_path — 현행 해법 유지, 운영 반영 대조 대기

검증 중 실제로 실패한 항목이다.

```
ERROR: operator class "gin_trgm_ops" does not exist for access method "gin"
```

확장이 `public` 에 설치되고 앱 롤 `search_path` 가 자기 스키마뿐이면 GIN 생성이 죽는다.
확장 존재만 보는 precondition 은 이 상태를 통과시킨다.

**결정(R9): 현행 해법 유지. changelog 변경 불필요.** v0.15 "Liquibase 운영 기준"에
명문화됐다(대안으로 `public.gin_trgm_ops` 스키마 한정도 허용).

```sql
-- manual-prereq.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm SCHEMA public;
ALTER ROLE core_app  SET search_path = motif_core, public;
ALTER ROLE notif_app SET search_path = motif_notif, public;
```

**대기 중**: 운영 원본 `configmap-init.yaml` 이 이 설정을 포함하는지 대조.
빠져 있으면 **운영 배포에서 `0012-05`/`0012-06` 이 같은 오류로 실패한다.**

---

## 🔶 미결

### Q7. `composite_jobs.edited_challenge_seq` NULL 허용

명세 "구현 단계에서 별도 확정할 사항" #8 — 팀 논의 예정.
현재는 기본안대로 `job_type='CHALLENGE'` 분기에서 NULL 을 허용한다.
편집본 필수로 확정되면 아래 초안을 **신규 changeSet** 으로 적용한다(R9 승인된 초안).

```sql
ALTER TABLE composite_jobs DROP CONSTRAINT ck_composite_jobs_type_shape;
ALTER TABLE composite_jobs ADD CONSTRAINT ck_composite_jobs_type_shape CHECK (
    (job_type = 'CHALLENGE' AND challenge_seq IS NOT NULL AND asset_seq IS NULL
     AND edited_challenge_seq IS NOT NULL)   -- ← 추가
    OR
    (job_type = 'MOTIF' AND asset_seq IS NOT NULL AND challenge_seq IS NULL
     AND edited_challenge_seq IS NULL)
);
```

---

## 지시서 대비 편차 (모두 사용자 승인 또는 명세 근거 있음)

| # | 편차 | 근거 |
|---|---|---|
| D1 | changelog 를 XML 이 아니라 **번호 붙은 raw SQL + master yaml** 로 작성 | ADR-0004 기록 체계 · 사용자 결정(C1) |
| D2 | 경로가 `db/changelog-core/` 가 아니라 기존 `db/changelog/sql/` | 위와 동일. `application.yml` 이 이미 이 경로를 가리킨다 |
| D3 | 기존 `db.changelog-master.yaml` 2개를 **수정**(지시서는 신규 파일만) | 빈 `databaseChangeLog: []` 스텁이며 include 를 담는 것이 그 파일의 목적 |
| D4 | 부분 UNIQUE 2개(`uq_companies_name_active`, `uq_asset_purchases_active`)를 `0012` 가 아니라 각 테이블 파일에 배치 | 지시서 규약 1 "테이블 생성 + 그 테이블의 CHECK/UNIQUE 까지 = 하나의 논리 단위" |
| D5 | `challenge_participants` 의 단일 CHECK 를 마스크용·bbox용 2개로 분할 | 조건 동일(AND 분해). 위반 진단 구분 목적 |
| D6 | `manual-prereq.sql` 을 `infra/db/` 가 아니라 `backend/docs/db/` 에 배치 | 사용자 결정(C5) — `infra/` 는 develop/infra 라인 소유 |
| D7 | GRANT 를 선행 SQL 이 아니라 changeSet 이 소유 | 사용자 결정(C2) · 순서상 선행 불가 — Q5 참조 |
| D8 | `0010-02` 원본 changeSet 수정(신규 changeSet 원칙의 예외) | 미커밋·미배포 상태 · R9 승인 — Q2 참조 |
