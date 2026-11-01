package io.github.eslamasabry.oc.ui.design.layout

/** Window classes, measured on the window, never the device (plan 06 §10.1; Dart `KitWindow`). */
public enum class WindowClass {
    Compact,
    Medium,
    Expanded,
    Large,
    ;

    public val isWide: Boolean get() = this >= Expanded

    public companion object {
        /** Dart `KitLayout.windowFor`: 600 / 840 / 1200. */
        public fun of(widthDp: Float): WindowClass =
            when {
                widthDp >= Layout.LARGE_FROM -> Large
                widthDp >= Layout.EXPANDED_FROM -> Expanded
                widthDp >= Layout.MEDIUM_FROM -> Medium
                else -> Compact
            }

        /** Phone landscape and other short windows: height under 480. */
        public fun isShort(heightDp: Float): Boolean = heightDp < Layout.SHORT_HEIGHT

        /** Modal parts stay at Medium behaviour on a wide but short window (Dart `modalWindowOf`). */
        public fun modal(widthDp: Float, heightDp: Float): WindowClass {
            val w = of(widthDp)
            return if (w.isWide && isShort(heightDp)) Medium else w
        }
    }
}
