package io.github.eslamasabry.oc.feature.search

import androidx.compose.ui.test.ExperimentalTestApi
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.FormatterHealth
import io.github.eslamasabry.oc.core.model.LanguageServiceHealth
import io.github.eslamasabry.oc.core.model.VersionControlFile
import io.github.eslamasabry.oc.core.model.VersionControlHealth
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.feature.search.presentation.ContentPhase
import io.github.eslamasabry.oc.feature.search.presentation.GoToPlace
import io.github.eslamasabry.oc.feature.search.presentation.GoToRow
import io.github.eslamasabry.oc.feature.search.presentation.GoToSection
import io.github.eslamasabry.oc.feature.search.presentation.ProjectHealthActions
import io.github.eslamasabry.oc.feature.search.presentation.ProjectHealthSnapshot
import io.github.eslamasabry.oc.feature.search.presentation.ProjectHealthUi
import io.github.eslamasabry.oc.feature.search.presentation.SearchActions
import io.github.eslamasabry.oc.feature.search.presentation.SearchHitUi
import io.github.eslamasabry.oc.feature.search.presentation.SearchPhase
import io.github.eslamasabry.oc.feature.search.presentation.SearchServer
import io.github.eslamasabry.oc.feature.search.presentation.SearchUi
import io.github.eslamasabry.oc.feature.search.presentation.ShortcutsActions
import io.github.eslamasabry.oc.feature.search.presentation.ShortcutsUi
import io.github.eslamasabry.oc.feature.search.ui.ProjectHealthScreen
import io.github.eslamasabry.oc.feature.search.ui.SearchScreen
import io.github.eslamasabry.oc.feature.search.ui.ShortcutsScreen
import io.github.eslamasabry.oc.feature.search.web.WebSourcesActions
import io.github.eslamasabry.oc.feature.search.web.WebSourcesProblem
import io.github.eslamasabry.oc.feature.search.web.WebSourcesUi
import io.github.eslamasabry.oc.feature.search.web.WebSourcesScreen
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test

