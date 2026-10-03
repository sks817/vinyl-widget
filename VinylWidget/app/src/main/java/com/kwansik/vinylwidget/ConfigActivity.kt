package com.kwansik.vinylwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 위젯 꾸미기
 *  - 위: 실제 배경화면 위에 실제 위젯 코드로 그린 미리보기 (보이는 그대로 홈 화면에 적용)
 *  - 아래: 시트 안의 카드 섹션. 날씨는 디자인·그림 효과(유리·그림자·모서리), 공통은 배경·투명도·색
 *  - 화면 색은 배경화면에서 뽑은 시스템 색(Material You)을 따름
 */
class ConfigActivity : Activity() {

    companion object {
        const val EXTRA_KIND = "kind"
    }

    /** 글자·버튼 추천 색: 윗줄은 배경화면에서 뽑은 색, 아랫줄은 진한 강조색 + 캠핑 그림과 어울리는 색 */
    private val presets by lazy {
        intArrayOf(
            getColor(android.R.color.system_neutral1_10), getColor(android.R.color.system_accent1_100),
            getColor(android.R.color.system_accent1_300), getColor(android.R.color.system_accent2_300),
            getColor(android.R.color.system_accent3_300),
            getColor(android.R.color.system_accent1_600), getColor(android.R.color.system_neutral1_900),
            0xFFF5B860.toInt(), 0xFFE0794F.toInt(), 0xFF8DB596.toInt()
        ).map { it or 0xFF000000.toInt() }.toIntArray()
    }

    /** 달력 윗부분 추천 색: 기본 벽돌색 + 배경화면 색 + 캠핑 톤 */
    private val pointPresets by lazy {
        intArrayOf(
            WidgetStyle.DEFAULT_POINT, getColor(android.R.color.system_accent1_500),
            getColor(android.R.color.system_accent2_500), getColor(android.R.color.system_accent3_500), 0xFFE0794F.toInt(),
            0xFF4F7D64.toInt(), 0xFF2F4A7A.toInt(), 0xFFD9A441.toInt(), 0xFF3A3A3A.toInt(), 0xFFE58FA6.toInt()
        ).map { it or 0xFF000000.toInt() }.toIntArray()
    }

    /** 배경 '컬러' 추천 색: 배경화면에서 뽑은 옅은 색·진한 색 + 캠핑 톤(모래·복숭아·민트·숲·밤하늘) */
    private val bgPresets by lazy {
        intArrayOf(
            getColor(android.R.color.system_accent1_100), getColor(android.R.color.system_accent2_100),
            getColor(android.R.color.system_accent3_100), getColor(android.R.color.system_accent1_700),
            getColor(android.R.color.system_neutral2_800),
            0xFFF6E7C8.toInt(), 0xFFFFD6C2.toInt(), 0xFFCFE6D4.toInt(), 0xFF2F4A3A.toInt(), 0xFF22304F.toInt()
        ).map { it or 0xFF000000.toInt() }.toIntArray()
    }

    // ---- 꾸미기 값 ----
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var kind = Kind.MUSIC
    private var white = false
    private var glass = true
    private var transparency = 0
    private var color = Color.WHITE
    private var design = 1
    private var corner = -1
    private var glassArt = true
    private var artShadow = true
    private var quirky = false
    private var point = WidgetStyle.DEFAULT_POINT
    private var bg = 0
    private var disc = 0
    private var player = WidgetStyle.VINYL
    private var nightFg = 0
    private var border = BorderFx.NONE
    private var borderDp = 4
    private var borderColor = 0
    private val sheet by lazy { SheetUi(this) }

    private val hsv = FloatArray(3)
    private var updatingUi = false
    private var lastLayoutId = 0

    // ---- 화면 부품 ----
    private lateinit var holder: FrameLayout
    private val refreshers = mutableListOf<() -> Unit>()     // 선택 상태가 바뀔 때마다 다시 칠할 것들
    private val swatches = mutableListOf<View>()
    private lateinit var autoChip: TextView
    private lateinit var transValue: TextView
    private lateinit var transBar: SeekBar
    private lateinit var hueBar: SeekBar
    private lateinit var satBar: SeekBar
    private lateinit var valBar: SeekBar
    private lateinit var satGradient: GradientDrawable
    private lateinit var valGradient: GradientDrawable
    private lateinit var hexInput: EditText
    private lateinit var colorDot: View

