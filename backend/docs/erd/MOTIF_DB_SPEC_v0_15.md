# MOTIF 데이터베이스 명세서 (PostgreSQL) — v0.19

본 문서는 changeSet 0020 까지를 반영한 **현행 스키마 계약**이다. 구현 시 Liquibase changelog와 JPA 엔티티는 본 명세와 동일한 타입·제약·서비스 불변식을 가져야 한다. 이미 배포된 changeSet은 수정하지 않고 신규 changeSet으로 마이그레이션한다.

**파일명의 `v0_15` 는 버전이 아니다.** v0.13·v0.14 는 그 시점에 얼어붙은 스냅샷이지만 이 파일은 v0.15 이후 제자리에서 갱신돼 현행 명세 노릇을 하고 있다. changelog 파일 21개(SQL 19 · master 2)와 `docs/DDL_*.md` 가 이 경로를 `명세:` 로 가리키므로 파일을 옮기지 않는다 — 버전은 제목과 아래 개정 이력 절이 갖는다.

## v0.19 반영 내용 (2026-09-21 · changeSet 0020)

앱이 편집본 영상을 직접 받는 API 가 생겼다(§7.6). 지금까지 편집본은 AI 워커만 S3 에서 내려받았고 앱에는 주소가 나간 적이 없어 공개 프리픽스로 충분했는데, 앱에 주는 순간 무기명 CDN 링크가 된다.

| 대상 | 변경 |
|---|---|
| 편집본 3키를 `private/edited/…` 로 | **서명은 프리픽스가 정한다.** `MediaKeys.isProtected` 는 `private/` 하나만 보고, CloudFront 의 "Restrict viewer access" 도 캐시 동작(`private/*`) 단위다. 키를 옮기지 않고 코드에서 등급만 올리면 서버는 서명을 붙이는데 **CloudFront 가 검증하지 않아 무서명 요청이 그대로 통과한다** — 서명이 장식이 된다. 어드민 presign(`EDITED_VIDEO`·`EDITED_SCENE`·`EDITED_PREVIEW`) 발급 경로와 AI 팀 업로드 위치가 함께 바뀐다 |
| `edited_duration_ms` 신설 | `INTEGER NULL`, CHECK `> 0`. 등록 폼이 이미 보내던 값을 저장만 한다(원본 ±500ms 대조에만 쓰고 버렸다). 앱 촬영 타이머가 편집본 길이에 맞아야 하는데 **챌린지 원본으로 갈음하면 최대 0.5초가 어긋나 녹화 끝이 잘린다**. 통짜 MP4 라 필요한 값이기도 하다 — HLS 면 재생목록의 `#EXTINF` 합으로 플레이어가 알아내지만 MP4 는 `moov` 를 읽기 전까지 모른다 |
| 편집본에 길이 필수 | 어드민 §3.7.2·§3.7.6 이 편집본 키를 받을 때 `durationMs` 를 함께 요구한다(`EDITED_DURATION_REQUIRED`). 없으면 §7.6 이 `null` 을 내보내 촬영 타이머를 그릴 수 없다 |
| `private/` 정의 확장 | ADR-0013 은 `private/` 를 "사용자 생성 콘텐츠"로 적었는데 편집본은 어드민·AI 카탈로그다. **`private/` = 서명 필요**이지 `= 사용자 생성`이 아니다 — 코드는 프리픽스만 보므로 동작은 같고, 정의만 넓힌다 |
| 앱 계약에서 마스크 소멸 | 슬롯을 가리키는 값이 `challenge_participant_seq` 로 통일됐다(§7.5·§7.6·§13.1). `participant_mask` 는 **AI 접수 페이로드 안에만** 남고 `1 << slot_order` 로 파생한다 — 스키마에는 0019 부터 이미 없다 |

## v0.18 반영 내용 (2026-09-21 · changeSet 0019)

**스키마 정리가 아니라 모델 변경이다** — 편집본이 "지운 슬롯 조합" 단위에서 **참가자 1명당 1개**로 바뀐다. 사용자가 고르는 것은 함께할 아티스트 한 명이고, 2명 이상을 한 번에 지운 편집본은 만들지 않는다.

| 대상 | 변경 |
|---|---|
| `edited_challenges` 폐기 (§14) | 테이블 삭제. 세 키가 `challenge_participants` 로 이관 — `video_key` → `edited_challenge_key`, `initial_scene_key`·`preview_image_key` 는 이름 그대로 |
| `challenge_participants` 대리키 (§13) | `challenge_participant_seq INTEGER IDENTITY` 를 PK 로. 복합 PK `(challenge_seq, artist_seq)` 는 UNIQUE 로 내린다 — `composite_jobs` 가 칼럼 하나로 슬롯을 가리켜야 한다 |
| `participant_mask` → `slot_order` | 조합이 없어져 비트값이 필요 없다. 화면 좌→우 **0부터**. AI 페이로드의 `participant_mask` 는 `1 << slot_order` 로 파생하므로 계약(AI 서버 명세 §3.2)은 칸 하나 바뀌지 않는다 |
| `ck_challenge_participants_edited` 신설 | 영상·첫 장면은 **짝** — 둘 다 NULL(편집본 미등록) 또는 둘 다 NOT NULL. `preview_image_key` 는 짝에 넣지 않는다(편집본이 있어도 없을 수 있다) |
| `challenge_participants` 감사 컬럼 신설 | `reg_dttm`·`mod_dttm`. v0.15 Q4 에서 "교체 = 하드 삭제/재생성이라 이력이 무의미" 로 뺐던 것을 되돌린다 — 편집본 키가 같은 행에서 UPDATE 되므로(§3.7.6) 이력이 의미를 얻었다 |
| `composite_jobs.edited_challenge_seq` → `challenge_participant_seq` | FK 대상이 편집본 행에서 슬롯 행으로. `ck_composite_jobs_type_shape` 도 새 칼럼명으로 재작성 |
| **전 조합 등록 전제 폐기** *(구 회의 확정 R8)* | 편집본이 비어 있는 슬롯이 정상 상태가 된다. 앱 §7.2 가 `selectable` 로 내려 화면에서 잠그고, 그래도 오면 §13.1 `EDITED_CHALLENGE_NOT_FOUND`. 어드민의 조합 완결성 검증(`EDITED_SET_INCOMPLETE`)은 사라진다 |
| 기존 행 처리 | 슬롯은 **전부 살린다**(앵커 포함, 마스크 지수 → `slot_order`). 편집본은 **단일 비트인 것만** 옮긴다 — 조합 편집본은 새 모델에 자리가 없다. 그 편집본을 쓰던 잡은 참조를 끊어 무교체로 읽힌다 |

## v0.17 반영 내용 (2026-09-20 · changeSet 0018 · [ADR-0014](../adr/0014-artist-alias-search.md))

| 대상 | 변경 |
|---|---|
| `artist_aliases` 신설 (§41) | 아티스트의 **언어별 별칭**. 다국어 검색용 — "Karina"·"카리나" 어느 쪽으로 쳐도 같은 아티스트를 찾게 한다. `artist_seq` FK · `language_code CHAR(2)` · `alias VARCHAR(100)` |
| `group_aliases` 신설 (§42) | 그룹의 언어별 별칭. **아티스트와 한 테이블로 합치지 않는다** — `artist_seq` FK 하나로는 그룹을 가리킬 수 없고, 폴리모픽(`target_type`+`target_seq`)으로 만들면 FK 를 못 건다. 무엇보다 **"이 매치가 그룹 매치였다" 는 사실이 테이블 구분으로 드러나야** §5.2 의 그룹 확장을 판정할 수 있다 |
| 별칭 UNIQUE **없음** | 한 언어에 별칭 여러 개를 허용한다(`aespa`·`æspa`, 활동명·본명). 중복 입력도 막지 않는다 — §5.2 가 `artistSeq` 로 중복을 제거하므로 결과가 겹치지 않는다 |
| GIN 인덱스 2개 | `gin_artist_aliases_alias`·`gin_group_aliases_alias` — `alias gin_trgm_ops`. **`lower(alias)` 가 아니라 원본 컬럼**에 건다(조회가 `ILIKE` 라서 그렇다. 근거는 "pg_trgm GIN" 절) |
| 검색 대상 이전 | API §5.2 검색이 `users.nickname`·`artist_groups.group_name` 대신 별칭 테이블을 본다. **어드민 §3.5.1 목록 검색은 종전대로 `users.nickname`** 이다 — `gin_users_nickname` 을 계속 쓴다 |
| 소프트 삭제 연쇄 **없음** | 아티스트·그룹을 소프트 삭제해도 별칭 행을 건드리지 않는다. 조회가 조인에서 `is_deleted = FALSE` 로 거른다 — 복구 시 별칭이 그대로 살아 돌아온다 |

## v0.16 반영 내용 (2026-09-18 · changeSet 0017 · S15P21D104-618)

| 대상 | 변경 |
|---|---|
| `challenge_participants` bbox → 앵커 | `bbox_*` 4컬럼·CHECK 삭제. `anchor_x`·`anchor_y`·`anchor_r DOUBLE PRECISION NULL` 추가 — 촬영 전 선택 화면(시안 10-1~3)에서 슬롯 프로필 원이 앉는 자리, **썸네일 대비 0~1 비율**. CHECK 는 "셋 다 NULL 또는 셋 다 유효". NULL = 0017 이전 행(어드민 §3.7.9 로 채움) |
| `edited_challenges.preview_image_key` 추가 | `VARCHAR(255) NULL`. 조합을 골랐을 때 썸네일 자리에 보이는 이미지 — mask 당 1장이라 편집본 행에 붙인다. NULL = 0017 이전 행(어드민 §3.7.6 으로 채움) |
| 기존 행 처리 | **백필 없음.** bbox→앵커 자동 변환은 원본 해상도를 저장하지 않아 불가능하고, 가능해도 사각형 중심은 원 자리(머리 옆)가 아니다. 두 컬럼 모두 NULL 허용으로 붙여 기존 챌린지를 살린다 |

## v0.15 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `motif_results.mod_dttm` 추가 *(회의 확정 R9 — DDL 질문 Q2, 누락 확정)* | `description`/`isRep` 이 PATCH 로 수정되는데 컬럼 표에만 없었다 — "모든 테이블 mod_dttm" 적용 기준과 정합화. `TIMESTAMPTZ NULL` |
| `users` 빈 CHECK 블록 제거 *(Q3 — v0.12 편집 잔재)* | `recent_search_terms` CHECK 삭제 시 남은 빈 `sql` 블록 정리. 실재 규칙 아님 |
| Liquibase 운영 기준 보강 *(Q6·T14 — 검증 실측 반영)* | ① pg_trgm 은 `public` 스키마 설치 + **앱 롤 `search_path` 에 `public` 포함 필수**(또는 연산자 클래스를 `public.gin_trgm_ops` 로 한정) — 미설정 시 GIN 생성 실패 ② `GENERATED BY DEFAULT` 특성상 **수동 INSERT 에서 PK 명시 금지**(시퀀스 미전진 → 이후 충돌) |
| 확정 기록 | Q1: `countries` 시드 표시명 `'대한민국'` 확정(노출처 발생 시 UPDATE changeSet 으로 정정) · Q4: `challenge_participants`·`notice_images` 감사 컬럼 부재는 **의도**(교체 = 하드 삭제/재생성) · Q8: reports UNIQUE 는 일반 UNIQUE(NULLS DISTINCT) · Q9: `payment_provider` DB CHECK 없음(앱 검증) |

## v0.14 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `countries.reg_usr_seq` NULL 허용 *(회의 확정 R9 — DDL 검증 발견)* | 시드(`KR`) 시점에 `users` 행이 존재하지 않아 `NOT NULL FK` 로는 시드가 불가능. `INTEGER NULL` 로 완화하고 **NULL = 시스템(마이그레이션) 생성** 의미 부여. 어드민 등록·수정 경로는 종전대로 행위자 기록. countries 외 감사 필드는 시드 대상이 아니므로 NOT NULL 유지 |

