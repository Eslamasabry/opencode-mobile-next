package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ports "connect refuses an unconfirmed profile in plain words" (test/private_network_cleartext_test.dart). */
class CleartextRefusalWordsTest {
    @Test
    fun `B-SEC-125 connect refuses an unconfirmed profile in its own plain category, never as a network problem`() {
        assertEquals(ServerFailureKind.CleartextConsent, serverFailureKind(Failure.Refused(RefusalReason.CleartextNotConfirmed)))
    }
}
