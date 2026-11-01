package io.github.eslamasabry.oc.engine.settings.scope

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsScopeCatalogTest {
    @Test
    fun `B-STATE-127 targets separate owners, connections, projects and chats`() {
        assertEquals("device", SettingsTarget.Device.ownerId)
        assertEquals(PHONE_TARGET, SettingsTarget.Connection(ProfileId("phone")))
        assertEquals(1, setOf(PHONE_TARGET, SettingsTarget.Connection(ProfileId("phone"))).size)
        assertNotEquals<SettingsTarget>(PHONE_TARGET, SettingsTarget.Host("phone"))
        assertNotEquals(SettingsTarget.Project(PHONE, "project"), SettingsTarget.Project(LAPTOP, "project"))
        assertNotEquals(SettingsTarget.Chat(PHONE, "chat", "one"), SettingsTarget.Chat(PHONE, "chat", "two"))
        assertEquals(SettingOwnerKind.Chat, SettingsTarget.Chat(PHONE, "chat").kind)
        assertFailsWith<IllegalArgumentException> { SettingsTarget.Connection(ProfileId("")) }
        assertFailsWith<IllegalArgumentException> { SettingsTarget.Project(PHONE, "") }
        assertFailsWith<IllegalArgumentException> { SettingsTarget.Chat(PHONE, "") }
        assertFailsWith<IllegalArgumentException> { SettingsTarget.Chat(PHONE, "chat", "") }
    }

    @Test
    fun `B-STATE-127 every settings door has a fixed, immutable owner set`() {
        val rows = SettingsScopeCatalog.rows
        // Flutter's test still counts 67; the freeze tip's catalog added settings-updates and settings-browser-mode.
        assertEquals(69, rows.size)
        assertEquals(69, rows.map { it.rowId }.toSet().size)
        assertTrue(rows.all { it.owners.isNotEmpty() })
        for (id in listOf("tab-chats", "project-terminal", "library-import-session")) assertNull(SettingsScopeCatalog.forRow(id))
        for (id in listOf("settings-models", "settings-model-and-mode")) {
            val row = SettingsScopeCatalog.forRow(id)!!
            assertEquals(setOf(SettingOwnerKind.Connection), row.owners)
            assertEquals("newChats", row.labelCode)
        }
        assertEquals(setOf(SettingOwnerKind.Device), SettingsScopeCatalog.forRow("settings-external-agents")!!.owners)
        assertEquals(setOf(SettingOwnerKind.Host), SettingsScopeCatalog.forRow("inside-phone-crash-recovery")!!.owners)
        assertEquals(
            setOf(SettingOwnerKind.Connection, SettingOwnerKind.ProviderAccount),
            SettingsScopeCatalog.forRow("inside-usage-remaining")!!.owners,
        )
        assertEquals(setOf(SettingOwnerKind.Device), SettingsScopeCatalog.forRow("settings-updates")!!.owners)
        assertEquals("settings-saved-servers", rows.first().rowId)
        assertEquals("settings-category-usage", rows.last().rowId)
    }

    @Test
    fun `B-STATE-127 absent scenes are unsupported and catalog metadata alone grants no writes`() = runTest {
        val access = ScopeFixture(this).access()
        val absent = access.read("glassLiquid", DEVICE)
        assertEquals(SettingValueState.Unsupported, absent.state)
        assertNull(absent.value)
        assertFalse(absent.writable)
        assertReason(SettingsFailureReason.Unsupported, access.beginEdit("glassLiquid", DEVICE))
        // The catalog lists connection ownership for models, but the local model preference is read-only here.
        assertReason(SettingsFailureReason.Unsupported, access.beginEdit("model", PHONE_TARGET))
        assertEquals(SettingValueState.Unsupported, access.read("appearance", SettingsTarget.Host("phone")).state)
        assertReason(SettingsFailureReason.Unsupported, access.beginEdit("shareReadState", SettingsTarget.Chat(PHONE, "chat")))
    }
}
