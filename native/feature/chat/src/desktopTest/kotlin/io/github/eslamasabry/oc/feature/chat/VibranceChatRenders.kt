package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.transcript.state.BlockRow
import io.github.eslamasabry.oc.engine.transcript.state.Origin
import io.github.eslamasabry.oc.engine.transcript.state.RowKind
import io.github.eslamasabry.oc.engine.transcript.state.RunFacts
import io.github.eslamasabry.oc.engine.transcript.state.WorkLineRow
import io.github.eslamasabry.oc.engine.transcript.state.WorkState
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.FrostPolicy
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.effects.LiveVibrance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

/**
 * Vibrance spike renders of a chat transcript (a finished turn, a code block and a reply), Glass, dark and light, for
 * each [LiveVibrance]. Not a check: runs only when VIBRANCE_OUT names a folder.
 */
@OptIn(ExperimentalTestApi::class)
class VibranceChatRenders {
    private class Outside : ChatOutside {
        override fun mask(text: String) = text
        override fun copy(text: String) = true
        override fun share(text: String) = true
        override fun checkLink(url: String): LinkCheck = LinkCheck.Ask("example.com")
        override fun openLink(url: String) = true
    }

    private object Actions : BasicComposerActions {
        override fun draftChanged(text: String) {}
        override suspend fun send(text: String) = true
        override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
    }

    private class VibePlatform(base: DesktopKitPlatform = DesktopKitPlatform()) : KitPlatform by base {
        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? =
            FrostPolicy.blurRadiusPx(spec.sigmaDp, density).let { BlurEffect(it, it) }
    }

    private fun code(start: Int, text: String) =
        BlockRow("b:a1/p:$start", "u1", RowKind.Code, "p", start, text, false, false, Origin.Loaded)

    private fun shoot(option: LiveVibrance, style: Style, dark: Boolean, out: File) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val world = FakeChatWorld()
        world.transcript.value =
            stateOf(
                prompt("u1", "Fix the login redirect loop. After signing in the app sends me straight back to /login."),
                block("u1", "a1", "p", 0, "I found it. `LoginRedirect.remember` stores **/login** itself as the next route, so after sign-in the app redirects to the login page again. I am guarding that case and adding a test."),
                WorkLineRow("work:u1", "u1", calls = 3, failed = 0, state = WorkState.Done),
                code(200, "```kotlin\nfun remember(route: String) {\n    if (route.startsWith(\"/login\")) return\n    target.value = route\n}\n```"),
                block("u1", "a2", "p2", 0, "Done. The auth tests pass, including the new one for the loop. Want me to open a pull request?"),
                prompt("u2", "Yes, and keep the description short."),
                block("u2", "a3", "p3", 0, "Opened it with a three-line description. The checks are running now."),
                run = RunFacts(busy = false),
            )
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = Outside())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        val host =
            object : ComposerHost {
                @Composable
                override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                    BasicComposer(BasicComposerUi(available = true, sending = false, steers = false, targetKey = "p/s", models = ComposerModels()), Actions, chrome, modifier)
                }
            }
        runDesktopComposeUiTest(1081, 2402) {
            mainClock.autoAdvance = false
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                                style = style,
                                liveBackground = true,
                                liveStyle = if (style == Style.Glass) "auroraImage" else "aurora",
                                liveVibrance = option,
                            ),
                        platform = VibePlatform(),
                        localeTag = "en",
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            val state by states.collectAsState()
                            KitStandaloneRedaction { KitSheetHost { ChatScreen(state, p, host) } }
                        }
                    }
                }
            }
            repeat(30) { mainClock.advanceTimeBy(100) }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "${option.wire}-chat-${style.storageName}-${if (dark) "dark" else "light"}.png"))
        }
    }

    @Test
    fun `vibrance renders of a chat transcript`() {
        val out = System.getenv("VIBRANCE_OUT")?.let(::File) ?: return
        out.mkdirs()
        for (option in LiveVibrance.entries) for (dark in listOf(true, false)) shoot(option, Style.Glass, dark, out)
    }
}
