
import re
p = r"D:\code\paibanzhushou_native\app\src\main\java\com\bilu\paibanzhushou\ui\CalendarSection.kt"
new = open(r"D:\code\paibanzhushou_native\tools\_cal_new.kt", encoding="utf-8").read().rstrip("\n")
consts = '''/** 日历一行高度（源 wu-calendar itemHeight = 70px，1:1 用 dp） */
private val CAL_ROW_HEIGHT = 70.dp

/** 虚拟无限 Pager：中间页 + 总页数（页索引保持在数千量级，避免超长偏移的精度问题） */
private const val PAGER_MIDDLE = 6000
private const val PAGER_COUNT = 12001

'''
src = open(p, encoding="utf-8").read()
lines = src.split("\n")
assert lines[195] == "@Composable", lines[195]
assert lines[196] == "private fun CalendarGrid(state: IndexState) {", lines[196]
assert lines[402].rstrip() == "    }", lines[402]
out = lines[:195] + (consts + new).split("\n") + lines[403:]
open(p, "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("ok", len(out))
