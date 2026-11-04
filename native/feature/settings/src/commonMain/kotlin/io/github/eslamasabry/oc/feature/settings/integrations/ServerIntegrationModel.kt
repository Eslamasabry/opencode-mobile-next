package io.github.eslamasabry.oc.feature.settings.integrations

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public enum class ServerIntegrationKind {
    Provider,
    CallableTool,
    RegisteredTool,
    Command,
    Mcp,
    Resource,
    Plugin,
    Source,
    Suggestion,
    HostCommand,
    Credential,
}

public enum class ServerIntegrationState {
    Connected,
    Available,
    Disconnected,
    Failed,
    Pending,
    Disabled,
    Unknown,
    Builtin,
    Added,
    Registered,
}

public data class ServerIntegrationItem(
    val key: String,
    val title: String,
    val description: String? = null,
    val kind: ServerIntegrationKind,
    val status: ServerIntegrationState = ServerIntegrationState.Unknown,
    val details: Redacted? = null,
    val builtIn: Boolean = false,
    val mayConnect: Boolean = false,
    val mayDisconnect: Boolean = false,
    val mayRename: Boolean = false,
    val mayActivate: Boolean = false,
)

public data class ServerIntegrationModelChoice(val key: String, val label: String)

public data class ServerIntegrationUi(
    val page: ServerIntegrationPage,
    val server: String? = null,
    val loading: Boolean = true,
    val available: Boolean = false,
    val items: ImmutableList<ServerIntegrationItem> = persistentListOf(),
    val models: ImmutableList<ServerIntegrationModelChoice> = persistentListOf(),
    val model: String? = null,
    val selected: ServerIntegrationItem? = null,
    val question: ServerIntegrationItem? = null,
    val failure: Failure? = null,
    val mayAdd: Boolean = false,
    val add: Boolean = false,
    val copied: Boolean = false,
    val inputRevision: Int = 0,
)

public interface ServerIntegrationActions {
    public fun refresh()

    public fun select(key: String)

    public fun chooseModel(key: String)

    public fun connect(key: String)

    public fun askDisconnect(key: String)

    public fun disconnect()

    public fun rename(key: String, label: String)

    public fun activate(key: String)

    public fun beginAdd()

    public fun add(name: String, url: String, executable: String, arguments: String)

    public fun dismiss()

    public fun copy()

    public fun go(page: ServerIntegrationPage)

    public fun docs()

    public fun close()
}

public interface ServerIntegrationSession : ServerIntegrationActions {
    public val state: StateFlow<ServerIntegrationUi>
}

public interface ServerIntegrations {
    public fun open(route: ServerIntegrationRoute): ServerIntegrationSession
}

@Inject
@ContributesBinding(AppScope::class)
public class UnavailableServerIntegrations : ServerIntegrations {
    override fun open(route: ServerIntegrationRoute): ServerIntegrationSession =
        object : ServerIntegrationSession {
            override val state: StateFlow<ServerIntegrationUi> =
                MutableStateFlow(ServerIntegrationUi(route.page, loading = false))

            override fun refresh() = Unit

            override fun select(key: String) = Unit

            override fun chooseModel(key: String) = Unit

            override fun connect(key: String) = Unit

            override fun askDisconnect(key: String) = Unit

            override fun disconnect() = Unit

            override fun rename(key: String, label: String) = Unit

            override fun activate(key: String) = Unit

            override fun beginAdd() = Unit

            override fun add(name: String, url: String, executable: String, arguments: String) =
                Unit

            override fun dismiss() = Unit

            override fun copy() = Unit

            override fun go(page: ServerIntegrationPage) = Unit

            override fun docs() = Unit

            override fun close() = Unit
        }
}

@AssistedInject
public class ServerIntegrationViewModel(
    @Assisted route: ServerIntegrationRoute,
    source: ServerIntegrations,
    private val nav: AppNavigator,
) : ViewModel(), ServerIntegrationActions {
    private val session = source.open(route)
    public val state: StateFlow<ServerIntegrationUi> = session.state

    override fun refresh(): Unit = session.refresh()

    override fun select(key: String): Unit = session.select(key)

    override fun chooseModel(key: String): Unit = session.chooseModel(key)

    override fun connect(key: String): Unit = session.connect(key)

    override fun askDisconnect(key: String): Unit = session.askDisconnect(key)

    override fun disconnect(): Unit = session.disconnect()

    override fun rename(key: String, label: String): Unit = session.rename(key, label)

    override fun activate(key: String): Unit = session.activate(key)

    override fun beginAdd(): Unit = session.beginAdd()

    override fun add(name: String, url: String, executable: String, arguments: String): Unit =
        session.add(name, url, executable, arguments)

    override fun dismiss(): Unit = session.dismiss()

    override fun copy(): Unit = session.copy()

    override fun go(page: ServerIntegrationPage): Unit = session.go(page)

    override fun docs(): Unit = session.docs()

    override fun close() {
        session.close()
        nav.back()
    }

    override fun onCleared() {
        session.close()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServerIntegrationRoute): ServerIntegrationViewModel
    }
}
