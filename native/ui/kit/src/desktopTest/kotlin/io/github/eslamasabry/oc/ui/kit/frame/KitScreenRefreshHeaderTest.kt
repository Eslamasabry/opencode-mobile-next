package io.github.eslamasabry.oc.ui.kit.frame

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chats: "the pull to refresh is fighting with the conversations list". Refresh must be the outermost scroll node,
 * around the scrolling header (list, then header, then refresh), as Flutter's RefreshIndicator wraps the whole
 * CustomScrollView: the header returns before a pull starts, and an upward drag retracts the pull before the header
 * hides.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class KitScreenRefreshHeaderTest {
    private var refreshes = 0
    private lateinit var pull: PullToRefreshState

    private fun androidx.compose.ui.test.ComposeUiTest.screen() {
        setContent {
            pull = rememberPullToRefreshState()
            KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                KitScreen(
                    page = true,
                    header = { KitText("Conversations", Modifier.testTag("title").fillMaxWidth().height(80.dp)) },
                    refresh = KitScreenRefresh(refreshing = false, onRefresh = { refreshes++ }, state = pull),
                ) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(60) { KitText("Row $it", Modifier.fillMaxWidth().height(56.dp)) }
                    }
                }
            }
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.headerTop() =
        onNodeWithTag("kit-screen-header").getUnclippedBoundsInRoot().top.value

    private fun indicatorTop() = pull.distanceFraction

    private fun androidx.compose.ui.test.ComposeUiTest.hideHeader() {
        onNodeWithTag("list").performTouchInput {
            down(Offset(centerX, bottom - 20f))
            repeat(4) { moveBy(Offset(0f, -30f)) }
            // Let go at rest: a fling would carry the list far from its top.
            advanceEventTime(400)
            up()
        }
        waitForIdle()
    }

    @Test
    fun `a drag down at the list top brings the hidden header back before the pull indicator moves`() =
        runDesktopComposeUiTest(360, 640) {
            screen()
            hideHeader()
            val hidden = headerTop()
            val idle = indicatorTop()
            assertTrue(hidden < 0f, "header hidden after scrolling up: $hidden")
            onNodeWithTag("list").performTouchInput { down(Offset(centerX, top + 10f)) }
            // Drag down in small steps: the list returns to its top, then the header, and only then the pull.
            repeat(16) {
                onNodeWithTag("list").performTouchInput { moveBy(Offset(0f, 10f)) }
                waitForIdle()
                if (headerTop() < 0f) assertEquals(idle, indicatorTop(), "pull distanceFraction stays 0 while the header is away")
            }
            assertEquals(0f, headerTop(), "header is fully back")
            assertTrue(indicatorTop() > idle, "the pull starts once the header is back")
            onNodeWithTag("list").performTouchInput { up() }
        }

    @Test
    fun `with the pull extended a drag up retracts the pull before the header hides`() =
        runDesktopComposeUiTest(360, 640) {
            screen()
            val idle = indicatorTop()
            onNodeWithTag("list").performTouchInput {
                down(Offset(centerX, top + 10f))
                repeat(12) { moveBy(Offset(0f, 10f)) }
            }
            waitForIdle()
            val pulled = indicatorTop()
            assertTrue(pulled > idle, "pull extended: $pulled > $idle")
            val header = headerTop()
            onNodeWithTag("list").performTouchInput { repeat(3) { moveBy(Offset(0f, -10f)) } }
            waitForIdle()
            assertTrue(indicatorTop() < pulled, "pull retracts")
            assertEquals(header, headerTop(), "header has not moved while the pull retracts")
            onNodeWithTag("list").performTouchInput { up() }
        }

    @Test
    fun `a normal pull from the top with the header shown still refreshes`() =
        runDesktopComposeUiTest(360, 640) {
            screen()
            onNodeWithTag("list").performTouchInput {
                down(Offset(centerX, top + 10f))
                repeat(8) { moveBy(Offset(0f, 60f)) }
                up()
            }
            waitForIdle()
            assertEquals(1, refreshes)
        }
}
