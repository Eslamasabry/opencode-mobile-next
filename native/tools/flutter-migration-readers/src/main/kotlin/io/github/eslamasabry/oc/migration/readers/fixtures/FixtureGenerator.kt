package io.github.eslamasabry.oc.migration.readers.fixtures

import io.github.eslamasabry.oc.migration.readers.json.MiniJson
import io.github.eslamasabry.oc.migration.readers.model.ExpectedType
import io.github.eslamasabry.oc.migration.readers.model.FlutterKey
import io.github.eslamasabry.oc.migration.readers.model.IdForm
import io.github.eslamasabry.oc.migration.readers.prefs.AsyncBackends
import io.github.eslamasabry.oc.migration.readers.prefs.FlutterPrefsCodec
import io.github.eslamasabry.oc.migration.readers.prefs.ImagePickerCache
import io.github.eslamasabry.oc.migration.readers.secure.FssFixtureWriter
import io.github.eslamasabry.oc.migration.readers.xml.AndroidPrefsXmlWriter
import io.github.eslamasabry.oc.migration.readers.xml.RawPref
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.interfaces.RSAPrivateCrtKey
import java.util.Base64

/**
 * Synthetic fixtures built from the confirmed on-disk formats. Every value is made up; nothing comes from a
 * real install. The real-install fixture (plan/05 §10.4) is recorded on the emulator later and read by the
 * same readers.
 */
public object FixtureGenerator {
    public const val PACKAGE: String = "io.github.eslamasabry.opencode_mobile"
    public const val PROFILE_A: String = "1735000000000001"
    public const val PROFILE_B: String = "1735000000000002"
    /** Data for this id exists, but it is not in `oc.profiles` (a deleted server's leftovers). */
    public const val ORPHAN: String = "1735000000000099"

    /** The fixture secrets (synthetic). */
    public val SECRETS: Map<String, String> = linkedMapOf(
        "pw.$PROFILE_A" to "fixture-pass-1",
        "oc.codexToken.$PROFILE_B" to "fixture-paseo-1",
        "oc.githubToken.$PROFILE_A" to """{"accessToken":"fixture-github-access","refreshToken":null}""",
        "oc.agentHostSecret.$PROFILE_A" to "0123456789abcdef".repeat(4),
        "oc.byoHostSecrets.$PROFILE_B" to """{"schema":2,"keyAlias":"oc.byoHostSsh.$PROFILE_B","publicKey":"ssh-ed25519 AAAAfixture","deviceToken":"dev-fixture"}""",
        "pw.$ORPHAN" to "orphan-pass",
        // Non-ASCII and an empty value: both are legal secrets.
        "oc.quotaToken.$PROFILE_A" to "كلمة-سر-🔑",
        "oc.teamEngineAuth.$PROFILE_B" to "",
    )

    /** A legacy (pre-2.4.3 writer) Java-serialized list value: `LIST_PREFIX + Base64.DEFAULT(ObjectOutputStream(list))`. */
    public fun javaSerializedList(list: List<String>, arraysAsList: Boolean = false): String {
        val bos = ByteArrayOutputStream()
        ObjectOutputStream(bos).use { it.writeObject(if (arraysAsList) java.util.Arrays.asList(*list.toTypedArray()) else ArrayList(list)) }
        return FlutterPrefsCodec.LIST_PREFIX + FlutterPrefsCodec.encodeAndroidBase64Default(bos.toByteArray())
    }

    private fun sampleValue(key: FlutterKey, seed: String): RawPref = when (key.type) {
        ExpectedType.Bool -> RawPref.Bool(true)
        ExpectedType.Int64 -> RawPref.Int64(42)
        ExpectedType.Dbl -> FlutterPrefsCodec.encodeDouble(0.5)
        ExpectedType.StrList -> FlutterPrefsCodec.encodeStringList(listOf("a:$seed", "b"))
        ExpectedType.Str, ExpectedType.StrOrList -> RawPref.Str(MiniJson.encode(linkedMapOf("v" to 1L, "fixture" to seed)))
    }

