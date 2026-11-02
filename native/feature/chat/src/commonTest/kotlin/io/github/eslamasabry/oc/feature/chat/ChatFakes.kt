package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.MessageId
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.engine.transcript.state.NewestFirst
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.NoticeRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.ThoughtRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatLoad
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.DraftFacts
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

val Ref: ConversationRef = ConversationRef(ProfileId("p1"), SessionId("s1"))

/** Runs a command's suspending half in place: the fakes never suspend, so it completes before `launch` returns. */
val Immediate: ChatLauncher = ChatLauncher { block -> block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() }) }

/** Rows are given OLDEST first, as a reader sees them; the state holds them newest first (plan 09 §7.1). */
fun stateOf(vararg oldestFirst: TranscriptRow, run: RunFacts = RunFacts(), revision: Long = 1): TranscriptState =
    TranscriptState(
        revision = revision,
        rows = NewestFirst(oldestFirst.toList().toPersistentList()),
        positions = oldestFirst.mapIndexed { i, r -> r.key to i }.toMap().toPersistentMap(),
        run = run,
    )

fun prompt(message: String, text: String): PromptRow = PromptRow("prompt:$message", message, text)

fun block(turn: String, message: String, part: String, start: Int, text: String, live: Boolean = false): BlockRow =
    BlockRow("b:$message/$part:$start", turn, RowKind.Prose, part, start, text, live, false, Origin.Loaded)

fun thought(turn: String, message: String, part: String, line: String): ThoughtRow = ThoughtRow("thought:$message/$part", turn, line)

fun notice(turn: String, part: String): NoticeRow = NoticeRow("notice:$part", turn, NoticeKind.Other)

fun footer(turn: String, failed: Boolean = false): FooterRow = FooterRow("footer:$turn", turn, failed)

/** One finished turn: prompt `u<n>`, reply `a<n>`. */
fun turn(n: Int, ask: String = "ask $n", answer: String = "answer $n", failed: Boolean = false): List<TranscriptRow> =
    listOf(prompt("u$n", ask), block("u$n", "a$n", "p$n", 0, answer), footer("u$n", failed))

class RecordingNavigator : AppNavigator {
    val calls = mutableListOf<String>()
    override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

    override fun open(route: AppRoute) {
        calls += "open"
    }

    override fun openChat(ref: ConversationRef, focusComposer: Boolean) {
        calls += "openChat ${ref.sessionId.raw}"
    }

    override fun land() {
        calls += "land"
    }

    override fun replaceRoot(root: AppRoute) {
        calls += "replaceRoot"
    }

    override fun back() {
        calls += "back"
    }
}

/** A scripted [ChatWorld]: flows the test sets, commands recorded, results the test chooses. */
open class FakeChatWorld(override val ref: ConversationRef = Ref) : ChatWorld {
    override val transcript = MutableStateFlow(TranscriptState.Empty)
    override val load = MutableStateFlow<ChatLoad>(ChatLoad.Live)
    override val title = MutableStateFlow<String?>("Fix the build")
    override val capabilities = MutableStateFlow(ServerCapabilities())
    override val link = MutableStateFlow(ChatLink.Live)
    override val pending = MutableStateFlow<ImmutableList<PendingSend>>(persistentListOf())
    override val draft = MutableStateFlow(DraftFacts())
    override val work = EmptyCoroutineContext

    val calls = mutableListOf<String>()
    var saveResult: Outcome<Unit> = Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))
    var forkResult: Outcome<SessionId> = Outcome.Ok(SessionId("s2"))
    var deleteResult: Outcome<Unit> = Outcome.Ok(Unit)
    var stopResult: Outcome<Unit> = Outcome.Ok(Unit)
    var reads = 0

    override fun draftChanged(text: String) = draft.update { it.copy(text = text) }

    override suspend fun saveDraft(): Outcome<Unit> {
        calls += "saveDraft"
        if (saveResult is Outcome.Ok) draft.update { it.copy(saved = it.text) }
        return saveResult
    }

    override suspend fun send(text: String): Outcome<Unit> {
        calls += "send $text"
        return Outcome.Ok(Unit)
    }

    override suspend fun retrySend(localId: Long): Outcome<Unit> {
        calls += "retrySend $localId"
        return Outcome.Ok(Unit)
    }

    override suspend fun stop(): Outcome<Unit> {
        calls += "stop"
        return stopResult
    }

    override suspend fun reload(): Outcome<Unit> {
        calls += "reload"
        return Outcome.Ok(Unit)
    }

    override suspend fun fork(from: MessageId): Outcome<SessionId> {
        calls += "fork ${from.raw}"
        return forkResult
    }

    override suspend fun deleteMessage(id: MessageId): Outcome<Unit> {
        calls += "delete ${id.raw}"
        return deleteResult
    }

    override fun markRead() {
        reads++
    }
}
