package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class SpinWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refresh(context)
    }

    /** 위젯 크기 변경·폴드 접고 펼 때 → 판·톤암 크기 다시 계산 */
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        WidgetUpdater.refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.delete(context, appWidgetIds)
    }
}