    // ---- 색 (시트는 항상 어두운 톤, 강조색은 배경화면 색) ----
    private val sheetBg by lazy { (getColor(android.R.color.system_neutral1_900) and 0x00FFFFFF) or (0xF7 shl 24) }
    private val cardBg by lazy { getColor(android.R.color.system_neutral1_800) }
    private val trackBg by lazy { getColor(android.R.color.system_neutral1_700) }
    private val onSurface by lazy { getColor(android.R.color.system_neutral1_50) }
    private val onSurfaceVar by lazy { getColor(android.R.color.system_neutral2_200) }
    private val outline by lazy { getColor(android.R.color.system_neutral2_500) }
    private val accent by lazy { getColor(android.R.color.system_accent1_200) }
    private val onAccent by lazy { getColor(android.R.color.system_accent1_800) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent())

        kind = if (targetId() != null) KindConfig.kindOf(this, widgetId)
        else runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "MUSIC") }.getOrDefault(Kind.MUSIC)

        val s = WidgetPrefs.style(this, kind, targetId())
        white = s.white; glass = s.glass; transparency = s.transparency; color = s.fg
        design = s.design; corner = s.corner; glassArt = s.glassArt; artShadow = s.artShadow
        quirky = s.quirky; point = s.point; bg = s.bg
        disc = s.disc; player = s.player; nightFg = s.nightFg
        border = s.border; borderDp = s.borderDp; borderColor = s.borderColor

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(buildPreview(), LinearLayout.LayoutParams(MATCH, dp(236)))
        root.addView(buildSheet(), LinearLayout.LayoutParams(MATCH, 0, 1f))
        setContentView(root)
        root.padForSystemBars()

        refreshAll()
        if (color == Palette.AUTO) setAutoColor() else applyColor(color, fromHsv = false)
    }

    private fun targetId(): Int? = widgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
    private fun currentStyle() = WidgetStyle(white, transparency, color, design, glass, corner, glassArt, artShadow, quirky, point, bg, disc, player, nightFg,
        border, borderDp, borderColor)
    private fun isWeather() = kind == Kind.WEATHER || kind == Kind.WEATHER_WIDE

    // ================= 미리보기 =================
    private fun buildPreview(): View {
        val area = FrameLayout(this)
        holder = FrameLayout(this)
        val (pw, ph) = KindConfig.previewSize(kind)
        area.addView(holder, FrameLayout.LayoutParams(dp(pw.toInt()), dp(ph.toInt()), Gravity.CENTER))
        area.addView(TextView(this).apply {
            text = "미리보기"
            textSize = 11f
            setTextColor(0xE6FFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(0x52000000, 12f)
        }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(10) })
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

    // ================= 시트 =================
    private fun buildSheet(): View {
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(sheetBg)
                val r = dpf(28f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        // 손잡이
        sheet.addView(View(this).apply { background = rounded(outline, 2f) },
            LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10); bottomMargin = dp(6) })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(16))
        }
        body.addView(text("${KindConfig.name(kind)} 위젯 꾸미기", 20f, onSurface, bold = true))
        body.addView(text(if (targetId() == null) "모든 ${KindConfig.name(kind)} 위젯에 한꺼번에 적용돼요"
            else "이 위젯에만 적용돼요", 13f, onSurfaceVar).apply { setPadding(0, dp(2), 0, dp(14)) })

        if (isWeather()) {
            body.addView(designCard())
            body.addView(artCard())
            if (kind == Kind.WEATHER) body.addView(calendarCard())
        }
        if (KindConfig.isMusic(kind)) body.addView(playerCard())
        if (kind == Kind.MEMO) body.addView(memoCard())
        body.addView(themeCard())
        body.addView(backgroundCard())
        // 테두리 (모든 위젯 공통): 스타일 · 두께 · 색
        body.addView(sheet.borderCard({ border }, { borderDp }, { borderColor }) { st, t, col ->
            if (st != null) border = st; if (t != null) borderDp = t; if (col != null) borderColor = col
            renderPreview()
        })
        refreshers += { sheet.refresh() }
        body.addView(colorCard())

        sheet.addView(ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false },
            LinearLayout.LayoutParams(MATCH, 0, 1f))
        sheet.addView(bottomBar())
        return sheet
    }

    // ---- 디자인 (날씨) ----
    private fun designCard(): View {
        val card = card("디자인", null)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        KindConfig.designs(kind)!!.forEachIndexed { i, name ->
            val chip = pill("$name")
            chip.setOnClickListener {
                design = i + 1
                // 1×4 '심플'은 그림 없이 카드가 곧 디자인 → 바탕 없음(투명)이면 카드가 보이게 맞춰줌
                // (유리: 0%, 검정·흰색 카드: 삼성 위젯과 같은 42%)
                if (kind == Kind.WEATHER_WIDE && design == 3 && transparency >= 95) setTransparency(if (glass) 0 else 42)
                // 캐릭터 카드는 카드가 곧 디자인 → 바탕 없음(투명)이면 카드가 보이게 (투명도로 카드 진하기 조절)
                val cardDesign = (kind == Kind.WEATHER && design == WeatherWidget.CARD_2X2) ||
                    (kind == Kind.WEATHER_WIDE && design == WeatherWidget.CARD_1X4)
                if (cardDesign && transparency >= 95) setTransparency(0)
                refreshAll(); renderPreview()
            }
            refreshers += { styleChip(chip, design == i + 1) }
            row.addView(chip, LinearLayout.LayoutParams(WRAP, dp(38)).apply { marginEnd = dp(8) })
        }
        card.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        return card
    }

    // ---- 그림 효과 (날씨): 유리 캡슐 / 그림자 / 모서리 ----
    private fun artCard(): View {
        val card = card("그림 효과", "LP 재킷 · 캠핑 파노라마 디자인에 적용돼요")
        val glassRow = toggleRow("유리 캡슐", "그림이 유리관 안에 담긴 것처럼 반사광을 얹어요", { glassArt }) { glassArt = it }
        val shadowRow = toggleRow("그림자", "배경 '없음'일 때 그림 아래에 은은한 그림자", { artShadow }) { artShadow = it }
        card.addView(glassRow)
        card.addView(divider())
        card.addView(shadowRow)
        refreshers += {
            val artDesign = design == 1
            dim(glassRow, artDesign)
            dim(shadowRow, artDesign && transparency >= 100)
        }
        return card
    }

    /** 판·테이프 색: 첫 번째(검정)는 '기본'(0)으로 저장 → 병맛이면 잉크색 판 / 분홍 테이프 */
    private val discPresets = intArrayOf(
        0xFF111111.toInt(), 0xFFC62828.toInt(), 0xFF7B1F3A.toInt(), 0xFFF48FB1.toInt(), 0xFFEF6C00.toInt(),
        0xFFF2C14E.toInt(), 0xFF6CC4A1.toInt(), 0xFF1E5AA8.toInt(), 0xFF6A4C93.toInt(), 0xFFE8E4DC.toInt()
    )

    // ---- 플레이어 (음악): 레코드판 / 카세트 + 판·테이프 색 ----
    private fun playerCard(): View {
        val card = card("플레이어", "레코드판 또는 레트로 카세트 테이프. 병맛 테마를 켜면 각각 병맛 버전이 돼요")
        card.addView(segmented(listOf("레코드판", "카세트 테이프"), selected = { player }) { i ->
            player = i; refreshAll(); renderPreview()
        })
        card.addView(caption("판·테이프 색 (첫 번째 = 기본)").apply { setPadding(0, dp(12), 0, dp(2)) })
        val sws = mutableListOf<View>()
        card.addView(swatchGrid(discPresets, sws) { c ->
            disc = if (c == discPresets[0]) 0 else c
            refreshAll(); renderPreview()
        })
        refreshers += { sws.forEach { styleSwatch(it, it.tag as Int == (if (disc == 0) discPresets[0] else disc)) } }
        return card
    }

    // ---- 일정: 캘린더 권한 ----
    private lateinit var permBtn: TextView
    private fun memoCard(): View {
        val card = card("캘린더", "기기에 연결된 캘린더(구글·삼성 등)의 일정 제목과 시간만 읽어요. 저장하거나 보내지 않아요")
        permBtn = TextView(this).apply {
            textSize = 15f; gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setOnClickListener { requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 7) }
        }
        card.addView(permBtn, LinearLayout.LayoutParams(MATCH, dp(46)).apply { topMargin = dp(8) })
        refreshers += {
            val ok = CalendarReader.hasPermission(this)
            permBtn.text = if (ok) "✓ 캘린더 권한 허용됨" else "캘린더 권한 허용하기"
            permBtn.isEnabled = !ok
            permBtn.setTextColor(if (ok) onSurfaceVar else onAccent)
            permBtn.background = if (ok) rounded(Color.TRANSPARENT, 22f, outline, 1) else rounded(accent, 22f)
        }
        return card
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshAll(); renderPreview()
        if (kind == Kind.MEMO) KindConfig.refreshAll(this, kind)
    }

    // ---- 병맛 테마 ----
    private fun themeCard(): View {
        val music = KindConfig.isMusic(kind)
        val card = card("테마", null)
        card.addView(toggleRow("병맛 테마",
            if (music) "만화풍 레코드판·왕눈이 라벨 / 눈알이 도는 카세트"
            else if (kind == Kind.MEMO) "병맛 스티커 캐릭터가 함께 붙어요" else "날씨 아이콘이 표정 있는 캐릭터로 바뀌어요",
            { quirky }) { quirky = it })
        return card
    }

    // ---- 달력 윗부분 색 (날씨 2×2 '달력' 디자인) ----
    private fun calendarCard(): View {
        val card = card("달력 윗부분 색", "'달력' 디자인의 맨 위 띠 색이에요")
        val sws = mutableListOf<View>()
        card.addView(swatchGrid(pointPresets, sws) { c -> point = c; sws.forEach { styleSwatch(it, it.tag as Int == point) }; renderPreview() })
        refreshers += {
            card.visibility = if (design == 7) View.VISIBLE else View.GONE
            sws.forEach { styleSwatch(it, it.tag as Int == point) }
        }
        return card
    }

    /** 정원형 색 동그라미 격자 (한 줄에 5개, 지름 40dp 고정) */
    private fun swatchGrid(colors: IntArray, into: MutableList<View>, onPick: (Int) -> Unit): View {
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        colors.toList().chunked(5).forEach { rowColors ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            rowColors.forEachIndexed { i, c ->
                if (i > 0) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))       // 동그라미 사이를 고르게 벌림
                val sw = View(this).apply {
                    tag = c
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                    setOnClickListener { onPick(c) }
                }
                into += sw
                row.addView(sw, LinearLayout.LayoutParams(dp(40), dp(40)).apply { setMargins(0, dp(6), 0, dp(6)) })
            }
            grid.addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(4), 0, dp(4), 0) })
        }
        return grid
    }

    // ---- 배경 ----
    private fun backgroundCard(): View {
        val card = card("배경", "없음: 배경화면 위에 그대로 · 유리: 배경화면 색을 띤 반투명 카드 · 컬러: 원하는 색" +
            if (isWeather()) " (캐릭터 카드는 카드 색)" else "")
        // (이름, 유리, 흰색, 바탕 없음, 컬러)
        data class Bg(val name: String, val glass: Boolean, val white: Boolean, val none: Boolean, val color: Boolean = false)
        val options = listOf(Bg("없음", true, false, true), Bg("유리", true, false, false),
            Bg("검정", false, false, false), Bg("흰색", false, true, false), Bg("컬러", false, false, false, true))
        val bgSwatches = mutableListOf<View>()
        val colorPick = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(segmented(options.map { it.name },
            selected = { if (transparency >= 100) 0 else if (glass) 1 else if (bg != 0) 4 else if (white) 3 else 2 }) { i ->
            val o = options[i]
            glass = o.glass; white = o.white
            bg = if (o.color) (if (bg != 0) bg else bgPresets[0]) else 0
            setTransparency(if (o.none) 100 else if (transparency >= 100) 0 else transparency)
            refreshAll(); renderPreview()
        })
        colorPick.addView(caption("배경화면에 어울리는 추천 색").apply { setPadding(0, dp(12), 0, dp(2)) })
        colorPick.addView(swatchGrid(bgPresets, bgSwatches) { c -> bg = c; refreshAll(); renderPreview() })
        card.addView(colorPick)
        refreshers += {
            colorPick.visibility = if (bg != 0 && transparency < 100) View.VISIBLE else View.GONE
            bgSwatches.forEach { styleSwatch(it, it.tag as Int == bg) }
        }
        card.addView(space(14))
        card.addView(slider("배경 투명도", 100, transparency, { "$it%" }, keep = { bar, value -> transBar = bar; transValue = value }) {
            transparency = it; refreshAll(); renderPreview()
        })
        // 모서리 둥글기: 위젯 바탕(과 날씨 그림) 모서리. 짧은 변의 %, 처음엔 시스템 기본 곡률
        card.addView(space(6))
        val shown = if (corner < 0) KindConfig.defaultCorner(kind) else corner
        card.addView(slider("모서리 둥글기", 50, shown, { "$it%" }) { corner = it; renderPreview() })
        return card
    }

    private fun setTransparency(v: Int) {
        transparency = v
        if (::transBar.isInitialized) { transBar.progress = v; transValue.text = "$v%" }
    }

    // ---- 색 ----
    private fun colorCard(): View {
        val card = card(KindConfig.colorTitle(kind), KindConfig.colorNote(kind))
        autoChip = pill("✦  자동 · 배경에 맞춤").apply { setOnClickListener { hexInput.clearFocus(); setAutoColor() } }
        card.addView(autoChip, LinearLayout.LayoutParams(WRAP, dp(38)).apply { bottomMargin = dp(10) })

        card.addView(caption("배경화면에 어울리는 추천 색").apply { setPadding(0, 0, 0, dp(2)) })
        card.addView(swatchGrid(presets, swatches) { c -> hexInput.clearFocus(); applyColor(c, fromHsv = false) })

        // 직접 고르기 (접었다 펴기)
        val picker = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(4))
        }
        val arrow = text("›", 20f, onSurfaceVar)
        header.addView(text("직접 고르기", 15f, onSurface, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        colorDot = View(this)
        header.addView(colorDot, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
        header.addView(arrow)
        header.setOnClickListener {
            val open = picker.visibility != View.VISIBLE
            picker.visibility = if (open) View.VISIBLE else View.GONE
            arrow.animate().rotation(if (open) 90f else 0f).setDuration(180).start()
        }
        card.addView(header)

        picker.addView(caption("색상"))
        hueBar = gradientBar(360, GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)))
        picker.addView(hueBar)
        picker.addView(caption("채도"))
        satGradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.WHITE, Color.RED))
        satBar = gradientBar(100, satGradient)
        picker.addView(satBar)
        picker.addView(caption("밝기"))
        valGradient = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.BLACK, Color.RED))
        valBar = gradientBar(100, valGradient)
        picker.addView(valBar)

        hexInput = EditText(this).apply {
            hint = "#FFFFFF"
            textSize = 15f
            setTextColor(onSurface)
            setHintTextColor(outline)
            background = rounded(trackBg, 14f)
            setPadding(dp(14), dp(10), dp(14), dp(10))
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
        picker.addView(hexInput, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })
        card.addView(picker)
        if (isWeather()) card.addView(nightSection())
        return card
    }

    /** 밤 글자색: 해가 지면 이 색. 자동 = 고른 글자색이 어두우면 밤엔 크림색으로 (어두운 밤 그림 위에서도 숫자가 보이게) */
    private val nightPresets = intArrayOf(
        WeatherWidget.NIGHT_AUTO_TEXT, 0xFFFFFFFF.toInt(), 0xFFFFE8A3.toInt(), 0xFFFFC9D6.toInt(), 0xFFBFE7FF.toInt(),
        0xFFC8F2D4.toInt(), 0xFFE3D4FF.toInt(), 0xFFFFD2A8.toInt(), 0xFFB9C3D6.toInt(), 0xFF8FB8FF.toInt()
    )

    private fun nightSection(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(divider(), LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(12) })
        box.addView(text("밤 글자색 🌙", 15f, onSurface, bold = true).apply { setPadding(0, dp(12), 0, dp(2)) })
        box.addView(text("해가 지면 이 색으로 바뀌어요. 자동은 어두운 글자색만 밝게 바꿔요", 12.5f, onSurfaceVar))
        val auto = pill("✦  자동 · 밤엔 밝게")
        box.addView(auto, LinearLayout.LayoutParams(WRAP, dp(38)).apply { topMargin = dp(10); bottomMargin = dp(4) })
        val sws = mutableListOf<View>()
        box.addView(swatchGrid(nightPresets, sws) { c -> nightFg = c; refreshAll(); renderPreview() })
        auto.setOnClickListener { nightFg = 0; refreshAll(); renderPreview() }
        refreshers += {
            styleChip(auto, nightFg == 0)
            sws.forEach { styleSwatch(it, it.tag as Int == nightFg) }
        }
        return box
    }

    // ---- 하단 고정 버튼 ----
    private fun bottomBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(14))
            setBackgroundColor(sheetBg)
        }
        val cancel = TextView(this).apply {
            text = "취소"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(onSurface)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = rounded(Color.TRANSPARENT, 26f, outline, 1)
            setOnClickListener { finish() }
        }
        val save = TextView(this).apply {
            text = "저장"; textSize = 16f; gravity = Gravity.CENTER
            setTextColor(onAccent)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = rounded(accent, 26f)
            setOnClickListener { save() }
        }
        bar.addView(cancel, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) })
        bar.addView(save, LinearLayout.LayoutParams(0, dp(52), 2f))
        return bar
    }

    // ================= 상태 =================
    private fun refreshAll() = refreshers.forEach { it() }

    /** 글자·버튼 색 '자동': 실제 색은 배경에 따라 정해지므로 표시만 지금 색으로 */
    private fun setAutoColor() {
        applyColor(Palette.accent(this, currentStyle().copy(fg = Palette.AUTO)), fromHsv = false)
        color = Palette.AUTO
        styleChip(autoChip, true)
        swatches.forEach { styleSwatch(it, false) }
        updatingUi = true
        hexInput.setText("")
        updatingUi = false
        renderPreview()
    }

    private fun applyColor(c: Int, fromHsv: Boolean) {
        color = c or 0xFF000000.toInt()
        if (::autoChip.isInitialized) styleChip(autoChip, false)
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
        colorDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(2), onSurface)
        }
        swatches.forEach { styleSwatch(it, it.tag as Int == color) }
        renderPreview()
    }

    private fun onHsvChanged() {
        if (updatingUi) return
        hsv[0] = hueBar.progress.toFloat()
        hsv[1] = satBar.progress / 100f
        hsv[2] = valBar.progress / 100f
        hexInput.clearFocus()
        applyColor(Color.HSVToColor(hsv), fromHsv = true)
    }

    private fun save() {
        WidgetPrefs.save(this, kind, targetId(), currentStyle())
        KindConfig.refreshAll(this, kind)
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    // ================= 부품 =================
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun rounded(fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dpf(radiusDp)
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun text(s: String, size: Float, c: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(c)
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun caption(s: String) = text(s, 12f, onSurfaceVar).apply { setPadding(0, dp(10), 0, dp(2)) }

    private fun space(h: Int) = View(this).apply { minimumHeight = dp(h) }

    private fun divider() = View(this).apply { setBackgroundColor((onSurface and 0x00FFFFFF) or (0x14 shl 24)) }
        .also { it.layoutParams = LinearLayout.LayoutParams(MATCH, dp(1)).apply { topMargin = dp(4); bottomMargin = dp(4) } }

    /** 섹션 카드: 제목 + 설명 + 내용 */
    private fun card(title: String, subtitle: String?): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cardBg, 22f)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(12) }
        addView(text(title, 16f, onSurface, bold = true))
        subtitle?.let { addView(text(it, 12.5f, onSurfaceVar).apply { setPadding(0, dp(3), 0, 0) }) }
        addView(space(12))
    }

    /** 알약 모양 선택 칩 */
    private fun pill(label: String) = TextView(this).apply {
        text = label; textSize = 14f; gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(16), 0, dp(16), 0)
    }

    private fun styleChip(v: TextView, on: Boolean) {
        v.background = if (on) rounded(accent, 19f) else rounded(Color.TRANSPARENT, 19f, outline, 1)
        v.setTextColor(if (on) onAccent else onSurface)
    }

    private fun styleSwatch(v: View, on: Boolean) {
        (v.background as GradientDrawable).setStroke(if (on) dp(3) else dp(1), if (on) accent else (outline and 0x00FFFFFF) or (0x80 shl 24))
        v.animate().scaleX(if (on) 1.08f else 1f).scaleY(if (on) 1.08f else 1f).setDuration(120).start()
    }

    /** 세그먼트 버튼: 하나만 고르는 옵션 */
    private fun segmented(labels: List<String>, selected: () -> Int, onPick: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(trackBg, 22f)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val segs = labels.mapIndexed { i, l ->
            TextView(this).apply {
                text = l; textSize = 14f; gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setOnClickListener { onPick(i) }
            }.also { box.addView(it, LinearLayout.LayoutParams(0, dp(38), 1f)) }
        }
        refreshers += {
            val sel = selected()
            segs.forEachIndexed { i, t ->
                t.background = if (i == sel) rounded(accent, 18f) else null
                t.setTextColor(if (i == sel) onAccent else onSurfaceVar)
            }
        }
        return box
    }

    /** 켜기/끄기 줄: 제목·설명 + 스위치 (줄 어디를 눌러도 바뀜) */
    private fun toggleRow(title: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(text(title, 15f, onSurface, bold = true))
        col.addView(text(desc, 12.5f, onSurfaceVar).apply { setPadding(0, dp(2), dp(12), 0) })
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        val sw = Switch(this).apply {
            isChecked = get()
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(onAccent, outline))
            trackTintList = ColorStateList(states, intArrayOf(accent, trackBg))
            setOnCheckedChangeListener { _, on -> set(on); refreshAll(); renderPreview() }
        }
        row.addView(sw)
        row.setOnClickListener { if (sw.isEnabled) sw.toggle() }
        return row
    }

    /** 슬라이더 줄: 이름(왼쪽) + 값(오른쪽, 강조색) + 막대 */
    private fun slider(
        label: String, max: Int, value: Int, format: (Int) -> String,
        keep: ((SeekBar, TextView) -> Unit)? = null, onChange: (Int) -> Unit
    ): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, 0) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(label, 15f, onSurface, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = text(format(value), 15f, accent, bold = true)
        head.addView(v)
        col.addView(head)
        val bar = SeekBar(this).apply {
            this.max = max
            progress = value
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(trackBg)
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(4), dp(10), dp(4), dp(6))
            setOnSeekBarChangeListener(listener { p -> v.text = format(p); onChange(p) })
        }
        col.addView(bar)
        keep?.invoke(bar, v)
        return col
    }

    /** 지금 디자인·배경에서 효과가 없는 설정은 흐리게 + 못 누르게 */
    private fun dim(v: View, enabled: Boolean) {
        v.alpha = if (enabled) 1f else 0.38f
        fun walk(x: View) {
            x.isEnabled = enabled
            if (x is ViewGroup) for (i in 0 until x.childCount) walk(x.getChildAt(i))
        }
        walk(v)
    }

    private fun gradientBar(maxValue: Int, drawable: GradientDrawable) = SeekBar(this).apply {
        max = maxValue
        drawable.cornerRadius = dpf(6f)
        progressDrawable = drawable
        thumbTintList = ColorStateList.valueOf(onSurface)
        minHeight = dp(12)
        maxHeight = dp(12)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnSeekBarChangeListener(listener { onHsvChanged() })
    }

    private fun listener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }
}
