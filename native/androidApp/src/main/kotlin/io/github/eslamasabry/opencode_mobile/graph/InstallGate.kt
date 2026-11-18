package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex

/**
 * The one gate of the phone's install jobs. The setup engine (OpenCode's update, Repair, Add tools) and the agents'
 * installer share components (Linux, the essentials, Node.js) and the one installer slot, so a job's START PHASE (its
 * checks, until its job is registered as running in its own record) is taken with [mutex].tryLock() by both: a
 * failed tryLock is "busy" and the try is kept. Once the job is registered the gate is released and each side's own
 * "a job is running" check covers the rest.
 */
@SingleIn(AppScope::class)
@Inject
class InstallGate {
    val mutex = Mutex()
}
