package io.github.eslamasabry.oc.ui.kit.content.foundation

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/**
 * The content kit's `kitAssert` (plan 06 §16.1): a broken rule throws only in debug, test and preview builds
 * (`KitPlatform.isDebug`); in release the part draws nothing instead of killing the process. Moves to the kit's own
 * `kitAssert` when `:ui:kit` ships it.
 */
@Composable
internal fun contentAssert(condition: Boolean, message: () -> String) {
    if (!condition && LocalKitPlatform.current.isDebug) throw IllegalArgumentException(message())
}
