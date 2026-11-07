package io.github.eslamasabry.oc.migration.readers.model

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator.ORPHAN
import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator.PROFILE_A
import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator.PROFILE_B
import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.migration.readers.ledger.FlutterStoreScanner
import io.github.eslamasabry.oc.migration.readers.ledger.LedgerStatus
import io.github.eslamasabry.oc.migration.readers.ledger.NamesSnapshot
import io.github.eslamasabry.oc.migration.readers.ledger.StoreLedger
import io.github.eslamasabry.oc.migration.readers.prefs.PrefValue
import io.github.eslamasabry.oc.migration.readers.prefs.StoredKind
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Assumptions.assumeTrue

class FlutterKeyRegistryTest {

    private val repoRoot = File(System.getProperty("oc.repoRoot") ?: "../../..")

    @Test
    fun `every key in the Flutter preference ledger has a native shape`() {
        val ledgerFile = File(repoRoot, "test/fixtures/coverage/settings_preferences_ledger.json")
        assertTrue(ledgerFile.exists(), "ledger not found at $ledgerFile")
        val ledger = MiniJson.parse(ledgerFile.readText()) as Map<*, *>
        assertEquals(188, ledger.size, "the Flutter ledger changed size: add rows to FlutterKey, then update this count")
        // Ledger spellings that differ from the stored key, with the reason.
        val aliases = mapOf(
            "oc.factsbook" to "oc.factsbook-node.stopped", // ledger entry for the node-stopped flag
            "oc.widgetUsage" to "oc.widgetUsage.", // Kotlin reads oc.widgetUsage.<id>
            "oc.providerRuntimeRefresh." to "oc.providerRuntimeRefresh.v1.",
        )
        val missing = mutableListOf<String>()
        for (k in ledger.keys.map { it as String }) {
            val name = aliases[k] ?: k
            val asPref = FlutterKey.entries.any { it.pattern == name }
            val asSecret = SecretKey.entries.any { it.pattern == name }
            val other = name in FlutterKey.NOT_STORED || name in FlutterKey.KEYSTORE_ALIAS_PREFIXES
            if (!asPref && !asSecret && !other) missing += k
        }
        if (missing.isNotEmpty()) fail("ledger keys without a native shape: $missing")
    }

    @Test
    fun `ledger scope agrees with the registry scope`() {
        val ledger = MiniJson.parse(File(repoRoot, "test/fixtures/coverage/settings_preferences_ledger.json").readText()) as Map<*, *>
        val ledgerToScope = mapOf("profile" to setOf(KeyScope.Profile, KeyScope.Owner), "global" to setOf(KeyScope.App), "shared" to setOf(KeyScope.Shared), "other" to setOf(KeyScope.Other))
        val disagreements = mutableListOf<String>()
        for ((k, v) in ledger) {
            val shape = FlutterKey.entries.firstOrNull { it.pattern == k } ?: continue
            val scope = (v as Map<*, *>)["scope"] as String
            if (shape.scope !in ledgerToScope.getValue(scope)) disagreements += "$k ledger=$scope registry=${shape.scope}"
        }
        assertEquals(emptyList(), disagreements)
    }

    @Test
    fun `patterns are unique and prefix patterns end with a dot`() {
        assertEquals(FlutterKey.entries.size, FlutterKey.entries.map { it.pattern }.toSet().size)
        for (k in FlutterKey.entries) if (k.form != IdForm.Exact) assertTrue(k.pattern.endsWith("."), k.name)
    }

