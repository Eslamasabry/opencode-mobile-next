package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.layout.Layout
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnIllustration
import io.github.eslamasabry.oc.ui.kit.scene.StatesUnpluggedScene
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonTranscript
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitReportProblem
import org.jetbrains.compose.resources.stringResource

/**
 * The chat's own states (Flutter lib/ui/screens/chat/chat_states.dart) and the conversation's width.
 */
public object ChatPane {
    /**
     * The conversation's cap with its gutters inside (Flutter chat_transcript.dart:146:
     * `KitLayout.paneDetailMaxWidth + 2 * tokens.gutter`): 700 dp of words, centred on a wide window. The composer
     * takes the same width (E20-S27).
     */
    public fun maxWidth(gutter: Int): Dp = (Layout.CONVERSATION_WIDTH + 2 * gutter).dp

    /** The illustration's width on a page state (Flutter kit_state_view.dart:733, `page ? 140 : 88`). */
    public const val ILLUSTRATION_PAGE_DP: Int = 140

    /**
     * What the details fold shows for a load failure (Flutter `productErrorDetails(error) ?? error.toString()`): the
     * failure's own redacted details, else its plain kind, so the fold is never empty.
     */
    public fun detailsNote(failure: Failure): String? =
        if (failure.details != null) null else failure::class.simpleName
}

/** Holds [content] to the conversation's width, centred (Flutter: `Center(ConstrainedBox(maxWidth))`). */
@Composable
internal fun Modifier.chatPane(): Modifier = kitMaxWidth(ChatPane.maxWidth(LocalKitTokens.current.gutter), CapAlign.Center)

/** First load (Flutter `_ChatLoadingBody`): two placeholder turns at the bottom, where the newest will be. */
@Composable
internal fun ChatLoadingBody(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().chatPane(), contentAlignment = Alignment.BottomCenter) {
        KitSkeletonTranscript(Modifier.fillMaxWidth().testTag("chat-loading"))
    }
}

/**
 * The conversation could not load (Flutter `_ChatLoadError`, chat_states.dart:69-95): a page state with the unplugged
 * cable, the title and body, Try again, "Report a problem" one tap away, and the redacted details folded last. The
 * page is centred in the room and the composer is hidden while it shows.
 */
@Composable
internal fun ChatLoadError(
    title: String,
    body: String,
    failure: Failure,
    retry: KitAction,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalKitTokens.current
    val report = onReport?.let { KitAction(stringResource(Res.string.kitReportProblem), it) }
    BoxWithConstraints(modifier.fillMaxSize().testTag("chat-load-error")) {
        val room = if (constraints.hasBoundedHeight) maxHeight else 0.dp
        Box(
            Modifier.fillMaxWidth()
                .then(if (constraints.hasBoundedHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .heightIn(min = room)
                .padding(horizontal = tokens.gutter.dp, vertical = FlutterKitTokens.STATE_VERTICAL_PADDING.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.kitMaxWidth(CapRole.State).fillMaxWidth()) {
                Box(Modifier.testTag("kit-state-illustration")) {
                    KitDrawnIllustration(StatesUnpluggedScene, width = ChatPane.ILLUSTRATION_PAGE_DP.dp)
                }
                Spacer(Modifier.height(Space.S5.dp))
                KitText(title, Modifier.semantics { heading() }, role = TypeRole.Title)
                Spacer(Modifier.height(Space.S2.dp))
                KitText(body, tone = TextTone.Secondary)
                Spacer(Modifier.height(Space.S6.dp))
                KitActionBlock(primary = retry, tertiary = listOfNotNull(report))
                Spacer(Modifier.height(Space.S3.dp))
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes = listOfNotNull(ChatPane.detailsNote(failure)),
                    text = failure.details,
                )
            }
        }
    }
}
