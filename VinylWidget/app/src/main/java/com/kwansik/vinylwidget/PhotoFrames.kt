package com.kwansik.vinylwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 사진 스티커 병맛 프레임: 진짜 물건 양식을 진지하게 흉내 내고 내용만 웃기게.
 *  영수증 / 택배 송장 / 영양성분표. 위젯 칸에서 짧은 변이 120dp 이상이면 2×2 배치, 아니면 1×1 배치.
 *  문구는 칸마다 글자 수 제한(FIELDS.max) 안에서 사용자가 직접 씀
 */
object PhotoFrames {
    const val RECEIPT = 0
    const val PARCEL = 1
    const val NUTRI = 2
    val NAMES = arrayOf("영수증", "택배 송장", "영양성분표")

    class Field(val label: String, val def: String, val max: Int)

    val FIELDS: Array<List<Field>> = arrayOf(
        listOf(
            Field("가게 이름", "귀여움상회", 8),
            Field("항목 1", "웃음", 6), Field("수량 1", "×999", 5),
            Field("항목 2", "사랑", 6), Field("수량 2", "×∞", 5),
            Field("합계", "측정불가", 6)
        ),
        listOf(
            Field("맨 위 문구", "귀여움 특급배송", 8), Field("보내는 분", "바다", 7), Field("받는 분", "우리집 거실", 7),
            Field("내용물", "귀여움 1개", 7), Field("무게", "측정불가", 6)
        ),
        listOf(
            Field("총 내용량", "1명", 6),
            Field("성분 1", "귀여움", 4), Field("함량 1", "999%", 5),
            Field("성분 2", "장난기", 4), Field("함량 2", "120%", 5),
            Field("성분 3", "애교", 4), Field("함량 3", "85%", 5),
            Field("성분 4", "낮잠", 4), Field("함량 4", "0%", 5),
            Field("성분 5", "간식욕", 4), Field("함량 5", "300%", 5),
            Field("주의 문구", "깨물고 싶음 주의", 10)
        )
    )

    /** 종이 색. 0 = 프레임 기본 */
    val PAPERS = intArrayOf(
        0, 0xFFFFF3C4.toInt(), 0xFFD9C7A5.toInt(), 0xFFFDE2EA.toInt(),
        0xFFDDF3EA.toInt(), 0xFFDDEBFB.toInt(), 0xFF26262A.toInt()
    )
    val PAPER_NAMES = arrayOf("기본", "크림", "크라프트", "핑크", "민트", "하늘", "검정")
    private val DEFAULT_PAPER = intArrayOf(0xFFFBFBF7.toInt(), 0xFFF6F3EA.toInt(), 0xFFFFFFFF.toInt())
    fun paperOf(frame: Int, c: Int) = if (c != 0) c else DEFAULT_PAPER[frame.coerceIn(0, 2)]

    /**
     * wPx×hPx 비트맵에 그림 (짧은 변 정사각 칸을 가운데에). dens = 1dp당 px.
     * tiltDeg = 기울기(−15~15, 칸 밖으로 안 나가게 줄여서 돌림), radiusPct = 모서리 R(짧은 변의 %)
     */
    fun render(
        photo: Bitmap?, frame: Int, texts: List<String>, paper: Int, tiltDeg: Int, radiusPct: Int,
        wPx: Int, hPx: Int, dens: Float, seed: Int
    ): Bitmap {
        val out = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val s = min(out.width, out.height).toFloat()
        c.translate((out.width - s) / 2f, (out.height - s) / 2f)
        val t = Math.toRadians(tiltDeg.toDouble())
        val k = if (tiltDeg == 0) 1f else (1.0 / (abs(cos(t)) + abs(sin(t)))).toFloat() * 0.96f
        c.save()
        c.translate(s / 2, s / 2); c.rotate(tiltDeg.toFloat()); c.scale(k, k); c.translate(-s / 2, -s / 2)
        val d = Drawer(c, s, s / dens >= 120f, photo, paperOf(frame, paper), texts, radiusPct.coerceIn(0, 50) / 100f, seed)
        when (frame) {
            PARCEL -> d.parcel()
            NUTRI -> d.nutri()
            else -> d.receipt()
        }
        c.restore()
        return out
    }

