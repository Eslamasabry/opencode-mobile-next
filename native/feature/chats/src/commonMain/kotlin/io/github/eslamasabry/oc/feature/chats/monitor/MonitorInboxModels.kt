package io.github.eslamasabry.oc.feature.chats.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public enum class MonitoredRowKind {
    Permission,
    Question,
    Form,
    CheckIn,
    FailedRun,
}

public data class MonitoredInboxRow(
    val key: String,
    val title: String?,
    val server: String,
    val kind: MonitoredRowKind,
    val checked: String,
)

public data class MonitoredUnreachable(val id: String, val name: String)

public data class MonitorInboxFacts(
    val loaded: Boolean = false,
    val rows: ImmutableList<MonitoredInboxRow> = persistentListOf(),
    val unreachable: ImmutableList<MonitoredUnreachable> = persistentListOf(),
    val failure: Failure? = null,
)

public sealed interface MonitoredOpen {
    public data class Confirm(
        val key: String,
        val server: String,
        val currentServer: String? = null,
    ) : MonitoredOpen

    public data class GlobalForm(
        val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId,
        val formId: String,
        val directory: String?,
        val workspace: String?,
    ) : MonitoredOpen

    public data class Chat(
        val ref: ConversationRef,
        val requestId: String?,
        val failedRun: Boolean,
    ) : MonitoredOpen
}

public interface MonitorInboxSource {
    public val facts: StateFlow<MonitorInboxFacts>

    public suspend fun refresh(): Outcome<Unit>

    public suspend fun open(key: String, allowSwitch: Boolean): Outcome<MonitoredOpen>

    public suspend fun token(profileId: String, sessionId: String, token: String): Outcome<String>
}

public data class MonitorInboxUi(
    val facts: MonitorInboxFacts = MonitorInboxFacts(),
    val working: Boolean = false,
    val failure: Failure? = null,
    val confirm: MonitoredOpen.Confirm? = null,
)

public interface MonitorInboxActions {
    public fun back()

    public fun refresh()

    public fun open(key: String)

    public fun confirmSwitch()

    public fun cancelSwitch()

    public fun notificationSettings()
}

@AssistedInject
public class MonitorInboxViewModel(
    @Assisted route: MonitorInboxRoute,
    private val source: MonitorInboxSource,
    private val nav: AppNavigator,
) : ViewModel(), MonitorInboxActions {
    private val local = MutableStateFlow(MonitorInboxUi())
    public val state: StateFlow<MonitorInboxUi> = local.asStateFlow()

    init {
        viewModelScope.launch {
            source.facts.collect { facts -> local.update { it.copy(facts = facts) } }
        }
        val profile = route.profileId
        val session = route.sessionId
        val token = route.token
        if (profile != null && session != null && token != null)
            viewModelScope.launch {
                when (val key = source.token(profile, session, token)) {
                    is Outcome.Failed -> local.update { it.copy(failure = key.failure) }
                    is Outcome.Ok -> open(key.value)
                }
            }
        else refresh()
    }

    override fun back(): Unit = nav.back()

    override fun refresh() {
        if (local.value.working) return
        local.update { it.copy(working = true, failure = null) }
        viewModelScope.launch {
            val result = source.refresh()
            local.update {
                it.copy(working = false, failure = (result as? Outcome.Failed)?.failure)
            }
        }
    }

    override fun open(key: String): Unit = openTarget(key, false)

    private fun openTarget(key: String, confirmed: Boolean) {
        if (local.value.working) return
        local.update { it.copy(working = true, confirm = null, failure = null) }
        viewModelScope.launch {
            val result = source.open(key, confirmed)
            local.update {
                it.copy(working = false, failure = (result as? Outcome.Failed)?.failure)
            }
            if (result is Outcome.Ok)
                when (val target = result.value) {
                    is MonitoredOpen.Confirm -> local.update { it.copy(confirm = target) }
                    is MonitoredOpen.Chat ->
                        nav.open(
                            ChatRoute(
                                target.ref,
                                requestId = target.requestId,
                                landOnFailure = target.failedRun,
                            )
                        )
                    is MonitoredOpen.GlobalForm ->
                        nav.open(
                            ConnectionFormRoute(
                                target.profile,
                                target.formId,
                                directory = target.directory,
                                workspace = target.workspace,
                            )
                        )
                }
        }
    }

    override fun confirmSwitch() {
        local.value.confirm?.let { openTarget(it.key, true) }
    }

    override fun cancelSwitch(): Unit = local.update { it.copy(confirm = null) }

    override fun notificationSettings(): Unit = nav.open(SettingsRoute.Home)

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: MonitorInboxRoute): MonitorInboxViewModel
    }
}
