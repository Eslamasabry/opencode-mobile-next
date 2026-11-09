package io.github.eslamasabry.oc.tools.strings

import java.io.File

/** One resource in a locale: a string, or plural forms by CLDR category. [raw] keeps the file text for escaping checks. */
data class Entry(val key: String, val forms: Map<String, String>, val raw: Map<String, String>, val file: String) {
    val isPlural get() = forms.keys != setOf("")
    fun texts(): Collection<String> = forms.values
}

/** The native resource folders the translation pipeline covers (mirrors ROOTS in translate/translate.py). */
class TranslationCatalog(private val nativeRoot: File) {
    enum class Kind { CMP, ANDROID }

    class Root(val name: String, val dir: File, val kind: Kind, val generated: Boolean)

    val roots: List<Root> =
        listOf(
            Triple("words", "ui/words/src/commonMain/composeResources", Kind.CMP),
            Triple("words-android", "ui/words/android-res", Kind.ANDROID),
            Triple("androidApp", "androidApp/src/main/res", Kind.ANDROID),
            Triple("notifications", "android/notifications/src/main/res", Kind.ANDROID),
            Triple("background", "android/background/src/main/res", Kind.ANDROID),
            Triple("linux", "android/linux/src/main/res", Kind.ANDROID),
            Triple("platform", "android/platform/src/main/res", Kind.ANDROID),
            Triple("schedules", "android/schedules/src/main/res", Kind.ANDROID),
            Triple("tiles", "android/tiles/src/main/res", Kind.ANDROID),
            Triple("widgets", "android/widgets/src/main/res", Kind.ANDROID),
        ).map { (n, path, k) -> Root(n, File(nativeRoot, path), k, n == "words-android") }

    companion object {
        private val ENTRY = Regex("""<(string|plurals)\b([^>]*?)>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
        private val ITEM = Regex("""<item\s+quantity="(\w+)"\s*>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
        private val NAME = Regex("""name="([^"]+)"""")

        /** Language tag of a values folder (`values-in` is Indonesian: `id`), or null for `values`/qualified ones. */
        fun tagOf(folder: String): String? {
            val t = folder.removePrefix("values-")
            if (folder == "values" || !Regex("[a-z]{2,3}").matches(t)) return null
            return if (t == "in") "id" else t
        }

        fun unescapeXml(s: String) =
            s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

        fun plain(raw: String) = XmlText.cmpUnescape(unescapeXml(raw))
    }

    /** Resource entries of one locale folder of [root], keyed by name. */
    fun read(root: Root, tag: String): Map<String, Entry> {
        val folder = if (tag == "en") "values" else File(root.dir, "values-$tag").takeIf { it.isDirectory }?.name
            ?: File(root.dir, "values-in").takeIf { tag == "id" && it.isDirectory }?.name ?: return emptyMap()
        val out = linkedMapOf<String, Entry>()
        File(root.dir, folder).listFiles().orEmpty().filter { it.extension == "xml" }.sortedBy { it.name }.forEach { f ->
            for (m in ENTRY.findAll(f.readText())) {
                val name = NAME.find(m.groupValues[2])?.groupValues?.get(1) ?: continue
                if (m.groupValues[1] == "string") {
                    out[name] = Entry(name, mapOf("" to plain(m.groupValues[3])), mapOf("" to m.groupValues[3]), f.name)
                } else {
                    val items = ITEM.findAll(m.groupValues[3]).toList()
                    out[name] = Entry(name, items.associate { it.groupValues[1] to plain(it.groupValues[2]) }, items.associate { it.groupValues[1] to it.groupValues[2] }, f.name)
                }
            }
        }
        return out
    }

    /** Tags of every language folder in any root, without English. */
    fun locales(): List<String> =
        roots.flatMap { r -> r.dir.listFiles().orEmpty().mapNotNull { tagOf(it.name) } }.toSortedSet().toList()
}
