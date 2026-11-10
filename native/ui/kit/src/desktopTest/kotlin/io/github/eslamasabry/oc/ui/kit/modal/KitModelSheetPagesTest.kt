package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.InWindow
import io.github.eslamasabry.oc.ui.kit.KitTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Choose a model" as pages: which tabs there are, where it opens, what a search shows, and that a pick still works. */
@OptIn(ExperimentalTestApi::class)
class KitModelSheetPagesTest {
    private fun model(provider: String, name: String, id: String, label: String) = KitModelOption(provider, id, label, providerName = name)

    // A made-up provider: the real "zai" id is shown under its regional name (kitProviderName), which is not the point here.
    private val zai = listOf(model("zen", "Zen", "glm-5.3", "GLM-5.3"), model("zen", "Zen", "glm-5.3-flash", "GLM-5.3-Flash"))
    private val openai =
        listOf(
            model("openai", "OpenAI", "gpt-5.5", "GPT-5.5"),
            model("openai", "OpenAI", "gpt-5.5-2026-10-01", "GPT-5.5"),
            model("openai", "OpenAI", "gpt-5.6-sol", "GPT-5.6 Sol"),
        )
    private val opencode =
        (1..14).map { model("opencode", "OpenCode", "claude-$it", "Claude $it") } + model("opencode", "OpenCode", "gpt-5.5", "GPT-5.5")
    private val all = zai + openai + opencode

    // --- the layout as numbers ---------------------------------------------------------------------------------------

    @Test
    fun `the picks lead, then one page per provider with the chosen model's provider first`() {
        val pages = KitModelPages.pages(all, favorites = listOf(openai[2]), recent = listOf(zai[0]), current = openai[0].key, canFavorite = true)
        assertEquals(listOf(KitModelPages.PICKS, "provider:openai", "provider:opencode", "provider:zen"), pages.map { it.key })
        assertEquals(listOf(KitModelSectionKind.Favorites, KitModelSectionKind.Recent), pages[0].sections.map { it.kind })
        assertTrue(openai[2] in pages[1].sections.single().rows, "a favourite stays on its provider's page too")
        assertEquals(3, pages[1].sections.single().rows.size)
        assertEquals(0, KitModelPages.opening(pages, openai[0].key), "it opens on the picks when there are any")
    }

    @Test
    fun `with no picks it opens on the chosen model's provider, and the empty picks tab is there only where it can be filled`() {
        val fillable = KitModelPages.pages(all, current = opencode[3].key, canFavorite = true)
        assertEquals(KitModelPages.PICKS, fillable.first().key)
        assertTrue(fillable.first().sections.isEmpty())
        assertEquals(1, KitModelPages.opening(fillable, opencode[3].key))
        assertEquals("provider:opencode", fillable[1].key)
        assertEquals(1, KitModelPages.opening(fillable, null), "Server default: the first provider")
        val fixed = KitModelPages.pages(all, current = opencode[3].key, canFavorite = false)
        assertEquals(listOf("provider:opencode", "provider:openai", "provider:zen"), fixed.map { it.key })
        assertEquals(0, KitModelPages.opening(fixed, opencode[3].key))
    }

    @Test
    fun `one provider with no picks is one page, with a favourite it is two`() {
        assertEquals(1, KitModelPages.pages(zai, canFavorite = true).size)
        assertEquals(listOf(KitModelPages.PICKS, "provider:zen"), KitModelPages.pages(zai, favorites = listOf(zai[1]), canFavorite = true).map { it.key })
    }

    @Test
    fun `only models that share a name within one provider are twins and show their id`() {
        val twins = KitModelPages.twins(all)
        assertEquals(setOf(openai[0].key, openai[1].key), twins, "the OpenCode GPT-5.5 is another provider's, not a twin")
        assertEquals("gpt-5.5", KitModelPages.secondLine(openai[0], provider = null, twin = true))
        assertEquals("OpenAI · gpt-5.5-2026-10-01", KitModelPages.secondLine(openai[1], provider = "OpenAI", twin = true))
        assertNull(KitModelPages.secondLine(openai[2], provider = null, twin = false))
        assertEquals("OpenAI", KitModelPages.secondLine(openai[2], provider = "OpenAI", twin = false))
        assertEquals("Free · x", KitModelPages.secondLine(KitModelOption("p", "x", "X", supporting = "Free"), provider = "P", twin = true))
    }

