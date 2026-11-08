package io.github.eslamasabry.oc.feature.chat.nudges

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.engine.settings.teaching.ActiveNudge
import io.github.eslamasabry.oc.engine.settings.teaching.ConversationNudgeFacts
import io.github.eslamasabry.oc.engine.settings.teaching.ConversationNudgeWatcher
import io.github.eslamasabry.oc.engine.settings.teaching.NudgeId
import io.github.eslamasabry.oc.engine.settings.teaching.NudgeRegistry
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * S28 story 14: the app's one [NudgeRegistry] (Flutter `_conn.nudges`), when the app has bound one. The default has
 * none, so the chat shows no nudge; the app graph replaces it with the registry over the Flutter prefs
 * (`oc.nudges.*`).
 */
public fun interface ChatNudgeSource {
    public suspend fun registry(): NudgeRegistry?
}

@Inject
@ContributesBinding(AppScope::class)
public class NoChatNudges : ChatNudgeSource {
    override suspend fun registry(): NudgeRegistry? = null
}

/**
 * What the conversation knows that the nudge rules read (Flutter `_observeNudges`, nudge_slot.dart), in plain
 * values. [pendingPermissions] is request id → permission kind for the asks waiting now.
 */
public data class ChatNudgeInputs(
    val busy: Boolean,
    val hasReply: Boolean,
    val pendingPermissions: ImmutableMap<String, String> = persistentMapOf(),
    val approvalsAvailable: Boolean = false,
    val runChangedFiles: Boolean = false,
    val reviewAvailable: Boolean = false,
    /** Percent of the context window in use, 0-100; null when unknown. */
    val contextPercent: Int? = null,
    val compactAvailable: Boolean = false,
    /** A request card or form owns the space above the composer: nothing is offered, nothing is consumed. */
    val suppressed: Boolean = false,
) {
    /** The engine's facts. "Leave and be told" needs a finished-run notification that would reach the person: the
     * chat cannot know that yet, so it is never offered here. */
    public fun facts(): ConversationNudgeFacts =
        ConversationNudgeFacts(
            busy = busy,
            hasAssistantReply = hasReply,
            pendingPermissions = pendingPermissions,
            approvalsAvailable = approvalsAvailable,
            runChangedFiles = runChangedFiles,
            reviewAvailable = reviewAvailable,
            contextUsage = contextPercent?.let { it / 100.0 },
            compactAvailable = compactAvailable,
            finishedRunNotificationsReady = false,
            suppressed = suppressed,
        )
}

/**
 * One open conversation's nudges (B-NAV-038/039): the engine's watcher turns its facts into offers on the one
 * registry; [active] is the card for this conversation, if it holds the one slot. Words and actions are the
 * screen's.
 */
public class ChatNudgeModel(
    private val registry: NudgeRegistry,
    private val conversation: String,
    private val scope: CoroutineScope,
) {
    private val watcher = ConversationNudgeWatcher(registry, conversation, scope)

    public val active: Flow<ActiveNudge?> = registry.active.map { it?.takeIf { a -> a.scope == conversation } }

    public fun observe(inputs: ChatNudgeInputs) {
        watcher.observe(inputs.facts())
    }

    /** The person closed the card or took its action: it never shows again. */
    public fun done(id: NudgeId) {
        scope.launch { registry.dismiss(id) }
    }

    public fun close() {
        watcher.dispose()
    }
}
