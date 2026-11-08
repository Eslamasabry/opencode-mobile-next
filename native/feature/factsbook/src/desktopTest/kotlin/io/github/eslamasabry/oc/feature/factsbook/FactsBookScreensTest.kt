package io.github.eslamasabry.oc.feature.factsbook

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.factsbook.Area
import io.github.eslamasabry.oc.engine.factsbook.AreaShare
import io.github.eslamasabry.oc.engine.factsbook.CaptureKind
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveEntry
import io.github.eslamasabry.oc.engine.factsbook.CouldNotSaveReason
import io.github.eslamasabry.oc.engine.factsbook.Fact
import io.github.eslamasabry.oc.engine.factsbook.FactProvenance
import io.github.eslamasabry.oc.engine.factsbook.FactsBookLoad
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouItem
import io.github.eslamasabry.oc.engine.factsbook.NeedsYouKind
import io.github.eslamasabry.oc.engine.factsbook.Thing
import io.github.eslamasabry.oc.engine.factsbook.ThingKind
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AboutYouUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.AreasUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureNotice
import io.github.eslamasabry.oc.feature.factsbook.presentation.CaptureUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.MemorySectionUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedActions
import io.github.eslamasabry.oc.feature.factsbook.presentation.NotSavedUi
import io.github.eslamasabry.oc.feature.factsbook.presentation.ThingFilter
import io.github.eslamasabry.oc.feature.factsbook.ui.AboutYouScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.AreasScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.CaptureScreen
import io.github.eslamasabry.oc.feature.factsbook.ui.MemorySectionContent
import io.github.eslamasabry.oc.feature.factsbook.ui.NotSavedScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

/** The real pages with fake state, found by visible text (Flutter test/factsbook/factsbook_pages_test.dart). */
@OptIn(ExperimentalTestApi::class)
class FactsBookScreensTest {
    private val t0 = Instant.parse("2026-11-08T10:00:00Z")

