package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The two big downloads the phone does by itself after an app update. */
public enum class HomeDownloadKind {
    /** The phone's OpenCode server binary. */
    OpenCode,

    /** The agents' helper (Paseo) and what it needs. */
    Helper,
}

/** Where one of those downloads is. */
public sealed interface HomeDownloadPhase {
    /** Held back because the network is mobile data (or cannot be told); nothing downloads. */
    public data object Waiting : HomeDownloadPhase

    /** Downloading now, on any network. [percent] is null when the share done is not known. */
    public data class Running(val percent: Int?, val sizeMb: Int? = null) : HomeDownloadPhase

    /** It ended without finishing. The words are plain; technical text stays out of the line. */
    public data object Failed : HomeDownloadPhase

    /**
     * Only agent updates wait for Wi-Fi (chosen on the sheet, held on mobile data; they start by themselves on Wi-Fi).
     * No own line (OpenCode, the helper) is waiting, so this is the whole line; its action is the yes for exactly these agents.
     */
    public data class AgentsWaiting(val held: HomeHeldAgents) : HomeDownloadPhase

    /**
     * The update is ready but has waited a while for the phone to be free ([why]); it installs by itself then. Said
     * only after a short bounded wait, so a hold is never silent for long. Nothing to tap: it needs no choice.
     */
    public data class Held(val why: HomeHold) : HomeDownloadPhase
}

/** Why a ready update waits (the plain words are in the strings, one per reason). */
public enum class HomeHold {
    Reply,
    PhoneNotReady,

    /** The agents' update waits: until the agents are idle, for a setup job, for a server move, or its read failed. */
    AgentsIdle,
    AgentsSetup,
    AgentsSwitch,
    AgentsRead,

    /** The agents' versions are being checked (the first look after an app update takes longer than a short wait). */
    AgentsChecking,
}

/** Agent updates the person chose that wait for Wi-Fi: how many, and their size as the update sheet shows it. */
public data class HomeHeldAgents(val count: Int, val megabytes: Int)

/** One download's state; [megabytes] is the honest "about" size in [HomeDownloadSizes]. */
public data class HomeDownloadFact(
    val kind: HomeDownloadKind,
    val phase: HomeDownloadPhase,
    val megabytes: Int = HomeDownloadSizes.of(kind),
    /** A failed download that stopped for want of room: what it needs and what the phone has. */
    val short: HomeShortSpace? = null,
    /** The agent being installed, by its name, while the agents' update runs ("Updating Claude Code"). */
    val name: String? = null,
    /** More agent updates wait for Wi-Fi beside this one: said as a second sentence of the same row. */
    val moreAgents: HomeHeldAgents? = null,
    /** The shared parts (the Agent helper) ride with this OpenCode update: one line, one size, one yes. */
    val withHelper: Boolean = false,
)

/** The room a stopped download needs and the room the phone has, in megabytes, with the agent's own name. */
public data class HomeShortSpace(val name: String, val neededMb: Int, val freeMb: Int)

/**
 * The sizes the line and the ask say, in one place. They are measured, not read from a release manifest: on the
 * 2.1.5 to 3.0.0 upgrade walk (emulator, 2026-11) the phone's OpenCode refresh received about 126 MB and the agents'
 * helper's first install (`npm ci`) about 333 MB. Said as "about 130 MB" and "about 330 MB".
 */
public object HomeDownloadSizes {
    public const val OPEN_CODE_MB: Int = 130
    public const val HELPER_MB: Int = 330

    /**
     * A size in decimal megabytes (1 MB = 1,000,000 bytes, the unit the update sheet writes), as the lines say it:
     * whole MB below 100, then to the nearest 10 ("about 660 MB").
     */
    public fun roundedMb(bytes: Long): Int {
        val mb = bytes / DECIMAL_MB
        val step = if (mb < ROUND_FROM_MB) 1.0 else TEN
        return (kotlin.math.round(mb / step) * step).toInt()
    }

    private const val DECIMAL_MB = 1_000_000.0
    private const val ROUND_FROM_MB = 100.0
    private const val TEN = 10.0

    public fun of(kind: HomeDownloadKind): Int =
        when (kind) {
            HomeDownloadKind.OpenCode -> OPEN_CODE_MB
            HomeDownloadKind.Helper -> HELPER_MB
        }

