package io.github.eslamasabry.oc.ui.kitchat.tool
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRole
import io.github.eslamasabry.oc.ui.design.effects.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurface
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind
import io.github.eslamasabry.oc.ui.kit.foundation.KitTappable
import io.github.eslamasabry.oc.ui.kit.foundation.shape
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * A step's opened output that is not a shell command (B-CHAT-112, plan 09 §9.5): the text exactly as the tool gave
 * it (leading and trailing whitespace kept, JSON already pretty-printed by the caller), mono and left to right in any
 * interface language, redacted for display, at most [maxLines] lines before [moreLabel] ([onMore] shows the rest).
 * [errorLabel] sits above failed output in the caller's words. Same box as [KitCommandBox], without the prompt sign.
 */
@Composable
public fun KitToolOutput(
    text: String,
    moreLabel: String,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = KIT_COMMAND_OUTPUT_LINES,
    errorLabel: String? = null,
) {
    val colors = LocalKitColors.current
    val shape = KitShapeId.Code.shape()
    val shown = redacted(text)
    val lines = shown.lines()
    Column(
        modifier
            .fillMaxWidth()
            .kitSurface(kitSurfaceLook(SurfaceRequest(SurfaceRole.Code), colors.surface1, colors.hairline, 1f), shape)
            .padding(horizontal = Space.S3.dp, vertical = Space.S2.dp),
        verticalArrangement = Arrangement.spacedBy(Space.S1.dp),
    ) {
        if (!errorLabel.isNullOrEmpty()) {
            KitText(errorLabel, role = TypeRole.Caption, tone = TextTone.Secondary, kind = KitTextKind.Frame)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                for (line in lines.take(maxLines)) KitMonoLtr(line, Modifier.fillMaxWidth(), TextTone.Tertiary, maxLines = 1)
            }
        }
        if (lines.size > maxLines) {
            KitTappable(onClick = onMore, shape = KitShapeId.Button) {
                KitText(
                    moreLabel,
                    Modifier.padding(horizontal = Space.S2.dp),
                    role = TypeRole.Label,
                    tone = TextTone.Accent,
                    kind = KitTextKind.Frame,
                    maxLines = 2,
                )
            }
        }
    }
}
