package io.github.eslamasabry.oc.core.common.text

/**
 * Which shipped language the phone's language list picks (B-L10N-009). The list is tried in order; an entry matches a
 * shipped language by its language code, so a region (pt-PT) or script (zh-Hant) still finds it. An unshipped first
 * entry falls through to the next one, and a list with no shipped language reads English.
 */
public object AppLanguageResolver {
    public const val FALLBACK: String = "en"

    public fun resolve(deviceTags: List<String>, shipped: Set<String>): String {
        for (tag in deviceTags) {
            val code = tag.substringBefore('-').substringBefore('_').lowercase()
            if (code in shipped) return code
        }
        return FALLBACK
    }
}
