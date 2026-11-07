package io.github.eslamasabry.oc.feature.settings.presentation

import io.github.eslamasabry.oc.core.model.ModelRef

/**
 * P-020: the new-conversation model as a person reads it (Flutter `serverModelLabel` / `readableModelLabel`): the
 * catalog's own names while they are loaded, the tidied ids otherwise, never "opencode / crouton-alpha-free".
 */
public fun serverModelLabel(
    model: ModelRef,
    variant: String,
    choices: List<ServerModelChoice>,
): String {
    val choice = choices.firstOrNull { it.model == model }
    val name = choice?.name?.trim()?.takeIf { it.isNotEmpty() } ?: tidyModelId(model.modelId)
    val provider =
        choice?.provider?.trim()?.takeIf { it.isNotEmpty() }
            ?: KNOWN_PROVIDERS[model.providerId.lowercase()]
            ?: model.providerId.replaceFirstChar { it.uppercaseChar() }
    val named = if (variant.isBlank()) name else "$name · $variant"
    return if (provider.isBlank()) named else "$named · $provider"
}

private val KNOWN_PROVIDERS =
    mapOf(
        "openai" to "OpenAI",
        "anthropic" to "Anthropic",
        "google" to "Google",
        "openrouter" to "OpenRouter",
        "xai" to "xAI",
        "mistral" to "Mistral",
        "deepseek" to "DeepSeek",
        "github-copilot" to "GitHub Copilot",
        "opencode" to "OpenCode",
    )

private val NUMERIC = Regex("^\\d+(\\.\\d+)*$")
private val ACRONYMS = setOf("gpt", "glm", "llm")

/** "gpt-6.1-sol" as "GPT-6.1 Sol", "claude-opus-4-5" as "Claude Opus 4.5" (Flutter `_tidyModelId`). */
internal fun tidyModelId(modelId: String): String {
    val id = modelId.substringAfterLast('/')
    val tokens = id.split('-', '_').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return modelId
    val words = mutableListOf<String>()
    for (token in tokens) {
        when {
            NUMERIC.matches(token) ->
                if (words.isNotEmpty() && NUMERIC.matches(words.last()) && token.length < 8)
                    words[words.lastIndex] = words.last() + "." + token
                else words.add(token)
            token.lowercase() in ACRONYMS -> words.add(token.uppercase())
            else -> words.add(token.replaceFirstChar { it.uppercaseChar() })
        }
    }
    if (words.size > 1 && words[0] == words[0].uppercase() && NUMERIC.matches(words[1]))
        return (listOf(words[0] + "-" + words[1]) + words.drop(2)).joinToString(" ")
    return words.joinToString(" ")
}
