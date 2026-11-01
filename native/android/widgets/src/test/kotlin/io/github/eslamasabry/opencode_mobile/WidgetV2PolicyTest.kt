package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetV2PolicyTest {
    @Test fun stoppedServiceOffersSettingsEvenWithAnOldConnectedSnapshot() {
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, true, false)))
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false)))
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(0, false, false)))
    }
    @Test fun activeServiceStillNeedsACurrentConnectedSnapshotForActions() {
        assertEquals(WidgetV2Policy.Connection.CONNECTED,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, true, true)))
        assertEquals(WidgetV2Policy.Connection.OFFLINE,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, true)))
        assertEquals(WidgetV2Policy.Connection.OFFLINE,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(1, true, true)))
    }
    @Test fun cachedReadyActionConnectsOnlyOnDemandWithoutPretendingToBeLive() {
        assertTrue(WidgetV2Policy.onDemandAvailable(2, true))
        assertEquals(WidgetV2Policy.Connection.ON_DEMAND,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, true, false, onDemandAvailable = true)))
        assertEquals(WidgetV2Policy.Connection.ON_DEMAND,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false, onDemandAvailable = true)))
        for (action in listOf("allowOnce", "reject", "stop", "approvePlan", "resume")) {
            assertTrue(WidgetV2Policy.actionEnabled(WidgetV2Policy.Connection.ON_DEMAND,
                action, setOf(action), identityValid = true))
        }
    }
    @Test fun settledSuccessfulActionReturnsToOnDemandAfterItsServiceLeaseEnds() {
        for (action in listOf("allowOnce", "reject", "stop")) {
            val ready = WidgetV2Policy.onDemandAvailable(2, true)
            assertEquals(WidgetV2Policy.Connection.ON_DEMAND,
                WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false,
                    onDemandAvailable = ready)))
            assertTrue(WidgetV2Policy.actionEnabled(WidgetV2Policy.Connection.ON_DEMAND,
                action, setOf(action), identityValid = true))
            assertEquals(WidgetV2Policy.Connection.PENDING,
                WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, true, true,
                    onDemandAvailable = ready, actionPending = true)))

            // An accepted action removes the request/turn action proof. The
            // descriptor still supports future taps after the lease stops.
            val settled = WidgetV2Policy.onDemandAvailable(2, true)
            val recoveryRequired = WidgetV2Policy.needsBackgroundSettings("accepted", null)
            assertFalse(recoveryRequired)
            assertNull(WidgetV2Policy.actionResultNotice("accepted"))
            val connection = WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(
                2, false, false, onDemandAvailable = settled,
                actionPending = false, recoveryRequired = recoveryRequired,
            ))
            assertEquals("Settled $action must not offer background recovery",
                WidgetV2Policy.Connection.ON_DEMAND, connection)
            assertFalse(WidgetV2Policy.actionEnabled(connection, action, emptySet(), false))
            assertFalse(WidgetStatusRules.timerTicks(false, 0,
                connection == WidgetV2Policy.Connection.CONNECTED))
        }
    }
    @Test fun coldConnectionNeedsKnownSchemaAndDescriptorEvenWithoutAvailableActions() {
        assertFalse(WidgetV2Policy.onDemandAvailable(1, true))
        assertFalse(WidgetV2Policy.onDemandAvailable(2, false))
        assertTrue(WidgetV2Policy.onDemandAvailable(2, true))
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(3, true, false, onDemandAvailable = true)))
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false,
                onDemandAvailable = WidgetV2Policy.onDemandAvailable(2, false))))
    }
    @Test fun coldActionsStillNeedAdvertisedCapabilityAndCurrentIdentity() {
        val connection = WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false,
            onDemandAvailable = WidgetV2Policy.onDemandAvailable(2, true)))
        assertEquals(WidgetV2Policy.Connection.ON_DEMAND, connection)
        assertFalse(WidgetV2Policy.actionEnabled(connection,
            "stop", setOf("stop"), identityValid = false))
        assertFalse(WidgetV2Policy.actionEnabled(connection,
            "reject", setOf("allowOnce"), identityValid = true))
        assertFalse(WidgetV2Policy.actionEnabled(connection,
            "stop", emptySet(), identityValid = true))
        assertFalse(WidgetV2Policy.actionEnabled(connection,
            "voice", setOf("voice"), identityValid = true))
    }
    @Test fun pendingLeaseKeepsValidatedTapAvailableForRecoveryAfterProcessDeath() {
        val connection = WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, true, true,
            onDemandAvailable = true, actionPending = true))
        assertEquals(WidgetV2Policy.Connection.PENDING, connection)
        assertTrue(WidgetV2Policy.actionEnabled(connection, "stop", setOf("stop"), true))
        assertTrue(WidgetV2Policy.actionEnabled(connection, "allowOnce", setOf("allowOnce"), true))
        // Runtime lease and durable ledger reject replay. A different or
        // unsupported target still cannot be enabled by the pending label.
        assertFalse(WidgetV2Policy.actionEnabled(connection, "stop", emptySet(), true))
        assertFalse(WidgetV2Policy.actionEnabled(connection, "stop", setOf("stop"), false))
        assertFalse(WidgetV2Policy.actionEnabled(connection, "voice", setOf("voice"), true))
    }
    @Test fun overlaysRequireLiveConnectionAndSettledLease() {
        assertFalse(WidgetV2Policy.actionEnabled(WidgetV2Policy.Connection.ON_DEMAND,
            "steer", setOf("steer"), true))
        assertFalse(WidgetV2Policy.actionEnabled(WidgetV2Policy.Connection.ON_DEMAND,
            "voice", setOf("voice"), true))
        assertTrue(WidgetV2Policy.actionEnabled(WidgetV2Policy.Connection.CONNECTED,
            "stop", emptySet(), true))
    }
    @Test fun temporaryFailureKeepsSupportedActionsRetryableButHardLimitOffersSettings() {
        assertFalse(WidgetV2Policy.needsBackgroundSettings("rejected", "unavailable"))
        assertTrue(WidgetV2Policy.needsBackgroundSettings("rejected", "unsupported"))
        assertFalse(WidgetV2Policy.needsBackgroundSettings("rejected", "busy"))
        assertFalse(WidgetV2Policy.needsBackgroundSettings("rejected", "staleRequest"))
        assertFalse(WidgetV2Policy.needsBackgroundSettings("uncertain", "unavailable"))
        assertEquals(WidgetV2Policy.Connection.ON_DEMAND,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false, onDemandAvailable = true,
                recoveryRequired = WidgetV2Policy.needsBackgroundSettings("rejected", "unavailable"))))
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(2, false, false, onDemandAvailable = false,
                recoveryRequired = WidgetV2Policy.needsBackgroundSettings("rejected", "unavailable"))))
        assertEquals(WidgetV2Policy.Connection.BACKGROUND_SETTINGS,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(
                2, false, false, onDemandAvailable = true, recoveryRequired = true,
            )))
        assertEquals(WidgetV2Policy.Connection.CONNECTED,
            WidgetV2Policy.connection(WidgetV2Policy.ConnectionState(
                2, true, true, onDemandAvailable = true, recoveryRequired = true,
            )))
    }
    @Test fun sizesChangeOnlyAtDocumentedBreakpoints() {
        // 2x1, 4x1, 2x2, 4x2 and 4x4 as launchers report them (dp).
        assertEquals(WidgetV2Policy.Size.TINY, WidgetSizePolicy.size(110, 70))
        assertEquals(WidgetV2Policy.Size.TINY, WidgetSizePolicy.size(249, 129))
        assertEquals(WidgetV2Policy.Size.STRIP, WidgetSizePolicy.size(250, 70))
        assertEquals(WidgetV2Policy.Size.STRIP, WidgetSizePolicy.size(320, 129))
        assertEquals(WidgetV2Policy.Size.CARD, WidgetSizePolicy.size(110, 130))
        assertEquals(WidgetV2Policy.Size.CARD, WidgetSizePolicy.size(249, 400))
        assertEquals(WidgetV2Policy.Size.WIDE, WidgetSizePolicy.size(250, 130))
        assertEquals(WidgetV2Policy.Size.WIDE, WidgetSizePolicy.size(320, 239))
        assertEquals(WidgetV2Policy.Size.LIST, WidgetSizePolicy.size(250, 240))
        assertEquals(WidgetV2Policy.Size.LIST, WidgetSizePolicy.size(320, 420))
    }
    @Test fun usageNeverSelectsErrorOrRed() {
        assertEquals("neutral", WidgetUsageStyle.usageRole(-1))
        assertEquals("neutral", WidgetUsageStyle.usageRole(0))
        assertEquals("running", WidgetUsageStyle.usageRole(20))
        assertEquals("running", WidgetUsageStyle.usageRole(21))
    }
    @Test fun usageBarFillAlwaysDiffersFromItsTrack() {
        val text = 0xFFF3F3F1.toInt()
        val muted = 0xFF8A8F8C.toInt()
        for (left in listOf(1, 20, 99, 100)) {
            val (fill, track) = WidgetUsageStyle.usageBar(text, muted, left, stale = false)
            assertEquals(text, fill)
            assertTrue(fill != track)
            assertEquals(0x38, track ushr 24)
        }
        assertEquals(muted, WidgetUsageStyle.usageBar(text, muted, 40, stale = true).first)
        assertEquals(muted, WidgetUsageStyle.usageBar(text, muted, 0, stale = false).first)
    }
    @Test fun usageRowsGoBackToNormalSizesWhenNoLongerAlone() {
        assertEquals(Triple(16f, 22f, 14f), WidgetUsageStyle.usageTextSizes(true))
        assertEquals(Triple(13f, 12f, 10f), WidgetUsageStyle.usageTextSizes(false))
    }
    @Test fun wallTimeIsTranslatedToMonotonicTimeAndClamped() {
        assertEquals(900L, WidgetStatusRules.chronometerBase(100, 200, 1000))
        assertEquals(1000L, WidgetStatusRules.chronometerBase(300, 200, 1000))
        assertEquals(0L, WidgetStatusRules.chronometerBase(100, 5000, 1000))
    }
    @Test fun timerTicksOnlyWhileBusyStateIsLive() {
        assertEquals(true, WidgetStatusRules.timerTicks(true, 1000, true))
        assertEquals(false, WidgetStatusRules.timerTicks(false, 1000, true))
        assertEquals(false, WidgetStatusRules.timerTicks(true, 0, true))
    }
    @Test fun cachedBusyTurnNeverRunsTheWorkingTimerAfterDisconnect() {
        for (connection in WidgetV2Policy.Connection.values()) {
            assertEquals(connection == WidgetV2Policy.Connection.CONNECTED,
                WidgetStatusRules.timerTicks(true, 1000,
                    connection == WidgetV2Policy.Connection.CONNECTED))
        }
    }
    @Test fun teamRowsAreNotShownAsConversationsUnlessWaitingOnTheUser() {
        assertEquals(false, WidgetStatusRules.showRow(true, "", "", false))
        assertEquals(true, WidgetStatusRules.showRow(true, "approvePlan", "", false))
        assertEquals(true, WidgetStatusRules.showRow(true, "", "", true))
        assertEquals(true, WidgetStatusRules.showRow(false, "", "", false))
    }
    @Test fun acceptedResultImmediatelyRestoresLiveSummaryEvenAfterProcessRestart() {
        assertNull(WidgetV2Policy.actionResultNotice("accepted"))
        assertNull(WidgetV2Policy.actionResultNotice(null))
        assertNull(WidgetV2Policy.actionResultNotice(""))
    }
    @Test fun failedAndUncertainResultsKeepTheirExplanation() {
        assertEquals("rejected", WidgetV2Policy.actionResultNotice("rejected"))
        assertEquals("uncertain", WidgetV2Policy.actionResultNotice("uncertain"))
    }

    @Test fun widgetRendersForThePortraitSizeNotTheShortLandscapeOne() {
        // ZTE MiFavor sent a portrait 4x2 and a landscape entry and drew the landscape strip
        // inside the 4x2 box. Upright, the taller entry is the one the widget has.
        val sizes = listOf(392 to 219, 820 to 96)
        assertEquals(392 to 219, WidgetSizePolicy.currentSize(sizes, true, WidgetSizePolicy.Bounds(0, 0, 0, 0)))
        assertEquals(820 to 96, WidgetSizePolicy.currentSize(sizes, false, WidgetSizePolicy.Bounds(0, 0, 0, 0)))
        assertEquals(WidgetV2Policy.Size.WIDE, WidgetSizePolicy.size(392, 219))
        // Order does not matter.
        val none = WidgetSizePolicy.Bounds(0, 0, 0, 0)
        assertEquals(392 to 219, WidgetSizePolicy.currentSize(sizes.reversed(), true, none))
        // Without the list, the legacy options give the same answer.
        val legacy = WidgetSizePolicy.Bounds(392, 820, 96, 219)
        assertEquals(392 to 219, WidgetSizePolicy.currentSize(emptyList(), true, legacy))
        assertEquals(820 to 96, WidgetSizePolicy.currentSize(emptyList(), false, legacy))
        assertEquals(null, WidgetSizePolicy.currentSize(emptyList(), true, WidgetSizePolicy.Bounds(0, 0, 0, 0)))
    }
}
