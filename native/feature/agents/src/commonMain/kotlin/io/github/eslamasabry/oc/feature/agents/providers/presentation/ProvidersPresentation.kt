package io.github.eslamasabry.oc.feature.agents.providers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.model.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** The original graph remains the owner of an attempt, even if its selected project changes. */
public class ProviderConnection(
    val operations: ServerOperationsGateway,
    val supported: Boolean,
    val isCurrent: () -> Boolean,
    val atLocation: () -> Boolean,
)

public fun interface ProviderSource {
    public suspend fun connect(profile: ProfileId): Outcome<ProviderConnection>
}

public data class ProviderLink(val token: Long, val host: String, val insecure: Boolean)

public interface ProviderBrowser {
    public fun review(url: String): ProviderLink?

    public suspend fun open(token: Long): Outcome<Unit>

    public fun discard(token: Long)
}

@ContributesTo(AppScope::class)
public interface ProviderBrowserBindings {
    @Multibinds(allowEmpty = true) public val providerBrowsers: Set<ProviderBrowser>
}

public data class ProviderField(val key: String, val label: String, val required: Boolean)

public data class ProviderMethod(
    val id: String,
    val label: String,
    val fields: ImmutableList<ProviderField>,
)

public data class ProviderRow(
    val id: String,
    val name: String,
    val connected: Boolean,
    val stored: Boolean,
    val key: Boolean,
    val methods: ImmutableList<ProviderMethod>,
)

public enum class ProviderIssue {
    Unavailable,
    Load,
    Changed,
    InvalidInput,
    Failed,
    Pending,
    Complete,
    Expired,
    Cancelled,
    Connected,
    Disconnected,
}

public data class ProvidersUi(
    val loading: Boolean = true,
    val working: Boolean = false,
    val rows: ImmutableList<ProviderRow> = persistentListOf(),
    val selected: String? = null,
    val method: ProviderMethod? = null,
    val keyEntry: Boolean = false,
    val attempt: Boolean = false,
    val codeMode: Boolean = false,
    val link: ProviderLink? = null,
    val issue: ProviderIssue? = null,
)

public interface ProvidersActions {
    public fun back()

    public fun refresh()

    public fun select(id: String)

    public fun selectMethod(id: String)

    public fun selectKey()

    public fun start(inputs: Map<String, String>)

    public fun connectKey(key: String)

    public fun disconnect()

    public fun confirmBrowser()

    public fun dismissBrowser()

    public fun check()

    public fun complete(code: String)

