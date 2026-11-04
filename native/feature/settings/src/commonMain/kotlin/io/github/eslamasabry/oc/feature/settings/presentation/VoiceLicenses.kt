package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.common.Outcome

/** Bundled licenses; network access is only the person's explicit website action. */
public interface VoiceLicenseSource {
    public suspend fun read(asset: String): Outcome<String>

    public suspend fun copy(text: String): Outcome<Unit>

    public fun openWebsite(url: String)
}

public sealed interface LicenseUi {
    public data object Loading : LicenseUi

    public data object Failed : LicenseUi

    public data class Ready(val text: String) : LicenseUi
}

/** Native voice components. Flutter's record plugin is replaced by Android AudioRecord. */
public enum class VoiceLicenseComponent(
    public val id: String,
    public val componentName: String?,
    public val maker: String,
    public val license: String,
    public val asset: String,
    public val website: String,
) {
    Whisper(
        "whisper",
        null,
        "OpenAI",
        "MIT License",
        "MIT-OpenAI-Whisper.txt",
        "https://github.com/openai/whisper",
    ),
    Kokoro(
        "kokoro",
        "Kokoro",
        "hexgrad",
        "Apache License 2.0",
        "Apache-2.0-Kokoro.txt",
        "https://huggingface.co/hexgrad/Kokoro-82M",
    ),
    Sherpa(
        "sherpa-onnx",
        "sherpa-onnx",
        "k2-fsa",
        "Apache License 2.0",
        "Apache-2.0.txt",
        "https://github.com/k2-fsa/sherpa-onnx",
    ),
    Onnx(
        "onnx-runtime",
        "ONNX Runtime",
        "Microsoft",
        "MIT License",
        "MIT-ONNX-Runtime.txt",
        "https://github.com/microsoft/onnxruntime",
    ),
}

/**
 * Preserve paragraph, indentation and numbered-clause boundaries while rejoining hard-wrapped
 * prose.
 */
public fun reflowLicenseText(text: String): String {
    val newLine = Regex("""^\s+|^[-*]\s|^\(?[0-9a-z]{1,3}[.)]\s""")
    return text.trim().split(Regex("""\n[ \t]*\n""")).joinToString("\n\n") { paragraph ->
        buildString {
            for (line in paragraph.split('\n')) {
                when {
                    isEmpty() -> append(line.trimEnd())
                    newLine.containsMatchIn(line) -> append('\n').append(line.trimEnd())
                    else -> append(' ').append(line.trim())
                }
            }
        }
    }
}
