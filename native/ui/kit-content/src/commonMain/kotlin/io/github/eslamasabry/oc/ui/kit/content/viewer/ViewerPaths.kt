package io.github.eslamasabry.oc.ui.kit.content.viewer

/** The viewer header's second line (B-NAV-028; Dart `KitViewer.folderOf`). Pure. */
public object ViewerPaths {
    private val leadingSeparators = Regex("^[\\s·:,-]+")

    /**
     * The parent folder of [path] with its trailing slash, plus anything that follows the name ("src/ · Line 12").
     * `null` for a file at the root, so the name never shows twice. A [path] that does not end in [name] is a caption
     * and shows as it is ("MIT License").
     */
    public fun folderOf(path: String, name: String): String? {
        val slash = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
        val last = path.substring(slash + 1)
        if (name.isEmpty() || !last.startsWith(name)) return path.ifEmpty { null }
        val folder = path.substring(0, slash + 1)
        var rest = last.substring(name.length).trim()
        if (folder.isEmpty()) {
            rest = rest.replaceFirst(leadingSeparators, "")
            return rest.ifEmpty { null }
        }
        return if (rest.isEmpty()) folder else "$folder $rest"
    }
}
