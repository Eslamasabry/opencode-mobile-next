package io.github.eslamasabry.oc.engine.voice.models

import io.github.eslamasabry.oc.engine.voice.state.PackId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** Speech and the natural voice share `files/voice_models` (plan 10 §7.1; the Flutter folder, read in place). */
public enum class PackKind(public val root: String) { Speech("voice_models"), NaturalVoice("voice_models") }

public data class ModelFile(val name: String, val bytes: Long, val sha256: String)

/** Natural-voice extras (Kokoro): speaker 3 (`af_heart`), 24 kHz output, languages it speaks. */
public data class NaturalVoiceSpec(val speakerId: Int, val sampleRate: Int, val languages: ImmutableSet<String>)

/**
 * One pinned pack. File order is fixed: speech packs are encoder, decoder, tokens; the natural voice is model,
 * voices, tokens, lexicon (see [ModelPackSpec.file]).
 */
public data class ModelPackSpec(
    val id: PackId,
    val kind: PackKind,
    val repo: String,
    val revision: String,
    val files: ImmutableList<ModelFile>,
    val minTotalRamMb: Long,
    val natural: NaturalVoiceSpec? = null,
) {
    val totalBytes: Long get() = files.sumOf { it.bytes }

    /** `<packId>-<revision>`, the Flutter folder name. */
    val dirName: String get() = "${id.raw}-$revision"

    /** `https://huggingface.co/<repo>/resolve/<revision>/<file>` (the only network use, plan 10 §17 S1). */
    public fun urlFor(file: ModelFile): String = "https://huggingface.co/$repo/resolve/$revision/${file.name}"

    /** The file playing [role]: "encoder" | "decoder" | "tokens" (speech); "model" | "voices" | "tokens" | "lexicon". */
    public fun file(role: String): ModelFile {
        val roles =
            if (kind == PackKind.Speech) listOf("encoder", "decoder", "tokens") else listOf("model", "voices", "tokens", "lexicon")
        val index = roles.indexOf(role)
        require(index >= 0) { "no such role" }
        return files[index]
    }
}

/**
 * The pins, copied byte for byte from Dart `voiceModelPacks` and `naturalVoicePacks` (plan 10 §7.1; B-MEDIA-031 and
 * B-MEDIA-005 check every value). `.int8.onnx` everywhere.
 */
public object ModelCatalog {
    private const val TOKENS_SHA = "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"

    public val base: ModelPackSpec =
        ModelPackSpec(
            id = PackId("base"),
            kind = PackKind.Speech,
            repo = "csukuangfj/sherpa-onnx-whisper-base",
            revision = "bb53ee204431c90d314c1cc08d28d23e5b7927cc",
            files =
                persistentListOf(
                    ModelFile("base-encoder.int8.onnx", 29_120_534, "0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11"),
                    ModelFile("base-decoder.int8.onnx", 130_672_026, "9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d"),
                    ModelFile("base-tokens.txt", 816_730, TOKENS_SHA),
                ),
            minTotalRamMb = 1536,
        )

    public val small: ModelPackSpec =
        ModelPackSpec(
            id = PackId("small"),
            kind = PackKind.Speech,
            repo = "csukuangfj/sherpa-onnx-whisper-small",
            revision = "8f3c18b358db4d1f2fc1eae49d75cd20989e4309",
            files =
                persistentListOf(
                    ModelFile("small-encoder.int8.onnx", 112_442_483, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"),
                    ModelFile("small-decoder.int8.onnx", 262_226_114, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"),
                    ModelFile("small-tokens.txt", 816_730, TOKENS_SHA),
                ),
            minTotalRamMb = 3400,
        )

    public val tiny: ModelPackSpec =
        ModelPackSpec(
            id = PackId("tiny"),
            kind = PackKind.Speech,
            repo = "csukuangfj/sherpa-onnx-whisper-tiny",
            revision = "65176e2deb88badc814a94058666cadccc29b61c",
            files =
                persistentListOf(
                    ModelFile("tiny-encoder.int8.onnx", 12_937_772, "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434"),
                    ModelFile("tiny-decoder.int8.onnx", 89_855_401, "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925"),
                    ModelFile("tiny-tokens.txt", 816_730, TOKENS_SHA),
                ),
            minTotalRamMb = 1024,
        )

    /** Kokoro v1.0 int8, voice `af_heart`. Its 2000 MB total-RAM gate is enforced (M5). Arabic is not offered (D10-9). */
    public val kokoroEn: ModelPackSpec =
        ModelPackSpec(
            id = PackId("kokoro-en"),
            kind = PackKind.NaturalVoice,
            repo = "csukuangfj/kokoro-int8-multi-lang-v1_0",
            revision = "57adb37fc0e8d4bd430cfc1b8f8c2294945b9af2",
            files =
                persistentListOf(
                    ModelFile("model.int8.onnx", 114_202_993, "75635ea3b85a32d00094dcdd61a258aba87c160f7c264c304a7b3ae9d734195e"),
                    ModelFile("voices.bin", 28_200_960, "1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29"),
                    ModelFile("tokens.txt", 687, "6ebb6bb288f20f3ae8d004d3c2ca27697da27c037d75e81a60e2a6a663f95425"),
                    ModelFile("lexicon-us-en.txt", 5_956_885, "7daaab53a181be9885b853a8582bf1838186317e5dadacbcef9c426d6fa0da14"),
                ),
            minTotalRamMb = 2000,
            natural = NaturalVoiceSpec(speakerId = 3, sampleRate = 24_000, languages = persistentSetOf("en")),
        )

    /** Speech packs in the Dart order (base first: the default, "Recommended"). */
    public val speech: ImmutableList<ModelPackSpec> = persistentListOf(base, small, tiny)

    public val natural: ImmutableList<ModelPackSpec> = persistentListOf(kokoroEn)

    public val all: ImmutableList<ModelPackSpec> = persistentListOf(base, small, tiny, kokoroEn)

    public val defaultSpeech: PackId = base.id

    public fun byId(id: PackId): ModelPackSpec? = all.firstOrNull { it.id == id }

    /** Dart `voiceModelPack(id)`: an unknown stored id falls back to the first pack. */
    public fun speechOrDefault(id: String?): ModelPackSpec = speech.firstOrNull { it.id.raw == id } ?: base
}

/** Recognizer threads: 2 when the phone has at least 4 cores, else 1 (Dart `conservativeVoiceThreadCount`). */
public fun conservativeThreadCount(cpuCount: Int): Int = if (cpuCount >= 4) 2 else 1
