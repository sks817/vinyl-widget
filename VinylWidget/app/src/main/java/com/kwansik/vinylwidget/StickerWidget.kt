package com.kwansik.vinylwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.widget.RemoteViews
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 병맛 스티커 목록 (art/stickers.js로 그린 오리지널 그림) */
object Stickers {
    val RES = intArrayOf(
        R.drawable.st_leave, R.drawable.st_nowork, R.drawable.st_godlife, R.drawable.st_angry, R.drawable.st_letsgo,
        R.drawable.st_lol, R.drawable.st_spirit, R.drawable.st_zone, R.drawable.st_hungry, R.drawable.st_coffee,
        R.drawable.st_music, R.drawable.st_photo, R.drawable.st_map, R.drawable.st_money, R.drawable.st_gym,
        R.drawable.st_sleep, R.drawable.st_call, R.drawable.st_ok,
        R.drawable.st_lucky, R.drawable.st_love, R.drawable.st_thanks, R.drawable.st_fighting, R.drawable.st_hot,
        R.drawable.st_cold, R.drawable.st_party, R.drawable.st_bday, R.drawable.st_study, R.drawable.st_game,
        // 캠핑 장비
        R.drawable.st_tent, R.drawable.st_lantern, R.drawable.st_chair, R.drawable.st_fire, R.drawable.st_marsh,
        R.drawable.st_cooler, R.drawable.st_kettle, R.drawable.st_mug, R.drawable.st_bbq, R.drawable.st_sleepbag,
        R.drawable.st_backpack, R.drawable.st_camper
    )
    val NAMES = arrayOf(
        "퇴근각!", "출근 싫어", "갓생 ON", "킹받네", "가보자고", "ㅋㅋㅋㅋ", "중꺾마", "멍...", "배고파", "커피 수혈",
        "음악 ON", "찰칵!", "어디가?", "텅장", "운동 가자", "잠 와..", "연락해", "오히려 좋아",
        "럭키비키", "사랑해", "감사합니다", "화이팅", "더워", "추워", "파티각", "생축", "공부 중", "한 판만",
        "텐트", "랜턴", "캠핑 의자", "불멍", "마시멜로", "아이스박스", "버너·코펠", "캠핑 머그", "캠핑 고기", "침낭", "배낭", "캠핑카"
    )
}

/** 스티커 위젯: 위젯마다 스티커 번호와 열 앱(패키지), 기울기·테두리를 저장 */
object StickerWidget {
    /** 기울기(−15~15) · 테두리 스타일 · 두께(dp) · 색 */
    class Look(var tilt: Int = 0, var border: Int = BorderFx.NONE, var borderDp: Int = 6, var borderColor: Int = 0)

    fun look(ctx: Context, id: Int) = sp(ctx).let {
        Look(it.getInt("t_$id", 0), it.getInt("bs_$id", BorderFx.NONE), it.getInt("bt_$id", 6), it.getInt("bc_$id", 0))
    }

    fun saveLook(ctx: Context, id: Int, l: Look) {
        sp(ctx).edit().putInt("t_$id", l.tilt).putInt("bs_$id", l.border).putInt("bt_$id", l.borderDp).putInt("bc_$id", l.borderColor).apply()
    }

