package io.github.eslamasabry.oc.feature.chat.header.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.transcript.Role
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCli
import io.github.eslamasabry.oc.engine.agents.resume.SessionResumeCommand
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.engine.transcript.turnfacts.contextReading
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModel
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeModelFacts
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeNoteStore
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHeaderPort
import io.github.eslamasabry.oc.feature.chat.header.world.HeaderFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update

/** What only the header knows (open menu, sheet, confirm, running act, clip). */
internal data class HeaderLocal(
    val menuOpen: Boolean = false,
    val sheet: SheetKind? = null,
    val confirm: HeaderConfirm? = null,
    val working: HeaderAction? = null,
    val failure: HeaderFailure? = null,
    val clip: HeaderClip? = null,
    val renaming: String? = null,
    /**
     * A rename the server acknowledged before its event arrived: shown until the server says
     * otherwise.
     */
    val renamed: String? = null,
    /**
     * The server's title when [renamed] was acknowledged; once the server names it otherwise, the
     * server wins.
     */
    val renamedOver: String? = null,
    val agentChoices: List<io.github.eslamasabry.oc.feature.chat.header.world.AgentHandoffChoice> =
        emptyList(),
    val handoffDraft: io.github.eslamasabry.oc.engine.agents.handoff.HandoffDraft? = null,
    val handoffFailure: io.github.eslamasabry.oc.engine.agents.handoff.HandoffFailureKind? = null,
    val handoffLoading: Boolean = false,
    val continueFailure: io.github.eslamasabry.oc.core.common.Failure? = null,
    val freeFacts: FreeModelFacts? = null,
    val freeDismissed: Boolean = false,
    val handoffState: io.github.eslamasabry.oc.engine.agents.handoff.HandoffState? = null,
    val handoffLabel: String? = null,
    val importState: io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportUi? = null,
)

internal enum class SheetKind {
    Details,
    ContinueWith,
    ContinueOnComputer,
    ImportConversation,
    Handoff,
}

/**
 * The header's state holder (plain `kotlin.test`, no ViewModel): [current] presents the present
 * values; [states] is the production flow. Acts go to the conversation's own port; a failure
 * becomes one plain line with Details.
 */
