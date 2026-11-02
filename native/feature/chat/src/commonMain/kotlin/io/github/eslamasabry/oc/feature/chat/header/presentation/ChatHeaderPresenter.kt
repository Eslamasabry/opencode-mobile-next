package io.github.eslamasabry.oc.feature.chat.header.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptState
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHeaderPort
import io.github.eslamasabry.oc.feature.chat.header.world.HeaderFacts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.world.ChatWorld
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    /** A rename the server acknowledged before its event arrived: shown until the server says otherwise. */
    val renamed: String? = null,
    /** The server's title when [renamed] was acknowledged; once the server names it otherwise, the server wins. */
    val renamedOver: String? = null,
    val continueFailure: io.github.eslamasabry.oc.core.common.Failure? = null,
)

internal enum class SheetKind { Details, ContinueWith }

/**
 * The header's state holder (plain `kotlin.test`, no ViewModel): [current] presents the present values; [states] is
 * the production flow. Acts go to the conversation's own port; a failure becomes one plain line with Details.
 */
public class ChatHeaderPresenter(
    private val world: ChatWorld,
    private val port: ChatHeaderPort,
    private val nav: AppNavigator,
    private val launcher: ChatLauncher,
) : ChatHeaderActions {
    private val local = MutableStateFlow(HeaderLocal())
    private var nextId = 1L

    public val ref: ConversationRef get() = world.ref

    public fun current(): ChatHeaderUi =
        present(world.title.value, world.capabilities.value, port.facts.value, world.transcript.value, world.models.value, local.value)

    public fun states(scope: CoroutineScope, started: SharingStarted): StateFlow<ChatHeaderUi> {
        val serverSide =
            combine(world.title, world.capabilities, port.facts, world.transcript, world.models) { t, c, f, tr, m ->
                ServerSide(t, c, f, tr, m)
            }
        return combine(serverSide, local) { s, l -> present(s.title, s.caps, s.facts, s.transcript, s.models, l) }
            .stateIn(scope, started, current())
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
                    val percent = HeaderRules.contextPercent(facts.contextTokens, facts.contextLimit)
                    HeaderSheet.Details(
                        DetailsUi(
                            serverName = facts.serverName,
                            projectName = project,
                            model = models.selected?.label ?: facts.model,
                            agent = models.agent ?: facts.agent,
                            contextPercent = percent,
                            contextLevel = HeaderRules.contextLevel(percent),
                            totalTokens = facts.totalTokens?.takeIf { it > 0 },
                        ),
                    )
                }
                SheetKind.ContinueWith ->
                    HeaderSheet.ContinueWith(
                        choices = continueChoices(models),
                        loading = models.loading || (models.choices.isEmpty() && models.failure == null && l.continueFailure == null),
                        failure = l.continueFailure ?: models.failure,
                    )
            }
        return ChatHeaderUi(
            title = title,
            serverName = facts.serverName,
            projectName = project,
            menu = HeaderRules.menu(caps, shared = facts.shareUrl != null),
            menuOpen = l.menuOpen,
            hasPrompt = transcript.rows.any { it is PromptRow },
            sheet = sheet,
            confirm = l.confirm,
            working = l.working,
            failure = l.failure,
            clip = l.clip,
            renaming = l.renaming,
        )
    }

    private fun shownTitle(serverTitle: String?, l: HeaderLocal): String? =
        if (l.renamed != null && serverTitle == l.renamedOver) l.renamed else serverTitle

    private fun titleNow(): String? = shownTitle(world.title.value, local.value)

    /** "Models on this server": the current one first, marked; at most eight others (Flutter caps the list at six picks). */
    private fun continueChoices(models: ComposerModels): kotlinx.collections.immutable.ImmutableList<ContinueChoice> {
        val current = models.selected
        val others = models.choices.filter { !same(it, current) }.take(MAX_CONTINUE)
        return (listOfNotNull(current) + others)
            .map { ContinueChoice(it.providerId, it.modelId, it.label, current = same(it, current)) }
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
            HeaderAction.Find -> Unit // the chat screen's own find bar
            HeaderAction.Details -> {
                local.update { it.copy(sheet = SheetKind.Details) }
                launcher.launch { port.refresh() }
            }
            HeaderAction.Rename -> renameRequested()
            HeaderAction.ContinueWith -> {
                local.update { it.copy(sheet = SheetKind.ContinueWith, continueFailure = null) }
                if (world.models.value.choices.isEmpty()) loadModels()
            }
            HeaderAction.CopyTranscript -> Unit // needs the person's words: [copyTranscript]
            HeaderAction.Share -> run(action) {
                when (val r = port.share()) {
                    is Outcome.Ok -> {
                        r.value?.let { url -> local.update { it.copy(clip = HeaderClip(nextId++, url, ClipKind.ShareLink)) } }
                        Outcome.Ok(Unit)
                    }
                    is Outcome.Failed -> r
                }
            }
            HeaderAction.Unshare -> run(action) { port.unshare() }
            HeaderAction.Fork -> run(action) {
                when (val r = port.fork()) {
                    is Outcome.Ok -> {
                        nav.openChat(ConversationRef(world.ref.profileId, r.value))
                        Outcome.Ok(Unit)
                    }
                    is Outcome.Failed -> r
                }
            }
            HeaderAction.Archive -> local.update { it.copy(confirm = HeaderConfirm.Archive(titleNow())) }
            HeaderAction.Delete -> local.update { it.copy(confirm = HeaderConfirm.Delete(titleNow())) }
        }
    }

    /** Copy transcript with the person's own words for the headings. */
    override fun copyTranscript(words: TranscriptWords, mask: (String) -> String) {
        local.update { it.copy(menuOpen = false) }
        val text =
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
            port.rename(next).also { r -> if (r is Outcome.Ok) local.update { it.copy(renamed = next, renamedOver = over) } }
        }
    }

    override fun closeSheet() {
        local.update { it.copy(sheet = null, continueFailure = null) }
    }

    override fun continueWith(choice: ContinueChoice) {
        if (choice.current) {
            closeSheet()
            return
        }
        val model = world.models.value.choices.firstOrNull { it.providerId == choice.providerId && it.modelId == choice.modelId }
            ?: return
        local.update { it.copy(working = HeaderAction.ContinueWith, continueFailure = null) }
        launcher.launch {
            when (val r = world.selectModel(model)) {
                is Outcome.Ok -> local.update { it.copy(working = null, sheet = null) }
                is Outcome.Failed -> local.update { it.copy(working = null, continueFailure = r.failure) }
            }
        }
    }

    override fun retryContinueWith() {
        local.update { it.copy(continueFailure = null) }
        loadModels()
    }

    private fun loadModels() {
        launcher.launch {
            val r = world.loadModels()
            if (r is Outcome.Failed) local.update { it.copy(continueFailure = r.failure) }
        }
    }

    override fun confirm() {
        val confirm = local.value.confirm ?: return
        val action = if (confirm is HeaderConfirm.Delete) HeaderAction.Delete else HeaderAction.Archive
        local.update { it.copy(confirm = null) }
        run(action) {
            val r = if (action == HeaderAction.Delete) port.delete() else port.archive()
            if (r is Outcome.Ok) nav.back()
            r
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
                    failure = (r as? Outcome.Failed)?.let { f -> HeaderFailure(nextId++, action, f.failure) },
                )
            }
        }
    }

    private companion object {
        const val MAX_CONTINUE = 8
    }
}
