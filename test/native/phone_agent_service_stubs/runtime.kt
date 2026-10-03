package io.github.eslamasabry.opencode_mobile
import android.content.Context
import java.util.concurrent.CountDownLatch
class SetupRunner {
    fun cancel() { cancelled.countDown(); if (denyStop) throw SecurityException("policy") }
    companion object { var cancelled = CountDownLatch(1); var denyStop = false; fun get(context: Context) = SetupRunner() }
}
class BuiltinLinux {
    fun requestServerStop() { if (denyRequest) throw SecurityException("policy") }
    fun stopAllServices() { stopped.countDown(); if (denyStop) throw SecurityException("policy") }
    companion object {
        var stopped = CountDownLatch(1)
        var denyRequest = false
        var denyStop = false
        fun get(context: Context) = BuiltinLinux()
    }
}
class MainActivity { companion object { const val EXTRA_LAUNCH_ACTION = "action" } }
object R { object mipmap { const val ic_launcher = 1 } }