    /** 기울이거나 테두리를 붙인 스티커 그림 (wPx×hPx, 가운데 정사각 칸) */
    fun image(ctx: Context, sticker: Int, l: Look, wPx: Int, hPx: Int, dens: Float): Bitmap {
        val out = Bitmap.createBitmap(wPx.coerceAtLeast(1), hPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val s = min(out.width, out.height)
        val style = if (l.borderDp <= 0) BorderFx.NONE else l.border
        val tPx = l.borderDp * dens
        val pad = BorderFx.pad(style, tPx, dens).coerceAtMost(s * 0.25f)
        val art = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        BitmapFactory.decodeResource(ctx.resources, Stickers.RES[sticker.coerceIn(0, Stickers.RES.size - 1)])?.let {
            Canvas(art).drawBitmap(it, null, RectF(pad, pad, s - pad, s - pad), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        }
        val framed = BorderFx.apply(art, style, tPx, l.borderColor, dens)
        val c = Canvas(out)
        val t = Math.toRadians(l.tilt.toDouble())
        val k = if (l.tilt == 0) 1f else (1.0 / (abs(cos(t)) + abs(sin(t)))).toFloat() * 0.97f
        c.translate(out.width / 2f, out.height / 2f); c.rotate(l.tilt.toFloat()); c.scale(k, k); c.translate(-s / 2f, -s / 2f)
        c.drawBitmap(framed, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("sticker_prefs", Context.MODE_PRIVATE)

    fun sticker(ctx: Context, id: Int) = sp(ctx).getInt("s_$id", (id and 0x7FFFFFFF) % Stickers.RES.size)
    fun app(ctx: Context, id: Int): String? = sp(ctx).getString("p_$id", null)
    fun save(ctx: Context, id: Int, sticker: Int, pkg: String?) {
        sp(ctx).edit().putInt("s_$id", sticker).putString("p_$id", pkg).apply()
    }
    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit(); ids.forEach { e.remove("s_$it").remove("p_$it").remove("t_$it").remove("bs_$it").remove("bt_$it").remove("bc_$it") }; e.apply()
    }

    /** 누르면 고른 앱. 앱이 없으면 스티커 고르기 화면 */
    private fun click(ctx: Context, id: Int): PendingIntent {
        val launch = app(ctx, id)?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
        val intent = launch ?: Intent(ctx, StickerConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, 1000 + id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun build(ctx: Context, id: Int, wDp: Float = 0f, hDp: Float = 0f): RemoteViews {
        val i = sticker(ctx, id).coerceIn(0, Stickers.RES.size - 1)
        val name = StickerAnim.nameOf(ctx, i)
        // 움직이는 스티커 (기울기·테두리는 움직임을 끈 때만)
        if (StickerAnim.enabled(ctx, id)) {
            val lid = StickerAnim.layoutId(ctx, StickerAnim.key(ctx, id, name))
            if (lid != 0) {
                val rv = RemoteViews(ctx.packageName, lid)
                val (w, h) = if (wDp > 0f) wDp to hDp else WidgetGeom.sizeDp(AppWidgetManager.getInstance(ctx), id)
                val s = (min(w, h) - 4f).coerceAtLeast(30f)
                rv.setViewLayoutWidth(R.id.st_anim, s, android.util.TypedValue.COMPLEX_UNIT_DIP)
                rv.setViewLayoutHeight(R.id.st_anim, s, android.util.TypedValue.COMPLEX_UNIT_DIP)
                rv.setContentDescription(R.id.st_anim, Stickers.NAMES[i])
                rv.setOnClickPendingIntent(R.id.st_root, click(ctx, id))
                return rv
            }
        }
        val rv = RemoteViews(ctx.packageName, R.layout.widget_sticker)
        val l = look(ctx, id)
        if (l.tilt == 0 && (l.border == BorderFx.NONE || l.borderDp <= 0)) {
            rv.setImageViewResource(R.id.st_img, Stickers.RES[i])
        } else {
            val (w, h) = if (wDp > 0f) wDp to hDp else WidgetGeom.sizeDp(AppWidgetManager.getInstance(ctx), id)
            val dens = WidgetGeom.density(ctx)
            val k = dens * min(1f, 512f / (maxOf(w, h) * dens))
            rv.setImageViewBitmap(R.id.st_img, image(ctx, i, l, (w * k).toInt(), (h * k).toInt(), k))
        }
        rv.setContentDescription(R.id.st_img, Stickers.NAMES[i])
        rv.setOnClickPendingIntent(R.id.st_img, click(ctx, id))
        return rv
    }

    fun renderAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in mgr.getAppWidgetIds(ComponentName(ctx, StickerWidgetProvider::class.java))) mgr.updateAppWidget(id, build(ctx, id))
    }
}

class StickerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 모닥불 스티커가 생기면 다른 마시멜로도 빨리 타도록 전부 다시 그림
        StickerWidget.renderAll(context)
    }

    /** 크기가 바뀌면 기울인·테두리 그림을 새 크기로 */
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        appWidgetManager.updateAppWidget(appWidgetId, StickerWidget.build(context, appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        StickerWidget.delete(context, appWidgetIds)
        StickerAnim.delete(context, appWidgetIds)
        StickerWidget.renderAll(context)
    }
}
