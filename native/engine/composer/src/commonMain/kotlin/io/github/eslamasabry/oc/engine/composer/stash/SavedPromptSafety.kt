package io.github.eslamasabry.oc.engine.composer.stash

import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.composer.store.StoredFormatException
import io.github.eslamasabry.oc.engine.composer.vault.schemeOf
import kotlin.io.encoding.Base64

/**
 * Port of Flutter saved_prompt_safety.dart: sanitize before either metadata or payload publication.
 */
internal class SavedPromptSafety(private val redactor: Redactor) {
    fun sanitize(profile: String, prompt: StashedPrompt): StashedPrompt {
        identity(profile)
        return prompt.copy(
            id = identity(prompt.id),
            text = redactor.redact(prompt.text),
            directory = prompt.directory?.let(::identity),
            workspace = prompt.workspace?.let(::identity),
            attachments =
                prompt.attachments.map {
                    it.copy(
                        filename = redactor.redact(it.filename),
                        mime = redactor.redact(it.mime),
                        url = safeUrl(it.url),
                    )
                },
            references =
                prompt.references.map {
                    it.copy(
                        id = identity(it.id),
                        path = redactor.redact(it.path),
                        lineLabel = it.lineLabel?.let(redactor::redact),
                        snippet = it.snippet?.let(redactor::redact),
                        comment = it.comment?.let(redactor::redact),
                        status = it.status?.let(redactor::redact),
                    )
                },
        )
    }

    /** Routing identities are refused rather than changed to point somewhere else. */
    private fun identity(value: String): String {
        if (redactor.containsSecret(value))
            throw StoredFormatException("Sensitive saved prompt identity")
        return value
    }

    private fun safeUrl(url: String): String {
        if (schemeOf(url) != "data") return identity(url)
        val comma = url.indexOf(',')
        if (comma < 0) throw StoredFormatException("Invalid saved text attachment")
        val header = url.substring(5, comma).split(';')
        val mime = identity(header.first()).lowercase().ifEmpty { "text/plain" }
        if (
            !mime.startsWith("text/") &&
                mime !in setOf("application/json", "application/xml", "application/javascript")
        ) {
            return identity(url) // Binary media is opaque and retains its exact bytes.
        }
        val text =
            try {
                val encoded = percentBytes(url.substring(comma + 1))
                val bytes =
                    if (header.drop(1).any { it.equals("base64", ignoreCase = true) }) {
                        Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                            .decode(encoded)
                    } else {
                        encoded
                    }
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (e: IllegalArgumentException) {
                throw StoredFormatException("Invalid saved text attachment")
            } catch (e: CharacterCodingException) {
                throw StoredFormatException("Invalid saved text attachment")
            }
        val safe = redactor.redact(text)
        // Clean attachments keep their representation; only changed text is normalized to UTF-8
        // base64.
        return if (safe == text) identity(url)
        else "data:$mime;charset=utf-8;base64,${Base64.encode(safe.encodeToByteArray())}"
    }

    /** RFC 2397 data bytes: percent escapes decode as bytes; a literal '+' stays a plus. */
    private fun percentBytes(value: String): ByteArray {
        val encoded = value.encodeToByteArray()
        if ('%' !in value) return encoded
        val decoded = ByteArray(encoded.size)
        var read = 0
        var written = 0
        while (read < encoded.size) {
            if (encoded[read] != '%'.code.toByte()) {
                decoded[written++] = encoded[read++]
                continue
            }
            if (read + 2 >= encoded.size) throw IllegalArgumentException("Invalid percent escape")
            val high =
                encoded[read + 1].toInt().toChar().digitToIntOrNull(16)
                    ?: throw IllegalArgumentException("Invalid percent escape")
            val low =
                encoded[read + 2].toInt().toChar().digitToIntOrNull(16)
                    ?: throw IllegalArgumentException("Invalid percent escape")
            decoded[written++] = ((high shl 4) or low).toByte()
            read += 3
        }
        return decoded.copyOf(written)
    }
}
