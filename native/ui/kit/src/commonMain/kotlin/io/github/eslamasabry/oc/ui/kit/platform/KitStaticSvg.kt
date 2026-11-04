package io.github.eslamasabry.oc.ui.kit.platform

/**
 * A provider mark is a bounded picture. This second check protects the renderer even when an older
 * cache predates the engine's current validation. References, DTDs, event handlers and CSS resource
 * loading never reach a decoder.
 */
internal fun isStaticKitSvg(text: String): Boolean {
    if (text.isEmpty() || text.length > 64 * 1024 || text.encodeToByteArray().size > 64 * 1024)
        return false
    val lower = text.lowercase()
    if (!SVG_ROOT.containsMatchIn(lower)) return false
    if (
        FORBIDDEN_MARKUP.containsMatchIn(lower) ||
            HANDLER.containsMatchIn(lower) ||
            HREF.containsMatchIn(lower)
    )
        return false
    if ('\\' in lower || "@import" in lower || "javascript:" in lower || "data:" in lower)
        return false
    // Only local paint/clip references (url(#paint)) are meaningful in a standalone picture.
    for (reference in RESOURCE.findAll(lower)) {
        val target = reference.groupValues[1].trim().removeSurrounding("'").removeSurrounding("\"")
        if (!LOCAL_REFERENCE.matches(target)) return false
    }
    return true
}

private val SVG_ROOT = Regex("""<svg(?:\s|>)""")
private val FORBIDDEN_MARKUP =
    Regex(
        """<!|<\s*(?:[\w.-]+:)?(?:script|foreignobject|image|use|iframe|object|embed|animate\w*|set|style)(?:\s|/|>)"""
    )
private val HANDLER = Regex("""(?:\s|:)on[\w.-]+\s*=""")
private val HREF = Regex("""(?:\s|:)href\s*=""")
private val RESOURCE = Regex("""url\s*\(([^)]*)\)""")
private val LOCAL_REFERENCE = Regex("""#[a-z_][a-z0-9_.:-]*""")
