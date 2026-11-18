package io.github.eslamasabry.opencode_mobile.graph

import java.util.concurrent.atomic.AtomicInteger

/** How many reads each updater made since the last per-minute report (a loop shows here at once). */
internal object UpdaterReads {
    val opencode = AtomicInteger()
    val agents = AtomicInteger()
}
