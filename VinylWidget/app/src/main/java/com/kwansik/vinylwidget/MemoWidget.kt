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
data class CalEvent(val title: String, val day: LocalDate, val start: String?, val allDay: Boolean, val color: Int)

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
                    if (day.isBefore(today)) { if (!allDay) out += CalEvent(title, today, "진행 중", false, c.getInt(4)); continue }
                    val t = Instant.ofEpochMilli(b).atZone(zone).toLocalTime()
                    out += CalEvent(title, day, if (allDay) null else "%d:%02d".format(t.hour, t.minute), allDay, c.getInt(4))
                }
            }
        } catch (e: Exception) {
            Log.e("VinylWidget", "calendar", e)
        }
        return out.sortedWith(compareBy({ it.day }, { !it.allDay }, { it.start ?: "" }))
    }
}

/**
 * 일정 메모 그림. 다른 위젯과 같은 문법: 바탕은 위젯 공통 배경(없음·유리·검정·흰색·컬러, bg_image),
 * 날짜는 날씨 위젯처럼 세리프 굵은 숫자, 글자색은 공통 자동 색(fg/sub).
 * '붙인 메모' 느낌은 왼쪽 위 모서리의 마스킹테이프 한 조각으로만. 병맛이면 오른쪽 위에 스티커 캐릭터
 * 크기에 따라: 4×1 = [날짜 | 다음 일정 두 개], 4×2 = 날짜 + [오늘 | 다가오는 일정], 2×4·2×2 = 날짜 + 날짜별 목록
 */
class MemoScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int, private val tape: Int, private val quirky: Boolean
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

    private val SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val MED = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private fun p(size: Float, tf: Typeface, col: Int, a: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = a }
    private fun small(v: Float) = max(v, 11f * k)
    private fun mid(s: String, x: Float, y: Float, pt: Paint) { val fm = pt.fontMetrics; c.drawText(s, x, y - (fm.ascent + fm.descent) / 2f, pt) }
    private fun ell(s: String, pt: Paint, maxW: Float): String {
        if (maxW <= 0f) return ""
        if (pt.measureText(s) <= maxW) return s
        var t = s; while (t.isNotEmpty() && pt.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
    private fun dayLabel(d: LocalDate): String {
        val today = LocalDate.now()
        return when (d) {
            today -> "오늘"; today.plusDays(1) -> "내일"
            else -> "${d.monthValue}.${d.dayOfMonth} ${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)}"
        }
    }

    /** 왼쪽 위 모서리에 비스듬히 붙인 마스킹테이프 (반투명, 옅은 사선 무늬) */
    private fun tapeCorner() {
        val tw = max(min(W, H) * 0.5f, 44f * k); val th = tw * 0.32f
        c.save(); c.translate(tw * 0.3f, th * 0.75f); c.rotate(-35f)
        val r = RectF(-tw / 2, -th / 2, tw / 2, th / 2)
        c.drawRect(r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (tape and 0x00FFFFFF) or (0xC8 shl 24) })
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF; strokeWidth = th * 0.16f }
        var x = r.left; while (x < r.right) { c.drawLine(x, r.top, x + th * 0.5f, r.bottom, sp); x += th * 0.45f }
        // 찢은 듯한 양 끝 (지그재그)
        val cut = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0; xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) }
        for (ex in floatArrayOf(r.left, r.right)) {
            val z = Path(); val n = 5; val d = th * 0.12f * (if (ex == r.left) 1 else -1)
            z.moveTo(ex, r.top)
            for (i in 0..n) z.lineTo(ex + if (i % 2 == 0) 0f else d, r.top + th * i / n)
            z.lineTo(ex - d * 3, r.bottom); z.lineTo(ex - d * 3, r.top); z.close()
            c.drawPath(z, cut)
        }
        c.restore()
    }

    /** 병맛: 오른쪽 위에 스티커 캐릭터 (일정이 있으면 갓생, 없으면 오히려 좋아) */
    private fun sticker(size: Float, busy: Boolean) {
        if (!quirky) return
        Assets.get(ctx, if (busy) R.drawable.st_godlife else R.drawable.st_ok)?.let {
            c.save(); c.rotate(8f, W - size * 0.55f, size * 0.5f)
            c.drawBitmap(it, null, RectF(W - size * 1.02f, -size * 0.02f, W - size * 0.02f, size * 0.98f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            c.restore()
        }
    }

    /** 다가오는 날짜를 짧게: 내일 / 10.4(토) */
    private fun shortDay(d: LocalDate): String =
        if (d == LocalDate.now().plusDays(1)) "내일"
        else "${d.monthValue}.${d.dayOfMonth}(${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"

    /**
     * 일정 한 줄. 오늘 일정 = [점] 14:00 제목,  다가오는 일정(showDay) = [점] 제목 …… 내일 10:00 (오른쪽 정렬, 흐리게).
     * 제목이 늘 먼저 보이게: 자리가 모자라면 시각을 빼고 요일만 남김
     */
    private fun eventLine(e: CalEvent, x: Float, y: Float, maxW: Float, size: Float, showDay: Boolean = false) {
        val dot = size * 0.26f
        c.drawCircle(x + dot, y, dot, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = e.color or 0xFF000000.toInt() })
        val tx = x + dot * 2 + size * 0.4f
        val np = p(size, BOLD, fg)
        val tp = p(size * 0.82f, MED, sub)
        if (showDay) {
            var meta = shortDay(e.day) + (e.start?.let { " $it" } ?: "")
            val titleW = np.measureText(e.title)
            if (tx + titleW + size * 0.5f + tp.measureText(meta) > x + maxW) meta = shortDay(e.day)
            val metaW = tp.measureText(meta)
            mid(ell(e.title, np, x + maxW - metaW - size * 0.5f - tx), tx, y, np)
            mid(meta, x + maxW, y, Paint(tp).apply { textAlign = Paint.Align.RIGHT })
            return
        }
        val whenS = e.start ?: "종일"
        mid(whenS, tx, y, tp)
        val nx = tx + tp.measureText(whenS) + size * 0.45f
        mid(ell(e.title, np, x + maxW - nx), nx, y, np)
    }

    fun draw(events: List<CalEvent>, permission: Boolean) {
        val today = LocalDate.now()
        val md = "${today.monthValue}.${today.dayOfMonth}"
        val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val wide = W >= H * 1.55f
        val empty = if (!permission) "눌러서 캘린더 권한 허용" else "다가오는 일정이 없어요"
        tapeCorner()

        if (wide && hDp < 110f) {                 // ---- 4×1: 날씨 1×4(텐트+날짜)와 같은 날짜 크기·줄 높이 ----
            val h = H
            val x0 = h * 0.42f
            val dp_ = p(h * 0.38f, SERIF, fg)
            mid(md, x0, h * 0.41f, dp_)
            mid(wk, x0 + h * 0.01f, h * 0.705f, p(small(h * 0.15f), MED, sub))
            val lx = x0 + max(dp_.measureText(md), h * 0.9f) + h * 0.32f
            c.drawRect(lx - h * 0.17f, h * 0.24f, lx - h * 0.17f + max(1f, k), h * 0.76f, Paint().apply { color = (sub and 0x00FFFFFF) or (0x40 shl 24) })
            val right = W - h * (if (quirky) 0.62f else 0.28f)
            val size = small(h * 0.17f)
            if (events.isEmpty()) mid(empty, lx, h / 2, p(size, BOLD, sub))
            else events.take(2).forEachIndexed { i, e -> eventLine(e, lx, h * (if (events.size == 1) 0.5f else 0.35f + i * 0.31f), right - lx, size, e.day != today) }
            sticker(h * 0.56f, events.isNotEmpty())
            return
        }

        val unit: Float; val m: Float
        if (wide) { unit = small(H * 0.085f); m = H * 0.1f } else { unit = small(min(W * 0.075f, H * 0.075f)); m = min(W, H) * 0.1f }
        // 머리글: 큰 세리프 날짜 + 요일 (날씨 위젯과 같은 글꼴)
        val dSize = if (wide) H * 0.2f else min(W * 0.2f, H * 0.12f)
        val dp_ = p(dSize, SERIF, fg)
        val baseY = m * 1.1f - dp_.fontMetrics.ascent * 0.92f
        c.drawText(md, m * 1.2f, baseY, dp_)
        c.drawText(wk, m * 1.2f + dp_.measureText(md) + unit * 0.5f, baseY, p(small(dSize * 0.36f), MED, sub))
        sticker(if (wide) H * 0.36f else min(W, H) * 0.3f, events.isNotEmpty())
        var top = baseY + unit * 1.6f

        if (wide) {                               // ---- 4×2: [오늘 | 다가오는 일정] ----
            val left = m * 1.2f; val gap = unit * 1.2f
            val colW = (W - left - m - gap) / 2
            if (events.isEmpty()) { mid(empty, left, top + unit, p(unit, BOLD, sub)); return }
            fun column(x: Float, title: String, list: List<CalEvent>, showDay: Boolean, none: String) {
                mid(title, x, top, p(small(unit * 0.8f), BOLD, sub))
                var y = top + unit * 1.55f
                if (list.isEmpty()) mid(none, x, y, p(small(unit * 0.86f), MED, sub))
                for (e in list) { if (y > H - m * 0.6f) break; eventLine(e, x, y, colW, unit, showDay); y += unit * 1.6f }
            }
            column(left, "오늘", events.filter { it.day == today }, false, "남은 일정 없음")
            c.drawRect(left + colW + gap / 2, top - unit * 0.5f, left + colW + gap / 2 + max(1f, k), H - m * 0.8f,
                Paint().apply { color = (sub and 0x00FFFFFF) or (0x33 shl 24) })
            column(left + colW + gap, "다가오는 일정", events.filter { it.day != today }, true, "이번 주는 여유")
            return
        }

        // ---- 2×4 / 2×2: 날짜별 목록 ----
        val left = m * 1.2f; val maxW = W - left - m
        if (events.isEmpty()) {
            val ep = p(unit, BOLD, sub)
            TextWrap.wrap(empty, ep, maxW, 3).forEachIndexed { i, l -> mid(l, left, top + unit + i * unit * 1.5f, ep) }
            return
        }
        var lastDay: LocalDate? = null
        for (e in events) {
            if (e.day != lastDay) {
                if (top > H - m - unit * 2f) break
                if (lastDay != null) top += unit * 0.35f
                mid(dayLabel(e.day), left, top, p(small(unit * 0.8f), BOLD, sub))
                top += unit * 1.5f; lastDay = e.day
            }
            if (top > H - m * 0.7f) break
            eventLine(e, left, top, maxW, unit)
            top += unit * 1.6f
        }
    }
}

/** 일정 메모 위젯 (꾸미기는 다른 위젯과 같은 ConfigActivity, 테이프 색은 style.point) */
object MemoWidget {
    /** 테이프 기본색: 크라프트지 베이지 */
    const val DEFAULT_TAPE = 0xFFE3C99A.toInt()
    val SAMPLE get() = listOf(
        CalEvent("팀 회의", LocalDate.now(), "14:00", false, 0xFF4F7DF3.toInt()),
        CalEvent("헬스장", LocalDate.now(), "19:30", false, 0xFF35B37E.toInt()),
        CalEvent("엄마 생신", LocalDate.now().plusDays(1), null, true, 0xFFE35D6A.toInt()),
        CalEvent("치과 예약", LocalDate.now().plusDays(2), "10:00", false, 0xFFF2A33A.toInt())
    )

    fun build(ctx: Context, style: WidgetStyle, id: Int?, wDp: Float, hDp: Float, events: List<CalEvent>, permission: Boolean): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_memo)
        Palette.applyBackground(rv, style)
        val scene = MemoScene(ctx, wDp, hDp, Palette.text(ctx, style), Palette.sub(ctx, style), style.point, style.quirky)
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
