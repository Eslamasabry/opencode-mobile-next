package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/** Text waiting for one conversation's composer. [target] is `profileId/sessionId`. */
@Immutable
public data class ComposerInsert(val id: Long, val target: String, val text: String)

/**
 * One slot of text a page outside the chat (Saved prompts, slash commands) hands back to a conversation's composer.
 * Snapshot state, so the composer that reads [pendingFor] while drawing sees an offer the moment the chat is back on
 * top. A newer offer replaces an older one (one at a time, never a queue); only the named conversation takes it.
 * Main thread only.
 */
@Inject
@SingleIn(AppScope::class)
public class ComposerInbox {
    private var slot by mutableStateOf<ComposerInsert?>(null)
    private var next = 0L

    public fun offer(target: String, text: String) {
        next += 1
        slot = ComposerInsert(next, target, text)
    }

    public fun pendingFor(target: String): ComposerInsert? = slot?.takeIf { it.target == target }

    public fun done(id: Long) {
        if (slot?.id == id) slot = null
    }

    public companion object {
        public fun target(profileId: String, sessionId: String): String = "$profileId/$sessionId"
    }
}
