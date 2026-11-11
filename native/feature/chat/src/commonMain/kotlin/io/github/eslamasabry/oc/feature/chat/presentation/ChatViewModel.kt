package io.github.eslamasabry.oc.feature.chat.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.schedules.ScheduleEngine
import io.github.eslamasabry.oc.engine.transcript.ledger.latest
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.FilesRoute
import io.github.eslamasabry.oc.ui.routes.GitRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One per chat entry (plan 09 §6.2): opens the conversation's world in init, releases it in
 * onCleared. All behaviour is [ChatPresenter]'s; this class only gives it the entry's scope (03
 * §14.3: WhileSubscribed(5 s)).
 */
@AssistedInject
public class ChatViewModel(
    @Assisted private val route: ChatRoute,
    private val worlds: ChatWorlds,
    private val nav: AppNavigator,
    clock: Clock,
    private val outside: ChatOutside,
    private val schedules: ScheduleEngine,
    starPrompts: Set<io.github.eslamasabry.oc.feature.chat.starprompt.ChatStarPrompt>,
) : ViewModel(), ChatActions {
    private val world = worlds.open(route.ref)
    private var openingSchedule = false
    private val presenter =
        ChatPresenter(
            world,
            nav,
            { block -> viewModelScope.launch { block() } },
            focusComposer = route.focusComposer,
            outside = outside,
            clock = clock,
            landOnFailure = route.landOnFailure,
            star = starPrompts.firstOrNull(),
        )

    public val serverCapabilities = world.capabilities
    public val requests = world.requests

    public val state: StateFlow<ChatUi> =
        presenter.states(viewModelScope, world.work, SharingStarted.WhileSubscribed(STOP_AFTER_MS))

    init {
        // The error card suggests a model by name only once the catalog is read.
        viewModelScope.launch { if (world.models.value.choices.isEmpty()) world.loadModels() }
        route.requestId?.let { id ->
            viewModelScope.launch {
                world.requests.collect { model ->
                    model?.focus(io.github.eslamasabry.oc.core.common.ids.RequestId(id))
                }
            }
        }
    }

    /**
     * CHAT-TRANSCRIPT: steps, results, context, retry and stall beside the rows (side tables, never
     * the rows).
     */
    public val facts: StateFlow<TurnFactsUi> =
        TurnFactsPresenter(clock)
            .states(
                (world as? TurnFeedSource)?.turnFeed ?: flowOf(TurnFeed.Empty),
                world.requests.latest { model ->
                    model?.state?.map { it.card != null } ?: flowOf(false)
                },
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MS),
                world.work,
            )

    /**
     * B-CHAT-110: a task step opens the conversation its sub-agent ran in (same server, same
     * profile).
     */
    public fun openSession(sessionId: String) {
        if (sessionId.isBlank()) return
        // The id is server-authored text: open it only when the server says it is this
        // conversation's own child (Flutter `isKnownChild` / `requireChild: true`).
        viewModelScope.launch {
            val child = world.isChildSession(sessionId)
            if (child is Outcome.Ok && child.value)
                nav.openChat(ConversationRef(world.ref.profileId, SessionId(sessionId)))
        }
    }

    override fun onCleared() {
        worlds.release(world)
    }

    override fun leaveRequested(): Unit = presenter.leaveRequested()

    override fun openGit(): Unit = nav.open(GitRoute(route.ref))

    private val scheduledSignal = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    private var scheduledCount = 0L

    override val laterScheduled: kotlinx.coroutines.flow.StateFlow<Long?> = scheduledSignal

    override fun laterShown(id: Long) {
        if (scheduledSignal.value == id) scheduledSignal.value = null
    }

    /** N-44: when the person comes back from the schedule editor with a new schedule, the composer says so and clears. */
    private fun watchSavedSchedule(profile: String) {
        val before = schedules.state(profile).value.schedules.map { it.id }.toSet()
        viewModelScope.launch {
            nav.top.dropWhile { it == route }.first { it == route }
            if (schedules.state(profile).value.schedules.any { it.id !in before }) {
                scheduledCount += 1
                scheduledSignal.value = scheduledCount
            }
        }
    }

    override fun sendLater(words: String) {
        if (words.isBlank() || openingSchedule) return
        openingSchedule = true
        viewModelScope.launch {
            try {
                val profile = route.ref.profileId.raw
                // Seed the editor's existing draft before navigation. Its regular restore path then
                // keeps subsequent edits across recreation without replaying the original words.
                schedules.setPromptDraft(profile, null, words)
                if (schedules.promptDraft(profile, null) != words) {
                    presenter.actionFailed(Failure.StorageRefused())
                    return@launch
                }
                watchSavedSchedule(profile)
                nav.open(
                    io.github.eslamasabry.oc.ui.routes.SchedulesRoute.Edit(
                        profileId = route.ref.profileId,
                        from = route.ref,
                    )
                )
            } finally {
                openingSchedule = false
            }
        }
    }

    override fun retryLastPrompt(): Unit = presenter.retryLastPrompt()

    override fun undoLastPrompt(): Unit = presenter.undoLastPrompt()

    override fun putBack(): Unit = presenter.putBack()

    override fun openNewConversation(): Unit = presenter.openNewConversation()

    override fun openConversations(): Unit = presenter.openConversations()

    /** Background commands, Stop command and Run shell command (Flutter running-work sheet). */
    public val work: io.github.eslamasabry.oc.feature.chat.work.ChatWorkModel =
        io.github.eslamasabry.oc.feature.chat.work
            .ChatWorkModel(world) { block ->
                viewModelScope.launch { block() }
            }
            .also { it.poll() }

    /** The server's own slash commands for the launcher. */
    public val serverCommands = world.commands

    public fun loadServerCommands() {
        viewModelScope.launch { world.loadCommands() }
    }

    override fun openProviders(): Unit =
        nav.open(
            io.github.eslamasabry.oc.ui.routes.LibraryRoute(tab = "providers", profileId = route.ref.profileId)
        )

    override fun useModelAndResend(turnId: String, providerId: String, modelId: String): Unit =
        presenter.useModelAndResend(turnId, providerId, modelId)

    override fun switchModel(turnId: String, resetsAtMs: Long?): Unit = presenter.switchModel(turnId, resetsAtMs)

    override fun switchTo(providerId: String, modelId: String): Unit = presenter.switchTo(providerId, modelId)

    override fun dismissSwitch(): Unit = presenter.dismissSwitch()

    override fun confirmSwitch(): Unit = presenter.confirmSwitch()

    /** The models the error card may suggest (safe catalog projection). */
    public val models = world.models

    override fun openSignInSettings(): Unit =
        nav.open(io.github.eslamasabry.oc.ui.routes.SettingsRoute.Home)

    override fun phoneAgentSignIn(): Boolean = outside.isPhoneServer(route.ref)

    override fun openAgentSignIn(providerId: String) {
        if (!outside.isPhoneServer(route.ref)) return
        nav.open(io.github.eslamasabry.oc.ui.routes.AgentsRoute.SignInTerminal(route.ref.profileId, providerId))
    }

    override fun confirmLeave(): Unit = presenter.confirmLeave()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun retryLoad(): Unit = presenter.retryLoad()

    override fun reportProblem(): Unit = presenter.reportProblem()

    /**
     * Project Files for this conversation (P12-FILES; 07 §5 D01/D02, `/files` and the chat's Files
     * action).
     */
    override fun openFiles(): Unit = nav.open(FilesRoute.Folder(presenter.ref))

    override fun openTools(): Unit =
        nav.open(io.github.eslamasabry.oc.ui.routes.LibraryRoute(ref = route.ref))

    override fun stop(): Unit = presenter.stop()

    override fun find(query: String?): Unit = presenter.find(query)

    override fun findNext(): Unit = presenter.findNext()

    override fun findPrevious(): Unit = presenter.findPrevious()

    override fun loadOlder(): Unit = presenter.loadOlder()

    override fun toggleReasoning(): Unit = presenter.toggleReasoning()

    override fun compactAgain(): Unit = presenter.compactAgain()

    override fun compactRequested(): Unit = presenter.compactRequested()

    override fun backgroundWork(): Unit = presenter.backgroundWork()

    override fun confirmCompact(): Unit = presenter.confirmCompact()

    override fun tryLinkNow(): Unit = presenter.tryLinkNow()

    override fun stopAfterStep(note: String): Unit = presenter.stopAfterStep(note)

    override fun undoStopAfterStep(): Unit = presenter.undoStopAfterStep()

    override fun openPlace(place: ChatPlace): Unit = presenter.openPlace(place)

    override fun restartPhoneServer(): Unit = presenter.restartPhoneServer()

    override fun restartAgent(): Unit = presenter.restartAgent()

    override fun openParent() {
        // Back to the parent: pops when it is right below (opened from its card), never stacks it twice.
        world.parent.value?.let { nav.backToChat(it) }
    }

    override fun showSubagents(): Unit = nav.open(io.github.eslamasabry.oc.ui.routes.SessionRelationsRoute(world.ref))

    override fun editServer(): Unit = nav.open(io.github.eslamasabry.oc.ui.routes.ServersRoute.Edit(world.ref.profileId))

    override fun openServers(): Unit = nav.open(io.github.eslamasabry.oc.ui.routes.ServersRoute.Home)

    override fun confirmRestartPhoneServer(): Unit = presenter.confirmRestartPhoneServer()

    override fun openTimeline(): Unit = presenter.openTimeline()

    override fun closeTimeline(): Unit = presenter.closeTimeline()

    override fun timelineQuery(query: String): Unit = presenter.timelineQuery(query)

    override fun timelineSelect(messageId: String, fork: Boolean): Unit =
        presenter.timelineSelect(messageId, fork)

    override fun searchAllHistory(): Unit = presenter.searchAllHistory()

    override fun cancelSearchAll(): Unit = presenter.cancelSearchAll()

    override fun openMessageMenu(rowKey: String): Unit = presenter.openMessageMenu(rowKey)

    override fun openChanges(): Unit = presenter.openChanges()

    override fun closeMessageMenu(): Unit = presenter.closeMessageMenu()

    override fun messageAction(target: MessageTarget, action: MessageAction): Unit =
        presenter.messageAction(target, action)

    override fun confirmDelete(): Unit = presenter.confirmDelete()

    override fun clipDone(id: Long): Unit = presenter.clipDone(id)

    override fun readAloudDone(id: Long): Unit = presenter.readAloudDone(id)

    override fun arrivalShown(id: Long): Unit = presenter.arrivalShown(id)

    override fun liveEdgeChanged(atLiveEdge: Boolean): Unit = presenter.liveEdgeChanged(atLiveEdge)

    override fun continueReply(words: String): Unit = presenter.continueReply(words)

    override fun checkReceipt(localId: Long): Unit = presenter.checkReceipt(localId)

    override fun retrySend(localId: Long): Unit = presenter.retrySend(localId)

    override fun dismissStatus(id: String): Unit = presenter.dismissStatus(id)

    override fun starTapped(): Unit = presenter.starTapped()

    override fun starDismissed(): Unit = presenter.starDismissed()

    override fun openLink(url: String): Unit = presenter.openLink(url)

    override fun confirmOpenLink(): Unit = presenter.confirmOpenLink()

    override fun copyText(text: String): Unit = presenter.copyText(text)

    override fun closeSelection(): Unit = presenter.closeSelection()

    override fun insertDone(id: Long): Unit = presenter.insertDone(id)

    override fun confirmUndo(): Unit = presenter.confirmUndo()

    override suspend fun readPicture(path: String): ByteArray? = presenter.readPicture(path)

    override val pictureScope: String get() = presenter.pictureScope

    override fun openPicture(path: String, name: String): Unit = presenter.openPicture(path, name)

    override fun closePicture(): Unit = presenter.closePicture()

    override fun openPath(path: String, line: Int?): Unit = presenter.openPath(path, line)

    override fun confirmPhoneFile(): Unit = presenter.confirmPhoneFile()

    override fun checkPaths(paths: List<String>): Unit = presenter.checkPaths(paths)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatViewModel
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
