
import io
p = r"D:\code\paibanzhushou_native\shots\framestats.txt"
txt = io.open(p, encoding="utf-8", errors="replace").read()
i = txt.find("---PROFILEDATA---")
lines = [l.strip() for l in txt[i:].splitlines()]
rows = []
for line in lines[2:]:
    if not line or line.startswith("---"):
        continue
    f = line.split(",")
    if len(f) < 22: continue
    try:
        v = [int(x) for x in f[:22]]
    except ValueError:
        continue
    rows.append(v)
print("frames:", len(rows))
MS = 1e6
data=[]
for v in rows:
    flags, tid, iv, vs, ie, hi, anim, trav, draw, dl, fst, fi, wt, at, sq, ss, issue, swap, comp = v[:19]
    data.append(dict(
        total=(comp-iv)/MS,
        input=(hi-iv)/MS,
        anim=(anim-iv)/MS,
        trav=(trav-anim)/MS,
        draw=(draw-trav)/MS,
        sync=(ss-draw)/MS,
        issue=(issue-ss)/MS,
        swap=(swap-issue)/MS,
        gpu=(comp-swap)/MS,
        interval=fi/1e6,
        lateness=(comp-dl)/MS,
    ))
data.sort(key=lambda d: -d["total"])
print(f"{'total':>7} {'input':>6} {'anim':>6} {'trav':>6} {'draw':>6} {'sync':>6} {'issue':>6} {'swap':>6} {'gpu':>6} {'interval':>8}")
for d in data[:12]:
    print(f"{d['total']:7.1f} {d['input']:6.1f} {d['anim']:6.1f} {d['trav']:6.1f} {d['draw']:6.1f} {d['sync']:6.1f} {d['issue']:6.1f} {d['swap']:6.1f} {d['gpu']:6.1f} {d['interval']:8.2f}")
import statistics
for k in ("total","trav","draw","sync","issue","swap","gpu"):
    vals=[d[k] for d in data]
    print(k, "p50", round(statistics.median(vals),1), "p90", round(sorted(vals)[int(len(vals)*0.9)],1), "max", round(max(vals),1))
print("interval set:", sorted(set(round(d["interval"],1) for d in data))[:10])
