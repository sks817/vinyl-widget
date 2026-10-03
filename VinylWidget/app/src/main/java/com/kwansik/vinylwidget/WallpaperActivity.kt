package com.kwansik.vinylwidget

import android.app.Activity
import android.app.WallpaperManager
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/**
 * 위젯과 어울리는 배경화면 고르기. 위 = 고른 그림 크게(고정), 아래 = 목록(한 줄에 3개).
 * 적용하면 지금 화면 비율에 맞는 그림을 고름: 폴드8 접은 화면 / 펼친 화면 / 일반 세로 화면.
 * 그림은 assets/wallpapers: p_ = 세로 1080×2400, c_ = 폴드8 커버 1248×1972, m_ = 폴드8 펼친 화면 2448×1848, t_ = 목록용 작은 그림
 */
class WallpaperActivity : Activity() {
    private class Wall(val id: String, val name: String, val desc: String, val fold: Boolean)

    private val walls = listOf(
        Wall("forest_night", "깊은 숲 · 모닥불", "검은 숲 + 주황 불빛", true),
        Wall("canyon_dusk", "협곡 노을 · 캠핑카", "청록·주황 노을, 산길", true),
        Wall("topo_map", "등산 지형도", "올리브 등고선 + 주황 경로", true),
        Wall("vinyl_noir", "블랙 바이닐", "차콜 + 레코드판 SIDE B", true),
        Wall("milkyway", "은하수 캠핑", "은하수 + 불 켜진 텐트", true),
        Wall("mono_grain", "모노 아웃도어", "차콜 그레인 + OUTSIDE", true),
        Wall("night_camp", "밤 캠핑", "별·달·전구줄·텐트", false),
        Wall("dawn_lake", "새벽 호수", "노을빛 산과 호수", false),
        Wall("retro_lp", "레트로 LP", "70년대 무지개 + 레코드판", false),
        Wall("doodle_pastel", "병맛 낙서", "파스텔 별·하트 낙서", false),
        Wall("forest_day", "숲속 텐트", "세이지 톤 숲", false),
        Wall("y2k_checker", "Y2K 체커", "파스텔 체커 + 반짝이", false)
    )

