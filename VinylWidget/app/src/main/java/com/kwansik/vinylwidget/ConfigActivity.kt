package com.kwansik.vinylwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 위젯 꾸미기
 * - 날씨: 디자인 5종 선택
 * - 공통: 배경(자동 유리/검정/흰색), 배경 투명도, 버튼(또는 글자·아이콘) 색 (자동 = 배경화면 색)
 * 미리보기는 실제 위젯과 똑같은 코드로 그려서, 보이는 그대로 홈 화면에 적용됨
 */
class ConfigActivity : Activity() {

    companion object {
        const val EXTRA_KIND = "kind"
    }

    private val presets = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt(), 0xFF161616.toInt(), 0xFFFF0033.toInt(), 0xFFFF8A00.toInt(),
        0xFFFFD600.toInt(), 0xFF00C853.toInt(), 0xFF00B8D4.toInt(), 0xFF2962FF.toInt(), 0xFFD500F9.toInt()
    )

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind = Kind.MUSIC
    private var white = false
    private var glass = true
    private var transparency = 0
    private var color = Color.WHITE
    private var design = 1

    private val hsv = FloatArray(3)
    private var updatingUi = false
    private var lastLayoutId = 0

    private lateinit var holder: FrameLayout
    private val designButtons = mutableListOf<Button>()
    private val bgButtons = mutableListOf<Button>()
    private val swatches = mutableListOf<View>()
    private lateinit var transLabel: TextView
    private lateinit var transBar: SeekBar
    private lateinit var hueBar: SeekBar
    private lateinit var satBar: SeekBar
    private lateinit var valBar: SeekBar
    private lateinit var satGradient: GradientDrawable
    private lateinit var valGradient: GradientDrawable
    private lateinit var hexInput: EditText
    private lateinit var colorChip: View
    private lateinit var autoColorBtn: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent())

        kind = if (targetId() != null) KindConfig.kindOf(this, widgetId)
        else runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "MUSIC") }.getOrDefault(Kind.MUSIC)

        val s = WidgetPrefs.style(this, kind, targetId())
        white = s.white
        glass = s.glass
        transparency = s.transparency
        color = s.fg
        design = s.design

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(buildPreview(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220)))
        root.addView(ScrollView(this).apply { addView(buildPanel()) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.padForSystemBars()

        refreshSelectors()
        if (color == Palette.AUTO) setAutoColor() else applyColor(color, fromHsv = false)
    }

    private fun targetId(): Int? = widgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    private fun currentStyle() = WidgetStyle(white, transparency, color, design, glass)

    // ---------- 미리보기 (실제 위젯 코드로 그림) ----------
    private fun buildPreview(): View {
        val area = FrameLayout(this)
        holder = FrameLayout(this)
        val (pw, ph) = KindConfig.previewSize(kind)
        area.addView(holder, FrameLayout.LayoutParams(dp(pw.toInt()), dp(ph.toInt()), Gravity.CENTER))
        return area
    }

    private fun renderPreview() {
        if (!::holder.isInitialized) return
        val rv: RemoteViews = KindConfig.preview(this, kind, currentStyle())
        val existing = holder.getChildAt(0)
        if (existing != null && rv.layoutId == lastLayoutId) {
            try {
                rv.reapply(this, existing)
                return
            } catch (e: Exception) {
                // 아래에서 새로 그림
            }
        }
        holder.removeAllViews()
        holder.addView(rv.apply(this, holder))
        lastLayoutId = rv.layoutId
    }

    // ---------- 설정 패널 ----------
    private fun buildPanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(0xF21C1C1E.toInt())
                val r = dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        if (targetId() == null) {
            panel.addView(text("※ 모든 ${KindConfig.name(kind)} 위젯에 한꺼번에 적용됩니다", 13f, 0xFFAAAAAA.toInt()))
        }

        // 디자인 (날씨만)
        val designs = KindConfig.designs(kind)
        if (designs != null) {
            panel.addView(text("디자인", 16f))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            designs.forEachIndexed { i, name ->
                val b = Button(this).apply {
                    text = "${i + 1}. $name"
                    setOnClickListener {
                        design = i + 1
                        // 1×4 '심플'은 그림 없이 카드가 곧 디자인 → 바탕 없음(투명)이면 카드가 보이게 맞춰줌
                        // (유리: 0%, 검정·흰색 카드: 삼성 위젯과 같은 42%)
                        if (kind == Kind.WEATHER_WIDE && design == 3 && transparency >= 95) {
                            transparency = if (glass) 0 else 42; transBar.progress = transparency
                            transLabel.text = "배경 투명도  $transparency%"
                        }
                        refreshSelectors(); renderPreview()
                    }
                }
                designButtons += b
                row.addView(b)
            }
            panel.addView(HorizontalScrollView(this).apply { addView(row) })
            panel.addView(space(12))
        }

        // 배경
        panel.addView(text("배경", 16f))
        panel.addView(text("없음: 배경화면 위에 그림·글자만 / 유리: 배경화면 색을 띤 반투명 카드", 12f, 0xFFAAAAAA.toInt()))
        val bgRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        // (이름, 유리, 흰색, 바탕 없음)
        listOf(
            listOf("없음", true, false, true), listOf("유리", true, false, false),
            listOf("검정", false, false, false), listOf("흰색", false, true, false)
        ).forEach { (name, isGlass, isWhite, none) ->
            val b = Button(this).apply {
                text = name as String
                setOnClickListener {
                    glass = isGlass as Boolean; white = isWhite as Boolean
                    transparency = if (none as Boolean) 100 else if (transparency >= 100) 0 else transparency
                    transBar.progress = transparency
                    transLabel.text = "배경 투명도  $transparency%"
                    refreshSelectors(); renderPreview()
                }
            }
            bgButtons += b
            bgRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        panel.addView(bgRow)

        // 배경 투명도
        panel.addView(space(8))
        transLabel = text("", 16f)
        panel.addView(transLabel)
        transBar = SeekBar(this).apply {
            max = 100
            progress = transparency
            setOnSeekBarChangeListener(listener { p ->
                transparency = p
                transLabel.text = "배경 투명도  $transparency%"
                refreshSelectors()
                renderPreview()
            })
        }
        panel.addView(transBar)
        transLabel.text = "배경 투명도  $transparency%"

        // 색 - 프리셋
        panel.addView(space(16))
        panel.addView(text(KindConfig.colorTitle(kind), 16f))
        KindConfig.colorNote(kind)?.let { panel.addView(text(it, 12f, 0xFFAAAAAA.toInt())) }
        autoColorBtn = Button(this).apply {
            text = "자동 (배경에 맞춤)"
            setOnClickListener { hexInput.clearFocus(); setAutoColor() }
        }
        panel.addView(autoColorBtn)
        for (rowIdx in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            for (i in 0 until 5) {
                val c = presets[rowIdx * 5 + i]
                val sw = View(this).apply {
                    tag = c
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                    }
                    setOnClickListener { hexInput.clearFocus(); applyColor(c, fromHsv = false) }
                }
                swatches += sw
                row.addView(sw, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
            }
            panel.addView(row)
        }

        // 색 - 직접 선택
        panel.addView(space(12))
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("직접 선택", 16f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        colorChip = View(this)
        header.addView(colorChip, LinearLayout.LayoutParams(dp(28), dp(28)))
        panel.addView(header)

        panel.addView(text("색상", 13f, 0xFFAAAAAA.toInt()))
        hueBar = gradientBar(360, GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)))
        panel.addView(hueBar)
        panel.addView(text("채도", 13f, 0xFFAAAAAA.toInt()))
        satGradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.WHITE, Color.RED))
        satBar = gradientBar(100, satGradient)
        panel.addView(satBar)
        panel.addView(text("밝기", 13f, 0xFFAAAAAA.toInt()))
        valGradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.BLACK, Color.RED))
        valBar = gradientBar(100, valGradient)
        panel.addView(valBar)

        hexInput = EditText(this).apply {
            hint = "#FFFFFF"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF777777.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (updatingUi) return
                    val hex = s.toString().trim().removePrefix("#")
                    if (hex.length == 6 && hex.all { it.isDigit() || it.uppercaseChar() in 'A'..'F' }) {
                        applyColor(0xFF000000.toInt() or hex.toInt(16), fromHsv = false)
                    }
                }
            })
        }
        panel.addView(hexInput)

        // 저장/취소
        panel.addView(space(16))
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply { text = "취소"; setOnClickListener { finish() } },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(Button(this).apply { text = "저장"; setOnClickListener { save() } },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(actions)
        return panel
    }

    private fun refreshSelectors() {
        designButtons.forEachIndexed { i, b -> b.alpha = if (i + 1 == design) 1f else 0.45f }
        val bgIndex = if (transparency >= 100) 0 else if (glass) 1 else if (white) 3 else 2
        bgButtons.forEachIndexed { i, b -> b.alpha = if (i == bgIndex) 1f else 0.45f }
    }

    private fun gradientBar(maxValue: Int, drawable: GradientDrawable) = SeekBar(this).apply {
        max = maxValue
        drawable.cornerRadius = dp(6).toFloat()
        progressDrawable = drawable
        minHeight = dp(12)
        maxHeight = dp(12)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        setOnSeekBarChangeListener(listener { onHsvChanged() })
    }

    // ---------- 색 처리 ----------
    private fun onHsvChanged() {
        if (updatingUi) return
        hsv[0] = hueBar.progress.toFloat()
        hsv[1] = satBar.progress / 100f
        hsv[2] = valBar.progress / 100f
        hexInput.clearFocus()
        applyColor(Color.HSVToColor(hsv), fromHsv = true)
    }

    /** 글자·버튼 색 '자동': 실제 색은 배경에 따라 정해지므로 표시만 지금 색으로 */
    private fun setAutoColor() {
        applyColor(Palette.accent(this, currentStyle().copy(fg = Palette.AUTO)), fromHsv = false)
        color = Palette.AUTO
        autoColorBtn.alpha = 1f
        swatches.forEach { (it.background as GradientDrawable).setStroke(0, Color.WHITE) }
        updatingUi = true
        hexInput.setText("")
        updatingUi = false
        renderPreview()
    }

    private fun applyColor(c: Int, fromHsv: Boolean) {
        color = c or 0xFF000000.toInt()
        if (::autoColorBtn.isInitialized) autoColorBtn.alpha = 0.45f
        if (!fromHsv) Color.colorToHSV(color, hsv)
        updatingUi = true
        hueBar.progress = hsv[0].roundToInt()
        satBar.progress = (hsv[1] * 100).roundToInt()
        valBar.progress = (hsv[2] * 100).roundToInt()
        if (!hexInput.hasFocus()) hexInput.setText(String.format("#%06X", color and 0xFFFFFF))
        updatingUi = false

        satGradient.colors = intArrayOf(Color.HSVToColor(floatArrayOf(hsv[0], 0f, hsv[2])),
            Color.HSVToColor(floatArrayOf(hsv[0], 1f, hsv[2])))
        valGradient.colors = intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
        colorChip.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
        swatches.forEach { sw ->
            val sc = sw.tag as Int
            (sw.background as GradientDrawable).setStroke(if (sc == color) dp(3) else 0, Color.WHITE)
        }
        renderPreview()
    }

    private fun save() {
        WidgetPrefs.save(this, kind, targetId(), currentStyle())
        KindConfig.refreshAll(this, kind)
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    // ---------- 작은 도우미 ----------
    private fun text(s: String, size: Float, c: Int = Color.WHITE) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(c)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun space(h: Int) = View(this).apply { minimumHeight = dp(h) }

    private fun listener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
}
