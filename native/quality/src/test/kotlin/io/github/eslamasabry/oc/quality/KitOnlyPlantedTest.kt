package io.github.eslamasabry.oc.quality

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** P9's planted imports, run against the K1 function (the full P9 plants files in a spike feature). */
class KitOnlyPlantedTest {
    private val pkg = "io.github.eslamasabry.oc.feature.spike.ui"

    @Test
    fun `K1 rejects material3, LocalContext, raw clickable and the KitPlatform seam`() {
        val planted =
            listOf(
                "androidx.compose.material3.Button",
                "androidx.compose.ui.platform.LocalContext",
                "androidx.compose.foundation.clickable",
                "io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform",
                "androidx.navigation3.runtime.NavKey", // only *Entries.kt may
            )
        assertEquals(planted, KitOnly.violations(pkg, "SpikeScreen", planted))
    }

    @Test
    fun `K1 accepts kit, words, layout and Nav3 in Entries`() {
        val ok =
            listOf(
                "io.github.eslamasabry.oc.ui.kit.KitButton",
                "io.github.eslamasabry.oc.ui.words.res.Res",
                "androidx.compose.foundation.layout.Column",
                "org.jetbrains.compose.resources.stringResource",
                "androidx.navigation3.runtime.NavKey",
            )
        assertTrue(KitOnly.violations("io.github.eslamasabry.oc.feature.spike", "SpikeEntries", ok).isEmpty())
    }
}
