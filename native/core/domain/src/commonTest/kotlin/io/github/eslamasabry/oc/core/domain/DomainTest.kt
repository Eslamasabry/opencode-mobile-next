package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DomainTest {
    @Test
    fun `operations default to Unavailable naming the capability`() = runTest {
        val ops = object : ServerOperationsGateway {}
        assertEquals(Outcome.Failed(Failure.Unavailable(FeatureId("sessionFork"))), ops.forkSession(SessionId("s")))
    }
}
