#!/usr/bin/env python3
"""CSV -> 마크다운 리포트.

세 가지를 낸다:
  1) 계측 검증 — 시뮬레이터 합계 vs nginx 로그 합계 (±5%). 이게 어긋나면 나머지는 볼 것 없다.
  2) 매트릭스 — 네트워크 x 화질 x 정책별 바이트·낭비·스톨·TTFF
  3) 해석 보조 — 트래픽 모델 대비 오차, 비트레이트 구간별 분해, 인벤토리 요약
"""
import argparse
import csv
import os
import statistics
import sys
from collections import defaultdict

RECONCILE_TOLERANCE = 0.05


def read_csv(path):
    if not path or not os.path.exists(path):
        return []
    with open(path, newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def read_nginx(path):
    """헤더 없는 CSV: time,uri,status,range,body_bytes_sent,request_time"""
    if not os.path.exists(path):
        return {}
    per_run = defaultdict(lambda: {"bytes": 0, "requests": 0})
    with open(path, newline="", encoding="utf-8") as fh:
        for row in csv.reader(fh):
            if len(row) < 5:
                continue
            uri = row[1]
            if "?s=" not in uri:
                continue
            runid = uri.split("?s=", 1)[1].split("&", 1)[0]
            try:
                per_run[runid]["bytes"] += int(row[4])
            except ValueError:
                continue
            per_run[runid]["requests"] += 1
    return per_run


def md_table(headers, rows):
    out = ["| " + " | ".join(headers) + " |",
           "|" + "|".join("---" for _ in headers) + "|"]
    out += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
    return "\n".join(out)


def pct(x):
    return f"{x * 100:.1f}%"


def mb(x):
    return f"{x / 1e6:.1f}"


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--results", default="/work/results")
    ap.add_argument("--nginx-log", default="/var/log/nginx/access.csv")
    ap.add_argument("--out", default="")
    args = ap.parse_args()

    R = args.results
    sessions = read_csv(os.path.join(R, "session.csv"))
    inventory = read_csv(os.path.join(R, "inventory.csv"))
    offsets = read_csv(os.path.join(R, "offsets.csv"))
    ttff = read_csv(os.path.join(R, "ttff.csv"))
    nginx = read_nginx(args.nginx_log)

    if not sessions:
        sys.exit(f"FAIL: {R}/session.csv 가 없다 — session.py 를 먼저 돌린다")

    L = ["# 영상 전달 PoC 결과", "",
         "판정 기준: [../../docs/poc/video-delivery.md](../../docs/poc/video-delivery.md)", ""]

    # ---- 1. 계측 검증 -------------------------------------------------
    L += ["## 1. 계측 검증 — 시뮬레이터 vs nginx 로그", "",
          "완료 조건은 두 합계가 ±5% 안에 드는 것이다. 벗어나면 시뮬레이터가",
          "요청을 빠뜨렸거나 잘못 세고 있다는 뜻이고, 아래 결과는 전부 무효다.", ""]

    per_run = defaultdict(int)
    for r in sessions:
        per_run[r["runid"]] += int(r["bytes_watch"]) + int(r["bytes_prefetch"])

    if not nginx:
        L += ["> ⚠️ nginx 로그에서 `?s=` 표식이 붙은 요청을 찾지 못했다 — 대조를 건너뛴다.",
              "> (하네스 밖에서 돌렸거나 로그가 비었다)", ""]
        worst = None
    else:
        rows, worst = [], 0.0
        for runid, sim in sorted(per_run.items()):
            srv = nginx.get(runid, {}).get("bytes", 0)
            req = nginx.get(runid, {}).get("requests", 0)
            delta = (sim - srv) / srv if srv else float("inf")
            worst = max(worst, abs(delta))
            rows.append([runid, mb(sim), mb(srv), req,
                         f"{delta * 100:+.2f}%",
                         "OK" if abs(delta) <= RECONCILE_TOLERANCE else "**FAIL**"])
        L += [md_table(["run", "시뮬(MB)", "nginx(MB)", "요청", "차이", "판정"], rows), ""]
        L += [f"**최대 오차 {worst * 100:.2f}%** — "
              + ("기준 통과 (±5%)" if worst <= RECONCILE_TOLERANCE else "**기준 초과. 계측을 먼저 고친다**"), ""]

    # ---- 2. 매트릭스 ---------------------------------------------------
    groups = defaultdict(lambda: {"watch": 0, "pre": 0, "waste": 0,
                                  "stalls": 0, "runs": set(), "items": 0, "watched": 0})
    exit_probs = set()
    for r in sessions:
        key = (r["network"], r["file_tag"], r["policy"])
        g = groups[key]
        w, p = int(r["bytes_watch"]), int(r["bytes_prefetch"])
        g["watch"] += w
        g["pre"] += p
        g["items"] += 1
        g["stalls"] += int(r["stalls"])
        g["runs"].add(r["runid"])
        if r["watched"] == "1":
            g["watched"] += 1
        else:
            g["waste"] += p
        exit_probs.add(r.get("exit_prob", "?"))

    ttff_idx = {(t["network"], t["tag"]): t for t in ttff}
    # 셰이핑 없이 잰 값 = ffmpeg 프로세스 시작 + 디코더 초기화. 설계문서의
    # TTFF(net) 은 "ffmpeg 실행 시간"으로 정의돼 이 상수를 포함하는데, 그
    # 상태로 p50<200ms 를 목표로 걸었다. 빼고도 보여줘야 판정이 성립한다.
    base = {t["tag"]: int(t["p50_ms"]) for t in ttff if t["network"] == "off"}

    rows = []
    for (net, tag, pol), g in sorted(groups.items()):
        total = g["watch"] + g["pre"]
        n_runs = len(g["runs"])
        t = ttff_idx.get((net, tag))
        b = base.get(tag)
        net_p50 = f"{int(t['p50_ms']) - b}ms" if (t and b is not None) else "-"
        rows.append([net, tag, pol, n_runs,
                     mb(total / n_runs),
                     pct(g["waste"] / total) if total else "-",
                     g["stalls"],
                     t["p50_ms"] + "ms" if t else "-",
                     t["p95_ms"] + "ms" if t else "-",
                     net_p50])
    L += ["## 2. 매트릭스", "",
          md_table(["네트워크", "화질", "정책", "세션", "세션당(MB)", "낭비율",
                    "스톨", "TTFF p50", "TTFF p95", "TTFF(net) p50"], rows), ""]

    if base:
        L += ["> `TTFF p50` 은 ffmpeg 실행 시간 전체다 — 프로세스 시작·디코더 초기화가",
              "> 섞여 있다. 셰이핑을 끄고 잰 그 상수가 "
              + ", ".join(f"`{k}` {v}ms" for k, v in sorted(base.items())) + " 이고,",
              "> `TTFF(net)` 은 그걸 뺀 순 네트워크 항이다. 설계문서의 p50<200ms 목표는",
              "> 상수 포함값에 걸려 있어 그대로 쓰면 도구 오버헤드까지 판정에 들어간다.", ""]

    if exit_probs == {"0.0"} or exit_probs == {"0"}:
        L += ["> ⚠️ **낭비율은 무효다.** `--exit-prob 0` 이면 피드를 끝까지 소비하므로",
              "> 프리페치가 100% 쓰이고 낭비가 구조적으로 0 이 된다. 그 상태에서는",
              "> N·T 를 키울수록 유리하기만 해서 정책 비교가 성립하지 않는다.", ""]

    # ---- 3. 트래픽 모델 대비 오차 --------------------------------------
    # 파일명이 태그 간에 겹친다(같은 원본의 사본이라 이름이 같다). 파일명만으로
    # 키를 잡으면 마지막 태그의 비트레이트가 나머지를 덮어써서 3·4절이 통째로 틀린다.
    rate = {(r["file"], r["tag"]): float(r["bitrate"]) / 8.0
            for r in inventory if r["bitrate"]}
    tag_rate = defaultdict(list)
    for (_, tg), v in rate.items():
        tag_rate[tg].append(v)
    model_rows = []
    for (net, tag, pol), g in sorted(groups.items()):
        n = int(dict(kv.split("=", 1) for kv in pol.split(","))["N"])
        T = float(dict(kv.split("=", 1) for kv in pol.split(","))["T"])
        sel = [r for r in sessions
               if (r["network"], r["file_tag"], r["policy"]) == (net, tag, pol)]
        watch_model = sum(float(r["watch_seconds"]) * rate.get((r["file"], r["file_tag"]), 0)
                          for r in sel)
        swipes = sum(1 for r in sel if r["watched"] == "1")
        # 프리페치 항의 "평균 비트레이트"는 그 화질의 평균이다 — 전체 평균을 쓰면
        # 480p 예측에 원본 비트레이트가 섞인다.
        mean_rate = statistics.fmean(tag_rate[tag]) if tag_rate.get(tag) else 0
        pre_model = swipes * n * T * mean_rate
        predicted = watch_model + pre_model
        actual = g["watch"] + g["pre"]
        err = (actual - predicted) / predicted if predicted else float("inf")
        model_rows.append([net, tag, pol, mb(predicted / len(g["runs"])),
                           mb(actual / len(g["runs"])), f"{err * 100:+.1f}%",
                           "OK" if abs(err) <= 0.20 else "±20% 초과"])
    L += ["## 3. 트래픽 모델 대비 오차", "",
          "모델: `Σ(시청초 × 비트레이트/8) + 스와이프수 × N × T × 평균비트레이트/8`", "",
          "설계문서의 트래픽 추정식이다. 목표 ±20%.", "",
          md_table(["네트워크", "화질", "정책", "모델(MB)", "실측(MB)", "오차", "판정"], model_rows), ""]

    if any(r[5].startswith("-") for r in model_rows):
        L += ["> 모델이 과대추정으로 치우치면 대개 계측 버그가 아니라 식 자체 때문이다.",
              "> 식은 프리페치 바이트를 시청 바이트 위에 그대로 더하는데, 실제로는",
              "> 시청까지 이어진 아이템의 프리페치분이 시청 바이트에 이미 포함돼 있다",
              "> (받아둔 구간을 다시 받지 않으므로). 즉 `시청된 아이템 수 × T × 비트레이트/8`",
              "> 만큼 이중계산된다. 비용 산정에 쓰기 전에 식을 고칠지 팀에서 정한다.", ""]

    # ---- 4. 비트레이트 구간별 분해 --------------------------------------
    # 태그별로 나눈다 — 섞으면 480p 전체가 "낮음", 원본 전체가 "높음"으로 몰려서
    # 화질 비교만 다시 보게 되고 "같은 화질 안에서 무거운 파일이 불리한가"를 못 본다.
    if inventory:
        sec, any_bucket = [], False
        for tg in sorted(tag_rate):
            brs = sorted(v * 8 for v in tag_rate[tg])
            if len(brs) < 3 or brs[-1] - brs[0] < 0.05 * statistics.median(brs):
                sec.append(f"- `{tg}`: 비트레이트 편차가 5% 미만 "
                           f"({brs[0]/1e6:.2f}~{brs[-1]/1e6:.2f}Mbps) — 분해 생략")
                continue
            lo, hi = brs[len(brs) // 3], brs[2 * len(brs) // 3]
            buckets = defaultdict(lambda: {"bytes": 0, "stalls": 0, "items": 0})
            for r in sessions:
                if r["file_tag"] != tg:
                    continue
                b = rate.get((r["file"], tg), 0) * 8
                k = "낮음" if b < lo else ("중간" if b < hi else "높음")
                buckets[k]["bytes"] += int(r["bytes_watch"]) + int(r["bytes_prefetch"])
                buckets[k]["stalls"] += int(r["stalls"])
                buckets[k]["items"] += 1
            if not buckets:
                continue
            any_bucket = True
            sec += [f"**{tg}** — 낮음 < {lo/1e6:.2f}Mbps ≤ 중간 < {hi/1e6:.2f}Mbps ≤ 높음", "",
                    md_table(["구간", "아이템", "총 바이트(MB)", "스톨"],
                             [[k, v["items"], mb(v["bytes"]), v["stalls"]]
                              for k, v in sorted(buckets.items())]), ""]
        if sec:
            L += ["## 4. 파일 특성 구간별 (화질 안에서 비트레이트 3분위)", ""] + sec + [""]

    # ---- 5. 인벤토리 요약 -----------------------------------------------
    if inventory:
        by_tag = defaultdict(list)
        for r in inventory:
            by_tag[r["tag"]].append(r)
        rows = []
        for tag, items in sorted(by_tag.items()):
            brs = [float(i["bitrate"]) for i in items if i["bitrate"]]
            durs = [float(i["duration"]) for i in items if i["duration"]]
            rear = sum(1 for i in items if i["moov"] == "rear")
            rows.append([tag, len(items),
                         f"{min(durs):.0f}~{max(durs):.0f}s" if durs else "-",
                         f"{statistics.fmean(brs)/1e6:.2f}" if brs else "-",
                         f"{min(brs)/1e6:.2f}~{max(brs)/1e6:.2f}" if brs else "-",
                         f"{rear}/{len(items)}"])
        L += ["## 5. 인벤토리", "",
              md_table(["태그", "개수", "길이", "평균 비트레이트(Mbps)",
                        "비트레이트 범위", "moov 뒤"], rows), ""]

    if offsets:
        per_t = defaultdict(list)
        for r in offsets:
            per_t[(r["tag"], r["t"])].append(int(r["bytes"]))
        L += ["### 첫 T초 바이트 (중앙값)", "",
              md_table(["태그", "T(초)", "중앙값(KB)", "최대(KB)"],
                       [[tag, t, f"{statistics.median(v)/1024:.0f}", f"{max(v)/1024:.0f}"]
                        for (tag, t), v in sorted(per_t.items())]), ""]

    text = "\n".join(L)
    if args.out:
        os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text)
        print(f"report -> {args.out}", file=sys.stderr)
    print(text)

    if worst is not None and worst > RECONCILE_TOLERANCE:
        sys.exit(1)


if __name__ == "__main__":
    main()
