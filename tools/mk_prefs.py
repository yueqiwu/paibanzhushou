
import json, io, os
people = [
  {"id":"import_1","name":"吴悦琦","anchorDate":"2026-01-10","cycleBans":["休息","休息","值班","中班"],"overrides":{},"weekendRestBans":[],"holidayRestBans":[]},
  {"id":"import_2","name":"顾焱焱","anchorDate":"2026-01-10","cycleBans":["中班","休息","休息","值班"],"overrides":{},"weekendRestBans":[],"holidayRestBans":[]},
  {"id":"import_3","name":"姚启晨","anchorDate":"2026-05-27","cycleBans":["中班","休息","休息","值班"],"overrides":{},"weekendRestBans":[],"holidayRestBans":[]},
  {"id":"import_4","name":"王一丹","anchorDate":"2026-01-10","cycleBans":["休息","值班","中班","休息"],"overrides":{},"weekendRestBans":[],"holidayRestBans":["中班"]},
  {"id":"import_5","name":"罗鋆烨","anchorDate":"2026-09-14","cycleBans":["大夜班","休息","白班","白班","值班","休息","白班","白班","值班","休息","白班","白班"],"overrides":{},"weekendRestBans":["白班"],"holidayRestBans":["白班"]},
]
def esc(s):
    return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
def dump(o):
    return esc(json.dumps(o, ensure_ascii=False, separators=(",",":")))
people_json = dump(people)
opts_json = dump(["白班","中班","值班","休息","大夜班"])
xml = ("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n"
  '    <string name="paiban_people_v1">' + people_json + "</string>\n"
  '    <string name="paiban_active_person_v1">import_1</string>\n'
  '    <string name="PAIBAN_OPTIONS_KEY">' + opts_json + "</string>\n"
  '    <string name="paiban_options_version_v1">2</string>\n'
  "</map>\n")
open(r"D:\code\paibanzhushou_native\shots\paiban_store.xml","w",encoding="utf-8",newline="\n").write(xml)
print("written", len(xml))
# predicted schedule sanity check
import datetime
def ymd(s): 
    y,m,d = s.split("-"); return datetime.date(int(y),int(m),int(d))
for p in people:
    a = ymd(p["anchorDate"]); cyc = p["cycleBans"]
    out=[]
    for d in range(1,32):
        dt = datetime.date(2026,10,d); off=(dt-a).days; out.append(cyc[off%len(cyc)])
    print(p["name"], "10月:", " ".join(out))
