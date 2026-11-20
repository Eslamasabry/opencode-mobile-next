package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.header.presentation.ChatHeaderUi
import io.github.eslamasabry.oc.feature.chat.header.ui.ChatHeaderSlot
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.presentation.LiveTurnUi
import io.github.eslamasabry.oc.feature.chat.presentation.TurnFactsPresenter
import io.github.eslamasabry.oc.feature.chat.rb.o42.ChatScene
import io.github.eslamasabry.oc.feature.chat.requests.presentation.CardState
import io.github.eslamasabry.oc.feature.chat.requests.presentation.ChatRequestActions
import io.github.eslamasabry.oc.feature.chat.requests.presentation.RequestSlotUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPaneColumn
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPaneColumn
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.collections.immutable.toImmutableList

/**
 * Pictures only (asserts nothing), big screens slice 3: the REAL conversation (capsule header, a reply holding code
 * and a diff, a waiting permission card with Allow once / Deny, the message box) at the size the shell gives it beside
 * the 360 dp list on a 1280 x 800 window (920 dp, inside a `KitPaneWindow`, its column at the pane's start), and the
 * same conversation over the whole window as today. Dark, density 2, into `build/bigscreen/list-pane-real/`.
 */
@OptIn(ExperimentalTestApi::class)
class ListPaneChatRenders {
    private object NoRequestActions : ChatRequestActions {
        override fun shown(id: RequestId) = Unit
        override fun allowOnce(id: RequestId) = Unit
        override fun reject(id: RequestId) = Unit
        override fun askAlwaysAllow(id: RequestId) = Unit
        override fun confirmAlwaysAllow() = Unit
        override fun cancelAlwaysAllow() = Unit
        override fun keepAsking(id: RequestId) = Unit
        override fun choose(id: RequestId, option: String) = Unit
        override fun openOther(id: RequestId) = Unit
        override fun editOther(id: RequestId, text: String) = Unit
        override fun sendOther(id: RequestId) = Unit
        override fun openSheet(id: RequestId) = Unit
        override fun closeSheet(id: RequestId) = Unit
        override fun chooseInSheet(id: RequestId, prompt: Int, label: String) = Unit
        override fun editSheetOther(id: RequestId, prompt: Int, text: String) = Unit
        override fun sendSheet(id: RequestId) = Unit
        override fun undo(id: RequestId) = Unit
        override fun retry(id: RequestId) = Unit
    }

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(0)
    }

    private val code =
        "The flakiness comes from `CheckoutBloc` racing the price refresh. Here is the settled wait, and the " +
            "diff that " +
            "went into the test:\n\n```kotlin\nfun applyCoupon(code: String): Total {\n    val price = api.re" +
                "freshPrice()   // awaits the network\n    return cart.total.copy(discount = coupons.lookup(code)" +
                ".amount, price = price)\n}\n```\n\n" +
            "```diff\n--- a/test/checkout_test.dart\n+++ b/test/checkout_test.dart\n@@ -41,7 +41,9 @@ void ma" +
                "in() {\n   test('coupon applies once', () async {\n-    await Future.delayed(const Duration(mill" +
                "iseconds: 200));\n+    await bloc.stream.firstWhere((s) => s.settled);\n     expect(bloc.state.t" +
                "otal, 1800);\n+    expect(bloc.state.couponApplications, 1);\n   });\n```\n\nI ran it ten times " +
                "and every run passed."

    private fun rows(): List<TranscriptRow> =
        listOf(
            WorkLineRow("work:m1", "m1", calls = 1, failed = 0, state = WorkState.Running),
            prompt("m1", "Now edit the test file and clean up the cache folder"),
            footer("u0"),
            block("u0", "a0", "pc", 0, code),
            WorkLineRow("work:u0", "u0", calls = 4, failed = 0, state = WorkState.Done),
            prompt("u0", "Why does the checkout total change after the coupon is applied? Show me the fix."),
        )

    @Composable
    internal fun Conversation() {
        val scene = remember { ChatScene() }
        val facts = TurnFactsPresenter(FixedClock).present(runningEditFeed(), 58_000, waitingForYou = true)
        ChatScreen(
            ChatUi.loading(Ref).copy(
                title = "Fix flaky checkout test",
                phase = ChatPhase.Live,
                rows = rows().toImmutableList(),
                live = LiveTurnUi(stopping = false),
            ),
            scene.actions,
            scene.host,
            header = ChatHeaderSlot(
                ChatHeaderUi(title = "Fix flaky checkout test", projectName = "shopfront", serverName = "This phone"),
                scene.headerActions,
            ),
            facts = facts,
            requests = RequestSlotUi(permissionCard(CardState.Waiting)),
            requestActions = NoRequestActions,
        )
    }

    private fun shoot(name: String, wDp: Int, hDp: Int, content: @Composable () -> Unit) {
        val out = File("build/bigscreen/list-pane-real").also { it.mkdirs() }
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("en"))
        try {
            val platform = DesktopKitPlatform()
            runDesktopComposeUiTest(wDp * 2, hDp * 2) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(2f, 1f),
                        LocalKitPlatform provides platform,
                    ) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = platform,
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                CompositionLocalProvider(LocalKitRedactor provides Redactor()) { content() }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    /** After: the pages pane beside the 360 dp list, its column at the pane's start. */
    @Test
    fun conversationPane() =
        shoot("chat-pane-920x800", 920, 800) {
            KitPaneWindow(Modifier.fillMaxSize()) { CompositionLocalProvider(
                LocalKitPaneColumn provides KitPaneColumn.PAGE_MAX_DP.dp,
            ) { Conversation() } }
        }

    /** Decision 3: the pages pane of a 1024 x 1366 portrait tablet beside the 360 dp list. */
    @Test
    fun conversationPanePortrait() =
        shoot("chat-pane-664x1366", 664, 1366) {
            KitPaneWindow(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalKitPaneColumn provides KitPaneColumn.PAGE_MAX_DP.dp) { Conversation() }
            }
        }

    /** Before: today's conversation over the whole 1280 x 800 window. */
    @Test
    fun conversationBefore() = shoot("before-chat-1280x800", 1280, 800) { Conversation() }

    /** Tablet portrait, 800 x 1280: under 840 dp the conversation is the whole window, before and after alike. */
    @Test
    fun conversationPortrait() = shoot("chat-800x1280", 800, 1280) { Conversation() }
}
