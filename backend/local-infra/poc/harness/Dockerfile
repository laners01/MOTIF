# PoC 계측 도구함 — 프로파일을 자기 netns 에 걸고 origin/CloudFront 를 때린다.
FROM alpine:3.20

# ffprobe·tc 존재를 빌드 시점에 못박는다 — 없으면 측정 도중이 아니라 여기서 터진다.
# coreutils: busybox date 는 %N 을 조용히 버려서 밀리초 측정이 전부 0 이 된다.
RUN apk add --no-cache ffmpeg curl python3 iproute2 coreutils \
 && ffprobe -version > /dev/null \
 && tc -V \
 && [ "$(date +%s%N)" != "$(date +%s)N" ] && date +%s%N | grep -qE '^[0-9]{19}$'

WORKDIR /work
CMD ["sleep", "infinity"]
