package io.github.eslamasabry.opencode_mobile.proof

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import io.github.eslamasabry.oc.core.common.CommonBindings
import io.github.eslamasabry.oc.core.common.ConnectionCoroutineScope
import io.github.eslamasabry.oc.core.domain.ServerGateway
import java.lang.reflect.Proxy
import io.github.eslamasabry.oc.engine.transcript.TranscriptEngine
import io.github.eslamasabry.oc.engine.transcript.TranscriptHandle
import io.github.eslamasabry.oc.engine.transcript.state.GatewayEvent
import io.github.eslamasabry.oc.engine.transcript.state.NoticeKind
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscript
import io.github.eslamasabry.oc.feature.chat.ui.ChatTranscriptWords
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTextRole
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.AndroidKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.stream.KitMotionLevel
import io.github.eslamasabry.oc.ui.kitchat.stream.rememberRevealRegistry
import io.github.eslamasabry.oc.ui.kitchat.transcript.rememberTranscriptListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object ProofWords : ChatTranscriptWords {
    override val jumpToLatest = "Jump to latest"

    override fun workLine(calls: Int, failed: Int, state: WorkState) = "$calls steps"

    override fun notice(kind: NoticeKind) = "Instructions updated"

    override fun footer(failed: Boolean) = "Copy"

    override fun thought(firstLine: String) = firstLine
}

/**
 * Slice-0 P3 on a device (benchmark build type only). `scenario=history5000`: the recorded 5,004-part OC2 stream; every
 * event older than the last [LIVE_MS] is fed at once (history), then on Start the last [LIVE_MS] of the recording is
 * replayed at its recorded times into the live transcript. `scenario=stream6000`: the 6,000-word reply with the open
 * 400-line fence, replayed at its recorded times. The Macrobenchmark taps Start and waits for "Done".
 */
class StreamProofActivity : ComponentActivity() {
    private val dispatchers = CommonBindings.dispatchers()
    private val work = CoroutineScope(SupervisorJob() + dispatchers.default)
    private val status = mutableStateOf("Loading")
    private var live: List<Pair<Long, GatewayEvent>> = emptyList()
    private lateinit var handle: TranscriptHandle

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scenario = intent.getStringExtra("scenario") ?: "history5000"
        val engine =
            TranscriptEngine(
                gateway = noGateway(),
                dispatchers = dispatchers,
                clock = CommonBindings.clock(),
                scope = ConnectionCoroutineScope(SupervisorJob() + dispatchers.default),
            )
        val platform = AndroidKitPlatform(window.decorView)
        work.launch { prepare(scenario, engine) }
        setContent {
            CompositionLocalProvider(LocalKitPlatform provides platform) {
                KitTheme {
                    val text by status
                    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                        KitText(text, KitTextRole.Supporting)
                        KitButton(label = "Start", onClick = ::start)
                        if (::handle.isInitialized) {
                            val state by handle.state.collectAsState()
                            Box(Modifier.weight(1f)) {
                                ChatTranscript(
                                    state, ProofWords, rememberTranscriptListState(), rememberRevealRegistry(),
                                    KitMotionLevel.Full,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun prepare(scenario: String, engine: TranscriptEngine) {
        val t0 = SystemClock.uptimeMillis()
        when (scenario) {
            "stream6000" -> {
                val events = Stream6000.events()
                live = events.map { it.ms to it.event }
                handle = engine.open(Stream6000.SESSION)
            }
            else -> {
                RecordedStream.recording = assets.open("oc2/events.sse").bufferedReader().readText()
                val stream = RecordedStream.build()
                val timed = Oc2Sse.adapt(stream.sse)
                val session = timed.firstNotNullOf { (it.event as? GatewayEvent.PartUpserted)?.part?.sessionId }
                val split = timed.last().atMs - LIVE_MS
                handle = engine.open(session)
                timed.filter { it.atMs < split }.forEach { handle.submit(it.event) }
                handle.awaitIdle()
                live = timed.filter { it.atMs >= split }.map { it.atMs to it.event }
                RecordedStream.recording = ""
                Runtime.getRuntime().gc() // drop the fixture text and JSON before anything is measured
                Log.i(TAG, "history parts=${stream.shape.parts} events=${timed.size} live=${live.size} rows=${handle.state.value.rows.size}")
            }
        }
        Log.i(TAG, "prepared $scenario in ${SystemClock.uptimeMillis() - t0} ms")
        status.value = "Ready"
    }

    private fun start() {
        if (status.value != "Ready") return
        status.value = "Streaming"
        work.launch {
            val first = live.first().first
            val start = SystemClock.uptimeMillis()
            for ((at, event) in live) {
                val wait = start + (at - first) - SystemClock.uptimeMillis()
                if (wait > 0) delay(wait)
                handle.submit(event)
            }
            handle.awaitIdle()
            delay(1_000) // let the reveal and fades finish
            Log.i(TAG, "done publishes=${handle.counters.publishes} rows=${handle.state.value.rows.size}")
            status.value = "Done"
        }
    }

    override fun onDestroy() {
        work.cancel()
        super.onDestroy()
    }

    /** The P3 handle never reads through the gateway (history is fed by the caller); any call is a proof bug. */
    private fun noGateway(): ServerGateway =
        Proxy.newProxyInstance(classLoader, arrayOf(ServerGateway::class.java)) { _, m, _ -> error("gateway.${m.name} unused") }
            as ServerGateway

    private companion object {
        const val TAG = "P3Proof"
        const val LIVE_MS = 20_000L
    }
}
