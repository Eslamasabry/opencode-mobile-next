package io.github.eslamasabry.oc.feature.chat.start.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.engine.composer.phrases.ChatStartFacts
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind
import io.github.eslamasabry.oc.engine.composer.phrases.Starters
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.ChatGraphAccessors
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class StartInsert(val id: Long, val text: String)

public data class ChatStartUi(
    val facts: ChatStartFacts = ChatStartFacts.pending(null),
    val showSuggestions: Boolean = false,
    val emptyEligible: Boolean = false,
    val insert: StartInsert? = null,
)

public interface ChatStartActions {
    public fun chooseStarter(kind: StarterKind, label: String)

    public fun insertDone(id: Long)
}

/**
 * Reads facts only for a visible empty chat; the connection and location fence every late answer.
 */
@AssistedInject
public class ChatStartViewModel(
    @Assisted private val route: ChatRoute,
    private val registry: ConnectionRegistry,
    private val worlds: ChatWorlds,
) : ViewModel(), ChatStartActions {
    private val world = worlds.open(route.ref)
    private val facts = MutableStateFlow(ChatStartFacts.pending(null))
    private val insert = MutableStateFlow<StartInsert?>(null)
    private var reads: Job? = null
    private var nextInsert = 0L

    public val state: StateFlow<ChatStartUi> =
        combine(facts, world.draft, insert, world.hasOlder) { f, draft, pending, older ->
                ChatStartUi(
                    f,
                    draft.ready && draft.text.isBlank() && draft.attachments.isEmpty(),
                    !older,
                    pending,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatStartUi())

    public fun visible(visible: Boolean) {
        if (!visible) {
            reads?.cancel()
            reads = null
            return
        }
        if (reads != null || world.hasOlder.value) return
        reads = viewModelScope.launch {
            registry.live.collectLatest graphRead@{ live ->
                val graph = live[route.ref.profileId]
                val gateway = (graph as? ChatGraphAccessors)?.chatGateway
                if (graph == null || gateway == null) {
                    facts.value = ChatStartFacts(null)
                    return@graphRead
                }
                gateway.location.collectLatest locationRead@{ location ->
                    val directory = location.directory
                    fun current(): Boolean =
                        registry.graphFor(route.ref) === graph && gateway.location.value == location
                    val bare = ChatStartFacts(directory)
                    facts.value = ChatStartFacts.pending(directory)
                    if (!bare.hasProject) {
                        facts.value = bare
                        return@locationRead
                    }
                    coroutineScope {
                        val fallback = launch {
                            delay(3_000)
                            if (current()) facts.value = bare
                        }
                        val files = async {
                            if (gateway.capabilities.value.fileBrowsing)
                                (gateway.listFiles("") as? Outcome.Ok)?.value
                            else null
                        }
                        val vcs = async {
                            ((graph as ChatGraphAccessors).chatOperations.versionControlHealth()
                                    as? Outcome.Ok)
                                ?.value
                        }
                        val entries = files.await()?.count { it.name != ".git" }
                        val health = vcs.await()
                        fallback.cancel()
                        if (!current()) return@coroutineScope
                        val git =
                            when (health?.setupState) {
                                VersionControlSetupState.Git -> true
                                VersionControlSetupState.Absent -> false
                                else -> true.takeIf { !health?.branch.isNullOrBlank() }
                            }
                        val branch = health?.branch?.trim().orEmpty()
                        facts.value =
                            bare.copy(
                                entries = entries,
                                git = git,
                                hasHistory =
                                    git != false && branch.isNotEmpty() && branch != "HEAD",
                                changes = health?.changes?.size,
                            )
                    }
                }
            }
        }
    }

    override fun chooseStarter(kind: StarterKind, label: String) {
        val draft = world.draft.value
        if (
            reads == null ||
                !draft.ready ||
                draft.text.isNotBlank() ||
                draft.attachments.isNotEmpty() ||
                kind !in Starters.suggestions(facts.value)
        )
            return
        nextInsert += 1
        insert.value = StartInsert(Long.MIN_VALUE + nextInsert, Starters.insertText(label))
    }

    override fun insertDone(id: Long) {
        if (insert.value?.id == id) insert.value = null
    }

    override fun onCleared() {
        reads?.cancel()
        worlds.release(world)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatStartViewModel
    }
}
