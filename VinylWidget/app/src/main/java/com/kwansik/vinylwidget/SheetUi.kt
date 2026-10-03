package com.kwansik.vinylwidget

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 스티커·사진 스티커 꾸미기 화면이 같이 쓰는 조각들: 어두운 시트 + 배경화면 강조색.
 * refresh()를 부르면 칩·색 고르기의 선택 표시가 다시 그려짐
 */
class SheetUi(private val a: Activity) {
    val sheetBg = a.getColor(android.R.color.system_neutral1_900)
    val cardBg = a.getColor(android.R.color.system_neutral1_800)
    val trackBg = a.getColor(android.R.color.system_neutral1_700)
    val onSurface = a.getColor(android.R.color.system_neutral1_50)
    val onSurfaceVar = a.getColor(android.R.color.system_neutral2_200)
    val outline = a.getColor(android.R.color.system_neutral2_500)
    val accent = a.getColor(android.R.color.system_accent1_200)
    val onAccent = a.getColor(android.R.color.system_accent1_800)

    val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val refreshers = mutableListOf<() -> Unit>()

    fun dp(v: Int) = (v * a.resources.displayMetrics.density).roundToInt()
    fun refresh() = refreshers.forEach { it() }

    fun text(s: String, size: Float, c: Int, bold: Boolean = false) = TextView(a).apply {
        text = s; textSize = size; setTextColor(c)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.BOLD) else Typeface.DEFAULT
    }

    fun rounded(fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(fill); cornerRadius = radiusDp * a.resources.displayMetrics.density
        if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    fun card(title: String, sub: String? = null) = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(cardBg, 24f)
        setPadding(dp(14), dp(14), dp(14), dp(12))
        addView(text(title, 16f, onSurface, true))
        if (sub != null) addView(text(sub, 12f, onSurfaceVar).apply { setPadding(0, dp(2), 0, 0) })
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(12) }
    }

    fun button(label: String, primary: Boolean = true, onClick: () -> Unit) = text(label, 15f, if (primary) onAccent else onSurface, true).apply {
        gravity = Gravity.CENTER
        background = if (primary) rounded(accent, 22f) else rounded(Color.TRANSPARENT, 22f, outline, 1)
        setOnClickListener { onClick() }
    }

    /** 슬라이더. 돌려준 SeekBar로 값을 코드에서 바꿀 수 있음 */
    fun slider(label: String, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit): Pair<View, SeekBar> {
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(label, 15f, onSurface, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = text(format(value), 15f, accent, true)
        head.addView(v)
        col.addView(head)
        val bar = SeekBar(a).apply {
            this.max = max; progress = value
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(trackBg)
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(4), dp(10), dp(4), dp(6))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { v.text = format(p); onChange(p) }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        col.addView(bar)
        return col to bar
    }

    /** 가로로 밀리는 칩 줄 (하나만 고름) */
    fun chips(names: Array<String>, selected: () -> Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, dp(2)) }
        val views = names.mapIndexed { i, n ->
            text(n, 14f, onSurface, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener { onPick(i) }
                row.addView(this, LinearLayout.LayoutParams(WRAP, dp(38)).apply { if (i > 0) marginStart = dp(8) })
            }
        }
        refreshers += {
            views.forEachIndexed { i, v ->
                val on = i == selected()
                v.background = if (on) rounded(accent, 19f) else rounded(Color.TRANSPARENT, 19f, outline, 1)
                v.setTextColor(if (on) onAccent else onSurface)
            }
        }
        return HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    /** 색 동그라미 줄. shown(c) = 동그라미에 실제로 칠할 색 (0 '기본'을 실제 색으로) */
    fun swatches(colors: IntArray, names: Array<String>?, shown: (Int) -> Int, selected: () -> Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        val dots = colors.mapIndexed { i, col ->
            val cell = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setOnClickListener { onPick(col) }
            }
            val dot = View(a)
            cell.addView(dot, LinearLayout.LayoutParams(dp(32), dp(32)))
            if (names != null) cell.addView(text(names[i], 11f, onSurfaceVar).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
            row.addView(cell, LinearLayout.LayoutParams(dp(50), WRAP))
            col to dot
        }
        refreshers += {
            dots.forEach { (col, dot) ->
                val on = col == selected()
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(shown(col))
                    setStroke(dp(if (on) 3 else 1), if (on) accent else outline)
                }
            }
        }
        return HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    /** 테두리 카드: 스타일 · 두께(0~12dp) · 색(색을 고르는 스타일만) */
    fun borderCard(style: () -> Int, thick: () -> Int, color: () -> Int, set: (Int?, Int?, Int?) -> Unit): View {
        val c = card("테두리", "두꺼울수록 진짜 스티커처럼 · 두께 0이면 테두리 없음")
        c.addView(chips(BorderFx.NAMES, style) { set(it, null, null); refresh() })
        c.addView(slider("두께", 12, thick(), { "${it}dp" }) { set(null, it, null) }.first)
        val colors = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        colors.addView(text("테두리 색", 13f, onSurfaceVar, true).apply { setPadding(0, dp(8), 0, 0) })
        colors.addView(swatches(BorderFx.COLORS, null, { BorderFx.colorOf(style(), it) }, color) { set(null, null, it); refresh() })
        c.addView(colors)
        refreshers += { colors.visibility = if (BorderFx.usesColor(style())) View.VISIBLE else View.GONE }
        return c
    }

    fun bottomBar(okLabel: String, onCancel: () -> Unit, onOk: () -> Unit): View {
        val bar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(10), dp(16), dp(14)); setBackgroundColor(sheetBg) }
        val cancel = text("취소", 16f, onSurface).apply {
            gravity = Gravity.CENTER; background = rounded(Color.TRANSPARENT, 26f, outline, 1); setOnClickListener { onCancel() }
        }
        val ok = text(okLabel, 16f, onAccent, true).apply {
            gravity = Gravity.CENTER; background = rounded(accent, 26f); setOnClickListener { onOk() }
        }
        bar.addView(cancel, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(10) })
        bar.addView(ok, LinearLayout.LayoutParams(0, dp(52), 2f))
        return bar
    }
}
