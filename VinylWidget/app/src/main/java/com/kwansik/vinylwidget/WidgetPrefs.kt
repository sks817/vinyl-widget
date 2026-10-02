package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews

enum class Kind { MUSIC, WEATHER, MUSIC_WIDE, WEATHER_WIDE, MEMO }

/**
 * 위젯 꾸미기 값. design은 날씨 위젯에서만 사용.
 * glass = 배경 '자동(유리)', fg = Palette.AUTO면 글자·버튼 색 자동
 * corner = 그림 모서리 둥글기(짧은 변의 %, -1이면 디자인 기본값), glassArt = 그림 유리 캡슐 효과,
 * artShadow = 그림 그림자 (바탕 없음일 때만 그림)
 * quirky = 병맛 테마 (날씨: 캐릭터 아이콘, 레코드: 만화풍 판과 왕눈이 라벨), point = 달력 디자인 윗부분 색
 * bg = 배경 '컬러'에서 고른 바탕색 (0이면 검정/흰색, 캐릭터 카드는 날씨마다 바뀌는 색)
 * nightFg = 밤(해 진 뒤) 글자색. 0이면 자동: 고른 글자색이 어두우면 밤엔 크림색으로 바꿔 어두운 그림 위에서도 보이게
 * disc = 레코드판·카세트 몸통 색 (0이면 기본: 판은 검정, 카세트는 진한 갈색/병맛 분홍), player = 레코드판 / 카세트
 */
data class WidgetStyle(
    val white: Boolean, val transparency: Int, val fg: Int, val design: Int, val glass: Boolean = true,
    val corner: Int = -1, val glassArt: Boolean = true, val artShadow: Boolean = true,
    val quirky: Boolean = false, val point: Int = WidgetStyle.DEFAULT_POINT,
    val bg: Int = 0,
    val disc: Int = 0, val player: Int = WidgetStyle.VINYL,
    val nightFg: Int = 0
) {
    companion object {
        /** 달력 디자인 윗부분 기본 색 (벽돌색) */
        const val DEFAULT_POINT = 0xFFB23A2E.toInt()
        const val VINYL = 0
        const val CASSETTE = 1
    }
}

/** 위젯 종류별 고정 값 (꾸미기 화면이 이걸 보고 동작) */
object KindConfig {
    fun isMusic(k: Kind) = k == Kind.MUSIC || k == Kind.MUSIC_WIDE

    /**
     * 기본값: 자동 색. 레코드 위젯은 유리 카드,
     * 날씨·날짜 위젯은 바탕 없이 그림과 글자만 배경화면 위에 (요즘 투명 위젯 문법)
     */
    fun default(k: Kind) = if (k == Kind.MEMO)          // 일정 메모: 레코드 위젯과 같은 유리 카드, 테이프는 크라프트지색
        WidgetStyle(white = false, transparency = 0, fg = Palette.AUTO, design = 1, glass = true)
    else WidgetStyle(
        white = false, transparency = if (isMusic(k)) 0 else 100, fg = Palette.AUTO, design = 1, glass = true,
        quirky = !isMusic(k)
    )

    fun provider(k: Kind): Class<*> = when (k) {
        Kind.MUSIC -> SpinWidgetProvider::class.java
        Kind.MUSIC_WIDE -> SpinWideProvider::class.java
        Kind.WEATHER -> WeatherWidgetProvider::class.java
        Kind.WEATHER_WIDE -> WeatherWideProvider::class.java
        Kind.MEMO -> MemoWidgetProvider::class.java
    }

    fun name(k: Kind) = when (k) {
        Kind.MUSIC -> "레코드"; Kind.MUSIC_WIDE -> "레코드 1×4"; Kind.WEATHER -> "날씨"; Kind.WEATHER_WIDE -> "날씨 1×4"; Kind.MEMO -> "다음 일정"
    }
    fun colorTitle(k: Kind) = if (isMusic(k)) "버튼·글자 색상" else if (k == Kind.MEMO) "글자 색상" else "글자·아이콘 색상"
    fun colorNote(k: Kind): String? =
        if (k == Kind.WEATHER || k == Kind.WEATHER_WIDE) "자동이면 그림·배경에 맞춰 바뀌고, 색을 고르면 그림 위 글자까지 모두 그 색이 돼요" else null
    /** 그림 모서리 둥글기 기본값(짧은 변의 %): 2×2 재킷 11, 1×4 파노라마 30 */
    fun defaultCorner(k: Kind) = if (k == Kind.WEATHER_WIDE) 30 else 11

    fun designs(k: Kind): Array<String>? = when (k) {
        Kind.WEATHER -> WeatherWidget.DESIGN_NAMES
        Kind.WEATHER_WIDE -> WeatherWidget.WIDE_DESIGN_NAMES
        else -> null
    }

    /** 설정 화면 미리보기 크기(dp) */
    fun previewSize(k: Kind): Pair<Float, Float> =
        if (k == Kind.MUSIC_WIDE || k == Kind.WEATHER_WIDE) 330f to 84f else if (k == Kind.MEMO) 330f to 170f else 170f to 170f

