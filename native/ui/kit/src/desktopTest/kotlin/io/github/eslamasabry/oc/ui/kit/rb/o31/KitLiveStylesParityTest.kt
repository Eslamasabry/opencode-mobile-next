package io.github.eslamasabry.oc.ui.kit.rb.o31

import io.github.eslamasabry.oc.ui.design.effects.AuroraImage
import io.github.eslamasabry.oc.ui.kit.live.KIT_LIVE_DEFAULT_STYLE_ID
import io.github.eslamasabry.oc.ui.kit.live.KitLiveAurora
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBlueprint
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBokeh
import io.github.eslamasabry.oc.ui.kit.live.KitLiveBrushedMetal
import io.github.eslamasabry.oc.ui.kit.live.KitLiveCaustics
import io.github.eslamasabry.oc.ui.kit.live.KitLiveCircuit
import io.github.eslamasabry.oc.ui.kit.live.kitLiveLookIds
import io.github.eslamasabry.oc.ui.kit.live.kitLiveLookLabel
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyleById
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyleOrNull
import io.github.eslamasabry.oc.ui.kit.live.kitLiveStyles
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.rb34LiveStyleAuroraImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Every constant and rule of lib/ui/kit/live_background/kit_live_styles.dart at 8302de537. */
class KitLiveStylesParityTest {
    /** kitLiveStyles in Dart order; the ids are stored in oc.effectsLiveStyle and must match byte for byte. */
    private val dartOrder =
        listOf(
            "aurora", "mesh", "dotRipple", "topo", "flowThreads", "glowSpill", "lightRays", "constellation", "halftone",
            "liquidBlobs", "matrixRain", "blueprint", "circuit", "oscilloscope", "radar", "honeycomb", "brushedMetal",
            "silk", "caustics", "bokeh",
        )

    @Test fun orderThirtyOnesIdsAreDartsByteForByte() {
        assertEquals("aurora", KitLiveAurora.id)
        assertEquals("blueprint", KitLiveBlueprint.id)
        assertEquals("bokeh", KitLiveBokeh.id)
        assertEquals("brushedMetal", KitLiveBrushedMetal.id)
        assertEquals("caustics", KitLiveCaustics.id)
        assertEquals("circuit", KitLiveCircuit.id)
    }

    @Test fun theRegistryKeepsDartsPickerOrderAndHasNoDuplicates() {
        val ids = kitLiveStyles.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(dartOrder.filter { it in ids }, ids, "each registered look sits at its Dart place")
        assertEquals(dartOrder.first(), ids.first())
    }

    @Test fun theDefaultIsAuroraAndAnUnknownIdReadsAsIt() {
        assertEquals("aurora", KIT_LIVE_DEFAULT_STYLE_ID)
        assertSame(KitLiveAurora, kitLiveStyleById(null))
        assertSame(KitLiveAurora, kitLiveStyleById("nope"))
        assertSame(KitLiveCaustics, kitLiveStyleById("caustics"))
        assertNull(kitLiveStyleOrNull(AuroraImage.ID), "the painted aurora is an image, not a drawn style")
    }

    @Test fun thePickerOffersThePaintedAuroraSecond() {
        val ids = kitLiveLookIds
        assertEquals(listOf("aurora", AuroraImage.ID), ids.take(2))
        assertEquals(kitLiveStyles.size + 1, ids.size)
        assertEquals(Res.string.rb34LiveStyleAuroraImage, kitLiveLookLabel(AuroraImage.ID))
        assertEquals(KitLiveBokeh.label, kitLiveLookLabel("bokeh"))
    }
}
