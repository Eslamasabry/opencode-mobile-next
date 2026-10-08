package io.github.eslamasabry.opencode_mobile
import android.content.Context
import java.util.concurrent.CountDownLatch
class SetupRunner {
    fun cancel() { cancelled.countDown(); if (denyStop) throw SecurityException("policy") }
    companion object { var cancelled = CountDownLatch(1); var denyStop = false; fun get(context: Context) = SetupRunner() }
}
class BuiltinLinux {
    // This service-copy/policy harness does not qualify runtime restoration.
    val serverRestorationArmed = false
    fun rejectServerRestoration() {}
    fun restoreServerAfterProcessReclaim() {}
    fun requestServerStop(reason: String, includeOther: Boolean, onRevoked: (Long) -> Unit) {
        onRevoked(1L)
        if (denyRequest) throw SecurityException("policy")
    }
    fun stopAllServices(revision: Long) { stopped.countDown(); if (denyStop) throw SecurityException("policy") }
    fun drainRevokedRuntimeChildren() { stopped.countDown(); if (denyStop) throw SecurityException("policy") }
    companion object {
        var stopped = CountDownLatch(1)
        var denyRequest = false
        var denyStop = false
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
