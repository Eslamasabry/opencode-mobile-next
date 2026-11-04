package io.github.eslamasabry.oc.feature.chat.forms

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.requests.forms.*
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ConnectionFormRoute
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public enum class ConnectionFormPhase {
    Loading,
    Waiting,
    Held,
    Sending,
    Refused,
    Settled,
    AnsweredElsewhere,
    Changed,
    Unavailable,
}

public data class ConnectionFormUi(
    val form: FormSpec? = null,
    val draft: FormDraft = persistentMapOf(),
    val evaluation: FormEvaluation? = null,
    val phase: ConnectionFormPhase = ConnectionFormPhase.Loading,
    val failure: Failure? = null,
    val slow: Boolean = false,
    val declineQuestion: Boolean = false,
    val linkHost: String? = null,
) {
    override fun toString(): String = "ConnectionFormUi(phase=$phase, fields=${form?.fields?.size})"
}

public interface ConnectionFormActions {
    public fun edit(key: String, value: FormValue)

    public fun send()

    public fun askDecline()

    public fun decline()

    public fun dismiss()

    public fun undo()

    public fun retry()

    public fun openLink(key: String)

    public fun approveLink()

    public fun close()
}

public interface ConnectionFormSession : ConnectionFormActions {
    public val state: StateFlow<ConnectionFormUi>
}

public interface ConnectionForms {
    public fun open(route: ConnectionFormRoute): ConnectionFormSession
}

/** Capability-off desktop binding; Android supplies the actual connection-owned form engine. */
@Inject
@ContributesBinding(AppScope::class)
public class UnavailableConnectionForms : ConnectionForms {
    override fun open(route: ConnectionFormRoute): ConnectionFormSession =
        object : ConnectionFormSession {
            override val state: StateFlow<ConnectionFormUi> =
                MutableStateFlow(ConnectionFormUi(phase = ConnectionFormPhase.Unavailable))

            override fun edit(key: String, value: FormValue) = Unit

            override fun send() = Unit

            override fun askDecline() = Unit

            override fun decline() = Unit

            override fun dismiss() = Unit

            override fun undo() = Unit

            override fun retry() = Unit

            override fun openLink(key: String) = Unit

            override fun approveLink() = Unit

            override fun close() = Unit
        }
}

@AssistedInject
public class ConnectionFormViewModel(
    @Assisted private val route: ConnectionFormRoute,
    forms: ConnectionForms,
    private val nav: AppNavigator,
) : ViewModel(), ConnectionFormActions {
    private val session = forms.open(route)
    public val state: StateFlow<ConnectionFormUi> = session.state

    override fun edit(key: String, value: FormValue): Unit = session.edit(key, value)

    override fun send(): Unit = session.send()

    override fun askDecline(): Unit = session.askDecline()

    override fun decline(): Unit = session.decline()

    override fun dismiss(): Unit = session.dismiss()

    override fun undo(): Unit = session.undo()

    override fun retry(): Unit = session.retry()

    override fun openLink(key: String): Unit = session.openLink(key)

    override fun approveLink(): Unit = session.approveLink()

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
        public fun create(@Assisted route: ConnectionFormRoute): ConnectionFormViewModel
    }
}
