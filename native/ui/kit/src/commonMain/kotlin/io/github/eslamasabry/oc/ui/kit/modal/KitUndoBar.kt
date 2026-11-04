package io.github.eslamasabry.oc.ui.kit.modal
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitWorkingMark
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitSheetDismiss
import io.github.eslamasabry.oc.ui.words.res.kitUndoAction
import io.github.eslamasabry.oc.ui.words.res.kitUndoFailed
import io.github.eslamasabry.oc.ui.words.res.kitUndoWorking
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** The undo window (Dart `KitUndo`): 8 s, stretched by the person's accessibility timeout. */
public const val UNDO_WINDOW_MS: Long = 8_000L

/** Plan 06 §7.4: the bar reads at most the reading width. */
private const val UNDO_MAX_DP = 560

/**
 * What can be undone right now (Dart `showKitUndo` arguments). [id] tells offers apart: a new offer replaces the old one
 * and restarts its window. [failed] is the already-resolved reason the undo did not happen.
 */
@Immutable
public data class KitUndoOffer(val id: String, val message: String, val working: Boolean = false, val failed: String? = null)

/** Pure rules of [KitUndoBar]. */
public object UndoRules {
    /**
     * How long the offer stays: null (until dismissed) under accessible navigation, a working or failed offer
     * (the person must see the outcome), otherwise the accessibility-recommended stretch of 8 s.
     */
    public fun timeoutMs(accessibleNavigation: Boolean, offer: KitUndoOffer, recommended: (Long) -> Long): Long? =
        if (accessibleNavigation || offer.working || offer.failed != null) null else recommended(UNDO_WINDOW_MS)
}

/**
 * The one undo offer (Dart `showKitUndo`; plan 06 §7.4), as screen state: shown while [offer] is non-null, at the
 * bottom of the root overlay (the shell places it once). One at a time; a new [KitUndoOffer.id] replaces the old one.
 * It times out after 8 s ([onTimeout]), stretched by the person's accessibility timeout; under accessible navigation it
 * stays until dismissed, with a Dismiss action. While [KitUndoOffer.working] it says "Undoing"; a failure says why and
 * stays. A polite live region; no haptic (MOT-11).
 *
 * States: offered, working, failed, accessible (no timeout)
 * Text: reading (message), frame (actions)
 * Cap: Reading (560)
 * Motion: rises and fades in on the enter spec; Off = instant
 * Replaces: showKitUndo (lib/ui/kit/kit_undo.dart)
 */
@Composable
public fun KitUndoBar(offer: KitUndoOffer?, onUndo: () -> Unit, onTimeout: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    val signals by LocalKitPlatform.current.signals.collectAsState()
    val a11y = LocalAccessibilityManager.current
    val last = remember { LastOffer() }
    if (offer != null) last.offer = offer
    val shown = last.offer
    if (offer != null) {
        val timeout = UndoRules.timeoutMs(signals.accessibleNavigation, offer) { ms ->
            a11y?.calculateRecommendedTimeoutMillis(ms, containsIcons = false, containsText = true, containsControls = true) ?: ms
        }
        if (timeout != null) {
            LaunchedEffect(offer.id, timeout) {
                delay(timeout)
                onTimeout()
            }
        }
    }
    AnimatedVisibility(
        visible = offer != null,
        modifier = modifier,
        enter = fadeIn(specs.enter()) + slideInVertically(specs.enter<IntOffset>()) { it / 2 },
        exit = fadeOut(specs.exit()) + slideOutVertically(specs.exit<IntOffset>()) { it / 2 },
    ) {
        val o = shown ?: return@AnimatedVisibility
        val shape = KitShapeId.Panel.shape()
        Row(
            Modifier.widthIn(max = UNDO_MAX_DP.dp)
                .fillMaxWidth()
                .padding(horizontal = tokens.gutter.dp, vertical = Space.S2.dp)
                .kitSurface(kitSurfaceLook(SurfaceRequest(SurfaceRole.Floating, SurfaceLevel.Surface2), colors.surface2, colors.hairline, 1f), shape)
                .heightIn(min = tokens.rowHeightOne.dp)
                .padding(start = tokens.gutter.dp, end = Space.S1.dp)
                .testTag("kit-undo")
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.S2.dp),
        ) {
            if (o.working) KitWorkingMark(colors.text2, tokens.smallIcon.dp)
            val words =
                when {
                    o.working -> stringResource(Res.string.kitUndoWorking)
                    o.failed != null -> stringResource(Res.string.kitUndoFailed, o.failed)
                    else -> o.message
                }
            KitText(words, Modifier.weight(1f), role = TypeRole.Body, tone = TextTone.Primary, maxLines = 3)
            if (!o.working && o.failed == null) {
                KitButton(label = stringResource(Res.string.kitUndoAction), onClick = onUndo, role = KitButtonRole.Tertiary)
            }
            if (signals.accessibleNavigation || o.failed != null) {
                KitButton(label = stringResource(Res.string.kitSheetDismiss), onClick = onTimeout, role = KitButtonRole.Tertiary)
            }
        }
    }
}

private class LastOffer {
    var offer: KitUndoOffer? = null
}
