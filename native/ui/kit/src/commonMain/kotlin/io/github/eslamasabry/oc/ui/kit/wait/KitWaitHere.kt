package io.github.eslamasabry.oc.ui.kit.wait

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/** The kind of wait a kit part's words name, and a download's share done (0..1). */
@Immutable
public data class KitWaitHere(val kind: KitWaitKind, val fraction: Float = 0f)

/**
 * The wait named by the kit part under it. A part that shows a working mark beside words (a status line, a receipt
 * while sending, a checklist step while it works) draws this wait's [KitWaitMark] in the same box instead.
 */
public val LocalKitWaitHere: ProvidableCompositionLocal<KitWaitHere?> = compositionLocalOf { null }

/**
 * Says which of the app's waits the kit part in [content] names ("Sending…", a step downloading): the part draws that
 * wait's mark where it draws its working mark, same box, so the words beside it never move. A null [kind] keeps the
 * working mark. [fraction] is a download's share done, 0..1. Features only switch it on; the kit draws.
 */
@Composable
public fun KitWaitMarkHere(kind: KitWaitKind?, fraction: Float = 0f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitWaitHere provides kind?.let { KitWaitHere(it, fraction) }, content = content)
}
