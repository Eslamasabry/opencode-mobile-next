package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.FactsBookCapabilities
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.FactsBookState
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeState
import io.github.eslamasabry.oc.engine.factsbook.PhoneNodeStatus
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouItem
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.engine.factsbook.ThingKind
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One newest thing for "Recently added": its newest fact's line. */
public data class RecentUi(val thing: Thing)

/** What the Memory section shows (F: memory_section.dart). No copy: the screen words every field. */
public data class MemorySectionUi(
    /** False: the developer switch is off and nothing about FactsBook shows. */
    val shown: Boolean = false,
    /** Not read yet: nothing to say. */
    val loaded: Boolean = false,
    val paired: Boolean = false,
    val load: FactsBookLoad = FactsBookLoad.Idle,
    val error: FactsBookError? = null,
    val needsYou: ImmutableList<NeedsYouItem> = persistentListOf(),
    val needsYouCount: Int = 0,
    val recent: ImmutableList<Thing> = persistentListOf(),
    val nowMs: Long = 0,
    /** The memory on this phone is paired here and stopped (or coming up): said in words, with Start. */
    val nodeStopped: Boolean = false,
    val nodeStarting: Boolean = false,
)

public interface MemorySectionActions {
    public fun setUp()

    public fun openAboutYou()

    public fun addSomething()

    public fun openNeedsYou(kind: NeedsYouKind)

    public fun openThing(id: String)

    public fun retry()

    /** Starts the memory on this phone. */
    public fun startNode()
}

/** Reads the connected profile's pairing and keeps the engine open on it. Shared by the section and About you. */
internal fun FactsBookAccess.follow(scope: kotlinx.coroutines.CoroutineScope, refresh: Boolean) {
    scope.launch {
        activeProfileId.collect { id ->
            engine.open(id)
            if (refresh && engine.state.value.paired && engine.state.value.load == FactsBookLoad.Idle) engine.refresh()
        }
    }
}

/**
 * The Inbox's Memory section (decision G1/D32). Before pairing it is one row, "Set up FactsBook"; after, the needs-you
 * rows, the newest things and the two ways in. Its count is the one the needs-you rows add up to, counted once.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class MemorySectionViewModel(
    private val access: FactsBookAccess,
    private val nav: AppNavigator,
    private val clock: Clock,
) : ViewModel(), MemorySectionActions {
    /**
     * While the memory on this phone is still starting, a read that cannot reach it is tried again this often, this many
     * times (about a minute), before the section says it did not answer (F: `readRetryDelay`, `readRetryAttempts`).
     */
    internal var readRetryDelay: Duration = 5.seconds
    internal var readRetryAttempts: Int = 12

    private val readRetries = MutableStateFlow(0)

    public val state: StateFlow<MemorySectionUi> =
        combine(access.enabled, access.engine.state, access.phoneNode.status, readRetries) { on, s, node, tries -> present(on, s, node, comingUp(s, node, tries)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemorySectionUi())

    init {
        access.follow(viewModelScope, refresh = true)
        // A real answer ends the waiting.
        viewModelScope.launch {
            access.engine.state.collect { s -> if (s.error == null) readRetries.value = 0 }
        }
        // The memory on this phone stopped while the section shows what it last read: read again, so the row says it is
        // stopped instead of showing old numbers (F: `_watchStop`).
        viewModelScope.launch {
            var seen: PhoneNodeState? = null
            access.phoneNode.status.collect { node ->
                val before = seen
                seen = node.state
                if (before != null && before != PhoneNodeState.Stopped && node.state == PhoneNodeState.Stopped && node.pairedHere) {
                    access.engine.refresh()
                }
            }
        }
        // Coming up: read again every few seconds instead of showing an error (F: `_scheduleRetry`).
        viewModelScope.launch {
            combine(access.engine.state, access.phoneNode.status, readRetries) { s, node, tries -> comingUp(s, node, tries) && s.load != FactsBookLoad.Loading }
                .collectLatest { waiting ->
                    if (!waiting) return@collectLatest
                    delay(readRetryDelay)
                    // The read itself changes what is watched; it is never cut short.
                    withContext(NonCancellable) {
                        access.engine.refresh()
                        readRetries.update { it + 1 }
                    }
                }
        }
    }

    /**
     * The last read could not reach the memory on this phone, which is paired here and not stopped by the person: it is
     * most likely still coming up (always just after an update). A real answer, such as unauthorized or locked, is not
     * waited for (F: `_comingUp`).
     */
    private fun comingUp(s: FactsBookState, node: PhoneNodeStatus, tries: Int): Boolean =
        access.phoneNode.supported && node.pairedHere && !node.personStopped && node.state != PhoneNodeState.Stopped &&
            s.error?.unreachable == true && tries < readRetryAttempts

    private fun present(on: Boolean, s: FactsBookState, node: PhoneNodeStatus, comingUp: Boolean): MemorySectionUi =
        MemorySectionUi(
            nodeStopped = node.pairedHere && node.state == PhoneNodeState.Stopped,
            nodeStarting = node.pairedHere && (node.busy || node.state == PhoneNodeState.Installing || comingUp),
            shown = on,
            loaded = s.loaded,
            paired = s.paired,
            load = s.load,
            error = s.error,
            needsYou = s.needsYou.toImmutableList(),
            needsYouCount = s.needsYouCount,
            recent = s.recent.toImmutableList(),
            nowMs = clock.now().toEpochMilliseconds(),
        )

    /** On a phone with the built-in Linux the first-run path is memory on this phone (F: memory_section.dart). */
    override fun setUp(): Unit = nav.open(if (access.phoneNode.supported) FactsBookRoute.PhoneNode else FactsBookRoute.Connect)

    override fun openAboutYou(): Unit = nav.open(FactsBookRoute.AboutYou)

    override fun addSomething(): Unit = nav.open(FactsBookRoute.Capture)

    /** Changes to review and offers go to Needs you; notes to Not saved yet (F: memory_section.dart). */
    override fun openNeedsYou(kind: NeedsYouKind): Unit =
        nav.open(if (kind == NeedsYouKind.NotSaved) FactsBookRoute.NotSaved else FactsBookRoute.NeedsYou)

    override fun openThing(id: String): Unit = nav.open(FactsBookRoute.Thing(id))

    /** The memory on this phone may simply not be running: start it first, then read again (F: the notice's Try again). */
    override fun retry() {
        access.background {
            if (access.phoneNode.supported && access.phoneNode.status.value.pairedHere) access.phoneNode.ensureRunning()
            access.engine.refresh()
        }
    }

    override fun startNode() {
        access.background {
            access.phoneNode.start()
            if (access.phoneNode.status.value.state == PhoneNodeState.Running) access.engine.refresh()
        }
    }
}

