package io.github.eslamasabry.oc.feature.team.fleet

import io.github.eslamasabry.oc.engine.team.fleet.FleetCapabilities
import io.github.eslamasabry.oc.engine.team.fleet.FleetFailure
import io.github.eslamasabry.oc.engine.team.fleet.FleetJob
import io.github.eslamasabry.oc.engine.team.fleet.FleetJobTrigger
import io.github.eslamasabry.oc.engine.team.fleet.FleetPhase
import io.github.eslamasabry.oc.engine.team.fleet.FleetPolicy
import io.github.eslamasabry.oc.engine.team.fleet.FleetProject
import io.github.eslamasabry.oc.engine.team.fleet.FleetReceipt
import io.github.eslamasabry.oc.engine.team.fleet.FleetSnapshot
import io.github.eslamasabry.oc.engine.team.fleet.FleetState
import io.github.eslamasabry.oc.engine.team.fleet.ReceiptStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetRigPresentTest {
    private fun job(trigger: FleetJobTrigger, schedule: String? = null, interval: String? = null) =
        FleetJob("j", "Job", enabled = true, trigger = trigger, schedule = schedule, interval = interval)

    private fun whenOf(job: FleetJob) = FleetJobWords.whenOf(job)

    @Test
    fun a_daily_cron_reads_as_a_time_of_day_and_a_weekday_one_as_weekdays() {
        assertEquals(FleetJobWhen.EveryDayAt("09:05"), whenOf(job(FleetJobTrigger.Cron, "5 9 * * *")))
        assertEquals(FleetJobWhen.EveryWeekdayAt("18:00"), whenOf(job(FleetJobTrigger.Cron, "0 18 * * 1-5")))
        assertEquals(FleetJobWhen.EveryDowAt(2, "07:30"), whenOf(job(FleetJobTrigger.Cron, "30 7 * * 2")))
    }

    @Test
    fun cron_sunday_is_zero_or_seven() {
        assertEquals(FleetJobWhen.EveryDowAt(0, "00:00"), whenOf(job(FleetJobTrigger.Cron, "0 0 * * 7")))
        assertEquals(FleetJobWhen.EveryDowAt(0, "00:00"), whenOf(job(FleetJobTrigger.Cron, "0 0 * * 0")))
    }

    @Test
    fun cron_steps_read_as_every_n_minutes_or_hours() {
        assertEquals(FleetJobWhen.EveryMinutes(15), whenOf(job(FleetJobTrigger.Cron, "*/15 * * * *")))
        assertEquals(FleetJobWhen.EveryMinutes(1), whenOf(job(FleetJobTrigger.Cron, "* * * * *")))
        assertEquals(FleetJobWhen.EveryHour, whenOf(job(FleetJobTrigger.Cron, "0 * * * *")))
        assertEquals(FleetJobWhen.EveryHours(4), whenOf(job(FleetJobTrigger.Cron, "0 */4 * * *")))
    }

    @Test
    fun a_schedule_too_unusual_to_say_simply_is_on_a_schedule_never_the_expression() {
        assertEquals(FleetJobWhen.OnSchedule, whenOf(job(FleetJobTrigger.Cron, "0 9 1 * *")))
        assertEquals(FleetJobWhen.OnSchedule, whenOf(job(FleetJobTrigger.Cron, "61 9 * * *")))
        assertEquals(FleetJobWhen.OnSchedule, whenOf(job(FleetJobTrigger.Cron, "0 25 * * *")))
        assertEquals(FleetJobWhen.OnSchedule, whenOf(job(FleetJobTrigger.Cron, "0 9 * * 8")))
        assertEquals(FleetJobWhen.OnSchedule, whenOf(job(FleetJobTrigger.Cron, "nonsense")))
        assertEquals(FleetJobWhen.OnSchedule, whenOf(job(FleetJobTrigger.Cron, null)))
    }

    @Test
    fun an_interval_reads_in_seconds_minutes_or_hours_and_anything_else_repeats() {
        assertEquals(FleetJobWhen.EverySeconds(30), whenOf(job(FleetJobTrigger.Interval, interval = "30s")))
        assertEquals(FleetJobWhen.EveryMinutes(5), whenOf(job(FleetJobTrigger.Interval, interval = " 5m ")))
        assertEquals(FleetJobWhen.EveryHours(2), whenOf(job(FleetJobTrigger.Interval, interval = "2h")))
        assertEquals(FleetJobWhen.Repeats, whenOf(job(FleetJobTrigger.Interval, interval = "0m")))
        assertEquals(FleetJobWhen.Repeats, whenOf(job(FleetJobTrigger.Interval, interval = "1d")))
        assertEquals(FleetJobWhen.Repeats, whenOf(job(FleetJobTrigger.Interval, interval = null)))
    }

    @Test
    fun the_other_triggers_have_their_own_words() {
        assertEquals(FleetJobWhen.Event, whenOf(job(FleetJobTrigger.Event)))
        assertEquals(FleetJobWhen.Manual, whenOf(job(FleetJobTrigger.Manual)))
        assertEquals(FleetJobWhen.Webhook, whenOf(job(FleetJobTrigger.Webhook)))
        assertEquals(FleetJobWhen.Condition, whenOf(job(FleetJobTrigger.Condition)))
        assertEquals(FleetJobWhen.Other, whenOf(job(FleetJobTrigger.Other)))
    }

    private val app = FleetProject("app", "App", directory = "/src/app")
    private val site = FleetProject("site", "Site", suspended = true)

    private fun ready(caps: FleetCapabilities, jobs: List<FleetJob>? = null) =
        FleetState(phase = FleetPhase.Ready, capabilities = caps, snapshot = FleetSnapshot(projects = listOf(app, site)), hasData = true, jobs = jobs)

    @Test
    fun the_page_waits_for_the_team_and_says_when_it_cannot_be_found() {
        assertEquals(FleetRigKind.Starting, FleetRigPresent.page(FleetState(), "app", FleetRigLocal()).kind)
        val failed = FleetState(phase = FleetPhase.Failed, failure = FleetFailure.Unreachable)
        assertEquals(FleetRigKind.Failed, FleetRigPresent.page(failed, "app", FleetRigLocal()).kind)
    }

    @Test
    fun a_project_the_computer_no_longer_lists_is_gone() {
        assertEquals(FleetRigKind.Gone, FleetRigPresent.page(ready(FleetCapabilities.FRONT), "old", FleetRigLocal()).kind)
    }

    @Test
    fun the_controls_show_only_what_the_host_offers() {
        val front = FleetRigPresent.page(ready(FleetCapabilities.FRONT), "app", FleetRigLocal())
        assertTrue(front.canControl && front.canRemove)
        assertFalse(front.paused)
        val loopback = FleetRigPresent.page(ready(FleetCapabilities.LOOPBACK), "site", FleetRigLocal())
        assertTrue(loopback.canControl)
        // The phone's own team cannot delete a project (Dart `gascityLoopback`).
        assertFalse(loopback.canRemove)
        assertTrue(loopback.paused)
        val read = FleetRigPresent.page(ready(FleetCapabilities.READ), "app", FleetRigLocal())
        assertFalse(read.canControl || read.canRemove || read.canSwitchJobs)
        assertEquals("/src/app", read.directory)
    }

    @Test
    fun a_projects_jobs_are_split_from_the_whole_teams_and_read_state_is_said() {
        val jobs =
            listOf(
                FleetJob("a", "Nightly", true, FleetJobTrigger.Cron, projectId = "app", schedule = "0 3 * * *"),
                FleetJob("b", "Other rig", true, FleetJobTrigger.Manual, projectId = "site"),
                FleetJob("c", "Patrol", false, FleetJobTrigger.Interval, interval = "5m"),
            )
        val ui = FleetRigPresent.page(ready(FleetCapabilities.FRONT, jobs), "app", FleetRigLocal())
        assertTrue(ui.jobsShown && ui.jobsRead && ui.canSwitchJobs)
        assertEquals(listOf("a"), ui.mine.map { it.id })
        assertEquals(listOf("c"), ui.team.map { it.id })
        assertEquals(FleetJobWhen.EveryDayAt("03:00"), ui.mine.single().whenWords)
        val loading = FleetRigPresent.page(ready(FleetCapabilities.FRONT), "app", FleetRigLocal(jobsRead = FleetJobsRead.Loading))
        assertTrue(loading.jobsLoading && !loading.jobsRead && !loading.jobsFailed)
        val failed = FleetRigPresent.page(ready(FleetCapabilities.FRONT), "app", FleetRigLocal(jobsRead = FleetJobsRead.Failed))
        assertTrue(failed.jobsFailed && !failed.jobsLoading)
    }

    @Test
    fun a_host_that_lists_no_jobs_has_no_jobs_section() {
        val caps = FleetCapabilities.FRONT.copy(scheduledJobs = false)
        assertFalse(FleetRigPresent.page(ready(caps), "app", FleetRigLocal()).jobsShown)
    }

    @Test
    fun a_job_keeps_the_receipt_of_its_own_switch() {
        val jobs = listOf(FleetJob("a", "Nightly", true, FleetJobTrigger.Manual, projectId = "app"))
        val local = FleetRigLocal(jobWrites = mapOf("a" to FleetWrite.Sending))
        assertEquals(FleetWrite.Sending, FleetRigPresent.page(ready(FleetCapabilities.FRONT, jobs), "app", local).mine.single().write)
    }

    @Test
    fun the_hosts_policy_rides_on_the_page_only_when_the_host_gave_one() {
        assertNull(FleetRigPresent.page(ready(FleetCapabilities.FRONT), "app", FleetRigLocal()).policy)
        val policy = FleetPolicy(rig = "app")
        assertEquals(policy, FleetRigPresent.page(ready(FleetCapabilities.FRONT), "app", FleetRigLocal(policy = policy)).policy)
    }

    @Test
    fun a_receipt_never_says_done_before_the_host_confirmed() {
        assertEquals(FleetWriteStatus.Confirmed, FleetWrite.of(FleetReceipt("r", ReceiptStatus.Accepted)).status)
        val maybe = FleetWrite.of(FleetReceipt("r", ReceiptStatus.Pending, "no answer"))
        assertEquals(FleetWriteStatus.NotConfirmed, maybe.status)
        val refused = FleetWrite.of(FleetReceipt.rejected("r", "front required"))
        assertEquals(FleetWriteStatus.Refused, refused.status)
        // The host's words travel apart from the status, for Details only.
        assertEquals("front required", refused.hostWords)
    }
}
