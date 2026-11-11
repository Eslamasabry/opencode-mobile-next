package io.github.eslamasabry.oc.feature.chat.header

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.requests.consent.AutoApproved
import io.github.eslamasabry.oc.feature.chat.header.approval.AutoApprovalRecords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class AutoApprovalRecordsTest {
    @Test
    fun `keeps the latest five of one conversation, newest first`() {
        val profile = ProfileId("record-test")
        val session = SessionId("s1")
        repeat(7) { AutoApprovalRecords.add(profile, AutoApproved(RequestId("r$it"), session, "bash$it", Instant.fromEpochMilliseconds(it.toLong()))) }
        assertEquals(listOf("bash6", "bash5", "bash4", "bash3", "bash2"), AutoApprovalRecords.recent(profile, session).map { it.permission })
        assertEquals(emptyList(), AutoApprovalRecords.recent(profile, SessionId("other")).map { it.permission })
    }

    @Test
    fun `a server's record goes when its connection is no longer live`() {
        val kept = ProfileId("record-kept")
        val gone = ProfileId("record-gone")
        val session = SessionId("s1")
        AutoApprovalRecords.add(kept, AutoApproved(RequestId("a"), session, "edit", Instant.fromEpochMilliseconds(1)))
        AutoApprovalRecords.add(gone, AutoApproved(RequestId("b"), session, "bash", Instant.fromEpochMilliseconds(2)))
        AutoApprovalRecords.retain(setOf(kept, ProfileId("record-test")))
        assertEquals(listOf("edit"), AutoApprovalRecords.recent(kept, session).map { it.permission })
        assertEquals(emptyList(), AutoApprovalRecords.recent(gone, session).map { it.permission })
    }
}
