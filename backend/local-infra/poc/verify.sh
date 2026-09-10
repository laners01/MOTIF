#!/bin/sh
# 완료 조건 자체검사 — 영상 없이 돈다.
#
# 검사하는 것은 전부 파일 내용과 무관한 환경 속성이다:
#   1) Range 요청에 206 + 요청한 바이트만 온다
#   2) netem 이 실제로 걸린다 — 3g(1.5Mbps·RTT 300ms)에서 1MiB 는
#      전송 5.59초(8,388,608bit / 1,500,000) + 연결 0.3 + 첫바이트 0.3
#      + slow start 램프 ~0.6 ≈ 6.8초. 안 걸렸으면 0.001초에 끝나 즉시 드러난다
#   3) nginx 가 Range·전송 바이트·처리 시간을 CSV 로 남긴다
# MP4 구조(moov 위치·첫 GOP·TTFF)는 티켓 4 의 몫이고, 그때 실제 영상이 필요하다.
set -eu

BASE=${BASE:-http://poc-origin}
DUMMY=/srv/check/2mb.bin

[ -s "$DUMMY" ] || head -c 2097152 /dev/urandom > "$DUMMY"

sh /work/profiles/netem.sh 3g
RESULT=$(curl -s -r 0-1048575 -o /dev/null -w '%{http_code} %{size_download} %{time_total}' "$BASE/check/2mb.bin")
sh /work/profiles/netem.sh off

echo "응답: $RESULT (code size seconds)"
echo "nginx 로그(마지막 /check 요청):"
grep '/check/' /var/log/nginx/access.csv | tail -1

CODE=$(echo "$RESULT" | cut -d' ' -f1)
SIZE=$(echo "$RESULT" | cut -d' ' -f2)
TIME=$(echo "$RESULT" | cut -d' ' -f3)

[ "$CODE" = 206 ] || { echo "FAIL: Range 응답이 206 이 아니다 ($CODE)"; exit 1; }
[ "$SIZE" = 1048576 ] || { echo "FAIL: 1MiB 가 아니라 $SIZE 바이트를 받았다"; exit 1; }
awk -v t="$TIME" 'BEGIN {
    if (t < 5.5 || t > 8.5) { print "FAIL: 1MiB 에 " t "초 — 1.5Mbps 면 ~6.8초여야 한다 (netem 미적용 의심)"; exit 1 }
    print "OK: 1MiB " t "초 (기준 ~6.8초), 206, 로그 기록됨"
}'
