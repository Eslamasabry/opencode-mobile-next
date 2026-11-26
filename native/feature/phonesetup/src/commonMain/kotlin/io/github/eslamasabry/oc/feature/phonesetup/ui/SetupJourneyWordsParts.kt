package io.github.eslamasabry.oc.feature.phonesetup.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentProgress
import io.github.eslamasabry.oc.engine.runtime.setup.ComponentState
import io.github.eslamasabry.oc.engine.runtime.setup.SetupActivity
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupError
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupState
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKill
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SystemKillMessage
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartGigabytes
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartMegabytes
import io.github.eslamasabry.oc.ui.words.res.setupActivityChecking
import io.github.eslamasabry.oc.ui.words.res.setupActivityConfiguring
import io.github.eslamasabry.oc.ui.words.res.setupActivityDownloading
import io.github.eslamasabry.oc.ui.words.res.setupActivityUnpacking
import io.github.eslamasabry.oc.ui.words.res.setupActivityWorking
import io.github.eslamasabry.oc.ui.words.res.setupKilled
import io.github.eslamasabry.oc.ui.words.res.setupKilledMemory
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/**
 * Add tools (from This phone, OpenCode already installed) opens its sheet over This phone's own name,
 * like Flutter's sheet over this_phone_screen.dart: never over the first-run "Set up OpenCode" page,
 * which is for a phone without it. A running add job still shows its progress.
 */
internal fun journeyBody(state: SetupJourneyUi): JourneyBody =
    when {
        state.adding ->
            if (state.progress.state == SetupState.Running) JourneyBody.Job else JourneyBody.Nothing
        state.customizing || state.progress.state == SetupState.Idle -> JourneyBody.Hero
        else -> JourneyBody.Job
    }

/** What the setup page draws under its sheets. */
internal enum class JourneyBody { Nothing, Hero, Job }

/** Flutter setupDurationText/setupStepsText round the registry estimate to the nearest minute. */
internal fun setupPromiseMinutes(seconds: Long): Int =
    (seconds / 60.0)
        .roundToInt()
        .coerceAtLeast(1) // F: lib/ui/screens/phone_setup/phone_setup_selection.dart:63,71

@Composable
internal fun activityWords(activity: SetupActivity): String =
    stringResource(
        when (activity) {
            SetupActivity.Downloading -> Res.string.setupActivityDownloading
            SetupActivity.Unpacking -> Res.string.setupActivityUnpacking
            SetupActivity.Configuring -> Res.string.setupActivityConfiguring
            SetupActivity.Checking -> Res.string.setupActivityChecking
            SetupActivity.Working -> Res.string.setupActivityWorking
        }
    )

/** m:ss, or h:mm:ss past an hour; digits are isolated so a right-to-left page keeps them in order. */
internal fun elapsedClock(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val body = if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "$m:${s.toString().padStart(2, '0')}"
    return "\u2066$body\u2069"
}

/** The job ended because Node.js crashed in every safe mode (the Node install's give-up line, #117). */
internal fun nodeCannotRun(progress: SetupProgress): Boolean =
    progress.state == SetupState.Failed &&
        (progress.error == SetupError.NodeCannotRun ||
            progress.components.any { it.error == SetupError.NodeCannotRun })

private const val OVERDUE_FLOOR_SECONDS = 180

/** The ring that follows the bytes stays short of full until the job itself is done (unpacking and checks remain). */
private const val BYTES_RING_CAP = 0.98f

/**
 * The memories outlive the screen: leaving the set-up page and coming back must not restart the "time left" at a
 * larger number. Few jobs ever run, so the map is capped.
 */
private val journeyMemories = LinkedHashMap<String, JourneyProgressMemory>()

internal fun journeyMemory(jobId: String): JourneyProgressMemory =
    journeyMemories.getOrPut(jobId) {
        if (journeyMemories.size >= 4) journeyMemories.remove(journeyMemories.keys.first())
        JourneyProgressMemory()
    }

/** UI memory from Flutter's _track / _measuredOf: a new job resets it; a new stage resets only its arc. */
internal class JourneyProgressMemory {
    private var measuredKey: Pair<String, String?>? = null
    private var furthest = 0f
    private var remaining: Int? = null

    fun measured(row: ComponentProgress?): Float? {
        if (row?.state != ComponentState.Running) return null
        val fraction = row.fraction?.toFloat() ?: return null
        val key = row.id to row.stage
        furthest = if (key == measuredKey) maxOf(furthest, fraction) else fraction
        measuredKey = key
        return furthest
    }

