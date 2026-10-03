package com.kwansik.vinylwidget

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast

/**
 * 타임테이블 꾸미기: 위 = 미리보기(고정), 탭 = 일정(사람·요일별) / 사람·메모 / 꾸미기.
 * 이름·픽업·메모는 모두 선택 입력. 비워 두면 위젯에 표시하지 않음
 */
class TimetableConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var ui: SheetUi
    private lateinit var data: TtData
    private lateinit var look: TtLook
    private lateinit var preview: ImageView
    private lateinit var list: LinearLayout
    private lateinit var personRow: View
    private lateinit var secondBox: LinearLayout
    private lateinit var showWhoCard: View
    private var person = 0
    private var day = 1
    private var pw = 390f
    private var ph = 200f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        ui = SheetUi(this)
        data = Timetable.load(this)
        look = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) TimetableWidget.look(this, widgetId) else TtLook()
        day = java.time.LocalDate.now().dayOfWeek.value
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val (w, h) = WidgetGeom.sizeDp(AppWidgetManager.getInstance(this), widgetId)
            if (w > 60f && h > 60f && !(w == 170f && h == 170f)) { pw = w; ph = h }
        }
        val dp = ui::dp
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.sheetBg) }

        // ---- 위에 고정: 미리보기 ----
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(6)) }
        head.addView(ui.text("타임테이블", 20f, ui.onSurface, true))
        preview = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        head.addView(preview, LinearLayout.LayoutParams(ui.MATCH, dp(200)).apply { topMargin = dp(6) })
        root.addView(head)

        fun section() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val secEvents = section(); val secPeople = section(); val secLook = section()

        // ---- 일정 ----
        secEvents.addView(ui.card("일정", "오전 8시 ~ 오후 8시 · 픽업·장소는 선택 입력 (비우면 표시 안 함)").also { c ->
            personRow = ui.chips(arrayOf("첫 번째", "두 번째"), { person }) { person = it; refreshList(); ui.refresh() }
            c.addView(personRow)
            c.addView(ui.chips(Timetable.DAYS, { day - 1 }) { day = it + 1; refreshList(); ui.refresh() })
            list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
            c.addView(list)
            c.addView(ui.button("+ 일정 추가") { edit(null) }, LinearLayout.LayoutParams(ui.MATCH, dp(44)).apply { topMargin = dp(10) })
            c.addView(ui.button("이 요일 일정을 다른 요일에 복사", primary = false) { copyDay() },
                LinearLayout.LayoutParams(ui.MATCH, dp(40)).apply { topMargin = dp(8) })
        })

        // ---- 사람·메모 ----
        secPeople.addView(ui.card("첫 번째", "이름·메모는 비워 두면 위젯에 나오지 않아요").also { c ->
            c.addView(field("이름 (선택)", data.names[0], 8) { data.names[0] = it; renderPreview() })
            c.addView(field("메모 (선택 · 준비물 등)", data.memos[0], 30) { data.memos[0] = it; renderPreview() })
        })
        secPeople.addView(ui.card("두 번째 사람").also { c ->
            c.addView(Switch(this).apply {
                text = "두 번째 사람 일정도 쓰기 (가족·형제 등)"; setTextColor(ui.onSurface); isChecked = data.second
                setOnCheckedChangeListener { _, on -> data.second = on; if (!on) person = 0; refreshVisibility(); ui.refresh(); refreshList() }
            })
            secondBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            secondBox.addView(field("이름 (선택)", data.names[1], 8) { data.names[1] = it; renderPreview() })
            secondBox.addView(field("메모 (선택)", data.memos[1], 30) { data.memos[1] = it; renderPreview() })
            c.addView(secondBox)
        })

        // ---- 꾸미기 ----
        secLook.addView(ui.card("배치", "자동이면 위젯 크기에 맞춰 바뀌어요 (크게 = 주간, 가로 = 오늘, 세로로 길게 = 세로)").also { c ->
            c.addView(ui.chips(TtLook.LAYOUT_NAMES, { look.layout }) { look.layout = it; ui.refresh(); renderPreview() })
        })
        showWhoCard = ui.card("보여줄 사람").also { c ->
            c.addView(ui.chips(arrayOf("첫 번째", "두 번째", "둘 다"), { look.person }) { look.person = it; ui.refresh(); renderPreview() })
        }
        secLook.addView(showWhoCard)
        secLook.addView(ui.card("바탕").also { c ->
            c.addView(ui.chips(TtLook.BG_NAMES, { look.bg }) { look.bg = it; ui.refresh(); renderPreview() })
            c.addView(ui.slider("투명도", 100, look.transparency, { "$it%" }) { look.transparency = it; renderPreview() }.first)
            c.addView(ui.slider("모서리 R", 50, look.radius, { "$it%" }) { look.radius = it; renderPreview() }.first)
        })
        secLook.addView(ui.borderCard({ look.border }, { look.borderDp }, { look.borderColor }) { st, t, col ->
            if (st != null) look.border = st; if (t != null) look.borderDp = t; if (col != null) look.borderColor = col
            renderPreview()
        })

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(16)) }
        listOf(secEvents, secPeople, secLook).forEach { body.addView(it) }
        val scroll = ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }
        root.addView(ui.tabs(arrayOf("일정", "사람·메모", "꾸미기"), listOf(secEvents, secPeople, secLook), scroll))
        root.addView(scroll, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))
        root.addView(ui.bottomBar("저장", { finish() }) { save() })
        setContentView(root)
        root.padForSystemBars()

        refreshVisibility()
        ui.refresh()
        refreshList()
    }

    private fun refreshVisibility() {
        personRow.visibility = if (data.second) View.VISIBLE else View.GONE
        secondBox.visibility = if (data.second) View.VISIBLE else View.GONE
        showWhoCard.visibility = if (data.second) View.VISIBLE else View.GONE
    }

    private fun renderPreview() {
        preview.setImageBitmap(TimetableScene(this, pw, ph, look, data).bitmap)
    }

    /** 고른 사람·요일의 일정 목록 (누르면 고치기) */
    private fun refreshList() {
        val dp = ui::dp
        list.removeAllViews()
        val evs = data.of(person, day)
        if (evs.isEmpty()) list.addView(ui.text("${Timetable.DAYS[day - 1]}요일 일정이 없어요", 13f, ui.onSurfaceVar).apply { setPadding(0, dp(6), 0, dp(4)) })
        for (e in evs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8)); background = ui.rounded(ui.trackBg, 12f)
                setOnClickListener { edit(e) }
            }
            row.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(e.color) } },
                LinearLayout.LayoutParams(dp(14), dp(14)))
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
            col.addView(ui.text("${Timetable.hm(e.start)} – ${Timetable.hm(e.end)}  ${e.name}", 15f, ui.onSurface, true))
            val picks = listOfNotNull(e.go.takeIf { it.isNotBlank() }?.let { "갈 때 $it" }, e.back.takeIf { it.isNotBlank() }?.let { "올 때 $it" })
            if (picks.isNotEmpty()) col.addView(ui.text(picks.joinToString(" · "), 12f, ui.onSurfaceVar))
            row.addView(col, LinearLayout.LayoutParams(0, ui.WRAP, 1f))
            row.addView(ui.text("고치기", 12f, ui.accent, true))
            list.addView(row, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP).apply { topMargin = dp(6) })
        }
        renderPreview()
    }

    private fun field(label: String, value: String, max: Int, onChange: (String) -> Unit): View {
        val dp = ui::dp
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        col.addView(ui.text(label, 12f, ui.onSurfaceVar))
        col.addView(EditText(this).apply {
            setText(value); textSize = 15f; setTextColor(ui.onSurface); isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(max)); background = ui.rounded(ui.trackBg, 12f); setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) { onChange(s?.toString().orEmpty()) }
            })
        }, LinearLayout.LayoutParams(ui.MATCH, ui.WRAP).apply { topMargin = dp(4) })
        return col
    }

    /** 일정 추가·고치기 창 */
    private fun edit(orig: TtEvent?) {
        val dp = ui::dp
        val evs = data.of(person, day)
        val e = orig?.copy() ?: run {
            val start = (evs.lastOrNull()?.end ?: 15 * 60).coerceIn(Timetable.H0, Timetable.H1 - 30)
            TtEvent(person, day, "", start, (start + 60).coerceAtMost(Timetable.H1), Timetable.COLORS[data.events.size % Timetable.COLORS.size])
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val name = EditText(this).apply { hint = "일정 이름 (예: 태권도, 회의)"; setText(e.name); isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(10)) }
        box.addView(name)
        val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(4)) }
        lateinit var startBtn: android.widget.Button
        lateinit var endBtn: android.widget.Button
        fun label() { startBtn.text = "시작 ${Timetable.hm(e.start)}"; endBtn.text = "끝 ${Timetable.hm(e.end)}" }
        fun pick(isStart: Boolean) {
            val m = if (isStart) e.start else e.end
            TimePickerDialog(this, { _, h, mi ->
                val v = (h * 60 + mi).coerceIn(Timetable.H0, Timetable.H1)
                if (isStart) { val len = e.end - e.start; e.start = v; if (e.end <= e.start) e.end = (v + maxOf(30, len)).coerceAtMost(Timetable.H1) }
                else e.end = v
                label()
            }, m / 60, m % 60, false).show()
        }
        startBtn = android.widget.Button(this).apply { setOnClickListener { pick(true) } }
        endBtn = android.widget.Button(this).apply { setOnClickListener { pick(false) } }
        times.addView(startBtn, LinearLayout.LayoutParams(0, ui.WRAP, 1f))
        times.addView(endBtn, LinearLayout.LayoutParams(0, ui.WRAP, 1f).apply { marginStart = dp(8) })
        label()
        box.addView(times)
        val colors = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(6)) }
        Timetable.COLORS.forEach { col ->
            View(this).also { v -> v.setOnClickListener { e.color = col; refreshDots(colors, e.color) }; colors.addView(v, LinearLayout.LayoutParams(0, dp(26), 1f).apply { marginEnd = dp(4) }) }
        }
        refreshDots(colors, e.color)
        box.addView(colors)
        val go = EditText(this).apply { hint = "갈 때 픽업 (선택 · 예: 아빠, 셔틀 · 정문)"; setText(e.go); isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(14)) }
        val back = EditText(this).apply { hint = "올 때 픽업 (선택 · 예: 엄마)"; setText(e.back); isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(14)) }
        box.addView(go); box.addView(back)
        val b = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (orig == null) "일정 추가 · ${Timetable.DAYS[day - 1]}요일" else "일정 고치기 · ${Timetable.DAYS[day - 1]}요일")
            .setView(box)
            .setPositiveButton("저장") { _, _ ->
                val n = name.text.toString().trim()
                if (n.isEmpty()) { Toast.makeText(this, "일정 이름을 넣어 주세요", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
                e.name = n; e.go = go.text.toString().trim(); e.back = back.text.toString().trim()
                if (e.end <= e.start) e.end = (e.start + 30).coerceAtMost(Timetable.H1)
                if (orig != null) data.events.remove(orig)
                data.events += e
                refreshList()
            }
            .setNegativeButton("취소", null)
        if (orig != null) b.setNeutralButton("삭제") { _, _ -> data.events.remove(orig); refreshList() }
        b.show()
    }

    private fun refreshDots(row: LinearLayout, sel: Int) {
        for (i in 0 until row.childCount) {
            val col = Timetable.COLORS[i]
            row.getChildAt(i).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(col); setStroke(ui.dp(if (col == sel) 3 else 0), Color.WHITE)
            }
        }
    }

    /** 지금 요일 일정을 고른 요일들에 복사 (그 요일의 기존 일정은 바꿔 씀) */
    private fun copyDay() {
        val src = data.of(person, day)
        if (src.isEmpty()) { Toast.makeText(this, "복사할 일정이 없어요", Toast.LENGTH_SHORT).show(); return }
        val names = Timetable.DAYS.mapIndexed { i, s -> "${s}요일" + if (i + 1 == day) " (지금)" else "" }.toTypedArray()
        val checked = BooleanArray(7)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${Timetable.DAYS[day - 1]}요일 일정을 어디에 복사할까요?")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("복사") { _, _ ->
                for (i in 0 until 7) if (checked[i] && i + 1 != day) {
                    data.events.removeAll { it.person == person && it.day == i + 1 }
                    src.forEach { data.events += it.copy(day = i + 1) }
                }
                refreshList()
            }
            .setNegativeButton("취소", null).show()
    }

    private fun save() {
        Timetable.save(this, data)
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) TimetableWidget.saveLook(this, widgetId, look)
        TimetableWidget.renderAll(this)
        TimetableWidget.schedule(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
