@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.eslamasabry.oc.core.net.stream

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.model.NetworkState
import io.github.eslamasabry.oc.core.model.NetworkStatus
import io.github.eslamasabry.oc.core.model.NetworkState.Online
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

/** A wall clock that reads the test scheduler's virtual time. */
internal class VirtualClock(private val scope: TestScope) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(scope.testScheduler.currentTime)
}

internal class FakeNetworkStatus(online: Online = Online.Yes, id: Long = 1) : NetworkStatus {
    private val flow = MutableStateFlow(NetworkState(online, defaultNetworkId = id))
    override val state: StateFlow<NetworkState> = flow

    fun set(online: Online, id: Long? = flow.value.defaultNetworkId) {
        flow.value = NetworkState(online, defaultNetworkId = id)
    }
}

/** What one connection attempt does. */
internal sealed interface Script {
    /** The request fails before a body (refused, 5xx, timeout). */
    data class Fail(val failure: Failure = Failure.Network(NetworkWhy.Refused), val stop: Boolean = false) : Script

    /** A 200 stream: each step is a chunk after a delay; then [end]. */
    data class Body(val steps: List<Pair<Long, String>>, val end: End) : Script

    enum class End { Clean, Error, Hang }
}

/** Plays scripts in order; after the list runs out, every attempt fails. Records the virtual time of each open. */
internal class FakeConnector(private val scope: TestScope, scripts: List<Script> = emptyList()) : SseConnector {
    private val queue = ArrayDeque(scripts)
    val opens = mutableListOf<Long>()
    val lastEventIds = mutableListOf<String?>()
    var fallback: Script = Script.Fail()

    fun add(vararg scripts: Script) {
        queue.addAll(scripts)
    }

    override suspend fun connect(lastEventId: String?, session: suspend (SseBody) -> Unit): SseConnectResult {
        opens += scope.testScheduler.currentTime
        lastEventIds += lastEventId
        return when (val script = queue.removeFirstOrNull() ?: fallback) {
            is Script.Fail -> SseConnectResult.Failed(script.failure, script.stop)
            is Script.Body -> {
                session(ScriptedBody(script))
                SseConnectResult.Ended
            }
        }
    }
}

private class ScriptedBody(private val script: Script.Body) : SseBody {
    private var index = 0
    private var pending: ByteArray? = null
    private var pendingAt = 0
    private val never = Channel<Unit>()

    override suspend fun read(buffer: ByteArray): Int {
        val chunk = pending ?: run {
            if (index == script.steps.size) {
                return when (script.end) {
                    Script.End.Clean -> -1
                    Script.End.Error -> throw IOException("reset")
                    Script.End.Hang -> {
                        never.receive()
                        -1
                    }
                }
            }
            val (wait, text) = script.steps[index++]
            if (wait > 0) delay(wait)
            text.encodeToByteArray().also { pending = it; pendingAt = 0 }
        }
        val n = minOf(buffer.size, chunk.size - pendingAt)
        chunk.copyInto(buffer, 0, pendingAt, pendingAt + n)
        pendingAt += n
        if (pendingAt == chunk.size) pending = null
        return n
    }
}
