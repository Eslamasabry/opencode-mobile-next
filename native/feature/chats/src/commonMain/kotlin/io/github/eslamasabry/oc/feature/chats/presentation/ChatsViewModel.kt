package io.github.eslamasabry.oc.feature.chats.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.FeedDay
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.SearchRoute
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import kotlin.time.Clock
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
) : ViewModel(), ChatsActions {
    private val filters = MutableStateFlow(ChatsFilters())

    public val state: StateFlow<ChatsUi> =
        combine(feed.snapshot, briefs.brief, filters, updates.ready) { snapshot, brief, chosen, update ->
                build(snapshot, brief, chosen).copy(updateReady = update)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                build(feed.snapshot.value, briefs.brief.value, filters.value)
                    .copy(updateReady = updates.ready.value),
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

    override fun openConversation(ref: ConversationRef): Unit = nav.openChat(ref)

    override fun newConversation(): Unit = nav.open(ChatsRoute.New(profile = null))

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

    override fun chooseProject(directory: String): Unit = filters.update {
        it.copy(projectDirectory = if (it.projectDirectory == directory) null else directory)
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
    override fun openProjects(): Unit = nav.open(ProjectsRoute.Picker())
}
