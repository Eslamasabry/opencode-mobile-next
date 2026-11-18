package io.github.eslamasabry.opencode_mobile.graph

import kotlinx.coroutines.sync.Mutex

/**
 * The one place both install paths look before they start a job. The setup engine (OpenCode's update, Repair, Add
 * tools) and the agents' installer share components (Linux, the essentials, Node.js) and the phone's one installer
 * slot, so they must never run on top of each other: while either has a job in its start phase or running, the other
 * says busy and keeps its try.
 */
internal object SetupLane {
    /** The setup engine's lane, held from the start of an ask until its job is handed over to the runner. */
    @Volatile var engineLane: Mutex? = null

    /** The setup engine is checking or handing over a job right now (the native job record does not say it yet). */
    val engineStarting: Boolean get() = engineLane?.isLocked == true
}
