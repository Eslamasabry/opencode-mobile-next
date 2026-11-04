package io.github.eslamasabry.oc.engine.runtime.recovery

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget

/** The automatic start of a stopped phone server (Flutter `BuiltinServerStarter.autoStartIfStopped`). */
public interface PhoneAutoStart {
    /** Whether the next [autoStartIfStopped] may start the server. */
    public val available: Boolean

    /** The app opened or came back to the front: one automatic start is allowed again. Never from a failure path. */
    public fun allowAgain()

    /**
     * Starts [profile]'s stopped server, at most once until [allowAgain]. A server already running is left alone and is
     * not a start. [mayStart] is asked after the status read and before every step of the start. True only when it
     * started one that now answers.
     */
    public suspend fun autoStartIfStopped(profile: ProfileId, mayStart: () -> Boolean): Boolean
}

/**
 * [PhoneAutoStart] over the one start path ([PhoneServerStarter], plan 11 §6.2): status through [port], then
 * [PhoneServerStarter.start]. [target] answers the profile's start facts, null when the profile is unreadable.
 * [mayStart] is the start's `stillWanted`: a policy turned off at any step withdraws it.
 */
public class StarterAutoStart(
    private val port: PhoneServerPort,
    private val starter: PhoneServerStarter,
    private val target: suspend (ProfileId) -> StartTarget?,
) : PhoneAutoStart {
    private var used = false
    private var starting = false

    override val available: Boolean get() = !used

    override fun allowAgain() {
        used = false
    }

    override suspend fun autoStartIfStopped(profile: ProfileId, mayStart: () -> Boolean): Boolean {
        if (used || starting) return false
        used = true
        val status = (port.refreshStatus() as? Outcome.Ok)?.value ?: return false
        if (!status.installed || status.serverRunning) return false
        if (!mayStart()) return false
        val facts = target(profile) ?: return false
        starting = true
        try {
            return starter.start(facts, stillWanted = mayStart) == null
        } finally {
            starting = false
        }
    }
}
