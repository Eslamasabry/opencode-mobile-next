package io.github.eslamasabry.opencode_mobile

/**
 * The stop revision a phone-agent helper start is admitted under ([NativeAgentHostAdmission]): a start already in
 * flight is revoked by a stop of the phone's server (the person's, Android's, the idle stop, the setting turned off),
 * never by the server's own start.
 *
 * Galaxy, build 10057, cold start: the helper's process came at +3 s, was gone at +10 s and started again at +14 s. The
 * app starts the phone's server (on the resume) and the helper (on Home's first frame) side by side; the server's start
 * moved the revision the helper's ticket carried, so the helper's next check refused the start it had admitted, its
 * process was stopped and the next try came 5 s later. Not guarded by itself: the caller holds its admission lock.
 */
internal class AgentHostRevision {
    var value: Long = 0L
        private set

    /** The phone server's wanted state was set: only turning it off revokes a helper start in flight. */
    fun serverWanted(wanted: Boolean) {
        value++ // RED RUN ONLY: the base behaviour (a start counted as a stop)
    }

    /** A stop of any cause (the person, Android, the idle stop): every helper start in flight is revoked. */
    fun stopped() {
        value++
    }
}
