package com.kwansik.vinylwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.PlaybackState
import android.view.KeyEvent

class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.kwansik.vinylwidget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.kwansik.vinylwidget.NEXT"
        const val ACTION_PREV = "com.kwansik.vinylwidget.PREV"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            handle(context, intent)
        } catch (e: Throwable) {
            CrashLog.record(context, "WidgetActionReceiver(${intent.action})", e)
        }
    }

    private fun handle(context: Context, intent: Intent) {
        val c = MediaHelper.findController(context)
        if (c == null) {
            val key = when (intent.action) {
                ACTION_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                ACTION_PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                else -> return
            }
            MediaHelper.dispatchKey(context, key)
            return
        }
        val controls = c.transportControls
        when (intent.action) {
            ACTION_PLAY_PAUSE ->
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause()
                else controls.play()
            ACTION_NEXT -> controls.skipToNext()
            ACTION_PREV -> controls.skipToPrevious()
        }
    }
}