    /** The size [of] says, in bytes: the decimal megabyte `PhoneSizes` reads (B-AGENT-SIZE-001). */
    public fun bytesOf(kind: HomeDownloadKind): Long = of(kind) * BYTES_PER_MB

    /** A size in bytes said in whole megabytes, rounded up (what a download needs). */
    public fun megabytesUp(bytes: Long): Int = ((bytes + BYTES_PER_MB - 1) / BYTES_PER_MB).toInt()

    /** A size in bytes said in whole megabytes, rounded down (what the phone has). */
    public fun megabytesDown(bytes: Long): Int = (bytes / BYTES_PER_MB).toInt()

    private const val BYTES_PER_MB: Long = 1_000_000L
}

/** Whose words the line shows ([HomeDownloadRules.placeWins]). */
public enum class HomeLineWinner { Stage, Download, None }

public object HomeDownloadRules {
    /**
     * The one download the Home line talks about: one that downloads now first, then one that failed, then one that
     * waits for Wi-Fi; OpenCode before the helper. Null when nothing is happening.
     */
    public fun shown(facts: List<HomeDownloadFact>): HomeDownloadFact? =
        facts
            .sortedWith(compareBy({ rank(it.phase) }, { if (it.short != null) 0 else 1 }, { it.kind.ordinal }))
            .firstOrNull()

    /**
     * Home's one place for "what is happening to this phone" is the Recent label's end (stage words, or the download).
     * Which says its words when both have some: a download that is running (the share done is the news) wins, then
     * the start-up stage (it is what stands between the person and their agents and ends by itself in seconds), then a
     * download that failed or waits for Wi-Fi (both stay until the person acts, so they show the moment the stage
     * ends). Nothing is lost: the loser's words come back; nothing is stacked.
     */
    public fun placeWins(stage: HomeStage?, download: HomeDownloadFact?): HomeLineWinner =
        when {
            download?.phase is HomeDownloadPhase.Running -> HomeLineWinner.Download
            stage != null -> HomeLineWinner.Stage
            download != null -> HomeLineWinner.Download
            else -> HomeLineWinner.None
        }

    /**
     * The agents' update line ("Agent updates ready · Review", or an agent that was not removed) takes the download's
     * place unless a download is running now (its share done is the news); it stays until the person answers.
     */
    public fun agentLineWins(download: HomeDownloadFact?, agentLine: Boolean): Boolean =
        agentLine && download?.phase !is HomeDownloadPhase.Running

    /** [placeWins] with the agents' update line: it waits for the start-up stage, as a download that waits does. */
    public fun placeWins(stage: HomeStage?, download: HomeDownloadFact?, agentLine: Boolean): HomeLineWinner =
        when {
            !agentLineWins(download, agentLine) -> placeWins(stage, download)
            stage != null -> HomeLineWinner.Stage
            else -> HomeLineWinner.Download
        }

    private const val HELD_RANK = 3

    private fun rank(phase: HomeDownloadPhase): Int =
        when (phase) {
            is HomeDownloadPhase.Running -> 0
            HomeDownloadPhase.Failed -> 1
            HomeDownloadPhase.Waiting, is HomeDownloadPhase.AgentsWaiting -> 2
            is HomeDownloadPhase.Held -> HELD_RANK
        }
}

/** What the agents' automatic update is doing, as far as Home cares. */
public enum class HomeHelperStep { None, Waiting, Updating, Failed }

/** The agents' install job, as far as Home cares. */
public enum class HomeInstallPhase { None, Installing, Failed }