    @Test
    fun `classification of each id form`() {
        fun c(k: String) = KeyClassifier.classify(k)
        assertEquals(ClassifiedKey("oc.activeProfile", FlutterKey.ActiveProfile, null, null, false, null), c("oc.activeProfile"))
        c("oc.model.$PROFILE_A").let { assertEquals(FlutterKey.Model, it.key); assertEquals(PROFILE_A, it.id) }
        c("oc.model.$PROFILE_A.agents").let { assertEquals(PROFILE_A, it.id); assertTrue(it.agentsPseudoProfile) }
        c("oc.promptStash.$PROFILE_A.1730000000000000").let {
            assertEquals(FlutterKey.PromptStash, it.key); assertEquals(PROFILE_A, it.id); assertEquals("1730000000000000", it.qualifier)
        }
        c("oc.providerRuntimeRefresh.v1.$PROFILE_A.%2Froot%0A%3Cdefault%3E").let {
            assertEquals(FlutterKey.ProviderRuntimeRefresh, it.key); assertEquals(PROFILE_A, it.id)
        }
        c("oc.draft.note.ses_x.$PROFILE_B").let { assertEquals(FlutterKey.Draft, it.key); assertEquals(PROFILE_B, it.id); assertEquals("note.ses_x", it.qualifier) }
        c("oc.draft.reportProblem.app").let { assertEquals(FlutterKey.Draft, it.key); assertNull(it.id) }
        c("oc.schedulePhoneLease.phone").let { assertEquals(FlutterKey.SchedulePhoneLease, it.key); assertEquals("phone", it.qualifier) }
        // exact keys win over a prefix family that would also match
        assertEquals(FlutterKey.PhraseChipsEnabled, c("oc.phraseChips.enabled").key)
        assertEquals(FlutterKey.ShareSessionViewsLegacy, c("oc.shareSessionViews").key)
        assertEquals(FlutterKey.ShareSessionViewsDefault, c("oc.shareSessionViewsDefault.$PROFILE_A").key)
        assertEquals(FlutterKey.GenuiEnabled, c("oc.genui.enabled.$PROFILE_A").key)
        assertEquals(FlutterKey.VoiceAutoSend, c("voice.auto_send").key)
    }

    @Test
    fun `unknown keys say why`() {
        assertEquals(UnknownReason.NoShape, KeyClassifier.classify("oc.futureFeature.v9").unknownReason)
        assertEquals(UnknownReason.BadIdShape, KeyClassifier.classify("oc.model.not a valid id").unknownReason)
        assertEquals(UnknownReason.BadIdShape, KeyClassifier.classify("oc.model.a.b").unknownReason)
        assertEquals(UnknownReason.SecretShapeInPrefs, KeyClassifier.classify("pw.$PROFILE_A").unknownReason)
    }

    @Test
    fun `secret names classify with their id`() {
        assertEquals(SecretKey.Password to PROFILE_A, KeyClassifier.classifySecret("pw.$PROFILE_A"))
        assertEquals(SecretKey.FactsbookOwner to PROFILE_A, KeyClassifier.classifySecret("oc.factsbookOwner.$PROFILE_A"))
        assertEquals(SecretKey.FactsbookNodePassphrase to null, KeyClassifier.classifySecret("oc.factsbook-node.passphrase"))
        assertEquals(SecretKey.A2aBearer to null, KeyClassifier.classifySecret("oc.a2aBearer.agent-1"))
        assertEquals(SecretKey.OrchestrationGrant to PROFILE_A, KeyClassifier.classifySecret("oc.orchestration.$PROFILE_A.grant"))
        for (n in FixtureGenerator.SECRETS.keys) assertNotNull(KeyClassifier.classifySecret(n), n)
        assertNull(KeyClassifier.classifySecret("something.else"))
    }

