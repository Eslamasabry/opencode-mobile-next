package io.github.eslamasabry.oc.ui.kitchat.code

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The code colour roles (plan 09 §9.2). The kit maps each to a theme colour at draw time, so a theme change never
 * re-tokenises. [Plain] is never emitted by a tokenizer (uncoloured text has no token).
 */
public enum class CodeRole { Keyword, String, Comment, Number, Type, Function, Punctuation, Meta, Addition, Deletion, Plain }

/** One coloured range `[start, end)` of the tokenised text. Tokens are in order and never overlap. */
@Immutable
public data class CodeToken(val start: Int, val end: Int, val role: CodeRole)

/**
 * The swap seam for syntax colour (plan 09 §9.2): pure, total (never throws), safe off the main thread. The first
 * implementation is [SyntaxMpCodeTokenizer]; the SP-4 fallbacks (kotlin-textmate on JVM/Android, or plain text) are
 * other implementations of this interface, and nothing else in the kit changes.
 */
public fun interface CodeTokenizer {
    public fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken>
}

/** The last fallback: no colour at all (the chat still works). */
public object PlainCodeTokenizer : CodeTokenizer {
    override fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken> = persistentListOf()
}

/**
 * Plan 09 §9.2's cache and limits around any [CodeTokenizer]: plain for [CodeLanguage.Plain] and for sources over
 * [SIZE_LIMIT] characters; an LRU of [CACHE_SIZE] entries keyed by (language, source), independent of the theme. Port of
 * `KitCodeHighlight` (`kit_code_block.dart:108`). Safe from any thread.
 */
@OptIn(ExperimentalAtomicApi::class)
public class CachedCodeTokenizer(private val delegate: CodeTokenizer) : CodeTokenizer {
    /** Insertion-ordered persistent map: the first key is the least recently used. Swapped atomically (any thread). */
    private val cache = AtomicReference(persistentMapOf<Pair<CodeLanguage, String>, ImmutableList<CodeToken>>())

    override fun tokenize(code: String, language: CodeLanguage): ImmutableList<CodeToken> {
        if (language == CodeLanguage.Plain || code.isEmpty() || code.length > SIZE_LIMIT) return persistentListOf()
        val key = language to code
        val hit = cache.load()[key]
        val tokens = hit ?: delegate.tokenize(code, language)
        while (true) {
            val current = cache.load()
            var next = current.remove(key).put(key, tokens) // most recently used goes last
            while (next.size > CACHE_SIZE) next = next.remove(next.keys.first())
            if (cache.compareAndSet(current, next)) return tokens
        }
    }

    public companion object {
        /** Sources longer than this stay plain (`KitCodeHighlight.sizeLimit`). */
        public const val SIZE_LIMIT: Int = 20_000

        /** Token cache entries (`KitCodeHighlight._cacheLimit`). */
        public const val CACHE_SIZE: Int = 48
    }
}

/** The app's tokenizer. Defaults to SyntaxMP behind the cache; tests and the SP-4 fallbacks provide another. */
public val LocalKitCodeTokenizer: ProvidableCompositionLocal<CodeTokenizer> =
    staticCompositionLocalOf { DefaultCodeTokenizer }

private val DefaultCodeTokenizer: CodeTokenizer by lazy { CachedCodeTokenizer(SyntaxMpCodeTokenizer()) }
