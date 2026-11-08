package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ProjectFilePick
import io.github.eslamasabry.oc.feature.chat.composer.presentation.ProjectFilePickUi
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * S28 story 12 (Flutter `KitProjectFileList`, composer_tools.dart:150-157): "File from the project" searches the
 * project's files as the person types; empty words show the hint, a failure keeps the words for Try again, and
 * folders are never offered as picks.
 */
class S28ProjectFilePickTest {
    private fun run(block: suspend () -> Unit) =
        block.startCoroutine(Continuation(EmptyCoroutineContext) { it.getOrThrow() })

    @Test
    fun `B-CHAT-S28-40 typing searches the project and lists files only`() {
        val asked = mutableListOf<String>()
        val pick = ProjectFilePick { q ->
            asked += q
            Outcome.Ok(listOf("src/app.kt", "src/", "README.md", "src/app.kt"))
        }
        run { pick.run("  app ") }
        assertEquals(listOf("app"), asked)
        assertEquals(ProjectFilePickUi("app", listOf("src/app.kt", "README.md")), pick.state.value)
        run { pick.run("") }
        assertEquals(ProjectFilePickUi(), pick.state.value)
        assertEquals(listOf("app"), asked, "empty words search nothing")
    }

    @Test
    fun `B-CHAT-S28-41 a failed search says so and keeps the words`() {
        val pick = ProjectFilePick { Outcome.Failed(Failure.Network(NetworkWhy.Offline)) }
        run { pick.run("app") }
        val state = pick.state.value
        assertTrue(state.failed)
        assertEquals("app", state.query)
        assertTrue(state.paths.isEmpty())
    }
}