    @Test
    fun `the model keeps every key, groups by profile, and decodes types`() {
        val physical = AndroidPrefsXml.parse(AndroidPrefsXmlWriter.write(FixtureGenerator.prefsFixture())).androidMap
        val model = FlutterPrefsModelBuilder.build(physical)

        // Nothing dropped, nothing reordered, nothing re-encoded.
        assertEquals(physical, model.toPhysicalMap())
        assertEquals(physical.keys.toList(), model.toPhysicalMap().keys.toList())

        assertEquals(ProfilesRead.Read(listOf(PROFILE_A, PROFILE_B), 0, 0), model.profiles)
        assertEquals(setOf(ORPHAN), model.idsWithoutProfile())
        assertEquals(setOf("oc.futureFeature.v9", "oc.model.not a valid id", "someOtherPlugin.flag", "oc.fixture.int32", "oc.fixture.float", "oc.fixture.null", "oc.fixture.bigint", "oc.fixture.double"), model.unknown.keys)
        assertEquals(setOf("unprefixed.kotlin.key"), model.unprefixed.keys)
        assertEquals(PrefValue.Str("ar"), model.get(FlutterKey.AppLocale)!!.decoded.value)
        assertEquals(PrefValue.Int64(22 * 60), model.get(FlutterKey.NotifyQuietStart)!!.decoded.value)

        val a = model.forProfile(PROFILE_A)
        assertTrue(a.keys.all { it.contains(PROFILE_A) })
        assertEquals(2, model.forProfile(PROFILE_A, FlutterKey.Model).size, "plain and .agents pseudo-profile")
        assertEquals(StoredKind.JavaList, model.forProfile(PROFILE_B, FlutterKey.ChatNoticesSeen).single().decoded.kind)
        assertEquals(StoredKind.StringSet, model.forProfile(PROFILE_B, FlutterKey.AgentTurnFailures).single().decoded.kind)

        val census = model.census()
        assertEquals(physical.size, census.physicalKeys)
        assertEquals(0, census.typeMismatches)
        assertEquals(0, census.undecodable)
        assertEquals(8, census.unknownKeys)
        assertEquals(2, census.agentsPseudoKeys)
        // Every registry shape is represented in the fixture.
        val seen = (model.app.values + model.byId.values.flatMap { it.values }).mapNotNull { it.classified.key }.toSet()
        assertEquals(FlutterKey.entries.toSet() - FlutterKey.DesktopWindowState, seen)
    }

    @Test
    fun `type mismatches and undecodable values are flagged, never dropped`() {
        val m = FlutterPrefsModelBuilder.build(
            linkedMapOf(
                "flutter.oc.thermalGuard" to RawPref.Str("true"),
                "flutter.oc.chatNoticesSeen.$PROFILE_A" to RawPref.Str("VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!{broken"),
            ),
        )
        assertEquals(TypeCheck.Mismatch, m.get(FlutterKey.ThermalGuard)!!.typeCheck)
        assertEquals(TypeCheck.Undecodable, m.forProfile(PROFILE_A).values.single().typeCheck)
        assertEquals(2, m.toPhysicalMap().size)
    }

    @Test
    fun `oc_profiles that is not a list reads as unreadable, absent reads as absent`() {
        assertEquals(ProfilesRead.Absent, FlutterPrefsModelBuilder.build(emptyMap()).profiles)
        assertEquals(ProfilesRead.Unreadable, FlutterPrefsModelBuilder.build(mapOf("flutter.oc.profiles" to RawPref.Str("{"))).profiles)
        val mixed = FlutterPrefsModelBuilder.build(mapOf("flutter.oc.profiles" to RawPref.Str("""[{"id":"1"},{"id":"bad id"},3,{}]""")))
        assertEquals(ProfilesRead.Read(listOf("1"), invalidIds = 2, nonObjects = 1), mixed.profiles)
    }

    @Test
    fun `the generic sweep takes every key the model attributes to a profile and nothing of another`() {
        val physical = FixtureGenerator.prefsFixture()
        val model = FlutterPrefsModelBuilder.build(physical)
        val logical = physical.keys.mapNotNull { if (it.startsWith("flutter.")) it.removePrefix("flutter.") else null }
        for (id in listOf(PROFILE_A, PROFILE_B, ORPHAN)) {
            val swept = ProfileSweep.scopedKeys(id, logical)
            val attributed = model.forProfile(id).keys
            assertTrue(swept.containsAll(attributed), "sweep misses attributed keys of $id: ${attributed - swept}")
            val others = listOf(PROFILE_A, PROFILE_B, ORPHAN) - id
            for (o in others) assertTrue(swept.none { it in model.forProfile(o).keys }, "sweep of $id took $o's keys")
            assertFalse(swept.any { model.app.containsKey(it) && !it.contains(".$id") })
        }
        // The app-wide draft is never swept.
        assertTrue(ProfileSweep.scopedKeys("app", logical).all { it.startsWith("oc.draft.") && it.endsWith(".app") })
    }

