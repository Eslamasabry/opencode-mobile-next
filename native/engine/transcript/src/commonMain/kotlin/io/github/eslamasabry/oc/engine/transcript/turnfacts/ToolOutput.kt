package io.github.eslamasabry.oc.engine.transcript.turnfacts

import io.github.eslamasabry.oc.core.model.transcript.ToolState
import io.github.eslamasabry.oc.core.model.transcript.ToolStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Port of Flutter lib/domain/models/models_messages.dart `ToolState.fromJson` text rules
// (`_pretty`, output /
// inputJson) and `_toolOutputFiles`, over the core ToolState the protocol adapters already built.

/** A file a tool call produced (Dart `ToolOutputFile`). */
public data class ToolArtifact(
    val path: String?,
    val url: String?,
    val mime: String?,
    val filename: String?,
) {
    /**
     * Dart `ToolOutputFile.displayName`: the explicit file name, else the last segment of the path
     * (or URL, without its query). Null where Dart says "Generated file" (copy belongs to the
     * feature).
     */
    public val displayName: String?
        get() {
            filename
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    return it
                }
            val location = if (path?.trim()?.isNotEmpty() == true) path else url ?: ""
            val withoutQuery = location.split('?').first()
            return withoutQuery.replace('\\', '/').split('/').lastOrNull { it.isNotEmpty() }
        }
}

/**
 * What a tool step's body shows, as text (B-CHAT-112/113/114).
 *
 * - [text]: Dart `ToolState.output` exactly: a finished or failed call's output with its leading
 *   and trailing whitespace kept, a JSON object or list pretty-printed with two-space indent; empty
 *   while pending or running.
 * - [errorText]: the same text when the call failed (Error), else null.
 * - [rawInput]: Dart `inputJson`: a pending call's streamed raw input unchanged, otherwise the
 *   structured input pretty-printed.
 * - [input] / [metadata]: the server's structured input and metadata, kept for renderers.
 * - [artifacts]: only explicit files (attachments, `filePath` outputs, a shell `outputPath`), never
 *   URLs or path-like prose; a `read` call's attachments take the requested file's name.
 */
public data class ToolOutputView(
    val text: String,
    val errorText: String?,
    val rawInput: String,
    val input: JsonObject,
    val metadata: JsonObject?,
    val artifacts: List<ToolArtifact>,
    val pruned: Boolean = false,
)

public fun toolOutputView(state: ToolState, toolName: String?): ToolOutputView {
    val text =
        when (state.status) {
            ToolStatus.Completed,
            ToolStatus.Error -> outputText(state)
            else -> ""
        }
    return ToolOutputView(
        text = text,
        errorText = if (state.status == ToolStatus.Error) text else null,
        rawInput =
            if (state.status == ToolStatus.Pending) state.inputRaw ?: ""
            else prettyJson(state.input),
        input = state.input,
        metadata = state.metadata,
        artifacts = toolArtifacts(state, toolName),
        pruned = state.pruned,
    )
}

/**
 * Dart `_pretty(structuredOutput)`: the structured value when the adapter kept one, else the output
 * text.
 */
private fun outputText(state: ToolState): String =
    when (val value = state.outputValue) {
        null -> state.output ?: ""
        is JsonObject,
        is JsonArray -> prettyJson(value)
        JsonNull -> ""
        is JsonPrimitive -> if (value.isString) value.content else value.content.trim()
    }

/** Dart `_toolOutputFiles`. */
private fun toolArtifacts(state: ToolState, toolName: String?): List<ToolArtifact> {
    val files = mutableListOf<ToolArtifact>()
    val locations = mutableSetOf<String>()

    fun add(path: String?, url: String?, mime: String?, filename: String?) {
        val normalizedPath = path?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedUrl = url?.trim()?.takeIf { it.isNotEmpty() }
        val location = normalizedPath ?: normalizedUrl ?: return
        if (!locations.add(location)) return
        files += ToolArtifact(normalizedPath, normalizedUrl, mime?.trim(), filename?.trim())
    }

    fun addCandidate(
        hasExplicitFilePath: Boolean,
        path: String?,
        url: String?,
        mime: String?,
        filename: String?,
        allowBareUrl: Boolean,
    ) {
        val hasFileSignal =
            hasExplicitFilePath ||
                mime?.isNotBlank() == true ||
                filename?.isNotBlank() == true ||
                (allowBareUrl && url?.isNotBlank() == true)
        if (
            (path?.isNotBlank() == true && hasExplicitFilePath) ||
                (url?.isNotBlank() == true && hasFileSignal) ||
                mime?.isNotBlank() == true
        ) {
            if (url?.startsWith("file://") == true && path == null) {
                add(filePathOf(url), null, mime, filename)
            } else {
                add(path, url, mime, filename)
            }
        }
    }

    fun scan(value: JsonElement?, allowBareUrl: Boolean, depth: Int) {
        if (value == null || value == JsonNull || depth > 6 || files.size >= 8) return
        when (value) {
            is JsonArray -> value.forEach { scan(it, allowBareUrl, depth + 1) }
            is JsonObject -> {
                val hasExplicitFilePath =
                    "filePath" in value || "filepath" in value || "file_path" in value
                val rawPath =
                    if (hasExplicitFilePath) {
                        nonNull(value["filePath"])
                            ?: nonNull(value["filepath"])
                            ?: nonNull(value["file_path"])
                    } else {
                        nonNull(value["path"])
                    }
                addCandidate(
                    hasExplicitFilePath,
                    path = rawPath?.let(::dartToString),
                    url = nonNull(value["url"])?.let(::dartToString),
                    mime =
                        (nonNull(value["mime"]) ?: nonNull(value["mimeType"]))?.let(::dartToString),
                    filename =
                        (nonNull(value["filename"]) ?: nonNull(value["name"]))?.let(::dartToString),
                    allowBareUrl = allowBareUrl,
                )
                value.values.forEach { scan(it, allowBareUrl, depth + 1) }
            }
            else -> Unit
        }
    }

    // Attachments (the adapter's outputFiles): a bare URL counts as a file here.
    for (file in state.outputFiles) {
        if (files.size >= 8) break
        addCandidate(false, file.path, file.url, file.mime, file.filename, true)
    }
    // A failed call's output is its error text, which Dart never scanned for files.
    if (state.status != ToolStatus.Error)
        scan(decodedOutput(state), allowBareUrl = false, depth = 0)

    val normalizedTool = toolName?.lowercase()
    if (normalizedTool == "read" && files.isNotEmpty()) {
        val sourcePath = nonNull(state.input["filePath"])?.let(::dartToString)
        if (sourcePath != null && sourcePath.isNotBlank()) {
            val sourceName = sourcePath.replace('\\', '/').split('/').last()
            for (index in files.indices) {
                val file = files[index]
                files[index] = file.copy(filename = file.filename ?: sourceName)
            }
        }
    }
    val outputPath = nonNull(state.metadata?.get("outputPath"))?.let(::dartToString)
    if (
        (normalizedTool == "bash" || normalizedTool == "shell") &&
            outputPath != null &&
            outputPath.isNotBlank()
    ) {
        add(outputPath, null, null, outputPath.split('/').last())
    }
    return files.toList()
}

