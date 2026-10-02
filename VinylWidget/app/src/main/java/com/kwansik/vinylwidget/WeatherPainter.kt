package com.kwansik.vinylwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 날씨·날짜 위젯을 '위젯 크기에 꽉 맞는 그림'으로 그림.
 * 모든 디자인은 170×170 단위 정사각형 좌표로 설계했고, 실제 픽셀 크기에 맞춰 확대/축소됨
 * → 레코드 위젯처럼 칸 크기에 따라 같이 커지고 작아짐.
 */
class WeatherPainter(
    private val ctx: Context, size: Int, private val fg: Int,
    private val point: Int = WidgetStyle.DEFAULT_POINT,      // 달력 윗부분 색
    private val quirky: Boolean = false                       // 병맛 테마: 날씨 아이콘을 캐릭터로
) {

    val bitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val c = Canvas(bitmap)
    private val u = size / 170f

    private val INK = 0xFF161616.toInt()
    private val CREAM = 0xFFEFE6D2.toInt()
    private val RED = 0xFFB23A2E.toInt()
    private val GOLD = 0xFFF2C14E.toInt()
    private val PAPER = 0xFFFAF9F5.toInt()

    private val SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val SERIF_M = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val SANS = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val SANS_M = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val SANS_R = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    private enum class H { L, C, R }
    private enum class V { TOP, MID }

    // ---------- 그리기 도우미 (좌표는 모두 170 단위) ----------
    private fun text(x: Float, y: Float, s: String, sz: Float, tf: Typeface, col: Int, h: H = H.L, v: V = V.TOP) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sz * u; typeface = tf; color = col
            textAlign = when (h) { H.L -> Paint.Align.LEFT; H.C -> Paint.Align.CENTER; H.R -> Paint.Align.RIGHT }
        }
        val fm = p.fontMetrics
        val base = if (v == V.TOP) y * u - fm.ascent else y * u - (fm.ascent + fm.descent) / 2f
        c.drawText(s, x * u, base, p)
    }

    private fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; style = Paint.Style.FILL }
    private fun stroke(col: Int, w: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = col; style = Paint.Style.STROKE; strokeWidth = w * u
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private fun rect(l: Float, t: Float, r: Float, b: Float) = RectF(l * u, t * u, r * u, b * u)
    private fun path(vararg pts: Float): Path = Path().apply {
        moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) { lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
        close()
    }
    private fun withAlpha(col: Int, a: Int) = (col and 0x00FFFFFF) or (a shl 24)

    private var funRes = 0

    /** 날씨 아이콘(해·달·구름·비·눈)을 원하는 색으로. 병맛 테마면 캐릭터 그림(조금 크게) */
    private fun icon(res: Int, cx: Float, cy: Float, sz: Float, col: Int) {
        if (quirky && funRes != 0) {
            val b = Assets.get(ctx, funRes) ?: return
            val half = sz * 1.45f * u / 2f
            c.drawBitmap(b, null, RectF(cx * u - half, cy * u - half, cx * u + half, cy * u + half), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return
        }
        val dr = ctx.getDrawable(res)?.mutate() ?: return
        dr.setTint(col)
        val half = sz * u / 2f
        dr.setBounds((cx * u - half).toInt(), (cy * u - half).toInt(), (cx * u + half).toInt(), (cy * u + half).toInt())
        dr.draw(c)
    }

    /** 레코드판 (홈 무늬 + 광택 + 라벨) */
    private fun disc(cx: Float, cy: Float, r: Float, labelRatio: Float, labelCol: Int) {
        c.drawCircle(cx * u, cy * u, r * u, fill(0xFF111111.toInt()))
        var g = r * 0.96f; var t = false
        while (g > r * labelRatio + 2f) {
            c.drawCircle(cx * u, cy * u, g * u, stroke(if (t) 0xFF242424.toInt() else 0xFF1A1A1A.toInt(), 0.5f))
            g -= 1.6f; t = !t
        }
        c.drawCircle(cx * u, cy * u, (r - 0.6f) * u, stroke(0xFF3A3A3A.toInt(), 1f))
        val rr = r * 0.82f
        val ov = rect(cx - rr, cy - rr, cx + rr, cy + rr)
        c.drawArc(ov, -60f, 40f, false, stroke(0x28FFFFFF, r * 0.14f))
        c.drawArc(ov, 120f, 30f, false, stroke(0x16FFFFFF, r * 0.14f))
        if (labelRatio > 0f) {
            c.drawCircle(cx * u, cy * u, r * labelRatio * u, fill(labelCol))
            c.drawCircle(cx * u, cy * u, r * 0.03f * u, fill(0xFF0D0D0D.toInt()))
        }
    }

    private fun glow(clip: Path, cx: Float, cy: Float, r: Float, col: Int) {
        c.save()
        c.clipPath(clip)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(cx * u, cy * u, r * u, withAlpha(col, 170), withAlpha(col, 0), Shader.TileMode.CLAMP)
        c.drawCircle(cx * u, cy * u, r * u, p)
        c.restore()
    }

    // ---------- 디자인 ----------
    fun draw(design: Int, d: WeatherData) {
        val today = LocalDate.now()
        val day = today.dayOfMonth.toString()
        val mon = "${today.monthValue}월"
        val md = "${today.monthValue}.${today.dayOfMonth}"
        val mdd = "%02d.%02d".format(today.monthValue, today.dayOfMonth)
        val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val wks = today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)
        val full = "${today.monthValue}월 ${today.dayOfMonth}일 $wk"
        val ym = "${today.year} · ${today.monthValue}월"
        val msg = d.message()
        val temp = d.tempText()
        val cond = msg ?: d.cond()
        val rng = if (msg == null) d.rangeText() else ""
        val ic = d.icon()
        funRes = d.iconFun()

        when (design) {
            1 -> { // 미니멀
                text(12f, 10f, md, 64f, SERIF, fg)
                text(14f, 92f, wk, 18f, SANS_M, fg)
                c.drawLine(14 * u, 122 * u, 158 * u, 122 * u, stroke(withAlpha(fg, 110), 1.2f))
                icon(ic, 24f, 144f, 30f, fg)
                text(44f, 144f, temp, 32f, SERIF, fg, v = V.MID)
                text(98f, 136f, cond, 12f, SANS_M, fg, v = V.MID)
                if (rng.isNotEmpty()) text(98f, 152f, rng, 12f, SANS_M, fg, v = V.MID)
            }
            2 -> { // 쌍둥이 판
                disc(85f, 72f, 68f, 0f, RED)
                c.drawCircle(85 * u, 72 * u, 42 * u, fill(CREAM))
                text(85f, 48f, mon, 11f, SANS, INK, H.C, V.MID)
                text(85f, 73f, day, 34f, SERIF, INK, H.C, V.MID)
                text(85f, 98f, wk, 10f, SANS, INK, H.C, V.MID)
                icon(ic, 50f, 158f, 22f, fg)
                text(64f, 158f, "$temp  $cond", 16f, SANS, fg, v = V.MID)
            }
            3 -> { // 다이얼: 270° 눈금에 -10~40℃
                val r = 66f; val cy = 76f
                fun ang(t: Double) = 135f + (((t.coerceIn(-10.0, 40.0) + 10.0) / 50.0) * 270.0).toFloat()
                val ov = rect(85 - r, cy - r, 85 + r, cy + r)
                c.drawArc(ov, 135f, 270f, false, stroke(withAlpha(fg, 70), 5f))
                if (d.min != null && d.max != null) {
                    val a1 = ang(d.min); val a2 = ang(d.max)
                    c.drawArc(ov, a1, max(2f, a2 - a1), false, stroke(fg, 8f))
                }
                d.temp?.let {
                    val a = Math.toRadians(ang(it).toDouble())
                    val px = 85 + r * cos(a).toFloat(); val py = cy + r * sin(a).toFloat()
                    c.drawCircle(px * u, py * u, 7 * u, fill(if (Color.luminance(fg) < 0.5f) PAPER else INK))
                    c.drawCircle(px * u, py * u, 7 * u, stroke(fg, 3f))
                }
                text(85f, cy - 6, temp, 42f, SERIF, fg, H.C, V.MID)
                text(85f, cy + 26, if (rng.isNotEmpty()) "$cond · ${rng.replace(" ", "")}" else cond, 12f, SANS, fg, H.C, V.MID)
                text(85f, 160f, full, 15f, SANS, fg, H.C, V.MID)
            }
            4 -> { // LP 재킷
                disc(102f, 85f, 66f, 0.36f, RED)
                val lt = 0xFFF4EFE6.toInt()
                c.drawRoundRect(rect(4f, 17f, 140f, 153f), 10 * u, 10 * u, fill(0xF51B1A18.toInt()))
                text(16f, 28f, wk, 11f, SANS, lt)
                text(16f, 44f, mdd, 19f, SERIF_M, lt)
                icon(ic, 122f, 40f, 26f, GOLD)
                text(16f, 82f, temp, 42f, SERIF, lt)
                text(16f, 138f, if (rng.isNotEmpty()) "$cond · $rng" else cond, 11f, SANS_M, lt)
            }
            5 -> { // 달력
                c.drawRoundRect(rect(14f, 6f, 156f, 164f), 14 * u, 14 * u, fill(PAPER))
                c.drawRoundRect(rect(14f, 6f, 156f, 36f), 14 * u, 14 * u, fill(point))
                c.drawRect(rect(14f, 22f, 156f, 36f), fill(point))
                text(85f, 21f, ym, 13f, SANS, if (Color.luminance(point) > 0.6f) INK else Color.WHITE, H.C, V.MID)
                text(85f, 72f, day, 56f, SERIF, INK, H.C, V.MID)
                text(85f, 108f, wk, 13f, SANS_M, 0xFF444444.toInt(), H.C, V.MID)
                var x = 24f
                while (x < 146f) { c.drawLine(x * u, 126 * u, (x + 5) * u, 126 * u, stroke(0xFFBDB6A8.toInt(), 1.6f)); x += 8f }
                icon(ic, 46f, 146f, 21f, point)
                text(60f, 146f, temp, 15f, SANS, INK, v = V.MID)
                text(92f, 146f, if (msg != null) cond else rng, 12f, SANS_R, 0xFF555555.toInt(), v = V.MID)
            }
            6 -> { // 캐빈 텐트 (거실형 에어텐트)
                val canvas = 0xFFE9DDC4.toInt(); val beam = 0xFF6F6A4E.toInt(); val dark = 0xFF2B2620.toInt()
                c.drawPath(path(10f, 152f, 14f, 82f, 85f, 24f, 156f, 82f, 160f, 152f), fill(canvas))
                val beams = Path().apply {
                    moveTo(10 * u, 152 * u); lineTo(14 * u, 82 * u); lineTo(85 * u, 24 * u)
                    lineTo(156 * u, 82 * u); lineTo(160 * u, 152 * u)
                }
                c.drawPath(beams, stroke(beam, 6f))                            // 에어빔(공기 튜브)
                c.drawLine(48 * u, 50 * u, 48 * u, 152 * u, stroke(beam, 4f))
                c.drawLine(122 * u, 50 * u, 122 * u, 152 * u, stroke(beam, 4f))
                val door = Path().apply {
                    addRoundRect(rect(56f, 76f, 114f, 152f), 26 * u, 26 * u, Path.Direction.CW)
                    addRect(rect(56f, 110f, 114f, 152f), Path.Direction.CW)
                }
                c.drawPath(door, fill(dark))
                glow(door, 85f, 104f, 48f, GOLD)                               // 안쪽 랜턴 불빛
                c.drawRoundRect(rect(52f, 68f, 118f, 77f), 4.5f * u, 4.5f * u, fill(beam))   // 말아 올린 문
                text(85f, 53f, "$md $wks", 13f, SERIF, beam, H.C, V.MID)
                icon(ic, 85f, 91f, 20f, GOLD)
                text(85f, 115f, temp, 26f, SERIF, 0xFFF6E8C8.toInt(), H.C, V.MID)
                text(85f, 140f, cond, 10f, SANS, 0xFFF6E8C8.toInt(), H.C, V.MID)
                c.drawRoundRect(rect(2f, 152f, 168f, 160f), 4 * u, 4 * u, fill(0xFF788456.toInt()))
            }
            7 -> { // 캠핑 랜턴
                val brass = 0xFFB08D57.toInt(); val iron = 0xFF2E2A25.toInt()
                c.drawArc(rect(60f, 4f, 110f, 46f), 180f, 180f, false, stroke(iron, 5f))     // 손잡이
                c.drawPath(path(58f, 26f, 112f, 26f, 124f, 40f, 46f, 40f), fill(iron))       // 뚜껑
                val glass = Path().apply { addRoundRect(rect(44f, 40f, 126f, 124f), 16 * u, 16 * u, Path.Direction.CW) }
                c.drawPath(glass, fill(0xFFF7D58A.toInt()))
                glow(glass, 85f, 82f, 50f, 0xFFE88C32.toInt())
                c.drawPath(glass, stroke(brass, 4f))
                c.drawLine(60 * u, 42 * u, 60 * u, 122 * u, stroke(withAlpha(brass, 150), 2f))
                c.drawLine(110 * u, 42 * u, 110 * u, 122 * u, stroke(withAlpha(brass, 150), 2f))
                c.drawPath(path(46f, 124f, 124f, 124f, 116f, 138f, 54f, 138f), fill(iron))    // 받침
                text(85f, 76f, temp, 34f, SERIF, 0xFF3A2412.toInt(), H.C, V.MID)
                text(85f, 104f, if (rng.isNotEmpty()) "$cond ${rng.replace(" ", "")}" else cond, 10f, SANS, 0xFF58381C.toInt(), H.C, V.MID)
                text(85f, 156f, full, 14f, SANS, fg, H.C, V.MID)
            }
            8 -> { // 캠핑장 나무 표지판
                val wood = 0xFFA87445.toInt(); val dark = 0xFF6B4A2B.toInt(); val carve = 0xFFF8EACE.toInt()
                c.drawRect(rect(80f, 30f, 92f, 166f), fill(dark))                               // 기둥
                c.drawRoundRect(rect(8f, 22f, 162f, 82f), 8 * u, 8 * u, fill(wood))              // 위 판자
                c.drawPath(path(14f, 92f, 140f, 92f, 162f, 114f, 140f, 136f, 14f, 136f), fill(wood))  // 화살표 판자
                for (y in intArrayOf(34, 46, 58, 70, 102, 114, 126)) {
                    val end = if (y < 90) 150f else 136f
                    c.drawLine(14 * u, y * u, end * u, (y + 1) * u, stroke(withAlpha(dark, 70), 1f))   // 나뭇결
                }
                for ((x, y) in listOf(16f to 30f, 154f to 30f, 16f to 74f, 154f to 74f, 22f to 114f)) {
                    c.drawCircle(x * u, y * u, 2 * u, fill(0xFF46301C.toInt()))                    // 못
                }
                text(20f, 52f, md, 36f, SERIF, carve, v = V.MID)
                text(152f, 52f, wk, 14f, SANS, carve, H.R, V.MID)
                icon(ic, 38f, 114f, 24f, GOLD)
                text(54f, 114f, temp, 24f, SERIF, carve, v = V.MID)
                text(98f, 114f, cond, 12f, SANS, carve, v = V.MID)
                c.drawRoundRect(rect(0f, 160f, 170f, 170f), 4 * u, 4 * u, fill(0xFF788456.toInt()))
            }
        }
    }
}
