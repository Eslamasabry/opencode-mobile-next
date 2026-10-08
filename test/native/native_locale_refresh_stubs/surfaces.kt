package io.github.eslamasabry.opencode_mobile

import android.content.Context

// Only detached surface entry points are doubled. The refresh coordinator and
// NativeStrings implementation are production sources, with real XML resources.
object BackgroundConnectionService {
    var unchangedPayload = "unchanged session counts"
    val refreshPayloads = mutableListOf<String>()
    fun refreshLocale(context: Context) { refreshPayloads.add(unchangedPayload) }
}

object SessionsWidgetProvider {
    var refreshes = 0
    fun refreshAll(context: Context) { refreshes++ }
}