/** Audit capture only (throwaway): the search module's screens, each in a few states. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private val m = "search"
    private val home = ProfileId("home")
    private val servers = persistentListOf(SearchServer(home, "Home"), SearchServer(ProfileId("vps"), "Build VPS with a rather long name"))

    private class A : SearchActions {
        override fun back() {}
        override fun search(query: String) {}
        override fun submit(query: String) {}
        override fun chooseServer(id: ProfileId) {}
        override fun searchInside(on: Boolean) {}
        override fun loadMore() {}
        override fun retry() {}
        override fun open(hit: SearchHitUi) {}
        override fun forgetRecent(query: String) {}
        override fun clearRecent() {}
        override fun openServers() {}
        override fun goTo(row: GoToRow) {}
    }

    private fun hit(id: String, title: String?, project: String? = "app", snippet: String? = null, mid: String? = null, you: Boolean = false) =
        SearchHitUi(ConversationRef(home, SessionId(id)), title, project, snippet, you, mid)

    private val goTo = persistentListOf(
        GoToRow(GoToSection.GoTo, place = GoToPlace.NeedsYou, waiting = 2),
        GoToRow(GoToSection.GoTo, place = GoToPlace.Files, project = "app"),
        GoToRow(GoToSection.GoTo, place = GoToPlace.Projects),
        GoToRow(GoToSection.GoTo, place = GoToPlace.Settings),
        GoToRow(GoToSection.GoTo, place = GoToPlace.Servers),
        GoToRow(GoToSection.Recent, ref = ConversationRef(home, SessionId("r1")), title = "Fix the login redirect"),
        GoToRow(GoToSection.Recent, ref = ConversationRef(home, SessionId("r2")), title = null),
    )

    @Test
    fun search() = Audit.guard("${m}__search__build") {
        val a = A()
        Audit.shoot(m, "search", "idle-recent") {
            SearchScreen(SearchUi(servers = servers, server = home, recent = persistentListOf("deploy", "login redirect", "a very long recent search phrase that wraps"), goTo = goTo), a)
        }
        Audit.shoot(m, "search", "idle-empty") { SearchScreen(SearchUi(servers = servers, server = home), a) }
        Audit.shoot(m, "search", "searching") {
            SearchScreen(SearchUi(query = "login", servers = servers, server = home, phase = SearchPhase.Searching), a)
        }
        Audit.shoot(m, "search", "results") {
            SearchScreen(
                SearchUi(
                    query = "login",
                    servers = servers,
                    server = home,
                    phase = SearchPhase.Results,
                    titleHits = persistentListOf(hit("s1", "Fix login"), hit("s3", null, null), hit("s4", "A conversation whose title is much too long to fit on one line of the phone screen", "a-long-project-name")),
                    contentHits = persistentListOf(hit("s2", "Deploy notes", snippet = "the rollback plan for the login service", mid = "m1", you = true)),
                    content = ContentPhase.Done,
                    canLoadMore = true,
                ),
                a,
            )
        }
        Audit.shoot(m, "search", "results-inside-searching") {
            SearchScreen(
                SearchUi(
                    query = "login", servers = servers, server = home, phase = SearchPhase.Results,
                    titleHits = persistentListOf(hit("s1", "Fix login")), content = ContentPhase.Searching, contentRead = 7,
                ),
                a,
            )
        }
        Audit.shoot(m, "search", "no-match") { SearchScreen(SearchUi(query = "zzz", servers = servers, server = home, phase = SearchPhase.NoMatch), a) }
        Audit.shoot(m, "search", "failed-offline") {
            SearchScreen(SearchUi(query = "x", servers = servers, server = home, phase = SearchPhase.Failed, failure = Failure.Network(NetworkWhy.Offline)), a)
        }
        Audit.shoot(m, "search", "no-server") { SearchScreen(SearchUi(phase = SearchPhase.NoServer), a) }
        Audit.shoot(m, "search", "load-more-failed") {
            SearchScreen(
                SearchUi(
                    query = "plan", servers = servers, server = home, phase = SearchPhase.Results,
                    titleHits = persistentListOf(hit("s1", "Plan the release")), canLoadMore = true, loadMoreFailed = true, allProjects = false,
                ),
                a,
            )
        }
    }

    private class HealthA : ProjectHealthActions {
        override fun back() {}
        override fun refresh() {}
    }

    @Test
    fun projectHealth() = Audit.guard("${m}__project-health__build") {
        val a = HealthA()
        Audit.shoot(m, "project-health", "loading") { ProjectHealthScreen(ProjectHealthUi("/home/dev/app", loading = true), a) }
        Audit.shoot(m, "project-health", "populated") {
            ProjectHealthScreen(
                ProjectHealthUi(
                    "/home/dev/app",
                    snapshot = ProjectHealthSnapshot(
                        vcs = VersionControlHealth(
                            "feature/login", "main",
                            persistentListOf(VersionControlFile("src/Login.kt", "modified", 12, 3), VersionControlFile("README.md", "added", 4, 0)),
                            VersionControlSetupState.Git,
                        ),
                        languages = persistentListOf(LanguageServiceHealth("kt", "Kotlin", "/home/dev/app", "connected"), LanguageServiceHealth("ts", "TypeScript", "/home/dev/app/web", "error")),
                        formatters = persistentListOf(FormatterHealth("ktfmt", persistentListOf(".kt", ".kts"), true), FormatterHealth("prettier", persistentListOf(".ts"), false)),
                        languagesAvailable = true,
                        formattersAvailable = true,
                    ),
                ),
                a,
            )
        }
        Audit.shoot(m, "project-health", "no-git") {
            ProjectHealthScreen(
                ProjectHealthUi("/home/dev/a/very/long/directory/path/for/the/project/that/does/not/fit", snapshot = ProjectHealthSnapshot(vcs = VersionControlHealth(null, null, persistentListOf(), VersionControlSetupState.Absent))),
                a,
            )
        }
        Audit.shoot(m, "project-health", "failed") {
            ProjectHealthScreen(ProjectHealthUi("/home/dev/app", failure = Failure.Network(NetworkWhy.Offline)), a)
        }
    }

    @Test
    fun shortcuts() = Audit.guard("${m}__shortcuts__build") {
        Audit.shoot(m, "shortcuts", "list") { ShortcutsScreen(ShortcutsUi(), ShortcutsActions {}) }
    }

    private class WebA : WebSourcesActions {
        override fun back() {}
        override fun discover() {}
        override fun chooseProvider(id: String) {}
        override fun search(query: String) {}
        override fun addResult(url: String) {}
        override fun addLink(title: String, url: String, excerpt: String) {}
        override fun select(url: String, on: Boolean) {}
        override fun remove(url: String) {}
        override fun confirm(draftLabel: String) {}
    }

    @Test
    fun webSources() = Audit.guard("${m}__web-sources__build") {
        val a = WebA()
        val providers = persistentListOf(WebSearchProvider("one", "Search provider"), WebSearchProvider("two", "Another provider"))
        Audit.shoot(m, "web-sources", "unavailable") { WebSourcesScreen(WebSourcesUi(), a) }
        Audit.shoot(m, "web-sources", "ready") { WebSourcesScreen(WebSourcesUi(available = true, ready = true, providers = providers, provider = "one"), a) }
        Audit.shoot(m, "web-sources", "busy") { WebSourcesScreen(WebSourcesUi(available = true, ready = true, busy = true, providers = providers, provider = "one"), a) }
        Audit.shoot(m, "web-sources", "failed") {
            WebSourcesScreen(WebSourcesUi(available = true, ready = true, providers = providers, provider = "one", problem = WebSourcesProblem.Failed), a)
        }
    }
}
