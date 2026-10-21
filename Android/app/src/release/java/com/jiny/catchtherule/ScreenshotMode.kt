package com.jiny.catchtherule

import android.app.Activity

/** 릴리스용 빈 구현. 실제 스크린샷 훅은 src/debug 에만 있다. */
object ScreenshotMode {
    val active: Boolean = false
    const val prefsName = "ctr_progress"
    val startTab: Int = 0
    val play: Pair<String, Int>? = null
    fun setup(activity: Activity) = Unit
}
