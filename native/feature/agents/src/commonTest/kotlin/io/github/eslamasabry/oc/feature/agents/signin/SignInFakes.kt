package io.github.eslamasabry.oc.feature.agents.signin

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * A single-threaded, run-to-idle coroutine loop for feature tests. Konsist K1 scans feature test files too and allows
 * only `launch`, `CoroutineScope` and `flow` from kotlinx.coroutines, so this stands in for `runTest`.
 */
class TestLoop : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
    private val queue = ArrayDeque<() -> Unit>()

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> =
        object : Continuation<T> {
            override val context: CoroutineContext get() = continuation.context

            override fun resumeWith(result: Result<T>) {
                queue.addLast { continuation.resumeWith(result) }
            }
        }

    val scope: CoroutineScope = CoroutineScope(this)

    /** Runs queued work until nothing is left (Dart `pumpEventQueue`). */
    fun idle() {
        var guard = 0
        while (queue.isNotEmpty()) {
            queue.removeFirst()()
            check(++guard < 100_000) { "the loop never went idle" }
        }
    }

    /** Lets every other queued task run first (from inside a test coroutine). */
    suspend fun pump(times: Int = 50) {
        repeat(times) { suspendCoroutine { c -> queue.addLast { c.resume(Unit) } } }
    }

    /** Runs [body] on the loop to completion and rethrows what it threw. */
    fun run(body: suspend CoroutineScope.() -> Unit) {
        var error: Throwable? = null
        var done = false
        scope.launch {
            try {
                body()
            } catch (e: Throwable) {
                error = e
            } finally {
                done = true
            }
        }
        idle()
        error?.let { throw it }
        check(done) { "the test body is still suspended" }
    }
}

class FakeLease(pending: Boolean = false) : SignInForegroundLease {
    val admission = MutableStateFlow<SignInAdmission?>(if (pending) null else SignInAdmission.Ready)
    val losses = MutableSharedFlow<String>(extraBufferCapacity = 8)
    var held = true
    var releases = 0

    override suspend fun admitted(): SignInAdmission = admission.filterNotNull().first()

    override val active: Boolean get() = held && admission.value == SignInAdmission.Ready

    override val lost: Flow<String> get() = losses

    override suspend fun release() {
        releases++
        held = false
    }
}

class FakeForeground(val lease: FakeLease) : SignInForeground {
    var reservations = 0

    override fun reserve(): SignInForegroundLease {
        reservations++
        return lease
    }
}

class FakeTerminal(val id: Int, private val host: FakeTerminalHost) : TerminalModel {
    override val view: PlatformViewHandle = object : PlatformViewHandle { override val native: Any = "view-$id" }
    override val title = MutableStateFlow("claude")
    override val transcript = MutableStateFlow("")
    val eventsFlow = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = 16)
    override val events: Flow<TerminalEvent> get() = eventsFlow
    val written = mutableListOf<String>()

    override suspend fun write(bytes: ByteArray) {
        written += bytes.decodeToString()
    }

    override suspend fun resize(cols: Int, rows: Int) {
        host.calls += "resize $id $cols x $rows"
    }

    override suspend fun close(): Outcome<Unit> {
        host.calls += "drain-begins $id"
        host.closeGate.filterNotNull().first { it }
        if (host.closeFails) return Failure.Runtime(io.github.eslamasabry.oc.core.common.RuntimeProblem.RemovalFailed).failed()
        host.calls += "remove $id"
        return Unit.ok()
    }
}

class FakeTerminalHost : TerminalHost {
    val calls = mutableListOf<String>()
    val targets = mutableListOf<TerminalTarget>()
    val terminals = mutableListOf<FakeTerminal>()

    /** null = answer at once; false = hold the native start until set to true. */
    val startGate = MutableStateFlow<Boolean?>(null)

    /** Set to make the native start fail with private CLI text in details. */
    var startFailure: String? = null
    val closeGate = MutableStateFlow<Boolean?>(true)
    var closeFails = false

    override suspend fun open(target: TerminalTarget): Outcome<TerminalModel> {
        targets += target
        calls += "sign-in ${(target as TerminalTarget.SignIn).command.program}"
        if (startGate.value != null) startGate.first { it == true }
        startFailure?.let { return Failure.Device(Redacted.shape(IllegalStateException(it))).failed() }
        val terminal = FakeTerminal(terminals.size + 1, this)
        terminals += terminal
        return terminal.ok()
    }
}

class FakeAgents(
    private val plans: Map<String, SignInPlan> =
        mapOf(
            "claude" to SignInPlan.Terminal("Claude Code", usesClaudeAccount = true),
            "codex" to SignInPlan.Terminal("Codex", usesClaudeAccount = false),
            "qwen" to SignInPlan.ProviderKeyOnHost("Qwen Code"),
        ),
) : SignInAgents {
    var signedIn = false
    var confirms = 0
    val commands = mutableListOf<String>()

    override fun plan(agent: AgentRef): SignInPlan = plans[agent.id] ?: SignInPlan.NotAvailable(agent.id)

    override fun command(owner: ProfileId, agent: AgentRef): Outcome<SignInCommand> {
        commands += "${owner.raw}/${agent.id}"
        return when (agent.id) {
            "claude" -> CLAUDE.ok()
            "codex" -> SignInCommand("codex", listOf("login", "--device-auth"), home = "/home/oc/.oc-profiles/p1").ok()
            else -> Failure.Unavailable(io.github.eslamasabry.oc.core.common.FeatureId("agentSignIn")).failed()
        }
    }

    override suspend fun confirmSignedIn(owner: ProfileId, agent: AgentRef): Boolean {
        confirms++
        return signedIn
    }

    override fun read(transcript: String): SignInFound {
        val page = Regex("https://\\S+").findAll(transcript).lastOrNull()?.value
        val code = Regex("\\b[A-Z0-9]{4}-[A-Z0-9]{4,5}\\b").find(transcript)?.value
        return SignInFound(page, code)
    }

    companion object {
        val CLAUDE = SignInCommand("claude", listOf("auth", "login", "--claudeai"), home = "/home/oc/.oc-profiles/p1")
    }
}

class RecordingLinks : SignInLinks {
    val opened = mutableListOf<String>()

    override suspend fun open(url: String): Outcome<Unit> {
        opened += url
        return Unit.ok()
    }
}

class RecordingClipboard : SignInClipboard {
    val copied = mutableListOf<String>()

    override fun copySensitive(text: String) {
        copied += text
    }
}

val StateFlow<SignInSessionState>.failure: SignInProblem? get() = (value as? SignInSessionState.Failed)?.problem
