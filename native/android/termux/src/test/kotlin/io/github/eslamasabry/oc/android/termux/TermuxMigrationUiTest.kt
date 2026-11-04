package io.github.eslamasabry.oc.android.termux

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Flutter test/termux_migration_ui_test.dart (the rows' sources: discarding a saved copy, the backend says whether the
// move finished, free space in the review, providers to sign in to again, entry points, the swept offer key), ported to
// the page presenter over plain state: the real owner and controller over the same fakes as the Flutter fixture.
@OptIn(ExperimentalCoroutinesApi::class)
class TermuxMigrationUiTest {
    private val source = "termux"
    private val sha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val ampleSpace = 64L * 1024 * 1024 * 1024

    private val sampleInventory = listOf(
        TermuxMigrationSize(TermuxMigrationItem.projects, 431227699, 3412),
        TermuxMigrationSize(TermuxMigrationItem.config, 48128, 23),
        TermuxMigrationSize(TermuxMigrationItem.sessions, 210763776, 5),
        TermuxMigrationSize(TermuxMigrationItem.gitConfig, 812, 1),
        TermuxMigrationSize(TermuxMigrationItem.shellFiles, 5120, 3),
        TermuxMigrationSize(TermuxMigrationItem.aiTeam, 0, 0, problem = TermuxMigrationFailure.unsupportedEntry),
    )

    /** Flutter test/support/termux_migration_fakes.dart FakeMigrationTransport. */
    private inner class UiTransport : TermuxMigrationTransport {
        var inspectCalls = 0
        var packCalls = 0
        var packError: Exception? = null
        var packGate: CompletableDeferred<Unit>? = null
        var holdAfterCancel = false
        val cancelled = mutableListOf<String>()

        override suspend fun inspect(): TermuxMigrationSource {
            inspectCalls++
            return TermuxMigrationSource(items = sampleInventory, freeBytes = ampleSpace)
        }

        override suspend fun pack(jobId: String, item: TermuxMigrationItem, maxBytes: Long): TermuxMigrationArchive {
            packCalls++
            packGate?.await()
            packError?.let {
                packError = null
                throw it
            }
            return TermuxMigrationArchive(10240L + item.ordinal, sha)
        }

        override suspend fun copy(
            jobId: String,
            item: TermuxMigrationItem,
            archive: TermuxMigrationArchive,
            destination: Path,
            cancelled: () -> Boolean,
        ) = Unit

        override suspend fun cancel(jobId: String) {
            cancelled.add(jobId)
            if (holdAfterCancel) return
            packGate?.complete(Unit)
        }
    }

    private inner class UiFixture(scope: CoroutineScope) {
        val transport = UiTransport()
        val archives = FakeArchives()
        val journal = MemoryJournal()
        var freeBytes: Long? = ampleSpace
        var switchError: Exception? = null
        var switches = 0
        var controller: TermuxMigrationController? = null
        val owner = TermuxMigrationOwner(scope) {
            TermuxMigrationController(
                transport = transport,
                archives = archives,
                journal = journal,
                availableBytes = { freeBytes },
                switchProfile = { _, _, _ ->
                    switchError?.let { throw it }
                    switches++
                    "builtin"
                },
                sourceProfileExists = { it == source },
                clock = MutableClock(),
                scope = scope,
                newJobId = { JOB },
            ).also { controller = it }
        }

        fun saveUnfinished(items: List<TermuxMigrationItem>, done: Boolean = false, configCopied: Boolean = false) {
            journal.records[source] = JsonObject(
                buildMap {
                    put("schema", JsonPrimitive(1))
                    put("job", JsonPrimitive(JOB))
                    put("selected", JsonArray(items.map { it.name }.sorted().map(::JsonPrimitive)))
                    put(
                        "archives",
                        JsonObject(
                            if (configCopied) {
                                mapOf("config" to JsonObject(mapOf("bytes" to JsonPrimitive(10240), "sha256" to JsonPrimitive(sha))))
                            } else {
                                emptyMap()
                            },
                        ),
                    )
                    if (done) {
                        put("done", JsonPrimitive(true))
                        put("destination", JsonPrimitive("builtin"))
                    }
                },
            )
        }

        fun page(model: TermuxMigrationPageModel) = model.page()

        suspend fun close() {
            transport.packGate?.complete(Unit)
            owner.cancel()
            owner.settled()
            owner.dispose()
        }
    }

    private fun ui(block: suspend TestScope.(UiFixture) -> Unit) = runTest {
        val fixture = UiFixture(backgroundScope)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private suspend fun TestScope.opened(fixture: UiFixture): TermuxMigrationPageModel {
        val model = TermuxMigrationPageModel(fixture.owner, source)
        model.open()
        runCurrent()
        return model
    }

    // --- Discarding a saved copy (Flutter line 384) -----------------------------------------------------------------

    @Test
    fun `B-LINUX-238 an unfinished copy can be discarded, its temporary files go and the review starts again`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects))
        val model = opened(fixture)
        val unfinished = assertIs<MigrationPage.Unfinished>(model.page())
        assertEquals(listOf(TermuxMigrationItem.projects), unfinished.items)
        assertTrue(unfinished.discardEnabled)
        // Cancelling the sheet calls nothing: everything stays.
        assertTrue(fixture.archives.discarded.isEmpty())
        assertTrue(fixture.journal.records.isNotEmpty())
        model.discard()
        assertEquals(listOf(JOB), fixture.archives.discarded)
        assertTrue(fixture.journal.records.isEmpty())
        val review = assertIs<MigrationPage.Review>(model.page())
        assertEquals(1, fixture.transport.inspectCalls, "a fresh look")
        // The choice starts over: projects on, nothing else.
        assertEquals(setOf(TermuxMigrationItem.projects), model.selected)
        assertTrue(review.moves.single().on)
        assertTrue(review.exports.none { it.on })
    }

    @Test
    fun `B-LINUX-238 a stopped copy waits for the stop, then discards`() = ui { fixture ->
        val model = opened(fixture)
        fixture.transport.packGate = CompletableDeferred()
        val running = launch { model.start() }
        runCurrent()
        assertIs<MigrationPage.Progress>(model.page())
        model.stop()
        running.join()
        val cancelled = assertIs<MigrationPage.Cancelled>(model.page())
        assertTrue(cancelled.discardOffered)
        model.discard()
        assertEquals(listOf(JOB), fixture.archives.discarded)
        assertTrue(fixture.journal.records.isEmpty())
        assertIs<MigrationPage.Review>(model.page())
    }

    @Test
    fun `B-LINUX-238 a failed copy can be discarded`() = ui { fixture ->
        val model = opened(fixture)
        fixture.transport.packError = TermuxMigrationException(TermuxMigrationFailure.sourceChanged)
        model.start()
        val failed = assertIs<MigrationPage.Failed>(model.page())
        assertTrue(failed.discardOffered)
        assertEquals(listOf(MigrationWord.migrationSourceChanged), failed.words)
        model.discard()
        assertTrue(fixture.journal.records.isEmpty())
        assertIs<MigrationPage.Review>(model.page())
    }

    @Test
    fun `B-LINUX-238 a failed removal says so in plain words, keeps the copy and can be tried again`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects))
        val model = opened(fixture)
        fixture.archives.cleanupError = java.nio.file.FileSystemException("/data/data/secret/path")
        model.discard()
        val unfinished = assertIs<MigrationPage.Unfinished>(model.page())
        assertTrue(unfinished.discardFailed)
        assertFalse(unfinished.toString().contains("/data/data"))
        assertTrue(fixture.journal.records.isNotEmpty())
        fixture.archives.cleanupError = null
        model.discard()
        assertIs<MigrationPage.Review>(model.page())
    }

    @Test
    fun `B-LINUX-238 a finished move offers no discard`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects), done = true)
        val done = assertIs<MigrationPage.Done>(opened(fixture).page())
        assertFalse(done.discardOffered)
    }

    // --- The backend says whether the move finished (Flutter line 482) ----------------------------------------------

    @Test
    fun `B-LINUX-238 a completed record shows the done page and nothing is asked of Termux`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects), done = true)
        val done = assertIs<MigrationPage.Done>(opened(fixture).page())
        assertEquals("termux-$JOB", done.projectsFolder)
        assertEquals("/root/projects/termux-$JOB", done.projectsPath)
        assertEquals(0, fixture.transport.inspectCalls)
    }

    @Test
    fun `B-LINUX-238 a completed record that cannot be read is not unfinished but unavailable with Try again`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects))
        fixture.journal.edit(source) {
            put("done", JsonPrimitive(true))
            remove("destination")
        }
        val model = opened(fixture)
        assertEquals(MigrationPage.Unavailable, model.page())
        assertEquals(MigrationEntry.Unavailable, model.entry)
    }

    // --- Free space in the review (Flutter line 510) ----------------------------------------------------------------

    @Test
    fun `B-LINUX-238 too little space blocks the copy with the way forward and choosing fewer clears it`() = ui { fixture ->
        fixture.freeBytes = 200L * 1024 * 1024
        val model = opened(fixture)
        val review = assertIs<MigrationPage.Review>(model.page())
        val space = assertNotNull(review.space)
        assertTrue(space.short)
        assertEquals(200L * 1024 * 1024, space.freeBytes)
        assertFalse(review.startEnabled)
        assertEquals(MigrationWord.migrationReviewSpaceShort, review.startDisabledReason)
        model.start()
        assertEquals(0, fixture.transport.packCalls)
        assertIs<MigrationPage.Review>(model.page())
        // Choosing fewer: with only the tiny Git settings the need fits.
        model.toggle(TermuxMigrationItem.projects, on = false)
        model.toggle(TermuxMigrationItem.gitConfig, on = true)
        val fewer = assertIs<MigrationPage.Review>(model.page())
        assertEquals(false, fewer.space?.short)
        assertTrue(fewer.startEnabled)
        assertNull(fewer.startDisabledReason)
    }

    @Test
    fun `B-LINUX-238 ticking an item updates the need without another look`() = ui { fixture ->
        val model = opened(fixture)
        val before = fixture.transport.inspectCalls
        val controller = fixture.controller!!
        val projectsOnly = controller.reviewSpace(setOf(TermuxMigrationItem.projects)).valueOrNull()!!
        val both = controller.reviewSpace(setOf(TermuxMigrationItem.projects, TermuxMigrationItem.sessions)).valueOrNull()!!
        model.toggle(TermuxMigrationItem.sessions, on = true)
        val space = assertNotNull(assertIs<MigrationPage.Review>(model.page()).space)
        assertEquals(both.requiredBytes, space.requiredBytes)
        assertEquals(ampleSpace, space.freeBytes)
        assertTrue(both.requiredBytes!! > projectsOnly.requiredBytes!!)
        assertEquals(before, fixture.transport.inspectCalls)
    }

    @Test
    fun `B-LINUX-238 free space that cannot be read is said, and Copy still goes to the backend, which decides`() = ui { fixture ->
        fixture.freeBytes = null
        val model = opened(fixture)
        val review = assertIs<MigrationPage.Review>(model.page())
        val space = assertNotNull(review.space)
        assertNull(space.freeBytes) // "Free space unknown"
        assertFalse(space.short)
        assertTrue(review.startEnabled)
        model.start()
        val needs = assertIs<MigrationPage.NeedsSpace>(model.page())
        assertEquals(MigrationWord.migrationNeedsSpaceBodyUnknown, needs.body)
    }

    @Test
    fun `B-LINUX-238 Stop reads Stopping until the backend settled and Resume waits for it`() = ui { fixture ->
        val model = opened(fixture)
        fixture.transport.packGate = CompletableDeferred()
        fixture.transport.holdAfterCancel = true
        val running = launch { model.start() }
        runCurrent()
        model.stop()
        runCurrent()
        val stopping = assertIs<MigrationPage.Cancelled>(model.page())
        assertTrue(stopping.settling)
        assertEquals(MigrationWord.migrationStopping, stopping.title)
        assertTrue(fixture.owner.busy)
        model.resume()
        assertEquals(1, fixture.transport.packCalls, "Resume did not start")
        fixture.transport.packGate!!.complete(Unit)
        running.join()
        val stopped = assertIs<MigrationPage.Cancelled>(model.page())
        assertFalse(stopped.settling)
        assertEquals(MigrationWord.migrationCancelled, stopped.title)
        assertFalse(fixture.owner.busy)
        fixture.transport.packGate = null
        fixture.transport.holdAfterCancel = false
        model.resume()
        assertIs<MigrationPage.Done>(model.page())
    }

    // --- Providers to sign in to again (Flutter line 616) -----------------------------------------------------------

    @Test
    fun `B-LINUX-238 a reopened finished move names the providers, offline`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects, TermuxMigrationItem.config), done = true, configCopied = true)
        fixture.archives.names = listOf("Anthropic", "OpenAI")
        val done = assertIs<MigrationPage.Done>(opened(fixture).page())
        assertEquals(listOf("Anthropic", "OpenAI"), done.providerNames)
        assertEquals(MigrationWord.migrationSignInAgainNamed, done.signInWords)
        assertEquals(listOf(TermuxMigrationItem.config), done.exports)
        assertEquals("/root/.oc-migration-exports/$JOB", done.exportsPath)
        assertEquals(0, fixture.transport.inspectCalls)
    }

    @Test
    fun `B-LINUX-238 the copy that just finished names the providers too`() = ui { fixture ->
        fixture.archives.names = listOf("Anthropic")
        val model = opened(fixture)
        model.toggle(TermuxMigrationItem.config, on = true)
        model.start()
        val done = assertIs<MigrationPage.Done>(model.page())
        assertEquals(listOf("Anthropic"), done.providerNames)
        assertEquals(MigrationWord.migrationSignInAgainNamed, done.signInWords)
    }

    @Test
    fun `B-LINUX-238 unknown provider names keep the generic words`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects), done = true)
        val done = assertIs<MigrationPage.Done>(opened(fixture).page())
        assertTrue(done.providerNames.isEmpty())
        assertEquals(MigrationWord.migrationSignInAgainAny, done.signInWords)
    }

    @Test
    fun `B-LINUX-238 raw text and credentials from below never reach the page and Try again repeats the copy`() = ui { fixture ->
        val model = opened(fixture)
        fixture.transport.packError = IllegalStateException("curl: (22) Authorization: Bearer sk-ant-api03-RAWSECRET at /data/data/com.termux")
        model.start()
        val failed = assertIs<MigrationPage.Failed>(model.page())
        assertEquals(TermuxMigrationFailure.storage, failed.code)
        assertEquals(listOf(MigrationWord.migrationStorageFailed), failed.words)
        for (raw in listOf("RAWSECRET", "Bearer", "curl", "com.termux")) assertFalse(failed.toString().contains(raw), raw)
        model.retry()
        assertIs<MigrationPage.Done>(model.page())
    }

    @Test
    fun `B-LINUX-238 a failed connection after the files are in offers Try again and This phone`() = ui { fixture ->
        fixture.switchError = TermuxMigrationException(TermuxMigrationFailure.profileSwitch)
        val model = opened(fixture)
        model.start()
        val failed = assertIs<MigrationPage.Failed>(model.page())
        assertEquals(listOf(MigrationWord.migrationConnectionFailed), failed.words)
        assertTrue(failed.openThisPhone)
        assertEquals(MigrationFailedAction.TryAgain, failed.primary)
        fixture.switchError = null
        model.retry()
        assertIs<MigrationPage.Done>(model.page())
    }

    // --- Entry points (Flutter line 725) ----------------------------------------------------------------------------

    private val termuxProfile = MigrationProfile(id = "termux", openCode = true, baseUrl = TERMUX_MANAGED_SERVER_URL, flavor = "v1")

    @Test
    fun `B-LINUX-238 This phone for Termux leads with the move and opening it shows the review`() = ui { fixture ->
        val row = TermuxMigrationRowModel(fixture.owner, source)
        row.read()
        assertEquals(MigrationRowState(MigrationWord.migrationTitle, MigrationWord.migrationRowBody, running = false, moved = false), row.state())
        assertEquals(0, fixture.transport.inspectCalls, "no look by itself")
        assertIs<MigrationPage.Review>(opened(fixture).page())
    }

    @Test
    fun `B-LINUX-238 This phone says Resume for an unfinished copy and Moved after`() = ui { fixture ->
        fixture.saveUnfinished(listOf(TermuxMigrationItem.projects))
        val row = TermuxMigrationRowModel(fixture.owner, source)
        row.read()
        assertEquals(MigrationWord.migrationRowResume, row.state().title)
        val model = opened(fixture)
        model.resume()
        assertIs<MigrationPage.Done>(model.page())
        row.read()
        assertEquals(MigrationWord.migrationDoneTitle, row.state().title)
        assertEquals(MigrationWord.migrationRowDoneBody, row.state().detail)
    }

    @Test
    fun `B-LINUX-238 the in-app host and a non-Termux server show no move`() {
        assertTrue(TermuxMigrationOffers.offersTermuxMigration(termuxProfile, builtinLinuxSupported = true))
        assertFalse(
            TermuxMigrationOffers.offersTermuxMigration(
                MigrationProfile("w", openCode = true, baseUrl = "https://example.com", flavor = "v2"),
                builtinLinuxSupported = true,
            ),
        )
        assertFalse(TermuxMigrationOffers.offersTermuxMigration(null, builtinLinuxSupported = true))
        assertFalse(TermuxMigrationOffers.offersTermuxMigration(termuxProfile, builtinLinuxSupported = false))
    }

    @Test
    fun `B-LINUX-238 the one-time Servers offer is remembered per server when closed and swept with it`() = ui { fixture ->
        val prefs = MemoryPreferences()
        val profiles = listOf(termuxProfile)
        assertEquals(termuxProfile, TermuxMigrationOffers.offerSource(profiles, prefs, termuxSupported = true, builtinLinuxSupported = true))
        assertNull(TermuxMigrationOffers.offerSource(profiles, prefs, termuxSupported = false, builtinLinuxSupported = true))
        assertEquals(0, fixture.transport.inspectCalls, "seen is not asked")
        assertIs<MigrationPage.Review>(opened(fixture).page())
        assertTrue(TermuxMigrationOffers.shouldOffer(profiles, prefs, source))
        assertEquals(MigrationAnswer.Ok(Unit), TermuxMigrationOffers.dismiss(prefs, source) { true })
        assertFalse(TermuxMigrationOffers.shouldOffer(profiles, prefs, source))
        assertEquals(true, prefs.getBoolean("oc.termuxMigrationOffer.$source"))
        assertNull(TermuxMigrationOffers.offerSource(profiles, prefs, termuxSupported = true, builtinLinuxSupported = true))
        assertTrue("oc.termuxMigrationOffer.$source" in TermuxMigrationOffers.profileScopedKeys(prefs, source))
        // A profile being deleted gets no flag written behind its sweep.
        val deleting = MemoryPreferences()
        assertEquals(MigrationAnswer.Refused(TermuxMigrationFailure.cancelled), TermuxMigrationOffers.dismiss(deleting, source) { false })
        assertTrue(deleting.keys().isEmpty())
    }

    @Test
    fun `B-LINUX-238 the offer is gone once a copy was saved`() {
        val prefs = MemoryPreferences(mapOf("oc.termuxMigration.$source" to """{"job":"$JOB"}"""))
        assertFalse(TermuxMigrationOffers.shouldOffer(listOf(termuxProfile), prefs, source))
        assertNull(TermuxMigrationOffers.offerSource(listOf(termuxProfile), prefs, termuxSupported = true, builtinLinuxSupported = true))
    }

    // --- The owner keeps no key of its own (Flutter line 883) -------------------------------------------------------

    @Test
    fun `B-LINUX-238 the owner keeps no key of its own and the offer key is still swept`() {
        val prefs = MemoryPreferences()
        prefs.setBoolean("oc.termuxMigrationOffer.$source", true)
        assertEquals(setOf("oc.termuxMigrationOffer.$source"), TermuxMigrationOffers.profileScopedKeys(prefs, source))
        assertEquals(setOf("oc.termuxMigrationOffer.$source", "oc.termuxMigration.$source"), TermuxMigrationOffers.scopedKeyNames(source))
        val sources = File("src/main/kotlin/io/github/eslamasabry/oc/android/termux")
        val owner = sources.resolve("MigrationOwner.kt").readText()
        assertFalse(owner.contains("oc.termuxMigrationDone"))
        assertFalse(owner.contains("oc.termuxMigrationProviders"))
        assertFalse(owner.contains("MigrationPreferences"))
        // Every oc.* key the module writes is one of the two scoped names.
        val keys = sources.listFiles()!!.flatMap { Regex("\"oc\\.([A-Za-z]+)\\.").findAll(it.readText()).map { m -> m.groupValues[1] }.toList() }.toSet()
        assertEquals(setOf("termuxMigration", "termuxMigrationOffer"), keys)
    }
}
