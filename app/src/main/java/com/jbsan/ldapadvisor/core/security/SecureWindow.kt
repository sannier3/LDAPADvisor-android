package com.jbsan.ldapadvisor.core.security

import android.app.Activity
import android.view.WindowManager

object SecureWindow {
    fun enable(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun disable(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    inline fun <T> withSecure(activity: Activity, block: () -> T): T {
        enable(activity)
        return try {
            block()
        } finally {
            disable(activity)
        }
    }
}
