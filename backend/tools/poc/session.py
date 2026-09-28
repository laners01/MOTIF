#!/usr/bin/env python3
"""세션 시뮬레이터 — 가짜 사용자가 피드를 스와이프하며 Range 로 받는다.

아이템마다: 현재 것을 W초분 받고("시청"), 다음 N개의 첫 T초를 받는다("프리페치").
이미 받아둔 구간은 다시 받지 않는다 — 실제 플레이어와 같고, 그래야 egress 가 맞다.

의존성은 표준 라이브러리뿐이다. 하네스 이미지에 pip 이 없다.

각 요청 URL 에 ?s=<runid> 를 붙인다. nginx 가 request_uri 를 통째로 로그에
남기므로, 실행별로 서버 측 바이트를 골라낼 수 있다 — ±5% 대조의 근거다.
정적 파일 서빙은 쿼리스트링을 무시하므로 응답에는 영향이 없다.
"""
import argparse
import csv
import http.client
from collections import defaultdict
import os
import random
import sys
import time
import urllib.parse
import uuid

# 시청 행동 모델 (설계문서 확정): 3초 스킵 40% / 절반 30% / 완시청 30%
WATCH_MODEL = ((0.40, "skip"), (0.30, "half"), (0.30, "full"))
SKIP_SECONDS = 3.0
SLICE_SECONDS = 1.0  # --realtime 에서 재버퍼를 판정하는 단위

# 플레이어가 재생 위치보다 앞서 받아두는 목표치(초).
#
# 이게 없으면 "1초 재생하려고 1초치를 그때 받는" 모델이 되어, 실제보다 훨씬
# 잘 끊긴다. 실제 플레이어는 여유가 있을 때 앞질러 받아 버퍼를 쌓고, 그
# 버퍼로 네트워크 요동을 흡수한다 (ExoPlayer DefaultLoadControl 기본 최대 50초).
# 30초는 그보다 보수적인 값이다.
BUFFER_TARGET = 30.0

# 스와이프 한 번마다 이 확률로 세션이 끝난다.
#
# 설계문서의 확정 시청 모델에는 이탈이 없다. 그런데 이탈이 없으면 피드 30개를
# 전부 보게 되고, 프리페치한 것이 100% 소비되어 "낭비"가 구조적으로 0 이 된다.
# 그러면 N·T 를 키울수록 무조건 유리해져(선반입은 늘고 총 바이트는 그대로) 주
# 실험이 성립하지 않는다 — 최적값이 N=∞ 로 나온다.
#
# 0.05 는 제안값이다(평균 ~20개 소비). 팀 회의에서 확정한다. 0 으로 두면 확정
# 문서 그대로 돌아가고, report.py 가 낭비 지표를 무효로 표시한다.
DEFAULT_EXIT_PROB = 0.05


