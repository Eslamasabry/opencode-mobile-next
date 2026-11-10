package io.github.eslamasabry.opencode_mobile

import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import io.flutter.plugin.common.MethodCall

/** Method handlers of the scheduled-prompt bridge; they run on the bridge's background executor. */
internal class ScheduledPromptCalls(private val context: Context, private val isClosed: () -> Boolean) {
    private val store = ScheduledPromptStore(context)
    private val scheduler = ScheduledPromptScheduler(context)
    private val tokens = mutableSetOf<String>()
    private val linux get() = BuiltinLinux.get(context.applicationContext)

    /** The value to answer with, or [UNKNOWN] when the method is not ours. */
    fun handle(call: MethodCall): Any? {
        val profile = call.argument<String>("profileId").orEmpty()
        return when (call.method) {
            "readStore" -> store.read(call.argument<String>("key").orEmpty())
            "casStore" -> store.cas(
                call.argument<String>("key").orEmpty(), call.argument<String>("expected"),
                call.argument<String>("value"),
            )
            // Derive the index from the latest CAS envelope under the
            // same lock. A stale UI payload must never undo a worker advance.
            "replaceProfile" -> {
                requireProfile(profile)
                scheduler.replace(profile)
                null
            }
            "cancelProfile" -> {
                requireProfile(profile)
                scheduler.cancelProfile(profile)
                null
            }
            "restoreProfile" -> {
                requireProfile(profile)
                store.restoreProfile(profile)
                scheduler.replace(profile)
                null
            }
            "readReceipts" -> store.receipts(requireProfile(profile))
            "acknowledgeReceipts" -> {
                requireProfile(profile)
                store.acknowledge(profile, call.argument<List<String>>("ids").orEmpty().toSet())
                null
            }
            "status" -> status()
            else -> phone(call, profile)
        }
    }

    private fun requireProfile(profile: String): String {
        require(ScheduledPromptPolicy.validId(profile))
        return profile
    }

    private fun status() = mapOf(
        "supported" to true, "exactAllowed" to scheduler.exactAllowed(),
        "batteryExempt" to context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
        "notificationsAllowed" to context.getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled(),
    )

    private fun phone(call: MethodCall, profile: String): Any? = when (call.method) {
        "activePhoneLease" -> linux.activeScheduledPhoneLease()
        "acquirePhoneServer" -> acquirePhoneServer(call, profile)
        "releasePhoneServer" -> {
            val token = call.argument<String>("token").orEmpty()
            if (synchronized(tokens) { tokens.remove(token) }) linux.releaseScheduledPhoneServer(token)
            null
        }
        else -> UNKNOWN
    }

    private fun acquirePhoneServer(call: MethodCall, profile: String): Map<String, Any> {
        require(ScheduledPromptPolicy.validId(profile) && store.profileExists(profile))
        val runtime = call.argument<String>("runtime")
        require(runtime == "openCode2")
        val deadline = call.argument<Number>("deadlineEpochMs")?.toLong() ?: 0L
        require(deadline > System.currentTimeMillis())
        val lease = linux.acquireScheduledPhoneServer(profile, call.argument<String>("password").orEmpty(), deadline)
        val token = lease["token"] as String
        synchronized(tokens) { tokens.add(token) }
        if (isClosed()) {
            linux.releaseScheduledPhoneServer(token)
            error("unavailable")
        }
        return lease
    }

    fun releaseAll() {
        synchronized(tokens) { tokens.toList().also { tokens.clear() } }.forEach {
            runCatching { linux.releaseScheduledPhoneServer(it) }
        }
    }

    companion object {
        /** Sentinel answer for a method the bridge does not implement. */
        val UNKNOWN = Any()
    }
}
