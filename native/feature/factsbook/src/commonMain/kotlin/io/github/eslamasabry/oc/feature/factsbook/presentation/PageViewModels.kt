package io.github.eslamasabry.oc.feature.factsbook.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.engine.factsbook.Area
import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.CaptureOutcome
import io.github.eslamasabry.oc.engine.factsbook.CaptureRequest
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveState
import io.github.eslamasabry.oc.engine.factsbook.Fact
import io.github.eslamasabry.oc.engine.factsbook.FactsBookError
import io.github.eslamasabry.oc.engine.factsbook.FactsBookGateway
import io.github.eslamasabry.oc.engine.factsbook.FbResult
import io.github.eslamasabry.oc.engine.factsbook.SearchResult
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.FactsBookRoute
import kotlin.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val unreachable = FbResult.Err(FactsBookError(FactsBookError.UNREACHABLE))

/** The engine opened on the connected profile, then its gateway (null while unpaired). */
private suspend fun FactsBookAccess.node(): FactsBookGateway? {
    engine.open(activeProfileId.value)
    return engine.node
}

// ---- Thing --------------------------------------------------------------------------------------------------------

/** What a write on the thing page came to, worded by the screen. */
public enum class ThingNotice { FactAdded, FactProposed, FactChanged, ChangeProposed }

public data class ThingUi(
    val thing: Thing? = null,
    val error: FactsBookError? = null,
    val working: Boolean = false,
    val notice: ThingNotice? = null,
    val writeError: FactsBookError? = null,
)

public interface ThingActions {
    public fun back()

    public fun reload()

    public fun addFact(text: String)

    public fun editFact(fact: Fact, text: String)

    public fun dismiss()
}

/** One thing and its facts, each with provenance, source and date (F: thing_screen.dart). */
@AssistedInject
public class ThingViewModel(
    @Assisted private val route: FactsBookRoute.Thing,
    private val access: FactsBookAccess,
    private val nav: AppNavigator,
) : ViewModel(), ThingActions {
    private val mutable = MutableStateFlow(ThingUi())
    public val state: StateFlow<ThingUi> = mutable.asStateFlow()

    init {
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun reload() {
        mutable.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val r = access.node()?.thing(route.id) ?: unreachable) {
                is FbResult.Ok -> mutable.update { it.copy(thing = r.value, error = null) }
                is FbResult.Err -> mutable.update { it.copy(error = r.error) }
            }
        }
    }

    /** One line: runs of white space become one space, as Flutter sends it. */
    private fun oneLine(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    override fun addFact(text: String) {
        val thing = mutable.value.thing ?: return
        val line = oneLine(text)
        if (line.isEmpty() || mutable.value.working) return
        write { node ->
            node.addFact(thing, line).let { r -> r.mapNotice { if (it == "ok") ThingNotice.FactAdded else ThingNotice.FactProposed } }
        }
    }

    override fun editFact(fact: Fact, text: String) {
        val line = oneLine(text)
        if (line.isEmpty() || line == fact.text || mutable.value.working) return
        write { node ->
            node.editFact(fact, line).let { r -> r.mapNotice { if (it == "ok") ThingNotice.FactChanged else ThingNotice.ChangeProposed } }
        }
    }

    private fun FbResult<io.github.eslamasabry.oc.engine.factsbook.MutationResult>.mapNotice(pick: (String) -> ThingNotice): FbResult<ThingNotice> =
        when (this) {
            is FbResult.Ok -> FbResult.Ok(pick(value.status))
            is FbResult.Err -> this
        }

    private fun write(call: suspend (FactsBookGateway) -> FbResult<ThingNotice>) {
        mutable.update { it.copy(working = true, writeError = null, notice = null) }
        viewModelScope.launch {
            val r = access.node()?.let { call(it) } ?: unreachable
            mutable.update {
                when (r) {
                    is FbResult.Ok -> it.copy(working = false, notice = r.value)
                    is FbResult.Err -> it.copy(working = false, writeError = r.error)
                }
            }
            if (r is FbResult.Ok) reload()
        }
    }

    override fun dismiss(): Unit = mutable.update { it.copy(notice = null, writeError = null) }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FactsBookRoute.Thing): ThingViewModel
    }
}

