package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.APK_SIZE
import io.github.eslamasabry.oc.feature.updates.INSTALLED
import io.github.eslamasabry.oc.feature.updates.RELEASE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The start-up / resume prompt of Flutter #126 (`showInAppUpdateSheet`). */
class UpdatePromptUiTest {
    private val open =
        AppUpdateState(
            phase = UpdatePhase.Available,
            release = RELEASE,
            downloadSize = APK_SIZE,
            installed = INSTALLED,
            promptOpen = true,
        )

    @Test
    fun `B-BUILD-045 the prompt names the version and the release's first sentence`() {
        assertEquals(OfferUi("2.2.0", "This release is faster."), promptUi(open))
    }

    @Test
    fun `B-BUILD-045 the prompt stays shut when closed, put off, on the Updates page, busy or unsupported`() {
        assertNull(promptUi(open.copy(promptOpen = false)))
        assertNull(promptUi(open.copy(notNow = true)))
        assertNull(promptUi(open.copy(pageOpen = true)))
        assertNull(promptUi(open.copy(phase = UpdatePhase.Downloading)))
        assertNull(promptUi(open.copy(supported = false)))
        assertNull(promptUi(open.copy(release = null)))
    }
}
