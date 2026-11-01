package io.github.eslamasabry.oc.migration.readers.model

import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator
import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator.ORPHAN
import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator.PROFILE_A
import io.github.eslamasabry.oc.migration.readers.fixtures.FixtureGenerator.PROFILE_B
import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.migration.readers.prefs.PrefValue
import io.github.eslamasabry.oc.migration.readers.prefs.StoredKind
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXml
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
}
