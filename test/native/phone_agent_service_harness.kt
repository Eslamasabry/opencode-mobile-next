package io.github.eslamasabry.opencode_mobile
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private fun before(first: String, second: String) {
    val events = ServiceEvents.values
    check(events.indexOf(first) >= 0 && events.indexOf(second) > events.indexOf(first)) {
        "service lifecycle order failed"
    }
}

/** A blocked child drain must not block an Android lifecycle callback. */
private fun boundedCallback(permit: CountDownLatch, action: () -> Unit, verify: () -> Unit) {
    val returned = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>()
    val callback = Thread({
        try { action() } catch (error: Throwable) { failure.set(error) }
        finally { returned.countDown() }
    }, "fixture-lifecycle").apply { isDaemon = true; start() }
    try {
        check(returned.await(1, TimeUnit.SECONDS)) { "lifecycle callback waited for child drain" }
        check(failure.get() == null) { "lifecycle callback threw" }
        verify()
    } finally {
        permit.countDown()
        callback.join(2000)
    }
    check(!callback.isAlive) { "lifecycle callback did not finish" }
}

private fun runtimeRevocation(stop: Boolean) {
    BuiltinLinux.armed = true
    BuiltinLinux.blockDrain = true
    val service = BuiltinServerService()
    boundedCallback(BuiltinLinux.drainPermit, {
        if (stop) check(service.onStartCommand(
            Intent(service, BuiltinServerService::class.java).setAction("stop"), 0, 9
        ) == Service.START_NOT_STICKY) else service.onTimeout(9, 1)
    }, {
        check(!BuiltinLinux.foregroundWork && !BuiltinLinux.armed)
        check(BuiltinLinux.includedOther)
        check(BuiltinLinux.stopReason == if (stop) "stopped" else "systemTimeout")
        check(BuiltinLinux.drainEntered.await(1, TimeUnit.SECONDS))
        check(BuiltinLinux.stopped.count == 1L) { "drain did not remain gated" }
        check(BuiltinLinux.drainMode == "revision" && BuiltinLinux.drainRevision == BuiltinLinux.REVISION)
        before("work.foreground.revoke", "runtime.request")
        before("runtime.revoked", "runtime.drain")
        if (!stop) check(service.stopped && !service.foreground)
    })
    check(BuiltinLinux.stopped.await(2, TimeUnit.SECONDS))
}

