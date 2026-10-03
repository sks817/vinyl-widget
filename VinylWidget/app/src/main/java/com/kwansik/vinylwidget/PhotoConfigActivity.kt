package com.kwansik.vinylwidget

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Toast
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 사진 스티커 꾸미기: 위 = 2×2·1×1 미리보기(끌어서 위치, 벌려서 확대), 아래 = 사진 / 프레임 / 다이컷 모양 /
 * 문구(글자 수 제한) / 종이 색 / 테두리 / 기울기·모서리 R
 */
class PhotoConfigActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var o: PhotoWidget.Opts
    private lateinit var ui: SheetUi
    private val photos = MutableList<Bitmap?>(PhotoWidget.SLOTS) { null }
    private val changed = mutableSetOf<Int>()
    private var slot = 0

    private lateinit var prevBig: ImageView
    private lateinit var prevSmall: ImageView
    private lateinit var fieldsBox: LinearLayout
    private lateinit var slotRow: View
    private lateinit var shapeCard: View
    private lateinit var paperCard: View
    private lateinit var zoomBar: SeekBar
    private lateinit var xBar: SeekBar
    private lateinit var yBar: SeekBar
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        o = PhotoWidget.load(this, widgetId)
        PhotoWidget.loadPhotos(this, widgetId).forEachIndexed { i, b -> photos[i] = b }
        ui = SheetUi(this)
        val dp = ui::dp

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.sheetBg) }

        // ---- 위에 고정: 제목 + 미리보기 (설정을 바꾸면 바로 보임) ----
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(6)) }
        head.addView(ui.text("사진 스티커", 20f, ui.onSurface, true))
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setPadding(0, dp(4), 0, 0) }
        prevBig = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        prevSmall = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        top.addView(labeled(prevBig, "2×2 · 끌면 위치, 벌리면 확대", dp(170)), LinearLayout.LayoutParams(dp(200), ui.WRAP))
        top.addView(labeled(prevSmall, "1×1", dp(80)), LinearLayout.LayoutParams(dp(90), ui.WRAP).apply { marginStart = dp(12) })
        head.addView(top)
        attachGestures(prevBig)

        // ---- 탭별 설정 ----
        fun section() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val secPhoto = section(); val secFrame = section(); val secLook = section()

        // 사진: 칸 고르기(네컷) + 고르기 버튼 + 확대·위치
        secPhoto.addView(ui.card("사진").also { c ->
            slotRow = ui.chips(Array(PhotoWidget.SLOTS) { "사진 ${it + 1}" }, { slot }) { slot = it; syncCrop(); ui.refresh() }
            c.addView(slotRow)
            c.addView(ui.button("갤러리에서 사진 고르기") { pick() }, LinearLayout.LayoutParams(ui.MATCH, dp(46)).apply { topMargin = dp(10) })
            ui.slider("확대", 300, 0, { "${100 + it}%" }) { if (!syncing) { o.crops[slot].zoom = 1f + it / 100f; renderPreview() } }.let { (v, b) -> zoomBar = b; c.addView(v) }
            ui.slider("좌우 위치", 200, 100, { pos(it, "왼쪽", "오른쪽") }) { if (!syncing) { o.crops[slot].ox = (it - 100) / 100f; renderPreview() } }.let { (v, b) -> xBar = b; c.addView(v) }
            ui.slider("위아래 위치", 200, 100, { pos(it, "위", "아래") }) { if (!syncing) { o.crops[slot].oy = (it - 100) / 100f; renderPreview() } }.let { (v, b) -> yBar = b; c.addView(v) }
            c.addView(ui.button("사진 위치 원래대로", primary = false) {
                o.crops[slot].apply { zoom = 1f; ox = 0f; oy = 0f }; syncCrop(); renderPreview()
            }, LinearLayout.LayoutParams(ui.MATCH, dp(40)).apply { topMargin = dp(8) })
        })

        // 프레임 · 다이컷 모양 · 문구
        secFrame.addView(ui.card("프레임").also { c ->
            c.addView(ui.chips(PhotoFrames.NAMES, { o.frame }) { o.frame = it; if (slot >= PhotoFrames.slots(it)) slot = 0; syncCrop(); buildFields(); ui.refresh(); renderPreview() })
        })
        shapeCard = ui.card("다이컷 모양").also { c ->
            c.addView(ui.chips(PhotoFrames.SHAPES, { o.shape }) { o.shape = it; ui.refresh(); renderPreview() })
        }
        secFrame.addView(shapeCard)
        secFrame.addView(ui.card("문구", "칸마다 글자 수 제한 안에서 직접 써 주세요. 지우면 그 칸은 비어 있어요 (처음엔 예시 문구)").also { c ->
            fieldsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(fieldsBox)
        })

        // 꾸미기: 테두리 · 기울기·모서리 · 종이 색
        secLook.addView(ui.borderCard({ o.borderStyle() }, { o.borderDp }, { o.borderColor }) { s, t, col ->
            if (s != null) o.border = s; if (t != null) o.borderDp = t; if (col != null) o.borderColor = col
            renderPreview()
        })
        secLook.addView(ui.card("모양").also { c ->
            c.addView(ui.slider("기울기", 30, o.tilt + 15, { v -> val d = v - 15; if (d > 0) "+$d°" else "$d°" }) { o.tilt = it - 15; renderPreview() }.first)
            c.addView(ui.slider("모서리 R", 50, o.radius, { "$it%" }) { o.radius = it; renderPreview() }.first)
        })
        paperCard = ui.card("종이 색").also { c ->
            c.addView(ui.swatches(PhotoFrames.PAPERS, PhotoFrames.PAPER_NAMES, { PhotoFrames.paperOf(o.frame, it) }, { o.paper }) { o.paper = it; ui.refresh(); renderPreview() })
        }
        secLook.addView(paperCard)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(16)) }
        listOf(secPhoto, secFrame, secLook).forEach { body.addView(it) }
        val scroll = ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }

        root.addView(head)
        root.addView(ui.tabs(arrayOf("사진", "프레임·문구", "테두리·꾸미기"), listOf(secPhoto, secFrame, secLook), scroll))
        root.addView(scroll, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))
        root.addView(ui.bottomBar("붙이기", { finish() }) { save() })
        setContentView(root)
        root.padForSystemBars()

        ui.refresh()   // 칩·색 표시 먼저
        syncCrop()
        buildFields()
        refreshVisibility()
        renderPreview()
    }

    private fun pos(v: Int, lo: String, hi: String) = when {
        v < 100 -> "$lo ${100 - v}"
        v > 100 -> "$hi ${v - 100}"
        else -> "가운데"
    }

    /** 지금 사진 칸의 확대·위치를 슬라이더에 */
    private fun syncCrop() {
        if (!::zoomBar.isInitialized) return
        syncing = true
        val cr = o.crops[slot]
        zoomBar.progress = ((cr.zoom - 1f) * 100).roundToInt().coerceIn(0, 300)
        xBar.progress = (cr.ox * 100 + 100).roundToInt().coerceIn(0, 200)
        yBar.progress = (cr.oy * 100 + 100).roundToInt().coerceIn(0, 200)
        syncing = false
    }

    private fun refreshVisibility() {
        slotRow.visibility = if (PhotoFrames.slots(o.frame) > 1) View.VISIBLE else View.GONE
        shapeCard.visibility = if (o.frame == PhotoFrames.DIECUT) View.VISIBLE else View.GONE
        paperCard.visibility = if (PhotoFrames.hasPaper(o.frame)) View.VISIBLE else View.GONE
    }

    /** 미리보기를 끌면 위치, 벌리면 확대 (지금 고른 사진 칸) */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures(v: ImageView) {
        val scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val cr = o.crops[slot]; cr.zoom = (cr.zoom * d.scaleFactor).coerceIn(1f, 4f); syncCrop(); renderPreview(); return true
            }
        })
        var lx = 0f; var ly = 0f
        v.setOnTouchListener { view, e ->
            scale.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lx = e.x; ly = e.y; view.parent.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> if (!scale.isInProgress && e.pointerCount == 1) {
                    val cr = o.crops[slot]; val w = max(1, view.width).toFloat()
                    cr.ox = (cr.ox - (e.x - lx) / (w * 0.35f)).coerceIn(-1f, 1f)
                    cr.oy = (cr.oy - (e.y - ly) / (w * 0.35f)).coerceIn(-1f, 1f)
                    lx = e.x; ly = e.y; syncCrop(); renderPreview()
                }
                MotionEvent.ACTION_POINTER_UP -> { val i = if (e.actionIndex == 0) 1 else 0; lx = e.getX(i); ly = e.getY(i) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }

    /** 지금 프레임의 문구 칸들: [이름] [입력칸] [글자 수]. 날짜 칸은 눌러서 달력으로 */
    private fun buildFields() {
        refreshVisibility()
        fieldsBox.removeAllViews()
        val f = o.frame
        val dp = ui::dp
        if (PhotoFrames.FIELDS[f].isEmpty()) {
            fieldsBox.addView(ui.text("이 프레임은 문구 없이 사진만 들어가요", 13f, ui.onSurfaceVar).apply { setPadding(0, dp(8), 0, 0) })
            return
        }
        PhotoFrames.FIELDS[f].forEachIndexed { i, fd ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
            row.addView(ui.text(fd.label, 13f, ui.onSurfaceVar), LinearLayout.LayoutParams(dp(76), ui.WRAP))
            val count = ui.text("", 12f, ui.outline).apply { gravity = Gravity.END }
            val edit = EditText(this).apply {
                textSize = 15f; setTextColor(ui.onSurface); setHintTextColor(ui.outline); hint = "예: " + PhotoFrames.fill(fd.def); isSingleLine = true
                filters = arrayOf(InputFilter.LengthFilter(fd.max))
                background = ui.rounded(ui.trackBg, 12f); setPadding(dp(12), dp(8), dp(12), dp(8))
                setText(o.texts[f][i])
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        o.texts[f][i] = s?.toString().orEmpty()
                        count.text = if (fd.date) "" else "${o.texts[f][i].length}/${fd.max}"
                        renderPreview()
                    }
                })
                if (fd.date) {
                    isFocusable = false; isCursorVisible = false
                    setOnClickListener { pickDate(this) }
                }
            }
            count.text = if (fd.date) "" else "${o.texts[f][i].length}/${fd.max}"
            row.addView(edit, LinearLayout.LayoutParams(0, ui.WRAP, 1f))
            row.addView(count, LinearLayout.LayoutParams(dp(40), ui.WRAP))
            fieldsBox.addView(row)
        }
    }

    private fun pickDate(edit: EditText) {
        val cur = try {
            val p = edit.text.toString().split('.', '-', '/').map { it.trim().toInt() }; LocalDate.of(p[0], p[1], p[2])
        } catch (e: Exception) { LocalDate.now() }
        DatePickerDialog(this, { _, y, m, d -> edit.setText("%04d.%02d.%02d".format(y, m + 1, d)) }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
    }

    private fun renderPreview() {
        prevBig.setImageBitmap(PhotoWidget.image(this, o, photos, 170f, 170f, widgetId))
        prevSmall.setImageBitmap(PhotoWidget.image(this, o, photos, 80f, 80f, widgetId))
    }

    // ---- 사진 고르기 (Android 13+ 사진 선택기, 그 아래는 파일 고르기). 네컷은 지금 칸부터 여러 장 ----
    private fun pick() {
        val n = PhotoFrames.slots(o.frame) - slot
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*").also {
            if (n > 1) it.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, n)
        } else Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE, n > 1)
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
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return
        val uris = ArrayList<Uri>()
        data.clipData?.let { cd -> for (k in 0 until cd.itemCount) uris += cd.getItemAt(k).uri }
        if (uris.isEmpty()) data.data?.let { uris += it }
        if (uris.isEmpty()) return
        val start = slot
        val n = PhotoFrames.slots(o.frame)
        Thread {
            val got = uris.take(n - start).map { decode(it) }
            runOnUiThread {
                if (got.all { it == null }) { Toast.makeText(this, "사진을 읽지 못했어요", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                got.forEachIndexed { k, b ->
                    if (b != null) { photos[start + k] = b; changed += start + k; o.crops[start + k].apply { zoom = 1f; ox = 0f; oy = 0f } }
                }
                syncCrop(); renderPreview()
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
        for (s in changed) photos[s]?.let { b ->
            try { PhotoWidget.photoFile(this, widgetId, s).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
            catch (t: Throwable) { CrashLog.record(this, "PhotoConfig.save", t) }
        }
        PhotoWidget.save(this, widgetId, o)
        PhotoWidget.render(this, AppWidgetManager.getInstance(this), widgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun labeled(v: View, label: String, h: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(v, LinearLayout.LayoutParams(ui.MATCH, h))
        addView(ui.text(label, 12f, ui.onSurfaceVar, true).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, 0) })
    }

    companion object { private const val REQ_PICK = 71 }
}
