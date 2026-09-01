# module-event — 이벤트 계약

발행·소비·직렬화·추적 규약의 전문은 [ADR-0005](../docs/adr/0005-event-conventions.md).
이 모듈은 **의존성 0**이다 — 상수(topology)와 record(contract)만 담고,
직렬화는 각 앱의 MQ 컨버터가 전담한다. 신규 이벤트는 ADR-0005 기준으로 리뷰한다.

## 규약 요약 (ADR-0005)

- **발행 — "DB 먼저, 발행은 커밋 후."** 트랜잭션 안에서 RabbitTemplate을 부르지
  않는다. 서비스는 `ApplicationEventPublisher`로 내부 이벤트만 올리고, 발행기가
  `@TransactionalEventListener(AFTER_COMMIT)`에서 MQ로 내보낸다. 발행 유실 보증은
  `composite_jobs` 상태 행(아웃박스 염가판) + 잡 스윕 배치가 담당한다.
- **소비 — 멱등이 기본값.** 브로커는 at-least-once, 재전달이 정상이다.
  `job_uuid`(또는 `event_id`) + 상태 전이 가드로 방어한다.
  `default-requeue-rejected: false`, 리스너 재시도 3회(지수 백오프) 소진 시
  DLX → `dlq.*` 격리.
- **직렬화 — 언어 중립 JSON.** 필드는 **snake_case**(Python 워커·DB 컬럼과 통일,
  MODERA camelCase에서 의도적 변경). `__TypeId__` 헤더에 의존하지 않고 리스너마다
  페이로드 클래스를 명시한다. 수신측은 `FAIL_ON_UNKNOWN_PROPERTIES=false`(전방
  호환), 발신측은 **additive-only**(필드 추가만, 제거·개명 금지).
- **공통 봉투.** `event_id`(uuid) + `occurred_at`(ISO-8601 UTC) + 본문.

## Python 워커용 JSON 예시

`job.composite`로 전달되는 `CompositeJobRequestedEvent`:

```json
{
  "event_id": "5f1c9a49-9d2e-4a5b-8b1e-1d6a1c1e7f3a",
  "occurred_at": "2026-09-02T04:12:33.123Z",
  "job_uuid": "b7c93b4e-0a56-4d2e-9f0d-2f6e8a54d61c",
  "job_type": "MOTIF"
}
```

Java record의 camelCase 컴포넌트(`jobUuid`)가 컨버터의 naming strategy에 의해
snake_case(`job_uuid`)로 나간다. record에는 Jackson 애너테이션을 두지 않는다.

## `x-request-id` 헤더 규약

- HTTP 진입 시 `X-Request-Id`를 승계(없으면 생성)해 MDC에 싣는다.
- MQ **발행 시 같은 값을 메시지 헤더 `x-request-id`로 싣는다.**
- 컨슈머(Java·Python)는 헤더에서 꺼내 로그 컨텍스트를 복원하고, 후속 발행이
  있으면 **받은 값을 그대로 이어붙인다.**
- logback 패턴의 `%X{requestId:-}`로 잡 하나를 API → 큐 → 워커 → 알림까지
  단일 ID로 grep 한다.
