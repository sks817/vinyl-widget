package com.kwansik.vinylwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 1×4 날씨 위젯.
 *  1) 파노라마: 날씨·시간에 따라 바뀌는 캠핑 풍경 위에 [큰 날짜 | 기온]
 *  2) 텐트 + 날짜: 배경 없이 [텐트] [큰 날짜] [날씨]
 */
class WideScene(private val ctx: Context, wDp: Float, hDp: Float, private val fg: Int) {
    private val k: Float
    val bitmap: Bitmap
    private val c: Canvas
    private val W: Float
    private val H: Float

    init {
        val dens = WidgetGeom.density(ctx)
        k = dens * min(1f, 1100f / (max(wDp, hDp) * dens))
        bitmap = Bitmap.createBitmap((wDp * k).toInt().coerceAtLeast(1), (hDp * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap); W = bitmap.width.toFloat(); H = bitmap.height.toFloat()
    }

    private val SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val SANS = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val SANS_M = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val SYS = Typeface.create("sans-serif", Typeface.NORMAL)
    private val SYS_M = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private fun paint(size: Float, tf: Typeface, col: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = align }

    /** 글자의 세로 가운데를 y에 맞춰 그림 */
    private fun textMid(s: String, x: Float, y: Float, p: Paint) {
        val fm = p.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p)
    }

