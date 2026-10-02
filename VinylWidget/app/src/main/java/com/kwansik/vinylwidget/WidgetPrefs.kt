package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews

enum class Kind { MUSIC, WEATHER, MUSIC_WIDE, WEATHER_WIDE }

/**
 * 위젯 꾸미기 값. design은 날씨 위젯에서만 사용.
 * glass = 배경 '자동(유리)', fg = Palette.AUTO면 글자·버튼 색 자동
 */
data class WidgetStyle(
    val white: Boolean, val transparency: Int, val fg: Int, val design: Int, val glass: Boolean = true
)

/** 위젯 종류별 고정 값 (꾸미기 화면이 이걸 보고 동작) */
object KindConfig {
    private fun isMusic(k: Kind) = k == Kind.MUSIC || k == Kind.MUSIC_WIDE

    /** 기본값: 어떤 배경화면에도 어울리도록 유리 카드 + 자동 색 */
    fun default(k: Kind) = WidgetStyle(white = false, transparency = 0, fg = Palette.AUTO, design = 1, glass = true)

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

    fun preview(ctx: Context, k: Kind, style: WidgetStyle): RemoteViews {
        val (w, h) = previewSize(k)
        val l = LabelRenderer.draw(null, Palette.label(ctx))
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
            p.getInt(dk(k, "a"), d.transparency),
            p.getInt(dk(k, "f"), d.fg),
            p.getInt(dk(k, "d"), d.design),
            p.getBoolean(dk(k, "g"), d.glass)
        )
        if (id == null) return base
        return WidgetStyle(
            p.getBoolean("w_$id", base.white),
            p.getInt("a_$id", base.transparency),
            p.getInt("f_$id", base.fg),
            p.getInt("d_$id", base.design),
            p.getBoolean("g_$id", base.glass)
        )
    }

    /** id가 null이면 그 종류의 모든 위젯에 적용(개별 설정은 지우고 기본값으로 통일) */
    fun save(ctx: Context, k: Kind, id: Int?, s: WidgetStyle) {
        val e = sp(ctx).edit()
        if (id != null) {
            e.putBoolean("w_$id", s.white).putInt("a_$id", s.transparency)
                .putInt("f_$id", s.fg).putInt("d_$id", s.design).putBoolean("g_$id", s.glass)
        } else {
            e.putBoolean(dk(k, "w"), s.white).putInt(dk(k, "a"), s.transparency)
                .putInt(dk(k, "f"), s.fg).putInt(dk(k, "d"), s.design).putBoolean(dk(k, "g"), s.glass)
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, KindConfig.provider(k)))
            ids.forEach { removeId(e, it) }
        }
        e.apply()
    }

    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        ids.forEach { removeId(e, it) }
        e.apply()
    }

    private fun removeId(e: android.content.SharedPreferences.Editor, id: Int) {
        for (f in listOf("w", "t", "c", "d", "a", "f", "g")) e.remove("${f}_$id")
    }

    fun alphaOf(transparency: Int): Int = ((100 - transparency) * 255 / 100).coerceIn(0, 255)

    fun bgColor(s: WidgetStyle): Int = if (s.white) Color.WHITE else 0xFF141414.toInt()
}
