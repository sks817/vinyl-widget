package com.kwansik.vinylwidget

import android.content.Context
import org.json.JSONObject
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** code = WMO 날씨 코드(Open-Meteo 표준) */
data class WeatherData(
    val temp: Double?, val min: Double?, val max: Double?,
    val code: Int?, val isDay: Boolean?, val updated: Long, val error: String?,
    val sunriseMin: Int? = null, val sunsetMin: Int? = null,     // 하루 중 분(예: 06:25 → 385)
    val hourly: List<HourWx> = emptyList()                         // 앞으로 몇 시간 예보 (4×2 위젯 아래 줄)
) {
    companion object {
        val EMPTY = WeatherData(null, null, null, null, null, 0L, null)
    }

    /** 지금이 밤인지: 일출·일몰 시각이 있으면 그걸로, 없으면 서버의 낮/밤 값, 그것도 없으면 시계(6~18시=낮) */
    fun isNight(): Boolean {
        val now = LocalTime.now(ZoneId.of("Asia/Seoul"))
        val m = now.hour * 60 + now.minute
        if (sunriseMin != null && sunsetMin != null) return m < sunriseMin || m >= sunsetMin
        isDay?.let { return !it }
        return now.hour < 6 || now.hour >= 19
    }

    /** 그림용 하늘 종류: clear / cloudy / rain / snow */
    fun skyKind(): String {
        val c = code ?: return "clear"
        return when (c) {
            in 51..67, in 80..82, in 95..99 -> "rain"
            in 71..77, 85, 86 -> "snow"
            2, 3, 45, 48 -> "cloudy"
            else -> "clear"
        }
    }

    fun isStale() = System.currentTimeMillis() - updated > 35 * 60 * 1000L

    fun cond(): String {
        val c = code ?: return "--"
        return when (c) {
            0, 1 -> "맑음"
            2 -> "구름조금"
            3 -> "흐림"
            45, 48 -> "안개"
            in 51..57 -> "이슬비"
            in 61..67, in 80..82 -> "비"
            in 71..77, 85, 86 -> "눈"
            in 95..99 -> "뇌우"
            else -> "--"
        }
    }

    fun icon(): Int {
        val day = !isNight()
        val c = code ?: return R.drawable.ic_w_cloud
        return when (c) {
            0, 1 -> if (day) R.drawable.ic_w_sun else R.drawable.ic_w_moon
            2 -> R.drawable.ic_w_partly
            in 51..67, in 80..82, in 95..99 -> R.drawable.ic_w_rain
            in 71..77, 85, 86 -> R.drawable.ic_w_snow
            else -> R.drawable.ic_w_cloud
        }
    }

    /** 표정이 있는 캐릭터 날씨 아이콘 (색이 들어간 그림이라 색을 입히지 않고 그대로 씀) */
    fun iconFun(): Int {
        val n = isNight()
        val c = code ?: return if (n) R.drawable.w_fun_partly_night else R.drawable.w_fun_partly_day
        return when (c) {
            0, 1 -> if (n) R.drawable.w_fun_clear_night else R.drawable.w_fun_clear_day
            2 -> if (n) R.drawable.w_fun_partly_night else R.drawable.w_fun_partly_day
            45, 48 -> R.drawable.w_fun_fog
            in 51..67, in 80..82 -> R.drawable.w_fun_rain
            in 71..77, 85, 86 -> R.drawable.w_fun_snow
            in 95..99 -> R.drawable.w_fun_thunder
            else -> R.drawable.w_fun_cloudy
        }
    }

    /** 삼성 위젯 같은 색이 들어간 입체 날씨 아이콘 */
    fun icon3d(): Int {
        val n = isNight()
        val c = code ?: return if (n) R.drawable.w_ic3_partly_night else R.drawable.w_ic3_partly_day
        return when (c) {
            0, 1 -> if (n) R.drawable.w_ic3_clear_night else R.drawable.w_ic3_clear_day
            2 -> if (n) R.drawable.w_ic3_partly_night else R.drawable.w_ic3_partly_day
            45, 48 -> R.drawable.w_ic3_fog
            in 51..67, in 80..82 -> R.drawable.w_ic3_rain
            in 71..77, 85, 86 -> R.drawable.w_ic3_snow
            in 95..99 -> R.drawable.w_ic3_thunder
            else -> R.drawable.w_ic3_cloudy
        }
    }

    fun tempText(): String = temp?.let { "${it.roundToInt()}°" } ?: "--°"

    fun rangeText(): String =
        if (min != null && max != null) "${min.roundToInt()}° / ${max.roundToInt()}°" else ""

    fun message(): String? = if (temp == null) (error ?: "갱신 대기 중") else null
}

