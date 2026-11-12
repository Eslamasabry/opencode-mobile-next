@file:OptIn(ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenEdges
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBackground
import io.github.eslamasabry.oc.ui.kit.logo.KitProviderLogo
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.modal.KitModelOption
import io.github.eslamasabry.oc.ui.kit.modal.KitModelSheet
import io.github.eslamasabry.oc.ui.kit.rb.o23.MARKS
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Pictures of the finger-linked tabs for a person to judge, not a test: the strip with its pill part of the way
 * between two tabs of different widths (each style, light and dark, stretched as at Full and plain as at Calm), and
 * the model sheet on its picks, on a provider, in mid-swipe and with search results.
 *
 * It runs only where the folder to write to exists: the one `SWIPE_TABS_RENDER_OUT` names, else
 * `Storage/tmp/swipe-tabs` under the home folder. Desktop has no lens and no backdrop sampling, so Glass shows the
 * kit's frosted face here, not the phone's liquid glass.
 */
class SwipeTabsRenders {
    private val out: File? =
        (System.getenv("SWIPE_TABS_RENDER_OUT")?.let(::File) ?: File(System.getProperty("user.home"), "Storage/tmp/swipe-tabs"))
            .takeIf { it.isDirectory }

    private val names = listOf("Favorites", "Z.AI", "OpenCode", "OpenAI", "Anthropic")
    private val ids = listOf("", "zai", "opencode", "openai", "anthropic")

    private fun ComposeUiTest.save(file: File) {
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
    }

    @Composable
    private fun Phone(style: Style, dark: Boolean, level: MotionLevel, w: Int, h: Int, rtl: Boolean = false, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalDensity provides Density(DENSITY, 1f),
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            KitTheme(
                KitAppearance(
                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                    style = style,
                    motion = level,
                    liveBackground = style == Style.Glass,
                    liveStyle = "auroraImage",
                ),
                FakeKitPlatform(frost = true),
                if (rtl) "ar" else "en",
                stillFrame = true,
            ) {
                // The app's own provider marks (its assets folder), so the tabs show them as the phone does.
                CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(w.toFloat(), h.toFloat()), LocalKitLogoLoader provides MARKS) {
                    Box(Modifier.size(w.dp, h.dp).background(LocalKitColors.current.ground)) {
                        if (style == Style.Glass) KitLiveBackground(Modifier.fillMaxSize()) { content() } else content()
                    }
                }
            }
        }
    }

    /** The strip alone, its pill held at [place]. */
    private fun strip(file: File, style: Style, dark: Boolean, place: Float, elastic: Boolean, rtl: Boolean = false, marks: Boolean = false) {
        runDesktopComposeUiTest((412 * DENSITY).toInt(), (88 * DENSITY).toInt()) {
            setContent {
                Phone(style, dark, MotionLevel.Off, 412, 88, rtl) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        TabStrip(
                            tabs =
                                names.mapIndexed { i, name ->
                                    if (!marks || i == 0) KitTab(name)
                                    else KitTab(name, mark = { tint -> KitProviderLogo(ids[i], name, markSize = 16.dp, tint = tint, slot = 16.dp) })
                                },
                            selected = place.toInt(),
                            onSelected = {},
                            position = { place },
                            elastic = elastic,
                            modifier = Modifier,
                            semanticsLabel = null,
                            gutter = 20.dp,
                        )
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(500)
            waitForIdle()
            if (marks) settleMarks()
            save(file)
        }
    }

    /** The marks are decoded off the main thread: give them a moment of real time. */
    private fun ComposeUiTest.settleMarks() {
        repeat(6) {
            Thread.sleep(150)
            mainClock.advanceTimeBy(100)
            waitForIdle()
        }
    }

    @Test
    fun `strip frames`() {
        val dir = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        // Solid, the default: every quarter of the way from a narrow tab to a wide one, stretched and plain.
        for (dark in listOf(true, false)) {
            val mode = if (dark) "dark" else "light"
            for (quarter in 0..4) {
                val f = quarter / 4f
                val pct = "%03d".format((f * 100).toInt())
                strip(File(dir, "strip-solid-$mode-f$pct-stretch.png"), Style.Solid, dark, 1f + f, elastic = true)
                if (quarter in 1..3) strip(File(dir, "strip-solid-$mode-f$pct-plain.png"), Style.Solid, dark, 1f + f, elastic = false)
            }
            // The other styles' own pills: at rest and half-way (the fast-drag stretch).
            for (style in listOf(Style.Glass, Style.Soft, Style.Brutal, Style.Paper, Style.Signal)) {
                strip(File(dir, "strip-${style.storageName}-$mode-f000-stretch.png"), style, dark, 1f, elastic = true)
                strip(File(dir, "strip-${style.storageName}-$mode-f050-stretch.png"), style, dark, 1.5f, elastic = true)
            }
        }
        strip(File(dir, "strip-solid-dark-rtl-f050-stretch.png"), Style.Solid, dark = true, place = 1.5f, elastic = true, rtl = true)
        strip(File(dir, "strip-solid-dark-marks-f050-stretch.png"), Style.Solid, dark = true, place = 2.5f, elastic = true, marks = true)
        strip(File(dir, "strip-solid-light-marks-f050-stretch.png"), Style.Solid, dark = false, place = 2.5f, elastic = true, marks = true)
    }

    private data class M(val provider: String, val id: String, val label: String)

    private val models: List<M> =
        listOf(
            "zai" to listOf("GLM-5.3", "GLM-5.3 Highspeed", "GLM-5.3-Flash"),
            "opencode" to
                listOf(
                    "Beam Free", "Big Pickle", "Claude Fable 5.1", "Claude Fable 5.2", "Claude Haiku 5.5", "Claude Opus 5.5",
                    "Claude Sonnet 5.5", "Iceman Free", "Muse Wonder", "Nemotron 3.5 Lightning Free", "Qwen3.8 Flash", "Qwen3.8 Max",
                ),
            "openai" to listOf("GPT-5.5", "GPT-5.5", "GPT-5.6 Luna", "GPT-5.6 Luna", "GPT-5.6 Sol", "GPT-5.6 Terra"),
            "anthropic" to listOf("Claude Fable 5.2", "Claude Opus 5.5", "Claude Sonnet 5.5"),
            "google" to listOf("Gemini 3.5 Flash", "Gemini 3.5 Pro"),
        ).flatMap { (provider, labels) ->
            labels.mapIndexed { i, label ->
                val slug = label.lowercase().replace(' ', '-')
                M(provider, if (labels.count { it == label } > 1 && labels.indexOf(label) != i) "$slug-2026-10-01" else slug, label)
            }
        }

    private val providerNames = mapOf("zai" to "Z.AI", "opencode" to "OpenCode", "openai" to "OpenAI", "anthropic" to "Anthropic", "google" to "Google")
    private val options = models.map { KitModelOption(it.provider, it.id, it.label, providerName = providerNames.getValue(it.provider)) }
    private fun option(provider: String, label: String) = options.first { it.providerId == provider && it.label == label }
    private val favorites = listOf(option("zai", "GLM-5.3"), option("opencode", "Claude Fable 5.2"), option("openai", "GPT-5.6 Sol"))
    private val recent = listOf(option("zai", "GLM-5.3"), option("opencode", "Iceman Free"))

    /** The model sheet over a page; [act] brings it to the state to picture. */
    private fun sheet(file: File, style: Style, dark: Boolean, level: MotionLevel = MotionLevel.Off, act: ComposeUiTest.() -> Unit = {}) {
        runDesktopComposeUiTest((412 * DENSITY).toInt(), (892 * DENSITY).toInt()) {
            var favs by mutableStateOf(favorites)
            setContent {
                Phone(style, dark, level, 412, 892) {
                    KitScreen(topBar = { KitTopBar(title = "New conversation") }, edges = KitScreenEdges.Bleed) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {}
                        KitModelSheet(
                            visible = true,
                            onDismissRequest = {},
                            options = options,
                            current = option("zai", "GLM-5.3").key,
                            onChoose = {},
                            favorites = favs,
                            recent = recent,
                            onToggleFavorite = { o -> favs = if (o in favs) favs - o else favs + o },
                        )
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            settleMarks()
            act()
            save(file)
        }
    }

    @Test
    fun `model sheet frames`() {
        val dir = out ?: return
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (dark in listOf(true, false)) {
            val mode = if (dark) "dark" else "light"
            sheet(File(dir, "sheet-1-favorites-$mode.png"), Style.Solid, dark)
            sheet(File(dir, "sheet-2-provider-openai-$mode.png"), Style.Solid, dark) {
                onNode(hasContentDescription("OpenAI")).performClick()
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
            }
            sheet(File(dir, "sheet-3-mid-swipe-$mode.png"), Style.Solid, dark, MotionLevel.Full) {
                mainClock.autoAdvance = false
                onNodeWithTag("kit-tab-pages").performTouchInput {
                    down(Offset(width * 0.8f, height * 0.5f))
                    moveBy(Offset(-width * 0.1f, 0f))
                    moveBy(Offset(-width * 0.2f, 0f))
                    moveBy(Offset(-width * 0.25f, 0f))
                }
                mainClock.advanceTimeByFrame()
                mainClock.advanceTimeByFrame()
            }
            sheet(File(dir, "sheet-4-search-$mode.png"), Style.Solid, dark) {
                onNodeWithTag("kit-search-field").performTextInput("claude")
                mainClock.advanceTimeBy(1_500)
                waitForIdle()
            }
        }
        sheet(File(dir, "sheet-5-glass-dark.png"), Style.Glass, dark = true)
        sheet(File(dir, "sheet-5-brutal-light.png"), Style.Brutal, dark = false)
        sheet(File(dir, "sheet-5-soft-light.png"), Style.Soft, dark = false)
        sheet(File(dir, "sheet-5-paper-light.png"), Style.Paper, dark = false)
    }

    private companion object {
        const val DENSITY = 2.625f
    }
}