    // ---- store ledger (docs/native/migration/store-ledger.json), E00-S01 ----

    private val ledger by lazy { StoreLedger.load(repoRoot) }
    private val snapshot by lazy { NamesSnapshot.load(repoRoot) }

    @Test
    fun `B-MIGR-005 every name the released 2_1_4 tree writes has a store ledger row`() {
        assertEquals("2.1.4+62", snapshot.release)
        assertEquals(ledger.flutterCommit, snapshot.commit, "the ledger and the snapshot must describe the same Flutter commit")
        val unmapped = ledger.unmapped(snapshot.names.map { it.name })
        if (unmapped.isNotEmpty()) fail("names the Flutter app writes with no row in ${StoreLedger.PATH}: $unmapped")
    }

    @Test
    fun `B-MIGR-005 rescanning a Flutter checkout finds the snapshot and nothing unmapped`() {
        val tree = System.getProperty("oc.flutterTree")
        assumeTrue(tree != null, "set -PflutterTree=<checkout of origin/master> to rescan the released tree")
        val scanned = FlutterStoreScanner.scan(File(tree!!))
        val unmapped = ledger.unmapped(scanned.map { it.name })
        if (unmapped.isNotEmpty()) fail("names the Flutter app writes with no row in ${StoreLedger.PATH}: $unmapped")
        assertEquals(snapshot.names.map { it.name }, scanned.map { it.name }, "the tree differs from the snapshot: run scanFlutterTree")
    }

    @Test
    fun `B-MIGR-005 a key added to the Flutter tree without a ledger row fails the check`() {
        val tree = createTempDirectory("flutter-tree").toFile()
        try {
            File(tree, "lib/state").mkdirs()
            File(tree, "lib/state/new_thing.dart").writeText(
                """
                const localAgentOfferSkippedKey = 'local_agents_offer_skipped';
                Future<void> save(SharedPreferences prefs, String id) async {
                  await prefs.setBool('oc.brandNewThing.${'$'}id', true);
                  await prefs.setString('oc.model.${'$'}id', 'x');
                  await prefs.setInt('plainCounter', 1);
                }
                """.trimIndent(),
            )
            File(tree, "android/app/src/main/kotlin/x").mkdirs()
            File(tree, "android/app/src/main/kotlin/x/NewStore.kt").writeText(
                """
                object NewStore {
                    private const val PREFS = "oc_new_native_file"
                    fun open(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    fun file(c: Context) = File(c.filesDir, "new-thing-v1.json")
                }
                """.trimIndent(),
            )
            val scanned = FlutterStoreScanner.scan(tree).map { it.name }
            assertEquals(listOf("local_agents_offer_skipped", "new-thing-v1.json", "oc.brandNewThing.", "oc.model.", "oc_new_native_file", "plainCounter"), scanned)
            assertEquals(listOf("new-thing-v1.json", "oc.brandNewThing.", "oc_new_native_file", "plainCounter"), ledger.unmapped(scanned))
        } finally {
            tree.deleteRecursively()
        }
    }

