package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentKeepBlock
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentRemovalFacts
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStarted
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetModel
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class RedrawOffered(offer: AgentUpdateOffer) : AgentUpdateChoices {
    override val offer: StateFlow<AgentUpdateOffer?> = MutableStateFlow(offer)
    var lastWaitForWifi: Boolean? = null
    var lastChoices: Map<AgentId, AgentUpdateChoice>? = null

    override suspend fun decide(
        choices: Map<AgentId, AgentUpdateChoice>,
        waitForWifi: Boolean,
    ): Outcome<AgentUpdateStarted> {
        lastChoices = choices
        lastWaitForWifi = waitForWifi
        return AgentUpdateStarted(emptyList(), emptyList(), emptyList()).ok()
    }

    override suspend fun notNow(): Outcome<Unit> = Unit.ok()

    override fun removalFacts(id: AgentId): AgentRemovalFacts = AgentRemovalFacts(null, true, true)

    override suspend fun review(raise: Boolean): Outcome<Unit> = Unit.ok()
}

private const val REDRAW_MB = 1_000_000L

private fun helperItem(): AgentUpdateItem =
    AgentUpdateItem(
        id = AgentId("agent-paseo"),
        name = "Agent helper",
        fromVersion = "0.16.1",
        toVersion = "0.17.2",
        downloadBytes = 330 * REDRAW_MB,
        installedBytes = 330 * REDRAW_MB,
        group = AgentUpdateGroup.Needed,
        keepAllowed = false,
        removeAllowed = false,
        notKeptReason = null,
        choice = null,
        neededWhen = AgentNeedRule.WithAnyUpdate,
    )

private fun optionalItem(
    id: String,
    name: String,
    versions: Pair<String, String>,
    downloadMb: Long,
    notKeptReason: AgentKeepBlock? = null,
): AgentUpdateItem =
    AgentUpdateItem(
        id = AgentId(id),
        name = name,
        fromVersion = versions.first,
        toVersion = versions.second,
        downloadBytes = downloadMb * REDRAW_MB,
        installedBytes = downloadMb * 2 * REDRAW_MB,
        group = AgentUpdateGroup.Optional,
        keepAllowed = notKeptReason == null,
        removeAllowed = notKeptReason == null,
        notKeptReason = notKeptReason,
        choice = null,
        neededWhen = null,
    )

internal fun redrawFiveOffer(): AgentUpdateOffer =
    AgentUpdateOffer(
        appUpdateKey = "10150",
        items =
            listOf(
                helperItem(),
                optionalItem("claude", "Claude Code", "2.1.330" to "2.1.341", 64),
                optionalItem("codex", "Codex", "0.171.0" to "0.174.0", 48),
                optionalItem("antigravity", "Antigravity", "1.5.13" to "1.5.14", 92),
                optionalItem(
                    id = "gemini",
                    name = "Gemini CLI",
                    versions = "0.66.0" to "0.68.1",
                    downloadMb = 71,
                    notKeptReason = AgentKeepBlock.HelperTooNew,
                ),
                optionalItem("qwen", "Qwen Code", "0.28.0" to "0.29.0", 55),
            ),
        network = AgentUpdateNetwork.Metered,
        freeBytes = 6_000 * REDRAW_MB,
        neededGoesAhead = false,
        sheetDue = true,
        answered = false,
    )

internal fun noLockedFiveOffer(): AgentUpdateOffer =
    redrawFiveOffer().let { offer ->
        offer.copy(
            items = offer.items.map { if (it.id == AgentId("gemini")) it.copy(keepAllowed = true) else it },
        )
    }

internal fun redrawOneOffer(): AgentUpdateOffer =
    AgentUpdateOffer(
        appUpdateKey = "10150",
        items = listOf(helperItem(), optionalItem("antigravity", "Antigravity", "1.5.13" to "1.5.14", 92)),
        network = AgentUpdateNetwork.Unmetered,
        freeBytes = 6_000 * REDRAW_MB,
        neededGoesAhead = false,
        sheetDue = true,
        answered = false,
    )

