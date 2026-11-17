package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.engine.analytics.Reporting
import io.github.eslamasabry.oc.engine.analytics.reportPhoneServerFollowRefused
import dev.zacsweers.metro.Inject

/**
 * Following the activity (BuiltinLinux.setActivityResumed) can be refused. The next resume asks again (the one bounded
 * retry); a second refusal in a row is handed once to [refused] (a non-fatal with a fixed name, behind the
 * crash-report switch), and nothing loops: the following resumes ask as usual and report nothing until one lands.
 */
internal class ActivityFollow(private val refused: () -> Unit) {
    private var refusals = 0
    private var told = false

    fun follow(on: Boolean, call: (Boolean) -> Unit) {
        val result = runCatching { call(on) }
        if (result.isSuccess) {
            refusals = 0
            told = false
            return
        }
        AppLog.tagged("Linux").w { "follow activity ${result.exceptionOrNull()?.let { it::class.simpleName }}" }
        refusals++
        if (refusals >= 2 && !told) {
            told = true
            refused()
        }
    }
}

/** Connects the runtime's refused-follow signal to the crash-report switch; armed once at app start. */
@Inject
class ActivityFollowReports(private val reporting: Reporting, private val gates: LocalServerGates) {
    fun arm() {
        gates.followRefused = { reporting.reportPhoneServerFollowRefused() }
    }
}
