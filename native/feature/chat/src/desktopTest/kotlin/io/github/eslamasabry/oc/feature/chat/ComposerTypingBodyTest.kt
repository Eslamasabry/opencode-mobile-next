package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The composer body used to rerun on every key (it read the whole text in its own scope). A key that changes none of
 * the yes/no facts the body uses (empty, blank, which suggestion) must leave the body alone.
 */
@OptIn(ExperimentalTestApi::class)
class ComposerTypingBodyTest {
    private class Actions : BasicComposerActions {
        override fun draftChanged(text: String) = Unit
        override suspend fun send(text: String): Boolean = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String): Boolean = true
    }

    @Test
    fun `typing twenty plain characters reruns the composer body only when a fact flips`() = runComposeUiTest {
        var bodyRuns = 0
        // chrome.requestWaiting() is read once per pass of BasicComposer's own body.
        val chrome =
            ComposerChrome({ false }, { bodyRuns++; false }, {}, { null }, {}, { null }, {}, {}, { false })
        val ui =
            BasicComposerUi(
                available = true,
                sending = false,
                steers = false,
                targetKey = "p/s",
                models = ComposerModels(usesDefault = true),
                filesAvailable = true,
            )
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
                localeTag = "en",
            ) {
                Box(Modifier.width(380.dp)) { BasicComposer(ui, Actions(), chrome) }
            }
        }
        waitForIdle()
        val field = hasSetTextAction() and hasAnyAncestor(hasTestTag("chat-composer-field"))
        // The first key flips empty to not-empty; later keys of the same word change no fact.
        onNode(field, useUnmergedTree = true).performTextInput("a")
        waitForIdle()
        val afterFirst = bodyRuns
        repeat(19) {
            onNode(field, useUnmergedTree = true).performTextInput("b")
            waitForIdle()
        }
        assertTrue(bodyRuns - afterFirst <= 1, "the body reran ${bodyRuns - afterFirst} times over 19 plain keys")
    }
}