    @Test
    fun `B-MIGR-005 each ledger row names its writer, its native reader or decision, and the fallback words`() {
        val problems = mutableListOf<String>()
        assertEquals(ledger.rows.size, ledger.rows.map { it.id }.toSet().size, "row ids are unique")
        val names = ledger.rows.flatMap { r -> (listOf(r.name) + r.aliases).map { r.kind to it } }
        assertEquals(names.size, names.toSet().size, "a name or alias belongs to one row of each kind")
        for (r in ledger.rows) {
            if (r.decision.isBlank()) problems += "${r.id}: no decision"
            if (r.nativeReader.isBlank()) problems += "${r.id}: no native reader or decision words"
            when (r.status) {
                LedgerStatus.READ -> if (!r.nativeReader.startsWith("same as ") && !File(repoRoot, r.nativeReader).isFile) {
                    problems += "${r.id}: reader ${r.nativeReader} does not exist"
                }
                LedgerStatus.MISSING -> if (r.nativeReader != "none yet") problems += "${r.id}: MISSING rows read 'none yet'"
                LedgerStatus.KEPT -> if (r.nativeReader != "kept untouched") problems += "${r.id}: KEPT rows read 'kept untouched'"
                LedgerStatus.DEFERRED -> if (!r.nativeReader.startsWith("deferred")) problems += "${r.id}: DEFERRED rows read 'deferred'"
                LedgerStatus.DROPPED -> if (!r.nativeReader.startsWith("dropped")) problems += "${r.id}: DROPPED rows read 'dropped'"
                LedgerStatus.NOT_STORED, LedgerStatus.NEW -> Unit
            }
            if (r.status != LedgerStatus.NOT_STORED && r.fallback.isNullOrBlank()) problems += "${r.id}: no fallback words"
            if (r.status != LedgerStatus.NEW && r.flutterWriter.isNullOrBlank()) problems += "${r.id}: no Flutter writer"
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `B-MIGR-007 every registry shape and secret name has a store ledger row`() {
        val missing = FlutterKey.entries.map { it.pattern }.filter { p -> ledger.rowsFor(p).none { it.kind == "pref" } } +
            SecretKey.entries.map { it.pattern }.filter { p -> ledger.rowsFor(p).none { it.kind == "secret" } }
        assertEquals(emptyList(), missing)
        for (k in FlutterKey.entries) {
            val r = ledger.rowsFor(k.pattern).single { it.kind == "pref" }
            val expected = when {
                Handling.D in k.handling -> setOf(LedgerStatus.DEFERRED)
                Handling.X in k.handling || Handling.R in k.handling || Handling.I in k.handling -> setOf(LedgerStatus.DROPPED)
                Handling.N in k.handling -> setOf(LedgerStatus.NOT_STORED)
                k.isNew -> setOf(LedgerStatus.NEW)
                else -> setOf(LedgerStatus.READ, LedgerStatus.MISSING)
            }
            assertTrue(r.status in expected, "${k.pattern}: ledger says ${r.status}, registry handling ${k.handling.toList()}")
        }
    }

    @Test
    fun `B-MIGR-005 the 2026-11-06 audit gaps are MISSING rows`() {
        val gaps = listOf(
            "oc.scheduleReceipts.", "oc_background_native",
            "oc_background_native/pausedByUser", "camera_permissions", "voice_permissions", "termux-setup-v2.json", ".TermuxResultService",
        )
        val wrong = gaps.filter { g -> ledger.rowsFor(g).none { it.status == LedgerStatus.MISSING && it.audit != null } }
        assertEquals(emptyList(), wrong, "audit gaps must stay MISSING (with the audit named) until a native reader lands")
    }

    @Test
    fun `B-MIGR-005 the adopted chat keys are read in place by engine connection (E00-S03)`() {
        val adopted = listOf(
            "oc.chatTail.", "oc.lastProject.", "oc.chatNoticesSeen.", "oc.withdrawnPrompts.", "oc.providerRuntimeRefresh.v1.",
            "oc.providerRuntimeUnloadable.",
        )
        for (name in adopted) {
            val row = ledger.rowsFor(name).single { it.kind == "pref" }
            assertEquals(LedgerStatus.READ, row.status, name)
            assertTrue(File(repoRoot, row.nativeReader).readText().contains("\"$name\""), "$name: ${row.nativeReader}")
        }
    }

    @Test
    fun `B-MIGR-005 local_agents_offer_skipped is read by the onboarding offer (E00-S06)`() {
        val row = ledger.rowsFor("local_agents_offer_skipped").single()
        assertEquals(LedgerStatus.READ, row.status)
        assertTrue(File(repoRoot, row.nativeReader).readText().contains("\"local_agents_offer_skipped\""), row.nativeReader)
    }
}
