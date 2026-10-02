package com.kwansik.vinylwidget

import android.Manifest
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.CalendarContract
import android.util.Log
import android.widget.RemoteViews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** 캘린더 일정 하나 */
data class CalEvent(
    val title: String, val day: LocalDate, val start: String?, val allDay: Boolean, val color: Int,
    val beginMs: Long = 0L, val endMs: Long = 0L, val end: String? = null
)

/** 기기 캘린더(구글·삼성 등 기기에 연결된 모든 캘린더)에서 오늘부터 7일 일정 읽기 */
object CalendarReader {
    fun hasPermission(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun upcoming(ctx: Context, days: Int = 7): List<CalEvent> {
        if (!hasPermission(ctx)) return emptyList()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val begin = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin); ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR
        )
        val out = ArrayList<CalEvent>()
        try {
            ctx.contentResolver.query(uri, proj, "${CalendarContract.Instances.VISIBLE}=1", null,
                "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(제목 없음)"
                    val b = c.getLong(1); val e = c.getLong(2); val allDay = c.getInt(3) == 1
                    if (!allDay && e < now) continue                       // 이미 끝난 일정은 빼기
                    // 종일 일정은 UTC 자정 기준으로 저장됨
                    val day = if (allDay) Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate()
                              else Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
                    val t = Instant.ofEpochMilli(b).atZone(zone).toLocalTime()
                    val te = Instant.ofEpochMilli(e).atZone(zone).toLocalTime()
                    val endS = "%d:%02d".format(te.hour, te.minute)
                    if (day.isBefore(today)) {                              // 어제 시작해 아직 진행 중
                        if (!allDay) out += CalEvent(title, today, "%d:%02d".format(t.hour, t.minute), false, c.getInt(4), b, e, endS)
                        continue
                    }
                    out += CalEvent(title, day, if (allDay) null else "%d:%02d".format(t.hour, t.minute), allDay, c.getInt(4), b, e, endS)
                }
            }
        } catch (e: Exception) {
            Log.e("VinylWidget", "calendar", e)
        }
        return out.sortedWith(compareBy({ it.day }, { !it.allDay }, { it.start ?: "" }))
    }
}

/**
 * 일정 위젯 그림. 이 앱다운 두 가지 디자인 (기본 캘린더 위젯과 다르게):
 *  SETLIST = 오늘의 셋리스트 — 일정이 LP 트랙리스트(01, 02…)가 되고 날마다 SIDE A(오늘)·B(내일)…, 다음 일정은
 *            'NEXT TRACK'(강조 띠 + 이퀄라이저 + 남은 시간). 옆에 레코드 위젯과 같은 색의 LP가 걸쳐 있음
 *  TICKET  = 티켓 — 다음 일정이 레트로 입장권(절취선·홈·바코드, 스텁에 시각과 남은 시간), 나머지는 아래 목록
 * 바탕·글자색은 다른 위젯과 같은 공통 배경과 자동 색. 날짜는 넣지 않음(날씨·날짜 위젯과 같이 쓰는 걸 전제)
 * 크기별로 배치가 바뀜: 4×1 / 4×2 / 2×4·2×2
 */
class MemoScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int, private val accent: Int,
    private val quirky: Boolean, private val design: Int = SETLIST,
    private val discColor: Int = 0xFF111111.toInt(), private val labelColor: Int = 0xFF7E6BC4.toInt()
) {
    companion object {
        const val SETLIST = 1; const val TICKET = 2
        val DESIGN_NAMES = arrayOf("셋리스트", "티켓")
        private const val INK = 0xFF2B2622.toInt()
    }

    val bitmap: Bitmap
    private val c: Canvas
    private val W: Float
    private val H: Float
    private val k: Float
    private val wDp = wDp
    private val hDp = hDp

    init {
        val dens = WidgetGeom.density(ctx)
        k = dens * min(1f, 1000f / (max(wDp, hDp) * dens))
        bitmap = Bitmap.createBitmap((wDp * k).toInt().coerceAtLeast(1), (hDp * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap); W = bitmap.width.toFloat(); H = bitmap.height.toFloat()
    }

    private val SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MED = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private fun p(size: Float, tf: Typeface, col: Int, a: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = a }
    private fun small(v: Float) = max(v, 10f * k)
    private fun top(s: String, x: Float, y: Float, pt: Paint) = c.drawText(s, x, y - pt.fontMetrics.ascent, pt)
    private fun mid(s: String, x: Float, y: Float, pt: Paint) { val fm = pt.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, pt) }
    private fun ell(s: String, pt: Paint, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (pt.measureText(s) <= maxW) return s
        var t = s; while (t.isNotEmpty() && pt.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
    private fun alpha(col: Int, a: Int) = (col and 0x00FFFFFF) or (a shl 24)
    private fun rr(l: Float, t: Float, w: Float, h: Float, r: Float, col: Int) =
        c.drawRoundRect(RectF(l, t, l + w, t + h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })

    // ---- 시각 글자 ----
    private fun dayName(d: LocalDate): String {
        val t = LocalDate.now()
        return when (d) { t -> "오늘"; t.plusDays(1) -> "내일"
            else -> "${d.monthValue}.${d.dayOfMonth}(${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})" }
    }
    private fun span(e: CalEvent): String = when {
        e.allDay -> "${dayName(e.day)} · 종일"
        else -> "${dayName(e.day)} ${e.start}" + (if (e.day == LocalDate.now() && e.end != null) " – ${e.end}" else "")
    }
    private fun whenS(e: CalEvent) = if (e.allDay) "${dayName(e.day)} · 종일" else "${dayName(e.day)} ${e.start}"
    private fun rel(e: CalEvent): String {
        val now = System.currentTimeMillis()
        if (e.allDay) return if (e.day == LocalDate.now()) "오늘 종일" else dayName(e.day)
        if (e.beginMs in 1..now && now < e.endMs) return "진행 중"
        val min = (e.beginMs - now) / 60_000
        return when {
            e.beginMs <= 0L -> dayName(e.day)
            min < 1 -> "곧 시작"
            min < 60 -> "${min}분 후"
            e.day == LocalDate.now() -> "${min / 60}시간 후"
            else -> dayName(e.day)
        }
    }

    /** 이퀄라이저 막대 3개 */
    private fun eq(x: Float, y: Float, h: Float, col: Int) {
        val w = h * 0.22f
        floatArrayOf(0.5f, 1f, 0.72f).forEachIndexed { i, f -> rr(x + i * w * 1.6f, y + h * (1 - f), w, h * f, w * 0.3f, col) }
    }

    /** LP: 레코드 위젯과 같은 판 색 + 홈·광택, 라벨은 배경화면 색(병맛이면 왕눈이 라벨). label=라벨 글자 넣을지 */
    private fun lp(cx: Float, cy: Float, d: Float, label: Boolean) {
        val r = RectF(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = discColor }
        c.drawCircle(cx, cy, if (quirky) d * 0.48f else d / 2, body)
        ctx.getDrawable(if (quirky) R.drawable.vinyl_grooves_fun else R.drawable.vinyl_grooves)?.let {
            it.setBounds(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()); it.draw(c)
        }
        if (quirky) {
            c.drawBitmap(LabelRenderer.draw(null, labelColor, true), null, r, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return
        }
        val lr = d * 0.31f
        c.drawCircle(cx, cy, lr, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = labelColor })
        c.drawCircle(cx, cy, lr * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = lr * 0.03f; color = 0x40FFFFFF })
        if (label) {
            val today = LocalDate.now()
            mid("SIDE A", cx, cy - lr * 0.45f, p(lr * 0.2f, BOLD, 0xE6FFFFFF.toInt(), Paint.Align.CENTER))
            mid("${today.monthValue}.${today.dayOfMonth}", cx, cy + lr * 0.45f, p(lr * 0.26f, SERIF, 0xFFFFFFFF.toInt(), Paint.Align.CENTER))
        }
        c.drawCircle(cx, cy, d * 0.02f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111111.toInt() })
    }

    fun draw(events: List<CalEvent>, permission: Boolean) {
        if (events.isEmpty()) { empty(permission); return }
        if (design == TICKET) ticket(events) else setlist(events)
    }

    /** 일정이 없을 때: 가운데 한 줄 (셋리스트면 LP, 병맛이면 스티커와 함께) */
    private fun empty(permission: Boolean) {
        val msg = if (!permission) "눌러서 캘린더 권한 허용" else if (design == TICKET) "예정된 티켓이 없어요" else "오늘은 쉬는 트랙 ♪"
        val sz = small(min(H * 0.15f, W * 0.07f))
        if (design == SETLIST) { c.save(); c.clipRect(0f, 0f, W, H); lp(if (W > H * 1.55f) -H * 0.15f else W / 2, if (W > H * 1.55f) H / 2 else -W * 0.05f, if (W > H * 1.55f) H * 1.3f else W * 0.8f, false); c.restore() }
        val pt = p(sz, BOLD, sub, Paint.Align.CENTER)
        val cx = if (design == SETLIST && W > H * 1.55f) W * 0.6f else W / 2
        val cy = if (design == SETLIST && W <= H * 1.55f) H * 0.7f else H / 2
        TextWrap.wrap(msg, pt, W * 0.6f, 2).forEachIndexed { i, l -> mid(l, cx, cy + i * sz * 1.4f, pt) }
    }

    // ================= 셋리스트 =================
    private fun setlist(events: List<CalEvent>) {
        val short = W >= H * 1.55f && hDp < 110f
        val wide = W >= H * 1.55f
        val next = events[0]
        if (short) {                                  // ---- 4×1: [LP 조금] NEXT TRACK / 제목 …… 시각 / 02 03 ----
            val d = H * 1.55f; val cx = H * 0.1f
            c.save(); c.clipRect(0f, 0f, W, H); lp(cx, H / 2, d, false); c.restore()
            val l = cx + d / 2 + H * 0.2f; val r = W - H * 0.3f
            val lb = small(H * 0.105f)
            val lp_ = p(lb, BOLD, accent)
            mid("NEXT TRACK", l, H * 0.26f, lp_)
            eq(l + lp_.measureText("NEXT TRACK") + H * 0.08f, H * 0.2f, H * 0.11f, accent)
            mid(rel(next), r, H * 0.26f, p(lb, BOLD, accent, Paint.Align.RIGHT))
            val timeS = next.start ?: "종일"
            val tp = p(H * 0.2f, SERIF, fg, Paint.Align.RIGHT)
            mid(timeS, r, H * 0.53f, tp)
            val np = p(H * 0.23f, BLACK, fg)
            mid(ell(next.title, np, r - l - tp.measureText(timeS) - H * 0.2f), l, H * 0.53f, np)
            var x = l; val sz = small(H * 0.12f)
            events.drop(1).take(2).forEachIndexed { i, e ->
                val num = "0${i + 2}"; val npp = p(sz, SERIF, sub)
                mid(num, x, H * 0.8f, npp); x += npp.measureText(num) + sz * 0.4f
                val tp2 = p(sz, MED, sub)
                val t = ell((if (e.day == LocalDate.now()) (e.start ?: "종일") else dayName(e.day)) + " " + e.title, tp2, if (i == 0) (r - x) * 0.55f else r - x)
                mid(t, x, H * 0.8f, tp2); x += tp2.measureText(t) + sz * 1.2f
            }
            return
        }
        val l: Float; val r: Float; var y: Float; val u: Float
        if (wide) {                                   // ---- 4×2: 왼쪽에 LP가 걸쳐 있고 오른쪽에 트랙리스트 ----
            val d = H * 1.35f; val cx = -d * 0.2f
            c.save(); c.clipRect(0f, 0f, W, H); lp(cx, H / 2, d, false); c.restore()
            l = cx + d / 2 + H * 0.16f; r = W - H * 0.12f; u = H * 0.085f; y = H * 0.16f
        } else {                                      // ---- 2×4·2×2: 위에 LP(라벨에 SIDE A·날짜), 아래 트랙리스트 ----
            val d = min(W * 0.92f, H * 0.5f); val cy = d * 0.28f
            c.save(); c.clipRect(0f, 0f, W, H); lp(W / 2, cy, d, true); c.restore()
            l = W * 0.1f; r = W * 0.9f; u = min(W * 0.075f, H * 0.06f); y = cy + d / 2 + u * 1.3f
        }
        mid("오늘의 셋리스트", l, y, p(small(u * 0.8f), BOLD, sub)); y += u * 1.55f
        var lastDay = LocalDate.now(); var n = 1
        val showEq = wDp >= 250f
        for (e in events) {
            if (e.day != lastDay) {                   // SIDE B · 내일 ───────
                if (y > H - u * 2.2f) break
                y += u * 0.15f
                val side = "SIDE ${'A' + java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), e.day).toInt().coerceIn(1, 25)} · ${dayName(e.day)}"
                val sp = p(small(u * 0.72f), BOLD, sub)
                mid(side, l, y, sp)
                val tw = sp.measureText(side)
                c.drawRect(l + tw + u * 0.5f, y, r, y + max(1f, k), Paint().apply { color = alpha(sub, 0x40) })
                y += u * 1.35f; lastDay = e.day
            }
            if (y > H - u * 0.9f) break
            val isNext = n == 1
            if (isNext) rr(l - u * 0.45f, y - u * 0.95f, r - l + u * 0.9f, u * 1.9f, u * 0.55f, alpha(accent, 0x24))
            mid("%02d".format(n), l, y, p(u * 0.95f, SERIF, if (isNext) accent else sub))
            val tx = l + u * 1.75f
            // 넓으면 다음 곡에 남은 시간까지 (좁으면 제목이 먼저 보이게 시각만)
            val tm = if (isNext && showEq && !next.allDay && rel(next).endsWith("후")) "${e.start} · ${rel(e)}" else (e.start ?: "종일")
            val tmp = p(u * 0.85f, if (isNext) BOLD else MED, if (isNext) accent else sub, Paint.Align.RIGHT)
            mid(tm, r, y, tmp)
            var mw = tmp.measureText(tm)
            if (isNext && showEq) { eq(r - mw - u * 1.15f, y - u * 0.42f, u * 0.8f, accent); mw += u * 1.35f }
            val np = p(u, if (isNext) BLACK else BOLD, fg)
            mid(ell(e.title, np, r - tx - mw - u * 0.5f), tx, y, np)
            y += u * 1.75f; n++
        }
    }

    // ================= 티켓 =================
    /** 입장권 모양: 둥근 모서리 + 절취선 자리 양쪽 반원 홈 (가로형은 위·아래, 세로형은 왼·오른쪽) */
    private fun ticketPath(l: Float, t: Float, w: Float, h: Float, at: Float, vertical: Boolean): Path {
        val nr = min(w, h) * 0.09f; val r = min(w, h) * 0.08f
        val path = Path().apply { addRoundRect(RectF(l, t, l + w, t + h), r, r, Path.Direction.CW) }
        val notches = Path().apply {
            if (!vertical) { addCircle(at, t, nr, Path.Direction.CW); addCircle(at, t + h, nr, Path.Direction.CW) }
            else { addCircle(l, at, nr, Path.Direction.CW); addCircle(l + w, at, nr, Path.Direction.CW) }
        }
        path.op(notches, Path.Op.DIFFERENCE)
        return path
    }

    private fun barcode(l: Float, t: Float, w: Float, h: Float) {
        val pt = Paint().apply { color = alpha(INK, 0xCC) }
        var x = l; var i = 0
        val bars = intArrayOf(1, 2, 1, 3, 1, 1, 2); val gaps = intArrayOf(1, 1, 2, 1)
        while (x < l + w) { val bw = bars[i % 7] * w / 70f; c.drawRect(x, t, min(x + bw, l + w), t + h, pt); x += bw + gaps[i % 4] * w / 70f; i++ }
    }

    private fun chip(s: String, cx: Float, cy: Float, size: Float, col: Int, center: Boolean) {
        val pt = p(size, BOLD, col or 0xFF000000.toInt(), Paint.Align.CENTER)
        val w = pt.measureText(s) + size * 1.1f; val h = size * 1.7f
        val l = if (center) cx - w / 2 else cx - w
        rr(l, cy - h / 2, w, h, h / 2, alpha(col, 0x2E))
        mid(s, l + w / 2, cy, pt)
    }

    private fun stamp(cx: Float, cy: Float, size: Float) {
        if (!quirky) return
        Assets.get(ctx, R.drawable.st_letsgo)?.let {
            c.save(); c.rotate(12f, cx, cy)
            c.drawBitmap(it, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
    }

    private fun ticket(events: List<CalEvent>) {
        val e = events[0]
        val paper = if (quirky) 0xFFFFE9A8.toInt() else 0xFFFFF6E6.toInt()
        val soft = 0xFF857868.toInt()
        val short = W >= H * 1.55f && hDp < 110f
        val wide = W >= H * 1.55f
        if (short || wide) {
            val pad = if (short) H * 0.12f else H * 0.08f
            val tl = pad; val tt = pad; val tw = W - pad * 2; val th = if (short) H - pad * 2 else H * 0.55f
            val stub = tl + tw * 0.72f
            val path = ticketPath(tl, tt, tw, th, stub, false)
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper; setShadowLayer(th * 0.08f, 0f, th * 0.03f, 0x33000000) })
            c.save(); c.clipPath(path); c.drawRect(tl, tt, tl + th * 0.07f, tt + th, Paint().apply { color = e.color or 0xFF000000.toInt() }); c.restore()
            c.drawLine(stub, tt + th * 0.14f, stub, tt + th * 0.86f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = soft; strokeWidth = max(1f, k * 1.2f); pathEffect = android.graphics.DashPathEffect(floatArrayOf(th * 0.04f, th * 0.05f), 0f) })
            val l = tl + th * 0.22f
            mid("ADMIT ONE · 다음 일정", l, tt + th * 0.21f, p(small(th * 0.085f), BOLD, soft))
            mid(ell(e.title, p(th * 0.21f, BLACK, INK), stub - l - th * 0.12f), l, tt + th * 0.48f, p(th * 0.21f, BLACK, INK))
            mid(span(e), l, tt + th * 0.76f, p(small(th * 0.095f), MED, soft))
            val sc = stub + (tl + tw - stub) / 2
            mid(e.start ?: "종일", sc, tt + th * (if (short) 0.4f else 0.34f), p(th * 0.22f, SERIF, INK, Paint.Align.CENTER))
            chip(rel(e), sc, tt + th * (if (short) 0.7f else 0.58f), small(th * 0.085f), e.color, true)
            if (!short) barcode(stub + (tl + tw - stub) * 0.18f, tt + th * 0.74f, (tl + tw - stub) * 0.64f, th * 0.13f)
            stamp(stub - th * 0.23f, tt + th * 0.17f, th * 0.42f)
            if (short) return
            // 아래: 다음 일정들 두 칸 × 두 줄
            val top0 = tt + th + H * 0.07f; val uu = H * 0.072f; val cw = tw / 2
            events.drop(1).take(4).forEachIndexed { i, ev ->
                val x = tl + (i % 2) * cw + (if (i % 2 == 1) uu * 0.6f else 0f)
                val y = top0 + (i / 2) * uu * 1.85f + uu * 0.6f
                rr(x, y - uu * 0.6f, uu * 0.22f, uu * 1.2f, uu * 0.11f, ev.color or 0xFF000000.toInt())
                val m = if (ev.day == LocalDate.now()) (ev.start ?: "종일") else dayName(ev.day) + (ev.start?.let { " $it" } ?: "")
                val mp = p(small(uu * 0.82f), MED, sub, Paint.Align.RIGHT)
                val right = x + cw - uu * (if (i % 2 == 1) 0.6f else 1.2f)
                mid(m, right, y, mp)
                val np = p(small(uu), BOLD, fg)
                mid(ell(ev.title, np, right - mp.measureText(m) - uu * 0.6f - (x + uu * 0.6f)), x + uu * 0.6f, y, np)
            }
            return
        }
        // ---- 세로(2×4·2×2): 위 = 본권, 아래 = 스텁(가로 절취선), 그 아래 목록 ----
        val pad = W * 0.08f
        val tl = pad; val tt = pad; val tw = W - pad * 2; val th = min(H * 0.46f, W * 1.05f)
        val stub = tt + th * 0.7f
        val path = ticketPath(tl, tt, tw, th, stub, true)
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paper; setShadowLayer(tw * 0.05f, 0f, tw * 0.02f, 0x33000000) })
        c.save(); c.clipPath(path); c.drawRect(tl, tt, tl + tw, tt + th * 0.045f, Paint().apply { color = e.color or 0xFF000000.toInt() }); c.restore()
        c.drawLine(tl + tw * 0.1f, stub, tl + tw * 0.9f, stub, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = soft; strokeWidth = max(1f, k * 1.2f); pathEffect = android.graphics.DashPathEffect(floatArrayOf(tw * 0.035f, tw * 0.04f), 0f) })
        val l = tl + tw * 0.1f
        mid("ADMIT ONE · 다음 일정", l, tt + th * 0.16f, p(small(tw * 0.06f), BOLD, soft))
        val tp = p(tw * 0.13f, BLACK, INK)
        val lines = TextWrap.wrap(e.title, tp, tw * 0.8f - (if (quirky) tw * 0.15f else 0f), 2)
        val ty0 = tt + th * (if (lines.size > 1) 0.3f else 0.34f)
        lines.forEachIndexed { i, s -> mid(s, l, ty0 + i * tw * 0.16f, tp) }
        mid(span(e), l, ty0 + lines.size * tw * 0.16f, p(small(tw * 0.065f), MED, soft))
        val sy = stub + (tt + th - stub) / 2
        mid(e.start ?: "종일", l, sy, p(tw * 0.14f, SERIF, INK))
        chip(rel(e), tl + tw * 0.9f, sy, small(tw * 0.06f), e.color, false)
        stamp(tl + tw - tw * 0.14f, tt + tw * 0.15f, tw * 0.3f)
        var y = tt + th + W * 0.1f
        val uu = min(W * 0.075f, H * 0.06f)
        for (ev in events.drop(1)) {
            if (y + uu * 2.2f > H - pad * 0.5f) break
            rr(tl, y, uu * 0.22f, uu * 2.2f, uu * 0.11f, ev.color or 0xFF000000.toInt())
            val np = p(small(uu), BOLD, fg)
            top(ell(ev.title, np, tw - uu), tl + uu * 0.6f, y - uu * 0.05f, np)
            top(whenS(ev), tl + uu * 0.6f, y + uu * 1.25f, p(small(uu * 0.8f), MED, sub))
            y += uu * 3f
        }
    }
}

