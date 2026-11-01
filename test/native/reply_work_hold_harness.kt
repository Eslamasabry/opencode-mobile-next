import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FakeWakeLock {
    @Volatile var held = false
    fun acquire(ms: Long) { check(ms in 1000..900000); held = true }
}

// The lease table the runtime hands a chat lease to: it admits only when the
// caller says the server runs, as WorkLeases does, and serverGone releases.
class FakeLeases(private val lock: FakeWakeLock) {
    fun chat(name: String, on: Boolean, holdMs: Long, running: Boolean): Map<String, Boolean> {
        if (!on || !running) {
            lock.held = false
            return mapOf("held" to false, "capped" to false)
        }
        lock.acquire(holdMs.coerceIn(1_000L, 900_000L))
        return mapOf("held" to lock.held, "capped" to false)
    }
    fun serverGone() { lock.held = false }
}

// The test runner inserts the actual production method, including its
// synchronization. These are its Android-free collaborators.
class Runtime {
    val checked = CountDownLatch(1)
    val continueAdmission = CountDownLatch(1)
    val stopped = CountDownLatch(1)
    @Volatile var running = true
    val wake = FakeWakeLock()
    val workLeases = FakeLeases(wake)
    val serverRunning: Boolean get() {
        val value = running
        checked.countDown()
        check(continueAdmission.await(3, TimeUnit.SECONDS))
        return value
    }
    @Synchronized fun stop() {
        running = false
        workLeases.serverGone()
        stopped.countDown()
    }
    // PRODUCTION_HOLD_METHOD
}

private var descendantLookups = 0
private fun pidOf(process: Process): Int? = 123
private fun descendants(root: Int): List<Int> {
    descendantLookups++
    return emptyList()
}
private fun signal(pid: Int, signal: Int) { error("must not signal") }
object OsConstants { const val SIGTERM = 15; const val SIGKILL = 9 }
object AndroidProcess { fun myUid(): Int = 1 }
object NativeProcScan {
    fun read(pid: Int, uid: Int): Any? = null
    fun signalIfSame(member: Any, signal: Int) { error("must not signal") }
}
class ExitedProcess : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
    override fun getInputStream(): InputStream = InputStream.nullInputStream()
    override fun getErrorStream(): InputStream = InputStream.nullInputStream()
    override fun waitFor(): Int = 0
    override fun exitValue(): Int = 0
    override fun destroy() {}
    override fun isAlive(): Boolean = false
}
// PRODUCTION_STOP_TREE

object TermuxActivityEnvironment { const val PERMISSION = "synthetic.permission" }
class FakeActivity(private val rationale: Boolean) {
    fun shouldShowRequestPermissionRationale(permission: String): Boolean = rationale
}

fun permissionAnswer(grantResults: IntArray, rationale: Boolean): String {
    val granted = grantResults.firstOrNull() == 0
    val activity = FakeActivity(rationale)
    return // PRODUCTION_PERMISSION_ANSWER
}

fun main() {
    val runtime = Runtime()
    val hold = Thread { runtime.setChatWorkLease("legacy.reply", true, 600000) }.apply { start() }
    check(runtime.checked.await(3, TimeUnit.SECONDS))
    val stop = Thread { runtime.stop() }.apply { start() }
    // Let Stop run during the gap between admission and acquire. With the
    // runtime monitor it must wait; without it Stop finishes too early.
    runtime.stopped.await(200, TimeUnit.MILLISECONDS)
    runtime.continueAdmission.countDown()
    hold.join(3000)
    stop.join(3000)
    check(!hold.isAlive && !stop.isAlive)
    check(!runtime.wake.held) { "Stop was followed by stale wake admission" }
    stopTree(ExitedProcess(), 0)
    check(descendantLookups == 0) { "Resolved a dead Process's reusable PID" }
    check(permissionAnswer(intArrayOf(), false) == "denied")
    check(permissionAnswer(intArrayOf(0), false) == "granted")
    check(permissionAnswer(intArrayOf(-1), true) == "denied")
    check(permissionAnswer(intArrayOf(-1), false) == "permanentlyDenied")
    println("PASS reply work admission versus Stop; exited Process PID ignored; permission cancellation")
}
