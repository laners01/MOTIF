# core-api 실행 이미지 (ADR-0007 / ADR-0008)
#
# 빌드는 Jenkins 호스트의 Gradle이 끝내고 온다. 이 Dockerfile은 이미 만들어진
# bootJar를 레이어로 펼치기만 한다. 멀티스테이지 Gradle 빌드를 쓰지 않는 이유:
#   - 15Gi 서버에서 Gradle 데몬(2g)과 빌드킷이 동시에 힙을 잡으면 위험하다.
#   - 빌드 컨테이너가 사라질 때 Gradle 캐시도 같이 버려져 매 빌드가 풀 다운로드다.
#   - 호스트에서 이미 test를 돌리므로 이미지 안에서 다시 돌릴 이유가 없다.
#
# ── 레이어 추출은 layertools 가 아니라 tools 모드다 ──────────────────────────
# Boot 3까지의 `-Djarmode=layertools ... extract` 는 3.3에서 deprecated,
# Boot 4에서 제거됐다. Boot 4는 아래 tools 모드를 쓴다.
# 되돌리면 "Unsupported jarmode" 로 이미지 빌드가 깨진다.
#
# 함께 박제할 것 — tools 모드는 layertools 와 산출물 구조가 다르다.
# 이 프로젝트 jar(Boot 4.0.8)로 실측한 결과:
#
#   extracted/dependencies/lib/        126개 jar, 75M   <- 거의 안 변함
#   extracted/spring-boot-loader/      비어 있음, 0B
#   extracted/snapshot-dependencies/   비어 있음, 0B
#   extracted/application/             app.jar + lib/(사내 모듈 3개), 44K  <- 커밋마다 변함
#
# spring-boot-loader 레이어가 비는 것이 정상이다. tools 모드가 뽑아내는
# application/app.jar 는 MANIFEST의 Main-Class + Class-Path로 lib/를 가리키는
# 얇은 jar라, 로더가 필요 없다. 따라서 ENTRYPOINT는
#   java -jar app.jar
# 이며 `org.springframework.boot.loader.launch.JarLauncher` 를 쓰면 안 된다.
# 그 클래스는 layertools 산출물에만 존재하므로 여기서는 ClassNotFoundException 이다.
# (검색으로 나오는 예제 대부분이 Boot 3 기준이라 재발하기 쉬운 지점이다.)
#
# 베이스 이미지 태그의 21은 backend/build.gradle 의
# `JavaLanguageVersion.of(21)` 과 맞춘 값이다. 툴체인을 올리면 여기도 올린다.
ARG JRE_IMAGE=eclipse-temurin:21-jre

# ── stage 1: jar를 레이어로 전개 ─────────────────────────────────────────────
FROM ${JRE_IMAGE} AS extractor
WORKDIR /builder

# build/libs 에는 bootJar 말고 -plain.jar 도 함께 있다. 그래서 `*.jar` 로 받으면
# COPY 대상이 둘이 되어 실패한다. `-SNAPSHOT.jar` 로 끝나는 쪽만 집는다
# (plain 은 `-SNAPSHOT-plain.jar` 라 이 패턴에 걸리지 않는다).
ARG JAR_FILE=build/libs/app-core-api-*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ── stage 2: 런타임 ──────────────────────────────────────────────────────────
FROM ${JRE_IMAGE}

# 비루트 실행. 이미지 안에서 쓰기가 필요한 경로가 없으므로 홈은 워크디렉터리로 둔다.
RUN groupadd --system --gid 1001 app \
 && useradd --system --uid 1001 --gid app --home-dir /application --shell /usr/sbin/nologin app

WORKDIR /application

# 변경 빈도가 낮은 순으로 COPY 해야 앞 레이어가 캐시에 남는다.
# dependencies(75M)가 맨 앞이라, 의존성이 그대로인 커밋은 44K만 새로 올라간다.
# 지금은 비어 있는 두 레이어도 순서를 지켜 남겨둔다 — 스냅샷 의존성이 생기거나
# Boot가 로더를 다시 넣는 날 순서를 다시 고민하지 않기 위해서다.
COPY --from=extractor --chown=app:app /builder/extracted/dependencies/ ./
COPY --from=extractor --chown=app:app /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /builder/extracted/application/ ./

USER app

# 8081 서비스 / 9081 관리 — application.yml 의 server.port, management.server.port.
# EXPOSE 는 문서 역할일 뿐이고 실제 노출은 Service·Ingress가 정한다.
EXPOSE 8081 9081

ENTRYPOINT ["java", "-jar", "app.jar"]
