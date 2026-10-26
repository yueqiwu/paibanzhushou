
import re, datetime
src = open(r"D:\code\paibanzhushou_native\app\src\main\java\com\bilu\paibanzhushou\core\LunarData.kt", encoding="utf8").read()
lunar = [int(x,16) for x in re.findall(r"0x[0-9a-fA-F]+", src.split("sTermInfo")[0])]
def leapMonth(y): return lunar[y-1900] & 0xf
def leapDays(y):
    if leapMonth(y): return 30 if (lunar[y-1900] & 0x10000) else 29
    return 0
def monthDays(y,m): return 30 if (lunar[y-1900] & (0x10000 >> m)) else 29
def lYearDays(y):
    s = 348
    i = 0x8000
    while i > 0x8:
        if lunar[y-1900] & i: s += 1
        i >>= 1
    return s + leapDays(y)
bad = [(1900+i, lYearDays(1900+i)) for i in range(len(lunar)) if not (350 <= lYearDays(1900+i) <= 390)]
print("odd years:", bad[:20], "count", len(bad))
tot = 0
for y in range(1900, 2026): tot += lYearDays(y)
print("sum 1900..2025 =", tot, "-> date", (datetime.date(1900,1,31)+datetime.timedelta(days=tot)).isoformat())
print("2026 lYearDays", lYearDays(2026), "months:", [monthDays(2026,m) for m in range(1,13)], "leap", leapMonth(2026))
