package com.kwansik.vinylwidget

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 오류 기록기: 앱이 죽거나(작동 중지) 위젯 처리 중 오류가 나면 내용을 파일에 남김.
 * 앱 첫 화면의 '오류 내용 복사' 버튼으로 복사해서 전달 → 정확한 원인 줄을 찾을 수 있음.
 */
object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "last_error.txt")

    fun record(ctx: Context, where: String, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val ver = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
            } catch (x: Exception) { "?" }
            val text = buildString {
                append("시각: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())).append('\n')
                append("위치: ").append(where).append('\n')
                append("앱 버전: ").append(ver).append(" / Android ").append(Build.VERSION.RELEASE)
                    .append(" (API ").append(Build.VERSION.SDK_INT).append(") / ").append(Build.MODEL).append('\n')
                append(sw.toString())
            }
            file(ctx).writeText(text.take(20_000))
        } catch (x: Throwable) {
            Log.e("VinylWidget", "crash log failed", x)
        }
        Log.e("VinylWidget", where, e)
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }
}

/** 앱 프로세스가 시작될 때 가장 먼저 실행: 처리 안 된 오류를 기록한 뒤 원래대로 종료 */
class App : Application() {
    @Volatile private var colors: String? = null

    override fun onCreate() {
        super.onCreate()
        colors = runCatching { Palette.signature(this) }.getOrNull()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            CrashLog.record(this, "작동 중지 (스레드: ${t.name})", e)
            previous?.uncaughtException(t, e)
        }
    }

    /**
     * 다크 모드를 켜고 끄거나 배경화면을 바꾸면(시스템 색이 바뀜) 위젯을 다시 그림.
     * 유리 배경·자동 버튼은 런처가 알아서 바꾸지만, 날씨 그림 속 글자는 앱이 다시 그려야 함
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val now = runCatching { Palette.signature(this) }.getOrNull() ?: return
        if (now == colors) return
        colors = now
        WidgetUpdater.refresh(this)
        Thread { WeatherWidget.renderAll(this) }.start()
    }
}
