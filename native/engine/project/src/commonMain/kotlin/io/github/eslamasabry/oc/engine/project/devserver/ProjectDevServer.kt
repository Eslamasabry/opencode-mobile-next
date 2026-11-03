package io.github.eslamasabry.oc.engine.project.devserver

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A dev server to preview: the loopback port it answers on. */
public data class DevPreviewPort(val port: Int)

/** A saved development service and whether its own shell runs now (only where the server has development services). */
public data class SavedServiceRun(val url: String, val running: Boolean)

/**
 * Whether the app really knows a dev server is running for the connected project (port of
 * lib/state/project_dev_server.dart): the Terminal's header line. Two signals count, and only these: a saved service
 * whose own shell runs, at the port of its saved address; and a program in the built-in Linux, started inside this
 * project, that holds a socket. Ports a shell printed are never used.
 */
public object ProjectDevServerLook {
    private val PORT = Regex("^[A-Za-z][A-Za-z0-9+.-]*://(?:[^/@?#]*@)?(?:\\[[^]]*]|[^/:?#]*):(\\d+)(?:[/?#]|$)")

    /** The port of a saved address (`http://localhost:5173/`), or null when it names none. */
    public fun portOf(url: String): Int? = PORT.find(url.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..65535 }

    /**
     * One look at both signals: the first saved service that runs wins, then the first program listening in the
     * project. [saved] and [listening] (the phone's listening-ports script output) are null where they do not exist.
     */
    public suspend fun lookFor(
        directory: String,
        saved: (suspend () -> Outcome<List<SavedServiceRun>>)? = null,
        listening: (suspend () -> Outcome<String>)? = null,
    ): Outcome<DevPreviewPort?> {
        var unknown: Outcome.Failed? = null
        if (saved != null) {
            when (val runs = saved()) {
                // The saved services could not be read: the listening programs may still answer.
                is Outcome.Failed -> unknown = runs
                is Outcome.Ok -> runs.value.firstOrNull { it.running && portOf(it.url) != null }?.let { return DevPreviewPort(portOf(it.url)!!).ok() }
            }
        }
        if (listening != null) {
            when (val output = listening()) {
                is Outcome.Failed -> return output
                is Outcome.Ok ->
                    for (server in ListeningPorts.parse(output.value)) {
                        if (!server.hasProcess) continue
                        val cwd = server.cwd ?: continue
                        if (cwd != directory && !cwd.startsWith("$directory/")) continue
                        server.ports.firstOrNull()?.let { return DevPreviewPort(it).ok() }
                    }
            }
        }
        return unknown ?: null.ok()
    }
}

/** The watch over [look]: says a found server once, and a look that fails keeps what was known. */
public class ProjectDevServer(
    private val scope: CoroutineScope,
    private val look: suspend () -> Outcome<DevPreviewPort?>,
    private val every: Duration = 15.seconds,
) {
    private val _running = MutableStateFlow<DevPreviewPort?>(null)
    private var looking = false
    private var timer: Job? = null

    /** The dev server to preview, or null when none is known to run. */
    public val running: StateFlow<DevPreviewPort?> = _running.asStateFlow()

    public suspend fun refresh() {
        if (looking) return
        looking = true
        try {
            when (val found = look()) {
                is Outcome.Ok -> _running.value = found.value
                is Outcome.Failed -> Unit // A look that fails says nothing new.
            }
        } finally {
            looking = false
        }
    }

    /** Looks now and then every [every] until [pause]. */
    public fun resume() {
        if (timer != null) return
        timer =
            scope.launch {
                while (isActive) {
                    refresh()
                    delay(every)
                }
            }
    }

    public fun pause() {
        timer?.cancel()
        timer = null
    }
}
