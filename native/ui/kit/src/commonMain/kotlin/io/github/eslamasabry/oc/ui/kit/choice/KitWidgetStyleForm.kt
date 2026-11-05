package io.github.eslamasabry.oc.ui.kit.choice

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitWidgetPreview
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/** KitWidgetStyleForm: preview, size segments, ground segments and the six accent swatches. */
@Composable
public fun KitWidgetStyleForm(
    previewSize: String,
    background: String,
    accent: String,
    accents: List<String>,
    accentName: @Composable (String) -> String,
    accentColor: @Composable (String) -> Color,
    onPreviewSize: (String) -> Unit,
    onBackground: (String) -> Unit,
    onAccent: (String) -> Unit,
    enabled: KitEnabled = KitEnabled.Enabled,
) {
    KitWidgetPreview(
        stringResource(Res.string.widgetV2PreviewTitle),
        background,
        accent,
        compact = previewSize == "compact",
        wide = previewSize == "list",
    )
    Spacer(Modifier.height(Space.S3.dp))
    KitSegmented(
        listOf(
            KitSegment("compact", stringResource(Res.string.widgetV2SizeCompact)),
            KitSegment("card", stringResource(Res.string.widgetV2SizeCard)),
            KitSegment("list", stringResource(Res.string.widgetV2SizeList)),
        ),
        selected = previewSize,
        semanticsLabel = stringResource(Res.string.widgetV2SizeLabel),
        onChange = onPreviewSize,
        enabled = enabled,
    )
    FormLabel(stringResource(Res.string.widgetV2BackgroundLabel))
    KitSegmented(
        listOf(
            KitSegment("solid", stringResource(Res.string.widgetV2BackgroundSolid)),
            KitSegment("tinted", stringResource(Res.string.widgetV2BackgroundTinted)),
            KitSegment("transparent", stringResource(Res.string.widgetV2BackgroundTransparent)),
        ),
        selected = background,
        semanticsLabel = stringResource(Res.string.widgetV2BackgroundLabel),
        onChange = onBackground,
        enabled = enabled,
    )
    FormLabel(stringResource(Res.string.widgetV2AccentLabel))
    KitSwatchGrid(
        label = stringResource(Res.string.widgetV2AccentLabel),
        inUseLabel = stringResource(Res.string.kitSwatchInUse),
        swatches =
            accents.map { id ->
                KitSwatch(
                    id,
                    accentName(id),
                    accentColor(id),
                    selected = accent == id,
                    onPressed = if (enabled.isEnabled) ({ onAccent(id) }) else null,
                    disabledReason = enabled.reason,
                )
            },
    )
    KitText(accentName(accent), Modifier.padding(top = Space.S2.dp), role = TypeRole.Caption)
}

@Composable
private fun FormLabel(text: String) {
    KitText(text, Modifier.padding(top = Space.S4.dp, bottom = Space.S2.dp), role = TypeRole.Label)
}
