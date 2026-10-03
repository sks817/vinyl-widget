package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import kotlin.math.min

/**
 * 두 위젯이 '같은 뼈대'를 공유하도록 하는 공통 치수.
 *  - 바깥 여백 8dp, 아래 줄(재생 버튼 / 날씨 정보) 44dp (손가락으로 누르기 편한 높이)
 *  - 그 위 영역에 '데크'(정사각 D + 오른쪽 톤암 자리 0.18D)가 들어감
 *  → 레코드판 지름 D와 날씨 그림 크기 D가 항상 같아지고, 아래 줄 높이도 같아짐
 */
object WidgetGeom {
    const val PAD = 8f
    const val ROW = 44f
    const val ARM = 1.18f

    /** 런처가 알려주는 위젯 크기(dp). 모르면 170×170 */
    fun sizeDp(mgr: AppWidgetManager, id: Int): Pair<Float, Float> {
        val o = mgr.getAppWidgetOptions(id)
        @Suppress("DEPRECATION")
        val sizes: ArrayList<SizeF>? = o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        var w = sizes?.firstOrNull()?.width ?: o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        var h = sizes?.firstOrNull()?.height ?: o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()
        if (w <= 0f) w = 170f
        if (h <= 0f) h = 170f
        return w to h
    }

    /** 판(=날씨 그림) 한 변 D (dp) */
    fun discDp(wDp: Float, hDp: Float): Float {
        val areaW = wDp - PAD * 2
        val areaH = hDp - PAD * 2 - ROW
        return min(areaH, areaW / ARM).coerceAtLeast(40f)
    }

    fun density(ctx: Context) = ctx.resources.displayMetrics.density
}
