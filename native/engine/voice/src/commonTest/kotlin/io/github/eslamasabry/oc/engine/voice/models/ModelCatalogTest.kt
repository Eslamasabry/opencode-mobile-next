package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.engine.voice.state.PackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelCatalogTest {
    @Test
    fun `B-MEDIA-031 the speech packs pin exact repos, revisions, files, lengths, hashes and totals`() {
        val base = ModelCatalog.base
        assertEquals("csukuangfj/sherpa-onnx-whisper-base", base.repo)
        assertEquals("bb53ee204431c90d314c1cc08d28d23e5b7927cc", base.revision)
        assertEquals(listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"), base.files.map { it.name })
        assertEquals(160_609_290, base.totalBytes)
        assertEquals(1536, base.minTotalRamMb)
        assertEquals("0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11", base.file("encoder").sha256)
        assertEquals("9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d", base.file("decoder").sha256)

        val small = ModelCatalog.small
        assertEquals("8f3c18b358db4d1f2fc1eae49d75cd20989e4309", small.revision)
        assertEquals(375_485_327, small.totalBytes)
        assertEquals(3400, small.minTotalRamMb)
        assertEquals("acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee", small.file("decoder").sha256)

        val tiny = ModelCatalog.tiny
        assertEquals("65176e2deb88badc814a94058666cadccc29b61c", tiny.revision)
        assertEquals(103_609_903, tiny.totalBytes)
        assertEquals(1024, tiny.minTotalRamMb)
        assertEquals("d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434", tiny.file("encoder").sha256)

        val tokens = ModelCatalog.speech.map { it.file("tokens") }
        assertTrue(tokens.all { it.bytes == 816_730L && it.sha256 == "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126" })
        assertEquals(listOf("base", "small", "tiny"), ModelCatalog.speech.map { it.id.raw })
        assertEquals(PackId("base"), ModelCatalog.defaultSpeech)
    }

    @Test
    fun `B-MEDIA-031 every file is an int8 onnx or a text table, downloaded only over pinned HTTPS`() {
        for (pack in ModelCatalog.all) {
            for (file in pack.files) {
                assertTrue(file.name.endsWith(".int8.onnx") || file.name.endsWith(".txt") || file.name.endsWith(".bin"))
                assertEquals(64, file.sha256.length)
                assertTrue(file.sha256.all { it in '0'..'9' || it in 'a'..'f' })
                assertEquals("https://huggingface.co/${pack.repo}/resolve/${pack.revision}/${file.name}", pack.urlFor(file))
            }
            assertEquals("${pack.id.raw}-${pack.revision}", pack.dirName)
        }
    }

    @Test
    fun `B-MEDIA-005 the natural voice pack is pinned like the speech models`() {
        val kokoro = ModelCatalog.kokoroEn
        assertEquals(PackKind.NaturalVoice, kokoro.kind)
        assertEquals("csukuangfj/kokoro-int8-multi-lang-v1_0", kokoro.repo)
        assertEquals("57adb37fc0e8d4bd430cfc1b8f8c2294945b9af2", kokoro.revision)
        assertEquals(listOf("model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt"), kokoro.files.map { it.name })
        assertEquals(148_361_525, kokoro.totalBytes)
        assertEquals(2000, kokoro.minTotalRamMb)
        assertEquals("75635ea3b85a32d00094dcdd61a258aba87c160f7c264c304a7b3ae9d734195e", kokoro.file("model").sha256)
        assertEquals("1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29", kokoro.file("voices").sha256)
        assertEquals("6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425", kokoro.file("tokens").sha256)
        assertEquals("7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14", kokoro.file("lexicon").sha256)
        assertEquals(NaturalVoiceSpec(3, 24_000, kokoro.natural!!.languages), kokoro.natural)
        assertEquals(setOf("en"), kokoro.natural!!.languages)
        assertEquals(PackKind.Speech.root, PackKind.NaturalVoice.root)
        assertEquals("voice_models", PackKind.Speech.root)
    }

    @Test
    fun `B-MEDIA-033 recognizer threads follow the CPU policy`() {
        assertEquals(1, conservativeThreadCount(1))
        assertEquals(1, conservativeThreadCount(3))
        assertEquals(2, conservativeThreadCount(4))
        assertEquals(2, conservativeThreadCount(12))
    }
}
