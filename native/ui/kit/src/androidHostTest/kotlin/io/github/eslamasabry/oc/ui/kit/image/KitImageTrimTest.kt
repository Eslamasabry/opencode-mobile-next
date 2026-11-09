package io.github.eslamasabry.oc.ui.kit.image

import android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
import android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
import android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KitImageTrimTest {
    @Test
    fun `the picture cache shrinks with the pressure level and is emptied when critical or in the background`() {
        assertNull(imageKeepFraction(0))
        assertEquals(0.5f, imageKeepFraction(TRIM_MEMORY_RUNNING_MODERATE))
        assertEquals(0.25f, imageKeepFraction(TRIM_MEMORY_RUNNING_LOW))
        assertEquals(0f, imageKeepFraction(TRIM_MEMORY_RUNNING_CRITICAL))
        assertEquals(0.25f, imageKeepFraction(TRIM_MEMORY_UI_HIDDEN))
        assertEquals(0f, imageKeepFraction(TRIM_MEMORY_BACKGROUND))
        assertEquals(0f, imageKeepFraction(TRIM_MEMORY_COMPLETE))
    }
}
