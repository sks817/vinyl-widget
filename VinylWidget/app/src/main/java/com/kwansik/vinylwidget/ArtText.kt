package com.kwansik.vinylwidget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * 그림(풍경) 위에 얹는 글자.
 *  - 글자색: 사용자가 고른 색 > 병맛이면 흰색 > 하늘 밝기에 맞춘 색(밝은 낮=진한 글자, 밤·비=크림색)
 *  - 테두리·그림자 없이 깨끗한 글자만
 *  - 밝은 글자 뒤 그림은 각 디자인이 옅은 어둠막(scrim)으로 살짝 눌러 줌 (needsScrim)
 */
class ArtText(
    private val c: Canvas, private val quirky: Boolean,
    custom: Int?, customSub: Int, darkText: Boolean
) {
    companion object {
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val CREAM = 0xFFFBF1DC.toInt()
        private const val CREAM_SUB = 0xFFD9CFC0.toInt()
        private const val DARK = 0xFF23262E.toInt()
        private const val DARK_SUB = 0xFF3E434D.toInt()

        fun light(col: Int) = Color.luminance(col or 0xFF000000.toInt()) > 0.45f
    }

    val main: Int = custom ?: if (quirky) WHITE else if (darkText) DARK else CREAM
    /** 보조 글자(요일 등) */
    val sub: Int = when {
        custom != null -> customSub
        quirky -> 0xE6FFFFFF.toInt()
        darkText -> DARK_SUB
        else -> CREAM_SUB
    }
    /** 글자가 밝아서 뒤 그림을 살짝 어둡게 눌러 줘야 하는가 */
    val needsScrim: Boolean get() = light(main)

    /** y = 글자 기준선(baseline) */
    fun draw(s: String, x: Float, y: Float, p: Paint) {
        c.drawText(s, x, y, Paint(p).apply { clearShadowLayer() })
    }

    /** 위쪽 기준(top)에 그림 */
    fun top(s: String, x: Float, yTop: Float, p: Paint) = draw(s, x, yTop - p.fontMetrics.ascent, p)

    /** 세로 가운데(y)에 그림 */
    fun mid(s: String, x: Float, y: Float, p: Paint) {
        val fm = p.fontMetrics; draw(s, x, y - (fm.ascent + fm.descent) / 2f, p)
    }
}
