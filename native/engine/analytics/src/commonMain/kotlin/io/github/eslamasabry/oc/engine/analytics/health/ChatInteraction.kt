package io.github.eslamasabry.oc.engine.analytics.health

/**
 * What the person is doing in the chat while frames are drawn, as one word of [HealthWords.interactions] for the `jank`
 * event (`interaction`, the chat only). The four signals come from the platform and are read per frame batch; nothing
 * is kept. In order of what wins when several hold: a menu or sheet open, the keyboard up, the finger scrolling (and the
 * moment after it lifts, while the list is still moving), a reply streaming, else idle.
 */
public class ChatInteraction(
    private val now: () -> Long,
    private val streaming: () -> Boolean,
    private val menuOpen: () -> Boolean,
    private val keyboardUp: () -> Boolean,
) {
    private var scrollUntilMs = Long.MIN_VALUE

    /** The finger is scrolling (moved past the touch slop), or has just lifted off a scroll. */
    public fun scrolled() {
        scrollUntilMs = now() + SCROLL_TAIL_MS
    }

    public fun word(): String =
        when {
            menuOpen() -> "menu"
            keyboardUp() -> "keyboard"
            now() < scrollUntilMs -> "scrolling"
            streaming() -> "streaming"
            else -> "idle"
        }

    public companion object {
        /** How long a scroll counts after the last movement: a fling keeps the list moving a moment. */
        public const val SCROLL_TAIL_MS: Long = 600L
    }
}