    private var furthestOverall = 0f
    private var furthestBytes = 0L
    private var furthestRing = 0f

    /**
     * The ring as the share of the job's downloaded bytes (never backwards, never full before the job is done), null
     * when no download has a known size: the same figure the line "x of y MB" under it says.
     */
    fun followBytes(rows: List<ComponentProgress>, total: Long): Float? {
        val (received, whole) = bytes(rows, total) ?: return null
        furthestRing = maxOf(furthestRing, (received.toFloat() / whole).coerceIn(0f, BYTES_RING_CAP))
        return furthestRing
    }

    /** The whole setup's fraction while it runs: never behind what it already showed, whatever stage a step is in. */
    fun overall(progress: SetupProgress): Float? {
        if (progress.state != SetupState.Running) return null
        furthestOverall = maxOf(furthestOverall, progress.overall.toFloat().coerceIn(0f, 1f))
        return furthestOverall
    }

    /** Bytes received of all the steps' downloads together ([total] is the home page's figure); null when unknown. */
    fun bytes(rows: List<ComponentProgress>, total: Long): Pair<Long, Long>? {
        val known = rows.sumOf { it.bytesTotal ?: 0L }
        val whole = maxOf(total, known)
        if (whole <= 0) return null
        val received =
            rows.sumOf { row ->
                when (row.state) {
                    ComponentState.Done, ComponentState.Skipped -> row.bytesTotal ?: 0L
                    else -> row.bytesDone ?: 0L
                }
            }
        furthestBytes = maxOf(furthestBytes, minOf(received, whole))
        return furthestBytes to whole
    }

    fun eta(progress: SetupProgress, registry: List<SetupComponent>): Int? {
        if (progress.state != SetupState.Running) return null
        // A step far past its own estimate has no honest "minutes left": show none (the step clock and the
        // still-working line remain) instead of a number that was wrong and then grows.
        val running = progress.components.firstOrNull { it.state == ComponentState.Running }
        val promised = running?.let { row -> registry.firstOrNull { it.id == row.id }?.estimatedSeconds }
        val onStep = progress.stepElapsedSeconds
        if (promised != null && onStep != null && onStep > maxOf(promised * 2, OVERDUE_FLOOR_SECONDS)) return null
        // The pace-scaled guess only says "enough has happened to guess" (it is null for the first seconds). The
        // number itself is the steps' own sizes: the running step's part not yet done plus every pending step, so it
        // drops as seconds pass and as steps complete, and a step with no size makes it unknown rather than a guess.
        if (progress.etaSeconds == null && remaining == null) return null
        if (progress.components.isEmpty()) {
            // No step is listed: the engine's own figure is all there is, and it still never rises.
            progress.etaSeconds?.let { remaining = remaining?.let { r -> minOf(r, it) } ?: it }
            return remaining
        }
        val sizes = progress.components.filter { it.state == ComponentState.Pending }.map { row ->
            registry.firstOrNull { it.id == row.id }?.estimatedSeconds?.takeIf { it > 0 }
        }
        if (sizes.any { it == null }) return remaining
        val ahead = sizes.sumOf { it ?: 0 }
        val left =
            when {
                running == null -> 0
                promised == null || promised <= 0 -> return remaining
                running.fraction != null -> (promised * (1.0 - running.fraction!!.coerceIn(0.0, 1.0))).toInt()
                onStep != null -> maxOf(0, promised - onStep)
                else -> promised
            }
        val estimate = ahead + left
        remaining = remaining?.let { minOf(it, estimate) } ?: estimate
        return remaining
    }
}

@Composable
internal fun killWords(kill: SystemKill): String =
    stringResource(
        when (kill.message) {
            SystemKillMessage.Closed -> Res.string.setupKilled
            SystemKillMessage.OutOfMemory -> Res.string.setupKilledMemory
        }
    )

@Composable
internal fun journeySize(bytes: Long): String =
    if (bytes >= 1_000_000_000)
        stringResource(
            Res.string.phoneSetupStartGigabytes,
            Numbers.decimal(bytes / 1_000_000_000.0, 1),
        )
    else
        stringResource(
            Res.string.phoneSetupStartMegabytes,
            Numbers.format((bytes / 1_000_000.0).roundToInt().coerceAtLeast(1)),
        )
