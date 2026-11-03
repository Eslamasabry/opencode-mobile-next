package io.github.eslamasabry.oc.engine.review

import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ReviewLifetimeTest {
    @Test
    fun `B-CHAT-106 closing one review stops its work and leaves the app and other reviews running`() = runTest {
        val first = ReviewLifetime(backgroundScope, EmptyCoroutineContext)
        val second = ReviewLifetime(backgroundScope, EmptyCoroutineContext)
        val firstWork = first.scope.launch { awaitCancellation() }
        val secondWork = second.scope.launch { awaitCancellation() }
        runCurrent()

        first.close()
        runCurrent()

        assertTrue(firstWork.isCancelled)
        assertFalse(first.scope.isActive)
        assertTrue(secondWork.isActive)
        assertTrue(backgroundScope.isActive)
    }
}