    private class Drawer(
        val c: Canvas, val S: Float, val big: Boolean, val photo: Bitmap?, val paper: Int,
        val tx: List<String>, val rad: Float, val seed: Int
    ) {
        val dark = Color.luminance(paper) < 0.4f
        val ink = if (dark) 0xFFF2F2F2.toInt() else 0xFF222222.toInt()
        val soft = if (dark) 0xFFB8B8B8.toInt() else 0xFF777777.toInt()
        val red = if (dark) 0xFFFF6B6B.toInt() else 0xFFC62828.toInt()
        val BAND = 0xFFE53935.toInt()
        val MONO: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        fun s(i: Int) = tx.getOrNull(i) ?: ""
        fun tf(w: Int): Typeface = Typeface.create(Typeface.SANS_SERIF, w, false)
        fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        fun shadowed(col: Int) = fill(col).apply { setShadowLayer(S * 0.035f, 0f, S * 0.016f, 0x55000000) }

        /** 글자 (y = 세로 가운데). 너무 길면 조금 줄이고 그래도 길면 자름 */
        fun text(
            str: String, x: Float, y: Float, size: Float, face: Typeface, col: Int,
            align: Paint.Align = Paint.Align.LEFT, maxW: Float = Float.MAX_VALUE
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; color = col; textAlign = align }
            var t = str
            val w = p.measureText(t)
            if (w > maxW) {
                p.textSize = max(size * 0.72f, size * maxW / w)
                while (t.isNotEmpty() && p.measureText(t) > maxW) t = t.dropLast(1)
            }
            val fm = p.fontMetrics
            c.drawText(t, x, y - (fm.ascent + fm.descent) / 2f, p)
        }

        /** 사진을 상자에 꽉 채움 (가운데 기준으로 잘라냄). 사진이 없으면 회색 칸 */
        fun photo(dst: RectF) {
            val b = photo
            if (b == null) {
                c.drawRect(dst, fill(0xFFCFD8DC.toInt()))
                text("사진", dst.centerX(), dst.centerY(), dst.height() * 0.2f, tf(700), 0xFF78909C.toInt(), Paint.Align.CENTER, dst.width() * 0.9f)
                return
            }
            val ar = dst.width() / dst.height(); val br = b.width.toFloat() / b.height
            val src = if (br > ar) { val w = (b.height * ar).toInt(); Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height) }
                      else { val h = (b.width / ar).toInt(); Rect(0, (b.height - h) / 2, b.width, (b.height + h) / 2) }
            c.drawBitmap(b, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }

        fun bars(l: Float, t: Float, w: Float, h: Float) {
            var r = (abs(seed) % 9973 + 7).toLong()
            fun rnd(): Float { r = (r * 9301 + 49297) % 233280; return r / 233280f }
            val p = fill(ink)
            var x = l
            while (x < l + w) {
                val bw = w * (0.006f + rnd() * 0.022f)
                if (rnd() > 0.4f) c.drawRect(x, t, min(x + bw, l + w), t + h, p)
                x += bw + w * 0.006f
            }
        }

        /** 종이 카드 (그림자 + 모서리 R) 그리고 그 안으로 잘라내기 시작 */
        fun card(box: RectF): Path {
            val r = min(S * rad, box.width() / 2)
            val path = Path().apply { addRoundRect(box, r, r, Path.Direction.CW) }
            c.drawPath(path, shadowed(paper))
            c.save(); c.clipPath(path)
            return path
        }

