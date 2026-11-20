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
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotice
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadFact
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadPhase
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotices
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pictures, nothing asserted, from the REAL sheet presenter (AgentUpdateSheetModel over a real offer), 360 dp, light
 * and dark: the sheet when the chosen updates do not fit (the button off, its reason at it) and when Keep makes them
 * fit; Home after Not now with a signed-out agent (the sign-in notice AND the Review line). Writes
 * `build/agent-update-noroom/<name>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class AgentUpdateNoRoomRenders {
    private val out = File("build/agent-update-noroom").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, dark: Boolean, fontScale: Float = 1f) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
        runDesktopComposeUiTest(900, 2000) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
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

    /** One row of the offer, as the engine reads it from the disk and the pins. */
    private data class Part(
        val id: String,
        val name: String,
        val from: String,
        val to: String,
        val downloadMb: Long,
        val installedMb: Long,
        val group: AgentUpdateGroup = AgentUpdateGroup.Optional,
        val removable: Boolean = false,
    ) {
        fun item() =
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
    }

    private fun offer(freeMb: Long, answered: Boolean = false) =
        AgentUpdateOffer(
            appUpdateKey = "10110",
            items =
                listOf(
                    Part("agent-paseo", "Agent helper", "0.15.4", "0.16.1", 330, 330, AgentUpdateGroup.Needed).item(),
                    Part("node", "Node.js", "22.11.0", "22.12.0", 30, 90, AgentUpdateGroup.Needed).item(),
                    Part("claude", "Claude Code", "2.1.325", "2.1.330", 372, 372).item(),
                    Part("codex", "Codex", "0.169.0", "0.172.0", 196, 497, removable = true).item(),
                    Part("antigravity", "Antigravity", "1.5.9", "1.6.2", 91, 253, removable = true).item(),
                    Part("opencode", "OpenCode", "2.2.6", "2.3.1", 130, 260).item(),
                ),
            network = AgentUpdateNetwork.Unmetered,
            freeBytes = freeMb * MB,
            neededGoesAhead = false,
            sheetDue = !answered,
            answered = answered,
        )

    /** The real presenter over the offer, with the person's picks applied. */
    private fun sheetState(
        picks: Map<String, AgentUpdateChoice>,
        offer: AgentUpdateOffer = offer(FREE_MB),
        base: ChatsUi = mixed(),
    ): ChatsUi {
        val model = AgentUpdateSheetModel(Offered(offer), CoroutineScope(Job()))
        picks.forEach { (id, choice) -> model.choose(AgentId(id), choice) }
        return base.copy(agentUpdates = model.current())
    }

    @Test
    fun `renders of the update sheet with too little room and of Home with its update lines, at 360 dp`() {
        val keep = AgentUpdateChoice.Keep
        val signedOut =
            mixed().copy(
                agentNotices =
                    HomeAgentNotices(
                        lines =
                            persistentListOf(HomeAgentNotice("claude", "Claude Code", HomeAgentNoticeKind.SignedOut)),
                    )
            )
        val scenes =
            listOf(
                "sheet-no-room-button-off" to sheetState(emptyMap()),
                "sheet-fits-keep-and-remove" to
                    sheetState(
                        mapOf(
                            "claude" to keep,
                            "codex" to AgentUpdateChoice.Remove,
                            "antigravity" to keep,
                            "opencode" to keep,
                        )
                    ),
            )
        for ((name, state) in scenes)
            for (dark in listOf(false, true)) shoot("$name-${if (dark) "dark" else "light"}", state, dark)
        // Home with two own lines stacked above the lists, at text 1.0 and 1.3: the sign-in notice, then the update's.
        val waiting = sheetState(emptyMap(), offer(FREE_MB, answered = true), signedOut)
        val running =
            signedOut.copy(
                download =
                    HomeDownloadFact(HomeDownloadKind.Helper, HomeDownloadPhase.Running(40), name = "Claude Code")
            )
        val looks = listOf(1f, 1.3f).flatMap { scale -> listOf(false, true).map { dark -> scale to dark } }
        for ((name, state) in listOf("home-review-and-sign-in" to waiting, "home-running-and-sign-in" to running))
            for ((scale, dark) in looks)
                shoot("$name-${if (scale == 1f) "1.0" else "1.3"}-${if (dark) "dark" else "light"}", state, dark, scale)
    }

    private companion object {
        const val MB = 1024L * 1024L

        /** About what the 10110 phone had (440 MB): the four updates and their parts need about 1.1 GB. */
        const val FREE_MB = 440L
    }
}
