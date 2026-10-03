package com.kwansik.vinylwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min

/** 타임테이블 위젯 모양: 바탕·투명도·모서리·테두리·보여줄 사람·배치 */
class TtLook(
    var bg: Int = DARK, var transparency: Int = 15, var radius: Int = 11,
    var border: Int = BorderFx.NONE, var borderDp: Int = 4, var borderColor: Int = 0,
    /** 0 = 첫 번째, 1 = 두 번째, 2 = 둘 다 */
    var person: Int = 0,
    /** 0 = 위젯 크기에 맞춰 자동, 1 = 주간, 2 = 오늘, 3 = 세로, 4 = 2명 */
    var layout: Int = 0
) {
    companion object {
        const val DARK = 0
        const val LIGHT = 1
        const val PAPER = 2
        val BG_NAMES = arrayOf("어두운 유리", "밝은 유리", "공책")
        val LAYOUT_NAMES = arrayOf("자동", "주간", "오늘", "세로", "2명")
    }
}

/**
 * 타임테이블 그림 (오전 8시 ~ 오후 8시).
 *  A 주간: 요일 × 시간 막대 / B 오늘: 하루 막대 + 지금·다음 / C 세로: 시간순 목록 / D 2명: 두 줄 하루 막대.
 *  이름·픽업·메모는 비어 있으면 아예 그리지 않음
 */
