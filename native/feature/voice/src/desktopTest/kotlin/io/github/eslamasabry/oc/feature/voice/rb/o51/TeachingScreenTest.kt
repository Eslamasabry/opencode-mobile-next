package io.github.eslamasabry.oc.feature.voice.rb.o51

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.settings.teaching.*
import io.github.eslamasabry.oc.feature.voice.presentation.*
import io.github.eslamasabry.oc.feature.voice.ui.TeachingScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TeachingScreenTest {
    @Test
    fun `B-NAV-068 tips name each entry and replay only the selected tip`() = runComposeUiTest {
        val calls = mutableListOf<TipId>()
        val actions =
            object : TeachingActions {
                override fun back() {}

                override fun replay(id: TipId) {
                    calls += id
                }

                override fun next() {}

                override fun dismiss() {}

                override fun reset() {}

                override fun retry() {}
            }
        setContent {
            KitTheme(
                platform = DesktopKitPlatform(),
                localeTag = "en",
                appearance = KitAppearance(motion = MotionLevel.Off),
                stillFrame = true,
            ) {
                Box(Modifier.size(412.dp, 915.dp)) {
                    TeachingScreen(TeachingUi(ready = true), actions)
                }
            }
        }
        val first = TeachingRegistry.defaults.tips.first { it.kind == TeachingKind.Tip }
        val tag = "tips-row-${first.id.raw}"
        onNodeWithTag(tag).performScrollTo()
        onNode(hasText("Show me") and hasAnyAncestor(hasTestTag(tag))).performClick()
        assertEquals(listOf(first.id), calls)
        // Tip prose stays behind Show me rather than turning the list into a wall of messages.
        onNodeWithText("Each tip shows at its moment, at most twice. Replay any of them here.")
            .assertExists()
    }
}
