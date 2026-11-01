package io.github.eslamasabry.opencode_mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdatePolicyTest {
    @Test fun installerStatusesMapToTheFrozenChannelStates() {
        assertEquals("pendingUser", AppUpdatePolicy.stateFor(-1))
        assertEquals("success", AppUpdatePolicy.stateFor(0))
        assertEquals("aborted", AppUpdatePolicy.stateFor(3))
        assertEquals("failure", AppUpdatePolicy.stateFor(1))
        assertEquals("failure", AppUpdatePolicy.stateFor(6))
    }

    @Test fun signerIsUppercaseHexOfSha256() {
        // SHA-256 of the empty input.
        assertEquals(
            "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
            AppUpdatePolicy.sha256Hex(ByteArray(0)),
        )
    }

    @Test fun severalSignersAreSortedAndJoinedSoOrderNeverMatters() {
        val a = byteArrayOf(1)
        val b = byteArrayOf(2)
        assertEquals(AppUpdatePolicy.signerString(listOf(a, b)), AppUpdatePolicy.signerString(listOf(b, a)))
        assertEquals(1, AppUpdatePolicy.signerString(listOf(a, b)).count { it == ',' })
    }
}
