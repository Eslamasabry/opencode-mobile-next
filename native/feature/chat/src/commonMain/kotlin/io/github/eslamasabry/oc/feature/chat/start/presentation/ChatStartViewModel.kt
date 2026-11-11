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
import io.github.eslamasabry.oc.core.domain.LocatedFileGateway
import io.github.eslamasabry.oc.core.domain.WorktreeInspectionGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.engine.analytics.health.AppTelemetry
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
import kotlinx.coroutines.withTimeoutOrNull

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
            registry.live.collectLatest graphRead@{ _ ->
                val graph = registry.graphFor(route.ref)
                val gateway = (graph as? ChatGraphAccessors)?.chatGateway
                if (graph == null || gateway == null) {
                    facts.value = ChatStartFacts(null)
                    return@graphRead
                }
                gateway.location.collectLatest locationRead@{ location ->
                    val revision = gateway.scopeRevision
                    fun current(): Boolean =
                        registry.graphFor(route.ref) === graph &&
                            gateway.location.value == location &&
                            gateway.scopeRevision == revision
                    // The connection may still point at another folder (or none) while the
                    // header already knows this conversation's folder. Read the same session.
                    val session =
                        withTimeoutOrNull(3_000) {
                            (graph.session.reads.session(route.ref.sessionId) as? Outcome.Ok)?.value
                        }
                    if (!current()) return@locationRead
                    val directory =
                        session?.directory?.takeIf(String::isNotBlank) ?: location.directory
                    val ownLocation =
                        ServerLocation(
                            directory = directory,
                            workspace =
                                session?.workspaceId?.raw
                                    ?: location.workspace.takeIf {
                                        directory == location.directory
                                    },
                        )
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
                            withTimeoutOrNull(3_000) {
                                val fileGateway =
                                    (gateway as? LocatedFileGateway)?.filesAt(ownLocation)
                                        ?: gateway.takeIf { ownLocation == location }
                                if (gateway.capabilities.value.fileBrowsing)
                                    (fileGateway?.listFiles("") as? Outcome.Ok)?.value
                                else null
                            }
                        }
                        val project = async {
                            withTimeoutOrNull(3_000) {
                                if (gateway.capabilities.value.projectManagement)
                                    ((gateway as? WorktreeInspectionGateway)?.inspectionProject(
                                            ownLocation
                                        ) as? Outcome.Ok)
                                        ?.value
                                else null
                            }
                        }
                        val vcs = async {
                            withTimeoutOrNull(3_000) {
                                // This API has no explicit location argument. Never attach another
                                // folder's branch or changes to this conversation.
                                if (
                                    gateway.capabilities.value.projectManagement &&
                                        ownLocation == location
                                )
                                    ((graph as ChatGraphAccessors)
                                            .chatOperations
                                            .versionControlHealth() as? Outcome.Ok)
                                        ?.value
                                else null
                            }
                        }
                        val rootFiles = files.await()
                        val entries = rootFiles?.count { it.name != ".git" }
                        val projectFacts = project.await()
                        val health = vcs.await()
                        fallback.cancel()
                        if (!current()) return@coroutineScope
                        val git =
                            io.github.eslamasabry.oc.core.model.gitProjectState(
                                health,
                                projectFacts?.isGit,
                                // This folder's own listing says "no Git" only when it is the project's root: a
                                // subfolder of a repository has no `.git` of its own.
                                rootFiles?.any { it.name == ".git" }?.takeIf { found ->
                                    found ||
                                        projectFacts?.directory?.trimEnd('/')?.let {
                                            it.isNotEmpty() && it == ownLocation.directory?.trimEnd('/')
                                        } == true
                                },
                            )
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
        AppTelemetry.current.starterUsed(kind.name)
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
