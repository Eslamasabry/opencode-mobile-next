package io.github.eslamasabry.opencode_mobile

import android.content.ComponentCallbacks2

/** Pure decisions for the on-device assistant (oc/local_llm); no Android or model types, so JVM tests cover them. */
object LocalLlmPolicy {
    /** The context window handed to the engine: prompt plus reply. Larger windows cost KV-cache memory. */
    const val CONTEXT_TOKENS = 4096
    const val DEFAULT_MAX_TOKENS = 384
    const val MAX_TOKENS_LIMIT = 1024
    const val MAX_PROMPT_CHARS = 24_000

    /** Backends to try, best first. A request for the CPU never tries the GPU; a GPU request falls back to the CPU. */
    fun backendOrder(requested: String?): List<String> = when (requested) {
        "cpu" -> listOf("cpu")
        else -> listOf("gpu", "cpu")
    }

    fun clampMaxTokens(requested: Int?): Int =
        (requested ?: DEFAULT_MAX_TOKENS).coerceIn(16, MAX_TOKENS_LIMIT)

    /**
     * Whether a trim-memory signal should release the model. Backgrounding alone must not stop a
     * summary the person is waiting for; only real pressure does while one runs.
     */
    fun unloadOnTrim(level: Int, generating: Boolean): Boolean = when {
        level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> true
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> true
        generating -> false
        level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> true
        else -> false
    }

    /** True for a path inside the app's private files; the engine never opens anything else. */
    fun isInsideAppStorage(path: String, roots: List<String>): Boolean {
        val normalized = java.io.File(path).normalize().path
        return roots.any { root ->
            val base = java.io.File(root).normalize().path
            normalized.startsWith(base + java.io.File.separator)
        }
    }
}
