package io.github.eslamasabry.oc.core.common

import kotlin.concurrent.atomics.AtomicReference

/** Masks the secrets the app has registered (provider keys, MCP values, typed sign-in answers) in [text]. */
public fun interface KnownSecretMask {
    public fun mask(text: String): String
}

/**
 * The registered secrets, for text built where the Redactor instance cannot be handed in: the notification shade's
 * `NotificationText` objects (:engine:attention and :android:notifications, the latter depends on core common only).
 * Their own patterns catch secret-SHAPED text; a registered value that has no telltale shape (an MCP env value, a
 * header under an ordinary name) reached the shade in plain when a reply or a tool line echoed it.
 *
 * The app installs its one Redactor here at start, right after AppLog (OcApplication); until then, and in tests that
 * install nothing, [mask] returns the text unchanged.
 */
public object KnownSecrets {
    private val current = AtomicReference<KnownSecretMask?>(null)

    /** Installs [mask] (the app's Redactor); `null` removes it (tests). */
    public fun install(mask: KnownSecretMask?) {
        current.store(mask)
    }

    public fun mask(text: String): String = current.load()?.mask(text) ?: text
}
