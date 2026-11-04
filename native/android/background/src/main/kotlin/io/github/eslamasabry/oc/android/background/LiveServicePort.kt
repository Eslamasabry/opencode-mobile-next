package io.github.eslamasabry.oc.android.background

/**
 * What the native service answered (the Flutter `oc/background` status map). `enabled == null` means the answer did
 * not say (the key was absent); [pause] null means no receipt came with it. [startFailure] is Android's fixed reason
 * a start did not run (`refused`, `receiptUnsaved`, `ackTimeout`), never OS text.
 */
public data class ServiceStatus(
    val enabled: Boolean? = null,
    val active: Boolean = false,
    val notificationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val pause: PauseState? = null,
    val startFailure: String? = null,
) {
    public companion object {
        /**
         * Strict reading of a status map: only a real `true` grants anything (a string "true", 1, a list or null
         * never does). An absent `enabled` stays null; an absent `backgroundPause` stays null.
         */
        public fun fromWire(value: Map<String, Any?>): ServiceStatus =
            ServiceStatus(
                enabled = if (value.containsKey("enabled")) value["enabled"] == true else null,
                active = value["active"] == true,
                notificationGranted = value["notificationGranted"] == true,
                batteryOptimizationIgnored = value["batteryOptimizationIgnored"] == true,
                pause =
                    if (value.containsKey("backgroundPause")) {
                        @Suppress("UNCHECKED_CAST")
                        PauseState.fromWire(value["backgroundPause"] as? Map<String, Any?>)
                    } else {
                        null
                    },
                startFailure = value["startFailure"] as? String,
            )
    }
}

/**
 * A refusal from the native side, as a fixed identifier only (`notification_denied`, `permission_in_progress`,
 * `foreground_service_failed`, …). The Android port only ever produces the constants below; anything else is treated
 * as unknown and never repeated.
 */
public data class ServiceRefusal(val code: String) {
    public companion object {
        public const val NOTIFICATION_DENIED: String = "notification_denied"
        public const val PERMISSION_IN_PROGRESS: String = "permission_in_progress"
        public const val FOREGROUND_SERVICE_FAILED: String = "foreground_service_failed"

        /** No Android host for the request (the Flutter `MissingPluginException`: tests, desktop, no activity). */
        public const val NO_HOST: String = "no_host"
        public const val UNKNOWN: String = "unknown"
    }
}

public sealed interface ServiceReply<out T> {
    public data class Ok<out T>(val value: T) : ServiceReply<T>

    public data class Refused(val refusal: ServiceRefusal) : ServiceReply<Nothing>
}

/**
 * The live-mode service seam (the Flutter `oc/background` channel methods). Implemented on Android by
 * [AndroidLiveServicePort]; faked in tests. Calls never throw on purpose; a throw is treated as [ServiceRefusal.UNKNOWN].
 */
public interface LiveServicePort {
    /** `getBackgroundPause`: the receipt, reconciled now. */
    public suspend fun pause(): ServiceReply<PauseState>

    /**
     * `enable`: asks for notification permission if needed, starts the service and waits a bounded time for its
     * foreground start. An accepted start request is not success: only `active = true` is.
     */
    public suspend fun enable(): ServiceReply<ServiceStatus>

    /** `disable`: clears the receipt, stops the service. */
    public suspend fun disable(): ServiceReply<ServiceStatus>

    /** `getStatus`. */
    public suspend fun status(): ServiceReply<ServiceStatus>

    public suspend fun requestBatteryOptimizationExemption(): ServiceReply<ServiceStatus>
}

/** Live mode exists on this platform (Flutter `platformCapabilities.supportsBackgroundService`). Read on every call. */
public fun interface BackgroundSupport {
    public fun supported(): Boolean
}
