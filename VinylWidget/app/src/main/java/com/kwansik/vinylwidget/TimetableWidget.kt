package com.kwansik.vinylwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews

/** 타임테이블 위젯: 일정은 모든 위젯이 같이 쓰고, 모양(바탕·테두리·사람·배치)은 위젯마다 */
object TimetableWidget {
    const val ACTION_TICK = "com.kwansik.vinylwidget.TT_TICK"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("timetable_look", Context.MODE_PRIVATE)

    fun look(ctx: Context, id: Int) = sp(ctx).let {
        TtLook(it.getInt("bg_$id", TtLook.DARK), it.getInt("a_$id", 15), it.getInt("r_$id", 11),
            it.getInt("bs_$id", BorderFx.NONE), it.getInt("bt_$id", 4), it.getInt("bc_$id", 0),
            it.getInt("pp_$id", 0), it.getInt("ly_$id", 0))
    }

    fun saveLook(ctx: Context, id: Int, l: TtLook) {
        sp(ctx).edit().putInt("bg_$id", l.bg).putInt("a_$id", l.transparency).putInt("r_$id", l.radius)
            .putInt("bs_$id", l.border).putInt("bt_$id", l.borderDp).putInt("bc_$id", l.borderColor)
            .putInt("pp_$id", l.person).putInt("ly_$id", l.layout).apply()
    }

    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        for (id in ids) for (k in listOf("bg", "a", "r", "bs", "bt", "bc", "pp", "ly")) e.remove("${k}_$id")
        e.apply()
    }

    fun build(ctx: Context, id: Int, wDp: Float, hDp: Float, data: TtData = Timetable.load(ctx)): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_timetable)
        rv.setImageViewBitmap(R.id.tt_img, TimetableScene(ctx, wDp, hDp, look(ctx, id), data).bitmap)
        val intent = Intent(ctx, TimetableConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(R.id.tt_img, PendingIntent.getActivity(ctx, 4000 + id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return rv
    }

    fun render(ctx: Context, mgr: AppWidgetManager, id: Int, data: TtData = Timetable.load(ctx)) {
        try {
            val (w, h) = WidgetGeom.sizeDp(mgr, id)
            mgr.updateAppWidget(id, build(ctx, id, w, h, data))
        } catch (t: Throwable) {
            CrashLog.record(ctx, "Timetable.render", t)
        }
    }

    fun renderAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val data = Timetable.load(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, TimetableWidgetProvider::class.java))) render(ctx, mgr, id, data)
    }

    /** 지금 시각 바늘·지금 일정이 움직이도록 15분마다 다시 그림 (깨우지 않는 알람이라 배터리 부담 적음) */
    private fun tick(ctx: Context) = PendingIntent.getBroadcast(ctx, 4999,
        Intent(ctx, TimetableWidgetProvider::class.java).setAction(ACTION_TICK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES, tick(ctx))
    }

    fun cancel(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(tick(ctx))
}

class TimetableWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TimetableWidget.ACTION_TICK) { async(context) { TimetableWidget.renderAll(it) }; return }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        TimetableWidget.schedule(context)
        async(context) { c -> val data = Timetable.load(c); for (id in appWidgetIds) TimetableWidget.render(c, appWidgetManager, id, data) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        async(context) { TimetableWidget.render(it, appWidgetManager, appWidgetId) }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = TimetableWidget.delete(context, appWidgetIds)
    override fun onDisabled(context: Context) = TimetableWidget.cancel(context)

    private fun async(context: Context, work: (Context) -> Unit) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread { try { work(app) } finally { pending.finish() } }.start()
    }
}
