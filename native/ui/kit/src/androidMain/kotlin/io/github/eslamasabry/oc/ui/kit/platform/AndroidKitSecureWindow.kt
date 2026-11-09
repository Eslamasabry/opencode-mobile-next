package io.github.eslamasabry.oc.ui.kit.platform

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * `FLAG_SECURE` on the activity window while secret content is on screen, and (API 33+) no recents screenshot.
 * Sheets and dialogs of the kit are drawn inside this window, so they are covered too.
 */
public fun androidKitSecureWindow(activity: Activity): KitSecureWindowCounter =
    KitSecureWindowCounter { secure ->
        val window = activity.window
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= 33) activity.setRecentsScreenshotEnabled(!secure)
    }
