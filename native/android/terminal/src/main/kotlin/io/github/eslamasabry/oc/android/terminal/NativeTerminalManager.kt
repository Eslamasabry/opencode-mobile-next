package io.github.eslamasabry.oc.android.terminal

import android.app.Application
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.security.Redactor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface NativeTerminalEvent {
    class OpenUrl(val url: String) : NativeTerminalEvent {
        override fun toString(): String = "OpenUrl(•••)"
    }

    data class Exited(val code: Int) : NativeTerminalEvent
}

/**
 * App-prepared program data. The PTY module knows neither the Linux launcher nor profile storage.
 */
class NativeTerminalProgram(
    val path: String,
    val directory: String,
    val arguments: List<String>,
    val environment: List<String>,
    val owner: String?,
    val confined: Boolean,
    val openRequest: java.io.File?,
    val stopTree: (pid: Int, exited: (Long) -> Boolean) -> Unit,
) {
    override fun toString(): String = "NativeTerminalProgram(•••)"
}

/** Real Termux JNI PTYs and emulator grids, kept in process. No output is logged or persisted. */
@SingleIn(AppScope::class)
@Inject
class NativeTerminalManager(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val appScope: AppCoroutineScope,
    private val redactor: Redactor,
) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = LinkedHashMap<Int, NativeSession>()
    private var nextId = 1
    private val denied = HashSet<String>()

    inner class NativeSession
    internal constructor(
        val id: Int,
        private val program: NativeTerminalProgram,
    ) {
        val owner: String?
            get() = program.owner

        val confined: Boolean
            get() = program.confined

        private val request: java.io.File?
            get() = program.openRequest

        internal lateinit var terminal: TerminalSession
        internal lateinit var view: TerminalView
        private lateinit var surface: TerminalSurfaceHost
        val platformView: android.view.View
            get() = surface

        private val ended = CountDownLatch(1)
        private val removalActions = ArrayList<() -> Unit>()
        private var removed = false

        fun onRemoved(action: () -> Unit) {
            synchronized(this) { if (removed) action() else removalActions += action }
        }

        internal fun removed() {
            val actions =
                synchronized(this) {
                    if (removed) return
                    removed = true
                    removalActions.toList().also { removalActions.clear() }
                }
            actions.forEach { it() }
        }

        private val mutableTitle = MutableStateFlow("")
        val title = mutableTitle.asStateFlow()
        private val mutableTranscript = MutableStateFlow("")
        val transcript = mutableTranscript.asStateFlow()
        private val mutableEvents =
            MutableSharedFlow<NativeTerminalEvent>(replay = 1, extraBufferCapacity = 8)
        val events = mutableEvents.asSharedFlow()
        @Volatile
        var exitCode: Int? = null
            private set

        val running: Boolean
            get() = exitCode == null

        val pid: Int
            get() = terminal.pid

        internal val hasProcess: Boolean
            get() = this::terminal.isInitialized && this::view.isInitialized && terminal.pid > 0

        private val client =
            object :
                QuietSessionClient({
                    if (this@NativeSession::view.isInitialized) view else null
                }) {
                override fun onTextChanged(session: TerminalSession) {
                    // The emulator parses UTF-8 incrementally; whole characters survive split PTY
                    // reads.
                    val text = session.emulator?.screen?.transcriptText.orEmpty()
                    val safe = redactor.redact(text)
                    var start = (safe.length - 100_000).coerceAtLeast(0)
                    if (
                        start > 0 &&
                            start < safe.length &&
                            safe[start].isLowSurrogate() &&
                            safe[start - 1].isHighSurrogate()
                    )
                        start++
                    mutableTranscript.value = safe.substring(start)
                    view.onScreenUpdated()
                }

                override fun onTitleChanged(session: TerminalSession) {
                    mutableTitle.value =
                        redactor
                            .redact(session.title.orEmpty())
                            .filter { it >= ' ' && it != '\u007f' }
                            .take(160)
                }

                override fun onSessionFinished(session: TerminalSession) {
                    exitCode = session.exitStatus
                    ended.countDown()
                    mutableEvents.tryEmit(NativeTerminalEvent.Exited(session.exitStatus))
                    view.onScreenUpdated()
                }
            }

        internal fun start(rows: Int, cols: Int) {
            terminal =
                TerminalSession(
                    program.path,
                    program.directory,
                    program.arguments.toTypedArray(),
                    program.environment.toTypedArray(),
                    2000,
                    client,
                )
            view =
                TerminalView(app, null).apply {
                    setTerminalViewClient(TerminalInputClient(this))
                    setIsTerminalViewKeyLoggingEnabled(false)
                    setTextSize(
                        (14 * app.resources.displayMetrics.scaledDensity).toInt().coerceAtLeast(14)
                    )
                    setTypeface(Typeface.MONOSPACE)
                    importantForAutofill =
                        android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }
            terminal.updateSize(cols.coerceIn(2, 500), rows.coerceIn(2, 200), 0, 0)
            surface = TerminalSurfaceHost(app, view)
            view.attachSession(terminal)
            request?.let { file ->
                appScope.launch(dispatchers.io) {
                    while (running) {
                        val url =
                            try {
                                if (file.isFile && file.length() <= 32768) file.readText().trim()
                                else ""
                            } catch (_: Exception) {
                                ""
                            }
                        if (file.exists()) file.delete()
                        if (url.isNotEmpty() && url.length <= 8192)
                            mutableEvents.emit(NativeTerminalEvent.OpenUrl(url))
                        kotlinx.coroutines.delay(300)
                    }
                }
            }
        }

        suspend fun write(bytes: ByteArray) =
            withContext(dispatchers.main) {
                if (running && bytes.size <= 32768) terminal.write(bytes, 0, bytes.size)
            }

        suspend fun resize(cols: Int, rows: Int) =
            withContext(dispatchers.main) {
                if (running) terminal.updateSize(cols.coerceIn(2, 500), rows.coerceIn(2, 200), 0, 0)
            }

        @Synchronized
        fun stop() {
            if (running) program.stopTree(pid, { ms -> ended.await(ms, TimeUnit.MILLISECONDS) })
            check(!running) { "The terminal did not stop." }
        }
    }

    /**
     * Blocking runtime seam: preparation stays on the caller's worker; emulator creation uses
     * Android's main looper.
     */
    fun start(program: NativeTerminalProgram, rows: Int = 24, cols: Int = 80): NativeSession {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "Terminal preparation needs a worker."
        }
        val answer = CompletableFuture<NativeSession>()
        val launch = Runnable {
            synchronized(answer) {
                if (answer.isDone) return@Runnable
                var pending: NativeSession? = null
                try {
                    synchronized(this) {
                        check(
                            sessions.size < 16 &&
                                (program.owner == null ||
                                    (program.owner !in denied &&
                                        sessions.values.none {
                                            it.owner == program.owner && it.running
                                        }))
                        )
                        val session = NativeSession(nextId++, program)
                        pending = session
                        session.start(rows, cols)
                        sessions[session.id] = session
                        answer.complete(session)
                    }
                } catch (_: Exception) {
                    val launched = pending
                    if (launched?.hasProcess == true) {
                        // A post-fork failure still returns the exact native handle. Its owner
                        // must be able to drain that PID before releasing either lease.
                        synchronized(this) { sessions[launched.id] = launched }
                        answer.complete(launched)
                    } else
                        answer.completeExceptionally(
                            IllegalStateException("The terminal could not start.")
                        )
                }
            }
        }
        main.post(launch)
        return try {
            answer.get(10, TimeUnit.SECONDS)
        } catch (error: Exception) {
            synchronized(answer) {
                // A queued launch is revoked before the caller may release foreground protection.
                // An executing launch holds this lock until its exact session is returned.
                if (!answer.isDone) {
                    answer.cancel(false)
                    main.removeCallbacks(launch)
                }
                if (answer.isDone && !answer.isCancelled && !answer.isCompletedExceptionally)
                    answer.get()
                else throw IllegalStateException("The terminal could not start.")
            }
        }
    }

    fun list(): List<NativeSession> = synchronized(this) { sessions.values.toList() }

    fun remove(id: Int) {
        val session = synchronized(this) { sessions[id] } ?: return
        session.stop()
        synchronized(this) { if (sessions[id] === session) sessions.remove(id) }
        session.removed()
    }

    fun stopAdmission(owner: String) {
        synchronized(this) { denied += owner }
    }

    suspend fun closeProfile(owner: String) =
        withContext(dispatchers.io) {
            stopAdmission(owner)
            val ids =
                synchronized(this@NativeTerminalManager) {
                    sessions.values.filter { it.owner == owner }.map { it.id }
                }
            ids.forEach(::remove)
        }

    fun reopen(owner: String) {
        synchronized(this) { denied -= owner }
    }
}

