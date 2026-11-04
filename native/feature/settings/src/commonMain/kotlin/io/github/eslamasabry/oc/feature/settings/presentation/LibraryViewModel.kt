package io.github.eslamasabry.oc.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.engine.setupassist.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LibraryRoute
import io.github.eslamasabry.oc.ui.routes.PhoneRoute
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Platform bindings supply real authenticated sources; desktop has no phone account-management
 * capability.
 */
public interface LibraryHost {
    public fun open(ref: ConversationRef?, scope: CoroutineScope): LibrarySession?

    public fun openProfile(profile: ProfileId, scope: CoroutineScope): LibrarySession? = null

    public suspend fun copy(text: String): Outcome<Unit>

    /** A shown host and opaque approved token; authorization URLs never enter screen state. */
    public fun checkLink(url: String): LibraryLink?

    public fun openLink(link: LibraryLink): Outcome<Unit>

    public suspend fun reference(ref: ConversationRef, reference: ReferenceInfo): Outcome<Unit>

    public suspend fun registry(profile: ProfileId): SetupRegistryStore?

    public fun closeRegistry(store: SetupRegistryStore)

    public fun onPhone(ref: ConversationRef?): Boolean

    /** One confirmation, bound to the captured connection and picker selection. */
    public fun skillConversation(
        profile: ProfileId,
        selection: SessionSelection?,
    ): SkillConversation?
}

public data class LibraryLink(val host: String, val token: Any)

@ContributesTo(AppScope::class)
public interface LibraryHosts {
    @Multibinds(allowEmpty = true) public val libraryHosts: Set<LibraryHost>
}

public enum class LibraryTab {
    Commands,
    Skills,
    References,
    Providers,
    Mcp,
}

public sealed interface LibrarySheet {
    public data class Command(val value: CommandInfo) : LibrarySheet

    public data class Skill(val value: SkillInfo) : LibrarySheet

    public data class Reference(val value: ReferenceInfo) : LibrarySheet

    public data class Provider(val value: IntegrationInfo) : LibrarySheet

    public data class Method(val provider: IntegrationInfo, val method: IntegrationMethodInfo) :
        LibrarySheet

    public data class Accounts(val provider: IntegrationInfo) : LibrarySheet

    public data class Credential(
        val provider: IntegrationInfo,
        val value: IntegrationConnectionInfo,
    ) : LibrarySheet

    public data class SignIn(val value: LibraryAttempt) : LibrarySheet

    public data class Uncertain(val provider: String) : LibrarySheet

    public data class Mcp(val value: McpServerInfo) : LibrarySheet

    public data class AddMcp(val prefill: McpServerDraft? = null) : LibrarySheet

    public data object McpAddChoice : LibrarySheet

    public data object Registry : LibrarySheet

    public data class RegistryNode(val draft: McpServerDraft) : LibrarySheet
}

public data class LibraryUi(
    val tab: LibraryTab = LibraryTab.Commands,
    val inventory: LibraryInventory = LibraryInventory(),
    val available: Boolean = false,
    val sheet: LibrarySheet? = null,
    val working: Boolean = false,
    val locked: Boolean = false,
    val link: LibraryLink? = null,
    val ref: ConversationRef? = null,
    val notice: LibraryNotice? = null,
    val registry: SetupRegistrySnapshot? = null,
    val onPhone: Boolean = false,
    val skillConversation: SkillConversationResult? = null,
    val skillConversationRef: ConversationRef? = null,
)

public interface LibraryActions {
    public fun back()

    public fun tab(value: LibraryTab)

    public fun refresh()

    public fun show(sheet: LibrarySheet?)

    public fun run(command: CommandInfo, session: SessionId, arguments: String)

    public fun activate(skill: SkillInfo, session: SessionId, resume: Boolean)

    public fun useSkillInNewConversation(skill: SkillInfo)

    public fun openSkillConversation()

    public fun copy(value: String)

