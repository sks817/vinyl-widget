package com.kwansik.vinylwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/** 레코드 위젯 화면 만들기 (실제 위젯과 설정 화면 미리보기가 같은 코드를 씀) */
object MusicWidget {

    fun build(
        ctx: Context, style: WidgetStyle, label: Bitmap, playing: Boolean, status: String?,
        wDp: Float = 170f, hDp: Float = 170f
    ): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_spin)

        // 위젯 크기에 맞춰 판과 톤암 데크 크기 지정 (날씨 위젯도 같은 계산을 써서 크기가 맞음)
        val d = WidgetGeom.discDp(wDp, hDp)
        val dip = TypedValue.COMPLEX_UNIT_DIP
        rv.setViewLayoutWidth(R.id.deck, d * WidgetGeom.ARM, dip)
        rv.setViewLayoutHeight(R.id.deck, d, dip)
        for (v in intArrayOf(R.id.static_disc, R.id.spinner, R.id.label)) {
            rv.setViewLayoutWidth(v, d, dip)
            rv.setViewLayoutHeight(v, d, dip)
        }
        // 톤암: 재생이면 판 위로, 멈추면 판 밖으로 (상태가 바뀔 때 애니메이션)
        rv.setCompoundButtonChecked(R.id.tonearm, playing)

        Palette.applyBackground(rv, style)

        rv.setViewVisibility(R.id.spinner, if (playing) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.static_disc, if (playing) View.GONE else View.VISIBLE)
        rv.setImageViewBitmap(R.id.label, label)

        setButtons(ctx, rv, style, playing)
        rv.setOnClickPendingIntent(R.id.btn_prev, action(ctx, WidgetActionReceiver.ACTION_PREV, 1))
        rv.setOnClickPendingIntent(R.id.btn_play, action(ctx, WidgetActionReceiver.ACTION_PLAY_PAUSE, 2))
        rv.setOnClickPendingIntent(R.id.btn_next, action(ctx, WidgetActionReceiver.ACTION_NEXT, 3))
        rv.setOnClickPendingIntent(R.id.disc_area, openIntent(ctx))

        if (status != null) {
            rv.setTextViewText(R.id.status, status)
            rv.setViewVisibility(R.id.status, View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.status, View.GONE)
        }
        return rv
    }

    /** 1×4: 판+톤암 / 곡 제목·가수 / 재생 버튼 */
    fun buildWide(
        ctx: Context, style: WidgetStyle, label: Bitmap, playing: Boolean,
        title: String?, artist: String?, status: String?, wDp: Float, hDp: Float
    ): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_spin_wide)
        Palette.applyBackground(rv, style)

        val d = (hDp - 12f).coerceIn(36f, 140f)            // 판 지름 = 위젯 높이에 맞춤
        val dip = TypedValue.COMPLEX_UNIT_DIP
        rv.setViewLayoutWidth(R.id.deck, d * WidgetGeom.ARM, dip)
        rv.setViewLayoutHeight(R.id.deck, d, dip)
        for (v in intArrayOf(R.id.static_disc, R.id.spinner, R.id.label)) {
            rv.setViewLayoutWidth(v, d, dip); rv.setViewLayoutHeight(v, d, dip)
        }
        rv.setCompoundButtonChecked(R.id.tonearm, playing)
        rv.setViewVisibility(R.id.spinner, if (playing) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.static_disc, if (playing) View.GONE else View.VISIBLE)
        rv.setImageViewBitmap(R.id.label, label)

        // 글자: 제목은 진하게, 가수는 70% 투명도
        rv.setTextViewText(R.id.m_title, status?.replace("\n", " ") ?: title?.takeIf { it.isNotBlank() } ?: "음악을 재생해 주세요")
        rv.setTextViewText(R.id.m_artist, if (status != null) "" else artist.orEmpty())
        Palette.setTextColor(ctx, rv, R.id.m_title, style)
        Palette.setTextColor(ctx, rv, R.id.m_artist, style, sub = true)
        // 위젯 높이가 높아지면 글자도 함께 키움
        val scale = (hDp / 70f).coerceIn(0.9f, 1.5f)
        rv.setTextViewTextSize(R.id.m_title, dip, 15f * scale)
        rv.setTextViewTextSize(R.id.m_artist, dip, 12f * scale)

        setButtons(ctx, rv, style, playing)
        rv.setOnClickPendingIntent(R.id.btn_prev, action(ctx, WidgetActionReceiver.ACTION_PREV, 1))
        rv.setOnClickPendingIntent(R.id.btn_play, action(ctx, WidgetActionReceiver.ACTION_PLAY_PAUSE, 2))
        rv.setOnClickPendingIntent(R.id.btn_next, action(ctx, WidgetActionReceiver.ACTION_NEXT, 3))
        rv.setOnClickPendingIntent(R.id.deck, openIntent(ctx))
        rv.setOnClickPendingIntent(R.id.w_text, openIntent(ctx))
        return rv
    }

    private fun setButtons(ctx: Context, rv: RemoteViews, style: WidgetStyle, playing: Boolean) {
        Palette.setIcon(ctx, rv, R.id.btn_prev, R.drawable.ic_prev, style)
        Palette.setIcon(ctx, rv, R.id.btn_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play, style)
        Palette.setIcon(ctx, rv, R.id.btn_next, R.drawable.ic_next, style)
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
