package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

open class WeatherWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.kwansik.vinylwidget.WEATHER_REFRESH"
        const val ACTION_RENDER = "com.kwansik.vinylwidget.WEATHER_RENDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {   // 위젯을 누르면 즉시 갱신
            val pending = goAsync()
            WeatherFetcher.fetchAsync(context) { pending.finish() }
            return
        }
        if (intent.action == ACTION_RENDER) {   // 자정: 날짜만 다시 그림
            val pending = goAsync()
            Thread {
                try {
                    WeatherWidget.renderAll(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        super.onReceive(context, intent)
    }

    /** 위젯 크기를 바꾸거나 폴드를 접고 펼 때 → 새 크기에 맞춰 다시 그림 */
    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        val pending = goAsync()
        Thread {
            try {
                WeatherWidget.renderAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WeatherScheduler.schedule(context)
        val pending = goAsync()
        Thread {
            try {
                WeatherWidget.renderAll(context.applicationContext)
                if (WeatherStore.load(context).isStale()) WeatherFetcher.fetch(context.applicationContext)
            } catch (t: Throwable) {
                android.util.Log.e("VinylWidget", "weather onUpdate failed", t)
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.delete(context, appWidgetIds)
    }

    /** 날씨 위젯(2×2·1×4)이 하나도 안 남았을 때만 30분 갱신을 멈춤 */
    override fun onDisabled(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val left = mgr.getAppWidgetIds(android.content.ComponentName(context, WeatherWidgetProvider::class.java)).size +
            mgr.getAppWidgetIds(android.content.ComponentName(context, WeatherWideProvider::class.java)).size
        if (left == 0) WeatherScheduler.cancel(context)
    }
}
