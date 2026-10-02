package com.kwansik.vinylwidget

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService

/**
 * 알림 내용은 전혀 읽지 않습니다. 이 서비스는 "미디어 세션"에 접근할 자격을 얻기 위한 용도입니다.
 */
class MediaListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var current: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = push()
        override fun onPlaybackStateChanged(state: PlaybackState?) = push()
        override fun onSessionDestroyed() {
            detach()
            push()
        }
    }

    override fun onListenerConnected() {
        val msm = getSystemService(MediaSessionManager::class.java)
        sessionManager = msm
        val cn = MediaHelper.listenerComponent(this)
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            attach(msm.getActiveSessions(cn))
        } catch (e: Throwable) {
            CrashLog.record(applicationContext, "MediaListenerService.onListenerConnected", e)
            push()
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        detach()
        push()
    }

    private fun attach(list: List<MediaController>?) {
        try {
            attachInner(list)
        } catch (e: Throwable) {
            CrashLog.record(applicationContext, "MediaListenerService.attach", e)
        }
    }

    private fun attachInner(list: List<MediaController>?) {
        val yt = list?.firstOrNull { it.packageName == MediaHelper.YTM_PACKAGE }
        val cur = current
        if (yt != null && cur != null && yt.sessionToken == cur.sessionToken) {
            push()
            return
        }
        detach()
        current = yt
        yt?.registerCallback(callback, handler)
        push()
    }

    private fun detach() {
        current?.unregisterCallback(callback)
        current = null
    }

    private fun push() {
        try {
            WidgetUpdater.render(applicationContext, current)
        } catch (e: Throwable) {
            CrashLog.record(applicationContext, "MediaListenerService.push", e)
        }
    }
}
