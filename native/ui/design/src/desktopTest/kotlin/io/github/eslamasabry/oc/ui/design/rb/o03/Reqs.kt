package io.github.eslamasabry.oc.ui.design.rb.o03

import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.style.Knobs
import io.github.eslamasabry.oc.ui.design.style.Radii
import io.github.eslamasabry.oc.ui.design.style.SurfaceRequest
import io.github.eslamasabry.oc.ui.design.style.SurfaceRole
import io.github.eslamasabry.oc.ui.design.tokens.SurfaceLevel

val bothModes: List<Roles> = listOf(Graphite.light, Graphite.dark)

fun req(
    roles: Roles,
    role: SurfaceRole,
    knobs: Knobs = Knobs(),
    level: SurfaceLevel = SurfaceLevel.Surface1,
    selected: Boolean = false,
    pressed: Boolean = false,
    color: io.github.eslamasabry.oc.ui.design.color.Argb? = null,
    radii: Radii = Radii.all(12f),
) = SurfaceRequest(roles, role, knobs, level = level, radii = radii, color = color, selected = selected, pressed = pressed)