    /** Physical key → raw value, the way `FlutterSharedPreferences.xml` holds them. */
    public fun prefsFixture(): LinkedHashMap<String, RawPref> {
        val m = LinkedHashMap<String, RawPref>()
        fun put(logical: String, v: RawPref) { m[FlutterPrefsCodec.physicalKey(logical)] = v }

        val profiles = listOf(
            linkedMapOf("id" to PROFILE_A, "name" to "Fixture OC2", "baseUrl" to "http://10.0.2.2:4097", "backend" to "openCode",
                "username" to "opencode", "flavor" to "v2", "requiresOpenCode2Migration" to false, "openCode2HistoryPending" to false),
            linkedMapOf("id" to PROFILE_B, "name" to "Fixture Paseo", "baseUrl" to "ws://10.0.2.2:5100", "backend" to "paseo",
                "username" to "", "codexDirectory" to "/root/projects/x", "flavor" to "v2", "requiresOpenCode2Migration" to false,
                "openCode2HistoryPending" to false),
        )
        put(FlutterKey.Profiles.pattern, RawPref.Str(MiniJson.encode(profiles)))

        for (key in FlutterKey.entries) {
            if (key == FlutterKey.Profiles || key == FlutterKey.DesktopWindowState) continue
            when (key.form) {
                IdForm.Exact -> put(key.pattern, sampleValue(key, key.name))
                IdForm.Suffix -> {
                    put(key.pattern + PROFILE_A, sampleValue(key, key.name))
                    put(key.pattern + PROFILE_B, sampleValue(key, key.name))
                }
                IdForm.IdThenMore -> put(key.pattern + PROFILE_A + ".item1", sampleValue(key, key.name))
                IdForm.LastSegment -> {
                    put(key.pattern + "note.ses_fixture.$PROFILE_A", sampleValue(key, key.name))
                    put(key.pattern + "reportProblem.app", sampleValue(key, key.name))
                }
                IdForm.Opaque -> put(key.pattern + "phone", sampleValue(key, key.name))
            }
        }
        // Realistic values for the high-risk keys.
        put("oc.offlineQueue", RawPref.Str(MiniJson.encode(listOf(
            linkedMapOf("id" to "q1", "profileID" to PROFILE_A, "sessionID" to "ses_1", "text" to "queued while offline",
                "attachments" to emptyList<Any>(), "mentions" to emptyList<Any>(), "createdAt" to 1730457000000L),
            linkedMapOf("id" to "q2", "profileID" to PROFILE_A, "sessionID" to "ses_1", "text" to "marked as dispatched",
                "attachments" to emptyList<Any>(), "mentions" to emptyList<Any>(), "createdAt" to 1730457001000L, "dispatchedAt" to 1730457002000L),
        ))))
        put("oc.appLocale", RawPref.Str("ar"))
        put("oc.notify.quietStart", RawPref.Int64(22 * 60))
        put("oc.notify.quietEnd", RawPref.Int64(7 * 60))
        // Agent-backend pseudo-profile keys of the phone profile.
        put("oc.model.$PROFILE_A.agents", RawPref.Str("anthropic|claude"))
        put("oc.sessionModels.$PROFILE_A.agents", RawPref.Str("{}"))
        put("oc.providerRuntimeRefresh.v1.$PROFILE_A.%3Cdefault%3E%0A%3Cdefault%3E", RawPref.Bool(true))
        // Edge encodings the UI cannot produce (plan/05 §10.4 step 5, add_edge_keys.py).
        put("oc.chatNoticesSeen.$PROFILE_B", RawPref.Str(javaSerializedList(listOf("auto-approve:ses_1", "auto-approve:ses_2"))))
        put("oc.withdrawnPrompts.$PROFILE_B", RawPref.Str(javaSerializedList(listOf("ses_1|msg_1"), arraysAsList = true)))
        put("oc.agentTurnFailures.$PROFILE_B", RawPref.StrSet(linkedSetOf("legacy-set-entry")))
        put("oc.budgets.$PROFILE_A", RawPref.Str("""{"retired":true}"""))
        put("oc.notifyRules.$PROFILE_A", RawPref.Str("""{"enabled":true,"notifications":true,"wifiOnly":false}"""))
        // Text with markup, control characters, Arabic and an emoji: must survive the XML round trip.
        put("oc.draft.note.ses_edge.$PROFILE_A", RawPref.Str("<b>&\"quoted\"</b>\tTab\nLine\u0001\u001b[0m مرحبا 🚀"))
        // Data of a server that is no longer in oc.profiles.
        put("oc.model.$ORPHAN", RawPref.Str("openai|gpt"))
        // Unknown keys (a newer Flutter build, a typo, a bad id): preserved, never dropped.
        put("oc.futureFeature.v9", RawPref.Str("kept"))
        put("oc.model.not a valid id", RawPref.Str("kept"))
        put("someOtherPlugin.flag", RawPref.Bool(false))
        // Types SharedPreferences can hold but Dart never writes.
        put("oc.fixture.int32", RawPref.Int32(7))
        put("oc.fixture.float", RawPref.F32(1.5f))
        put("oc.fixture.null", RawPref.Null)
        put("oc.fixture.bigint", RawPref.Str(FlutterPrefsCodec.BIG_INTEGER_PREFIX + "zz"))
        put("oc.fixture.double", FlutterPrefsCodec.encodeDouble(1.0E-7))
        // A physical key without the flutter. prefix (Dart cannot see it).
        m["unprefixed.kotlin.key"] = RawPref.Str("kept")
        return m
    }

