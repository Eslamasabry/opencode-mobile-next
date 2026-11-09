package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The window the secret content is drawn in. One [hold] per piece of secret content on screen; the window is secure
 * while at least one hold is open. The shell provides the real one (Android: `FLAG_SECURE`); the default does nothing,
 * so desktop, previews and tests need no setup (B-SEC-156, audit 2026-11-09 gap 10).
 */
public fun interface KitSecureWindow {
    /** Returns the release for this hold; call it once when the content leaves the screen. */
    public fun hold(): () -> Unit
}

public val LocalKitSecureWindow: ProvidableCompositionLocal<KitSecureWindow> = staticCompositionLocalOf { KitSecureWindow { {} } }

/**
 * Counts holds and tells [apply] `true` on the first and `false` after the last, so two secret fields on one screen
 * (or a field under a sheet) never switch each other off. Main thread only, like composition.
 */
public class KitSecureWindowCounter(private val apply: (Boolean) -> Unit) : KitSecureWindow {
    private var open = 0

    public val secure: Boolean get() = open > 0

    override fun hold(): () -> Unit {
        if (open++ == 0) apply(true)
        var released = false
        return {
            if (!released) {
                released = true
                if (--open == 0) apply(false)
            }
        }
    }
}

/**
 * Keeps the window out of the app switcher, screenshots and screen recordings while [active] and this is composed.
 * Used by the kit's own secret parts; a screen that reveals a secret itself wraps that part in [KitSecureContent].
 */
@Composable
public fun KitSecureEffect(active: Boolean = true) {
    val window = LocalKitSecureWindow.current
    DisposableEffect(window, active) {
        val release = if (active) window.hold() else null
        onDispose { release?.invoke() }
    }
}

/**
 * Wraps content that shows a password, API key, token, sign-in code or provider credential. The window is secure only
 * while it is composed. Screens use this; they never touch window flags.
 *
 * States: none. Text: none. Cap: none (draws nothing of its own). Motion: none.
 */
@Composable
public fun KitSecureContent(content: @Composable () -> Unit) {
    KitSecureEffect()
    content()
}
