package io.github.eslamasabry.oc.ui.kit.image

import android.content.ComponentCallbacks2

/**
 * How much of the picture cache's budget survives an `onTrimMemory` [level], or null to leave it alone. The cache is
 * only a speed-up (everything re-reads from the server or the disk thumbnails), so it goes first: half under moderate
 * pressure while visible, a quarter when low or when the app is hidden, nothing when critical or in the background.
 */
public fun imageKeepFraction(level: Int): Float? =
    when {
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> 0f
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL && level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> 0f
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> 0.25f
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> 0.25f
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> 0.5f
        else -> null
    }
