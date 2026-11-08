package io.github.eslamasabry.oc.feature.chat.composer

import io.github.eslamasabry.oc.core.model.PromptAttachment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier

/*
 * The composer slot (plan 09 §6.3, conflict C2). Its home is `:ui:kit-chat` `…host` (a chat-lead file there); it sits
 * here in slice 2 because this unit's write set is the chat feature folder only. It MUST move to `:ui:kit-chat` before
 * S3-COMP-UI, since `:feature:chat-composer` may not import this feature (K19). Request K-1 in the S2-CHAT report.
 * Plain ids and lambdas only: no engine type, no ViewModel.
 */

/** Draws the composer for one conversation inside the chat's bottom slot. */
public interface ComposerHost {
    @Composable
    public fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier)

    /** S24-CHAT: the composer can open the full-screen prompt editor ([ComposerChrome.editorRequest]). */
    public val hasPromptEditor: Boolean
        get() = false
}

/** Plain ids: the profile, and the session (null = a new conversation that has none yet). */
@Immutable public data class ComposerTarget(val profileId: String, val sessionId: String?)

/**
 * Words waiting for the composer (state, never a one-shot flow; ADR §9.1): a saved prompt, a slash
 * command or an agent ```choices tap go after the person's words; [replace] (P12-CHAT-FEEL "Edit
 * and resend") takes the field whole.
 */
@Immutable
public data class InsertRequest(
    val id: Long,
    val text: String,
    val replace: Boolean = false,
    /**
     * A starter belongs only to an untouched field. A late starter never overwrites or appends to
     * typing.
     */
    val onlyIfEmpty: Boolean = false,
    val attachments: List<PromptAttachment> = emptyList(),
    val missingAttachments: List<String> = emptyList(),
    val stillCurrent: () -> Boolean = { true },
) {
    override fun toString(): String =
        "InsertRequest(id=$id, textChars=${text.length}, replace=$replace, attachments=${attachments.size}, missing=${missingAttachments.size})"
}

/**
 * What the chat hands the composer. Every getter is read in draw, layout or an effect, so a change
 * never recomposes the chat. Slice-2 additions to the plan's shape (request K-1):
 * [focusRequest]/[focusDone] carry the "ready to type" arrival (07 §2.5, B-NAV-005);
 * [onStop]/[stopping] keep Stop in the composer's row while the chat owns the stop and its live
 * line (plan 09 §4.4).
 */
public class ComposerChrome(
    public val busy: () -> Boolean,
    public val requestWaiting: () -> Boolean,
    public val onSent: () -> Unit,
    public val pendingInsert: () -> InsertRequest?,
    public val insertDone: (id: Long) -> Unit,
    public val focusRequest: () -> Long?,
    public val focusDone: (id: Long) -> Unit,
    public val onStop: () -> Unit,
    public val stopping: () -> Boolean,
    /**
     * S24-CHAT story 6: what holding Stop does now ("Stop after this step"), or null when the hold
     * offers nothing (no steering, a note already went).
     */
    public val stopAfterStep: () -> (() -> Unit)? = { null },
    /** S24-CHAT story 4: the commands launcher's `/editor` asks the composer to open its editor once. */
    public val editorRequest: () -> Long? = { null },
    public val editorDone: (id: Long) -> Unit = {},
    /**
     * Chips another part folds into the composer's own row (the approval mode; P12 CHAT-HEADER).
     * Never a new bar.
     */
    public val rowChips: @Composable () -> Unit = {},
    public val freeModel: () -> Boolean = { false },
    public val agentName: () -> String = { "OpenCode" },
    /** An error card's "Choose another model" asks the composer to open its model list once. */
    public val modelPickerRequest: () -> Long? = { null },
    public val modelPickerDone: (id: Long) -> Unit = {},
    /** The commands launcher (a Commands row in the attach tray); null = not offered. */
    public val openCommands: (() -> Unit)? = null,
    /**
     * Saved prompts (B-151): a row in the attach tray, where 2.1.4 kept them ("Attach and more ›
     * Prompts › Saved prompts"); null = not offered.
     */
    public val openSavedPrompts: (() -> Unit)? = null,
    /** Send later or repeat for the words given (Hold Send, and its visible tray twin). */
    public val sendLater: ((words: String) -> Unit)? = null,
    /** A new id when a Send later schedule was just saved from this chat: clear the draft and say so once. */
    public val laterScheduled: () -> Long? = { null },
    public val laterShown: (id: Long) -> Unit = {},
    /**
     * N-55: the chat's hook for a stray hardware key (typed while nothing had focus): the composer
     * registers how to take focus; the chat steers focus there.
     */
    public val typing: ComposerTypingTarget = ComposerTypingTarget(),
    /**
     * This conversation's own settings, drawn under the Model row at the top of the "+" sheet (Flutter
     * `_composerSettings`, chat_composer_region.dart:118-180: Auto-approve, Approval settings…); [close] closes the
     * sheet. Null = none.
     */
    public val promptSettings: (@Composable (close: () -> Unit) -> Unit)? = null,
)

/** N-55: how the message field takes focus, registered by the composer while it is shown. */
public class ComposerTypingTarget {
    internal var focus: (() -> Boolean)? = null

    /** True when the message field took focus. */
    public fun focus(): Boolean = focus?.invoke() ?: false
}