    @Composable
    private fun Themed(locale: String = "en", content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
            KitTheme(
                platform = DesktopKitPlatform(),
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = locale,
                stillFrame = true,
            ) {
                Box(Modifier.width(412.dp)) { content() }
            }
        }
    }

    private class Recorder : MemorySectionActions, AboutYouActions, AreasActions, CaptureActions, NotSavedActions {
        val calls = mutableListOf<String>()

        override fun setUp() { calls += "setUp" }
        override fun openAboutYou() { calls += "aboutYou" }
        override fun addSomething() { calls += "add" }
        override fun openNeedsYou(kind: NeedsYouKind) { calls += "needs $kind" }
        override fun openThing(id: String) { calls += "thing $id" }
        override fun retry() { calls += "retry" }
        override fun back() { calls += "back" }
        override fun search(query: String) { calls += "search $query" }
        override fun filter(filter: ThingFilter) { calls += "filter $filter" }
        override fun reload() { calls += "reload" }
        override fun openNeedsYou() { calls += "needs" }
        override fun openAreas() { calls += "areas" }
        override fun setUpApproval() { calls += "approval" }
        override fun kind(kind: CaptureKind) { calls += "kind $kind" }
        override fun area(slug: String?) { calls += "area $slug" }
        override fun thing(id: String?) { calls += "pick $id" }
        override fun save(text: String) { calls += "save $text" }
        override fun seeNotSaved() { calls += "seeNotSaved" }
        override fun dismiss() { calls += "dismiss" }
        override fun retry(entry: CouldNotSaveEntry) { calls += "retry ${entry.id}" }
        override fun askDiscard(entry: CouldNotSaveEntry) { calls += "ask ${entry.id}" }
        override fun discard() { calls += "discard" }
        override fun cancelDiscard() { calls += "cancel" }
    }

    private val sara =
        Thing(
            id = "per_01",
            title = "Sara",
            kind = ThingKind.Person,
            updatedAt = t0,
            facts = listOf(Fact("f_1", "per_01", "Likes tea", FactProvenance.Told, t0, "note")),
        )

    @Test
    fun `B-FB-040 unpaired the Memory section is one row that sets FactsBook up`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { MemorySectionContent(MemorySectionUi(shown = true, loaded = true, paired = false), actions) } }
        onNodeWithText("Set up Memory").performClick()
        onNodeWithText("Open About you").assertDoesNotExist()
        assertEquals(listOf("setUp"), actions.calls)
    }

    @Test
    fun `B-FB-041 while the switch is off nothing about FactsBook shows`() = runComposeUiTest {
        setContent { Themed { MemorySectionContent(MemorySectionUi(shown = false, loaded = true, paired = true), Recorder()) } }
        onNodeWithText("Open About you").assertDoesNotExist()
        onNodeWithText("Set up Memory").assertDoesNotExist()
    }

    @Test
    fun `B-FB-042 paired the section shows what needs you, the newest thing and the two ways in`() = runComposeUiTest {
        val actions = Recorder()
        val state =
            MemorySectionUi(
                shown = true,
                loaded = true,
                paired = true,
                load = FactsBookLoad.Ready,
                needsYou = persistentListOf(NeedsYouItem(NeedsYouKind.Review, 2)),
                needsYouCount = 2,
                recent = persistentListOf(sara),
                nowMs = t0.toEpochMilliseconds(),
            )
        setContent { Themed { MemorySectionContent(state, actions) } }
        onNodeWithText("2 changes to review").performClick()
        onNodeWithText("Sara").performClick()
        onNodeWithText("Add something").performClick()
        assertEquals(listOf("needs Review", "thing per_01", "add"), actions.calls)
    }

    @Test
    fun `B-FB-043 About you lists every thing with its newest fact and opens it`() = runComposeUiTest {
        val actions = Recorder()
        setContent {
            Themed { AboutYouScreen(AboutYouUi(load = FactsBookLoad.Ready, needsYouCount = 1, things = persistentListOf(sara)), actions) }
        }
        onNodeWithText("Needs you").assertExists()
        onNodeWithText("People", useUnmergedTree = true).performClick()
        onNodeWithText("Sara").performClick()
        assertEquals(listOf("filter People", "thing per_01"), actions.calls)
    }

    @Test
    fun `B-FB-044 Areas says why sharing cannot be changed yet`() = runComposeUiTest {
        val area = Area("health", sensitiveStorage = true, reviewFirst = true, share = AreaShare(), brief = false, export = false, notify = "generic")
        setContent { Themed { AreasScreen(AreasUi(areas = persistentListOf(area), loading = false, canApprove = false), Recorder()) } }
        onNodeWithText("Other apps and agents can read it").assertExists()
        onNodeWithText("Set up approval first", substring = true).assertExists()
    }

    @Test
    fun `B-FB-045 a note kept on the phone says so and offers Not saved yet`() = runComposeUiTest {
        val actions = Recorder()
        setContent { Themed { CaptureScreen(CaptureUi(notice = CaptureNotice.KeptOnPhone), actions) } }
        onNodeWithText("It could not be saved yet. It is kept on this phone.").assertExists()
    }

    @Test
    fun `B-FB-046 Not saved yet discards only after asking`() = runComposeUiTest {
        val entry = CouldNotSaveEntry("out_1", CouldNotSaveReason.NodeUnreachable, t0, text = "Old note", onPhone = true)
        val actions = Recorder()
        setContent {
            Themed {
                NotSavedScreen(NotSavedUi(entries = persistentListOf(entry), confirmDiscard = entry, nowMs = t0.toEpochMilliseconds()), actions)
            }
        }
        onNodeWithText("Old note", useUnmergedTree = true).assertExists()
        onNodeWithText("Discard this note?").assertExists()
        onNodeWithText("Discard note").performClick()
        assertEquals(listOf("discard"), actions.calls)
    }

    @Test
    fun `B-FB-047 the Arabic Memory section reads in plain Arabic words`() = runComposeUiTest {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("ar"))
        try {
            setContent {
                Themed("ar") {
                    MemorySectionContent(MemorySectionUi(shown = true, loaded = true, paired = true, load = FactsBookLoad.Ready), Recorder())
                }
            }
            onNodeWithText("أضف شيئًا").assertExists()
            onNodeWithText("افتح «عنّي»").assertExists()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
