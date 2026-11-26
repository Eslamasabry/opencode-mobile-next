package io.github.eslamasabry.oc.feature.chats

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
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentRemovalFacts
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStarted
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 360 dp renders of AgentUpdateSheet in Glass Dark & Light (verifies no nested card-inside-card inside KitSheet).
 * Writes `build/agent-update-glass/sheet-glass-dark.png` and `sheet-glass-light.png`.
 */
@OptIn(ExperimentalTestApi::class)
class AgentUpdateGlassSheetRenders {
    private val out = File("build/agent-update-glass").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, dark: Boolean) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
        kitUiTest(PHONE_W_PX, PHONE_H_PX) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY, 1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(style = Style.Glass, mode = mode, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                ChatsScreen(state, RecordingChatsActions())
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(SETTLE_MS)
            waitForIdle()
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
        }
    }

    private class Offered(offer: AgentUpdateOffer) : AgentUpdateChoices {
        override val offer: StateFlow<AgentUpdateOffer?> = MutableStateFlow(offer)

        override suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>): Outcome<AgentUpdateStarted> =
            AgentUpdateStarted(emptyList(), emptyList(), emptyList()).ok()

        override suspend fun notNow(): Outcome<Unit> = Unit.ok()

        override fun removalFacts(id: AgentId): AgentRemovalFacts = AgentRemovalFacts(null, true, true)

        override suspend fun review(raise: Boolean): Outcome<Unit> = Unit.ok()
    }

    private fun item(
        id: String,
        name: String,
        from: String,
        to: String,
        mb: Long,
        group: AgentUpdateGroup = AgentUpdateGroup.Optional,
        removable: Boolean = false,
    ) =
        AgentUpdateItem(
            id = AgentId(id),
            name = name,
            fromVersion = from,
            toVersion = to,
            downloadBytes = mb * MB,
            installedBytes = mb * MB,
            group = group,
            keepAllowed = group == AgentUpdateGroup.Optional,
            removeAllowed = removable,
            notKeptReason = null,
            choice = null,
            neededWhen = if (group == AgentUpdateGroup.Needed) AgentNeedRule.WithAnyUpdate else null,
        )

    private val offer =
        AgentUpdateOffer(
            appUpdateKey = "10162",
            items =
                listOf(
                    item("agent-paseo", "Agent helper", "0.16.1", "0.17.3", 330, AgentUpdateGroup.Needed),
                    item("claude", "Claude Code", "2.1.327", "2.1.330", 372),
                    item("codex", "Codex", "0.171.0", "0.172.0", 201, removable = true),
                    item("antigravity", "Antigravity", "1.5.15", "1.5.17", 93, removable = true),
                ),
            network = AgentUpdateNetwork.Unmetered,
            freeBytes = FREE_MB * MB,
            neededGoesAhead = false,
            sheetDue = true,
            answered = false,
        )

    @Test
    fun `B-KIT-032 AgentUpdateSheet renders flat without nested card inside Glass sheet at 360 dp`() {
        val model = AgentUpdateSheetModel(Offered(offer), CoroutineScope(Job()))
        val state = mixed().copy(agentUpdates = model.current())
        shoot("sheet-glass-dark", state, dark = true)
        shoot("sheet-glass-light", state, dark = false)
    }

    private companion object {
        const val PHONE_W_PX: Int = 900
        const val PHONE_H_PX: Int = 2000
        const val DENSITY: Float = 2.5f
        const val SETTLE_MS: Long = 1_000L
        const val FREE_MB: Long = 6_000L
        const val MB: Long = 1024L * 1024L
    }
}