/** Dart: a String output that looks like a JSON object or list is decoded before the file scan. */
private fun decodedOutput(state: ToolState): JsonElement? {
    state.outputValue?.let { if (it !is JsonPrimitive || !it.isString) return it }
    val output = (state.outputValue as? JsonPrimitive)?.content ?: state.output ?: return null
    val trimmed = output.trim()
    if (
        (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
            (trimmed.startsWith('[') && trimmed.endsWith(']'))
    ) {
        return try {
            Json.parseToJsonElement(trimmed)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    return null
}

private fun nonNull(value: JsonElement?): JsonElement? = value?.takeIf { it != JsonNull }

/** Dart `value.toString()` for a JSON value (a string's content; other values as JSON text). */
private fun dartToString(value: JsonElement): String =
    (value as? JsonPrimitive)?.content ?: value.toString()

/**
 * Dart `Uri.tryParse(url)?.toFilePath()` for a local `file:///…` URL; null for a host or anything
 * unreadable.
 */
private fun filePathOf(url: String): String? {
    val rest = url.removePrefix("file://")
    if (!rest.startsWith("/")) return null
    val path = rest.substringBefore('#').substringBefore('?')
    return percentDecode(path)
}

private fun percentDecode(text: String): String? {
    if ('%' !in text) return text
    val bytes = ByteArray(text.length * 4)
    var size = 0
    var i = 0
    while (i < text.length) {
        if (text[i] == '%') {
            if (i + 2 >= text.length) return null
            val byte = text.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
            bytes[size++] = byte.toByte()
            i += 3
        } else {
            var end = i
            while (end < text.length && text[end] != '%') end++
            for (b in text.substring(i, end).encodeToByteArray()) bytes[size++] = b
            i = end
        }
    }
    return bytes.decodeToString(0, size)
}

/**
 * Dart `JsonEncoder.withIndent(' ').convert(value)`: two-space indent, `"key": value`, empty
 * containers as `{}` and `[]`, strings escaped the way Dart's encoder escapes them.
 */
private fun prettyJson(value: JsonElement): String =
    StringBuilder().also { writePretty(it, value, 0) }.toString()

private fun writePretty(out: StringBuilder, value: JsonElement, depth: Int) {
    when (value) {
        is JsonObject -> {
            if (value.isEmpty()) {
                out.append("{}")
                return
            }
            out.append("{\n")
            var first = true
            for ((key, item) in value) {
                if (!first) out.append(",\n")
                first = false
                indent(out, depth + 1)
                writeString(out, key)
                out.append(": ")
                writePretty(out, item, depth + 1)
            }
            out.append('\n')
            indent(out, depth)
            out.append('}')
        }
        is JsonArray -> {
            if (value.isEmpty()) {
                out.append("[]")
                return
            }
            out.append("[\n")
            value.forEachIndexed { index, item ->
                if (index > 0) out.append(",\n")
                indent(out, depth + 1)
                writePretty(out, item, depth + 1)
            }
            out.append('\n')
            indent(out, depth)
            out.append(']')
        }
        is JsonNull -> out.append("null")
        is JsonPrimitive ->
            if (value.isString) writeString(out, value.content) else out.append(value.content)
    }
}

private fun indent(out: StringBuilder, depth: Int) {
    repeat(depth) { out.append("  ") }
}

private fun writeString(out: StringBuilder, text: String) {
    out.append('"')
    for (c in text) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\t' -> out.append("\\t")
            '\n' -> out.append("\\n")
            '\u000C' -> out.append("\\f")
            '\r' -> out.append("\\r")
            else ->
                if (c.code < 0x20) {
                    out.append("\\u00")
                    out.append(HEX[c.code shr 4])
                    out.append(HEX[c.code and 0xF])
                } else {
                    out.append(c)
                }
        }
    }
    out.append('"')
}

private const val HEX = "0123456789abcdef"