    private fun icon(res: Int, cx: Float, cy: Float, size: Float, col: Int) {
        ctx.getDrawable(res)?.mutate()?.let {
            it.setTint(col); it.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(), (cx + size / 2).toInt(), (cy + size / 2).toInt()); it.draw(c)
        }
    }

    fun draw(design: Int, d: WeatherData, cardAlpha: Int = 148, lightCard: Boolean = false) {
        val today = LocalDate.now()
        val md = "${today.monthValue}.${today.dayOfMonth}"
        val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val night = d.isNight(); val kind = d.skyKind()
        val lit = night || kind == "rain"
        val pad = 6f * k
        val msg = d.message()

        if (design == 3) {
            // ---- 심플 (삼성 기본 위젯 문법) ----
            // 카드 = 위젯 영역 전체 → 옆·아래 삼성 위젯들과 가장자리가 정확히 맞음
            val r = RectF(0f, 0f, W, H)
            val h = r.height()
            val cardRgb = if (lightCard) 0x00F2F3F5 else 0x00272C38
            c.drawRoundRect(r, h * 0.36f, h * 0.36f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (cardAlpha shl 24) or cardRgb })
            val main = if (lightCard) 0xFF1E2128.toInt() else 0xFFFFFFFF.toInt()
            val soft = if (lightCard) 0xFF5B616C.toInt() else 0xB3FFFFFF.toInt()
            val inset = h * 0.30f
            // 오른쪽: 날짜(작게). 시각(크게)은 시스템 시계 부품이 아래쪽에 들어감
            val right = r.right - inset
            val dateP = paint(h * 0.16f, SYS, soft, Paint.Align.RIGHT)
            val dateStr = "${today.monthValue}월 ${today.dayOfMonth}일 $wk"
            textMid(dateStr, right, h * 0.30f, dateP)
            val rightStart = right - max(dateP.measureText(dateStr), paint(h * 0.40f, SYS_M, main).measureText("00:00"))
            // 왼쪽: 입체 날씨 아이콘(크게) + 기온 / 동네·최저최고
            // 아이콘 그림은 자체 여백이 있어 상자를 크게 잡음(실제 그림 ≈ 높이의 60%, 삼성 위젯과 같은 비중)
            val ic = h * 0.98f
            val ix = inset * 0.25f
            Assets.get(ctx, d.icon3d())?.let {
                c.drawBitmap(it, null, RectF(ix, (h - ic) / 2, ix + ic, (h + ic) / 2),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            val tx = ix + ic * 0.90f + h * 0.08f
            if (msg != null) {
                textMid(msg, tx, h * 0.5f, paint(h * 0.16f, SYS_M, main))
            } else {
                textMid(d.tempText(), tx, h * 0.39f, paint(h * 0.40f, SYS_M, main))
                val sp = paint(h * 0.16f, SYS, soft)
                val place = WeatherStore.place(ctx)
                val range = d.rangeText().replace(" ", "")
                val limit = rightStart - h * 0.20f
                var x = tx
                if (place != null) {
                    val pin = h * 0.17f
                    ctx.getDrawable(R.drawable.ic_pin)?.mutate()?.let {
                        it.setTint(soft); it.setBounds(x.toInt(), (h * 0.715f - pin / 2).toInt(), (x + pin).toInt(), (h * 0.715f + pin / 2).toInt()); it.draw(c)
                    }
                    x += pin + h * 0.05f
                }
                val label = place ?: d.cond()
                val full = if (range.isEmpty()) label else "$label · $range"
                textMid(if (x + sp.measureText(full) < limit) full else label, x, h * 0.72f, sp)
            }
        } else if (design == 1) {
            // ---- 파노라마 ----
            val r = RectF(pad, pad, W - pad, H - pad)
            val clip = Path().apply { addRoundRect(r, r.height() * 0.30f, r.height() * 0.30f, Path.Direction.CW) }   // 곡률 ↑
            c.save(); c.clipPath(clip)
            Assets.get(ctx, Scenes.panorama(kind, night))?.let { b ->
                // 위젯 비율에 맞게 가운데를 잘라서(center-crop) 채움
                val ar = r.width() / r.height(); val br = b.width.toFloat() / b.height
                val src = if (br > ar) { val w = (b.height * ar).toInt(); Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height) }
                          else { val h = (b.width / ar).toInt(); Rect(0, (b.height - h) / 2, b.width, (b.height + h) / 2) }
                c.drawBitmap(b, src, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            }
            c.restore()
            val dark = !night && kind != "rain"
            val main = if (dark) 0xFF23262E.toInt() else 0xFFFBF1DC.toInt()
            val sub = if (dark) 0xFF3E434D.toInt() else 0xFFE2D7C6.toInt()
            val h = r.height()
            // 왼쪽: 큰 날짜 + 요일
            val dateP = paint(h * 0.50f, SERIF, main)
            textMid(md, r.left + h * 0.32f, r.top + h * 0.42f, dateP)
            textMid(wk, r.left + h * 0.34f, r.top + h * 0.80f, paint(h * 0.15f, SANS_M, sub))
            // 오른쪽: 기온 + 날씨
            val right = r.right - h * 0.30f
            if (msg != null) {
                textMid(msg, right, r.centerY(), paint(h * 0.16f, SANS, main, Paint.Align.RIGHT))
            } else {
                textMid(d.tempText(), right, r.top + h * 0.40f, paint(h * 0.40f, SERIF, main, Paint.Align.RIGHT))
                val line = "${d.cond()}  ${d.rangeText().replace(" ", "")}"
                textMid(line, right, r.top + h * 0.78f, paint(h * 0.14f, SANS_M, sub, Paint.Align.RIGHT))
            }
        } else {
            // ---- 텐트 + 날짜 (배경 없음) ----
            val h = H - pad * 2
            val res = if (lit) R.drawable.w_tent_glow else R.drawable.w_tent_day
            Assets.get(ctx, res)?.let { b ->
                val th = h * 0.92f; val tw = th * b.width / b.height
                c.drawBitmap(b, null, RectF(pad, H - pad - th, pad + tw, H - pad), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                val x = pad + tw + h * 0.18f
                textMid(md, x, pad + h * 0.40f, paint(h * 0.52f, SERIF, fg))
                textMid(wk, x + h * 0.02f, pad + h * 0.82f, paint(h * 0.16f, SANS_M, fg))
            }
            val right = W - pad - h * 0.10f
            if (msg != null) {
                textMid(msg, right, H / 2, paint(h * 0.16f, SANS, fg, Paint.Align.RIGHT))
            } else {
                val tp = paint(h * 0.36f, SERIF, fg, Paint.Align.RIGHT)
                val t = d.tempText(); val tw = tp.measureText(t)
                textMid(t, right, pad + h * 0.40f, tp)
                icon(d.icon(), right - tw - h * 0.22f, pad + h * 0.40f, h * 0.30f, fg)
                textMid("${d.cond()}  ${d.rangeText().replace(" ", "")}", right, pad + h * 0.80f, paint(h * 0.14f, SANS_M, fg, Paint.Align.RIGHT))
            }
        }
    }
}
