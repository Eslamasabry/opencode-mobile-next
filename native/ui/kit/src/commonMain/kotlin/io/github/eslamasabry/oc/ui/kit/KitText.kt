package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitAppDensity
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

/** Frame text is capped at 1.3×; reading text follows the person's size up to 2.5× (plan 06 §9). */
public enum class KitTextKind { Frame, Reading }

/**
 * Text in one of the ten roles and seven tones (Dart `KitText`; plan 06 §7.1). Reading text picks its direction per
 * paragraph (an English reply in an Arabic interface reads left to right, K17); frame text follows the app locale.
 *
 * Text: frame or reading, by [kind].
 */
@Composable
public fun KitText(
    text: String,
    modifier: Modifier = Modifier,
    role: TypeRole = TypeRole.Body,
    tone: TextTone = TextTone.Primary,
    kind: KitTextKind = KitTextKind.Reading,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    pageTitle: Boolean = false,
    sectionLabel: Boolean = false,
) {
    // pageTitle / sectionLabel are role hints; the head typography resolves the role alone.
    val base = LocalKitTypography.current.style(role)
    val style =
        base.copy(
            color = LocalKitColors.current.tone(tone),
            textDirection = if (kind == KitTextKind.Reading) TextDirection.Content else TextDirection.Unspecified,
            textAlign = textAlign ?: TextAlign.Start,
        )
    if (kind == KitTextKind.Reading) {
        BasicText(text, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    // Frame text, the Flutter way: min(platform size at the current scale, 1.3 × spec), per role size. Compose text
    // reads only a density's font scale, so the words are laid out at that exact size under font scale 1 (1 sp = 1 dp),
    // with line height and tracking scaled in proportion. Measured in P7; see KitTextScale.kt.
    val app = LocalKitAppDensity.current ?: LocalDensity.current
    val specSp = style.fontSize.value
    val target = TextScalePolicy.frameDp(specSp, with(app) { specSp.sp.toDp().value })
    val k = target / specSp
    val sized =
        style.copy(
            fontSize = target.sp,
            lineHeight = (style.lineHeight.value * k).sp,
            letterSpacing = (style.letterSpacing.value * k).sp,
        )
    CompositionLocalProvider(LocalDensity provides Density(app.density, 1f)) {
        BasicText(text, modifier, style = sized, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Slice-0 roles the first feature screens use. Kept so P1's screens compile; new code passes a [TypeRole]. */
public enum class KitTextRole { Title, Body, Supporting }

@Composable
public fun KitText(text: String, role: KitTextRole, modifier: Modifier = Modifier) {
    when (role) {
        KitTextRole.Title -> KitText(text, modifier, TypeRole.Title, kind = KitTextKind.Frame)
        KitTextRole.Body -> KitText(text, modifier, TypeRole.Body)
        KitTextRole.Supporting -> KitText(text, modifier, TypeRole.Secondary, TextTone.Secondary)
    }
}
