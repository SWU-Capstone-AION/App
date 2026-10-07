"""
탭이 계산한 심박 위험도를, PC에 저장된 기록(data/<기기>_calc_YYYYMMDD.csv)만으로 처음부터 다시 계산해 비교한다.
탭의 계산을 믿지 않고 원본 심박에서 직접 M·S·위험도를 구해, 탭이 기록한 값과 한 줄씩 맞춰 본다.

실행 (watch-hr 폴더에서):
    python tools/verify_risk.py data/SM-X610_calc_20261005.csv
    python tools/verify_risk.py data/SM-X610_calc_20261005.csv 10:58:30   # 그 시각 근처 한 줄을 풀어서 보여 줌

공식 (설계서 3장):
    M = 기준선 심박의 중앙값
    S = max(MAD × 1.4826, 3)
    위험도 = clamp((최근 5초 평균 심박 − M) ÷ (2 × S), 0, 1)
기준선: 보류(90초)를 넘긴 조용한 구간 값만, 최근 10분 (최소 60개, 최대 1200개). 60개 + 2분 이상 모여야 준비.
"""
import csv
import math
import statistics
import sys
from collections import deque
from datetime import datetime

WINDOW_MS, MIN_SAMPLES, MAX_SAMPLES, MIN_SPAN_MS = 600_000, 60, 1200, 120_000
S_FLOOR, MAD_TO_SD, AVG_MS, STALE_MS = 3.0, 1.4826, 5_000, 20_000


def ms(text):
    return int(datetime.strptime(text, "%Y-%m-%d %H:%M:%S.%f").timestamp() * 1000)


def num(text):
    return float(text) if text not in ("", None) else None


def main(path, focus=None, quiet=False):
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f))

    bpm_at = {r["잰시각"]: int(r["심박"]) for r in rows if r["필터"] == "정상"}
    baseline = deque()          # (잰시각 ms, bpm)
    recent = deque()            # 5초 평균용
    latest_at = None
    checked = same_m = same_s = same_risk = 0
    worst = 0.0
    shown = False

    for r in rows:
        at = ms(r["잰시각"])
        now = ms(r["받은시각"])
        # 1) 이번에 기준선에 들어간 값: 앱이 적은 범위의 "보류" 값들을 원본에서 직접 찾아 넣는다
        commit = r["이번에_기준선에_넣은_값(잰시각)"]
        if commit:
            first, last = commit.split("개 ", 1)[1].split("~")
            for t_text, b in sorted((t, b) for t, b in bpm_at.items() if first <= t <= last):
                t = ms(t_text)
                if baseline and baseline[-1][0] >= t:
                    continue
                baseline.append((t, b))
                while len(baseline) > MAX_SAMPLES or (len(baseline) > MIN_SAMPLES and t - baseline[0][0] > WINDOW_MS):
                    baseline.popleft()
        # 2) 최근 5초 평균
        if r["필터"] == "정상":
            recent.append((at, int(r["심박"])))
            while recent and at - recent[0][0] >= AVG_MS:
                recent.popleft()
            latest_at = at
        # 앱(Kotlin Math.round)과 같은 반올림: .5는 올린다 (파이썬 round는 .5를 짝수 쪽으로 보냄)
        avg = math.floor(statistics.fmean(b for _, b in recent) + 0.5) if recent else None

        # 3) M, S, 위험도
        values = [b for _, b in baseline]
        if values:
            m = statistics.median(values)
            mad = statistics.median(abs(v - m) for v in values)
            s = max(mad * MAD_TO_SD, S_FLOOR)
        else:
            m = s = None
        span = baseline[-1][0] - baseline[0][0] if baseline else 0
        ready = len(values) >= MIN_SAMPLES and span >= MIN_SPAN_MS
        risk = None
        if ready and avg is not None and latest_at is not None and now - latest_at <= STALE_MS:
            risk = min(max((avg - m) / (2 * s), 0.0), 1.0)

        # 4) 앱이 기록한 값과 비교
        app_m, app_s, app_risk = num(r["M"]), num(r["S"]), num(r["위험도"])
        checked += 1
        same_m += (app_m is None and m is None) or (app_m is not None and m is not None and abs(app_m - m) < 0.001)
        same_s += (app_s is None and s is None) or (app_s is not None and s is not None and abs(app_s - s) < 0.001)
        if app_risk is None and risk is None:
            same_risk += 1
        elif app_risk is not None and risk is not None:
            worst = max(worst, abs(app_risk - risk))
            same_risk += abs(app_risk - risk) < 0.001

        if focus and not shown and r["받은시각"][11:19] >= focus and risk is not None:
            shown = True
            print(f"\n■ {r['받은시각']} 한 줄 풀어 보기")
            print(f"  기준선 값 {len(values)}개 ({datetime.fromtimestamp(baseline[0][0]/1000):%H:%M:%S}"
                  f" ~ {datetime.fromtimestamp(baseline[-1][0]/1000):%H:%M:%S}), 가장 낮은 값 {min(values)}, 가장 높은 값 {max(values)}")
            print(f"  M  = 중앙값 = {m}")
            print(f"  MAD = |값 − M| 의 중앙값 = {mad}")
            print(f"  S  = max({mad} × 1.4826, 3) = {s:.3f}")
            print(f"  최근 5초 심박 {[b for _, b in recent]} → 평균 {avg}")
            raw = (avg - m) / (2 * s)
            print(f"  위험도 = ({avg} − {m}) ÷ (2 × {s:.3f}) = {raw:.3f} → 0~1로 자르면 {risk:.3f}")
            print(f"  앱이 기록한 값: M {r['M']}, S {r['S']}, 5초평균 {r['5초평균']}, 위험도 {r['위험도']}")

    result = {"rows": checked, "m": same_m, "s": same_s, "risk": same_risk, "worst": worst,
              "first": rows[0]["받은시각"] if rows else "", "last": rows[-1]["받은시각"] if rows else ""}
    if quiet:
        return result
    print(f"\n■ 전체 비교 ({checked}줄)")
    print(f"  M 일치      {same_m}/{checked}")
    print(f"  S 일치      {same_s}/{checked}")
    print(f"  위험도 일치 {same_risk}/{checked}  (가장 큰 차이 {worst:.4f})")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
