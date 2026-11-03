package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.settings.presentation.CapabilitiesUi
import io.github.eslamasabry.oc.feature.settings.presentation.FeatureSource
import io.github.eslamasabry.oc.feature.settings.presentation.ServerFeature
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Available on this server (Flutter `server_capabilities_screen_test`). */
class ServerFeaturesTest {
    private val sparse =
        ServerCapabilities(
            serverCatalog = false, toolInventory = false, terminal = false, fileBrowsing = false, sessionDiff = false,
            projectManagement = false, sessionShare = false, sessionFork = false,
        )

    private fun facts(caps: ServerCapabilities) = phoneFacts(ServerLine("Workstation", onPhone = false)).copy(capabilities = caps)

    @Test
    fun `B-L10N-018 feature ids are unique and every feature names its source`() {
        val ids = ServerFeature.entries.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.all { it.matches(Regex("[a-z-]+")) })
        assertEquals(
            listOf(ServerFeature.BackgroundNotifications, ServerFeature.OnThisPhone, ServerFeature.Voice),
            ServerFeature.entries.filter { it.source == FeatureSource.Device },
        )
    }

    @Test
    fun `B-L10N-018 missing features come first, then the device's, then one fold for what works`() {
        val ui = CapabilitiesUi.of(facts(sparse).copy(platform = android.copy(voice = false)), focus = null)
        assertEquals("Workstation", ui.serverName)
        assertTrue(ui.serverGaps)
        assertFalse(ui.allAvailable)
        assertTrue(ServerFeature.Files in ui.missingOnServer.map { it.feature })
        assertTrue(ServerFeature.Changes in ui.missingOnServer.map { it.feature }, "neither door to Changes is open")
        assertEquals(listOf(ServerFeature.Voice), ui.missingOnDevice.map { it.feature })
        assertEquals("device", ui.missingOnDevice.single().slug)
        assertTrue(ui.available.all { it.present && it.slug == "available" })
        val listed = (ui.missingOnServer + ui.missingOnDevice + ui.available).map { it.feature }
        assertEquals(ServerFeature.entries.toSet(), listed.toSet(), "every feature is listed once")
        assertEquals(listed.size, ServerFeature.entries.size)
        assertTrue(ui.canAddServer)
    }

    @Test
    fun `B-L10N-018 the feature a person came about is listed first, available or not`() {
        val ui = CapabilitiesUi.of(facts(ServerCapabilities.Unknown), focus = ServerFeature.Terminal)
        assertEquals(listOf(ServerFeature.Terminal), ui.focused.map { it.feature })
        assertFalse(ServerFeature.Terminal in ui.available.map { it.feature })
        val missing = CapabilitiesUi.of(facts(sparse), focus = ServerFeature.Terminal)
        assertEquals("unavailable", missing.focused.single().slug)
    }

    @Test
    fun `B-L10N-018 Add a server shows only where the app can open that flow and the page is a Help row`() {
        assertFalse(CapabilitiesUi.of(facts(sparse).copy(opens = emptySet()), null).canAddServer)
        assertTrue(CapabilitiesUi.of(facts(sparse).copy(opens = setOf(SettingsOutside.Servers)), null).canAddServer)
        val help = SettingsStructure.rows(SettingsGroupId.Help, facts(sparse), reasoning = false, timestamps = false)
        assertEquals(
            SettingsPage.ServerCapabilities(),
            (help.single { it.id == SettingsRowId.ServerCapabilities }.door as io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor.Page).page,
        )
        assertFalse(SettingsRowId.ServerCapabilities in SettingsStructure.searchable(SettingsFacts(platform = android)))
    }
}
