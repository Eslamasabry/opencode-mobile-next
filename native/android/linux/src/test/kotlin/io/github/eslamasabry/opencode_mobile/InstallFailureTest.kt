package io.github.eslamasabry.opencode_mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class InstallFailureTest {
    private val error = IllegalStateException("proot exited 9 at /data/user/0/app/files/x")

    @Test
    fun `B-LINUX-208 a failed install's message carries no exception text`() {
        val shown = InstallFailure.shown(error)
        assertFalse("proot" in shown || "/data" in shown, shown)
        assertEquals(InstallFailure.CODE, shown)
    }

    @Test
    fun `B-LINUX-208 the technical text is kept apart for Details`() {
        assertEquals("proot exited 9 at /data/user/0/app/files/x", InstallFailure.details(error))
        assertEquals("IllegalStateException", InstallFailure.details(IllegalStateException()))
    }
}
