package io.github.eslamasabry.oc.feature.chats

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
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.core.domain.platform.AppNotices
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsAllViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsViewModel
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.ui.ChatsAllScreen
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.feature.chats.ui.homeAppNoticeStatus
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/**
 * The EntryInstaller; the ONLY file in the module that imports androidx.navigation3.runtime
 * (Konsist K1).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ChatsEntries(
    private val appNotices: AppNotices,
    private val navigator: AppNavigator,
    private val sections: Set<ChatsHomeSection> = emptySet(),
    /** The edge dock on the home, where the app binds one. */
    private val edgeDocks: Set<io.github.eslamasabry.oc.feature.chats.dock.HomeEdgeDock> = emptySet(),
) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatsRoute.New> { route ->
            val vm =
                assistedMetroViewModel<NewConversationViewModel, NewConversationViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            NewConversationScreen(state, vm)
        }
        entry<ChatsRoute.Home> {
            val vm = metroViewModel<ChatsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val appNotice by appNotices.status.collectAsStateWithLifecycle()
            val replyOffer by appNotices.replyOffer.collectAsStateWithLifecycle()
            val appStatus =
                appNotice?.let { homeAppNoticeStatus(it, appNotices::act, appNotices::dismiss) }
            val offerContent: (@androidx.compose.runtime.Composable () -> Unit)? =
                replyOffer?.let { offer ->
                    {
                        KitNotice(
                            message = offer.message,
                            actions =
                                if (offer.busy) emptyList()
                                else if (offer.failed) emptyList()
                                else
                                    listOf(
                                        KitLineAction(
                                            offer.acceptLabel,
                                            { appNotices.answerReplyOffer(true) },
                                        )
                                    ),
                            dismiss =
                                if (offer.busy) null
                                else
                                    KitLineAction(
                                        offer.declineLabel,
                                        { appNotices.answerReplyOffer(false) },
                                    ),
                        )
                    }
                }
            // Home is a Root route, so on top it is the whole back stack: Back there would close the app (E05-S21).
            // Read only where it is used (the back guard), so a page pushed over the home does not recompose it.
            val top = navigator.top.collectAsStateWithLifecycle()
            val orderedSections =
                remember(sections) { sections.sortedBy { it.order }.map { section -> @androidx.compose.runtime.Composable { section.Content() } } }
            ChatsScreen(
                state,
                vm,
                appStatus = appStatus,
                replyOffer = offerContent,
                root = { top.value == ChatsRoute.Home },
                sections = orderedSections,
                edgeDock = edgeDocks.firstOrNull(),
            )
        }
        entry<ChatsRoute.Attention> {
            val vm =
                metroViewModel<
                    io.github.eslamasabry.oc.feature.chats.attention.AttentionViewModel
                >()
            val state by vm.state.collectAsStateWithLifecycle()
            io.github.eslamasabry.oc.feature.chats.attention.AttentionScreen(state, vm)
        }
        entry<ChatsRoute.All> { route ->
            val vm =
                assistedMetroViewModel<ChatsAllViewModel, ChatsAllViewModel.Factory> {
                    create(route)
                }
            val state by vm.state.collectAsStateWithLifecycle()
            ChatsAllScreen(state, vm)
        }
    }
}

/**
 * A section another feature places on the home under its notes (features may not depend on each other, so the app
 * binds them). s23-factsbook: FactsBook's Memory section.
 */
public interface ChatsHomeSection {
    /** Lower first. */
    public val order: Int
        get() = 0

    @androidx.compose.runtime.Composable public fun Content()
}

@dev.zacsweers.metro.ContributesTo(AppScope::class)
public interface ChatsHomeSectionBindings {
    @dev.zacsweers.metro.Multibinds(allowEmpty = true) public val chatsHomeSections: Set<ChatsHomeSection>
}