    /** The image_picker lost-data record of a camera result taken just before the app was killed. */
    public fun imagePickerFixture(): LinkedHashMap<String, RawPref> = linkedMapOf(
        ImagePickerCache.KEY_TYPE to RawPref.Str("image"),
        ImagePickerCache.KEY_IMAGE_PATHS to RawPref.StrSet(linkedSetOf("/data/user/0/$PACKAGE/cache/image_picker_fixture.jpg")),
        ImagePickerCache.KEY_MAX_WIDTH to RawPref.Int64(java.lang.Double.doubleToRawLongBits(2048.0)),
        ImagePickerCache.KEY_MAX_HEIGHT to RawPref.Int64(java.lang.Double.doubleToRawLongBits(2048.0)),
        ImagePickerCache.KEY_IMAGE_QUALITY to RawPref.Int32(85),
    )

    /** A DataStore file as `SharedPreferencesAsync` would write it (not used by this app; detection fixture). */
    public fun dataStoreFixture(): LinkedHashMap<String, RawPref> = linkedMapOf(
        "asyncBool" to RawPref.Bool(true),
        "asyncInt" to RawPref.Int64(1234567890123L),
        "asyncDouble" to RawPref.F64(2.5),
        "asyncString" to RawPref.Str("hello"),
        "asyncList" to FlutterPrefsCodec.encodeStringList(listOf("x", "y")),
    )

    public fun newKeyPair(random: SecureRandom): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048, random) }.generateKeyPair()

    /** PKCS#8 → key pair (the public half is derived from the CRT private key). */
    public fun keyPairFromPkcs8(pkcs8: ByteArray): KeyPair {
        val kf = KeyFactory.getInstance("RSA")
        val priv = kf.generatePrivate(PKCS8EncodedKeySpec(pkcs8)) as RSAPrivateCrtKey
        val pub = kf.generatePublic(RSAPublicKeySpec(priv.modulus, priv.publicExponent))
        return KeyPair(pub, priv)
    }

    /**
     * Writes a complete synthetic app data dir:
     * `shared_prefs/<name>.xml`, `files/datastore/…preferences_pb`, `fixture-rsa-private.pk8.b64` (test-only key that
     * stands in for the Keystore RSA key), and `seed-manifest.json` (expected counts and the synthetic secrets).
     */
    public fun writeAll(outDir: File, keyPair: KeyPair, random: SecureRandom) {
        val shared = File(outDir, "shared_prefs").apply { mkdirs() }
        File(shared, "${FlutterPrefsCodec.FILE_NAME}.xml").writeBytes(AndroidPrefsXmlWriter.write(prefsFixture()))
        File(shared, "${ImagePickerCache.FILE_NAME}.xml").writeBytes(AndroidPrefsXmlWriter.write(imagePickerFixture()))
        val fss = FssFixtureWriter(keyPair.public, random).write(SECRETS, backupCopies = false)
        for ((name, bytes) in fss.xmlFiles()) File(shared, "$name.xml").writeBytes(bytes)
        File(outDir, AsyncBackends.DATASTORE_RELATIVE_PATH).apply { parentFile.mkdirs() }
            .writeBytes(AsyncBackends.writeDataStore(dataStoreFixture()))
        File(outDir, "fixture-rsa-private.pk8.b64").writeText(Base64.getMimeEncoder().encodeToString(keyPair.private.encoded) + "\n")
        val manifest = linkedMapOf(
            "note" to "Synthetic fixture. Every value is made up; the RSA key is a throwaway test key standing in for the Keystore key.",
            "package" to PACKAGE,
            "profiles" to listOf(PROFILE_A, PROFILE_B),
            "orphanIds" to listOf(ORPHAN),
            "prefsPhysicalKeys" to prefsFixture().size.toLong(),
            "secrets" to SECRETS,
        )
        File(outDir, "seed-manifest.json").writeText(MiniJson.encode(manifest) + "\n")
    }
}

/** `./gradlew generateFixtures` → build/fixtures. Pass a second argument to reuse a PKCS#8 key file. */
public object FixtureGeneratorMain {
    @JvmStatic
    public fun main(args: Array<String>) {
        val out = File(args.getOrElse(0) { "build/fixtures" })
        val random = SecureRandom()
        val keyPair = args.getOrNull(1)?.let { FixtureGenerator.keyPairFromPkcs8(Base64.getMimeDecoder().decode(File(it).readText())) }
            ?: FixtureGenerator.newKeyPair(random)
        FixtureGenerator.writeAll(out, keyPair, random)
        println("fixtures written to ${out.absolutePath}")
    }
}
