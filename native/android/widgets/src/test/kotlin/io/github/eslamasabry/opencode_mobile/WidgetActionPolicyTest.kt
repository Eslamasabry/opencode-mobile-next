package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetActionPolicyTest {
    private val identity = mapOf<String, Any?>(
        "profileID" to "profile-a", "scopeToken" to "scope-a", "sessionID" to "session-a",
        "requestID" to "request-a", "runID" to "run-a", "gateID" to "gate-a",
        "lastProjectID" to "project-a", "expectedStartedAt" to 100L,
    )
    private fun payload(action: String) = identity + mapOf("id" to "action-token", "action" to action)
    private fun current() = identity + mapOf("busy" to true, "teamAction" to "approvePlan")

    @Test fun permitsAnExactPendingRequest() {
        assertNull(WidgetActionPolicy.validate(payload("allowOnce"), current()))
        assertNull(WidgetActionPolicy.validate(payload("reject"), current()))
    }
    @Test fun rejectsEveryChangedRoutingDimension() {
        for (field in WidgetActionPolicy.identityFields) {
            assertEquals(
                field,
                "staleRequest",
                WidgetActionPolicy.validate(payload("allowOnce"), current() + mapOf(field to "changed")),
            )
        }
    }
    @Test fun missingSnapshotCannotAuthorizeAnAction() {
        assertEquals("staleRequest", WidgetActionPolicy.validate(payload("stop"), null))
    }
    @Test fun stopRequiresTheSameRunningTurn() {
        assertNull(WidgetActionPolicy.validate(payload("stop"), current()))
        assertEquals("staleRequest", WidgetActionPolicy.validate(payload("stop"), current() + mapOf("busy" to false)))
        val unknownStart = mapOf("expectedStartedAt" to 0L)
        assertEquals(
            "staleRequest",
            WidgetActionPolicy.validate(payload("stop") + unknownStart, current() + unknownStart),
        )
    }
    @Test fun nullIdentityDoesNotBecomeTheLiteralNullString() {
        val absent = WidgetActionPolicy.identityFields.associateWith { null }
        assertEquals("staleRequest", WidgetActionPolicy.validate(payload("stop") + absent, absent))
        assertEquals("invalidRequest", WidgetActionPolicy.validate(payload("stop") + mapOf("id" to null), current()))
    }
    @Test fun teamButtonMustStillRepresentTheSameAvailableAction() {
        assertNull(WidgetActionPolicy.validate(payload("approvePlan"), current()))
        assertEquals("staleRequest", WidgetActionPolicy.validate(payload("resume"), current()))
    }
    @Test fun textIsBoundedAndRequiredForVoiceAndSteer() {
        for (action in listOf("voice", "steer")) {
            assertEquals("invalidRequest", WidgetActionPolicy.validate(payload(action), current()))
            assertEquals(
                "invalidRequest",
                WidgetActionPolicy.validate(payload(action) + mapOf("text" to " "), current()),
            )
            assertEquals(
                "invalidRequest",
                WidgetActionPolicy.validate(payload(action) + mapOf("text" to "x".repeat(8001)), current()),
            )
            assertNull(WidgetActionPolicy.validate(payload(action) + mapOf("text" to "Use this approach"), current()))
        }
    }
    @Test fun noProjectCannotCreateVoiceMessage() {
        val absent = mapOf("lastProjectID" to "")
        assertEquals(
            "unavailable",
            WidgetActionPolicy.validate(payload("voice") + absent + mapOf("text" to "hello"), current() + absent),
        )
    }
    @Test fun permissionRequiresExactRequestIdentifier() {
        val absent = mapOf("requestID" to "")
        assertEquals("staleRequest", WidgetActionPolicy.validate(payload("allowOnce") + absent, current() + absent))
    }
    @Test fun dedupTokenStaysStableAcrossRenderedUpdatesButChangesWithTurn() {
        val base = payload("stop")
        assertEquals(WidgetActionPolicy.token(base), WidgetActionPolicy.token(base + mapOf("updatedAt" to 999L)))
        assertNotEquals(
            WidgetActionPolicy.token(base),
            WidgetActionPolicy.token(base + mapOf("expectedStartedAt" to 200L)),
        )
        assertNotEquals(WidgetActionPolicy.token(base), WidgetActionPolicy.token(payload("reject")))
        assertEquals(64, WidgetActionPolicy.token(base).length)
    }
    @Test fun promptsAreNeverPartOfPersistentDedupToken() {
        assertEquals(
            WidgetActionPolicy.token(payload("steer")),
            WidgetActionPolicy.token(payload("steer") + mapOf("text" to "private message")),
        )
    }
}
