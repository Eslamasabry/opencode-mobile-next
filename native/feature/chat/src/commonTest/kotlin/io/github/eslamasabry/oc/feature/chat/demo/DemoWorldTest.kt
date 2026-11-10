package io.github.eslamasabry.oc.feature.chat.demo

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.PermissionAsk
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.PromptRow
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoStage
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWords
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWorld
import io.github.eslamasabry.oc.feature.chat.requests.presentation.PermissionDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

/**
 * The demo ("Just show me") shows the app's own request card (owner 2026-11-10: two "simulated edit" buttons in the
 * message box's place "feels slop"): the sample agent's ask is a real edit request for one file with its change, the
 * answer ends it, and the message box keeps working afterwards.
 */
class DemoWorldTest {
    private val words =
        DemoWords("Offline demo", "Make it friendlier.", "I'll change it.", "Done.", "Left as it is.", "Hello", "Welcome aboard!", "That's the demo.")

    private fun world(dispatcher: CoroutineDispatcher) =
        DemoWorld(
            object : AppDispatchers {
                override val main = dispatcher
                override val default = dispatcher
                override val io = dispatcher

                override fun serial(name: String) = dispatcher
            }
        ).also { it.configure(words) }

    private fun DemoWorld.said(): List<String> =
        transcript.value.rows.reversed().map {
            when (it) {
                is PromptRow -> "you: ${it.text}"
                is BlockRow -> "agent: ${it.text}"
                else -> "?"
            }
        }

    @Test
    fun `a sent message gets a reply and one waiting edit request for the sample file`() = runTest {
        val world = world(StandardTestDispatcher(testScheduler))
        assertNull(world.request.value)
        assertIs<Outcome.Ok<Unit>>(world.send("Make it friendlier."))
        assertEquals(DemoStage.Review, world.stage.value)
        assertEquals(listOf("you: Make it friendlier.", "agent: I'll change it."), world.said())
        val asked = assertNotNull(world.request.value)
        // The card's question is the app's own: "Edit welcome.txt?", never the demo's words.
        val ask = PermissionAsk.of(asked)
        assertEquals(AskVerb.Edit, ask.verb)
        assertEquals("welcome.txt", ask.name)
        // Details shows the change itself: the sample's line before and after.
        val diff = assertNotNull(PermissionDetails.of(asked).diff)
        assertTrue("-Hello" in diff && "+Welcome aboard!" in diff, diff)
        // Nothing is sent while the request waits.
        assertIs<Outcome.Failed>(world.send("again"))
    }

    @Test
    fun `allowing ends the request and says what changed`() = runTest {
        val world = world(StandardTestDispatcher(testScheduler))
        world.send("Make it friendlier.")
        assertIs<Outcome.Ok<Unit>>(world.decide(true))
        assertNull(world.request.value)
        assertEquals(DemoStage.Finished, world.stage.value)
        assertEquals("agent: Done.", world.said().last())
    }

    @Test
    fun `denying ends the request and leaves the sample alone`() = runTest {
        val world = world(StandardTestDispatcher(testScheduler))
        world.send("Make it friendlier.")
        world.decide(false)
        assertNull(world.request.value)
        assertEquals("agent: Left as it is.", world.said().last())
    }

    @Test
    fun `after the sample the message box still answers, with one honest line and no new request`() = runTest {
        val world = world(StandardTestDispatcher(testScheduler))
        world.send("Make it friendlier.")
        world.decide(true)
        assertIs<Outcome.Ok<Unit>>(world.send("Now add a logo"))
        assertNull(world.request.value)
        assertEquals(listOf("you: Now add a logo", "agent: That's the demo."), world.said().takeLast(2))
    }

    @Test
    fun `Stop while the request waits ends it as a deny`() = runTest {
        val world = world(StandardTestDispatcher(testScheduler))
        world.send("Make it friendlier.")
        assertIs<Outcome.Ok<Unit>>(world.stop())
        assertNull(world.request.value)
        assertEquals("agent: Left as it is.", world.said().last())
        // With nothing waiting there is nothing to stop.
        assertIs<Outcome.Failed>(world.stop())
    }

    @Test
    fun `reset clears the conversation and the request`() = runTest {
        val world = world(StandardTestDispatcher(testScheduler))
        world.send("Make it friendlier.")
        world.reset()
        assertNull(world.request.value)
        assertEquals(DemoStage.Ready, world.stage.value)
        assertTrue(world.said().isEmpty())
    }
}