/** Turns the two updates' readings into the facts Home shows (pure: the app only supplies the readings). */
public object HomeDownloadMapping {
    /**
     * The OpenCode update that is ready but held back ([why]) says so after the facts, only when OpenCode has no
     * line of its own (running, failed or waiting for Wi-Fi): one line per download, never stacked.
     */
    public fun withHeld(facts: List<HomeDownloadFact>, why: HomeHold?): List<HomeDownloadFact> =
        if (why == null || facts.any { it.kind == HomeDownloadKind.OpenCode }) {
            facts
        } else {
            facts + HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Held(why))
        }

    /**
     * OpenCode waits for the shared parts the sheet lists (the Agent helper): the helper's own waiting line would
     * replace OpenCode's and hide what the yes starts. One line names both, with the one total ([totalMegabytes], the
     * sheet's sizes added; null adds the helper's usual size).
     */
    public fun openCodeWithHelper(facts: List<HomeDownloadFact>, totalMegabytes: Int?): List<HomeDownloadFact> {
        val openCode =
            facts.firstOrNull { it.kind == HomeDownloadKind.OpenCode && it.phase == HomeDownloadPhase.Waiting }
        return if (totalMegabytes == null || openCode == null) {
            facts
        } else {
            facts
                .filterNot { it.kind == HomeDownloadKind.Helper && it.phase == HomeDownloadPhase.Waiting }
                .map { if (it === openCode) it.copy(withHelper = true, megabytes = totalMegabytes) else it }
        }
    }

    /**
     * Agent updates held on mobile data are said in the row that already speaks about waiting: a second sentence on an
     * own line that waits for Wi-Fi (OpenCode, the helper), or the whole line when nothing else waits. No new row.
     */
    public fun withHeldAgents(facts: List<HomeDownloadFact>, held: HomeHeldAgents?): List<HomeDownloadFact> =
        when {
            held == null || held.count <= 0 -> facts
            facts.any { it.phase == HomeDownloadPhase.Waiting } ->
                facts.map { if (it.phase == HomeDownloadPhase.Waiting) it.copy(moreAgents = held) else it }
            else -> facts + HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.AgentsWaiting(held))
        }

    public fun facts(
        openCodeWaiting: Boolean,
        openCodeRunning: Boolean,
        openCodeFailed: Boolean,
        openCodePercent: Int?,
        helper: HomeHelperStep,
        helperInstall: HomeInstallPhase,
        helperPercent: Int?,
    ): List<HomeDownloadFact> = buildList {
        when {
            openCodeRunning ->
                add(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Running(openCodePercent?.coerceIn(0, 100))))
            openCodeFailed -> add(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Failed))
            openCodeWaiting -> add(HomeDownloadFact(HomeDownloadKind.OpenCode, HomeDownloadPhase.Waiting))
        }
        when (helper) {
            HomeHelperStep.None -> Unit
            HomeHelperStep.Waiting -> add(HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Waiting))
            HomeHelperStep.Updating,
            HomeHelperStep.Failed ->
                add(
                    HomeDownloadFact(
                        HomeDownloadKind.Helper,
                        when (helperInstall) {
                            HomeInstallPhase.Failed -> HomeDownloadPhase.Failed
                            HomeInstallPhase.Installing -> HomeDownloadPhase.Running(helperPercent?.coerceIn(0, 100))
                            HomeInstallPhase.None ->
                                if (helper == HomeHelperStep.Failed) {
                                    HomeDownloadPhase.Failed
                                } else {
                                    HomeDownloadPhase.Running(null)
                                }
                        },
                    )
                )
        }
    }
}

/**
 * What Home needs from the phone's automatic updates: what each is doing, "Download now" on the current network once,
 * and Try again. :androidApp binds it; closed by default.
 */
public interface HomeDownloads {
    public val facts: StateFlow<List<HomeDownloadFact>>

    /** The person chose Download now on mobile data: that one download runs on this network, once. */
    public fun downloadNow(kind: HomeDownloadKind)

    /**
     * Starts [kind] again after it failed. False when the network is mobile data (or cannot be told): nothing started
     * and the person is asked first.
     */
    public fun tryAgain(kind: HomeDownloadKind): Boolean

    /**
     * The person tapped "Download on mobile data" on the agents-only line: the agents that line names download on this
     * network, once, and nothing else gains the yes.
     */
    public fun downloadHeldAgents(): Unit = Unit
}

@ContributesBinding(AppScope::class)
@Inject
public class NoHomeDownloads : HomeDownloads {
    override val facts: StateFlow<List<HomeDownloadFact>> = MutableStateFlow(emptyList<HomeDownloadFact>()).asStateFlow()

    override fun downloadNow(kind: HomeDownloadKind): Unit = Unit

    override fun tryAgain(kind: HomeDownloadKind): Boolean = true
}
