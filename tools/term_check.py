
import re
src = open(r"D:\code\paibanzhushou_native\app\src\main\java\com\bilu\paibanzhushou\core\LunarData.kt", encoding="utf8").read()
seg = src.split("sTermInfo")[1]
entries = re.findall(r'"([0-9a-fA-F]+)"', seg)
print("entries", len(entries), "len of first", len(entries[0]))
table = entries[126]
print("2026 table", table, len(table))
for i in range(6):
    chunk = table[i*5:i*5+5]
    dec = str(int(chunk,16))
    print(i, chunk, dec, "->", dec[0], dec[1:3], dec[3], dec[4:6])
