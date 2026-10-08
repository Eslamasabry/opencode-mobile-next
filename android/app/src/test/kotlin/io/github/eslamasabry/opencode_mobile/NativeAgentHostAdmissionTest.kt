package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAgentHostAdmissionTest {
    private fun ticket(idle: Long? = 7) = NativeAgentHostAdmission.Ticket(
        profile = "helper", owner = "server", revision = 4, epoch = 9, idleGeneration = idle)

    private fun current() = NativeAgentHostAdmission.Snapshot(
        available = true, activityResumed = true, activityEpoch = 9, wantedRevision = 4,
        owner = "server", wanted = true, userStopped = false, supervisionEnabled = true,
        serverLive = true, migrationValid = true, policyValid = true, helperOwner = "helper",
        idle = NativeIdleState.Snapshot(enabled = true, counter = 7, generation = 7,
            stopped = false, helperStopped = true, owner = "server", helper = "helper"))

    private fun ordinary() = current().copy(idle = NativeIdleState.Snapshot())
    private fun completed() = current().copy(idle = current().idle.copy(helperStopped = false, helper = null))

    @Test fun owedHelperStartsOnlyAfterItsServerResumed() {
        assertTrue(NativeAgentHostAdmission.admitted(ticket(), current()))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(serverLive = false)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(idle = current().idle.copy(stopped = true))))
    }

    @Test fun unavailableReceiptRefusesBothLaunchPaths() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(available = false)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), ordinary().copy(available = false)))
    }

    @Test fun pausedActivityCannotAuthorizeEitherLaunchPath() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(activityResumed = false)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), ordinary().copy(activityResumed = false)))
    }

    @Test fun pauseAndReturnDoNotReviveTheOldTicket() {
        val resumed = current().copy(activityEpoch = 11)
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), resumed))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), ordinary().copy(activityEpoch = 11)))
        assertTrue(NativeAgentHostAdmission.admitted(ticket().copy(epoch = 11), resumed))
    }

    @Test fun changedStopRevisionRefusesBothOldTickets() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(wantedRevision = 5)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), ordinary().copy(wantedRevision = 5)))
    }

    @Test fun ownerTransferAndReturnDoNotReviveOldWork() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(owner = "replacement")))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), ordinary().copy(owner = "replacement")))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(wantedRevision = 6)))
    }

    @Test fun deletedOwnerCannotResumeOwedHelper() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(owner = null)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket().copy(owner = null), current().copy(owner = null)))
    }

    @Test fun explicitStopWinsOverAStillLiveIdleServer() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(userStopped = true)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(wanted = false)))
    }

    @Test fun missingSupervisionRefusesIdleHelper() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(supervisionEnabled = false)))
    }

    @Test fun missingMigrationRefusesIdleHelper() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(migrationValid = false)))
    }

    @Test fun deniedPolicyRefusesIdleHelper() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(policyValid = false)))
    }

    @Test fun idleTokenMustRemainExactlyCurrent() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(6), current()))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(8), current()))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(idle = current().idle.copy(generation = 8, counter = 8))))
    }

    @Test fun idleTokenMustBePositiveEvenIfSnapshotRepeatsIt() {
        for (generation in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertFalse(NativeAgentHostAdmission.admitted(ticket(generation), current().copy(
                idle = current().idle.copy(generation = generation, counter = generation))))
        }
    }

    @Test fun idleReceiptCannotBelongToAnotherServer() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(idle = current().idle.copy(owner = "other"))))
    }

    @Test fun onlyPreviouslyLiveExactHelperCanResume() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(idle = current().idle.copy(helper = "other"))))
        assertFalse(NativeAgentHostAdmission.admitted(ticket().copy(profile = "other"), current()))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), completed()))
    }

    @Test fun ordinaryHelperCannotConsumeOwedIdleMarkers() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), current()))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), ordinary().copy(idle = ordinary().idle.copy(stopped = true))))
    }

    @Test fun ordinaryForegroundHelperIsIndependentOfServerIntentAndRecoveryAuthority() {
        // Compatibility: standalone helper setup does not need a server or a
        // crash-retry reservation. Stop-revision/foreground ticket checks still apply.
        val standalone = ordinary().copy(owner = null, wanted = false, userStopped = true,
            supervisionEnabled = false, serverLive = false, migrationValid = false,
            policyValid = false, helperOwner = null)
        assertTrue(NativeAgentHostAdmission.admitted(ticket(null).copy(owner = null), standalone))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null).copy(owner = null),
            standalone.copy(wantedRevision = 5)))
    }

    @Test fun completedIdleTokenAdmitsOnlyItsCanonicalHelper() {
        assertTrue(NativeAgentHostAdmission.admitted(ticket(null), completed()))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), completed().copy(helperOwner = "other")))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null).copy(profile = "other"), completed()))
    }

    @Test fun completedIdleTokenCannotAuthorizeAnotherOwner() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), completed().copy(idle = completed().idle.copy(owner = "other"))))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null).copy(owner = null), completed().copy(owner = null)))
    }

    @Test fun completedTokenUsesHelperOwnershipRatherThanServerRecoveryAuthority() {
        val independent = completed().copy(wanted = false, userStopped = true,
            supervisionEnabled = false, serverLive = false, migrationValid = false, policyValid = false)
        assertTrue(NativeAgentHostAdmission.admitted(ticket(null), independent))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(null), independent.copy(activityResumed = false)))
    }

    @Test fun invalidHelperIdentifiersCannotAuthorizeEitherPath() {
        for (profile in listOf("", "helper/path", "helper.name", "a".repeat(81))) {
            assertFalse(NativeAgentHostAdmission.admitted(ticket(null).copy(profile = profile), ordinary()))
            assertFalse(NativeAgentHostAdmission.admitted(ticket().copy(profile = profile),
                current().copy(idle = current().idle.copy(helper = profile))))
        }
        assertTrue(NativeAgentHostAdmission.admitted(ticket(null).copy(profile = "a".repeat(80)), ordinary()))
    }

    @Test fun futureOrPreviousActivityEpochIsNotCurrent() {
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(activityEpoch = 8)))
        assertFalse(NativeAgentHostAdmission.admitted(ticket(), current().copy(activityEpoch = 10)))
    }
}
