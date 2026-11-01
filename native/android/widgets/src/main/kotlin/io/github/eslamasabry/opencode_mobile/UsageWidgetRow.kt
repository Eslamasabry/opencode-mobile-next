package io.github.eslamasabry.opencode_mobile

internal data class UsageWidgetRow(
    val provider: String,
    val displayName: String,
    val profileName: String,
    val seconds: Long,
    val label: String,
    val left: Int,
    val resetsAt: Long,
    val fetchedAt: Long,
    val authNeeded: Boolean,
    /** "sign_in", "add_key" or "set_up" when a tap should open the fix, else "". */
    val fixAction: String = "",
    val profileId: String = "",
    /** No numbers yet: the row only offers the way in. */
    val signInOnly: Boolean = false,
    /** The numbers are old whatever their age says (offline, reader broken, reset since). */
    val forceStale: Boolean = false
)
