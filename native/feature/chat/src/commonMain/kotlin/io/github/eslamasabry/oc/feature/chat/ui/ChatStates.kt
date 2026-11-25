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
import io.github.eslamasabry.oc.core.common.label
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
import io.github.eslamasabry.oc.ui.kit.frame.kitScreenChromePadding
import io.github.eslamasabry.oc.ui.kit.scene.KitDrawnIllustration
import io.github.eslamasabry.oc.ui.kit.scene.StatesUnpluggedScene
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonTranscript
import io.github.eslamasabry.oc.ui.kit.state.KitSlowWords
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedAgentBody
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedAgentsBody
import io.github.eslamasabry.oc.ui.words.res.chatLoadFailedBody
import io.github.eslamasabry.oc.ui.words.res.chatLoadMissingAgentBody
import io.github.eslamasabry.oc.ui.words.res.chatLoadMissingAgentsBody
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

    /** How long a first load runs before the page says in words what it is doing. */
    public const val LOADING_WORDS_AFTER_MS: Long = 1_000

    /**
     * What the details fold names for a load failure, above its redacted details when it has any: the failure's
     * stable label (`Refused · StaleScope`), never a class name, which a release build renames (it printed `un5`).
     */
    public fun detailsNote(failure: Failure): String = failure.label()
}

/** Holds [content] to the conversation's width, centred (Flutter: `Center(ConstrainedBox(maxWidth))`). */
@Composable
internal fun Modifier.chatPane(): Modifier = kitMaxWidth(ChatPane.maxWidth(LocalKitTokens.current.gutter), CapAlign.Center)

/**
 * First load: two placeholder turns at the bottom, where the newest will be, and, once the load has taken
 * [ChatPane.LOADING_WORDS_AFTER_MS], [label] in quiet words in the middle of the room (owner 2026-11-14: a ten-second
 * load said nothing). The middle, not under the turns: on an empty page the eye goes there first, and the turns stay
 * where the conversation's newest lines will land. Null [label]: the screen's line already says what is happening
 * (starting, updating, connecting), and the page does not say a second thing.
 */
@Composable
internal fun ChatLoadingBody(label: String?, modifier: Modifier = Modifier) {
    // B-561: top-aligned column starting right under the header (skeleton first, then the slow words).
    Column(
        modifier.fillMaxSize().padding(kitScreenChromePadding()).padding(bottom = Space.S2.dp).chatPane(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KitSkeletonTranscript(Modifier.fillMaxWidth().testTag("chat-loading"))
        Spacer(Modifier.height(Space.S4.dp))
        KitSlowWords(
            label,
            ChatPane.LOADING_WORDS_AFTER_MS,
            Modifier.padding(horizontal = LocalKitTokens.current.gutter.dp),
            textModifier = Modifier.testTag("chat-loading-words"),
        )
    }
}

/**
 * OMP-OPEN: the load-failure page's words name what did not answer. A conversation of this phone's agents names its
 * agent (its `name`), or "the phone's agents" when they no longer say which; a conversation the agents do
 * not have ([Failure.NotFound]) says so and how to go on instead of "try again when it answers". A server's own
 * conversation keeps its words.
 */
@Composable
internal fun chatLoadBody(phase: ChatPhase.Failed): String {
    val agent = phase.agent ?: return stringResource(Res.string.chatLoadFailedBody)
    val name = agent.name
    return if (phase.failure is Failure.NotFound) {
        if (name != null) stringResource(Res.string.chatLoadMissingAgentBody, name)
        else stringResource(Res.string.chatLoadMissingAgentsBody)
    } else {
        if (name != null) stringResource(Res.string.chatLoadFailedAgentBody, name)
        else stringResource(Res.string.chatLoadFailedAgentsBody)
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
    setUp: KitAction? = null,
) {
    val tokens = LocalKitTokens.current
    val report = onReport?.let { KitAction(stringResource(Res.string.kitReportProblem), it) }
    // Inside the room the frame's overlaid bar and bottom block leave (the chat keeps one layout in every state).
    BoxWithConstraints(modifier.fillMaxSize().padding(kitScreenChromePadding()).testTag("chat-load-error")) {
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
                KitActionBlock(primary = retry, tertiary = listOfNotNull(setUp, report))
                Spacer(Modifier.height(Space.S3.dp))
                KitDetailsFold(
                    label = stringResource(Res.string.kitDetails),
                    copyLabel = stringResource(Res.string.kitCopyDetails),
                    notes = listOf(ChatPane.detailsNote(failure)),
                    text = failure.details,
                )
            }
        }
    }
}

/**
 * Whether the page has finished arriving: no navigation transition moves it (it grew out of its row, or slid in) any
 * more. Parts that place themselves from the page's measured chrome (the edge light) wait for it, so they are never
 * drawn at a place computed mid-flight, cut by the growing page. True where no shell hosts the page (tests, previews).
 */
@Composable
internal fun pageSettled(): Boolean {
    val host = io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost.current ?: return true
    val transition = host.animatedVisibilityScope.transition
    return transition.currentState == androidx.compose.animation.EnterExitState.Visible &&
        transition.targetState == androidx.compose.animation.EnterExitState.Visible
}
