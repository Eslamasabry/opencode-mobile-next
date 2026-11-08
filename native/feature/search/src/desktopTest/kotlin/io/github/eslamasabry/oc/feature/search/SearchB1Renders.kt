package io.github.eslamasabry.oc.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.GoToPlace
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.GoToSection
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchActivity
import io.github.eslamasabry.oc.feature.search.presentation.SearchHitUi
import io.github.eslamasabry.oc.feature.search.presentation.SearchIndex
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchProject
import io.github.eslamasabry.oc.feature.search.presentation.SearchRecentConversation
import io.github.eslamasabry.oc.feature.search.presentation.SearchRowStatus
import io.github.eslamasabry.oc.feature.search.presentation.SearchServer
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.feature.search.ui.SearchScreen
import io.github.eslamasabry.oc.feature.search.ui.SearchWebOption
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Proof renders of Search B1 (empty and typing; dark and light; Glass and Solid; one Arabic pass). Capture only, no
 * comparison: runs when `-Poc.searchRenders=<dir>` is given, otherwise it returns at once (CI writes nothing).
 * 412 × 915 dp at density 2.625 (1081 × 2402 px), the owner's phone.
 */
@OptIn(ExperimentalTestApi::class)
class SearchB1Renders {
    private val out = System.getProperty("oc.search.renders").orEmpty()
    private val home = ProfileId("home")
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    private val marks = KitLogoLoader { id, _ ->
        File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks/$id.svg").takeIf { it.isFile }?.readText()
    }

    private fun ref(id: String) = ConversationRef(home, SessionId(id))

    private val activity =
        SearchActivity(
            persistentListOf(
                SearchRecentConversation(ref("a"), "Explain this project", "new", now, "claude", "Claude Code", SearchRowStatus.Working),
                SearchRecentConversation(ref("b"), "Hi", "new", now - 5 * minute, "", null, SearchRowStatus.NeedsYou),
                SearchRecentConversation(ref("c"), "You know who you are and where are we heading next", "new", now - 26 * 60 * minute, "opencode", "OpenCode"),
                SearchRecentConversation(ref("d"), "Fix the login redirect on the web shell", "shopfront", now - 30 * 60 * minute, "codex", "Codex"),
                SearchRecentConversation(ref("e"), "Hi", "new", now - 50 * 60 * minute, "opencode", "OpenCode"),
            ),
            persistentListOf(SearchProject("debian-box", "/root/debian-box", 3), SearchProject("new", "/root/new", 9)),
            needsYou = 1,
        )

    private val goTo =
        listOf(GoToPlace.NeedsYou, GoToPlace.Projects, GoToPlace.Settings, GoToPlace.Servers)
            .map { GoToRow(GoToSection.GoTo, it) }
            .toImmutableList()

    private val empty =
        SearchUi(
            servers = persistentListOf(SearchServer(home, "This phone")),
            server = home,
            recent = persistentListOf("flutter build", "login error", "deb"),
            content = ContentPhase.Done,
            goTo = goTo,
            activity = activity,
            nowMs = now,
        )

    private val typing =
        empty.copy(
            query = "deb",
            phase = SearchPhase.Results,
            titleHits = persistentListOf(SearchHitUi(ref("x"), "Debug the release build", "shopfront", updatedMs = now - 3 * 60 * minute)),
            contentHits =
                persistentListOf(
                    SearchHitUi(ref("a"), "Explain this project", "new", "…the debug build won't run on the pinned engine…", false, "m1", now),
                    SearchHitUi(ref("e"), "Hi", "new", "…install the Debian image first, then sign in…", true, "m2", now - 50 * 60 * minute),
                    SearchHitUi(ref("c"), "You know who you are", "new", "…a debian chroot keeps the agents apart…", false, "m3", now - 26 * 60 * minute),
                ),
            destinations =
                SearchIndex.appEntries(PlatformCapabilities.desktop().copy(builtinLinux = true), hasShell = true).toImmutableList(),
        )

    private object Quiet : SearchActions {
        override fun back() = Unit
        override fun search(query: String) = Unit
        override fun submit(query: String) = Unit
        override fun chooseServer(id: ProfileId) = Unit
        override fun searchInside(on: Boolean) = Unit
        override fun loadMore() = Unit
        override fun retry() = Unit
        override fun open(hit: SearchHitUi) = Unit
        override fun forgetRecent(query: String) = Unit
        override fun clearRecent() = Unit
        override fun openServers() = Unit
        override fun goTo(row: GoToRow) = Unit
    }

    private fun shoot(name: String, state: SearchUi, style: Style, dark: Boolean, arabic: Boolean = false) {
        val tag = if (arabic) "ar" else "en"
        Locale.setDefault(Locale.forLanguageTag(tag))
        runDesktopComposeUiTest(1081, 2402) {
            setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(2.625f, 1f),
                    LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                                style = style,
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = tag,
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(LocalKitLogoLoader provides marks) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                SearchScreen(
                                    state,
                                    Quiet,
                                    field = rememberTextFieldState(state.query),
                                    web = SearchWebOption(false) {},
                                )
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
            val image = onRoot().captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(out, "$name.png"))
        }
    }

    @Test
    fun `SCREENS-NAV-34 B1 proof renders of the empty and typing states`() {
        if (out.isBlank()) return
        File(out).mkdirs()
        val previous = Locale.getDefault()
        try {
            for (style in listOf(Style.Glass, Style.Solid)) {
                for (dark in listOf(true, false)) {
                    val look = "${style.storageName}-${if (dark) "dark" else "light"}"
                    shoot("search-empty-$look", empty, style, dark)
                    shoot("search-typing-$look", typing, style, dark)
                }
            }
            shoot("search-empty-glass-dark-ar", empty, Style.Glass, dark = true, arabic = true)
            shoot("search-typing-glass-dark-ar", typing.copy(query = "deb"), Style.Glass, dark = true, arabic = true)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
