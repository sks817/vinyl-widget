package com.kwansik.vinylwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 사진 스티커 꾸미기: 위 = 2×2·1×1 미리보기, 아래 = 사진 고르기 / 프레임 / 문구(글자 수 제한) / 종이 색 / 기울기·모서리 R.
 * 스티커 고르기 화면과 같은 어두운 시트 + 배경화면 강조색
 */
class PhotoConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var o: PhotoWidget.Opts
    private var photo: Bitmap? = null
    private var newPhoto = false

    private val sheetBg by lazy { getColor(android.R.color.system_neutral1_900) }
    private val cardBg by lazy { getColor(android.R.color.system_neutral1_800) }
    private val trackBg by lazy { getColor(android.R.color.system_neutral1_700) }
    private val onSurface by lazy { getColor(android.R.color.system_neutral1_50) }
    private val onSurfaceVar by lazy { getColor(android.R.color.system_neutral2_200) }
    private val outline by lazy { getColor(android.R.color.system_neutral2_500) }
    private val accent by lazy { getColor(android.R.color.system_accent1_200) }
    private val onAccent by lazy { getColor(android.R.color.system_accent1_800) }

    private lateinit var prevBig: ImageView
    private lateinit var prevSmall: ImageView
    private lateinit var fieldsBox: LinearLayout
    private val frameChips = mutableListOf<TextView>()
    private val paperCells = mutableListOf<View>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        o = PhotoWidget.load(this, widgetId)
        photo = PhotoWidget.loadPhoto(this, widgetId)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(sheetBg) }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16)) }

        body.addView(text("사진 스티커", 22f, onSurface, true))
        body.addView(text("병맛 프레임에 사진을 붙여요 · 2×2, 줄이면 1×1", 13f, onSurfaceVar).apply { setPadding(0, dp(2), 0, dp(12)) })

        // 미리보기: 2×2 + 1×1
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }
        prevBig = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        prevSmall = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        top.addView(labeled(prevBig, "2×2"), LinearLayout.LayoutParams(dp(190), WRAP))
        top.addView(labeled(prevSmall, "1×1"), LinearLayout.LayoutParams(dp(96), WRAP).apply { marginStart = dp(18) })
        (prevBig.layoutParams as LinearLayout.LayoutParams).height = dp(190)
        (prevSmall.layoutParams as LinearLayout.LayoutParams).height = dp(90)
        body.addView(top)

        body.addView(card("사진").also { c ->
            c.addView(text("갤러리에서 사진 고르기", 15f, onAccent, true).apply {
                gravity = Gravity.CENTER; background = rounded(accent, 22f)
                setOnClickListener { pick() }
            }, LinearLayout.LayoutParams(MATCH, dp(46)).apply { topMargin = dp(10) })
        })

        body.addView(card("프레임").also { c ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            PhotoFrames.NAMES.forEachIndexed { i, n ->
                val chip = text(n, 14f, onSurface, true).apply {
                    gravity = Gravity.CENTER; setOnClickListener { o.frame = i; buildFields(); refresh() }
                }
                frameChips += chip
                row.addView(chip, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i > 0) marginStart = dp(8) })
            }
            c.addView(row)
        })

        body.addView(card("문구").also { c ->
            c.addView(text("칸마다 글자 수 제한 안에서 직접 써 주세요. 비워 두면 예시 문구가 들어가요", 12f, onSurfaceVar).apply { setPadding(0, dp(2), 0, dp(4)) })
            fieldsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(fieldsBox)
        })

        body.addView(card("종이 색").also { c ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            PhotoFrames.PAPERS.forEachIndexed { i, col ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; tag = col
                    setOnClickListener { o.paper = col; refresh() }
                }
                cell.addView(View(this), LinearLayout.LayoutParams(dp(34), dp(34)))
                cell.addView(text(PhotoFrames.PAPER_NAMES[i], 11f, onSurfaceVar).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
                paperCells += cell
                row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f))
            }
            c.addView(row)
        })

        body.addView(card("모양").also { c ->
            c.addView(slider("기울기", 30, o.tilt + 15, { v -> val d = v - 15; if (d > 0) "+$d°" else "$d°" }) { o.tilt = it - 15; renderPreview() })
            c.addView(slider("모서리 R", 50, o.radius, { "$it%" }) { o.radius = it; renderPreview() })
        })

        root.addView(ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(MATCH, 0, 1f))
        root.addView(bottomBar())
        setContentView(root)
        root.padForSystemBars()

        buildFields()
        refresh()
    }

    /** 지금 프레임의 문구 칸들: [이름] [입력칸] [글자 수] */
    private fun buildFields() {
        fieldsBox.removeAllViews()
        val f = o.frame
        PhotoFrames.FIELDS[f].forEachIndexed { i, fd ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
            row.addView(text(fd.label, 13f, onSurfaceVar), LinearLayout.LayoutParams(dp(76), WRAP))
            val count = text("", 12f, outline).apply { gravity = Gravity.END }
            val edit = EditText(this).apply {
                textSize = 15f; setTextColor(onSurface); setHintTextColor(outline); hint = fd.def; isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(fd.max))
                background = rounded(trackBg, 12f); setPadding(dp(12), dp(8), dp(12), dp(8))
                setText(o.texts[f][i])
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        o.texts[f][i] = s?.toString().orEmpty()
                        count.text = "${o.texts[f][i].length}/${fd.max}"
                        renderPreview()
                    }
                })
            }
            count.text = "${o.texts[f][i].length}/${fd.max}"
            row.addView(edit, LinearLayout.LayoutParams(0, WRAP, 1f))
            row.addView(count, LinearLayout.LayoutParams(dp(40), WRAP))
            fieldsBox.addView(row)
        }
    }

    private fun refresh() {
        frameChips.forEachIndexed { i, chip ->
            val on = i == o.frame
            chip.background = if (on) rounded(accent, 20f) else rounded(Color.TRANSPARENT, 20f, outline, 1)
            chip.setTextColor(if (on) onAccent else onSurface)
        }
        paperCells.forEach { cell ->
            val col = cell.tag as Int
            val on = col == o.paper
            cell.getChildAt(0).background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(PhotoFrames.paperOf(o.frame, col))
                setStroke(dp(if (on) 3 else 1), if (on) accent else outline)
            }
        }
        renderPreview()
    }

    private fun renderPreview() {
        prevBig.setImageBitmap(PhotoWidget.image(this, o, photo, 170f, 170f, widgetId))
        prevSmall.setImageBitmap(PhotoWidget.image(this, o, photo, 80f, 80f, widgetId))
    }

    // ---- 사진 고르기 (Android 13+ 사진 선택기, 그 아래는 파일 고르기) ----
    private fun pick() {
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
                else Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try {
            @Suppress("DEPRECATION") startActivityForResult(i, REQ_PICK)
        } catch (e: ActivityNotFoundException) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), REQ_PICK)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, "사진을 고를 앱이 없어요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || uri == null) return
        Thread {
            val b = decode(uri)
            runOnUiThread {
                if (b == null) Toast.makeText(this, "사진을 읽지 못했어요", Toast.LENGTH_SHORT).show()
                else { photo = b; newPhoto = true; renderPreview() }
            }
        }.start()
    }

    /** 사진 읽기: 찍은 방향대로 돌리고, 긴 변 1080px 이하로 줄임 */
    private fun decode(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { d, info, _ ->
            val w = info.size.width; val h = info.size.height
            val sc = min(1f, 1080f / max(w, h))
            if (sc < 1f) d.setTargetSize((w * sc).toInt().coerceAtLeast(1), (h * sc).toInt().coerceAtLeast(1))
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (t: Throwable) {
        null
    }

    private fun save() {
        if (newPhoto) photo?.let { b ->
            try { PhotoWidget.photoFile(this, widgetId).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            catch (t: Throwable) { CrashLog.record(this, "PhotoConfig.save", t) }
        }
        PhotoWidget.save(this, widgetId, o)
        PhotoWidget.render(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    // ---- 화면 조각 ----
    private fun labeled(v: View, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(v, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(text(label, 12f, onSurfaceVar, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
    }

    private fun slider(label: String, max: Int, value: Int, format: (Int) -> String, onChange: (Int) -> Unit): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0) }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text(label, 15f, onSurface, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val v = text(format(value), 15f, accent, true)
        head.addView(v)
        col.addView(head)
        col.addView(SeekBar(this).apply {
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
        })
        return col
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

    companion object { private const val REQ_PICK = 71 }
}
