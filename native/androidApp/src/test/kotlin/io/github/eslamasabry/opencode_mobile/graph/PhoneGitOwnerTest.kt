package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-GIT-147 the Git page reaches a project a phone agent works in (issue 147 walk F-017). */
class PhoneGitOwnerTest {
    private val phone = ProfileId("phone-1")
    private val helper = ProfileId("phone-1-agents")
    private val port = 4097
    private val local = ServerOrigin("http", "127.0.0.1", port)

    @Test
    fun `B-GIT-147 a phone agent's conversation lives in the phone's Linux`() {
        val helperOrigin = ServerOrigin("http", "127.0.0.1", 6767)
        assertEquals(phone, phoneGitOwner(helper, helperOrigin, port, phone.raw, helperOf = phone))
    }

    @Test
    fun `B-GIT-147 the phone's own server conversation lives in the phone's Linux`() {
        assertEquals(phone, phoneGitOwner(phone, local, port, phone.raw, helperOf = null))
    }

    @Test
    fun `B-GIT-147 another phone's helper or another server is not this phone's Linux`() {
        assertNull(phoneGitOwner(helper, local, port, "someone-else", helperOf = phone))
        assertNull(phoneGitOwner(ProfileId("pc"), ServerOrigin("http", "10.0.0.2", port), port, phone.raw, helperOf = null))
        assertNull(phoneGitOwner(phone, local, port, null, helperOf = null))
    }
}