    public fun addReference(reference: ReferenceInfo)

    public fun key(provider: IntegrationInfo, key: String)

    public fun start(
        provider: IntegrationInfo,
        method: IntegrationMethodInfo,
        inputs: ImmutableMap<String, String>,
    )

    public fun disconnect(provider: IntegrationInfo)

    public fun credential(
        provider: IntegrationInfo,
        credential: IntegrationConnectionInfo,
        action: String,
        label: String = "",
    )

    public fun mcp(server: McpServerInfo, remove: Boolean = false)

    public fun addMcp(draft: McpServerDraft)

    public fun check(attempt: LibraryAttempt, code: String? = null, cancel: Boolean = false)

    public fun forget(attempt: LibraryAttempt)

    public fun forgetUncertain(id: String)

    public fun retrySave()

    public fun openBrowser(attempt: LibraryAttempt)

    public fun confirmLink()

    public fun cancelLink()

    public fun external(url: String)

    public fun launch(attempt: LibraryAttempt): IntegrationAuthLaunch?

    public fun registryEnable()

    public fun registrySearch(query: String)

    public fun registryRefresh(query: String)

    public fun registryForget()

    public fun phoneTools()
}

@AssistedInject
public class LibraryViewModel(
    @Assisted private val route: LibraryRoute,
    hosts: Set<LibraryHost>,
    private val nav: AppNavigator,
) : ViewModel(), LibraryActions {
    private val host = hosts.singleOrNull()
    private val session =
        if (route.ref == null && route.profileId != null)
            host?.openProfile(route.profileId!!, viewModelScope)
        else host?.open(route.ref, viewModelScope)
    private val initialTab =
        LibraryTab.entries.firstOrNull { it.name.equals(route.tab, ignoreCase = true) }
            ?: LibraryTab.Commands
    private val mutable =
        MutableStateFlow(
            LibraryUi(
                tab = initialTab,
                available = session != null,
                ref = route.ref,
                onPhone = host?.onPhone(route.ref) == true,
            )
        )
    public val state: StateFlow<LibraryUi> = mutable.asStateFlow()
    private var linkAttempt: LibraryAttempt? = null
    private var registryStore: SetupRegistryStore? = null
    private var registrySearchJob: Job? = null
    private var registryObserve: Job? = null
    private var registryGeneration = 0
    private var cancelAuthOnDecline = false

    init {
        session?.let { library ->
            viewModelScope.launch {
                library.state.collect { value -> mutable.update { it.copy(inventory = value) } }
            }
            refresh()
        }
    }

    override fun back() {
        if (mutable.value.link != null) cancelLink()
        else if (mutable.value.sheet != null) show(null) else nav.back()
    }

    override fun tab(value: LibraryTab) {
        if (mutable.value.working) return
        show(null)
        mutable.update { it.copy(tab = value, locked = false) }
    }

    override fun refresh() {
        viewModelScope.launch { session?.refresh() }
    }

    override fun show(sheet: LibrarySheet?) {
        if (mutable.value.working) return
        val generation = ++registryGeneration
        if (mutable.value.sheet == LibrarySheet.Registry && sheet != LibrarySheet.Registry) {
            registrySearchJob?.cancel()
            registryObserve?.cancel()
            registryStore?.let { host?.closeRegistry(it) }
            registryStore = null
            mutable.update { it.copy(registry = null) }
        }
        mutable.update {
            it.copy(
                sheet = sheet,
                locked = false,
                notice = null,
                skillConversation = null,
                skillConversationRef = null,
            )
        }
        if (sheet == LibrarySheet.Registry && registryStore == null)
            viewModelScope.launch {
                val profile = session?.profileId ?: return@launch
                val store = host?.registry(profile) ?: return@launch
                if (
                    generation != registryGeneration ||
                        mutable.value.sheet != LibrarySheet.Registry ||
                        registryStore != null
                ) {
                    host?.closeRegistry(store)
                    return@launch
                }
                registryStore = store
                store.load()
                registryObserve = viewModelScope.launch {
                    store.snapshots.collect { value ->
                        if (registryStore === store) mutable.update { it.copy(registry = value) }
                    }
                }
                if (store.snapshot.optedIn) store.refresh()
            }
    }

    override fun registryEnable() {
        viewModelScope.launch {
            registryStore?.let { if (it.setOptIn(true) is SetupOutcome.Ok) it.refresh() }
        }
    }

    override fun registrySearch(query: String) {
        registrySearchJob?.cancel()
        registrySearchJob = viewModelScope.launch {
            registryStore?.let {
                if (query.isBlank()) it.showSaved()
                else {
                    kotlinx.coroutines.delay(500)
                    it.refresh(query = query)
                }
            }
        }
    }

    override fun registryRefresh(query: String) {
        registrySearchJob?.cancel()
        registrySearchJob = viewModelScope.launch { registryStore?.refresh(query = query) }
    }

    override fun registryForget() {
        registrySearchJob?.cancel()
        viewModelScope.launch {
            if (registryStore?.clear() is SetupOutcome.Failed)
                mutable.update { it.copy(notice = LibraryNotice.Storage) }
        }
    }

    override fun phoneTools() {
        nav.open(PhoneRoute.ThisPhone)
    }

    private fun act(block: suspend (LibrarySession) -> Outcome<Unit>, success: () -> Unit = {}) {
        val library = session ?: return
        if (mutable.value.working || mutable.value.locked) return
        mutable.update { it.copy(working = true, notice = null) }
        viewModelScope.launch {
            try {
                val result = block(library)
                if (result is Outcome.Ok) success()
            } finally {
                mutable.update { it.copy(working = false) }
            }
        }
    }

    override fun run(command: CommandInfo, session: SessionId, arguments: String) {
        act({ it.runCommand(session, command, arguments) }) {
            this.session?.profileId?.let { nav.openChat(ConversationRef(it, session)) }
        }
        // Dispatch from this confirmation once, even if its acknowledgement is lost.
        mutable.update { it.copy(locked = true) }
    }

    override fun activate(skill: SkillInfo, session: SessionId, resume: Boolean) {
        act({ it.activateSkill(session, skill, resume) }) {
            mutable.update { it.copy(sheet = null, locked = false) }
        }
        mutable.update { it.copy(locked = true) }
    }

    override fun useSkillInNewConversation(skill: SkillInfo) {
        val library = session ?: return
        val profile = library.profileId ?: return
        if (
            mutable.value.working || mutable.value.locked || mutable.value.skillConversation != null
        )
            return
        if (mutable.value.inventory.skills.none { it == skill } || skill.id.isNullOrBlank()) return
        // Reopening the sheet must not re-arm a confirmation that may already have reached the
        // server (Dart: never duplicates on failure, no retry).
        val skillId = skill.id ?: return
        if (skillId in consumedSkills) {
            mutable.update {
                it.copy(
                    skillConversation =
                        SkillConversationResult(SkillConversationStatus.AlreadyStarted),
                    skillConversationRef = consumedSkills[skillId],
                )
            }
            return
        }
        val selection =
            route.ref?.sessionId?.let { id ->
                mutable.value.inventory.conversations.firstOrNull { it.id == id }?.selection
            }
        val action = host?.skillConversation(profile, selection)
        if (action == null) {
            mutable.update {
                it.copy(
                    skillConversation = SkillConversationResult(SkillConversationStatus.Changed)
                )
            }
            return
        }
        // Keep this confirmation consumed after failure: creation/activation may have reached the
        // server.
        mutable.update { it.copy(working = true, locked = true, notice = null) }
        consumedSkills[skillId] = null
        viewModelScope.launch {
            val result = action.useInNewConversation(skillId)
            if (result.status in NOTHING_SENT) consumedSkills -= skillId
            else
                consumedSkills[skillId] =
                    result.sessionId?.let { id -> ConversationRef(profile, SessionId(id)) }
            mutable.update {
                it.copy(
                    working = false,
                    skillConversation = result,
                    skillConversationRef =
                        result.sessionId?.let { id -> ConversationRef(profile, SessionId(id)) },
                )
            }
            if (result.status == SkillConversationStatus.Ready) library.refresh()
        }
    }

    /** Skills whose confirmation was spent, with the conversation it made when known. */
    private val consumedSkills = mutableMapOf<String, ConversationRef?>()

    private companion object {
        /** Outcomes refused before anything reached the server; the person may try again. */
        val NOTHING_SENT =
            setOf(
                SkillConversationStatus.Unsupported,
                SkillConversationStatus.InvalidInput,
                SkillConversationStatus.Changed,
            )
    }

    override fun openSkillConversation() {
        mutable.value.skillConversationRef?.let { nav.openChat(it, focusComposer = true) }
    }

    override fun copy(value: String) {
        viewModelScope.launch {
            val copied = host?.copy(value)
            mutable.update {
                it.copy(
                    notice = if (copied is Outcome.Ok) LibraryNotice.Done else LibraryNotice.Failed
                )
            }
        }
    }

    override fun addReference(reference: ReferenceInfo) {
        val ref = route.ref ?: return
        viewModelScope.launch {
            val result = host?.reference(ref, reference)
            if (result is Outcome.Ok) nav.openChat(ref, focusComposer = true)
            else mutable.update { it.copy(notice = LibraryNotice.Failed) }
        }
    }

    override fun key(provider: IntegrationInfo, key: String) =
        act({ it.connectKey(provider, key) }) { mutable.update { it.copy(sheet = null) } }

    override fun start(
        provider: IntegrationInfo,
        method: IntegrationMethodInfo,
        inputs: ImmutableMap<String, String>,
    ) {
        val library = session ?: return
        if (mutable.value.working) return
        mutable.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val launch = library.start(provider, method, inputs)
                val entry =
                    library.state.value.attempts.lastOrNull {
                        it.integration == provider.id &&
                            (launch == null || it.attempt == launch.attemptId)
                    }
                mutable.update {
                    it.copy(
                        sheet =
                            entry?.let { LibrarySheet.SignIn(it) }
                                ?: if (provider.id in library.state.value.uncertain)
                                    LibrarySheet.Uncertain(provider.id)
                                else it.sheet
                    )
                }
                if (launch != null && entry != null && !entry.command)
                    requestBrowser(entry, cancelOnDecline = true)
            } finally {
                mutable.update { it.copy(working = false) }
            }
        }
    }

    override fun disconnect(provider: IntegrationInfo) =
        act({ it.disconnect(provider) }) { mutable.update { it.copy(sheet = null) } }

    override fun credential(
        provider: IntegrationInfo,
        credential: IntegrationConnectionInfo,
        action: String,
        label: String,
    ) =
        act({ it.credential(credential.id ?: "", action, label) }) {
            mutable.update { it.copy(sheet = LibrarySheet.Accounts(provider)) }
        }

    override fun mcp(server: McpServerInfo, remove: Boolean) =
        act({ it.mcp(server, remove) }) { mutable.update { it.copy(sheet = null) } }

    override fun addMcp(draft: McpServerDraft) =
        act({ it.addMcp(draft) }) { mutable.update { it.copy(sheet = null) } }

    override fun check(attempt: LibraryAttempt, code: String?, cancel: Boolean) {
        if (mutable.value.working) return
        mutable.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val result = session?.check(attempt, code, cancel)
                if (cancel && result == IntegrationAuthState.Expired)
                    mutable.update { it.copy(sheet = null) }
            } finally {
                mutable.update { it.copy(working = false) }
            }
        }
    }

    override fun forget(attempt: LibraryAttempt) {
        viewModelScope.launch {
            session?.forget(attempt)
            mutable.update { it.copy(sheet = null) }
        }
    }

    override fun forgetUncertain(id: String) {
        session?.forgetUncertain(id)
        show(null)
    }

    override fun retrySave() {
        viewModelScope.launch { session?.retryPersistence() }
    }

    override fun launch(attempt: LibraryAttempt): IntegrationAuthLaunch? = session?.launch(attempt)

    override fun openBrowser(attempt: LibraryAttempt) =
        requestBrowser(attempt, cancelOnDecline = false)

    private fun requestBrowser(attempt: LibraryAttempt, cancelOnDecline: Boolean) {
        if (session?.isCurrent() != true) {
            mutable.update { it.copy(notice = LibraryNotice.Changed) }
            return
        }
        val launch = launch(attempt) ?: return
        val safe = parseAuthorizationUrl(launch.url)
        val checked = safe?.let { host?.checkLink(it) }
        if (checked == null) {
            mutable.update { it.copy(notice = LibraryNotice.Failed) }
            return
        }
        linkAttempt = attempt
        cancelAuthOnDecline = cancelOnDecline
        mutable.update { it.copy(link = checked) }
    }

    override fun external(url: String) {
        val link = host?.checkLink(url) ?: return
        linkAttempt = null
        cancelAuthOnDecline = false
        mutable.update { it.copy(link = link) }
    }

    override fun confirmLink() {
        if (mutable.value.working) return
        val link = mutable.value.link ?: return
        val attempt = linkAttempt
        if (session?.isCurrent() != true) {
            mutable.update { it.copy(link = null, notice = LibraryNotice.Changed) }
            return
        }
        val code = attempt?.let(::launch)?.instructions?.let(::libraryDeviceCode)
        mutable.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                if (code != null) host?.copy(code)
                if (mutable.value.link !== link || session?.isCurrent() != true) return@launch
                val opened = host?.openLink(link)
                if (opened is Outcome.Ok && attempt != null) session?.attach(attempt)
                else if (opened !is Outcome.Ok)
                    mutable.update { it.copy(notice = LibraryNotice.Failed) }
                linkAttempt = null
                cancelAuthOnDecline = false
                mutable.update { it.copy(link = null) }
            } finally {
                mutable.update { it.copy(working = false) }
            }
        }
    }

    override fun cancelLink() {
        val pending = linkAttempt.takeIf { cancelAuthOnDecline }
        linkAttempt = null
        cancelAuthOnDecline = false
        mutable.update { it.copy(link = null) }
        if (pending != null) viewModelScope.launch { session?.check(pending, cancel = true) }
    }

    override fun onCleared() {
        session?.close()
        registryStore?.let { host?.closeRegistry(it) }
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(route: LibraryRoute): LibraryViewModel
    }
}

/** A device code is only an uppercase token after the word code, never arbitrary provider prose. */
public fun libraryDeviceCode(instructions: String): String? =
    Regex(
            "(?:[Ee]nter\\s+(?:the\\s+|your\\s+)?)?[Cc]ode\\s*[:：]\\s*([A-Z0-9]{3,}(?:-[A-Z0-9]{2,})*)(?![A-Za-z0-9-])"
        )
        .find(instructions)
        ?.groupValues
        ?.get(1)
        ?.takeIf { it.length in 4..24 }

/** Front matter and a repeated heading belong to Raw, rather than the instructions preview. */
public fun librarySkillProse(content: String, name: String): String {
    var text =
        content.replaceFirst(Regex("^﻿?---\\r?\\n[\\s\\S]*?\\r?\\n---[ \\t]*(\\r?\\n|$)"), "")
    val heading = Regex("^\\s*#[ \\t]+([^\\n]+)\\n?").find(text)
    fun plain(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "")
    if (heading != null && plain(name).isNotEmpty() && plain(heading.groupValues[1]) == plain(name))
        text = text.substring(heading.range.last + 1)
    return text.trimStart()
}
