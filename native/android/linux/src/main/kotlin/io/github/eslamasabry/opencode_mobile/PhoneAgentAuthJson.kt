package io.github.eslamasabry.opencode_mobile

import org.json.JSONObject

/** Accept only one flat JSON string object, including unique keys. */
internal class PhoneAgentAuthJson(private val text: String) {
    private var index = 0
    private val jsonString = Regex("\"(?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*\"")

    fun parse(): Map<String, String> {
        check(take('{'))
        val values = mutableMapOf<String, String>()
        if (!take('}')) {
            do {
                val key = string()
                check(key !in values && take(':'))
                values[key] = string()
            } while (take(','))
            check(take('}'))
        }
        skip()
        check(index == text.length)
        return values
    }

    private fun skip() { while (index < text.length && text[index] in " \t\r\n") index++ }

    private fun take(symbol: Char): Boolean {
        skip()
        val matches = text.getOrNull(index) == symbol
        if (matches) index++
        return matches
    }

    private fun string(): String {
        skip()
        val token = checkNotNull(jsonString.find(text, index)?.takeIf { it.range.first == index })
        index = token.range.last + 1
        return JSONObject("{\"value\":${token.value}}").getString("value")
    }
}
