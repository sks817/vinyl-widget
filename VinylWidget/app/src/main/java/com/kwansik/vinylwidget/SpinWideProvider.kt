package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/** 1×4 레코드 위젯 */
class SpinWideProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        WidgetUpdater.refresh(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.delete(context, appWidgetIds)
    }
}
