package io.github.eslamasabry.oc.feature.phonesetup

import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupDeviceInfo
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupJourneyUi
import io.github.eslamasabry.oc.feature.phonesetup.presentation.SetupOptions
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A nearly full phone sees what set-up needs and what it has; a roomy one sees nothing extra. */
class SetupSpaceTightTest {
    private fun ui(free: Long?): SetupJourneyUi {
        val part =
            SetupComponent(
                "essentials", "E", "E", "", "", required = true, defaultOn = true,
                downloadBytes = 208_000_000L, installedBytes = 600_000_000L,
            )
        return SetupJourneyUi(
            options = SetupOptions(listOf(part), emptySet(), SetupDeviceInfo(listOf("x86_64"), 8000, free)),
            chosen = setOf("essentials"),
        )
    }

    @Test
    fun `a phone within a gigabyte of the need is told`() {
        assertNotNull(ui(free = 1_200_000_000L).spaceTight)
    }

    @Test
    fun `a roomy phone or an unknown reading says nothing`() {
        assertNull(ui(free = 20_000_000_000L).spaceTight)
        assertNull(ui(free = null).spaceTight)
    }
}
