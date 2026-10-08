package io.github.eslamasabry.opencode_mobile
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) {
    if (args.single().startsWith("localized-")) {
        localizedServiceCopy(args.single())
        println("PASS ${args.single()}")
        return
    }
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

private fun localizedServiceCopy(scenario: String) {
    val language = if (scenario == "localized-copy-ar") "ar" else "en"
    val service = BuiltinServerService().apply { selectedLanguage = language }
    check(service.onStartCommand(null, 0, 1) == Service.START_NOT_STICKY)
    val notification = checkNotNull(service.foregroundNotification)
    check(notification.title == "$language:phone-title")
    check(notification.text == "$language:phone-body")
    check(notification.actions.map { it.label } == listOf("$language:stop"))
    val channel = NotificationManager.instance.channels.last()
    check(channel.name == "$language:phone-channel")
    check(channel.description == "$language:phone-description")

    service.selectedLanguage = if (language == "ar") "en" else "ar"
    service.onStartCommand(Intent(service, BuiltinServerService::class.java)
        .putExtra("title", "authored supplied title"), 0, 2)
    val changed = checkNotNull(service.foregroundNotification)
    check(changed.title == "authored supplied title")
    check(changed.text == "${service.selectedLanguage}:phone-body")
    check(changed.actions.single().label == "${service.selectedLanguage}:stop")

    val setup = SetupService().apply { selectedLanguage = language }
    setup.onStartCommand(null, 0, 1)
    check(NotificationManager.instance.channels.last().name == "$language:setup-channel")
    setup.onStartCommand(Intent(setup, SetupService::class.java)
        .putExtra("channel", "authored setup channel")
        .putExtra("title", "authored setup title")
        .putExtra("text", "authored setup progress"), 0, 2)
    check(NotificationManager.instance.channels.last().name == "authored setup channel")
    val progress = checkNotNull(setup.foregroundNotification)
    check(progress.title == "authored setup title")
    check(progress.text == "authored setup progress")
}
