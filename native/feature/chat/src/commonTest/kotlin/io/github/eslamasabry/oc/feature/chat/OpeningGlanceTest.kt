package io.github.eslamasabry.oc.feature.chat

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.testing.TestDispatchers
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.world.ChatGlance
import io.github.eslamasabry.oc.feature.chat.world.GraphChatWorlds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Open-transitions (Galaxy S22 recordings walker-live-1 and item2): an existing conversation opened from a list showed
 * an empty title in its bar for its first frames, although the row tapped had just shown it. The page's world starts
 * from what the list shows, before anything is read.
 */
class OpeningGlanceTest {
    private val listed = ConversationRef(ProfileId("p1"), SessionId("listed"))
    private val unlisted = ConversationRef(ProfileId("p1"), SessionId("unlisted"))
    private val glance = ChatGlance { ref -> if (ref == listed) "Build a Python CLI Todo tool" else null }

    private fun TestScope.worlds(): GraphChatWorlds {
        val dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))
        val app = AppCoroutineScope(backgroundScope.coroutineContext)
        val registry = ConnectionRegistry(emptySet(), { _, _, _ -> error("nothing is opened here") }, app, dispatchers)
        return GraphChatWorlds(registry, glances = setOf(glance))
    }

    @Test
    fun `OT-12 the title the list shows is the page's title from its first frame`() = runTest {
        val world = worlds().open(listed)
        // Nothing has run yet (no dispatcher step, no read): this is what the bar's first frame shows.
        assertEquals("Build a Python CLI Todo tool", world.title.value)
    }

    @Test
    fun `OT-13 a conversation the list does not hold starts with no title (unknown), not a made-up one`() = runTest {
        assertNull(worlds().open(unlisted).title.value)
    }
}
