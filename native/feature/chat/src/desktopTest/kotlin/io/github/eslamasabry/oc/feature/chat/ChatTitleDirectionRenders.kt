package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.transcript.state.TranscriptRow
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPhase
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.ChatUi
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.feature.chat.world.ComposerModel
import io.github.eslamasabry.oc.feature.chat.world.ComposerModels
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.toPersistentList

/**
 * Capture only (no comparison, E-4): the chat's header capsule with a title in the other direction than the app's (an
 * English title in the Arabic app, an Arabic title in the English app), Solid and Glass, at 360 dp, text 1.0 and 1.3.
 * Writes PNGs to `TITLE_BIDI_OUT`; does nothing when that is unset, so CI never writes.
 */
@OptIn(ExperimentalTestApi::class)
class ChatTitleDirectionRenders {
    private val out = System.getenv("TITLE_BIDI_OUT")?.let { File(it).also(File::mkdirs) }

    private val composerActions =
        object : BasicComposerActions {
            override fun draftChanged(text: String) = Unit

            override suspend fun send(text: String) = true

            override suspend fun selectModel(choice: ComposerModel?, variant: String) = true
        }

    private val host =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                BasicComposer(
                    BasicComposerUi(
                        available = true,
                        sending = false,
                        steers = true,
                        targetKey = "title-bidi",
                        models = ComposerModels(usesDefault = true),
                        filesAvailable = true,
                    ),
                    composerActions,
                    chrome,
                    modifier,
                )
            }
        }

    private fun shoot(style: Style, scale: Float, tag: String, title: String, name: String) {
        val o = out ?: return
        Locale.setDefault(Locale.forLanguageTag(tag))
        try {
            kitUiTest(WIDTH, HEIGHT) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(DENSITY, scale)) {
                        KitTheme(
                            appearance =
                                KitAppearance(mode = AppearanceMode.Light, style = style, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = tag,
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor()) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    val presenter = ChatPresenter(FakeChatWorld(), RecordingNavigator(), Immediate)
                                    ChatScreen(screen(title), presenter, host)
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(o, "$name.png"))
            }
        } finally {
            Locale.setDefault(Locale.forLanguageTag("en"))
        }
    }

    private fun screen(title: String): ChatUi {
        val rows: List<TranscriptRow> =
            listOf(prompt("u2", SECOND)) + turn(1, ask = FIRST, answer = ANSWER).asReversed()
        return ChatUi.loading(Ref).copy(title = title, phase = ChatPhase.Live, rows = rows.toPersistentList())
    }

    @Test
    fun `title direction renders`() {
        if (out == null) return
        for (style in listOf(Style.Solid, Style.Glass)) for (scale in listOf(1.0f, 1.3f)) {
            val size = if (scale == 1.0f) "t10" else "t13"
            shoot(style, scale, "ar", ENGLISH, "english-title-in-arabic-${style.storageName}-$size")
            shoot(style, scale, "en", ARABIC, "arabic-title-in-english-${style.storageName}-$size")
        }
    }

    private companion object {
        /** 360 dp wide at the A54's density. */
        const val DENSITY = 2.625f
        const val WIDTH = 945
        const val HEIGHT = 2100
        const val ENGLISH = "Fix the flaky checkout test"
        const val ARABIC = "أصلح اختبار الدفع المتقطع في صفحة السلة"
        const val FIRST = "Why does the checkout test fail one run in five?"
        const val ANSWER =
            "The coupon total is read before the network call settles. Waiting on the settled state fixes it."
        const val SECOND = "Good, now make the same fix in the cart test."
    }
}
