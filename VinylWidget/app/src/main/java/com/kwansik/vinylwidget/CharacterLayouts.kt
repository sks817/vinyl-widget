package com.kwansik.vinylwidget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 캐릭터(병맛) 디자인 3종 — 2×2와 1×4 공통 문법. 모든 구성에 날짜가 들어감
 *  A. 포스터     : 큰 캐릭터 + 큰 기온 (+ 작은 날짜). 글자 최소
 *  B. 컬러 카드  : 날씨마다 색이 바뀌는 카드 + 캐릭터 + 큰 기온 + 한마디 한 줄. 카드 진하기 = 배경 투명도
 *  C. 헤드라인   : 한마디가 큰 제목 + 캐릭터 + 기온 (+ 작은 날짜)
 * 시안(art/samples)과 같은 비율. 2×2는 위젯 한 변(S), 1×4는 위젯 높이(h) 기준
 */
class CharacterLayouts(
    private val ctx: Context, private val c: Canvas, private val W: Float, private val H: Float, private val k: Float,
    private val fg: Int, private val sub: Int, private val shadow: Int
) {
    companion object {
        const val POSTER = 0; const val CARD = 1; const val HEADLINE = 2

        /** 컬러 카드 색: [위, 아래, 글자, 보조 글자] — 하늘 종류 × 낮/밤 */
        fun cardColors(d: WeatherData): IntArray {
            val n = d.isNight()
            return when (d.skyKind()) {
                "rain" -> if (n) intArrayOf(0xFF2C3A52.toInt(), 0xFF161E2D.toInt(), 0xFFF2F6FB.toInt(), 0xA6F2F6FB.toInt())
                          else intArrayOf(0xFFDDE9F6.toInt(), 0xFFA9C2DE.toInt(), 0xFF1E2C3D.toInt(), 0x991E2C3D.toInt())
                "snow" -> if (n) intArrayOf(0xFF3D4A72.toInt(), 0xFF202A4A.toInt(), 0xFFF4F7FF.toInt(), 0xA6F4F7FF.toInt())
                          else intArrayOf(0xFFF4F8FC.toInt(), 0xFFD3E2F1.toInt(), 0xFF22324A.toInt(), 0x9922324A.toInt())
                "cloudy" -> if (n) intArrayOf(0xFF3A4256.toInt(), 0xFF1E2330.toInt(), 0xFFF3F4F7.toInt(), 0xA6F3F4F7.toInt())
                            else intArrayOf(0xFFEEF1F4.toInt(), 0xFFC9D2DC.toInt(), 0xFF26303A.toInt(), 0x9926303A.toInt())
                else -> if (n) intArrayOf(0xFF34447A.toInt(), 0xFF1B2240.toInt(), 0xFFFFFBEF.toInt(), 0xA6FFFBEF.toInt())
                        else intArrayOf(0xFFFFF1C2.toInt(), 0xFFFFD27A.toInt(), 0xFF3A2E12.toInt(), 0x993A2E12.toInt())
            }
        }
    }

    private val BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MED = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val today = LocalDate.now()
    private val md = "${today.monthValue}.${today.dayOfMonth}"
    private val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    private val wks = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

    /** shade = 바탕 없이 배경화면 위에 쓸 때만 옅은 그림자 */
    private fun p(size: Float, tf: Typeface, col: Int, align: Paint.Align = Paint.Align.LEFT, shade: Boolean = true) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = tf; color = col; textAlign = align
            if (shade && shadow != 0) setShadowLayer(size * 0.14f, 0f, size * 0.03f, shadow)
        }

    /** 위쪽 기준(top)에 글자를 씀 */
    private fun top(s: String, x: Float, y: Float, paint: Paint) = c.drawText(s, x, y - paint.fontMetrics.ascent, paint)
    /** 세로 가운데(y)에 글자를 씀 */
    private fun mid(s: String, x: Float, y: Float, paint: Paint) {
        val fm = paint.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2, paint)
    }
    /** 아래 기준선(baseline)에 글자를 씀 */
    private fun base(s: String, x: Float, y: Float, paint: Paint) = c.drawText(s, x, y, paint)

    private fun character(d: WeatherData, cx: Float, cy: Float, size: Float) {
        Assets.get(ctx, d.iconFun())?.let {
            c.drawBitmap(it, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun card(r: RectF, radius: Float, d: WeatherData, alpha: Int) {
        val col = cardColors(d)
        c.drawRoundRect(r, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(r.left, r.top, r.right * 0.4f, r.bottom, col[0], col[1], Shader.TileMode.CLAMP)
            this.alpha = alpha
        })
    }

    private fun temp(d: WeatherData) = d.message() ?: d.tempText()

    // ---------------- 2×2 ----------------
    fun draw2x2(style: Int, d: WeatherData, cardAlpha: Int) {
        val S = min(W, H)
        val ox = (W - S) / 2; val oy = (H - S) / 2               // 정사각형 칸을 가운데에
        val m = S * 0.082f                                          // 바깥 여백 (170dp 기준 14dp)
        val tempSize = if (d.message() != null) S * 0.08f else 0f
        when (style) {
            POSTER -> {
                top("$md $wks", ox + m, oy + m, p(S * 0.07f, MED, sub))
                val cs = S * 0.56f
                character(d, ox + S / 2, oy + m + S * 0.02f + cs / 2, cs)
                base(temp(d), ox + S / 2, oy + S - m, p(if (tempSize > 0) tempSize else S * 0.235f, BLACK, fg, Paint.Align.CENTER))
            }
            CARD -> {
                val col = cardColors(d)
                card(RectF(ox, oy, ox + S, oy + S), S * 0.14f, d, cardAlpha)
                val ink = if (cardAlpha >= 110) col[2] else fg          // 카드를 아주 옅게 하면 배경 기준 글자색
                val soft = if (cardAlpha >= 110) col[3] else sub
                val shade = cardAlpha < 110
                top("$md $wk", ox + m, oy + m, p(S * 0.07f, MED, soft, shade = shade))
                val cs = S * 0.5f
                character(d, ox + S - S * 0.035f - cs / 2, oy + S * 0.06f + cs / 2, cs)
                base(temp(d), ox + m, oy + S - m - S * 0.12f, p(if (tempSize > 0) tempSize else S * 0.26f, BLACK, ink, shade = shade))
                val qp = p(S * 0.07f, BOLD, soft, shade = shade)
                if (d.message() == null) base(TextWrap.wrap(Quips.of(d), qp, S - m * 2, 1).first(), ox + m, oy + S - m, qp)
            }
            else -> { // HEADLINE
                top("$md $wk", ox + m, oy + m, p(S * 0.07f, MED, sub))
                val qp = p(S * 0.112f, BLACK, fg)
                val lh = qp.textSize * 1.3f
                TextWrap.wrap(d.message() ?: Quips.of(d), qp, S - m * 2, 2).forEachIndexed { i, line ->
                    top(line, ox + m, oy + m + S * 0.11f + i * lh, qp)
                }
                val cs = S * 0.45f
                character(d, ox + S - S * 0.035f - cs / 2, oy + S - S * 0.035f - cs / 2, cs)
                if (d.message() == null) base(d.tempText(), ox + m, oy + S - m, p(S * 0.21f, BLACK, fg))
            }
        }
    }

    // ---------------- 1×4 ----------------
    fun draw1x4(style: Int, d: WeatherData, cardAlpha: Int) {
        val h = H
        val msg = d.message()
        val small = { v: Float -> max(v, 12f * k) }
        when (style) {
            POSTER -> { // [캐릭터][큰 기온] …… [날짜 / 요일]
                val cs = h * 0.88f
                character(d, h * 0.08f + cs / 2, h / 2, cs)
                val tx = h * 0.08f + cs + h * 0.08f
                if (msg != null) mid(msg, tx, h / 2, p(small(h * 0.16f), BOLD, fg))
                else mid(d.tempText(), tx, h / 2, p(h * 0.5f, BLACK, fg))
                val right = W - h * 0.26f
                mid(md, right, h * 0.42f, p(h * 0.3f, BLACK, fg, Paint.Align.RIGHT))
                mid(wk, right, h * 0.74f, p(small(h * 0.15f), MED, sub, Paint.Align.RIGHT))
            }
            CARD -> { // 카드: [캐릭터][큰 기온 / 한마디] …… [날짜 / 요일]
                val col = cardColors(d)
                card(RectF(0f, 0f, W, h), min(h * 0.36f, 28f * k), d, cardAlpha)
                val ink = if (cardAlpha >= 110) col[2] else fg
                val soft = if (cardAlpha >= 110) col[3] else sub
                val shade = cardAlpha < 110
                val cs = h * 0.84f
                character(d, h * 0.1f + cs / 2, h / 2, cs)
                val tx = h * 0.1f + cs + h * 0.08f
                val right = W - h * 0.3f
                val dp = p(h * 0.28f, BLACK, ink, Paint.Align.RIGHT, shade)
                val wp = p(small(h * 0.14f), MED, soft, Paint.Align.RIGHT, shade)
                mid(md, right, h * 0.42f, dp)
                mid(wk, right, h * 0.74f, wp)
                val limit = right - max(dp.measureText(md), wp.measureText(wk)) - h * 0.25f - tx
                if (msg != null) mid(msg, tx, h / 2, p(small(h * 0.16f), BOLD, ink, shade = shade))
                else {
                    mid(d.tempText(), tx, h * 0.4f, p(h * 0.44f, BLACK, ink, shade = shade))
                    val qp = p(small(h * 0.14f), BOLD, soft, shade = shade)
                    mid(TextWrap.wrap(Quips.of(d), qp, limit, 1).first(), tx + h * 0.02f, h * 0.78f, qp)
                }
            }
            else -> { // HEADLINE: [캐릭터][한마디 크게] …… [기온 / 날짜]
                val cs = h * 0.88f
                character(d, h * 0.08f + cs / 2, h / 2, cs)
                val tx = h * 0.08f + cs + h * 0.1f
                val right = W - h * 0.26f
                val tp = p(h * 0.44f, BLACK, fg, Paint.Align.RIGHT)
                val dp = p(small(h * 0.15f), MED, sub, Paint.Align.RIGHT)
                val dateStr = "$md $wk"
                if (msg == null) mid(d.tempText(), right, h * 0.4f, tp)
                mid(dateStr, right, h * 0.78f, dp)
                val rightW = max(if (msg == null) tp.measureText(d.tempText()) else 0f, dp.measureText(dateStr))
                val qp = p(h * 0.2f, BLACK, fg)
                val lines = TextWrap.wrap(msg ?: Quips.of(d), qp, right - rightW - h * 0.3f - tx, 2)
                val lh = qp.textSize * 1.25f
                lines.forEachIndexed { i, line -> mid(line, tx, h / 2 - (lines.size - 1) * lh / 2 + i * lh, qp) }
            }
        }
    }
}
