package oc.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** One allowed and one denied vector per ADR §6.1 row (plan 03 §6.6), plus the audit BLOCKER 3 edges. */
class ModuleRulesTest {
    private fun allowed(from: String, to: String, inTest: Boolean = false) =
        assertNull(ModuleRules.violation(from, to, inTest), "$from -> $to should be allowed")

    private fun denied(from: String, to: String, inTest: Boolean = false) =
        assertNotNull(ModuleRules.violation(from, to, inTest), "$from -> $to should be denied")

    @Test fun `core common depends on nothing`() = denied(":core:common", ":core:model")

    @Test fun `core model uses common only`() {
        allowed(":core:model", ":core:common"); denied(":core:model", ":core:domain")
    }

    @Test fun `core security may use model (audit blocker 3)`() {
        allowed(":core:security", ":core:model"); denied(":core:security", ":core:domain")
    }

    @Test fun `core domain`() {
        allowed(":core:domain", ":core:security"); denied(":core:domain", ":core:net")
    }

    @Test fun `core data`() {
        allowed(":core:data", ":core:model"); denied(":core:data", ":core:domain")
    }

    @Test fun `core db`() {
        allowed(":core:db", ":core:data"); denied(":core:db", ":core:net")
    }

    @Test fun `core net`() {
        allowed(":core:net", ":core:net-okhttp"); denied(":core:net", ":core:data")
    }

    @Test fun `core net-okhttp has no project deps`() = denied(":core:net-okhttp", ":core:common")

    @Test fun `core markdown`() {
        allowed(":core:markdown", ":core:model"); denied(":core:markdown", ":core:domain")
    }

    @Test fun `protocols`() {
        allowed(":protocol:oc2", ":core:net"); denied(":protocol:oc2", ":engine:connection")
        denied(":protocol:oc2", ":protocol:paseo")
    }

    @Test fun `engines follow the order`() {
        allowed(":engine:connection", ":engine:profiles"); denied(":engine:profiles", ":engine:connection")
        denied(":engine:connection", ":ui:kit"); denied(":engine:connection", ":protocol:oc2")
    }

    @Test fun `ui design`() {
        allowed(":ui:design", ":core:common"); denied(":ui:design", ":core:model")
    }

    @Test fun `ui words and routes`() {
        allowed(":ui:words", ":core:model"); denied(":ui:words", ":ui:kit")
        allowed(":ui:routes", ":core:common"); denied(":ui:routes", ":engine:connection")
    }

    @Test fun `kits`() {
        allowed(":ui:kit", ":ui:design"); denied(":ui:kit", ":engine:connection")
        allowed(":ui:kit-chat", ":core:markdown"); denied(":ui:kit-chat", ":core:net")
        allowed(":ui:kit-content", ":core:security") // audit blocker 3: WebPreview(ApprovedLink)
        denied(":ui:kit-content", ":core:data")
    }

    @Test fun `shell`() {
        allowed(":ui:shell", ":ui:routes"); denied(":ui:shell", ":feature:chats")
        denied(":ui:shell", ":engine:connection")
    }

    @Test fun `features`() {
        allowed(":feature:chats", ":engine:connection"); denied(":feature:chats", ":core:net")
        denied(":feature:chats", ":feature:chat"); denied(":feature:chats", ":protocol:oc2")
    }

    @Test fun `android modules`() {
        allowed(":android:linux", ":engine:runtime"); denied(":android:linux", ":ui:kit")
        allowed(":android:migration", ":core:data"); denied(":android:migration", ":engine:profiles")
    }

    @Test fun `apps`() {
        allowed(":androidApp", ":android:linux"); allowed(":desktopApp", ":ui:shell")
        denied(":desktopApp", ":android:linux")
    }

    @Test fun `test-only modules only from tests`() {
        allowed(":engine:connection", ":core:testing", inTest = true); denied(":engine:connection", ":core:testing")
    }

    @Test fun `material3 only in kits`() {
        assertNull(ExternalRules.violation(":ui:kit", "org.jetbrains.compose.material3", "material3", false))
        assertNotNull(ExternalRules.violation(":feature:chats", "org.jetbrains.compose.material3", "material3", false))
        assertNotNull(ExternalRules.violation(":ui:routes", "org.jetbrains.compose.material3", "material3", false))
    }

    @Test fun `namespaces drop hyphens`() =
        assertEquals("io.github.eslamasabry.oc.ui.kitchat", Namespaces.of(":ui:kit-chat"))
}