## v0.13 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| 편집본 **전(全)조합 등록 전제 확정** *(회의 확정 R8)* | 어드민이 챌린지 슬롯 등록 시 가능한 슬롯 조합의 편집본을 **전부 등록**한다(+등록 완결성 검증 — 슬롯 n개 기준 2^n−1개, 어드민 명세 반영). 미결 #10 종결 — 편집본 없는 조합은 정상 흐름에서 발생하지 않으며 API 404 는 방어선으로만 잔존 |
| 잔여 논의 표기 | CHALLENGE 잡 `edited_challenge_seq NULL`(무교체 원본 합성) 허용 여부(미결 #8)는 **팀 논의 예정**으로 유지 |

## v0.12 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `shop_banners` 신설 *(회의 확정 R7)* | 모티프 숍 상단 공용 배너(`motif_core`) — 아티스트별 `motif_bg_image_key` 와 별개. `shop_banner_seq` PK, `image_key`(공개 CDN), `display_order` UNIQUE, `is_active`. 클릭 링크 컬럼은 미도입(필요 시 확장) |
| 재시도 = **동일 잡 재큐 + `attempt` 증가** *(회의 확정 R7)* | 사용자 재시도(API 13.4)는 `FAILED` 행을 같은 행으로 재큐: `status=QUEUED`, `fail_code=NULL` 초기화(상태별 CHECK 그대로 성립), `attempt+1`. 직전 실패 사유는 행에서 소거 — 이력 필요 시 로그/이벤트. 결과 키 멱등(`results/{type}/{job_uuid}.mp4`) 규칙과 정합 |
| `users.recent_search_terms` 삭제 *(회의 확정 R7)* | 최근 검색어 기능 제외 확정 — 컬럼·`cardinality` CHECK·Redis 백업 플러시 규칙 제거. 신규 changeSet `DROP COLUMN`. 미결 #12 종결 |
| `participant_mask` 0 = 예약값 *(회의 확정 R7)* | CHECK `>= 0` 확정 유지. **0 은 현재 발급하지 않는 예약값** — 원본(무교체) 참여는 `composite_jobs.edited_challenge_seq NULL` 경로 사용. 미결 #11 부분 종결 |

## v0.11 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `motif_assets.title` 신설 *(회의 확정 R6)* | 에셋 표시명 `VARCHAR(100) NOT NULL`. 카탈로그(11.1)·상세(11.2)·홈 활동(6.3) 노출용 — `description` 은 소개 문구로 별도 유지. 배포 환경에 기존 행이 있으면 신규 changeSet에서 `description` 앞부분으로 백필 후 `NOT NULL` 적용. 미결 #13 종결 |
| MOTIF 결과 **자동 생성 규칙** *(회의 확정 R5/R6)* | 구 저장 API 삭제 — 결과 행은 `composite.done` 이벤트를 수신한 **코어 컨슈머**가 생성(워커 DB 접근 없음 — 설계 원칙 ③). 순서: 결과 업로드 → done 발행 → `motif_results(description=NULL)` 생성 → 잡 `DONE` → FCM. 재전송·재큐 대비 `uq_motif_results_job` 기반 `ON CONFLICT DO NOTHING` 멱등 |
| SECRET 판정 종결 *(회의 확정 R5·해석 R6 확인)* | 별도 판정 규칙 없음 — 노출·구매·**PASS 커버**에서 NORMAL 과 동일 취급(PREMIUM 만 개별 구매 전용). 카드 `n/m` 의 SECRET 집계 제외 규칙 해제. 미결 #6 종결 |
| 패스 취소 구현 제외 *(회의 확정 R5)* | 취소 API 는 현재 구현 범위 제외 — `canceled_dttm` 컬럼·취소 규칙은 기획 유지(재도입 시 그대로 적용) |
| 서비스 불변식 추가 *(회의 확정 R5)* | ① `role='ARTIST'` 사용자의 참여 영상 게시 거부 ② 알림: 클라이언트가 수신 FCM 을 로컬 DB 에 저장해 목록 구성(서버 무관) ③ `artists.motif_bg_image_key` 는 공개 CloudFront URL 로 노출 |

## v0.10 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `participant_mask` CHECK 완화 *(회의 확정 R3)* | `challenge_participants`·`edited_challenges` 모두 `>= 0` 으로 완화. **값 0의 의미는 확정 필요**(미결 #11): ⓐ edited 의 0 = "지운 슬롯 없음(원본 베이스)" ⓑ 슬롯 번호를 0부터 매기는 위치 저장 전환 — ⓑ면 단일 비트 CHECK 제거·컬럼명 `slot_index` 권장 |
| `challenges.title`·`participation_videos.title` 삭제 *(회의 확정 R3)* | 챌린지·참여 영상의 제목 개념 제거. 표시 식별은 곡명(`songs.title`)·아티스트·썸네일로. API(7.x/8.x/5.4)·기능 명세서(CHALLENGE-UPLOAD-01 제목 입력) 정정 파급 |
| **`notifications` 테이블 삭제** *(회의 확정 R3)* | 인앱 알림 목록·읽음·미읽음 배지(레드닷) 스코프 아웃. 모든 알림은 **FCM 푸시 전용**(행 미저장). §39는 발송 규칙 절로 대체, `motif_notif` 스키마에는 `device_tokens`만 잔존. 관련 UNIQUE·인덱스 삭제 |
| 계보 병합 — v0.9(API R2) 확정 재반영 | ① 카드 `MOTIF n/m` **최종 확정**: 개별 구매 수 / 전체 활성 에셋 수(PASS 미포함 — v0.7 정의, 스키마 무변) ② `motif_results.artist_seq` 용도에 아티스트 상세 상단 자동재생 목록 명시 ③ 미결 등재: `users.recent_search_terms` 존치 여부, 에셋 표시명(`title` 컬럼) |

## v0.9 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `challenge_participants` 슬롯 확장 *(회의 확정)* | 추가 참여 아티스트 행을 사용자가 교체해 들어가는 **인물 슬롯**으로 확장. 원본 영상 px 기준 바운딩 박스 4칼럼(`bbox_left_top_x/y`, `bbox_right_bottom_x/y`)과 슬롯 비트값 `participant_mask`(화면 좌측 인물부터 1·2·4…, 단일 비트 CHECK) 추가, `(challenge_seq, participant_mask)` UNIQUE. 메인 아티스트는 교체 대상이 아니므로 현행대로 행을 두지 않는다 *(회의 확정 — v0.6 시맨틱 유지)* |
| `edited_challenges` 신설 *(회의 확정)* | 특정 슬롯 조합을 지운 사전 편집 영상 — CHALLENGE 합성의 베이스. `participant_mask`(지워진 슬롯 비트 OR 합)로 조합 식별, `(challenge_seq, participant_mask)` UNIQUE. `initial_scene_key`는 촬영 가이드용 첫 장면 이미지. PK는 `{단수}_seq` 규약에 따라 `edited_challenge_seq` |
| `composite_jobs.edited_challenge_seq` 신설 *(회의 확정)* | CHALLENGE 잡의 합성 베이스 편집본 기록. 워커는 무상태이고 잡 행이 큐 메시지의 원천이므로 스윕 재큐 재구성을 위해 행에 보존. `NULL`이면 원본 영상 위 합성 *(기본안 — 확인 필요)*. MOTIF 잡은 항상 `NULL`(CHECK) |

## v0.8 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `motif_passes.canceled_dttm` 신설 *(회의 확정)* | 패스 "구독취소" API(API 명세 §11.10) 채택에 따른 취소 의사 기록 컬럼. `TIMESTAMPTZ NULL`. 자동결제가 없어 해지할 갱신이 없으므로 기록 후에도 `status=ACTIVE`·이용 판정 불변(만료일까지 이용 유지). 만료 후 재활성 시 `NULL` 초기화. 자동결제 재도입 시 갱신 차단 판정 필드로 승계 |
| 마이페이지 아티스트 카드 재정의 *(회의 확정 — v0.7 규칙 번복)* | `MOTIF n/m` = **이용 가능 에셋 수 / 총 활성 에셋 수**. `n` = 개별 구매(ACTIVE) ∪ (유효 PASS 보유 시 해당 아티스트의 활성 NORMAL 전체)의 **중복 제거 합집합** — v0.7의 "PASS 이용은 분자 미포함(0/m)" 규칙 폐기. `m` = `is_active=TRUE` 에셋 수 |
| `motif_results.artist_seq` 용도 정정 | 카드가 결과물 수를 집계하지 않게 되어 카드 용도 서술 삭제 — 아티스트 상세의 사용자 최근 결과물 조회·보관함 아티스트별 조회용으로 한정 |
| 알림 설정 종결 *(회의 확정)* | 클라이언트 로컬(DataStore) 저장 확정 — 설정 테이블을 신설하지 않는다. 성립 조건인 FCM data-only 발송 규약은 API 명세 §14.8 소관. 미결 목록에서 제거 |

## v0.7 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| `users.nickname` 중복 허용 *(회의 확정)* | 활성 행 부분 UNIQUE `uq_users_nickname_active` 삭제. 닉네임은 표시 이름이며 식별자가 아님. 사용자 식별은 `user_seq`, 아티스트 구분은 `role` 뱃지로 처리 |
| 알림 행 미저장 *(회의 확정)* | 공지/챌린지 신규 콘텐츠 알림은 푸시 전용. `noti_type='ARTIST'`·관련 CHECK·group key 삭제. 팬아웃(`subscriptions` 읽기)은 유지 |
| `motif_passes` 환불 상태 *(회의 확정)* | `CANCELED` → `REFUNDED`로 개명. 대응 주문 상태와 어휘 일치 |
| 삭제 사용자 콘텐츠 *(회의 확정)* | 참여 영상·결과물뿐 아니라 댓글도 비노출. 루트 댓글 작성자가 삭제 사용자면 답글 스레드도 비노출. `comment_count`·`participant_count`와 표시 개수 불일치는 감수 |
| 국가·결제 제공자 *(현재 범위)* | `countries`는 `KR` 1행만 시드하고 사용자 국가는 서버 설정 상수. `payment_provider`는 `TOSS`만 허용 |
| 주문 만료 *(회의 확정)* | `expires_dttm = reg_dttm + 30분`. 승인은 조건부 UPDATE로 지연 판정, PENDING 정리는 1시간 배치 |
| 배치 통합 *(회의 확정)* | 주문 만료 정리와 PASS 만료 전이를 1시간 주기 ShedLock 배치 하나로 처리 |
| `motif_results` | `artist_seq` 반정규화(에셋 아티스트 복사, 불변) + `idx_motif_results_user_artist` |
| 인덱스 | `idx_pv_user_artist` 추가. `gin_users_nickname`을 `role='ARTIST' AND is_deleted=FALSE` 부분 GIN으로 변경 |
| 마이페이지 아티스트 카드 | `개별 구매 수 / 아티스트 에셋 수` 정의. PASS 이용은 분자에 포함하지 않음 |
| 저장소 | RDS → 서버 내 자체 운영 PostgreSQL |
| 자동결제 제거 *(회의 확정)* | PASS는 자동결제 없는 기간제 상품. `motif_passes.auto_renew` 컬럼·CHECK 삭제, 자동갱신 성공/실패 규칙 및 가격·국가·아티스트 변경 시 `auto_renew=FALSE` 연쇄 규칙 전부 삭제. PASS 만료는 배치가 `EXPIRED` 전이만 수행하고 재구매는 새 PAID 주문으로 같은 행을 재활성 |

## v0.6 반영 내용 (이전 버전)

| 대상 | 변경 |
|---|---|
| 공통 수정 시각 | 모든 `mod_dttm`은 `NULL` 허용, 기본값 없음. 실제 수정이 발생한 시점에만 기록 |
| 수정 주체 | `mod_usr_seq`가 있는 테이블은 최신 논리 변경을 유발한 서비스 사용자 기록. 순수 시스템/배치는 `NULL` 허용 |
| `users` | `recent_search_terms TEXT[]` 추가, 최대 10개 CHECK. `rep_motif_result_seq BIGINT`. (v0.7에서 nickname UNIQUE 제거) |
| `companies` | `company_name` 활성 행 부분 UNIQUE. `mod_usr_seq`, `mod_dttm` 추가. 활성 하위 그룹/아티스트가 있으면 소프트 삭제 차단 |
| `artist_groups` | 그룹명 중복 허용 유지. `mod_usr_seq`, `mod_dttm` 추가. 활성 소속 아티스트가 있으면 소프트 삭제 차단 |
| `artists` | `group_seq` NULL 허용(솔로). `is_active` 제거, `is_deleted`만 사용. 그룹/소속사 일관성 규칙 추가 |
| 아티스트 삭제 | 연결 `users`도 같은 트랜잭션에서 소프트 삭제. 에셋/가격 판매 중단. ACTIVE PASS 기간은 유지 |
| `songs` | `album_name`, `released_at` 제거. 곡 검색용 pg_trgm 인덱스 제거 |
| `challenges` | `status` 제거. `is_deleted`만 사용. `description NOT NULL` |
| `challenge_artists` | `challenge_participants`로 변경. 메인 아티스트를 제외한 추가 참여 아티스트만 저장 |
| `participation_videos` | `video_seq BIGINT`, `title NOT NULL`, `description NOT NULL`, `duration_ms NOT NULL` 추가. 모든 참조 BIGINT 전파 |
| 참여 영상 공개범위 | `PRIVATE`도 DB 카운터·관계에는 정상 포함. 조회는 작성자 본인만 허용하며 다른 사용자에게 존재 여부를 노출하지 않음. 삭제된 챌린지/사용자의 콘텐츠는 API 노출 제외 |
| 댓글 | 댓글 PK 및 부모 참조 BIGINT. 루트 삭제 시 직계 답글까지 소프트 삭제하고 해당 댓글 좋아요는 하드 삭제 |
| 댓글 카운터 | `comment_count`는 `is_deleted=FALSE` 댓글 수. 루트 삭제 시 실제 새로 삭제된 행 수만큼 감소 |
| 신고 | `report_seq BIGINT`, 모든 영상/댓글 대상 BIGINT. 처리 결과와 관계없이 1인 1대상 1회만 신고 가능. ACCEPTED 삭제 후처리는 멱등 처리 |
| `notices` | `title` 제거, `content`만 유지 |
| `countries` | 국가 판매 설정 마스터 신설. ISO 2자리 코드, 활성 상태 및 감사 필드 관리 |
| `motif_assets` | 기존 단일 `price`, `is_deleted` 제거. `is_active`는 신규 카탈로그 노출·신규 판매 및 일반 PASS 이용 가능 상태. 판매 중단 전 취득한 ACTIVE PASS는 예외적으로 기존 만료까지 이용 가능 |
| `motif_asset_prices` | 에셋×국가 현재 가격 테이블 신설. 가격 이력은 별도 보관하지 않고 같은 행 갱신 |
| `motif_pass_prices` | 아티스트×국가×기간별 PASS 현재 가격 테이블 신설 |
| `orders` | 국가/가격/기간 스냅샷, 주문 만료, `EXPIRED`, `CANCELED`, `REFUNDED`, `payment_provider` 관리. `CANCELED`는 승인 전 주문 취소, `REFUNDED`는 PAID 이후 환불. `(payment_provider,payment_key)` UNIQUE |
| 결제 구현 범위 | 반복결제용 billing key/payment method는 현재 DB 범위에서 제외. 모든 PASS 결제 시도는 새 `orders` 행 생성 |
| `asset_purchases` | `is_deleted` 제거, `status ACTIVE/REFUNDED` 사용. ACTIVE 행 부분 UNIQUE |
| `motif_passes` | `duration_months` 추가. `order_seq`는 현재 이용 기간을 만든 PAID 주문. 최초 결제 실패 시 행 미생성 |
| PASS 재구매 | 만료 후 새 PAID 주문으로 같은 행을 재활성. 자동갱신 없음 |
| `motif_results` | `result_seq BIGINT`, `duration_ms` 추가. 대표 결과 삭제 시 `users.rep_motif_result_seq=NULL` |
| `composite_jobs` | 상태별 결과키/fail_code CHECK 강화. stale 판정은 `COALESCE(mod_dttm, reg_dttm)` 사용 |
| 합성 권한 | CHALLENGE는 결과 생성 직전 챌린지 삭제 여부 재검증. MOTIF는 job 생성 시 권한 검증 후 진행 중 권한 변화로 취소하지 않음 |
| `refresh_tokens` | `token_seq BIGINT`, rotation 적용. 폐기/만료 과거 행은 운영 설정에 따라 주기적 하드 삭제 |
| 최근 검색 감사필드 | Redis→DB `recent_search_terms` 백업 플러시는 `users.mod_dttm`, `mod_usr_seq`를 변경하지 않음 |
| 합성 업로드 정리 | 장시간 `UPLOADED` 상태로 합성 요청이 시작되지 않은 임시 잡/원본 업로드는 운영 보존 기준에 따라 주기적으로 하드 삭제 |
| `device_tokens` | 동일 FCM token은 새 로그인 사용자에게 재할당. 로그아웃/invalid token/계정 정지·삭제 시 `is_active=FALSE` |
| `notifications` | `actor_count` → `event_count`. 미읽은 이벤트 수 의미. `last_event_dttm` 추가 및 목록 정렬 기준 변경 |
| 알림 읽음 | 읽음 시 `event_count=0`. 전체 미읽음 배지는 `SUM(event_count)`가 아니라 `is_read=FALSE` 행 개수 |
| 알림 대상 삭제 | 알림 행은 유지. 클릭 시 대상 API가 삭제 상태를 반환하도록 처리 |
| 인덱스 | 제거된 컬럼을 참조하던 인덱스 정리, BIGINT 참조 전파, 알림/합성 stale 표현식 인덱스 재정의 |

---

## 적용 기준

- 전체 영속 데이터는 서버 내 자체 운영 PostgreSQL 단일 인스턴스에 저장하고 `motif_core`, `motif_notif` 스키마로 소유권을 분리한다.
- `motif_core`는 `core_app`, `motif_notif`는 `notif_app`이 소유한다.
- 기본적으로 스키마 간 FK는 두지 않는다. `motif_notif`의 core 식별자는 논리 참조다.
- 알림 팬아웃을 위해 `notif_app`에는 `motif_core.subscriptions`에 대한 읽기 전용 `SELECT` 권한만 추가한다.
- 내부 식별자는 `*_seq`로 명명하고, 대리키가 필요한 경우 `GENERATED BY DEFAULT AS IDENTITY`를 사용한다.
- 일반 엔티티 PK는 `INTEGER`를 기본으로 하되 대량 누적 가능성이 큰 사용자 생성 콘텐츠/이력/잡은 `BIGINT`를 사용한다.
- 현재 BIGINT PK: `refresh_tokens.token_seq`, `participation_videos.video_seq`, 댓글 3종 PK, `reports.report_seq`, `orders.order_seq`, `asset_purchases.purchase_seq`, `motif_results.result_seq`, `composite_jobs.job_seq`.
- FK 및 논리 참조는 대상 식별자와 같은 타입을 사용한다.
- Java/JPA 매핑은 `INTEGER → Integer`, `BIGINT → Long`, `NUMERIC(10,2) → BigDecimal`을 사용한다.
- 서비스 시각은 `TIMESTAMPTZ`, 앱·서버·DB는 UTC 기준으로 통일한다.
- `reg_dttm`은 생성 시각이며 변경하지 않는다.
- `mod_dttm`은 모든 테이블에서 기본값을 두지 않고 `NULL`로 시작하며, 실제 논리 변경 시에만 기록한다.
- `mod_usr_seq`는 해당 변경을 유발한 서비스 사용자를 기록한다. 순수 시스템/배치 변경은 `NULL`을 허용한다.
- 소프트 삭제는 복구 기능을 제공하지 않는 현재 범위에서 사실상 종료 상태로 취급한다.
- 소프트 삭제된 엔티티의 자식 행은 특별히 명시하지 않는 한 대량 연쇄 수정하지 않는다. API 접근 제어로 노출을 막는다.
- 단순 N:M 관계/토글 테이블은 별도 식별자가 필요하지 않으면 복합 PK를 사용하고 해제 시 하드 삭제한다.
- 교차 테이블 값을 조회해야 하는 규칙은 일반 `CHECK`로 강제하지 않고 서비스 트랜잭션 불변식으로 관리한다.

## Liquibase 운영 기준

- changelog는 도메인 모듈별로 분리한다.
- 각 모듈의 `spring.liquibase.default-schema`, `liquibase-schema`를 자기 스키마로 지정한다.
- CHECK, 부분 UNIQUE, 표현식/부분 인덱스는 필요 시 `<sql>` changeSet으로 작성한다.
- `CREATE EXTENSION pg_trgm`은 관리자 계정으로 1회, **`public` 스키마에** 설치하고 GIN 인덱스 changeSet에는 precondition을 둔다.
- **search_path 필수** *(회의 확정 R9 — 검증 실측)*: 확장이 `public`에 있고 앱 롤 `search_path`가 자기 스키마뿐이면 `gin_trgm_ops`를 찾지 못해 GIN 생성이 실패한다(확장 존재 precondition은 이를 통과시킴). `ALTER ROLE core_app SET search_path = motif_core, public;` `ALTER ROLE notif_app SET search_path = motif_notif, public;` 을 선행 SQL(운영: configmap-init.yaml)에 포함한다. 대안: 인덱스 정의에서 `public.gin_trgm_ops`로 스키마 한정.
- `GENERATED BY DEFAULT AS IDENTITY` 특성상 **수동 INSERT에서 PK를 명시하지 않는다** — 명시값 삽입 시 시퀀스가 전진하지 않아 이후 자동 발급이 PK 충돌한다 *(R9 검증 T14 실측)*.
- 적용된 changeSet은 수정하지 않고 신규 changeSet으로 정정한다.
- 문서의 표 번호는 도메인 설명 순서이며 실제 DDL 생성 순서를 의미하지 않는다. FK 대상 부모 테이블을 먼저 만들거나, 자식 테이블 생성 후 FK를 별도 changeSet에서 추가한다. 특히 `notices`를 참조하는 좋아요/댓글, `composite_jobs`를 참조하는 `participation_videos`/`motif_results`는 생성 순서를 주의한다.
- JPA Auditing을 사용하더라도 INSERT 시 `mod_dttm`을 자동 채우지 않도록 설정한다. 최초 행의 `mod_dttm`은 NULL이어야 한다.
- 복합 PK 엔티티는 `@EmbeddedId` 또는 `@IdClass` 중 팀 표준 하나를 선택해 일관되게 적용한다.

---

# Users

## 1. 회원 (`users`)

모든 서비스 행위 주체의 기준 테이블이다. 일반 사용자, 아티스트, 서비스 관리자는 모두 `users`에 존재한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `user_seq` | `INTEGER` | Y | PK, IDENTITY | 회원 식별자 |
| `nickname` | `VARCHAR(50)` | Y | CHECK | 서비스 표시 이름. 중복 허용 |
| `profile_image_key` | `VARCHAR(255)` | N | - | 프로필 이미지 S3 키 |
| `rep_motif_result_seq` | `BIGINT` | N | 논리 참조 | 대표 모티프 결과. 순환 참조 방지를 위해 FK 미적용 |
| `role` | `VARCHAR(10)` | Y | CHECK | `USER`, `ARTIST`, `ADMIN` |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 서비스 사용자 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 가입 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 마지막 실제 수정 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 논리 변경 주체. 시스템/배치는 NULL |

주요 CHECK:

```sql
role IN ('USER','ARTIST','ADMIN')
```

```sql
char_length(nickname) BETWEEN 1 AND 50
```

서비스 규칙:

- 일반 USER 닉네임 형식/2~20자 정책은 API에서 검증한다.
- 닉네임은 중복을 허용한다 *(회의 확정)*. 사용자 식별은 항상 `user_seq`로 하며, 화면에서 아티스트와 일반 사용자는 `role` 기반 뱃지로 구분한다.
- `is_deleted=TRUE` 사용자는 서비스상 유효 사용자로 취급하지 않으며 해당 사용자가 소유한 콘텐츠(참여 영상, 결과물, 댓글 3종 포함)는 일반 API에서 노출하지 않는다. 콘텐츠 행 자체를 일괄 소프트 삭제하지 않고 조회 시 사용자 삭제 상태를 함께 검증한다.
- 루트 댓글 작성자가 삭제 사용자이면 그 답글 스레드 전체를 비노출한다. 비노출 콘텐츠도 `comment_count`, `participant_count` 등 카운터에는 그대로 포함되므로 표시 개수와 불일치할 수 있음을 감수한다 *(회의 확정)*.
- 인증 시 `users.is_deleted=FALSE`와 `user_accounts.account_status='ACTIVE'`를 모두 확인한다.
- `rep_motif_result_seq`를 설정할 때 해당 결과가 같은 `user_seq` 소유이며 `is_deleted=FALSE`인지 검증한다.
- 대표 결과가 소프트 삭제되면 같은 트랜잭션에서 `rep_motif_result_seq=NULL`로 변경한다.
- pg_trgm GIN 대상은 `users.nickname`, `artist_groups.group_name`, 그리고 v0.17 의 `artist_aliases.alias`·`group_aliases.alias` 넷뿐이다. **팬 앱 검색(API §5.2)은 별칭 두 컬럼만 본다** — `users.nickname` 을 보는 것은 어드민 §3.5.1 목록 검색이다.

## 2. 사용자 계정 (`user_accounts`)

사용자 프로필과 인증 계정 상태를 분리한다. 현재 user당 계정 1개다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `account_seq` | `INTEGER` | Y | PK, IDENTITY | 인증 계정 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users, UNIQUE | 연결 사용자. 1:1 |
| `account_status` | `VARCHAR(12)` | Y | 기본값 ACTIVE, CHECK | `ACTIVE`, `SUSPENDED` |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 계정 생성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 계정 상태 변경 일시 |

CHECK:

```sql
account_status IN ('ACTIVE','SUSPENDED')
```

`WITHDRAWN`은 향후 탈퇴 기능 확장 후보일 뿐 현재 허용값과 구현 범위에는 포함하지 않는다.

## 3. OAuth 제공자 (`oauth_providers`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `provider_seq` | `INTEGER` | Y | PK, IDENTITY | 제공자 식별자 |
| `provider_code` | `VARCHAR(20)` | Y | UNIQUE, CHECK | `GOOGLE`, 향후 `KAKAO`, `APPLE` 등 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |

CHECK:

```sql
provider_code ~ '^[A-Z][A-Z0-9_]{1,19}$'
```

## 4. 사용자 OAuth 연결 (`user_oauth_accounts`)

하나의 서비스 계정에 서로 다른 OAuth 제공자 계정을 연결할 수 있다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `oauth_account_seq` | `INTEGER` | Y | PK, IDENTITY | OAuth 연결 식별자 |
| `account_seq` | `INTEGER` | Y | FK → user_accounts | 서비스 인증 계정 |
| `provider_seq` | `INTEGER` | Y | FK → oauth_providers | OAuth 제공자 |
| `oauth_subject` | `VARCHAR(255)` | Y | - | 제공자가 발급한 subject/sub |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 연결 일시 |

UNIQUE:

- `(account_seq, provider_seq)`
- `(provider_seq, oauth_subject)`

## 5. 시연/운영 편의 어드민 (`admins`)

시연 편의를 위한 가짜 백오피스 로그인용 테이블이다. 서비스 사용자 모델과 별개이며 실제 서비스 관리자는 `users.role='ADMIN'`으로 관리한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `admin_seq` | `INTEGER` | Y | PK, IDENTITY | 편의용 어드민 식별자 |
| `login_id` | `VARCHAR(30)` | Y | UNIQUE | 백오피스 로그인 ID |
| `password_hash` | `VARCHAR(60)` | Y | - | BCrypt 해시 |
| `name` | `VARCHAR(30)` | Y | - | 표시 이름 |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 사용 가능 여부 |
| `last_login_dttm` | `TIMESTAMPTZ` | N | - | 마지막 로그인 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 생성 일시 |

`admins`와 `users` 사이에는 관계를 두지 않는다. 도메인 데이터의 `reg_usr_seq`/`mod_usr_seq` 등 실제 관리자 행위자는 `users.role='ADMIN'` 계정의 `user_seq`를 기록한다.

## 6. 리프레시 토큰 (`refresh_tokens`)

기기별 로그인 유지, rotation, 개별 로그아웃을 관리한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `token_seq` | `BIGINT` | Y | PK, IDENTITY | 토큰 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 회원 |
| `token_hash` | `CHAR(64)` | Y | UNIQUE | SHA-256 hex |
| `expires_dttm` | `TIMESTAMPTZ` | Y | CHECK | 만료 일시 |
| `revoked_dttm` | `TIMESTAMPTZ` | N | - | rotation·로그아웃·정지 폐기 시각 |
| `last_used_dttm` | `TIMESTAMPTZ` | N | - | 마지막 재발급 사용 시각 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 발급 일시 |

CHECK:

```sql
expires_dttm > reg_dttm
```

서비스 규칙:

- refresh token 사용 시 해당 행을 잠그고 `last_used_dttm`, `revoked_dttm`을 갱신한 뒤 새 토큰 행을 INSERT한다.
- 일반 로그아웃은 현재 기기의 refresh token만 revoke한다.
- `account_status='SUSPENDED'` 전이 또는 `users.is_deleted=TRUE` 처리 시 해당 사용자의 모든 활성 refresh token을 revoke하고 신규 access/refresh token 발급을 거부한다. 정지 해제 후에도 기존 refresh token을 복구하지 않고 재로그인한다.
- revoke/만료 후 오래된 행은 보안·운영 정책의 보존 기간에 따라 주기적으로 하드 삭제한다. 보존 기간은 DB 제약으로 고정하지 않는다.

---

# Artists

## 7. 소속사 (`companies`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `company_seq` | `INTEGER` | Y | PK, IDENTITY | 소속사 식별자 |
| `company_name` | `VARCHAR(50)` | Y | 부분 UNIQUE | 회사명 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 논리 변경 주체 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

활성 회사명 부분 UNIQUE:

```sql
CREATE UNIQUE INDEX uq_companies_name_active
ON companies (company_name)
WHERE is_deleted = FALSE;
```

서비스 규칙:

- 비삭제 `artist_groups` 또는 `artists`가 참조 중이면 회사 소프트 삭제를 거부한다.
- 하위 그룹/아티스트를 먼저 다른 회사로 이동해야 한다.

## 8. 아티스트 그룹 (`artist_groups`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `group_seq` | `INTEGER` | Y | PK, IDENTITY | 그룹 식별자 |
| `company_seq` | `INTEGER` | Y | FK → companies | 소속사 |
| `group_name` | `VARCHAR(50)` | Y | - | 그룹명. 중복 허용. 어드민 §3.4.1 목록 검색 대상(pg_trgm). **팬 앱 §5.2 검색은 이 칼럼이 아니라 `group_aliases.alias` 를 본다**(v0.17) |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 논리 변경 주체 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

서비스 규칙:

- `company_seq`가 가리키는 회사는 비삭제 상태여야 한다.
- 비삭제 `artists`가 참조 중이면 그룹 소프트 삭제를 거부한다.
- 그룹 삭제 전 아티스트를 다른 그룹으로 이동하거나 `group_seq=NULL`로 변경한다.
- `artist_groups.company_seq` 변경 시 해당 그룹의 모든 비삭제 멤버 `artists.company_seq`를 같은 트랜잭션에서 동일 회사로 갱신한다.

## 9. 아티스트 (`artists`)

`users.role='ARTIST'` 사용자에 붙는 아티스트 도메인 확장 정보다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `artist_seq` | `INTEGER` | Y | PK, IDENTITY | 아티스트 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users, UNIQUE | 대응 ARTIST 사용자 |
| `group_seq` | `INTEGER` | N | FK → artist_groups | 그룹 소속. 솔로는 NULL |
| `company_seq` | `INTEGER` | Y | FK → companies | 소속사 |
| `motif_bg_image_key` | `VARCHAR(255)` | N | - | 모티프 샵 배너 배경. 공개 콘텐츠 — CloudFront 일반 URL 로 변환해 노출 *(R5 확인)* |
| `bio` | `TEXT` | N | - | 아티스트 소개 |
| `subscriber_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 구독자 수 거울 카운터 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

서비스 불변식:

- 비삭제 artist의 `user_seq`는 `users.role='ARTIST' AND users.is_deleted=FALSE`여야 한다.
- `users.role='ARTIST' AND users.is_deleted=FALSE`인 사용자는 비삭제 artist 행 하나를 가져야 한다.
- `group_seq IS NOT NULL`이면 해당 그룹이 비삭제 상태이고 `artists.company_seq = artist_groups.company_seq`여야 한다.
- 그룹 소속 artist는 `company_seq`를 독립적으로 변경하지 않고 그룹 변경/그룹 회사 변경 흐름을 통해 갱신한다.
- `company_seq`가 가리키는 회사는 비삭제 상태여야 한다.
- 아티스트 삭제 시 `artists.is_deleted=TRUE`, 연결 `users.is_deleted=TRUE`를 같은 트랜잭션에서 처리한다.
- 아티스트 삭제 시 해당 아티스트의 `motif_assets.is_active=FALSE`, `motif_asset_prices.is_active=FALSE`, `motif_pass_prices.is_active=FALSE`로 판매를 중단한다.
- 해당 아티스트 ACTIVE PASS의 현재 유효기간은 즉시 취소하지 않으며, 판매 중단 전에 취득한 ACTIVE PASS 사용자는 기존 `expires_dttm`까지 해당 아티스트의 기존 에셋을 계속 이용할 수 있다.
- 과거 아티스트 좋아요/댓글 자체는 아티스트 계정 삭제만으로 수정하지 않는다.

## 10. 구독 (`subscriptions`)

무료 팔로우 관계다. 해제 시 하드 삭제한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `user_seq` | `INTEGER` | Y | PK, FK → users | 구독 회원 |
| `artist_seq` | `INTEGER` | Y | PK, FK → artists | 구독 대상 아티스트 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 구독 일시 |

복합 PK: `(user_seq, artist_seq)`.

- 구독 생성/삭제와 `artists.subscriber_count` 증감을 같은 트랜잭션에서 처리한다.
- 사용자 계정 소프트 삭제 정리가 수행되는 경우 `subscriptions`는 관계 데이터이므로 하드 삭제 대상이다.

---

# Challenges

## 11. 곡 (`songs`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `song_seq` | `INTEGER` | Y | PK, IDENTITY | 곡 식별자 |
| `title` | `VARCHAR(100)` | Y | - | 곡명 |
| `singer_name` | `VARCHAR(100)` | Y | - | 가수명 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |

동일 곡 중복은 리믹스·재녹음 등을 고려해 DB UNIQUE를 두지 않는다. 곡은 현재 검색 인덱스 대상이 아니다.

## 12. 챌린지 (`challenges`)

아티스트의 원본 챌린지 영상이다. 별도 `status`는 사용하지 않고 `is_deleted`만 사용한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `challenge_seq` | `INTEGER` | Y | PK, IDENTITY | 챌린지 식별자 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 메인 아티스트. 생성 후 불변 |
| `song_seq` | `INTEGER` | Y | FK → songs | 곡 |
| `description` | `TEXT` | Y | - | 소개글 |
| `video_key` | `VARCHAR(255)` | Y | - | 원본 영상 S3 키 |
| `thumbnail_key` | `VARCHAR(255)` | Y | - | 썸네일 |
| `duration_ms` | `INTEGER` | Y | CHECK (`> 0`) | 영상 길이(ms) |
| `view_count` | `BIGINT` | Y | 기본값 0, CHECK (`>= 0`) | 누적 조회수 |
| `participant_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 비삭제 참여 영상 수. PUBLIC/PRIVATE 모두 포함 |
| `like_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 좋아요 수 |
| `comment_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 비삭제 댓글 수 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |

서비스 규칙:

- `artist_seq`는 생성 이후 변경하지 않는다.
- `is_deleted=TRUE` 챌린지에는 신규 참여/좋아요/댓글을 생성하지 않는다.
- 챌린지 소프트 삭제 시 자식 참여 영상/댓글/좋아요 행은 연쇄 삭제하지 않는다.
- 삭제된 챌린지에 속한 참여 영상은 자체 `is_deleted=FALSE`여도 일반 API에서 노출하지 않는다.
- `participant_count`는 `COUNT(*) WHERE challenge_seq=? AND is_deleted=FALSE`와 일치해야 하며 visibility는 조건에 포함하지 않는다.

## 13. 챌린지 추가 참여 아티스트 · 편집본 (`challenge_participants`)

메인 `challenges.artist_seq`를 제외한 추가 참여 아티스트만 저장한다. 각 행은 합성 시 사용자가 교체해 들어갈 수 있는 **인물 슬롯**이며, 메인 아티스트는 교체 대상이 아니다 *(회의 확정)*.

**v0.18(changeSet 0019)에서 편집본이 이 테이블로 들어왔다.** 편집본은 "그 사람을 지운 영상"이고 슬롯 1개당 1개다 — 사용자가 고르는 것이 **함께할 아티스트 한 명**이므로 2명 이상을 동시에 지운 조합이 없어졌다. 조합을 표현하던 `participant_mask` 는 `slot_order` 로 바뀌었고 `edited_challenges`(구 §14)는 폐기됐다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `challenge_participant_seq` | `INTEGER` | Y | PK, IDENTITY | 슬롯 식별자 *(v0.18 · changeSet 0019 — `composite_jobs` 가 이 값을 가리킨다)* |
| `challenge_seq` | `INTEGER` | Y | FK → challenges, 복합 UNIQUE ×2 | 챌린지 |
| `artist_seq` | `INTEGER` | Y | FK → artists, 복합 UNIQUE | 추가 참여 아티스트 |
| `slot_order` | `INTEGER` | Y | CHECK, 복합 UNIQUE | 화면 좌→우 순서, **0부터**. AI 페이로드의 `participant_mask` 는 `1 << slot_order` 로 파생한다 *(v0.18 — 구 `participant_mask` 컬럼 대체)* |
| `anchor_x` | `DOUBLE PRECISION` | N | CHECK | 선택 화면 프로필 원 중심 x — **썸네일 가로 대비 0~1 비율** *(v0.16 · changeSet 0017)* |
| `anchor_y` | `DOUBLE PRECISION` | N | CHECK | 원 중심 y — 썸네일 세로 대비 0~1 비율 |
| `anchor_r` | `DOUBLE PRECISION` | N | CHECK | 원 반지름 — 썸네일 가로 대비 비율, `0 < r ≤ 0.5`. 시안(402 폭·지름 80) 기본값 `0.0995` |
| `edited_challenge_key` | `VARCHAR(255)` | N | CHECK | **이 아티스트를 지운** 편집본 영상 S3 키 — CHALLENGE 합성의 베이스이자 §7.6 이 앱에 내주는 영상. **`private/edited/…`** *(v0.19 — 앱에 나가므로 서명 등급)*. `NULL` 이면 편집본이 아직 없는 슬롯이다 *(v0.18 — 구 `edited_challenges.video_key`)* |
| `initial_scene_key` | `VARCHAR(255)` | N | CHECK | 촬영 가이드용 첫 장면 이미지 키. `edited_challenge_key` 와 **짝** *(v0.18)*. `private/edited/…` *(v0.19)* |
| `preview_image_key` | `VARCHAR(255)` | N | - | 촬영 전 선택 화면에서 이 슬롯을 골랐을 때 썸네일 자리에 보이는 이미지 키(AI팀 산출). 편집본이 있어도 `NULL` 일 수 있다 — 클라이언트가 챌린지 썸네일로 폴백한다 *(v0.16 · v0.18 이관 · v0.19 `private/`)* |
| `edited_duration_ms` | `INTEGER` | N | CHECK (`> 0`) | 편집본 길이. **앱 촬영 타이머가 쓴다**(§7.6) — 원본 길이로 갈음하면 ±500ms 만큼 녹화 끝이 잘린다. 편집본이 있으면 서비스가 필수로 받는다 *(v0.19 · changeSet 0020)* |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 *(v0.18 신설 — 편집본 키를 갖게 되어 이력이 의미를 얻었다)* |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |
| ~~`participant_mask`~~ | — | — | — | **v0.18 삭제** — 조합이 없어져 `slot_order` 가 대신한다 |
| ~~`bbox_left_top_x/y` · `bbox_right_bottom_x/y`~~ | — | — | — | **v0.16 삭제** — 사각형 중심은 원 자리가 못 되고, 원본 해상도를 저장하지 않아 비율로 바꿀 수도 없었다 |

PK: `challenge_participant_seq`. UNIQUE: `uq_challenge_participants_artist (challenge_seq, artist_seq)` · `uq_challenge_participants_slot_order (challenge_seq, slot_order)`.

CHECK:

```sql
-- ck_challenge_participants_slot_order (0019) — 상한은 슬롯 하드 상한 4 와 같은 값이다
slot_order >= 0 AND slot_order < 4
```

```sql
-- ck_challenge_participants_anchor (0017) — 셋 다 NULL(0017 이전 행) 또는 셋 다 유효
(anchor_x IS NULL AND anchor_y IS NULL AND anchor_r IS NULL)
OR (anchor_x BETWEEN 0 AND 1 AND anchor_y BETWEEN 0 AND 1 AND anchor_r > 0 AND anchor_r <= 0.5)
```

```sql
-- ck_challenge_participants_edited (0019) — 영상과 첫 장면은 짝이다. 둘 다 NULL(편집본
-- 미등록)이거나 둘 다 채워져 있다. preview_image_key 는 이 짝에 들어가지 않는다
(edited_challenge_key IS NULL AND initial_scene_key IS NULL)
OR (edited_challenge_key IS NOT NULL AND initial_scene_key IS NOT NULL)
```

```sql
-- ck_challenge_participants_edited_duration (0020)
edited_duration_ms IS NULL OR edited_duration_ms > 0
```

서비스 규칙:

- **메인 아티스트도 슬롯이 될 수 있다** — `artist_seq = challenges.artist_seq` 를 막지 않는다. 메인은 "이 챌린지가 누구 것인가"(귀속)이고 슬롯은 "영상 속 어느 자리를 가져갈 수 있는가"라 층이 다르다. 슬롯끼리의 중복만 UNIQUE 가 막는다.
- `slot_order` 는 **요청 배열 순서대로 서버가 부여한다**(어드민 §3.7.2). 어드민이 직접 입력하지 않으므로 인물과 자리가 어긋날 수 없다.
- **`slot_order` 는 재정렬용 숫자가 아니다.** `notice_images.display_order` 와 이름이 비슷하지만 성질이 다르다 — 앵커·편집본과 짝이고 AI 페이로드의 마스크가 여기서 파생되므로, 순서를 바꾸면 **합성이 교체하는 인물이 바뀐다**. 슬롯 구성은 개별 이동이 아니라 통째로 지웠다 다시 만든다(어드민 §3.7.5).
- 앵커는 **비율**이라 좌표계가 따로 없다 — 클라이언트는 썸네일을 그린 뷰 크기 `W×H` 를 곱해 중심 `(x·W, y·H)`, 반지름 `r·W` 로 원을 그린다. **`NULL` 은 0017 이전 행**이며 어드민 §3.7.9 로 채운다(그때까지 앱은 원을 숨긴다). 신규 등록은 서비스가 필수로 받는다.
- **편집본이 없는 슬롯은 정상 상태다** *(v0.18 — 전 조합 등록 전제 폐기)*. 앱 §7.5 가 `initialSceneUrl = null` 로 내려 화면에서 잠그고, 그래도 §7.6·§13.1 로 오면 `EDITED_CHALLENGE_NOT_FOUND` 가 막는다. **DB CHECK 가 영상 ↔ 첫 장면 짝을 강제하므로 그 값의 유무가 곧 판정이다** — 앱이 따로 플래그를 받지 않는 근거다 *(v0.19)*.
- **편집본 키는 `private/` 아래다** *(v0.19)*. §7.6 이 앱에 영상을 내주면서 서명이 필요해졌고, 서명 여부는 프리픽스가 가른다 — CloudFront 캐시 동작이 `private/*` 하나라 키를 옮기지 않으면 서명이 검증되지 않는다. AI 워커는 S3 로 직접 받으므로 영향이 없지만 **읽기 권한 프리픽스는 함께 옮겨야 한다**.
- 편집본 영상/첫 장면 교체는 같은 행의 키 갱신으로 처리한다(`mod_dttm` 기록, 어드민 §3.7.6). 인물·순서가 그대로라 `composite_jobs` 가 참조 중이어도 허용된다 — 결과물은 별도 오브젝트다.
- 슬롯 구성 교체는 하드 삭제/재생성이다. `composite_jobs` 가 참조하는 행은 FK RESTRICT 라 지울 수 없고, 어드민 API 가 먼저 409 `EDITED_IN_USE` 로 거른다.
- 자체 `is_deleted`를 두지 않는다. 노출·참여 가능 여부는 원본 `challenges.is_deleted`를 따른다.

## 14. ~~챌린지 편집본 (`edited_challenges`)~~ — v0.18 폐기

**changeSet 0019 에서 삭제됐다.** 편집본이 슬롯 1개당 1개가 되면서 §13 `challenge_participants` 로 합쳐졌다(`video_key` → `edited_challenge_key`, `initial_scene_key`·`preview_image_key` 는 이름 그대로).

없어진 개념:

- **조합 마스크** — `participant_mask` 가 비트 OR 합이라 슬롯 n개면 `1 … 2ⁿ−1` 이었다. 사용자가 고르는 것이 함께할 아티스트 한 명으로 정리되면서 조합 자체가 사라졌다.
- **전 조합 등록 전제** *(구 회의 확정 R8)* 와 어드민의 조합 완결성 검증(`EDITED_SET_INCOMPLETE`). 이제 편집본이 비어 있는 슬롯은 오류가 아니라 "아직 안 만든 자리"다.

번호는 비워 두지 않고 그대로 둔다 — 이후 절 번호가 밀리면 다른 문서의 상호 참조가 전부 어긋난다.

---

# Participation Videos

## 15. 참여 영상 (`participation_videos`)

사용자가 챌린지에 참여해 합성·게시한 영상이다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `video_seq` | `BIGINT` | Y | PK, IDENTITY | 참여 영상 식별자 |
| `challenge_seq` | `INTEGER` | Y | FK → challenges | 원본 챌린지 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 원본 챌린지의 메인 artist 반정규화 |
| `user_seq` | `INTEGER` | Y | FK → users | 게시 사용자 |
| `job_seq` | `BIGINT` | Y | FK → composite_jobs, UNIQUE | 생성 CHALLENGE 잡 |
| `description` | `TEXT` | Y | - | 참여 영상 설명 |
| `video_key` | `VARCHAR(255)` | Y | - | 합성 결과 영상 키 |
| `thumbnail_key` | `VARCHAR(255)` | Y | - | 썸네일 키 |
| `duration_ms` | `INTEGER` | Y | CHECK (`> 0`) | 영상 길이(ms) |
| `visibility` | `VARCHAR(10)` | Y | 기본값 PUBLIC, CHECK | `PUBLIC`, `PRIVATE` |
| `comment_enabled` | `BOOLEAN` | Y | 기본값 TRUE | 댓글 기능 활성 여부 |
| `view_count` | `BIGINT` | Y | 기본값 0, CHECK (`>= 0`) | 누적 조회수 |
| `like_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 좋아요 수 |
| `comment_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 비삭제 댓글 수 |
| `has_artist_like` | `BOOLEAN` | Y | 기본값 FALSE | ARTIST role 사용자의 활성 좋아요 존재 여부 |
| `has_artist_comment` | `BOOLEAN` | Y | 기본값 FALSE | ARTIST role 사용자의 비삭제 댓글 존재 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 게시 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 직접 수정 사용자 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |

CHECK:

```sql
visibility IN ('PUBLIC','PRIVATE')
```

서비스 검증:

- `job_seq`의 잡은 `job_type='CHALLENGE' AND status='DONE'`이어야 한다.
- `job.user_seq = participation_videos.user_seq`여야 한다.
- 게시 사용자의 `role='ARTIST'` 면 게시를 거부한다(403) — 아티스트 계정은 참여 영상을 게시하지 않는다 *(회의 확정 R5)*.
- `artist_seq`는 요청값을 사용하지 않고 `challenge_seq → challenges.artist_seq`에서 복사한다.
- 최종 결과 행 생성 직전에 원본 챌린지가 `is_deleted=FALSE`인지 다시 확인한다. 삭제되었으면 참여 영상은 생성하지 않고 잡을 FAILED 처리한다.
- 생성 성공 시 같은 트랜잭션에서 `challenges.participant_count + 1` 한다.
- 참여 영상 소프트 삭제 시 최초 삭제인 경우에만 `participant_count - 1` 한다. 신고 ACCEPTED 삭제도 동일하다.
- `PRIVATE`은 DB 카운터, 댓글, 좋아요 등 데이터 처리에는 PUBLIC과 동일하게 포함한다. 조회는 작성자 본인만 가능하며 다른 사용자에게는 존재 여부 자체를 노출하지 않는다.
- `PUBLIC ↔ PRIVATE` 변경은 기존 댓글/좋아요/카운터를 수정하지 않는다.
- `participant_count`에 PRIVATE이 포함되어 개수로는 존재가 간접 노출될 수 있음을 감수한다 *(회의 확정)*.
- `comment_enabled=FALSE`이면 기존 댓글/댓글좋아요/카운터는 그대로 보존하지만 댓글 목록 API를 막고 신규 댓글·답글 작성 및 댓글 좋아요 생성/해제를 모두 차단한다. 즉 댓글 영역 데이터를 동결한다. 다시 TRUE가 되면 기존 데이터가 다시 노출될 수 있다.
- `is_deleted=TRUE`이면 신규 좋아요/댓글/관심없음 관계를 생성하지 않는다.

## 16. 영상 좋아요 (`video_likes`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `video_seq` | `BIGINT` | Y | PK, FK → participation_videos | 대상 영상 |
| `user_seq` | `INTEGER` | Y | PK, FK → users | 좋아요 사용자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 좋아요 일시 |

복합 PK: `(video_seq, user_seq)`.

- 토글 해제 시 하드 삭제한다.
- 생성/삭제와 `participation_videos.like_count` 원자 증감을 같은 트랜잭션으로 처리한다.
- 해당 좋아요 행이 ARTIST role 사용자의 행이면 `has_artist_like`를 재계산한다.
- 아티스트 계정 자체가 이후 소프트 삭제된다는 이유만으로 과거 좋아요 행을 수정하지 않는다.

## 17. 관심 없음 (`video_hides`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `user_seq` | `INTEGER` | Y | PK, FK → users | 회원 |
| `video_seq` | `BIGINT` | Y | PK, FK → participation_videos | 숨긴 영상 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 처리 일시 |

복합 PK: `(user_seq, video_seq)`. 행 존재 자체가 관심 없음 상태다.

## 18. 챌린지 좋아요 (`challenge_likes`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `challenge_seq` | `INTEGER` | Y | PK, FK → challenges | 챌린지 |
| `user_seq` | `INTEGER` | Y | PK, FK → users | 사용자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 좋아요 일시 |

복합 PK: `(challenge_seq, user_seq)`. 토글 해제 시 하드 삭제한다.

## 19. 공지 좋아요 (`notice_likes`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `notice_seq` | `INTEGER` | Y | PK, FK → notices | 공지 |
| `user_seq` | `INTEGER` | Y | PK, FK → users | 사용자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 좋아요 일시 |

복합 PK: `(notice_seq, user_seq)`. 토글 해제 시 하드 삭제한다.

---

# Comments & Reports

## 20. 챌린지 댓글 (`challenge_comments`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `challenge_comment_seq` | `BIGINT` | Y | PK, IDENTITY | 댓글 식별자 |
| `challenge_seq` | `INTEGER` | Y | FK → challenges | 대상 챌린지 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성 사용자 |
| `parent_comment_seq` | `BIGINT` | N | FK → challenge_comments | 부모 댓글. 루트는 NULL |
| `content` | `TEXT` | Y | - | 댓글 내용 |
| `like_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 좋아요 수 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 작성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 마지막 수정 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 논리 변경 주체 |

CHECK:

```sql
parent_comment_seq IS NULL OR parent_comment_seq <> challenge_comment_seq
```

## 21. 참여 영상 댓글 (`video_comments`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `video_comment_seq` | `BIGINT` | Y | PK, IDENTITY | 댓글 식별자 |
| `video_seq` | `BIGINT` | Y | FK → participation_videos | 대상 참여 영상 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성 사용자 |
| `parent_comment_seq` | `BIGINT` | N | FK → video_comments | 부모 댓글. 루트는 NULL |
| `content` | `TEXT` | Y | - | 댓글 내용 |
| `like_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 좋아요 수 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 작성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 마지막 수정 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 논리 변경 주체 |

CHECK:

```sql
parent_comment_seq IS NULL OR parent_comment_seq <> video_comment_seq
```

아티스트 댓글 행 생성/삭제 시 `participation_videos.has_artist_comment`를 재계산한다.

## 22. 공지 댓글 (`notice_comments`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `notice_comment_seq` | `BIGINT` | Y | PK, IDENTITY | 댓글 식별자 |
| `notice_seq` | `INTEGER` | Y | FK → notices | 대상 공지 |
| `user_seq` | `INTEGER` | Y | FK → users | 작성 사용자 |
| `parent_comment_seq` | `BIGINT` | N | FK → notice_comments | 부모 댓글. 루트는 NULL |
| `content` | `TEXT` | Y | - | 댓글 내용 |
| `like_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 좋아요 수 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 소프트 삭제 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 작성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 마지막 수정 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 논리 변경 주체 |

CHECK:

```sql
parent_comment_seq IS NULL OR parent_comment_seq <> notice_comment_seq
```

### 댓글 공통 서비스 규칙

- 루트 + 답글의 최대 Depth 2만 허용한다.
- `parent_comment_seq`가 가리키는 행은 반드시 루트 댓글이어야 한다.
- 부모와 자식은 동일한 challenge/video/notice를 가져야 한다. 단순 self FK만으로는 이 조건이 보장되지 않으므로 서비스에서 검증한다.
- 댓글 생성 시 부모 콘텐츠가 비삭제 상태인지 검증한다. 참여 영상은 추가로 `comment_enabled=TRUE`인지 확인한다.
- 댓글 직접 삭제는 소프트 삭제한다.
- 루트 댓글 삭제 시 아직 비삭제인 직계 답글도 같은 트랜잭션에서 소프트 삭제한다.
- 삭제 대상 댓글들의 댓글 좋아요 행은 하드 삭제한다.
- 대상 콘텐츠의 `comment_count`는 이번 트랜잭션에서 실제 `FALSE → TRUE`로 변경된 댓글 행 수만큼 감소한다.
- 답글 단독 삭제는 해당 답글만 소프트 삭제하고 `comment_count - 1` 한다.
- `comment_count` 재계산 기준은 `COUNT(*) WHERE is_deleted=FALSE`다.
- 신고 ACCEPTED 삭제도 위와 동일한 삭제 정책을 사용한다.
- 이미 삭제된 댓글을 신고 처리하는 경우 신고 상태는 정상 처리하되 중복 카운터 감소/관계 삭제를 수행하지 않는다.
- 상위 게시물 자체가 소프트 삭제된 경우 댓글 행까지 대량 연쇄 소프트 삭제하지 않는다.

## 23. 챌린지 댓글 좋아요 (`challenge_comment_likes`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `challenge_comment_seq` | `BIGINT` | Y | PK, FK → challenge_comments | 댓글 |
| `user_seq` | `INTEGER` | Y | PK, FK → users | 사용자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 좋아요 일시 |

복합 PK: `(challenge_comment_seq, user_seq)`. 토글 해제 또는 대상 댓글 삭제 시 하드 삭제한다.

## 24. 참여 영상 댓글 좋아요 (`video_comment_likes`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `video_comment_seq` | `BIGINT` | Y | PK, FK → video_comments | 댓글 |
| `user_seq` | `INTEGER` | Y | PK, FK → users | 사용자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 좋아요 일시 |

복합 PK: `(video_comment_seq, user_seq)`. 토글 해제 또는 대상 댓글 삭제 시 하드 삭제한다.

## 25. 공지 댓글 좋아요 (`notice_comment_likes`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `notice_comment_seq` | `BIGINT` | Y | PK, FK → notice_comments | 댓글 |
| `user_seq` | `INTEGER` | Y | PK, FK → users | 사용자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 좋아요 일시 |

복합 PK: `(notice_comment_seq, user_seq)`. 토글 해제 또는 대상 댓글 삭제 시 하드 삭제한다.

## 26. 신고 (`reports`)

참여 영상과 댓글 3종을 신고한다. 원본 챌린지 자체는 신고 대상이 아니다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `report_seq` | `BIGINT` | Y | PK, IDENTITY | 신고 식별자 |
| `reporter_seq` | `INTEGER` | Y | FK → users | 신고 사용자 |
| `video_seq` | `BIGINT` | N | FK → participation_videos | 영상 신고 대상 |
| `challenge_comment_seq` | `BIGINT` | N | FK → challenge_comments | 챌린지 댓글 신고 대상 |
| `video_comment_seq` | `BIGINT` | N | FK → video_comments | 참여 영상 댓글 신고 대상 |
| `notice_comment_seq` | `BIGINT` | N | FK → notice_comments | 공지 댓글 신고 대상 |
| `reason_code` | `VARCHAR(30)` | Y | - | 신고 사유 코드 |
| `status` | `VARCHAR(12)` | Y | 기본값 RECEIVED, CHECK | `RECEIVED`, `ACCEPTED`, `REJECTED` |
| `processed_usr_seq` | `INTEGER` | N | FK → users | 처리 관리자 사용자 |
| `processed_dttm` | `TIMESTAMPTZ` | N | - | 처리 일시 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 신고 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 상태 수정 일시 |

정확히 하나의 대상 CHECK:

```sql
(
  (video_seq IS NOT NULL)::int
+ (challenge_comment_seq IS NOT NULL)::int
+ (video_comment_seq IS NOT NULL)::int
+ (notice_comment_seq IS NOT NULL)::int
) = 1
```

처리 상태 CHECK:

```sql
(
  status = 'RECEIVED'
  AND processed_usr_seq IS NULL
  AND processed_dttm IS NULL
)
OR
(
  status IN ('ACCEPTED','REJECTED')
  AND processed_usr_seq IS NOT NULL
  AND processed_dttm IS NOT NULL
)
```

1인 1대상 1회 신고 UNIQUE:

- `(reporter_seq, video_seq)`
- `(reporter_seq, challenge_comment_seq)`
- `(reporter_seq, video_comment_seq)`
- `(reporter_seq, notice_comment_seq)`

서비스 규칙:

- 처리자의 `users.role='ADMIN'`을 검증한다.
- 동일 사용자는 신고 처리 결과(`ACCEPTED`, `REJECTED`)와 관계없이 동일 대상을 한 번만 신고할 수 있으며 재신고할 수 없다.
- `ACCEPTED` 시 대상 삭제와 필요한 카운터/관계 후처리를 같은 트랜잭션에서 수행한다.
- 참여 영상 ACCEPTED는 최초 삭제인 경우에만 `challenges.participant_count - 1` 한다.
- 댓글 ACCEPTED는 댓글 공통 삭제 정책을 그대로 적용한다.
- 신고 접수 후 대상이 먼저 삭제되어도 신고 행은 ACCEPTED/REJECTED로 정상 처리할 수 있다. 삭제 후처리는 멱등하게 수행한다.

---

# Notices

## 27. 공지 (`notices`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `notice_seq` | `INTEGER` | Y | PK, IDENTITY | 공지 식별자 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 작성/소유 아티스트 |
| `content` | `TEXT` | Y | - | 본문 |
| `like_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 좋아요 수 |
| `comment_count` | `INTEGER` | Y | 기본값 0, CHECK (`>= 0`) | 비삭제 댓글 수 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 삭제 여부 |

- `is_deleted=TRUE`이면 신규 좋아요/댓글을 생성하지 않는다.
- 공지 소프트 삭제 시 기존 댓글/좋아요/이미지는 연쇄 삭제하지 않는다.
- 최신순 조회한다. 등록 후 구독자 팬아웃은 `subscriptions`를 직접 조회한다.

## 28. 공지 이미지 (`notice_images`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `notice_image_seq` | `INTEGER` | Y | PK, IDENTITY | 이미지 식별자 |
| `notice_seq` | `INTEGER` | Y | FK → notices | 공지 |
| `image_key` | `VARCHAR(255)` | Y | - | 이미지 S3 키 |
| `display_order` | `SMALLINT` | Y | 기본값 0, CHECK (`>= 0`) | 표시 순서 |

UNIQUE: `(notice_seq, display_order)`.

이미지 교체 시 필요한 관계 행을 하드 삭제 후 재생성한다. 공지 소프트 삭제만으로 이미지를 삭제하지 않는다.

---

# Motif & Payments

## 29. 국가 판매 설정 (`countries`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `country_seq` | `INTEGER` | Y | PK, IDENTITY | 국가 설정 식별자 |
| `country_code` | `CHAR(2)` | Y | UNIQUE, CHECK | ISO 3166-1 alpha-2 형식 코드 |
| `country_name` | `VARCHAR(100)` | Y | - | 국가 표시명 |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 해당 국가 신규 판매 가능 여부 |
| `reg_usr_seq` | `INTEGER` | N | FK → users | 등록 행위자. **NULL = 시스템(마이그레이션 시드) 생성** *(회의 확정 R9)* |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 변경 주체 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

CHECK:

```sql
country_code ~ '^[A-Z]{2}$'
```

- 실제 ISO 코드 유효성은 애플리케이션 사전에서 검증한다.
- `country_code`, `country_name`은 운영상 변경 가능하되 주문에는 코드 스냅샷을 저장한다.
- `is_active=FALSE` 전이 시 해당 국가의 `motif_asset_prices`, `motif_pass_prices` 활성 행을 비활성화한다.
- 해당 국가/가격 조건으로 취득한 ACTIVE PASS의 현재 기간은 유지한다.
- 국가 재활성화 시 가격 행은 자동 재활성화하지 않는다.
- 활성 국가에 활성 가격이 하나도 없는 상태도 허용한다.
- 현재 범위에서는 `KR` 1행만 시드하며 사용자 국가 판정은 서버 설정 상수(`KR`)를 사용한다. 사용자별 국가 컬럼은 두지 않는다 *(현재 범위)*. 시드 행은 `reg_usr_seq=NULL`(시스템 생성), `country_name='대한민국'` 으로 삽입한다 *(회의 확정 R9 — 표시명은 현재 노출처 없음, 필요 시 UPDATE changeSet 정정)*.

## 30. 모티프 에셋 (`motif_assets`)

아티스트별 영상 에셋이다. 삭제 플래그를 사용하지 않는다. `is_active`는 신규 카탈로그 노출·신규 판매 및 일반적인 PASS 이용 가능 상태를 의미한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `asset_seq` | `INTEGER` | Y | PK, IDENTITY | 에셋 식별자 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 아티스트 |
| `title` | `VARCHAR(100)` | Y | - | 에셋 표시명(카탈로그·홈 활동 노출) *(회의 확정 R6)* |
| `description` | `TEXT` | N | - | 소개 문구 |
| `video_key` | `VARCHAR(255)` | Y | - | 에셋 영상 키 |
| `thumbnail_key` | `VARCHAR(255)` | Y | - | 썸네일 |
| `duration_ms` | `INTEGER` | Y | CHECK (`> 0`) | 길이(ms) |
| `asset_type` | `VARCHAR(10)` | Y | 기본값 NORMAL, CHECK | `NORMAL`, `PREMIUM`, `SECRET`. 노출·구매·PASS 커버 판정에서 **SECRET 은 NORMAL 과 동일 취급**, PREMIUM 은 개별 구매 전용(PASS 미커버) *(회의 확정 R5)* |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 신규 카탈로그 노출·신규 판매 및 일반 PASS 이용 가능 상태 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

CHECK:

```sql
asset_type IN ('NORMAL','PREMIUM','SECRET')
```

권한 규칙:

- 신규 카탈로그 노출/구매는 `is_active=TRUE` 에셋만 가능하다.
- PASS 사용자는 원칙적으로 `motif_assets.is_active=TRUE`인 에셋만 이용할 수 있다. 단, 아티스트/에셋 판매 중단 전에 이미 취득한 ACTIVE PASS는 기존 `expires_dttm`까지 해당 시점의 기존 에셋 이용을 허용한다.
- 이미 개별 구매한 사용자는 이후 `is_active=FALSE`가 되어도 `asset_purchases.status='ACTIVE'`이면 새 MOTIF 합성 job을 생성할 수 있다.
- 에셋 비활성화 시 모든 `motif_asset_prices.is_active=FALSE`로 변경한다.
- 에셋 재활성화 시 가격 행은 자동 재활성화하지 않는다.
- 과거 주문/구매/잡에서 참조할 수 있으므로 에셋은 운영 중 물리 삭제하지 않고 `is_active`로 종료 상태를 표현한다.

## 31. 모티프 에셋 국가별 가격 (`motif_asset_prices`)

현재 판매 가격만 보관하며 별도 가격 이력 테이블은 두지 않는다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `asset_price_seq` | `INTEGER` | Y | PK, IDENTITY | 가격 식별자 |
| `asset_seq` | `INTEGER` | Y | FK → motif_assets | 에셋 |
| `country_seq` | `INTEGER` | Y | FK → countries | 국가 |
| `amount` | `NUMERIC(10,2)` | Y | CHECK (`> 0`) | 판매 금액 |
| `currency` | `CHAR(3)` | Y | CHECK | ISO 4217 형식 통화 코드 |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 해당 국가 신규 판매 가능 여부 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 변경 주체 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

UNIQUE: `(asset_seq, country_seq)`.

CHECK:

```sql
amount > 0
AND currency ~ '^[A-Z]{3}$'
```

- 실제 ISO 4217 유효 코드는 애플리케이션에서 검증한다.
- `is_active=TRUE` 전환은 `motif_assets.is_active=TRUE AND countries.is_active=TRUE`일 때만 허용한다.
- 가격/통화 변경 및 가격 비활성화는 기존 개별 구매 권한에 영향을 주지 않는다.

## 32. 모티프 PASS 국가/기간별 가격 (`motif_pass_prices`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `pass_price_seq` | `INTEGER` | Y | PK, IDENTITY | 가격 식별자 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 아티스트 |
| `country_seq` | `INTEGER` | Y | FK → countries | 국가 |
| `duration_months` | `SMALLINT` | Y | CHECK (`> 0`) | 이용 개월 수 |
| `amount` | `NUMERIC(10,2)` | Y | CHECK (`> 0`) | 판매 금액 |
| `currency` | `CHAR(3)` | Y | CHECK | ISO 4217 형식 통화 코드 |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 신규 PASS 판매 가능 여부 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_usr_seq` | `INTEGER` | N | FK → users | 마지막 변경 주체 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

UNIQUE: `(artist_seq, country_seq, duration_months)`.

CHECK:

```sql
duration_months > 0
AND amount > 0
AND currency ~ '^[A-Z]{3}$'
```

- 초기 제공 기간이 1/6/12개월이더라도 DB CHECK로 고정하지 않는다.
- `is_active=TRUE` 전환은 `countries.is_active=TRUE AND artists.is_deleted=FALSE`일 때만 허용한다.
- `amount`, `currency`, `is_active` 변경은 이미 취득한 ACTIVE PASS에 영향을 주지 않는다. 주문에는 결제 시점 가격 스냅샷이 남는다.

## 33. 주문 (`orders`)

모든 개별 에셋/PASS 결제 시도의 이력 원천이다. 주문은 삭제하지 않는다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `order_seq` | `BIGINT` | Y | PK, IDENTITY | 주문 식별자 |
| `order_uuid` | `UUID` | Y | UNIQUE | 외부 노출/결제 요청 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 구매 사용자 |
| `product_type` | `VARCHAR(10)` | Y | CHECK | `ASSET`, `PASS` |
| `asset_seq` | `INTEGER` | N | FK → motif_assets | ASSET 주문 대상 |
| `artist_seq` | `INTEGER` | N | FK → artists | PASS 주문 대상 |
| `country_code` | `CHAR(2)` | Y | CHECK | 주문 시 국가 코드 스냅샷 |
| `pass_duration_months` | `SMALLINT` | N | CHECK (`> 0`) | PASS 기간 스냅샷 |
| `amount` | `NUMERIC(10,2)` | Y | CHECK (`> 0`) | 주문 시 금액 스냅샷 |
| `currency` | `CHAR(3)` | Y | CHECK | 주문 시 통화 스냅샷 |
| `status` | `VARCHAR(12)` | Y | 기본값 PENDING, CHECK | `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELED`, `REFUNDED` |
| `payment_provider` | `VARCHAR(20)` | N | - | 결제 제공자 코드. 현재 `TOSS`만 허용, 확장 시 코드 추가 |
| `payment_key` | `VARCHAR(200)` | N | 복합 UNIQUE | 결제 트랜잭션 키 |
| `approved_dttm` | `TIMESTAMPTZ` | N | - | 승인 일시 |
| `fail_reason` | `VARCHAR(200)` | N | - | 실패 사유 |
| `expires_dttm` | `TIMESTAMPTZ` | Y | CHECK | 결제 가능 만료 시각. 생성 시 `reg_dttm + 30분` |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 주문 생성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 상태 변경 일시 |

UNIQUE:

- `(payment_provider, payment_key)`

상품 형태 CHECK:

```sql
(
  product_type = 'ASSET'
  AND asset_seq IS NOT NULL
  AND artist_seq IS NULL
  AND pass_duration_months IS NULL
)
OR
(
  product_type = 'PASS'
  AND artist_seq IS NOT NULL
  AND asset_seq IS NULL
  AND pass_duration_months IS NOT NULL
  AND pass_duration_months > 0
)
```

공통 CHECK:

```sql
country_code ~ '^[A-Z]{2}$'
AND currency ~ '^[A-Z]{3}$'
AND amount > 0
AND expires_dttm > reg_dttm
```

```sql
payment_key IS NULL OR payment_provider IS NOT NULL
```

상태별 CHECK:

```sql
(
  status = 'PENDING'
  AND payment_key IS NULL
  AND approved_dttm IS NULL
  AND fail_reason IS NULL
)
OR
(
  status = 'PAID'
  AND payment_provider IS NOT NULL
  AND payment_key IS NOT NULL
  AND approved_dttm IS NOT NULL
  AND fail_reason IS NULL
)
OR
(
  status = 'FAILED'
  AND payment_provider IS NOT NULL
  AND approved_dttm IS NULL
  AND fail_reason IS NOT NULL
)
OR
(
  status = 'EXPIRED'
  AND payment_key IS NULL
  AND approved_dttm IS NULL
  AND fail_reason IS NULL
)
OR
(
  status = 'CANCELED'
  AND payment_key IS NULL
  AND approved_dttm IS NULL
  AND fail_reason IS NULL
)
OR
(
  status = 'REFUNDED'
  AND payment_provider IS NOT NULL
  AND payment_key IS NOT NULL
  AND approved_dttm IS NOT NULL
  AND fail_reason IS NULL
)
```

서비스 규칙:

- 주문 생성 시 서버가 활성 가격 행을 조회해 `amount`, `currency`, `country_code`, PASS면 `pass_duration_months`를 스냅샷으로 저장한다.
- 클라이언트가 금액/통화를 결정하지 않는다.
- 주문은 mutable 가격 행 FK를 저장하지 않는다.
- PENDING 주문은 가격 금액/통화가 나중에 바뀌어도 `expires_dttm`까지 기존 스냅샷 가격으로 결제 가능하다.
- 결제 승인 직전 국가/상품 판매 가능 상태를 다시 검증한다. 판매 중단이면 `PENDING → EXPIRED` 처리한다.
- 승인 전 토스 응답 금액과 `orders.amount`를 `BigDecimal.compareTo`로 대조하며 불일치 시 승인하지 않는다.
- `PAID` 전이는 `UPDATE orders SET status='PAID', ... WHERE order_seq=? AND status='PENDING' AND expires_dttm > now()` 형태의 조건부 UPDATE로 수행하고, 영향 행이 0이면 만료 또는 중복 승인으로 거절한다. 만료 판정의 정확성은 이 지연 판정이 책임진다.
- `expires_dttm`이 지난 PENDING 주문은 1시간 주기 배치(ShedLock)가 `EXPIRED`로 정리한다. 배치 주기는 목록 표시 지연에만 영향을 준다.
- 허용 상태 전이는 `PENDING → PAID / FAILED / EXPIRED / CANCELED`, `PAID → REFUNDED`다. `FAILED`, `EXPIRED`, `CANCELED`, `REFUNDED`는 종료 상태다. `CANCELED`는 결제 승인 전 주문 취소, `REFUNDED`는 승인 완료 후 전액 환불을 의미한다.
- 실패 후 재시도는 기존 주문을 되살리지 않고 새 주문 행을 생성한다.
- 모든 PASS 최초 구매/재구매 결제 시도는 각각 새 주문 행을 생성한다.
- `orders.status='PAID'` 전이와 대응 `asset_purchases` 생성 또는 `motif_passes` 생성/재활성은 같은 트랜잭션으로 처리한다.
- `payment_provider` 허용 코드 집합은 애플리케이션에서 검증하며 현재 `TOSS`만 허용한다. 별도 provider 마스터 테이블은 두지 않는다 *(현재 범위)*.
- PASS는 자동결제 없는 기간제 상품이므로 billing key/payment method 저장 테이블을 두지 않는다 *(회의 확정)*. `payment_key`는 재사용 결제수단이 아니라 해당 결제 트랜잭션 식별자다.

## 34. 에셋 구매 권한 (`asset_purchases`)

개별 에셋 보유권과 환불 이력을 보존한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `purchase_seq` | `BIGINT` | Y | PK, IDENTITY | 보유권 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 회원 |
| `asset_seq` | `INTEGER` | Y | FK → motif_assets | 에셋 |
| `order_seq` | `BIGINT` | Y | FK → orders, UNIQUE | 해당 구매 주문 |
| `status` | `VARCHAR(12)` | Y | 기본값 ACTIVE, CHECK | `ACTIVE`, `REFUNDED` |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 구매 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 보유권 상태 변경 일시 |

CHECK:

```sql
status IN ('ACTIVE','REFUNDED')
```

ACTIVE 부분 UNIQUE:

```sql
CREATE UNIQUE INDEX uq_asset_purchases_active
ON asset_purchases (user_seq, asset_seq)
WHERE status = 'ACTIVE';
```

서비스 규칙:

- ASSET 주문 `PAID` 전이와 `asset_purchases(status='ACTIVE')` 생성은 같은 트랜잭션이다.
- ACTIVE purchase는 연결 주문이 `PAID`, REFUNDED purchase는 해당 구매 주문이 `REFUNDED`이어야 한다.
- ASSET 환불은 `orders.status='REFUNDED'`와 해당 purchase `status='REFUNDED'`를 같은 트랜잭션에서 처리한다.
- 환불 후 재구매는 새 주문과 새 ACTIVE purchase 행을 생성한다.
- MOTIF 합성 권한 판정 시 개별 구매 사용자는 `asset_purchases.status='ACTIVE'`만 확인하며 `motif_assets.is_active`를 추가 조건으로 요구하지 않는다.
- 마이페이지 아티스트 카드의 `MOTIF n/m`은 `n = 해당 아티스트 에셋에 대한 사용자의 status='ACTIVE' 개별 구매 수`, `m = 해당 아티스트의 is_active=TRUE 에셋 수`다. **PASS 이용은 `n`에 포함하지 않는다**(PASS만 있으면 `0/m` — 패스 상태는 카드의 별도 뱃지가 표현) *(최종 확정 2026-09-02 R2 — v0.7 정의 복귀, v0.8 번복 철회. API 필드 `purchasedAssetCount`/`totalAssetCount`)*.
  - `SECRET` 은 NORMAL 과 동일 취급이므로 `n`·`m` 집계에 포함한다 *(R5 종결 — 구 제외 규칙 해제)*.
  - 구매한 에셋이 이후 판매 중단(`is_active=FALSE`)되어도 구매 권한이 유지되므로 `n > m`이 발생할 수 있다. 집계값은 그대로 제공하며 표시 처리(cap 여부)는 클라이언트 정책이다.

## 35. 모티프 PASS (`motif_passes`)

회원-아티스트당 1행을 유지하는 현재 이용권 상태다. 결제 이력은 `orders`에 누적한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `pass_seq` | `INTEGER` | Y | PK, IDENTITY | 패스 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 회원 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 아티스트 |
| `order_seq` | `BIGINT` | Y | FK → orders, UNIQUE | 현재 이용 기간을 만든 PAID 주문 |
| `duration_months` | `SMALLINT` | Y | CHECK (`> 0`) | 현재 이용권 기간 개월 수 |
| `started_dttm` | `TIMESTAMPTZ` | Y | - | 현재 이용 기간 시작 |
| `expires_dttm` | `TIMESTAMPTZ` | Y | CHECK | 현재 이용 기간 만료 |
| `status` | `VARCHAR(12)` | Y | 기본값 ACTIVE, CHECK | `ACTIVE`, `EXPIRED`, `REFUNDED` |
| `canceled_dttm` | `TIMESTAMPTZ` | N | 기본값 없음, CHECK | 사용자 취소 의사 기록 일시. 이용 판정에 불참(서비스 규칙 참조) |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 최초 PASS 생성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 상태 변경 일시 |

UNIQUE: `(user_seq, artist_seq)`, `order_seq`.

CHECK:

```sql
duration_months > 0
AND expires_dttm > started_dttm
AND status IN ('ACTIVE','EXPIRED','REFUNDED')
AND (canceled_dttm IS NULL OR canceled_dttm >= started_dttm)
```

서비스 규칙:

- 최초 PASS 결제가 `PAID`가 된 경우에만 처음 행을 생성한다. 최초 결제 FAILED/EXPIRED이면 PASS 행을 만들지 않는다.
- 실제 이용 가능 여부는 반드시 `status='ACTIVE' AND expires_dttm > now()`로 판단한다.
- ACTIVE 상태에서는 `duration_months`를 변경하거나 추가 기간을 구매하지 않는다.
- 만료 후 재구매 시 현재 판매 중인 다른 duration을 선택할 수 있다.
- 자동결제(자동갱신)는 제공하지 않는다. `expires_dttm <= now()`인 ACTIVE 행은 1시간 주기 배치(주문 만료 정리와 동일 잡)가 `status='EXPIRED'`로 전이한다. 실제 이용 판정은 `expires_dttm > now()`가 책임지므로 배치 지연은 권한에 영향을 주지 않는다.
- 취소(구독취소)는 사용자 의사 기록이다 *(회의 확정 — API 명세 §11.10)*: ACTIVE 패스에 `canceled_dttm = now()`를 기록하고 `mod_dttm`을 갱신한다. `status`와 이용 판정은 변하지 않으며 만료일까지 이용이 유지된다. 이미 `canceled_dttm`이 기록된 패스의 재취소 요청은 거부한다(409). 자동결제가 없어 해지할 갱신이 없으므로 현재는 기록 전용이며, 자동결제 재도입 시 이 컬럼이 갱신 차단 판정을 담당한다. `canceled_dttm`은 만료(`EXPIRED`) 전이·환불(`REFUNDED`)과 독립적으로 값을 유지한다. **취소 API 는 현재 구현 범위 제외** *(회의 확정 R5 — 기획·컬럼·본 규칙은 유지, 재도입 시 그대로 적용)*.
- 만료 후 재구매는 새 주문을 PAID 처리한 뒤 같은 행을 `started_dttm = now()`, `expires_dttm = now() + duration_months 개월`, `duration_months = 구매 기간`, `order_seq = 새 주문`, `status='ACTIVE'`, `canceled_dttm = NULL`로 재활성한다.
- PASS 환불은 관련 주문을 `REFUNDED`로 전이하고 PASS는 `status='REFUNDED'`로 즉시 권한을 종료한다. 환불 후 재구매는 같은 행을 재활성한다.
- 가격 금액/통화 변경, 가격 비활성화, 국가 비활성화, 아티스트 판매 중단은 이미 취득한 ACTIVE PASS의 현재 유효기간에 영향을 주지 않는다.
- PASS는 원칙적으로 `motif_assets.is_active=TRUE`인 대상에 이용한다. 다만 판매 중단 전에 이미 취득한 ACTIVE PASS는 해당 PASS의 기존 `expires_dttm`까지 판매 중단 시점의 기존 에셋 이용을 허용한다.
- PASS 상태와 `order_seq`가 가리키는 주문 상태의 관계는 교차 테이블 규칙이므로 CHECK로 강제하지 않는다. 일반적으로 ACTIVE는 현재 기간의 PAID 주문, REFUNDED는 환불된 REFUNDED 주문, EXPIRED는 만료된 기간의 마지막 PAID 주문을 가리킨다.

## 36. 모티프 결과물 보관함 (`motif_results`)

성공한 MOTIF 합성 결과만 생성한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `result_seq` | `BIGINT` | Y | PK, IDENTITY | 결과물 식별자 |
| `user_seq` | `INTEGER` | Y | FK → users | 소유 회원 |
| `asset_seq` | `INTEGER` | Y | FK → motif_assets | 사용 에셋 |
| `artist_seq` | `INTEGER` | Y | FK → artists | 에셋 아티스트 반정규화(INSERT 시 `motif_assets.artist_seq` 복사, 불변) |
| `job_seq` | `BIGINT` | Y | FK → composite_jobs, UNIQUE | 성공 MOTIF 잡 |
| `video_key` | `VARCHAR(255)` | Y | - | 결과 영상 키 |
| `thumbnail_key` | `VARCHAR(255)` | Y | - | 썸네일 |
| `duration_ms` | `INTEGER` | Y | CHECK (`> 0`) | 결과 영상 길이(ms) |
| `description` | `TEXT` | N | - | 결과물 설명 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 저장 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시(`description`·삭제 등 논리 변경 시) *(회의 확정 R9 — Q2 누락 보정)* |
| `is_deleted` | `BOOLEAN` | Y | 기본값 FALSE | 보관함 소프트 삭제 여부 |

서비스 규칙:

- `job_seq`는 `job_type='MOTIF' AND status='DONE'`이어야 한다.
- 잡의 user/asset과 결과 행의 user/asset이 일치해야 한다. `artist_seq`는 `motif_assets.artist_seq`와 일치해야 하며 에셋의 아티스트는 변경하지 않으므로 갱신하지 않는다.
- `artist_seq`는 아티스트 상세의 사용자 최근 결과물 조회(상단 자동재생 목록 — API §5.3 `myMotifVideos`)와 보관함 아티스트별 조회용이다. (마이페이지 카드는 결과물 수를 집계하지 않는다.)
- **자동 생성** *(회의 확정 R5/R6)*: 결과 행은 클라이언트 저장 API 가 아니라 **`composite.done` 이벤트를 수신한 코어 컨슈머**가 생성한다(워커는 DB 접근 없음 — 설계 원칙 ③). 순서: 워커 결과 업로드 → `composite.done` 발행 → 코어가 `motif_results(description=NULL, 대표 아님)` 생성 → 잡 `DONE` 확정 → FCM 발송. 결과 행 생성에 실패하면 DONE·FCM 으로 진행하지 않는다.
- 이벤트 재전송·잡 스윕 재큐에 대비해 생성은 `uq_motif_results_job` 기반 **`ON CONFLICT (job_seq) DO NOTHING`(멱등)** 으로 수행한다. `description` 은 생성 시 `NULL`, 이후 수정 API(PATCH)로 선택 입력한다.
- 실패/처리 중 잡에는 결과 행을 생성하지 않는다.
- 결과 소프트 삭제 시 `users.rep_motif_result_seq`가 해당 결과를 가리키고 있다면 같은 트랜잭션에서 NULL로 변경한다.
- `motif_results` 삭제는 `composite_jobs.result_video_key`, `result_thumbnail_key`를 수정하지 않는다. 잡 결과키는 처리 이력으로 유지한다.

---

# Composite Jobs

## 37. 합성 잡 (`composite_jobs`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `job_seq` | `BIGINT` | Y | PK, IDENTITY | 잡 식별자 |
| `job_uuid` | `UUID` | Y | UNIQUE | 공개 상태 조회 식별자 |
| `job_type` | `VARCHAR(10)` | Y | CHECK | `CHALLENGE`, `MOTIF` |
| `user_seq` | `INTEGER` | Y | FK → users | 요청 사용자 |
| `challenge_seq` | `INTEGER` | N | FK → challenges | CHALLENGE 잡 대상 |
| `asset_seq` | `INTEGER` | N | FK → motif_assets | MOTIF 잡 대상 |
| `challenge_participant_seq` | `INTEGER` | N | FK → challenge_participants | CHALLENGE 잡의 **교체 대상 슬롯** — 그 사람을 지운 편집본이 합성 베이스다. `NULL`이면 원본 영상 위 합성 *(v0.18 · changeSet 0019 — 구 `edited_challenge_seq`)* |
| `raw_video_key` | `VARCHAR(255)` | Y | - | 사용자 원본 영상 |
| `correction_enabled` | `BOOLEAN` | Y | 기본값 TRUE | 보정 사용 여부 |
| `status` | `VARCHAR(12)` | Y | 기본값 UPLOADED, CHECK | `UPLOADED`, `QUEUED`, `PROCESSING`, `DONE`, `FAILED` |
| `fail_code` | `VARCHAR(30)` | N | - | 실패 코드 |
| `result_video_key` | `VARCHAR(255)` | N | - | 결과 영상 키 |
| `result_thumbnail_key` | `VARCHAR(255)` | N | - | 결과 썸네일 키 |
| `attempt` | `SMALLINT` | Y | 기본값 1, CHECK (`>= 1`) | 시도 횟수 |
| `processing_ms` | `INTEGER` | N | CHECK (`>= 0`) | 처리 소요 시간 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 생성 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 마지막 상태 전이 일시 |

타입 CHECK:

```sql
(
  job_type = 'CHALLENGE'
  AND challenge_seq IS NOT NULL
  AND asset_seq IS NULL
)
OR
(
  job_type = 'MOTIF'
  AND asset_seq IS NOT NULL
  AND challenge_seq IS NULL
  AND challenge_participant_seq IS NULL
)
```

상태별 CHECK:

```sql
(
  status = 'DONE'
  AND result_video_key IS NOT NULL
  AND result_thumbnail_key IS NOT NULL
  AND fail_code IS NULL
)
OR
(
  status = 'FAILED'
  AND fail_code IS NOT NULL
  AND result_video_key IS NULL
  AND result_thumbnail_key IS NULL
)
OR
(
  status IN ('UPLOADED','QUEUED','PROCESSING')
  AND fail_code IS NULL
  AND result_video_key IS NULL
  AND result_thumbnail_key IS NULL
)
```

기타 CHECK:

```sql
attempt >= 1
AND (processing_ms IS NULL OR processing_ms >= 0)
```

처리 규칙:

1. presigned URL 요청 시 UPLOADED 잡을 생성한다.
2. 업로드 완료 후 합성 요청 시 QUEUED로 전이한다.
3. GPU 워커가 PROCESSING으로 전이한다.
4. 성공 시 DONE, 실패 시 FAILED로 전이한다.
5. 결과 도메인 행은 DONE 잡에서만 생성한다.
6. `UPLOADED` 상태에서 운영 기준 시간을 초과하도록 합성 요청이 시작되지 않은 잡은 임시 업로드 데이터로 간주하여 원본 업로드와 함께 주기적으로 하드 삭제할 수 있다. 구체 보존 시간은 DB 제약으로 고정하지 않는다.

CHALLENGE 잡:

- 결과 최종화 직전 `challenges.is_deleted=FALSE`를 다시 확인한다.
- 챌린지가 삭제된 경우 참여 영상은 생성하지 않고 잡을 `FAILED` 처리한다. 이때 최종 FAILED CHECK를 만족하도록 결과키를 저장하지 않는다.
- 잡 생성 시 `challenge_participant_seq`가 지정되면 그 행의 `challenge_seq`가 잡의 `challenge_seq`와 일치하는지 검증한다(반정규화 정합 — 서비스 불변식).
- 합성 베이스 키는 슬롯의 `edited_challenge_key`(`challenge_participant_seq`가 `NULL`이면 원본 `challenges.video_key`)이며, 큐 메시지는 최초 발행과 스윕 재큐 모두 잡 행에서 재구성한다.
- **편집본이 없는 슬롯은 잡에 실리지 않는다** *(v0.18)*. `edited_challenge_key IS NULL` 인 슬롯을 고르면 §13.1 이 `EDITED_CHALLENGE_NOT_FOUND` 로 막으므로, 이 칼럼이 가리키는 행에는 언제나 베이스가 있다.
- `NULL` 허용(무교체 원본 합성)은 기본안이다. 편집본 필수로 확정되면 `job_type='CHALLENGE'` 분기에 `challenge_participant_seq IS NOT NULL`을 추가한다 *(기본안 — 확인 필요)*.

MOTIF 잡:

- job 생성 시점에 개별 구매 또는 PASS 등 이용 권한을 검증한다.
- 생성 이후 에셋 비활성화, 개별 구매 환불, PASS 만료/환불 등 권한 상태가 변해도 진행 중 잡은 취소하지 않는다.
- 성공하면 정상적으로 DONE 및 `motif_results`를 생성할 수 있다.

스윕/재시도:

- 기본 스윕 대상은 `QUEUED`, `PROCESSING` 중 `COALESCE(mod_dttm, reg_dttm)` 기준 15분 초과 정체 행이다.
- `attempt < 3`이면 재큐, 최대 시도 도달 시 FAILED 확정한다.
- **사용자 재시도(API 13.4)** *(회의 확정 R7 — 동일 잡 재큐 모델)*: 본인 `FAILED` 잡을 같은 행으로 재큐한다 — `status='QUEUED'`, `fail_code=NULL` 초기화(상태별 CHECK 를 그대로 만족), `attempt = attempt + 1`, `mod_dttm=now()` 후 잡 행에서 큐 메시지를 재구성해 발행한다. 결과 키는 `results/{type}/{job_uuid}.mp4` 멱등 규칙이라 재합성이 같은 키를 덮어쓴다. 직전 실패 사유는 행에서 소거되므로 이력이 필요하면 로그/이벤트로 남긴다. 수동 재시도의 시도 상한은 두지 않는다 *(기본안 — 확인 필요)*. 자동 재큐 상한(`attempt < 3`)은 스윕 루프 방지용으로 별개다.
- 실패 큐는 DLX/DLQ를 사용한다.

---

## 40. 숍 공용 배너 (`shop_banners`) *(회의 확정 R7 신설 — `motif_core`)*

모티프 숍 상단의 공용 배너다. 아티스트별 `artists.motif_bg_image_key` 와 별개이며 어드민이 관리한다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `shop_banner_seq` | `INTEGER` | Y | PK, IDENTITY | 배너 식별자 |
| `image_key` | `VARCHAR(255)` | Y | - | 배너 이미지 S3 키(`banners/` 프리픽스). 공개 콘텐츠 — CloudFront 일반 URL 로 노출 |
| `display_order` | `SMALLINT` | Y | 기본값 0, CHECK (`>= 0`), UNIQUE | 표시 순서 |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 노출 여부 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

UNIQUE: `uq_shop_banners_order (display_order)` — 순서 중복 방지 *(기본안 — 재정렬 시 스왑은 어드민 트랜잭션 처리)*.

서비스 규칙:

- 노출은 `is_active=TRUE` 를 `display_order ASC` 로 전량 반환한다(API §11.11 — 비페이지).
- 클릭 액션(링크) 컬럼은 도입하지 않는다 — 필요 시 `link_type`/`link_target` 확장 *(미결)*.
- 등록·수정·비활성은 어드민 대행. 이미지 업로드는 어드민 presigned 흐름을 따른다.

---

# Notifications

`motif_notif`의 core seq/UUID는 교차 스키마 FK를 걸지 않는 논리 참조다.

## 38. 디바이스 토큰 (`device_tokens`)

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `token_seq` | `INTEGER` | Y | PK, IDENTITY | 식별자 |
| `user_seq` | `INTEGER` | Y | 논리 참조 | 현재 토큰 소유 회원 |
| `fcm_token` | `VARCHAR(255)` | Y | UNIQUE | FCM 토큰 |
| `platform` | `VARCHAR(10)` | Y | 기본값 ANDROID, CHECK | `ANDROID`, `IOS`, `WEB` |
| `is_active` | `BOOLEAN` | Y | 기본값 TRUE | 수신 가능 여부 |
| `last_used_dttm` | `TIMESTAMPTZ` | N | - | 마지막 성공 발송 일시 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 최초 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 갱신 일시 |

CHECK:

```sql
platform IN ('ANDROID','IOS','WEB')
```

서비스 규칙:

- 동일 `fcm_token`이 다른 사용자로 로그인되면 기존 행을 실패시키지 않고 `user_seq`를 현재 로그인 사용자로 재할당하고 `is_active=TRUE`로 갱신한다.
- 로그아웃 시 현재 기기 토큰을 `is_active=FALSE`로 변경한다.
- `account_status='SUSPENDED'` 전이 또는 `users.is_deleted=TRUE` 처리 시 해당 사용자의 모든 device token을 `is_active=FALSE`로 변경한다. 정지 해제 후에는 재로그인/토큰 재등록을 통해 다시 활성화한다.
- FCM이 invalid/unregistered 응답을 반환하면 해당 토큰을 `is_active=FALSE`로 변경한다.

## 39. 알림 발송 규칙 (푸시 전용 — 저장 테이블 없음) *(회의 확정 2026-09-03 R3 — `notifications` 테이블 삭제)*

인앱 알림 목록·읽음 처리·미읽음 배지(레드닷)는 범위에서 제외한다. 모든 알림(댓글·답글, 합성 완료/실패, 신규 챌린지, 공지)은 **FCM 푸시 전용**이며 서버에 행을 저장하지 않는다. `motif_notif` 스키마에는 `device_tokens`만 남는다.

- 푸시 페이로드는 data-only(API 명세 §14.3)로, 이동 대상 식별자(`challengeSeq`/`videoSeq`/`noticeSeq`/`jobUuid`)를 포함한다 — 클릭 시 딥링크로 대상 화면 직행. 합성 결과는 진입 시 잡 단건 조회 1회.
- 삭제된 게시물의 푸시를 클릭하면 대상 API가 삭제 상태(404)를 반환한다.
- 자기 자신의 행동으로 자기 자신에게 알림을 발송하지 않는다. 좋아요 알림은 발송하지 않는다.
- 클라이언트(Android)는 수신한 FCM 을 로컬 DB(Room 등)에 저장해 앱 내 알림 목록·읽음·삭제를 구성할 수 있다 — 서버는 관여하지 않는다 *(회의 확정 R5)*.
- 댓글/답글 푸시는 댓글 자체가 아니라 대상 게시물 상세로 이동한다.

### 구독자 팬아웃

- 공지/챌린지 생성 이벤트는 `artist_seq`와 이동 대상(`challenge_seq` 또는 `notice_seq`), 표시용 아티스트 nickname을 포함한다.
- 알림 서비스는 읽기 전용으로 `motif_core.subscriptions WHERE artist_seq=?`를 조회해 수신 `user_seq`를 얻는다.
- `device_tokens.is_active=TRUE` 토큰에만 푸시를 발송한다.
- subscriber replica는 두지 않는다.

## 41. 아티스트 별칭 (`artist_aliases`) *(v0.17 신설 — changeSet 0018)*

아티스트를 **찾기 위한** 언어별 별칭이다. 다국어 검색(API §5.2)이 이 칼럼만 본다.

**보여주기용이 아니다.** 응답의 `name` 은 언제나 `users.nickname` 이다 — 별칭을 표시에 쓰면 같은 아티스트가 질의에 따라 다른 이름으로 보이고, 공통 아티스트 요약을 쓰는 15개 엔드포인트가 전부 흔들린다(API 상세 명세 README §3.5).

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `alias_seq` | `INTEGER` | Y | PK, IDENTITY | 별칭 식별자 |
| `artist_seq` | `INTEGER` | Y | FK → artists, INDEX | 대상 아티스트 |
| `language_code` | `CHAR(2)` | Y | - | ISO 639-1 (`ko`·`en`·`ja`). **검색에 쓰지 않는다** — 어드민이 언어별로 정리해 넣기 위한 구분일 뿐 |
| `alias` | `VARCHAR(100)` | Y | GIN (`gin_trgm_ops`) | 별칭. `ILIKE '%q%'` 대상 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

서비스 규칙:

- **UNIQUE 제약이 없다.** 한 언어에 별칭 여러 개를 허용한다(`aespa`·`æspa`, 활동명·본명). 같은 값이 두 번 들어가도 막지 않는다 — §5.2 가 `artist_seq` 로 중복을 제거하므로 결과가 겹치지 않는다.
- **소프트 삭제 칼럼이 없다.** 아티스트를 소프트 삭제해도 이 행을 건드리지 않고, 조회가 `artists.is_deleted = FALSE` 조인으로 거른다. 복구하면 별칭이 그대로 살아 돌아온다.
- 어드민이 직접 지우는 것(어드민 명세 §3.5.6~9)은 **하드 삭제**다. 위의 소프트 삭제 연쇄와 별개 동작이다.
- `mod_usr_seq` 를 두지 않는다 — `artists` 와 같은 이유로 수정 주체는 감사 로그가 갖는다.

## 42. 그룹 별칭 (`group_aliases`) *(v0.17 신설 — changeSet 0018)*

그룹의 언어별 별칭. 구조는 §41 과 같고 `artist_seq` 자리가 `group_seq` 다.

| 칼럼 | 타입 | 필수 | 키·제약 | 설명 |
|---|---|---:|---|---|
| `alias_seq` | `INTEGER` | Y | PK, IDENTITY | 별칭 식별자 |
| `group_seq` | `INTEGER` | Y | FK → artist_groups, INDEX | 대상 그룹 |
| `language_code` | `CHAR(2)` | Y | - | ISO 639-1 |
| `alias` | `VARCHAR(100)` | Y | GIN (`gin_trgm_ops`) | 별칭 |
| `reg_usr_seq` | `INTEGER` | Y | FK → users | 등록 행위자 |
| `reg_dttm` | `TIMESTAMPTZ` | Y | 기본값 CURRENT_TIMESTAMP | 등록 일시 |
| `mod_dttm` | `TIMESTAMPTZ` | N | 기본값 없음 | 수정 일시 |

**§41 과 한 테이블로 합치지 않는다.** 세 가지 이유다:

1. `artist_seq` FK 하나로는 그룹을 가리킬 수 없다. 폴리모픽(`target_type` + `target_seq`)으로 만들면 FK 를 못 걸고, 이 스키마는 모든 참조에 FK 를 건다.
2. 그룹 별칭을 멤버별로 복제해 넣으면 별칭 한 번 고치는 데 N 행 UPDATE 이고, 멤버가 추가될 때 누락된다.
3. **무엇보다, "이 매치가 그룹 매치였다" 는 사실이 필요하다.** §5.2 는 그룹 별칭에 걸리면 그 그룹의 멤버 전원을 결과에 넣고, 그렇게 들어온 멤버를 본인 매치보다 뒤로 정렬한다. 테이블이 갈려 있으면 그 판정이 조회 구조에서 공짜로 나온다.

서비스 규칙은 §41 과 같다 — UNIQUE 없음, 소프트 삭제 연쇄 없음(조인에서 `artist_groups.is_deleted = FALSE`), 어드민 삭제는 하드.

---

# 공통 삭제·카운터 정책

## 소프트 삭제 적용 대상

- `users`
- `companies`
- `artist_groups`
- `artists`
- `challenges`
- `participation_videos`
- `challenge_comments`
- `video_comments`
- `notice_comments`
- `notices`
- `motif_results`

## 하드 삭제 또는 별도 상태 사용

- `subscriptions`, 일반 좋아요, 댓글 좋아요: 관계 해제 시 하드 삭제
- 댓글 삭제 시 해당 댓글 좋아요는 하드 삭제
- `video_hides`: 행 존재 자체가 상태, 해제 시 하드 삭제 가능
- `challenge_participants`, `notice_images`: 교체 시 하드 삭제/재생성. 슬롯은 `composite_jobs` 참조 전에만 하드 삭제 가능(FK RESTRICT), 참조 후에는 유지 — 편집본 키 교체는 같은 행 키 갱신이라 참조 중에도 된다 *(v0.18 — 구 `edited_challenges` 규칙 흡수)*
- `refresh_tokens`: 활성 수명 중 `revoked_dttm`으로 폐기하고 오래된 과거 행은 운영 보존정책에 따라 주기적 하드 삭제
- `artist_aliases`, `group_aliases`: 어드민이 지울 때 하드 삭제. **대상 아티스트·그룹의 소프트 삭제는 따라가지 않는다** — 조회가 조인에서 거른다(§41·§42)
- `device_tokens`: 로그아웃/무효화 시 `is_active=FALSE`
- `user_accounts`: `account_status` 사용
- `motif_assets`, 가격 테이블: `is_active` 사용
- `asset_purchases`: `ACTIVE/REFUNDED` 상태 사용
- `orders`: 상태 이력 보존, 삭제하지 않음
- `motif_passes`: 상태 전이, 삭제하지 않음
- `composite_jobs`, `reports`: 운영 이력, 삭제하지 않음

## 부모 콘텐츠 소프트 삭제

- `users.is_deleted=TRUE`인 사용자가 소유한 콘텐츠는 일반 API에서 노출하지 않는다. 사용자 삭제만으로 콘텐츠 행을 일괄 소프트 삭제하지 않으며 조회 시 소유 사용자 삭제 상태를 함께 검증한다.
- 부모 챌린지/참여 영상/공지 삭제만으로 자식 댓글/좋아요/이미지를 대량 연쇄 삭제하지 않는다.
- 삭제된 부모를 기준으로 일반 API 접근을 차단한다.
- 예외적으로 댓글 루트 삭제는 직접 답글 집합이 명확하므로 답글까지 소프트 삭제하고 댓글 좋아요를 정리한다.

## 거울 카운터

- `like_count`, `comment_count`, `participant_count`, `subscriber_count`는 관계/활성 행 생성·삭제와 같은 트랜잭션에서 원자 증감한다.
- 재계산 기준이 항상 존재해야 한다.
- `participant_count`: `participation_videos WHERE challenge_seq=? AND is_deleted=FALSE` 수. visibility 무관.
- 댓글 `comment_count`: 각 댓글 테이블 `WHERE target=? AND is_deleted=FALSE` 수.
- `has_artist_like`: `video_likes JOIN users WHERE role='ARTIST'` 활성 관계 존재 여부.
- `has_artist_comment`: `video_comments JOIN users WHERE role='ARTIST' AND video_comments.is_deleted=FALSE` 존재 여부.
- 아티스트 계정이 이후 소프트 삭제되었다는 이유만으로 과거 관계를 일괄 제거/재작성하지 않는다.
- `view_count`는 Redis 버퍼를 통해 원자 가산하며 1회 조회의 제품 기준은 별도 API/서비스 명세에서 정의한다.

---

# 키·인덱스 목록

## 복합 PK

- `subscriptions (user_seq, artist_seq)`
- `video_likes (video_seq, user_seq)`
- `video_hides (user_seq, video_seq)`
- `challenge_likes (challenge_seq, user_seq)`
- `notice_likes (notice_seq, user_seq)`
- `challenge_comment_likes (challenge_comment_seq, user_seq)`
- `video_comment_likes (video_comment_seq, user_seq)`
- `notice_comment_likes (notice_comment_seq, user_seq)`

## UNIQUE / 부분 UNIQUE

- `uq_user_accounts_user` — `user_accounts(user_seq)`
- `uq_oauth_providers_code` — `oauth_providers(provider_code)`
- `uq_user_oauth_account_provider` — `(account_seq, provider_seq)`
- `uq_user_oauth_subject` — `(provider_seq, oauth_subject)`
- `uq_admins_login_id` — `admins(login_id)`
- `uq_refresh_tokens_hash` — `refresh_tokens(token_hash)`
- `uq_companies_name_active` — `(company_name) WHERE is_deleted=FALSE`
- `uq_artists_user` — `artists(user_seq)`
- `uq_challenge_participants_artist` — `challenge_participants(challenge_seq, artist_seq)` *(v0.18 — 구 복합 PK)*
- `uq_challenge_participants_slot_order` — `challenge_participants(challenge_seq, slot_order)` *(v0.18 — 구 `uq_challenge_participants_mask`)*
- `uq_participation_videos_job` — `participation_videos(job_seq)`
- 신고 4종 — reporter + 실제 target 조합 UNIQUE
- `uq_notice_images_order` — `(notice_seq, display_order)`
- `uq_countries_code` — `countries(country_code)`
- `uq_motif_asset_prices_asset_country` — `(asset_seq, country_seq)`
- `uq_motif_pass_prices_artist_country_duration` — `(artist_seq, country_seq, duration_months)`
- `uq_orders_order_uuid` — `orders(order_uuid)`
- `uq_orders_payment_provider_key` — `(payment_provider, payment_key)`
- `uq_asset_purchases_order` — `asset_purchases(order_seq)`
- `uq_asset_purchases_active` — `(user_seq, asset_seq) WHERE status='ACTIVE'`
- `uq_motif_passes_user_artist` — `(user_seq, artist_seq)`
- `uq_motif_passes_order` — `motif_passes(order_seq)`
- `uq_motif_results_job` — `motif_results(job_seq)`
- `uq_composite_jobs_job_uuid` — `composite_jobs(job_uuid)`
- `uq_device_tokens_fcm_token` — `device_tokens(fcm_token)`

## 일반 인덱스

- `idx_subscriptions_artist` — `subscriptions(artist_seq)`
- `idx_refresh_tokens_user` — `refresh_tokens(user_seq, expires_dttm DESC)`
- `idx_artists_group` — `artists(group_seq, is_deleted)`
- `idx_artists_company` — `artists(company_seq, is_deleted)`
- `idx_artist_aliases_artist` — `artist_aliases(artist_seq)` *(v0.17)* — 어드민 §3.5.6 별칭 목록과 §5.2 의 `artists` 조인이 탄다
- `idx_group_aliases_group` — `group_aliases(group_seq)` *(v0.17)* — 어드민 §3.4.5 용. 구조는 위와 같다
- `idx_challenges_feed` — `challenges(is_deleted, reg_dttm DESC)`
- `idx_challenges_song` — `challenges(song_seq)`
- `idx_pv_challenge_feed` — `participation_videos(challenge_seq, visibility, is_deleted, like_count DESC)`
- `idx_pv_artist_feed` — `participation_videos(artist_seq, visibility, is_deleted, reg_dttm DESC)`
- `idx_pv_user` — `participation_videos(user_seq, is_deleted, reg_dttm DESC)`
- `idx_pv_user_artist` — `participation_videos(user_seq, artist_seq, is_deleted)` — 마이페이지 아티스트 카드 참여 수
- `idx_video_likes_user_dttm` — `video_likes(user_seq, reg_dttm DESC)`
- 댓글 3종 target 인덱스 — `(target_seq, is_deleted, reg_dttm DESC)`
- 댓글 3종 parent 인덱스 — `(parent_comment_seq)`
- 댓글 3종 user 인덱스 — `(user_seq, reg_dttm DESC)`
- `idx_reports_status` — `reports(status, reg_dttm)`
- `idx_notices_artist` — `notices(artist_seq, is_deleted, reg_dttm DESC)`
- `idx_motif_assets_artist` — `motif_assets(artist_seq, is_active, reg_dttm DESC)`
- `idx_orders_user` — `orders(user_seq, reg_dttm DESC)`
- `idx_motif_passes_user` — `motif_passes(user_seq, status, expires_dttm DESC)`
- `idx_motif_results_user` — `motif_results(user_seq, is_deleted, reg_dttm DESC)`
- `idx_motif_results_asset` — `motif_results(asset_seq, is_deleted, reg_dttm DESC)`
- `idx_motif_results_user_artist` — `motif_results(user_seq, artist_seq, is_deleted, reg_dttm DESC)` — 사용자×아티스트 결과물 수·최근 결과물
- `idx_composite_jobs_user` — `composite_jobs(user_seq, reg_dttm DESC)`
- `idx_device_tokens_user_active` — `device_tokens(user_seq, is_active)`

## 부분/표현식 인덱스

```sql
CREATE INDEX ix_composite_jobs_stuck
ON composite_jobs (status, COALESCE(mod_dttm, reg_dttm))
WHERE status IN ('QUEUED','PROCESSING');
```

```sql
CREATE INDEX ix_refresh_tokens_active
ON refresh_tokens (user_seq, expires_dttm)
WHERE revoked_dttm IS NULL;
```

```sql
CREATE INDEX ix_orders_pending_expiry
ON orders (expires_dttm)
WHERE status = 'PENDING';
```

```sql
CREATE INDEX ix_motif_passes_active_expiry
ON motif_passes (expires_dttm)
WHERE status = 'ACTIVE';
```

두 부분 인덱스는 1시간 주기 ShedLock 배치(주문 만료 정리 + PASS 만료 전이) 전용이다.

## pg_trgm GIN

- `gin_users_nickname` — `users USING gin (nickname gin_trgm_ops) WHERE role='ARTIST' AND is_deleted=FALSE` — 검색은 아티스트만 대상이므로 부분 GIN. **어드민 §3.5.1 목록 검색용**이다
- `gin_groups_name` — `artist_groups USING gin (group_name gin_trgm_ops)` — 어드민 §3.4.1 용
- `gin_artist_aliases_alias` — `artist_aliases USING gin (alias gin_trgm_ops)` *(v0.17)* — 팬 앱 §5.2 용
- `gin_group_aliases_alias` — `group_aliases USING gin (alias gin_trgm_ops)` *(v0.17)*
- 곡 검색용 GIN 인덱스는 두지 않는다.

**인덱스는 `lower(alias)` 가 아니라 원본 칼럼에 건다.** `gin_trgm_ops` 는 트라이그램을 뽑을 때 스스로 소문자로 내리므로 인덱스 자체가 이미 대소문자 무시이고, `ILIKE` 를 그대로 받는다. 반대로 조회를 `lower(alias) LIKE lower(?)` 로 쓰면 **인덱스에 붙지 않는다** — 인덱스가 걸린 식(`alias`)과 조회하는 식(`lower(alias)`)이 다르기 때문이다. 그래서 §5.2 조회는 JPQL 이 아니라 네이티브 `ILIKE` 로 쓴다.

**이 인덱스가 성능을 좌우하지는 않는다.** pg_trgm 은 3글자 조각 기반이라 한글 2음절 질의(`카리`)는 트라이그램이 하나도 안 나와 인덱스를 못 탄다. 다만 아티스트가 수백·별칭이 천 단위라 플래너가 어차피 순차 스캔을 고르는 크기이고, 데이터가 커졌을 때 코드 변경 없이 인덱스가 쓰이기 시작하도록 미리 달아 둔다. pg_bigm 을 검토했으나 채택하지 않았다 — 근거는 [ADR-0014](../adr/0014-artist-alias-search.md).

## FK 삭제 정책

- 기본 FK 삭제 정책은 `ON DELETE RESTRICT`다.
- 소프트 삭제 엔티티가 많으므로 DB CASCADE에 의존하지 않는다.
- 관계 해제/교체는 애플리케이션이 명시적으로 DELETE한다.
- 현재 회원 탈퇴 자체는 프로젝트 범위 밖이지만, 소프트 삭제 계정의 `subscriptions` 관계는 정리 시 하드 삭제한다.

---

# CHECK 작성 기준

- 값 집합은 `IN (...)`으로 제한한다.
- 한 행 내부에서만 판단 가능한 상태 조합은 적극적으로 CHECK로 강제한다.
- 3개 이상 nullable 타깃 중 정확히 하나를 선택하는 경우 PostgreSQL boolean→int 합산식을 사용한다.
- 다른 테이블 값을 조회해야 하는 규칙은 CHECK가 아니라 서비스 트랜잭션 불변식으로 명시한다.
- `currency`는 `CHAR(3)` 형식 CHECK를 두고 실제 ISO 4217 유효 코드는 애플리케이션에서 검증한다.
- `country_code`는 `CHAR(2)` 형식 CHECK를 두고 실제 ISO 3166 코드 유효성은 애플리케이션에서 검증한다.
- 같은 컬럼의 `NOT NULL`, 상태 CHECK, 부분 UNIQUE, 인덱스가 서로 충돌하지 않도록 Liquibase 작성 시 본 문서의 최종 상태를 기준으로 한다.

---

# 구현 단계에서 별도 확정할 사항

아래 항목은 DB 구조 오류가 아니라 제품/API/운영 정책이므로 본 스키마 확정과 분리한다.

1. 조회수 1회 인정 기준
2. `correction_enabled` 사용자 노출 방식 및 보정 파라미터
3. AI 객체 분리 대상 범위
4. `reports.reason_code`, `composite_jobs.fail_code` 코드표
5. 사용자 차단 기능 도입 시점
6. ~~SECRET 판정 규칙~~ — **종결(R5/R6)**: 별도 판정 없음, NORMAL 과 동일 취급(PASS 커버 포함·카드 집계 포함)
7. 향후 회원 탈퇴 구현 시 `user_accounts.account_status`에 `WITHDRAWN`을 추가할지 여부
8. CHALLENGE 잡의 `challenge_participant_seq` `NULL` 허용(무교체 원본 합성 모드) 유지 여부 — **팀 논의 예정(R8)**. 편집본 필수로 확정되면 CHECK 강화 + 앱 참여 흐름에서 "원본 그대로 참여" 제거
9. 앵커·슬롯 순서 산출 주체와 등록 흐름(AI 파이프라인 산출 vs 어드민 수기 입력) — 어드민 명세서 반영 필요
10. ~~편집본 미존재 조합 처리~~ — **재종결(v0.18)**: 조합 자체가 없어졌다. 편집본이 비어 있는 슬롯은 정상 상태이고, §7.2 `selectable` 이 화면에서 걸러 §13.1 의 404 가 예외가 아닌 정상 방어선이 된다. 구 종결(R8 "전 조합 등록 전제")은 폐기
11. ~~participant_mask 0 의미~~ — **종결(v0.18)**: 컬럼이 `slot_order` 로 대체돼 질문이 사라졌다. 순서는 `>= 0` 이고 0 은 맨 왼쪽 슬롯이라 예약값이 아니다. 무교체 참여는 종전대로 `challenge_participant_seq NULL` 경로
12. ~~recent_search_terms 존치~~ — **종결(R7)**: 컬럼·CHECK·백업 플러시 규칙 삭제(신규 changeSet `DROP COLUMN`)
13. ~~모티프 에셋 표시명~~ — **종결(R6)**: `motif_assets.title` 컬럼 신설

종결: ~~알림 설정 UI/필터 도입 여부~~ — v0.8에서 클라이언트 로컬 저장으로 확정(설정 테이블 없음, FCM data-only 발송 규약은 API 명세 §14.8 참조).
