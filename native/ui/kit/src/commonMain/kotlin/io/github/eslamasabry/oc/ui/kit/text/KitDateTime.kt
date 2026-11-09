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

/** A date alone, in the app's language (Flutter yMMMd); null where the platform has no formatter. */
@Composable
@ReadOnlyComposable
public fun kitDate(atMillis: Long): String? =
    LocalKitPlatform.current.dateOnly(atMillis, LocalKitLocaleTag.current)

/** A clock time alone: the phone's 12 or 24 hour setting and digits. Null where the platform has no formatter. */
@Composable
@ReadOnlyComposable
public fun kitClock(atMillis: Long): String? =
    LocalKitPlatform.current.messageTime(atMillis, atMillis)
