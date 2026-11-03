package io.github.eslamasabry.oc.ui.kit.style

import io.github.eslamasabry.oc.ui.design.color.Argb
import io.github.eslamasabry.oc.ui.design.color.Graphite
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.color.Roles
import io.github.eslamasabry.oc.ui.design.color.ThemeResolver
import io.github.eslamasabry.oc.ui.design.color.argb
import io.github.eslamasabry.oc.ui.design.color.over
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.effects.KitCorners
import io.github.eslamasabry.oc.ui.design.effects.KitDepth
import io.github.eslamasabry.oc.ui.design.effects.KitEdges
import io.github.eslamasabry.oc.ui.design.effects.KitKnobs
import io.github.eslamasabry.oc.ui.design.tokens.Density

internal val graphiteDark: Roles = Graphite.dark
internal val graphiteLight: Roles = Graphite.light
internal val graphite: List<Roles> = listOf(graphiteDark, graphiteLight)

/** The part's own panel the Flutter tests resolve (`_solidPanel`). */
internal val solidPanel: KitSurfaceLook = KitSurfaceLook(radius = KitRadii.all(18f), fill = argb(0xFF141518))

internal fun ratio(a: Argb, b: Argb): Double = wcagContrast(a, b)

internal fun Argb.on(ground: Argb): Argb = over(ground)

/** Every combination of the four knobs. */
internal fun allKnobs(): List<KitKnobs> =
    KitCorners.entries.flatMap { c ->
        KitDepth.entries.flatMap { d ->
            Density.entries.flatMap { n -> KitEdges.entries.map { e -> KitKnobs(c, d, n, e) } }
        }
    }

/** Every static theme pack in both brightnesses (Material You aside), Graphite first. */
internal fun everyTheme(): List<Pair<String, Roles>> =
    PackId.entries.filter { it != PackId.Dynamic }.flatMap { id ->
        listOf(true, false).map { dark -> "${id.storageName}/${if (dark) "dark" else "light"}" to ThemeResolver.roles(id, null, dark, false, null) }
    }
