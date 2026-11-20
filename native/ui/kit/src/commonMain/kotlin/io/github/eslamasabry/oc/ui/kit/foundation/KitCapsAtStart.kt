package io.github.eslamasabry.oc.ui.kit.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.eslamasabry.oc.ui.design.layout.CapRole

/**
 * True inside a pane that stands beside a list (big screens, decision (a)): the page's capped reading columns
 * ([CapRole.Conversation], [CapRole.Reading], [CapRole.List]) sit at the pane's start, right after the page's gutter, and
 * the room the cap leaves goes to the end side (mirrored in right-to-left), instead of half on each side. Centred
 * parts (a state, a dialog, a sheet, a control) stay centred. False everywhere else, so a window that is one page
 * (every phone) lays out as before. The shell provides it for the pane beside its list.
 */
public val LocalKitCapsAtStart: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** The cap roles that follow [LocalKitCapsAtStart]: the page's own reading columns. */
internal val CapsAtStartRoles: Set<CapRole> = setOf(CapRole.Conversation, CapRole.Reading, CapRole.List)
