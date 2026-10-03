package io.github.eslamasabry.opencode_mobile
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) {
    val uncaught = AtomicReference<Throwable?>()
    Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.set(error) }
    when (args.single()) {
        "setup-main-denied" -> {
            Service.denyForeground = true
            val service = SetupService()
            check(service.onStartCommand(null, 0, 1) == Service.START_NOT_STICKY)
            check(SetupRunner.cancelled.await(2, TimeUnit.SECONDS))
            check(service.stopped)
        }
        "daemon-main-denied" -> {
            Service.denyForeground = true
            BuiltinLinux.denyRequest = true
            val service = BuiltinServerService()
            check(service.onStartCommand(null, 0, 1) == Service.START_NOT_STICKY)
            check(BuiltinLinux.stopped.await(2, TimeUnit.SECONDS))
        }
        "setup-channel-denied" -> {
            NotificationManager.denyChannel = true
            SetupRunner.denyStop = true
            val service = SetupService()
            check(service.onStartCommand(null, 0, 1) == Service.START_NOT_STICKY)
            check(SetupRunner.cancelled.await(2, TimeUnit.SECONDS))
            check(service.stopped)
        }
        "daemon-timeout-denied" -> {
            BuiltinLinux.denyStop = true
            BuiltinLinux.denyRequest = true
            BuiltinServerService().onTimeout(1, 1)
            check(BuiltinLinux.stopped.await(2, TimeUnit.SECONDS))
        }
        "daemon-stop-denied" -> {
            BuiltinLinux.denyStop = true
            val service = BuiltinServerService()
            service.onStartCommand(Intent(service, BuiltinServerService::class.java).setAction("stop"), 0, 1)
            check(BuiltinLinux.stopped.await(2, TimeUnit.SECONDS))
        }
        "result-notification-denied" -> {
            NotificationManager.denyNotify = true
            SetupService.update(Context(), "setup", "title", "progress")
            SetupService.finish(Context(), "setup", "done", true)
        }
        "dispatch-denied" -> {
            Context.denyDispatch = true
            check(runCatching { SetupService.start(Context(), "setup", "title", "progress") }.exceptionOrNull() is SecurityException)
            check(runCatching { BuiltinServerService.start(Context()) }.exceptionOrNull() is SecurityException)
        }
        else -> error("unknown scenario")
    }
    Thread.sleep(50)
    check(uncaught.get() == null) { "native callback failure was uncaught" }
    println("PASS ${args.single()}")
}
