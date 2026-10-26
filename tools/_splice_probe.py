
import io, re, sys
p = r"D:\code\paibanzhushou_native\app\src\main\java\com\bilu\paibanzhushou\ui\CalendarSection.kt"
src = open(p, encoding="utf-8").read()
lines = src.split("\n")
# 1-based line numbers from earlier reads: 196..403 replaced
start = 196
end = 403
old = "\n".join(lines[start-1:end])
print("HEAD:", lines[start-1])
print("TAIL:", lines[end-1])
print("LEN", len(old))
