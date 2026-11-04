package io.github.eslamasabry.oc.protocol.oc2.live

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.WebSearchGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.WebSearchProvider
import io.github.eslamasabry.oc.core.model.WebSearchResponse
import io.github.eslamasabry.oc.core.model.WebSearchResult
import kotlin.test.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** Flutter test/web_search_test.dart, the WebSourcesOverview half (B-CHAT-124). */
class WebSourcesReviewTest {
    private class SearchApi : WebSearchGateway {
        var available: List<WebSearchProvider> = listOf(WebSearchProvider("one", "One"))
        var searches = 0
        var discoveries = 0
        var answer: (suspend () -> WebSearchResponse)? = null

        override suspend fun webSearchProviders(): Outcome<ImmutableList<WebSearchProvider>> {
            discoveries++
            return available.toImmutableList().ok()
        }

        override suspend fun searchWeb(query: String, providerId: String): Outcome<WebSearchResponse> {
            searches++
            return (answer?.invoke()
                    ?: WebSearchResponse(
                        "one",
                        persistentListOf(
                            WebSearchResult("https://example.com/doc", "Documentation", "Untrusted excerpt"),
                            WebSearchResult("https://localhost/private", null, null),
                        ),
                    ))
                .ok()
        }
    }

    private val api = SearchApi()
    private var scope: Any = "search"
    private val review = WebSourcesReview(api, { true }, { scope })

    private fun source(title: String, url: String, excerpt: String? = null): WebSourceSelection =
        assertIs<WebSourceSelection.Built.Ok>(WebSourceSelection.of(title, url, excerpt)).source

    @Test
    fun `B-CHAT-124 discovery never searches and results require review and explicit confirmation`() = runTest {
        review.discoverProviders()
        assertEquals(0, api.searches)
        assertEquals("one", review.providerId)
        review.search("documentation")
        assertEquals(1, api.searches)
        assertEquals(1, review.results.size)
        assertEquals(1, review.omittedResults)
        assertTrue(review.reviewedSelection()!!.isEmpty())
        assertNull(review.add(review.results.single()))
        assertEquals("Untrusted excerpt", review.reviewedSelection()!!.single().excerpt)
        review.select(review.sources.single(), false)
        assertTrue(review.reviewedSelection()!!.isEmpty())
    }

    @Test
    fun `B-CHAT-124 source staging exposes immutable snapshots and rejects unsafe overflow`() = runTest {
        review.discoverProviders()
        // ImmutableList: no add/clear exists on the snapshot type at all.
        assertIs<ImmutableList<WebSearchProvider>>(review.providers)
        review.search("documentation")
        assertIs<ImmutableList<WebSourceSelection>>(review.results)
        val result = review.results.single()
        assertNull(review.add(result))
        assertEquals(WebSourcesReview.AddProblem.Duplicate, review.add(source("Duplicate", "  https://example.com/doc  ")))
        assertEquals(
            WebSourceSelection.Built.Refused(WebSourceSelection.Problem.Title),
            WebSourceSelection.of("a".repeat(WebSourceSelection.MAX_TITLE + 1), "https://example.com/title"),
        )
        assertEquals(
            WebSourceSelection.Built.Refused(WebSourceSelection.Problem.ExcerptControl),
            WebSourceSelection.of("Excerpt", "https://example.com/excerpt", "visible\u001bhidden"),
        )
        for (bad in listOf("https://user:pw@example.com/", "http://" + listOf(192, 168, 1, 1).joinToString(".") + "/", "https://printer.local/", "ftp://example.com/", "https://example"))
            assertEquals(WebSourceSelection.Built.Refused(WebSourceSelection.Problem.Url), WebSourceSelection.of("t", bad), bad)
        for (i in 1 until WebSourcesReview.MAX_SOURCES) assertNull(review.add(source("s$i", "https://example.com/$i")))
        assertEquals(WebSourcesReview.AddProblem.TooMany, review.add(source("over", "https://example.com/over")))
        assertEquals(WebSourcesReview.MAX_SOURCES, review.sources.size)
    }

    @Test
    fun `B-CHAT-124 multiple providers require a choice and refresh never submits a query`() = runTest {
        api.available = listOf(WebSearchProvider("one", "One"), WebSearchProvider("two", "Two"))
        review.discoverProviders()
        assertNull(review.providerId)
        review.search("docs")
        assertEquals(0, api.searches)
        review.chooseProvider("one")
        review.search("docs")
        review.add(review.results.single())
        api.available = emptyList()
        review.discoverProviders()
        assertNull(review.providerId)
        assertEquals(1, review.sources.size)
        assertEquals(1, api.searches)
    }

    @Test
    fun `B-CHAT-124 a scope switch discards pending search results and the local review`() = runTest {
        review.discoverProviders()
        val pending = CompletableDeferred<WebSearchResponse>()
        api.answer = { pending.await() }
        val request = async { review.search("docs") }
        runCurrent()
        scope = "other account"
        pending.complete(WebSearchResponse("one", persistentListOf(WebSearchResult("https://example.com", null, null))))
        request.await()
        assertTrue(review.scopeChanged)
        assertTrue(review.results.isEmpty())
        assertNull(review.reviewedSelection())
        assertEquals(WebSourcesReview.AddProblem.ScopeChanged, review.add(source("late", "https://example.com/late")))
    }

    @Test
    fun `B-CHAT-124 provider invalidation supersedes an in-flight search without another query`() = runTest {
        review.discoverProviders()
        val pending = CompletableDeferred<WebSearchResponse>()
        api.answer = { pending.await() }
        val request = async { review.search("docs") }
        runCurrent()
        review.invalidateProviders()
        pending.complete(WebSearchResponse("one", persistentListOf(WebSearchResult("https://example.com", null, null))))
        request.await()
        assertTrue(review.results.isEmpty())
        assertEquals(2, api.discoveries)
        assertEquals(1, api.searches)
    }

    @Test
    fun `B-CHAT-124 over the real OC2 gateway discovery sends no search and a location change is a scope change`() = runTest {
        val server = RowServer(location = ServerLocation("/project")) {
            json("""{"location":{"directory":"/project"},"data":[{"id":"one","name":"One"}]}""")
        }
        val g = server.gateway
        val live = WebSourcesReview(g, { g.capabilities.value.webSearch }, { g.location.value })
        live.discoverProviders()
        assertEquals("one", live.providerId)
        assertEquals(listOf("/api/websearch/provider"), server.seen.map { it.path })
        g.setLocation(ServerLocation("/elsewhere"))
        assertNull(live.reviewedSelection())
        assertTrue(live.scopeChanged)
        g.close()
    }
}
