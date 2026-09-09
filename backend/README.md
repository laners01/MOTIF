# MOTIF Backend

Spring Boot 4.0 멀티모듈. 모든 아키텍처 결정의 근거는 [docs/adr/](docs/adr/)에 있다.

## 모듈 구성

```
module-common ← module-event ← (domain-core, domain-notif) ← app-*
```

| 모듈 | 역할 |
|---|---|
| module-common | ApiResponse 봉투, ErrorCode/BusinessException, @ApiController 마커, BaseTimeEntity, 공통 logback |
| module-event | MQ 토폴로지 상수 + 이벤트 계약 record — **의존성 0** ([README](module-event/README.md), ADR-0005) |
| domain-core | motif_core 스키마 도메인 (엔티티·리포지토리·QueryDSL) — 패키지 `com.ssafy.motif.domain.core` |
| domain-notif | motif_notif 스키마 도메인 — domain-core에 의존하지 않는다 |
| app-core-api | 코어 API — :8081 (관리 :9081) |
| app-notification | 알림 컨슈머 — :8082 (관리 :9082). **domain-core 의존 금지(컴파일 타임 강제)** |

## 로컬 개발 3모드 (ADR-0003)

| 모드 | 프로파일 | DB | MQ·Redis | 용도 |
|---|---|---|---|---|
| 격리 | `local` (기본) | compose | compose | 스키마 실험·기능 개발 |
| 하이브리드 | `local-remote` | compose(자기 것) | 서버(테일넷) | 파이프라인 통합 확인 |
| 서버 | `prod` | 클러스터 | 클러스터 | k8s 배포 |

### 격리 모드 (기본)

```bash
cd backend/local-infra && docker compose up -d --wait   # PG + MQ + Redis + 스텁 워커
cd backend && ./gradlew :app-core-api:bootRun            # 프로파일 미지정 = local
```

로컬 자격증명은 전부 더미(`motif` 계열, 비밀 아님)로 compose에 고정돼 있다.
RabbitMQ 관리 UI: <http://localhost:15672> (motif/motif).

### 하이브리드 모드

```bash
cp backend/.env.example backend/.env    # 값은 팀 비밀 저장소에서 — .env는 gitignore
cd backend && set -a && source .env && set +a && \
  SPRING_PROFILES_ACTIVE=local-remote ./gradlew :app-core-api:bootRun
```

- `SERVER_MQ_PASSWORD`는 fallback이 없다 (placeholder 규율: 비밀값 fallback 금지,
  접속 정보만 fallback 허용 — ADR-0004). 미주입이면 placeholder 원문이 값으로
  남아 **서버 MQ 인증이 실패한다** — 주입 없이는 동작하지 않는다.
  (참고: DB 비밀번호는 Hikari·Liquibase가 기동 시 접속하므로 미주입=기동 실패.)
- **컨슈머 규칙**: 서버 큐를 구독하는 컨슈머를 로컬에서 띄우지 않는다. 같은 큐를
  여럿이 물면 라운드로빈으로 서버 알림 서비스의 이벤트를 로컬이 삼킨다. 코드로
  강제돼 있다 — `local-remote`에서 app-notification은 `listener auto-startup=false`.
  컨슈머까지 통합해야 하면 개인 vhost(`/motif-dev-<이름>`)를 만들어 쓴다 (ADR-0003).

### 서버 모드

k8s 매니페스트가 `SPRING_PROFILES_ACTIVE=prod`와 Secret(비밀번호)을 주입한다.
로컬에서 prod 프로파일을 켤 일은 없다.

## 파이프라인 스모크 (격리 모드)

```bash
cd backend/local-infra && docker compose up -d --wait
cd backend && ./gradlew :app-core-api:bootRun &
cd backend && ./gradlew :app-notification:bootRun &

curl -X POST localhost:8081/api/dev/jobs \
  -H 'Content-Type: application/json' -d '{"jobType":"MOTIF"}'
```

