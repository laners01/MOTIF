#!/bin/sh
# 영상 신상조사 — 결과 해석의 기준표를 만든다.
#
# 사용: probe.sh <영상디렉터리> [태그] [출력디렉터리]
#   APPEND=1 로 두 번째 태그(480p 등)를 같은 표에 이어붙인다.
#
# 커스텀 MP4 파서를 쓰지 않는다 — 전부 ffprobe 출력이다.
#   moov 앞/뒤: -v trace 가 루트 atom 을 파일 순서대로 찍는다. 먼저 나온 쪽이 앞이다.
#   첫 T초 바이트: 비디오 패킷 pos 의 최대값 = "여기까지 읽어야 T초가 재생된다"
set -eu

DIR=${1:?"사용법: probe.sh <영상디렉터리> [태그] [출력디렉터리]"}
TAG=${2:-$(basename "$DIR")}
OUT=${3:-/work/results}
TS=${TS:-"0.5 1 1.5 2 3"}

mkdir -p "$OUT"
INV="$OUT/inventory.csv"
OFF="$OUT/offsets.csv"

if [ "${APPEND:-0}" != 1 ]; then
    echo "file,tag,duration,bitrate,width,height,gop,moov,moov_bytes,size" > "$INV"
    echo "file,tag,t,bytes" > "$OFF"
fi

# 지정한 키를 key=value 로 받아 하나만 뽑는다. ffprobe 는 요청 순서대로 출력하지
# 않으므로 위치로 자르면 언젠가 틀린다 — 키로 찾는다.
field() { echo "$1" | sed -n "s/^$2=//p" | head -1; }

count=0
for f in "$DIR"/*.mp4 "$DIR"/*.MP4 "$DIR"/*.mov "$DIR"/*.MOV; do
    [ -f "$f" ] || continue
    name=$(basename "$f")

    fmt=$(ffprobe -v error -show_entries format=duration,size,bit_rate -of default=nw=0 "$f")
    dur=$(field "$fmt" duration)
    size=$(field "$fmt" size)
    br=$(field "$fmt" bit_rate)

    st=$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of default=nw=0 "$f")
    w=$(field "$st" width)
    h=$(field "$st" height)

    # 키프레임 간격 — 앞 10초의 키프레임 시각 평균 차이. 하나뿐이면 10초 이상이라는 뜻.
    gop=$(ffprobe -v error -select_streams v:0 -skip_frame nokey \
              -show_entries frame=pts_time -read_intervals %+10 -of csv=p=0 "$f" 2>/dev/null \
          | awk 'NF{n++; if(n>1) s+=$1-p; p=$1} END{if(n>1) printf "%.2f", s/(n-1); else print ">10"}')

    # 루트 atom 을 파일에 놓인 순서대로 훑는다.
    trace=$(ffprobe -v trace -i "$f" 2>&1 | grep -o "type:'[a-z0-9]*' parent:'root' sz: [0-9]*" || true)
    first=$(echo "$trace" | sed -n "s/^type:'\([a-z0-9]*\)'.*/\1/p" | grep -E '^(moov|mdat)$' | head -1)
    [ "$first" = moov ] && moov=front || moov=rear
    moov_bytes=$(echo "$trace" | sed -n "s/^type:'moov' parent:'root' sz: \([0-9]*\)$/\1/p" | head -1)

    echo "$name,$TAG,$dur,$br,$w,$h,$gop,$moov,${moov_bytes:-0},$size" >> "$INV"

    for t in $TS; do
        pos=$(ffprobe -v error -select_streams v -show_entries packet=pos \
                  -read_intervals "%+$t" -of csv=p=0 "$f" 2>/dev/null \
              | awk 'BEGIN{m=0} $1+0>m{m=$1+0} END{printf "%d", m}')
        echo "$name,$TAG,$t,$pos" >> "$OFF"
    done

    count=$((count + 1))
    printf '.' >&2
done

echo >&2
[ "$count" -gt 0 ] || { echo "FAIL: $DIR 에 영상이 없다" >&2; exit 1; }
echo "probe: $count 개 ($TAG) -> $INV, $OFF" >&2

rear=$(awk -F, -v t="$TAG" '$2==t && $8=="rear"' "$INV" | wc -l | tr -d ' ')
if [ "$rear" -gt 0 ]; then
    echo "  moov 가 뒤인 파일 $rear 개 — remux A/B 대상이다 (transcode.sh --faststart)" >&2
else
    echo "  전부 moov 가 앞이다 — faststart remux 불필요, A/B 대조군 없음" >&2
fi
