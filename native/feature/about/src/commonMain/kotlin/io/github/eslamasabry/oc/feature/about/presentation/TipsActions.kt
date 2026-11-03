package io.github.eslamasabry.oc.feature.about.presentation

/** What Help › Tips can do. Each names its target. */
public interface TipsActions {
    public fun onBack()

    /** Show me on the row of [id]: a tip replays in place; a tutorial opens its screen and runs there. */
    public fun onShowMe(id: String)

    /** The replayed tip faded (by itself or by a touch). */
    public fun onReplayDismissed(id: String)

    /** Puts every tip, tutorial and one-time card back. */
    public fun onShowTipsAgain()
}
