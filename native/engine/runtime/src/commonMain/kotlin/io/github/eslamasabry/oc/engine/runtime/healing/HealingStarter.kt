package io.github.eslamasabry.oc.engine.runtime.healing

import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.StartFailure
import io.github.eslamasabry.oc.engine.runtime.server.StartProblem

/**
 * The bookkeeping lib/builtin/builtin_server.dart's `BuiltinServerStarter` keeps around the one start path
 * ([PhoneServerStarter]): which start is running, how many ended answering (all and explicit), and each profile's last
 * failure for the card. An explicit Start first runs [beforeManualStart] (healing's owner claim, which waits for the
 * native owner binding); a claim that fails or is overtaken (another owner, a deletion) launches nothing.
 */
public class HealingStarter(private val starter: PhoneServerStarter) {
    private val failures = mutableMapOf<String, StartFailure>()

    /** Starts that ended answering (any kind). */
    public var readyCount: Int = 0
        private set

    /** Explicit starts (Start, the app-open start) that ended answering: these give the crash budget back. */
    public var manualReadyCount: Int = 0
        private set

    public var manuallyStartedProfileId: String? = null
        private set

    public var starting: Boolean = false
        private set

    /** Set by healing: claims ownership and drains the native binding; false refuses the start. */
    public var beforeManualStart: (suspend (HealingProfile) -> Boolean)? = null

    /** Set by recovery: an explicit start answered (Dart `_starterChanged`). */
    public var onManualReady: (suspend (HealingProfile) -> Unit)? = null

    public fun failureFor(profileId: String): StartFailure? = failures[profileId]

    /** A pending automatic restart was confirmed answering after all: its failure is no longer true. */
    public fun confirmRecovered(profileId: String) {
        failures.remove(profileId)
    }

    /** The person's Start (and setup). */
    public suspend fun start(profile: HealingProfile): StartFailure? {
        if (starting) return StartFailure(StartProblem.Busy)
        starting = true
        val failure =
            try {
                val claim = beforeManualStart
                if (claim != null && !claim(profile)) {
                    StartFailure.interrupted()
                } else {
                    starter.start(profile.target, LaunchMode.Manual)
                }
            } finally {
                starting = false
            }
        record(profile, failure, manual = true)
        if (failure == null) onManualReady?.invoke(profile)
        return failure
    }

    /** The app-open start: [admission] reads the native intent generation just before; null means a Stop came first. */
    public suspend fun startForLaunch(
        profile: HealingProfile,
        admission: suspend () -> Long?,
        stillWanted: () -> Boolean,
    ): StartFailure? {
        if (starting) return StartFailure(StartProblem.Busy)
        starting = true
        val failure =
            try {
                val generation = admission()
                if (generation == null) StartFailure.interrupted() else starter.start(profile.target, LaunchMode.ForAppOpen(generation), stillWanted = stillWanted)
            } finally {
                starting = false
            }
        record(profile, failure, manual = true)
        if (failure == null) onManualReady?.invoke(profile)
        return failure
    }

    /** An automatic crash restart guarded by the native recovery generation. */
    public suspend fun restart(profile: HealingProfile, generation: Long, stillWanted: () -> Boolean): StartFailure? {
        if (starting) return StartFailure(StartProblem.Busy)
        starting = true
        val failure =
            try {
                starter.start(profile.target, LaunchMode.Recovery(generation), stillWanted = stillWanted)
            } finally {
                starting = false
            }
        record(profile, failure, manual = false)
        return failure
    }

    private fun record(profile: HealingProfile, failure: StartFailure?, manual: Boolean) {
        if (failure != null) {
            failures[profile.id] = failure
            return
        }
        failures.remove(profile.id)
        readyCount++
        if (manual) {
            manualReadyCount++
            manuallyStartedProfileId = profile.id
        }
    }
}