/** 시간별 예보 한 칸. hour = 0~23시 */
data class HourWx(val hour: Int, val temp: Double, val code: Int, val day: Boolean) {
    fun tempText() = "${temp.roundToInt()}°"
    fun label() = if (hour == 0) "자정" else if (hour == 12) "정오" else "${hour}시"
    /** 같은 코드의 병맛 캐릭터 / 입체 아이콘 (WeatherData와 같은 규칙) */
    fun iconFun() = funFor(code, !day)
    fun icon3d() = ic3For(code, !day)

    companion object {
        fun funFor(c: Int, n: Boolean): Int = when (c) {
            0, 1 -> if (n) R.drawable.w_fun_clear_night else R.drawable.w_fun_clear_day
            2 -> if (n) R.drawable.w_fun_partly_night else R.drawable.w_fun_partly_day
            45, 48 -> R.drawable.w_fun_fog
            in 51..67, in 80..82 -> R.drawable.w_fun_rain
            in 71..77, 85, 86 -> R.drawable.w_fun_snow
            in 95..99 -> R.drawable.w_fun_thunder
            else -> R.drawable.w_fun_cloudy
        }
        fun ic3For(c: Int, n: Boolean): Int = when (c) {
            0, 1 -> if (n) R.drawable.w_ic3_clear_night else R.drawable.w_ic3_clear_day
            2 -> if (n) R.drawable.w_ic3_partly_night else R.drawable.w_ic3_partly_day
            45, 48 -> R.drawable.w_ic3_fog
            in 51..67, in 80..82 -> R.drawable.w_ic3_rain
            in 71..77, 85, 86 -> R.drawable.w_ic3_snow
            in 95..99 -> R.drawable.w_ic3_thunder
            else -> R.drawable.w_ic3_cloudy
        }
    }
}

object WeatherStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    private fun JSONObject.optD(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.optI(k: String): Int? = if (has(k) && !isNull(k)) getInt(k) else null
    private fun JSONObject.optS(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    fun load(ctx: Context): WeatherData {
        val s = sp(ctx).getString("data2", null) ?: return WeatherData.EMPTY
        return try {
            val o = JSONObject(s)
            WeatherData(o.optD("temp"), o.optD("min"), o.optD("max"), o.optI("code"),
                if (o.has("day")) o.getBoolean("day") else null, o.optLong("updated", 0L), o.optS("error"),
                o.optI("sunrise"), o.optI("sunset"),
                o.optJSONArray("hourly")?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        a.optJSONObject(i)?.let { h -> HourWx(h.getInt("h"), h.getDouble("t"), h.getInt("c"), h.optBoolean("d", true)) }
                    }
                } ?: emptyList())
        } catch (e: Exception) {
            WeatherData.EMPTY
        }
    }

    fun save(ctx: Context, d: WeatherData) {
        val o = JSONObject()
            .putOpt("temp", d.temp).putOpt("min", d.min).putOpt("max", d.max)
            .putOpt("code", d.code).putOpt("day", d.isDay).put("updated", d.updated).putOpt("error", d.error)
            .putOpt("sunrise", d.sunriseMin).putOpt("sunset", d.sunsetMin)
            .put("hourly", org.json.JSONArray().apply {
                d.hourly.forEach { h -> put(JSONObject().put("h", h.hour).put("t", h.temp).put("c", h.code).put("d", h.day)) }
            })
        sp(ctx).edit().putString("data2", o.toString()).apply()
    }

    /** 동네 이름(예: 정릉동). 위치가 바뀔 때만 다시 찾음 */
    fun place(ctx: Context): String? = sp(ctx).getString("place", null)
    fun placeKey(ctx: Context): String? = sp(ctx).getString("place_key", null)
    fun setPlace(ctx: Context, key: String, name: String?) {
        sp(ctx).edit().putString("place_key", key).putString("place", name).apply()
    }

    /** 마지막으로 확인한 위치(소수 둘째 자리 ≈ 1km 단위로 반올림해 저장) */
    fun location(ctx: Context): Pair<Double, Double>? {
        val p = sp(ctx)
        if (!p.contains("lat")) return null
        return p.getFloat("lat", 0f).toDouble() to p.getFloat("lon", 0f).toDouble()
    }

    fun setLocation(ctx: Context, lat: Double, lon: Double) {
        sp(ctx).edit()
            .putFloat("lat", (Math.round(lat * 100) / 100.0).toFloat())
            .putFloat("lon", (Math.round(lon * 100) / 100.0).toFloat())
            .apply()
    }
}
