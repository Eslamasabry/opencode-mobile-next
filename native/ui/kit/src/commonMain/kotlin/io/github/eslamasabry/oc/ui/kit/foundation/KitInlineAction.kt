package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.KitShapeId
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextKind

/**
 * One act a line offers (a status line's "Try again", a notice's "Switch server", a row's "Turn
 * on"). Words only, as the person reads them; disabled carries its reason. Part A's action model
 * until `KitAction` (S1-KITA2) is frozen; the two converge then.
 */
@Immutable
public data class KitLineAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
    val testTag: String? = null,
    val semanticsLabel: String? = null,
    /**
     * The glyph a bar shows instead of the words on a phone (the words become what TalkBack says and the tooltip).
     * Rows and notices keep the words.
     */
    val icon: ImageVector? = null,
)

/**
 * A tertiary action in a 48 dp target, drawn as a tertiary [io.github.eslamasabry.oc.ui.kit.KitButton]: the accent's
 * readable ink on a faint capsule of it ([io.github.eslamasabry.oc.ui.kit.kitTertiaryInk]), text3 words and no capsule
 * when disabled. Never the primary's solid fill.
 * Kit-internal.
 */
@Composable
internal fun KitInlineAction(action: KitLineAction, modifier: Modifier = Modifier, maxLines: Int = 2) {
    val tagged = action.testTag?.let { modifier.testTag(it) } ?: modifier
    val ink = io.github.eslamasabry.oc.ui.kit.kitTertiaryInk()
    // The capsule is drawn before the target's clip, on the same shape.
    val capsule = if (action.enabled) Modifier.background(io.github.eslamasabry.oc.ui.kit.kitTertiaryCapsule(), KitShapeId.Button.shape()) else Modifier
    KitTappable(
        onClick = action.onClick,
        modifier =
            (if (action.semanticsLabel == null) tagged
            else tagged.semantics { contentDescription = action.semanticsLabel }).then(capsule),
        enabled = action.enabled,
        disabledReason = action.disabledReason,
        shape = KitShapeId.Button,
        ink = TextTone.Primary,
    ) {
        // The words in the readable ink: the accent tone, with the accent swapped for that ink around them only.
        val colors = io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors.current
        androidx.compose.runtime.CompositionLocalProvider(io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors provides colors.copy(accent = ink)) {
            KitText(
                action.label,
                Modifier.padding(horizontal = Space.S3.dp),
                role = TypeRole.Button,
                tone = if (action.enabled) TextTone.Accent else TextTone.Tertiary,
                kind = KitTextKind.Frame,
                maxLines = maxLines,
            )
        }
    }
}
