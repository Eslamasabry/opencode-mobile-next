package io.github.eslamasabry.oc.feature.updates.presentation

import io.github.eslamasabry.oc.engine.updates.AppUpdateState
import io.github.eslamasabry.oc.engine.updates.UpdatePhase
import io.github.eslamasabry.oc.feature.updates.INSTALLED
import io.github.eslamasabry.oc.feature.updates.RELEASE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-217: the first visit after Android installed the offered release says what happened. */
class UpdatedNoticeUiTest {
    private val updated = AppUpdateState(installed = INSTALLED, updated = RELEASE)

    @Test
    fun `B-217 the line says which version is now installed`() {
        assertEquals(NoticeUi.Updated("2.2.0"), noticeUi(updated))
    }

    @Test
    fun `B-217 the line is gone once dismissed, on the Updates page, or while unsupported`() {
        assertNull(noticeUi(updated.copy(notNow = true)))
        assertNull(noticeUi(updated.copy(pageOpen = true)))
        assertNull(noticeUi(updated.copy(supported = false)))
        assertNull(noticeUi(updated.copy(updated = null)))
    }

    @Test
    fun `B-217 a newer offer or a running update takes the line first`() {
        assertEquals(NoticeUi.Ready("2.2.0"), noticeUi(updated.copy(phase = UpdatePhase.Available, release = RELEASE)))
        assertEquals(NoticeUi.Verifying, noticeUi(updated.copy(phase = UpdatePhase.Verifying, release = RELEASE)))
    }
}
