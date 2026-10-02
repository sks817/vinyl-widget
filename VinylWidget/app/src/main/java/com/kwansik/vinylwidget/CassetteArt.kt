package com.kwansik.vinylwidget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/**
 * 레트로 카세트 테이프 몸통 그림 (릴은 따로 돌아가는 뷰가 위에 얹힘).
 *  - 기본: 진한 몸통 + 크림색 라벨, 80년대 무지개 줄무늬, 나사, 작은 앨범 커버 + 곡 제목(손글씨 라벨 느낌)
 *  - 병맛: 굵은 잉크 외곽선 + 노란 라벨, 릴이 왕눈이 눈알이 되고 아래에 헤벌쭉 입과 볼터치
 * 비율은 실제 카세트처럼 가로:세로 = 1.6:1
 */
object CassetteArt {
    const val RATIO = 1.6f
    /** 릴 중심(가로·세로 비율)과 릴 지름(세로 비율) — 위젯 코드가 릴 뷰를 여기에 맞춰 놓음 */
    val REEL_X = floatArrayOf(0.315f, 0.685f)
    const val REEL_Y = 0.46f
    const val REEL_D = 0.22f

    private const val INK = 0xFF2B2622.toInt()
    const val DEFAULT_SHELL = 0xFF2E2A28.toInt()
    const val DEFAULT_SHELL_FUN = 0xFFFF8FAB.toInt()

    private fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
    private fun lighten(c: Int, f: Float) = Color.rgb(Color.red(c) + ((255 - Color.red(c)) * f).toInt(), Color.green(c) + ((255 - Color.green(c)) * f).toInt(), Color.blue(c) + ((255 - Color.blue(c)) * f).toInt())

