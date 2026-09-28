# ADR-0008. 레이어드 jar를 채택한다 — Boot 4는 `jarmode=tools`

- 상태: 승인
- 일자: 2026-09-02
- 결정자: 백엔드·인프라
- 관련: ADR-0004("단일 스테이지 jre + 통 jar COPY → 레이어드 jar 검토
  (Jenkins 티켓)" 항목을 **종결**), ADR-0007(레지스트리 없음),
  Jira S15P21D104-181, `backend/app-core-api/Dockerfile`

## 컨텍스트

ADR-0004는 MODERA의 "단일 스테이지 jre + 통 jar COPY"를 계승하지 않고
**"레이어드 jar 검토 (Jenkins 티켓)"** 로 유보했다. 근거는 "롤링 배포에서 pull
시간이 배포 속도에 직결"이었다. Jenkins 티켓이 지금 이 티켓이므로 여기서 닫는다.

전제 하나가 달라졌다. ADR-0007로 레지스트리가 사라져 **pull이 없다**. 유보의
원래 근거는 그대로 성립하지 않는다. 그래서 남은 이득이 실제로 있는지를
이 프로젝트의 jar로 직접 재보고 판단했다.

`app-core-api` bootJar(79MB, Boot 4.0.8)를 실제로 전개한 결과:

| 레이어 | 내용 | 크기 |
|---|---|---|
| `dependencies` | 외부 jar 126개 | **75M** |
| `spring-boot-loader` | 비어 있음 | 0B |
| `snapshot-dependencies` | 비어 있음 | 0B |
| `application` | `app.jar` + 사내 모듈 3개(`module-common`/`module-event`/`domain-core`) | **44K** |

## 결정

**레이어드 jar를 채택한다.** `backend/app-core-api/Dockerfile` 은 2스테이지로,
stage 1이 jar를 레이어로 전개하고 stage 2가 변경 빈도가 낮은 순
(`dependencies` → `spring-boot-loader` → `snapshot-dependencies` → `application`)
으로 COPY한다.

**추출은 `-Djarmode=tools ... extract --layers` 다. `layertools` 가 아니다.**
Boot 3의 `-Djarmode=layertools` 는 3.3에서 deprecated, Boot 4에서 제거됐다.
검색으로 나오는 예제 대부분이 Boot 3 기준이라 되돌아가기 쉬운 지점이므로
Dockerfile 주석에도 같은 내용을 박제해 둔다.

**두 모드는 산출물 구조가 다르다.** 이것이 실제로 사람을 잡는 부분이다.

- layertools: `application` 레이어에 `BOOT-INF/`, `org/` 가 풀리고,
  진입점은 `org.springframework.boot.loader.launch.JarLauncher` 다.
- tools: `application` 레이어에 **`app.jar` 라는 얇은 jar** 가 놓인다.
  MANIFEST의 `Main-Class` 와 `Class-Path: lib/...` 가 의존성을 가리키므로
  로더가 필요 없다. 그래서 `spring-boot-loader` 레이어가 **정상적으로 비고**,
  진입점은 `java -jar app.jar` 다.

즉 **tools 모드로 뽑고 진입점만 `JarLauncher` 로 두면 로더 클래스가 이미지에
아예 없어 `ClassNotFoundException` 으로 죽는다.** 두 결정은 한 쌍이다.

## 근거

- **커밋당 이동량이 75M에서 44K로 줄어든다.** 의존성이 그대로인 커밋
  — 즉 대부분의 커밋 — 에서 새로 쓰이는 레이어는 `application` 하나다.
  레지스트리가 없어 push/pull은 사라졌지만 `docker save | ctr images import`
  는 남아 있고, 이 경로도 바뀐 레이어만 다룬다.
- **이미지가 3종으로 늘 때 이득이 커진다.** core-api·notification·gateway는
  Spring Boot 의존성 대부분을 공유한다. 통 jar로 두면 공유분까지 3벌이
  독립적으로 쌓이지만, 레이어드면 겹치는 레이어를 containerd가 공유한다.
  309GB 디스크에서 당장 문제는 아니나, 이미지를 5개씩 보관하는 정책과
  곱해지면 차이가 실재한다.
- **비용이 거의 없다.** Dockerfile 몇 줄과 빌드 단계 1초 미만이다.
  ADR-0004가 유보한 이유는 비용이 아니라 확인 부족이었다.

## 검토한 대안

| 대안 | 기각 사유 |
|---|---|
| 통 jar COPY 유지(MODERA 방식) | 커밋마다 79MB 레이어가 통째로 새로 생긴다. 도입 비용이 사실상 없는데 유지할 이유가 없다 |
| CDS/AOT 아카이브까지 | 기동 시간 최적화는 별개 문제다. Phase 0의 병목은 기동이 아니라 배포 파이프라인의 존재 자체이므로 범위 밖이다 |
| Buildpacks(`bootBuildImage`) | 레이어링은 자동으로 해주지만 베이스 이미지·JVM 버전 통제가 우리 손을 떠난다. 툴체인 21을 build.gradle 한 곳에서 고정하는 현재 규율과 어긋난다 |

## 결과

- ADR-0004의 "레이어드 jar 검토 (Jenkins 티켓)" 항목은 이 문서로 **종결**된다.
  ADR-0004 본문은 수정하지 않는다(ADR-0001: 승인된 ADR은 고치지 않는다).
- Boot 버전을 올릴 때 확인할 것이 하나 늘었다 — 레이어 이름과 tools 모드의
  산출물 구조. 위 표가 그때의 비교 기준이다.
- `spring-boot-loader` 와 `snapshot-dependencies` 레이어는 지금 비어 있지만
  Dockerfile에 COPY를 남겨둔다. 스냅샷 의존성이 생기거나 Boot가 로더를 다시
  넣는 날 순서를 다시 고민하지 않기 위해서다.
- notification·gateway 이미지도 같은 Dockerfile 형태를 쓴다. 지금 만들지
  않는 이유는 Phase 0의 배포 대상이 core-api 하나이기 때문이며, 추가 시
  이 문서를 근거로 복제하면 된다.
