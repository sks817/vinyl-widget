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
 * 1×4 날씨 위젯. 좌·중·우 3단으로 무게를 맞춤 (폭이 좁으면 가운데 캐릭터는 왼쪽으로)
 *  1) 캠핑 파노라마: [큰 날짜·요일] (가운데 캠프 풍경) [캐릭터+기온 · 날씨·최저/최고]
 *  2) 텐트 + 날짜:   [텐트][큰 날짜·요일]  (가운데 큰 날씨 캐릭터)  [기온 · 날씨·최저/최고]
 *  3) 심플:          [기온·동네]  (가운데 큰 날씨 캐릭터)  [날짜·시계(시계는 TextClock)]
 * 크기는 모두 위젯 높이(h) 비율이고, 작은 글자는 12dp 아래로 내려가지 않음
 */
class WideScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int = fg, private val accent: Int = fg,
    private val shadow: Int = 0, private val quirky: Boolean = true
) {
    /** 바탕 없이 배경화면 위에 쓰는 글자(fg/sub 색)에만 옅은 그림자 */
    private fun Paint.shade(): Paint {
        if (shadow != 0 && (color == fg || color == sub)) setShadowLayer(textSize * 0.14f, 0f, textSize * 0.03f, shadow)
        return this
    }

    private val k: Float
    private val widthDp = wDp
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

    /** 작은 글자 최소 크기 */
    private fun small(v: Float) = max(v, 12f * k)

    private fun paint(size: Float, tf: Typeface, col: Int, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = align }.shade()

    /** 글자의 세로 가운데를 y에 맞춰 그림 */
    private fun textMid(s: String, x: Float, y: Float, p: Paint) {
        val fm = p.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, p)
    }

    /** 날씨 아이콘: 병맛 테마면 캐릭터, 아니면 선 아이콘(tint 색). big3d = 기본일 때 입체 아이콘 사용 */
    private fun wIcon(d: WeatherData, cx: Float, cy: Float, size: Float, tint: Int, big3d: Boolean = false) {
        if (quirky) { funIcon(d.iconFun(), cx, cy, size); return }
        if (big3d) { funIcon(d.icon3d(), cx, cy, size * 1.1f); return }
        val s = size * 0.7f
        ctx.getDrawable(d.icon())?.mutate()?.let {
            it.setTint(tint); it.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt()); it.draw(c)
        }
    }

    /** 캐릭터 날씨 아이콘 (색 그대로) */
    private fun funIcon(res: Int, cx: Float, cy: Float, size: Float) {
        Assets.get(ctx, res)?.let {
            c.drawBitmap(it, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    /** 왼쪽 단 끝(leftEnd)과 오른쪽 단 시작(rightStart) 사이 가운데에 큰 날씨 캐릭터. 자리가 없으면 false */
    private fun centerIcon(leftEnd: Float, rightStart: Float, h: Float, d: WeatherData): Boolean {
        val size = min(h * 0.86f, rightStart - leftEnd - h * 0.3f)
        if (size < h * 0.5f) return false
        wIcon(d, (leftEnd + rightStart) / 2, h / 2, size, accent, big3d = true)
        return true
    }

    /** cardAlpha = 0이면 회색 카드를 그리지 않고(유리 배경 위) 글자는 fg/sub 색을 씀 */
    fun draw(
        design: Int, d: WeatherData, cardAlpha: Int = 148, lightCard: Boolean = false,
        corner: Int = -1, glassArt: Boolean = false, artShadow: Boolean = false
    ) {
        val today = LocalDate.now()
        val md = "${today.monthValue}.${today.dayOfMonth}"
        val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val night = d.isNight(); val kind = d.skyKind()
        val lit = night || kind == "rain"
        val msg = d.message()

        when (design) {
            3 -> { // ---- 심플 (삼성 기본 위젯 문법) ----
                val h = H
                val ownCard = cardAlpha > 0
                if (ownCard) {
                    val cardRgb = if (lightCard) 0x00F2F3F5 else 0x00272C38
                    c.drawRoundRect(RectF(0f, 0f, W, H), h * 0.36f, h * 0.36f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (cardAlpha shl 24) or cardRgb })
                }
                val main = if (!ownCard) fg else if (lightCard) 0xFF1E2128.toInt() else 0xFFFFFFFF.toInt()
                val soft = if (!ownCard) sub else if (lightCard) 0xFF5B616C.toInt() else 0xB3FFFFFF.toInt()
                // 오른쪽: 날짜(작게). 시각(크게)은 시스템 시계 부품(TextClock)이 그 아래에 들어감
                val right = W - h * 0.22f
                val dateP = paint(small(h * 0.15f), SYS, soft, Paint.Align.RIGHT)
                val dateStr = "${today.monthValue}월 ${today.dayOfMonth}일 $wk"
                textMid(dateStr, right, h * 0.28f, dateP)
                val rightStart = right - max(dateP.measureText(dateStr), paint(h * 0.38f, SYS_M, main).measureText("00:00"))
                // 왼쪽: 기온 / 동네 (폭이 좁으면 캐릭터를 맨 왼쪽에 두고 그 옆에)
                val narrow = widthDp < 290f
                val ic = h * 0.74f
                if (narrow) wIcon(d, h * 0.08f + ic / 2, h / 2, ic, accent, big3d = true)
                val tx = if (narrow) h * 0.08f + ic + h * 0.04f else h * 0.3f
                var leftEnd: Float
                if (msg != null) {
                    val mp = paint(small(h * 0.16f), SYS_M, main)
                    textMid(msg, tx, h * 0.5f, mp); leftEnd = tx + mp.measureText(msg)
                } else {
                    val tp = paint(h * 0.38f, SYS_M, main)
                    textMid(d.tempText(), tx, h * 0.41f, tp)
                    leftEnd = tx + tp.measureText(d.tempText())
                    val sp = paint(small(h * 0.15f), SYS, soft)
                    val place = WeatherStore.place(ctx)
                    var x = tx
                    if (place != null) {
                        val pin = sp.textSize * 1.1f
                        ctx.getDrawable(R.drawable.ic_pin)?.mutate()?.let {
                            it.setTint(soft); it.setBounds(x.toInt(), (h * 0.705f - pin / 2).toInt(), (x + pin).toInt(), (h * 0.705f + pin / 2).toInt()); it.draw(c)
                        }
                        x += pin + h * 0.04f
                    }
                    val label = place ?: d.cond()
                    val range = d.rangeText().replace(" ", "")
                    val text = if (range.isEmpty()) label else "$label · $range"
                    val limit = if (narrow) rightStart - h * 0.3f else (tx + rightStart) / 2 - h * 0.5f
                    val shown = if (x + sp.measureText(text) < limit) text else label
                    textMid(shown, x, h * 0.705f, sp)
                    leftEnd = max(leftEnd, x + sp.measureText(shown))
                }
                if (!narrow && msg == null) centerIcon(leftEnd, rightStart, h, d)
            }
            4 -> { // ---- 캐릭터 (병맛): [큰 캐릭터] [말풍선 한마디 / 날짜·날씨] [큰 기온] ----
                val h = H
                val ink = 0xFF2B2622.toInt()
                val black = Typeface.create("sans-serif-black", Typeface.NORMAL)
                val cs = h * 0.92f
                funIcon(d.iconFun(), h * 0.06f + cs / 2, h / 2, cs)
                val x = h * 0.06f + cs + h * 0.1f
                val right = W - h * 0.24f
                val tp = paint(h * 0.46f, black, fg, Paint.Align.RIGHT)
                val tw = if (msg == null) tp.measureText(d.tempText()) else 0f
                if (msg == null) textMid(d.tempText(), right, h * 0.5f, tp)
                val maxW = right - tw - h * 0.25f - x
                // 말풍선 (꼬리는 왼쪽 캐릭터 쪽)
                val q = msg ?: Quips.of(d)
                val qp = paint(small(h * 0.16f), SANS, ink)
                val bh = h * 0.3f
                var bw = qp.measureText(q) + h * 0.32f
                if (bw > maxW) bw = maxW
                val bubble = RectF(x, h * 0.13f, x + bw, h * 0.13f + bh)
                val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
                val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; style = Paint.Style.STROKE; strokeWidth = h * 0.028f; strokeJoin = Paint.Join.ROUND }
                val tail = Path().apply {
                    moveTo(bubble.left + 1, bubble.centerY() - bh * 0.15f); lineTo(bubble.left - h * 0.09f, bubble.centerY() + bh * 0.25f); lineTo(bubble.left + 1, bubble.centerY() + bh * 0.2f); close()
                }
                c.drawPath(tail, fillP); c.drawPath(tail, lineP)
                c.drawRoundRect(bubble, bh / 2, bh / 2, fillP); c.drawRoundRect(bubble, bh / 2, bh / 2, lineP)
                c.drawRect(bubble.left + lineP.strokeWidth / 2, bubble.centerY() - bh * 0.12f, bubble.left + lineP.strokeWidth * 1.6f, bubble.centerY() + bh * 0.17f, fillP)
                c.save(); c.clipRect(bubble.left, bubble.top, bubble.right - h * 0.12f, bubble.bottom)
                textMid(q, bubble.left + h * 0.16f, bubble.centerY(), qp)
                c.restore()
                // 아래 줄: 날짜·요일 · 날씨·최저/최고
                val range = d.rangeText().replace(" ", "")
                val line = if (msg != null) "$md $wk" else "$md $wk · ${d.cond()}${if (range.isEmpty()) "" else " $range"}"
                val lp = paint(small(h * 0.15f), SANS_M, sub)
                c.save(); c.clipRect(x, 0f, x + maxW, h)
                textMid(line, x + h * 0.02f, h * 0.72f, lp)
                c.restore()
            }
            1 -> { // ---- 캠핑 파노라마 ----
                val pad = 6f * k
                val r = RectF(pad, pad, W - pad, H - pad)
                val h = r.height()
                val rad = h * (if (corner < 0) 30 else corner) / 100f        // 모서리: 높이의 % (기본 30)
                if (artShadow) GlassArt.shadow(c, r, rad)
                c.save(); c.clipPath(Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) })
                Assets.get(ctx, Scenes.panorama(kind, night))?.let { b ->
                    // 위젯 비율에 맞게 가운데를 잘라서(center-crop) 채움
                    val ar = r.width() / r.height(); val br = b.width.toFloat() / b.height
                    val src = if (br > ar) { val w = (b.height * ar).toInt(); Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height) }
                              else { val hh = (b.width / ar).toInt(); Rect(0, (b.height - hh) / 2, b.width, (b.height + hh) / 2) }
                    c.drawBitmap(b, src, r, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                }
                c.restore()
                if (glassArt) GlassArt.draw(c, r, rad)
                val dark = !night && kind != "rain"
                val main = if (dark) 0xFF23262E.toInt() else 0xFFFBF1DC.toInt()
                val soft = if (dark) 0xFF3E434D.toInt() else 0xFFE2D7C6.toInt()
                // 좌·우 블록 모두 [큰 줄 0.46h + 간격 0.05h + 작은 줄] 을 세로 가운데에
                val big = h * 0.46f; val sm = small(h * 0.17f)
                val top = r.top + (h - (big + h * 0.05f + sm)) / 2
                val y1 = top + big / 2; val y2 = top + big + h * 0.05f + sm / 2
                textMid(md, r.left + h * 0.3f, y1, paint(big, SERIF, main))
                textMid(wk, r.left + h * 0.32f, y2, paint(sm, SANS_M, soft))
                val right = r.right - h * 0.3f
                if (msg != null) {
                    textMid(msg, right, r.centerY(), paint(sm, SANS, main, Paint.Align.RIGHT))
                } else {
                    val tp = paint(big, SERIF, main, Paint.Align.RIGHT)
                    textMid(d.tempText(), right, y1, tp)
                    val ic = big * 1.05f
                    wIcon(d, right - tp.measureText(d.tempText()) - big * 0.08f - ic / 2, y1, ic, main)
                    val range = d.rangeText().replace(" ", "")
                    textMid(if (range.isEmpty()) d.cond() else "${d.cond()} · $range", right, y2, paint(sm, SANS_M, soft, Paint.Align.RIGHT))
                }
            }
            else -> { // ---- 텐트 + 날짜 ----
                val h = H
                var x = h * 0.1f
                val res = if (lit) R.drawable.w_tent_glow else R.drawable.w_tent_day
                Assets.get(ctx, res)?.let { b ->
                    val th = h * 0.62f; val tw = th * b.width / b.height
                    c.drawBitmap(b, null, RectF(x, (h - th) / 2, x + tw, (h + th) / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                    x += tw + h * 0.12f
                }
                val dp = paint(h * 0.38f, SERIF, fg)
                textMid(md, x, h * 0.41f, dp)
                val wp = paint(small(h * 0.15f), SANS_M, sub)
                textMid(wk, x + h * 0.01f, h * 0.705f, wp)
                val leftEnd = x + max(dp.measureText(md), wp.measureText(wk))
                val right = W - h * 0.24f
                if (msg != null) {
                    textMid(msg, right, h / 2, paint(small(h * 0.16f), SANS, fg, Paint.Align.RIGHT))
                } else {
                    // 오른쪽: 기온(크게) / 날씨·최저최고 — 왼쪽 날짜와 같은 크기·같은 줄 높이
                    val tp = paint(h * 0.38f, SERIF, fg, Paint.Align.RIGHT)
                    textMid(d.tempText(), right, h * 0.41f, tp)
                    val range = d.rangeText().replace(" ", "")
                    val line = if (range.isEmpty()) d.cond() else "${d.cond()} · $range"
                    val lp = paint(small(h * 0.15f), SANS_M, sub, Paint.Align.RIGHT)
                    textMid(line, right, h * 0.705f, lp)
                    val rightStart = right - max(tp.measureText(d.tempText()), lp.measureText(line))
                    centerIcon(leftEnd, rightStart, h, d)
                }
            }
        }
    }
}