    private lateinit var ui: SheetUi
    private lateinit var big: ImageView
    private lateinit var nameView: android.widget.TextView
    private var sel = 0
    private val cells = mutableListOf<ImageView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SheetUi(this)
        val dp = ui::dp
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(ui.sheetBg) }

        // ---- 위에 고정: 고른 그림 + 적용 버튼 ----
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(12), dp(16), dp(8)) }
        big = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = ui.rounded(ui.cardBg, 16f) }
        head.addView(big, LinearLayout.LayoutParams(dp(120), dp(240)))
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        side.addView(ui.text("배경화면", 20f, ui.onSurface, true))
        nameView = ui.text("", 15f, ui.accent, true).apply { setPadding(0, dp(4), 0, dp(8)) }
        side.addView(nameView)
        fun btn(label: String, primary: Boolean, run: () -> Unit) =
            side.addView(ui.button(label, primary, run), LinearLayout.LayoutParams(ui.MATCH, dp(40)).apply { topMargin = dp(6) })
        btn("홈·잠금 화면 모두", true) { apply(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK) }
        btn("홈 화면만", false) { apply(WallpaperManager.FLAG_SYSTEM) }
        btn("잠금 화면만", false) { apply(WallpaperManager.FLAG_LOCK) }
        btn("갤러리에 저장", false) { saveToGallery() }
        head.addView(side, LinearLayout.LayoutParams(0, ui.WRAP, 1f))
        root.addView(head)
        root.addView(ui.text("폴드는 지금 쓰는 화면(접은/펼친)에 맞는 그림이 적용돼요. 다른 화면도 바꾸려면 접거나 펼친 뒤 한 번 더 누르거나, 갤러리에 저장해 삼성 배경화면 설정에서 골라 주세요.",
            12f, ui.onSurfaceVar).apply { setPadding(dp(16), 0, dp(16), dp(8)) })

        // ---- 목록 (한 줄에 3개) ----
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), dp(16)) }
        walls.chunked(3).forEachIndexed { r, row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { i, w ->
                val idx = r * 3 + i
                val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(6), dp(4), dp(6)) }
                val img = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; setPadding(dp(3), dp(3), dp(3), dp(3))
                    setImageBitmap(asset("t_${w.id}.webp"))
                    contentDescription = w.name
                    setOnClickListener { select(idx) }
                }
                cells += img
                cell.addView(img, LinearLayout.LayoutParams(ui.MATCH, dp(190)))
                cell.addView(ui.text(w.name, 12f, ui.onSurface, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
                line.addView(cell, LinearLayout.LayoutParams(0, ui.WRAP, 1f))
            }
            body.addView(line)
        }
        root.addView(ScrollView(this).apply { addView(body); isVerticalScrollBarEnabled = false }, LinearLayout.LayoutParams(ui.MATCH, 0, 1f))
        setContentView(root)
        root.padForSystemBars()
        select(0)
    }

    private fun select(i: Int) {
        sel = i
        val w = walls[i]
        big.setImageBitmap(asset("p_${w.id}.webp", 2))
        nameView.text = w.name + (if (w.fold) " · 폴드8 전용 크기 있음" else "")
        cells.forEachIndexed { k, v -> v.background = if (k == i) ui.rounded(0, 14f, ui.accent, 3) else ui.rounded(0, 14f) }
    }

    private fun asset(name: String, sample: Int = 1): Bitmap? = try {
        assets.open("wallpapers/$name").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (e: Exception) { null }

    /** 지금 화면 비율에 맞는 그림: 넓으면 펼친 화면, 조금 넓으면 커버, 아니면 세로형 */
    private fun variant(w: Wall): String {
        val b = windowManager.currentWindowMetrics.bounds
        val ratio = b.width().toFloat() / b.height()
        val prefix = when {
            !w.fold -> "p"
            ratio > 0.9f -> "m"
            ratio > 0.56f -> "c"
            else -> "p"
        }
        return "${prefix}_${w.id}.webp"
    }

    private fun apply(flags: Int) {
        val w = walls[sel]
        Toast.makeText(this, "배경화면 적용 중…", Toast.LENGTH_SHORT).show()
        Thread {
            val ok = try {
                val bmp = asset(variant(w)) ?: throw IllegalStateException("no image")
                WallpaperManager.getInstance(this).setBitmap(bmp, null, true, flags)
                true
            } catch (t: Throwable) {
                CrashLog.record(this, "Wallpaper.apply", t); false
            }
            runOnUiThread { Toast.makeText(this, if (ok) "${w.name} 적용했어요" else "적용하지 못했어요", Toast.LENGTH_SHORT).show() }
        }.start()
    }

    /** 원본(세로형 + 폴드8 커버·펼친 화면)을 사진/VinylWidget 폴더에 저장 */
    private fun saveToGallery() {
        val w = walls[sel]
        Thread {
            val names = if (w.fold) listOf("p", "c", "m") else listOf("p")
            var saved = 0
            for (p in names) try {
                val v = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "vinylwidget_${w.id}_${when (p) { "c" -> "fold8_cover"; "m" -> "fold8_main"; else -> "phone" }}.webp")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VinylWidget")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: continue
                contentResolver.openOutputStream(uri)?.use { out -> assets.open("wallpapers/${p}_${w.id}.webp").use { it.copyTo(out) } }
                saved++
            } catch (t: Throwable) {
                CrashLog.record(this, "Wallpaper.save", t)
            }
            runOnUiThread {
                Toast.makeText(this, if (saved > 0) "사진 › VinylWidget 폴더에 ${saved}장 저장했어요" else "저장하지 못했어요", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
