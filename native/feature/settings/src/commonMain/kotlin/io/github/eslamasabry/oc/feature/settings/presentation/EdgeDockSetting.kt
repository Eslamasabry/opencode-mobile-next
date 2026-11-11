package io.github.eslamasabry.oc.feature.settings.presentation

import kotlinx.coroutines.flow.StateFlow

/**
 * The edge dock's on/off switch (Settings › Conversations), bound by the app that draws the dock. Device-wide: the
 * dock's edge and height are chosen on the dock itself (drag its top), and saved there.
 */
public interface EdgeDockSetting {
    public val shown: StateFlow<Boolean>

    public fun setShown(on: Boolean)
}
