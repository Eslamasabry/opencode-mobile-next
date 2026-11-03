package io.github.eslamasabry.oc.feature.search

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.search.presentation.RecentSearchHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class RecentSearchHistoryTest {
    @Test
    fun `SCREENS-NAV-34 a search remembered before start-up restoration runs keeps the saved history`() = runTest {
        var saved = listOf("deploy", "login")
        // Start-up restoration is launched but has not run yet when the first search is remembered.
        val startup = TestScope(StandardTestDispatcher())
        val history =
            RecentSearchHistory(
                startup,
                warm = { Outcome.Ok(Unit) },
                load = { saved },
                save = { next ->
                    saved = next
                    Outcome.Ok(Unit)
                },
            )
        assertEquals(Outcome.Ok(Unit), history.remember("rollback"))
        assertEquals(listOf("rollback", "deploy", "login"), saved)
        startup.testScheduler.advanceUntilIdle()
        assertEquals(listOf("rollback", "deploy", "login"), history.recent.value)
    }
}
