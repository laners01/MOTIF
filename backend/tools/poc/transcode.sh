#!/bin/sh
# 원본은 건드리지 않는다 — 사본만 만든다. 출력은 origin 이 /renditions/ 로 서빙한다.
#
# 사용:
#   transcode.sh --faststart <src>            moov 가 뒤인 파일만 앞으로 옮긴 사본 (-c copy, 화질 무변경)
#   transcode.sh --rendition 480 <src>        저화질 렌디션 (재인코딩, 화질 변함 — ABR 판정용)
#
# --faststart 는 probe 가 rear 를 찾았을 때만 의미가 있다. 전부 front 면 쓰지 않는다.
set -eu

MODE=${1:?"사용법: transcode.sh --faststart <src> | --rendition <높이> <src>"}
case "$MODE" in
    --faststart) SRC=${2:?src 없음}; TAG=faststart ;;
    --rendition) H=${2:?높이 없음}; SRC=${3:?src 없음}; TAG="${H}p" ;;
    *) echo "알 수 없는 모드: $MODE" >&2; exit 2 ;;
esac

DST=${DST:-/srv/renditions/$TAG}
mkdir -p "$DST"

n=0
for f in "$SRC"/*.mp4 "$SRC"/*.MP4 "$SRC"/*.mov "$SRC"/*.MOV; do
    [ -f "$f" ] || continue
    name=$(basename "$f")
    out="$DST/${name%.*}.mp4"
    [ -f "$out" ] && { echo "skip $name (이미 있음)" >&2; continue; }

    if [ "$MODE" = --faststart ]; then
        # 뒤에 moov 가 있는 파일만. 앞이면 사본이 원본과 같아 A/B 가 성립하지 않는다.
        first=$(ffprobe -v trace -i "$f" 2>&1 \
                | sed -n "s/^type:'\([a-z0-9]*\)' parent:'root'.*/\1/p" \
                | grep -E '^(moov|mdat)$' | head -1)
        [ "$first" = mdat ] || { echo "skip $name (이미 faststart)" >&2; continue; }
        ffmpeg -v error -y -i "$f" -c copy -movflags +faststart "$out"
    else
        # -2: 폭을 짝수로 맞춘다(h264 요구). CRF 고정이라 비트레이트는 내용에 따라 정해진다.
        ffmpeg -v error -y -i "$f" -vf "scale=-2:$H" \
               -c:v libx264 -preset veryfast -crf 26 \
               -c:a aac -b:a 96k -movflags +faststart "$out"
    fi
    n=$((n + 1))
    printf '.' >&2
done

echo >&2
echo "transcode($TAG): $n 개 -> $DST" >&2
[ "$n" -gt 0 ] || echo "  (대상 없음)" >&2
