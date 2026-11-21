package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import io.github.eslamasabry.oc.feature.chat.E20Scene
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowHost
import io.github.eslamasabry.oc.ui.kit.motion.LocalKitGrowHost
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/**
 * Open-conversation-plain (Galaxy S22, build 10054): the conversation's loading skeleton was drawn over Home while the
 * page was still arriving. The skeleton is the conversation's own page, for content that is really unknown, and only
 * once the page has arrived: while the page transition runs, the body is empty.
 */
@OptIn(ExperimentalTestApi::class)
class OpeningSkeletonTest {
    private class Host(
        override val sharedTransitionScope: SharedTransitionScope,
        override val animatedVisibilityScope: AnimatedVisibilityScope,
    ) : KitGrowHost {
        override val transformEnabled: Boolean get() = false
        override val opening: Boolean get() = true
        override val rowTransformInFlight: Boolean get() = false
    }

    @Test
    fun `OCP-04 an unknown conversation shows no skeleton while its page arrives, then the skeleton`() =
        kitUiTest(412, 915) {
            val scene = ChatScene()
            val loading = scene.live().copy(phase = ChatPhase.Loading, rows = persistentListOf())
            mainClock.autoAdvance = false
            setContent {
                E20Scene {
                    val arrival = remember { MutableTransitionState(false).apply { targetState = true } }
                    SharedTransitionLayout {
                        val shared = this
                        // As the shell hosts a pushed page: the entry's own enter transition (the page transition).
                        AnimatedVisibility(arrival, enter = fadeIn(tween(PAGE_MS))) {
                            CompositionLocalProvider(LocalKitGrowHost provides Host(shared, this)) { scene.Screen(loading) }
                        }
                    }
                }
            }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(0)
            mainClock.advanceTimeBy(PAGE_MS / 2L)
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(0)
            mainClock.advanceTimeBy(PAGE_MS.toLong())
            onAllNodesWithTag("chat-loading", useUnmergedTree = true).assertCountEquals(1)
        }

    private companion object {
        const val PAGE_MS = 450
    }
}
