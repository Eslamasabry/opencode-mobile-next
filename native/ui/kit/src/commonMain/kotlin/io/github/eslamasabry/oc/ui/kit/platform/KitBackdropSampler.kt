package io.github.eslamasabry.oc.ui.kit.platform

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density

/**
 * A liquid pane's ink sampler (`KitPlatform.backdropSampler`): draws a small picture of the content behind the pane and
 * hands back its pixels without holding the UI thread on the GPU. One per pane, kept for the pane's life and released
 * with it, so a sample reuses one small render target instead of making a bitmap each time.
 */
public interface KitBackdropSampler {
    /**
     * Draws [draw] into a [width]×[height] target and returns its pixels (ARGB, row after row), or null when nothing
     * could be read this time. [draw] runs before this suspends, while the caller's coordinates are current.
     */
    public suspend fun sample(width: Int, height: Int, density: Density, draw: DrawScope.() -> Unit): IntArray?

    /** Frees the target; the next sample makes a new one. */
    public fun release()
}
