@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.state

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitVisibility
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitVisibility
import kotlin.test.Test

/**
 * Owner 2026-11-14 (a ten-second load said nothing): the words of a slow wait come after their moment and not before,
 * never when something else already says it, and their clock waits through the visibility tick (background CPU
 * 2026-11-14): a wait that runs out while the app is hidden shows its words once the app is back.
 */
class KitSlowWordsTest {
    private val words = "Loading the conversation"

    @Test
    fun `LOAD-6 the words come after their moment and not before`() =
        runDesktopComposeUiTest(400, 400) {
            mainClock.autoAdvance = false
            setContent { KitTheme(KitAppearance(), FakeKitPlatform(), "en") { KitSlowWords(words, AFTER) } }
            mainClock.advanceTimeBy(AFTER / 2)
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(0)
            mainClock.advanceTimeBy(AFTER)
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(1)
        }

    @Test
    fun `LOAD-6 null words show nothing however long the wait`() =
        runDesktopComposeUiTest(400, 400) {
            mainClock.autoAdvance = false
            setContent { KitTheme(KitAppearance(), FakeKitPlatform(), "en") { KitSlowWords(null, AFTER) } }
            mainClock.advanceTimeBy(AFTER * 5)
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(0)
        }

    @Test
    fun `BG-CPU-003 a wait that runs out while the app is hidden shows its words when the app is back`() =
        runDesktopComposeUiTest(400, 400) {
            mainClock.autoAdvance = false
            val visibility = KitVisibility(visible = false)
            setContent {
                CompositionLocalProvider(LocalKitVisibility provides visibility) {
                    KitTheme(KitAppearance(), FakeKitPlatform(), "en") { KitSlowWords(words, AFTER) }
                }
            }
            mainClock.advanceTimeBy(AFTER * 5)
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(0)
            visibility.set(true)
            mainClock.advanceTimeBy(AFTER)
            onAllNodesWithText(words, useUnmergedTree = true).assertCountEquals(1)
        }

    private companion object {
        const val AFTER = 1_000L
    }
}