// ---- Search -------------------------------------------------------------------------------------------------------

public data class SearchUi(val query: String = "", val result: SearchResult? = null, val error: FactsBookError? = null)

public interface SearchActions {
    public fun back()

    public fun search(query: String)

    public fun openThing(id: String)
}

/** Text match search (v1): every hit with its source and date; locked notes are skipped and said so. */
@AssistedInject
public class SearchViewModel(
    @Assisted route: FactsBookRoute.Search,
    private val access: FactsBookAccess,
    private val nav: AppNavigator,
) : ViewModel(), SearchActions {
    private val mutable = MutableStateFlow(SearchUi(query = route.query))
    public val state: StateFlow<SearchUi> = mutable.asStateFlow()

    init {
        search(route.query)
    }

    override fun back(): Unit = nav.back()

    override fun search(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        mutable.value = SearchUi(query = q)
        viewModelScope.launch {
            val r = access.node()?.search(q) ?: unreachable
            if (mutable.value.query != q) return@launch
            mutable.update {
                when (r) {
                    is FbResult.Ok -> it.copy(result = r.value)
                    is FbResult.Err -> it.copy(error = r.error)
                }
            }
        }
    }

    override fun openThing(id: String): Unit = nav.open(FactsBookRoute.Thing(id))

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: FactsBookRoute.Search): SearchViewModel
    }
}

// ---- Areas --------------------------------------------------------------------------------------------------------

public data class AreasUi(
    val areas: ImmutableList<Area>? = null,
    val error: FactsBookError? = null,
    val loading: Boolean = true,
    /** Sharing is signed by this phone's owner key; off with the reason until approval is set up. */
    val canApprove: Boolean = false,
    /** The areas whose change is on its way. */
    val working: ImmutableSet<String> = persistentSetOf(),
    /** Opening the locked folder to others asks first. */
    val confirmShare: Area? = null,
    /** A change that did not go through. */
    val failure: FactsBookError? = null,
)

public interface AreasActions {
    public fun back()

    public fun reload()

    public fun setUpApproval()

    /** Whether others may read [area]. Turning it on for the locked folder asks first. */
    public fun share(area: Area, on: Boolean)

    public fun confirmShare()

    public fun cancelShare()

    public fun dismiss()
}

/** Areas and sharing (spec 60 screen 17): each built-in area and whether others may read it (F: areas_screen.dart). */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AreasViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), AreasActions {
    private val mutable = MutableStateFlow(AreasUi())
    public val state: StateFlow<AreasUi> = mutable.asStateFlow()

    init {
        reload()
    }

    override fun back(): Unit = nav.back()

    override fun reload() {
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val r = access.node()?.areas() ?: unreachable
            val canApprove = access.engine.state.value.canApprove
            mutable.update {
                when (r) {
                    is FbResult.Ok -> it.copy(areas = r.value.toImmutableList(), loading = false, canApprove = canApprove)
                    is FbResult.Err -> it.copy(error = r.error, loading = false, canApprove = canApprove)
                }
            }
        }
    }

    override fun setUpApproval(): Unit = nav.open(FactsBookRoute.Connect)

    override fun share(area: Area, on: Boolean) {
        val now = mutable.value
        if (!now.canApprove || area.slug in now.working) return
        if (on && area.sensitiveStorage) {
            mutable.update { it.copy(confirmShare = area) }
            return
        }
        write(area, on)
    }

    override fun confirmShare() {
        val area = mutable.value.confirmShare ?: return
        mutable.update { it.copy(confirmShare = null) }
        write(area, true)
    }

    override fun cancelShare(): Unit = mutable.update { it.copy(confirmShare = null) }

    private fun write(area: Area, on: Boolean) {
        mutable.update { it.copy(working = (it.working + area.slug).toImmutableSet(), failure = null) }
        viewModelScope.launch {
            val r = access.node()?.setAreaShare(area, area.share.copy(readByDefault = on)) ?: unreachable
            mutable.update { it.copy(working = (it.working - area.slug).toImmutableSet(), failure = (r as? FbResult.Err)?.error) }
            if (r is FbResult.Ok) reload()
        }
    }

    override fun dismiss(): Unit = mutable.update { it.copy(failure = null) }
}

