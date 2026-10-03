package com.kwansik.vinylwidget

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.SweepGradient
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 스티커식 테두리: 그림(투명 배경)의 '모양'을 따라 바깥으로 두께만큼 테두리를 붙임.
 * 영수증 지그재그·하트·스티커 그림처럼 어떤 모양이든 외곽을 따라감. 두꺼울수록 진짜 스티커처럼.
 * 그림은 테두리·그림자 자리(pad)만큼 안쪽에 그려 넘겨야 함
 */
object BorderFx {
    const val NONE = 0
    const val SOLID = 1
    const val LIQUID = 2
    const val BRUTAL = 3
    const val BRUTAL_COLOR = 4
    const val AURORA = 5
    const val HOLO = 6
    const val STITCH = 7
    const val DOUBLE = 8
    val NAMES = arrayOf("없음", "단색 스티커", "리퀴드 글래스", "네오브루탈", "네오브루탈 컬러", "오로라", "홀로그램", "스티치", "이중선")

    /** 사용자가 색을 고르는 스타일 (나머지는 효과 자체가 색) */
    fun usesColor(style: Int) = style == SOLID || style == BRUTAL_COLOR || style == STITCH || style == DOUBLE

    /** 고를 수 있는 색. 0 = 스타일 기본색 */
    val COLORS = intArrayOf(
        0, 0xFFFFFFFF.toInt(), 0xFF1B1B1B.toInt(), 0xFFFF5CA8.toInt(), 0xFFF6A5C0.toInt(), 0xFF8FD3FF.toInt(),
        0xFF5EEAD4.toInt(), 0xFFFFE066.toInt(), 0xFFA78BFA.toInt(), 0xFFFF7A45.toInt(), 0xFFD4A63A.toInt()
    )

