package io.github.eslamasabry.oc.feature.chat.header.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipActions
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChipUi
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalChoice
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalPresenter
import io.github.eslamasabry.oc.feature.chat.header.approval.ApprovalStore
import io.github.eslamasabry.oc.feature.chat.header.approval.NoApprovals
import io.github.eslamasabry.oc.feature.chat.header.freemodel.FreeNoteStore
import io.github.eslamasabry.oc.feature.chat.header.world.ChatHeaderPorts
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * One per chat entry, beside the ChatViewModel: shares the conversation's world (ref-counted by
 * [ChatWorlds]) and owns the header's port. All behaviour is [ChatHeaderPresenter]'s.
 */
@AssistedInject
public class ChatHeaderViewModel(
    @Assisted route: ChatRoute,
    private val worlds: ChatWorlds,
    ports: ChatHeaderPorts,
    nav: AppNavigator,
    approvalStores: Set<ApprovalStore>,
    freeNoteStores: Set<FreeNoteStore>,
    clock: Clock,
    handoffs: Set<io.github.eslamasabry.oc.feature.chat.header.world.ChatAgentHandoffs> =
        emptySet(),
) : ViewModel(), ChatHeaderActions, ChatAgentHandoffActions, ApprovalChipActions {
    private val world = worlds.open(route.ref)
    private val port = ports.open(route.ref)
    private val launcher = ChatLauncher { block -> viewModelScope.launch { block() } }
    private val presenter =
        ChatHeaderPresenter(
            world,
            port,
            nav,
            launcher,
            freeNoteStores.singleOrNull(),
            handoffs.singleOrNull()?.open(route.ref),
        )
    private val approvals =
        ApprovalPresenter(
            route.ref,
            approvalStores.singleOrNull(),
            world.capabilities,
            port.approvals ?: NoApprovals,
            launcher,
            clock,
        )

    public val state: StateFlow<ChatHeaderUi> =
        presenter.states(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS))

    /**
     * The sweep must run while the chat is open even before the chip is drawn, so this one starts
     * eagerly.
     */
    public val approval: StateFlow<ApprovalChipUi> =
        approvals.states(viewModelScope, SharingStarted.Eagerly)

    override fun openApprovalMenu(): Unit = approvals.openApprovalMenu()

    override fun closeApprovalMenu(): Unit = approvals.closeApprovalMenu()

    override fun chooseApproval(choice: ApprovalChoice): Unit = approvals.chooseApproval(choice)

    override fun confirmEverything(): Unit = approvals.confirmEverything()

    override fun cancelEverything(): Unit = approvals.cancelEverything()

    override fun dismissApprovalFailure(): Unit = approvals.dismissApprovalFailure()

    override fun onCleared() {
        presenter.close()
        worlds.release(world)
    }

    override fun openMenu(): Unit = presenter.openMenu()

    override fun closeMenu(): Unit = presenter.closeMenu()

    override fun choose(action: HeaderAction): Unit = presenter.choose(action)

    override fun copyTranscript(words: TranscriptWords, mask: (String) -> String): Unit =
        presenter.copyTranscript(words, mask)

    override fun renameRequested(): Unit = presenter.renameRequested()

    override fun renameDone(title: String?): Unit = presenter.renameDone(title)

    override fun closeSheet(): Unit = presenter.closeSheet()

    override fun continueWith(choice: ContinueChoice): Unit = presenter.continueWith(choice)

    override fun retryContinueWith(): Unit = presenter.retryContinueWith()

    override fun prepareHandoff(
        choice: io.github.eslamasabry.oc.feature.chat.header.world.AgentHandoffChoice
    ): Unit = presenter.prepareHandoff(choice)

    override fun sendHandoff(prompt: String): Unit = presenter.sendHandoff(prompt)

    override fun retryHandoffLink(): Unit = presenter.retryHandoffLink()

    override fun copyResumeCommand(): Unit = presenter.copyResumeCommand()

    override fun confirm(): Unit = presenter.confirm()

    override fun dismissConfirm(): Unit = presenter.dismissConfirm()

    override fun dismissFailure(): Unit = presenter.dismissFailure()

    override fun clipDone(id: Long): Unit = presenter.clipDone(id)

    override fun dismissFreeNote(): Unit = presenter.dismissFreeNote()

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute): ChatHeaderViewModel
    }

    private companion object {
        const val STOP_AFTER_MS = 5_000L
    }
}
