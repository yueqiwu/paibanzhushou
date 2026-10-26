package com.bilu.paibanzhushou.data

import com.bilu.paibanzhushou.core.BanColors
import com.bilu.paibanzhushou.core.Schedule
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/** 人员排班配置 */
data class Person(
    val id: String,
    val name: String,
    val anchorDate: String,
    val cycleBans: List<String>,
    val overrides: Map<String, String>,
    val weekendRestBans: List<String>,
    val holidayRestBans: List<String>
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("id", id)
        o.put("name", name)
        o.put("anchorDate", anchorDate)
        o.put("cycleBans", JSONArray(cycleBans))
        val ov = JSONObject()
        overrides.forEach { (k, v) -> ov.put(k, v) }
        o.put("overrides", ov)
        o.put("weekendRestBans", JSONArray(weekendRestBans))
        o.put("holidayRestBans", JSONArray(holidayRestBans))
        return o
    }
}

/** 移植自 utils/people-store.js */
object PeopleStore {
    const val PEOPLE_KEY = "paiban_people_v1"
    const val ACTIVE_PERSON_KEY = "paiban_active_person_v1"
    const val LEGACY_CONFIG_KEY = "paiban_config_v1"

    private var lastSaved: String? = null

    fun createPersonId(): String =
        System.currentTimeMillis().toString() + "_" + Random.nextLong(1L, Long.MAX_VALUE).toString(16)

    private fun JSONArray.toStringList(): List<String> {
        val out = ArrayList<String>(length())
        for (i in 0 until length()) {
            out.add(optString(i, ""))
        }
        return out
    }

    private fun optList(o: JSONObject, key: String): List<String>? {
        val arr = o.optJSONArray(key) ?: return null
        return arr.toStringList()
    }

    private fun optMap(o: JSONObject, key: String): Map<String, String>? {
        val jo = o.optJSONObject(key) ?: return null
        val out = LinkedHashMap<String, String>()
        val it = jo.keys()
        while (it.hasNext()) {
            val k = it.next()
            out[k] = jo.optString(k, "")
        }
        return out
    }

    fun normalize(person: Person?, fallbackAnchorDate: String, fallbackCycle: List<String>): Person {
        val cycle = when {
            !person?.cycleBans.isNullOrEmpty() -> person!!.cycleBans
            fallbackCycle.isNotEmpty() -> fallbackCycle
            else -> listOf(Schedule.REST_BAN)
        }
        val name = person?.name?.takeIf { it.isNotEmpty() } ?: "未命名"
        val anchor = person?.anchorDate?.takeIf { it.isNotEmpty() } ?: fallbackAnchorDate
        return Person(
            id = person?.id?.takeIf { it.isNotEmpty() } ?: createPersonId(),
            name = name,
            anchorDate = anchor,
            cycleBans = cycle.toList(),
            overrides = person?.overrides ?: emptyMap(),
            weekendRestBans = person?.weekendRestBans ?: emptyList(),
            holidayRestBans = person?.holidayRestBans ?: emptyList()
        )
    }

    private fun parsePerson(o: JSONObject): Person = Person(
        id = o.optString("id", ""),
        name = o.optString("name", ""),
        anchorDate = o.optString("anchorDate", ""),
        cycleBans = optList(o, "cycleBans") ?: emptyList(),
        overrides = optMap(o, "overrides") ?: emptyMap(),
        weekendRestBans = optList(o, "weekendRestBans") ?: emptyList(),
        holidayRestBans = optList(o, "holidayRestBans") ?: emptyList()
    )

    fun loadPeople(fallbackAnchorDate: String, fallbackCycle: List<String>): List<Person> {
        val raw = Store.readStringOrNull(PEOPLE_KEY)
        val arr = raw?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (arr != null && arr.length() > 0) {
            val out = ArrayList<Person>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(normalize(parsePerson(o), fallbackAnchorDate, fallbackCycle))
            }
            if (out.isNotEmpty()) {
                lastSaved = raw
                return out
            }
        }

        val legacyRaw = Store.readStringOrNull(LEGACY_CONFIG_KEY)
        val legacy = legacyRaw?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (legacy != null) {
            val p = Person(
                id = "",
                name = "默认人员",
                anchorDate = legacy.optString("anchorDate", ""),
                cycleBans = optList(legacy, "cycleBans") ?: emptyList(),
                overrides = emptyMap(),
                weekendRestBans = emptyList(),
                holidayRestBans = emptyList()
            )
            return listOf(normalize(p, fallbackAnchorDate, fallbackCycle))
        }

        val p = Person(
            id = "",
            name = "默认人员",
            anchorDate = fallbackAnchorDate,
            cycleBans = fallbackCycle,
            overrides = emptyMap(),
            weekendRestBans = emptyList(),
            holidayRestBans = emptyList()
        )
        return listOf(normalize(p, fallbackAnchorDate, fallbackCycle))
    }

    fun savePeople(people: List<Person>) {
        val arr = JSONArray()
        people.forEach { arr.put(it.toJson()) }
        val text = arr.toString()
        if (text == lastSaved) return
        Store.writeRaw(PEOPLE_KEY, text)
        lastSaved = text
    }

    fun loadActivePersonId(): String = Store.readRaw(ACTIVE_PERSON_KEY, "")

    fun saveActivePersonId(id: String) {
        Store.writeRaw(ACTIVE_PERSON_KEY, id)
    }

    fun personColor(person: Person): String = BanColors.get(person.name)
}
