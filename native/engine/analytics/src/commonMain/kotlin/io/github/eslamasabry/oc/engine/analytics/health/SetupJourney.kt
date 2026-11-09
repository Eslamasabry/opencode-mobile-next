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
        }
        jobState = state
        val at = now()
        var current = saved
        fun update(change: (SetupJourneySaved) -> SetupJourneySaved) {
            val base = current ?: SetupJourneySaved(at, "", false, at, emptyMap())
            current = change(base).copy(seenAtMs = at)
        }
        for (step in steps) {
            val before = seen[step.id] ?: SetupStepState.Pending
            if (step.state == before) continue
            seen[step.id] = step.state
            val word = ProductSetupStep.of(step.id).wire
            val linux = HealthFlow.linuxStepOf(step.id)
            when (step.state) {
                SetupStepState.Running -> {
                    started[step.id] = at
                    var attempt = 1
                    update { s ->
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
                SetupStepState.Done, SetupStepState.Skipped, SetupStepState.Failed -> {
                    val began = started.remove(step.id)
                    val attempt = current?.attempts?.get(word) ?: 1
                    if (linux != null) {
                        val elapsed = if (began != null) at - began else 0L
                        when {
                            step.state == SetupStepState.Failed ->
                                flow.linuxSetupStep(
                                    linux, ok = false, elapsedMs = elapsed, attempt = attempt,
                                    failKind = step.failKind ?: "unknown", freeGigabytes = freeGigabytes(),
                                )
                            step.state == SetupStepState.Skipped || began == null ->
                                flow.linuxSetupStep(linux, ok = false, elapsedMs = 0, attempt = attempt, notRun = "skipped")
                            else ->
                                flow.linuxSetupStep(
                                    linux, ok = true, elapsedMs = elapsed, attempt = attempt, freeGigabytes = freeGigabytes(),
                                )
                        }
                    }
                    update { it.copy(lastStep = if (step.state == SetupStepState.Failed) word else it.lastStep) }
                }
                else -> Unit
            }
        }
        // A job that ended while a step was running: cancelled by the person, or taken away by the system.
        if (state == SetupJobState.Cancelled || state == SetupJobState.Interrupted || state == SetupJobState.Failed) {
            for ((id, began) in started.toMap()) {
                val linux = HealthFlow.linuxStepOf(id) ?: continue
                val word = ProductSetupStep.of(id).wire
                val attempt = current?.attempts?.get(word) ?: 1
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
        if (state == SetupJobState.Done && !doneSent) {
            doneSent = true
            val begun = current?.startedAtMs ?: at
            val total = (at - begun).coerceAtLeast(0)
            flow.linuxSetupStep("done", ok = true, elapsedMs = total, freeGigabytes = freeGigabytes())
            if (firstSetup) flow.setupDone(total, "phone")
            update { SetupJourneySaved(it.startedAtMs, it.lastStep, true, at, emptyMap()) }
        }
        if (current != saved) {
            saved = current
            memory.save(current)
        }
    }

    public companion object {
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
