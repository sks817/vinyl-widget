package com.kwansik.vinylwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import java.util.concurrent.Executors

/** 레코드 위젯 갱신 */
object WidgetUpdater {

    data class NowPlaying(val trackKey: String, val art: Bitmap?, val playing: Boolean, val title: String = "", val artist: String = "")

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var lastKey: String? = null

    private fun snapshot(c: MediaController): NowPlaying {
        val m = c.metadata
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        return NowPlaying("$title|$artist|${art != null}", art, playing, title, artist)
    }

    fun render(ctx: Context, controller: MediaController?) {
        val app = ctx.applicationContext
        val np = controller?.let { snapshot(it) }
        executor.execute { safeRender(app, np, force = false) }
    }

    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        executor.execute {
            val np = MediaHelper.findController(app)?.let { snapshot(it) }
            safeRender(app, np, force = true)
        }
    }

    private fun safeRender(ctx: Context, np: NowPlaying?, force: Boolean) {
        try {
            renderInternal(ctx, np, force)
        } catch (t: Throwable) {
            CrashLog.record(ctx, "WidgetUpdater.render", t)
        }
    }

    private fun renderInternal(ctx: Context, np: NowPlaying?, force: Boolean) {
        val access = MediaHelper.hasAccess(ctx)
        val key = "${np?.trackKey}|${np?.playing}|$access|${Palette.signature(ctx)}"
        if (!force && key == lastKey) return

        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, SpinWidgetProvider::class.java))
        val wideIds = mgr.getAppWidgetIds(ComponentName(ctx, SpinWideProvider::class.java))
        if (ids.isEmpty() && wideIds.isEmpty()) {
            lastKey = null
            return
        }
        val status = when {
            !access -> "탭해서 권한 설정"
            np == null -> "유튜브 뮤직을\n재생해 주세요"
            else -> null
        }
        val playing = np?.playing == true
        // 라벨은 테마(기본/병맛)별로 필요할 때 한 번만 그림
        val labels = HashMap<Boolean, Bitmap>()
        fun labelFor(s: WidgetStyle) = labels.getOrPut(s.quirky) { LabelRenderer.draw(np?.art, Palette.label(ctx), s.quirky) }
        for (id in ids) {
            val style = WidgetPrefs.style(ctx, Kind.MUSIC, id)
            val (w, h) = WidgetGeom.sizeDp(mgr, id)
            mgr.updateAppWidget(id, MusicWidget.build(ctx, style, labelFor(style), playing, status, w, h, np?.art, np?.title, np?.artist))
        }
        for (id in wideIds) {
            val style = WidgetPrefs.style(ctx, Kind.MUSIC_WIDE, id)
            val (w, h) = WidgetGeom.sizeDp(mgr, id)
            mgr.updateAppWidget(id, MusicWidget.buildWide(ctx, style, labelFor(style), playing, np?.title, np?.artist, status, w, h, np?.art))
        }
        lastKey = key
    }
}
