package io.github.eslamasabry.opencode_mobile

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Fixed categories only; tests never record real commands, outputs or accounts. */
object ServiceEvents {
    val values = CopyOnWriteArrayList<String>()
    val workers = CopyOnWriteArrayList<Thread>()
    fun add(value: String) { values.add(value) }
    fun worker() { workers.add(Thread.currentThread()) }
}

class SetupRunner {
    fun cancel() {
        ServiceEvents.worker()
        ServiceEvents.add("setup.cancel")
        cancelEntered.countDown()
        try {
            if (blockCancel) check(cancelPermit.await(5, TimeUnit.SECONDS)) { "cancel gate expired" }
            if (denyStop) throw SecurityException("policy")
        } finally { cancelled.countDown() }
    }
    companion object {
        val cancelled = CountDownLatch(1)
        val cancelEntered = CountDownLatch(1)
        val cancelPermit = CountDownLatch(1)
        var denyStop = false
        var blockCancel = false
        fun get(context: Context) = SetupRunner()
    }
}

class BuiltinLinux {
    internal fun prepareServerEventRestore(event: NativeServerRestoreEvent): NativeServerRestoreTicket? {
        ServiceEvents.add("event.prepare")
        return if (eventAllowed) NativeServerRestoreTicket() else null
    }
    internal fun restoreServerAfterSystemEvent(ticket: NativeServerRestoreTicket): Boolean {
        eventRestored++; ServiceEvents.add("event.restore"); return eventAllowed
    }
    internal fun cancelServerEventRestore(ticket: NativeServerRestoreTicket) { eventCancelled++; ServiceEvents.add("event.cancel") }
    fun settleServerEventService() { eventSettled++; ServiceEvents.add("event.settle") }
    val serverRestorationArmed: Boolean get() = armed
    fun rejectServerRestoration() { rejected++; ServiceEvents.add("runtime.reject") }
    fun restoreServerAfterProcessReclaim() {
        restored++; ServiceEvents.add("runtime.restore")
        if (!restorationKeepsArmed) armed = false
    }
    fun revokeForegroundWork() { foregroundWork = false; ServiceEvents.add("work.foreground.revoke") }
    fun revokeSetupWork() { setupWork = false; ServiceEvents.add("work.setup.revoke") }
    fun requestServerStop(reason: String = "stopped", includeOther: Boolean = false,
        onRevoked: ((Long) -> Unit)? = null) {
        stopReason = reason; includedOther = includeOther
        ServiceEvents.add("runtime.request")
        if (denyRequest) throw SecurityException("policy")
        armed = false
        onRevoked?.invoke(REVISION)
        ServiceEvents.add("runtime.revoked")
        if (throwAfterRevoked) throw SecurityException("policy")
    }
    fun stopAllServices(expectedStopRevision: Long? = null) { drain("revision", expectedStopRevision) }
    fun drainRevokedRuntimeChildren() { drain("fallback", null) }
    private fun drain(mode: String, revision: Long?) {
        ServiceEvents.worker()
        drainMode = mode; drainRevision = revision
        ServiceEvents.add("runtime.drain")
        drainEntered.countDown()
        try {
            if (blockDrain) check(drainPermit.await(5, TimeUnit.SECONDS)) { "drain gate expired" }
            if (denyStop) throw SecurityException("policy")
        } finally { stopped.countDown() }
    }
    companion object {
        var eventAllowed = true
        var eventRestored = 0
        var eventCancelled = 0
        var eventSettled = 0
        const val REVISION = 47L
        val stopped = CountDownLatch(1)
        val drainEntered = CountDownLatch(1)
        val drainPermit = CountDownLatch(1)
        var denyRequest = false
        var denyStop = false
        var throwAfterRevoked = false
        var blockDrain = false
        @Volatile var armed = false
        @Volatile var foregroundWork = true
        @Volatile var setupWork = true
        @Volatile var restored = 0
        @Volatile var rejected = 0
        var restorationKeepsArmed = true
        @Volatile var stopReason: String? = null
        @Volatile var includedOther = false
        @Volatile var drainMode: String? = null
        @Volatile var drainRevision: Long? = null
        fun get(context: Context) = BuiltinLinux()
    }
}

object BuildConfig { const val BUILTIN_RUNTIME_QA = false }
class MainActivity { companion object { const val EXTRA_LAUNCH_ACTION = "action" } }
object R {
    object mipmap { const val ic_launcher = 1 }
    object string {
        const val native_setup_channel = 101
        const val native_phone_server_channel = 102
        const val native_phone_server_description = 103
        const val native_phone_server_title = 104
        const val native_phone_server_body = 105
        const val native_stop = 106
    }
}

/**
 * Resource-boundary double for service integration only. Distinct markers prove
 * the returned lookup is rendered. The real NativeStrings configuration and
 * Android XML translations are qualified by the dedicated locale harness.
 */
object NativeStrings {
    fun get(context: Context, id: Int, vararg args: Any): String {
        val name = when (id) {
            R.string.native_setup_channel -> "setup-channel"
            R.string.native_phone_server_channel -> "phone-channel"
            R.string.native_phone_server_description -> "phone-description"
            R.string.native_phone_server_title -> "phone-title"
            R.string.native_phone_server_body -> "phone-body"
            R.string.native_stop -> "stop"
            else -> error("unknown resource")
        }
        return "${context.selectedLanguage}:$name"
    }
}

internal class NativeServerRestoreTicket
internal enum class NativeServerRestoreEvent {
    BOOT_COMPLETED, PACKAGE_REPLACED;
    companion object {
        fun fromAction(action: String?): NativeServerRestoreEvent? = when (action) {
            "android.intent.action.BOOT_COMPLETED" -> BOOT_COMPLETED
            "android.intent.action.MY_PACKAGE_REPLACED" -> PACKAGE_REPLACED
            else -> null
        }
    }
}