/** The thing list's filter (F: about_you_screen.dart chips: All, Cards, People, Documents). */
public enum class ThingFilter(public val kind: ThingKind?) { All(null), Cards(ThingKind.Card), People(ThingKind.Person), Documents(ThingKind.Document) }

public data class AboutYouUi(
    val load: FactsBookLoad = FactsBookLoad.Idle,
    /** Shown only when the node cannot be reached: the page shows what it last had. */
    val stale: Boolean = false,
    val needsYouCount: Int = 0,
    val showReminders: Boolean = false,
    val showTriggers: Boolean = false,
    /** True while the node cannot open the locked folder yet: the note says "not yet" instead of "unlock". */
    val lockedNotYet: Boolean = true,
    val filter: ThingFilter = ThingFilter.All,
    /** Null while the list is being read. */
    val things: ImmutableList<Thing>? = null,
    val listError: FactsBookError? = null,
    /** The list is read again behind what is shown (pull to refresh). */
    val refreshing: Boolean = false,
)

public interface AboutYouActions {
    public fun back()

    public fun search(query: String)

    public fun filter(filter: ThingFilter)

    public fun reload()

    public fun openThing(id: String)

    public fun openNeedsYou()

    public fun openAreas()

    public fun openReminders()

    public fun openTriggers()

    public fun openStatus()

    public fun openSettings()

    public fun addSomething()
}

/** About you, the full place (spec 60 screen 8). */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AboutYouViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), AboutYouActions {
    private data class Local(
        val filter: ThingFilter = ThingFilter.All,
        val things: List<Thing>? = null,
        val error: FactsBookError? = null,
        val refreshing: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<AboutYouUi> =
        combine(access.engine.state, local) { s, own ->
                AboutYouUi(
                    load = s.load,
                    stale = s.status != null && ((s.status?.staleSeconds ?: 0) > 0 || s.error?.unreachable == true),
                    needsYouCount = s.needsYouCount,
                    showReminders = s.capabilities.supports(FactsBookCapabilities.REMINDERS),
                    showTriggers = s.capabilities.supports(FactsBookCapabilities.TRIGGERS),
                    lockedNotYet = s.capabilities.isDisabled(FactsBookCapabilities.SENSITIVE_UNLOCK),
                    filter = own.filter,
                    things = own.things?.toImmutableList(),
                    listError = own.error,
                    refreshing = own.refreshing,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AboutYouUi())

    init {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            access.engine.refresh()
        }
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun search(query: String) {
        if (query.isNotBlank()) nav.open(FactsBookRoute.Search(query.trim()))
    }

    override fun filter(filter: ThingFilter) {
        if (local.value.filter == filter) return
        local.update { it.copy(filter = filter, things = null) }
        reload()
    }

    override fun reload() {
        val filter = local.value.filter
        local.update { it.copy(error = null, refreshing = true) }
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            val node = access.engine.node
            val result = node?.things(filter.kind) ?: FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))
            if (local.value.filter != filter) return@launch
            when (result) {
                is FbResult.Ok -> local.update { it.copy(things = result.value, refreshing = false) }
                is FbResult.Err -> local.update { it.copy(error = result.error, refreshing = false) }
            }
        }
    }

    override fun openThing(id: String): Unit = nav.open(FactsBookRoute.Thing(id))

    override fun openNeedsYou(): Unit = nav.open(FactsBookRoute.NeedsYou)

    override fun openAreas(): Unit = nav.open(FactsBookRoute.Areas)

    override fun openReminders(): Unit = nav.open(FactsBookRoute.Reminders)

    override fun openTriggers(): Unit = nav.open(FactsBookRoute.Triggers)

    override fun openStatus(): Unit = nav.open(FactsBookRoute.Status)

    override fun openSettings(): Unit = nav.open(FactsBookRoute.Settings)

    override fun addSomething(): Unit = nav.open(FactsBookRoute.Capture)
}
