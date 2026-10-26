
import io, statistics
p = r"D:\code\paibanzhushou_native\shots\framestats_idle.txt"
txt = io.open(p, encoding="utf-8", errors="replace").read()
i = txt.find("---PROFILEDATA---")
lines = [l.strip() for l in txt[i:].splitlines()]
rows = []
for line in lines[2:]:
    if not line or line.startswith("---"): continue
    f = line.split(",")
    if len(f) < 22: continue
    try: v = [int(x) for x in f[:22]]
    except ValueError: continue
    rows.append(v)
MS = 1e6
data = []
for v in rows:
    iv, anim, trav, draw, sq, ss, issue, swap, comp = v[2], v[6], v[7], v[8], v[14], v[15], v[16], v[17], v[18]
    data.append(((comp-iv)/MS, (trav-anim)/MS, (ss-draw)/MS, (swap-issue)/MS, (comp-swap)/MS, v[11]/1e6))
print("IDLE frames:", len(data))
print("total p50 %.1f p90 %.1f max %.1f" % (statistics.median([d[0] for d in data]), sorted(d[0] for d in data)[int(len(data)*0.9)], max(d[0] for d in data)))
print("trav p50 %.2f  sync p50 %.2f  swap p50 %.2f" % (statistics.median([d[1] for d in data]), statistics.median([d[2] for d in data]), statistics.median([d[3] for d in data])))
print("gpu p50 %.2f p90 %.2f" % (statistics.median([d[4] for d in data]), sorted(d[4] for d in data)[int(len(data)*0.9)]))
print("interval", sorted(set(round(d[5],1) for d in data))[:6])