public class ChatHeaderPresenter(
    private val world: ChatWorld,
    private val port: ChatHeaderPort,
    private val nav: AppNavigator,
    private val launcher: ChatLauncher,
    private val freeNotes: FreeNoteStore? = null,
    private val handoffs: io.github.eslamasabry.oc.feature.chat.header.world.ChatHandoffs? = null,
    /** The connected daemon's agent handoff (reviewed record, files, then a new conversation). */
    private val agentHandoffs: io.github.eslamasabry.oc.feature.chat.header.world.ChatAgentHandoff? =
        null,
) : ChatHeaderActions, ChatAgentHandoffActions {
    private val local = MutableStateFlow(HeaderLocal())
    private var nextId = 1L
    private var handoff: io.github.eslamasabry.oc.engine.agents.handoff.HandoffEngine? = null
    private val handoffGeneration = MutableStateFlow(0L)
    private var importer:
        io.github.eslamasabry.oc.engine.agents.sessionimport.SessionImportEngine? =
        null

    public val ref: ConversationRef
        get() = world.ref

    public fun current(): ChatHeaderUi =
        present(
            world.title.value,
            world.capabilities.value,
            port.facts.value,
            world.transcript.value,
            world.models.value,
            local.value,
        )

    public fun states(scope: CoroutineScope, started: SharingStarted): StateFlow<ChatHeaderUi> {
        val serverSide =
            combine(world.title, world.capabilities, port.facts, world.transcript, world.models) {
                t,
                c,
                f,
                tr,
                m ->
                ServerSide(t, c, f, tr, m)
            }
        return combine(serverSide, local, world.readOnly) { s, l, _ ->
                present(s.title, s.caps, s.facts, s.transcript, s.models, l)
            }
            .stateIn(scope, started, current())
            .also { launcher.launch { loadFreeFacts() } }
    }

    private suspend fun loadFreeFacts() {
        val facts = (port.freeModelFacts() as? Outcome.Ok)?.value ?: return
        local.update { it.copy(freeFacts = facts) }
    }

    private fun freeNoteDue(facts: HeaderFacts, models: ComposerModels, l: HeaderLocal): Boolean {
        val free = l.freeFacts ?: return false
        if (l.freeDismissed) return false
        val provider = models.selected?.providerId ?: facts.providerId
        val model = models.selected?.modelId ?: facts.model
        if (!FreeModel.uses(free.signedIn, provider, model, free.models)) return false
        return freeNotes?.dismissed(world.ref.profileId, world.ref.sessionId.raw) != true
    }

    override fun dismissFreeNote() {
        local.update { it.copy(freeDismissed = true) }
        val store = freeNotes ?: return
        launcher.launch { store.dismiss(world.ref.profileId, world.ref.sessionId.raw) }
    }

    private data class ServerSide(
        val title: String?,
        val caps: ServerCapabilities,
        val facts: HeaderFacts,
        val transcript: TranscriptState,
        val models: ComposerModels,
    )

    internal fun present(
        serverTitle: String?,
        caps: ServerCapabilities,
        facts: HeaderFacts,
        transcript: TranscriptState,
        models: ComposerModels,
        l: HeaderLocal,
    ): ChatHeaderUi {
        val project = HeaderRules.projectName(facts.directory)
        val title = shownTitle(serverTitle, l)
        val sheet =
            when (l.sheet) {
                null -> null
                SheetKind.Details -> {
                    // Codex chat-header #8: OC2 sessions carry no context reading; the replies' own
                    // tokens against
                    // their model's window do (Flutter derives it the same way, as the transcript's
                    // context action).
                    val feed = (world as? TurnFeedSource)?.turnFeed?.value
                    val reading =
                        if (facts.contextTokens != null || feed == null) null
                        else
                            contextReading(feed.ledger.messages) { p, m ->
                                feed.windows[TurnFeed.windowKey(p, m)]
                            }
                    val percent =
                        HeaderRules.contextPercent(
                            facts.contextTokens ?: reading?.tokens,
                            facts.contextLimit ?: reading?.limit,
                        )
                    val replyTokens =
                        feed
                            ?.ledger
                            ?.messages
                            ?.filter { it.info.role == Role.Assistant }
                            ?.sumOf { it.info.tokens.total }
                    HeaderSheet.Details(
                        DetailsUi(
                            serverName = facts.serverName,
                            projectName = project,
                            model = models.selected?.label ?: facts.model,
                            agent = models.agent ?: facts.agent,
                            contextPercent = percent,
                            contextLevel = HeaderRules.contextLevel(percent),
                            totalTokens = (facts.totalTokens ?: replyTokens)?.takeIf { it > 0 },
                        )
                    )
                }
                SheetKind.ContinueWith ->
                    HeaderSheet.ContinueWith(
                        choices = continueChoices(models),
                        loading =
                            l.handoffLoading ||
                                models.loading ||
                                (models.choices.isEmpty() &&
                                    models.failure == null &&
                                    l.continueFailure == null),
                        failure = l.continueFailure ?: models.failure,
                        agents = l.agentChoices,
                        draft = l.handoffDraft,
                        handoffFailure = l.handoffFailure,
                    )
                SheetKind.Handoff ->
                    HeaderSheet.Handoff(
                        continueChoices(models),
                        models.loading || (models.choices.isEmpty() && models.failure == null),
                        models.failure,
                        l.handoffLabel,
                        l.handoffState,
                    )
                SheetKind.ContinueOnComputer -> HeaderSheet.ContinueOnComputer(resumeCommand(facts))
                SheetKind.ImportConversation ->
                    l.importState?.let { HeaderSheet.ImportConversation(it) }
            }
        return ChatHeaderUi(
            branch = facts.branch,
            title = title,
            serverName = facts.serverName,
            projectName = project,
            menu =
                HeaderRules.menu(caps, shared = facts.shareUrl != null)
                    .filter {
                        world.readOnly.value == null ||
                            it in
                                setOf(
                                    HeaderAction.Find,
                                    HeaderAction.Details,
                                    HeaderAction.CopyTranscript,
                                )
                    }
                    .toImmutableList(),
            menuOpen = l.menuOpen,
            hasPrompt = transcript.rows.any { it is PromptRow },
            sheet = sheet,
            confirm = l.confirm,
            working = l.working,
            failure = l.failure,
            clip = l.clip,
            renaming = l.renaming,
            freeModelNote = freeNoteDue(facts, models, l),
        )
    }

    private fun shownTitle(serverTitle: String?, l: HeaderLocal): String? =
        if (l.renamed != null && serverTitle == l.renamedOver) l.renamed else serverTitle

    private fun titleNow(): String? = shownTitle(world.title.value, local.value)

    /**
     * "Models on this server": the current one first, marked; at most eight others (Flutter caps
     * the list at six picks).
     */
    private fun continueChoices(
        models: ComposerModels
    ): kotlinx.collections.immutable.ImmutableList<ContinueChoice> {
        val current = models.selected
        val others = models.choices.filter { it.enabled && !same(it, current) }.take(MAX_CONTINUE)
        return (listOfNotNull(current) + others)
            .map {
                ContinueChoice(it.providerId, it.modelId, it.label, current = same(it, current))
            }
            .toImmutableList()
    }

    private fun same(a: ComposerModel, b: ComposerModel?): Boolean =
        b != null && a.providerId == b.providerId && a.modelId == b.modelId

    override fun openMenu() {
        local.update { it.copy(menuOpen = true) }
    }

    override fun closeMenu() {
        local.update { it.copy(menuOpen = false) }
    }

    override fun choose(action: HeaderAction) {
        local.update { it.copy(menuOpen = false) }
        when (action) {
            HeaderAction.AgentHandoff ->
                nav.open(io.github.eslamasabry.oc.ui.routes.AgentHandoffRoute(ref))
            HeaderAction.CardsSettings ->
                nav.open(io.github.eslamasabry.oc.ui.routes.CardsSettingsRoute)
            HeaderAction.AgentOptions ->
                nav.open(
                    io.github.eslamasabry.oc.ui.routes.AgentToolsRoute(ref.profileId, ref.sessionId)
                )
            HeaderAction.Find -> Unit // the chat screen's own find bar
            HeaderAction.Details -> {
                local.update { it.copy(sheet = SheetKind.Details) }
                launcher.launch { port.refresh() }
            }
            HeaderAction.Rename -> renameRequested()
            HeaderAction.ContinueWith -> {
                local.update { it.copy(sheet = SheetKind.ContinueWith, continueFailure = null) }
                if (world.models.value.choices.isEmpty()) loadModels()
                loadHandoffChoices()
            }
            HeaderAction.ContinueOnComputer -> {
                // Built from the folder the server reports now (Flutter showSessionHandoff reads
                // the session first).
                local.update { it.copy(sheet = SheetKind.ContinueOnComputer) }
                launcher.launch { port.refresh() }
            }
            HeaderAction.ImportConversation -> openImport()
            HeaderAction.Handoff -> {
                closeSheet()
                local.update { it.copy(sheet = SheetKind.Handoff) }
                if (world.models.value.choices.isEmpty()) loadModels()
            }
            HeaderAction.CopyTranscript -> Unit // needs the person's words: [copyTranscript]
            HeaderAction.Share -> {
                if (world.capabilities.value.sessionShare && local.value.working == null) {
                    local.update { it.copy(confirm = HeaderConfirm.Share) }
                }
            }
            HeaderAction.Unshare -> {
                if (world.capabilities.value.sessionShare && local.value.working == null) {
                    local.update { it.copy(confirm = HeaderConfirm.Unshare) }
                }
            }
            HeaderAction.Fork ->
                run(action) {
                    when (val r = port.fork()) {
                        is Outcome.Ok -> {
                            nav.openChat(ConversationRef(world.ref.profileId, r.value))
                            Outcome.Ok(Unit)
                        }
                        is Outcome.Failed -> r
                    }
                }
            HeaderAction.Archive ->
                local.update { it.copy(confirm = HeaderConfirm.Archive(titleNow())) }
            HeaderAction.Delete ->
                local.update { it.copy(confirm = HeaderConfirm.Delete(titleNow())) }
        }
    }

    /**
     * B-AGENT-227/228: the resume command for this conversation. The CLI follows the server's
     * product generation (an OpenCode 2 server, the only one that reports `cliSessionResume`); the
     * menu row is gated on that capability.
     */
    private fun resumeCommand(facts: HeaderFacts): SessionResumeCommand =
        SessionResumeCommand.build(
            cli = SessionResumeCli.OpenCode2,
            sessionId = world.ref.sessionId.raw,
            directory = facts.directory,
            workspaceId = facts.workspaceId,
        )

    override fun copyResumeCommand() {
        val text = resumeCommand(port.facts.value).command ?: return
        local.update { it.copy(clip = HeaderClip(nextId++, text, ClipKind.ResumeCommand)) }
    }

    /** Copy transcript with the person's own words for the headings. */
    override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) {
        local.update { it.copy(menuOpen = false) }
        val messages = (world as? TurnFeedSource)?.turnFeed?.value?.ledger?.messages.orEmpty()
        val text =
            if (messages.isNotEmpty())
                TranscriptCopy.markdownOf(titleNow(), messages, words, world.hasOlder.value, mask)
            else
                TranscriptCopy.markdown(
                    title = titleNow(),
                    rows = world.transcript.value.rows,
                    words = words,
                    partial = world.hasOlder.value,
                    mask = mask,
                )
        local.update { it.copy(clip = HeaderClip(nextId++, text, ClipKind.Transcript)) }
    }

    override fun renameRequested() {
        local.update { it.copy(menuOpen = false, renaming = titleNow() ?: "") }
    }

    override fun renameDone(title: String?) {
        local.update { it.copy(renaming = null) }
        val next = title?.trim() ?: return
        if (!HeaderRules.renameValid(next) || next == (titleNow())) return
        run(HeaderAction.Rename) {
            val over = world.title.value
            port.rename(next).also { r ->
                if (r is Outcome.Ok) local.update { it.copy(renamed = next, renamedOver = over) }
            }
        }
    }

    override fun closeSheet() {
        handoffGeneration.value += 1
        handoff = null
        agentHandoffs?.close()
        importer?.close()
        importer = null
        local.update {
            it.copy(
                sheet = null,
                continueFailure = null,
                importState = null,
                handoffState = null,
                handoffLabel = null,
                handoffDraft = null,
                handoffFailure = null,
            )
        }
    }

    override fun handoffSelect(choice: ContinueChoice) {
        if (handoff != null) return
        val generation = handoffGeneration.value
        val engine =
            handoffs?.open(
                world.ref,
                io.github.eslamasabry.oc.core.model.ModelRef(choice.providerId, choice.modelId),
                choice.label,
                { handoffGeneration.value == generation },
            )
        if (engine == null) {
            local.update {
                it.copy(
                    handoffState =
                        io.github.eslamasabry.oc.engine.agents.handoff.HandoffState(
                            phase =
                                io.github.eslamasabry.oc.engine.agents.handoff.HandoffPhase.Failed,
                            failure =
                                io.github.eslamasabry.oc.engine.agents.handoff.HandoffFailureKind
                                    .Unavailable,
                        )
                )
            }
            return
        }
        handoff = engine
        local.update { it.copy(handoffState = engine.state.value, handoffLabel = choice.label) }
        launcher.launch {
            combine(engine.state, handoffGeneration) { state, current -> state to current }
                .takeWhile { it.second == generation }
                .collect { (state, _) ->
                    if (handoff === engine) local.update { it.copy(handoffState = state) }
                }
        }
        launcher.launch {
            engine.prepare("Source conversation; confirm current project state before continuing")
        }
    }

    override fun handoffSend(prompt: String) {
        handoff?.let { engine -> launcher.launch { engine.send(prompt) } }
    }

    override fun handoffRetryLink() {
        handoff?.let { engine -> launcher.launch { engine.retryLink() } }
    }

    override fun handoffOpen() {
        val engine = handoff ?: return
        val id = engine.completedSessionId() ?: return
        closeSheet()
        nav.openChat(
            ConversationRef(
                world.ref.profileId,
                io.github.eslamasabry.oc.core.common.ids.SessionId(id),
            )
        )
    }

    private fun openImport() {
        closeSheet()
        importer?.close()
        val engine = port.openImport() ?: return
        importer = engine
        local.update {
            it.copy(sheet = SheetKind.ImportConversation, importState = engine.state.value)
        }
        launcher.launch {
            engine.state
                .takeWhile { !it.closed }
                .collect { value ->
                    if (importer === engine) local.update { it.copy(importState = value) }
                }
        }
    }

    override fun importChoose() {
        importer?.let { engine -> launcher.launch { engine.choose() } }
    }

    override fun importReview(text: String) {
        importer?.let { engine -> launcher.launch { engine.review(text) } }
    }

    override fun importDestination(directory: String, workspace: String) {
        importer?.destination(directory, workspace)
    }

    override fun importSend() {
        importer?.let { engine -> launcher.launch { engine.send() } }
    }

    override fun importOpen() {
        val engine = importer ?: return
        val session = engine.state.value.imported ?: return
        if (!engine.mayOpen()) return
        closeSheet()
        nav.openChat(ConversationRef(world.ref.profileId, session))
    }

    override fun continueWith(choice: ContinueChoice) {
        if (choice.current) {
            closeSheet()
            return
        }
        val model =
            world.models.value.choices.firstOrNull {
                it.enabled && it.providerId == choice.providerId && it.modelId == choice.modelId
            } ?: return
        local.update { it.copy(working = HeaderAction.ContinueWith, continueFailure = null) }
        launcher.launch {
            when (val r = world.selectModel(model)) {
                is Outcome.Ok -> local.update { it.copy(working = null, sheet = null) }
                is Outcome.Failed ->
                    local.update { it.copy(working = null, continueFailure = r.failure) }
            }
        }
    }

    private fun loadHandoffChoices() {
        val host = agentHandoffs ?: return
        local.update { it.copy(handoffLoading = true) }
        launcher.launch {
            when (val r = host.choices()) {
                is Outcome.Ok ->
                    local.update { it.copy(agentChoices = r.value, handoffLoading = false) }
                is Outcome.Failed ->
                    local.update { it.copy(continueFailure = r.failure, handoffLoading = false) }
            }
        }
    }

    override fun prepareHandoff(
        choice: io.github.eslamasabry.oc.feature.chat.header.world.AgentHandoffChoice
    ) {
        val host = agentHandoffs ?: return
        if (local.value.working != null || choice !in local.value.agentChoices) return
        local.update {
            it.copy(working = HeaderAction.ContinueWith, handoffDraft = null, handoffFailure = null)
        }
        launcher.launch {
            when (val read = host.prepare(choice)) {
                is Outcome.Failed ->
                    local.update { it.copy(working = null, continueFailure = read.failure) }
                is Outcome.Ok ->
                    when (val r = read.value) {
                        is io.github.eslamasabry.oc.engine.agents.handoff.HandoffPrepared.Ready ->
                            local.update { it.copy(working = null, handoffDraft = r.draft) }
                        is io.github.eslamasabry.oc.engine.agents.handoff.HandoffPrepared.Refused ->
                            local.update { it.copy(working = null, handoffFailure = r.kind) }
                    }
            }
        }
    }

    override fun sendHandoff(prompt: String) {
        val host = agentHandoffs ?: return
        if (
            local.value.working != null ||
                local.value.handoffDraft == null ||
                local.value.handoffFailure != null
        )
            return
        local.update { it.copy(working = HeaderAction.ContinueWith) }
        launcher.launch { finishHandoff(host.send(prompt)) }
    }

    override fun retryHandoffLink() {
        val host = agentHandoffs ?: return
        if (
            local.value.working != null ||
                local.value.handoffFailure !=
                    io.github.eslamasabry.oc.engine.agents.handoff.HandoffFailureKind.LinkFailed
        )
            return
        local.update { it.copy(working = HeaderAction.ContinueWith) }
        launcher.launch { finishHandoff(host.retryLink()) }
    }

    private fun finishHandoff(
        result: Outcome<io.github.eslamasabry.oc.engine.agents.handoff.HandoffSent>
    ) {
        when (result) {
            is Outcome.Failed ->
                local.update { it.copy(working = null, continueFailure = result.failure) }
            is Outcome.Ok ->
                when (val sent = result.value) {
                    is io.github.eslamasabry.oc.engine.agents.handoff.HandoffSent.Sent -> {
                        local.update { it.copy(working = null, sheet = null, handoffDraft = null) }
                        nav.openChat(
                            ConversationRef(
                                world.ref.profileId,
                                io.github.eslamasabry.oc.core.common.ids.SessionId(
                                    sent.navigationSessionId
                                ),
                            )
                        )
                    }
                    is io.github.eslamasabry.oc.engine.agents.handoff.HandoffSent.Refused ->
                        local.update { it.copy(working = null, handoffFailure = sent.kind) }
                }
        }
    }

    override fun retryContinueWith() {
        local.update { it.copy(continueFailure = null) }
        loadModels()
        loadHandoffChoices()
    }

    private fun loadModels() {
        launcher.launch {
            val r = world.loadModels()
            if (r is Outcome.Failed) local.update { it.copy(continueFailure = r.failure) }
        }
    }

    override fun confirm() {
        val confirm = local.value.confirm ?: return
        if (local.value.working != null) return
        local.update { it.copy(confirm = null) }
        when (confirm) {
            HeaderConfirm.Share ->
                run(HeaderAction.Share) {
                    if (!world.capabilities.value.sessionShare) {
                        Outcome.Failed(Failure.Unavailable(FeatureId("sessionShare")))
                    } else {
                        when (val answer = port.share()) {
                            is Outcome.Ok -> {
                                answer.value?.let { url ->
                                    local.update {
                                        it.copy(
                                            clip = HeaderClip(nextId++, url, ClipKind.ShareLink)
                                        )
                                    }
                                }
                                Outcome.Ok(Unit)
                            }
                            is Outcome.Failed -> answer
                        }
                    }
                }
            HeaderConfirm.Unshare ->
                run(HeaderAction.Unshare) {
                    if (!world.capabilities.value.sessionShare) {
                        Outcome.Failed(Failure.Unavailable(FeatureId("sessionShare")))
                    } else port.unshare()
                }
            is HeaderConfirm.Archive ->
                run(HeaderAction.Archive) {
                    port.archive().also { if (it is Outcome.Ok) nav.back() }
                }
            is HeaderConfirm.Delete ->
                run(HeaderAction.Delete) {
                    port.delete().also { if (it is Outcome.Ok) nav.back() }
                }
        }
    }

    override fun dismissConfirm() {
        local.update { it.copy(confirm = null) }
    }

    override fun dismissFailure() {
        local.update { it.copy(failure = null) }
    }

    override fun clipDone(id: Long) {
        local.update { if (it.clip?.id == id) it.copy(clip = null) else it }
    }

    public fun close() {
        closeSheet()
        port.close()
    }

    /** One act at a time; a second tap while one runs does nothing. */
    private fun run(action: HeaderAction, block: suspend () -> Outcome<Unit>) {
        if (local.value.working != null) return
        local.update { it.copy(working = action, failure = null) }
        launcher.launch {
            val r = block()
            local.update {
                it.copy(
                    working = null,
                    failure =
                        (r as? Outcome.Failed)?.let { f ->
                            HeaderFailure(nextId++, action, f.failure)
                        },
                )
            }
        }
    }

    private companion object {
        const val MAX_CONTINUE = 8
    }
}
