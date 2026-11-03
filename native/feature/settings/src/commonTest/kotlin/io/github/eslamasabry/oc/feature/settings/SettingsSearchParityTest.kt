package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.feature.settings.presentation.PhraseChipsSetting
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPlaceEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPlaceId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPlaces
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSection
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings search and deep links over capability sets (Flutter `search_index_test`, `settings_rebuild_test`). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSearchParityTest {
    /** A server without the catalogue, its tools or shell settings, signing in itself (the Flutter Codex/Paseo shape). */
    private val noCatalog =
        ServerCapabilities(serverCatalog = false, toolInventory = false, shellSettings = false, agentAccount = true)

    /** The OpenCode 2 catalogue flags (`Oc2Gateway.Capabilities`): the catalogue, no tool inventory, no shared setup. */
    private val oc2 = ServerCapabilities(serverCatalog = true, toolInventory = false, setupConfigRead = false)

    private fun facts(caps: ServerCapabilities = ServerCapabilities.Unknown, onPhone: Boolean = false) =
        phoneFacts(ServerLine(if (onPhone) "On this phone" else "Workstation", onPhone)).copy(capabilities = caps)

    private fun all(f: SettingsFacts): Set<String> =
        (SettingsStructure.searchable(f).map { it.tag } + SettingsPlaces.searchable(f).map { it.tag }).toSet()

    @Test
    fun `B-NAV-058 the in-app Linux offers one Running on this phone and Services only there`() {
        val inApp = all(facts(onPhone = true))
        assertEquals(1, inApp.count { it == SettingsPlaceId.RunningOnPhone.tag })
        assertTrue(SettingsPlaceId.LinuxServices.tag in inApp)
        val other = all(facts(onPhone = false))
        assertFalse(SettingsPlaceId.LinuxServices.tag in other)
        assertFalse(SettingsPlaceId.RunningOnPhone.tag in other)
        val tags = SettingsPlaceId.entries.map { it.tag } + SettingsRowId.entries.map { it.tag }
        assertEquals(tags.distinct(), tags, "every search id is unique")
    }

    @Test
    fun `B-NAV-058 a server without the catalogue drops it and its tools but keeps what is about the app`() {
        val f = facts(noCatalog)
        val ids = all(f)
        for (gone in listOf(SettingsPlaceId.Models.tag, SettingsPlaceId.CodingTools.tag, SettingsRowId.Tools.tag, SettingsRowId.DefaultShell.tag)) {
            assertFalse(gone in ids, gone)
        }
        // Providers is the server account's door there; with neither the catalogue nor an account, it is absent.
        assertTrue(SettingsRowId.Providers.tag in ids)
        assertFalse(SettingsRowId.Providers.tag in all(facts(noCatalog.copy(agentAccount = false))))
        for (kept in listOf(SettingsRowId.Appearance.tag, SettingsRowId.Language.tag, SettingsPlaceId.TextSize.tag)) {
            assertTrue(kept in ids, kept)
        }
        // Typing the name of something absent finds nothing that opens it.
        val entries = listOf(SettingsPlaceEntry(SettingsPlaceId.Models, "Models & agents", "models"))
        val p = rigFor(f)
        p.presenter.onPlaceEntries(entries)
        p.presenter.onQuery("models")
        p.settle()
        assertEquals(emptyList(), p.presenter.state.value.hub.places)
    }

    @Test
    fun `B-NAV-058 OpenCode 2 keeps the catalogue, and its tools follow the tool inventory`() {
        val ids = all(facts(oc2))
        assertEquals(oc2.serverCatalog, SettingsPlaceId.Models.tag in ids)
        assertEquals(oc2.serverCatalog, SettingsRowId.Tools.tag in ids)
        assertEquals(oc2.serverCatalog && oc2.toolInventory, SettingsPlaceId.CodingTools.tag in ids)
        assertTrue(SettingsPlaceId.CodingTools.tag in all(facts(oc2.copy(toolInventory = true))))
    }

    @Test
    fun `B-NAV-058 a desktop has no phone-only results`() {
        val ids = all(SettingsFacts(platform = desktop, server = ServerLine("Workstation", onPhone = true)))
        for (gone in listOf(
            SettingsRowId.ThisPhone.tag, SettingsRowId.Voice.tag, SettingsRowId.Notifications.tag, SettingsRowId.Background.tag,
            SettingsPlaceId.RunningOnPhone.tag, SettingsPlaceId.LinuxServices.tag, SettingsPlaceId.QuietHours.tag,
            SettingsPlaceId.BackgroundChecks.tag,
        )) {
            assertFalse(gone in ids, gone)
        }
        assertTrue(SettingsRowId.Appearance.tag in ids)
    }

    @Test
    fun `B-NAV-058 AI setup is found where the server shares its setup and the app opens it, absent otherwise`() {
        assertTrue(SettingsPlaceId.AiSetup.tag in all(facts(ServerCapabilities(setupConfigRead = true))))
        assertFalse(SettingsPlaceId.AiSetup.tag in all(facts(ServerCapabilities(setupConfigRead = false))))
        val unopenable = facts(ServerCapabilities(setupConfigRead = true)).copy(opens = setOf(SettingsOutside.Servers))
        assertFalse(SettingsPlaceId.AiSetup.tag in all(unopenable), "a result that leads nowhere is absent")
        assertEquals(SettingsRowId.Servers, SettingsPlaceId.AiSetup.parent)
    }

    @Test
    fun `B-NAV-042 the phrase suggestions switch is a Conversations row search can find, only where the store exists`() {
        val off = phoneFacts()
        assertFalse(SettingsRowId.PhraseChips in SettingsStructure.searchable(off))
        val on = phoneFacts().copy(phraseChips = true)
        val row = SettingsStructure.rows(SettingsGroupId.Conversations, on, reasoning = false, timestamps = false)
            .single { it.id == SettingsRowId.PhraseChips }
        assertEquals(SettingsDoor.Switch(true), row.door)
        val rig = rigFor(phoneFacts(), phrases = FakePhrases(true))
        rig.presenter.onSearchEntries(listOf(SettingsSearchEntry(SettingsRowId.PhraseChips, "Phrase suggestions", "phrase chips")))
        rig.presenter.onQuery("phrase")
        rig.settle()
        assertEquals(listOf(SettingsRowId.PhraseChips), rig.presenter.state.value.hub.results)
    }

    @Test
    fun `B-NAV-042 the switch flips through the phrase store and shows only acknowledged values`() {
        val phrases = FakePhrases(true)
        val rig = rigFor(phoneFacts(), phrases = phrases)
        rig.presenter.onOpen(SettingsPage.Group(SettingsGroupId.Conversations))
        rig.settle()
        fun door() = rig.presenter.state.value.group!!.rows.single { it.id == SettingsRowId.PhraseChips }.door
        assertEquals(SettingsDoor.Switch(true), door())
        rig.presenter.onRow(SettingsRowId.PhraseChips)
        rig.settle()
        assertEquals(listOf(false), phrases.writes)
        assertEquals(SettingsDoor.Switch(false), door())
        phrases.refuse = true
        rig.presenter.onRow(SettingsRowId.PhraseChips)
        rig.settle()
        assertEquals(listOf(false, true), phrases.writes)
        assertEquals(SettingsDoor.Switch(false), door(), "a refused write leaves the switch as it was")
        assertEquals(SettingsNote.ChoiceNotSaved, rig.presenter.state.value.note)
    }

    @Test
    fun `B-NAV-050 background checks lands on the section where this phone runs them`() {
        assertEquals(
            SettingsTarget.Page(SettingsPage.Notifications, SettingsSection.NotificationsBackground),
            SettingsPlaces.target(SettingsPlaceId.BackgroundChecks),
        )
        assertEquals(SettingsRowId.Notifications, SettingsPlaceId.BackgroundChecks.parent)
        val rig = rigFor(phoneFacts())
        rig.presenter.onPlaceEntries(listOf(SettingsPlaceEntry(SettingsPlaceId.BackgroundChecks, "Background checks", "stay connected")))
        rig.presenter.onSearchEntries(listOf(SettingsSearchEntry(SettingsRowId.Background, "Background", "background checks keep running")))
        rig.presenter.onQuery("background checks")
        rig.settle()
        val hub = rig.presenter.state.value.hub
        assertEquals(listOf(SettingsRowId.Background), hub.results)
        assertEquals(listOf(SettingsPlaceId.BackgroundChecks), hub.places)
        rig.presenter.onPlace(SettingsPlaceId.BackgroundChecks)
        rig.settle()
        val ui = rig.presenter.state.value
        assertEquals(SettingsPage.Notifications, ui.page)
        assertEquals(SettingsSection.NotificationsBackground.tag, ui.focus)
    }

    @Test
    fun `B-NAV-059 a row result opens its group arrived at the row and never flips a switch`() {
        val phrases = FakePhrases(true)
        val rig = rigFor(phoneFacts(), phrases = phrases)
        rig.presenter.onSearchResult(SettingsRowId.PhraseChips)
        rig.settle()
        assertEquals(SettingsPage.Group(SettingsGroupId.Conversations), rig.presenter.state.value.page)
        assertEquals(SettingsRowId.PhraseChips.tag, rig.presenter.state.value.focus)
        assertTrue(phrases.writes.isEmpty())
        // A row with a page opens through its group too; a place another feature owns goes to its owner.
        rig.presenter.onBack()
        rig.presenter.onPlace(SettingsPlaceId.Models)
        rig.settle()
        assertEquals(listOf(SettingsOutside.ModelAndMode), rig.openers.opened)
    }

    @Test
    fun `B-NAV-070 a deep link lands on its row or section, and an absent one leaves the hub`() {
        val rig = rigFor(phoneFacts())
        rig.presenter.onDeepLink("settings-language")
        rig.settle()
        assertEquals(listOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.ThisApp)), rig.presenter.state.value.stack.toList())
        assertEquals("settings-language", rig.presenter.state.value.focus)

        rig.presenter.onDeepLink("inside-notifications-quiet")
        rig.settle()
        assertEquals(listOf(SettingsPage.Hub, SettingsPage.Notifications), rig.presenter.state.value.stack.toList())
        assertEquals(SettingsSection.NotificationsQuiet.tag, rig.presenter.state.value.focus)
        rig.presenter.onBack()
        rig.settle()
        assertNull(rig.presenter.state.value.focus, "Back forgets the landing")

        val desk = rigFor(SettingsFacts(platform = desktop))
        desk.presenter.onDeepLink("settings-this-phone")
        desk.presenter.onDeepLink("inside-notifications-quiet")
        desk.presenter.onDeepLink("no-such-row")
        desk.settle()
        assertEquals(SettingsPage.Hub, desk.presenter.state.value.page)
        assertNull(SettingsPlaces.resolve("settings-this-phone", SettingsFacts(platform = desktop)))
    }

    private class FakePhrases(initial: Boolean) : PhraseChipsSetting {
        private val flow = MutableStateFlow(initial)
        override val enabled: StateFlow<Boolean> = flow
        val writes = mutableListOf<Boolean>()
        var refuse = false

        override suspend fun setEnabled(on: Boolean): Outcome<Unit> {
            writes += on
            if (refuse) return Outcome.Failed(io.github.eslamasabry.oc.core.common.Failure.StorageRefused())
            flow.value = on
            return Outcome.Ok(Unit)
        }
    }

    private class Rig(val scope: TestScope, val presenter: SettingsPresenter, val openers: RecordingOpeners) {
        fun settle() = scope.runCurrent()
    }

    private fun rigFor(f: SettingsFacts, phrases: PhraseChipsSetting? = null): Rig {
        val scope = TestScope()
        val openers = RecordingOpeners()
        val p =
            SettingsPresenter(
                scope.backgroundScope, MutableStateFlow(f), FakeChoices(), openers,
                notifications = FakeNotifications(), phraseChips = phrases,
            )
        scope.runCurrent()
        return Rig(scope, p, openers)
    }
}
