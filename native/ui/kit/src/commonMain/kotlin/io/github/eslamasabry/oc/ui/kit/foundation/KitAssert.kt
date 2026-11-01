package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

private val log = AppLog.tagged("kit")

/** Thrown by [kitAssert] in debug, test and preview builds. */
public class KitRuleBroken(message: String) : IllegalStateException(message)

/**
 * The kit's debug assertion (plan 06 §16.1): composition never throws in release, because Compose has no error widget.
 * Debug, test and preview builds ([io.github.eslamasabry.oc.ui.kit.platform.KitPlatform.isDebug]) throw [KitRuleBroken];
 * release logs the rule (never the caller's data) once per call.
 */
@Composable
@NonRestartableComposable
internal fun kitAssert(condition: Boolean, message: () -> String) {
    if (condition) return
    if (LocalKitPlatform.current.isDebug) throw KitRuleBroken(message())
    log.w { message() }
}
