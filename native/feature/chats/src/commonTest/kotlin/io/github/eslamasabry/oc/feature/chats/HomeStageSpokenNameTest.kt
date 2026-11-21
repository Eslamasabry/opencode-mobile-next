package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.AppStart
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStage
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.stageStartingHelper
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** B-HOME-240: one stage, one name, also when it is spoken; the chip never renames what the Recent line says. */
@OptIn(ExperimentalTestApi::class)
class HomeStageSpokenNameTest {
    private val phone =
        ChatsListSourceUi(ProfileId("p1"), "On this phone", shown = true, main = true, onThisPhone = true, unreachable = false)
    private val agents =
        ChatsListSourceUi(
            ProfileId("phone-agents"),
            "Agents on this phone",
            shown = true,
            main = false,
            onThisPhone = true,
            unreachable = false,
            agents = true,
            starting = AppStart.Agents,
        )

    private fun ui() =
        ChatsState.build(
            ChatFeedSnapshot(persistentListOf(item("a"))),
            ReturnBrief.None,
            ChatsFilters(),
            10_000,
            true,
        ).copy(home = ChatsHomeExtraState(sources = persistentListOf(phone, agents), stage = HomeStage.Helper))

    @Test
    fun `B-HOME-240 the starting-helper stage is spoken with the same words it shows`() =
        runComposeUiTest {
            val actions = RecordingChatsActions()
            var shown by mutableStateOf("")
            setContent {
                Themed {
                    // The words the Recent line shows for the helper-starting stage, built as the product builds them.
                    shown = stringResource(Res.string.stageStartingHelper)
                    ChatsScreen(ui(), actions)
                }
            }
            // The stage's one shown name is on the screen.
            onNodeWithText(shown).assertExists()
            // The connections chip speaks those same words for the same stage, never a second name.
            val spoken = onAllNodesWithContentDescription(shown).fetchSemanticsNodes()
            assertTrue(spoken.isNotEmpty(), "the chip's spoken text must be the shown words \"$shown\"")
        }
}
