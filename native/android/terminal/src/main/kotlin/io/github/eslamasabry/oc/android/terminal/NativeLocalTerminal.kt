package io.github.eslamasabry.oc.android.terminal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.PlatformViewHandle
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.project.terminal.LocalShell
import io.github.eslamasabry.oc.engine.project.terminal.LocalTerminalInputPort
import io.github.eslamasabry.oc.engine.project.terminal.LocalTerminalPort
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKey
import io.github.eslamasabry.oc.engine.project.terminal.TerminalKeys
import java.io.File
import java.util.concurrent.FutureTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Launch facts arrive from the app graph; this module never depends on the Linux implementation.
 */
data class TerminalLaunch(
    val command: String,
    val cwd: String,
    val argv: List<String>,
    val environment: List<String>,
    val confined: Boolean,
) {
    override fun toString(): String = "TerminalLaunch(confined=$confined)"
}

class TerminalLinuxMissing : IllegalStateException()

/**
 * Real Termux PTYs and emulators, held for the lifetime of the app. Termux's bounded I/O queues
 * acknowledge every consumed byte, decode split UTF-8 and keep wide cells whole. The emulator,
 * including selection after erase, stays with its PTY when the screen goes away; no output is
 * replayed into a second emulator on re-entry.
 */
class NativeLocalTerminal(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val scope: AppCoroutineScope,
    private val launchShell: () -> TerminalLaunch,
    private val stopTree: (Int, Long) -> Unit,
) : LocalTerminalPort, LocalTerminalInputPort {
    private val state = MutableStateFlow<List<LocalShell>>(emptyList())
    override val shells: StateFlow<List<LocalShell>> = state.asStateFlow()
    private val lane = Mutex()
    private val inputHandler = Handler(Looper.getMainLooper())
    private val sessions = linkedMapOf<Int, Held>()
    private var nextId = 1
    private var publishing: Job? = null

    private inner class Held(val id: Int, val number: Int, val confined: Boolean) {
        lateinit var session: TerminalSession
        lateinit var view: TerminalView
        lateinit var host: SettledTerminalHost
        var ctrl = false
        var alt = false
        var startTicks: Long? = null
        val pendingInput = StringBuilder()
        val flushInput = Runnable { flush(this) }

        val handle =
            object : PlatformViewHandle {
                override val native: Any
                    get() = host
            }

        fun snapshot(): LocalShell =
            LocalShell(
                id,
                number,
                session.pid,
                session.isRunning,
                if (session.isRunning) null else session.exitStatus,
                session.emulator?.screen?.transcriptText?.takeLast(512 * 1024).orEmpty(),
                handle,
                control = ctrl,
                confined = confined,
                rows = session.emulator?.mRows ?: 24,
                cols = session.emulator?.mColumns ?: 80,
                alt = alt,
            )

        fun finishIfCreated() {
            if (::session.isInitialized) session.finishIfRunning()
        }
    }

    override suspend fun start(rows: Int, cols: Int): Outcome<Int> = lane.withLock {
        if (rows !in 1..1000 || cols !in 1..1000) return@withLock failed()
        // Guest preparation reads and writes the Linux tree: keep it off the main thread.
        val facts = withContext(dispatchers.io) { launchFacts() }
        withContext(dispatchers.main) { startWith(rows, cols, facts) }
    }

    /** The launch facts, `null` when Linux is missing, [Unit] when preparation failed. */
    private fun launchFacts(): Any? =
        try {
            launchShell()
        } catch (_: TerminalLinuxMissing) {
            null
        } catch (_: Exception) {
            Unit
        }

    private fun startWith(rows: Int, cols: Int, facts: Any?): Outcome<Int> =
        when (facts) {
            null -> Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
            is TerminalLaunch ->
                if (rows !in 1..1000 || cols !in 1..1000) failed() else startNow(rows, cols, facts)
            else -> failed()
        }

    private fun startNow(rows: Int, cols: Int, facts: TerminalLaunch): Outcome<Int> {
        val id = nextId++
        val held = Held(id, id, facts.confined)
        val client =
            object : QuietTerminalClient() {
                override fun onTextChanged(changedSession: TerminalSession) {
                    held.view.onScreenUpdated()
                    schedulePublish()
                }

                override fun onSessionFinished(finishedSession: TerminalSession) {
                    discardInput(held.id)
                    publish()
                }

                override fun onColorsChanged(session: TerminalSession) {
                    held.view.onScreenUpdated()
                }

                override fun onEmulatorSet() {
                    schedulePublish()
                }

                override fun readControlKey(): Boolean {
                    flush(held)
                    return held.ctrl.also {
                        held.ctrl = false
                        schedulePublish()
                    }
                }

                override fun readAltKey(): Boolean {
                    flush(held)
                    return held.alt.also {
                        held.alt = false
                        schedulePublish()
                    }
                }

                override fun onKeyDown(
                    keyCode: Int,
                    e: KeyEvent,
                    session: TerminalSession,
                ): Boolean {
                    flush(held)
                    return false
                }

                override fun onCodePoint(
                    codePoint: Int,
                    ctrlDown: Boolean,
                    session: TerminalSession,
                ): Boolean {
                    flush(held)
                    return false
                }

                override fun onSingleTapUp(e: MotionEvent) {
                    if (held.host.openLink(e)) return
                    held.view.requestFocus()
                    (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showSoftInput(held.view, InputMethodManager.SHOW_IMPLICIT)
                }

                override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
                    copyTerminalText(session, text, context)
                }
            }
        try {
            held.session =
                TerminalSession(
                    facts.command,
                    facts.cwd,
                    facts.argv.toTypedArray(),
                    facts.environment.toTypedArray(),
                    5000,
                    client,
                )
            held.view =
                TerminalView(context, null).apply {
                    setTerminalViewClient(client)
                    setTextSize((14 * resources.displayMetrics.scaledDensity).toInt())
                    attachSession(held.session)
                }
            held.host = SettledTerminalHost(context, held.view)
            sessions[id] = held
            held.session.updateSize(cols, rows, 0, 0)
            held.startTicks = startTicks(held.session.pid)
            if (held.session.pid <= 0) {
                sessions.remove(id)
                return failed()
            }
            publish()
            return Outcome.Ok(id)
        } catch (_: Exception) {
            sessions.remove(id)
            held.finishIfCreated()
            return failed()
        }
    }

    override suspend fun send(id: Int, text: String): Outcome<Unit> = onMain {
        val sent = enqueueText(id, text)
        sessions[id]?.let(::flush)
        sent
    }

    override fun enqueueText(id: Int, text: String): Outcome<Unit> {
        if (Looper.myLooper() != Looper.getMainLooper()) return failed()
        val held = sessions[id] ?: return failed()
        if (!held.session.isRunning || text.isEmpty()) return Outcome.Ok(Unit)
        val typed = TerminalKeys.text(text, held.ctrl, held.alt)
        held.ctrl = false
        held.alt = false
        return enqueue(held, typed)
    }

    override fun enqueueKey(id: Int, key: TerminalKey): Outcome<Unit> {
        if (Looper.myLooper() != Looper.getMainLooper()) return failed()
        val held = sessions[id] ?: return failed()
        if (!held.session.isRunning) return Outcome.Ok(Unit)
        when (key) {
            TerminalKey.Control -> held.ctrl = !held.ctrl
            TerminalKey.Alt -> held.alt = !held.alt
            else -> {
                val text =
                    TerminalKeys.bytes(
                        key,
                        held.ctrl,
                        held.alt,
                        held.session.emulator?.isCursorKeysApplicationMode == true,
                    )
                held.ctrl = false
                held.alt = false
                return enqueue(held, text)
            }
        }
        publish()
        return Outcome.Ok(Unit)
    }

    private fun enqueue(held: Held, text: String): Outcome<Unit> {
        if (held.pendingInput.length + text.length > 65_536) {
            discardInput(held.id)
            return failed()
        }
        held.pendingInput.append(text)
        inputHandler.removeCallbacks(held.flushInput)
        inputHandler.post(held.flushInput)
        publish()
        return Outcome.Ok(Unit)
    }

    /** Direct grid input drains earlier app input first, before Termux writes the native key. */
    private fun flush(held: Held) {
        inputHandler.removeCallbacks(held.flushInput)
        val bytes = held.pendingInput.toString().toByteArray(Charsets.UTF_8)
        held.pendingInput.clear()
        if (sessions[held.id] === held && held.session.isRunning && bytes.isNotEmpty())
            held.session.write(bytes, 0, bytes.size)
    }

    override fun discardInput(id: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            inputHandler.post { discardInput(id) }
            return
        }
        sessions[id]?.let { held ->
            inputHandler.removeCallbacks(held.flushInput)
            held.pendingInput.clear()
        }
    }

    override suspend fun resize(id: Int, rows: Int, cols: Int): Outcome<Unit> = onMain {
        val held = sessions[id] ?: return@onMain failed()
        if (rows !in 1..1000 || cols !in 1..1000) return@onMain failed()
        if (held.session.isRunning) held.session.updateSize(cols, rows, 0, 0)
        Outcome.Ok(Unit)
    }

    override suspend fun control(id: Int, enabled: Boolean): Outcome<Unit> = onMain {
        sessions[id]?.ctrl = enabled
        publish()
        Outcome.Ok(Unit)
    }

    override suspend fun stop(id: Int): Outcome<Unit> = lane.withLock { stopNow(id) }

    private suspend fun stopNow(id: Int): Outcome<Unit> {
        val held = withContext(dispatchers.main) { sessions[id] } ?: return Outcome.Ok(Unit)
        if (!held.session.isRunning) return Outcome.Ok(Unit)
        return try {
            val identity = held.startTicks ?: return failed()
            withContext(dispatchers.io) { stopTree(held.session.pid, identity) }
            withContext(dispatchers.main) { publish() }
            Outcome.Ok(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed()
        }
    }

    override suspend fun remove(id: Int): Outcome<Unit> = lane.withLock {
        val stopped = stopNow(id)
        if (stopped is Outcome.Failed) return@withLock stopped
        withContext(dispatchers.main) {
            val held = sessions[id]
            if (held?.session?.isRunning == true) return@withContext failed()
            discardInput(id)
            sessions.remove(id)
            publish()
            Outcome.Ok(Unit)
        }
    }

    override suspend fun restart(id: Int): Outcome<Int> = lane.withLock {
        val size =
            withContext(dispatchers.main) {
                val old = sessions[id] ?: return@withContext null
                if (old.session.isRunning) null
                else old.session.emulator.mRows to old.session.emulator.mColumns
            } ?: return@withLock failed()
        val facts = withContext(dispatchers.io) { launchFacts() }
        withContext(dispatchers.main) {
            val old = sessions[id] ?: return@withContext failed()
            if (old.session.isRunning) return@withContext failed()
            val restarted = startWith(size.first, size.second, facts)
            if (restarted is Outcome.Ok) {
                sessions.remove(id)
                publish()
            }
            restarted
        }
    }

    /** Synchronous read for BuiltinLinux's removal/idle safety checks. */
    fun runningSessions(): List<Triple<Int, Int, Boolean>> =
        shells.value
            .filter { it.running }
            .map { shell ->
                Triple(shell.id, shell.pid, shell.confined)
            }

    /** Runtime cleanup calls this synchronous bridge from its own background lane. */
    fun startFromBridge(rows: Int, cols: Int): Int {
        val facts = launchFacts()
        return onMainBlocking { startWith(rows, cols, facts) }
            .let { started ->
                when (started) {
                    is Outcome.Ok -> started.value
                    is Outcome.Failed -> error("terminal_start_failed")
                }
            }
    }

    fun stopFromBridge(id: Int) {
        val held = onMainBlocking { sessions[id] } ?: return
        if (held.session.isRunning)
            stopTree(
                held.session.pid,
                checkNotNull(held.startTicks) { "terminal_identity_missing" },
            )
    }

    fun removeFromBridge(id: Int) {
        stopFromBridge(id)
        onMainBlocking {
            check(sessions[id]?.session?.isRunning != true) { "terminal_stop_failed" }
            discardInput(id)
            sessions.remove(id)
            publish()
        }
    }

    private fun <T> onMainBlocking(action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return action()
        val task = FutureTask(action)
        Handler(Looper.getMainLooper()).post(task)
        return task.get()
    }

    private suspend fun <T> onMain(action: () -> Outcome<T>): Outcome<T> = lane.withLock {
        withContext(dispatchers.main) { action() }
    }

    private fun publish() {
        state.value = sessions.values.map { it.snapshot() }
    }

    private fun schedulePublish() {
        if (publishing?.isActive == true) return
        publishing =
            scope.launch(dispatchers.main) {
                delay(50)
                publish()
            }
    }

    private fun failed(): Outcome.Failed =
        Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable))

    companion object {
        /** Kernel process identity, independent of the program name or command line. */
        fun startTicks(pid: Int): Long? =
            try {
                File("/proc/$pid/stat")
                    .readText()
                    .substringAfterLast(')')
                    .trim()
                    .split(Regex("\\s+"))[19]
                    .toLongOrNull()
            } catch (_: Exception) {
                null
            }
    }
}

