package com.kwansik.vinylwidget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.min

/** 투명 배경 위에 원형 앨범 커버(레코드 라벨)를 그림. vinyl_disc.xml의 반지름 62/100과 맞춤 */
object LabelRenderer {
    private const val SIZE = 300
    private const val LABEL_RATIO = 0.62f

    /** quirky = 병맛 테마: 굵은 잉크 테두리 + 왕눈이 스티커 (커버가 없으면 얼굴까지) */
    fun draw(art: Bitmap?, fallback: Int = 0xFFB23A2E.toInt(), quirky: Boolean = false): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = SIZE / 2f
        val lr = cx * LABEL_RATIO
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (art != null) {
            val src = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) else art
            val side = min(src.width, src.height).toFloat()      // 가운데를 정사각형으로 잘라 원에 맞춤
            val left = (src.width - side) / 2f
            val top = (src.height - side) / 2f
            val scale = (lr * 2f) / side
            val m = Matrix().apply {
                setScale(scale, scale)
                postTranslate(cx - lr - left * scale, cx - lr - top * scale)
            }
            p.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            c.drawCircle(cx, cx, lr, p)
            p.shader = null
        } else {
            p.color = fallback                                // 커버가 없으면 배경화면 색을 띤 라벨
            c.drawCircle(cx, cx, lr, p)
            p.color = 0x22FFFFFF                              // 라벨 안쪽 고리 무늬
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3f
            c.drawCircle(cx, cx, lr * 0.62f, p)
            p.style = Paint.Style.FILL
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0x66000000
        c.drawCircle(cx, cx, lr, p)
        if (quirky) drawFace(c, cx, lr, art == null)
        p.style = Paint.Style.FILL
        p.color = 0xFF0D0D0D.toInt()
        c.drawCircle(cx, cx, SIZE * 0.02f, p)      // 가운데 구멍
        return bmp
    }

    private const val INK = 0xFF2B2622.toInt()

    /** 왕눈이 스티커 두 개 (눈동자는 서로 다른 쪽을 봄) + 커버가 없으면 헤벌쭉 웃는 입 */
    private fun drawFace(c: Canvas, cx: Float, lr: Float, mouth: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE; p.strokeWidth = lr * 0.07f; p.color = INK
        c.drawCircle(cx, cx, lr - p.strokeWidth / 2, p)                      // 잉크 테두리
        val er = lr * 0.24f; val ey = cx - lr * 0.3f
        for ((ex, dx, dy) in listOf(Triple(cx - lr * 0.32f, -0.35f, 0.25f), Triple(cx + lr * 0.3f, 0.3f, -0.2f))) {
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex, ey, er, p)
            p.style = Paint.Style.STROKE; p.color = INK; p.strokeWidth = lr * 0.045f; c.drawCircle(ex, ey, er, p)
            p.style = Paint.Style.FILL; c.drawCircle(ex + er * dx, ey + er * dy, er * 0.48f, p)
            p.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex + er * dx - er * 0.15f, ey + er * dy - er * 0.18f, er * 0.13f, p)
        }
        if (mouth) {
            p.style = Paint.Style.STROKE; p.color = INK; p.strokeWidth = lr * 0.07f; p.strokeCap = Paint.Cap.ROUND
            c.drawArc(android.graphics.RectF(cx - lr * 0.35f, cx - lr * 0.05f, cx + lr * 0.35f, cx + lr * 0.5f), 15f, 150f, false, p)
            p.style = Paint.Style.FILL; p.color = 0xFFFF8A80.toInt()
            c.drawOval(android.graphics.RectF(cx + lr * 0.05f, cx + lr * 0.38f, cx + lr * 0.25f, cx + lr * 0.55f), p)   // 혀
        }
    }
}
