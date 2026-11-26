package io.github.eslamasabry.oc.feature.chat.demo.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitTranscriptActions
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatsHomeConnectComputer
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartSetUp
import org.jetbrains.compose.resources.stringResource

/**
 * B-623: follow-up actions under the offline demo's honest off-script reply: one-tap sample run,
 * direct phone setup exit, and computer connection beside it.
 */
@Immutable
public class DemoOwnReply(
    public val rowKey: String?,
    public val samplePrompt: String,
    public val onRunSample: () -> Unit,
    public val onSetUpPhone: () -> Unit,
    public val onConnectComputer: () -> Unit,
)

public val LocalDemoOwnReply: ProvidableCompositionLocal<DemoOwnReply?> =
    compositionLocalOf { null }

@Composable
internal fun DemoOwnReplyActions(reply: DemoOwnReply) {
    val setUpPhone = stringResource(Res.string.phoneSetupStartSetUp)
    val connectComputer = stringResource(Res.string.chatsHomeConnectComputer)
    KitTranscriptActions(Modifier.padding(top = Space.S2.dp).testTag("demo-own-reply-actions")) {
        if (reply.samplePrompt.isNotBlank()) {
            KitButton(
                label = reply.samplePrompt,
                onClick = reply.onRunSample,
                role = KitButtonRole.Secondary,
                icon = KitIcons.send,
                expand = false,
                modifier = Modifier.testTag("demo-run-sample"),
            )
        }
        KitActionBlock(
            modifier = Modifier.padding(top = Space.S1.dp).testTag("demo-exit-actions"),
            primary = KitAction(setUpPhone, reply.onSetUpPhone, icon = KitIcons.phone),
            secondary = KitAction(connectComputer, reply.onConnectComputer, icon = KitIcons.computer),
        )
    }
}
