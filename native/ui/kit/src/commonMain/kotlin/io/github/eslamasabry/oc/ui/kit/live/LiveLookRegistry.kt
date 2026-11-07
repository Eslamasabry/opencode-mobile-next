package io.github.eslamasabry.oc.ui.kit.live

import io.github.eslamasabry.oc.ui.kit.live.looks.LiveBokeh
import io.github.eslamasabry.oc.ui.kit.live.looks.LiveConstellation
import io.github.eslamasabry.oc.ui.kit.live.looks.LiveDotRipple
import io.github.eslamasabry.oc.ui.kit.live.looks.LiveFlowThreads
import io.github.eslamasabry.oc.ui.kit.live.looks.LiveHalftone
import io.github.eslamasabry.oc.ui.kit.live.looks.LiveLightRays
import io.github.eslamasabry.oc.ui.kit.live.looks.LiveTopo

/**
 * Every shader look the native app draws, by stored id. Builder A owns this file (shared infrastructure); builders B and
 * C add one line per look. An id with no entry here keeps the page's flat ground.
 */
public object LiveLookRegistry {
    public val looks: List<LiveLook> =
        listOf(
            // Builder B
            LiveDotRipple,
            LiveTopo,
            LiveFlowThreads,
            LiveLightRays,
            LiveConstellation,
            LiveBokeh,
            LiveHalftone,
        )

    private val byId: Map<String, LiveLook> = looks.associateBy { it.id }

    /** The look stored as [id], or null when the native app does not draw it (the page keeps its flat ground). */
    public fun of(id: String?): LiveLook? = id?.let(byId::get)
}
