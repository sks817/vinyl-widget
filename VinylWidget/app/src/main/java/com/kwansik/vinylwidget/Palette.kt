package com.kwansik.vinylwidget

import android.content.Context
import android.content.res.Configuration
import android.widget.RemoteViews

/**
 * 위젯 색 정하기.
 *  - 배경 '자동(유리)': 배경화면에서 뽑은 시스템 색(Material You)을 반투명하게 깐 카드. 다크 모드도 따라감
 *  - 글자·버튼 색 '자동'(fg == AUTO): 배경에 맞춰 읽기 좋은 색을 고름
 */
object Palette {
    /** 글자·버튼 색 '자동'. 진짜 색은 항상 불투명(알파 FF)이라 0과 겹치지 않음 */
    const val AUTO = 0

    fun night(ctx: Context) =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** 위젯 바탕이 밝은가 (유리는 다크 모드가 아니면 밝음) */
    fun lightBg(ctx: Context, s: WidgetStyle) = if (s.glass) !night(ctx) else s.white

    /** 주 글자색 */
    fun text(ctx: Context, s: WidgetStyle): Int = when {
        s.fg != AUTO -> s.fg
        s.glass -> ctx.getColor(R.color.glass_text)
        s.white -> ctx.getColor(android.R.color.system_neutral1_900)
        else -> ctx.getColor(android.R.color.system_neutral1_10)
    }

    /** 보조 글자색 (가수 이름, 최저/최고 기온 등) */
    fun sub(ctx: Context, s: WidgetStyle): Int = when {
        s.fg != AUTO -> (s.fg and 0x00FFFFFF) or (0xB3 shl 24)
        s.glass -> ctx.getColor(R.color.glass_sub)
        s.white -> ctx.getColor(android.R.color.system_neutral2_600)
        else -> ctx.getColor(android.R.color.system_neutral2_200)
    }

    /** 버튼·아이콘 색 */
    fun accent(ctx: Context, s: WidgetStyle): Int = when {
        s.fg != AUTO -> s.fg
        s.glass -> ctx.getColor(R.color.glass_accent)
        s.white -> ctx.getColor(android.R.color.system_accent1_600)
        else -> ctx.getColor(android.R.color.system_accent1_200)
    }

    /** 앨범 커버가 없을 때 레코드 라벨 색: 배경화면 색을 따라감 */
    fun label(ctx: Context): Int = ctx.getColor(android.R.color.system_accent1_400)

    /** 위젯 바탕(bg_image). visible=false면 바탕을 숨김 */
    fun applyBackground(rv: RemoteViews, s: WidgetStyle, visible: Boolean = true) {
        if (s.glass) {
            rv.setImageViewResource(R.id.bg_image, R.drawable.widget_bg_glass)
            rv.setInt(R.id.bg_image, "setColorFilter", 0)            // 색 덮어쓰기 없음 (투명색 SRC_ATOP = 원본 그대로)
        } else {
            rv.setImageViewResource(R.id.bg_image, R.drawable.widget_bg)
            rv.setInt(R.id.bg_image, "setColorFilter", WidgetPrefs.bgColor(s))
        }
        rv.setInt(R.id.bg_image, "setImageAlpha", if (visible) WidgetPrefs.alphaOf(s.transparency) else 0)
    }

    private val AUTO_ICONS = mapOf(
        R.drawable.ic_prev to R.drawable.ic_prev_auto, R.drawable.ic_next to R.drawable.ic_next_auto,
        R.drawable.ic_play to R.drawable.ic_play_auto, R.drawable.ic_pause to R.drawable.ic_pause_auto
    )

    /**
     * 재생 버튼 아이콘. 유리 + 자동 색이면 아이콘 자체에 시스템 색이 들어 있어서
     * 런처가 그릴 때 다크 모드·배경화면 색이 바뀌어도 앱을 다시 그리지 않고 따라감
     */
    fun setIcon(ctx: Context, rv: RemoteViews, id: Int, icon: Int, s: WidgetStyle) {
        if (s.glass && s.fg == AUTO) {
            rv.setImageViewResource(id, AUTO_ICONS[icon] ?: icon)
            rv.setInt(id, "setColorFilter", 0)
        } else {
            rv.setImageViewResource(id, icon)
            rv.setInt(id, "setColorFilter", accent(ctx, s))
        }
    }

    /** 글자 색. 유리 + 자동이면 시스템 색 자원으로 지정해 런처 쪽에서 저절로 바뀜 */
    fun setTextColor(ctx: Context, rv: RemoteViews, id: Int, s: WidgetStyle, sub: Boolean = false) {
        if (s.glass && s.fg == AUTO) {
            rv.setColorStateList(id, "setTextColor", if (sub) R.color.glass_sub else R.color.glass_text)
        } else {
            rv.setTextColor(id, if (sub) sub(ctx, s) else text(ctx, s))
        }
    }

    /** 다크 모드·배경화면 색이 바뀌었는지 알아보는 값 */
    fun signature(ctx: Context): String =
        "${night(ctx)}|${ctx.getColor(R.color.glass_accent)}|${ctx.getColor(R.color.glass_fill)}"
}
