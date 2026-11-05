package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.action.KitIconButtonCopy
import io.github.eslamasabry.oc.ui.kit.foundation.KitSurfaceInput
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.foundation.kitSurfaceLook
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Flutter kit_one_time_code.dart: a large mono value, always LTR, with its own Copy control. */
@Composable
public fun KitOneTimeCode(
    code: String,
    copyLabel: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val colors = LocalKitColors.current
    val typography = LocalKitTypography.current
    val style =
        typography
            .style(TypeRole.LargeTitle)
            .copy(
                fontFamily = typography.style(TypeRole.Mono).fontFamily,
                color = colors.text1,
                textDirection = TextDirection.Ltr,
            )
    val look =
        kitSurfaceLook(
            KitSurfaceInput(
                SurfaceRole.Field,
                KitShapeId.Panel,
                SurfaceLevel.Surface1,
                ownFill = if (colors.dark) colors.surface1 else colors.ground,
            )
        )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Column(modifier.kitMaxWidth(CapRole.Control).fillMaxWidth()) {
        if (label != null) {
            KitText(label, role = TypeRole.Secondary)
            Spacer(Modifier.height(Space.S2.dp))
        }
        Row(
            Modifier.fillMaxWidth().then(look.modifier).clip(look.shape).padding(Space.S3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(Modifier.weight(1f)) {
                // Flutter's FittedBox scales the whole mono title down only when the code needs it.
                val measured = measurer.measure(code, style = style, maxLines = 1, softWrap = false)
                val scale =
                    minOf(
                        1f,
                        with(density) { maxWidth.toPx() } / measured.size.width.coerceAtLeast(1),
                    )
                BasicText(
                    code,
                    Modifier.testTag("kit-one-time-code").semantics {
                        contentDescription = code.toList().joinToString(" ")
                    },
                    style =
                        style.copy(
                            fontSize = style.fontSize * scale,
                            lineHeight = style.lineHeight * scale,
                        ),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.width(Space.S2.dp))
            KitIconButtonCopy(
                { code },
                Modifier.testTag("kit-one-time-code-copy"),
                label = copyLabel,
            )
        }
    }
}
