package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/**
 * Frame scope (Dart `KitChrome`) for frame content that is not a `KitText`: inside it the font scale is capped at 1.3,
 * on the platform's own curve. Frame `KitText` (`kind = Frame`) does not need it: it sizes its words exactly as Flutter
 * does, `min(platform size, 1.3 × spec)`, from the app density (see `KitText`). P7 measured that Compose text reads
 * only a density's font scale, which is why this scope caps the scale rather than each size. Frame parts wrap their
 * non-text content (icons sized from text, custom glyph text) in it; screens never call it. TalkBack is unchanged.
 *
 * Text: frame.
 */
@Composable
public fun KitFrameText(content: @Composable () -> Unit) {
    val appDensity = LocalDensity.current
    val platform = LocalKitPlatform.current
    val capped = remember(appDensity, platform) { platform.frameDensity(appDensity, TextScalePolicy.FRAME_CAP) }
    CompositionLocalProvider(LocalDensity provides capped, content = content)
}
