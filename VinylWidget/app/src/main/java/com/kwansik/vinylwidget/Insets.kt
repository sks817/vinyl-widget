package com.kwansik.vinylwidget

import android.view.View
import android.view.WindowInsets

/**
 * Android 15+ (targetSdk 35 이상)은 화면이 상태바·내비게이션바 밑까지 그려짐(edge-to-edge 강제).
 * 버튼·글자가 가려지지 않도록 시스템 바(와 키보드) 높이만큼 안쪽 여백을 줌.
 */
fun View.padForSystemBars() {
    val base = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    setOnApplyWindowInsetsListener { v, insets ->
        val b = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
        v.setPadding(base[0] + b.left, base[1] + b.top, base[2] + b.right, base[3] + b.bottom)
        insets
    }
    requestApplyInsets()
}