/**
 * Library diagnostics are discarded; valid bounded OSC 52 writes use the system clipboard, only
 * while [shownView] is on screen.
 * Clipboard queries are removed by TerminalSession before VT decoding.
 */
internal open class QuietSessionClient(
    private val shownView: () -> android.view.View? = { null }
) : TerminalSessionClient {
    override fun onTextChanged(session: TerminalSession) = Unit

    override fun onTitleChanged(session: TerminalSession) = Unit

    override fun onSessionFinished(session: TerminalSession) = Unit

    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {
        copyTerminalText(session, text, shownView())
    }

    override fun onPasteTextFromClipboard(session: TerminalSession) = Unit

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) = Unit

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun getTerminalCursorStyle(): Int = 0

    override fun logError(tag: String, message: String) = Unit

    override fun logWarn(tag: String, message: String) = Unit

    override fun logInfo(tag: String, message: String) = Unit

    override fun logDebug(tag: String, message: String) = Unit

    override fun logVerbose(tag: String, message: String) = Unit

    override fun logStackTraceWithMessage(tag: String, message: String, error: Exception) = Unit

    override fun logStackTrace(tag: String, error: Exception) = Unit
}

/** Ordinary terminal input; a tapped output link enters the kit's review sheet. */
internal class TerminalInputClient(private val view: TerminalView) : TerminalViewClient {
    override fun onScale(scale: Float): Float = 1f

