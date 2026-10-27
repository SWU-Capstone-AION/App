#!/usr/bin/env python3
"""
AION 규칙모델 v3 — w·τ 자동 조정 도구

앱 모니터링 화면의 '기록 저장'으로 만든 CSV(0.5초마다 한 줄, label 칸 포함)를 읽어서
  1) 규칙모델·학습모델 점수를 확률로 보정하고 (Platt 보정)
  2) 여러 w(학습모델 비중) · τ(기준값) · 지속 시간 조합을 앱과 똑같은 방식으로 다시 계산해
  3) "상동행동은 잘 잡고, 평소에는 위험이 안 뜨는" 조합을 골라
  4) app/src/main/assets/rule_v3_config.json 으로 저장한다.

쓰는 법 (파이썬 3.8 이상, 추가 설치 없음)
  python tools/rule_v3_tune.py 기록1.csv 기록2.csv ...
  python tools/rule_v3_tune.py records/*.csv --max-false 1 --out app/src/main/assets/rule_v3_config.json

라벨을 앱에서 누르지 않고 따로 적어 두었다면 (--labels)
  라벨 파일: CSV, 칸 = file,start_sec,end_sec   (file 은 기록 파일 이름, 시각은 기록의 t_sec 기준)
  python tools/rule_v3_tune.py 기록1.csv --labels labels.csv
"""
import argparse
import csv
import glob
import json
import math
import os
import sys

# ---------------- 앱과 같은 값 (RuleV3Config.kt / RuleV3Engine.kt) ----------------
STRIDE_S = 0.5
DUR_SOFTNESS = 0.05
ENTER_LEVEL = 0.999
EXIT_LEVEL = 0.5
MERGE_GAP_S = 5.0
GRACE_S = 3.0          # 라벨이 끝난 뒤 이 시간 안에 뜬 위험도 '맞춤'으로 친다 (반응이 늦게 오므로)


def sigmoid(z):
    if z < -60:
        return 0.0
    if z > 60:
        return 1.0
    return 1.0 / (1.0 + math.exp(-z))


def logit(p):
    q = min(1 - 1e-6, max(1e-6, p))
    return math.log(q / (1 - q))


def fnum(s):
    try:
        v = float(s)
        return v if math.isfinite(v) else None
    except (TypeError, ValueError):
        return None


# ---------------- 읽기 ----------------
def read_records(paths, label_file=None):
    intervals = {}
    if label_file:
        with open(label_file, encoding="utf-8-sig") as f:
            for row in csv.DictReader(f):
                intervals.setdefault(os.path.basename(row["file"]), []).append(
                    (float(row["start_sec"]), float(row["end_sec"])))
    sessions = []
    for p in paths:
        rows = []
        with open(p, encoding="utf-8-sig") as f:
            for r in csv.DictReader(f):
                t = fnum(r.get("t_sec"))
                if t is None:
                    continue
                lab = int(fnum(r.get("label")) or 0)
                iv = intervals.get(os.path.basename(p))
                if iv is not None:
                    lab = 1 if any(a <= t <= b for a, b in iv) else 0
                rows.append({"t": t, "label": lab, "rule": fnum(r.get("p_rule_raw")),
                             "dl": fnum(r.get("p_dl_raw")), "dom": r.get("dominant", "")})
        rows.sort(key=lambda x: x["t"])
        if rows:
            sessions.append((os.path.basename(p), rows))
    return sessions


