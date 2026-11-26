package io.github.eslamasabry.opencode_mobile

/**
 * Tracks real forward progress for a running setup step (B-488).
 *
 * Repeated identical `::oc bytes` or `::oc percent` events (such as `oc_fetch` polling a stalled `curl`) do not
 * count as progress, so both the silence watchdog and `setup.json`'s `updatedAt` reflect actual movement.
 */
internal class SetupStepSilence(
    private val timeoutMs: Long = SetupStepRetry.SILENCE_TIMEOUT_MS,
    private val nowMs: () -> Long = { System.nanoTime() / NANOS_PER_MS },
) {
    private val lock = Any()
    private var lastProgressAt: Long = nowMs()
    private var lastStage: String? = null
    private var lastDone: Long? = null
    private var lastTotal: Long? = null
    private var lastPercent: Double? = null

    /** Records a protocol event; returns true only when it represents real forward progress. */
    fun recordEvent(event: SetupProtocol.Event): Boolean =
        synchronized(lock) {
            val changed =
                when (event) {
                    is SetupProtocol.Event.Stage -> recordStage(event.label)
                    is SetupProtocol.Event.Bytes -> recordBytes(event.done, event.total)
                    is SetupProtocol.Event.Percent -> recordPercent(event.value)
                    is SetupProtocol.Event.Version,
                    is SetupProtocol.Event.Fetch,
                    is SetupProtocol.Event.Unpack -> true
                }
            if (changed) lastProgressAt = nowMs()
            changed
        }

    /** Records a plain script output line; non-blank output counts as progress. */
    fun recordOutput(line: String): Boolean =
        synchronized(lock) {
            val changed = line.isNotBlank()
            if (changed) lastProgressAt = nowMs()
            changed
        }

    /** Marks non-line progress (e.g., starting an app-side download or unpack). */
    fun touch() {
        synchronized(lock) { lastProgressAt = nowMs() }
    }

    /** Milliseconds elapsed since the last real progress event. */
    fun silentForMs(): Long = synchronized(lock) { (nowMs() - lastProgressAt).coerceAtLeast(0L) }

    /** True once silence has reached or exceeded [timeoutMs]. */
    fun isTimedOut(): Boolean = silentForMs() >= timeoutMs

    /** Milliseconds remaining until [timeoutMs], or 0 when already timed out. */
    fun remainingMs(): Long = (timeoutMs - silentForMs()).coerceAtLeast(0L)

    private fun recordStage(label: String): Boolean {
        val changed = label != lastStage || lastDone != null || lastPercent != null
        lastStage = label
        lastDone = null
        lastTotal = null
        lastPercent = null
        return changed
    }

    private fun recordBytes(done: Long, total: Long): Boolean {
        val changed = done != lastDone || total != lastTotal
        lastDone = done
        lastTotal = total
        return changed
    }

    private fun recordPercent(value: Double): Boolean {
        val changed = value != lastPercent
        lastPercent = value
        return changed
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

/**
 * Runs one setup script step with at most one automatic retry in compatible Linux mode when it either crashes
 * (SIGSEGV, issue #117) or stops making progress past its wait bound (B-488). Records the Linux mode used so
 * Details always names it when a step cannot finish.
 */
internal object SetupCompatFallback {
    data class StepAttempt(
        val failure: String?,
        val exitCode: Int?,
        val stalled: Boolean,
    )

    data class Outcome(
        val failure: String?,
        val usedCompat: Boolean,
        val retriedInCompat: Boolean,
        val detailLines: List<String>,
        val terminalDetail: String?,
    )

    interface Hooks {
        fun isCancelled(): Boolean

        fun persistCompat()

        fun clearCompat()

        fun onCompatRetry(reasonLine: String)

        fun runOnce(compatible: Boolean): StepAttempt
    }

    fun run(stepId: String, initialCompat: Boolean, hooks: Hooks): Outcome {
        val first = hooks.runOnce(initialCompat)
        return when {
            hooks.isCancelled() -> Outcome("cancelled", initialCompat, retriedInCompat = false, emptyList(), null)
            first.failure == null && !first.stalled ->
                Outcome(null, initialCompat, retriedInCompat = false, emptyList(), null)
            SetupStepRetry.compatibility(first.exitCode, first.stalled, initialCompat, hooks.isCancelled()) ->
                retryOnceInCompat(stepId, first, hooks)
            else -> singleAttemptFailure(stepId, initialCompat, first)
        }
    }

    private fun retryOnceInCompat(stepId: String, first: StepAttempt, hooks: Hooks): Outcome {
        hooks.persistCompat()
        val reason =
            if (first.stalled) {
                "[oc] Step $stepId stopped making progress in normal mode; trying compatibility mode"
            } else {
                "[oc] Step $stepId crashed in normal mode; trying compatibility mode"
            }
        hooks.onCompatRetry(reason)
        val second = hooks.runOnce(true)
        return when {
            hooks.isCancelled() -> {
                hooks.clearCompat()
                Outcome("cancelled", usedCompat = true, retriedInCompat = true, listOf(reason), null)
            }
            second.failure == null && !second.stalled -> {
                val doneLine = SetupStepRetry.linuxModeDetail(stepId, compatible = true, outcome = "finished")
                Outcome(null, usedCompat = true, retriedInCompat = true, listOf(reason, doneLine), doneLine)
            }
            else -> {
                hooks.clearCompat()
                val failure =
                    if (second.stalled) SetupStepRetry.stalledFailure(compatible = true) else second.failure
                val outcomeWord = if (second.stalled) "stopped making progress" else "failed"
                val failLine = SetupStepRetry.linuxModeDetail(stepId, compatible = true, outcome = outcomeWord)
                Outcome(failure, usedCompat = true, retriedInCompat = true, listOf(reason, failLine), failLine)
            }
        }
    }

    private fun singleAttemptFailure(stepId: String, compatible: Boolean, attempt: StepAttempt): Outcome {
        val failure = if (attempt.stalled) SetupStepRetry.stalledFailure(compatible) else attempt.failure
        val outcomeWord = if (attempt.stalled) "stopped making progress" else "failed"
        val failLine =
            if (failure != null) SetupStepRetry.linuxModeDetail(stepId, compatible, outcomeWord) else null
        return Outcome(failure, compatible, retriedInCompat = false, listOfNotNull(failLine), failLine)
    }
}
