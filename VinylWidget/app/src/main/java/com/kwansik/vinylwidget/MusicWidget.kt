package com.kwansik.vinylwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.min

/**
 * 음악 위젯 화면 만들기 (실제 위젯과 설정 화면 미리보기가 같은 코드를 씀).
 * 플레이어는 레코드판 또는 카세트 테이프. 크기에 따라 배치가 자동으로 바뀜:
 *  2×2 = [플레이어 / 조작 줄],  1×4 = [플레이어][제목·가수][조작],  4×2 = [플레이어 크게][제목·가수·조작]
 */
object MusicWidget {
    private const val DIP = TypedValue.COMPLEX_UNIT_DIP

    /** 2×2 위젯. 옆으로 늘려 4×2가 되면 4×2 배치 */
    fun build(
        ctx: Context, style: WidgetStyle, label: Bitmap, playing: Boolean, status: String?,
        wDp: Float = 170f, hDp: Float = 170f, art: Bitmap? = null, title: String? = null, artist: String? = null
    ): RemoteViews {
        if (BigScene.isBig(wDp, hDp)) return buildBig(ctx, style, label, playing, title, artist, status, wDp, hDp, art)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_spin)
        Palette.applyBackground(rv, style)

        // 위젯 크기에 맞춰 판과 톤암 데크 크기 지정 (날씨 위젯도 같은 계산을 써서 크기가 맞음)
        val d = WidgetGeom.discDp(wDp, hDp)
        val areaW = wDp - WidgetGeom.PAD * 2
        val areaH = hDp - WidgetGeom.PAD * 2 - WidgetGeom.ROW
        val tapeW = min(areaW, areaH * CassetteArt.RATIO)
        setPlayer(ctx, rv, style, playing, label, art, if (status != null) null else title, artist, d, tapeW)

