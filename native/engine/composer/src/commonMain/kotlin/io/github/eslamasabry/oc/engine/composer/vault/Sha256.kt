package io.github.eslamasabry.oc.engine.composer.vault

import io.github.eslamasabry.oc.core.common.dart.DartJson

/**
 * SHA-256, lowercase hex (Dart `sha256.convert(bytes).toString()`), delegating to `:core:common`'s one copy
 * ([DartJson.sha256Hex], INTEGRATE-W1 D15). Text is hashed as Dart `utf8.encode` bytes.
 */
internal object Sha256 {
    fun hex(bytes: ByteArray): String = DartJson.sha256Hex(bytes)

    fun hex(text: String): String = DartJson.sha256Hex(text)
}
