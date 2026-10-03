package com.kwansik.vinylwidget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import kotlin.math.max
import kotlin.math.min

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

    /** 바탕이 밝은 색인가 (흰색, 또는 '컬러'에서 고른 밝은 색) → 진한 글자 */
    fun bgLight(s: WidgetStyle): Boolean = if (s.bg != 0) android.graphics.Color.luminance(s.bg) > 0.5f else s.white

    /** 바탕 없음(투명도 100%): 글자가 배경화면 위에 바로 놓임 */
    fun bgHidden(s: WidgetStyle) = s.transparency >= 100

    /** 배경화면이 밝아서 어두운 글자가 어울리는가 (시스템이 배경화면을 보고 알려주는 힌트) */
    fun wallpaperWantsDarkText(ctx: Context): Boolean = try {
        val c = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        c != null && (c.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    } catch (e: Exception) {
        false
    }

    /** 바탕 없이 배경화면 위에 쓸 때 쓰는 자동 색 (어두운 글자인지) */
    private fun onWallpaperDark(ctx: Context, s: WidgetStyle) = s.fg == AUTO && bgHidden(s) && wallpaperWantsDarkText(ctx)

    /** 글자 그림자 색 (0 = 그림자 없음). 지저분해 보여서 이제 어디에도 그림자를 깔지 않음 */
    @Suppress("UNUSED_PARAMETER")
    fun shadow(ctx: Context, s: WidgetStyle): Int = 0

    /** 주 글자색 */
    fun text(ctx: Context, s: WidgetStyle): Int = when {
        s.fg != AUTO -> s.fg
        bgHidden(s) -> ctx.getColor(if (onWallpaperDark(ctx, s)) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10)
        s.glass -> ctx.getColor(R.color.glass_text)
        bgLight(s) -> ctx.getColor(android.R.color.system_neutral1_900)
        else -> ctx.getColor(android.R.color.system_neutral1_10)
    }

    /** 보조 글자색 (가수 이름, 최저/최고 기온 등) */
    fun sub(ctx: Context, s: WidgetStyle): Int = when {
        s.fg != AUTO -> (s.fg and 0x00FFFFFF) or (0xB3 shl 24)
        bgHidden(s) -> ctx.getColor(if (onWallpaperDark(ctx, s)) android.R.color.system_neutral2_700 else android.R.color.system_neutral2_100)
        s.glass -> ctx.getColor(R.color.glass_sub)
        bgLight(s) -> ctx.getColor(android.R.color.system_neutral2_600)
        else -> ctx.getColor(android.R.color.system_neutral2_200)
    }

    /** 버튼·아이콘 색 */
    fun accent(ctx: Context, s: WidgetStyle): Int = when {
        s.fg != AUTO -> s.fg
        bgHidden(s) -> ctx.getColor(if (onWallpaperDark(ctx, s)) android.R.color.system_accent1_700 else android.R.color.system_accent1_100)
        s.glass -> ctx.getColor(R.color.glass_accent)
        bgLight(s) -> ctx.getColor(android.R.color.system_accent1_600)
        else -> ctx.getColor(android.R.color.system_accent1_200)
    }

    /** 앨범 커버가 없을 때 레코드 라벨 색: 배경화면 색을 따라감 */
    fun label(ctx: Context): Int = ctx.getColor(android.R.color.system_accent1_400)

    /**
     * 위젯 바탕(bg_image). visible=false면 바탕을 숨김.
     * 테두리를 켜거나 모서리 둥글기를 직접 고르면 바탕을 그려 넣고, 아니면 시스템 기본 곡률 그림을 씀
     */
    fun applyBackground(ctx: Context, rv: RemoteViews, s: WidgetStyle, wDp: Float, hDp: Float, visible: Boolean = true) {
        val bordered = visible && s.border != BorderFx.NONE && s.borderDp > 0
        if (bordered) {
            // 테두리는 바탕 투명도와 상관없이 또렷하게: 투명도는 그림 안의 바탕에만 넣고 전체는 불투명으로
            rv.setImageViewBitmap(R.id.bg_image, borderedBg(ctx, s, wDp, hDp))
            rv.setInt(R.id.bg_image, "setColorFilter", 0)
            rv.setInt(R.id.bg_image, "setImageAlpha", 255)
            return
        }
        if (s.corner >= 0 && visible) {
            rv.setImageViewBitmap(R.id.bg_image, roundedBg(ctx, s, wDp, hDp))
            rv.setInt(R.id.bg_image, "setColorFilter", 0)
        } else if (s.glass) {
            rv.setImageViewResource(R.id.bg_image, R.drawable.widget_bg_glass)
            rv.setInt(R.id.bg_image, "setColorFilter", 0)            // 색 덮어쓰기 없음 (투명색 SRC_ATOP = 원본 그대로)
        } else {
            rv.setImageViewResource(R.id.bg_image, R.drawable.widget_bg)
            rv.setInt(R.id.bg_image, "setColorFilter", WidgetPrefs.bgColor(s))
        }
        rv.setInt(R.id.bg_image, "setImageAlpha", if (visible) WidgetPrefs.alphaOf(s.transparency) else 0)
    }

    private fun scaleOf(ctx: Context, wDp: Float, hDp: Float): Float {
        val dens = WidgetGeom.density(ctx)
        return dens * min(1f, 720f / (max(wDp, hDp) * dens))
    }

    /** 고른 곡률(짧은 변의 %)로 그린 바탕. 유리면 widget_bg_glass와 같은 층(바탕·색 비침·빛 반사·테두리) */
    private fun roundedBg(ctx: Context, s: WidgetStyle, wDp: Float, hDp: Float): Bitmap {
        val k = scaleOf(ctx, wDp, hDp)
        val w = (wDp * k).toInt().coerceAtLeast(2); val h = (hDp * k).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        drawCard(ctx, Canvas(bmp), r, min(w, h) * s.corner.coerceIn(0, 50) / 100f, s, k)
        return bmp
    }

    /** 바탕 카드 한 장 (유리 층 또는 단색) */
    private fun drawCard(ctx: Context, c: Canvas, r: RectF, rad: Float, s: WidgetStyle, k: Float) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = r.width(); val h = r.height()
        if (s.glass) {
            pt.color = ctx.getColor(R.color.glass_fill); c.drawRoundRect(r, rad, rad, pt)
            pt.color = 0
            pt.shader = LinearGradient(r.left, r.top, r.right, r.bottom, ctx.getColor(R.color.glass_tint), 0, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, rad, rad, pt)
            pt.shader = LinearGradient(0f, r.top, 0f, r.top + h / 2f, ctx.getColor(R.color.glass_shine), 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, rad, rad, pt)
            pt.shader = null
            val sw = k
            pt.style = Paint.Style.STROKE; pt.strokeWidth = sw; pt.color = ctx.getColor(R.color.glass_stroke)
            val ri = RectF(r.left + sw / 2, r.top + sw / 2, r.right - sw / 2, r.bottom - sw / 2)
            c.drawRoundRect(ri, max(0f, rad - sw / 2), max(0f, rad - sw / 2), pt)
        } else {
            pt.color = WidgetPrefs.bgColor(s); c.drawRoundRect(r, rad, rad, pt)
        }
    }

    /** 테두리 바탕은 그리는 데 시간이 걸려서 최근 것 몇 장을 기억 (음악 위젯은 자주 다시 그림) */
    private val bgCache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 6
    }

    /** 테두리 스타일을 붙인 바탕: 테두리·그림자 자리만큼 안쪽에 카드, 바탕 투명도는 카드에만 */
    private fun borderedBg(ctx: Context, s: WidgetStyle, wDp: Float, hDp: Float): Bitmap {
        val k = scaleOf(ctx, wDp, hDp)
        val w = (wDp * k).toInt().coerceAtLeast(2); val h = (hDp * k).toInt().coerceAtLeast(2)
        val key = "$w,$h,${s.border},${s.borderDp},${s.borderColor},${s.corner},${s.glass},${s.bg},${s.white},${s.transparency}," +
            "${ctx.getColor(R.color.glass_fill)},${ctx.getColor(R.color.glass_tint)}"
        synchronized(bgCache) { bgCache[key]?.let { return it } }
        return makeBorderedBg(ctx, s, k, w, h).also { synchronized(bgCache) { bgCache[key] = it } }
    }

    private fun makeBorderedBg(ctx: Context, s: WidgetStyle, k: Float, w: Int, h: Int): Bitmap {
        val tPx = s.borderDp * k
        val pad = BorderFx.pad(s.border, tPx, k).coerceAtMost(min(w, h) * 0.2f)
        val r = RectF(pad, pad, w - pad, h - pad)
        val pct = if (s.corner >= 0) s.corner.coerceIn(0, 50) else 11
        val rad = min(r.width(), r.height()) * pct / 100f
        val card = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cc = Canvas(card)
        val alpha = if (bgHidden(s)) 0 else WidgetPrefs.alphaOf(s.transparency)
        if (alpha > 0) {
            cc.saveLayerAlpha(null, alpha)
            drawCard(ctx, cc, r, rad, s, k)
            cc.restore()
        }
        // 테두리는 카드 모양을 따라감 (바탕이 투명해도)
        val shape = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(shape).drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
        return BorderFx.apply(card, s.border, tPx, s.borderColor, k, shape)
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
        if (s.glass && s.fg == AUTO && !bgHidden(s)) {
            rv.setImageViewResource(id, AUTO_ICONS[icon] ?: icon)
            rv.setInt(id, "setColorFilter", 0)
        } else {
            rv.setImageViewResource(id, icon)
            rv.setInt(id, "setColorFilter", accent(ctx, s))
        }
    }

    /**
     * 재생 버튼: 강조색 원 + 그 위 대비 색 아이콘. 유리 + 자동 색이면 둘 다 시스템 색 자원이라
     * 런처가 다크 모드·배경화면에 맞춰 칠함
     */
    fun setPlay(ctx: Context, rv: RemoteViews, playing: Boolean, s: WidgetStyle) {
        if (s.glass && s.fg == AUTO && !bgHidden(s)) {
            rv.setImageViewResource(R.id.play_bg, R.drawable.play_circle_auto)
            rv.setInt(R.id.play_bg, "setColorFilter", 0)
            rv.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_pause_on else R.drawable.ic_play_on)
            rv.setInt(R.id.btn_play, "setColorFilter", 0)
        } else {
            val a = accent(ctx, s)
            rv.setImageViewResource(R.id.play_bg, R.drawable.play_circle)
            rv.setInt(R.id.play_bg, "setColorFilter", a)
            rv.setImageViewResource(R.id.btn_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            rv.setInt(R.id.btn_play, "setColorFilter", if (android.graphics.Color.luminance(a) > 0.55f) 0xFF1B1B1F.toInt() else 0xFFFFFFFF.toInt())
        }
    }

    /** 글자 색. 유리 + 자동이면 시스템 색 자원으로 지정해 런처 쪽에서 저절로 바뀜 */
    fun setTextColor(ctx: Context, rv: RemoteViews, id: Int, s: WidgetStyle, sub: Boolean = false) {
        if (s.glass && s.fg == AUTO && !bgHidden(s)) {
            rv.setColorStateList(id, "setTextColor", if (sub) R.color.glass_sub else R.color.glass_text)
        } else {
            rv.setTextColor(id, if (sub) sub(ctx, s) else text(ctx, s))
        }
    }

    /** 다크 모드·배경화면 색이 바뀌었는지 알아보는 값 */
    fun signature(ctx: Context): String =
        "${night(ctx)}|${ctx.getColor(R.color.glass_accent)}|${ctx.getColor(R.color.glass_fill)}|${wallpaperWantsDarkText(ctx)}"
}
