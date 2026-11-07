package io.github.eslamasabry.oc.ui.kit.live

import io.github.eslamasabry.oc.ui.kit.live.looks.BlueprintLook
import io.github.eslamasabry.oc.ui.kit.live.looks.BrushedMetalLook
import io.github.eslamasabry.oc.ui.kit.live.looks.CircuitLook
import io.github.eslamasabry.oc.ui.kit.live.looks.HoneycombLook
import io.github.eslamasabry.oc.ui.kit.live.looks.MatrixRainLook
import io.github.eslamasabry.oc.ui.kit.live.looks.OscilloscopeLook
import io.github.eslamasabry.oc.ui.kit.live.looks.RadarLook

/**
 * The shader looks by stored id (`oc.effectsLiveStyle`). One line per look; an id with no entry here has no shader yet
 * and the page keeps its plain ground.
 */
public object LiveLooks {
    public val all: List<LiveLook> =
        listOf(
            MatrixRainLook,
            BlueprintLook,
            CircuitLook,
            OscilloscopeLook,
            RadarLook,
            HoneycombLook,
            BrushedMetalLook,
        )

    public fun byId(id: String): LiveLook? = all.firstOrNull { it.id == id }
}
