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

/** 사진 스티커 위젯: 위젯마다 사진 한 장 + 프레임·문구·종이 색·기울기·모서리 R */
object PhotoWidget {
    class Opts(var frame: Int, var tilt: Int, var paper: Int, var radius: Int, val texts: Array<MutableList<String>>)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("photo_prefs", Context.MODE_PRIVATE)

    fun load(ctx: Context, id: Int): Opts {
        val p = sp(ctx)
        val texts = Array(PhotoFrames.FIELDS.size) { f ->
            PhotoFrames.FIELDS[f].indices.map { i -> p.getString("x_${id}_${f}_$i", null) ?: PhotoFrames.FIELDS[f][i].def }.toMutableList()
        }
        return Opts(p.getInt("f_$id", PhotoFrames.RECEIPT), p.getInt("t_$id", -3), p.getInt("c_$id", 0), p.getInt("r_$id", 6), texts)
    }

    fun save(ctx: Context, id: Int, o: Opts) {
        val e = sp(ctx).edit().putInt("f_$id", o.frame).putInt("t_$id", o.tilt).putInt("c_$id", o.paper).putInt("r_$id", o.radius)
        o.texts.forEachIndexed { f, list -> list.forEachIndexed { i, s -> e.putString("x_${id}_${f}_$i", s) } }
        e.apply()
    }

    fun delete(ctx: Context, ids: IntArray) {
        val p = sp(ctx); val e = p.edit()
        for (id in ids) {
            p.all.keys.filter { it.split('_').getOrNull(1) == id.toString() }.forEach { e.remove(it) }
            photoFile(ctx, id).delete()
        }
        e.apply()
    }

    fun photoFile(ctx: Context, id: Int) = File(ctx.filesDir, "photo_$id.jpg")

    fun loadPhoto(ctx: Context, id: Int): Bitmap? = try {
        photoFile(ctx, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    } catch (t: Throwable) { null }

    /** 위젯 크기(dp)대로 그린 그림. 비워 둔 문구 칸은 기본 문구 */
    fun image(ctx: Context, o: Opts, photo: Bitmap?, wDp: Float, hDp: Float, seed: Int): Bitmap {
        val dens = WidgetGeom.density(ctx)
        val k = dens * min(1f, 720f / (max(wDp, hDp) * dens))
        val f = o.frame.coerceIn(0, PhotoFrames.FIELDS.size - 1)
        val texts = PhotoFrames.FIELDS[f].mapIndexed { i, fd -> o.texts[f].getOrNull(i)?.takeIf { it.isNotBlank() } ?: fd.def }
        return PhotoFrames.render(photo, f, texts, o.paper, o.tilt, o.radius, (wDp * k).toInt(), (hDp * k).toInt(), k, seed)
    }

    fun build(ctx: Context, id: Int, wDp: Float, hDp: Float): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_photo)
        rv.setImageViewBitmap(R.id.ph_img, image(ctx, load(ctx, id), loadPhoto(ctx, id), wDp, hDp, id))
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
