# ADR-0005. 이벤트 규약 — 발행·소비·직렬화·추적

- 상태: 승인
- 일자: 2026-09-02 (MODERA 실측 반영: 동일 일자)
- 관련: 아키텍처 설계서 §5, DB 명세 v0.5(composite_jobs), ai/ 파트 JSON 계약,
  MODERA 분석보고서 §4

## 컨텍스트

DB 커밋과 큐 발행은 원자적으로 묶을 수 없고, 브로커는 at-least-once라
중복 전달이 정상이며, 소비자는 Java와 Python이 섞여 있다.

MODERA 실측이 규약의 필요를 증명한다: **발행-트랜잭션 관계가 발행처
3곳에서 제각각이었고**(커밋 전 발행 1곳, 커밋 후+보상 1곳, 무트랜잭션
1곳 — 규칙 문서에 해당 항목 부재), requestId는 HTTP 경계까지만 전파되어
worker 로그에서 추적이 끊겼다(worker 설정 주석이 자인). 아래 규약은
계승이 아니라 **그 공백의 교정**이다.

## 결정

### 발행 — "DB 먼저, 발행은 커밋 후"

- 트랜잭션 **안에서** RabbitTemplate을 호출하지 않는다. 서비스는
  `ApplicationEventPublisher`로 내부 이벤트만 올리고, 발행기가
  `@TransactionalEventListener(AFTER_COMMIT)`에서 MQ로 내보낸다.
- **발행 유실 보증은 아웃박스의 염가판으로 확보한다**: `composite_jobs`
  상태 행이 아웃박스, 잡 스윕 배치(15분 초과 QUEUED 재큐, attempt<3)가
  릴레이다. MODERA도 FCM 도메인에서는 DB 폴링 outbox로 같은 문제를
  풀었던 전례가 있다 — 별도 아웃박스 테이블은 두지 않는다.

### 소비 — 멱등이 기본값

- 컨슈머는 재전달을 전제한다. `job_uuid`(또는 event_id) + 상태 전이
  가드로 방어한다. MODERA의 2방식(Redis SET dedup / DB 유니크 제약 +
  경합의 정상 스킵 처리)이 그대로 선택지다.
- `default-requeue-rejected: false`. 재시도는 리스너 인터셉터 3회
  (지수 백오프), 소진 시 DLX → `dlq.*` 격리. MODERA에서 손수 구현했던
  회수 배치(PelReclaimScanner)와 그 미구현 비대칭이 브로커 기능으로
  대체되는 지점이다.
- DLQ 재발행 판단은 잡 스윕 배치와 어드민의 몫이다.
- 단, **도메인 타임아웃 배치는 메시징이 대체하지 못한다** — 콜백이
  영영 오지 않는 PROCESSING 잡의 FAILED 처리(MODERA StuckJobScanner
  상당)는 MOTIF 잡 스윕에 그대로 필요하다.

### 직렬화 — 언어 중립 JSON

- Jackson 2 전용 ObjectMapper 빈 + `Jackson2JsonMessageConverter`.
  Java 타입 헤더(`__TypeId__`)에 의존하지 않고 리스너마다 페이로드
  클래스를 명시한다. (Boot 4의 REST 기본은 Jackson 3 — 세대 경계는
  ADR-0004 참조.)
- 필드는 **snake_case** — Python 워커·DB 컬럼 관례와 통일. **MODERA는
  camelCase였으므로 이것은 계승이 아니라 의도적 변경이다.** 네이밍은
  record 애너테이션이 아니라 컨버터 ObjectMapper의 naming strategy로
  일괄 적용한다(계약 record는 애너테이션 무결).
- 수신측 ObjectMapper는 `FAIL_ON_UNKNOWN_PROPERTIES=false` — 상대가
  필드를 추가해도 깨지지 않는 전방 호환(MODERA 관례 계승). 발신측
  규칙은 **additive-only**: 필드 추가만 허용, 제거·개명 금지.
- 공통 봉투: `event_id`(uuid), `occurred_at`(ISO-8601 UTC), 본문.
  module-event의 record는 **의존성 0**으로 유지한다 — MODERA의
  event-contract가 jackson을 물었던 것은 스트림 필드맵 직렬화 로직이
  계약 안에 있었기 때문이며, AMQP에서는 컨버터가 전담하므로 불필요.

### 추적 — requestId를 비동기 경계 너머로

- HTTP 진입 시 `X-Request-Id` 승계 또는 생성 → MDC (필터,
  HIGHEST_PRECEDENCE — MODERA RequestIdFilter 관례 계승).
- MQ 발행 시 같은 값을 메시지 헤더 `x-request-id`로 싣는다 — MODERA에서
  끊겼던 반쪽의 완성.
- 컨슈머(Java·Python)는 헤더에서 꺼내 로그 컨텍스트를 복원한다.
- logback 패턴에 `%X{requestId:-}` 고정. 잡 하나를 API → 큐 → 워커 →
  알림까지 단일 ID로 grep한다.

## 검토한 대안

- **정식 아웃박스 테이블 + 릴레이** — 보증 동일, 구성물 추가. composite_jobs가 이미 그 역할.
- **브로커 재큐(requeue=true) 재시도** — 포이즌 메시지가 큐 머리에서 무한 회전. 기각.
- **camelCase JSON** — Python 측 변환 계층 발생. 기각(MODERA에서 이미 경험).

## 결과

- module-event README에 요약을 두고, 신규 이벤트는 이 ADR 기준으로 리뷰.
- GPU 워커(Python) 계약서에 `x-request-id` 전파와 snake_case를 명시해
  AI 파트에 전달.
