package io.github.eslamasabry.oc.core.data

import io.github.eslamasabry.oc.core.data.kv.FlutterPrefsCodec
import io.github.eslamasabry.oc.core.data.kv.JavaListDecoder
import io.github.eslamasabry.oc.core.data.kv.RawPref
import io.github.eslamasabry.oc.core.data.xml.AndroidLoad
import io.github.eslamasabry.oc.core.data.xml.AndroidPrefsXml
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * The real-format fixture P5 committed (`synthetic-v1`: a Flutter 2.1.0+57 prefs file with every
 * encoding). Read in place, not copied, so there is one drift-guarded copy (`CommittedFixturesTest`
 * in the readers build).
 */
internal object Fixtures {
    private val moduleDir = File(System.getProperty("user.dir"))
    val repoRoot: File =
        generateSequence(moduleDir) { it.parentFile }
            .first { File(it, "native/settings.gradle.kts").isFile }
    val syntheticV1: File =
        File(
            repoRoot,
            "native/tools/flutter-migration-readers/src/test/resources/fixtures/synthetic-v1",
        )
    val preferenceLedger: String
        get() =
            checkNotNull(Fixtures::class.java.getResource("/settings_preferences_ledger.json"))
                .readText()

    fun prefsXml(): Map<String, RawPref> {
        val read =
            AndroidPrefsXml.parse(File(syntheticV1, "shared_prefs/FlutterSharedPreferences.xml"))
        check(read.load == AndroidLoad.Loaded) { read.toString() }
        return read.androidMap
    }

    const val PROFILE_A = "1735000000000001"
    const val PROFILE_B = "1735000000000002"
    const val ORPHAN = "1735000000000099"
}

/**
 * Same allowlist as androidMain `ObjectStreamJavaListDecoder` (that one is not visible to desktop
 * tests).
 */
internal val jvmCodec =
    FlutterPrefsCodec(
        JavaListDecoder { bytes ->
            try {
                object : ObjectInputStream(ByteArrayInputStream(bytes) as InputStream) {
                        override fun resolveClass(desc: ObjectStreamClass?): Class<*>? {
                            if (desc?.name !in FlutterPrefsCodec.JAVA_LIST_ALLOWED_CLASSES)
                                throw ClassNotFoundException()
                            return super.resolveClass(desc)
                        }
                    }
                    .use {
                        (it.readObject() as? List<*>)
                            ?.takeIf { l -> l.all { e -> e is String } }
                            ?.map { e -> e as String }
                    }
            } catch (e: Exception) {
                null
            }
        }
    )
