package io.github.eslamasabry.opencode_mobile

import android.content.Context

/** A schedule owns only the server process admitted by the retained native lease/watchdog. */
public class ScheduledPhoneRuntime(context: Context) {
    private val host = BuiltinLinux.get(context.applicationContext)

    public enum class Reason {
        AuthRequired,
        Timeout,
        Unavailable,
    }

    public sealed interface Acquisition {
        public class Granted(public val lease: Lease) : Acquisition

        public class Refused(public val reason: Reason) : Acquisition
    }

    /** The native token never leaves this handle or enters a persisted schedule or diagnostic. */
    public class Lease
    internal constructor(
        public val started: Boolean,
        private val host: BuiltinLinux,
        private val token: String,
    ) {
        private var closed = false

        @Synchronized
        public fun release() {
            if (closed) return
            closed = true
            if (token.isNotEmpty()) host.releaseScheduledPhoneServer(token)
        }
    }

    /**
     * Call on the supplied I/O dispatcher. Expected errors are fixed values, never native exception
     * text.
     */
    public fun acquire(profileId: String, password: String, deadlineEpochMs: Long): Acquisition =
        try {
            val acquired = host.acquireScheduledPhoneServer(profileId, password, deadlineEpochMs)
            val started = acquired["started"] == true
            val token = acquired["token"] as? String
            if (token == null || (started && token.isEmpty())) {
                Acquisition.Refused(Reason.Unavailable)
            } else {
                Acquisition.Granted(Lease(started, host, token))
            }
        } catch (e: IllegalStateException) {
            // These are private, app-authored codes from acquireScheduledPhoneServer, never remote
            // text.
            Acquisition.Refused(
                when (e.message) {
                    "auth_required" -> Reason.AuthRequired
                    "timeout" -> Reason.Timeout
                    else -> Reason.Unavailable
                }
            )
        } catch (e: Exception) {
            Acquisition.Refused(Reason.Unavailable)
        }
}
