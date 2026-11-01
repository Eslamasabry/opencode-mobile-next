package io.github.eslamasabry.oc.feature.onboarding.presentation

/** The two device-wide string keys first run keeps (bound over `:core:data`'s prefs store by the app graph). */
public interface FirstRunPreferences {
    public suspend fun read(key: String): String?

    public suspend fun write(key: String, value: String)
}

/**
 * What this device remembers about first run (Dart `FirstRun`, `lib/state/first_run.dart`). A device is new only if
 * the welcome (no saved servers) was the first thing it showed; a build upgraded over saved servers never walks the
 * person through first run (B-NAV-017). Same keys and values as Flutter, so the migrated prefs carry over.
 */
public class FirstRun(private val prefs: FirstRunPreferences) {
    /** True from the welcome until the first conversation opened. */
    public suspend fun landingPending(): Boolean = prefs.read(STATE_KEY) == ARMED

    /** Where the saved servers are listed: with none and nothing recorded, this is a new device; first answer wins. */
    public suspend fun observeServers(hasServers: Boolean) {
        if (prefs.read(STATE_KEY) != null) return
        prefs.write(STATE_KEY, if (hasServers) DONE else ARMED)
    }

    /** The shell opened for someone who never saw the welcome. */
    public suspend fun markReturning() {
        if (prefs.read(STATE_KEY) == null) prefs.write(STATE_KEY, DONE)
    }

    /** The first conversation opened: first run is over, and the notification question becomes due. */
    public suspend fun markLanded() {
        prefs.write(STATE_KEY, DONE)
        if (prefs.read(NOTIFY_ASK_KEY) == null) prefs.write(NOTIFY_ASK_KEY, PENDING)
    }

    /** True until the person answers the one notification question; never for someone who skipped first run. */
    public suspend fun notifyAskPending(): Boolean = prefs.read(NOTIFY_ASK_KEY) == PENDING

    public suspend fun answerNotifyAsk(): Unit = prefs.write(NOTIFY_ASK_KEY, ANSWERED)

    public companion object {
        public const val STATE_KEY: String = "oc.firstRun.state"
        public const val NOTIFY_ASK_KEY: String = "oc.firstRun.notifyAsk"
        private const val ARMED = "armed"
        private const val DONE = "done"
        private const val PENDING = "pending"
        private const val ANSWERED = "answered"
    }
}
