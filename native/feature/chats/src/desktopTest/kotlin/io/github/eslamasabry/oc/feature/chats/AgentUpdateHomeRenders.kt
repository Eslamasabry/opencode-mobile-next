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
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
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
 * Pictures, nothing asserted: Home at 360 dp with an agent's update running (its own status line: the mark at 40 %, the
 * agent's name, Show progress), and the agents' update sheet from the REAL sheet presenter (AgentUpdateSheetModel over
 * a real offer) with its button in three states, light and dark. Writes `build/agent-update-home/<name>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class AgentUpdateHomeRenders {
    private val out = File("build/agent-update-home").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, dark: Boolean) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
        runDesktopComposeUiTest(900, 2000) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, 1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
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
            mainClock.advanceTimeBy(1_000)
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

        override suspend fun review(): Outcome<Unit> = Unit.ok()
    }

    private fun item(
        id: String,
        name: String,
        from: String,
        to: String,
        downloadMb: Long,
        installedMb: Long,
        group: AgentUpdateGroup = AgentUpdateGroup.Optional,
        removable: Boolean = false,
    ) =
        AgentUpdateItem(
            id = AgentId(id),
            name = name,
            fromVersion = from,
            toVersion = to,
            downloadBytes = downloadMb * MB,
            installedBytes = installedMb * MB,
            group = group,
            keepAllowed = group == AgentUpdateGroup.Optional,
            removeAllowed = removable,
            notKeptReason = null,
            choice = null,
            neededWhen = if (group == AgentUpdateGroup.Needed) AgentNeedRule.WithAnyUpdate else null,
        )

    private val offer =
        AgentUpdateOffer(
            appUpdateKey = "10110",
            items =
                listOf(
                    item("agent-paseo", "Agent helper", "0.15.4", "0.16.1", 330, 330, AgentUpdateGroup.Needed),
                    item("node", "Node.js", "22.11.0", "22.12.0", 30, 90, AgentUpdateGroup.Needed),
                    item("claude", "Claude Code", "2.1.325", "2.1.330", 372, 372),
                    item("codex", "Codex", "0.169.0", "0.172.0", 196, 497, removable = true),
                    item("antigravity", "Antigravity", "1.5.9", "1.6.2", 91, 253, removable = true),
                    item("opencode", "OpenCode", "2.2.6", "2.3.1", 130, 260),
                ),
            network = AgentUpdateNetwork.Unmetered,
            freeBytes = 6_000 * MB,
            neededGoesAhead = false,
            sheetDue = true,
            answered = false,
        )

    /** The real presenter over the offer, with the person's picks applied. */
    private fun sheetState(picks: Map<String, AgentUpdateChoice>): ChatsUi {
        val model = AgentUpdateSheetModel(Offered(offer), CoroutineScope(Job()))
        picks.forEach { (id, choice) -> model.choose(AgentId(id), choice) }
        return mixed().copy(agentUpdates = model.current())
    }

    @Test
    fun `renders of Home with the agents' update running and the update sheet, at 360 dp`() {
        val running =
            mixed().copy(
                download = HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Running(40), name = "Claude Code")
            )
        val keep = AgentUpdateChoice.Keep
        val scenes =
            listOf(
                "home-update-running" to running,
                "sheet-update-4-agents" to sheetState(emptyMap()),
                "sheet-update-2-remove-1" to
                    sheetState(mapOf("codex" to AgentUpdateChoice.Remove, "antigravity" to keep)),
                "sheet-keep-these-versions" to
                    sheetState(mapOf("claude" to keep, "codex" to keep, "antigravity" to keep, "opencode" to keep)),
            )
        for ((name, state) in scenes) for (dark in listOf(false, true)) shoot("$name-${if (dark) "dark" else "light"}", state, dark)
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}
