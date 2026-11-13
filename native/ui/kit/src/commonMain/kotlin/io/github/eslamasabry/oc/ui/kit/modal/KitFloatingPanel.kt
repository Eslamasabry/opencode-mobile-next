package io.github.eslamasabry.oc.ui.kit.modal

import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardInsets
import io.github.eslamasabry.oc.ui.kit.frame.kitSystemInsets
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.*
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitEdge
import io.github.eslamasabry.oc.ui.kit.foundation.kitEdge

/** Flutter's widget window: a 440 dp floating card, inset on all sides, above the keyboard. */
@Composable
public fun KitFloatingPanel(
    onDismiss: (() -> Unit)?,
    dismissLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKitColors.current
    val still = KitMotion.state.reduced
    val travels = KitMotion.state.travels
    var entered by remember { mutableStateOf(still) }
    LaunchedEffect(Unit) { entered = true }
    val progress by
        animateFloatAsState(
            if (entered) 1f else 0f,
            KitMotion.specs.enter(),
            label = "floating-panel",
        )
    KitBackGuard(true) { onDismiss?.invoke() }
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Floating,
                KitShapeId.Sheet,
                SurfaceLevel.Surface2,
                ownFill = colors.surface2,
                roundSheetBottom = true, // F: lib/ui/kit/kit_floating_panel.dart:118-120
            )
        )
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(colors.scrim.copy(alpha = colors.scrim.alpha * progress))
                .clickable(enabled = onDismiss != null, onClickLabel = dismissLabel) {
                    onDismiss?.invoke()
                }
                .testTag("floating-panel-scrim")
                .kitEdge(KitEdge.Chrome) // the scrim covers the window (native/docs/kit/LAYOUT-RULES.md §2)
        )
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(kitSystemInsets().union(kitKeyboardInsets()))
                .padding(Space.S4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier.widthIn(max = 440.dp)
                    .fillMaxWidth() // F: lib/ui/kit/kit_floating_panel.dart:31
                    .graphicsLayer {
                        alpha = progress
                        // Calm fades only; the 24 dp rise is Full's.
                        translationY = if (travels) (1 - progress) * 24.dp.toPx() else 0f
                    } // F: lib/ui/kit/kit_floating_panel.dart:72
                    .clip(look.shape)
                    .then(look.modifier)
                    .pointerInput(Unit) { detectTapGestures {} }
                    .padding(Space.S4.dp)
                    .testTag("kit-floating-panel")
                    .semantics { isTraversalGroup = true },
                content = content,
            )
        }
    }
}

/** The title and visible Close target shared by the widget's three floating panels. */
@Composable
public fun KitPanelHeading(
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KitText(
            title,
            Modifier.weight(1f).semantics { heading() },
            role = TypeRole.Headline,
            maxLines = 2,
        ) // F: lib/ui/kit/kit_steer_box.dart:116
        KitIconButton(KitIcons.close, closeLabel, onClose, enabled = enabled)
    }
}