# ---------------- 1) 보정 (Platt: p' = sigmoid(a × logit(p) + b)) ----------------
def fit_platt(xs, ys, iters=100):
    """뉴턴법 로지스틱 회귀 (특징 1개 + 절편). 데이터가 너무 적거나 한쪽 라벨뿐이면 None.
    점수가 라벨과 완전히 갈리면 a 가 끝없이 커지므로, (a=1, b=0 쪽으로) 약하게 당기고 범위를 제한한다."""
    if len(xs) < 40 or sum(ys) < 10 or len(ys) - sum(ys) < 10:
        return None
    z = [logit(x) for x in xs]
    a, b = 1.0, 0.0
    lam = 1.0
    for _ in range(iters):
        ga, gb = lam * (a - 1), lam * b
        haa, hab, hbb = lam, 0.0, lam
        for zi, yi in zip(z, ys):
            p = sigmoid(a * zi + b)
            g = p - yi
            ga += g * zi
            gb += g
            w = p * (1 - p)
            haa += w * zi * zi
            hab += w * zi
            hbb += w
        det = haa * hbb - hab * hab
        if abs(det) < 1e-12:
            break
        da = (hbb * ga - hab * gb) / det
        db = (haa * gb - hab * ga) / det
        step = max(1.0, abs(da) / 2, abs(db) / 2)   # 한 번에 너무 크게 움직이지 않게
        a = min(10.0, max(0.1, a - da / step))
        b = min(15.0, max(-15.0, b - db / step))
        if abs(da) < 1e-6 and abs(db) < 1e-6:
            break
    return {"type": "platt", "a": round(a, 4), "b": round(b, 4)}


def apply_cal(cal, x):
    if cal is None or x is None:
        return x
    return sigmoid(cal["a"] * logit(x) + cal["b"])


# ---------------- 2) 앱과 같은 방식으로 다시 계산 ----------------
def episodes_of(rows):
    """라벨 = 1 이 이어진 구간들 [(시작, 끝)]"""
    eps, start, last = [], None, None
    for r in rows:
        if r["label"] and start is None:
            start = r["t"]
        if not r["label"] and start is not None:
            eps.append((start, last))
            start = None
        last = r["t"]
    if start is not None:
        eps.append((start, last))
    return eps


def replay(rows, w, tau, dur_s, decay, rcal, dcal, confident=None):
    """fuseTick + updateAlert 그대로. 위험이 새로 시작된 시각들을 돌려준다
    confident: 한 모델이 이 값 이상이면 그 점수를 그대로 씀 (v3.5, None = 안 씀)"""
    acc, last_t = 0.0, None
    in_ep, below = False, None
    alarms, ds = [], []
    for r in rows:
        pr = apply_cal(rcal, r["rule"])
        pd = apply_cal(dcal, r["dl"])
        if pr is not None and pd is not None:
            p = w * pd + (1 - w) * pr
            if confident is not None:
                if pr >= confident and pr > p:
                    p = pr
                if pd >= confident and pd > p:
                    p = pd
        elif pd is not None:
            p = pd
        else:
            p = pr
        dt = 0.0 if last_t is None else min(STRIDE_S * 3, max(0.0, r["t"] - last_t))
        last_t = r["t"]
        x = 0.0 if p is None else sigmoid((p - tau) / DUR_SOFTNESS)
        acc = min(dur_s, max(0.0, acc + dt * (x - (1 - x) * decay)))
        d = acc / dur_s
        ds.append(d)
        if not in_ep and d >= ENTER_LEVEL:
            in_ep, below = True, None
            alarms.append(r["t"])
        elif in_ep:
            if d < EXIT_LEVEL:
                below = r["t"] if below is None else below
                if r["t"] - below >= MERGE_GAP_S:
                    in_ep = False
            else:
                below = None
    return alarms, ds