    fun preview(ctx: Context, k: Kind, style: WidgetStyle): RemoteViews {
        val (w, h) = previewSize(k)
        val l = LabelRenderer.draw(null, Palette.label(ctx), style.quirky)
        return when (k) {
            Kind.MUSIC -> MusicWidget.build(ctx, style, l, playing = false, status = null, title = "곡 제목", artist = "가수 이름")
            Kind.MUSIC_WIDE -> MusicWidget.buildWide(ctx, style, l, false, "곡 제목", "가수 이름", null, w, h)
            Kind.WEATHER -> WeatherWidget.build(ctx, style, WeatherStore.load(ctx), w, h)
            Kind.WEATHER_WIDE -> WeatherWidget.buildWide(ctx, style, WeatherStore.load(ctx), w, h)
            Kind.MEMO -> {                              // 실제 일정이 있으면 실제 일정, 없으면 예시
                val ev = CalendarReader.upcoming(ctx).ifEmpty { MemoWidget.SAMPLE }
                MemoWidget.build(ctx, style, null, w, h, ev, true)
            }
        }
    }

    fun refreshAll(ctx: Context, k: Kind) {
        if (isMusic(k)) {
            WidgetUpdater.refresh(ctx)
        } else if (k == Kind.MEMO) {
            val app = ctx.applicationContext
            Thread { MemoWidget.renderAll(app) }.start()
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
        Kind.MEMO -> "${f}_default_memo"
    }

    fun style(ctx: Context, k: Kind, id: Int?): WidgetStyle {
        val p = sp(ctx)
        val d = KindConfig.default(k)
        val base = WidgetStyle(
            p.getBoolean(dk(k, "w"), d.white),
            p.getInt(dk(k, "a"), d.transparency),
            p.getInt(dk(k, "f"), d.fg),
            p.getInt(dk(k, "d"), d.design),
            p.getBoolean(dk(k, "g"), d.glass),
            p.getInt(dk(k, "r"), d.corner),
            p.getBoolean(dk(k, "ga"), d.glassArt),
            p.getBoolean(dk(k, "sh"), d.artShadow),
            p.getBoolean(dk(k, "q"), d.quirky),
            p.getInt(dk(k, "pc"), d.point),
            p.getInt(dk(k, "bc"), d.bg),
            p.getInt(dk(k, "dc"), d.disc),
            p.getInt(dk(k, "pl"), d.player),
            p.getInt(dk(k, "nf"), d.nightFg)
        )
        if (id == null) return base
        return WidgetStyle(
            p.getBoolean("w_$id", base.white),
            p.getInt("a_$id", base.transparency),
            p.getInt("f_$id", base.fg),
            p.getInt("d_$id", base.design),
            p.getBoolean("g_$id", base.glass),
            p.getInt("r_$id", base.corner),
            p.getBoolean("ga_$id", base.glassArt),
            p.getBoolean("sh_$id", base.artShadow),
            p.getBoolean("q_$id", base.quirky),
            p.getInt("pc_$id", base.point),
            p.getInt("bc_$id", base.bg),
            p.getInt("dc_$id", base.disc),
            p.getInt("pl_$id", base.player),
            p.getInt("nf_$id", base.nightFg)
        )
    }

    /** id가 null이면 그 종류의 모든 위젯에 적용(개별 설정은 지우고 기본값으로 통일) */
    fun save(ctx: Context, k: Kind, id: Int?, s: WidgetStyle) {
        val e = sp(ctx).edit()
        if (id != null) {
            e.putBoolean("w_$id", s.white).putInt("a_$id", s.transparency)
                .putInt("f_$id", s.fg).putInt("d_$id", s.design).putBoolean("g_$id", s.glass)
                .putInt("r_$id", s.corner).putBoolean("ga_$id", s.glassArt).putBoolean("sh_$id", s.artShadow)
                .putBoolean("q_$id", s.quirky).putInt("pc_$id", s.point).putInt("bc_$id", s.bg)
                .putInt("dc_$id", s.disc).putInt("pl_$id", s.player).putInt("nf_$id", s.nightFg)
        } else {
            e.putBoolean(dk(k, "w"), s.white).putInt(dk(k, "a"), s.transparency)
                .putInt(dk(k, "f"), s.fg).putInt(dk(k, "d"), s.design).putBoolean(dk(k, "g"), s.glass)
                .putInt(dk(k, "r"), s.corner).putBoolean(dk(k, "ga"), s.glassArt).putBoolean(dk(k, "sh"), s.artShadow)
                .putBoolean(dk(k, "q"), s.quirky).putInt(dk(k, "pc"), s.point).putInt(dk(k, "bc"), s.bg)
                .putInt(dk(k, "dc"), s.disc).putInt(dk(k, "pl"), s.player).putInt(dk(k, "nf"), s.nightFg)
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
        for (f in listOf("w", "t", "c", "d", "a", "f", "g", "r", "ga", "sh", "q", "pc", "bc", "dc", "pl", "nf")) e.remove("${f}_$id")
    }

    fun alphaOf(transparency: Int): Int = ((100 - transparency) * 255 / 100).coerceIn(0, 255)

    fun bgColor(s: WidgetStyle): Int = if (s.bg != 0) s.bg else if (s.white) Color.WHITE else 0xFF141414.toInt()
}
