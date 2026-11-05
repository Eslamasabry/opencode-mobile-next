package io.github.eslamasabry.oc.ui.kit.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitLocaleTag

/** Account/reset facts retain their date, even today; the supplied instant is never polled. */
@Composable
@ReadOnlyComposable
public fun kitDateTime(atMillis: Long): String? =
    LocalKitPlatform.current.dateTime(atMillis, LocalKitLocaleTag.current)
