package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import io.github.eslamasabry.oc.ui.design.text.TextScalePolicy
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/**
 * Frame text (Dart `KitChrome`): the words inside never render larger than 1.3× their spec size, whatever the system
 * and in-app text size; below the cap the platform's non-linear curve is kept. Frame parts (top bar, chips, buttons,
 * sheet headers, tabs) wrap their words in it; screens never call it. TalkBack labels are unchanged.
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
