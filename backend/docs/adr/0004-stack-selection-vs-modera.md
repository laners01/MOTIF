# ADR-0004. 기술 스택 선정 — 직전 프로젝트(MODERA) 대비 계승과 변경

- 상태: 승인
- 일자: 2026-09-02 (MODERA 코드 실측 분석 반영: 동일 일자)
- 관련: MODERA(2026.07–08, Spring Boot 4.0.2 / Redis Streams / MinIO /
  Nginx blue-green / Prometheus·Grafana·Loki / k6), MODERA 분석보고서

## 컨텍스트

팀은 두 달 전 같은 계열의 구조(presigned 직접 업로드, 이벤트 기반 서버
분리, 논리 참조, CQRS read model, FCM)를 MODERA에서 운영해봤다. MOTIF의
스택은 백지 선택이 아니라 **그 경험에 대한 개정**이다. 본 ADR의 실측
근거는 MODERA 레포 정적 분석 보고서에 파일 경로 단위로 남아 있다.

## 결정

### 계승

| 항목 | 근거 |
|---|---|
| Java 21 (toolchain + foojay resolver) | MODERA 검증 구성 그대로 |
| Spring Boot 4.0 최신 패치 | 4.0.2 운영 실적. 단 Boot 4는 스타터 명칭이 다르고(`-webmvc`, `starter-liquibase`) Jackson 3을 자동구성한다 — 아래 "변경·주의" 참조 |
| Gradle Groovy DSL | 계승. 단 버전 관리는 gradle.properties 문자열 참조에서 **버전 카탈로그로 승격** |
| QueryDSL | **`io.github.openfeign.querydsl` 7.x (OpenFeign 포크)** — MODERA 실측 좌표. `com.querydsl` 5.x를 쓰면 검증된 것과 다른 물건이 된다 |
| springdoc 3.x (webmvc-ui/-api) | 실측 3.0.1 |
| Liquibase | Boot 기본 경로 + 번호 붙은 raw SQL changelog + master yaml include 체계(api 44개 파일 운영 실적). MOTIF는 모듈별·스키마별로 확장 |
| 멀티모듈 + 계약 모듈 | "공유는 계약 모듈뿐" 규칙이 유효했음 → 7모듈로 확장, 스키마 경계를 Gradle 의존으로 컴파일 타임 강제 |
| local-infra compose | 전 서비스 healthcheck + `service_healthy` + 더미 자격증명 주석 명시 관례 계승, 스텁 워커·테일넷 하이브리드 확장 (ADR-0003) |
| Prometheus·Grafana | micrometer는 지금, 스택은 Phase 4. 배포 게이트용 5xx PromQL(무트래픽 방어 포함)은 MODERA 산출물 재사용 |
| k6 | Phase 4 데모 도구 |
| 설정 규율 | 비밀값 placeholder **fallback 금지**(미주입=기동 실패), 접속 정보만 fallback 허용. `ddl-auto: validate` 전 프로파일 고정. `open-in-view: false` |
| 응답·예외 3종 | ApiResponse envelope + ErrorCode 인터페이스/도메인 enum(코드 전역 유일) + 마커 애너테이션으로 스코프 제한한 전역 핸들러 |
| 관리 포트 분리 | MODERA api는 분리(9080), worker는 미분리였음 — MOTIF는 **전 모듈 분리로 통일** |

### 변경

| MODERA 실물 | MOTIF | 근거 |
|---|---|---|
| Redis Streams — 수동 XREADGROUP 루프, BUSYGROUP 처리, block<timeout 불변식, 컨슈머명 랜덤으로 인한 PEL 고아 | **RabbitMQ** | ① 우선순위 큐(유료 5/무료 0)는 Streams에 없어 스트림 2개에 12개 타입이 혼승하며 HOL 블로킹 주석까지 남았다 ② **`PelReclaimScanner`(1분 주기 XPENDING idle≥3분 → XCLAIM, 5회 초과 XACK 폐기)를 직접 구현·운영했고, api 측 스트림은 같은 배치가 TODO로 미구현인 비대칭까지 있었다** — 이 전부가 브로커 내장(사망 시 unacked 자동 재큐, x-delivery-limit, DLX)으로 대체된다 ③ Python 워커와 AMQP 표준, 계정·vhost 격리. Kafka는 리플레이 불요·우선순위 필요·램 제약으로 기각 |
| envelope payload 이중 직렬화(스트림 필드맵이 문자열만 허용) | AMQP body는 바이트 — 이중 직렬화 소멸, 메타는 메시지 프로퍼티로 | 구조 단순화 |
| MinIO + ObjectCreated webhook | S3 + CloudFront, 완료는 클라이언트 보고 | 관리형 전환(무료 1TB CDN), webhook 의존 제거 |
| Nginx + bounce blue-green(sed conf 치환·resolver 트릭·수동 conf 배포 함정) + worker는 force-recreate로 **중단 배포** | Traefik + k3s 롤링 | 수제 전환 스크립트 94줄이 오케스트레이터 내장으로. api만 무중단이던 격차도 해소 |
| 각 서버 내 Spring Security | SCG 엣지 인증 | ADR-0002 |
| 로컬 도커 데몬 태그 + `:latest` 운영 참조 (레지스트리 부재) | **GHCR + 빌드번호 태그, `:latest` 운영 참조 금지** | k3s 롤링은 레지스트리가 전제. 태그 불변성으로 롤백 가능 |
| 단일 스테이지 jre + 통 jar COPY | 레이어드 jar 검토 (Jenkins 티켓) | 롤링 배포에서 pull 시간이 배포 속도에 직결 |

### 신규

| 항목 | 근거 |
|---|---|
| MapStruct | MODERA는 수동 매핑. 38테이블 규모 대비 컴파일 타임 생성 도입 |
| ShedLock | replica 2 배치 중복 방지 (설계서 확정) |

## 결과와 트레이드오프

- **Boot 4 = Jackson 3 자동구성.** `com.fasterxml` ObjectMapper 빈이 자동
  생성되지 않아 MODERA도 이벤트용 Jackson 2 빈을 수동 등록했다. MOTIF는
  REST=Jackson 3(자동) / MQ=Jackson 2(전용 빈, Spring AMQP
  `Jackson2JsonMessageConverter`)로 **세대를 경계별로 고정**한다.
  Spring AMQP가 Jackson 3 컨버터로 이행하면 그때 별도 ADR로 전환한다.
- 가상 스레드: MODERA는 전 모듈 `spring.threads.virtual.enabled: true`로
  운영했다(실적 있음). MOTIF는 Phase 4 부하 테스트에서 켜는 결정을
  유지하되, 이 실적을 근거로 리스크는 낮게 본다.
- MODERA의 JWT 만료 구성(access 30일 > refresh 14일)은 통상과 반대 —
  Phase 1 인증 설계에서 **복사 금지** 항목으로 기록한다.
- Loki는 경험 스택이며 현 규모는 `kubectl logs`로 충분 — Phase 4 옵션.
