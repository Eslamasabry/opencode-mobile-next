package io.github.eslamasabry.oc.engine.media.preview

import io.github.eslamasabry.oc.engine.media.xml.XmlEvent
import io.github.eslamasabry.oc.engine.media.xml.XmlLimits
import io.github.eslamasabry.oc.engine.media.xml.XmlPull
import io.github.eslamasabry.oc.engine.media.xml.XmlRefused

/**
 * A deliberately small static-SVG contract (port of `lib/domain/static_svg.dart`, B-CHAT-107). A file that passes
 * renders as a picture; anything active, external, malformed, declaration-bearing, oversized or dash-exploding is
 * rejected here, before any renderer sees it, and the viewer keeps Source and Save.
 */
public data class StaticSvg(val source: String, val width: Double, val height: Double) {
    public companion object {
        public const val MAX_BYTES: Int = 256 * 1024
        private const val MAX_ELEMENTS = 1000
        private const val MAX_DEPTH = 32
        private const val MAX_ATTRIBUTE = 16_384
        private const val MAX_NUMBER = 1_000_000.0
        private const val MAX_EDGE = 16_384.0
        private const val SVG_NS = "http://www.w3.org/2000/svg"

        private val ELEMENTS =
            setOf(
                "svg", "g", "defs", "title", "desc", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
                "text", "tspan", "linearGradient", "radialGradient", "stop",
            )

        // No stroke-dasharray / stroke-dashoffset: dashing can expand a short path into unbounded geometry.
        private val ATTRIBUTES =
            setOf(
                "id", "viewBox", "width", "height", "x", "y", "x1", "y1", "x2", "y2", "cx", "cy", "r", "rx", "ry", "fx",
                "fy", "d", "points", "transform", "fill", "fill-rule", "fill-opacity", "stroke", "stroke-width",
                "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-opacity", "opacity", "font-family",
                "font-size", "font-weight", "font-style", "text-anchor", "dx", "dy", "offset", "stop-color",
                "stop-opacity", "gradientUnits", "gradientTransform", "spreadMethod", "preserveAspectRatio", "version",
                "style",
            )
        private val NUMERIC =
            setOf(
                "width", "height", "x", "y", "x1", "x2", "y1", "y2", "cx", "cy", "r", "rx", "ry", "fx", "fy", "d",
                "points", "transform", "gradientTransform", "stroke-width", "font-size", "dx", "dy",
            )
        private val DECLARATION = Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE)
        private val NUMBER = Regex("[-+]?(?:\\d*\\.\\d+|\\d+\\.?\\d*)(?:[eE][-+]?\\d+)?")
        private val NON_FINITE = Regex("NaN|Infinity", RegexOption.IGNORE_CASE)
        private val URL = Regex("url\\s*\\(", RegexOption.IGNORE_CASE)
        private val LOCAL_URL = Regex("^url\\(\\s*#([A-Za-z_][\\w.-]*)\\s*\\)$")
        private val SEPARATORS = Regex("[\\s,]+")

        /** The checked SVG, or null (the caller shows Source). */
        public fun parse(source: String): StaticSvg? {
            if (source.length > MAX_BYTES || StrictUtf8.encodedLength(source) > MAX_BYTES || DECLARATION.containsMatchIn(source)) {
                return null
            }
            return try {
                check(source)
            } catch (_: XmlRefused) {
                null
            }
        }

        private fun check(source: String): StaticSvg? {
            val parser = XmlPull(source, XmlLimits(maxDepth = MAX_DEPTH, maxElements = MAX_ELEMENTS))
            val gradients = HashSet<String>()
            val ids = HashSet<String>()
            val references = ArrayList<String>()
            var root: XmlEvent.Start? = null
            val gradientDepths = ArrayList<Int>() // depth of each open gradient, to check its children are stops
            while (true) {
                val e = parser.next() ?: break
                when (e) {
                    is XmlEvent.Instruction -> if (e.target != "xml") return null
                    is XmlEvent.Text -> Unit
                    is XmlEvent.End -> if (gradientDepths.lastOrNull() == parser.depth + 1) gradientDepths.removeAt(gradientDepths.lastIndex)
                    is XmlEvent.Start -> {
                        if (root == null) {
                            if (e.name.local != "svg") return null
                            root = e
                        }
                        val local = e.name.local
                        if (local !in ELEMENTS || (e.name.namespace != null && e.name.namespace != SVG_NS)) return null
                        if (e.name.prefix != null) return null
                        val depth = if (e.selfClosing) parser.depth + 1 else parser.depth
                        if (gradientDepths.lastOrNull() == depth - 1 && local != "stop") return null
                        val id = e.attr("id")
                        if (id != null && !ids.add(id)) return null
                        if (local.endsWith("Gradient")) {
                            if (id != null) gradients += id
                            if (!e.selfClosing) gradientDepths += depth
                        }
                        for (attr in e.attributes) {
                            if (attr.name.qualified == "xmlns" || attr.name.prefix == "xmlns") continue
                            if (attr.name.prefix != null || attr.name.local !in ATTRIBUTES) return null
                            val name = attr.name.local
                            val value = attr.value
                            if (value.length > MAX_ATTRIBUTE) return null
                            if (name == "style") {
                                for (declaration in value.split(';')) {
                                    if (declaration.isBlank()) continue
                                    val colon = declaration.indexOf(':')
                                    if (colon < 1) return null
                                    val property = declaration.substring(0, colon).trim()
                                    if (property !in ATTRIBUTES || property == "style" ||
                                        !safeValue(property, declaration.substring(colon + 1).trim(), references)
                                    ) {
                                        return null
                                    }
                                }
                            } else if (!safeValue(name, value, references)) {
                                return null
                            }
                        }
                    }
                }
            }
            val svg = root ?: return null
            if (references.any { it !in gradients }) return null
            val width: Double?
            val height: Double?
            val viewBox = svg.attr("viewBox")?.trim()?.split(SEPARATORS)
            if (viewBox != null) {
                val numbers = viewBox.map { it.toDoubleOrNull() }
                if (numbers.size != 4 || numbers.any { it == null || !it.isFinite() || kotlin.math.abs(it) > MAX_NUMBER }) {
                    return null
                }
                width = numbers[2]
                height = numbers[3]
            } else {
                width = (svg.attr("width") ?: "300").removeSuffix("px").toDoubleOrNull()
                height = (svg.attr("height") ?: "150").removeSuffix("px").toDoubleOrNull()
            }
            if (width == null || height == null || !width.isFinite() || !height.isFinite() ||
                width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE
            ) {
                return null
            }
            return StaticSvg(source, width, height)
        }

        private fun safeValue(name: String, value: String, references: MutableList<String>): Boolean {
            // No CSS escapes, imports or comments; no URL syntax except a local gradient paint.
            if ('\\' in value || '@' in value || "/*" in value) return false
            if (name in NUMERIC) {
                for (match in NUMBER.findAll(value)) {
                    val number = match.value.toDoubleOrNull() ?: return false
                    if (!number.isFinite() || kotlin.math.abs(number) > MAX_NUMBER) return false
                }
                if (NON_FINITE.containsMatchIn(value)) return false
            }
            if (URL.containsMatchIn(value)) {
                val match = LOCAL_URL.find(value)
                if ((name != "fill" && name != "stroke") || match == null) return false
                references += match.groupValues[1]
            }
            return true
        }
    }
}
