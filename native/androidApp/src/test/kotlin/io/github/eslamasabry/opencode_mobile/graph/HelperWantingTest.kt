package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Small-faults 301: opening a phone-agent conversation starts the agents' helper whether its source is shown in the
 * home's list or hidden ("not shown in the list"); the app coming to the front starts shown sources only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelperWantingTest {
    private val helper = ProfileId("phone-agents")

    private class World(var shown: Boolean, var listed: Boolean = false, var failure: Failure? = null) {
        val started = mutableListOf<ProfileId>()
    }

    private fun TestScope.wanting(world: World): Pair<HelperWanting, HelperJoins> {
        val joins = HelperJoins(backgroundScope, now = { testScheduler.currentTime })
        val wanting =
            HelperWanting(
                joins = joins,
                isHelper = { it == helper },
                isActive = { false },
                listed = { world.listed },
                shown = { world.shown },
                failure = { world.failure },
                startLoop = { id ->
                    world.started += id
                    joins.ensure(id, HelperStartRules::keepTrying, {}) { null }
                },
            )
        return wanting to joins
    }

    @Test
    fun `SMALL-301 opening a conversation of a hidden source asks for the helper start`() = runTest {
        val world = World(shown = false)
        val (wanting, _) = wanting(world)
        wanting.want(helper, evenHidden = true)
        runCurrent()
        assertEquals(listOf(helper), world.started)
    }

    @Test
    fun `SMALL-301 a shown source starts the same way, with or without the hidden flag`() = runTest {
        val world = World(shown = true)
        val (wanting, _) = wanting(world)
        wanting.want(helper, evenHidden = true)
        runCurrent()
        assertEquals(listOf(helper), world.started)
    }

    @Test
    fun `SMALL-301 the app coming to the front and the home start nothing for a hidden source`() = runTest {
        val world = World(shown = false)
        val (wanting, _) = wanting(world)
        wanting.want(helper)
        wanting.keepListed(helper)
        runCurrent()
        assertEquals(emptyList(), world.started)
    }

    @Test
    fun `SMALL-301 a source already listed, or a failure waiting cannot fix, starts nothing`() = runTest {
        val listed = World(shown = false, listed = true)
        wanting(listed).first.want(helper, evenHidden = true)
        val missing = World(shown = false, failure = Failure.Runtime(RuntimeProblem.NotInstalled))
        wanting(missing).first.want(helper, evenHidden = true)
        runCurrent()
        assertEquals(emptyList(), listed.started)
        assertEquals(emptyList(), missing.started)
    }
}
