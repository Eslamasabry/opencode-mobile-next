package io.github.eslamasabry.oc.feature.chat.demo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import io.github.eslamasabry.oc.engine.requests.PendingRequest
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestCardUi
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

public data class DemoScreenUi(
    val chat: ChatUi,
    val stage: DemoStage = DemoStage.Ready,
    val ownReplyKey: String? = null,
    val requests: RequestSlotUi = RequestSlotUi(),
)

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class DemoViewModel(private val nav: AppNavigator, dispatchers: AppDispatchers) :
    ViewModel(), BasicComposerActions {
    private val world = DemoWorld(dispatchers)
    private val presenter =
        ChatPresenter(world, nav, { block -> viewModelScope.launch { block() } })
    public val actions: ChatActions = presenter
    public val state =
        presenter.states(viewModelScope, world.work, SharingStarted.WhileSubscribed(5_000))
    public val stage = world.stage
    public val ownReplyKey = world.ownReplyKey
    public val composer =
        combine(world.draft, world.stage, world.model, world.title) { draft, phase, model, title ->
                // One sample model, so the model chip names something and its sheet is not an empty list; its group
                // is named as the demo is, never by an id.
                val sample =
                    model.takeIf { it.isNotEmpty() }?.let {
                        ComposerModel("demo", "demo", it, supportsAttachments = false, providerName = title.orEmpty())
                    }
                BasicComposerUi(
                    // The message box stays where it is in a real chat; Send waits while the request does.
                    available = phase != DemoStage.Review,
                    sending = false,
                    steers = false,
                    draft = draft,
                    targetKey = "offline-demo",
                    models = ComposerModels(choices = listOfNotNull(sample), selected = sample),
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                BasicComposerUi(available = false, sending = false, steers = false),
            )

    /** The sample agent's request as the app's own request card: one waiting permission ask, or nothing. */
    public val requests =
        world.request
            .map(::requestSlotOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RequestSlotUi())

    /** One atomic screen snapshot so button dismiss, reply insert, and request card land in the same frame. */
    public val ui =
        combine(state, world.step) { chat, snap ->
                if (chat.newestRowKey != snap.newestRowKey) null
                else DemoScreenUi(chat, snap.stage, snap.ownReplyKey, requestSlotOf(snap.request))
            }
            .filterNotNull()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                DemoScreenUi(state.value, stage.value, ownReplyKey.value, requests.value),
            )

    /** Allow and Deny answer the sample; the demo has no questions, no saved rules and nothing to undo. */
    public val requestActions: ChatRequestActions =
        object : ChatRequestActions {
            override fun shown(id: RequestId) = Unit

            override fun allowOnce(id: RequestId) = allow()

            override fun reject(id: RequestId) = deny()

            override fun askAlwaysAllow(id: RequestId) = Unit

            override fun confirmAlwaysAllow() = Unit

            override fun cancelAlwaysAllow() = Unit

            override fun keepAsking(id: RequestId) = Unit

            override fun choose(id: RequestId, option: String) = Unit

            override fun openOther(id: RequestId) = Unit

            override fun editOther(id: RequestId, text: String) = Unit

            override fun sendOther(id: RequestId) = Unit

            override fun openSheet(id: RequestId) = Unit

            override fun closeSheet(id: RequestId) = Unit

            override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit

            override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit

            override fun sendSheet(id: RequestId) = Unit

            override fun undo(id: RequestId) = Unit

            override fun retry(id: RequestId) = Unit
        }

    public fun configure(words: DemoWords): Unit = world.configure(words)

    override fun draftChanged(text: String): Unit = world.draftChanged(text)

    override suspend fun send(text: String): Boolean = world.send(text) is Outcome.Ok

    public fun allow(): Unit {
        viewModelScope.launch { world.decide(true) }
    }

    public fun deny(): Unit {
        viewModelScope.launch { world.decide(false) }
    }

    public fun reset(): Unit {
        viewModelScope.launch { world.reset() }
    }

    public fun runSample(): Unit {
        viewModelScope.launch { world.runSample() }
    }

    public fun setUpPhone(): Unit {
        nav.back()
        nav.open(PhoneSetupRoute.Setup)
    }

    public fun connectComputer(): Unit {
        nav.back()
        nav.open(ServersRoute.Edit())
    }

    override fun onCleared() {
        world.close()
    }
}

private fun requestSlotOf(asked: PermissionRequest?): RequestSlotUi =
    RequestSlotUi(
        asked?.let {
            RequestCardUi.Permission(
                id = it.id,
                waitingSinceMs = null,
                state = CardState.Waiting,
                ask = PermissionAsk.of(it),
                canAlwaysAllow = false,
                captured = PendingRequest.Permission(it),
            )
        }
    )
