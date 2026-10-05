package io.github.eslamasabry.opencode_mobile

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.termux.terminal.PtyAccess
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Shells inside the app's built-in Ubuntu, each on a real PTY, with no
 * OpenCode server involved (docs/design/local-terminal-2026-09-24.md).
 *
 * A session is `bash -l` started through the same proot command line as the
 * app's own scripts ([BuiltinLinux.prootCommand]) on a pseudoterminal opened
 * by Termux's Apache 2.0 terminal-emulator library ([PtyAccess]). Sessions
 * belong to this manager, not to a screen: leaving the terminal leaves them
 * running, and the screen finds them again (with the last output kept here
 * for a Dart side that started over).
 *
 * The Dart side reads output through one event stream and answers each chunk
 * with `ack`; until it does, output waits here and, past [MAX_PENDING], in
 * the PTY itself. A program that prints without end (`yes`) is then slowed
 * to what the screen can draw instead of filling memory, and Ctrl-C reaches
 * it at once.
 */
class LocalTerminal private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val sessions = LinkedHashMap<Int, Session>()
    private var nextId = 1
    private var sink: EventChannel.EventSink? = null

    inner class Session(
        val id: Int,
        val pid: Int,
        val confined: Boolean,
        private val fd: Int,
        private val pty: ParcelFileDescriptor,
        /** Set for an agent sign-in: where Claude's page request lands. */
        private val openRequest: java.io.File? = null,
    ) {
        private val lock = Object()
        private val pending = ByteArrayOutputStream()
        private val recent = RecentBytes(RECENT_BYTES)
        private var flushPosted = false
        private var inFlight = false
        private var exitReported = false
        private val writer = Executors.newSingleThreadExecutor()
        private val output = FileOutputStream(pty.fileDescriptor)
        private val ended = CountDownLatch(1)

        @Volatile var exitCode: Int? = null
            private set

        val running: Boolean get() = exitCode == null

        fun startThreads() {
            val reader = Thread({ readLoop() }, "local-terminal-$id-read").apply { start() }
            if (openRequest != null) Thread({ watchOpenRequests(openRequest) }, "local-terminal-$id-open").start()
            Thread({
                val code = PtyAccess.waitFor(pid)
                // The last output is still in the PTY when the shell ends.
                reader.join(2000)
                exitCode = code
                ended.countDown()
                try {
                    pty.close()
                } catch (_: IOException) {
                }
                writer.shutdown()
                scheduleFlush()
            }, "local-terminal-$id-wait").start()
        }

        private fun readLoop() {
            val input = FileInputStream(pty.fileDescriptor)
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = try {
                    input.read(buffer)
                } catch (_: IOException) {
                    // EIO: the shell and everything on the PTY are gone.
                    -1
                }
                if (read <= 0) break
                synchronized(lock) {
                    while (pending.size() >= MAX_PENDING && exitCode == null && !detached) {
                        lock.wait(250)
                    }
                    pending.write(buffer, 0, read)
                    recent.append(buffer, read)
                }
                scheduleFlush()
            }
        }

        /**
         * Claude asked the system to open its sign-in page: hand the address
         * to the screen, which checks it and opens the browser.
         */
        private fun watchOpenRequests(file: java.io.File) {
            while (running) {
                if (file.isFile) {
                    val url = try { file.readText().trim() } catch (_: IOException) { "" }
                    file.delete()
                    if (url.isNotEmpty() && url.length <= 8192) {
                        main.post { sink?.success(mapOf("type" to "openUrl", "id" to id, "url" to url)) }
                    }
                }
                try { Thread.sleep(300) } catch (_: InterruptedException) { return }
            }
        }

        /** No one reads the stream now: output only goes to [recent]. */
        private val detached: Boolean get() = sink == null

        fun scheduleFlush() {
            synchronized(lock) {
                if (flushPosted || inFlight) return
                flushPosted = true
            }
            main.post { flush() }
        }

        /** On the main thread: one chunk to Dart, or the exit once all output is out. */
        private fun flush() {
            val events = sink
            val chunk: ByteArray?
            var exit: Int? = null
            synchronized(lock) {
                flushPosted = false
                if (inFlight) return
                if (pending.size() > 0) {
                    val all = pending.toByteArray()
                    // Small chunks: the screen draws between them instead of
                    // parsing a quarter megabyte in one go.
                    chunk = if (all.size <= MAX_CHUNK) all else all.copyOf(MAX_CHUNK)
                    pending.reset()
                    if (all.size > MAX_CHUNK) pending.write(all, MAX_CHUNK, all.size - MAX_CHUNK)
                    inFlight = events != null
                    lock.notifyAll()
                } else {
                    chunk = null
                    val code = exitCode
                    if (code != null && !exitReported) {
                        exitReported = events != null
                        exit = code
                    }
                }
            }
            if (events == null) return
            if (chunk != null) {
                events.success(mapOf("type" to "output", "id" to id, "data" to chunk))
            } else if (exit != null) {
                events.success(mapOf("type" to "exit", "id" to id, "code" to exit))
            }
        }

        /** Dart drew the last chunk: send what came since. */
        fun ack() {
            synchronized(lock) { inFlight = false }
            scheduleFlush()
        }

        /**
         * A Dart side that starts over (a new screen engine) takes the recent
         * output as its scrollback; live output continues after it.
         */
        fun attach(): ByteArray = synchronized(lock) {
            pending.reset()
            inFlight = false
            exitReported = false
            lock.notifyAll()
            recent.bytes()
        }

        fun write(bytes: ByteArray) {
            if (!running) return
            writer.execute {
                try {
                    output.write(bytes)
                } catch (error: IOException) {
                    Log.w(TAG, "write to shell $id failed", error)
                }
            }
        }

        fun resize(rows: Int, cols: Int) {
            if (!running || rows <= 0 || cols <= 0) return
            PtyAccess.setPtyWindowSize(fd, rows, cols, 0, 0)
        }

        /** Stops the shell and everything started from it; returns once it ended. */
        fun stop() {
            if (!running) return
            BuiltinLinux.stopPidTree(pid, { ms -> ended.await(ms, TimeUnit.MILLISECONDS) })
        }

        fun toMap(): Map<String, Any?> = mapOf(
            "id" to id,
            "pid" to pid,
            "running" to running,
            "exitCode" to exitCode,
            "signIn" to (openRequest != null),
        )
    }

    /**
     * Starts `bash -l` in Ubuntu on a new PTY of [rows] x [cols], or, for
     * [signInProfile], Claude's own sign-in for that profile's agent account.
     */
    fun start(rows: Int, cols: Int, signInProfile: String? = null, signInProgram: List<String>? = null): Session {
        val linux = BuiltinLinux.get(context)
        return synchronized(linux) {
            synchronized(this) {
                check(linux.installed) { "Ubuntu is not installed in the app yet" }
                linux.nameAndroidGroups()
                val signIn = signInProfile?.let {
                    linux.agentSignInCommand(it, signInProgram ?: listOf("claude", "auth", "login", "--claudeai"))
                }
                val argv = signIn?.first ?: linux.prootCommand(listOf("/bin/bash", "-l"))
                val env = (System.getenv() + linux.prootEnvironment())
                    .map { (key, value) -> "$key=$value" }
                val pid = IntArray(1)
                val fd = PtyAccess.createSubprocess(
                    linux.prootLaunchPath,
                    context.filesDir.absolutePath,
                    argv.toTypedArray(),
                    env.toTypedArray(),
                    pid,
                    rows.coerceAtLeast(1),
                    cols.coerceAtLeast(1),
                    0,
                    0,
                )
                val session = Session(
                    nextId++, pid[0], linux.prootIsConfined, fd, ParcelFileDescriptor.adoptFd(fd), signIn?.second,
                )
                sessions[session.id] = session
                session.startThreads()
                Log.i(TAG, "shell ${session.id} started as pid ${session.pid}")
                session
            }
        }
    }

    @Synchronized
    fun session(id: Int): Session? = sessions[id]

    @Synchronized
    fun list(): List<Session> = sessions.values.toList()

    @Synchronized
    fun hasLiveSessions(): Boolean = sessions.values.any { it.running }

    @Synchronized
    fun hasUnconfinedSessions(): Boolean = sessions.values.any { it.running && !it.confined }

    /** Forgets a shell, stopping it first when it still runs. */
    fun remove(id: Int) {
        val session = synchronized(this) { sessions[id] } ?: return
        session.stop()
        synchronized(this) { sessions.remove(id) }
    }

    fun register(messenger: BinaryMessenger) {
        EventChannel(messenger, EVENTS).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                    sink = events
                }

                override fun onCancel(arguments: Any?) {
                    sink = null
                }
            },
        )
        MethodChannel(messenger, METHODS).setMethodCallHandler { call, result ->
            handle(call, result)
        }
    }

    private fun handle(call: MethodCall, result: MethodChannel.Result) {
        fun inBackground(work: () -> Any?) {
            Thread {
                try {
                    val value = work()
                    main.post { result.success(value) }
                } catch (error: Throwable) {
                    Log.w(TAG, "${call.method} failed", error)
                    main.post {
                        result.error("local_terminal", error.message ?: error.javaClass.simpleName, null)
                    }
                }
            }.start()
        }
        val id = call.argument<Int>("id")
        val session = id?.let { session(it) }
        when (call.method) {
            "start" -> {
                val rows = call.argument<Int>("rows") ?: 24
                val cols = call.argument<Int>("cols") ?: 80
                val signIn = call.argument<String>("signInProfile")
                val program = call.argument<List<String>>("signInProgram")
                inBackground { start(rows, cols, signIn, program).toMap() }
            }
            "list" -> result.success(
                mapOf(
                    "sessions" to list().map { it.toMap() },
                    "processes" to BuiltinLinux.appProcessCount(),
                ),
            )
            "processCount" -> inBackground { BuiltinLinux.appProcessCount() }
            "attach" -> result.success(session?.attach())
            "write" -> {
                val data = call.argument<ByteArray>("data")
                if (session != null && data != null) session.write(data)
                result.success(null)
            }
            "ack" -> {
                session?.ack()
                result.success(null)
            }
            "resize" -> {
                val rows = call.argument<Int>("rows") ?: 0
                val cols = call.argument<Int>("cols") ?: 0
                session?.resize(rows, cols)
                result.success(null)
            }
            "stop" -> inBackground {
                session?.stop()
                session?.toMap()
            }
            "remove" -> inBackground {
                if (id != null) remove(id)
                null
            }
            else -> result.notImplemented()
        }
    }

    /** The last [capacity] bytes a shell printed. */
    private class RecentBytes(private val capacity: Int) {
        private val ring = ByteArray(capacity)
        private var start = 0
        private var size = 0

        fun append(bytes: ByteArray, count: Int) {
            for (i in 0 until count) {
                val at = (start + size) % capacity
                ring[at] = bytes[i]
                if (size < capacity) size++ else start = (start + 1) % capacity
            }
        }

        fun bytes(): ByteArray = ByteArray(size) { ring[(start + it) % capacity] }
    }

    companion object {
        const val TAG = "OcTerminal"
        const val METHODS = "io.github.eslamasabry.opencode_mobile/local_terminal"
        const val EVENTS = "io.github.eslamasabry.opencode_mobile/local_terminal/events"

        /** Output held for a slow screen before the shell itself is made to wait. */
        private const val MAX_PENDING = 256 * 1024

        /** The most output one event carries. */
        private const val MAX_CHUNK = 32 * 1024

        /** Output kept for a screen that starts over. */
        private const val RECENT_BYTES = 512 * 1024

        @Volatile private var instance: LocalTerminal? = null

        fun get(context: Context): LocalTerminal =
            instance ?: synchronized(this) {
                instance ?: LocalTerminal(context.applicationContext).also { instance = it }
            }
    }
}
