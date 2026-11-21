package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.TapAudit
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Issue 147, priority 1: the tap audit over the home page (where New conversation and opening a conversation start),
 * in its opening, empty and full states. Every control that is drawn acts on the first tap or says why it cannot.
 */
@OptIn(ExperimentalTestApi::class)
class HomeTapAuditTest {
    private fun states(): List<Pair<String, ChatsUi>> =
        listOf(
            "home opening (skeleton)" to ChatsUi(phase = ChatsPhase.Opening),
            "home empty" to ChatsUi(phase = ChatsPhase.Empty),
            "home not read" to ChatsUi(phase = ChatsPhase.NotRead),
            "home with conversations" to mixed(),
        )

    @Test
    fun `every control on the home page answers its first tap in every state`() {
        val found = ArrayList<TapAudit.Offender>()
        for ((name, ui) in states()) {
            val audit = TapAudit(name)
            kitUiTest(412, 915) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(1f)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                audit.provide {
                                    Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                        ChatsScreen(ui, RecordingChatsActions())
                                    }
                                }
                            }
                        }
                    }
                }
                found += audit.run(this, 412, 915)
            }
        }
        assertTrue(found.isEmpty(), "tap audit offenders (${found.size}):\n" + found.joinToString("\n"))
    }
}
