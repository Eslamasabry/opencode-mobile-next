package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** FLICKER F8 and F7: Home's waiting stage and its "Done" word each have one steady truth. */
class LaunchStagesSteadyTest {
    private fun stage(wanted: Boolean, phone: Boolean = false, helper: Boolean = false, agent: Boolean = false) =
        LaunchStages.helperStageOf(wanted, phone, helper, agent)

    @Test
    fun `B-HOME-223 between the helper's own start and its connection the wait keeps its name`() {
        // The helper's start ended (it answers) and its source has not begun to connect: still waiting, still named.
        assertEquals(LaunchStage.Helper, stage(wanted = true))
        // A refused try waiting to be asked again, inside the first window: the same.
        assertEquals(LaunchStage.Helper, stage(wanted = true, helper = false, agent = false))
    }

    @Test
    fun `B-HOME-223 the real signals still win, and no wish means no stage`() {
        assertEquals(LaunchStage.Agent, stage(wanted = true, agent = true, helper = true))
        assertEquals(LaunchStage.Helper, stage(wanted = true, helper = true))
        assertEquals(LaunchStage.Linux, stage(wanted = true, phone = true))
        assertNull(stage(wanted = false))
    }

    @Test
    fun `B-HOME-224 the list publishes Done from the saved read marks only, never the engine's early guess`() {
        val source =
            listOf(
                    File("src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt"),
                    File("androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt"),
                )
                .first { it.exists() }
                .readText()
        // The engine's in-memory read state does not know the saved marks at launch: copying it into the listing said
        // "Done" for a moment on a row that was read, then took it back.
        assertFalse(Regex("""finishedUnseen\s*=\s*\n?\s*(reads|graph\.session\.readState)""").containsMatchIn(source))
    }
}
