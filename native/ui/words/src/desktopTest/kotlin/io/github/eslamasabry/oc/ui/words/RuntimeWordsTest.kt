package io.github.eslamasabry.oc.ui.words

import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.allStringResources
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetForgetFailed
import io.github.eslamasabry.oc.ui.words.res.linuxMgrResetLeft
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The runtime's words read back by CMP (BUILD-RUNTIME-A): the reset leftover sentences (B-L10N-011, port of
 * test/linux_reset_copy_test.dart) and the keys `:engine:runtime` SetupMessages maps setup messages to (B-L10N-019,
 * port of test/setup_ui_messages_test.dart; the mapping itself is proven in SetupMessagesTest).
 */
class RuntimeWordsTest {
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun lang(tag: String) = Locale.setDefault(Locale.forLanguageTag(tag))

    private fun phones(text: String) = Regex("phone", RegexOption.IGNORE_CASE).findAll(text).count()

    private suspend fun word(key: String, vararg args: Any): String =
        getString(Res.allStringResources.getValue(key), *args)

    @Test
    fun `B-L10N-011 the reset leftover sentence does not say phone twice`() = runTest {
        lang("en")
        val text = getString(Res.string.linuxMgrResetForgetFailed, "This phone")
        assertEquals(1, phones(text), text)
        assertFalse("Servers" in text, "setup re-creates it: $text")
        assertFalse("could not be cleared" in text, text)
        val left = getString(Res.string.linuxMgrResetLeft, "This phone", "its saved connection")
        assertEquals(1, phones(left), left)
    }

    @Test
    fun `B-L10N-019 English words keep the actionable validation detail and the status value`() = runTest {
        lang("en")
        assertEquals("Remove the path from the server URL. Enter only its origin.", word("e7SetupUrlPath"))
        assertEquals("OpenCode server exited (code 17)", word("e7SetupServerExit", "17"))
    }

    @Test
    fun `B-L10N-019 Arabic localizes parser and manager facts while preserving status values`() = runTest {
        lang("ar")
        val pairing = word("e7SetupPairInvalid")
        assertTrue("رمز اقتران" in pairing, pairing)
        assertFalse("not a pairing payload" in pairing, pairing)
        assertTrue("تثبيت" in word("e7SetupInstallingOpenCode"))
        assertTrue("17" in word("e7SetupServerExit", "17"))
    }
}
