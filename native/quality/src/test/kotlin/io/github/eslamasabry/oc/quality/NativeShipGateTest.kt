package io.github.eslamasabry.oc.quality

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P12-FIX-LINUX-VOICE ship gate (migration proof 2026-11-03 §5 bugs 2 and 3): the shipped app must bind the REAL built-in
 * Linux and the REAL offline speech engine, never a placeholder, and carry their native libraries. The APK itself is
 * checked by `:androidApp:verify<Variant>NativeLibraries`, which every preview/switch `assemble` runs.
 */
class NativeShipGateTest {
    private val root = File(System.getProperty("oc.root"))
    private val appMain = File(root, "androidApp/src/main")
    private val linuxJni = File(root, "android/linux/src/main/jniLibs")
    private val abis = listOf("arm64-v8a", "x86_64")
    private val linuxLibraries =
        listOf(
            "libproot.so", "libproot-loader.so", "libtalloc.so", "libandroid-shmem.so",
            "libaiteam_engine.so", "libaiteam_sandbox.so", "libaiteam_boundary_probe.so",
        )

    private fun appSources(): List<File> = appMain.walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `the app binds no Linux or speech placeholder in a non-test build`() {
        val offenders =
            appSources().flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//").trim()
                    val placeholder =
                        Regex("""\bAbsentLinuxHost\s*\(""").containsMatchIn(code) ||
                            Regex("""\bNoOfflineSpeechEngine\b""").containsMatchIn(code) ||
                            Regex("""LinuxPresence\.NotOnThisBuild""").containsMatchIn(code)
                    if (placeholder && !code.startsWith("*")) "${file.relativeTo(root)}:${i + 1}: $code" else null
                }
            }
        assertTrue(offenders.isEmpty(), "Placeholder bindings in :androidApp main:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `the Linux seams and the speech port are bound to the real runtime`() {
        val wiring = File(appMain, "kotlin/io/github/eslamasabry/opencode_mobile/graph/LinuxVoiceWiring.kt").readText()
        assertTrue("fun linuxHost(runtime: BuiltinRuntime): LinuxHost = runtime.host" in wiring)
        assertTrue("fun linuxLifecycle(runtime: BuiltinRuntime): LinuxLifecycle = runtime.lifecycle" in wiring)
        assertTrue("fun phoneServerSource(runtime: BuiltinRuntime): PhoneServerSource = runtime.server" in wiring)
        assertTrue("BuiltinLinux.get(app)" in wiring, "BuiltinRuntime must drive :android:linux's BuiltinLinux")
        assertTrue("SherpaOnnx.whisper(" in wiring && "SherpaOnnx.kokoro(" in wiring)
        val voice = File(appMain, "kotlin/io/github/eslamasabry/opencode_mobile/graph/VoiceWiring.kt").readText()
        assertTrue("sherpa = OfflineSpeechEngine," in voice, "VoiceWiring must hand the real sherpa-onnx engine to :android:voice")
        val app = File(root, "androidApp/build.gradle.kts").readText()
        assertTrue("\":android:linux\"" in app, ":android:linux must be on the app classpath")
        assertTrue("useLegacyPackaging = true" in app, "proot runs from the native library folder: libraries must be extracted")
        assertTrue("VerifyNativeLibraries" in app, "the APK library gate must stay wired")
        val media = File(root, "android/media/build.gradle.kts").readText()
        assertTrue("com.k2fsa.sherpa.onnx:sherpa-onnx-android:2.0.1" in media)
    }

    @Test
    fun `the built-in Linux libraries are present for both ABIs and 16 KB aligned`() {
        for (abi in abis) for (name in linuxLibraries) {
            val lib = File(linuxJni, "$abi/$name")
            assertTrue(lib.isFile, "missing ${lib.relativeTo(root)}")
            val align = minLoadAlign(lib.readBytes())
            assertTrue(align != null && align >= 0x4000 && align % 0x4000 == 0L, "${lib.relativeTo(root)}: LOAD align $align")
        }
        assertTrue(File(root, "android/linux/src/main/assets/aiteam-engine-manifest.json").isFile)
    }

    @Test
    fun `the built-in Linux libraries are the Flutter app's own, byte for byte`() {
        val flutter = File(root, "../android/app/src/main/jniLibs")
        if (!flutter.isDirectory) return // a native-only checkout: the alignment test above still holds
        for (abi in abis) for (name in linuxLibraries) {
            assertEquals(sha256(File(flutter, "$abi/$name")), sha256(File(linuxJni, "$abi/$name")), "$abi/$name differs from the Flutter app's")
        }
        assertEquals(
            sha256(File(root, "../android/app/src/main/assets/aiteam-engine-manifest.json")),
            sha256(File(root, "android/linux/src/main/assets/aiteam-engine-manifest.json")),
        )
    }

    @Test
    fun `the vendored sherpa-onnx AAR is the pinned one`() {
        val sums = File(root, "third_party/SHA256SUMS").readLines().filter { it.isNotBlank() }.associate { line ->
            val (hash, path) = line.trim().split(Regex("\\s+"), limit = 2)
            path to hash
        }
        val aar = "maven/com/k2fsa/sherpa/onnx/sherpa-onnx-android/2.0.1/sherpa-onnx-android-2.0.1.aar"
        assertEquals("a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7", sums[aar])
        assertTrue(File(root, "third_party/${aar.removeSuffix(".aar")}.pom").isFile)
        val file = File(root, "third_party/$aar")
        if (file.isFile) assertEquals(sums[aar], sha256(file), "run tools/vendor/fetch.sh again: the AAR does not match its pin")
    }

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    /** The smallest p_align of a 64-bit little-endian ELF's PT_LOAD segments; null when it is not one. */
    private fun minLoadAlign(b: ByteArray): Long? {
        if (b.size < 64 || b[0] != 0x7f.toByte() || b[1] != 'E'.code.toByte() || b[4] != 2.toByte() || b[5] != 1.toByte()) return null
        val buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val phoff = buf.getLong(0x20).toInt()
        val size = buf.getShort(0x36).toInt() and 0xffff
        val count = buf.getShort(0x38).toInt() and 0xffff
        return (0 until count).mapNotNull { i ->
            val at = phoff + i * size
            if (at + 56 <= b.size && buf.getInt(at) == 1) buf.getLong(at + 48) else null
        }.minOrNull()
    }
}
