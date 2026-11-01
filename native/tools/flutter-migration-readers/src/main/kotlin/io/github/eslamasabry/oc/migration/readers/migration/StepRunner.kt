package io.github.eslamasabry.oc.migration.readers.migration

/**
 * One migration step. [apply] must be idempotent (it can run again after a crash in the middle of it);
 * [verify] must be read-only.
 */
public interface MigrationStep {
    public val id: String

    /** True when the step writes something the Flutter rollback build cannot read (Room import, format change). */
    public val breaksFlutterReadability: Boolean get() = false

    public fun apply()

    public fun verify(): Boolean
}

/**
 * The Flutter-readable copy of the barrier: `flutter.oc.native.flutterReadable` in `FlutterSharedPreferences.xml`.
 * On Android: a blocking `commit()` through the migrator's `MigrationWriter`; [read] re-reads the value.
 */
public interface BarrierPref {
    public fun read(): Boolean?
    /** Returns false when the platform refused the commit. */
    public fun write(value: Boolean): Boolean
}

public sealed interface StepOutcome {
    public data object Verified : StepOutcome
    public data object AlreadyVerified : StepOutcome
    public data class Blocked(val reason: String) : StepOutcome
}

/**
 * Runs steps with the order the audit asks for (findings 4 and 5):
 *
 * 1. **journal** — `attempts + 1`, state `running`, written atomically *before* any write of the step, so a
 *    crash counts as an attempt and three crashes block instead of looping;
 * 2. **barrier first** — a step that breaks Flutter readability lowers the barrier (journal, then the pref,
 *    then a read-back) *before* its first write; the barrier never goes up again in this storage generation;
 * 3. **apply** — the step's own writes;
 * 4. **commit marker** — state `committed` in the journal;
 * 5. **verify** — read-only check; state `verified`. A committed step whose verify fails blocks.
 *
 * Re-running: `verified` → re-verify and skip; `committed` → verify only; `running`/`pending` → apply again.
 */
public class StepRunner(
    private val journal: JournalStore,
    private val barrier: BarrierPref,
    private val faults: FaultPoints = FaultPoints.None,
    private val maxAttempts: Int = 3,
) {
    /**
     * Must run once before the first step that writes preferences (after M1's read-only integrity check:
     * no write may reach a prefs file Android loaded as empty). Makes the barrier durable as `true`, and
     * repairs the pref from the journal after a crash between the two writes.
     */
    public fun initBarrier(): StepOutcome {
        val j = journal.load()
        j.blocked?.let { return StepOutcome.Blocked(it.reason) }
        when (j.flutterReadable) {
            null -> {
                // Journal first: once native has written anything, a missing pref means "uncertain" to the guard.
                j.flutterReadable = true
                journal.save(j)
                faults.reached("barrier:init:journal")
                if (!barrier.write(true) || barrier.read() != true) return block(j, "WriteRefused", "barrier")
                faults.reached("barrier:init:pref")
            }
            true -> if (barrier.read() != true) {
                if (!barrier.write(true) || barrier.read() != true) return block(j, "WriteRefused", "barrier")
            }
            false -> if (barrier.read() != false) {
                // The journal says lowered: the pref must say so too, before anything else runs.
                if (!barrier.write(false) || barrier.read() != false) return block(j, "BarrierNotDurable", "barrier")
            }
        }
        return StepOutcome.Verified
    }

    public fun run(step: MigrationStep): StepOutcome {
        var j = journal.load()
        j.blocked?.let { return StepOutcome.Blocked(it.reason) }
        val rec = j.step(step.id)
        when (rec.state) {
            StepState.Verified -> return if (step.verify()) StepOutcome.AlreadyVerified else block(j, "VerifyRegressed", step.id)
            StepState.Committed -> return finishVerify(step)
            StepState.Failed -> return block(j, "StepCrashed", step.id)
            StepState.Pending, StepState.Running -> Unit
        }
        if (rec.attempts >= maxAttempts) {
            j.steps[step.id] = rec.copy(state = StepState.Failed)
            return block(j, "StepCrashed", step.id)
        }
        if (step.breaksFlutterReadability) {
            lowerBarrier(step.id)?.let { return it }
            j = journal.load()
        }
        j.steps[step.id] = StepRecord(StepState.Running, rec.attempts + 1)
        journal.save(j)
        faults.reached("${step.id}:running")
        step.apply()
        faults.reached("${step.id}:applied")
        journal.update { it.steps[step.id] = StepRecord(StepState.Committed, rec.attempts + 1) }
        faults.reached("${step.id}:committed")
        return finishVerify(step)
    }

    private fun finishVerify(step: MigrationStep): StepOutcome {
        val j = journal.load()
        if (!step.verify()) return block(j, "VerifyFailed", step.id)
        j.steps[step.id] = j.step(step.id).copy(state = StepState.Verified)
        journal.save(j)
        faults.reached("${step.id}:verified")
        return StepOutcome.Verified
    }

    private fun lowerBarrier(stepId: String): StepOutcome? {
        val j = journal.load()
        if (j.flutterReadable != false) {
            j.flutterReadable = false
            journal.save(j)
        }
        faults.reached("$stepId:barrier:journal")
        if (!barrier.write(false) || barrier.read() != false) return block(journal.load(), "BarrierNotDurable", stepId)
        faults.reached("$stepId:barrier:pref")
        return null
    }

    private fun block(j: MigrationJournal, reason: String, step: String?): StepOutcome {
        j.blocked = Blocked(reason, step)
        journal.save(j)
        return StepOutcome.Blocked(reason)
    }

    /** "Try again" on the failure screen: clears the block and the attempt counters of failed steps only. */
    public fun retry() {
        journal.update { j ->
            j.blocked = null
            for ((id, r) in j.steps.toMap()) if (r.state == StepState.Failed || r.state == StepState.Running) j.steps[id] = StepRecord(StepState.Pending, 0)
        }
    }
}

/**
 * The rule the R1 Flutter rollback build applies first in `main()` (`lib/native_return_guard.dart`), mirrored
 * here so both sides are tested against the same table (audit finding 4: one barrier both apps understand;
 * missing or uncertain is blocked once native migration has begun).
 */
public object RollbackGuard {
    public sealed interface Decision {
        public data object Allowed : Decision
        public data class Blocked(val reason: String) : Decision
    }

    /**
     * @param flag `oc.native.flutterReadable` as the Flutter build reads it (`prefs.getBool`), null when absent
     *   or not a bool.
     * @param nativeJournalExists whether `files/native/migration.json` exists (the Flutter build can check the
     *   file through `getApplicationSupportDirectory()`).
     */
    public fun evaluate(flag: Boolean?, nativeJournalExists: Boolean): Decision = when {
        flag == true -> Decision.Allowed
        flag == false -> Decision.Blocked("Incompatible")
        nativeJournalExists -> Decision.Blocked("Uncertain")
        else -> Decision.Allowed // the native app never ran: plain Flutter data
    }
}