기대: ① core-api `job.composite 발행` 로그 → ② 스텁 워커 `수신`/`발행` 로그(2초)
→ ③ notification `composite.done 수신` 로그. **세 로그의 requestId가 같다**
(`docker logs motif-stub-worker`, 각 앱 콘솔의 `[requestId]` 비교 — ADR-0005 추적).

health 확인: `curl localhost:8081/api/health`(앱 포트, Traefik 계약),
`curl localhost:9081/actuator/health`(관리 포트). 게이트웨이는 없다 —
Traefik이 core-api로 직결한다 (ADR-0009).

## 팀 표준 요약

- **복합 PK는 `@EmbeddedId`** 로 통일한다 (`@IdClass` 금지).
- **Auditing**: 모든 엔티티는 `BaseTimeEntity` 상속 — `@PrePersist`→`regDttm`,
  `@PreUpdate`→`modDttm`(INSERT 시 NULL이 정상). 타입 `Instant`/컬럼 TIMESTAMPTZ.
- **app 내부 패키지는 `domain/` + `global/`** — 기능은 `coreapi.domain.<도메인>`,
  횡단 관심사는 `coreapi.global`. `domain-core` **모듈**(엔티티·리포지토리,
  `motif.domain.core`)과 앱 안의 `coreapi.domain`(웹·유스케이스)은 다른 계층이다 —
  같은 단어를 쓰지만 앞 경로로 구분한다.
- **컨트롤러와 Swagger 문서를 분리한다** — `domain/<도메인>/controller/XxxController` +
  `domain/<도메인>/docs/XxxControllerDocs`(인터페이스). Swagger 애너테이션은 인터페이스에,
  **Spring MVC 매핑과 `@Valid` 는 구현 클래스에** 둔다(인터페이스 매핑 상속은 동작이 미묘하다).
  필드 설명은 DTO 의 `@Schema`, 인증 불필요(🔓) 오퍼레이션은 `@SecurityRequirements` 로
  전역 bearerAuth 요구를 해제한다. 클라이언트 계약이 아닌 엔드포인트는 `@Hidden`.
- **ErrorCode 문자열은 전역 유일** — 도메인 enum 추가 시 접두사를 예약하고
  리뷰에서 충돌을 확인한다 (공통은 `C___`).
- **응답·예외 3종**: `ApiResponse` 봉투 + `ErrorCode` enum + `@ApiController`
  마커로 스코프 제한한 전역 핸들러 (ADR-0004).
- **DDL은 Liquibase 소유** — `ddl-auto: validate` 전 프로파일 고정. changelog는
  모듈별·스키마별 master + 번호 붙은 raw SQL include (0001부터, 배포된 파일 수정 금지).
- **MQ 규약**은 [module-event/README.md](module-event/README.md) — snake_case,
  AFTER_COMMIT 발행, 멱등 소비, `x-request-id` 전파.
- Boot 4 주의: REST는 Jackson 3 자동구성(camelCase), MQ만 Jackson 2 전용 빈.
  스타터 명칭이 다르다(`-webmvc`, `starter-liquibase`) — ADR-0004.

## ADR

| # | 제목 |
|---|---|
| [0001](docs/adr/0001-adopt-adr.md) | ADR 도입 |
| [0002](docs/adr/0002-two-tier-gateway.md) | Traefik + SCG 2층 구성 — **ADR-0009로 대체됨** |
| [0003](docs/adr/0003-local-dev-three-modes.md) | 로컬 개발 3모드 |
| [0004](docs/adr/0004-stack-selection-vs-modera.md) | 스택 선정 — MODERA 대비 계승·변경 |
| [0005](docs/adr/0005-event-conventions.md) | 이벤트 규약 — 발행·소비·직렬화·추적 |
| [0007](docs/adr/0007-no-registry-containerd-direct-import.md) | 레지스트리 없이 containerd 직접 임포트 |
| [0008](docs/adr/0008-layered-jar-dockerfile.md) | 레이어드 jar — Boot 4는 `jarmode=tools` |
| [0009](docs/adr/0009-remove-spring-cloud-gateway.md) | SCG 제거 — 인증은 core-api Spring Security |
