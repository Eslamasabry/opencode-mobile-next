package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationActions
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationProject
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSheet
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationUi
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Issue 147 (F-034): a person taps where a chip was. The chips and Start keep ONE place from the first frame, through
 * "getting ready", to the settled page (the reason line between them used to come and go).
 */
@OptIn(ExperimentalTestApi::class)
class NewConversationStableLayoutTest {
    private object Actions : NewConversationActions {
        override fun back() {}
        override fun addServer() {}
        override fun chooseServer(profile: ProfileId) {}
        override fun draftChanged(text: String) {}
        override fun chooseDirectory(directory: String) {}
        override fun chooseModel(model: ModelRef?) {}
        override fun chooseAgent(agent: String?) {}
        override fun separateCopy(on: Boolean) {}
        override fun sheet(sheet: NewConversationSheet?) {}
        override fun start() {}
        override fun import(conversation: ImportableConversation) {}
        override fun retryLoad() {}
    }

    private val p1 = ProfileId("p1")
    private val server = NewConversationServer(p1, "This phone", connected = true)
    private val ready =
        NewConversationUi(
            servers = listOf(server),
            profile = p1,
            setup = NewConversationSetup("/root/projects/shop", listOf(NewConversationProject("/root/projects/shop", "shop", false)), emptyList(), emptyList()),
            directory = "/root/projects/shop",
        )

    private fun places(ui: NewConversationUi): List<Float> {
        var out = emptyList<Float>()
        kitUiTest(412, 915) {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", appearance = KitAppearance(motion = MotionLevel.Off)) {
                    NewConversationScreen(ui, Actions)
                }
            }
            waitForIdle()
            val where = onNodeWithTag("kit-where-line").getBoundsInRoot()
            val chip = onNodeWithTag("kit-agent-model-chip").getBoundsInRoot()
            val start = onNodeWithTag("kit-button-hero").getBoundsInRoot()
            out = listOf(where.top.value, chip.top.value, start.top.value)
        }
        return out
    }

    @Test
    fun `F-034 the chips and Start stand in one place at the first frame, while loading and when ready`() {
        val first = places(NewConversationUi(servers = listOf(server), profile = p1, busy = true))
        val slow = places(NewConversationUi(servers = listOf(server), profile = p1, busy = true, preparingSlow = true))
        val settled = places(ready)
        val typed = places(ready.copy(text = "Find the bug"))
        val noProject = places(ready.copy(directory = null, text = "hello"))
        assertEquals(settled, first, "first frame")
        assertEquals(settled, slow, "getting ready for a while")
        assertEquals(settled, typed, "words typed")
        assertEquals(settled, noProject, "no project chosen")
    }
}
