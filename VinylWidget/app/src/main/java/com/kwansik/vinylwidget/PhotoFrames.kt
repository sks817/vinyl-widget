package com.kwansik.vinylwidget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 사진 스티커 프레임 9종. 위젯 칸에서 짧은 변이 120dp 이상이면 2×2 배치, 아니면 1×1 배치.
 *  생활용품 패러디(영수증·택배 송장·영양성분표) + 폴라로이드·다이컷·필름·네컷·스크랩북·기념일.
 *  문구는 칸마다 글자 수 제한 안에서 사용자가 직접 씀. {date}·{md}·{stamp}는 오늘 날짜로 바뀜
 */
object PhotoFrames {
    const val RECEIPT = 0
    const val PARCEL = 1
    const val NUTRI = 2
    const val POLAROID = 3
    const val DIECUT = 4
    const val FILM = 5
    const val FOURCUT = 6
    const val SCRAP = 7
    const val ANNIV = 8
    val NAMES = arrayOf("영수증", "택배 송장", "영양성분표", "폴라로이드", "다이컷", "필름", "네컷", "스크랩북", "기념일")

    class Field(val label: String, val def: String, val max: Int, val date: Boolean = false)

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
        ),
        listOf(Field("아래 손글씨", "바다 처음 간 날", 12), Field("1×1 문구", "{md}", 6)),
        listOf(),
        listOf(Field("날짜 도장", "{stamp}", 9), Field("필름 글자", "FILM 400 ▸ 12", 12)),
        listOf(Field("아래 문구", "♡ our day · {date}", 16), Field("1×1 문구", "♡ {md}", 8)),
        listOf(Field("손글씨", "우리 꼬마 ☀", 8)),
        listOf(Field("이름", "우리 아들", 8), Field("시작한 날", "2020.10.03", 10, date = true))
    )

    /** 사진 칸 수 (네컷은 4장, 1×1에서는 앞의 2장) */
    fun slots(frame: Int) = if (frame == FOURCUT) 4 else 1

    /** 종이 색을 고를 수 있는 프레임 (다이컷·기념일은 사진이 곧 전체) */
    fun hasPaper(frame: Int) = frame != DIECUT && frame != ANNIV

    /** 다이컷 모양 */
    val SHAPES = arrayOf("꽃", "하트", "동그라미", "별")

    /** 프레임마다 처음 테두리 (다이컷은 흰 스티커 테두리, 나머지는 없음) */
    fun defaultBorder(frame: Int) = if (frame == DIECUT) BorderFx.SOLID else BorderFx.NONE

    /** 종이 색. 0 = 프레임 기본 */
    val PAPERS = intArrayOf(
        0, 0xFFFFF3C4.toInt(), 0xFFD9C7A5.toInt(), 0xFFFDE2EA.toInt(),
        0xFFDDF3EA.toInt(), 0xFFDDEBFB.toInt(), 0xFF26262A.toInt()
    )
    val PAPER_NAMES = arrayOf("기본", "크림", "크라프트", "핑크", "민트", "하늘", "검정")
    private val DEFAULT_PAPER = intArrayOf(
        0xFFFBFBF7.toInt(), 0xFFF6F3EA.toInt(), 0xFFFFFFFF.toInt(), 0xFFFBFAF6.toInt(), 0xFFFFFFFF.toInt(),
        0xFF1A1714.toInt(), 0xFFFFD9E4.toInt(), 0xFFF3EAD7.toInt(), 0xFFFFFFFF.toInt()
    )
    fun paperOf(frame: Int, c: Int) = if (c != 0) c else DEFAULT_PAPER[frame.coerceIn(0, DEFAULT_PAPER.size - 1)]

    /** 사진 자르기: zoom 1~3배, ox·oy = 남는 부분 안에서 위치 (−1 왼쪽/위 ~ 1 오른쪽/아래) */
    class Crop(var zoom: Float = 1f, var ox: Float = 0f, var oy: Float = 0f)

    /** 문구 안의 날짜 표시를 오늘 날짜로 */
    fun fill(s: String): String {
        if (!s.contains('{')) return s
        val d = LocalDate.now()
        return s.replace("{date}", "%04d.%02d.%02d".format(d.year, d.monthValue, d.dayOfMonth))
            .replace("{md}", "%02d.%02d".format(d.monthValue, d.dayOfMonth))
            .replace("{stamp}", "'%02d %02d %02d".format(d.year % 100, d.monthValue, d.dayOfMonth))
    }

    /** "2020.10.03" → D+2190 (미래면 D-3). 못 읽으면 null */
    fun dday(s: String): String? = try {
        val p = s.trim().split('.', '-', '/', ' ').filter { it.isNotBlank() }.map { it.toInt() }
        val days = ChronoUnit.DAYS.between(LocalDate.of(p[0], p[1], p[2]), LocalDate.now())
        if (days >= 0) "D+%,d".format(days) else "D-%,d".format(-days)
    } catch (e: Exception) { null }

    class Look(
        val frame: Int, val texts: List<String>, val paper: Int, val shape: Int, val tilt: Int, val radiusPct: Int,
        val border: Int, val borderDp: Int, val borderColor: Int
    )

    /**
     * wPx×hPx 비트맵에 그림 (짧은 변 정사각 칸을 가운데에). dens = 1dp당 px.
     * 테두리·그림자 자리를 남기고 그린 뒤 테두리를 붙이고, 기울기만큼 돌림 (칸 밖으로 안 나가게 줄여서)
     */
    fun render(photos: List<Bitmap?>, crops: List<Crop>, look: Look, wPx: Int, hPx: Int, dens: Float, seed: Int): Bitmap {
        val out = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val s = min(out.width, out.height)
        val tPx = look.borderDp * dens
        val style = if (look.borderDp <= 0) BorderFx.NONE else look.border
        val pad = BorderFx.pad(style, tPx, dens).coerceAtMost(s * 0.25f)
        val inner = s - pad * 2
        val content = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val cc = Canvas(content)
        cc.translate(pad, pad)
        val d = Drawer(cc, inner, s / dens >= 120f, photos, crops, paperOf(look.frame, look.paper), look.texts,
            look.radiusPct.coerceIn(0, 50) / 100f, look.shape, seed, style == BorderFx.NONE)
        when (look.frame) {
            PARCEL -> d.parcel()
            NUTRI -> d.nutri()
            POLAROID -> d.polaroid()
            DIECUT -> d.diecut()
            FILM -> d.film()
            FOURCUT -> d.fourcut()
            SCRAP -> d.scrap()
            ANNIV -> d.anniv()
            else -> d.receipt()
        }
        val art = BorderFx.apply(content, style, tPx, look.borderColor, dens)
        val c = Canvas(out)
        val t = Math.toRadians(look.tilt.toDouble())
        val k = if (look.tilt == 0) 1f else (1.0 / (abs(cos(t)) + abs(sin(t)))).toFloat() * 0.97f
        c.translate(out.width / 2f, out.height / 2f); c.rotate(look.tilt.toFloat()); c.scale(k, k); c.translate(-s / 2f, -s / 2f)
        c.drawBitmap(art, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private class Drawer(
        val c: Canvas, val S: Float, val big: Boolean, val photos: List<Bitmap?>, val crops: List<Crop>, val paper: Int,
        val tx: List<String>, val rad: Float, val shape: Int, val seed: Int, val ownShadow: Boolean
    ) {
        val dark = Color.luminance(paper) < 0.4f
        val ink = if (dark) 0xFFF2F2F2.toInt() else 0xFF222222.toInt()
        val soft = if (dark) 0xFFB8B8B8.toInt() else 0xFF777777.toInt()
        val red = if (dark) 0xFFFF6B6B.toInt() else 0xFFC62828.toInt()
        val BAND = 0xFFE53935.toInt()
        val WHITE = 0xFFFFFFFF.toInt()
        val MONO: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

        fun s(i: Int) = tx.getOrNull(i) ?: ""
        fun tf(w: Int): Typeface = Typeface.create(Typeface.SANS_SERIF, w, false)
        fun fill(col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col }
        /** 종이 그림자: 테두리를 켜면 테두리 쪽에서 그림자를 그리므로 여기선 안 그림 */
        fun shadowed(col: Int) = fill(col).apply { if (ownShadow) setShadowLayer(S * 0.035f, 0f, S * 0.016f, 0x55000000) }
        fun r(): Float = S * rad

        /** 글자 (y = 세로 가운데). 너무 길면 조금 줄이고 그래도 길면 자름 */
        fun text(
            str: String, x: Float, y: Float, size: Float, face: Typeface, col: Int,
            align: Paint.Align = Paint.Align.LEFT, maxW: Float = Float.MAX_VALUE, glow: Int = 0
        ) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = face; color = col; textAlign = align }
            if (glow != 0) p.setShadowLayer(size * 0.35f, 0f, 0f, glow)
            var t = str
            val w = p.measureText(t)
            if (w > maxW) {
                p.textSize = max(size * 0.72f, size * maxW / w)
                while (t.isNotEmpty() && p.measureText(t) > maxW) t = t.dropLast(1)
            }
            val fm = p.fontMetrics
            c.drawText(t, x, y - (fm.ascent + fm.descent) / 2f, p)
        }

        /** 사진을 상자에 채움: 기본은 가운데 기준 꽉 채우기, 확대·위치는 사용자가 정함. 사진이 없으면 회색 칸 */
        fun photo(dst: RectF, slot: Int = 0) {
            val b = photos.getOrNull(slot)
            if (b == null) {
                c.drawRect(dst, fill(0xFFCFD8DC.toInt()))
                text("사진", dst.centerX(), dst.centerY(), dst.height() * 0.2f, tf(700), 0xFF78909C.toInt(), Paint.Align.CENTER, dst.width() * 0.9f)
                return
            }
            val cr = crops.getOrNull(slot) ?: Crop()
            val ar = dst.width() / dst.height(); val br = b.width.toFloat() / b.height
            var sw = if (br > ar) b.height * ar else b.width.toFloat()
            var sh = if (br > ar) b.height.toFloat() else b.width / ar
            val z = cr.zoom.coerceIn(1f, 4f); sw /= z; sh /= z
            val cx = b.width / 2f + cr.ox.coerceIn(-1f, 1f) * (b.width - sw) / 2f
            val cy = b.height / 2f + cr.oy.coerceIn(-1f, 1f) * (b.height - sh) / 2f
            val src = Rect((cx - sw / 2).toInt(), (cy - sh / 2).toInt(), (cx + sw / 2).toInt(), (cy + sh / 2).toInt())
            c.drawBitmap(b, src, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }

        fun photoClip(path: Path, bounds: RectF, slot: Int = 0) { c.save(); c.clipPath(path); photo(bounds, slot); c.restore() }

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

        /** 종이 카드 (모서리 R) 그리고 그 안으로 잘라내기 시작 → 끝나면 c.restore() */
        fun card(box: RectF, col: Int = paper) {
            val rr = min(r(), box.width() / 2)
            val path = Path().apply { addRoundRect(box, rr, rr, Path.Direction.CW) }
            c.drawPath(path, shadowed(col))
            c.save(); c.clipPath(path)
        }

        fun tape(cx: Float, cy: Float, w: Float, h: Float, rotDeg: Float, col: Int) {
            c.save(); c.translate(cx, cy); c.rotate(rotDeg)
            val p = Path(); val n = 6
            p.moveTo(-w / 2, -h / 2)
            for (i in 0..n) p.lineTo(-w / 2 + w * i / n, -h / 2 + (if (i % 2 == 1) h * 0.12f else 0f))
            for (i in n downTo 0) p.lineTo(-w / 2 + w * i / n, h / 2 - (if (i % 2 == 1) h * 0.12f else 0f))
            p.close()
            c.drawPath(p, fill(col)); c.restore()
        }

        fun starPath(cx: Float, cy: Float, ro: Float, ri: Float, n: Int = 5) = Path().apply {
            for (i in 0 until n * 2) {
                val a = -Math.PI / 2 + i * Math.PI / n; val q = if (i % 2 == 0) ro else ri
                val x = cx + (q * cos(a)).toFloat(); val y = cy + (q * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }

        fun heartPath(cx: Float, cy: Float, s: Float) = Path().apply {
            moveTo(cx, cy + s * 0.36f)
            cubicTo(cx - s * 0.62f, cy - s * 0.04f, cx - s * 0.4f, cy - s * 0.56f, cx, cy - s * 0.26f)
            cubicTo(cx + s * 0.4f, cy - s * 0.56f, cx + s * 0.62f, cy - s * 0.04f, cx, cy + s * 0.36f)
            close()
        }

        fun scallop(cx: Float, cy: Float, rr: Float, n: Int = 11, d: Float = 0.07f) = Path().apply {
            var i = 0
            while (i <= 360) {
                val a = Math.toRadians(i.toDouble())
                val q = rr * (1 - d + d * cos(a * n)).toFloat()
                val x = cx + (q * cos(a)).toFloat(); val y = cy + (q * sin(a)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
                i += 2
            }
            close()
        }

        // ---- 영수증: 위쪽만 둥글고 아래는 지그재그로 찢김 ----
        fun receipt() {
            val w = S * (if (big) 0.66f else 0.7f); val l = (S - w) / 2; val t = S * 0.03f; val b = S * 0.97f
            val rr = min(r() * 0.6f, w / 2)
            val path = Path().apply {
                moveTo(l + rr, t); lineTo(l + w - rr, t); quadTo(l + w, t, l + w, t + rr); lineTo(l + w, b)
                val n = 10
                for (i in n downTo 0) lineTo(l + w * i / n, b - (if (i % 2 == 1) S * 0.025f else 0f))
                lineTo(l, t + rr); quadTo(l, t, l + rr, t); close()
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
            if (big) {
                c.drawRect(m, m, S - m, m + S * 0.1f, fill(BAND))
                text(s(0), m + S * 0.04f, m + S * 0.05f, S * 0.06f, tf(900), WHITE, Paint.Align.LEFT, S * 0.56f)
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
                text("파손주의", S / 2, m + S * 0.63f, S * 0.08f, tf(900), WHITE, Paint.Align.CENTER)
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
                val l = m + pw + S * 0.04f; val rt = S - m - S * 0.04f; val mw = rt - l
                val bar = fill(ink)
                text("영양정보", l, m + S * 0.08f, S * 0.072f, tf(900), ink, Paint.Align.LEFT, mw)
                text("총 내용량 ${s(0)}", l, m + S * 0.14f, S * 0.034f, tf(500), ink, Paint.Align.LEFT, mw)
                c.drawRect(l, m + S * 0.17f, rt, m + S * 0.188f, bar)
                var y = m + S * 0.22f
                text("1일 기준치 대비", rt, y, S * 0.03f, tf(700), ink, Paint.Align.RIGHT, mw)
                y += S * 0.025f
                for (i in 0..4) {
                    c.drawRect(l, y, rt, y + max(1f, S * 0.004f), bar)
                    y += S * 0.055f
                    text(s(1 + i * 2), l, y - S * 0.025f, S * 0.044f, tf(if (i == 0) 900 else 700), ink, Paint.Align.LEFT, mw * 0.55f)
                    text(s(2 + i * 2), rt, y - S * 0.025f, S * 0.044f, tf(900), if (i == 0) red else ink, Paint.Align.RIGHT, mw * 0.44f)
                }
                c.drawRect(l, y, rt, y + S * 0.012f, bar)
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

        // ---- 폴라로이드: 흰 액자 + 아래 손글씨 + 위에 마스킹테이프 ----
        fun polaroid() {
            val box = RectF(S * 0.13f, S * 0.07f, S * 0.87f, S * 0.93f)
            card(box)
            val ins = S * (if (big) 0.045f else 0.05f)
            photo(RectF(box.left + ins, box.top + ins, box.right - ins, box.top + ins + (box.width() - ins * 2)))
            if (big) text(s(0), S / 2, box.bottom - S * 0.075f, S * 0.065f, tf(700), ink, Paint.Align.CENTER, box.width() * 0.9f)
            else text(s(1), S / 2, box.bottom - S * 0.08f, S * 0.085f, tf(700), ink, Paint.Align.CENTER, box.width() * 0.9f)
            c.restore()
            tape(S / 2, box.top + S * 0.005f, S * 0.27f, S * 0.075f, 5f, 0xD9F4C7A1.toInt())
        }

        // ---- 다이컷: 사진을 꽃·하트·원·별 모양으로 오림 (흰 테두리는 테두리 설정) ----
        fun diecut() {
            val cx = S / 2; val cy = S / 2
            val path = when (shape) {
                1 -> heartPath(cx, cy + S * 0.04f, S * 1.02f)
                2 -> Path().apply { addCircle(cx, cy, S * 0.46f, Path.Direction.CW) }
                3 -> starPath(cx, cy + S * 0.03f, S * 0.5f, S * 0.3f)
                else -> scallop(cx, cy, S * 0.48f)
            }
            if (ownShadow) c.drawPath(path, shadowed(WHITE))
            photoClip(path, RectF(0f, 0f, S, S))
        }

        // ---- 필름: 35mm 필름 칸 + 주황색 날짜 도장 ----
        fun film() {
            val m = S * 0.035f
            if (big) {
                card(RectF(m, m, S - m, S - m))
                val hole = fill(0xFFE9E2D0.toInt())
                for (i in 0 until 7) {
                    val x = S * (0.082f + i * 0.1265f)
                    c.drawRoundRect(RectF(x, S * 0.076f, x + S * 0.059f, S * 0.117f), S * 0.012f, S * 0.012f, hole)
                    c.drawRoundRect(RectF(x, S * 0.883f, x + S * 0.059f, S * 0.924f), S * 0.012f, S * 0.012f, hole)
                }
                val ph = RectF(S * 0.07f, S * 0.153f, S * 0.93f, S * 0.847f)
                photo(ph)
                c.drawRect(ph, fill(0x14FFAA3C))
                text(s(0), S * 0.9f, S * 0.8f, S * 0.065f, MONO, 0xFFFF9A2E.toInt(), Paint.Align.RIGHT, S * 0.6f, glow = 0xE6FF7800.toInt())
                text(s(1), S * 0.094f, S * 0.95f, S * 0.03f, MONO, 0xFFD9A441.toInt(), Paint.Align.LEFT, S * 0.8f)
                c.restore()
            } else {
                val box = RectF(m, m, S - m, S - m)
                val rr = min(max(r(), S * 0.08f), box.width() / 2)
                val path = Path().apply { addRoundRect(box, rr, rr, Path.Direction.CW) }
                if (ownShadow) c.drawPath(path, shadowed(paper))
                photoClip(path, box)
                text(s(0), S * 0.88f, S * 0.85f, S * 0.09f, MONO, 0xFFFF9A2E.toInt(), Paint.Align.RIGHT, S * 0.8f, glow = 0xE6FF7800.toInt())
            }
        }

        // ---- 네컷: 사진 4장(1×1은 2장) + 아래 문구 ----
        fun fourcut() {
            val m = S * 0.035f
            card(RectF(m, m, S - m, S - m))
            val pr = S * 0.035f
            val cap = if (dark) ink else 0xFFD0567D.toInt()
            if (big) {
                for (i in 0 until 4) {
                    val x = S * (0.082f + (i % 2) * 0.43f); val y = S * (0.082f + (i / 2) * 0.376f)
                    val box = RectF(x, y, x + S * 0.406f, y + S * 0.353f)
                    photoClip(Path().apply { addRoundRect(box, pr, pr, Path.Direction.CW) }, box, i)
                }
                text(s(0), S / 2, S * 0.894f, S * 0.053f, tf(800), cap, Paint.Align.CENTER, S * 0.85f)
            } else {
                for (i in 0 until 2) {
                    val x = S * (0.11f + i * 0.405f)
                    val box = RectF(x, S * 0.11f, x + S * 0.375f, S * 0.785f)
                    photoClip(Path().apply { addRoundRect(box, pr, pr, Path.Direction.CW) }, box, i)
                }
                text(s(1), S / 2, S * 0.875f, S * 0.075f, tf(800), cap, Paint.Align.CENTER, S * 0.8f)
            }
            c.restore()
        }

        // ---- 스크랩북: 찢은 종이 + 흰 테두리 사진 + 와시테이프 + 낙서 ----
        fun scrap() {
            if (big) {
                val path = Path()
                val l = S * 0.06f; val t = S * 0.07f; val rt = S * 0.94f; val b = S * 0.94f
                path.moveTo(l, t)
                for (i in 0..20) path.lineTo(l + (rt - l) * i / 20, t + (if (i % 2 == 1) S * 0.018f else 0f))
                for (i in 0..20) path.lineTo(rt + (if (i % 2 == 1) S * 0.012f else 0f), t + (b - t) * i / 20)
                for (i in 20 downTo 0) path.lineTo(l + (rt - l) * i / 20, b - (if (i % 2 == 1) S * 0.018f else 0f))
                path.close()
                c.drawPath(path, shadowed(paper))
                c.save(); c.translate(S * 0.47f, S * 0.45f); c.rotate(2.5f)
                val pw = S * 0.66f; val phh = S * 0.61f
                c.drawRect(-pw / 2, -phh / 2, pw / 2, phh / 2, fill(WHITE))
                photo(RectF(-pw / 2 + S * 0.03f, -phh / 2 + S * 0.03f, pw / 2 - S * 0.03f, phh / 2 - S * 0.03f))
                c.restore()
                tape(S * 0.19f, S * 0.15f, S * 0.24f, S * 0.07f, -34f, 0xD19FD3C7.toInt())
                tape(S * 0.82f, S * 0.76f, S * 0.24f, S * 0.07f, -34f, 0xD1F7B2C4.toInt())
                text(s(0), S * 0.1f, S * 0.88f, S * 0.065f, tf(800), if (dark) ink else 0xFF7A4A2A.toInt(), Paint.Align.LEFT, S * 0.6f)
                c.drawPath(heartPath(S * 0.865f, S * 0.17f, S * 0.13f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFE8657F.toInt(); style = Paint.Style.STROKE; strokeWidth = S * 0.01f
                })
                c.drawPath(starPath(S * 0.88f, S * 0.88f, S * 0.042f, S * 0.019f), fill(0xFFF5B82E.toInt()))
            } else {
                card(RectF(S * 0.06f, S * 0.06f, S * 0.94f, S * 0.94f))
                c.restore()
                c.save(); c.translate(S / 2, S * 0.48f); c.rotate(-3f)
                val pw = S * 0.675f
                c.drawRect(-pw / 2, -pw / 2, pw / 2, pw / 2, fill(WHITE))
                photo(RectF(-pw / 2 + S * 0.04f, -pw / 2 + S * 0.04f, pw / 2 - S * 0.04f, pw / 2 - S * 0.04f))
                c.restore()
                tape(S / 2, S * 0.12f, S * 0.37f, S * 0.11f, 3f, 0xD19FD3C7.toInt())
            }
        }

        // ---- 기념일: 사진 꽉 채우고 아래에 이름 + D+ 날짜 ----
        fun anniv() {
            val m = S * 0.035f
            val box = RectF(m, m, S - m, S - m)
            val rr = min(max(r(), S * 0.06f), box.width() / 2)
            val path = Path().apply { addRoundRect(box, rr, rr, Path.Direction.CW) }
            if (ownShadow) c.drawPath(path, shadowed(WHITE))
            c.save(); c.clipPath(path)
            photo(box)
            c.drawRect(box.left, S * 0.53f, box.right, box.bottom, Paint().apply {
                shader = LinearGradient(0f, S * 0.53f, 0f, box.bottom, 0, 0x8C000000.toInt(), Shader.TileMode.CLAMP)
            })
            c.restore()
            val d = dday(s(1)) ?: "D+?"
            if (big) {
                text(s(0), S * 0.105f, S * 0.73f, S * 0.065f, tf(700), 0xE6FFFFFF.toInt(), Paint.Align.LEFT, S * 0.8f)
                text(d, S * 0.1f, S * 0.86f, S * 0.155f, tf(900), WHITE, Paint.Align.LEFT, S * 0.82f)
            } else {
                text(d, S * 0.11f, S * 0.82f, S * 0.17f, tf(900), WHITE, Paint.Align.LEFT, S * 0.8f)
            }
        }
    }
}
