package com.kwansik.vinylwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** 병맛 스티커 목록 (art/stickers.js로 그린 오리지널 그림) */
object Stickers {
    val RES = intArrayOf(
        R.drawable.st_leave, R.drawable.st_nowork, R.drawable.st_godlife, R.drawable.st_angry, R.drawable.st_letsgo,
        R.drawable.st_lol, R.drawable.st_spirit, R.drawable.st_zone, R.drawable.st_hungry, R.drawable.st_coffee,
        R.drawable.st_music, R.drawable.st_photo, R.drawable.st_map, R.drawable.st_money, R.drawable.st_gym,
        R.drawable.st_sleep, R.drawable.st_call, R.drawable.st_ok
    )
    val NAMES = arrayOf(
        "퇴근각!", "출근 싫어", "갓생 ON", "킹받네", "가보자고", "ㅋㅋㅋㅋ", "중꺾마", "멍...", "배고파", "커피 수혈",
        "음악 ON", "찰칵!", "어디가?", "텅장", "운동 가자", "잠 와..", "연락해", "오히려 좋아"
    )
}

/** 스티커 위젯: 위젯마다 스티커 번호와 열 앱(패키지)을 저장 */
object StickerWidget {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("sticker_prefs", Context.MODE_PRIVATE)

    fun sticker(ctx: Context, id: Int) = sp(ctx).getInt("s_$id", (id and 0x7FFFFFFF) % Stickers.RES.size)
    fun app(ctx: Context, id: Int): String? = sp(ctx).getString("p_$id", null)
    fun save(ctx: Context, id: Int, sticker: Int, pkg: String?) {
        sp(ctx).edit().putInt("s_$id", sticker).putString("p_$id", pkg).apply()
    }
    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit(); ids.forEach { e.remove("s_$it").remove("p_$it") }; e.apply()
    }

    fun build(ctx: Context, id: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_sticker)
        val i = sticker(ctx, id).coerceIn(0, Stickers.RES.size - 1)
        rv.setImageViewResource(R.id.st_img, Stickers.RES[i])
        rv.setContentDescription(R.id.st_img, Stickers.NAMES[i])
        // 누르면 고른 앱. 앱을 안 골랐거나 지워졌으면 스티커 고르기 화면
        val launch = app(ctx, id)?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        val intent = launch ?: Intent(ctx, StickerConfigActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(R.id.st_img, PendingIntent.getActivity(ctx, 1000 + id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        return rv
    }

    fun renderAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, StickerWidgetProvider::class.java))) mgr.updateAppWidget(id, build(ctx, id))
    }
}

class StickerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, StickerWidget.build(context, id))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        StickerWidget.delete(context, appWidgetIds)
    }
}
