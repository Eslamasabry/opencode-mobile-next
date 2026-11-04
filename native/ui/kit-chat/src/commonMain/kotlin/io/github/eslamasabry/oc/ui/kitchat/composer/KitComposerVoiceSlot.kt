package io.github.eslamasabry.oc.ui.kitchat.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ConversationRef

/*
 * Voice slots (P12-VOICE). The chat feature draws the composer and the message menu; `:feature:voice` fills these two
 * slots, so neither feature imports the other (module rule: features never depend on features). Plain ids, lambdas
 * and strings only: no engine type, no ViewModel.
 */

/**
 * What the composer lends the voice slot: its field and its one send path. Every getter is read
 * when used.
 */
@Stable
public class KitVoiceField(
    /**
     * Unique per conversation composer ("profile:session"); the slot keeps one voice state per key.
     */
    public val key: String,
    public val text: () -> String,
    /**
     * The field's selection as UTF-16 offsets, or null when there is none (the words then land at
     * the end).
     */
    public val selection: () -> IntRange?,
    /** Replaces the whole field (the dictated words merged around the selection). */
    public val replace: (String) -> Unit,
    /**
     * The composer's own Send, exactly as a tap on Send. False when it was refused (nothing in the
     * field, busy…).
     */
    public val send: () -> Unit,
    /** True while the composer can send (ready, not already sending). */
    public val canSend: () -> Boolean,
    /** Exact owner for optional conversation-mode voice; never parsed from [key]. */
    public val conversationRef: ConversationRef? = null,
    /** A draft-only surface (for example Ask for change) never auto-sends recorded words. */
    public val dictationOnly: Boolean = false,
)

/** Contributed by `:feature:voice` (empty set = no mic, never a dead one). */
public interface KitComposerVoice {
    /**
     * The mic beside Send: tap to talk, hold to talk, slide away to cancel. Absent where voice
     * cannot work.
     */
    @Composable public fun Mic(field: KitVoiceField, modifier: Modifier)

    /**
     * Above the field while voice is active: what is heard, the countdown, the notes. Draws nothing
     * when idle.
     */
    @Composable public fun Panel(field: KitVoiceField, modifier: Modifier)
}

/**
 * A reply the person asked to hear (the message menu's "Read aloud"). [id] changes on every ask.
 */
public data class KitReadRequest(val id: Long, val rowKey: String, val text: String)

/**
 * Contributed by `:feature:voice`: reads a reply aloud, with consent once per phone, and draws its
 * own strip.
 */
public interface KitReplyReader {
    /**
     * Draws the reading strip (Stop, notes, the consent sheet) and starts [request] once per id.
     */
    @Composable
    public fun Reader(request: KitReadRequest?, onHandled: (id: Long) -> Unit, modifier: Modifier)
}

/** Declares the two voice slots so a build without `:feature:voice` still compiles (empty sets). */
@ContributesTo(AppScope::class)
public interface KitVoiceSlotBindings {
    @Multibinds(allowEmpty = true) public val composerVoices: Set<KitComposerVoice>

    @Multibinds(allowEmpty = true) public val replyReaders: Set<KitReplyReader>
}