def evaluate(sessions, w, tau, dur_s, decay, rcal, dcal, confident=None):
    n_ep = hit = 0
    delays = []
    false_alarms = 0
    normal_s = 0.0
    near = 0.0
    for _, rows in sessions:
        eps = episodes_of(rows)
        alarms, ds = replay(rows, w, tau, dur_s, decay, rcal, dcal, confident)
        # 평소 구간(라벨 끝 + 10초 이후)에서 지속 시간이 얼마나 차올랐나 = 오탐에 얼마나 가까웠나
        for r, d in zip(rows, ds):
            if not r["label"] and not any(a <= r["t"] <= b + 10 for a, b in eps):
                near = max(near, d)
        n_ep += len(eps)
        for a, b in eps:
            got = [t for t in alarms if a <= t <= b + GRACE_S]
            if got:
                hit += 1
                delays.append(got[0] - a)
        for t in alarms:
            if not any(a <= t <= b + GRACE_S for a, b in eps):
                false_alarms += 1
        normal_s += STRIDE_S * sum(1 for r in rows if not r["label"])
    per10 = false_alarms / max(normal_s / 600.0, 1e-9) if normal_s > 0 else 0.0
    return {
        "w": w, "tau": tau, "dur": dur_s, "decay": decay,
        "episodes": n_ep, "hit": hit, "recall": hit / n_ep if n_ep else 0.0,
        "delay": sum(delays) / len(delays) if delays else None,
        "false": false_alarms, "false_per10min": per10, "near": near,
    }


def frange(a, b, step):
    out, x = [], a
    while x <= b + 1e-9:
        out.append(round(x, 3))
        x += step
    return out