fun main(args: Array<String>) {
    if (args.single().startsWith("localized-")) {
        localizedServiceCopy(args.single())
        println("PASS ${args.single()}")
        return
    }
    val uncaught = AtomicReference<Throwable?>()
    Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.set(error) }
    when (args.single()) {
        "event-foreground-before-restore" -> {
            val context = Context()
            BuiltinRestoreReceiver().onReceive(context, Intent(context, BuiltinRestoreReceiver::class.java)
                .setAction("android.intent.action.BOOT_COMPLETED"))
            check(Context.foregroundDispatches == 1)
            val service = BuiltinServerService()
            check(service.onStartCommand(checkNotNull(Context.dispatched), 0, 1) == Service.START_STICKY)
            check(BuiltinLinux.eventRestored == 1 && BuiltinLinux.restored == 0)
            before("foreground.start", "event.restore")
            service.onStartCommand(Context.dispatched, 0, 2)
            check(BuiltinLinux.eventRestored == 1) { "event capability was replayed" }
        }
        "event-dispatch-denied" -> {
            Context.denyDispatch = true
            val context = Context()
            BuiltinRestoreReceiver().onReceive(context, Intent(context, BuiltinRestoreReceiver::class.java)
                .setAction("android.intent.action.MY_PACKAGE_REPLACED"))
            check(BuiltinLinux.eventCancelled == 1 && BuiltinLinux.eventRestored == 0)
            check(BuiltinLinux.foregroundWork && BuiltinLinux.stopReason == null)
        }
        "event-foreground-denied" -> {
            val context = Context()
            check(BuiltinServerService.startForRestore(context, NativeServerRestoreTicket()))
            Service.denyForeground = true
            val service = BuiltinServerService()
            check(service.onStartCommand(Context.dispatched, 0, 1) == Service.START_NOT_STICKY)
            check(BuiltinLinux.eventRestored == 0 && BuiltinLinux.eventCancelled == 1)
            check(BuiltinLinux.stopReason == null && BuiltinLinux.foregroundWork)
        }
        "event-stale-settles" -> {
            val context = Context()
            check(BuiltinServerService.startForRestore(context, NativeServerRestoreTicket()))
            BuiltinLinux.eventAllowed = false
            BuiltinServerService().onStartCommand(Context.dispatched, 0, 1)
            check(BuiltinLinux.eventSettled == 1)
            check(BuiltinLinux.stopReason == null && BuiltinLinux.foregroundWork)
        }
        "event-stop-cancels" -> {
            val context = Context()
            check(BuiltinServerService.startForRestore(context, NativeServerRestoreTicket()))
            BuiltinServerService.stop(context)
            BuiltinServerService().onStartCommand(Context.dispatched, 0, 1)
            check(BuiltinLinux.eventCancelled == 1 && BuiltinLinux.eventRestored == 0)
        }
        "event-untrusted-ignored" -> {
            val context = Context()
            BuiltinRestoreReceiver().onReceive(context, Intent(context, BuiltinRestoreReceiver::class.java)
                .setAction("android.intent.action.PACKAGE_REPLACED"))
            check(ServiceEvents.values.isEmpty() && Context.foregroundDispatches == 0)
        }
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
            check(BuiltinLinux.drainMode == "fallback")
            check(!BuiltinLinux.foregroundWork)
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
            check(BuiltinLinux.drainMode == "fallback")
            check(!BuiltinLinux.foregroundWork)
        }
        "daemon-stop-denied" -> {
            BuiltinLinux.denyStop = true
            val service = BuiltinServerService()
            service.onStartCommand(Intent(service, BuiltinServerService::class.java).setAction("stop"), 0, 1)
            check(BuiltinLinux.stopped.await(2, TimeUnit.SECONDS))
            check(BuiltinLinux.drainMode == "revision" && BuiltinLinux.drainRevision == BuiltinLinux.REVISION)
        }
        "armed-null-sticky" -> {
            BuiltinLinux.armed = true
            val service = BuiltinServerService()
            check(service.onStartCommand(null, 0, 1) == Service.START_STICKY)
            check(BuiltinLinux.restored == 1 && BuiltinLinux.rejected == 0)
            check(service.foreground && !service.stopped && BuiltinServerService.isForegroundRunning)
            before("foreground.start", "runtime.restore")
            check(BuiltinLinux.drainEntered.count == 1L)
        }
        "unarmed-null-rejected" -> {
            val service = BuiltinServerService()
            check(service.onStartCommand(null, 0, 1) == Service.START_NOT_STICKY)
            check(BuiltinLinux.rejected == 1 && BuiltinLinux.restored == 0)
            check(service.stopped && !service.foreground)
            check(BuiltinLinux.drainEntered.count == 1L)
        }
        "restoration-disarmed-not-sticky" -> {
            BuiltinLinux.armed = true
            BuiltinLinux.restorationKeepsArmed = false
            check(BuiltinServerService().onStartCommand(null, 0, 1) == Service.START_NOT_STICKY)
            check(BuiltinLinux.restored == 1 && !BuiltinLinux.armed)
        }
        "explicit-unarmed-not-sticky" -> {
            val service = BuiltinServerService()
            check(service.onStartCommand(Intent(service, BuiltinServerService::class.java), 0, 1) == Service.START_NOT_STICKY)
            check(BuiltinLinux.restored == 0 && BuiltinLinux.rejected == 0)
        }
        "daemon-timeout-async-revocation" -> runtimeRevocation(stop = false)
        "daemon-stop-async-revocation" -> runtimeRevocation(stop = true)
        "daemon-request-threw-after-revocation" -> {
            BuiltinLinux.throwAfterRevoked = true
            val service = BuiltinServerService()
            check(service.onStartCommand(Intent(service, BuiltinServerService::class.java).setAction("stop"), 0, 1) == Service.START_NOT_STICKY)
            check(BuiltinLinux.stopped.await(2, TimeUnit.SECONDS))
            check(BuiltinLinux.drainMode == "revision" && BuiltinLinux.drainRevision == BuiltinLinux.REVISION)
            before("runtime.revoked", "runtime.drain")
        }
        "setup-timeout-async-revocation" -> {
            SetupRunner.blockCancel = true
            val service = SetupService()
            boundedCallback(SetupRunner.cancelPermit, { service.onTimeout(7, 1) }, {
                check(!BuiltinLinux.setupWork && BuiltinLinux.foregroundWork)
                check(service.stopped && !service.foreground)
                check(SetupRunner.cancelEntered.await(1, TimeUnit.SECONDS))
                check(SetupRunner.cancelled.count == 1L)
                before("work.setup.revoke", "setup.cancel")
            })
            check(SetupRunner.cancelled.await(2, TimeUnit.SECONDS))
        }
        "destroy-revokes-kind" -> {
            BuiltinServerService().onDestroy()
            check(!BuiltinLinux.foregroundWork && BuiltinLinux.setupWork)
            BuiltinLinux.foregroundWork = true
            SetupService().onDestroy()
            check(!BuiltinLinux.setupWork && BuiltinLinux.foregroundWork)
            check(BuiltinLinux.drainEntered.count == 1L && SetupRunner.cancelEntered.count == 1L)
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
    ServiceEvents.workers.forEach { worker ->
        worker.join(2000)
        check(!worker.isAlive) { "native callback worker did not finish" }
    }
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
