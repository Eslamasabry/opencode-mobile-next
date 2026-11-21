package io.github.eslamasabry.oc.ui.kitchat.request

import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two answers' roles on Home's waiting row: the refusing answer is never the accented one, and only the first
 * waiting row's allowing answer is the primary one.
 */
class KitInboxAnswerRolesTest {

    @Test
    fun `B-KIT-INBOX-001 the refusing answer on the waiting row is never accented`() {
        assertEquals(KitButtonRole.Secondary, inboxAnswerRoles(true).reject)
        assertEquals(KitButtonRole.Secondary, inboxAnswerRoles(false).reject)
    }

    @Test
    fun `B-KIT-INBOX-002 only the first waiting row's allowing answer is the primary one`() {
        assertEquals(KitButtonRole.Primary, inboxAnswerRoles(true).allow)
        assertEquals(KitButtonRole.Secondary, inboxAnswerRoles(false).allow)
    }
}
