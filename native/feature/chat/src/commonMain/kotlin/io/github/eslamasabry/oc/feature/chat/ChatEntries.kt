package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.chat.cards.ChatCards
import io.github.eslamasabry.oc.feature.chat.cards.LocalChatCards
import io.github.eslamasabry.oc.feature.chat.cards.ModelCardSlotHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.presentation.ChatViewModel
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.CardsSettingsRoute
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/**
 * The chat's entry (07 C01): one ChatViewModel per ChatRoute entry, the composer slot injected (plan 09 §6.3). The ONLY
 * file in the module that imports androidx.navigation3.runtime (Konsist K1).
 *
 * Merge note: `:feature:chats` still installs its P2 placeholder for ChatRoute; the coordinator removes that entry in
 * the merge that adds `:feature:chat` to the app (one `entry<ChatRoute>` per graph).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ChatEntries(
    private val composerHost: ComposerHost,
    private val environment: KitChatEnvironment,
    private val cards: ChatCards,
    private val nav: AppNavigator,
) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatRoute> { route ->
            val vm = assistedMetroViewModel<ChatViewModel, ChatViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            val requests by vm.requests.collectAsStateWithLifecycle()
            val slot = requests?.state?.collectAsStateWithLifecycle()?.value ?: io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi()
            // P12-CHAT-CARDS: the conversation's agent cards, read by the transcript's card slot.
            val cardModel = remember(route.ref) { cards.open(route.ref) }
            DisposableEffect(cardModel) { onDispose { cardModel.close() } }
            val cardSlot = remember(cardModel) { ModelCardSlotHost(cardModel, cards.linkOpener, { nav.open(CardsSettingsRoute) }) }
            CompositionLocalProvider(LocalChatCards provides cardSlot) {
                environment.Provide { ChatScreen(state, vm, composerHost, requests = slot, requestActions = requests) }
            }
        }
    }
}
