package com.kwansik.vinylwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 4×2 날씨 위젯 (2×2를 옆으로 늘리거나 1×4를 아래로 늘리면 자동으로 이 배치).
 * 위쪽 = 날짜와 지금 날씨를 크게, 아래쪽 = 3시간 간격 예보 6칸.
 *  - ART  : 캠핑 풍경이 위젯을 꽉 채우는 카드 (LP 재킷·하늘 원·파노라마)
 *  - PLAIN: 그림 없이 글자 + 작은 텐트 (텐트·큰 날짜·미니멀·다이얼·달력·심플)
 *  - CHAR : 병맛 캐릭터가 크게 (캐릭터 포스터·카드·헤드라인). 카드면 날씨마다 색이 바뀌는 카드
 * 크기는 모두 위젯 높이(H) 비율. 작은 글자는 11dp 아래로 내려가지 않음
 */
class BigScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int, private val accent: Int,
    private val quirky: Boolean, private val customFg: Boolean
) {
    companion object {
        const val ART = 0; const val PLAIN = 1; const val CHAR = 2

        /** 2×2를 옆으로 늘렸거나 1×4를 아래로 늘렸으면 4×2 배치 */
        fun isBig(wDp: Float, hDp: Float) = hDp >= 120f && wDp >= hDp * 1.55f

        /** 아래 예보 줄 높이(dp): 이 부분을 누르면 날씨 사이트 */
        fun rowDp(hDp: Float) = hDp * 0.4f
    }

    val bitmap: Bitmap
    private val c: Canvas
    private val W: Float
    private val H: Float
    private val k: Float

    init {
        val dens = WidgetGeom.density(ctx)
        k = dens * min(1f, 1100f / (max(wDp, hDp) * dens))
        bitmap = Bitmap.createBitmap((wDp * k).toInt().coerceAtLeast(1), (hDp * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap); W = bitmap.width.toFloat(); H = bitmap.height.toFloat()
    }

    private val SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MED = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val today = LocalDate.now()
    private val md = "${today.monthValue}.${today.dayOfMonth}"
    private val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)

    private fun small(v: Float) = max(v, 11f * k)
    private fun p(size: Float, tf: Typeface, col: Int, a: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = a }
    private fun top(s: String, x: Float, y: Float, pt: Paint) = c.drawText(s, x, y - pt.fontMetrics.ascent, pt)
    private fun mid(s: String, x: Float, y: Float, pt: Paint) { val fm = pt.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, pt) }
    private fun icon(res: Int, cx: Float, cy: Float, size: Float) {
        Assets.get(ctx, res)?.let { c.drawBitmap(it, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)) }
    }

    /** 예보 6칸: [시각 / 아이콘 / 기온]. 예보가 없으면 날씨 이름과 한마디 */
    private fun hourlyRow(d: WeatherData, area: RectF, ink: Int, soft: Int) {
        val hs = d.hourly
        if (hs.isEmpty()) {
            mid("${d.cond()} · ${Quips.of(d)}", area.centerX(), area.centerY(), p(small(area.height() * 0.2f), MED, soft, Paint.Align.CENTER))
            return
        }
        val n = min(6, hs.size); val cw = area.width() / n
        val ic = min(area.height() * 0.42f, cw * 0.62f)
        hs.take(n).forEachIndexed { i, h ->
            val cx = area.left + cw * (i + 0.5f)
            mid(h.label(), cx, area.top + area.height() * 0.17f, p(small(area.height() * 0.16f), MED, soft, Paint.Align.CENTER))
            icon(if (quirky) h.iconFun() else h.icon3d(), cx, area.top + area.height() * 0.5f, ic)
            mid(h.tempText(), cx, area.top + area.height() * 0.84f, p(small(area.height() * 0.19f), BOLD, ink, Paint.Align.CENTER))
        }
    }

    fun draw(look: Int, d: WeatherData, corner: Int = -1, glassArt: Boolean = true, artShadow: Boolean = false,
             cardAlpha: Int = 0, cardColor: Int = 0) {
        val msg = d.message()
        when (look) {
            ART -> {
                val ins = if (artShadow) 4f * k else 0f
                val r = RectF(ins, ins, W - ins, H - ins)
                val rad = r.height() * (if (corner < 0) 11 else corner) / 100f
                if (artShadow) GlassArt.shadow(c, r, rad)
                c.save(); c.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })
                Assets.get(ctx, Scenes.panorama(d.skyKind(), d.isNight(), quirky))?.let { b ->
                    // 위젯 비율에 맞게 가운데를 잘라서 채움 (캐릭터·텐트가 가운데에 있음)
                    val ar = r.width() / r.height(); val br = b.width.toFloat() / b.height
                    val src = if (br > ar) { val w = (b.height * ar).toInt(); Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height) }
                              else { val hh = (b.width / ar).toInt(); Rect(0, b.height - hh, b.width, b.height) }
                    c.drawBitmap(b, src, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                }
                val art = ArtText(c, quirky, if (customFg) fg else null, sub, !d.isNight() && d.skyKind() != "rain",
                    d.skyKind() == "snow" && !d.isNight())
                if (art.needsScrim) {                          // 밝은 글자 뒤(위쪽 양 끝)만 옅게 눌러 줌
                    for (cx in floatArrayOf(r.left, r.right)) c.drawRect(r, Paint().apply {
                        shader = RadialGradient(cx, r.top, r.height() * 0.9f, intArrayOf(0x52000000, 0x24000000, 0x00000000),
                            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                    })
                }
                // 아래 예보 줄 어둠막 → 흰 글자가 어떤 그림 위에서도 읽힘
                val rowTop = r.bottom - r.height() * 0.4f
                c.drawRect(r.left, rowTop - r.height() * 0.12f, r.right, r.bottom, Paint().apply {
                    shader = LinearGradient(0f, rowTop - r.height() * 0.12f, 0f, r.bottom,
                        intArrayOf(0x00000000, 0x66000000, 0x99000000.toInt()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
                })
                c.restore()
                if (glassArt) GlassArt.draw(c, r, rad)
                val h = r.height(); val m = h * 0.1f
                top(md, r.left + m, r.top + h * 0.07f, p(h * 0.22f, SERIF, art.main))
                top(wk, r.left + m * 1.04f, r.top + h * 0.33f, p(small(h * 0.075f), MED, art.sub))
                if (msg != null) top(msg, r.right - m, r.top + h * 0.1f, p(small(h * 0.08f), BOLD, art.main, Paint.Align.RIGHT))
                else {
                    top(d.tempText(), r.right - m, r.top + h * 0.07f, p(h * 0.22f, SERIF, art.main, Paint.Align.RIGHT))
                    top(d.rangeText().replace(" ", ""), r.right - m, r.top + h * 0.33f, p(small(h * 0.075f), MED, art.sub, Paint.Align.RIGHT))
                }
                val white = if (customFg) fg else 0xFFFFFFFF.toInt()
                val soft = if (customFg) sub else 0xCCFFFFFF.toInt()
                hourlyRow(d, RectF(r.left + m * 0.5f, rowTop, r.right - m * 0.5f, r.bottom - h * 0.04f), white, soft)
            }
            CHAR -> {
                val col = if (cardColor != 0) CharacterLayouts.cardColors(cardColor) else CharacterLayouts.cardColors(d)
                val card = cardAlpha > 0
                if (card) c.drawRoundRect(RectF(0f, 0f, W, H), min(H * 0.16f, 28f * k), min(H * 0.16f, 28f * k), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, W * 0.4f, H, col[0], col[1], Shader.TileMode.CLAMP); alpha = cardAlpha
                })
                val onCard = card && cardAlpha >= 110 && !customFg
                val ink = if (onCard) col[2] else fg
                val soft = if (onCard) col[3] else sub
                val m = H * 0.09f
                val upper = H * 0.6f
                val cs = upper * 0.92f
                icon(d.iconFun(), m + cs / 2, upper / 2 + H * 0.02f, cs)
                val tx = m + cs + H * 0.06f
                if (msg != null) mid(msg, tx, upper / 2, p(small(H * 0.08f), BOLD, ink))
                else {
                    mid(d.tempText(), tx, upper * 0.42f, p(H * 0.27f, BLACK, ink))
                    mid(Quips.of(d), tx + H * 0.01f, upper * 0.82f, p(small(H * 0.075f), BOLD, soft))
                }
                top(md, W - m, m * 0.9f, p(H * 0.15f, BLACK, ink, Paint.Align.RIGHT))
                top(wk, W - m, m * 0.9f + H * 0.19f, p(small(H * 0.07f), MED, soft, Paint.Align.RIGHT))
                c.drawRect(m, upper + H * 0.01f, W - m, upper + H * 0.01f + max(1f, k), Paint().apply { color = (soft and 0x00FFFFFF) or (0x40 shl 24) })
                hourlyRow(d, RectF(m * 0.5f, upper + H * 0.03f, W - m * 0.5f, H - H * 0.04f), ink, soft)
            }
            else -> { // PLAIN
                val m = H * 0.09f
                val upper = H * 0.58f
                top(md, m, m * 0.7f, p(H * 0.3f, SERIF, fg))
                top(wk, m * 1.05f, m * 0.7f + H * 0.36f, p(small(H * 0.08f), MED, sub))
                val dateW = p(H * 0.3f, SERIF, fg).measureText(md)
                val lit = d.isNight() || d.skyKind() == "rain"
                Assets.get(ctx, Scenes.tentIcon(lit, quirky))?.let { b ->
                    val th = upper * 0.5f; val tw = th * b.width / b.height
                    val x = m + dateW + H * 0.1f
                    c.drawBitmap(b, null, RectF(x, upper - th - H * 0.02f, x + tw, upper - H * 0.02f), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                }
                val right = W - m
                if (msg != null) mid(msg, right, upper / 2, p(small(H * 0.08f), BOLD, fg, Paint.Align.RIGHT))
                else {
                    val tp = p(H * 0.24f, SERIF, fg, Paint.Align.RIGHT)
                    top(d.tempText(), right, m * 0.8f, tp)
                    top(d.rangeText().replace(" ", ""), right, m * 0.8f + H * 0.3f, p(small(H * 0.075f), MED, sub, Paint.Align.RIGHT))
                    val ic = H * 0.3f
                    icon(if (quirky) d.iconFun() else d.icon3d(), right - tp.measureText(d.tempText()) - H * 0.03f - ic / 2, m * 0.8f + H * 0.13f, ic)
                }
                c.drawRect(m, upper + H * 0.01f, W - m, upper + H * 0.01f + max(1f, k), Paint().apply { color = (sub and 0x00FFFFFF) or (0x40 shl 24) })
                hourlyRow(d, RectF(m * 0.5f, upper + H * 0.03f, W - m * 0.5f, H - H * 0.04f), fg, sub)
            }
        }
    }
}
