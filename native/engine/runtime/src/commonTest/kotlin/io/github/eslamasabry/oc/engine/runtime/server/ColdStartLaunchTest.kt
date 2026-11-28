package io.github.eslamasabry.oc.engine.runtime.server

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * B-486: proves cold start boots the phone agents helper alongside OpenCode instead of waiting
 * behind OpenCode's boot and initial connection, cutting combined cold-start readiness time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ColdStartLaunchTest {
    private val phone = ProfileId("phone")
    private val helper = ProfileId("phone-agents")

    @Test
    fun `B-486 agents helper seeds and starts alongside OpenCode instead of waiting behind it`() =
        runTest {
            val events = mutableListOf<Pair<Long, String>>()
            var helperReadyAt: Long? = null
            var openCodeReadyAt: Long? = null
            val openCodeGate = CompletableDeferred<Unit>()
            var helperJobRunning = false

            val job = launch {
                ColdStartLaunch.run(
                    ColdStartLaunch.Targets(active = phone, phoneHelpers = listOf(helper)),
                    ColdStartLaunch.HelperHooks(
                        seed = { id -> events += testScheduler.currentTime to "seed:${id.raw}" },
                        start = { id ->
                            events += testScheduler.currentTime to "startHelper:${id.raw}"
                            helperJobRunning = true
                            launch {
                                delay(6_000)
                                helperReadyAt = testScheduler.currentTime
                                events += testScheduler.currentTime to "helperReady:${id.raw}"
                            }
                        },
                        adopt = { events += testScheduler.currentTime to "adoptHelpers" },
                    ),
                    ColdStartLaunch.PrimaryHooks(
                        connect = { id ->
                            events += testScheduler.currentTime to "connectPrimary:${id.raw}"
                            openCodeGate.await()
                            openCodeReadyAt = testScheduler.currentTime
                            "ok"
                        },
                        empty = { "empty" },
                        onComplete = { events += testScheduler.currentTime to "primaryComplete:$it" },
                        syncRemaining = { events += testScheduler.currentTime to "syncRemaining" },
                    ),
                )
            }

            runCurrent()
            // At t = 0, OpenCode is still booting (blocked on openCodeGate), yet both sources are
            // seeded and the helper has ALREADY started. Adoption of a source not saved yet waits
            // for the primary connection: a helper start it needs is refused before the activity resumed.
            assertTrue(helperJobRunning, "helper must start while OpenCode is still booting")
            assertEquals(
                listOf(
                    0L to "seed:phone",
                    0L to "seed:phone-agents",
                    0L to "startHelper:phone-agents",
                    0L to "connectPrimary:phone",
                ),
                events,
            )

            // Both OpenCode and the helper finish at 6 s concurrently (not 6 s + 6 s = 12 s).
            advanceTimeBy(6_000)
            openCodeGate.complete(Unit)
            runCurrent()
            job.join()

            assertEquals(6_000L, openCodeReadyAt)
            assertEquals(6_000L, helperReadyAt, "helper finishes alongside OpenCode at 6 s")
            assertEquals(
                listOf("primaryComplete:ok", "adoptHelpers", "syncRemaining"),
                events.map { it.second }.filter { it in setOf("primaryComplete:ok", "adoptHelpers", "syncRemaining") },
                "adoption follows the primary connection, before the remaining sources join",
            )
        }

    @Test
    fun `B-486 cold start without an active profile still adopts helpers and runs sync`() =
        runTest {
            val events = mutableListOf<String>()
            ColdStartLaunch.run(
                ColdStartLaunch.Targets(active = null, phoneHelpers = emptyList()),
                ColdStartLaunch.HelperHooks(
                    seed = { events += "seed:${it.raw}" },
                    start = { events += "startHelper:${it.raw}" },
                    adopt = { events += "adoptHelpers" },
                ),
                ColdStartLaunch.PrimaryHooks(
                    connect = { "unexpected" },
                    empty = { "empty" },
                    onComplete = { events += "complete:$it" },
                    syncRemaining = { events += "syncRemaining" },
                ),
            )
            assertEquals(listOf("complete:empty", "adoptHelpers", "syncRemaining"), events)
        }
}
