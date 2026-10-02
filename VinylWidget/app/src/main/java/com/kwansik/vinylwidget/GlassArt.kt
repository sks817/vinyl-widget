package com.kwansik.vinylwidget

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.max
import kotlin.math.min

/**
 * 그림 위에 얹는 '유리 캡슐' 효과: 그림이 유리관 안에 담긴 것처럼 보이게 함.
 *  - 위쪽 빛 / 아래쪽 유리 두께 그림자, 좌우 가장자리 굴절, 흐린 반사광 띠, 얇은 테두리 빛
 * 그림을 그린 다음, 글자를 쓰기 전에 호출
 */
object GlassArt {

    fun draw(c: Canvas, r: RectF, radius: Float) {
        val w = r.width(); val h = r.height(); val s = min(w, h)
        val rad = radius.coerceIn(0f, s / 2)
        c.save()
        c.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })

        // 위→아래: 위쪽은 빛을 받아 밝고, 아래쪽은 유리 두께만큼 살짝 어두움
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, r.top, 0f, r.bottom,
                intArrayOf(0x8CFFFFFF.toInt(), 0x14FFFFFF, 0x00FFFFFF, 0x00000000, 0x38000000),
                floatArrayOf(0f, 0.18f, 0.5f, 0.86f, 1f), Shader.TileMode.CLAMP)
        })

        // 좌우 가장자리: 둥근 유리면에서 굴절된 빛
        val edge = max(s * 0.06f, min(w, h * 4f) * 0.06f)
        c.drawRect(r.left, r.top, r.left + edge, r.bottom, Paint().apply {
            shader = LinearGradient(r.left, 0f, r.left + edge, 0f, 0x59FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })
        c.drawRect(r.right - edge, r.top, r.right, r.bottom, Paint().apply {
            shader = LinearGradient(r.right, 0f, r.right - edge, 0f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        })

        // 위쪽 반사광 띠 (흐리게 번진 빛)
        val sw = s * 0.05f
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x8CFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = sw
            strokeCap = Paint.Cap.ROUND; maskFilter = BlurMaskFilter(sw * 0.6f, BlurMaskFilter.Blur.NORMAL)
        }
        val y = r.top + s * 0.12f
        val x0 = r.left + max(rad * 0.75f, w * 0.06f)
        c.drawPath(Path().apply {
            moveTo(x0, y + s * 0.02f)
            quadTo(r.left + w * 0.32f, y - s * 0.06f, r.left + w * 0.6f, y - s * 0.03f)
        }, band)
        band.alpha = 0x80
        c.drawLine(r.left + w * 0.64f, y - s * 0.03f, r.left + w * 0.66f, y - s * 0.03f, band)
        c.restore()

        // 테두리 빛
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = max(1f, s * 0.008f)
        }
        val half = rim.strokeWidth / 2
        val rr = RectF(r.left + half, r.top + half, r.right - half, r.bottom - half)
        c.drawRoundRect(rr, max(0f, rad - half), max(0f, rad - half), rim)
    }
}
