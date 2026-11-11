package io.github.eslamasabry.oc.engine.runtime.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The reconnect after a start of this phone's server answered. It runs on its own, after the start has returned, never
 * inside it: a start can itself be part of a connection attempt. The first connection to This phone waits for the
 * server and restarts it once when it does not answer (LocalServerGate), and that attempt holds the connection
 * runtime's command lock and the healing's connect lane while it waits. A reconnect awaited inside the restart waits
 * for those same locks, which wait for the restart: the home read "Connecting" for good, with the server answering,
 * until the app was force-stopped (device run 2, M1: after a phone restart the server boots slower than the wait).
 */
public class ReadyReconnect(private val scope: CoroutineScope, private val reconnect: suspend () -> Unit) {
    /** The server answered a start: reconnect once the start and whatever asked for it have let go. */
    public fun announce(): Job = scope.launch { reconnect() }
}
