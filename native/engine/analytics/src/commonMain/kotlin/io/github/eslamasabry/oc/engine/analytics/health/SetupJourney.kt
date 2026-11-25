package io.github.eslamasabry.oc.engine.analytics.health

import io.github.eslamasabry.oc.engine.analytics.product.ProductSetupStep

/** A setup step's state, as the funnel needs it (the setup engine's own enum is mapped to this by the app). */
public enum class SetupStepState { Pending, Running, Done, Failed, Skipped }

/** A setup job's state. */
public enum class SetupJobState { Idle, Running, Done, Failed, Interrupted, Cancelled }

/** One step of a job. [failKind] is a word of [HealthWords.setupFailKinds], set only for a failed step. */
public data class SetupStepView(val id: String, val state: SetupStepState, val failKind: String? = null)

/** What the funnel remembers across restarts (words and numbers only). */
public data class SetupJourneySaved(
    val startedAtMs: Long,
    val lastStep: String,
    val finished: Boolean,
    val seenAtMs: Long,
    val attempts: Map<String, Int>,
)

/** Where [SetupJourney] keeps [SetupJourneySaved] between runs. */
public interface SetupJourneyMemory {
    public fun load(): SetupJourneySaved?

    public fun save(saved: SetupJourneySaved?)
}

/**
 * The setup funnel (docs/native/analytics/journeys-2026-11-09.md). Fed by the setup engine's progress, it sends, per
 * step, `setup_step_reached` (step, attempt) when the step starts and `linux_setup_step` (result ok, fail, skipped or
 * cancelled, duration range, fail kind) when it ends; for a first setup it also sends `setup_done` (total time range),
 * and `setup_abandoned` / `setup_resumed` when the person leaves an unfinished setup for [ABANDON_AFTER_MS] or more
 * (backgrounded and not running, or the app closed) and comes back. Nothing here carries text.
 */
