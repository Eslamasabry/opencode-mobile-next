package io.github.eslamasabry.oc.engine.project.terminal

/** A cell colour as the terminal was told it; the theme turns it into a real colour. */
public sealed interface TerminalColor {
    public data object Default : TerminalColor

    /** One of the 16 named colours: 0..7 normal, 8..15 bright. */
    public data class Named(val index: Int) : TerminalColor

    /** One of the 256 palette colours (`38;5;n`, `38:5:n`). */
    public data class Palette(val index: Int) : TerminalColor

    public data class Rgb(val red: Int, val green: Int, val blue: Int) : TerminalColor {
        /** 0xRRGGBB. */
        val packed: Int get() = (red and 0xff shl 16) or (green and 0xff shl 8) or (blue and 0xff)
    }
}

/** The drawing attributes of a cell. The underline colour (SGR 58/59) is read and dropped: it is not drawn. */
public data class TerminalStyle(
    val foreground: TerminalColor = TerminalColor.Default,
    val background: TerminalColor = TerminalColor.Default,
    val bold: Boolean = false,
    val faint: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val blink: Boolean = false,
    val inverse: Boolean = false,
    val invisible: Boolean = false,
    val strikethrough: Boolean = false,
) {
    public companion object {
        public val Plain: TerminalStyle = TerminalStyle()
    }
}

/**
 * The parameters of one CSI: [values] in order, and [subs], the indices of values that are colon sub-parameters of the
 * value before them (`4:3`, `38:2::r:g:b`). An empty parameter after a colon still counts as 0.
 */
public class CsiParams(public val values: List<Int>, public val subs: Set<Int>) {
    public companion object {
        public val Empty: CsiParams = CsiParams(emptyList(), emptySet())

        /** Parses the parameter bytes between `CSI` (and any prefix) and the final byte. */
        public fun parse(raw: String): CsiParams {
            val values = mutableListOf<Int>()
            val subs = mutableSetOf<Int>()
            var param = 0
            var hasParam = false
            var afterColon = false
            for (char in raw) {
                when (char) {
                    ':' -> {
                        values += param
                        param = 0
                        hasParam = false
                        afterColon = true
                        subs += values.size
                    }
                    ';' -> {
                        if (hasParam || afterColon) values += param
                        param = 0
                        hasParam = false
                        afterColon = false
                    }
                    in '0'..'9' -> {
                        hasParam = true
                        param = (param * 10 + (char - '0')).coerceAtMost(MAX_PARAM)
                    }
                    else -> Unit // intermediates
                }
            }
            if (hasParam || afterColon) values += param
            return CsiParams(values, subs)
        }

        private const val MAX_PARAM = 65_535
    }
}

/**
 * Select Graphic Rendition (`CSI … m`) with colon sub-parameters, as the app's patched xterm reads it: `4:0` ends an
 * underline and `4:1`..`4:5` start one (never SGR 40 or 43 backgrounds), `58` consumes its colour arguments in either
 * form so they never leak into other styles, and `38`/`48` take `2::r:g:b`, `2:r:g:b` and `5:n` as well as the
 * semicolon forms.
 */
public fun TerminalStyle.applySgr(params: CsiParams): TerminalStyle {
    val p = params.values
    if (p.isEmpty()) return TerminalStyle.Plain
    var style = this
    var i = 0
    fun sub(at: Int): Boolean = params.subs.contains(at) && at < p.size

    fun colonLength(at: Int): Int {
        var n = 0
        while (params.subs.contains(at + 1 + n) && at + 1 + n < p.size) n++
        return n
    }

    /** Returns how many values after [at] the colour used, and the colour (null when incomplete). */
    fun colour(at: Int): Pair<Int, TerminalColor?> {
        if (sub(at + 1)) {
            val n = colonLength(at)
            val mode = p[at + 1]
            val colour =
                when {
                    mode == 2 && n >= 4 -> TerminalColor.Rgb(p[at + n - 2], p[at + n - 1], p[at + n])
                    mode == 5 && n >= 2 -> TerminalColor.Palette(p[at + 2])
                    else -> null
                }
            return n to colour
        }
        return when (p.getOrNull(at + 1)) {
            2 ->
                if (at + 4 < p.size) 4 to TerminalColor.Rgb(p[at + 2], p[at + 3], p[at + 4])
                else (p.size - 1 - at) to null
            5 -> if (at + 2 < p.size) 2 to TerminalColor.Palette(p[at + 2]) else (p.size - 1 - at) to null
            else -> 0 to null
        }
    }
    while (i < p.size) {
        when (val param = p[i]) {
            0 -> style = TerminalStyle.Plain
            1 -> style = style.copy(bold = true)
            2 -> style = style.copy(faint = true)
            3 -> style = style.copy(italic = true)
            4 ->
                if (sub(i + 1)) {
                    style = style.copy(underline = p[i + 1] != 0)
                    i++
                } else {
                    style = style.copy(underline = true)
                }
            5 -> style = style.copy(blink = true)
            7 -> style = style.copy(inverse = true)
            8 -> style = style.copy(invisible = true)
            9 -> style = style.copy(strikethrough = true)
            21 -> style = style.copy(bold = false)
            22 -> style = style.copy(bold = false, faint = false)
            23 -> style = style.copy(italic = false)
            24 -> style = style.copy(underline = false)
            25 -> style = style.copy(blink = false)
            27 -> style = style.copy(inverse = false)
            28 -> style = style.copy(invisible = false)
            29 -> style = style.copy(strikethrough = false)
            in 30..37 -> style = style.copy(foreground = TerminalColor.Named(param - 30))
            38 -> {
                val (used, colour) = colour(i)
                if (colour != null) style = style.copy(foreground = colour)
                i += used
            }
            39 -> style = style.copy(foreground = TerminalColor.Default)
            in 40..47 -> style = style.copy(background = TerminalColor.Named(param - 40))
            48 -> {
                val (used, colour) = colour(i)
                if (colour != null) style = style.copy(background = colour)
                i += used
            }
            49 -> style = style.copy(background = TerminalColor.Default)
            // Underline colour: not drawn, but its arguments are consumed.
            58 -> i += colour(i).first
            59 -> Unit
            in 90..97 -> style = style.copy(foreground = TerminalColor.Named(param - 90 + 8))
            in 100..107 -> style = style.copy(background = TerminalColor.Named(param - 100 + 8))
            else -> Unit
        }
        i++
    }
    return style
}
