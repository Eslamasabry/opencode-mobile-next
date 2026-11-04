package io.github.eslamasabry.oc.feature.computer

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.ImmutableList

/**
 * One Paseo computer's tools (Flutter host-update row + `ClaudeImportScreen`), bound by the app over the live
 * connection graph. Every call connects first when the computer is not connected yet; none retries on its own.
 */
public interface ComputerTools {
    /** The saved name, or null once the computer was removed. */
    public fun name(profile: ProfileId): String?

    /** The agent helper version the computer reported, or null when it did not say. */
    public suspend fun version(profile: ProfileId): Outcome<String?>

    /** Updates the helper there (B-AGENT-167). [onStep] gets each phase in order; the call ends with the answer. */
    public suspend fun update(profile: ProfileId, onStep: (UpdateStep) -> Unit): Outcome<UpdateAnswer>

    /** Claude Code conversations of the computer's project that are not here yet, newest first (B-AGENT-174). */
    public suspend fun importable(profile: ProfileId): Outcome<ImportList>

    /** Brings one across and returns the conversation to open; a refusal is a failure, never a conversation. */
    public suspend fun import(profile: ProfileId, item: ImportItem): Outcome<ConversationRef>
}

public enum class UpdateStep { Starting, Downloading, Installing, Finishing }

public sealed interface UpdateAnswer {
    /** Updated; [version] is the new one when the computer named it. */
    public data class Done(val version: String?) : UpdateAnswer

    /** Nothing newer was there. */
    public data object Current : UpdateAnswer

    /** The computer explained it could not update. Its own words are never shown. */
    public data object Refused : UpdateAnswer
}

/** One importable conversation. [whenLabel] is already formatted by the platform (a relative time). */
public data class ImportItem(val key: String, val title: String?, val folder: String, val whenLabel: String)

public data class ImportList(val project: String, val items: ImmutableList<ImportItem>, val alreadyHere: Int)
