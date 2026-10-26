
import re, datetime
src = open(r"D:\code\paibanzhushou_native\app\src\main\java\com\bilu\paibanzhushou\core\LunarData.kt", encoding="utf8").read()
lunar = [int(x,16) for x in re.findall(r"0x[0-9a-fA-F]+", src.split("sTermInfo")[0])]
print("table len", len(lunar), "first", hex(lunar[0]), "2026:", hex(lunar[126]))

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

def utcDays(y,m,d):
    yy = y-1 if m <= 2 else y
    era = (yy if yy >= 0 else yy-399)//400
    yoe = yy - era*400
    mp = (m+11)%12
    doy = (153*mp+2)//5 + d - 1
    doe = yoe*365 + yoe//4 - yoe//100 + doy
    return era*146097 + doe - 719468

base = utcDays(1900,1,31)
for (y,m,d) in [(1900,1,31),(2026,9,25),(2026,10,1),(2026,10,11),(2026,10,26),(2026,10,28)]:
    off = utcDays(y,m,d) - base
    i = 1900; temp = 0
    while i < 2101 and off > 0:
        temp = lYearDays(i); off -= temp; i += 1
    if off < 0:
        off += temp; i -= 1
    ly = i; leap = leapMonth(ly); isLeap = False
    i = 1
    while i < 13 and off > 0:
        if leap > 0 and i == leap+1 and not isLeap:
            i -= 1; isLeap = True; temp = leapDays(ly)
        else:
            temp = monthDays(ly, i)
        if isLeap and i == leap+1: isLeap = False
        off -= temp; i += 1
    if off == 0 and leap > 0 and i == leap+1:
        if isLeap: isLeap = False
        else: isLeap = True; i -= 1
    if off < 0:
        off += temp; i -= 1
    print(y,m,d,"->",ly,"leap" if isLeap else "", i, off+1, "days_since_base", utcDays(y,m,d)-base)
