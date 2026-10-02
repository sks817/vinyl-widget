package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews

enum class Kind { MUSIC, WEATHER, MUSIC_WIDE, WEATHER_WIDE }

/** 위젯 꾸미기 값. design은 날씨 위젯에서만 사용(1~5) */
data class WidgetStyle(val white: Boolean, val transparency: Int, val fg: Int, val design: Int)

/** 위젯 종류별 고정 값 (꾸미기 화면이 이걸 보고 동작) */
object KindConfig {
    private fun isMusic(k: Kind) = k == Kind.MUSIC || k == Kind.MUSIC_WIDE

    fun default(k: Kind) = when (k) {
        Kind.MUSIC -> WidgetStyle(white = false, transparency = 15, fg = Color.WHITE, design = 1)
        Kind.MUSIC_WIDE -> WidgetStyle(white = false, transparency = 25, fg = Color.WHITE, design = 1)
        Kind.WEATHER -> WidgetStyle(white = false, transparency = 100, fg = 0xFF161616.toInt(), design = 1)
        Kind.WEATHER_WIDE -> WidgetStyle(white = false, transparency = 100, fg = 0xFF161616.toInt(), design = 1)
    }

    fun provider(k: Kind): Class<*> = when (k) {
        Kind.MUSIC -> SpinWidgetProvider::class.java
        Kind.MUSIC_WIDE -> SpinWideProvider::class.java
        Kind.WEATHER -> WeatherWidgetProvider::class.java
        Kind.WEATHER_WIDE -> WeatherWideProvider::class.java
    }

    fun name(k: Kind) = when (k) {
        Kind.MUSIC -> "레코드"; Kind.MUSIC_WIDE -> "레코드 1×4"; Kind.WEATHER -> "날씨"; Kind.WEATHER_WIDE -> "날씨 1×4"
    }
    fun colorTitle(k: Kind) = if (isMusic(k)) "버튼·글자 색상" else "글자·아이콘 색상"
    fun colorNote(k: Kind): String? =
        if (!isMusic(k)) "풍경 그림 위 글자는 그림에 맞춰 자동으로 바뀌고, 그림 밖 글자는 이 색을 씁니다" else null
    fun designs(k: Kind): Array<String>? = when (k) {
        Kind.WEATHER -> WeatherWidget.DESIGN_NAMES
        Kind.WEATHER_WIDE -> WeatherWidget.WIDE_DESIGN_NAMES
        else -> null
    }

    /** 설정 화면 미리보기 크기(dp) */
    fun previewSize(k: Kind): Pair<Float, Float> =
        if (k == Kind.MUSIC_WIDE || k == Kind.WEATHER_WIDE) 330f to 84f else 170f to 170f

    private var label: android.graphics.Bitmap? = null

    fun preview(ctx: Context, k: Kind, style: WidgetStyle): RemoteViews {
        val (w, h) = previewSize(k)
        val l = label ?: LabelRenderer.draw(null).also { label = it }
        return when (k) {
            Kind.MUSIC -> MusicWidget.build(ctx, style, l, playing = false, status = null)
            Kind.MUSIC_WIDE -> MusicWidget.buildWide(ctx, style, l, false, "곡 제목", "가수 이름", null, w, h)
            Kind.WEATHER -> WeatherWidget.build(ctx, style, WeatherStore.load(ctx), w, h)
            Kind.WEATHER_WIDE -> WeatherWidget.buildWide(ctx, style, WeatherStore.load(ctx), w, h)
        }
    }

    fun refreshAll(ctx: Context, k: Kind) {
        if (isMusic(k)) {
            WidgetUpdater.refresh(ctx)
        } else {
            val app = ctx.applicationContext
            Thread { WeatherWidget.renderAll(app) }.start()
        }
    }

    fun kindOf(ctx: Context, id: Int): Kind {
        val cls = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)?.provider?.className
        return Kind.values().firstOrNull { provider(it).name == cls } ?: Kind.MUSIC
    }
}

/** 위젯별 꾸미기 설정. 개별 설정이 없으면 종류별 '전체 기본값'을 따름 */
object WidgetPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)

    private fun dk(k: Kind, f: String) = when (k) {
        Kind.MUSIC -> "${f}_default"                // 이전 버전 호환
        Kind.WEATHER -> "${f}_default_weather"
        Kind.MUSIC_WIDE -> "${f}_default_mwide"
        Kind.WEATHER_WIDE -> "${f}_default_wwide"
    }

    fun style(ctx: Context, k: Kind, id: Int?): WidgetStyle {
        val p = sp(ctx)
        val d = KindConfig.default(k)
        val base = WidgetStyle(
            p.getBoolean(dk(k, "w"), d.white),
            p.getInt(dk(k, "t"), d.transparency),
            p.getInt(dk(k, "c"), d.fg),
            p.getInt(dk(k, "d"), d.design)
        )
        if (id == null) return base
        return WidgetStyle(
            p.getBoolean("w_$id", base.white),
            p.getInt("t_$id", base.transparency),
            p.getInt("c_$id", base.fg),
            p.getInt("d_$id", base.design)
        )
    }

    /** id가 null이면 그 종류의 모든 위젯에 적용(개별 설정은 지우고 기본값으로 통일) */
    fun save(ctx: Context, k: Kind, id: Int?, s: WidgetStyle) {
        val e = sp(ctx).edit()
        if (id != null) {
            e.putBoolean("w_$id", s.white).putInt("t_$id", s.transparency)
                .putInt("c_$id", s.fg).putInt("d_$id", s.design)
        } else {
            e.putBoolean(dk(k, "w"), s.white).putInt(dk(k, "t"), s.transparency)
                .putInt(dk(k, "c"), s.fg).putInt(dk(k, "d"), s.design)
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, KindConfig.provider(k)))
            ids.forEach { e.remove("w_$it").remove("t_$it").remove("c_$it").remove("d_$it") }
        }
        e.apply()
    }

    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        ids.forEach { e.remove("w_$it").remove("t_$it").remove("c_$it").remove("d_$it") }
        e.apply()
    }

    fun alphaOf(transparency: Int): Int = ((100 - transparency) * 255 / 100).coerceIn(0, 255)

    fun bgColor(s: WidgetStyle): Int = if (s.white) Color.WHITE else 0xFF141414.toInt()
}
