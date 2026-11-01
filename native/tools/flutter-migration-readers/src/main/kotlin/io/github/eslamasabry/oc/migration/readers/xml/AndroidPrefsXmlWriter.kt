package io.github.eslamasabry.oc.migration.readers.xml

/**
 * Writes a SharedPreferences map the way `XmlUtils.writeMapXml` + `FastXmlSerializer` lay it out on Android
 * (`<?xml version='1.0' encoding='utf-8' standalone='yes' ?>`, four-space indent, `&#N;` for control
 * characters). Used by the fixture generators; production code on Android writes through
 * `SharedPreferences.Editor.commit()` instead.
 */
public object AndroidPrefsXmlWriter {

    public fun write(entries: Map<String, RawPref>): ByteArray {
        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n")
        if (entries.isEmpty()) {
            sb.append("<map />\n")
            return sb.toString().toByteArray(Charsets.UTF_8)
        }
        sb.append("<map>\n")
        for ((name, value) in entries) {
            sb.append("    ")
            val n = escape(name)
            when (value) {
                is RawPref.Str -> sb.append("<string name=\"").append(n).append("\">").append(escape(value.value)).append("</string>")
                is RawPref.Bool -> sb.append("<boolean name=\"").append(n).append("\" value=\"").append(value.value).append("\" />")
                is RawPref.Int32 -> sb.append("<int name=\"").append(n).append("\" value=\"").append(value.value).append("\" />")
                is RawPref.Int64 -> sb.append("<long name=\"").append(n).append("\" value=\"").append(value.value).append("\" />")
                is RawPref.F32 -> sb.append("<float name=\"").append(n).append("\" value=\"").append(value.value.toString()).append("\" />")
                is RawPref.F64 -> sb.append("<double name=\"").append(n).append("\" value=\"").append(value.value.toString()).append("\" />")
                is RawPref.StrSet -> {
                    sb.append("<set name=\"").append(n).append("\">\n")
                    for (s in value.value) sb.append("        <string>").append(escape(s)).append("</string>\n")
                    sb.append("    </set>")
                }
                RawPref.Null -> sb.append("<null name=\"").append(n).append("\" />")
                is RawPref.Other -> sb.append(value.rawXml)
            }
            sb.append('\n')
        }
        sb.append("</map>\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** FastXmlSerializer's escape table: `&#0;`…`&#31;`, `&quot;`, `&amp;`, `&lt;`, `&gt;`. */
    public fun escape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (c in text) {
            when {
                c.code < 32 -> sb.append("&#").append(c.code).append(';')
                c == '"' -> sb.append("&quot;")
                c == '&' -> sb.append("&amp;")
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
