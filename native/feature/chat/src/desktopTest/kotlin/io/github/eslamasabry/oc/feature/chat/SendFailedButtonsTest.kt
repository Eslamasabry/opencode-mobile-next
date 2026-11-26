package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.gate.flicker.Allow
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B-399 (F-044): under a failed send bubble (including a genuine StaleScope refusal), tapping Details unfolds the
 * plain reason and code, and tapping Try again invokes retrySend — never dead buttons.
 */
@OptIn(ExperimentalTestApi::class)
class SendFailedButtonsTest {
    private val foldOpens = "tapping Details unfolds its body and pushes Try again down: intended"
    private val allowDetailsFold =
        setOf(
            Allow.first("kit-details-body", foldOpens),
            Allow.first("kit-details-text", foldOpens),
            Allow.first("kit-details-copy-all", foldOpens),
            Allow.jump("chat-send-error-try-again", foldOpens),
            Allow.jump("Try again", foldOpens),
            Allow.jump("kit-details-fold", foldOpens),
            Allow.jump("chat-send-failure-details", foldOpens),
            Allow.jump("kit-details-toggle", foldOpens),
            Allow.jump("Details", foldOpens),
        )

    private fun failedUi(scene: ChatScene, failure: Failure) =
        scene.live().copy(
            pending = persistentListOf(PendingSend(7L, "follow up", PendingState.Failed, failure = failure)),
        )

    @Test
    fun `B-399-2b failed StaleScope bubble opens Details with reason and Try again invokes retrySend`() =
        kitUiTest(412, 915, allow = allowDetailsFold) {
            val scene = ChatScene()
            val details = Redactor().details("model read back differently: model")
            val failure = Failure.Refused(RefusalReason.StaleScope, details)
            setContent { E20Scene { scene.Screen(failedUi(scene, failure)) } }
            waitForIdle()

            assertEquals(0, onAllNodesWithTag("kit-details-body", useUnmergedTree = true).fetchSemanticsNodes().size)
            onNodeWithTag("kit-details-toggle").performClick()
            waitForIdle()
            onNodeWithTag("kit-details-body", useUnmergedTree = true).assertExists()
            onNodeWithText("Refused.StaleScope", useUnmergedTree = true).assertExists()
            onNodeWithText("model read back differently: model", useUnmergedTree = true).assertExists()

            onNodeWithTag("chat-send-error-try-again").performClick()
            waitForIdle()
            assertEquals(listOf("retrySend 7"), scene.world.calls.filter { it.startsWith("retrySend") })
        }

    @Test
    fun `B-399-2c failed StaleScope bubble without extra details still opens Details with failure code`() =
        kitUiTest(412, 915, allow = allowDetailsFold) {
            val scene = ChatScene()
            val failure = Failure.Refused(RefusalReason.StaleScope)
            setContent { E20Scene { scene.Screen(failedUi(scene, failure)) } }
            waitForIdle()

            onNodeWithTag("kit-details-toggle").performClick()
            waitForIdle()
            onNodeWithTag("kit-details-body", useUnmergedTree = true).assertExists()
            assertEquals(
                1,
                onAllNodesWithText("Refused.StaleScope", useUnmergedTree = true).fetchSemanticsNodes().size,
            )
            onNodeWithTag("chat-send-error-try-again").performClick()
            waitForIdle()
            assertEquals(listOf("retrySend 7"), scene.world.calls.filter { it.startsWith("retrySend") })
        }
}