/** 일정 위젯 (꾸미기는 다른 위젯과 같은 ConfigActivity) */
object MemoWidget {
    /** 미리보기용 예시 (실제 일정이 없거나 권한이 없을 때) */
    val SAMPLE: List<CalEvent> get() {
        val now = System.currentTimeMillis(); val t = LocalDate.now()
        return listOf(
            CalEvent("팀 주간 회의", t, "14:00", false, 0xFF4F7DF3.toInt(), now + 25 * 60_000L, now + 85 * 60_000L, "15:00"),
            CalEvent("헬스장 PT", t, "19:30", false, 0xFF35B37E.toInt(), now + 6 * 3_600_000L, now + 7 * 3_600_000L, "20:30"),
            CalEvent("엄마 생신", t.plusDays(1), null, true, 0xFFE35D6A.toInt()),
            CalEvent("치과 예약", t.plusDays(1), "10:00", false, 0xFFF2A33A.toInt(), now + 20 * 3_600_000L, now + 21 * 3_600_000L, "11:00"),
            CalEvent("가평 캠핑", t.plusDays(2), null, true, 0xFF35B37E.toInt())
        )
    }

    fun build(ctx: Context, style: WidgetStyle, id: Int?, wDp: Float, hDp: Float, events: List<CalEvent>, permission: Boolean): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_memo)
        Palette.applyBackground(rv, style)
        val scene = MemoScene(ctx, wDp, hDp, Palette.text(ctx, style), Palette.sub(ctx, style), Palette.accent(ctx, style),
            style.quirky, style.design.coerceIn(1, MemoScene.DESIGN_NAMES.size), MusicWidget.discColor(style), Palette.label(ctx))
        scene.draw(events, permission)
        rv.setImageViewBitmap(R.id.memo_canvas, scene.bitmap)
        // 누르면 캘린더 앱. 권한이 없으면 꾸미기 화면(권한 버튼이 있음)
        val intent = if (permission) Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                     else Intent(ctx, ConfigActivity::class.java).also { i ->
                         if (id != null) i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) else i.putExtra(ConfigActivity.EXTRA_KIND, Kind.MEMO.name)
                     }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(R.id.memo_canvas, PendingIntent.getActivity(ctx, 2000 + (id ?: 0), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return rv
    }

    fun renderAll(ctx: Context) {
        try {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, MemoWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val perm = CalendarReader.hasPermission(ctx)
            val events = CalendarReader.upcoming(ctx)
            for (id in ids) {
                val (w, h) = WidgetGeom.sizeDp(mgr, id)
                mgr.updateAppWidget(id, build(ctx, WidgetPrefs.style(ctx, Kind.MEMO, id), id, w, h, events, perm))
            }
            MemoJob.schedule(ctx)
        } catch (t: Throwable) {
            CrashLog.record(ctx, "MemoWidget.render", t)
        }
    }
}

open class MemoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = renderAsync(context)
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) = renderAsync(context)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) = WidgetPrefs.delete(context, appWidgetIds)
    override fun onDisabled(context: Context) = MemoJob.cancel(context)

    private fun renderAsync(context: Context) {
        val pending = goAsync()
        Thread { try { MemoWidget.renderAll(context.applicationContext) } finally { pending.finish() } }.start()
    }
}

/** 캘린더가 바뀌면(일정 추가·수정) 메모지를 다시 그림. 한 번 실행되면 다시 예약 */
class MemoJob : JobService() {
    companion object {
        private const val ID = 4201
        fun schedule(ctx: Context) {
            if (!CalendarReader.hasPermission(ctx)) return
            val js = ctx.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(ID) != null) return
            val job = JobInfo.Builder(ID, ComponentName(ctx, MemoJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(2_000).setTriggerContentMaxDelay(30_000)
                .build()
            js.schedule(job)
        }
        fun cancel(ctx: Context) = ctx.getSystemService(JobScheduler::class.java).cancel(ID)
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            try { MemoWidget.renderAll(applicationContext) } finally { jobFinished(params, false); schedule(applicationContext) }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?) = false
}