class TimetableScene(
    ctx: Context, wDp: Float, hDp: Float, private val look: TtLook, private val d: TtData,
    private val now: LocalDateTime = LocalDateTime.now()
) {
    val bitmap: Bitmap
    private val k: Float
    private val W: Int
    private val H: Int
    private lateinit var c: Canvas
    private var u = 1f
    private var CW = 0f
    private var CH = 0f

    private val dark = look.bg == TtLook.DARK
    private val ink = if (dark) 0xFFF2F2EE.toInt() else if (look.bg == TtLook.PAPER) 0xFF2B2622.toInt() else 0xFF1D1B20.toInt()
    private val sub = if (dark) 0xFFB9BDB5.toInt() else if (look.bg == TtLook.PAPER) 0xFF6B6460.toInt() else 0xFF605D66.toInt()
    private val line = if (dark) 0x1FFFFFFF else 0x1F000000
    private val ACC = 0xFFFF8A3D.toInt()
    private val ONBLOCK = 0xFF1E1E1E.toInt()
    private val today = now.dayOfWeek.value
    private val nowMin = now.hour * 60 + now.minute

    init {
        val dens = WidgetGeom.density(ctx)
        k = dens * min(1f, 900f / (max(wDp, hDp) * dens))
        W = (wDp * k).toInt().coerceAtLeast(2); H = (hDp * k).toInt().coerceAtLeast(2)
        bitmap = render()
    }

    // ---------- 그리기 도구 ----------
    private fun tf(w: Int) = Typeface.create(Typeface.SANS_SERIF, w, false)
    private fun p(sz: Float, w: Int, col: Int, a: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sz * u; typeface = tf(w); color = col; textAlign = a }
    private fun t(s: String, x: Float, y: Float, pt: Paint, maxW: Float = Float.MAX_VALUE) {
        var str = s
        if (pt.measureText(str) > maxW) { while (str.isNotEmpty() && pt.measureText("$str…") > maxW) str = str.dropLast(1); str += "…" }
        if (maxW <= 0f) return
        val fm = pt.fontMetrics
        c.drawText(str, x, y - (fm.ascent + fm.descent) / 2f, pt)
    }
    private fun rr(l: Float, tp: Float, w: Float, h: Float, r: Float, col: Int) {
        if (w <= 0f || h <= 0f) return
        c.drawRoundRect(RectF(l, tp, l + w, tp + h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    }
    private fun dot(x: Float, y: Float, r: Float, col: Int) = c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    private fun a(col: Int, al: Int) = (col and 0x00FFFFFF) or (al shl 24)
    private fun blockCol(col: Int, done: Boolean) = if (done) a(col, 0x66) else a(col, 0xF0)
    private fun dayName(day: Int) = Timetable.DAYS[(day - 1).coerceIn(0, 6)]
    private fun name(i: Int) = d.names[i].trim()
    private fun until(m: Int) = if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"

    // ---------- 전체 ----------
    private fun render(): Bitmap {
        val style = if (look.borderDp <= 0) BorderFx.NONE else look.border
        val tPx = look.borderDp * k
        val pad = BorderFx.pad(style, tPx, k).coerceAtMost(min(W, H) * 0.15f)
        val content = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        c = Canvas(content)
        val r = RectF(pad, pad, W - pad, H - pad)
        val rad = min(r.width(), r.height()) * look.radius.coerceIn(0, 50) / 100f
        val path = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
        val alpha = ((100 - look.transparency.coerceIn(0, 100)) * 255 / 100)
        val fill = when (look.bg) { TtLook.DARK -> 0xFF16181A.toInt(); TtLook.LIGHT -> 0xFFF8F6FC.toInt(); else -> 0xFFFFFDF6.toInt() }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = a(fill, alpha) })
        c.save(); c.clipPath(path)
        if (look.bg == TtLook.PAPER) {                                  // 공책 줄
            val lp = Paint().apply { color = 0x3378A0DC }
            var y = r.top + 30 * k
            while (y < r.bottom) { c.drawRect(r.left, y, r.right, y + max(1f, k * 0.6f), lp); y += 16 * k }
        }
        c.translate(r.left, r.top)
        CW = r.width(); CH = r.height()
        val people = when { !d.second -> listOf(0); look.person == 1 -> listOf(1); look.person == 2 -> listOf(0, 1); else -> listOf(0) }
        val lay = if (look.layout != 0) look.layout else when {
            CH >= CW * 1.4f -> 3
            people.size == 2 && CW >= CH * 1.5f -> 4
            CH / k >= 250f && CW / k >= 240f -> 1
            people.size == 2 -> 4
            else -> 2
        }
        val who = people.first()
        fun scale(bw: Float, bh: Float) { u = k * min(CW / k / bw, CH / k / bh).coerceIn(0.7f, 1.5f) }
        if (d.events.isEmpty()) {                                       // 아직 일정이 없으면 안내만
            u = k * min(CW / k / 200f, CH / k / 120f).coerceIn(0.7f, 1.4f)
            t("타임테이블", CW / 2, CH / 2 - 12 * u, p(15f, 900, ink, Paint.Align.CENTER), CW - 20 * u)
            t("눌러서 일정을 넣어 주세요", CW / 2, CH / 2 + 12 * u, p(11f, 600, sub, Paint.Align.CENTER), CW - 20 * u)
        } else when (lay) {
            1 -> { scale(390f, 380f); weekly(who) }
            3 -> { scale(190f, 400f); vertical(who) }
            4 -> { scale(390f, 200f); two(if (d.second) listOf(0, 1) else listOf(0)) }
            else -> { scale(390f, 200f); todayView(who) }
        }
        c.restore()
        val shape = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        Canvas(shape).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() })
        return BorderFx.apply(content, style, tPx, look.borderColor, k, shape)
    }

    // ---------- A 주간 ----------
    private fun weekly(who: Int) {
        val pd = 14 * u
        val title = if (name(who).isEmpty()) "이번 주" else "${name(who)} 일주일"
        t(title, pd, 20 * u, p(15f, 900, ink), CW * 0.45f)
        val cur = d.of(who, today).firstOrNull { it.start <= nowMin && nowMin < it.end }
        t("${dayName(today)} ${Timetable.hm(nowMin)}" + (cur?.let { " · ${it.name} 중" } ?: ""), CW - pd, 20 * u, p(12f, 800, ACC, Paint.Align.RIGHT), CW * 0.5f)
        val days = (1..6).toMutableList().apply { if (d.of(who, 7).isNotEmpty()) add(7) }
        val shown = days.flatMap { d.of(who, it) }
        val hasPick = shown.any { it.go.isNotBlank() || it.back.isNotBlank() }
        val gx = pd + 26 * u; val gy = 50 * u; val gw = CW - pd - gx; val gh = CH - gy - (if (hasPick) 26 * u else 12 * u)
        val cw = gw / days.size
        fun yy(m: Int) = gy + gh * ((m - Timetable.H0).toFloat() / (Timetable.H1 - Timetable.H0)).coerceIn(0f, 1f)
        days.forEachIndexed { i, day ->
            if (day == today) rr(gx + i * cw + 1, gy - 16 * u, cw - 2, gh + 18 * u, 9 * u, a(ACC, 0x22))
            t(dayName(day), gx + i * cw + cw / 2, gy - 8 * u, p(12f, if (day == today) 900 else 700, if (day == today) ACC else sub, Paint.Align.CENTER))
        }
        var hr = Timetable.H0
        while (hr <= Timetable.H1) {
            val y = yy(hr); c.drawRect(gx, y, gx + gw, y + max(1f, k * 0.6f), Paint().apply { color = line })
            t(Timetable.hm(hr).substringBefore(':'), gx - 8 * u, y, p(9f, 600, sub, Paint.Align.RIGHT)); hr += 120
        }
        days.forEachIndexed { i, day ->
            for (e in d.of(who, day)) {
                if (e.end <= Timetable.H0 || e.start >= Timetable.H1) continue
                val y0 = yy(e.start); val y1 = yy(e.end)
                val done = day < today || (day == today && e.end <= nowMin)
                val x0 = gx + i * cw + 3 * u; val bw = cw - 6 * u
                rr(x0, y0, bw, y1 - y0 - 1, 6 * u, blockCol(e.color, done))
                if (y1 - y0 > 16 * u) t(e.name, x0 + bw / 2, (y0 + y1) / 2, p(10f, 800, if (done) a(ONBLOCK, 0x99) else ONBLOCK, Paint.Align.CENTER), bw - 4 * u)
                if (day == today && e.start <= nowMin && nowMin < e.end) {
                    c.drawRoundRect(RectF(x0 - 1.5f * u, y0 - 1.5f * u, x0 + bw + 1.5f * u, y1 + 0.5f * u), 7 * u, 7 * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * u; color = ACC })
                }
                if (e.go.isNotBlank()) dot(x0 + bw - 3 * u, y0, 4 * u, 0xFFFFFFFF.toInt())
                if (e.back.isNotBlank()) dot(x0 + bw - 3 * u, y1, 4 * u, 0xFFFFFFFF.toInt())
            }
        }
        val ti = days.indexOf(today)
        if (ti >= 0 && nowMin in Timetable.H0..Timetable.H1) {
            val ny = yy(nowMin); rr(gx + ti * cw - 2 * u, ny - 1 * u, cw + 4 * u, 2 * u, 1 * u, ACC); dot(gx + ti * cw - 2 * u, ny, 3.5f * u, ACC)
        }
        if (hasPick) t("● = 픽업 (갈 때·올 때)", pd, CH - 12 * u, p(9f, 600, sub))
    }

    // ---------- B 오늘 ----------
    private fun todayView(who: Int) {
        val pd = 16 * u
        val evs = d.of(who, today)
        t(listOf(name(who), "${dayName(today)}요일").filter { it.isNotEmpty() }.joinToString(" · "), pd, 20 * u, p(11f, 700, sub), CW * 0.6f)
        val L = pd; val R = CW - pd; val ty = 36 * u; val bh = 16 * u
        bar(evs, L, R, ty, bh, true)
        val cur = evs.firstOrNull { it.start <= nowMin && nowMin < it.end }
        val focus = cur ?: evs.firstOrNull { it.start > nowMin }
        val y = ty + 44 * u
        val rx = CW * 0.56f
        // 오른쪽: 남은 픽업, 없으면 다음 일정들
        data class Pt(val time: Int, val head: String, val desc: String)
        val picks = evs.flatMap { e ->
            listOfNotNull(
                if (e.go.isNotBlank() && e.start > nowMin) Pt(e.start, "${Timetable.pickIcon(e.go)} ${Timetable.hm(e.start)}", "${e.name} 갈 때 · ${e.go}") else null,
                if (e.back.isNotBlank() && e.end > nowMin) Pt(e.end, "${Timetable.pickIcon(e.back)} ${Timetable.hm(e.end)}", "${e.name} 끝나고 · ${e.back}") else null
            )
        }.sortedBy { it.time }
        val right = if (picks.isNotEmpty()) picks.take(2)
                    else evs.filter { it.start > nowMin && it != focus }.take(2).map { Pt(it.start, Timetable.hm(it.start), it.name) }
        val leftW = (if (right.isEmpty()) R else rx - 14 * u) - (pd + 12 * u)
        if (focus == null) {
            t(if (evs.isEmpty()) "오늘 일정 없음" else "오늘 남은 일정 없음", pd, y + 24 * u, p(16f, 800, sub), R - pd)
        } else {
            rr(pd, y, 4 * u, 56 * u, 2 * u, a(focus.color, 0xFF))
            val head = if (focus === cur) "지금 · ${until(focus.end - nowMin)} 남음" else "다음 · ${until(focus.start - nowMin)} 후"
            t(head, pd + 12 * u, y + 7 * u, p(11f, 700, a(focus.color, 0xFF)), leftW)
            t(focus.name, pd + 12 * u, y + 28 * u, p(22f, 900, ink), leftW)
            t("${Timetable.hm(focus.start)} – ${Timetable.hm(focus.end)}", pd + 12 * u, y + 50 * u, p(11f, 600, sub), leftW)
        }
        if (right.isNotEmpty()) {
            c.drawRect(rx - 12 * u, y, rx - 12 * u + max(1f, k * 0.6f), y + 58 * u, Paint().apply { color = line })
            right.forEachIndexed { i, pt ->
                t(pt.head, rx, y + 8 * u + i * 30 * u, p(12f, 800, ink), R - rx)
                t(pt.desc, rx, y + 22 * u + i * 30 * u, p(10f, 500, sub), R - rx)
            }
        }
        // 맨 아래: 메모, 없으면 다음 일정 있는 날 요약
        val memo = d.memos[who].trim()
        val foot = if (memo.isNotEmpty()) "📝 $memo" else nextDaySummary(who)
        if (foot != null) {
            c.drawRect(L, CH - 34 * u, R, CH - 34 * u + max(1f, k * 0.6f), Paint().apply { color = line })
            t(foot, L, CH - 18 * u, p(10.5f, 600, sub), R - L)
        }
    }

    private fun nextDaySummary(who: Int): String? {
        for (off in 1..7) {
            val day = (today - 1 + off) % 7 + 1
            val evs = d.of(who, day)
            if (evs.isEmpty()) continue
            val label = if (off == 1) "내일(${dayName(day)})" else "${dayName(day)}요일"
            return "$label · " + evs.joinToString(" · ") { "${it.name} ${Timetable.hm(it.start)}" }
        }
        return null
    }

    /** 하루 가로 막대 (8시~8시) + 지금 바늘. ticks = 시각 숫자 */
    private fun bar(evs: List<TtEvent>, L: Float, R: Float, ty: Float, bh: Float, ticks: Boolean, picks: Boolean = false) {
        fun xx(m: Int) = L + (R - L) * ((m - Timetable.H0).toFloat() / (Timetable.H1 - Timetable.H0)).coerceIn(0f, 1f)
        rr(L, ty, R - L, bh, bh / 2, line)
        for (e in evs) {
            if (e.end <= Timetable.H0 || e.start >= Timetable.H1) continue
            val x0 = xx(e.start); val x1 = xx(e.end); val done = e.end <= nowMin
            rr(x0, ty, max(x1 - x0, bh), bh, bh / 2, blockCol(e.color, done))
            if (x1 - x0 > 34 * u) t(e.name, (x0 + x1) / 2, ty + bh / 2, p(8.5f, 800, if (done) a(ONBLOCK, 0x99) else ONBLOCK, Paint.Align.CENTER), x1 - x0 - 6 * u)
            if (picks) {
                if (e.go.isNotBlank()) dot(x0, ty + bh, 4 * u, 0xFFFFFFFF.toInt())
                if (e.back.isNotBlank()) dot(x1, ty + bh, 4 * u, 0xFFFFFFFF.toInt())
            }
        }
        if (ticks) {
            var hr = Timetable.H0
            while (hr <= Timetable.H1) { t(Timetable.hm(hr).substringBefore(':'), xx(hr), ty + bh + 9 * u, p(8.5f, 500, sub, Paint.Align.CENTER)); hr += 120 }
        }
        if (nowMin in Timetable.H0..Timetable.H1) {
            val nx = xx(nowMin)
            rr(nx - 1 * u, ty - 6 * u, 2 * u, bh + 12 * u, 1 * u, ink); dot(nx, ty - 6 * u, 3.5f * u, ink)
        }
    }

    // ---------- C 세로 ----------
    private fun vertical(who: Int) {
        val pd = 14 * u
        t(listOf(name(who), dayName(today)).filter { it.isNotEmpty() }.joinToString(" · "), pd, 20 * u, p(15f, 900, ink), CW * 0.6f)
        t(Timetable.hm(nowMin), CW - pd, 20 * u, p(12f, 800, ACC, Paint.Align.RIGHT))
        val evs = d.of(who, today)
        val memo = d.memos[who].trim()
        val bottom = CH - (if (memo.isNotEmpty()) 54 * u else 10 * u)
        if (evs.isEmpty()) { t("오늘 일정 없음", pd, 60 * u, p(14f, 700, sub)); drawMemo(memo, pd); return }
        // 줄: 픽업(점) / 일정(막대)
        class Row(val time: Int, val ev: TtEvent?, val text: String)
        val rows = evs.flatMap { e ->
            listOfNotNull(
                if (e.go.isNotBlank()) Row(e.start, null, "${Timetable.pickIcon(e.go)} ${e.go}") else null,
                Row(e.start, e, e.name),
                if (e.back.isNotBlank()) Row(e.end, null, "${Timetable.pickIcon(e.back)} ${e.back}") else null
            )
        }
        val tx = pd + 50 * u
        var y = 40 * u
        val top = y
        var left = 0
        for ((i, r) in rows.withIndex()) {
            val h = if (r.ev != null) 42 * u else 30 * u
            if (y + h > bottom) { left = rows.size - i; break }
            val done = if (r.ev != null) r.ev.end <= nowMin else r.time < nowMin
            t(Timetable.hm(r.time), tx - 17 * u, y + 12 * u, p(10.5f, 700, if (done) sub else ink, Paint.Align.RIGHT))
            if (r.ev == null) {
                dot(tx - 9 * u, y + 12 * u, 4 * u, if (done) sub else 0xFFFFFFFF.toInt())
                t(r.text, tx + 2 * u, y + 12 * u, p(11f, 600, if (done) sub else ink), CW - pd - tx)
            } else {
                val e = r.ev
                rr(tx, y, CW - pd - tx, 34 * u, 9 * u, blockCol(e.color, done))
                t(e.name, tx + 10 * u, y + 12 * u, p(13f, 900, if (done) a(ONBLOCK, 0x99) else ONBLOCK), CW - pd - tx - 14 * u)
                t("~ ${Timetable.hm(e.end)}", tx + 10 * u, y + 26 * u, p(9.5f, 600, a(ONBLOCK, if (done) 0x8C else 0xBF)))
                if (e.start <= nowMin && nowMin < e.end) {
                    c.drawRoundRect(RectF(tx - 1 * u, y - 1 * u, CW - pd + 1 * u, y + 35 * u), 10 * u, 10 * u,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * u; color = ACC })
                    dot(tx - 9 * u, y + 17 * u, 5 * u, ACC)
                }
            }
            y += h
        }
        c.drawRect(tx - 10 * u, top, tx - 8 * u, y - 6 * u, Paint().apply { color = line })
        if (left > 0) t("+ ${left}개 더", tx, y + 6 * u, p(10f, 700, sub))
        drawMemo(memo, pd)
    }

    private fun drawMemo(memo: String, pd: Float) {
        if (memo.isEmpty()) return
        c.drawRect(pd, CH - 50 * u, CW - pd, CH - 50 * u + max(1f, k * 0.6f), Paint().apply { color = line })
        t("📝 메모", pd, CH - 34 * u, p(10f, 800, sub))
        t(memo, pd, CH - 17 * u, p(11f, 600, ink), CW - pd * 2)
    }

    // ---------- D 2명 ----------
    private fun two(people: List<Int>) {
        val pd = 16 * u
        t("${dayName(today)}요일 · ${Timetable.hm(nowMin)}", pd, 20 * u, p(12f, 800, ink), CW * 0.5f)
        data class Pk(val time: Int, val text: String)
        val picks = people.flatMap { who ->
            val nm = name(who)
            d.of(who, today).flatMap { e ->
                listOfNotNull(
                    if (e.go.isNotBlank() && e.start > nowMin) Pk(e.start, "${Timetable.pickIcon(e.go)} ${Timetable.hm(e.start)} ${if (nm.isEmpty()) "" else "$nm "}${e.go}") else null,
                    if (e.back.isNotBlank() && e.end > nowMin) Pk(e.end, "${Timetable.pickIcon(e.back)} ${Timetable.hm(e.end)} ${if (nm.isEmpty()) "" else "$nm "}${e.back}") else null
                )
            }
        }.sortedBy { it.time }
        if (picks.isNotEmpty()) t("다음 픽업 " + picks.take(2).joinToString(" · ") { Timetable.hm(it.time) }, CW - pd, 20 * u, p(11f, 700, ACC, Paint.Align.RIGHT), CW * 0.45f)
        val anyName = people.any { name(it).isNotEmpty() }
        val L = pd + (if (anyName) 44 * u else 0f); val R = CW - pd
        val foot = if (picks.isNotEmpty()) 36 * u else 8 * u
        val rowH = (CH - 40 * u - foot) / people.size
        people.forEachIndexed { i, who ->
            val ty = 40 * u + i * rowH
            if (anyName) t(name(who), pd, ty + 8 * u, p(13f, 900, ink), 40 * u)
            val evs = d.of(who, today)
            bar(evs, L, R, ty, 16 * u, false, picks = true)
            val cur = evs.firstOrNull { it.start <= nowMin && nowMin < it.end }
            val nx = evs.firstOrNull { it.start > nowMin }
            when {
                cur != null -> t("지금: ${cur.name} · ${Timetable.hm(cur.end)}까지", L, ty + 30 * u, p(10.5f, 700, a(cur.color, 0xFF)), R - L)
                nx != null -> t("다음: ${nx.name} ${Timetable.hm(nx.start)}", L, ty + 30 * u, p(10.5f, 600, sub), R - L)
                evs.isEmpty() -> t("오늘 일정 없음", L, ty + 30 * u, p(10.5f, 600, sub), R - L)
            }
        }
        if (picks.isNotEmpty()) {
            c.drawRect(pd, CH - 34 * u, CW - pd, CH - 34 * u + max(1f, k * 0.6f), Paint().apply { color = line })
            t(picks.joinToString(" · ") { it.text }, pd, CH - 18 * u, p(10f, 600, sub), CW - pd * 2)
        }
    }
}
