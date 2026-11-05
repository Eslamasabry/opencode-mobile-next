package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.icon.*
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

public enum class KitNeedsYouReason { Decision, Blocked, Consent }

@Composable
public fun kitNeedsYouReasonWord(reason: KitNeedsYouReason): String = when (reason) {
    KitNeedsYouReason.Decision -> stringResource(Res.string.kitNeedsYouReasonDecision)
    KitNeedsYouReason.Blocked -> stringResource(Res.string.kitNeedsYouReasonBlocked)
    KitNeedsYouReason.Consent -> stringResource(Res.string.kitNeedsYouReasonConsent)
}

/** Reason words stay in the attention ink, without a second chip or a second mark. */
@Composable
public fun KitNeedsYou(reason: KitNeedsYouReason, modifier: Modifier = Modifier, waiting: String? = null) {
    Row(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(Space.S1.dp), verticalAlignment = Alignment.CenterVertically) {
        KitText(kitNeedsYouReasonWord(reason), role = TypeRole.Label, tone = TextTone.Attention)
        if (waiting != null) KitText(stringResource(Res.string.kitNeedsYouWaiting, waiting), role = TypeRole.Secondary)
    }
}

/** The supporting line's word and separator, styled at the label role. */
@Composable
public fun kitNeedsYouSpan(count: Int = 1): AnnotatedString {
    val words = if (count == 1) stringResource(Res.string.kitNeedsYouSpan__eq1) else pluralStringResource(Res.plurals.kitNeedsYouSpan, count, count)
    return AnnotatedString(words, LocalKitTypography.current.style(TypeRole.Label).copy(color = LocalKitColors.current.attention).toSpanStyle())
}

/** The mark uses the current kit icon API until order 07 supplies KitTaskMark. */
@Composable
public fun KitNeedsYouMark(modifier: Modifier = Modifier) {
    val word = stringResource(Res.string.kitTaskNeedsYou)
    Box(modifier.size(FlutterKitTokens.MARK_SLOT_SIZE.dp).clearAndSetSemantics { contentDescription = word }, contentAlignment = Alignment.Center) {
        KitIcon(KitIcons.question, size = KitIconSize.Small, tone = TextTone.Attention, growsWithText = true)
    }
}

/** Badge at the top/end corner; zero keeps the child, and the number is spoken only with its host. */
@Composable
public fun KitNeedsYouBadge(count: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val specs = KitMotion.specs
    val suffix = if (count == 1) stringResource(Res.string.kitNeedsYouBadgeSuffix__eq1) else pluralStringResource(Res.plurals.kitNeedsYouBadgeSuffix, count, count)
    val c = LocalKitColors.current
    val density = LocalDensity.current
    Box(modifier.semantics(mergeDescendants = true) { if (count > 0) stateDescription = suffix }) {
        content()
        AnimatedVisibility(count > 0, Modifier.align(Alignment.TopEnd).offset(x = FlutterKitTokens.BADGE_OFFSET.dp, y = -FlutterKitTokens.BADGE_OFFSET.dp), enter = fadeIn(specs.enter()), exit = fadeOut(specs.enter())) {
            CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, FlutterKitTokens.BADGE_TEXT_SCALE_MAX))) {
                Box(Modifier.defaultMinSize(FlutterKitTokens.BADGE_MIN_WIDTH.dp, FlutterKitTokens.BADGE_HEIGHT.dp).background(c.attentionFill, KitShapeId.Pill.shape()).padding(horizontal = Space.S1.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    AnimatedContent(count, transitionSpec = { fadeIn(specs.quick()) togetherWith fadeOut(specs.quick()) }, label = "needs-you-count") { shown ->
                        CompositionLocalProvider(LocalKitColors provides c.copy(text2 = c.onAttentionFill)) {
                            KitText(if (shown > 99) "99+" else shown.toString(), role = TypeRole.Caption) // F: lib/ui/kit/kit_needs_you.dart:301
                        }
                    }
                }
            }
        }
    }
}

/** A pointing row; decisions stay with the request card that this row opens. */
@Composable
public fun KitNeedsYouRow(title: String, reason: KitNeedsYouReason, ifIgnored: String, onOpen: () -> Unit, modifier: Modifier = Modifier, who: String? = null, server: String? = null, waiting: String? = null) {
    require(ifIgnored.isNotEmpty())
    val reasonWord = kitNeedsYouReasonWord(reason)
    val whoOnServer = if (who != null && server != null) stringResource(Res.string.kitNeedsYouWhoOnServer, "⁨$who⁩", "⁨$server⁩") else (who ?: server)?.let { "⁨$it⁩" }
    val age = waiting?.let { stringResource(Res.string.kitNeedsYouWaiting, it) }
    val supporting = listOfNotNull(whoOnServer, age, ifIgnored).joinToString(" · ")
    KitRow(title, modifier = modifier, supporting = supporting, supportingMaxLines = 2, state = KitRowState(reasonWord, KitRowStateTone.NeedsYou), onClick = onOpen) // F: lib/ui/kit/kit_needs_you.dart:209-211
}
