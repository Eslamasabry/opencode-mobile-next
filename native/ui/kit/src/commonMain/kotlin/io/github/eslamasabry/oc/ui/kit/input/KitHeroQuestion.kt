package io.github.eslamasabry.oc.ui.kit.input

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.eslamasabry.oc.ui.design.layout.CapAlign
import io.github.eslamasabry.oc.ui.design.layout.CapRole
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.kitMaxWidth
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTypography

private const val HeroSize = 40f
private const val HeroNarrowSize = 32f
private const val HeroNarrowBelow = 340f
private const val HeroMaxScale = 1.15f

/**
 * The one big question a first page opens on (Dart `KitHeroQuestion`): display type, larger than a screen title, tight
 * tracking, at the start of the page's rails. The size grows with the person's text size up to 1.15 times, steps down
 * on a narrow phone, then wraps at its words.
 *
 * States: default
 * Text: reading
 * Cap: Reading
 * Motion: none
 * Hidden: none
 * Replaces: KitHeroQuestion (lib/ui/kit/kit_hero_question.dart)
 */
@Composable
public fun KitHeroQuestion(question: String, modifier: Modifier = Modifier) {
    val base = LocalKitTypography.current.style(TypeRole.LargeTitle)
    val color = LocalKitColors.current.tone(TextTone.Primary)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.kitMaxWidth(CapRole.Reading, CapAlign.Start).fillMaxWidth()) {
        val size = if (maxWidth.value < HeroNarrowBelow) HeroNarrowSize else HeroSize
        // Capped text scale: only kit parts clamp (A11Y-8). Laid out at font scale 1 with the capped size.
        val scale = density.fontScale.coerceAtMost(HeroMaxScale)
        val px = size * scale
        val spacing = if (base.letterSpacing.value == 0f) 0f else -px * 0.02f
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            BasicText(
                question,
                Modifier.semantics { heading() }.testTag("kit-hero-question"),
                style = base.copy(
                    color = color,
                    fontSize = px.sp,
                    lineHeight = (px + 6f * scale).sp,
                    fontWeight = FontWeight.W500,
                    letterSpacing = spacing.sp,
                    textDirection = TextDirection.Content,
                ),
            )
        }
    }
}
