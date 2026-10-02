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

    fun draw(art: Bitmap?): Bitmap {
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
            p.color = 0xFFB23A2E.toInt()
            c.drawCircle(cx, cx, lr, p)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f
        p.color = 0x66000000
        c.drawCircle(cx, cx, lr, p)
        p.style = Paint.Style.FILL
        p.color = 0xFF0D0D0D.toInt()
        c.drawCircle(cx, cx, SIZE * 0.02f, p)      // 가운데 구멍
        return bmp
    }
}
