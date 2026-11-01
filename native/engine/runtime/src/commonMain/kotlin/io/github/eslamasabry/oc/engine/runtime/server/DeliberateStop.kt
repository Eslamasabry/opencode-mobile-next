package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * The small key-value store the runtime's per-profile marks live in. The binding is the migrated preferences store
 * (Flutter's `oc.*` keys are adopted unchanged); readers answer null when a value is missing or unreadable.
 */
public interface RuntimeFlags {
    public suspend fun readBoolean(key: String): Boolean?

    public suspend fun writeBoolean(key: String, value: Boolean): Outcome<Unit>

    public suspend fun remove(key: String): Outcome<Unit>
}

/**
 * Remembers that the person stopped the phone's OpenCode themselves (port of lib/builtin/deliberate_stop.dart; plan 11
 * §5.2), so a later Android exit does not say it is "starting again" and nothing in the app brings back a server that
 * was turned off on purpose. Set by Stop on This phone and the status row's Stop, cleared by any start that ended with
 * the server answering (plan 11 §6.2 step 6). Key `oc.phoneServerStopped.<profileId>`: per-profile, so it is registered
 * with `ProfileScope` and swept with the profile (native AGENTS rule 9).
 *
 * The native intent (`wanted = false, userStopped = true`, plan 11 §6.7) is the authority for restarts; this mark is
 * the app-side half the words and the exit report read. An unreadable mark reads as not set, as in Flutter.
 */
public class DeliberateStop(private val flags: RuntimeFlags) {
    public suspend fun mark(profile: ProfileId): Outcome<Unit> = flags.writeBoolean(keyFor(profile), true)

    public suspend fun isMarked(profile: ProfileId): Boolean = flags.readBoolean(keyFor(profile)) ?: false

    public suspend fun clear(profile: ProfileId): Outcome<Unit> = flags.remove(keyFor(profile))

    public companion object {
        public const val KEY_PREFIX: String = "oc.phoneServerStopped."

        public fun keyFor(profile: ProfileId): String = KEY_PREFIX + profile.raw
    }
}