/** Keyboard animation can deliver many sizes; only its settled size reaches the PTY. */
private class SettledTerminalHost(context: Context, terminal: TerminalView) :
    TerminalSurfaceHost(context, terminal) {
    private val main = Handler(Looper.getMainLooper())
    private var wantedWidth = 0
    private var wantedHeight = 0
    private val settled = Runnable {
        terminal.measure(
            MeasureSpec.makeMeasureSpec(wantedWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(wantedHeight, MeasureSpec.EXACTLY),
        )
        terminal.layout(0, 0, wantedWidth, wantedHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val height = bottom - top
        if (wantedWidth == width && wantedHeight == height) return
        wantedWidth = width
        wantedHeight = height
        main.removeCallbacks(settled)
        main.postDelayed(settled, 80)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(settled)
        main.postDelayed(settled, 80)
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(settled)
        super.onDetachedFromWindow()
    }
}

/** Termux diagnostics may contain shell text. None is copied into application logs. */
private open class QuietTerminalClient : TerminalSessionClient, TerminalViewClient {
    override fun onTextChanged(changedSession: TerminalSession) = Unit

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) = Unit

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) = Unit

    override fun onPasteTextFromClipboard(session: TerminalSession) = Unit

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) = Unit

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun getTerminalCursorStyle(): Int? = null

    override fun onScale(scale: Float): Float = scale

    override fun onSingleTapUp(e: MotionEvent) = Unit

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = true

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(copyMode: Boolean) = Unit

    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean =
        false

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) = Unit

    override fun logWarn(tag: String, message: String) = Unit

    override fun logInfo(tag: String, message: String) = Unit

    override fun logDebug(tag: String, message: String) = Unit

    override fun logVerbose(tag: String, message: String) = Unit

    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) = Unit

    override fun logStackTrace(tag: String, e: Exception) = Unit
}
