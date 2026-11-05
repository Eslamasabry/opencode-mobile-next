package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfacePadding

/** A full-page local editor, with its own focus and modal host; the owning screen keeps its state. */
@Composable
public fun KitPageOverlay(visible: Boolean, title: String, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    if (!visible) return
    Popup(onDismissRequest = onDismissRequest, properties = PopupProperties(focusable = true)) {
        KitSurface(
            modifier = Modifier.fillMaxSize().testTag("kit-page-overlay").semantics { paneTitle = title },
            level = SurfaceLevel.Ground,
            shape = KitShapeId.Square,
            padding = KitSurfacePadding.None,
        ) {
            KitSheetHost { content() }
        }
    }
}