    fun draw(wPx: Int, quirky: Boolean, shellColor: Int, art: Bitmap?, title: String?, artist: String?): Bitmap {
        val W = wPx.coerceAtLeast(16).toFloat(); val H = W / RATIO
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val shell = if (shellColor != 0) shellColor else if (quirky) DEFAULT_SHELL_FUN else DEFAULT_SHELL
        val darkShell = Color.luminance(shell) < 0.4f
        val ol = W * 0.012f                                       // 병맛 외곽선 굵기
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ol; color = INK; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // 몸통 (위가 살짝 밝은 플라스틱)
        val body = RectF(if (quirky) ol / 2 else 0f, if (quirky) ol / 2 else 0f, W - (if (quirky) ol / 2 else 0f), H - (if (quirky) ol / 2 else 0f))
        val br = W * 0.045f
        fill.shader = LinearGradient(0f, 0f, 0f, H, lighten(shell, 0.12f), shade(shell, 0.92f), Shader.TileMode.CLAMP)
        c.drawRoundRect(body, br, br, fill); fill.shader = null
        if (quirky) c.drawRoundRect(body, br, br, ink)
        else {                                                    // 테두리 빛 + 나사 4개
            c.drawRoundRect(body, br, br, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, W * 0.004f); color = 0x33FFFFFF })
            for ((sx, sy) in listOf(0.035f to 0.06f, 0.965f to 0.06f, 0.035f to 0.94f, 0.965f to 0.94f)) {
                val x = W * sx; val y = H * sy; val r = H * 0.026f
                fill.color = if (darkShell) lighten(shell, 0.25f) else shade(shell, 0.75f)
                c.drawCircle(x, y, r, fill)
                c.drawLine(x - r * 0.6f, y, x + r * 0.6f, y, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = r * 0.3f; color = 0x66000000 })
            }
        }

        // 아래 사다리꼴 (테이프 헤드 자리) + 구멍
        val tz = Path().apply {
            moveTo(W * 0.19f, H * 0.995f); lineTo(W * 0.24f, H * 0.78f); lineTo(W * 0.76f, H * 0.78f); lineTo(W * 0.81f, H * 0.995f); close()
        }
        fill.color = if (darkShell) lighten(shell, 0.1f) else shade(shell, 0.88f)
        c.drawPath(tz, fill)
        if (quirky) c.drawPath(tz, ink)
        fill.color = 0xCC1A1715.toInt()
        for (x in floatArrayOf(0.35f, 0.65f)) c.drawCircle(W * x, H * 0.885f, H * 0.035f, fill)
        for (x in floatArrayOf(0.455f, 0.545f)) c.drawRect(W * x - H * 0.02f, H * 0.86f, W * x + H * 0.02f, H * 0.91f, fill)

        // 라벨 스티커
        val label = RectF(W * 0.055f, H * 0.08f, W * 0.945f, H * 0.72f)
        val lr = W * 0.02f
        fill.color = if (quirky) 0xFFFFE9A8.toInt() else 0xFFF6EEDF.toInt()
        c.drawRoundRect(label, lr, lr, fill)
        if (quirky) c.drawRoundRect(label, lr, lr, ink)
        // 무지개 줄무늬 (병맛은 대신 입과 볼터치)
        if (!quirky) {
            val cols = intArrayOf(0xFFE85D3F.toInt(), 0xFFF2A33A.toInt(), 0xFFF5D06B.toInt(), 0xFF6CB7A4.toInt())
            val sh = H * 0.028f; var y = H * 0.615f
            c.save(); c.clipRect(label)
            for (col in cols) { fill.color = col; c.drawRect(label.left, y, label.right, y + sh, fill); y += sh }
            c.restore()
        }

        // 창: 연기색 유리 + 감긴 테이프(왼쪽 많이, 오른쪽 조금)
        val win = RectF(W * 0.2f, H * (REEL_Y - 0.13f), W * 0.8f, H * (REEL_Y + 0.13f))
        val wr = win.height() / 2
        c.save(); c.clipPath(Path().apply { addRoundRect(win, wr, wr, Path.Direction.CW) })
        fill.color = if (quirky) 0xFFFFFFFF.toInt() else 0xFF3A3532.toInt()
        c.drawRect(win, fill)
        if (!quirky) {
            fill.color = 0xFF5A3E2B.toInt()
            c.drawCircle(W * REEL_X[0], H * REEL_Y, H * 0.19f, fill)
            c.drawCircle(W * REEL_X[1], H * REEL_Y, H * 0.135f, fill)
            fill.color = 0xFF6E4E37.toInt()
            c.drawCircle(W * REEL_X[0], H * REEL_Y, H * 0.17f, fill)
            c.drawCircle(W * REEL_X[1], H * REEL_Y, H * 0.115f, fill)
            fill.color = 0x40FFFFFF                                 // 유리 반사
            c.drawRect(win.left, win.top, win.right, win.top + win.height() * 0.28f, fill)
        }
        c.restore()
        if (quirky) c.drawRoundRect(win, wr, wr, ink)
        else c.drawRoundRect(win, wr, wr, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, W * 0.005f); color = 0xFF1E1A18.toInt() })

        // 제목 줄: [작은 앨범 커버] 제목 · 가수  …  [A]
        val rowTop = label.top + H * 0.035f; val rowH = H * 0.15f
        var tx = label.left + W * 0.03f
        if (art != null) {
            val s = rowH; val dst = RectF(tx, rowTop, tx + s, rowTop + s)
            val src = if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false) else art
            val side = min(src.width, src.height).toFloat()
            val m = Matrix().apply { setScale(s / side, s / side); postTranslate(dst.left - (src.width - side) / 2 * s / side, dst.top - (src.height - side) / 2 * s / side) }
            c.drawRoundRect(dst, s * 0.12f, s * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) } })
            if (quirky) c.drawRoundRect(dst, s * 0.12f, s * 0.12f, Paint(ink).apply { strokeWidth = ol * 0.6f })
            tx += s + W * 0.025f
        }
        val sideR = rowH * 0.42f; val sideX = label.right - W * 0.03f - sideR
        fill.color = if (quirky) INK else 0xFFE85D3F.toInt()
        c.drawCircle(sideX, rowTop + rowH / 2, sideR, fill)
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sideR * 1.3f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER }
        c.drawText("A", sideX, rowTop + rowH / 2 - (ap.fontMetrics.ascent + ap.fontMetrics.descent) / 2, ap)
        val maxW = sideX - sideR - W * 0.02f - tx
        val name = title?.takeIf { it.isNotBlank() } ?: if (quirky) "띵곡 모음.zip" else "MIX TAPE"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = rowH * 0.6f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = INK }
        val tline = ellipsize(name, tp, maxW)
        val ap2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = rowH * 0.42f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); color = 0xFF6B6158.toInt() }
        if (!artist.isNullOrBlank()) {
            c.drawText(tline, tx, rowTop + rowH * 0.5f, tp)
            c.drawText(ellipsize(artist, ap2, maxW), tx, rowTop + rowH * 0.98f, ap2)
        } else c.drawText(tline, tx, rowTop + rowH / 2 - (tp.fontMetrics.ascent + tp.fontMetrics.descent) / 2, tp)

        // 병맛: 창 아래 헤벌쭉 입 + 볼터치
        if (quirky) {
            val my = H * 0.63f
            c.drawArc(RectF(W * 0.43f, my - H * 0.07f, W * 0.57f, my + H * 0.05f), 15f, 150f, false, Paint(ink).apply { strokeCap = Paint.Cap.ROUND })
            fill.color = 0xFFFF8A80.toInt()
            c.drawOval(RectF(W * 0.505f, my + H * 0.015f, W * 0.545f, my + H * 0.06f), fill)
            fill.color = 0xB3FF8A80.toInt()
            for (x in REEL_X) c.drawOval(RectF(W * x - W * 0.05f, my - H * 0.01f, W * x + W * 0.05f, my + H * 0.035f), fill)
        }
        return bmp
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
