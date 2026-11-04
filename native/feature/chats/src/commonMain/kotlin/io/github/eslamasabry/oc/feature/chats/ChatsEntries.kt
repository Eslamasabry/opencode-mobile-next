package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.getValue
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
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller

/**
 * The EntryInstaller; the ONLY file in the module that imports androidx.navigation3.runtime
 * (Konsist K1).
 */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class ChatsEntries(private val appNotices: AppNotices) : EntryInstaller {
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
            val appStatus = appNotice?.let { notice ->
                KitStatus(
                    if (notice.stoppedApp) KitStatusKind.AppStopped else KitStatusKind.Info,
                    notice.message,
                    id = notice.id,
                    action =
                        notice.action?.let { action ->
                            notice.actionLabel?.let { label ->
                                KitLineAction(label, { appNotices.act(action) })
                            }
                        },
                    onDismiss = { appNotices.dismiss(notice.id) },
                    dismissLabel = notice.dismissLabel,
                )
            }
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
            ChatsScreen(state, vm, appStatus = appStatus, replyOffer = offerContent)
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
