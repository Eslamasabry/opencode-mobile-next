package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitTokens
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.math.max

/**
 * The desktop right-click region (Dart `KitContextRegion`, lib/ui/kit/kit_context_region.dart): a secondary click,
 * Shift+F10 and the context-menu key open the one popup menu around [content], which keeps its own taps. Off desktop
 * it is [content] untouched: the long-press sheets stay the only action surface on touch. Keyboard focus draws the
 * kit focus ring (2 physical px, `accent`). [menu] is built at the moment of the click. The region is excluded from
 * semantics. States: idle, focused, menu-open (a second click is ignored until the menu closes).
 */
@Composable
public fun KitContextRegion(
    menu: () -> List<KitMenuItem>,
    modifier: Modifier = Modifier,
    menuLabel: String? = null,
    content: @Composable () -> Unit,
) {
    if (LocalKitPlatform.current.family != KitPlatformFamily.Desktop) { // F: lib/ui/kit/kit_context_region.dart:83
        content()
        return
    }
    var open by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(emptyList<KitMenuItem>()) }
    var focused by remember { mutableStateOf(false) }
    val dpr = LocalDensity.current.density
    // F: lib/ui/kit/kit_tokens.dart:322-326
    val ring = if (dpr <= 1f) max(FlutterKitTokens.FOCUS_RING_WIDTH_PHYSICAL_PX / dpr, 1f) else FlutterKitTokens.FOCUS_RING_WIDTH_PHYSICAL_PX / dpr
    val show = { if (!open) { items = menu(); open = true } } // F: lib/ui/kit/kit_context_region.dart:62
    val accent = LocalKitColors.current.accent
    KitMenu(
        expanded = open,
        items = items,
        onDismissRequest = { open = false },
        title = menuLabel,
        modifier = modifier
            .clearAndSetSemantics {}
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent {
                val hit = it.type == KeyEventType.KeyDown && (it.key == Key.Menu || (it.key == Key.F10 && it.isShiftPressed))
                if (hit) show()
                hit
            }
            .focusable()
            .then(if (focused) Modifier.border(androidx.compose.ui.unit.Dp(ring), accent) else Modifier)
            .secondaryClick { show() },
    ) { Box { content() } }
}

/** A secondary (right) button press anywhere in the region, seen first and left unconsumed so the child keeps its taps. */
private fun Modifier.secondaryClick(onClick: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.buttons.isSecondaryPressed && event.changes.any { it.changedToDown() }) onClick()
            }
        }
    }
