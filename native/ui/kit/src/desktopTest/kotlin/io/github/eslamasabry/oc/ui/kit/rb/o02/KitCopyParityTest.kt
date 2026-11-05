package io.github.eslamasabry.oc.ui.kit.rb.o02

import kotlin.test.*
import io.github.eslamasabry.oc.ui.kit.foundation.KitCopy
import io.github.eslamasabry.oc.ui.kit.text.KitRedact
import kotlinx.coroutines.test.runTest

class KitCopyParityTest {
    @Test fun redactsByDefaultThenTicksWritesAndAnnouncesOnce() = runTest {
        KitRedact.clearKnownSecrets()
        val events = mutableListOf<String>()
        KitCopy.copy("password=example", "Copied", { events += "write:$it" }, { events += "announce:$it" }, { events += "tick" })
        assertEquals(listOf("tick", "write:password=•••", "announce:Copied"), events)
    }
    @Test fun verbatimPreservesRegisteredSecretsAndCustomAnnouncement() = runTest {
        KitRedact.registerKnownSecret("sample-secret")
        try {
            var text = ""
            var announcement = ""
            KitCopy.copy("sample-secret", "تم النسخ", { text = it }, { announcement = it }, {}, redact = false)
            assertEquals("sample-secret", text)
            assertEquals("تم النسخ", announcement)
        } finally { KitRedact.clearKnownSecrets() }
    }
    @Test fun failedClipboardDoesNotAnnounceSuccess() = runTest {
        var announced = false
        assertFailsWith<IllegalStateException> {
            KitCopy.copy("safe", "Copied", { error("clipboard unavailable") }, { announced = true }, {})
        }
        assertFalse(announced)
    }
}
