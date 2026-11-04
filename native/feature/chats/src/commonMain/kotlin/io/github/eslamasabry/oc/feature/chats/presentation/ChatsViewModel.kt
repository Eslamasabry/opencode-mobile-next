package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.FeedDay
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.MonitorInboxRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlin.time.Clock
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Conversations home (07 §5 B01, B15). Plain screen ViewModel through Metro (plan 03 §13.7).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ChatsViewModel(
    private val feed: ConversationsFeed,
    private val briefs: ReturnBriefFeed,
    private val settings: SettingsDoor,
    private val day: FeedDay,
    private val clock: Clock,
    private val nav: AppNavigator,
    private val updates: UpdateDoor = NoUpdateDoor(),
    private val home: ChatsHomeExtras = NoChatsHomeExtras(),
    private val permissionAnswers: Set<HomePermissionAnswers> = emptySet(),
) : ViewModel(), ChatsActions {
    private val filters = MutableStateFlow(ChatsFilters())
    private val sheet = MutableStateFlow<ChatsHomeSheet?>(null)
    private val watchedSelection = MutableStateFlow<String?>(null)
    private val answers =
        MutableStateFlow<Pair<Set<String>, io.github.eslamasabry.oc.core.common.Failure?>>(
            emptySet<String>() to null
        )

    public val state: StateFlow<ChatsUi> =
        combine(
                combine(feed.snapshot, briefs.brief, filters, updates.ready) {
                    snapshot,
                    brief,
                    chosen,
                    update ->
                    build(snapshot, brief, chosen).copy(updateReady = update)
                },
                home.snapshot,
                sheet,
                watchedSelection,
                answers,
            ) { base, extra, open, selected, answering ->
                base.copy(
                    home = extra,
                    answering = answering.first.toImmutableSet(),
                    answerFailure = answering.second,
                    sheet = open,
                    confirmWatched = extra.monitoring.requests.firstOrNull { it.key == selected },
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                build(feed.snapshot.value, briefs.brief.value, filters.value)
                    .copy(updateReady = updates.ready.value, home = home.snapshot.value),
            )

    private fun build(snapshot: ChatFeedSnapshot, brief: ReturnBrief, chosen: ChatsFilters) =
        ChatsState.build(
            feed = snapshot,
            brief = brief,
            filters = chosen,
            startOfTodayMs = day.startOfToday(clock.now()).toEpochMilliseconds(),
            settingsAvailable = settings.available,
            nowMs = clock.now().toEpochMilliseconds(),
        )

    override fun answerPermission(row: ConversationRowUi, allow: Boolean) {
        val permission = row.permission ?: return
        val port = permissionAnswers.singleOrNull() ?: return
        if (permission.requestId in answers.value.first) return
        answers.value = (answers.value.first + permission.requestId) to null
        viewModelScope.launch {
            val result = port.answer(row.ref, permission.requestId, allow)
            answers.value =
                (answers.value.first - permission.requestId) to
                    (result as? io.github.eslamasabry.oc.core.common.Outcome.Failed)?.failure
            feed.refresh()
        }
    }

    override fun openConversation(ref: ConversationRef) {
        if (state.value.home.opening != null) return
        // A row that cannot be reopened yet (its workspace is still connecting) stays closed.
        if (feed.snapshot.value.items.any { it.ref == ref && !it.canReopen }) return
        sheet.value = null
        feed.prefetch(ref)
        nav.openChat(ref)
    }

    override fun newConversation() {
        if (state.value.home.opening == null) nav.open(ChatsRoute.New(profile = null))
    }

    override fun openSettings() {
        if (settings.available) settings.open()
    }

    override fun openUpdate(): Unit = updates.open()

    override fun search(query: String): Unit = filters.update { it.copy(query = query) }

    override fun toggleRunning(): Unit = filters.update {
        it.copy(running = !it.running, needsYou = false)
    }

    override fun toggleNeedsYou(): Unit = filters.update {
        it.copy(needsYou = !it.needsYou, running = false)
    }

    override fun chooseProject(directory: String) {
        val fromSheet = sheet.value == ChatsHomeSheet.Projects
        filters.update {
            it.copy(
                projectDirectory =
                    if (fromSheet || it.projectDirectory != directory) directory else null,
                otherFolders = false,
            )
        }
        sheet.value = null
    }

    override fun clearFilters(): Unit = filters.update { ChatsFilters() }

    override fun dismissBrief(shown: ReturnBrief) {
        viewModelScope.launch { briefs.dismiss(shown) }
    }

    override fun showBriefOverflow(shown: BriefUi) {
        filters.value = ChatsFilters(identities = shown.overflowKeys)
    }

    override fun showAll(): Unit = nav.open(ChatsRoute.All(filter = null))

    override fun openSearch(): Unit = nav.open(SearchRoute.Conversations())

    override fun openInbox() {
        sheet.value = null
        nav.open(MonitorInboxRoute())
    }

    override fun openAttention() {
        sheet.value = null
        nav.open(ChatsRoute.Attention)
    }

    override fun openProjects() {
        sheet.value = null
        nav.open(ProjectsRoute.Picker())
    }

    override fun showProjectChoices() {
        sheet.value = ChatsHomeSheet.Projects
    }

    override fun showSources() {
        sheet.value = ChatsHomeSheet.Sources
    }

    override fun showInbox() {
        sheet.value = ChatsHomeSheet.Inbox
    }

    override fun closeHomeSheet() {
        sheet.value = null
    }

    override fun allProjects() {
        filters.update { it.copy(projectDirectory = null, otherFolders = false) }
        sheet.value = null
    }

    override fun otherFolders() {
        filters.update { it.copy(projectDirectory = null, otherFolders = true) }
        sheet.value = null
    }

    override fun showSource(profile: ProfileId, shown: Boolean) {
        viewModelScope.launch { home.showSource(profile, shown) }
    }

    override fun retrySource(profile: ProfileId) {
        viewModelScope.launch { home.retrySource(profile) }
    }

    override fun retryOpening() {
        viewModelScope.launch { home.retryOpening() }
    }

    override fun openServers() {
        sheet.value = null
        nav.open(ServersRoute.Home)
    }

    override fun openFilterProject() {
        sheet.value = ChatsHomeSheet.OpenProject
    }

    override fun finishFilterProject(directory: String?) {
        if (directory != null) {
            val path = directory.trim().trimEnd('/').ifEmpty { "/" }
            if (
                path.startsWith('/') &&
                    path.split('/').none { it == ".." || it == "." } &&
                    path.none { it.code <= 0x1f || it.code == 0x7f }
            ) {
                filters.update { it.copy(projectDirectory = path, otherFolders = false) }
            }
        }
        sheet.value = null
    }

    override fun showMonitoring() {
        sheet.value = ChatsHomeSheet.Monitoring
    }

    override fun setWatched(profile: ProfileId, enabled: Boolean) {
        viewModelScope.launch { home.setWatched(profile, enabled) }
    }

    override fun refreshWatched() {
        viewModelScope.launch { home.refreshWatched() }
    }

    override fun openWatched(key: String) {
        sheet.value = null
        watchedSelection.value = key
    }

    override fun dismissWatched() {
        watchedSelection.value = null
    }

    override fun confirmWatched() {
        val key = watchedSelection.value ?: return
        watchedSelection.value = null
        viewModelScope.launch {
            when (val result = home.openWatched(key)) {
                is io.github.eslamasabry.oc.core.common.Outcome.Ok -> {
                    sheet.value = null
                    nav.openChat(result.value)
                }
                // The refusal is shown in the Inbox, which was closed for the confirmation.
                is io.github.eslamasabry.oc.core.common.Outcome.Failed ->
                    sheet.value = ChatsHomeSheet.Inbox
            }
        }
    }
}

public fun interface HomePermissionAnswers {
    public suspend fun answer(
        ref: ConversationRef,
        requestId: String,
        allow: Boolean,
    ): io.github.eslamasabry.oc.core.common.Outcome<Unit>
}

@dev.zacsweers.metro.ContributesTo(dev.zacsweers.metro.AppScope::class)
public interface HomePermissionBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true)
    public val homePermissionAnswers: Set<HomePermissionAnswers>
}
