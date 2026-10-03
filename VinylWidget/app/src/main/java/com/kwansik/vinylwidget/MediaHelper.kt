package com.kwansik.vinylwidget

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.view.KeyEvent

object MediaHelper {
    const val YTM_PACKAGE = "com.google.android.apps.youtube.music"

    fun listenerComponent(ctx: Context) = ComponentName(ctx, MediaListenerService::class.java)

    fun hasAccess(ctx: Context): Boolean =
        ctx.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(listenerComponent(ctx))

    /** 현재 활성화된 유튜브 뮤직 세션. 권한이 없거나 재생 중인 세션이 없으면 null */
    fun findController(ctx: Context): MediaController? = try {
        ctx.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(listenerComponent(ctx))
            .firstOrNull { it.packageName == YTM_PACKAGE }
    } catch (e: SecurityException) {
        null
    }

    /** 세션을 못 찾았을 때의 대비책: 마지막으로 재생하던 미디어 앱에 버튼 신호를 보냄 */
    fun dispatchKey(ctx: Context, keyCode: Int) {
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
