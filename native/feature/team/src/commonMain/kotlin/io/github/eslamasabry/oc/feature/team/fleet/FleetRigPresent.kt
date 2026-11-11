package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetJob
import io.github.eslamasabry.oc.engine.team.fleet.FleetJobTrigger
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetPolicy
import io.github.eslamasabry.oc.engine.team.fleet.FleetProject
import io.github.eslamasabry.oc.engine.team.fleet.FleetProjectAction
import io.github.eslamasabry.oc.engine.team.fleet.FleetReceipt
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// ---- a project of a computer's team on its own page (Dart `TeamRigScreen`, `teamJobWhen`, `TeamPolicyBlock`)

/** Where a write stands for the person (Dart `teamControlReceipt`): it never says "done" before the host confirmed it. */
public enum class FleetWriteStatus { Sending, Confirmed, NotConfirmed, Refused }

/** One write's receipt as a page shows it. [hostWords] are the host's own and technical: under Details only. */
public data class FleetWrite(val status: FleetWriteStatus, val hostWords: String? = null) {
    public companion object {
        public val Sending: FleetWrite = FleetWrite(FleetWriteStatus.Sending)

        /** Accepted is confirmed; a transport failure ("may have landed") is not confirmed; a refusal is refused. */
        public fun of(receipt: FleetReceipt): FleetWrite =
            when (receipt.status) {
                ReceiptStatus.Accepted -> FleetWrite(FleetWriteStatus.Confirmed)
                ReceiptStatus.Pending -> FleetWrite(FleetWriteStatus.NotConfirmed, receipt.message)
                ReceiptStatus.Rejected -> FleetWrite(FleetWriteStatus.Refused, receipt.message)
            }
    }
}

/** When a scheduled job runs, in the person's words (Dart `teamJobWhen`): never a cron expression or a duration string. */
public sealed interface FleetJobWhen {
    public data object OnSchedule : FleetJobWhen

    public data object Repeats : FleetJobWhen

    public data object Event : FleetJobWhen

    public data object Manual : FleetJobWhen

    public data object Webhook : FleetJobWhen

    public data object Condition : FleetJobWhen

    public data object Other : FleetJobWhen

    public data class EverySeconds(val n: Int) : FleetJobWhen

    public data class EveryMinutes(val n: Int) : FleetJobWhen

    public data object EveryHour : FleetJobWhen

    public data class EveryHours(val n: Int) : FleetJobWhen

    /** [time] is `HH:mm`. */
    public data class EveryDayAt(val time: String) : FleetJobWhen

    public data class EveryWeekdayAt(val time: String) : FleetJobWhen

    /** [day] is 0 (Sunday) to 6 (Saturday). */
    public data class EveryDowAt(val day: Int, val time: String) : FleetJobWhen
}

public object FleetJobWords {
    private val FIELDS = Regex("\\s+")
    private val STEP = Regex("^\\*/(\\d+)$")
    private val INTERVAL = Regex("^\\s*(\\d+)\\s*(s|m|h)\\s*$")

    public fun whenOf(job: FleetJob): FleetJobWhen =
        when (job.trigger) {
            FleetJobTrigger.Cron -> cron(job.schedule)
            FleetJobTrigger.Interval -> interval(job.interval)
            FleetJobTrigger.Event -> FleetJobWhen.Event
            FleetJobTrigger.Manual -> FleetJobWhen.Manual
            FleetJobTrigger.Webhook -> FleetJobWhen.Webhook
            FleetJobTrigger.Condition -> FleetJobWhen.Condition
            FleetJobTrigger.Other -> FleetJobWhen.Other
        }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    /** Dart `_cron`: the simple expressions in words, everything else "On a schedule". */
    internal fun cron(expression: String?): FleetJobWhen {
        val fields = expression?.trim()?.split(FIELDS)
        if (fields == null || fields.size != 5) return FleetJobWhen.OnSchedule
        val (minute, hour, dayOfMonth, month, dayOfWeek) = fields
        val m = minute.toIntOrNull()
        val h = hour.toIntOrNull()
        if (!(dayOfMonth == "*" && month == "*")) return FleetJobWhen.OnSchedule
        if (hour == "*" && dayOfWeek == "*") {
            val every = STEP.matchEntire(minute)?.groupValues?.get(1)?.toIntOrNull()
            if (every != null && every > 0) return FleetJobWhen.EveryMinutes(every)
            if (m == 0) return FleetJobWhen.EveryHour
            if (minute == "*") return FleetJobWhen.EveryMinutes(1)
        }
        val everyHours = STEP.matchEntire(hour)?.groupValues?.get(1)?.toIntOrNull()
        if (m == 0 && everyHours != null && dayOfWeek == "*") {
            if (everyHours > 0) return FleetJobWhen.EveryHours(everyHours)
        }
        if (m == null || h == null || m > 59 || h > 23 || m < 0 || h < 0) return FleetJobWhen.OnSchedule
        val time = "${two(h)}:${two(m)}"
        if (dayOfWeek == "*") return FleetJobWhen.EveryDayAt(time)
        if (dayOfWeek == "1-5") return FleetJobWhen.EveryWeekdayAt(time)
        val day = dayOfWeek.toIntOrNull()
        if (day == null || day < 0 || day > 7) return FleetJobWhen.OnSchedule
        // 0 and 7 are both Sunday in cron.
        return FleetJobWhen.EveryDowAt(day % 7, time)
    }