/**
 * Pictures of the redrawn agents' update sheet, nothing asserted: FIVE on mobile data (dark, light, and text 1.3 dark),
 * FIVE with Wi-Fi only off, FIVE with every agent off (dark) and ONE on Wi-Fi (dark), and a fold strip at Full motion
 * (frames 00, 04, 08, 12 and 24 after the last agent goes off). Writes `build/agent-update-sheet-redraw/<name>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class AgentUpdateSheetRedrawRenders {
    private val out = File("build/agent-update-sheet-redraw").also { it.mkdirs() }

    private fun shoot(name: String, state: ChatsUi, dark: Boolean, fontScale: Float = 1f) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
            kitUiTest(900, 2000) {
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
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun sheetState(
        offer: AgentUpdateOffer,
        waitForWifi: Boolean = true,
        keepNames: Set<String> = emptySet(),
    ): ChatsUi {
        val scope = CoroutineScope(Job())
        val model = AgentUpdateSheetModel(RedrawOffered(offer), scope)
        if (!waitForWifi) model.chooseWifiOnly(false)
        offer.items
            .filter { it.group == AgentUpdateGroup.Optional && it.name in keepNames }
            .forEach { model.choose(it.id, AgentUpdateChoice.Keep) }
        val home = mixed().copy(agentUpdates = model.current())
        scope.cancel()
        return home
    }

    private fun shootMatrix(prefix: String, state: ChatsUi) {
        shoot("$prefix-text100-dark", state, dark = true, fontScale = 1f)
        shoot("$prefix-text100-light", state, dark = false, fontScale = 1f)
        shoot("$prefix-text130-dark", state, dark = true, fontScale = 1.3f)
        shoot("$prefix-text130-light", state, dark = false, fontScale = 1.3f)
    }

    @Test
    fun `renders of the redrawn agents update sheet at 360 dp`() {
        val allNames = setOf("Claude Code", "Codex", "Antigravity", "Gemini CLI", "Qwen Code")
        val five = sheetState(redrawFiveOffer())
        val fiveWifiOff = sheetState(redrawFiveOffer(), waitForWifi = false)
        val fiveOneOn = sheetState(noLockedFiveOffer(), keepNames = allNames - "Qwen Code")
        val fiveAllOff = sheetState(noLockedFiveOffer(), keepNames = allNames)
        val one = sheetState(redrawOneOffer())
        shootMatrix("us-plus-five-agents-mobile-data", five)
        shoot("us-plus-five-agents-mobile-data-wifi-off-text100-dark", fiveWifiOff, dark = true, fontScale = 1f)
        shootMatrix("us-plus-five-agents-one-on", fiveOneOn)
        shootMatrix("us-plus-five-agents-all-off", fiveAllOff)
        shootMatrix("us-plus-one-agent", one)
    }

    @Test
    fun `fold strip of the last agent going off at full motion`() {
        val previous = Locale.getDefault()
        val scope = CoroutineScope(Job())
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val offer = noLockedFiveOffer()
            val model = AgentUpdateSheetModel(RedrawOffered(offer), scope)
            offer.items
                .filter { it.group == AgentUpdateGroup.Optional && it.name != "Qwen Code" }
                .forEach { model.choose(it.id, AgentUpdateChoice.Keep) }
            kitUiTest(900, 2000) {
                setContent {
                    val updates by model.ui.collectAsState(initial = remember { model.current() })
                    CompositionLocalProvider(LocalDensity provides Density(2.5f, 1f)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Full),
                                platform = DesktopKitPlatform(),
                                localeTag = "en",
                                stillFrame = true,
                            ) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatsScreen(mixed().copy(agentUpdates = updates), foldActions(model))
                                }
                            }
                        }
                    }
                }
                val qwen = onNode(isToggleable() and hasText("Qwen Code", substring = true))
                qwen.performScrollTo()
                waitForIdle()
                mainClock.advanceTimeBy(1_500)
                waitForIdle()
                mainClock.autoAdvance = false
                write("us-plus-fold-frame-00")
                qwen.performClick()
                var frame = 0
                FOLD_SHOTS.forEach { at ->
                    while (frame < at) {
                        mainClock.advanceTimeByFrame()
                        frame++
                    }
                    write("us-plus-fold-frame-${at.toString().padStart(2, '0')}")
                }
                mainClock.autoAdvance = true
                waitForIdle()
            }
        } finally {
            scope.cancel()
            Locale.setDefault(previous)
        }
    }

    private fun KitUiTest.write(name: String) {
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    private fun foldActions(model: AgentUpdateSheetModel): ChatsActions =
        object : RecordingChatsActions() {
            override val agentUpdates: AgentUpdateActions =
                object : AgentUpdateActions {
                    override fun chooseAgentUpdate(id: AgentId, choice: AgentUpdateChoice) {
                        model.choose(id, choice)
                    }
                }
        }

    private companion object {
        val FOLD_SHOTS = listOf(4, 8, 12, 24)
    }
}
