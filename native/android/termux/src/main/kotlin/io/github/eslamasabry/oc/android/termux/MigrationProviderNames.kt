package io.github.eslamasabry.oc.android.termux

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

// Flutter lib/builtin/migration/migration_provider_names.dart, rule for rule.

private const val MAXIMUM_CONFIG_BYTES = 1024 * 1024
private const val MAXIMUM_PROVIDERS = 64
private const val MAXIMUM_DEPTH = 64

private val PROVIDER_LABELS: Map<String, String> = mapOf(
    "anthropic" to "Anthropic",
    "openai" to "OpenAI",
    "google" to "Google",
    "google-vertex" to "Google Vertex AI",
    "amazon-bedrock" to "Amazon Bedrock",
    "azure" to "Azure",
    "azure-cognitive-services" to "Azure",
    "github-copilot" to "GitHub Copilot",
    "github-copilot-enterprise" to "GitHub Copilot",
    "opencode" to "OpenCode",
    "openrouter" to "OpenRouter",
    "deepseek" to "DeepSeek",
    "groq" to "Groq",
    "mistral" to "Mistral",
    "xai" to "xAI",
    "ollama" to "Ollama",
    "lmstudio" to "LM Studio",
)

private val CONFIG_PATHS: List<String> = listOf(
    ".config/opencode/opencode.json",
    ".config/opencode/opencode.jsonc",
    ".oc-opencode2/config/opencode/opencode.json",
    ".oc-opencode2/config/opencode/opencode.jsonc",
)

private val NO_FOLLOW = arrayOf(LinkOption.NOFOLLOW_LINKS)

/**
 * Reads labels from an already verified, app-private config item export.
 *
 * The caller verifies the migration receipt before passing its directory; that verification owns the
 * trusted-support-to-export path check. This reader rejects links at the export root and below; platform aliases above
 * it are allowed (Android's app-private storage parent aliases). It never reads authentication files, session
 * databases or a live server. Labels indicate configuration only, not a working connection or sign-in. Missing,
 * malformed, oversized or unsafe files produce no names. Every returned string is a fixed label above. Blocking IO:
 * call it on an IO lane.
 */
public fun readMigrationProviderNames(verifiedConfigExport: Path): List<String> {
    val labels = sortedSetOf<String>()
    val root = verifiedConfigExport.toAbsolutePath()
    for (relative in CONFIG_PATHS) {
        try {
            if (!regularFileInsideExport(root, relative)) continue
            val bytes = Files.newByteChannel(root.resolve(relative), setOf(StandardOpenOption.READ), *NO_FOLLOW).use { input ->
                if (input.size() > MAXIMUM_CONFIG_BYTES) null else readAtMost(input, MAXIMUM_CONFIG_BYTES + 1)
            } ?: continue
            if (bytes.size > MAXIMUM_CONFIG_BYTES || !regularFileInsideExport(root, relative)) continue
            labels.addAll(parseMigrationProviderNames(decodeUtf8(bytes) ?: continue))
        } catch (_: Exception) {
            // Filesystem and parser exceptions may embed confidential paths or contents: nothing leaves.
        }
    }
    return labels.toList()
}

private fun readAtMost(input: SeekableByteChannel, limit: Int): ByteArray {
    val buffer = ByteBuffer.allocate(limit)
    while (buffer.hasRemaining()) {
        if (input.read(buffer) < 0) break
    }
    return buffer.array().copyOf(buffer.position())
}

private fun decodeUtf8(bytes: ByteArray): String? =
    try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

private val STRICT_JSON = Json { isLenient = false }

/**
 * Parses JSON/JSONC without reflecting any input key, value or error text. Top-level `provider` IDs and `model` /
 * `small_model` provider prefixes can supply fixed labels. No model identifier or custom name is returned.
 */
public fun parseMigrationProviderNames(jsonOrJsonc: String): List<String> {
    try {
        if (jsonOrJsonc.length > MAXIMUM_CONFIG_BYTES || jsonOrJsonc.toByteArray(Charsets.UTF_8).size > MAXIMUM_CONFIG_BYTES) {
            return emptyList()
        }
        val clean = withoutComments(jsonOrJsonc) ?: return emptyList()
        val decoded = STRICT_JSON.parseToJsonElement(withoutTrailingCommas(clean)) as? JsonObject ?: return emptyList()
        val providers = if (decoded.containsKey("provider")) decoded["provider"] else JsonObject(emptyMap())
        if (providers !is JsonObject || providers.size > MAXIMUM_PROVIDERS || providers.values.any { it !is JsonObject }) {
            return emptyList()
        }
        val labels = sortedSetOf<String>()
        for (key in providers.keys) PROVIDER_LABELS[key]?.let(labels::add)
        for (field in listOf("model", "small_model")) {
            val model = (decoded[field] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: continue
            val slash = model.indexOf('/')
            if (slash <= 0 || slash == model.length - 1) continue
            PROVIDER_LABELS[model.substring(0, slash)]?.let(labels::add)
        }
        return labels.toList()
    } catch (_: Exception) {
        return emptyList()
    }
}

/** True only for a regular file reached through real directories from a real directory [root], no link anywhere. */
internal fun regularFileInsideExport(root: Path, relative: String): Boolean {
    val rootText = root.toString()
    if (rootText.split('/').any { it == "." || it == ".." } || !Files.isDirectory(root, *NO_FOLLOW)) return false
    val components = relative.split('/')
    if (components.isEmpty() || components.any { it.isEmpty() || it == "." || it == ".." }) return false
    var path = root
    for ((index, component) in components.withIndex()) {
        path = path.resolve(component)
        val last = index == components.lastIndex
        val ok = if (last) Files.isRegularFile(path, *NO_FOLLOW) else Files.isDirectory(path, *NO_FOLLOW)
        if (!ok) return false
    }
    return true
}

private fun withoutComments(text: String): String? {
    val result = StringBuilder()
    var quoted = false
    var escaped = false
    var depth = 0
    var i = 0
    while (i < text.length) {
        val character = text[i]
        if (quoted) {
            result.append(character)
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                quoted = false
            }
            i++
            continue
        }
        if (character == '"') {
            quoted = true
        } else if (character == '{' || character == '[') {
            if (++depth > MAXIMUM_DEPTH) return null
        } else if (character == '}' || character == ']') {
            if (--depth < 0) return null
        } else if (character == '/' && i + 1 < text.length) {
            if (text[i + 1] == '/') {
                result.append(' ')
                i += 2
                while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
                if (i < text.length) result.append(text[i])
                i++
                continue
            }
            if (text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2)
                if (end < 0) return null
                result.append(' ')
                i = end + 2
                continue
            }
        }
        result.append(character)
        i++
    }
    return if (quoted || depth != 0) null else result.toString()
}

private fun withoutTrailingCommas(text: String): String {
    val result = StringBuilder()
    var quoted = false
    var escaped = false
    for (i in text.indices) {
        val character = text[i]
        if (quoted) {
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == '"') {
                quoted = false
            }
        } else if (character == '"') {
            quoted = true
        } else if (character == ',') {
            var next = i + 1
            while (next < text.length && text[next] in " \t\r\n") next++
            if (next < text.length && (text[next] == '}' || text[next] == ']')) continue
        }
        result.append(character)
    }
    return result.toString()
}
