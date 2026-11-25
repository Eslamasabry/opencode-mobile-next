package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

public const val REPLY_ASK_TAG: String = "kit-reply-ask"
public const val REPLY_ASK_YES_TAG: String = "kit-reply-ask-yes"
public const val REPLY_ASK_NO_TAG: String = "kit-reply-ask-no"
public const val REPLY_ASK_SKIP_TAG: String = "kit-reply-ask-skip"
public const val REPLY_ASK_AFTER_TAG: String = "kit-reply-ask-after"
public const val REPLY_ASK_ACTION_TAG: String = "kit-reply-ask-action"

/**
 * Quiet one-time ask riding in [KitReplyFooter]'s metadata slot in place of [KitReplyMeta]:
 * "Enjoying it?" (Caption, Tertiary), then Yes / No (Secondary, Primary) and Skip (Secondary, Tertiary),
 * each word centred in a 48 dp tall target with 8 dp horizontal padding.
 */
@Suppress("LongParameterList")
@Composable
public fun KitReplyAsk(
    question: String,
    yes: String,
    no: String,
    skip: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag(REPLY_ASK_TAG), verticalAlignment = Alignment.CenterVertically) {
        KitText(
            question,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            role = TypeRole.Caption,
            tone = TextTone.Tertiary,
            maxLines = 1,
        )
        ReplyAskTarget(onYes, TextTone.Primary, REPLY_ASK_YES_TAG) {
            KitText(yes, role = TypeRole.Secondary, tone = TextTone.Primary, maxLines = 1)
        }
        ReplyAskTarget(onNo, TextTone.Primary, REPLY_ASK_NO_TAG) {
            KitText(no, role = TypeRole.Secondary, tone = TextTone.Primary, maxLines = 1)
        }
        ReplyAskTarget(onSkip, TextTone.Tertiary, REPLY_ASK_SKIP_TAG) {
            KitText(skip, role = TypeRole.Secondary, tone = TextTone.Tertiary, maxLines = 1)
        }
    }
}

/**
 * Follow-up line shown in [KitReplyFooter]'s metadata slot after Yes ("Thanks." + star + "Star on GitHub")
 * or No ("Sorry." + "Report a problem").
 */
@Composable
public fun KitReplyAskAfter(
    note: String,
    action: String,
    onAction: () -> Unit,
    star: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier.testTag(REPLY_ASK_AFTER_TAG), verticalAlignment = Alignment.CenterVertically) {
        KitText(note, role = TypeRole.Caption, tone = TextTone.Tertiary, maxLines = 1)
        ReplyAskTarget(onAction, TextTone.Accent, REPLY_ASK_ACTION_TAG) {
            if (star) {
                KitIcon(KitIcons.starFilled, size = KitIconSize.Tiny, tone = TextTone.Accent)
                Spacer(Modifier.width(Space.S1.dp))
            }
            KitText(action, role = TypeRole.Secondary, tone = TextTone.Accent, maxLines = 1)
        }
    }
}

@Composable
private fun ReplyAskTarget(
    onClick: () -> Unit,
    tone: TextTone,
    tag: String,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val platform = LocalKitPlatform.current
    val reduced = LocalKitMotion.current.reduced
    val inkColor = colors.tone(tone)
    val indication =
        remember(platform, inkColor, reduced) {
            if (reduced) null else platform.pressIndication(inkColor)
        }
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier.heightIn(min = tokens.minTarget.dp)
                .clip(KitShapeId.Button.shape())
                .clickable(
                    interactionSource = interactions,
                    indication = indication,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(horizontal = Space.S2.dp)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}
