package io.github.eslamasabry.oc.core.common.terminal

/** Theme values cross the native-view seam without importing Compose or the kit. */
public data class TerminalPalette(
    val ansi: List<Int>,
    val foreground: Int,
    val background: Int,
    val cursor: Int,
)

/** Implemented by the platform terminal host, read only by the kit's platform slot. */
public interface TerminalSurface {
    public val columns: Int

    public fun appearance(palette: TerminalPalette)

    public fun links(onOpen: ((String) -> Unit)?)

    public fun selectedText(): String
}
