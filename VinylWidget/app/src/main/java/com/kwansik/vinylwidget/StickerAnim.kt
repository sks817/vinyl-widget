package com.kwansik.vinylwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * 움직이는 스티커. 그림은 art/sticker_anim.js가 만든 레이아웃(st_anim_<키>)의 ProgressBar가 프레임을 돌려 움직임
 * (레코드판 회전과 같은 원리라 홈 화면이 보일 때만 돌고 앱은 깨우지 않음).
 * 시간이 흐르는 스티커: 마시멜로 5분에 다 탐(같은 화면에 모닥불 있으면 3분), 모닥불 5분에 꺼짐, 커피·머그·주전자 5분에 식음.
 * 누르면: 새 마시멜로 / 장작 넣기 / 다시 데우기
 */
object StickerAnim {
    const val ACTION_TICK = "com.kwansik.vinylwidget.ST_TICK"
    const val ACTION_TAP = "com.kwansik.vinylwidget.ST_TAP"
    private const val MIN = 60_000L
    private const val MARSH_MS = 5 * MIN
    private const val MARSH_NEAR_FIRE_MS = 3 * MIN
    private const val FIRE_MS = 5 * MIN
    private const val WARM_MS = 5 * MIN
    private val TIMED = setOf("marsh", "fire", "mug", "coffee", "kettle")

    private fun sp(ctx: Context) = ctx.getSharedPreferences("sticker_prefs", Context.MODE_PRIVATE)

    /** 스티커 번호 → 이름 (그림 파일 이름에서 st_ 뺀 것: marsh, fire …) */
    fun nameOf(ctx: Context, sticker: Int): String =
        ctx.resources.getResourceEntryName(Stickers.RES[sticker.coerceIn(0, Stickers.RES.size - 1)]).removePrefix("st_")

    fun interactive(name: String) = name in TIMED

    fun enabled(ctx: Context, id: Int) = sp(ctx).getBoolean("an_$id", true)
    fun tapReacts(ctx: Context, id: Int) = sp(ctx).getBoolean("tr_$id", true)
    fun saveOptions(ctx: Context, id: Int, on: Boolean, tapReact: Boolean) {
        sp(ctx).edit().putBoolean("an_$id", on).putBoolean("tr_$id", tapReact).apply()
    }

    /** 시작 시각 (처음 보면 지금부터) */
    private fun start(ctx: Context, id: Int): Long {
        val p = sp(ctx)
        val t = p.getLong("t0_$id", 0L)
        if (t > 0L) return t
        val now = System.currentTimeMillis(); p.edit().putLong("t0_$id", now).apply(); return now
    }

    fun reset(ctx: Context, id: Int) = sp(ctx).edit().putLong("t0_$id", System.currentTimeMillis()).apply()

    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        ids.forEach { e.remove("an_$it").remove("tr_$it").remove("t0_$it"); cancel(ctx, it) }
        e.apply()
    }

    /** 같은 홈 화면(어느 화면이든)에 움직이는 모닥불 스티커가 있는가 → 마시멜로가 더 빨리 탐 */
    private fun fireNearby(ctx: Context, exceptId: Int): Boolean {
        val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, StickerWidgetProvider::class.java))
        return ids.any { it != exceptId && enabled(ctx, it) && nameOf(ctx, StickerWidget.sticker(ctx, it)) == "fire" }
    }

    /** 지금 보여줄 레이아웃 키와, 다음에 모습이 바뀔 때까지 남은 시간(ms, 없으면 null) */
    fun state(ctx: Context, id: Int, name: String): Pair<String, Long?> {
        if (name !in TIMED) {
            return (if (layoutId(ctx, name) != 0) name else "w_$name") to null
        }
        val e = System.currentTimeMillis() - start(ctx, id)
        return when (name) {
            "marsh" -> {
                val step = (if (fireNearby(ctx, id)) MARSH_NEAR_FIRE_MS else MARSH_MS) / 4
                val st = (e / step).toInt().coerceIn(0, 4)
                "marsh_$st" to (if (st < 4) (st + 1) * step - e else null)
            }
            "fire" -> {
                val step = FIRE_MS / 3
                val st = (e / step).toInt().coerceIn(0, 3)
                "fire_$st" to (if (st < 3) (st + 1) * step - e else null)
            }
            else -> if (e < WARM_MS) "${name}_0" to (WARM_MS - e) else "${name}_1" to null
        }
    }

    fun layoutId(ctx: Context, key: String) = ctx.resources.getIdentifier("st_anim_$key", "layout", ctx.packageName)

    private fun tick(ctx: Context, id: Int) = PendingIntent.getBroadcast(ctx, 6000 + id,
        Intent(ctx, StickerWidgetProvider::class.java).setAction(ACTION_TICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun tapIntent(ctx: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(ctx, 7000 + id,
        Intent(ctx, StickerWidgetProvider::class.java).setAction(ACTION_TAP).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** 다음 모습으로 바뀔 때 다시 그리기 (정확한 알람 권한 없이, 15초 안팎 오차 허용) */
    fun schedule(ctx: Context, id: Int, delayMs: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setWindow(AlarmManager.RTC, System.currentTimeMillis() + delayMs + 500, 15_000L, tick(ctx, id))
    }

    fun cancel(ctx: Context, id: Int) = ctx.getSystemService(AlarmManager::class.java).cancel(tick(ctx, id))
}