public class SetupJourney(
    private val now: () -> Long,
    private val memory: SetupJourneyMemory,
    private val flow: HealthFlow = HealthFlow(),
    private val freeGigabytes: () -> Double? = { null },
) {
    private var job: String? = null
    private val seen = HashMap<String, SetupStepState>()
    private val started = HashMap<String, Long>()
    private var saved: SetupJourneySaved? = memory.load()
    private var jobState: SetupJobState = SetupJobState.Idle
    private var awayAt: Long? = null
    private var pendingResume: Pair<String, Long>? = null
    private var doneSent = false

    /** The job had already ended (failed, interrupted or cancelled) when this process first saw it. */
    private var endedOnArrival = false

    /** The saved state as [observe] is changing it; written back once at the end. */
    private var working: SetupJourneySaved? = null

    /** The process started: a setup left unfinished when the app was closed counts as abandoned. */
    public fun coldStart() {
        val s = saved ?: return
        if (s.finished) return
        val gap = (now() - s.seenAtMs).coerceAtLeast(0)
        if (gap >= ABANDON_AFTER_MS) {
            flow.setupAbandoned(s.lastStep, "closed", gap)
            pendingResume = s.lastStep to gap
        }
    }

    public fun onBackground() {
        awayAt = now()
    }

    public fun onForeground() {
        val left = awayAt ?: return
        awayAt = null
        val s = saved ?: return
        if (s.finished || jobState == SetupJobState.Running || jobState == SetupJobState.Done) return
        val gap = (now() - left).coerceAtLeast(0)
        if (gap >= ABANDON_AFTER_MS) {
            flow.setupAbandoned(s.lastStep, "background", gap)
            pendingResume = s.lastStep to gap
        }
    }

    /** The newest progress of the job [jobId]; [firstSetup] is the first-run job. */
    public fun observe(jobId: String?, state: SetupJobState, steps: List<SetupStepView>, firstSetup: Boolean) {
        if (jobId != job) {
            job = jobId
            seen.clear()
            started.clear()
            doneSent = false
            endedOnArrival = state in ENDED_EARLY
        }
        jobState = state
        val at = now()
        working = saved
        for (step in steps) {
            val before = seen[step.id] ?: SetupStepState.Pending
            if (step.state == before) continue
            seen[step.id] = step.state
            when (step.state) {
                SetupStepState.Running -> stepStarted(step, at, firstSetup)
                SetupStepState.Done, SetupStepState.Skipped, SetupStepState.Failed -> stepEnded(step, at)
                else -> Unit
            }
        }
        // A job that ended while a step was running: cancelled by the person, or taken away by the system.
        if (state in ENDED_EARLY) closeRunningSteps(state, at)
        if (state == SetupJobState.Done && !doneSent) finish(at, firstSetup)
        if (working != saved) {
            saved = working
            memory.save(working)
        }
    }

    private fun update(at: Long, change: (SetupJourneySaved) -> SetupJourneySaved) {
        val base = working ?: SetupJourneySaved(at, "", false, at, emptyMap())
        working = change(base).copy(seenAtMs = at)
    }

    private fun stepStarted(step: SetupStepView, at: Long, firstSetup: Boolean) {
        val word = ProductSetupStep.of(step.id).wire
        started[step.id] = at
        var attempt = 1
        update(at) { s ->
            attempt = (s.attempts[word] ?: 0) + 1
            s.copy(
                lastStep = word,
                finished = false,
                startedAtMs = if (s.finished || s.lastStep.isEmpty()) at else s.startedAtMs,
                attempts = s.attempts + (word to attempt),
            )
        }
        flow.setupStepReached(word, attempt)
        pendingResume?.let { (_, gap) ->
            pendingResume = null
            if (firstSetup) flow.setupResumed(word, gap)
        }
    }

    private fun stepEnded(step: SetupStepView, at: Long) {
        val word = ProductSetupStep.of(step.id).wire
        val began = started.remove(step.id)
        val attempt = working?.attempts?.get(word) ?: 1
        val linux = HealthFlow.linuxStepOf(step.id)
        if (linux != null) {
            val elapsed = if (began != null) at - began else 0L
            when {
                // A failure this process never watched is not an ending: a job read again after a restart sends
                // none. A job watched from its start sends it once, with no time when the step was never seen running.
                step.state == SetupStepState.Failed ->
                    if (!endedOnArrival) sendFailed(linux, began, at, attempt, step.failKind ?: "unknown")
                step.state == SetupStepState.Skipped || began == null ->
                    flow.linuxSetupStep(linux, ok = false, elapsedMs = 0, attempt = attempt, notRun = "skipped")
                else ->
                    flow.linuxSetupStep(
                        linux, ok = true, elapsedMs = elapsed, attempt = attempt, freeGigabytes = freeGigabytes(),
                    )
            }
        }
        update(at) { it.copy(lastStep = if (step.state == SetupStepState.Failed) word else it.lastStep) }
    }

    /** A failed step with the time it ran for; one never seen running has no time to send. */
    private fun sendFailed(linux: String, began: Long?, at: Long, attempt: Int, failKind: String) {
        if (began == null) {
            flow.linuxSetupStepUnmeasured(linux, attempt, failKind, freeGigabytes())
        } else {
            flow.linuxSetupStep(
                linux, ok = false, elapsedMs = at - began, attempt = attempt, failKind = failKind,
                freeGigabytes = freeGigabytes(),
            )
        }
    }

    private fun closeRunningSteps(state: SetupJobState, at: Long) {
        for ((id, began) in started.toMap()) {
            val linux = HealthFlow.linuxStepOf(id) ?: continue
            val word = ProductSetupStep.of(id).wire
            val attempt = working?.attempts?.get(word) ?: 1
            started.remove(id)
            if (state == SetupJobState.Cancelled) {
                flow.linuxSetupStep(linux, ok = false, elapsedMs = at - began, attempt = attempt, notRun = "cancelled")
            } else {
                flow.linuxSetupStep(
                    linux, ok = false, elapsedMs = at - began, attempt = attempt, failKind = "killed_by_system",
                    freeGigabytes = freeGigabytes(),
                )
            }
        }
    }

    private fun finish(at: Long, firstSetup: Boolean) {
        doneSent = true
        val begun = working?.startedAtMs ?: at
        val total = (at - begun).coerceAtLeast(0)
        flow.linuxSetupStep("done", ok = true, elapsedMs = total, freeGigabytes = freeGigabytes())
        if (firstSetup) flow.setupDone(total, "phone")
        update(at) { SetupJourneySaved(it.startedAtMs, it.lastStep, true, at, emptyMap()) }
    }

    public companion object {
        private val ENDED_EARLY = setOf(SetupJobState.Cancelled, SetupJobState.Interrupted, SetupJobState.Failed)

        /** Away from an unfinished setup this long (5 minutes) counts as leaving it. */
        public const val ABANDON_AFTER_MS: Long = 5 * 60_000L
    }
}

/** [SetupJourneySaved] as one short string of words and numbers (`started;step;finished;seen;step=n,step=n`). */
public object SetupJourneyCodec {
    public fun encode(s: SetupJourneySaved): String =
        listOf(
            s.startedAtMs, s.lastStep, if (s.finished) 1 else 0, s.seenAtMs,
            s.attempts.entries.joinToString(",") { "${it.key}=${it.value}" },
        ).joinToString(";")

    public fun decode(text: String?): SetupJourneySaved? {
        val parts = text?.split(';') ?: return null
        if (parts.size != 5) return null
        val started = parts[0].toLongOrNull() ?: return null
        val seen = parts[3].toLongOrNull() ?: return null
        val attempts =
            parts[4].split(',').filter { it.isNotEmpty() }.mapNotNull {
                val kv = it.split('=')
                val n = kv.getOrNull(1)?.toIntOrNull()
                if (kv.size == 2 && n != null) kv[0] to n else null
            }.toMap()
        return SetupJourneySaved(started, parts[1], parts[2] == "1", seen, attempts)
    }
}
