package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs

/**
 * Solid (Dart `KitStyleSolid`): today's calm matte look. At its defaults it returns the part's own look untouched, so a
 * solid screen is exactly the app before styles existed. Only content takes Depth › Raised, and content and sheets lose
 * their edge under Edges › Off. Lists are flat.
 */
public object KitStyleSolid : KitStyleSpec {
    override val tokens: KitStyleTokens = KitStyleTokens(defaults = KitKnobs(), keepsNestedSurfaces = true, flatLists = true)

    /** The one shadow the kit has (the elevation shadow): y 6, blur 16, pulled in 6 so no halo shows above. */
    public fun lift(roles: Roles): KitShadow = KitShadow(roles.elevationShadow, dy = 6f, blur = 16f, spread = -6f)

    override fun resolve(r: KitSurfaceRequest): KitSurfaceLook {
        val raise = r.knobs.depth == KitDepth.Raised && r.role == KitSurfaceRole.Content
        val strip = r.knobs.edges == KitEdges.None && (r.role == KitSurfaceRole.Content || r.role == KitSurfaceRole.Sheet)
        if (!raise && !strip) return r.solid
        val solid = r.solid
        return solid.copy(
            edgeStart = if (strip) Clear else solid.edgeStart,
            edgeEnd = if (strip) Clear else solid.edgeEnd,
            edgeWidth = if (strip) 0f else solid.edgeWidth,
            shadows = if (raise) solid.shadows + lift(r.roles) else solid.shadows,
        )
    }
}
