package io.github.eslamasabry.oc.engine.project.terminal

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public enum class LocalShellState { Starting, Running, Exited, Failed }

/** What a shell's screen shows about it, as one value. [revision] grows with every change, output included. */
public data class LocalShellStatus(
    val state: LocalShellState,
    val id: Int?,
    val pid: Int,
    val exitCode: Int?,
    val failure: Failure?,
    val revision: Long,
)

/**
 * One shell and its screen contents (Flutter `LocalShell`). The [screen] lives as long as the shell, not the screen
 * that shows it, so leaving the terminal and coming back finds the same scrollback.
 */
public class LocalShell internal constructor(
    private val port: LocalTerminalPort,
    private val scope: CoroutineScope,
    /** "Shell 1", "Shell 2": the order they were opened in. */
    public val number: Int,
    id: Int? = null,
    pid: Int = 0,
    private val resizeSettle: Duration,
) {
    public val screen: TerminalScreen = TerminalScreen(maxLines = SCROLLBACK_LINES)

    public var id: Int? = id
        private set
    public var pid: Int = pid
        private set
    public var exitCode: Int? = null
        private set
    public var failure: Failure? = null
        private set

    public val state: LocalShellState
        get() =
            when {
                failure != null -> LocalShellState.Failed
                exitCode != null -> LocalShellState.Exited
                id == null -> LocalShellState.Starting
                else -> LocalShellState.Running
            }

    public val running: Boolean get() = state == LocalShellState.Running

    private val mutableStatus = MutableStateFlow(snapshot(0))
    public val status: StateFlow<LocalShellStatus> = mutableStatus.asStateFlow()

    /** Applied to everything typed before it is sent: the key bar's sticky Ctrl and Alt. Null sends text as typed. */
    public var inputFilter: ((String) -> String)? = null

    private var disposed = false
    private var quiesced = false
    private var resizeJob: Job? = null
    private var sentSize: Pair<Int, Int>? = null
    private val decoder = Utf8StreamDecoder()

    // One writer per shell keeps bytes in the order typed.
    private val writes = Channel<ByteArray>(Channel.UNLIMITED)
    private val writer: Job =
        scope.launch {
            for (bytes in writes) {
                val target = this@LocalShell.id ?: continue
                port.write(target, bytes) // A failed write is the shell's problem to show; nothing to retry.
            }
        }

    private fun snapshot(revision: Long): LocalShellStatus =
        LocalShellStatus(state, id, pid, exitCode, failure, revision)

    private fun changed() {
        mutableStatus.value = snapshot(mutableStatus.value.revision + 1)
    }

    /** Typed text, a pasted string or a key's text: sent as UTF-8 while the shell runs, never before or after. */
    public fun textInput(text: String) {
        if (quiesced || id == null || !running || text.isEmpty()) return
        val filtered = inputFilter?.invoke(text) ?: text
        if (filtered.isEmpty()) return
        writes.trySend(filtered.encodeToByteArray())
    }

    /** A key-bar key with the [keys]' latched modifiers (released), honouring the program's cursor-key mode. */
    public fun pressKey(key: TerminalBarKey, keys: TerminalKeyBarController) {
        textInput(keys.press(key, applicationCursorKeys = screen.cursorKeysMode))
    }

    /** The view's new size. The keyboard sliding in resizes on every frame: the shell gets the size once it settles. */
    public fun resize(cols: Int, rows: Int) {
        screen.resize(cols, rows)
        if (quiesced) return
        resizeJob?.cancel()
        resizeJob =
            scope.launch {
                delay(resizeSettle)
                sendSize()
            }
    }

    private suspend fun sendSize() {
        val target = id
        if (quiesced || target == null || !running) return
        val size = screen.rows to screen.cols
        if (size == sentSize) return
        sentSize = size
        port.resize(target, rows = size.first, cols = size.second)
    }

    internal fun output(data: ByteArray) {
        if (disposed) return
        screen.write(decoder.decode(data))
        changed()
    }

    internal suspend fun started(info: LocalShellInfo, size: Pair<Int, Int>) {
        if (disposed) return
        id = info.id
        pid = info.pid
        sentSize = size
        changed()
        // The screen may have laid out while the shell started.
        sendSize()
    }

    internal fun failed(failure: Failure) {
        if (disposed) return
        this.failure = failure
        changed()
    }

    internal fun exited(code: Int) {
        if (disposed || exitCode != null) return
        exitCode = code
        changed()
    }

    internal fun dispose() {
        if (disposed) return
        disposed = true
        quiesced = true
        inputFilter = null
        resizeJob?.cancel()
        writes.close()
        writer.cancel()
    }

    public companion object {
        /** Lines kept above the screen. */
        public const val SCROLLBACK_LINES: Int = 10_000
    }
}

