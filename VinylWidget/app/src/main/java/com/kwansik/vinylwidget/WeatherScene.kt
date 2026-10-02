package com.kwansik.vinylwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 날씨·날짜 위젯을 '위젯 실제 크기'로 그림.
 * 레코드 위젯과 같은 뼈대(WidgetGeom)를 써서:
 *   - 그림(재킷·텐트·원)의 크기 = 레코드판 지름 D
 *   - 아래 날씨 줄 = 재생 버튼 줄과 같은 높이, 같은 3칸 간격
 */
class WeatherScene(
    private val ctx: Context, wDp: Float, hDp: Float,
    private val fg: Int, private val sub: Int = fg, private val accent: Int = fg,
    private val shadow: Int = 0
) {
    /** 바탕 없이 배경화면 위에 쓰는 글자(fg/sub 색)에만 옅은 그림자 */
    private fun Paint.shade(): Paint {
        if (shadow != 0 && (color == fg || color == sub)) setShadowLayer(textSize * 0.14f, 0f, textSize * 0.03f, shadow)
        return this
    }


    private val k: Float          // dp당 픽셀 (그림이 너무 커지지 않게 최대 720px로 제한)
    val bitmap: Bitmap
    private val c: Canvas
    private val W: Float
    private val H: Float

    init {
        val dens = WidgetGeom.density(ctx)
        val maxSide = max(wDp, hDp) * dens
        k = dens * min(1f, 720f / maxSide)
        bitmap = Bitmap.createBitmap((wDp * k).toInt().coerceAtLeast(1), (hDp * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        c = Canvas(bitmap)
        W = bitmap.width.toFloat(); H = bitmap.height.toFloat()
    }

    private val pad get() = WidgetGeom.PAD * k
    private val row get() = WidgetGeom.ROW * k
    private val areaL get() = pad
    private val areaT get() = pad
    private val areaW get() = W - pad * 2
    private val areaH get() = H - pad * 2 - row
    private val D get() = min(areaH, areaW / WidgetGeom.ARM)
    private val square: RectF get() {
        val cx = areaL + areaW / 2; val cy = areaT + areaH / 2
        return RectF(cx - D / 2, cy - D / 2, cx + D / 2, cy + D / 2)
    }

    private val SERIF = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val SANS = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val SANS_M = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val CREAM = 0xFFFBF1DC.toInt()
    private val CREAM_SUB = 0xFFD9CFC0.toInt()
    private val BROWN = 0xFF5A3214.toInt()
    private val BROWN_SUB = 0xFF6A3E1A.toInt()

    private enum class A { L, C, R }

    private fun text(s: String, x: Float, yTop: Float, size: Float, tf: Typeface, col: Int, a: A = A.L) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = tf; color = col
            textAlign = when (a) { A.L -> Paint.Align.LEFT; A.C -> Paint.Align.CENTER; A.R -> Paint.Align.RIGHT }
        }.shade()
        c.drawText(s, x, yTop - p.fontMetrics.ascent, p)
    }

    private val SANS_R = Typeface.create("sans-serif", Typeface.NORMAL)

    private fun textMid(s: String, x: Float, yMid: Float, size: Float, tf: Typeface, col: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = Paint.Align.CENTER }.shade()
        val fm = p.fontMetrics
        c.drawText(s, x, yMid - (fm.ascent + fm.descent) / 2f, p)
    }

    private fun asset(res: Int, dst: RectF) {
        val b = Assets.get(ctx, res) ?: return
        c.drawBitmap(b, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    /** 원본 비율을 지키며 상자 안에 맞춤 (아래 가운데 정렬) */
    private fun fitBottom(res: Int, box: RectF): RectF {
        val b = Assets.get(ctx, res) ?: return box
        val r = b.width.toFloat() / b.height
        var w = box.width(); var h = w / r
        if (h > box.height()) { h = box.height(); w = h * r }
        return RectF(box.centerX() - w / 2, box.bottom - h, box.centerX() + w / 2, box.bottom)
    }

    /** 아래 줄: 재생 버튼 3개와 같은 위치에 [기온] [날씨] [최저/최고] */
    private fun bottomRow(d: WeatherData) {
        val y = H - pad - row / 2
        val xs = FloatArray(3) { pad + (W - pad * 2) * (it + 0.5f) / 3f }
        val size = 13f * k
        val msg = d.message()
        if (msg != null) { textMid(msg, W / 2, y, size, SANS, fg); return }
        // 아이콘 + 기온
        val t = d.tempText()
        val p = Paint().apply { textSize = size; typeface = SANS }
        val tw = p.measureText(t); val ic = size * 1.15f; val gap = size * 0.3f
        val start = xs[0] - (ic + gap + tw) / 2
        ctx.getDrawable(d.icon())?.mutate()?.let {
            it.setTint(accent); it.setBounds(start.toInt(), (y - ic / 2).toInt(), (start + ic).toInt(), (y + ic / 2).toInt()); it.draw(c)
        }
        // 기온은 진하게, 날씨는 보통, 최저/최고는 한 단계 흐리게 → 정보 위계가 보이게
        textMid(t, start + ic + gap + tw / 2, y, size, SANS, fg)
        textMid(d.cond(), xs[1], y, size, SANS_M, fg)
        textMid(d.rangeText().replace(" ", ""), xs[2], y, size, SANS_R, sub)
    }

    fun draw(design: Int, d: WeatherData) {
        val today = LocalDate.now()
        val md = "${today.monthValue}.${today.dayOfMonth}"
        val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val sq = square
        val night = d.isNight()
        val kind = d.skyKind()
        val lit = night || kind == "rain"                      // 밤이거나 비 오는 날엔 텐트 불을 켬
        val darkText = !night && kind != "rain"                 // 밝은 낮 하늘엔 진한 글자

        when (design) {
            1 -> { // LP 재킷: 날씨·시간에 따라 그림이 바뀜 (맑음·구름·비·눈 × 낮·밤)
                c.save()
                c.clipPath(android.graphics.Path().apply { addRoundRect(sq, D * 0.11f, D * 0.11f, android.graphics.Path.Direction.CW) })
                asset(Scenes.jacket(kind, night), sq)
                c.restore()
                val main = if (darkText) 0xFF23262E.toInt() else CREAM
                val sub = if (darkText) 0xFF3E434D.toInt() else CREAM_SUB
                text(md, sq.left + D * 0.08f, sq.top + D * 0.07f, D * 0.30f, SERIF, main)
                text(wk, sq.left + D * 0.09f, sq.top + D * 0.41f, D * 0.085f, SANS_M, sub)
                bottomRow(d)
            }
            2 -> { // 불 켜진 텐트: 날짜를 텐트 천 위에
                val box = RectF(areaL, sq.top, areaL + areaW, sq.bottom)
                val tent = if (lit) R.drawable.w_tent_glow else R.drawable.w_tent_day   // 낮엔 불 꺼진 텐트
                val r = fitBottom(tent, box)
                asset(tent, r)
                text(wk, r.centerX(), r.top + r.height() * 0.36f, r.width() * 0.055f, SANS_M, BROWN_SUB, A.C)
                text(md, r.centerX(), r.top + r.height() * 0.45f, r.width() * 0.19f, SERIF, BROWN, A.C)
                bottomRow(d)
            }
            3 -> { // 하늘 원: 재킷과 같은 규칙으로 날씨·시간에 따라 바뀜
                asset(Scenes.circle(kind, night), sq)
                val main = if (darkText) 0xFF23262E.toInt() else CREAM
                val sub = if (darkText) 0xFF3E434D.toInt() else CREAM_SUB
                text(md, sq.centerX(), sq.top + D * 0.07f, D * 0.27f, SERIF, main, A.C)
                text(wk, sq.centerX(), sq.top + D * 0.35f, D * 0.07f, SANS_M, sub, A.C)
                bottomRow(d)
            }
            4 -> { // 큰 날짜 + 작은 텐트: 도형 없이 글자 중심 (레코드판과 경쟁하지 않음)
                text(md, areaL + areaW * 0.02f, sq.top, D * 0.42f, SERIF, fg)
                text(wk, areaL + areaW * 0.04f, sq.top + D * 0.50f, D * 0.10f, SANS_M, sub)
                val box = RectF(areaL + areaW * 0.40f, sq.top + D * 0.45f, areaL + areaW, sq.bottom)
                val icon = if (lit) R.drawable.w_tent_icon else R.drawable.w_tent_icon_day
                asset(icon, fitBottom(icon, box))
                bottomRow(d)
            }
            else -> { // 기존 디자인(미니멀·다이얼·달력)은 정사각 칸 가운데에
                val legacy = when (design) { 5 -> 1; 6 -> 3; else -> 5 }
                val s = (min(W, H) - pad * 2).toInt().coerceAtLeast(10)
                val p = WeatherPainter(ctx, s, fg)
                p.draw(legacy, d)
                c.drawBitmap(p.bitmap, (W - s) / 2, (H - s) / 2, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }
}

/** 날씨(맑음·구름·비·눈) × 낮/밤 → 그림 파일 */
object Scenes {
    private fun key(kind: String, night: Boolean) = kind + if (night) "_n" else "_d"
    fun jacket(kind: String, night: Boolean) = when (key(kind, night)) {
        "cloudy_d" -> R.drawable.w_sc_cloudy_day; "rain_d" -> R.drawable.w_sc_rain_day; "snow_d" -> R.drawable.w_sc_snow_day
        "clear_n" -> R.drawable.w_sc_clear_night; "cloudy_n" -> R.drawable.w_sc_cloudy_night
        "rain_n" -> R.drawable.w_sc_rain_night; "snow_n" -> R.drawable.w_sc_snow_night
        else -> R.drawable.w_sc_clear_day
    }
    fun circle(kind: String, night: Boolean) = when (key(kind, night)) {
        "cloudy_d" -> R.drawable.w_ci_cloudy_day; "rain_d" -> R.drawable.w_ci_rain_day; "snow_d" -> R.drawable.w_ci_snow_day
        "clear_n" -> R.drawable.w_ci_clear_night; "cloudy_n" -> R.drawable.w_ci_cloudy_night
        "rain_n" -> R.drawable.w_ci_rain_night; "snow_n" -> R.drawable.w_ci_snow_night
        else -> R.drawable.w_ci_clear_day
    }
    fun panorama(kind: String, night: Boolean) = when (key(kind, night)) {
        "cloudy_d" -> R.drawable.w_pa_cloudy_day; "rain_d" -> R.drawable.w_pa_rain_day; "snow_d" -> R.drawable.w_pa_snow_day
        "clear_n" -> R.drawable.w_pa_clear_night; "cloudy_n" -> R.drawable.w_pa_cloudy_night
        "rain_n" -> R.drawable.w_pa_rain_night; "snow_n" -> R.drawable.w_pa_snow_night
        else -> R.drawable.w_pa_clear_day
    }
}

/** 그림 파일은 한 번만 읽어서 재사용 */
object Assets {
    private val cache = HashMap<Int, Bitmap>()
    @Synchronized
    fun get(ctx: Context, res: Int): Bitmap? = cache[res] ?: try {
        BitmapFactory.decodeResource(ctx.resources, res)?.also { cache[res] = it }
    } catch (e: Exception) {
        null
    }
}
