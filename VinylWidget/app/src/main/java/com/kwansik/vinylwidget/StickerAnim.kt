package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * 움직이는 스티커. 그림은 art/sticker_anim.js가 만든 레이아웃(st_anim_<키>)의 ProgressBar가 프레임을 돌려 움직임
 * (레코드판 회전과 같은 원리라 홈 화면이 보일 때만 돌고 앱은 깨우지 않음).
 * 모든 움직임은 1분 안에 한 바퀴 돌고 반복: 마시멜로 굽기→탐(모닥불 스티커가 있으면 40초), 모닥불 활활→숯불, 커피·머그·주전자 김→식음.
 */
object StickerAnim {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("sticker_prefs", Context.MODE_PRIVATE)

    /** 스티커 번호 → 이름 (그림 파일 이름에서 st_ 뺀 것: marsh, fire …) */
    fun nameOf(ctx: Context, sticker: Int): String =
        ctx.resources.getResourceEntryName(Stickers.RES[sticker.coerceIn(0, Stickers.RES.size - 1)]).removePrefix("st_")

    fun enabled(ctx: Context, id: Int) = sp(ctx).getBoolean("an_$id", true)
    fun saveOptions(ctx: Context, id: Int, on: Boolean) = sp(ctx).edit().putBoolean("an_$id", on).apply()
    fun delete(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit(); ids.forEach { e.remove("an_$it") }; e.apply()
    }

    /** 홈 화면(어느 화면이든)에 움직이는 모닥불 스티커가 있는가 → 마시멜로가 더 빨리 탐 */
    private fun fireNearby(ctx: Context, exceptId: Int): Boolean {
        val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, StickerWidgetProvider::class.java))
        return ids.any { it != exceptId && enabled(ctx, it) && nameOf(ctx, StickerWidget.sticker(ctx, it)) == "fire" }
    }

    /** 보여줄 레이아웃 키 */
    fun key(ctx: Context, id: Int, name: String): String = when {
        name == "marsh" && fireNearby(ctx, id) -> "marsh_fast"
        layoutId(ctx, name) != 0 -> name
        else -> "w_$name"
    }

    fun layoutId(ctx: Context, key: String) = ctx.resources.getIdentifier("st_anim_$key", "layout", ctx.packageName)
}
