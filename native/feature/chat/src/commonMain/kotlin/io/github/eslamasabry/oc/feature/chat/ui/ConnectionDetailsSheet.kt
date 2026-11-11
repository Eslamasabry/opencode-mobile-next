package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chat.presentation.ChatActions
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusKind
import io.github.eslamasabry.oc.feature.chat.presentation.ChatStatusUi
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.connectionRestartAgent
import io.github.eslamasabry.oc.ui.words.res.connectionRestartPhoneServer
import io.github.eslamasabry.oc.ui.words.res.e7BannerCheckingExplanation
import io.github.eslamasabry.oc.ui.words.res.e7BannerLost
import io.github.eslamasabry.oc.ui.words.res.e7BannerStaleExplanation
import io.github.eslamasabry.oc.ui.words.res.isolatedTaskRetryOpen
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.mcpReconnecting
import io.github.eslamasabry.oc.ui.words.res.productStatesSwitchServer
import org.jetbrains.compose.resources.stringResource

/**
 * Flutter `showConnectionDetailsSheet`: what the connection line means in plain words, with the ways out
 * (Try again, Restart this phone's server, Switch server). Never the raw error text.
 */
@Composable
internal fun ConnectionDetailsSheet(
    visible: Boolean,
    line: ChatStatusUi?,
    actions: ChatActions,
    onDismiss: () -> Unit,
) {
    val reconnecting = line?.kind == ChatStatusKind.Connecting
    KitSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(if (reconnecting) Res.string.mcpReconnecting else Res.string.e7BannerLost),
        closeLabel = stringResource(Res.string.kitSheetDismiss),
        primary =
            if (reconnecting) null
            else
                KitAction(stringResource(Res.string.isolatedTaskRetryOpen), {
                    onDismiss()
                    if (line?.retryInSeconds != null) actions.tryLinkNow() else actions.retryLoad()
                }),
        tertiary =
            listOfNotNull(
                if (line?.canRestartAgent == true && !reconnecting)
                    KitAction(stringResource(Res.string.connectionRestartAgent), {
                        onDismiss()
                        actions.restartAgent()
                    })
                else if (line?.canRestart == true && !reconnecting)
                    KitAction(stringResource(Res.string.connectionRestartPhoneServer), {
                        onDismiss()
                        actions.restartPhoneServer()
                    })
                else null,
                if (!reconnecting)
                    KitAction(stringResource(Res.string.productStatesSwitchServer), {
                        onDismiss()
                        actions.openServers()
                    })
                else null,
            ),
    ) {
        KitText(
            stringResource(
                if (reconnecting) Res.string.e7BannerCheckingExplanation else Res.string.e7BannerStaleExplanation
            ),
            tone = TextTone.Secondary,
        )
    }
}