// ---- Capture ------------------------------------------------------------------------------------------------------

/** What saving came to, worded by the screen (F: capture_screen.dart). */
public enum class CaptureNotice { Saved, QueuedAtNode, KeptOnPhone, Full, FactAdded, FactProposed }

public data class CaptureUi(
    val kind: CaptureKind = CaptureKind.Note,
    /** Null: "Pick for me". */
    val area: String? = null,
    /** A thing the note is added to as a fact, or null. */
    val thingId: String? = null,
    val things: ImmutableList<Thing> = persistentListOf(),
    val areas: ImmutableList<String> = persistentListOf(),
    val working: Boolean = false,
    val notice: CaptureNotice? = null,
    val error: FactsBookError? = null,
    /** Bumped when the field must be cleared (a save that reached somewhere other than the phone's editor). */
    val clearSeed: Int = 0,
    val needsText: Boolean = false,
)

public interface CaptureActions {
    public fun back()

    public fun kind(kind: CaptureKind)

    public fun area(slug: String?)

    public fun thing(id: String?)

    public fun save(text: String)

    public fun seeNotSaved()

    public fun dismiss()
}

/** Add something: a note or pasted text. Never a loss: saved, held by the node, kept on the phone, or kept on screen. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class CaptureViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), CaptureActions {
    private val mutable = MutableStateFlow(CaptureUi())
    public val state: StateFlow<CaptureUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val node = access.node() ?: return@launch
            (node.things() as? FbResult.Ok)?.value?.let { t -> mutable.update { it.copy(things = t.toImmutableList()) } }
            (node.areas() as? FbResult.Ok)?.value?.let { a -> mutable.update { it.copy(areas = a.map(Area::slug).toImmutableList()) } }
        }
    }

    override fun back(): Unit = nav.back()

    override fun kind(kind: CaptureKind): Unit = mutable.update { it.copy(kind = kind, thingId = if (kind == CaptureKind.Note) it.thingId else null) }

    override fun area(slug: String?): Unit = mutable.update { it.copy(area = slug) }

    override fun thing(id: String?): Unit = mutable.update { it.copy(thingId = id) }

    override fun save(text: String) {
        val now = mutable.value
        if (now.working) return
        if (text.isBlank()) {
            mutable.update { it.copy(needsText = true) }
            return
        }
        mutable.update { it.copy(working = true, needsText = false, notice = null, error = null) }
        viewModelScope.launch {
            val thing = now.things.firstOrNull { it.id == now.thingId }.takeIf { now.kind == CaptureKind.Note }
            if (thing != null) {
                val r = access.node()?.addFact(thing, text.trim().replace(Regex("\\s+"), " ")) ?: unreachable
                mutable.update {
                    when (r) {
                        is FbResult.Ok ->
                            it.copy(
                                working = false,
                                notice = if (r.value.status == "ok") CaptureNotice.FactAdded else CaptureNotice.FactProposed,
                                clearSeed = it.clearSeed + 1,
                            )
                        is FbResult.Err -> it.copy(working = false, error = r.error)
                    }
                }
                return@launch
            }
            access.engine.open(access.activeProfileId.value)
            val outcome = access.engine.capture(CaptureRequest(now.kind, text, now.area))
            mutable.update {
                when (outcome) {
                    is CaptureOutcome.SavedAtNode -> it.copy(working = false, notice = CaptureNotice.Saved, clearSeed = it.clearSeed + 1)
                    is CaptureOutcome.QueuedAtNode ->
                        it.copy(working = false, notice = CaptureNotice.QueuedAtNode, clearSeed = it.clearSeed + 1)
                    // Kept on the phone or refused: the text stays where it was typed.
                    is CaptureOutcome.KeptOnPhone -> it.copy(working = false, notice = CaptureNotice.KeptOnPhone)
                    CaptureOutcome.RefusedFull -> it.copy(working = false, notice = CaptureNotice.Full)
                    is CaptureOutcome.Refused -> it.copy(working = false, error = outcome.error)
                }
            }
        }
    }

    override fun seeNotSaved(): Unit = nav.open(FactsBookRoute.NotSaved)

    override fun dismiss(): Unit = mutable.update { it.copy(notice = null, error = null) }
}

// ---- Not saved yet ------------------------------------------------------------------------------------------------

public data class NotSavedUi(
    val entries: ImmutableList<CouldNotSaveEntry> = persistentListOf(),
    val confirmDiscard: CouldNotSaveEntry? = null,
    val working: Boolean = false,
    val error: FactsBookError? = null,
    val nowMs: Long = 0,
    /** False while the node cannot open the locked folder yet: "unlock it" is no way out then. */
    val canUnlock: Boolean = false,
)

