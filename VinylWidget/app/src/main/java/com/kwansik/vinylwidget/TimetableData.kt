package com.kwansik.vinylwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 타임테이블 일정 하나. day 1=월 … 7=일, start·end = 하루 중 분(예: 15:00 → 900).
 * go·back = 갈 때·올 때 픽업 (비워 두면 표시 안 함)
 */
data class TtEvent(
    var person: Int, var day: Int, var name: String, var start: Int, var end: Int, var color: Int,
    var go: String = "", var back: String = ""
)

/** 사람 2명까지. 이름·메모는 비워 둬도 됨(그러면 표시 안 함) */
class TtData(val names: Array<String>, val memos: Array<String>, val events: MutableList<TtEvent>, var second: Boolean) {
    fun of(person: Int, day: Int) = events.filter { it.person == person && it.day == day }.sortedBy { it.start }
    fun any(person: Int) = events.any { it.person == person }
}

/** 타임테이블 일정 저장 (모든 타임테이블 위젯이 같은 일정을 씀) */
object Timetable {
    const val H0 = 8 * 60
    const val H1 = 20 * 60
    val DAYS = arrayOf("월", "화", "수", "목", "금", "토", "일")
    val COLORS = intArrayOf(
        0xFF9FB3C8.toInt(), 0xFFFF9EC7.toInt(), 0xFF7AA7FF.toInt(), 0xFFFFB86B.toInt(), 0xFFB79CFF.toInt(),
        0xFF5BC8D8.toInt(), 0xFF6FD3A0.toInt(), 0xFFFFD84D.toInt(), 0xFFFF8A80.toInt(), 0xFFA8D86B.toInt()
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("timetable", Context.MODE_PRIVATE)

    fun load(ctx: Context): TtData {
        val raw = sp(ctx).getString("data", null)
        val names = arrayOf("", ""); val memos = arrayOf("", "")
        val events = ArrayList<TtEvent>()
        var second = false
        if (raw != null) try {
            val o = JSONObject(raw)
            o.optJSONArray("names")?.let { a -> for (i in 0..1) names[i] = a.optString(i, "") }
            o.optJSONArray("memos")?.let { a -> for (i in 0..1) memos[i] = a.optString(i, "") }
            second = o.optBoolean("second", false)
            o.optJSONArray("events")?.let { a ->
                for (i in 0 until a.length()) {
                    val e = a.getJSONObject(i)
                    events += TtEvent(e.optInt("p"), e.optInt("d"), e.optString("n"), e.optInt("s"), e.optInt("e"),
                        e.optInt("c", COLORS[0]), e.optString("g"), e.optString("b"))
                }
            }
        } catch (e: Exception) { /* 깨진 값은 무시하고 빈 일정 */ }
        return TtData(names, memos, events, second)
    }

    fun save(ctx: Context, d: TtData) {
        val o = JSONObject()
        o.put("names", JSONArray(d.names.toList()))
        o.put("memos", JSONArray(d.memos.toList()))
        o.put("second", d.second)
        o.put("events", JSONArray().also { a ->
            d.events.forEach { e ->
                a.put(JSONObject().put("p", e.person).put("d", e.day).put("n", e.name).put("s", e.start).put("e", e.end)
                    .put("c", e.color).put("g", e.go).put("b", e.back))
            }
        })
        sp(ctx).edit().putString("data", o.toString()).apply()
    }

    /** 15:00 → "3:00" (오전 8시~오후 8시라 오전·오후 없이) */
    fun hm(min: Int): String {
        val h = min / 60; val m = min % 60
        return "%d:%02d".format(if (h > 12) h - 12 else h, m)
    }

    /** 픽업 문구 앞 아이콘: 셔틀·버스면 🚌, 아니면 🚗 */
    fun pickIcon(s: String) = if (s.contains("셔틀") || s.contains("버스")) "🚌" else "🚗"
}