    fun colorOf(style: Int, c: Int): Int = if (c != 0) c else when (style) {
        BRUTAL_COLOR -> 0xFFFF5CA8.toInt()
        STITCH -> 0xFFF6A5C0.toInt()
        DOUBLE -> 0xFF2A2A2A.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    /** 테두리 + 그림자가 차지하는 바깥 여백(px). 그림은 이만큼 안쪽에 */
    fun pad(style: Int, tPx: Float, dens: Float): Float = when (style) {
        NONE -> 0f
        BRUTAL, BRUTAL_COLOR -> tPx + 2f * dens + tPx * 0.6f + dens
        else -> tPx + 6f * dens
    }

    /**
     * content(투명 배경 그림)에 테두리를 붙인 새 비트맵 (크기 같음).
     * shape = 테두리를 따라 그릴 모양 (없으면 content 모양). 바탕이 거의 투명한 위젯 카드처럼 그림과 모양이 다를 때
     */
    fun apply(content: Bitmap, style: Int, tPx: Float, color: Int, dens: Float, shape: Bitmap? = null): Bitmap {
        if (style == NONE) return content
        val w = content.width; val h = content.height
        val t = max(tPx, 0.5f)
        val sil = silhouette(shape ?: content)
        val ring = dilate(sil, t)
        val col = colorOf(style, color)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        // 그림 아래쪽 층(그림자·테두리)은 그림 자리에서 지워서, 반투명 바탕 밑으로 비치지 않게
        fun under(b: Bitmap, dx: Float = 0f, dy: Float = 0f) {
            val l = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val lc = Canvas(l)
            lc.drawBitmap(b, dx, dy, null)
            lc.drawBitmap(sil, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
            c.drawBitmap(l, 0f, 0f, null)
        }
        when (style) {
            SOLID -> { under(shadow(ring, 0x40000000, 6f * dens), 0f, 2.5f * dens); under(tint(ring, col)) }
            LIQUID -> {
                under(shadow(ring, 0x40001428, 8f * dens), 0f, 3f * dens)
                under(tint(ring, 0x59FFFFFF))
                under(shade(ring, LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(0xFAFFFFFF.toInt(), 0x59FFFFFF, 0x4DFFFFFF, 0xE6FFFFFF.toInt()), floatArrayOf(0f, 0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)))
            }
            BRUTAL, BRUTAL_COLOR -> {
                val o = 2f * dens + t * 0.6f
                under(tint(ring, if (style == BRUTAL) 0xFF111111.toInt() else col), o, o)
                under(tint(ring, 0xFF111111.toInt()))
            }
            AURORA -> {
                under(shadow(ring, 0x99A78BFA.toInt(), 8f * dens))
                under(shade(ring, sweep(w, h, intArrayOf(0xFFFF7EB3.toInt(), 0xFFA78BFA.toInt(), 0xFF60A5FA.toInt(), 0xFF5EEAD4.toInt(), 0xFFFDE68A.toInt(), 0xFFFF7EB3.toInt()), -34f)))
            }
            HOLO -> {
                under(shadow(ring, 0x38000000, 6f * dens), 0f, 2.5f * dens)
                val holo = shade(ring, sweep(w, h, intArrayOf(0xFFD8F3FF.toInt(), 0xFFFFD1F3.toInt(), 0xFFFFF1B8.toInt(), 0xFFCCFFE6.toInt(),
                    0xFFD3D9FF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD1F3.toInt(), 0xFFD8F3FF.toInt()), 17f))
                Canvas(holo).drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), intArrayOf(0, 0xE6FFFFFF.toInt(), 0), floatArrayOf(0.4f, 0.5f, 0.6f), Shader.TileMode.CLAMP)
                })
                under(holo)
            }
            STITCH -> {
                under(shadow(ring, 0x40000000, 6f * dens), 0f, 2.5f * dens)
                under(tint(ring, col))
                // 테두리 한가운데에 흰 바느질 점선: 가는 띠를 각도 줄무늬로 끊어 점선처럼
                val band = dilate(sil, t * 0.62f)
                Canvas(band).drawBitmap(dilate(sil, t * 0.38f), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
                val n = (w / dens / 3.2f).toInt().coerceIn(24, 160) * 2
                val cols = IntArray(n * 2) { if ((it / 2) % 2 == 0) 0xFFFFFFFF.toInt() else 0 }
                val pos = FloatArray(n * 2) { ((it + 1) / 2).toFloat() / n }
                under(shade(band, SweepGradient(w / 2f, h / 2f, cols, pos)))
            }
            DOUBLE -> {
                under(shadow(ring, 0x2E000000, 5f * dens), 0f, 2f * dens)
                under(tint(dilate(sil, t + 0.9f * dens), col))
                under(tint(ring, 0xFFFFFFFF.toInt()))
            }
        }
        c.drawBitmap(content, 0f, 0f, null)
        return out
    }

    /** 조금이라도 보이는 곳은 불투명한 검정 모양으로 (가장자리 부드러움은 조금 남김) */
    private fun silhouette(src: Bitmap): Bitmap {
        val b = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val m = ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 6f, 0f))
        Canvas(b).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(m) })
        return b
    }

    /** 모양을 사방으로 r만큼 밀어 찍어 넓힘 */
    private fun dilate(sil: Bitmap, r: Float): Bitmap {
        val b = Bitmap.createBitmap(sil.width, sil.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        c.drawBitmap(sil, 0f, 0f, p)
        val steps = (r * 1.4f).toInt().coerceIn(16, 48)
        for (rr in floatArrayOf(r, r * 0.66f, r * 0.33f)) {
            if (rr < 0.5f) continue
            for (i in 0 until steps) {
                val a = Math.PI * 2 * i / steps
                c.drawBitmap(sil, (rr * cos(a)).toFloat(), (rr * sin(a)).toFloat(), p)
            }
        }
        return b
    }

    private fun tint(mask: Bitmap, col: Int): Bitmap {
        val b = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        Canvas(b).drawBitmap(mask, 0f, 0f, Paint().apply { colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN) })
        return b
    }

    private fun shade(mask: Bitmap, sh: Shader): Bitmap {
        val b = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawBitmap(mask, 0f, 0f, null)
        c.drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), Paint().apply { shader = sh; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        return b
    }

    private fun shadow(mask: Bitmap, col: Int, blur: Float): Bitmap {
        val b = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { maskFilter = BlurMaskFilter(max(1f, blur), BlurMaskFilter.Blur.NORMAL) }
        val off = IntArray(2)
        val a = mask.extractAlpha(p, off)
        Canvas(b).drawBitmap(a, off[0].toFloat(), off[1].toFloat(), Paint().apply { color = col })
        return b
    }

    private fun sweep(w: Int, h: Int, cols: IntArray, rotDeg: Float): Shader =
        SweepGradient(w / 2f, h / 2f, cols, null).apply { setLocalMatrix(Matrix().apply { setRotate(rotDeg, w / 2f, h / 2f) }) }
}
