# PoC origin — nginx + tc.
#
# tc 를 origin 에 넣는 이유: 하네스에서 다운링크를 조이려면 ifb 모듈이 필요한데
# Docker Desktop(macOS)의 LinuxKit 커널에는 없다. ifb 가 없으면 origin 의
# egress 를 조인다 — 그쪽이 곧 다운로드 경로라 같은 결과가 나온다.
# (netem.sh 주석의 "대안" 경로. ifb 가 있는 환경에서는 하네스 쪽을 그대로 쓴다)
FROM nginx:1.27-alpine
RUN apk add --no-cache iproute2 && tc -V
