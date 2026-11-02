package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.presentation.ChatViewModel
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.routes.PreviewRoute
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.devPreviewMenu
import org.jetbrains.compose.resources.stringResource

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
    private val nav: AppNavigator,
) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatRoute> { route ->
            val vm = assistedMetroViewModel<ChatViewModel, ChatViewModel.Factory> { create(route) }
            val state by vm.state.collectAsStateWithLifecycle()
            val requests by vm.requests.collectAsStateWithLifecycle()
            val slot = requests?.state?.collectAsStateWithLifecycle()?.value ?: io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi()
            // PREVIEW-QR: the dev servers this conversation mentioned (rows are newest first; the scan wants oldest first).
            val preview =
                KitLineAction(stringResource(Res.string.devPreviewMenu), {
                    val texts =
                        state.rows.asReversed().mapNotNull { row ->
                            when (row) {
                                is PromptRow -> row.text
                                is BlockRow -> row.text
                                else -> null
                            }
                        }
                    nav.open(PreviewRoute.Ports.from(route.ref, texts))
                }, testTag = "chat-dev-preview")
            environment.Provide {
                ChatScreen(state, vm, composerHost, requests = slot, requestActions = requests, extraActions = listOf(preview))
            }
        }
    }
}
