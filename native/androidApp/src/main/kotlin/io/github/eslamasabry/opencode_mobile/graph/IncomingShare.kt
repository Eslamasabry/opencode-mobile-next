package io.github.eslamasabry.opencode_mobile.graph

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.NewSession
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.drafts.DraftSession
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Only the phase leaves this owner. Shared words never appear in diagnostics or UI state. */
internal enum class IncomingSharePhase {
    Empty,
    Waiting,
    Opening,
    Failed,
    FailedAgain,
    Discarding,
}

/** One incoming share, owned by the existing platform router. No prompt is sent automatically. */
internal class IncomingShare(
    private val context: Context,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val runtime: PreviewRuntime,
    private val drafts: Set<ConversationDrafts>,
    private val navigator: AppNavigator,
) {
    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.main)
    private val mutablePhase = MutableStateFlow(IncomingSharePhase.Empty)
    val phase: StateFlow<IncomingSharePhase> = mutablePhase
    private var text: String? = null
    private var version = 0L
    private var opening = false
    private var failures = 0
    private var target: ConversationRef? = null
    private var draft: DraftSession? = null
    private var prepared: String? = null
    private var beforeDiscard = IncomingSharePhase.Failed

    init {
        scope.launch {
            combine(registry.live, runtime.busy, stores.profiles.activeId) { _, _, _ -> Unit }
                .collect { openIfReady() }
        }
    }

    fun arrived(words: String) {
        version++
        text = words
        target = null
        draft = null
        prepared = null
        failures = 0
        mutablePhase.value = IncomingSharePhase.Waiting
        openIfReady()
    }

    fun retry() {
        if (mutablePhase.value !in setOf(IncomingSharePhase.Failed, IncomingSharePhase.FailedAgain))
            return
        mutablePhase.value = IncomingSharePhase.Waiting
        openIfReady()
    }

    fun copy() {
        val words = text ?: return
        context
            .getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("", words))
    }

    fun discard() {
        if (opening || text == null) return
        val now = mutablePhase.value
        if (now == IncomingSharePhase.Discarding || now == IncomingSharePhase.Empty) return
        beforeDiscard = now
        mutablePhase.value = IncomingSharePhase.Discarding
    }

    /** Undo restores what was shown before Discard; it is not counted as another failure. */
    fun undoDiscard() {
        if (mutablePhase.value != IncomingSharePhase.Discarding) return
        mutablePhase.value = beforeDiscard
        if (beforeDiscard == IncomingSharePhase.Waiting) openIfReady()
    }

    fun commitDiscard() {
        if (mutablePhase.value != IncomingSharePhase.Discarding) return
        version++
        text = null
        target = null
        draft = null
        prepared = null
        mutablePhase.value = IncomingSharePhase.Empty
    }

    private fun failed() {
        failures++
        mutablePhase.value =
            if (failures > 1) IncomingSharePhase.FailedAgain else IncomingSharePhase.Failed
    }

    private fun openIfReady() {
        if (opening || mutablePhase.value != IncomingSharePhase.Waiting || runtime.busy.value)
            return
        val words = text ?: return
        val profile = target?.profileId ?: stores.profiles.activeId.value ?: return
        if (stores.profiles.activeId.value != profile) return
        val graph = registry.live.value[profile] ?: return
        val saved = stores.profiles.profile(profile)?.profile ?: return
        val location = graph.session.server.location.value
        val generation = version
        fun current(): Boolean =
            version == generation &&
                registry.live.value[profile] === graph &&
                stores.profiles.activeId.value == profile &&
                stores.profiles.profile(profile)?.profile == saved &&
                graph.session.server.location.value == location
        opening = true
        mutablePhase.value = IncomingSharePhase.Opening
        scope.launch {
            try {
                val result =
                    attempt({ Failure.Unexpected() }) {
                        if (!current()) return@attempt false
                        val ref =
                            target
                                ?: when (
                                    val created = graph.session.server.createSession(NewSession())
                                ) {
                                    is Outcome.Failed -> return@attempt false
                                    is Outcome.Ok ->
                                        ConversationRef(profile, created.value.id).also {
                                            if (current()) target = it
                                        }
                                }
                        if (!current()) return@attempt false
                        val owner = drafts.singleOrNull() ?: return@attempt false
                        val session = draft ?: owner.open(ref).also { draft = it }
                        val ready =
                            withTimeoutOrNull(10_000) { session.state.first { it.ready } }
                                ?: return@attempt false
                        if (!current()) return@attempt false
                        val previous = prepared
                        if (previous == null) {
                            val merged =
                                if (ready.text.isBlank()) words
                                else ready.text.trimEnd() + "\n\n" + words
                            prepared = merged
                            session.changed(merged)
                        } else if (ready.text != previous) {
                            // The person edited the draft after a failed save. Never overwrite that
                            // edit or append twice.
                            return@attempt false
                        }
                        if (session.flush() !is Outcome.Ok || !current()) return@attempt false
                        text = null
                        target = null
                        draft = null
                        prepared = null
                        mutablePhase.value = IncomingSharePhase.Empty
                        navigator.openChat(ref)
                        true
                    }
                if (version == generation && (result !is Outcome.Ok || !result.value)) failed()
            } finally {
                opening = false
                // A newer share that arrived during a call owns the next attempt.
                if (version != generation) openIfReady()
            }
        }
    }
}