        // ---- 영수증: 위쪽만 둥글고 아래는 지그재그로 찢김 ----
        fun receipt() {
            val w = S * (if (big) 0.66f else 0.7f); val l = (S - w) / 2; val t = S * 0.03f; val b = S * 0.97f
            val r = min(S * rad * 0.6f, w / 2)
            val path = Path().apply {
                moveTo(l + r, t); lineTo(l + w - r, t); quadTo(l + w, t, l + w, t + r); lineTo(l + w, b)
                val n = 10
                for (i in n downTo 0) lineTo(l + w * i / n, b - (if (i % 2 == 1) S * 0.025f else 0f))
                lineTo(l, t + r); quadTo(l, t, l + r, t); close()
            }
            c.drawPath(path, shadowed(paper))
            text(if (big) "★ ${s(0)} ★" else "★${s(0)}★", S / 2, t + S * 0.06f, S * (if (big) 0.06f else 0.085f), tf(900), ink, Paint.Align.CENTER, w * 0.9f)
            val pw = w * 0.82f; val ph = S * (if (big) 0.38f else 0.42f); val py = t + S * 0.11f
            photo(RectF(S / 2 - pw / 2, py, S / 2 + pw / 2, py + ph))
            val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink; style = Paint.Style.STROKE; strokeWidth = max(1f, S * 0.005f)
                pathEffect = DashPathEffect(floatArrayOf(S * 0.012f, S * 0.012f), 0f)
            }
            val dy = py + ph + S * 0.035f
            c.drawLine(l + w * 0.08f, dy, l + w * 0.92f, dy, dash)
            if (big) {
                var y = py + ph + S * 0.075f
                for (i in intArrayOf(1, 3)) {
                    text(s(i), l + w * 0.08f, y, S * 0.045f, tf(700), ink, Paint.Align.LEFT, w * 0.5f)
                    text(s(i + 1), l + w * 0.92f, y, S * 0.045f, MONO, ink, Paint.Align.RIGHT, w * 0.34f)
                    y += S * 0.058f
                }
                text("합계", l + w * 0.08f, y + S * 0.02f, S * 0.06f, tf(900), ink)
                text(s(5), l + w * 0.92f, y + S * 0.02f, S * 0.06f, tf(900), red, Paint.Align.RIGHT, w * 0.58f)
            } else {
                text("합계 ${s(5)}", S / 2, py + ph + S * 0.1f, S * 0.1f, tf(900), red, Paint.Align.CENTER, w * 0.9f)
            }
        }

        // ---- 택배 송장: 빨간 띠 + 사진 + 보내는 분/받는 분/내용물/무게 + 바코드 ----
        fun parcel() {
            val m = S * 0.05f
            card(RectF(m, m, S - m, S - m))
            val white = 0xFFFFFFFF.toInt()
            if (big) {
                c.drawRect(m, m, S - m, m + S * 0.1f, fill(BAND))
                text(s(0), m + S * 0.04f, m + S * 0.05f, S * 0.06f, tf(900), white, Paint.Align.LEFT, S * 0.56f)
                text("파손주의", S - m - S * 0.04f, m + S * 0.05f, S * 0.05f, tf(900), 0xFFFFEB3B.toInt(), Paint.Align.RIGHT)
                val pw = S * 0.42f
                photo(RectF(m + S * 0.04f, m + S * 0.14f, m + S * 0.04f + pw, m + S * 0.14f + pw))
                val x0 = m + S * 0.04f + pw + S * 0.04f; val mw = S - m - S * 0.03f - x0
                val labels = arrayOf("보내는 분", "받는 분", "내용물", "무게")
                for (i in 0..3) {
                    text(labels[i], x0, m + S * 0.17f + i * S * 0.1f, S * 0.038f, tf(500), soft, Paint.Align.LEFT, mw)
                    text(s(i + 1), x0, m + S * 0.215f + i * S * 0.1f, S * 0.05f, tf(900), ink, Paint.Align.LEFT, mw)
                }
                bars(m + S * 0.04f, S - m - S * 0.25f, S - 2 * m - S * 0.08f, S * 0.13f)
                val d = LocalDate.now()
                val no = "%04d %02d%02d %04d 0001".format(d.year, d.monthValue, d.dayOfMonth, abs(seed) % 10000)
                text(no, S / 2, S - m - S * 0.075f, S * 0.045f, MONO, ink, Paint.Align.CENTER, S - 2 * m - S * 0.08f)
            } else {
                photo(RectF(m, m, S - m, m + S * 0.58f))
                c.drawRect(m, m + S * 0.58f, S - m, m + S * 0.68f, fill(BAND))
                text("파손주의", S / 2, m + S * 0.63f, S * 0.08f, tf(900), white, Paint.Align.CENTER)
                bars(m + S * 0.07f, m + S * 0.71f, S - 2 * m - S * 0.14f, S * 0.12f)
            }
            c.restore()
        }

        // ---- 영양성분표: 왼쪽 사진 + 오른쪽 영양정보 표 ----
        fun nutri() {
            val m = S * 0.05f
            card(RectF(m, m, S - m, S - m))
            if (big) {
                val pw = S * 0.4f
                photo(RectF(m, m, m + pw, S - m))
                val l = m + pw + S * 0.04f; val r = S - m - S * 0.04f; val mw = r - l
                val bar = fill(ink)
                text("영양정보", l, m + S * 0.08f, S * 0.072f, tf(900), ink, Paint.Align.LEFT, mw)
                text("총 내용량 ${s(0)}", l, m + S * 0.14f, S * 0.034f, tf(500), ink, Paint.Align.LEFT, mw)
                c.drawRect(l, m + S * 0.17f, r, m + S * 0.188f, bar)
                var y = m + S * 0.22f
                text("1일 기준치 대비", r, y, S * 0.03f, tf(700), ink, Paint.Align.RIGHT, mw)
                y += S * 0.025f
                for (i in 0..4) {
                    c.drawRect(l, y, r, y + max(1f, S * 0.004f), bar)
                    y += S * 0.055f
                    text(s(1 + i * 2), l, y - S * 0.025f, S * 0.044f, tf(if (i == 0) 900 else 700), ink, Paint.Align.LEFT, mw * 0.55f)
                    text(s(2 + i * 2), r, y - S * 0.025f, S * 0.044f, tf(900), if (i == 0) red else ink, Paint.Align.RIGHT, mw * 0.44f)
                }
                c.drawRect(l, y, r, y + S * 0.012f, bar)
                text("※ ${s(11)}", l, y + S * 0.06f, S * 0.034f, tf(700), red, Paint.Align.LEFT, mw)
            } else {
                photo(RectF(m, m, S - m, m + S * 0.5f))
                text("영양정보", m + S * 0.07f, m + S * 0.58f, S * 0.09f, tf(900), ink, Paint.Align.LEFT, S * 0.76f)
                c.drawRect(m + S * 0.07f, m + S * 0.635f, S - m - S * 0.07f, m + S * 0.655f, fill(ink))
                text(s(1), m + S * 0.07f, m + S * 0.74f, S * 0.08f, tf(700), ink, Paint.Align.LEFT, S * 0.4f)
                text(s(2), S - m - S * 0.07f, m + S * 0.74f, S * 0.08f, tf(900), red, Paint.Align.RIGHT, S * 0.36f)
            }
            c.restore()
        }
    }
}