    override fun onSingleTapUp(event: MotionEvent) {
        if ((view.parent as? TerminalSurfaceHost)?.openLink(event) == true) return
        view.requestFocus()
        (view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun shouldBackButtonBeMappedToEscape(): Boolean = false

    override fun shouldEnforceCharBasedInput(): Boolean = false

    override fun shouldUseCtrlSpaceWorkaround(): Boolean = false

    override fun isTerminalViewSelected(): Boolean = true

    override fun copyModeChanged(on: Boolean) = Unit

    override fun onKeyDown(code: Int, event: KeyEvent, session: TerminalSession): Boolean = false

    override fun onKeyUp(code: Int, event: KeyEvent): Boolean = false

    override fun onLongPress(event: MotionEvent): Boolean = false

    override fun readControlKey(): Boolean = false

    override fun readAltKey(): Boolean = false

    override fun readShiftKey(): Boolean = false

    override fun readFnKey(): Boolean = false

    override fun onCodePoint(code: Int, ctrl: Boolean, session: TerminalSession): Boolean = false

    override fun onEmulatorSet() = Unit

    override fun logError(tag: String, message: String) = Unit

    override fun logWarn(tag: String, message: String) = Unit

    override fun logInfo(tag: String, message: String) = Unit

    override fun logDebug(tag: String, message: String) = Unit

    override fun logVerbose(tag: String, message: String) = Unit

    override fun logStackTraceWithMessage(tag: String, message: String, error: Exception) = Unit

    override fun logStackTrace(tag: String, error: Exception) = Unit
}
