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
     * The big round mic that stands in for Send while the box is empty: hold to dictate, slide away to cancel. Absent where voice
     * cannot work.
     */
    @Composable public fun Mic(field: KitVoiceField, modifier: Modifier)

    /**
     * True while the mic is listening or writing. The composer keeps the mic where it started until
     * this ends, so a hold is never cut by the button changing under the finger.
     */
    @Composable public fun isActive(field: KitVoiceField): Boolean = false

    /**
     * The small mic inside the field while a message is being drafted or the agent works: hold to
     * dictate more. Absent where voice cannot work.
     */
    @Composable public fun InlineMic(field: KitVoiceField, modifier: Modifier) {}

    /** The hands-free voice conversation, as its own item while the plus menu is open. */
    @Composable public fun Actions(field: KitVoiceField, modifier: Modifier) {}

    /**
     * Above the field: the hands-free voice mode's extra controls (its tour, "Read aloud" switch, "Read the reply"), its
     * sheets, and the effects voice needs (words typed during a recording, leaving the app). The listening, speaking
     * and waiting states are the box's own recorder row ([recording], [Recorder]); notes are [Note].
     */
    @Composable public fun Panel(field: KitVoiceField, modifier: Modifier)

    /**
     * While words are dictated into [field], the hands-free voice mode runs, or a reply is read aloud (owner
     * 2026-11-17: the message box itself becomes the recorder; one look for one thing): how the box hosts the recorder
     * row. Null otherwise; the box is then as always.
     */
    @Composable public fun recording(field: KitVoiceField): KitVoiceRecording? = null

    /**
     * Where in [field]'s text the dictated words still forming sit (UTF-16 indices): the box draws them in the quieter
     * text colour until they are confirmed. Null when nothing is unconfirmed (always, with an engine that does not
     * stream).
     */
    @Composable public fun unconfirmed(field: KitVoiceField): IntRange? = null

    /** The recorder row inside the box while [recording] is not null: ✕, the live waveform and time, ✓. */
    @Composable public fun Recorder(field: KitVoiceField, modifier: Modifier) {}

    /**
     * The one note after dictation ends (nothing heard, the microphone is off, it failed, no speech model), inside the
     * box above the words, with its way forward. Draws nothing when there is none.
     */
    @Composable public fun Note(field: KitVoiceField, modifier: Modifier) {}

    /**
     * Stops any dictation into [field] when this composer leaves the screen (a switch to another conversation, Files,
     * Back), so the mic never listens for a conversation the person is no longer in. Draws nothing.
     */
    @Composable public fun StopWhenLeft(field: KitVoiceField) {}

    /** Optional voice mode entry in the visible + tray, without an idle row above the field. */
    @Composable
    public fun Options(field: KitVoiceField, modifier: Modifier, onSelected: () -> Unit) {}
}

/**
 * How the message box hosts a dictation (kit-chat `KitComposerVoice.recording`).
 *
 * [held]: the finger is on the mic (hold to talk). The mic stays exactly where the hold began, so the touch is never
 * cut; the recorder row shows only what letting go does.
 *
 * [sendNow]: the box's own Send while a tapped dictation runs: stop, write the words into the box and send them once
 * through the composer's one send. Null while it cannot (the words are already being written down).
 */
@androidx.compose.runtime.Immutable
public class KitVoiceRecording(
    public val held: Boolean,
    public val sendNow: (() -> Unit)?,
    /** What Send says while it means "write down and send" (its spoken label). */
    public val sendLabel: String,
    /**
     * True while words are dictated into the box: Send then means "write down and send" ([sendNow]). False for the
     * hands-free voice mode and "Read aloud", which show the same row while Send keeps its usual meaning.
     */
    public val dictating: Boolean = true,
)

/**
 * A reply the person asked to hear (the message menu's "Read aloud"). [id] changes on every ask.
 */
public data class KitReadRequest(
    val id: Long,
    val rowKey: String,
    val text: String,
    /** S28: "Read aloud with another voice" (Flutter `chooseVoice: true`): the reader asks which voice first. */
    val chooseVoice: Boolean = false,
)

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
