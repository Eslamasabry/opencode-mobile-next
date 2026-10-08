package io.github.eslamasabry.opencode_mobile

import android.app.ExitRecord
import android.content.Context

private fun running(context: Context) {
    context.getSharedPreferences("oc_background_pause", 0).edit()
        .putString("phase", "running").putString("reason", "none").putLong("at", 1000L).commit()
}

fun main(args: Array<String>) {
    val context = Context()
    val store = BackgroundPauseStore(context)
    val flutter = context.getSharedPreferences(LivePauseReceiver.FLUTTER_PREFERENCES, 0)
    val receipts = context.getSharedPreferences("oc_background_pause", 0)
    when (args.single()) {
        "timeout-reopen" -> {
            check(store.confirmedStarted())
            check(flutter.getBoolean(LivePauseReceiver.FLUTTER_PREFERENCE_KEEP_LIVE, false))
            check(store.timeout())
            check(!flutter.getBoolean(LivePauseReceiver.FLUTTER_PREFERENCE_KEEP_LIVE, true))
            check(receipts.commits == 2)
            val state = BackgroundPauseStore(context.reopen()).status(false)
            check(state["paused"] == true && state["reason"] == "timeLimit")
            check(state["at"] is Long && (state["at"] as Long) > 0L)
            check(state["active"] == false && state["canResume"] == true)
        }
        "task-removal" -> {
            check(store.confirmedStarted())
            store.recordTaskRemoved()
            val state = store.status(true)
            check(state["active"] == true && state["paused"] == false && state["reason"] == "none")
            check(receipts.getString("phase", "") == "running")
        }
        "current-restriction" -> {
            running(context)
            context.manager.isBackgroundRestricted = true
            val observed = store.status(false)
            check(observed["reason"] == "batteryRestricted")
            check((observed["at"] as Long) > 1000L)
            context.manager.isBackgroundRestricted = false
            check(BackgroundPauseStore(context.reopen()).status(false)["reason"] == "interrupted")
        }
        "user-stop" -> {
            running(context)
            val lifecycle = context.getSharedPreferences("oc_lifecycle", 0)
            lifecycle.edit().putLong("seen_exit_timestamp", 999L).commit()
            context.manager.exits = listOf(
                ExitRecord(context.packageName + ":helper", 10, 9000L),
                ExitRecord(context.packageName, 10, 2000L),
            )
            val state = store.status(false)
            check(state["reason"] == "userStopped" && state["at"] == 2000L)
            check(lifecycle.getLong("seen_exit_timestamp", 0L) == 999L)
            check(lifecycle.commits == 1)
        }
        "old-exit-interruption" -> {
            running(context)
            context.manager.exits = listOf(ExitRecord(context.packageName, 10, 999L))
            check(store.status(false)["reason"] == "interrupted")
        }
        "intentional-clear" -> {
            check(store.confirmedStarted())
            check(store.timeout())
            check(store.clear())
            val state = BackgroundPauseStore(context.reopen()).status(false)
            check(state["paused"] == false && state["reason"] == "none" && state["at"] == null)
            check(!flutter.getBoolean(LivePauseReceiver.FLUTTER_PREFERENCE_KEEP_LIVE, true))
        }
        "failed-commit" -> {
            check(store.confirmedStarted())
            receipts.failCommit = true
            check(!store.timeout())
            check(!flutter.getBoolean(LivePauseReceiver.FLUTTER_PREFERENCE_KEEP_LIVE, true))
            val failure = runCatching { BackgroundPauseStore(context.reopen()).status(false) }.exceptionOrNull()
            check(failure is IllegalStateException)
            check(failure.message == "Background state could not be saved or read.")
        }
        "confirmed-start" -> {
            check(store.confirmedStarted())
            check(store.timeout())
            check(receipts.getString("reason", "") == "timeLimit")
            check(store.confirmedStarted())
            val state = BackgroundPauseStore(context.reopen()).status(true)
            check(state["paused"] == false && state["active"] == true)
            check(receipts.getString("phase", "") == "running")
            check(flutter.getBoolean(LivePauseReceiver.FLUTTER_PREFERENCE_KEEP_LIVE, false))
        }
        "reason-eleven" -> {
            running(context)
            context.manager.exits = listOf(ExitRecord(context.packageName, 11, 2000L))
            check(store.status(false)["reason"] == "userStopped")
        }
        else -> error("Unknown harness scenario")
    }
    println("PASS ${args.single()}")
}
