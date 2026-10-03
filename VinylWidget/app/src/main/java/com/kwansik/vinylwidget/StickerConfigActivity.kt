package com.kwansik.vinylwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 스티커 위젯 고르기: 위 = 고른 스티커 크게, 가운데 = 스티커 18종, 아래 = 누르면 열 앱 (검색 가능).
 * 꾸미기 화면(ConfigActivity)과 같은 어두운 시트 + 배경화면 강조색
 */
class StickerConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var sticker = 0
    private var pkg: String? = null
    private lateinit var look: StickerWidget.Look
    private lateinit var ui: SheetUi

    private val sheetBg by lazy { getColor(android.R.color.system_neutral1_900) }
    private val cardBg by lazy { getColor(android.R.color.system_neutral1_800) }
    private val trackBg by lazy { getColor(android.R.color.system_neutral1_700) }
    private val onSurface by lazy { getColor(android.R.color.system_neutral1_50) }
    private val onSurfaceVar by lazy { getColor(android.R.color.system_neutral2_200) }
    private val outline by lazy { getColor(android.R.color.system_neutral2_500) }
    private val accent by lazy { getColor(android.R.color.system_accent1_200) }
    private val onAccent by lazy { getColor(android.R.color.system_accent1_800) }

    private lateinit var preview: ImageView
    private lateinit var appName: TextView
    private val cells = mutableListOf<View>()
    private val appRows = mutableListOf<Pair<View, String?>>()       // (줄, 패키지)
    private lateinit var appList: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        sticker = StickerWidget.sticker(this, widgetId)
        pkg = StickerWidget.app(this, widgetId)
        look = StickerWidget.look(this, widgetId)
        ui = SheetUi(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(sheetBg) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }

        body.addView(text("병맛 스티커", 22f, onSurface, true))
        body.addView(text("홈 화면에 붙이고, 누르면 원하는 앱이 열려요", 13f, onSurfaceVar).apply { setPadding(0, dp(2), 0, dp(12)) })

        // 고른 스티커 크게 + 열 앱 이름
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        top.addView(preview, LinearLayout.LayoutParams(dp(112), dp(112)))
        appName = text("", 15f, onSurface, true).apply { setPadding(dp(14), 0, 0, 0) }
        top.addView(appName, LinearLayout.LayoutParams(0, WRAP, 1f))
        body.addView(top)

        // 스티커 고르기 (한 줄에 6개)
        body.addView(card("스티커 고르기").also { c ->
            Stickers.RES.toList().chunked(6).forEachIndexed { r, row ->
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.forEachIndexed { i, res ->
                    val idx = r * 6 + i
                    val cell = ImageView(this).apply {
                        setImageResource(res); scaleType = ImageView.ScaleType.FIT_CENTER
                        setPadding(dp(3), dp(3), dp(3), dp(3)); tag = idx
                        contentDescription = Stickers.NAMES[idx]
                        setOnClickListener { sticker = idx; refresh() }
                    }
                    cells += cell
                    line.addView(cell, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
                }
                c.addView(line)
            }
        })

        // 기울기 · 테두리
        body.addView(ui.card("기울기").also { c ->
            c.addView(ui.slider("기울기", 30, look.tilt + 15, { v -> val d = v - 15; if (d > 0) "+$d°" else "$d°" }) { look.tilt = it - 15; refresh() }.first)
        })
        body.addView(ui.borderCard({ look.border }, { look.borderDp }, { look.borderColor }) { st, t, col ->
            if (st != null) look.border = st; if (t != null) look.borderDp = t; if (col != null) look.borderColor = col
            refresh()
        })

        // 누르면 열 앱
        body.addView(card("누르면 열 앱").also { c ->
            val search = EditText(this).apply {
                hint = "앱 이름 검색"; textSize = 14f; setTextColor(onSurface); setHintTextColor(outline)
                background = rounded(trackBg, 14f); setPadding(dp(14), dp(10), dp(14), dp(10)); isSingleLine = true
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { filter(s.toString().trim()) }
                })
            }
            c.addView(search, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8); bottomMargin = dp(6) })
            appList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(appList)
        })

        root.addView(ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(bottomBar())
        setContentView(root)
        root.padForSystemBars()

        ui.refresh()
        addAppRow(null, "앱 안 열기 (그냥 스티커)", null)
        loadApps()
        refresh()
    }

    /** 홈 화면에 아이콘이 있는 앱들 (이름순). 아이콘 읽기가 느릴 수 있어 뒤에서 읽음 */
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val apps: List<ResolveInfo> = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            ).filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .sortedBy { it.loadLabel(pm).toString() }
            val rows = apps.map { Triple(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            runOnUiThread {
                rows.forEach { (p, l, ic) -> addAppRow(p, l, ic) }
                refresh()
            }
        }.start()
    }

    private fun addAppRow(p: String?, label: String, icon: android.graphics.drawable.Drawable?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8)); tag = label
            setOnClickListener { pkg = p; refresh() }
        }
        row.addView(ImageView(this).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(32), dp(32)))
        row.addView(text(label, 15f, onSurface).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, WRAP, 1f))
        appRows += row to p
        appList.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(2) })
    }

    private fun filter(q: String) {
        appRows.forEach { (v, p) -> v.visibility = if (q.isEmpty() || p == null || (v.tag as String).contains(q, ignoreCase = true)) View.VISIBLE else View.GONE }
    }

    private fun refresh() {
        if (look.tilt == 0 && (look.border == BorderFx.NONE || look.borderDp <= 0)) preview.setImageResource(Stickers.RES[sticker.coerceIn(0, Stickers.RES.size - 1)])
        else preview.setImageBitmap(StickerWidget.image(this, sticker, look, dp(112), dp(112), resources.displayMetrics.density))
        cells.forEach { it.background = if (it.tag == sticker) rounded(Color.TRANSPARENT, 16f, accent, 3) else null }
        val label = appRows.firstOrNull { it.second == pkg && pkg != null }?.first?.tag as String?
        appName.text = if (pkg == null) "누르면: 아무 일도 안 해요" else "누르면: ${label ?: pkg} 열기"
        appRows.forEach { (v, p) -> v.background = if (p == pkg) rounded((accent and 0x00FFFFFF) or (0x33 shl 24), 14f) else null }
    }

    private fun save() {
        StickerWidget.save(this, widgetId, sticker, pkg)
        StickerWidget.saveLook(this, widgetId, look)
        AppWidgetManager.getInstance(this).updateAppWidget(widgetId, StickerWidget.build(this, widgetId))
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun bottomBar(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(10), dp(16), dp(14)); setBackgroundColor(sheetBg) }
        val cancel = text("취소", 16f, onSurface).apply {
            gravity = Gravity.CENTER; background = rounded(Color.TRANSPARENT, 26f, outline, 1); setOnClickListener { finish() }
        }
        val ok = text("붙이기", 16f, onAccent, true).apply {
            gravity = Gravity.CENTER; background = rounded(accent, 26f); setOnClickListener { save() }
        }
        bar.addView(cancel, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) })
        bar.addView(ok, LinearLayout.LayoutParams(0, dp(52), 2f))
        return bar
    }

    private fun card(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cardBg, 24f)
        setPadding(dp(14), dp(14), dp(14), dp(12))
        addView(text(title, 16f, onSurface, true))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    private fun text(s: String, size: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(c)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
    }

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radiusDp * resources.displayMetrics.density
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }
}
