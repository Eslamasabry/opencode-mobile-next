package io.github.eslamasabry.oc.ui.design.style

/**
 * Matte, the default. At its defaults every part keeps its own look, so Solid is pixel-identical to the baseline. The
 * Depth and Edges knobs are never set by the person at the switch (plan 06 §4.3), so nothing is left to resolve.
 */
public object SolidSpec : StyleSpec {
    override val tokens: StyleTokens = StyleTokens(keepsNestedSurfaces = true, flatLists = true)

    override fun resolve(r: SurfaceRequest): SurfaceDecoration? = null
}
