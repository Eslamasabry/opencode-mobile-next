package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.engine.runtime.host.RuntimeCall
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Why opening the app leaves the phone server alone. */
public enum class LaunchLeave {
    /** Not in front, another server selected or connected, a start already running, or restart-on-phone is off. */
    NotNow,

    /** The status could not be read. */
    StatusUnreadable,
    NotInstalled,

    /** The person stopped it (Stop, the row's Stop OpenCode, the service timeout): it stays stopped (plan 11 §6.4 R6). */
    PersonStopped,

    /** The idle owner holds it: only a foreground resume brings it back (R6). */
    IdleHolds,

    /** The native recovery owns it: a scheduled retry, or a live native-owned server (never superseded). */
    NativeRecoveryOwns,

    /** It answers our password: connect, start nothing. */
    Answering,

    /** The once-per-open start was used. */
    AlreadyUsed,

    /** The last start needs the app restarted (ownership unknown): nothing is tried. */
    NeedsAppRestart,
}

/** The next step of the app-open start. */
public sealed interface LaunchStep {
    public data class Leave(val reason: LaunchLeave) : LaunchStep

    public data object Start : LaunchStep

    /** A server runs but has not answered: probe again until [until], then replace it. */
    public data class Probe(val until: Instant) : LaunchStep
}

/**
 * The app-open rules (port of phone_server_healing.dart `_launchStartWanted`; plan 11 §6.2, §6.4 R6, R9): pure, so
 * every branch is a table test. Opening the app is the person's own act, like Start: a stopped server is started even
 * past an exhausted crash budget. The person's Stop always wins.
 */
public object LaunchStartRules {
    /** A tracked OpenCode younger than this may still be booting; one this old that does not answer is stale. */
    public val staleAfter: Duration = 90.seconds

    /** How long to wait on a server whose age the phone does not report before counting it as stale. */
    public val unknownAgeGrace: Duration = 30.seconds

    public fun firstLook(status: RuntimeStatus?, now: Instant): LaunchStep {
        val held = held(status)
        if (held != null) return LaunchStep.Leave(held)
        status!!
        if (status.serverRecoveryAuthority && (status.serverRecoveryScheduled || status.serverRunning)) {
            return LaunchStep.Leave(LaunchLeave.NativeRecoveryOwns)
        }
        if (!status.serverRunning) return LaunchStep.Start
        val uptime = status.serverUptime
        val wait =
            when {
                uptime == null -> unknownAgeGrace
                uptime < staleAfter -> staleAfter - uptime
                else -> Duration.ZERO
            }
        return LaunchStep.Probe(now + wait)
    }

    /** After one probe of a running server that had not answered. */
    public fun afterProbe(answer: ProbeAnswer, status: RuntimeStatus?, now: Instant, until: Instant): LaunchStep {
        if (answer == ProbeAnswer.Answered) return LaunchStep.Leave(LaunchLeave.Answering)
        val held = held(status)
        if (held != null) return LaunchStep.Leave(held)
        status!!
        if (status.serverRecoveryAuthority) return LaunchStep.Leave(LaunchLeave.NativeRecoveryOwns)
        // A server that rejects our password never starts accepting it.
        if (answer == ProbeAnswer.NeedsPassword) return LaunchStep.Start
        if (!status.serverRunning) return LaunchStep.Start
        return if (now >= until) LaunchStep.Start else LaunchStep.Probe(until)
    }

    /** The checks every step repeats: readable, installed, still wanted (never restart what the person stopped), idle. */
    private fun held(status: RuntimeStatus?): LaunchLeave? =
        when {
            status == null -> LaunchLeave.StatusUnreadable
            !status.installed -> LaunchLeave.NotInstalled
            !status.serverRestartWanted -> LaunchLeave.PersonStopped
            status.idleBlocksLaunch -> LaunchLeave.IdleHolds
            else -> null
        }
}

/** How one app-open start ended: [failure] is kept for the card; null [leave] and [failure] means it answered. */
public data class AppOpenResult(val starts: Int, val leave: LaunchLeave? = null, val failure: StartFailure? = null)

/**
 * The app-open start (port of phone_server_healing.dart `startForLaunch`/`_launchStart`; plan 11 §6.2, R9 (c)): once
 * per app open, a stopped phone server is started for the person, with one more try after a fast failure; a timeout is
 * not tried again, and a failed crash restart counts as the first try. [mayAct] is the shell's gate (in front, this
 * server selected and not connected, its profile readable, `restartPhoneServer` allowed); it is asked before every
 * step. Each start reads the native intent generation again and launches with [LaunchMode.ForAppOpen], so a Stop that
 * lands at any point wins (B-LINUX-151, -152).
 */
public class AppOpenStart(
    private val port: PhoneServerPort,
    private val starter: PhoneServerStarter,
    private val clock: Clock,
    private val mayAct: () -> Boolean,
    public val retryDelay: Duration = 2.seconds,
    public val probeEvery: Duration = 2.seconds,
) {
    private var used = false

    /** A new app open (the shell's first resume after the app left): the once-per-open start is available again. */
    public fun allowAgain() {
        used = false
    }

    /** [previous] is the last start's failure for this profile (a failed crash restart counts as the first try). */
    public suspend fun run(target: StartTarget, previous: StartFailure? = null): AppOpenResult {
        if (used) return AppOpenResult(0, LaunchLeave.AlreadyUsed)
        used = true
        if (previous?.problem == StartProblem.ComponentRecovery) return AppOpenResult(0, LaunchLeave.NeedsAppRestart)
        var tries = if (previous == null) 2 else 1
        var starts = 0
        var failure: StartFailure? = previous
        while (tries > 0) {
            val leave = wanted(target.password)
            if (leave != null) return AppOpenResult(starts, leave, failure.takeIf { starts > 0 })
            tries--
            starts++
            val gate = admission() ?: return AppOpenResult(starts, failure = StartFailure.interrupted())
            failure = starter.start(target, LaunchMode.ForAppOpen(gate), stillWanted = mayAct)
            if (failure == null) return AppOpenResult(starts)
            if (!failure.retryable || tries == 0) break
            delay(retryDelay)
        }
        return AppOpenResult(starts, failure = failure)
    }

    /** Null when a start is wanted now; else why not. Probes a running server that has not answered. */
    private suspend fun wanted(password: String): LaunchLeave? {
        if (!mayAct()) return LaunchLeave.NotNow
        var step = LaunchStartRules.firstLook(status(), clock.now())
        while (true) {
            when (val s = step) {
                is LaunchStep.Leave -> return s.reason
                LaunchStep.Start -> return null
                is LaunchStep.Probe -> {
                    val answer = port.probe(password)
                    if (!mayAct()) return LaunchLeave.NotNow
                    step = LaunchStartRules.afterProbe(answer, status(), clock.now(), s.until)
                    if (step is LaunchStep.Probe) delay(probeEvery)
                }
            }
        }
    }

    /** The native intent generation this start may act on, read again just before it; null when a Stop came first. */
    private suspend fun admission(): Long? {
        if (!mayAct()) return null
        val status = status() ?: return null
        if (!mayAct() || !status.serverRestartWanted || status.idleBlocksLaunch) return null
        return status.serverRecoveryGeneration
    }

    private suspend fun status(): RuntimeStatus? = (port.refreshStatus() as? RuntimeCall.Done)?.value
}
