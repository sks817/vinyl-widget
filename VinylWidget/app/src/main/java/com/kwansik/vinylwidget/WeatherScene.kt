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
    private val shadow: Int = 0, private val quirky: Boolean = true,
    /** 사용자가 글자색을 직접 골랐는가 → 그림 위 글자도 그 색 */
    private val customFg: Boolean = false
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

    /** 날씨 아이콘: 병맛 테마면 캐릭터(색 그대로), 아니면 선 아이콘(tint 색) */
    private fun wIcon(d: WeatherData, cx: Float, cy: Float, size: Float, tint: Int) {
        if (quirky) { funIcon(d.iconFun(), cx, cy, size); return }
        val s = size * 0.7f
        ctx.getDrawable(d.icon())?.mutate()?.let {
            it.setTint(tint); it.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt()); it.draw(c)
        }
    }

    /** 캐릭터 날씨 아이콘 (색 그대로) */
    private fun funIcon(res: Int, cx: Float, cy: Float, size: Float) {
        Assets.get(ctx, res)?.let {
            c.drawBitmap(it, null, RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    /** 그림을 상자에 꽉 채우고 넘치는 쪽은 가운데 기준으로 잘라냄 (center-crop) */
    private fun cover(res: Int, dst: RectF) {
        val b = Assets.get(ctx, res) ?: return
        val ar = dst.width() / dst.height(); val br = b.width.toFloat() / b.height
        val src = if (br > ar) { val w = (b.height * ar).toInt(); android.graphics.Rect((b.width - w) / 2, 0, (b.width + w) / 2, b.height) }
                  else { val h = (b.width / ar).toInt(); android.graphics.Rect(0, (b.height - h) / 2, b.width, (b.height + h) / 2) }
        c.drawBitmap(b, src, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
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
        val msg = d.message()
        if (msg != null) { textMid(msg, W / 2, y, 14f * k, SANS, fg); return }
        // 캐릭터 + 기온(크게) | 날씨 | 최저/최고(흐리게) → 기온이 가장 먼저 보이게
        val t = d.tempText()
        val tSize = 19f * k
        val p = Paint().apply { textSize = tSize; typeface = SERIF }
        val tw = p.measureText(t); val ic = row * 0.82f; val gap = tSize * 0.15f
        val start = xs[0] - (ic + gap + tw) / 2
        wIcon(d, start + ic / 2, y, ic, accent)
        textMid(t, start + ic + gap + tw / 2, y, tSize, SERIF, fg)
        textMid(d.cond(), xs[1], y, 14f * k, SANS_M, fg)
        textMid(d.rangeText().replace(" ", ""), xs[2], y, 13f * k, SANS_R, sub)
    }

    /** corner = 그림 모서리(짧은 변의 %, -1이면 기본 11), glassArt = 유리 캡슐 효과 */
    fun draw(
        design: Int, d: WeatherData, corner: Int = -1, glassArt: Boolean = false, artShadow: Boolean = false,
        point: Int = WidgetStyle.DEFAULT_POINT, cardAlpha: Int = 255, cardColor: Int = 0
    ) {
        val today = LocalDate.now()
        val md = "${today.monthValue}.${today.dayOfMonth}"
        val wk = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val sq = square
        val night = d.isNight()
        val kind = d.skyKind()
        val lit = night || kind == "rain"                      // 밤이거나 비 오는 날엔 텐트 불을 켬
        val darkText = !night && kind != "rain"                 // 밝은 낮 하늘엔 진한 글자
        val art = ArtText(c, quirky, if (customFg) fg else null, sub, darkText)
        fun ap(size: Float, tf: Typeface, col: Int, a: Paint.Align = Paint.Align.LEFT) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = tf; color = col; textAlign = a }

        when (design) {
            1 -> { // LP 재킷: 그림이 위젯을 꽉 채우는 카드. 날짜는 하늘(왼쪽 위), 기온은 아래 어둠막 위에 크게
                val inset = if (artShadow) 4f * k else 0f
                val card = RectF(inset, inset, W - inset, H - inset)
                val S = min(card.width(), card.height())
                val rad = S * (if (corner < 0) 11 else corner) / 100f
                if (artShadow) GlassArt.shadow(c, card, rad)
                c.save()
                c.clipPath(android.graphics.Path().apply { addRoundRect(card, rad, rad, android.graphics.Path.Direction.CW) })
                cover(Scenes.jacket(kind, night, quirky), card)
                // 아래쪽 절반: 위에서 아래로 짙어지는 어둠막 → 흰 기온 글자가 어떤 그림 위에서도 읽힘
                val scrimTop = card.bottom - card.height() * 0.52f
                c.drawRect(card.left, scrimTop, card.right, card.bottom, Paint().apply {
                    shader = android.graphics.LinearGradient(0f, scrimTop, 0f, card.bottom,
                        intArrayOf(0x00000000, 0x4D000000, 0x9E000000.toInt()), floatArrayOf(0f, 0.45f, 1f), android.graphics.Shader.TileMode.CLAMP)
                })
                c.restore()
                if (glassArt) GlassArt.draw(c, card, rad)
                val m = S * 0.08f
                art.top(md, card.left + m, card.top + S * 0.065f, ap(S * 0.22f, SERIF, art.main))
                art.top(wk, card.left + m * 1.06f, card.top + S * 0.30f, ap(S * 0.075f, SANS_M, art.sub))
                // 아래 어둠막 위: 기본은 흰 글자, 직접 고른 색이면 그 색
                val white = if (customFg) fg else 0xFFFFFFFF.toInt()
                val low = ArtText(c, quirky, white, if (customFg) sub else 0xD9FFFFFF.toInt(), false)
                val bottom = card.bottom - S * 0.07f
                val msg = d.message()
                if (msg != null) {
                    low.draw(msg, card.left + m, bottom, ap(S * 0.08f, SANS, low.main))
                } else {
                    // 왼쪽 아래: 기온 (가장 크게)
                    low.draw(d.tempText(), card.left + m, bottom, ap(S * 0.23f, SERIF, low.main))
                    // 오른쪽 아래: 최저·최고 (오른쪽 정렬)
                    val right = card.right - m
                    val range = d.rangeText().replace(" ", "")
                    low.draw(range, right, bottom, ap(S * 0.072f, SANS_R, low.sub, Paint.Align.RIGHT))
                    val ic = S * 0.2f
                    // 병맛 그림은 하늘에 이미 캐릭터가 있으니 여기 아이콘은 생략
                    if (!quirky) wIcon(d, right - ic / 2 + S * 0.02f, bottom - S * 0.1f - ic / 2, ic, white)
                }
            }
            2 -> { // 불 켜진 텐트: 날짜를 텐트 천 위에
                val box = RectF(areaL, sq.top, areaL + areaW, sq.bottom)
                val tent = Scenes.tent(lit, quirky)   // 낮엔 불 꺼진 텐트
                val r = fitBottom(tent, box)
                asset(tent, r)
                // 텐트 천 위 글자: 기본은 갈색, 직접 고른 색이면 그 색
                text(wk, r.centerX(), r.top + r.height() * 0.36f, r.width() * 0.055f, SANS_M, if (customFg) sub else BROWN_SUB, A.C)
                text(md, r.centerX(), r.top + r.height() * 0.45f, r.width() * 0.19f, SERIF, if (customFg) fg else BROWN, A.C)
                bottomRow(d)
            }
            3 -> { // 하늘 원: 재킷과 같은 규칙으로 날씨·시간에 따라 바뀜
                asset(Scenes.circle(kind, night, quirky), sq)
                art.top(md, sq.centerX(), sq.top + D * 0.07f, ap(D * 0.27f, SERIF, art.main, Paint.Align.CENTER))
                art.top(wk, sq.centerX(), sq.top + D * 0.35f, ap(D * 0.07f, SANS_M, art.sub, Paint.Align.CENTER))
                bottomRow(d)
            }
            4 -> { // 큰 날짜 + 작은 텐트: 도형 없이 글자 중심 (레코드판과 경쟁하지 않음)
                text(md, areaL + areaW * 0.02f, sq.top, D * 0.42f, SERIF, fg)
                text(wk, areaL + areaW * 0.04f, sq.top + D * 0.50f, D * 0.10f, SANS_M, sub)
                val box = RectF(areaL + areaW * 0.40f, sq.top + D * 0.45f, areaL + areaW, sq.bottom)
                val icon = Scenes.tentIcon(lit, quirky)
                asset(icon, fitBottom(icon, box))
                bottomRow(d)
            }
            8, 9, 10 -> { // 캐릭터 포스터 / 컬러 카드 / 헤드라인
                CharacterLayouts(ctx, c, W, H, k, fg, sub, shadow, customFg, cardColor).draw2x2(design - 8, d, cardAlpha)
            }
            else -> { // 기존 디자인(미니멀·다이얼·달력)은 정사각 칸 가운데에
                val legacy = when (design) { 5 -> 1; 6 -> 3; else -> 5 }
                val s = (min(W, H) - pad * 2).toInt().coerceAtLeast(10)
                val p = WeatherPainter(ctx, s, fg, point, quirky)
                p.draw(legacy, d)
                c.drawBitmap(p.bitmap, (W - s) / 2, (H - s) / 2, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }
}

/** 날씨(맑음·구름·비·눈) × 낮/밤 → 그림 파일. quirky = 병맛 캠핑장 (굵은 선, 얼굴 있는 텐트, 캐릭터 해·달) */
object Scenes {
    private fun key(kind: String, night: Boolean) = kind + if (night) "_n" else "_d"
    fun jacket(kind: String, night: Boolean, quirky: Boolean = false) = if (quirky) when (key(kind, night)) { "clear_n" -> R.drawable.w_scq_clear_night; "cloudy_d" -> R.drawable.w_scq_cloudy_day; "cloudy_n" -> R.drawable.w_scq_cloudy_night; "rain_d" -> R.drawable.w_scq_rain_day; "rain_n" -> R.drawable.w_scq_rain_night; "snow_d" -> R.drawable.w_scq_snow_day; "snow_n" -> R.drawable.w_scq_snow_night; else -> R.drawable.w_scq_clear_day }
        else when (key(kind, night)) { "clear_n" -> R.drawable.w_sc_clear_night; "cloudy_d" -> R.drawable.w_sc_cloudy_day; "cloudy_n" -> R.drawable.w_sc_cloudy_night; "rain_d" -> R.drawable.w_sc_rain_day; "rain_n" -> R.drawable.w_sc_rain_night; "snow_d" -> R.drawable.w_sc_snow_day; "snow_n" -> R.drawable.w_sc_snow_night; else -> R.drawable.w_sc_clear_day }
    fun circle(kind: String, night: Boolean, quirky: Boolean = false) = if (quirky) when (key(kind, night)) { "clear_n" -> R.drawable.w_ciq_clear_night; "cloudy_d" -> R.drawable.w_ciq_cloudy_day; "cloudy_n" -> R.drawable.w_ciq_cloudy_night; "rain_d" -> R.drawable.w_ciq_rain_day; "rain_n" -> R.drawable.w_ciq_rain_night; "snow_d" -> R.drawable.w_ciq_snow_day; "snow_n" -> R.drawable.w_ciq_snow_night; else -> R.drawable.w_ciq_clear_day }
        else when (key(kind, night)) { "clear_n" -> R.drawable.w_ci_clear_night; "cloudy_d" -> R.drawable.w_ci_cloudy_day; "cloudy_n" -> R.drawable.w_ci_cloudy_night; "rain_d" -> R.drawable.w_ci_rain_day; "rain_n" -> R.drawable.w_ci_rain_night; "snow_d" -> R.drawable.w_ci_snow_day; "snow_n" -> R.drawable.w_ci_snow_night; else -> R.drawable.w_ci_clear_day }
    fun panorama(kind: String, night: Boolean, quirky: Boolean = false) = if (quirky) when (key(kind, night)) { "clear_n" -> R.drawable.w_paq_clear_night; "cloudy_d" -> R.drawable.w_paq_cloudy_day; "cloudy_n" -> R.drawable.w_paq_cloudy_night; "rain_d" -> R.drawable.w_paq_rain_day; "rain_n" -> R.drawable.w_paq_rain_night; "snow_d" -> R.drawable.w_paq_snow_day; "snow_n" -> R.drawable.w_paq_snow_night; else -> R.drawable.w_paq_clear_day }
        else when (key(kind, night)) { "clear_n" -> R.drawable.w_pa_clear_night; "cloudy_d" -> R.drawable.w_pa_cloudy_day; "cloudy_n" -> R.drawable.w_pa_cloudy_night; "rain_d" -> R.drawable.w_pa_rain_day; "rain_n" -> R.drawable.w_pa_rain_night; "snow_d" -> R.drawable.w_pa_snow_day; "snow_n" -> R.drawable.w_pa_snow_night; else -> R.drawable.w_pa_clear_day }

    fun tent(lit: Boolean, quirky: Boolean) = if (quirky) { if (lit) R.drawable.w_tentq_glow else R.drawable.w_tentq_day }
        else { if (lit) R.drawable.w_tent_glow else R.drawable.w_tent_day }
    fun tentIcon(lit: Boolean, quirky: Boolean) = if (quirky) { if (lit) R.drawable.w_tentq_icon else R.drawable.w_tentq_icon_day }
        else { if (lit) R.drawable.w_tent_icon else R.drawable.w_tent_icon_day }
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

/** 병맛 테마 한마디: 날씨마다 캐릭터가 하는 말 */
object Quips {
    fun of(d: WeatherData): String {
        val n = d.isNight()
        val c = d.code ?: return "날씨 알아보는 중..."
        return when (c) {
            0, 1 -> if (n) "꿀잠 예약 완료" else "광합성 하기 딱 좋은 날"
            2 -> if (n) "구름이 달 가리는 중" else "해가 구름 뒤에서 눈치 봄"
            3 -> "하늘도 오늘은 귀찮대"
            45, 48 -> "앞이 하나도 안 보여요.."
            in 51..57 -> "찔끔찔끔.. 우산 살짝"
            in 61..67, in 80..82 -> "하늘이 운다 ㅠㅠ 우산!"
            in 71..77, 85, 86 -> "덜덜.. 패딩 필수"
            in 95..99 -> "하늘이 단단히 화났다"
            else -> "오늘도 무사히"
        }
    }
}
