package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform

/** The kit-owned locale/time-zone seam for displayed timestamps. The host supplies both instants; this never polls. */
@Composable
@ReadOnlyComposable
public fun kitLocalTime(createdMillis: Long, nowMillis: Long): String? =
    LocalKitPlatform.current.messageTime(createdMillis, nowMillis)
