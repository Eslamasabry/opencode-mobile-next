package io.github.eslamasabry.oc.ui.kit.content.image

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.content.foundation.ContentTextAction
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitZoomAtMax
import io.github.eslamasabry.oc.ui.words.res.kitZoomAtStart
import io.github.eslamasabry.oc.ui.words.res.kitZoomFit
import io.github.eslamasabry.oc.ui.words.res.kitZoomIn
import io.github.eslamasabry.oc.ui.words.res.kitZoomOut
import org.jetbrains.compose.resources.stringResource

/** Test tags of the three buttons. */
public object KitZoomTags {
    public const val OUT: String = "kit-zoom-out"
    public const val FIT: String = "kit-zoom-fit"
    public const val IN: String = "kit-zoom-in"
}

/**
 * The visible zoom path (A11Y-5): Zoom out, Fit to screen, Zoom in. A disabled button says why ("Already at full view",
 * "Largest zoom"). Wraps to a second line rather than squeezing at large text.
 *
 * States: fitted (out and fit disabled), zoomed, at the largest zoom (in disabled)
 * Text: frame
 * Cap: Control
 * Motion: none
 * Hidden: none
 * Replaces: the zoom buttons of KitImageGallery / KitViewer (lib/ui/kit/kit_image.dart)
 */
@Composable
public fun KitZoomControls(state: KitZoomState, modifier: Modifier = Modifier) {
    val atStart = stringResource(Res.string.kitZoomAtStart)
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.S1.dp, Alignment.CenterHorizontally),
    ) {
        ContentTextAction(
            stringResource(Res.string.kitZoomOut), state::zoomOut,
            Modifier.testTag(KitZoomTags.OUT), enabled = state.canZoomOut, disabledReason = atStart,
        )
        ContentTextAction(
            stringResource(Res.string.kitZoomFit), state::fit,
            Modifier.testTag(KitZoomTags.FIT), enabled = state.canZoomOut, disabledReason = atStart,
        )
        ContentTextAction(
            stringResource(Res.string.kitZoomIn), state::zoomIn,
            Modifier.testTag(KitZoomTags.IN), enabled = state.canZoomIn,
            disabledReason = stringResource(Res.string.kitZoomAtMax),
        )
    }
}
