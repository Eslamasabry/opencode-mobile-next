package io.github.eslamasabry.oc.engine.agents.models

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What [ModelChoices] publishes. [revision] grows by one per published change (one change, one notification). */
public data class ModelChoicesState(
    public val defaultChoice: SessionModelChoice? = null,
    public val sessions: ImmutableMap<String, SessionModelChoice> = persistentMapOf(),
    public val library: ModelLibrary = ModelLibrary(),
    public val catalog: ModelCatalog? = null,
    public val revision: Long = 0,
) {
    /** The chat's own choice, otherwise the default for new chats. */
    public fun modelFor(sessionId: String): ModelRef? = sessions[sessionId]?.model ?: defaultChoice?.model

    public fun variantFor(sessionId: String): String = sessions[sessionId]?.variant ?: defaultChoice?.variant ?: ""

    /** Unknown until a catalog arrives. */
    public fun available(ref: ModelRef): Boolean = catalog?.available(ref) ?: false
}

/**
 * Model selection for one profile when the app (not the server) keeps each chat's choice (Flutter
 * `ConnectionController` model mixin, local mode). A choice made inside a chat belongs to that chat only; the default is
 * for pickers outside a chat and new chats (B-STATE-111/112). Recents record choices, never select (B-STATE-032,
 * B-NAV-033). One serial lane; storage through [ModelStore].
 */
public class ModelChoices(
    private val store: ModelStore,
    private val profile: ProfileId,
    initialDefault: SessionModelChoice? = null,
) {
    private val lane = Mutex()
    private val _state = MutableStateFlow(
        ModelChoicesState(
            defaultChoice = initialDefault,
            sessions = store.sessionModels(profile).toImmutableMap(),
            library = store.modelLibrary(profile),
        ),
    )
    public val state: StateFlow<ModelChoicesState> = _state.asStateFlow()

    private fun publish(change: (ModelChoicesState) -> ModelChoicesState) {
        _state.value = change(_state.value).let { it.copy(revision = _state.value.revision + 1) }
    }

    /** A new catalog. Pruning shortcuts is separate ([pruneUnavailable]) so a failed refresh never erases them. */
    public fun setCatalog(catalog: ModelCatalog?) {
        publish { it.copy(catalog = catalog) }
    }

    private fun acceptable(ref: ModelRef, variant: String): Boolean {
        val s = _state.value
        if (s.catalog != null && !s.catalog.available(ref)) return false
        return s.catalog?.variantAllowed(ref, variant) ?: variant.isEmpty()
    }

    /**
     * Chooses [ref] for [sessionId] only. Unavailable models and variants the catalog does not offer are ignored (no
     * change). [recordRecent] false keeps the recent ring stable while cycling.
     */
    public suspend fun selectForSession(
        sessionId: String,
        ref: ModelRef,
        variant: String = "",
        recordRecent: Boolean = true,
    ): Outcome<Unit> = lane.withLock { selectForSessionLocked(sessionId, ref.normalized, variant, recordRecent) }

    private suspend fun selectForSessionLocked(
        sessionId: String,
        model: ModelRef,
        variant: String,
        recordRecent: Boolean,
    ): Outcome<Unit> {
        if (!acceptable(model, variant)) return Unit.ok()
        val before = _state.value
        val sessions = LinkedHashMap(before.sessions).apply {
            remove(sessionId)
            put(sessionId, SessionModelChoice(model, variant))
        }
        val library = if (recordRecent) before.library.remember(model) else before.library
        publish { it.copy(sessions = sessions.toImmutableMap(), library = library) }
        val libraryWrite = if (recordRecent) store.setModelLibrary(profile, library) else Unit.ok()
        val sessionsWrite = store.setSessionModels(profile, sessions)
        return if (libraryWrite is Outcome.Failed) libraryWrite else sessionsWrite
    }

    /** Sets the default for new chats; chats with their own choice keep it. */
    public suspend fun selectDefault(ref: ModelRef, variant: String = ""): Outcome<Unit> = lane.withLock {
        val model = ref.normalized
        if (!acceptable(model, variant)) return@withLock Unit.ok()
        val library = _state.value.library.remember(model)
        publish { it.copy(defaultChoice = SessionModelChoice(model, variant), library = library) }
        store.setModelLibrary(profile, library)
    }

    /** Favoriting never selects. An unavailable model can only be un-favorited. A refused save is rolled back. */
    public suspend fun toggleFavorite(model: ModelRef): Outcome<Unit> = lane.withLock {
        val before = _state.value.library
        if (!_state.value.available(model) && !before.isFavorite(model)) return@withLock Unit.ok()
        val next = before.toggleFavorite(model)
        publish { it.copy(library = next) }
        store.setModelLibrary(profile, next).also { result ->
            if (result is Outcome.Failed) publish { it.copy(library = before) }
        }
    }

    /**
     * Moves only [sessionId] to the next available recent (or favorite) model. Cycling the recent ring does not reorder
     * it, so repeated taps visit every model instead of bouncing between two. Null when there is nowhere to go.
     */
    public suspend fun cycleForSession(
        sessionId: String,
        reverse: Boolean = false,
        favoritesOnly: Boolean = false,
    ): Outcome<ModelRef?> = lane.withLock {
        val s = _state.value
        val next = s.library.next(s.modelFor(sessionId), reverse, favoritesOnly, s::available)
            ?: return@withLock null.ok()
        when (val written = selectForSessionLocked(sessionId, next, "", recordRecent = favoritesOnly)) {
            is Outcome.Failed -> written
            is Outcome.Ok -> next.ok()
        }
    }

    /** After a successful catalog refresh: drops shortcuts the server no longer offers. */
    public suspend fun pruneUnavailable(): Outcome<Unit> = lane.withLock {
        val s = _state.value
        val catalog = s.catalog ?: return@withLock Unit.ok()
        val next = s.library.retainWhere(catalog::available)
        if (next == s.library) return@withLock Unit.ok()
        publish { it.copy(library = next) }
        store.setModelLibrary(profile, next)
    }

    /** The chat is gone: its choice goes with it. */
    public suspend fun forgetSession(sessionId: String): Outcome<Unit> = lane.withLock {
        val sessions = _state.value.sessions
        if (sessionId !in sessions) return@withLock Unit.ok()
        val next = LinkedHashMap(sessions).apply { remove(sessionId) }
        publish { it.copy(sessions = next.toImmutableMap()) }
        store.setSessionModels(profile, next)
    }
}
