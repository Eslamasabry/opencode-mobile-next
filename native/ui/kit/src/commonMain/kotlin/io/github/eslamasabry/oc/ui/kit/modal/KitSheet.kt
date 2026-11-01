package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.toShape
import io.github.eslamasabry.oc.ui.kit.motion.KitMotion
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens

/** How tall a sheet is (Dart `KitSheetHeight`). */
public enum class KitSheetHeight { Content, Half, Full }

/** Plan 06 §7.4: a medium window caps a bottom sheet at 640 dp. Wide-window panels join with `WindowClass` (WP K1). */
private const val SHEET_MAX_WIDTH_DP = 640
private const val CONTENT_MAX_SHARE = 0.9f

/**
 * Where a screen's sheets render: in-window, above the screen's content (plan 06 K9, §7.4). `KitScreen` places one;
 * `:ui:shell` places one root host for app-level overlays. It holds one sheet at a time: a second sheet replaces the
 * first (no sheet on a sheet).
 */
@Stable
public class KitSheetHostState internal constructor() {
    internal var entry: SheetEntry? by mutableStateOf(null)
        private set
    internal val shown: MutableTransitionState<Boolean> = MutableTransitionState(false)

    internal fun show(e: SheetEntry) {
        entry = e
        shown.targetState = true
    }

    internal fun hide(e: SheetEntry) {
        if (entry === e) shown.targetState = false
    }

    /** True while a sheet is up or animating (tests and `KitScreenAudit`). */
    public val isShowing: Boolean get() = shown.currentState || shown.targetState
}

internal class SheetEntry {
    var title: String by mutableStateOf("")
    var subtitle: String? by mutableStateOf(null)
    var closeLabel: String by mutableStateOf("")
    var height: KitSheetHeight by mutableStateOf(KitSheetHeight.Content)
    var onDismissRequest: () -> Unit by mutableStateOf({})
    var body: @Composable ColumnScope.() -> Unit by mutableStateOf({})
}

public val LocalKitSheetHost: ProvidableCompositionLocal<KitSheetHostState?> = staticCompositionLocalOf { null }

/** Places the host around [content]. */
@Composable
public fun KitSheetHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { KitSheetHostState() }
    Box(modifier) {
        CompositionLocalProvider(LocalKitSheetHost provides state, content = content)
        SheetLayer(state)
    }
}

/**
 * A bottom sheet as screen state (Dart `showKitSheet`, plan 06 §7.4): shown while [visible], closed by the scrim, the
 * Close action or [onDismissRequest]'s caller. A handle, a header (title ≤ 4 words, frame text) with Close at the end,
 * then [body]. Rendered by the nearest [KitSheetHost].
 *
 * Text: frame (header), reading (body). Motion: rises on the spatial spring, scrim fades on `STANDARD`; instant under
 * Off. [closeLabel] comes from `:ui:words` until the kit's own words land.
 */
@Composable
public fun KitSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    closeLabel: String,
    subtitle: String? = null,
    height: KitSheetHeight = KitSheetHeight.Content,
    body: @Composable ColumnScope.() -> Unit,
) {
    val host = checkNotNull(LocalKitSheetHost.current) { "KitSheet needs a KitSheetHost (KitScreen places one)" }
    val entry = remember { SheetEntry() }
    SideEffect {
        entry.title = title
        entry.subtitle = subtitle
        entry.closeLabel = closeLabel
        entry.height = height
        entry.onDismissRequest = onDismissRequest
        entry.body = body
    }
    DisposableEffect(host, visible) {
        if (visible) host.show(entry) else host.hide(entry)
        onDispose { host.hide(entry) }
    }
}

@Composable
private fun SheetLayer(state: KitSheetHostState) {
    val entry = state.entry ?: return
    val colors = LocalKitColors.current
    val tokens = LocalKitTokens.current
    val specs = KitMotion.specs
    AnimatedVisibility(state.shown, enter = fadeIn(specs.enter()), exit = fadeOut(specs.exit())) {
        Box(
            Modifier.fillMaxSize()
                .background(colors.scrim)
                .pointerInput(entry) { detectTapGestures { entry.onDismissRequest() } },
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val maxSheet = maxHeight * CONTENT_MAX_SHARE
        AnimatedVisibility(
            state.shown,
            enter = slideInVertically(specs.spatial<IntOffset>()) { it },
            exit = slideOutVertically(specs.exit<IntOffset>()) { it },
        ) {
            val sized =
                when (entry.height) {
                    KitSheetHeight.Content -> Modifier.heightIn(max = maxSheet)
                    KitSheetHeight.Half -> Modifier.height(maxHeight * 0.5f)
                    KitSheetHeight.Full -> Modifier.height(maxSheet)
                }
            Column(
                Modifier.widthIn(max = SHEET_MAX_WIDTH_DP.dp)
                    .fillMaxWidth()
                    .then(sized)
                    .clip(KitShapeId.Sheet.toShape())
                    .background(colors.surface2)
                    .pointerInput(Unit) { detectTapGestures { } } // taps on the sheet never reach the scrim
                    .semantics {
                        paneTitle = entry.title
                        isTraversalGroup = true
                    }
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.fillMaxWidth().height(tokens.handleBand.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(tokens.handleWidth.dp, tokens.handleThickness.dp)
                            .clip(KitShapeId.Pill.toShape())
                            .background(colors.text3),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = tokens.gutter.dp, end = (tokens.gutter / 2).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        KitText(entry.title, role = TypeRole.Title, kind = KitTextKind.Frame, maxLines = 2)
                        entry.subtitle?.let {
                            KitText(it, role = TypeRole.Secondary, tone = TextTone.Secondary, kind = KitTextKind.Frame)
                        }
                    }
                    KitTappable(onClick = { entry.onDismissRequest() }, shape = KitShapeId.Pill) {
                        KitText(
                            entry.closeLabel,
                            Modifier.padding(horizontal = tokens.labelGap.dp),
                            role = TypeRole.Label,
                            tone = TextTone.Secondary,
                            kind = KitTextKind.Frame,
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(tokens.gutter.dp),
                    verticalArrangement = Arrangement.spacedBy(tokens.labelGap.dp),
                    content = entry.body,
                )
            }
        }
    }
}
