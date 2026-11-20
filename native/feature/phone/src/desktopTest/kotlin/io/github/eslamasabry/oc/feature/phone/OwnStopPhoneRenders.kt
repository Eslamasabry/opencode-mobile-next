package io.github.eslamasabry.oc.feature.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.feature.phone.presentation.KeepAliveStepKind
import io.github.eslamasabry.oc.feature.phone.presentation.KeepRunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneAct
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerState
import io.github.eslamasabry.oc.feature.phone.presentation.RunningActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneActions
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneAnswers
import io.github.eslamasabry.oc.feature.phone.presentation.ThisPhoneUi
import io.github.eslamasabry.oc.feature.phone.ui.ThisPhoneScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/**
 * Pictures of Settings > This phone after the person's own stop, at 360 dp wide, dark, Solid and Glass, text 1.0 and
 * 1.3. Writes `build/own-stop-phone/<name>.png` to look at; nothing is compared (picture-making, not proof).
 */
@OptIn(ExperimentalTestApi::class)
class OwnStopPhoneRenders {
    private val out = File("build/own-stop-phone").also { it.mkdirs() }

    private class Calls : ThisPhoneActions, RunningActions, KeepRunningActions {
        override fun act(act: PhoneAct) = Unit
        override fun stopServer() = Unit
        override fun askRestart() = Unit
        override fun askUpdate() = Unit
        override fun confirm() = Unit
        override fun dismiss() = Unit
        override fun openRunning() = Unit
        override fun openKeepRunning() = Unit
        override fun back() = Unit
        override fun shown(visible: Boolean) = Unit
        override fun retry() = Unit
        override fun details(pid: Int) = Unit
        override fun askStop(pid: Int) = Unit
        override fun confirmStop() = Unit
        override fun openChildLimitSettings() = Unit
        override fun hideChildLimit() = Unit
        override fun resumed() = Unit
        override fun dismissRecovery() = Unit
        override fun refreshStorage() = Unit
        override fun measureStorage() = Unit
        override fun open(step: KeepAliveStepKind) = Unit
    }

    @Test
    fun `renders of This phone after the person's own stop`() {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val facts =
            PhoneServerFacts(
                status = RuntimeStatus(installed = true, phase = RuntimePhase.Ready),
                stoppedByPerson = true,
            )
        val answer = ThisPhoneAnswers.of(PhoneServerState.of(facts), facts)
        val ui = ThisPhoneUi(answer = answer, serverRows = persistentListOf())
        for (style in listOf(Style.Solid, Style.Glass))
            for (scale in listOf(1f, 1.3f))
                runDesktopComposeUiTest(900, 2000) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2.5f, scale)) {
                            KitTheme(
                                appearance =
                                    KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off, style = style),
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ThisPhoneScreen(ui, Calls())
                                }
                            }
                        }
                    }
                    waitForIdle()
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    ImageIO.write(
                        onRoot().captureToImage().toAwtImage(),
                        "png",
                        File(out, "settings-${style.storageName}-${if (scale == 1f) "1.0" else "1.3"}-dark.png"),
                    )
                }
    }
}
