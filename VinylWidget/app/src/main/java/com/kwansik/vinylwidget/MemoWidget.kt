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
                    // 끝난 일정: 오늘 것만 남김 (하루 막대에 흐리게), 지난날 것은 빼기
                    if (!allDay && e < now && Instant.ofEpochMilli(b).atZone(zone).toLocalDate() != today) continue
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
        return out.sortedWith(compareBy({ it.day }, { !it.allDay }, { it.beginMs }))
    }
}

/**
 * 일정 위젯 = '하루 타임라인'.
 *  - 오늘을 가로(2×4는 세로) 막대 하나로: 일정은 길이만큼의 색 캡슐(지난 일정은 흐리게), 지금 시각엔 바늘.
 *    바쁜 시간과 빈 시간이 한눈에 보임. 병맛이면 바늘 끝이 지금 날씨 캐릭터
 *  - 다음 일정은 크게(색 막대 + 남은 시간 + 제목 + 시각), 그 뒤 일정 한두 개와 내일 일정은 작게
 *  - 바탕·글자색은 다른 위젯과 같은 공통 배경과 자동 색. 날짜는 넣지 않음(날씨·날짜 위젯과 같이 쓰는 걸 전제)
 *  - 크기별: 4×1 / 4×2 / 2×4 / 2×2 (치수는 dp 기준, 시안 cal4와 같음)
 */
class MemoScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int, private val accent: Int, private val quirky: Boolean
) {
    val bitmap: Bitmap
    private val c: Canvas
    private val W: Float
    private val H: Float
    private val k: Float
    private val wd = wDp
    private val hd = hDp

    init {
        val dens = WidgetGeom.density(ctx)
        k = dens * min(1f, 1000f / (max(wDp, hDp) * dens))
        bitmap = Bitmap.createBitmap((wDp * k).toInt().coerceAtLeast(1), (hDp * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap); W = bitmap.width.toFloat(); H = bitmap.height.toFloat()
    }

    private fun tf(weight: Int) = Typeface.create(Typeface.SANS_SERIF, weight, false)
    /** 글자(크기 dp, 굵기 400~800) */
    private fun p(sizeDp: Float, weight: Int, col: Int, a: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizeDp * k; typeface = tf(weight); color = col; textAlign = a }
    private fun top(s: String, x: Float, y: Float, pt: Paint) = c.drawText(s, x, y - pt.fontMetrics.ascent, pt)
    private fun mid(s: String, x: Float, y: Float, pt: Paint) { val fm = pt.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, pt) }
    private fun ell(s: String, pt: Paint, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (pt.measureText(s) <= maxW) return s
        var t = s; while (t.isNotEmpty() && pt.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
    private fun a(col: Int, al: Int) = (col and 0x00FFFFFF) or (al shl 24)
    private fun rr(l: Float, t: Float, w: Float, h: Float, r: Float, col: Int) =
        c.drawRoundRect(RectF(l, t, l + w, t + h), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    private fun dot(x: Float, y: Float, r: Float, col: Int) = c.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
    private val track get() = a(fg, 0x17)
    private val line get() = a(fg, 0x1F)
    private fun solid(col: Int) = col or 0xFF000000.toInt()

    // ---- 시간 계산 ----
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()
    private val nowMs = System.currentTimeMillis()
    private fun hourOf(ms: Long): Float {
        val z = Instant.ofEpochMilli(ms).atZone(zone)
        if (z.toLocalDate().isBefore(today)) return 0f
        if (z.toLocalDate().isAfter(today)) return 24f
        return z.hour + z.minute / 60f
    }
    private val nowH = hourOf(nowMs)
    private fun done(e: CalEvent) = !e.allDay && e.endMs in 1..nowMs
    private fun rel(e: CalEvent): String {
        if (e.allDay) return if (e.day == today) "오늘 종일" else dayName(e.day)
        if (e.beginMs in 1..nowMs && nowMs < e.endMs) return "진행 중"
        val min = (e.beginMs - nowMs) / 60_000
        return when {
            e.day != today -> dayName(e.day)
            min < 1 -> "곧 시작"
            min < 60 -> "${min}분 후"
            else -> if (min % 60 >= 30) "${min / 60}시간 반 후" else "${min / 60}시간 후"
        }
    }
    private fun dayName(d: LocalDate) = when (d) {
        today -> "오늘"; today.plusDays(1) -> "내일"
        else -> "${d.monthValue}.${d.dayOfMonth}(${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
    }
    private fun span(e: CalEvent) = if (e.allDay) "종일" else "${e.start} – ${e.end ?: ""}".trimEnd(' ', '–')

    /** 막대가 보여줄 시간 범위: 기본 8~23시, 일정이 그 밖에 있으면 넓힘 (3시간 단위) */
    private fun range(timed: List<CalEvent>): Pair<Float, Float> {
        var h0 = 8f; var h1 = 23f
        for (e in timed) { h0 = min(h0, kotlin.math.floor(hourOf(e.beginMs) / 3f) * 3f); h1 = max(h1, kotlin.math.ceil(hourOf(e.endMs))) }
        return h0.coerceIn(0f, 21f) to h1.coerceIn(h0 + 3f, 24f)
    }

    /** 오늘 남은 여유 시간(지금~막대 끝에서 남은 일정 시간 뺌), 시간 단위 반올림 */
    private fun freeHours(timed: List<CalEvent>, h1: Float): Int {
        var busy = 0f
        for (e in timed) { val s = max(hourOf(e.beginMs), nowH); val t = min(hourOf(e.endMs), h1); if (t > s) busy += t - s }
        return kotlin.math.round(max(0f, h1 - nowH - busy)).toInt()
    }

    /** 가로 막대 (+ 시각 눈금). x(h) 함수를 돌려줌 */
    private fun hBar(l: Float, r: Float, y: Float, h: Float, timed: List<CalEvent>, h0: Float, h1: Float, ticks: Boolean): (Float) -> Float {
        val x = { hh: Float -> l + (r - l) * ((hh - h0) / (h1 - h0)).coerceIn(0f, 1f) }
        rr(l, y, r - l, h, h / 2, track)
        for (e in timed) {
            val x0 = x(hourOf(e.beginMs)); val x1 = max(x(hourOf(e.endMs)), x0 + h)
            rr(x0, y, x1 - x0, h, h / 2, if (done(e)) a(e.color, 0x59) else solid(e.color))
        }
        if (ticks) {
            var t = kotlin.math.ceil(h0 / 3f) * 3f; if (t <= h0) t += 3f
            while (t < h1) { mid(t.toInt().toString(), x(t), y + h + 10f * k, p(9f, 500, sub, Paint.Align.CENTER)); t += 3f }
        }
        return x
    }

    /** 지금 바늘. 병맛이면 바늘 끝이 지금 날씨 캐릭터 */
    private fun needle(x: Float, y0: Float, y1: Float, vertical: Boolean = false) {
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg }
        if (!vertical) c.drawRect(x - k, y0, x + k, y1, pt) else c.drawRect(y0, x - k, y1, x + k, pt)
        val fun_ = if (quirky) Assets.get(ctx, WeatherStore.load(ctx).iconFun()) else null
        if (fun_ != null) {
            val s = 16f * k
            val r = if (!vertical) RectF(x - s / 2, y0 - s + 3f * k, x + s / 2, y0 + 3f * k) else RectF(y0 - s + 2f * k, x - s / 2, y0 + 2f * k, x + s / 2)
            c.drawBitmap(fun_, null, r, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        } else if (!vertical) c.drawCircle(x, y0, 3.5f * k, pt) else c.drawCircle(y0, x, 3.5f * k, pt)
    }

    /** 다음 일정 블록: [색 막대] 남은 시간 / 제목(크게) / 시각. 블록 높이를 돌려줌 */
    private fun nextBlock(e: CalEvent, x: Float, y: Float, maxW: Float, title: Float, barH: Float) {
        rr(x, y, 4f * k, barH, 2f * k, solid(e.color))
        val lx = x + 14f * k
        top((if (e.day == today) "다음 · " else "") + rel(e), lx, y - k, p(11f * title / 22f + 0.5f, 700, solid(e.color)))
        val np = p(title, 800, fg)
        top(ell(e.title, np, maxW - 14f * k), lx, y + 15f * k * title / 22f, np)
        top(if (e.day == today) span(e) else (e.start ?: "종일"), lx, y + 45f * k * title / 22f, p(12f * title / 22f + 0.5f, 500, sub))
    }

    fun draw(events: List<CalEvent>, permission: Boolean) {
        val todays = events.filter { it.day == today }
        val timed = todays.filter { !it.allDay }
        val upcoming = events.filter { !done(it) && !(it.allDay && it.day == today) }   // 다음 일정 후보
        val next = upcoming.firstOrNull()
        val after = upcoming.drop(1)
        val tomorrow = events.filter { it.day == today.plusDays(1) }
        val (h0, h1) = range(timed)
        val emptyMsg = if (!permission) "눌러서 캘린더 권한 허용" else "오늘 일정 없음 · 여유로운 하루"
        val headRight = if (todays.isEmpty()) "일정 없음" else "일정 ${todays.size} · 여유 ${freeHours(timed, h1)}시간"
        val tomorrowS = if (tomorrow.isEmpty()) "내일 일정 없음" else "내일 · ${tomorrow[0].title}" + (if (tomorrow.size > 1) " 외 ${tomorrow.size - 1}" else "")
        val wide = wd >= hd * 1.55f

        if (wide && hd < 110f) {                              // ---- 4×1 ----
            val pd = 16f * k
            val ty = pd + 2f * k
            val x = hBar(pd, W - pd, ty, 10f * k, timed, h0, h1, false)
            needle(x(nowH), ty - 5f * k, ty + 15f * k)
            val y = H - pd - 12f * k
            if (next == null) { mid(emptyMsg, pd, y, p(14f, 700, sub)); return }
            rr(pd, y - 15f * k, 4f * k, 30f * k, 2f * k, solid(next.color))
            mid(rel(next), pd + 13f * k, y - 7f * k, p(10f, 700, solid(next.color)))
            val right0 = W - pd - 80f * k
            val tp = p(13f, 600, fg, Paint.Align.RIGHT)
            mid(next.start ?: "종일", right0, y, tp)
            mid(ell(next.title, p(17f, 800, fg), right0 - tp.measureText(next.start ?: "종일") - 12f * k - (pd + 13f * k)), pd + 13f * k, y + 8f * k, p(17f, 800, fg))
            c.drawRect(W - pd - 70f * k, y - 12f * k, W - pd - 69f * k, y + 12f * k, Paint().apply { color = line })
            val f = after.firstOrNull()
            if (f != null) {
                mid("이후 " + (if (f.day == today) (f.start ?: "종일") else dayName(f.day)), W - pd, y - 6f * k, p(10f, 500, sub, Paint.Align.RIGHT))
                mid(ell(f.title, p(11f, 700, fg), 62f * k), W - pd, y + 8f * k, p(11f, 700, fg, Paint.Align.RIGHT))
            } else mid("이후 없음", W - pd, y, p(10f, 500, sub, Paint.Align.RIGHT))
            return
        }

        if (wide) {                                           // ---- 4×2 ----
            val pd = 20f * k
            top("오늘", pd, pd - k, p(11f, 700, sub))
            mid(headRight, W - pd, pd + 6f * k, p(11f, 600, sub, Paint.Align.RIGHT))
            val ty = pd + 24f * k
            val x = hBar(pd, W - pd, ty, 16f * k, timed, h0, h1, true)
            needle(x(nowH), ty - 6f * k, ty + 22f * k)
            val y = ty + 52f * k
            if (next == null) { mid(emptyMsg, pd, y + 24f * k, p(16f, 800, sub)); mid(tomorrowS, pd, y + 50f * k, p(11f, 600, sub)); return }
            val rx = W - pd - 118f * k
            nextBlock(next, pd, y, rx - 14f * k - pd - 8f * k, 22f, 62f * k)
            c.drawRect(rx - 14f * k, y + 2f * k, rx - 13f * k, y + 60f * k, Paint().apply { color = line })
            val rows = ArrayList<Triple<String, String, Int>>()
            after.filter { it.day == today }.take(2).forEach { rows += Triple(it.title, it.start ?: "종일", it.color) }
            if (rows.size < 2 && tomorrow.isNotEmpty() && next.day == today)
                rows += Triple("내일 · " + tomorrow[0].title, if (tomorrow.size > 1) "외 ${tomorrow.size - 1}" else (tomorrow[0].start ?: "종일"), tomorrow[0].color)
            if (rows.isEmpty()) rows += Triple("이후 일정 없음", "", sub)
            var yy = y
            for ((t, m, col) in rows.take(2)) {
                dot(rx + 3f * k, yy + 9f * k, 3f * k, solid(col))
                val mp = p(11f, 500, sub, Paint.Align.RIGHT)
                mid(m, W - pd, yy + 9f * k, mp)
                top(ell(t, p(12f, 700, fg), W - pd - mp.measureText(m) - 6f * k - (rx + 12f * k)), rx + 12f * k, yy + k, p(12f, 700, fg))
                yy += 34f * k
            }
            return
        }

        if (hd >= wd * 1.5f) {                                // ---- 2×4: 세로 막대 ----
            val pd = 18f * k
            top("오늘", pd, pd - k, p(11f, 700, sub))
            mid(if (todays.isEmpty()) "일정 없음" else "일정 ${todays.size}", W - pd, pd + 6f * k, p(11f, 600, sub, Paint.Align.RIGHT))
            val t0 = pd + 30f * k; val b0 = H - pd - 34f * k; val lx = pd + 6f * k
            val yOf = { hh: Float -> t0 + (b0 - t0) * ((hh - h0) / (h1 - h0)).coerceIn(0f, 1f) }
            rr(lx, t0, 12f * k, b0 - t0, 6f * k, track)
            var t = kotlin.math.ceil(h0 / 3f) * 3f; if (t <= h0) t += 3f
            while (t < h1) { mid(t.toInt().toString(), lx - 6f * k, yOf(t), p(8f, 500, sub, Paint.Align.RIGHT)); t += 3f }
            val ny = yOf(nowH)
            var lastY = -999f
            for (e in timed) {
                val y0 = yOf(hourOf(e.beginMs)); val y1 = max(yOf(hourOf(e.endMs)), y0 + 12f * k)
                rr(lx, y0, 12f * k, y1 - y0, 6f * k, if (done(e)) a(e.color, 0x59) else solid(e.color))
                var ly = max(y0 + 2f * k, lastY + 34f * k)
                if (ly < ny + 6f * k && ly + 30f * k > ny - 6f * k) ly = if (hourOf(e.beginMs) < nowH) min(ly, ny - 34f * k) else ny + 8f * k
                if (ly + 28f * k > b0 + 10f * k) break
                val isN = e == next
                top(if (isN) "다음 · " + rel(e) else (e.start ?: ""), lx + 24f * k, ly, p(10f, if (isN) 700 else 500, if (isN) solid(e.color) else sub))
                val np = p(if (isN) 15f else 13f, if (isN) 800 else 700, if (done(e)) sub else fg)
                top(ell(e.title, np, W - lx - 24f * k - pd), lx + 24f * k, ly + 13f * k, np)
                lastY = ly
            }
            needle(ny, lx - 5f * k, lx + 17f * k, vertical = true)
            if (timed.isEmpty()) mid(emptyMsg, lx + 24f * k, (t0 + b0) / 2, p(12f, 700, sub))
            c.drawRect(pd, H - pd - 22f * k, W - pd, H - pd - 21f * k, Paint().apply { color = line })
            mid(ell(tomorrowS, p(11f, 600, sub), W - pd * 2), pd, H - pd - 8f * k, p(11f, 600, sub))
            return
        }

        // ---- 2×2 ----
        val pd = 16f * k
        top(if (todays.isEmpty()) "오늘 · 일정 없음" else "오늘 · 일정 ${todays.size}", pd, pd - 3f * k, p(10f, 700, sub))
        val ty = pd + 26f * k
        val x = hBar(pd, W - pd, ty, 12f * k, timed, h0, h1, false)
        needle(x(nowH), ty - 5f * k, ty + 17f * k)
        val y = ty + 30f * k
        if (next == null) { mid(emptyMsg, pd, y + 24f * k, p(13f, 800, sub)); return }
        nextBlock(next, pd, y, W - pd * 2, 18f, 56f * k)
        val f = after.firstOrNull()
        c.drawRect(pd, H - pd - 24f * k, W - pd, H - pd - 23f * k, Paint().apply { color = line })
        if (f != null) {
            dot(pd + 3f * k, H - pd - 9f * k, 3f * k, solid(f.color))
            val m = if (f.day == today) (f.start ?: "종일") else dayName(f.day)
            val mp = p(10f, 500, sub, Paint.Align.RIGHT)
            mid(m, W - pd, H - pd - 9f * k, mp)
            mid(ell(f.title, p(11f, 700, fg), W - pd - mp.measureText(m) - 6f * k - (pd + 12f * k)), pd + 12f * k, H - pd - 9f * k, p(11f, 700, fg))
        } else mid(tomorrowS, pd, H - pd - 9f * k, p(11f, 600, sub))
    }
}

/** 일정 위젯 (꾸미기는 다른 위젯과 같은 ConfigActivity) */
object MemoWidget {
    /** 미리보기용 예시 (실제 일정이 없거나 권한이 없을 때) */
    val SAMPLE: List<CalEvent> get() {
        // 오늘 12:00·14:00·19:30 + 내일 (지금 시각 기준으로 지난 것은 흐리게 보임)
        val zone = ZoneId.systemDefault(); val t = LocalDate.now()
        fun ev(title: String, h: Int, m: Int, mins: Int, col: Int, day: LocalDate = t): CalEvent {
            val b = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli(); val e = b + mins * 60_000L
            val te = java.time.Instant.ofEpochMilli(e).atZone(zone)
            return CalEvent(title, day, "%d:%02d".format(h, m), false, col, b, e, "%d:%02d".format(te.hour, te.minute))
        }
        return listOf(
            ev("점심 약속", 12, 0, 60, 0xFFF2A33A.toInt()), ev("팀 주간 회의", 14, 0, 60, 0xFF4F7DF3.toInt()),
            ev("헬스장 PT", 19, 30, 60, 0xFF35B37E.toInt()),
            CalEvent("엄마 생신", t.plusDays(1), null, true, 0xFFE35D6A.toInt()),
            ev("치과 예약", 10, 0, 60, 0xFFF2A33A.toInt(), t.plusDays(1))
        )
    }

    fun build(ctx: Context, style: WidgetStyle, id: Int?, wDp: Float, hDp: Float, events: List<CalEvent>, permission: Boolean): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_memo)
        Palette.applyBackground(rv, style)
        val scene = MemoScene(ctx, wDp, hDp, Palette.text(ctx, style), Palette.sub(ctx, style), Palette.accent(ctx, style), style.quirky)
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
