package com.kwansik.vinylwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.RemoteViews
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * 사진 스티커 위젯: 위젯마다 사진(네컷은 4장) + 프레임·문구·종이 색·다이컷 모양·기울기·모서리 R·테두리,
 * 사진마다 확대·위치
 */
object PhotoWidget {
    const val SLOTS = 4

    class Opts(
        var frame: Int, var tilt: Int, var paper: Int, var radius: Int, var shape: Int,
        /** -1 = 프레임 기본 테두리 */
        var border: Int, var borderDp: Int, var borderColor: Int,
        val texts: Array<MutableList<String>>, val crops: List<PhotoFrames.Crop>
    ) {
        fun borderStyle() = if (border < 0) PhotoFrames.defaultBorder(frame) else border
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("photo_prefs", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): Opts {
        val p = sp(ctx)
        val texts = Array(PhotoFrames.FIELDS.size) { f ->
            PhotoFrames.FIELDS[f].indices.map { i -> p.getString("x_${id}_${f}_$i", null) ?: PhotoFrames.FIELDS[f][i].def }.toMutableList()
        }
        val crops = List(SLOTS) { s -> PhotoFrames.Crop(p.getFloat("z${s}_$id", 1f), p.getFloat("ox${s}_$id", 0f), p.getFloat("oy${s}_$id", 0f)) }
        return Opts(
            p.getInt("f_$id", PhotoFrames.RECEIPT), p.getInt("t_$id", -3), p.getInt("c_$id", 0), p.getInt("r_$id", 6),
            p.getInt("sh_$id", 0), p.getInt("bs_$id", -1), p.getInt("bt_$id", 6), p.getInt("bc_$id", 0), texts, crops
        )
    }

    fun save(ctx: Context, id: Int, o: Opts) {
        val e = sp(ctx).edit().putInt("f_$id", o.frame).putInt("t_$id", o.tilt).putInt("c_$id", o.paper).putInt("r_$id", o.radius)
            .putInt("sh_$id", o.shape).putInt("bs_$id", o.border).putInt("bt_$id", o.borderDp).putInt("bc_$id", o.borderColor)
        o.texts.forEachIndexed { f, list -> list.forEachIndexed { i, s -> e.putString("x_${id}_${f}_$i", s) } }
        o.crops.forEachIndexed { s, cr -> e.putFloat("z${s}_$id", cr.zoom).putFloat("ox${s}_$id", cr.ox).putFloat("oy${s}_$id", cr.oy) }
        e.apply()
    }

    fun delete(ctx: Context, ids: IntArray) {
        val p = sp(ctx); val e = p.edit()
        for (id in ids) {
            p.all.keys.filter { it.split('_').getOrNull(1) == id.toString() }.forEach { e.remove(it) }
            for (s in 0 until SLOTS) photoFile(ctx, id, s).delete()
        }
        e.apply()
    }

    fun photoFile(ctx: Context, id: Int, slot: Int) = File(ctx.filesDir, "photo_${id}_$slot.jpg")

    fun loadPhotos(ctx: Context, id: Int): MutableList<Bitmap?> = MutableList(SLOTS) { s ->
        try { photoFile(ctx, id, s).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } } catch (t: Throwable) { null }
    }

    /** 위젯 크기(dp)대로 그린 그림. 처음엔 예시 문구, 사용자가 지운 칸은 비워 둠 */
    fun image(ctx: Context, o: Opts, photos: List<Bitmap?>, wDp: Float, hDp: Float, seed: Int): Bitmap {
        val dens = WidgetGeom.density(ctx)
        val k = dens * min(1f, 720f / (max(wDp, hDp) * dens))
        val f = o.frame.coerceIn(0, PhotoFrames.FIELDS.size - 1)
        val texts = PhotoFrames.FIELDS[f].mapIndexed { i, fd -> PhotoFrames.fill((o.texts[f].getOrNull(i) ?: fd.def).trim()) }
        val look = PhotoFrames.Look(f, texts, o.paper, o.shape, o.tilt, o.radius, o.borderStyle(), o.borderDp, o.borderColor)
        return PhotoFrames.render(photos, o.crops, look, (wDp * k).toInt(), (hDp * k).toInt(), k, seed)
    }

    fun build(ctx: Context, id: Int, wDp: Float, hDp: Float): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_photo)
        rv.setImageViewBitmap(R.id.ph_img, image(ctx, load(ctx, id), loadPhotos(ctx, id), wDp, hDp, id))
        // 누르면 사진·문구 바꾸기 화면
        val intent = Intent(ctx, PhotoConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(R.id.ph_img, PendingIntent.getActivity(ctx, 3000 + id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return rv
    }

    fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
        try {
            val (w, h) = WidgetGeom.sizeDp(mgr, id)
            mgr.updateAppWidget(id, build(ctx, id, w, h))
        } catch (t: Throwable) {
            CrashLog.record(ctx, "PhotoWidget.render", t)
        }
    }

    fun renderAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, PhotoWidgetProvider::class.java))) render(ctx, mgr, id)
    }
}

/** 기념일 D+·날짜 문구가 바뀌도록 한 시간마다(updatePeriodMillis) 다시 그림 */
class PhotoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) =
        async(context) { for (id in appWidgetIds) PhotoWidget.render(it, appWidgetManager, id) }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        async(context) { PhotoWidget.render(it, appWidgetManager, appWidgetId) }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = PhotoWidget.delete(context, appWidgetIds)

    /** 사진 읽기·그리기는 뒤에서 */
    private fun async(context: Context, work: (Context) -> Unit) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread { try { work(app) } finally { pending.finish() } }.start()
    }
}