def load_feed(path, tag, ladder=None):
    """tag 하나면 고정 화질, ladder 가 있으면 화질별 정보를 한 아이템에 묶는다."""
    with open(path, newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    if ladder:
        by_file = defaultdict(dict)
        for r in rows:
            if r["tag"] in ladder:
                by_file[r["file"]][r["tag"]] = r
        feed = []
        for f in sorted(by_file):
            v = by_file[f]
            if len(v) != len(ladder):        # 화질이 다 갖춰진 파일만 쓴다
                continue
            base = v[ladder[0]]
            feed.append({
                "file": f,
                "duration": float(base["duration"]),
                "levels": [{"tag": t,
                            "rate": float(v[t]["bitrate"]) / 8.0,
                            "size": int(v[t]["size"])} for t in ladder],
            })
        if not feed:
            sys.exit(f"FAIL: {path} 에 {ladder} 가 모두 있는 파일이 없다")
        return feed

    rows = [r for r in rows if not tag or r["tag"] == tag]
    if not rows:
        sys.exit(f"FAIL: {path} 에 tag={tag} 인 항목이 없다")
    return [{"file": r["file"], "duration": float(r["duration"]),
             "levels": [{"tag": r["tag"], "rate": float(r["bitrate"]) / 8.0,
                         "size": int(r["size"])}]} for r in rows]


def parse_policy(text):
    """'N=2,T=1.5' -> (2, 1.5)"""
    kv = dict(p.split("=", 1) for p in text.split(","))
    return int(kv["N"]), float(kv["T"])


class Fetcher:
    """Range 요청 한 곳. 받은 바이트와 걸린 시간만 돌려준다.

    커넥션을 재사용한다(HTTP/1.1 keep-alive). 매번 새로 열면 요청마다 핸드셰이크
    1 RTT + 슬로스타트를 다시 문다 — RTT 300ms 구간에서는 1초짜리 조각 하나에
    600ms 넘는 고정비가 붙어서, 대역폭이 남는데도 재버퍼로 잡힌다. 실제
    플레이어와 CDN 은 전부 커넥션을 재사용하므로 그쪽이 맞는 모델이다.
    """

    def __init__(self, origin, runid, timeout):
        u = urllib.parse.urlsplit(origin)
        self.scheme = u.scheme or "http"
        self.host = u.netloc
        self.base = u.path.rstrip("/")
        self.sub = ""            # ABR 이면 화질 태그가 여기 들어간다
        self.runid = runid
        self.timeout = timeout
        self.requests = 0
        self.reconnects = 0
        self._conn = None

    def _connect(self):
        cls = (http.client.HTTPSConnection if self.scheme == "https"
               else http.client.HTTPConnection)
        self._conn = cls(self.host, timeout=self.timeout)

    def get(self, name, start, end, sub=""):
        """[start, end] 바이트를 받는다. 받은 바이트 수와 소요 초를 돌려준다."""
        self.sub = sub
        if end < start:
            return 0, 0.0
        sub = f"/{self.sub}" if self.sub else ""
        path = f"{self.base}{sub}/{urllib.parse.quote(name)}?s={self.runid}"
        t0 = time.monotonic()
        for attempt in (1, 2):      # 서버가 idle 커넥션을 닫았으면 한 번 다시 연다
            try:
                if self._conn is None:
                    self._connect()
                self._conn.request("GET", path, headers={"Range": f"bytes={start}-{end}"})
                resp = self._conn.getresponse()
                body = resp.read()  # 끝까지 읽어야 커넥션을 다음 요청에 쓸 수 있다
                status = resp.status
                break
            except (http.client.HTTPException, OSError):
                if self._conn is not None:
                    self._conn.close()
                self._conn = None
                self.reconnects += 1
                if attempt == 2:
                    raise
        if status not in (200, 206):
            sys.exit(f"FAIL: {self.scheme}://{self.host}{path} 가 {status} 를 냈다")
        if status == 200 and start > 0:
            sys.exit(f"FAIL: origin 이 Range 를 무시했다(200) — {path}")
        self.requests += 1
        return len(body), time.monotonic() - t0

    def close(self):
        if self._conn is not None:
            self._conn.close()
            self._conn = None


class Abr:
    """ExoPlayer AdaptiveTrackSelection 의 핵심 휴리스틱만 옮긴 것.

    라이브러리 자체가 아니라 **판단 규칙의 모형**이다. 파라미터는 ExoPlayer
    기본값을 따랐다 — 대역폭의 70%만 쓰고(여유), 버퍼가 충분할 때만 올리고,
    마르면 즉시 내린다. 절대값 예측이 아니라 고정 화질과의 비교가 목적이다.
    """

    BANDWIDTH_FRACTION = 0.70     # ExoPlayer bandwidthFraction
    UP_MIN_BUFFER = 10.0          # 이만큼 쌓여야 화질을 올린다 (초)
    PANIC_BUFFER = 3.0            # 이 아래로 마르면 최저 화질로 즉시 내린다
    EWMA_ALPHA = 0.3              # 최근 표본 가중치

    def __init__(self, levels):
        self.levels = sorted(levels, key=lambda l: l["rate"])   # 낮은 화질부터
        self.idx = 0
        self.est = None
        self.switches = 0

    def sample(self, nbytes, seconds):
        if seconds <= 0 or nbytes <= 0:
            return
        tp = nbytes / seconds
        self.est = tp if self.est is None else (
            self.EWMA_ALPHA * tp + (1 - self.EWMA_ALPHA) * self.est)

    def select(self, buffer_seconds):
        if self.est is None:
            return self.levels[self.idx]          # 첫 조각은 최저에서 시작
        budget = self.est * self.BANDWIDTH_FRACTION
        want = 0
        for i, lv in enumerate(self.levels):
            if lv["rate"] <= budget:
                want = i
        if buffer_seconds < self.PANIC_BUFFER:
            want = 0
        elif want > self.idx and buffer_seconds < self.UP_MIN_BUFFER:
            want = self.idx                        # 버퍼가 얕으면 올리지 않는다
        if want != self.idx:
            self.switches += 1
            self.idx = want
        return self.levels[self.idx]


def run_session(feed, policy_n, policy_t, fetcher, rng, realtime, exit_prob, abr, pace):
    n = len(feed)
    have = [defaultdict(float) for _ in range(n)]   # 아이템별·화질별 확보한 초
    watch_b = [0] * n
    pre_b = [0] * n
    watched = [False] * n
    secs = [0.0] * n
    stalls = [0] * n          # 멈춘 횟수(연속 구간 1회)
    stall_sec = [0.0] * n     # 멈춘 시간 합
    switches = [0] * n

    def fetch(i, lv, a, b):
        """아이템 i 의 재생구간 [a, b) 초를 화질 lv 로 확보한다. (바이트, 초)

        화질을 바꾸면 **바꾼 지점부터** 받는다. 앞부분을 다시 받지 않는다 —
        실제 플레이어는 조각 단위로 주소만 바꿔 이어받는다. 이미 확보한
        구간(프리페치분)은 건너뛴다.
        """
        cov = have[i][lv["tag"]]          # 이 화질로 확보한 지점(초)
        if b <= cov:
            return 0, 0.0
        a = max(a, cov)
        start = min(int(a * lv["rate"]), lv["size"])
        end = min(int(b * lv["rate"]), lv["size"])
        have[i][lv["tag"]] = b
        if end <= start:
            return 0, 0.0
        return fetcher.get(feed[i]["file"], start, end - 1,
                           lv["tag"] if abr else "")

    for i in range(n):
        if i > 0 and rng.random() < exit_prob:
            break
        watched[i] = True
        item = feed[i]
        # 화질 정보는 **아이템마다** 다르다 (비트레이트·크기가 파일별로 다름).
        # 하나를 뽑아 전체에 쓰면 짧은 파일에서 파일 끝을 넘는 Range 를 요청해 416 이 난다.
        fixed = item["levels"][0]
        sel = Abr(item["levels"]) if abr else None

        roll, acc = rng.random(), 0.0
        for pv, kind in WATCH_MODEL:
            acc += pv
            if roll < acc:
                break
        w = {"skip": min(SKIP_SECONDS, item["duration"]),
             "half": item["duration"] / 2.0,
             "full": item["duration"]}[kind]
        secs[i] = w

        if not realtime:
            lv = sel.levels[0] if abr else fixed
            nb, _ = fetch(i, lv, 0.0, w)
            watch_b[i] += nb
        else:
            lv = sel.select(0.0) if abr else fixed
            # 재생 위치 t 와 받아둔 끝 filled 을 따로 둔다. 둘의 차이가 버퍼다.
            t = 0.0
            dry = False
            filled = have[i][lv["tag"]]        # 프리페치로 확보한 지점
            while t < w:
                if filled < w and (filled - t) < BUFFER_TARGET:
                    if abr:
                        lv = sel.select(filled - t)
                    nxt = min(filled + SLICE_SECONDS, w)
                    nb, dt = fetch(i, lv, filled, nxt)
                    watch_b[i] += nb
                    if abr:
                        sel.sample(nb, dt)
                    filled = nxt
                else:
                    # 버퍼가 찼다 — 받을 게 없으니 재생 속도로 시간이 흐른다.
                    dt = min(SLICE_SECONDS, w - t)
                    if pace:
                        time.sleep(dt)
                # 흐른 시간(dt)만큼 재생한다. 버퍼가 모자라면 거기서 멈춘다.
                advance = max(min(dt, filled - t), 0.0)
                if advance < dt - 1e-9:
                    stall_sec[i] += dt - advance
                    if not dry:
                        stalls[i] += 1     # 새로 멈춘 순간만 1회로 센다
                    dry = True
                else:
                    dry = False
                t += advance
            switches[i] = sel.switches if abr else 0

        pf = sel.levels[sel.idx] if abr else fixed
        for k in range(i + 1, min(i + 1 + policy_n, n)):
            lvk = next((x for x in feed[k]["levels"] if x["tag"] == pf["tag"]),
                       feed[k]["levels"][0])
            nb, _ = fetch(k, lvk, 0.0, policy_t)
            pre_b[k] += nb

    return dict(watch_b=watch_b, pre_b=pre_b, watched=watched,
                secs=secs, stalls=stalls, stall_sec=stall_sec, switches=switches)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--feed", required=True, help="probe.sh 가 만든 inventory.csv")
    ap.add_argument("--tag", default="", help="inventory 의 tag 로 거른다 (720p 등)")
    ap.add_argument("--abr", default="",
                    help="화질 사다리를 쉼표로 (예: 480p,720p). 주면 재생 중 전환을 "
                         "시뮬레이션한다. --origin 은 태그 바로 위 경로여야 한다 "
                         "(예: http://poc-origin/renditions)")
    ap.add_argument("--policy", required=True, help="N=2,T=1.5")
    ap.add_argument("--origin", required=True, help="http://poc-origin/videos")
    ap.add_argument("--network", default="-", help="CSV 라벨 (netem 프로파일명)")
    ap.add_argument("--sessions", type=int, default=1)
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--exit-prob", type=float, default=DEFAULT_EXIT_PROB,
                    help="스와이프당 이탈 확률 (기본 %(default)s — 제안값, 팀 확인 필요). "
                         "0 이면 낭비 지표가 구조적으로 0 이 된다")
    ap.add_argument("--pace", action="store_true",
                    help="버퍼가 찼을 때 실제로 대기해서 벽시계를 재생 시간에 맞춘다. "
                         "측정 도중 네트워크를 바꾸는 시나리오에 필요하다")
    ap.add_argument("--realtime", action="store_true",
                    help="비트레이트 속도로 받으며 재버퍼를 판정한다 (세션이 영상 길이만큼 걸린다)")
    ap.add_argument("--timeout", type=float, default=120.0)
    ap.add_argument("--out", default="/work/results/session.csv")
    args = ap.parse_args()

    policy_n, policy_t = parse_policy(args.policy)
    ladder = [x.strip() for x in args.abr.split(",") if x.strip()]
    if ladder and not args.realtime:
        sys.exit("FAIL: --abr 은 --realtime 과 같이 쓴다 (전환 판단에 시간축이 필요하다)")
    feed = load_feed(args.feed, args.tag, ladder or None)

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    new = not os.path.exists(args.out)
    with open(args.out, "a", newline="", encoding="utf-8") as fh:
        wr = csv.writer(fh)
        if new:
            wr.writerow(["runid", "session", "item", "file", "policy", "network",
                         "file_tag", "watch_seconds", "bytes_watch", "bytes_prefetch",
                         "watched", "stalls", "stall_sec", "exit_prob", "switches"])

        for s in range(args.sessions):
            runid = uuid.uuid4().hex[:12]
            rng = random.Random(args.seed + s)
            f = Fetcher(args.origin, runid, args.timeout)
            t0 = time.monotonic()
            r = run_session(feed, policy_n, policy_t, f, rng, args.realtime,
                            args.exit_prob, bool(ladder), args.pace)
            f.close()
            elapsed = time.monotonic() - t0

            for i, item in enumerate(feed):
                if r["watch_b"][i] == 0 and r["pre_b"][i] == 0:
                    continue
                wr.writerow([runid, s, i, item["file"], args.policy, args.network,
                             ("abr:" + "+".join(ladder)) if ladder else (args.tag or "-"),
                             f"{r['secs'][i]:.2f}",
                             r["watch_b"][i], r["pre_b"][i],
                             int(r["watched"][i]), r["stalls"][i],
                             f"{r['stall_sec'][i]:.1f}", args.exit_prob,
                             r["switches"][i]])

            total = sum(r["watch_b"]) + sum(r["pre_b"])
            waste = sum(p for p, w in zip(r["pre_b"], r["watched"]) if not w)
            label = ("abr:" + "/".join(ladder)) if ladder else (args.tag or "-")
            sw = f" 전환={sum(r['switches'])}" if ladder else ""
            print(f"session {s} [{args.network}/{label}/{args.policy}] "
                  f"run={runid} {total/1e6:.1f}MB 낭비={waste/1e6:.2f}MB "
                  f"스톨={sum(r['stalls'])}회/{sum(r['stall_sec']):.0f}초{sw} "
                  f"요청={f.requests} {elapsed:.1f}s",
                  file=sys.stderr)


if __name__ == "__main__":
    main()
