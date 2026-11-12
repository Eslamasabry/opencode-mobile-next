package io.github.eslamasabry.oc.feature.chat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.world.PendingSend
import io.github.eslamasabry.oc.feature.chat.world.PendingState
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.queuedKeepForReview
import io.github.eslamasabry.oc.ui.words.res.queuedResendConfirm
import io.github.eslamasabry.oc.ui.words.res.queuedResendMessage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Paseo fault 2 gate check: an unconfirmed send with nothing on the waiting-sends page offers Send again on its bubble.
 * The words may already have arrived, so that Send again asks first with the waiting-sends page's own warning (N-48),
 * and nothing is resent until the person confirms.
 */
@OptIn(ExperimentalTestApi::class)
class UnconfirmedSendAgainTest {
    private val sendAgain: String by lazy { runBlocking { getString(Res.string.queuedResendConfirm) } }
    private val warning: String by lazy { runBlocking { getString(Res.string.queuedResendMessage) } }
    private val keep: String by lazy { runBlocking { getString(Res.string.queuedKeepForReview) } }

    private fun ui(scene: ChatScene) =
        scene.live().copy(pending = persistentListOf(PendingSend(7L, "say hi", PendingState.Unconfirmed)))

    @Test
    fun `Send again on an unconfirmed bubble asks first and resends only when confirmed`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(ui(scene)) } }
            waitForIdle()
            onNodeWithTag("chat-receipt-send-again-7").performClick()
            waitForIdle()
            assertEquals(emptyList(), scene.world.calls.filter { it.startsWith("retrySend") }, "one tap never resends")
            onNodeWithText(warning).assertExists()
            // The dialog's confirm is the last "Send again" (the bubble's own line action is the first).
            onAllNodesWithText(sendAgain).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
            waitForIdle()
            assertEquals(listOf("retrySend 7"), scene.world.calls.filter { it.startsWith("retrySend") })
        }

    @Test
    fun `keeping it for review sends nothing`() =
        runDesktopComposeUiTest(412, 915) {
            val scene = ChatScene()
            setContent { E20Scene { scene.Screen(ui(scene)) } }
            waitForIdle()
            onNodeWithTag("chat-receipt-send-again-7").performClick()
            waitForIdle()
            onNodeWithText(keep).performClick()
            waitForIdle()
            assertEquals(emptyList(), scene.world.calls.filter { it.startsWith("retrySend") })
            onNodeWithText(warning).assertDoesNotExist()
        }
}
