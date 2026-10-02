package com.kwansik.vinylwidget

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Open-Meteo(가입·키 불필요) 1회 호출로 현재 기온·날씨 코드·낮/밤, 오늘 최저/최고를 받음.
 * 모델은 기본값(best_match: 위치별로 가장 적합한 모델 자동 선택)을 사용.
 *   ※ 기상청(KMA) 모델 지정은 현재 Open-Meteo 쪽 데이터 갱신이 중단돼 있어 쓰지 않음
 */
object WeatherFetcher {
    private val executor = Executors.newSingleThreadExecutor()

    fun fetchAsync(ctx: Context, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        executor.execute {
            try {
                fetch(app)
            } catch (t: Throwable) {
                Log.e("VinylWidget", "weather failed", t)
            } finally {
                done?.invoke()
            }
        }
    }

    /** 좌표 → '정릉동' 같은 동네 이름 (주소 문자열에서 동·읍·면을 찾음) */
    private fun updatePlace(ctx: Context, lat: Double, lon: Double) {
        val key = String.format(Locale.US, "%.2f,%.2f", lat, lon)
        if (WeatherStore.placeKey(ctx) == key && WeatherStore.place(ctx) != null) return
        try {
            if (!android.location.Geocoder.isPresent()) return
            @Suppress("DEPRECATION")
            val a = android.location.Geocoder(ctx, Locale.KOREA).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return
            val line = a.getAddressLine(0).orEmpty()
            val dong = line.split(" ").lastOrNull { Regex(".+(동|읍|면)$").matches(it) }
            val name = dong ?: a.subLocality ?: a.locality ?: a.subAdminArea ?: a.adminArea
            WeatherStore.setPlace(ctx, key, name)
        } catch (e: Exception) {
            Log.e("VinylWidget", "geocode", e)
        }
    }

    fun fetch(ctx: Context) {
        val old = WeatherStore.load(ctx)
        val loc = LocationHelper.bestLocation(ctx)
        if (loc == null) {
            val msg = if (LocationHelper.hasPermission(ctx)) "위치 확인 불가" else "위치 권한 필요"
            WeatherStore.save(ctx, old.copy(error = msg))
            WeatherWidget.renderAll(ctx)
            return
        }
        try {
            val (lat, lon) = loc
            val url = URL(String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                    "&timezone=Asia%%2FSeoul&forecast_days=1", lat, lon))
            val c = url.openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            val body = try {
                val code = c.responseCode
                val text = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("날씨 서버 오류($code)")
                text
            } finally {
                c.disconnect()
            }

            val o = JSONObject(body)
            val cur = o.getJSONObject("current")
            val daily = o.getJSONObject("daily")
            val temp = cur.optDouble("temperature_2m").takeIf { !it.isNaN() }
            val code = if (cur.has("weather_code") && !cur.isNull("weather_code")) cur.getInt("weather_code") else null
            val isDay = if (cur.has("is_day") && !cur.isNull("is_day")) cur.getInt("is_day") == 1 else null
            val max = daily.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeIf { !it.isNaN() }
            val min = daily.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeIf { !it.isNaN() }

            fun minutes(key: String): Int? = daily.optJSONArray(key)?.optString(0)
                ?.substringAfter('T', "")?.split(":")?.let { p -> if (p.size >= 2) (p[0].toIntOrNull() ?: return@let null) * 60 + (p[1].toIntOrNull() ?: 0) else null }
            WeatherStore.save(ctx, WeatherData(temp, min, max, code, isDay, System.currentTimeMillis(), null,
                minutes("sunrise"), minutes("sunset")))
            updatePlace(ctx, lat, lon)
        } catch (e: Exception) {
            Log.e("VinylWidget", "open-meteo", e)
            val msg = if (e is IOException && e.message?.startsWith("날씨") == true) e.message!! else "갱신 실패(인터넷 확인)"
            WeatherStore.save(ctx, old.copy(error = msg))
        }
        WeatherWidget.renderAll(ctx)
    }
}
