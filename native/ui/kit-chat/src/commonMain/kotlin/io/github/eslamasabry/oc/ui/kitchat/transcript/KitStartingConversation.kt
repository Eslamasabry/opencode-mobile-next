package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.frame.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitPace
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** Where the first message is while its conversation is made and the words go out. */
public enum class KitStartingPhase {
    /** Creating the conversation and sending: the bubble is already the person's message. */
    Sending,

    /** Definitely not sent. The words and choices are kept; Try again or Edit. */
    NotSent,

    /** Maybe sent. Never resent from here; only the made conversation can be opened. */
    Unconfirmed,
}

/**
 * The first chat frame, shown the moment Start is tapped and before the server answers: the exact
 * prompt as the first message bubble, the chosen agent and model under it, and one status line that
 * says what is happening (Sending…, not sent, not sure). A definite failure offers Try again (the
 * same attempt) and Edit (back to the words and choices, nothing lost); an unsure one offers only
 * opening the made conversation. The words remain owned by the screen's ViewModel and draft.
 *
 * States: sending, not sent, unconfirmed Text: reading (bubble), frame (status) Cap: Conversation
 * Motion: status and actions cross-fade QUICK; Off = instant
 */
@Composable
public fun KitStartingConversation(
    text: String,
    project: String,
    model: String,
    status: String,
    backLabel: String,
    onBack: () -> Unit,
    phase: KitStartingPhase = KitStartingPhase.Sending,
    agent: String? = null,
    reason: String? = null,
    details: Redacted? = null,
    detailsLabel: String? = null,
    copyLabel: String? = null,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    editLabel: String? = null,
    onEdit: (() -> Unit)? = null,
    reviewLabel: String? = null,
    onReview: (() -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
    KitScreen(
        topBar = {
            KitTopBar(
                title = text.lineSequence().firstOrNull().orEmpty().take(80),
                subtitle = project,
                exit = KitTopBarExit.Back,
                exitLabel = backLabel,
                onExit = onBack,
            )
        },
        bottom = {
            // The choices the message goes out with, read-only: they are never lost on a failure.
            Row(
                Modifier.padding(
                    horizontal = LocalKitTokens.current.gutter.dp,
                    vertical = Space.S2.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
            ) {
                agent?.takeIf(String::isNotBlank)?.let { KitChip(it, icon = KitIcons.agent) }
                KitChip(model, icon = KitIcons.model)
            }
        },
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalKitTokens.current.gutter.dp, vertical = Space.S4.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
            horizontalAlignment = Alignment.End,
        ) {
            KitPromptBubble(text, Modifier.testTag("starting-prompt"))
            KitSwap(phase, pace = KitPace.Quick, alignment = Alignment.TopEnd) { shown ->
                Column(
                    Modifier.fillMaxWidth().testTag("starting-status").semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    verticalArrangement = Arrangement.spacedBy(Space.S2.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    KitQuietLine(status)
                    if (shown == KitStartingPhase.NotSent) {
                        reason?.takeIf(String::isNotBlank)?.let { KitQuietLine(it) }
                        if (details != null && detailsLabel != null && copyLabel != null)
                            KitDetailsFold(
                                label = detailsLabel,
                                copyLabel = copyLabel,
                                text = details,
                                modifier = Modifier.fillMaxWidth(),
                            )
                    }
                    // Row follows the layout direction: in Arabic the pair sits on the start edge
                    // of the bubble's own side, mirrored with it.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.S2.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (
                            shown == KitStartingPhase.NotSent && onEdit != null && editLabel != null
                        )
                            KitButton(
                                editLabel,
                                onEdit,
                                role = KitButtonRole.Tertiary,
                                modifier = Modifier.testTag("starting-edit"),
                            )
                        if (
                            shown == KitStartingPhase.NotSent &&
                                onRetry != null &&
                                retryLabel != null
                        )
                            KitButton(
                                retryLabel,
                                onRetry,
                                role = KitButtonRole.Secondary,
                                modifier = Modifier.testTag("starting-retry"),
                            )
                        if (
                            shown == KitStartingPhase.Unconfirmed &&
                                onReview != null &&
                                reviewLabel != null
                        )
                            KitButton(
                                reviewLabel,
                                onReview,
                                role = KitButtonRole.Secondary,
                                modifier = Modifier.testTag("starting-review"),
                            )
                    }
                }
            }
        }
    }
}
