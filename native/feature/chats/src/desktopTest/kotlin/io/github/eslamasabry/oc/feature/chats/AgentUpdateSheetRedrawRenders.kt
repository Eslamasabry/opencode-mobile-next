package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.common.text.PhoneSizes
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
import io.github.eslamasabry.oc.feature.chats.presentation.AgentNeededRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateActions
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetModel
import io.github.eslamasabry.oc.feature.chats.presentation.AgentUpdateSheetUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.feature.chats.ui.amount
import io.github.eslamasabry.oc.feature.chats.ui.frees
import io.github.eslamasabry.oc.feature.chats.ui.versions
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.gate.flicker.KitUiTest
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitContentRow
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAgentsAction
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesAgentsLine
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonBoth
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonKeep
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonRemove
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesButtonUpdate
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceKeep
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceLabel
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceRemove
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesChoiceUpdate
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesDownloads
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesDownloadsUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesKeepHelperTooNew
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesKeepNotSupported
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesLinuxAction
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesLinuxLine
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesMetered
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNeededAlways
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNeededLabel
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNeededWhy
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesNetworkUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesStays
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesStaysUnknown
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesTitle
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesTotalDownload
import io.github.eslamasabry.oc.ui.words.res.agentUpdatesTotalFrees
import io.github.eslamasabry.oc.ui.words.res.appUpdateNotNow
import io.github.eslamasabry.oc.ui.words.res.kitSheetClose
import io.github.eslamasabry.oc.ui.words.res.localAgentRowOptional
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.CRC32
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class RedrawOffered(offer: AgentUpdateOffer) : AgentUpdateChoices {
    val mutableOffer: MutableStateFlow<AgentUpdateOffer?> = MutableStateFlow(offer)
    override val offer: StateFlow<AgentUpdateOffer?> = mutableOffer
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

    private fun shoot(
        name: String,
        state: ChatsUi,
        dark: Boolean,
        fontScale: Float = 1f,
        localeTag: String = "en",
    ) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(localeTag))
            val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
            kitUiTest(900, 2000) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                                platform = DesktopKitPlatform(),
                                localeTag = localeTag,
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

    private fun shootBefore(
        name: String,
        state: ChatsUi,
        dark: Boolean,
        fontScale: Float = 1f,
        localeTag: String = "en",
    ) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(localeTag))
            val mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light
            kitUiTest(900, 2000) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2.5f, fontScale)) {
                        ChatEnvironment {
                            KitTheme(
                                appearance = KitAppearance(mode = mode, motion = MotionLevel.Off),
                                platform = DesktopKitPlatform(),
                                localeTag = localeTag,
                                stillFrame = true,
                            ) {
                                KitSheetHost(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatsScreen(mixed(), RecordingChatsActions())
                                    BeforeRedesignSheet(state.agentUpdates.sheet)
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
        val fiveOneOn = sheetState(noLockedFiveOffer(), keepNames = allNames - "Claude Code")
        val fiveTwoOn = sheetState(noLockedFiveOffer(), keepNames = allNames - setOf("Claude Code", "Codex"))
        val fiveAllOff = sheetState(noLockedFiveOffer(), keepNames = allNames)
        val one = sheetState(redrawOneOffer())
        shootMatrix("us-plus-five-agents-mobile-data", five)
        shoot("us-plus-five-agents-mobile-data-wifi-off-text100-dark", fiveWifiOff, dark = true, fontScale = 1f)
        shootMatrix("us-plus-five-agents-one-on", fiveOneOn)
        shootMatrix("us-plus-five-agents-two-on", fiveTwoOn)
        shootMatrix("us-plus-five-agents-all-off", fiveAllOff)
        shootMatrix("us-plus-one-agent", one)
        shoot("us-plus-five-agents-mobile-data-ar-text130-dark", five, dark = true, fontScale = 1.3f, localeTag = "ar")
        shoot(
            "us-plus-five-agents-mobile-data-ar-text130-light",
            five,
            dark = false,
            fontScale = 1.3f,
            localeTag = "ar",
        )
        shoot("us-plus-five-agents-one-on-ar-text130-dark", fiveOneOn, dark = true, fontScale = 1.3f, localeTag = "ar")
        shoot(
            "us-plus-five-agents-one-on-ar-text130-light",
            fiveOneOn,
            dark = false,
            fontScale = 1.3f,
            localeTag = "ar",
        )
        shoot("us-plus-five-agents-two-on-ar-text130-dark", fiveTwoOn, dark = true, fontScale = 1.3f, localeTag = "ar")
        shoot(
            "us-plus-five-agents-all-off-ar-text130-dark",
            fiveAllOff,
            dark = true,
            fontScale = 1.3f,
            localeTag = "ar",
        )
    }

    private fun beforeFive(): ChatsUi = sheetState(redrawFiveOffer())

    private fun beforeFiveOneOn(): ChatsUi =
        sheetState(noLockedFiveOffer(), keepNames = ALL_AGENT_NAMES - "Claude Code")

    private fun beforeFiveAllOff(): ChatsUi = sheetState(noLockedFiveOffer(), keepNames = ALL_AGENT_NAMES)

    private fun beforeOne(): ChatsUi = sheetState(redrawOneOffer())

    @Test
    fun `before redesign five agents mobile data text100 dark`() {
        shootBefore("before-five-agents-mobile-data-text100-dark", beforeFive(), dark = true, fontScale = 1f)
    }

    @Test
    fun `before redesign five agents mobile data text100 light`() {
        shootBefore("before-five-agents-mobile-data-text100-light", beforeFive(), dark = false, fontScale = 1f)
    }

    @Test
    fun `before redesign five agents mobile data text130 dark`() {
        shootBefore("before-five-agents-mobile-data-text130-dark", beforeFive(), dark = true, fontScale = 1.3f)
    }

    @Test
    fun `before redesign five agents mobile data text130 light`() {
        shootBefore("before-five-agents-mobile-data-text130-light", beforeFive(), dark = false, fontScale = 1.3f)
    }

    @Test
    fun `before redesign five agents one on text100 dark`() {
        shootBefore("before-five-agents-one-on-text100-dark", beforeFiveOneOn(), dark = true, fontScale = 1f)
    }

    @Test
    fun `before redesign five agents one on text130 dark`() {
        shootBefore("before-five-agents-one-on-text130-dark", beforeFiveOneOn(), dark = true, fontScale = 1.3f)
    }

    @Test
    fun `before redesign five agents all off text100 dark`() {
        shootBefore("before-five-agents-all-off-text100-dark", beforeFiveAllOff(), dark = true, fontScale = 1f)
    }

    @Test
    fun `before redesign five agents all off text130 dark`() {
        shootBefore("before-five-agents-all-off-text130-dark", beforeFiveAllOff(), dark = true, fontScale = 1.3f)
    }

    @Test
    fun `before redesign one agent text100 dark`() {
        shootBefore("before-one-agent-text100-dark", beforeOne(), dark = true, fontScale = 1f)
    }

    @Test
    fun `before redesign one agent text130 dark`() {
        shootBefore("before-one-agent-text130-dark", beforeOne(), dark = true, fontScale = 1.3f)
    }

    @Test
    fun `before redesign five agents mobile data ar text130 dark`() {
        shootBefore(
            "before-five-agents-mobile-data-ar-text130-dark",
            beforeFive(),
            dark = true,
            fontScale = 1.3f,
            localeTag = "ar",
        )
    }

    @Test
    fun `before redesign five agents mobile data ar text130 light`() {
        shootBefore(
            "before-five-agents-mobile-data-ar-text130-light",
            beforeFive(),
            dark = false,
            fontScale = 1.3f,
            localeTag = "ar",
        )
    }

    @Test
    fun `before redesign five agents one on ar text130 dark`() {
        shootBefore(
            "before-five-agents-one-on-ar-text130-dark",
            beforeFiveOneOn(),
            dark = true,
            fontScale = 1.3f,
            localeTag = "ar",
        )
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
                .filter { it.group == AgentUpdateGroup.Optional && it.name != "Claude Code" }
                .forEach { model.choose(it.id, AgentUpdateChoice.Keep) }
            kitUiTest(900, 2000) {
                setContent { FullMotionSheet(model) }
                val claude = onNode(isToggleable() and hasText("Claude Code", substring = true))
                waitForIdle()
                mainClock.advanceTimeBy(1_500)
                waitForIdle()
                mainClock.autoAdvance = false
                write("us-plus-fold-frame-00")
                claude.performClick()
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

    @Test
    fun `journey recording switching one, two and none at full motion`() {
        val previous = Locale.getDefault()
        val scope = CoroutineScope(Job())
        try {
            Locale.setDefault(Locale.forLanguageTag("en"))
            val offer = noLockedFiveOffer()
            val model = AgentUpdateSheetModel(RedrawOffered(offer), scope)
            offer.items
                .filter { it.group == AgentUpdateGroup.Optional }
                .forEach { model.choose(it.id, AgentUpdateChoice.Keep) }
            val frames = mutableListOf<Pair<BufferedImage, Int>>()
            val strip = mutableListOf<BufferedImage>()
            kitUiTest(900, 2000) {
                setContent { FullMotionSheet(model) }
                waitForIdle()
                mainClock.advanceTimeBy(1_500)
                waitForIdle()
                mainClock.autoAdvance = false
                val startImg = captureScaled()
                frames += startImg to HOLD_MS
                strip += startImg
                recordTap("Claude Code", UNFOLD_STEPS, frames, strip)
                recordTap("Codex", SWAP_STEPS, frames, strip)
                recordTap("Codex", SWAP_STEPS, frames, strip)
                recordTap("Claude Code", UNFOLD_STEPS, frames, strip)
                mainClock.autoAdvance = true
                waitForIdle()
            }
            writeApng(frames, File(out, "us-plus-journey-recording.png"))
            writeGif(frames, File(out, "us-plus-journey-recording.gif"))
            writeStrip(strip, File(out, "us-plus-journey-strip.png"))
        } finally {
            scope.cancel()
            Locale.setDefault(previous)
        }
    }

    @Composable
    private fun BeforeRedesignSheet(shown: AgentUpdateSheetUi) {
        KitSheet(
            visible = shown.visible,
            onDismissRequest = {},
            title = stringResource(Res.string.agentUpdatesTitle),
            closeLabel = stringResource(Res.string.kitSheetClose),
            primary = KitAction(beforeButton(shown), {}),
            secondary = KitAction(stringResource(Res.string.appUpdateNotNow), {}),
        ) {
            Column {
                if (shown.optional.isNotEmpty()) {
                    KitRowGroup(label = stringResource(Res.string.localAgentRowOptional)) {
                        shown.optional.forEach { row -> BeforeOptionalRow(row) }
                    }
                }
                if (shown.needed.isNotEmpty()) {
                    KitRowGroup(
                        label = stringResource(Res.string.agentUpdatesNeededLabel),
                        note =
                            stringResource(
                                if (shown.neededWhy == AgentNeedRule.Always) Res.string.agentUpdatesNeededAlways
                                else Res.string.agentUpdatesNeededWhy
                            ),
                    ) {
                        shown.needed.forEach { row -> BeforeNeededRow(row) }
                    }
                }
                BeforeTotals(shown)
                when (shown.network) {
                    AgentUpdateNetwork.Metered -> KitGroupNote(stringResource(Res.string.agentUpdatesMetered))
                    AgentUpdateNetwork.Unknown -> KitGroupNote(stringResource(Res.string.agentUpdatesNetworkUnknown))
                    AgentUpdateNetwork.Unmetered -> Unit
                }
                KitGroupNote(
                    stringResource(Res.string.agentUpdatesAgentsLine),
                    action = KitAction(stringResource(Res.string.agentUpdatesAgentsAction), {}),
                )
                KitGroupNote(
                    stringResource(Res.string.agentUpdatesLinuxLine),
                    action = KitAction(stringResource(Res.string.agentUpdatesLinuxAction), {}),
                )
            }
        }
    }

    @Composable
    private fun BeforeOptionalRow(row: AgentUpdateRowUi) {
        KitContentRow(title = KitBidi.auto(row.name), supporting = versions(row.from, row.to)) {
            Column {
                if (row.choices.size >= 2) {
                    val segments = row.choices.map { KitSegment(it, beforeChoiceWord(it)) }
                    KitSegmented(
                        segments = segments,
                        selected = row.choice,
                        semanticsLabel = stringResource(Res.string.agentUpdatesChoiceLabel, row.name),
                        onChange = {},
                    )
                }
                KitText(beforeConsequence(row), role = TypeRole.Secondary, tone = TextTone.Secondary)
                row.keepBlock?.let {
                    KitText(
                        stringResource(
                            when (it) {
                                AgentKeepBlock.HelperTooNew -> Res.string.agentUpdatesKeepHelperTooNew
                                AgentKeepBlock.NotSupportedAnyMore -> Res.string.agentUpdatesKeepNotSupported
                            }
                        ),
                        role = TypeRole.Secondary,
                        tone = TextTone.Secondary,
                    )
                }
            }
        }
    }

    @Composable
    private fun BeforeNeededRow(row: AgentNeededRowUi) {
        val dl =
            row.downloadBytes?.let { stringResource(Res.string.agentUpdatesDownloads, PhoneSizes.text(it)) }
                ?: stringResource(Res.string.agentUpdatesDownloadsUnknown)
        KitRow(
            title = KitBidi.auto(row.name),
            supporting = "${versions(row.from, row.to)}\n$dl",
            supportingMaxLines = 3,
        )
    }

    @Composable
    private fun BeforeTotals(sheet: AgentUpdateSheetUi) {
        if (sheet.updating == 0 && sheet.removing == 0 && sheet.needed.isEmpty()) return
        KitRowGroup {
            if (sheet.updating > 0 || sheet.needed.isNotEmpty()) {
                KitRow(
                    stringResource(Res.string.agentUpdatesTotalDownload),
                    trailing = KitRowTrailing.Value(amount(sheet.downloadBytes, sheet.downloadKnown), chevron = false),
                )
            }
            if (sheet.removing > 0) {
                KitRow(
                    stringResource(Res.string.agentUpdatesTotalFrees),
                    trailing = KitRowTrailing.Value(amount(sheet.freesBytes, sheet.freesKnown), chevron = false),
                )
            }
        }
    }

    @Composable
    private fun beforeButton(sheet: AgentUpdateSheetUi): String =
        when {
            sheet.updating > 0 && sheet.removing > 0 ->
                pluralStringResource(Res.plurals.agentUpdatesButtonBoth, sheet.removing, sheet.updating, sheet.removing)
            sheet.updating > 0 ->
                pluralStringResource(Res.plurals.agentUpdatesButtonUpdate, sheet.updating, sheet.updating)
            sheet.removing > 0 ->
                pluralStringResource(Res.plurals.agentUpdatesButtonRemove, sheet.removing, sheet.removing)
            else -> stringResource(Res.string.agentUpdatesButtonKeep)
        }

    @Composable
    private fun beforeChoiceWord(choice: AgentUpdateChoice): String =
        stringResource(
            when (choice) {
                AgentUpdateChoice.Update -> Res.string.agentUpdatesChoiceUpdate
                AgentUpdateChoice.Keep -> Res.string.agentUpdatesChoiceKeep
                AgentUpdateChoice.Remove -> Res.string.agentUpdatesChoiceRemove
            }
        )

    @Composable
    private fun beforeConsequence(row: AgentUpdateRowUi): String =
        when (row.choice) {
            AgentUpdateChoice.Update ->
                row.downloadBytes?.let { stringResource(Res.string.agentUpdatesDownloads, PhoneSizes.text(it)) }
                    ?: stringResource(Res.string.agentUpdatesDownloadsUnknown)
            AgentUpdateChoice.Keep ->
                row.from?.let { stringResource(Res.string.agentUpdatesStays, KitBidi.ltr(it)) }
                    ?: stringResource(Res.string.agentUpdatesStaysUnknown)
            AgentUpdateChoice.Remove -> frees(row.installedBytes)
        }

    @androidx.compose.runtime.Composable
    private fun FullMotionSheet(model: AgentUpdateSheetModel) {
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

    private fun KitUiTest.recordTap(
        agent: String,
        steps: Int,
        frames: MutableList<Pair<BufferedImage, Int>>,
        strip: MutableList<BufferedImage>,
    ) {
        onNode(isToggleable() and hasText(agent, substring = true)).performClick()
        repeat(steps) {
            repeat(FRAME_STRIDE) { mainClock.advanceTimeByFrame() }
            frames += captureScaled() to STEP_DELAY_MS
        }
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }
        val settled = captureScaled()
        frames += settled to HOLD_MS
        strip += settled
        mainClock.autoAdvance = true
        mainClock.advanceTimeBy(1_500)
        waitForIdle()
        mainClock.autoAdvance = false
    }

    private fun KitUiTest.captureScaled(): BufferedImage {
        val raw = onRoot().captureToImage().toAwtImage()
        val dst = BufferedImage(RECORD_W, RECORD_H, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(raw, 0, 0, RECORD_W, RECORD_H, null)
        g.dispose()
        return dst
    }

    private fun writeStrip(images: List<BufferedImage>, file: File) {
        val gap = STRIP_GAP
        val totalW = images.size * RECORD_W + (images.size - 1) * gap
        val canvas = BufferedImage(totalW, RECORD_H, BufferedImage.TYPE_INT_ARGB)
        val g = canvas.createGraphics()
        images.forEachIndexed { idx, img -> g.drawImage(img, idx * (RECORD_W + gap), 0, null) }
        g.dispose()
        ImageIO.write(canvas, "png", file)
    }

    private fun writeGif(frames: List<Pair<BufferedImage, Int>>, file: File) {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        try {
            FileImageOutputStream(file).use { stream ->
                writer.output = stream
                val param = writer.defaultWriteParam
                writer.prepareWriteSequence(null)
                frames.forEachIndexed { index, (img, delayMs) ->
                    val rgb = toOpaqueRgb(img)
                    val meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(rgb), param)
                    applyGifFrameMeta(meta, delayMs, loop = index == 0)
                    writer.writeToSequence(IIOImage(rgb, null, meta), param)
                }
                writer.endWriteSequence()
            }
        } finally {
            writer.dispose()
        }
    }

    private fun toOpaqueRgb(img: BufferedImage): BufferedImage =
        BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_RGB).also { dst ->
            val g = dst.createGraphics()
            g.drawImage(img, 0, 0, null)
            g.dispose()
        }

    private fun applyGifFrameMeta(meta: IIOMetadata, delayMs: Int, loop: Boolean) {
        val format = meta.nativeMetadataFormatName
        val root = meta.getAsTree(format) as IIOMetadataNode
        val gce = gifChild(root, "GraphicControlExtension")
        gce.setAttribute("disposalMethod", "none")
        gce.setAttribute("userInputFlag", "FALSE")
        gce.setAttribute("transparentColorFlag", "FALSE")
        gce.setAttribute("delayTime", (delayMs / 10).coerceAtLeast(1).toString())
        gce.setAttribute("transparentColorIndex", "0")
        if (loop) {
            val apps = gifChild(root, "ApplicationExtensions")
            val app = IIOMetadataNode("ApplicationExtension").apply {
                setAttribute("applicationID", "NETSCAPE")
                setAttribute("authenticationCode", "2.0")
                userObject = byteArrayOf(0x1, 0x0, 0x0)
            }
            apps.appendChild(app)
        }
        meta.setFromTree(format, root)
    }

    private fun gifChild(root: IIOMetadataNode, name: String): IIOMetadataNode {
        for (i in 0 until root.length) {
            if (root.item(i).nodeName.equals(name, ignoreCase = true)) {
                return root.item(i) as IIOMetadataNode
            }
        }
        return IIOMetadataNode(name).also { root.appendChild(it) }
    }

    private fun writeApng(frames: List<Pair<BufferedImage, Int>>, file: File) {
        val encoded = frames.map { (img, ms) -> pngChunks(img) to ms }
        val ihdr = encoded.first().first.first { it.first == "IHDR" }.second
        val outBytes = ByteArrayOutputStream()
        outBytes.write(PNG_SIG)
        writeChunk(outBytes, "IHDR", ihdr)
        writeChunk(outBytes, "acTL", intPair(frames.size, 0))
        var seq = 0
        encoded.forEachIndexed { index, (chunks, delayMs) ->
            val idat = concatIdat(chunks)
            writeChunk(outBytes, "fcTL", fctlData(seq++, RECORD_W, RECORD_H, delayMs))
            if (index == 0) {
                writeChunk(outBytes, "IDAT", idat)
            } else {
                writeChunk(outBytes, "fdAT", intPrefix(seq++, idat))
            }
        }
        writeChunk(outBytes, "IEND", ByteArray(0))
        file.writeBytes(outBytes.toByteArray())
    }

    private fun pngChunks(img: BufferedImage): List<Pair<String, ByteArray>> {
        val raw = ByteArrayOutputStream()
        ImageIO.write(img, "png", raw)
        val bytes = raw.toByteArray()
        val chunks = mutableListOf<Pair<String, ByteArray>>()
        var pos = PNG_SIG.size
        while (pos + CHUNK_HEADER <= bytes.size) {
            val len = readInt(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val data = bytes.copyOfRange(pos + CHUNK_HEADER, pos + CHUNK_HEADER + len)
            chunks += type to data
            pos += CHUNK_HEADER + len + 4
        }
        return chunks
    }

    private fun concatIdat(chunks: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        chunks.filter { it.first == "IDAT" }.forEach { out.write(it.second) }
        return out.toByteArray()
    }

    private fun fctlData(seq: Int, w: Int, h: Int, delayMs: Int): ByteArray =
        ByteArrayOutputStream().also { baos ->
            DataOutputStream(baos).apply {
                writeInt(seq)
                writeInt(w)
                writeInt(h)
                writeInt(0)
                writeInt(0)
                writeShort(delayMs)
                writeShort(1000)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray()

    private fun intPair(a: Int, b: Int): ByteArray =
        ByteArrayOutputStream().also { baos ->
            DataOutputStream(baos).apply {
                writeInt(a)
                writeInt(b)
            }
        }.toByteArray()

    private fun intPrefix(value: Int, payload: ByteArray): ByteArray =
        ByteArrayOutputStream(4 + payload.size).also { baos ->
            DataOutputStream(baos).writeInt(value)
            baos.write(payload)
        }.toByteArray()

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(crc)
        }
    }

    private fun readInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
            ((b[offset + 1].toInt() and 0xFF) shl 16) or
            ((b[offset + 2].toInt() and 0xFF) shl 8) or
            (b[offset + 3].toInt() and 0xFF)

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
        val ALL_AGENT_NAMES = setOf("Claude Code", "Codex", "Antigravity", "Gemini CLI", "Qwen Code")
        val FOLD_SHOTS = listOf(4, 8, 12, 24)
        const val RECORD_W = 450
        const val RECORD_H = 1000
        const val STRIP_GAP = 16
        const val FRAME_STRIDE = 2
        const val UNFOLD_STEPS = 12
        const val SWAP_STEPS = 8
        const val SETTLE_FRAMES = 20
        const val STEP_DELAY_MS = 48
        const val HOLD_MS = 700
        const val CHUNK_HEADER = 8
        val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
