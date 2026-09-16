# app-notification 실행 이미지 (ADR-0007 / ADR-0008)
#
# 구조는 app-core-api/Dockerfile 과 같다 — Jenkins 호스트의 Gradle 이 끝낸
# bootJar 를 레이어로 펼치기만 한다. 이유·주의사항은 그쪽 주석을 참조.
#
# app-media 와 달리 **ffmpeg 가 필요 없다.** 하는 일이 DB 조회 한 번과 FCM
# HTTPS 호출이라 JRE 만 있으면 된다. 그래서 alpine 위에 얹을 것이 없고
# 이미지가 베이스(286MB) 그대로다.
#
# ** 쓰기 경로도 없다. ** 자격증명은 Secret 볼륨으로 읽기 전용 마운트된다
# (/etc/fcm/service-account.json). 컨테이너 안에 비밀을 굽지 않는다.
#
# 베이스 이미지 태그의 21은 backend/build.gradle 의
# `JavaLanguageVersion.of(21)` 과 맞춘 값이다. 툴체인을 올리면 여기도 올린다.
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ── stage 1: jar를 레이어로 전개 ─────────────────────────────────────────────
FROM ${JRE_IMAGE} AS extractor
WORKDIR /builder

# build/libs 에는 bootJar 말고 -plain.jar 도 함께 있다. `-SNAPSHOT.jar` 로 끝나는
# 쪽만 집는다 (plain 은 `-SNAPSHOT-plain.jar` 라 이 패턴에 걸리지 않는다).
ARG JAR_FILE=build/libs/app-notification-*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# Boot 4 는 tools 모드다. layertools 로 되돌리면 "Unsupported jarmode" 로 깨진다.
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ── stage 2: 런타임 ──────────────────────────────────────────────────────────
FROM ${JRE_IMAGE}

# 비루트 실행. uid/gid 1001 은 매니페스트의 securityContext.fsGroup 과 같은 값이라
# Secret 볼륨을 0440 으로 좁혀도 읽을 수 있다 — 어긋나면 기동 시점에
# "FCM 서비스 계정 JSON 을 읽을 수 없다" 로 실패한다(FcmConfig).
RUN addgroup -S -g 1001 app \
 && adduser -S -u 1001 -G app -h /application -s /sbin/nologin app

WORKDIR /application

# 변경 빈도가 낮은 순으로 COPY 해야 앞 레이어가 캐시에 남는다.
COPY --from=extractor --chown=app:app /builder/extracted/dependencies/ ./
COPY --from=extractor --chown=app:app /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /builder/extracted/application/ ./

USER app

# 8082 서비스 / 9082 관리 — application.yml 의 server.port, management.server.port.
# 8082 는 아무도 부르지 않는다(Service·Ingress 없음). 9082 가 프로브 전용이다.
EXPOSE 8082 9082

ENTRYPOINT ["java", "-jar", "app.jar"]
