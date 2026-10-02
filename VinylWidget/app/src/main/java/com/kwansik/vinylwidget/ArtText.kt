package com.kwansik.vinylwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.max

/**
 * 그림(풍경) 위에 얹는 글자.
 *  - 글자색: 사용자가 고른 색 > 병맛이면 흰색 > 하늘 밝기에 맞춘 색(밝은 낮=진한 글자, 밤·비=크림색)
 *  - 병맛: 만화 스티커처럼 굵은 테두리(밝은 글자엔 잉크색, 어두운 글자엔 흰색) → 굵은 선 그림과 겹쳐도 또렷
 *  - 기본: 글자와 반대 밝기의 은은한 번짐
 */
class ArtText(
    private val c: Canvas, private val quirky: Boolean,
    custom: Int?, customSub: Int, darkText: Boolean
) {
    companion object {
        const val INK = 0xFF2B2622.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private val CREAM = 0xFFFBF1DC.toInt()
        private val CREAM_SUB = 0xFFD9CFC0.toInt()
        private val DARK = 0xFF23262E.toInt()
        private val DARK_SUB = 0xFF3E434D.toInt()
    }

    val main: Int = custom ?: if (quirky) WHITE else if (darkText) DARK else CREAM
    /** 보조 글자(요일 등). 병맛 테두리 글자는 반투명이면 테두리가 비치므로 불투명 색만 씀 */
    val sub: Int = when {
        custom != null -> if (quirky) custom else customSub
        quirky -> 0xFFF4EDE1.toInt()
        darkText -> DARK_SUB
        else -> CREAM_SUB
    }

    private fun light(col: Int) = Color.luminance(col or 0xFF000000.toInt()) > 0.45f

    /** y = 글자 기준선(baseline) */
    fun draw(s: String, x: Float, y: Float, p: Paint) {
        val light = light(p.color)
        if (quirky) {
            val o = Paint(p).apply {
                clearShadowLayer()
                style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
                strokeWidth = max(p.textSize * 0.14f, 3f)
                color = if (light) INK else WHITE
            }
            c.drawText(s, x, y, o)
            c.drawText(s, x, y, Paint(p).apply { clearShadowLayer() })
        } else {
            val q = Paint(p).apply {
                setShadowLayer(p.textSize * 0.12f, 0f, p.textSize * 0.02f, if (light) 0x66000000 else 0x8CFFFFFF.toInt())
            }
            c.drawText(s, x, y, q)
        }
    }

    /** 위쪽 기준(top)에 그림 */
    fun top(s: String, x: Float, yTop: Float, p: Paint) = draw(s, x, yTop - p.fontMetrics.ascent, p)

    /** 세로 가운데(y)에 그림 */
    fun mid(s: String, x: Float, y: Float, p: Paint) {
        val fm = p.fontMetrics; draw(s, x, y - (fm.ascent + fm.descent) / 2f, p)
    }
}