    public fun cancel()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ProvidersViewModel(
    private val source: ProviderSource,
    private val browsers: Set<ProviderBrowser>,
    private val nav: AppNavigator,
) : ViewModel(), ProvidersActions {
    private val mutable = MutableStateFlow(ProvidersUi())
    public val state: StateFlow<ProvidersUi> = mutable.asStateFlow()
    private var profile: ProfileId? = null
    private var connection: ProviderConnection? = null
    private var inventory: List<IntegrationInfo> = emptyList()
    private var attemptId: String? = null
    private var generation = 0
    private var job: Job? = null
    private val browser
        get() = browsers.singleOrNull()

    public fun open(profile: ProfileId) {
        if (this.profile == profile) return
        job?.cancel()
        generation++
        this.profile = profile
        connection = null
        inventory = emptyList()
        attemptId = null
        mutable.value = ProvidersUi()
        refresh()
    }

    override fun back() = nav.back()

    override fun refresh() {
        val id = profile ?: return
        if (mutable.value.working || attemptId != null || job?.isActive == true) return
        val epoch = generation
        job = viewModelScope.launch {
            mutable.update { it.copy(loading = true, issue = null) }
            val target =
                when (val result = source.connect(id)) {
                    is Outcome.Ok -> result.value
                    is Outcome.Failed -> {
                        if (epoch == generation)
                            mutable.update {
                                it.copy(loading = false, issue = ProviderIssue.Unavailable)
                            }
                        return@launch
                    }
                }
            connection = target
            if (!target.supported) {
                mutable.update { it.copy(loading = false, issue = ProviderIssue.Unavailable) }
                return@launch
            }
            val result = target.operations.integrations()
            if (epoch != generation || !target.isCurrent() || !target.atLocation()) {
                if (epoch == generation)
                    mutable.update { it.copy(loading = false, issue = ProviderIssue.Changed) }
                return@launch
            }
            when (result) {
                is Outcome.Failed ->
                    mutable.update { it.copy(loading = false, issue = ProviderIssue.Load) }
                is Outcome.Ok -> {
                    inventory = result.value
                    mutable.update {
                        it.copy(loading = false, rows = result.value.map(::row).toImmutableList())
                    }
                }
            }
        }
    }

    private fun row(info: IntegrationInfo): ProviderRow =
        ProviderRow(
            info.id,
            info.name,
            info.connectionCount > 0,
            info.connections.any { it.type == "credential" && !it.id.isNullOrEmpty() },
            info.methods.any { it.type == "key" },
            info.methods
                .filter { it.type == "oauth" && it.id != null }
                .map { method ->
                    ProviderMethod(
                        method.id!!,
                        method.label,
                        method.prompts
                            .map { field ->
                                ProviderField(
                                    (field["key"] as JsonPrimitive).content,
                                    (field["message"] as JsonPrimitive).content,
                                    (field["required"] as? JsonPrimitive)?.booleanOrNull == true,
                                )
                            }
                            .toImmutableList(),
                    )
                }
                .toImmutableList(),
        )

    override fun select(id: String) {
        if (attemptId != null || mutable.value.working) return
        if (inventory.none { it.id == id }) return
        mutable.update { it.copy(selected = id, method = null, keyEntry = false, issue = null) }
    }

    override fun selectMethod(id: String) {
        if (attemptId != null || mutable.value.working) return
        val method =
            mutable.value.rows
                .firstOrNull { it.id == mutable.value.selected }
                ?.methods
                ?.firstOrNull { it.id == id } ?: return
        mutable.update { it.copy(method = method, keyEntry = false, issue = null) }
    }

    override fun selectKey() {
        if (attemptId == null && !mutable.value.working)
            mutable.update { it.copy(method = null, keyEntry = true, issue = null) }
    }

    private fun target(requireLocation: Boolean = true): ProviderConnection? {
        val target = connection
        if (target == null || !target.isCurrent() || (requireLocation && !target.atLocation())) {
            mutable.update { it.copy(issue = ProviderIssue.Changed) }
            return null
        }
        return target
    }

    private fun work(
        requireLocation: Boolean = true,
        command: suspend (ProviderConnection) -> ProviderIssue,
    ) {
        if (mutable.value.working || mutable.value.loading) return
        val target = target(requireLocation) ?: return
        val epoch = generation
        mutable.update { it.copy(working = true, issue = null) }
        job = viewModelScope.launch {
            val issue = command(target)
            if (epoch != generation) return@launch
            if (!target.isCurrent()) {
                mutable.update {
                    it.copy(working = false, issue = ProviderIssue.Changed, link = null)
                }
                return@launch
            }
            mutable.update { it.copy(working = false, issue = issue) }
        }
    }

    override fun start(inputs: Map<String, String>) {
        val id = mutable.value.selected ?: return
        val chosen = mutable.value.method ?: return
        val method =
            inventory.firstOrNull { it.id == id }?.methods?.firstOrNull { it.id == chosen.id }
                ?: return
        if (
            chosen.fields.any { it.required && inputs[it.key].isNullOrBlank() } ||
                inputs.any { (k, v) -> chosen.fields.none { it.key == k } || v.length > 65536 }
        ) {
            mutable.update { it.copy(issue = ProviderIssue.InvalidInput) }
            return
        }
        val answers = (method.presetAnswers + inputs).toImmutableMap()
        work { target ->
            when (val result = target.operations.startIntegrationOAuth(id, chosen.id, answers)) {
                is Outcome.Failed -> ProviderIssue.Failed
                is Outcome.Ok -> {
                    if (connection !== target || !target.isCurrent())
                        return@work ProviderIssue.Changed
                    attemptId = result.value.attemptId
                    val link = browser?.review(result.value.url)
                    mutable.update {
                        it.copy(
                            attempt = true,
                            codeMode = result.value.mode == IntegrationAuthMode.Code,
                            link = link,
                        )
                    }
                    if (link == null) ProviderIssue.Failed else ProviderIssue.Pending
                }
            }
        }
    }

    override fun connectKey(key: String) {
        val id = mutable.value.selected ?: return
        if (key.isBlank()) {
            mutable.update { it.copy(issue = ProviderIssue.InvalidInput) }
            return
        }
        work { target ->
            if (target.operations.connectIntegrationKey(id, key) is Outcome.Ok)
                ProviderIssue.Connected
            else ProviderIssue.Failed
        }
    }

    override fun disconnect() {
        val selected = inventory.firstOrNull { it.id == mutable.value.selected } ?: return
        work { target ->
            if (target.operations.disconnectIntegration(selected) is Outcome.Ok)
                ProviderIssue.Disconnected
            else ProviderIssue.Failed
        }
    }

    override fun confirmBrowser() {
        val link = mutable.value.link ?: return
        mutable.update { it.copy(link = null) }
        work(false) {
            if (browser?.open(link.token) is Outcome.Ok) ProviderIssue.Pending
            else ProviderIssue.Failed
        }
    }

    override fun dismissBrowser() {
        mutable.value.link?.let { browser?.discard(it.token) }
        mutable.update { it.copy(link = null) }
    }

    private suspend fun checked(target: ProviderConnection, id: String): ProviderIssue =
        when (val result = target.operations.integrationOAuthStatus(id)) {
            is Outcome.Failed -> ProviderIssue.Failed
            is Outcome.Ok ->
                if (connection !== target || !target.isCurrent()) ProviderIssue.Changed
                else
                    when (result.value.state) {
                        IntegrationAuthState.Pending -> ProviderIssue.Pending
                        IntegrationAuthState.Complete -> {
                            attemptId = null
                            mutable.update { it.copy(attempt = false) }
                            ProviderIssue.Complete
                        }
                        IntegrationAuthState.Expired -> {
                            attemptId = null
                            mutable.update { it.copy(attempt = false) }
                            ProviderIssue.Expired
                        }
                        IntegrationAuthState.Failed -> {
                            attemptId = null
                            mutable.update { it.copy(attempt = false) }
                            ProviderIssue.Failed
                        }
                    }
        }

    override fun check() {
        val id = attemptId ?: return
        work(false) { checked(it, id) }
    }

    override fun complete(code: String) {
        val id = attemptId ?: return
        if (mutable.value.codeMode && code.isBlank()) {
            mutable.update { it.copy(issue = ProviderIssue.InvalidInput) }
            return
        }
        work(false) { target ->
            when (target.operations.completeIntegrationOAuth(id, code.takeIf { it.isNotBlank() })) {
                is Outcome.Ok -> checked(target, id)
                is Outcome.Failed -> ProviderIssue.Failed
            }
        }
    }

    override fun cancel() {
        val id = attemptId ?: return
        work(false) { target ->
            when (target.operations.cancelIntegrationOAuth(id)) {
                is Outcome.Ok -> {
                    if (connection !== target || !target.isCurrent()) ProviderIssue.Changed
                    else {
                        attemptId = null
                        mutable.value.link?.let { browser?.discard(it.token) }
                        mutable.update { it.copy(attempt = false, link = null) }
                        ProviderIssue.Cancelled
                    }
                }
                is Outcome.Failed -> ProviderIssue.Failed
            }
        }
    }

    override fun onCleared() {
        mutable.value.link?.let { browser?.discard(it.token) }
        super.onCleared()
    }
}
