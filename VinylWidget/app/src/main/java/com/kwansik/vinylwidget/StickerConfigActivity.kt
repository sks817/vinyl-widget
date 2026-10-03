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
    private var animOn = true
    private lateinit var animHolder: android.widget.FrameLayout
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
        animOn = StickerAnim.enabled(this, widgetId)
        ui = SheetUi(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(sheetBg) }

        // ---- 위에 고정: 제목 + 고른 스티커 크게 + 열 앱 이름 (바꾸면 바로 보임) ----
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(8)) }
        head.addView(text("병맛 스티커", 20f, onSurface, true))
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        // 미리보기: 움직임을 켜면 실제 위젯과 같은 움직이는 그림
        val box = android.widget.FrameLayout(this)
        box.addView(preview, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
        animHolder = android.widget.FrameLayout(this)
        box.addView(animHolder, android.widget.FrameLayout.LayoutParams(MATCH, MATCH))
        top.addView(box, LinearLayout.LayoutParams(dp(112), dp(112)))
        appName = text("", 15f, onSurface, true).apply { setPadding(dp(14), 0, 0, 0) }
        top.addView(appName, LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(top)

        fun section() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val secPick = section(); val secLook = section(); val secApp = section()

        // 스티커 고르기 (한 줄에 6개)
        secPick.addView(card("스티커 고르기").also { c ->
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

        // 움직임
        secLook.addView(ui.card("움직임", "켜면 스티커가 움직여요 (1분마다 반복) · 움직이는 동안엔 기울기·테두리는 적용되지 않아요").also { c ->
            c.addView(android.widget.Switch(this).apply {
                text = "움직이기"; setTextColor(onSurface); isChecked = animOn; textSize = 15f
                setOnCheckedChangeListener { _, on -> animOn = on; refresh() }
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
        })
        // 기울기 · 테두리
        secLook.addView(ui.card("기울기").also { c ->
            c.addView(ui.slider("기울기", 30, look.tilt + 15, { v -> val d = v - 15; if (d > 0) "+$d°" else "$d°" }) { look.tilt = it - 15; refresh() }.first)
        })
        secLook.addView(ui.borderCard({ look.border }, { look.borderDp }, { look.borderColor }) { st, t, col ->
            if (st != null) look.border = st; if (t != null) look.borderDp = t; if (col != null) look.borderColor = col
            refresh()
        })

        // 누르면 열 앱
        secApp.addView(card("누르면 열 앱").also { c ->
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

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(16)) }
        listOf(secPick, secLook, secApp).forEach { body.addView(it) }
        val scroll = ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }
        root.addView(head)
        root.addView(ui.tabs(arrayOf("스티커 고르기", "기울기·테두리", "누르면 열 앱"), listOf(secPick, secLook, secApp), scroll))
        root.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
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
        val name = StickerAnim.nameOf(this, sticker)
        animHolder.removeAllViews()
        val lid = if (animOn) StickerAnim.layoutId(this, StickerAnim.key(this, widgetId, name)) else 0
        if (lid != 0) {
            layoutInflater.inflate(lid, animHolder, true)
            animHolder.findViewById<View>(R.id.st_anim)?.layoutParams = android.widget.FrameLayout.LayoutParams(dp(110), dp(110), Gravity.CENTER)
            preview.visibility = View.INVISIBLE
        } else preview.visibility = View.VISIBLE
        if (look.tilt == 0 && (look.border == BorderFx.NONE || look.borderDp <= 0)) preview.setImageResource(Stickers.RES[sticker.coerceIn(0, Stickers.RES.size - 1)])
        else preview.setImageBitmap(StickerWidget.image(this, sticker, look, dp(112), dp(112), resources.displayMetrics.density))
        cells.forEach { it.background = if (it.tag == sticker) rounded(Color.TRANSPARENT, 16f, accent, 3) else null }
        val label = appRows.firstOrNull { it.second == pkg && pkg != null }?.first?.tag as String?
        appName.text = when {
            pkg == null -> "누르면: 아무 일도 안 해요"
            else -> "누르면: ${label ?: pkg} 열기"
        }
        appRows.forEach { (v, p) -> v.background = if (p == pkg) rounded((accent and 0x00FFFFFF) or (0x33 shl 24), 14f) else null }
    }

    private fun save() {
        StickerWidget.save(this, widgetId, sticker, pkg)
        StickerWidget.saveLook(this, widgetId, look)
        StickerAnim.saveOptions(this, widgetId, animOn)
        StickerWidget.renderAll(this)   // 모닥불을 고르거나 빼면 다른 마시멜로 속도도 바뀜
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