def main():
    ap = argparse.ArgumentParser(description="규칙모델 v3 w·τ 자동 조정")
    ap.add_argument("files", nargs="+", help="앱에서 저장한 기록 CSV (여러 개 가능, *.csv 가능)")
    ap.add_argument("--labels", help="라벨 구간 파일 (file,start_sec,end_sec)")
    ap.add_argument("--max-false", type=float, default=1.0,
                    help="평소 10분에 허용하는 오탐(위험) 횟수 (기본 1)")
    ap.add_argument("--no-cal", action="store_true", help="보정 없이 원래 점수로만 계산")
    ap.add_argument("--decay", type=float, default=0.75, help="반복이 끊길 때 줄어드는 속도 (기본 0.75)")
    ap.add_argument("--confident", type=float, default=0.85,
                    help="한 모델이 이 값 이상이면 그 점수를 그대로 씀 (기본 0.85, 0 이면 안 씀)")
    ap.add_argument("--out", default="app/src/main/assets/rule_v3_config.json", help="저장할 설정 파일")
    args = ap.parse_args()

    paths = []
    for f in args.files:
        paths += glob.glob(f) or [f]
    sessions = read_records(paths, args.labels)
    if not sessions:
        sys.exit("읽은 기록이 없습니다. 파일 경로를 확인하세요.")
    all_rows = [r for _, rows in sessions for r in rows]
    n_lab = sum(r["label"] for r in all_rows)
    print(f"기록 {len(sessions)}개 · {len(all_rows)}줄 ({len(all_rows) * STRIDE_S / 60:.1f}분) · "
          f"상동행동 라벨 {n_lab}줄 ({n_lab * STRIDE_S:.0f}초) · 상동행동 구간 {sum(len(episodes_of(r)) for _, r in sessions)}개")
    if n_lab == 0:
        sys.exit("label = 1 인 줄이 없습니다. 앱에서 '라벨' 버튼을 누르며 기록했는지 확인하세요.")
    has_dl = any(r["dl"] is not None for r in all_rows)

    # 1) 보정
    rcal = dcal = None
    if not args.no_cal:
        rr = [(r["rule"], r["label"]) for r in all_rows if r["rule"] is not None]
        rcal = fit_platt([x for x, _ in rr], [y for _, y in rr])
        if has_dl:
            dd = [(r["dl"], r["label"]) for r in all_rows if r["dl"] is not None]
            dcal = fit_platt([x for x, _ in dd], [y for _, y in dd])
        print(f"\n[1] 보정  규칙모델: {rcal or '데이터 부족 → 보정 안 함'}  학습모델: {dcal or ('없음' if not has_dl else '데이터 부족 → 보정 안 함')}")

    # 라벨별 평균 점수 (모델이 상동행동에 반응하는지 한눈에)
    def avg(key, lab):
        v = [r[key] for r in all_rows if r["label"] == lab and r[key] is not None]
        return sum(v) / len(v) if v else float("nan")
    print(f"    평균 점수  규칙: 평소 {avg('rule', 0):.2f} / 상동행동 {avg('rule', 1):.2f}"
          + (f"   학습: 평소 {avg('dl', 0):.2f} / 상동행동 {avg('dl', 1):.2f}" if has_dl else ""))

    # 2) 조합 전부 계산
    ws = frange(0.0, 1.0, 0.1) if has_dl else [0.0]
    taus = frange(0.30, 0.70, 0.05)
    durs = [1.5, 2.0, 2.5, 3.0, 4.0]
    conf = args.confident if args.confident and args.confident > 0 else None
    results = [evaluate(sessions, w, t, d, args.decay, rcal, dcal, conf) for w in ws for t in taus for d in durs]

    # 3) 고르기
    #   ① 오탐이 허용치 이하   ② 적중률이 높은 것   ③ 오탐 횟수가 적은 것
    #   ④ 평소에 지속 시간이 덜 차오른 것 (오탐까지 여유가 큰 것)   ⑤ 반응이 빠른 것
    ok = [r for r in results if r["false_per10min"] <= args.max_false]
    pool = ok if ok else results
    pool.sort(key=lambda r: (-round(r["recall"], 3), r["false_per10min"], round(r["near"], 1),
                             r["delay"] if r["delay"] is not None else 99))
    if not ok:
        print(f"\n⚠ 평소 10분에 오탐 {args.max_false}회 이하인 조합이 없습니다. 적중률 순으로 보여줍니다.")

    print("\n[2] 좋은 조합 상위 10개")
    print("  순위   w    τ    지속  | 적중(잡은 구간/전체)   평균 걸린 시간 | 오탐 횟수 (평소 10분당) | 오탐 여유")
    for i, r in enumerate(pool[:10], 1):
        delay = f"{r['delay']:.1f}초" if r["delay"] is not None else "  -  "
        print(f"  {i:>2}   {r['w']:.1f}  {r['tau']:.2f}  {r['dur']:.1f}초 | "
              f"{r['recall'] * 100:5.1f}% ({r['hit']}/{r['episodes']})        {delay:>6}   | "
              f"{r['false']}회 ({r['false_per10min']:.2f})          | 평소 최고 {r['near'] * 100:.0f}%")

    # 지금 앱 기본값과 비교
    for name, (w, t, d, dec, cf) in {"보통(기준서)": (0.5, 0.5, 4.0, 1.5, None),
                                      "민감(현재 기본)": (0.3, 0.45, 2.5, 0.75, 0.85)}.items():
        r = evaluate(sessions, w, t, d, dec, None, None, cf)
        delay = f"{r['delay']:.1f}초" if r["delay"] is not None else "-"
        print(f"  비교 {name}: 적중 {r['recall'] * 100:.1f}% · 걸린 시간 {delay} · 오탐 {r['false']}회 ({r['false_per10min']:.2f}/10분) · 평소 최고 {r['near'] * 100:.0f}%")

    best = pool[0]
    cfg = {
        "w": best["w"], "decision": best["tau"], "duration_s": best["dur"], "decay_rate": args.decay,
        "confident": conf,
        "_note": f"rule_v3_tune.py 결과 · 기록 {len(sessions)}개 · 적중 {best['recall'] * 100:.1f}% · "
                 f"오탐 {best['false_per10min']:.2f}/10분",
    }
    if rcal:
        cfg["rule_calibration"] = rcal
    if dcal:
        cfg["dl_calibration"] = dcal
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)
    print(f"\n[3] 1위 조합을 저장했습니다 → {args.out}")
    print(json.dumps(cfg, ensure_ascii=False, indent=2))
    print("\n앱을 다시 빌드하면 이 값으로 판정합니다. (모니터링 화면 민감도 버튼에 '사용자 지정'으로 표시)")


if __name__ == "__main__":
    main()
