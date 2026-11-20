package io.github.eslamasabry.opencode_mobile.graph

import java.util.concurrent.atomic.AtomicInteger

/** How many reads each updater made since the last per-minute report (a loop shows here at once). */
internal object UpdaterReads {
    val opencode = AtomicInteger()
    val agents = AtomicInteger()
}

/** What an updater asks before it starts anything: is the app in front, and did the person choose this update. */
@dev.zacsweers.metro.SingleIn(dev.zacsweers.metro.AppScope::class)
@dev.zacsweers.metro.Inject
class UpdaterGates(val visibility: AppVisibility, val choices: PhoneUpdateChoices)
