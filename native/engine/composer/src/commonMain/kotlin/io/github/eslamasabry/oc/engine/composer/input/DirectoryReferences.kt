package io.github.eslamasabry.oc.engine.composer.input

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.model.PromptAttachment

/** A reference to a folder on the server. This never checks or opens the phone's filesystem. */
public object DirectoryReferences {
    public fun reference(path: String): Outcome<PromptAttachment> {
        val windows = Regex("^[A-Za-z]:[\\\\/]").containsMatchIn(path)
        val unc = path.startsWith("\\\\")
        if (
            path.isBlank() ||
                path.any { it.code < 0x20 || it.code in 0x7f..0x9f } ||
                (!path.startsWith('/') && !windows && !unc)
        )
            return Failure.Refused(RefusalReason.InvalidAddress).failed()
        val forward = if (windows || unc) path.replace('\\', '/') else path
        val prefix =
            when {
                unc -> "//"
                windows -> forward.take(2) + "/"
                else -> "/"
            }
        val start = if (windows) 3 else if (unc) 2 else 1
        val parts = ArrayList<String>()
        for (part in forward.drop(start).split('/')) when (part) {
            "",
            "." -> Unit
            ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
        val normalized = prefix + parts.joinToString("/")
        val encoded = buildString {
            for (byte in normalized.encodeToByteArray()) {
                val c = byte.toInt() and 0xff
                val ch = c.toChar()
                if (c < 128 && (ch.isLetterOrDigit() || ch in "-._~!$&'()*+,;=:@/")) append(ch)
                else append('%').append(HEX[c shr 4]).append(HEX[c and 15])
            }
        }
        val url =
            if (windows) "file:///$encoded" else if (unc) "file:$encoded" else "file://$encoded"
        return PromptAttachment("application/x-directory", parts.lastOrNull() ?: normalized, url)
            .ok()
    }

    private const val HEX = "0123456789ABCDEF"
}