    /** Dart `_interval`: `30s`, `5m`, `2h`. */
    internal fun interval(text: String?): FleetJobWhen {
        val match = INTERVAL.matchEntire(text ?: "") ?: return FleetJobWhen.Repeats
        val n = match.groupValues[1].toIntOrNull() ?: return FleetJobWhen.Repeats
        if (n == 0) return FleetJobWhen.Repeats
        return when (match.groupValues[2]) {
            "s" -> FleetJobWhen.EverySeconds(n)
            "m" -> FleetJobWhen.EveryMinutes(n)
            else -> FleetJobWhen.EveryHours(n)
        }
    }
}

public data class FleetJobRow(val id: String, val name: String, val enabled: Boolean, val whenWords: FleetJobWhen, val write: FleetWrite? = null)

/** Reading the host's scheduled jobs (Dart `scheduledJobList` / `scheduledJobsError` / `scheduledJobsLoading`). */
public enum class FleetJobsRead { Idle, Loading, Failed }

public enum class FleetRigConfirm { Pause, Delete }

public enum class FleetRigKind {
    /** Looking for the team, nothing read yet. */
    Starting,

    /** The team could not be found at its address. */
    Failed,

    /** The computer no longer lists this project (Dart `team-rig-gone`). */
    Gone,

    Ready,
}

/** What the page remembers beside the engine's state: the question being asked, the writes in flight and their answers. */
public data class FleetRigLocal(
    val confirming: FleetRigConfirm? = null,
    val busy: Boolean = false,
    val write: FleetWrite? = null,
    /** The act [write] belongs to, so Try again repeats that one. */
    val lastAction: FleetProjectAction? = null,
    val jobsRead: FleetJobsRead = FleetJobsRead.Idle,
    val jobWrites: Map<String, FleetWrite> = emptyMap(),
    val policy: FleetPolicy? = null,
)

public data class FleetRigUi(
    val kind: FleetRigKind = FleetRigKind.Starting,
    val projectId: String = "",
    val name: String = "",
    val paused: Boolean = false,
    val directory: String? = null,
    /** Pause and Resume are offered (Dart `controlProject`). */
    val canControl: Boolean = false,
    /** Delete is offered (Dart `controlProjectRemove`). */
    val canRemove: Boolean = false,
    val busy: Boolean = false,
    val write: FleetWrite? = null,
    val confirming: FleetRigConfirm? = null,
    /** The scheduled-jobs section is part of the page (the host lists jobs). */
    val jobsShown: Boolean = false,
    val jobsLoading: Boolean = false,
    val jobsFailed: Boolean = false,
    /** The jobs have been read: [mine] and [team] are the answer, even when empty. */
    val jobsRead: Boolean = false,
    val canSwitchJobs: Boolean = false,
    val mine: ImmutableList<FleetJobRow> = persistentListOf(),
    /** Jobs of the whole team (no project). */
    val team: ImmutableList<FleetJobRow> = persistentListOf(),
    val policy: FleetPolicy? = null,
)

/** Pure mapping from a computer's team to one project's page (tested without Compose). */
public object FleetRigPresent {
    public fun page(fleet: FleetState, projectId: String, local: FleetRigLocal): FleetRigUi {
        if (fleet.phase == FleetPhase.Failed && !fleet.hasData) return FleetRigUi(FleetRigKind.Failed, projectId)
        if (!fleet.hasData) return FleetRigUi(FleetRigKind.Starting, projectId)
        val project: FleetProject = fleet.snapshot.projects.firstOrNull { it.id == projectId } ?: return FleetRigUi(FleetRigKind.Gone, projectId)
        val caps: FleetCapabilities = fleet.capabilities
        val jobs = fleet.jobs
        fun row(job: FleetJob) = FleetJobRow(job.id, job.name, job.enabled, FleetJobWords.whenOf(job), local.jobWrites[job.id])
        return FleetRigUi(
            kind = FleetRigKind.Ready,
            projectId = project.id,
            name = project.name,
            paused = project.suspended,
            directory = project.directory?.takeIf { it.isNotEmpty() },
            canControl = caps.controlProject,
            canRemove = caps.controlProjectRemove,
            busy = local.busy,
            write = local.write,
            confirming = local.confirming,
            jobsShown = caps.scheduledJobs,
            jobsLoading = caps.scheduledJobs && jobs == null && local.jobsRead != FleetJobsRead.Failed,
            jobsFailed = caps.scheduledJobs && jobs == null && local.jobsRead == FleetJobsRead.Failed,
            jobsRead = jobs != null,
            canSwitchJobs = caps.controlScheduledJobs,
            mine = jobs.orEmpty().filter { it.projectId == project.id }.map(::row).toImmutableList(),
            team = jobs.orEmpty().filter { it.projectId.isNullOrEmpty() }.map(::row).toImmutableList(),
            policy = local.policy,
        )
    }
}