        setButtons(ctx, rv, style, playing)
        rv.setOnClickPendingIntent(R.id.disc_area, openIntent(ctx))
        if (status != null) {
            rv.setTextViewText(R.id.status, status)
            rv.setViewVisibility(R.id.status, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.status, View.GONE)
        }
        return rv
    }

    /** 1×4: 플레이어 / 곡 제목·가수 / 재생 버튼. 아래로 늘려 4×2가 되면 4×2 배치 */
    fun buildWide(
        ctx: Context, style: WidgetStyle, label: Bitmap, playing: Boolean,
        title: String?, artist: String?, status: String?, wDp: Float, hDp: Float, art: Bitmap? = null
    ): RemoteViews {
        if (BigScene.isBig(wDp, hDp)) return buildBig(ctx, style, label, playing, title, artist, status, wDp, hDp, art)
        val rv = RemoteViews(ctx.packageName, R.layout.widget_spin_wide)
        Palette.applyBackground(rv, style)

        val d = (hDp - 14f).coerceIn(36f, 140f)            // 판 지름 = 위젯 높이에 맞춤
        val tapeW = ((hDp - 12f) * CassetteArt.RATIO).coerceIn(56f, 220f)
        setPlayer(ctx, rv, style, playing, label, art, if (status != null) null else title, artist, d, tapeW)

        setTexts(ctx, rv, style, title, artist, status)
        // 위젯 높이에 맞춰: 제목 0.2h, 가수 0.16h, 재생 원 0.6h, 이전·다음 0.42h (시안과 같은 비율)
        rv.setTextViewTextSize(R.id.m_title, DIP, (hDp * 0.2f).coerceIn(14f, 24f))
        rv.setTextViewTextSize(R.id.m_artist, DIP, (hDp * 0.16f).coerceIn(12f, 18f))
        sizeButtons(ctx, rv, (hDp * 0.6f).coerceIn(40f, 72f), (hDp * 0.42f).coerceIn(30f, 50f))

        setButtons(ctx, rv, style, playing)
        rv.setOnClickPendingIntent(R.id.deck, openIntent(ctx))
        rv.setOnClickPendingIntent(R.id.tape, openIntent(ctx))
        rv.setOnClickPendingIntent(R.id.w_text, openIntent(ctx))
        return rv
    }

    /** 4×2: 왼쪽에 플레이어를 크게, 오른쪽에 제목(두 줄)·가수·조작 버튼 */
    fun buildBig(
        ctx: Context, style: WidgetStyle, label: Bitmap, playing: Boolean,
        title: String?, artist: String?, status: String?, wDp: Float, hDp: Float, art: Bitmap? = null
    ): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_spin_big)
        Palette.applyBackground(rv, style)
        val boxH = hDp - 20f
        val d = min(boxH * 0.96f, (wDp * 0.5f) / WidgetGeom.ARM).coerceAtLeast(60f)
        val tapeW = min(boxH * 0.8f * CassetteArt.RATIO, wDp * 0.52f)
        val boxW = if (style.player == WidgetStyle.CASSETTE) tapeW else d * WidgetGeom.ARM
        rv.setViewLayoutWidth(R.id.player_box, boxW, DIP)
        rv.setViewLayoutHeight(R.id.player_box, boxH, DIP)
        setPlayer(ctx, rv, style, playing, label, art, if (status != null) null else title, artist, d, tapeW)

        setTexts(ctx, rv, style, title, artist, status)
        rv.setTextViewTextSize(R.id.m_title, DIP, (hDp * 0.12f).coerceIn(16f, 24f))
        rv.setTextViewTextSize(R.id.m_artist, DIP, (hDp * 0.085f).coerceIn(12f, 17f))
        sizeButtons(ctx, rv, (hDp * 0.34f).coerceIn(48f, 68f), (hDp * 0.26f).coerceIn(38f, 52f))

        setButtons(ctx, rv, style, playing)
        rv.setOnClickPendingIntent(R.id.player_box, openIntent(ctx))
        rv.setOnClickPendingIntent(R.id.m_title, openIntent(ctx))
        return rv
    }

    private fun setTexts(ctx: Context, rv: RemoteViews, style: WidgetStyle, title: String?, artist: String?, status: String?) {
        // 글자: 제목은 진하게, 가수는 70% 투명도
        rv.setTextViewText(R.id.m_title, status?.replace("\n", " ") ?: title?.takeIf { it.isNotBlank() } ?: "음악을 재생해 주세요")
        rv.setTextViewText(R.id.m_artist, if (status != null) "" else artist.orEmpty())
        Palette.setTextColor(ctx, rv, R.id.m_title, style)
        Palette.setTextColor(ctx, rv, R.id.m_artist, style, sub = true)
    }

    /** 재생 원(play)과 이전·다음(side) 크기(dp) */
    private fun sizeButtons(ctx: Context, rv: RemoteViews, play: Float, side: Float) {
        rv.setViewLayoutWidth(R.id.play_box, play, DIP); rv.setViewLayoutHeight(R.id.play_box, play, DIP)
        val dens = ctx.resources.displayMetrics.density
        val pp = (play * 0.25f * dens).toInt()
        rv.setViewPadding(R.id.btn_play, pp, pp, pp, pp)
        for (b in intArrayOf(R.id.btn_prev, R.id.btn_next)) {
            rv.setViewLayoutWidth(b, side, DIP); rv.setViewLayoutHeight(b, side, DIP)
            val sp = (side * 0.19f * dens).toInt()
            rv.setViewPadding(b, sp, sp, sp, sp)
        }
    }

    /**
     * 플레이어: 레코드판(판 지름 d) 또는 카세트(가로 tapeW).
     * 레코드판 = 판 몸통(고른 색) + 홈·광택(멈추면 그림, 재생 중이면 도는 ProgressBar) + 라벨 + 톤암
     * 카세트 = 몸통 그림(라벨에 제목·커버) + 릴 두 개(재생 중이면 돎)
     */
    private fun setPlayer(
        ctx: Context, rv: RemoteViews, style: WidgetStyle, playing: Boolean, label: Bitmap,
        art: Bitmap?, title: String?, artist: String?, d: Float, tapeW: Float
    ) {
        val tape = style.player == WidgetStyle.CASSETTE
        rv.setViewVisibility(R.id.deck, if (tape) View.GONE else View.VISIBLE)
        rv.setViewVisibility(R.id.tape, if (tape) View.VISIBLE else View.GONE)
        if (tape) {
            val tapeH = tapeW / CassetteArt.RATIO
            rv.setViewLayoutWidth(R.id.tape, tapeW, DIP); rv.setViewLayoutHeight(R.id.tape, tapeH, DIP)
            val px = (tapeW * ctx.resources.displayMetrics.density).toInt().coerceAtMost(720)
            rv.setImageViewBitmap(R.id.tape_img, CassetteArt.draw(px, style.quirky, style.disc, art, title, artist))
            val s = tapeH * CassetteArt.REEL_D
            val reels = listOf(
                Triple(R.id.reel_l_still, R.id.reel_l, R.id.reel_lf) to CassetteArt.REEL_X[0],
                Triple(R.id.reel_r_still, R.id.reel_r, R.id.reel_rf) to CassetteArt.REEL_X[1]
            )
            for ((ids, fx) in reels) {
                val (still, spin, spinFun) = ids
                for (v in intArrayOf(still, spin, spinFun)) {
                    rv.setViewLayoutWidth(v, s, DIP); rv.setViewLayoutHeight(v, s, DIP)
                    rv.setViewLayoutMargin(v, RemoteViews.MARGIN_START, tapeW * fx - s / 2, DIP)
                    rv.setViewLayoutMargin(v, RemoteViews.MARGIN_TOP, tapeH * CassetteArt.REEL_Y - s / 2, DIP)
                }
                rv.setImageViewResource(still, if (style.quirky) R.drawable.reel_fun else R.drawable.reel)
                rv.setViewVisibility(still, if (playing) View.GONE else View.VISIBLE)
                rv.setViewVisibility(spin, if (playing && !style.quirky) View.VISIBLE else View.GONE)
                rv.setViewVisibility(spinFun, if (playing && style.quirky) View.VISIBLE else View.GONE)
            }
            return
        }
        rv.setViewLayoutWidth(R.id.deck, d * WidgetGeom.ARM, DIP)
        rv.setViewLayoutHeight(R.id.deck, d, DIP)
        for (v in intArrayOf(R.id.disc_body, R.id.static_disc, R.id.spinner, R.id.spinner_fun, R.id.label)) {
            rv.setViewLayoutWidth(v, d, DIP); rv.setViewLayoutHeight(v, d, DIP)
        }
        // 톤암: 재생이면 판 위로, 멈추면 판 밖으로 (상태가 바뀔 때 애니메이션)
        rv.setCompoundButtonChecked(R.id.tonearm, playing)
        // 판 몸통 색: 고른 색, 아니면 기본(검정 / 병맛은 잉크색)
        rv.setImageViewResource(R.id.disc_body, if (style.quirky) R.drawable.vinyl_body_fun else R.drawable.vinyl_body)
        rv.setInt(R.id.disc_body, "setColorFilter", discColor(style))
        rv.setImageViewResource(R.id.static_disc, if (style.quirky) R.drawable.vinyl_grooves_fun else R.drawable.vinyl_grooves)
        rv.setViewVisibility(R.id.spinner, if (playing && !style.quirky) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.spinner_fun, if (playing && style.quirky) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.static_disc, if (playing) View.GONE else View.VISIBLE)
        rv.setImageViewBitmap(R.id.label, label)
    }

    fun discColor(style: WidgetStyle): Int =
        if (style.disc != 0) style.disc else if (style.quirky) 0xFF2B2622.toInt() else 0xFF111111.toInt()

    private fun setButtons(ctx: Context, rv: RemoteViews, style: WidgetStyle, playing: Boolean) {
        Palette.setIcon(ctx, rv, R.id.btn_prev, R.drawable.ic_prev, style)
        Palette.setPlay(ctx, rv, playing, style)
        Palette.setIcon(ctx, rv, R.id.btn_next, R.drawable.ic_next, style)
        rv.setOnClickPendingIntent(R.id.btn_prev, action(ctx, WidgetActionReceiver.ACTION_PREV, 1))
        rv.setOnClickPendingIntent(R.id.btn_play, action(ctx, WidgetActionReceiver.ACTION_PLAY_PAUSE, 2))
        rv.setOnClickPendingIntent(R.id.btn_next, action(ctx, WidgetActionReceiver.ACTION_NEXT, 3))
    }

    private fun action(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun openIntent(ctx: Context): PendingIntent {
        val ytm = ctx.packageManager.getLaunchIntentForPackage(MediaHelper.YTM_PACKAGE)
        val intent = if (MediaHelper.hasAccess(ctx) && ytm != null) ytm
        else Intent(ctx, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            ctx, 10, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