    @Test
    fun `a search is one flat list across providers, each model once`() {
        val found = KitModelPages.results(all, favorites = listOf(openai[0]), recent = emptyList(), current = null, query = "gpt-5.5")
        assertEquals(setOf(openai[0].key, openai[1].key, opencode.last().key), found.map { it.key }.toSet())
        assertEquals(found.size, found.map { it.key }.toSet().size)
        assertEquals(openai[0].key, found.first().key, "a favourite leads")
    }

    // --- the sheet ---------------------------------------------------------------------------------------------------

    private class Scene(favorites: List<KitModelOption> = emptyList()) {
        var favorites by mutableStateOf(favorites)
        val chosen = mutableListOf<KitModelOption?>()
        var dismissed = 0
        val platform = FakeKitPlatform()
    }

    @Composable
    private fun Sheet(
        scene: Scene,
        options: List<KitModelOption> = all,
        current: String? = zai[0].key,
        recent: List<KitModelOption> = emptyList(),
        level: MotionLevel = MotionLevel.Off,
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            KitTheme(KitAppearance(motion = level), scene.platform, "en", stillFrame = true) {
                InWindow(412, 915) {
                    KitSheetHost {
                        KitModelSheet(
                            visible = true,
                            onDismissRequest = { scene.dismissed++ },
                            options = options,
                            current = current,
                            onChoose = { scene.chosen += it },
                            favorites = scene.favorites,
                            recent = recent,
                            onToggleFavorite = { o -> scene.favorites = if (o in scene.favorites) scene.favorites - o else scene.favorites + o },
                        )
                    }
                }
            }
        }
    }

    private fun ComposeUiTest.settle() {
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private fun ComposeUiTest.selected(tab: String, yes: Boolean = true) =
        onNode(hasContentDescription(tab)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, yes))

    @Test
    fun `with favourites it opens on Favorites, each row with its provider as the second line`() = runComposeUiTest {
        val scene = Scene(favorites = listOf(openai[2], opencode[0]))
        setContent { Sheet(scene, recent = listOf(zai[1])) }
        settle()
        selected("Favorites")
        onNode(hasTestTag("chat-model-choice") and hasText("GPT-5.6 Sol") and hasText("OpenAI")).assertExists()
        onNode(hasTestTag("chat-model-choice") and hasText("Claude 1") and hasText("OpenCode")).assertExists()
        onNodeWithText("Recent").assertExists()
        onNode(hasTestTag("chat-model-choice") and hasText("GLM-5.3-Flash") and hasText("Zen")).assertExists()
        onNodeWithText("Server default").assertExists()
        onNodeWithText("Claude 5").assertDoesNotExist()
    }

    @Test
    fun `with no favourites or recent picks it opens on the chosen model's provider and the picks tab says how to fill it`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene, current = openai[2].key) }
        settle()
        selected("OpenAI")
        selected("Favorites", yes = false)
        onNodeWithText("GPT-5.6 Sol").assertExists()
        onNode(hasContentDescription("Favorites")).performClick()
        settle()
        onNodeWithText("Star a model to keep it here.").assertExists()
        onNodeWithText("Server default").assertExists()
    }

    @Test
    fun `one provider and no favourites shows no tab row`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene, options = opencode, current = null) }
        settle()
        assertTrue(onAllNodesWithTag("chat-model-tabs").fetchSemanticsNodes().isEmpty(), "a single tab is noise")
        onNodeWithText("Claude 1").assertExists()
        onNodeWithText("Server default").assertExists()
    }

    @Test
    fun `two models of one provider with the same name show their ids, the others do not`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene, current = openai[2].key) }
        settle()
        onNodeWithText("gpt-5.5").assertExists()
        onNodeWithText("gpt-5.5-2026-10-01").assertExists()
        onNodeWithText("gpt-5.6-sol").assertDoesNotExist()
    }

    @Test
    fun `typing in search gives way to one flat list and clearing it returns to the tab that was open`() = runComposeUiTest {
        val scene = Scene(favorites = listOf(zai[0]))
        setContent { Sheet(scene) }
        settle()
        onNode(hasContentDescription("OpenCode")).performClick()
        settle()
        selected("OpenCode")
        onNodeWithTag("kit-search-field").performTextInput("gpt-5.5")
        settle()
        assertTrue(onAllNodesWithTag("chat-model-tabs").fetchSemanticsNodes().isEmpty(), "the tabs give way")
        assertEquals(3, onAllNodesWithText("GPT-5.5").fetchSemanticsNodes().size, "found across providers")
        onNodeWithText("OpenAI · gpt-5.5").assertExists()
        onNode(hasTestTag("chat-model-choice") and hasText("OpenCode")).assertExists()
        onNodeWithText("Server default").assertDoesNotExist()
        onNodeWithTag("kit-search-field").performTextInput("zzz")
        settle()
        assertTrue(onAllNodesWithTag("chat-model-choice").fetchSemanticsNodes().isEmpty(), "nothing is called gpt-5.5zzz")
        onNodeWithText("Clear search").performClick()
        settle()
        selected("OpenCode")
        onNodeWithText("Claude 1").assertExists()
    }

    @Test
    fun `choosing a row, Server default and a star call what they always called`() = runComposeUiTest {
        val scene = Scene(favorites = listOf(zai[1]))
        setContent { Sheet(scene) }
        settle()
        onNodeWithText("GLM-5.3-Flash").performClick()
        onNodeWithText("Server default").performClick()
        assertEquals(listOf(zai[1], null), scene.chosen)
        onAllNodesWithContentDescription("Remove favorite")[0].performClick()
        settle()
        assertTrue(scene.favorites.isEmpty())
        assertTrue(scene.platform.haptics.isNotEmpty(), "the star ticks")
    }

    @Test
    fun `the title, the search field, the first tab and the rows share one start edge and the stars one column`() = runComposeUiTest {
        val scene = Scene()
        setContent { Sheet(scene, current = openai[2].key) }
        settle()
        val title = onNodeWithText("Choose a model", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val row = onNodeWithText("GPT-5.6 Sol", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(title.left, row.left, 1f, "one start edge")
        val search = onNodeWithTag("chat-model-search").fetchSemanticsNode().boundsInRoot
        val tab = onNode(hasContentDescription("Favorites")).fetchSemanticsNode().boundsInRoot
        assertEquals(search.left, tab.left, 1f, "the first tab starts on the same edge")
        val close = onNode(hasContentDescription("Close")).fetchSemanticsNode().boundsInRoot
        val star = onAllNodesWithContentDescription("Add favorite").fetchSemanticsNodes().first().boundsInRoot
        assertEquals(close.center.x, star.center.x, 1f, "the stars stand in one column under Close")
        onNodeWithTag("kit-choice-tick", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a swipe inside the sheet turns the page and a drag down a list scrolls it`() = runComposeUiTest {
        val scene = Scene(favorites = listOf(zai[0]))
        setContent { Sheet(scene, current = opencode[0].key, level = MotionLevel.Full) }
        settle()
        selected("Favorites")
        onNodeWithTag("kit-tab-pages").performTouchInput { swipeLeft() }
        settle()
        selected("OpenCode")
        onNodeWithText("Claude 1").assertExists()
        onNodeWithTag("chat-model-list").performTouchInput { swipeUp() }
        settle()
        selected("OpenCode")
        onNodeWithText("Claude 1").assertDoesNotExist()
        assertEquals(0, scene.dismissed)
    }

    @Test
    fun `the sheet still closes by dragging its handle down`() = runComposeUiTest {
        val scene = Scene(favorites = listOf(zai[0]))
        setContent { Sheet(scene, level = MotionLevel.Full) }
        settle()
        onNodeWithTag("kit-sheet").performTouchInput { swipe(Offset(centerX, 10f), Offset(centerX, height * 0.7f), durationMillis = 200) }
        settle()
        assertEquals(1, scene.dismissed)
    }
}
