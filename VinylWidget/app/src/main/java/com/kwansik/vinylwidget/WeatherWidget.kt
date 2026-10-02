package com.kwansik.vinylwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

/** 날씨·날짜 위젯. 그림을 누르면 기본 캘린더 앱, 아래 날씨 줄을 누르면 새로고침 */
object WeatherWidget {

    val DESIGN_NAMES = arrayOf("LP 재킷", "불 켜진 텐트", "하늘 원", "큰 날짜 + 텐트", "미니멀", "다이얼", "달력",
        "캐릭터 포스터", "캐릭터 카드", "캐릭터 헤드라인")
    val WIDE_DESIGN_NAMES = arrayOf("캠핑 파노라마", "텐트 + 날짜", "심플", "캐릭터 포스터", "캐릭터 카드", "캐릭터 헤드라인")

    /** 자체 카드를 그리는 디자인 (배경 투명도 = 카드 진하기) */
    const val CARD_2X2 = 9
    const val CARD_1X4 = 5

    fun renderAll(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWidgetProvider::class.java))
            val wideIds = mgr.getAppWidgetIds(ComponentName(ctx, WeatherWideProvider::class.java))
            if (ids.isEmpty() && wideIds.isEmpty()) return
            val data = WeatherStore.load(ctx)
            for (id in ids) {
                val style = WidgetPrefs.style(ctx, Kind.WEATHER, id)
                val (w, h) = WidgetGeom.sizeDp(mgr, id)
                mgr.updateAppWidget(id, build(ctx, style, data, w, h))
            }
            for (id in wideIds) {
                val style = WidgetPrefs.style(ctx, Kind.WEATHER_WIDE, id)
                val (w, h) = WidgetGeom.sizeDp(mgr, id)
                mgr.updateAppWidget(id, buildWide(ctx, style, data, w, h))
            }
            scheduleMidnight(ctx)
            scheduleSunEvent(ctx, data)
        } catch (t: Throwable) {
            Log.e("VinylWidget", "weather render failed", t)
        }
    }

    fun build(ctx: Context, style: WidgetStyle, d: WeatherData, wDp: Float = 170f, hDp: Float = 170f): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.weather_canvas)
        val design = style.design.coerceIn(1, DESIGN_NAMES.size)
        val ownCard = design == CARD_2X2
        Palette.applyBackground(rv, style, visible = !ownCard)
        val scene = WeatherScene(ctx, wDp, hDp, Palette.text(ctx, style), Palette.sub(ctx, style), Palette.accent(ctx, style),
            Palette.shadow(ctx, style), style.quirky, style.fg != Palette.AUTO)
        scene.draw(design, d, style.corner, style.glassArt,
            style.artShadow && Palette.bgHidden(style), style.point, WidgetPrefs.alphaOf(style.transparency), style.bg)
        rv.setImageViewBitmap(R.id.w_canvas, scene.bitmap)
        rv.setOnClickPendingIntent(R.id.w_tap_calendar, calendarIntent(ctx))
        rv.setOnClickPendingIntent(R.id.w_tap_refresh, refreshIntent(ctx))
        return rv
    }

    /** 1×4 날씨 위젯 */
    fun buildWide(ctx: Context, style: WidgetStyle, d: WeatherData, wDp: Float, hDp: Float): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.weather_wide)
        val design = style.design.coerceIn(1, WIDE_DESIGN_NAMES.size)
        val simple = design == 3
        // 심플(검정/흰색): 슬라이더(배경 투명도)가 회색 카드에 적용되므로 위젯 기본 배경은 숨김.
        // 유리 배경이면 유리 카드가 곧 심플 카드
        val ownCard = (simple && !style.glass) || design == CARD_1X4
        Palette.applyBackground(rv, style, visible = !ownCard)
        val main = Palette.text(ctx, style)
        val scene = WideScene(ctx, wDp, hDp, main, Palette.sub(ctx, style), Palette.accent(ctx, style), Palette.shadow(ctx, style),
            style.quirky, style.fg != Palette.AUTO)
        scene.draw(design, d, if (ownCard) WidgetPrefs.alphaOf(style.transparency) else 0, Palette.bgLight(style),
            style.corner, style.glassArt, style.artShadow && Palette.bgHidden(style), style.bg)
        rv.setImageViewBitmap(R.id.w_canvas, scene.bitmap)
        rv.setOnClickPendingIntent(R.id.w_tap_calendar, calendarIntent(ctx))
        rv.setOnClickPendingIntent(R.id.w_tap_refresh, refreshIntent(ctx))
        if (simple) {
            val h = hDp                                           // 카드 = 위젯 전체 높이(dp)
            val dip = android.util.TypedValue.COMPLEX_UNIT_DIP
            rv.setViewVisibility(R.id.w_time, android.view.View.VISIBLE)
            // 그림 쪽 기준(오른쪽 여백 0.34h, 큰 글자 0.36h, 아래쪽 줄 중심 0.66h)과 똑같이 맞춤
            rv.setTextViewTextSize(R.id.w_time, dip, h * 0.38f)
            rv.setViewLayoutMargin(R.id.w_time, RemoteViews.MARGIN_END, h * 0.22f, dip)
            rv.setViewLayoutMargin(R.id.w_time, RemoteViews.MARGIN_BOTTOM, h * 0.17f, dip)
            if (ownCard) {                                        // 카드 글자: 직접 고른 색, 아니면 카드 밝기에 맞춘 색
                rv.setTextColor(R.id.w_time, if (style.fg != Palette.AUTO) style.fg
                    else if (Palette.bgLight(style)) 0xFF1E2128.toInt() else 0xFFFFFFFF.toInt())
            } else {
                Palette.setTextColor(ctx, rv, R.id.w_time, style)
            }
            rv.setOnClickPendingIntent(R.id.w_time, clockIntent(ctx))
        } else {
            rv.setViewVisibility(R.id.w_time, android.view.View.GONE)
        }
        return rv
    }

    /** 시계를 누르면 시계(알람) 앱 */
    private fun clockIntent(ctx: Context): PendingIntent {
        val i = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 22, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** 폰에서 '기본 캘린더'로 지정된 앱을 엶 (삼성 캘린더, 구글 캘린더 등) */
    private fun calendarIntent(ctx: Context): PendingIntent {
        val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 21, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun refreshIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 20,
            Intent(ctx, WeatherWidgetProvider::class.java).setAction(WeatherWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** 다음 일출 또는 일몰 1분 뒤에 한 번 다시 그림 → 텐트 불이 켜지고 꺼지는 시점을 맞춤 */
    private fun scheduleSunEvent(ctx: Context, d: WeatherData) {
        val rise = d.sunriseMin ?: return
        val set = d.sunsetMin ?: return
        try {
            val zone = ZoneId.of("Asia/Seoul")
            val now = java.time.ZonedDateTime.now(zone)
            val today = now.toLocalDate()
            val candidates = listOf(rise, set).flatMap { m ->
                listOf(today, today.plusDays(1)).map { day -> day.atStartOfDay(zone).plusMinutes(m.toLong() + 1) }
            }.filter { it.isAfter(now) }
            val next = candidates.minOrNull() ?: return
            val pi = PendingIntent.getBroadcast(
                ctx, 31,
                Intent(ctx, WeatherWidgetProvider::class.java).setAction(WeatherWidgetProvider.ACTION_RENDER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            ctx.getSystemService(AlarmManager::class.java)
                .setAndAllowWhileIdle(AlarmManager.RTC, next.toInstant().toEpochMilli(), pi)
        } catch (e: Exception) {
            Log.e("VinylWidget", "sun alarm failed", e)
        }
    }

    /** 날짜가 그림 안에 들어가므로, 자정 직후 한 번 다시 그리도록 예약 */
    private fun scheduleMidnight(ctx: Context) {
        try {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000L
            val pi = PendingIntent.getBroadcast(
                ctx, 30,
                Intent(ctx, WeatherWidgetProvider::class.java).setAction(WeatherWidgetProvider.ACTION_RENDER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
        } catch (e: Exception) {
            Log.e("VinylWidget", "midnight alarm failed", e)
        }
    }
}
