package com.kwansik.vinylwidget

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var errorBox: LinearLayout
    private lateinit var errorText: TextView
    private lateinit var musicStatus: TextView
    private lateinit var weatherStatus: TextView
    private val pad by lazy { (16 * resources.displayMetrics.density).toInt() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // 오류 기록 (있을 때만 보임)
        errorText = body()
        errorBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title("⚠️ 최근 오류 기록"))
            addView(errorText)
            addView(button("오류 내용 복사") {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("오류 기록", CrashLog.read(this@MainActivity) ?: ""))
                Toast.makeText(this@MainActivity, "복사했습니다. 그대로 붙여넣어 보내주세요.", Toast.LENGTH_LONG).show()
            })
            addView(button("기록 지우기") { CrashLog.clear(this@MainActivity); updateErrorBox() })
        }
        root.addView(errorBox)

        // 레코드 위젯
        root.addView(title("레코드 위젯"))
        musicStatus = body()
        root.addView(musicStatus)
        root.addView(button("알림 접근 권한 열기") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        root.addView(button("레코드 위젯 추가 (2×2)") { pin(SpinWidgetProvider::class.java) })
        root.addView(button("레코드 위젯 추가 (1×4)") { pin(SpinWideProvider::class.java) })
        root.addView(button("레코드 위젯 모두 꾸미기 (2×2)") { openConfig(Kind.MUSIC) })
        root.addView(button("레코드 위젯 모두 꾸미기 (1×4)") { openConfig(Kind.MUSIC_WIDE) })

        // 날씨 위젯
        root.addView(title("날씨·날짜 위젯"))
        weatherStatus = body()
        root.addView(weatherStatus)
        root.addView(button("위치 권한 허용") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        })
        root.addView(button("날씨 위젯 추가 (2×2)") { pin(WeatherWidgetProvider::class.java) })
        root.addView(button("날씨 위젯 추가 (1×4)") { pin(WeatherWideProvider::class.java) })
        root.addView(button("날씨 위젯 모두 꾸미기 (2×2)") { openConfig(Kind.WEATHER) })
        root.addView(button("날씨 위젯 모두 꾸미기 (1×4)") { openConfig(Kind.WEATHER_WIDE) })
        root.addView(button("날씨 지금 새로고침") { refreshLocationAndFetch() })

        // 더 많은 위젯
        root.addView(title("일정 메모 · 병맛 스티커"))
        root.addView(button("캘린더 권한 허용 (일정 메모)") {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 2)
        })
        root.addView(button("일정 메모 위젯 추가 (4×1, 늘리면 4×2·2×4)") { pin(MemoWidgetProvider::class.java) })
        root.addView(button("일정 메모 모두 꾸미기") { openConfig(Kind.MEMO) })
        root.addView(button("병맛 스티커 추가 (1×1, 누르면 고른 앱 열기)") { pin(StickerWidgetProvider::class.java) })

        root.addView(body().apply {
            setPadding(0, pad, 0, 0)
            text = "날씨 정보: Open-Meteo\n" +
                "위치는 이 앱을 열 때 갱신됩니다. 다른 지역으로 이동하면 앱을 한 번 열어 주세요.\n" +
                "위젯마다 다르게 꾸미려면 홈 화면에서 위젯을 길게 눌러 '설정'을 누르세요."
        })

        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        scroll.padForSystemBars()
    }

    private fun updateErrorBox() {
        val log = CrashLog.read(this)
        errorBox.visibility = if (log == null) View.GONE else View.VISIBLE
        errorText.text = log?.lines()?.take(6)?.joinToString("\n") ?: ""
    }

    override fun onResume() {
        super.onResume()
        updateErrorBox()
        val ok = MediaHelper.hasAccess(this)
        musicStatus.text = if (ok) "✅ 알림 접근 허용됨 — 유튜브 뮤직을 재생하면 판이 돌아갑니다."
        else "⚠️ 알림 접근 권한이 필요합니다. 아래 버튼에서 '레코드 위젯'을 켜 주세요.\n" +
            "(곡 정보·커버를 기기 안에서 위젯에 표시하는 데만 사용)"
        if (ok) {
            NotificationListenerService.requestRebind(MediaHelper.listenerComponent(this))
            WidgetUpdater.refresh(this)
        }
        WeatherScheduler.schedule(this)
        updateWeatherStatus()
        if (LocationHelper.hasPermission(this)) refreshLocationAndFetch()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 2) {                       // 캘린더 → 일정 메모 다시 그리기
            val app = applicationContext
            Thread { MemoWidget.renderAll(app) }.start()
            return
        }
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            refreshLocationAndFetch()
        } else {
            updateWeatherStatus()
        }
    }

    private fun refreshLocationAndFetch() {
        weatherStatus.text = "위치 확인 및 날씨 갱신 중…"
        LocationHelper.requestCurrent(this) {
            WeatherFetcher.fetchAsync(this) { runOnUiThread { updateWeatherStatus() } }
        }
    }

    private fun updateWeatherStatus() {
        val d = WeatherStore.load(this)
        val lines = mutableListOf<String>()
        lines += if (LocationHelper.hasPermission(this)) "✅ 위치 권한 허용됨" else "⚠️ 위치 권한이 필요합니다"
        if (d.updated > 0) {
            val t = SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(d.updated))
            lines += "마지막 갱신 $t — ${d.tempText()} ${d.cond()} ${d.rangeText()}"
        }
        d.error?.let { lines += "⚠️ $it" }
        weatherStatus.text = lines.joinToString("\n")
    }

    private fun openConfig(kind: Kind) {
        startActivity(Intent(this, ConfigActivity::class.java).putExtra(ConfigActivity.EXTRA_KIND, kind.name))
    }

    private fun pin(cls: Class<*>) {
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, cls), null, null)
        } else {
            Toast.makeText(this, "홈 화면을 길게 눌러 위젯 메뉴에서 추가해 주세요", Toast.LENGTH_LONG).show()
        }
    }

    private fun title(s: String) = TextView(this).apply {
        text = s
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, pad * 2, 0, pad / 2)
    }

    private fun body() = TextView(this).apply { textSize = 14f; setPadding(0, 0, 0, pad / 2) }

    private fun button(text: String, onClick: () -> Unit) =
        Button(this).apply { this.text = text; setOnClickListener { onClick() } }
}
