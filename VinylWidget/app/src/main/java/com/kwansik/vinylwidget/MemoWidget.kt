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
 * 일정 위젯 그림 (Fantastical·iOS '다음 일정'·One UI 7 일정 위젯 문법):
 *  - '다음 일정' 하나를 크게(제목 + 남은 시간 칩 + 시각), 나머지는 [색 막대 | 제목 / 시각] 두 줄 목록
 *  - 바탕·글자색은 다른 위젯과 같은 공통 배경과 자동 색. 날짜는 넣지 않음(날씨·날짜 위젯과 같이 쓰는 걸 전제)
 *  - 크기별: 4×1 = [다음 일정 크게 | 그다음 2개],  4×2 = [다음 일정 | 목록],  2×4·2×2 = 다음 일정 위, 목록 아래
 *  - 병맛: 스티커 캐릭터 하나 (4×1은 자리가 없어 생략)
 */
class MemoScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int, private val quirky: Boolean
) {
    val bitmap: Bitmap
    private val c: Canvas
    private val W: Float
    private val H: Float
    private val k: Float
    private val hDp = hDp

    init {
        val dens = WidgetGeom.density(ctx)
        k = dens * min(1f, 1000f / (max(wDp, hDp) * dens))
        bitmap = Bitmap.createBitmap((wDp * k).toInt().coerceAtLeast(1), (hDp * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap); W = bitmap.width.toFloat(); H = bitmap.height.toFloat()
    }

    private val BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL)
    private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MED = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private fun p(size: Float, tf: Typeface, col: Int, a: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = a }
    private fun small(v: Float) = max(v, 11f * k)
    private fun top(s: String, x: Float, y: Float, pt: Paint) = c.drawText(s, x, y - pt.fontMetrics.ascent, pt)
    private fun mid(s: String, x: Float, y: Float, pt: Paint) { val fm = pt.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, pt) }
    private fun ell(s: String, pt: Paint, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (pt.measureText(s) <= maxW) return s
        var t = s; while (t.isNotEmpty() && pt.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
    private fun line(x: Float, y: Float, w: Float, h: Float) =
        c.drawRect(x, y, x + w, y + h, Paint().apply { color = (sub and 0x00FFFFFF) or (0x40 shl 24) })
    private fun bar(x: Float, y: Float, h: Float, w: Float, col: Int) =
        c.drawRoundRect(RectF(x, y, x + w, y + h), w / 2, w / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col or 0xFF000000.toInt() })

    // ---- 시각 글자 ----
    private fun dayName(d: LocalDate): String {
        val t = LocalDate.now()
        return when (d) { t -> "오늘"; t.plusDays(1) -> "내일"
            else -> "${d.monthValue}.${d.dayOfMonth}(${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})" }
    }
    /** 다음 일정 시각: 오늘이면 14:00 – 15:00, 다른 날이면 내일 10:00 / 10.4(토) · 종일 */
    private fun span(e: CalEvent): String = when {
        e.allDay -> "${dayName(e.day)} · 종일"
        e.day == LocalDate.now() -> "${e.start} – ${e.end ?: ""}".trimEnd(' ', '–')
        else -> "${dayName(e.day)} ${e.start}"
    }
    /** 목록 둘째 줄: 오늘 19:30 / 내일 · 종일 */
    private fun whenS(e: CalEvent) = if (e.allDay) "${dayName(e.day)} · 종일" else "${dayName(e.day)} ${e.start}"
    /** 오른쪽 짧은 표시(4×1): 오늘이면 시각, 다른 날이면 날짜 */
    private fun shortS(e: CalEvent) = if (e.day == LocalDate.now()) (e.start ?: "종일") else dayName(e.day)
    /** 남은 시간 칩: 진행 중 / 25분 후 / 3시간 후 / 내일 */
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

    /** 남은 시간 칩 (일정 색을 옅게 깐 알약 + 일정 색 글자). 칩 너비를 돌려줌 */
    private fun chip(s: String, x: Float, y: Float, size: Float, col: Int): Float {
        val pt = p(size, BOLD, col or 0xFF000000.toInt())
        val w = pt.measureText(s) + size * 1.1f; val h = size * 1.7f
        c.drawRoundRect(RectF(x, y - h / 2, x + w, y + h / 2), h / 2, h / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (col and 0x00FFFFFF) or (0x2E shl 24) })
        mid(s, x + size * 0.55f, y, pt)
        return w
    }

    /** 목록 한 줄: [색 막대] 제목 / 시각. 줄 높이를 돌려줌 */
    private fun row(e: CalEvent, x: Float, y: Float, w: Float, size: Float): Float {
        val bw = max(3f * k, size * 0.22f); val rh = size * 2.35f
        bar(x, y, rh, bw, e.color)
        val tx = x + bw + size * 0.6f
        val tp = p(size, BOLD, fg)
        top(ell(e.title, tp, w - (tx - x)), tx, y - size * 0.08f, tp)
        top(whenS(e), tx, y + size * 1.3f, p(size * 0.8f, MED, sub))
        return rh
    }

    /** 제목을 두 줄까지 (띄어쓰기에서 나눔) */
    private fun twoLines(s: String, pt: Paint, maxW: Float): List<String> = TextWrap.wrap(s, pt, maxW, 2)

    private fun sticker(x: Float, y: Float, size: Float) {
        if (!quirky) return
        Assets.get(ctx, R.drawable.st_godlife)?.let {
            c.drawBitmap(it, null, RectF(x, y, x + size, y + size), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    fun draw(events: List<CalEvent>, permission: Boolean) {
        val wide = W >= H * 1.55f
        if (events.isEmpty()) {                    // 일정이 없으면: 가운데에 한 줄 (+ 병맛 스티커)
            val msg = if (!permission) "눌러서 캘린더 권한 허용" else if (quirky) "일정 없음! 오늘은 자유다" else "다가오는 일정이 없어요"
            val sz = small(min(H * 0.16f, W * 0.07f))
            if (quirky && !(wide && hDp < 110f)) sticker(W / 2 - min(W, H) * 0.2f, H / 2 - min(W, H) * 0.45f, min(W, H) * 0.4f)
            val pt = p(sz, BOLD, sub, Paint.Align.CENTER)
            TextWrap.wrap(msg, pt, W * 0.85f, 2).forEachIndexed { i, l -> mid(l, W / 2, H / 2 + (if (quirky && !(wide && hDp < 110f)) min(W, H) * 0.12f else 0f) + i * sz * 1.4f, pt) }
            return
        }
        val next = events[0]; val rest = events.drop(1)

        if (wide && hDp < 110f) {                  // ---- 4×1 ----
            val pd = H * 0.2f; val bw = H * 0.06f
            bar(pd, pd, H - pd * 2, bw, next.color)
            val tx = pd + bw + H * 0.16f; val split = W * 0.56f
            val tp = p(H * 0.24f, BLACK, fg)
            top(ell(next.title, tp, split - tx - H * 0.2f), tx, pd - H * 0.02f, tp)
            val cs = small(H * 0.12f)
            val cw = chip(rel(next), tx, H - pd - H * 0.1f, cs, next.color)
            mid(span(next), tx + cw + H * 0.1f, H - pd - H * 0.1f, p(small(H * 0.13f), MED, sub))
            if (rest.isNotEmpty()) {
                line(split, pd, max(1f, k), H - pd * 2)
                val sz = small(H * 0.13f); val right = W - H * 0.28f
                rest.take(2).forEachIndexed { i, e ->
                    val y = pd + (H - pd * 2) * (if (rest.size == 1) 0.5f else 0.25f + i * 0.5f)
                    c.drawCircle(split + H * 0.25f, y, sz * 0.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = e.color or 0xFF000000.toInt() })
                    val mp = p(sz * 0.9f, MED, sub, Paint.Align.RIGHT)
                    val ms = shortS(e); mid(ms, right, y, mp)
                    val np = p(sz, BOLD, fg)
                    mid(ell(e.title, np, right - mp.measureText(ms) - H * 0.12f - (split + H * 0.42f)), split + H * 0.42f, y, np)
                }
            }
            return
        }

        val pad = min(W, H) * 0.09f
        if (wide) {                                // ---- 4×2 ----
            val lw = W * 0.47f; val bw = max(4f * k, H * 0.03f)
            bar(pad, pad, H - pad * 2, bw, next.color)
            val tx = pad + bw + H * 0.08f
            val tp = p(H * 0.13f, BLACK, fg)
            val lines = twoLines(next.title, tp, lw - tx)
            val lab = small(H * 0.07f); val cs = small(H * 0.068f)
            val blockH = H * 0.13f + lines.size * H * 0.165f + H * 0.14f
            val y0 = (H - blockH) / 2
            top("다음 일정", tx, y0, p(lab, BOLD, sub))
            lines.forEachIndexed { i, l -> top(l, tx, y0 + H * 0.13f + i * H * 0.165f, tp) }
            val ty = y0 + H * 0.13f + lines.size * H * 0.165f + H * 0.07f
            val cw = chip(rel(next), tx, ty, cs, next.color)
            mid(span(next), tx + cw + H * 0.04f, ty, p(cs, MED, sub))
            line(lw + H * 0.04f, pad, max(1f, k), H - pad * 2)
            var y = pad; val rx = lw + H * 0.12f; val sz = small(H * 0.075f)
            for (e in rest) { if (y + sz * 2.35f > H - pad + 2f) break; y += row(e, rx, y, W - pad - rx, sz) + sz * 0.75f }
            if (rest.isEmpty()) mid("이후 일정 없음", rx, H / 2, p(sz, MED, sub))
            sticker(W - H * 0.33f, H - H * 0.33f, H * 0.3f)
            return
        }

        // ---- 2×4 / 2×2 ----
        val u = min(W * 0.08f, H * 0.06f).coerceAtLeast(10f * k)
        var y = pad
        top("다음 일정", pad, y, p(small(u * 0.85f), BOLD, sub)); y += u * 1.6f
        val tp = p(u * 1.55f, BLACK, fg)
        val lines = twoLines(next.title, tp, W - pad * 2 - (if (quirky) W * 0.2f else 0f))
        lines.forEachIndexed { i, l -> top(l, pad, y + i * u * 1.95f, tp) }
        y += lines.size * u * 1.95f + u * 0.5f
        val cs = small(u * 0.85f)
        val cw = chip(rel(next), pad, y + u * 0.6f, cs, next.color)
        mid(span(next), pad + cw + u * 0.5f, y + u * 0.6f, p(cs, MED, sub))
        y += u * 2.2f
        sticker(W - W * 0.3f, pad * 0.3f, W * 0.27f)
        if (rest.isEmpty()) return
        line(pad, y, W - pad * 2, max(1f, k)); y += u * 1.1f
        for (e in rest) { if (y + u * 2.35f > H - pad + 2f) break; y += row(e, pad, y, W - pad * 2, u) + u * 0.8f }
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
        val scene = MemoScene(ctx, wDp, hDp, Palette.text(ctx, style), Palette.sub(ctx, style), style.quirky)
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
