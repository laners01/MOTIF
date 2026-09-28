# app-media 실행 이미지 (ADR-0007 / ADR-0008)
#
# 구조는 app-core-api/Dockerfile 과 같다 — Jenkins 호스트의 Gradle 이 끝낸
# bootJar 를 레이어로 펼치기만 한다. 이유·주의사항은 그쪽 주석을 참조.
#
# ── 여기만 다른 점: ffmpeg 가 필요하다 ──────────────────────────────────────
# 다른 모듈은 JRE 만 있으면 되지만 app-media 는 ffmpeg·ffprobe 를 프로세스로
# 부른다(HlsPackager). 그래서 베이스가 다르다.
#
# 베이스를 alpine 으로 고른 근거 — 실측:
#
#   eclipse-temurin:21-jre          507MB  + ffmpeg(apt) = 1.08GB
#   eclipse-temurin:21-jre-alpine   286MB  + ffmpeg(apk) =  441MB   <- 채택
#
# Ubuntu 쪽은 --no-install-recommends 를 줘도 의존성이 573MB 를 끌고 온다.
# ADR-0007 이 레지스트리 없이 containerd 로 직접 import 하는 구성이라
# 이미지 크기가 곧 배포 전송 시간이다. 2.5배 차이를 그냥 두지 않는다.
#
# musl 이라 JNI 네이티브 라이브러리를 쓰는 의존성이 들어오면 깨질 수 있다.
# 지금 app-media 는 순수 자바 + 외부 프로세스라 해당 사항이 없다 — 그런
# 의존성이 생기면 이 선택을 다시 본다.
#
# 베이스 이미지 태그의 21은 backend/build.gradle 의
# `JavaLanguageVersion.of(21)` 과 맞춘 값이다. 툴체인을 올리면 여기도 올린다.
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ── stage 1: jar를 레이어로 전개 ─────────────────────────────────────────────
FROM ${JRE_IMAGE} AS extractor
WORKDIR /builder

# build/libs 에는 bootJar 말고 -plain.jar 도 함께 있다. `-SNAPSHOT.jar` 로 끝나는
# 쪽만 집는다 (plain 은 `-SNAPSHOT-plain.jar` 라 이 패턴에 걸리지 않는다).
ARG JAR_FILE=build/libs/app-media-*-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

# Boot 4 는 tools 모드다. layertools 로 되돌리면 "Unsupported jarmode" 로 깨진다.
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ── stage 2: 런타임 ──────────────────────────────────────────────────────────
FROM ${JRE_IMAGE}

# ffmpeg 패키지에 ffprobe 가 같이 들어온다. 빌드 시점에 존재를 못박는다 —
# 없으면 운영 중 첫 잡에서가 아니라 여기서 터진다.
RUN apk add --no-cache ffmpeg \
 && ffmpeg -version > /dev/null \
 && ffprobe -version > /dev/null

# 비루트 실행. app-core-api 와 달리 쓰기 경로가 필요하다 — 인코딩 작업 디렉터리다.
RUN addgroup -S -g 1001 app \
 && adduser -S -u 1001 -G app -h /application -s /sbin/nologin app \
 && mkdir -p /var/tmp/motif-media \
 && chown app:app /var/tmp/motif-media

WORKDIR /application

# 변경 빈도가 낮은 순으로 COPY 해야 앞 레이어가 캐시에 남는다.
COPY --from=extractor --chown=app:app /builder/extracted/dependencies/ ./
COPY --from=extractor --chown=app:app /builder/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /builder/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /builder/extracted/application/ ./

USER app

# 8083 서비스 / 9083 관리 — application.yml 의 server.port, management.server.port.
EXPOSE 8083 9083

ENTRYPOINT ["java", "-jar", "app.jar"]