public interface NotSavedActions {
    public fun back()

    public fun retry(entry: CouldNotSaveEntry)

    public fun askDiscard(entry: CouldNotSaveEntry)

    public fun discard()

    public fun cancelDiscard()

    public fun dismiss()
}

/** Notes not saved yet: kept until the person discards them (F105, D32); at 30 days the page asks. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class NotSavedViewModel(private val access: FactsBookAccess, private val nav: AppNavigator, private val clock: Clock) :
    ViewModel(), NotSavedActions {
    private data class Local(val confirm: CouldNotSaveEntry? = null, val working: Boolean = false, val error: FactsBookError? = null)

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<NotSavedUi> =
        combine(access.engine.state, local) { s, own ->
                NotSavedUi(
                    entries = s.pending.filter { it.state != CouldNotSaveState.Discarded }.toImmutableList(),
                    confirmDiscard = own.confirm,
                    working = own.working,
                    error = own.error,
                    nowMs = clock.now().toEpochMilliseconds(),
                    canUnlock = !s.capabilities.isDisabled(io.github.eslamasabry.oc.engine.factsbook.FactsBookCapabilities.SENSITIVE_UNLOCK),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotSavedUi())

    init {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            access.engine.refresh()
        }
    }

    override fun back(): Unit = nav.back()

    override fun retry(entry: CouldNotSaveEntry) {
        if (local.value.working) return
        local.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            val r = access.engine.retry(entry)
            local.update { it.copy(working = false, error = (r as? FbResult.Err)?.error) }
        }
    }

    override fun askDiscard(entry: CouldNotSaveEntry): Unit = local.update { it.copy(confirm = entry) }

    override fun discard() {
        val entry = local.value.confirm ?: return
        local.update { it.copy(confirm = null, working = true) }
        viewModelScope.launch {
            val r = access.engine.discard(entry)
            local.update { it.copy(working = false, error = (r as? FbResult.Err)?.error) }
        }
    }

    override fun cancelDiscard(): Unit = local.update { it.copy(confirm = null) }

    override fun dismiss(): Unit = local.update { it.copy(error = null) }
}

// ---- Connect ------------------------------------------------------------------------------------------------------

public data class ConnectUi(
    val working: Boolean = false,
    val error: FactsBookError? = null,
    val paired: Boolean = false,
    /** This phone's public owner key as hex, for the console's `--owner-key`. */
    val ownerKey: String? = null,
)

public interface ConnectActions {
    public fun back()

    public fun connect(address: String, code: String, ownerDeviceId: String)
}

/** Set up FactsBook: a private address and the one-time code the node's console shows. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ConnectViewModel(private val access: FactsBookAccess, private val nav: AppNavigator) : ViewModel(), ConnectActions {
    private val mutable = MutableStateFlow(ConnectUi())
    public val state: StateFlow<ConnectUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            val key = access.engine.ownerPublicKeyHex()
            mutable.update { it.copy(ownerKey = key) }
        }
    }

    override fun back(): Unit = nav.back()

    override fun connect(address: String, code: String, ownerDeviceId: String) {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            access.engine.open(access.activeProfileId.value)
            val r = access.engine.pair(address, code, ownerDeviceId, access::pairingGateway)
            when (r) {
                is FbResult.Ok -> {
                    mutable.update { it.copy(working = false, paired = true) }
                    nav.back()
                    nav.open(FactsBookRoute.AboutYou)
                }
                is FbResult.Err -> mutable.update { it.copy(working = false, error = r.error) }
            }
        }
    }
}
