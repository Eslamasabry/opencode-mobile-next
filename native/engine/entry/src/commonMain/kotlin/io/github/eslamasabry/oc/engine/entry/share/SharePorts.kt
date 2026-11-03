package io.github.eslamasabry.oc.engine.entry.share

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * The platform half of `oc/share` (Flutter `ShareIntent` / `ShareOut` over the MethodChannel). The Android binding
 * reads the launching intent's text and forwards later `ACTION_SEND` intents; on a platform without one the app binds
 * nothing and [ShareReceiver] / [ShareOut] stay inert.
 */
public interface ShareInbox {
    /** The share that launched the app, read once (`consumeSharedText`); Ok(null) when there was none. */
    public suspend fun consumeSharedText(): Outcome<String?>

    /**
     * Starts forwarding live shares to [onShared]. The platform holds ONE listener (like a MethodChannel handler):
     * a later [listen] replaces the earlier one, and a [ShareRegistration.cancel] clears it only while it is still the
     * registered one, so a stale receiver can never silence an active one (B-ANDR-076).
     */
    public fun listen(onShared: (String) -> Unit): ShareRegistration
}

/** Undoes one [ShareInbox.listen]. Idempotent. */
public fun interface ShareRegistration {
    public fun cancel()
}

/** The system share sheet (`shareText` with `text` and `subject`). Ok(true) when the sheet opened. */
public fun interface ShareSheet {
    public suspend fun shareText(text: String, subject: String?): Outcome<Boolean>
}