/**
 * The shells of the app's built-in Ubuntu, for as long as the app runs (Flutter `LocalTerminalSessions`). Android owns
 * the processes; this holds what the screen draws, and [load] adopts shells a previous run left behind. Every output
 * chunk is acknowledged after a turn of [scope] (so a frame due now draws first), even for a shell this side does not
 * know, so Android never holds output back. [scope] is the engine's serial lane; agent sign-ins are not handled here.
 */
public class LocalTerminalSessions(
    private val port: LocalTerminalPort,
    private val scope: CoroutineScope,
    private val resizeSettle: Duration = RESIZE_SETTLE,
) {
    private val list = MutableStateFlow<List<LocalShell>>(emptyList())

    /** In the order they were opened. */
    public val shells: StateFlow<List<LocalShell>> = list.asStateFlow()

    private val processCount = MutableStateFlow<Int?>(null)

    /** Processes running as this app at the last [load] or [refreshProcesses]. */
    public val processes: StateFlow<Int?> = processCount.asStateFlow()

    public var loaded: Boolean = false
        private set

    /** The shell the screen showed last, so coming back shows it again. */
    public var current: LocalShell? = null

    private var disposed = false
    private var subscription: Job? = null
    private var loading: Deferred<Unit>? = null
    private var nextNumber = 1

    private fun listen() {
        if (disposed || subscription != null) return
        subscription = scope.launch(start = CoroutineStart.UNDISPATCHED) { port.events.collect { event(it) } }
    }

    private fun event(event: LocalTerminalEvent) {
        if (disposed) return
        val shell = shellWithId(event.id)
        when (event) {
            is LocalTerminalEvent.Output -> {
                shell?.output(event.data)
                scope.launch {
                    yield()
                    port.ack(event.id)
                }
            }
            is LocalTerminalEvent.Exit -> shell?.exited(event.code)
            is LocalTerminalEvent.OpenUrl -> Unit // Only sign-ins open pages; plain shells ignore it.
        }
    }

    private fun shellWithId(id: Int): LocalShell? = list.value.firstOrNull { it.id == id }

    /** Reads the shells Android runs, once; shells this side does not know yet come back with their recent output. */
    public suspend fun load() {
        val running = loading ?: scope.async { readExisting() }.also { loading = it }
        running.await()
    }

    private suspend fun readExisting() {
        listen()
        val listing = port.list()
        if (listing is Outcome.Ok) {
            processCount.value = listing.value.processes
            for (info in listing.value.shells) {
                if (info.signIn) {
                    scope.launch { port.remove(info.id) }
                    continue
                }
                if (shellWithId(info.id) != null) continue
                val shell = newShell(id = info.id, pid = info.pid)
                val recent = (port.attach(info.id) as? Outcome.Ok)?.value
                if (recent != null && recent.isNotEmpty()) shell.output(recent)
                if (!info.running) shell.exited(info.exitCode ?: -1)
                list.value = list.value + shell
            }
        }
        // No listing: no shells to adopt; starting one reports its own failure.
        loaded = true
    }

    public suspend fun refreshProcesses() {
        val listing = port.list()
        if (listing is Outcome.Ok) processCount.value = listing.value.processes
    }

    private fun newShell(id: Int? = null, pid: Int = 0): LocalShell =
        LocalShell(port, scope, nextNumber++, id = id, pid = pid, resizeSettle = resizeSettle)

    /** Opens a new shell, listed at once as starting; its state says when it runs or why it could not start. */
    public fun startShell(rows: Int = 24, cols: Int = 80): LocalShell {
        listen()
        val shell = newShell()
        shell.screen.resize(cols, rows)
        list.value = list.value + shell
        scope.launch { begin(shell) }
        return shell
    }

    private suspend fun begin(shell: LocalShell) {
        val size = shell.screen.rows to shell.screen.cols
        when (val started = port.start(rows = size.first, cols = size.second)) {
            is Outcome.Ok -> {
                shell.started(started.value, size)
                scope.launch { refreshProcesses() }
            }
            is Outcome.Failed -> shell.failed(started.failure)
        }
    }

    /** Stops [shell]; it stays listed as ended until [remove]. */
    public suspend fun stop(shell: LocalShell) {
        val id = shell.id ?: return
        port.stop(id)
    }

    /** Stops and forgets [shell]. */
    public suspend fun remove(shell: LocalShell) {
        forget(shell)
        shell.id?.let { port.remove(it) }
        refreshProcesses()
    }

    private fun forget(shell: LocalShell) {
        list.value = list.value - shell
        if (current === shell) current = null
        shell.dispose()
    }

    /** Replaces an ended [shell] with a new one of the same size. */
    public fun restart(shell: LocalShell): LocalShell {
        val rows = shell.screen.rows
        val cols = shell.screen.cols
        val id = shell.id
        forget(shell)
        scope.launch {
            if (id != null) port.remove(id)
            refreshProcesses()
        }
        return startShell(rows = rows, cols = cols)
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        subscription?.cancel()
        list.value.forEach { it.dispose() }
    }

    public companion object {
        /** How long a view must keep its size before the shell is told. */
        public val RESIZE_SETTLE: Duration = 80.milliseconds
    }
}
