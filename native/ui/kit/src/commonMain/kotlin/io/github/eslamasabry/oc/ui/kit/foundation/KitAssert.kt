package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/**
 * The kit's structural rule check (plan 06 §16.1): throws only in debug, test and preview builds
 * (`KitPlatform.isDebug`), so a broken rule fails a test, while a release build never dies in composition (§01 §9.6).
 * Release logging joins when `:ui:kit` gets `AppLog`. `require(`/`check(` stay out of composables.
 */
@Composable
@ReadOnlyComposable
internal fun kitAssert(condition: Boolean, message: () -> String) {
    if (!condition && LocalKitPlatform.current.isDebug) throw KitRuleBroken(message())
}

/** A kit rule a caller broke (debug builds only). */
public class KitRuleBroken(message: String) : IllegalStateException(message)
