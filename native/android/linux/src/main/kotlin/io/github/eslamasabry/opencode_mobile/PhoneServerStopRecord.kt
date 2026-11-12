package io.github.eslamasabry.opencode_mobile

import android.content.SharedPreferences

/**
 * Who stopped the phone's OpenCode. It is part of the app, so only the person's own Stop keeps it off (owner rule,
 * 2026-11-11): This phone › Stop, the status notification's Stop, and Undo on an automatic restart. Everything else
 * that stops it (the app replacing it for a restart, an OpenCode update, setup or the phone team; Android refusing or
 * ending the foreground service) only withdraws the automatic background restore (`wanted`); the next app open in front
 * starts it again.
 *
 * The record is device-wide `builtin_server_recovery` [KEY], written under the same lock as the stop's revocation.
 * Nothing recorded is not the person's Stop. Older builds wrote [LEGACY_KEY] for every stop (and read a missing one as
 * the person's), which left a server nobody stopped "off until you start it"; that record cannot tell who stopped it,
 * so it is still written for the background restore's own check but never read as the person's Stop.
 */
object PhoneServerStopRecord {
    const val PREFERENCES: String = "builtin_server_recovery"
    const val KEY: String = "personStopped"
    const val LEGACY_KEY: String = "userStopped"

    /** Why the server is being stopped. */
    enum class Cause {
        /** The person's Stop (This phone, the notification's Stop, Undo of an automatic restart). */
        Person,

        /** The app stops it to start it again (Restart, an OpenCode update, setup, the phone team, folder access). */
        Replace,

        /** The foreground service could not come up (Android refused it), so the runtime is withdrawn. */
        Refused,

        /** Android ended the foreground service at its time limit. */
        SystemTimeout,
    }

    fun byPerson(cause: Cause): Boolean = cause == Cause.Person

    /** [recorded] is the saved value, null when nothing is saved. */
    fun stoppedByPerson(recorded: Boolean?): Boolean = recorded == true

    /** A person's Stop is remembered; any other stop keeps the record as it was (an earlier person's Stop stays). */
    fun afterStop(before: Boolean, cause: Cause): Boolean = before || byPerson(cause)

    /** The app-open start, in front: the person's opening of the app starts it unless the person stopped it. */
    fun openStartAdmitted(resumed: Boolean, stoppedByPerson: Boolean, generationCurrent: Boolean): Boolean =
        resumed && !stoppedByPerson && generationCurrent

    /** Reads the record; a value of the wrong type throws, as SharedPreferences does. */
    fun read(preferences: SharedPreferences): Boolean =
        stoppedByPerson(if (preferences.contains(KEY)) preferences.getBoolean(KEY, false) else null)
}
