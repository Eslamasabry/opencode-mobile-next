package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitVisibility

/**
 * Quiet words for a wait that runs long (owner 2026-11-14: a ten-second load said nothing): nothing while the wait is
 * quick, then [words] fold in once it has run [afterMs]. The clock waits through the kit's visibility tick, so it never
 * wakes while the app is hidden, and a wait that ran out behind the launcher shows its words when the app is back.
 * Null [words]: something else on the screen already says what is happening, and nothing shows.
 *
 * States: quick (nothing), slow (words)
 * Text: [words], secondary tone; [textModifier] reaches the words (a test tag)
 * Cap: none (the caller places it)
 * Motion: KitReveal; Off = instant
 */
@Composable
public fun KitSlowWords(
    words: String?,
    afterMs: Long,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
) {
    var slow by remember { mutableStateOf(false) }
    val visibility = LocalKitVisibility.current
    LaunchedEffect(visibility, afterMs) {
        visibility.tick(afterMs)
        slow = true
    }
    KitReveal(visible = slow && words != null, modifier = modifier) {
        KitText(words.orEmpty(), textModifier, tone = TextTone.Secondary)
    }
}
