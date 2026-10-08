package android.content.res

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import io.github.eslamasabry.opencode_mobile.ResourceNames

class LocaleList(private val values: List<Locale>) {
    operator fun get(index: Int): Locale = values[index]
    fun size(): Int = values.size
    fun toList(): List<Locale> = values.toList()
}

class Configuration {
    var locales: LocaleList
    constructor(locale: Locale) { locales = LocaleList(listOf(locale)) }
    constructor(values: List<Locale>) { locales = LocaleList(values) }
    constructor(other: Configuration) { locales = LocaleList(other.locales.toList()) }
    fun setLocale(locale: Locale) { locales = LocaleList(listOf(locale)) }
}

// Android's Context/Resources boundary is stubbed, while the resource values
// under test come from the actual committed XML (not a duplicate dictionary).
class Resources(val configuration: Configuration) {
    private fun resource(id: Int): Element {
        val folder = if (configuration.locales[0].language == "ar") "values-ar" else "values"
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("android/app/src/main/res/$folder/strings.xml"))
        val children = document.documentElement.childNodes
        for (index in 0 until children.length) {
            val element = children.item(index) as? Element ?: continue
            if (element.getAttribute("name") == ResourceNames.names[id]) return element
        }
        error("Resource missing: ${ResourceNames.names[id]}")
    }
    private fun format(value: String, args: Array<out Any>): String =
        String.format(configuration.locales[0], value.trim().replace("\\'", "'"), *args)
    fun getString(id: Int, vararg args: Any): String = format(resource(id).textContent, args)
    fun getQuantityString(id: Int, count: Int, vararg args: Any): String {
        val category = if (configuration.locales[0].language == "ar") when {
            count == 0 -> "zero"
            count == 1 -> "one"
            count == 2 -> "two"
            count % 100 in 3..10 -> "few"
            count % 100 in 11..99 -> "many"
            else -> "other"
        } else if (count == 1) "one" else "other"
        val items = resource(id).getElementsByTagName("item")
        val values = (0 until items.length).map { items.item(it) as Element }
        val selected = values.firstOrNull { it.getAttribute("quantity") == category }
            ?: values.single { it.getAttribute("quantity") == "other" }
        return format(selected.textContent, args)
    }
}
