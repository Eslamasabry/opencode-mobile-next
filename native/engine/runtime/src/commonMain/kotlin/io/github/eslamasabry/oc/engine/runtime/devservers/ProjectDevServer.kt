package io.github.eslamasabry.oc.engine.runtime.devservers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStatus
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A saved running shell's configured port or a real listener inside this project. Printed ports
 * never count.
 */
public class ProjectDevServer(
    private val directory: String,
    private val saved: DevelopmentServices?,
    private val listening: DetectedDevServers?,
    private val isCurrent: () -> Boolean,
) {
    private val lane = Mutex()
    private val mutable = MutableStateFlow<Int?>(null)
    public val running: StateFlow<Int?> = mutable.asStateFlow()
    private var closed = false

    public suspend fun refresh(): Outcome<Unit> = lane.withLock {
        if (closed || !isCurrent()) {
            mutable.value = null
            return@withLock Outcome.Ok(Unit)
        }
        var failure: Failure? = null
        var found: Int? = null
        if (saved != null) {
            saved.reload()
            when (val result = saved.refresh()) {
                is Outcome.Failed -> failure = result.failure
                is Outcome.Ok ->
                    found =
                        saved.state.value.services.firstNotNullOfOrNull { service ->
                            if (saved.status(service) == DevelopmentServiceStatus.Running)
                                configuredPort(service.url)
                            else null
                        }
            }
        }
        if (found == null && listening != null) {
            listening.refresh()
            listening.state.value.error?.let { failure = it }
            found =
                listening.servers
                    .firstOrNull { server ->
                        server.source == DetectedSource.Process &&
                            server.cwd?.let { cwd ->
                                cwd == directory || cwd.startsWith("${directory.trimEnd('/')}/")
                            } == true &&
                            server.firstPort != null
                    }
                    ?.firstPort
        }
        if (closed || !isCurrent()) {
            mutable.value = null
            return@withLock Outcome.Ok(Unit)
        }
        if (found != null || failure == null) mutable.value = found
        failure?.let { Outcome.Failed(it) } ?: Outcome.Ok(Unit)
    }

    public val available: Boolean
        get() = !closed && isCurrent()

    public fun close() {
        closed = true
        saved?.close()
        listening?.close()
    }

    private fun configuredPort(url: String): Int? =
        Regex("^https?://(?:\\[[^]]+]|[^/:?#]+):([0-9]+)(?:[/?#]|$)")
            .find(url)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
}
