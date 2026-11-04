package io.github.eslamasabry.oc.ui.design.color

/** The owner-approved Signal instruments palette, copied from Flutter theme_packs_signal.dart. */
public data class SignalPalette(
    val ground: Argb, val slip: Argb, val slipEdge: Argb, val rule: Argb, val track: Argb,
    val siding: Argb, val head: Argb, val lampOff: Argb, val lcd: Argb, val lcdGhost: Argb,
    val lcdBezel: Argb, val key: Argb, val keyEdge: Argb, val stopKey: Argb, val stopEdge: Argb,
    val alertInk: Argb, val inkKey: Argb, val inkKeyEdge: Argb, val inkKeyWords: Argb,
) {
    public companion object {
        public val Light: SignalPalette = SignalPalette(
            argb(0xFFEFE9DA), argb(0xFFFBF9F2), argb(0xFFCDC3A9), argb(0xFFC9BFA6), argb(0xFF191815),
            argb(0xFF6F6858), argb(0xFF15140F), argb(0xFF4A4740), argb(0xFF1B1D1A), argb(0xFF2C302A),
            argb(0x00000000), argb(0xFFF7F3E8), argb(0xFF8F887A), argb(0xFFB3261E), argb(0xFF6E130E),
            argb(0xFFA8231C), argb(0xFF22211D), argb(0xFF000000), argb(0xFFF4EFE2),
        )
        public val Night: SignalPalette = SignalPalette(
            argb(0xFF12110E), argb(0xFF221F19), argb(0xFF3F3A30), argb(0xFF2E2B24), argb(0xFF8E8673),
            argb(0xFF6A6353), argb(0xFF000000), argb(0xFF3A372F), argb(0xFF070806), argb(0xFF1A1D18),
            argb(0xFF34302A), argb(0xFF26241F), argb(0xFF0A0908), argb(0xFFC0332A), argb(0xFF5A0F0A),
            argb(0xFFFF8A7F), argb(0xFF3A372F), argb(0xFF000000), argb(0xFFF4EFE2),
        )
        public val LampRed: Argb = argb(0xFFFF3B2F)
        public val LampGreen: Argb = argb(0xFF3BE070)
        public val LcdOn: Argb = argb(0xFF9FE870)
        public fun of(dark: Boolean): SignalPalette = if (dark) Night else Light
    }
}
