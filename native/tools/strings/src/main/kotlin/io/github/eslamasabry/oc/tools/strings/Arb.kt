package io.github.eslamasabry.oc.tools.strings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** Metadata for one placeholder, from the template's `@key.placeholders`. */
data class PlaceholderMeta(val type: String?, val format: String?)

/**
 * One parsed `app_<locale>.arb`.
 *
 * [messages] keeps the file's key order. [placeholders] holds `@key.placeholders` where the file has them
 * (in practice only the English template does).
 */
data class ArbFile(
    val locale: String,
    val messages: LinkedHashMap<String, String>,
    val placeholders: Map<String, LinkedHashMap<String, PlaceholderMeta>>,
    /** Argument order forced for a key (hand conversions: every select branch numbers its arguments alike). */
    val argOrder: Map<String, List<String>> = emptyMap(),
)

class ArbFormatException(message: String) : RuntimeException(message)

object Arb {
    private val fileName = Regex("""app_([A-Za-z]{2,3}(?:_[A-Za-z0-9]+)*)\.arb""")
    private val json = Json { isLenient = false }

    /** Every `app_<locale>.arb` in [dir], sorted with the template first, then by locale. */
    fun readDir(dir: Path, template: String = "en"): List<ArbFile> {
        val files = dir.listDirectoryEntries("app_*.arb").filter { fileName.matches(it.name) }
        if (files.isEmpty()) throw ArbFormatException("No app_<locale>.arb files in $dir")
        return files.map { read(it) }
            .sortedWith(compareBy<ArbFile>({ it.locale != template }, { it.locale }))
            .also { list ->
                if (list.first().locale != template) throw ArbFormatException("Template app_$template.arb is missing in $dir")
            }
    }

    fun read(file: Path): ArbFile {
        val m = fileName.matchEntire(file.name) ?: throw ArbFormatException("Not an ARB file name: ${file.name}")
        return parse(file.readText(), m.groupValues[1], file.toString())
    }

    /** Parses ARB [text]; [expectedLocale] is the locale from the file name, which `@@locale` must match. */
    fun parse(text: String, expectedLocale: String, source: String = "<arb>"): ArbFile {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ArbFormatException("$source is not a JSON object: ${e.message}")
        }
        val declared = (root["@@locale"] as? JsonPrimitive)?.contentOrNull
        if (declared != null && declared != expectedLocale) {
            throw ArbFormatException("$source declares @@locale \"$declared\" but its file name says \"$expectedLocale\"")
        }
        val messages = LinkedHashMap<String, String>()
        val placeholders = HashMap<String, LinkedHashMap<String, PlaceholderMeta>>()
        for ((key, value) in root) {
            when {
                key.startsWith("@@") -> Unit
                key.startsWith("@") -> {
                    val ph = (value as? JsonObject)?.get("placeholders") as? JsonObject ?: continue
                    placeholders[key.substring(1)] = LinkedHashMap<String, PlaceholderMeta>().apply {
                        for ((name, meta) in ph) {
                            val o = meta as? JsonObject
                            put(
                                name,
                                PlaceholderMeta(
                                    type = (o?.get("type") as? JsonPrimitive)?.contentOrNull,
                                    format = (o?.get("format") as? JsonPrimitive)?.contentOrNull,
                                ),
                            )
                        }
                    }
                }
                else -> {
                    val p = value as? JsonPrimitive
                    if (p == null || !p.isString) throw ArbFormatException("$source: message \"$key\" is not a string")
                    messages[key] = p.content
                }
            }
        }
        return ArbFile(expectedLocale, messages, placeholders)
    }
}
