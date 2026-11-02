package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.feature.settings.presentation.NotificationChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsNote
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSearchEntry
import io.github.eslamasabry.oc.feature.settings.presentation.SurfaceStyle
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.ui.design.color.PackId
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPresenterTest {
    private class Rig(
        val scope: TestScope,
        prefs: PersonalPrefs = PersonalPrefs(),
        facts: SettingsFacts = phoneFacts(),
    ) {
        val facts = MutableStateFlow(facts)
        val choices = FakeChoices(prefs)
        val analytics = FakeAnalytics()
        val notifications = FakeNotifications()
        val updates = FakeUpdates(UpdateFacts(current = "3.0.0", build = "10001"))
        val openers = RecordingOpeners()
        val p =
            SettingsPresenter(
                scope = scope.backgroundScope,
                facts = this.facts,
                choices = choices,
                openers = openers,
                analytics = analytics,
                notifications = notifications,
                updates = updates,
            )

        fun settle() = scope.runCurrent()

        val ui get() = p.state.value
    }

    private fun rig(
        prefs: PersonalPrefs = PersonalPrefs(),
        facts: SettingsFacts = phoneFacts(),
        body: suspend Rig.() -> Unit,
    ) = runTest { Rig(this, prefs, facts).apply { settle() }.body() }

    @Test
    fun `B-NAV-068 the hub lists the five groups and the server's one value line`() =
        rig {
            assertEquals<List<SettingsGroupId>>(SettingsGroupId.entries.toList(), ui.hub.groups)
            assertEquals(ServerLine("laptop", onPhone = false), ui.hub.server)
            assertEquals(SettingsPage.Hub, ui.page)
        }

    @Test
    fun `SCREENS-NAV-90 hub to group to page, and Back walks the way out`() =
        rig {
            p.onOpen(SettingsPage.Group(SettingsGroupId.ThisApp)); settle()
            assertEquals(SettingsGroupId.ThisApp, ui.group?.id)
            p.onRow(SettingsRowId.Appearance); settle()
            assertEquals(SettingsPage.Appearance, ui.page)
            assertTrue(p.onBack()); settle()
            assertEquals(SettingsPage.Group(SettingsGroupId.ThisApp), ui.page)
            assertTrue(p.onBack()); settle()
            assertEquals(SettingsPage.Hub, ui.page)
            assertFalse(p.onBack())
        }

    @Test
    fun `SCREENS-NAV-90 a row another feature owns is handed to the host, not drawn here`() =
        rig {
            p.onRow(SettingsRowId.Background); settle()
            p.onRow(SettingsRowId.Servers); settle()
            assertEquals(listOf(SettingsOutside.KeepRunning, SettingsOutside.Servers), openers.opened)
            assertEquals(SettingsPage.Hub, ui.page)
        }

    @Test
    fun `plan 07 3_4 Back on the hub first clears the search`() =
        rig {
            p.onSearchEntries(
                listOf(SettingsSearchEntry(SettingsRowId.Appearance, "Appearance", "theme")),
            )
            p.onQuery("theme"); settle()
            assertEquals(listOf(SettingsRowId.Appearance), ui.hub.results)
            assertTrue(p.onBack()); settle()
            assertEquals("", ui.hub.query)
            assertEquals(emptyList(), ui.hub.results)
        }

    @Test
    fun `B-NAV-073 a search result opens the page that holds the row`() =
        rig {
            p.onRow(SettingsRowId.About); settle()
            assertEquals(SettingsPage.About, ui.page)
        }

    @Test
    fun `B-KIT-061 Graphite is the default, the most used packs lead, and See all lists all 31`() =
        rig {
            assertEquals(PackId.Opencode, ui.appearance.pack)
            assertEquals(PackId.Opencode, ui.appearance.packs.first())
            assertEquals(8, ui.appearance.packs.size)
            assertEquals(31, ui.appearance.seeAllCount)
            p.onSeeAllPacks(); settle()
            assertEquals(31, ui.appearance.packs.size)
            assertEquals(0, ui.appearance.seeAllCount)
        }

    @Test
    fun `B-KIT-061 Material You is offered only with the platform's tones, and a pack in use stays listed`() =
        rig(prefs = PersonalPrefs(pack = PackId.Coffee), facts = phoneFacts().copy(dynamicColour = false)) {
            assertTrue(PackId.Coffee in ui.appearance.packs)
            p.onSeeAllPacks(); settle()
            assertEquals(30, ui.appearance.packs.size)
            assertFalse(PackId.Dynamic in ui.appearance.packs)
        }

    @Test
    fun `plan 07 I04 mode and pack apply at once through the personal choices`() =
        rig {
            p.onOpen(SettingsPage.Appearance); settle()
            p.onMode(AppearanceMode.Light); settle()
            p.onPack(PackId.Nord); settle()
            assertEquals(AppearanceMode.Light, ui.appearance.mode)
            assertEquals(PackId.Nord, ui.appearance.pack)
            assertEquals(listOf(PersonalChange.Mode(AppearanceMode.Light), PersonalChange.Pack(PackId.Nord)), choices.writes)
        }

    @Test
    fun `B-KIT-040 a different style clears the fine-tune choices, the same style writes nothing`() =
        rig(prefs = PersonalPrefs(style = SurfaceStyle.Soft, fineTuned = true)) {
            p.onStyle(SurfaceStyle.Soft); settle()
            assertEquals(emptyList(), choices.writes)
            p.onStyle(SurfaceStyle.Paper); settle()
            assertEquals(listOf<PersonalChange>(PersonalChange.Style(SurfaceStyle.Paper)), choices.writes)
            assertFalse(choices.value.value.fineTuned)
            assertEquals(SurfaceStyle.Paper, ui.appearance.style)
        }

    @Test
    fun `B-KIT-006 the motion choice is Full until chosen and the choice is written`() =
        rig {
            assertEquals(MotionLevel.Full, ui.appearance.motion)
            p.onMotion(MotionLevel.Calm); settle()
            assertEquals(MotionLevel.Calm, ui.appearance.motion)
            assertEquals(MotionLevel.Calm, choices.value.value.motion)
            p.onMotion(MotionLevel.Off); settle()
            assertEquals(MotionLevel.Off, ui.appearance.motion)
        }

    @Test
    fun `B-KIT-008 Saver holds style and motion and taps on them change nothing`() =
        rig(prefs = PersonalPrefs(mode = AppearanceMode.Saver)) {
            assertTrue(ui.appearance.heldBySaver)
            p.onStyle(SurfaceStyle.Brutal); settle()
            p.onMotion(MotionLevel.Calm); settle()
            assertEquals(emptyList(), choices.writes)
        }

    @Test
    fun `B-STATE-005 a refused write keeps the acknowledged value and says it was not saved`() =
        rig {
            choices.refuseNext = true
            p.onPack(PackId.Dracula); settle()
            assertEquals(PackId.Opencode, ui.appearance.pack)
            assertEquals(SettingsNote.AppearanceNotSaved, ui.note)
            p.onNoteSeen(); settle()
            assertNull(ui.note)
        }

    @Test
    fun `plan 07 I10 the reasoning and timestamps switches flip in place`() =
        rig {
            p.onRow(SettingsRowId.ShowReasoning); settle()
            p.onRow(SettingsRowId.ShowTimestamps); settle()
            assertTrue(choices.value.value.reasoning)
            assertTrue(choices.value.value.timestamps)
            assertEquals(SettingsPage.Hub, ui.page)
        }

    @Test
    fun `plan 07 I05 notification switches write the shared choice and a failed test says so`() =
        rig {
            p.onNotification(NotificationChange.WifiOnly(true)); settle()
            assertTrue(ui.notifications!!.wifiOnly)
            notifications.refuseTest = true
            p.onSendTestNotification(); settle()
            assertEquals(1, notifications.tests)
            assertEquals(SettingsNote.TestNotSent, ui.note)
        }

    @Test
    fun `plan 07 I20 Check now, the checks switch and the main action reach the updater`() =
        rig {
            p.onCheckForUpdates(); settle()
            p.onUpdateChecks(false); settle()
            p.onUpdateAction(); settle()
            assertEquals(listOf("check", "checks:false", "act"), updates.calls)
            assertFalse(ui.updates!!.checksOn)
        }

    @Test
    fun `B-NAV-081 a server that goes away takes its rows with it`() =
        rig {
            p.onOpen(SettingsPage.Group(SettingsGroupId.Agent)); settle()
            assertTrue(ui.group!!.rows.any { it.id == SettingsRowId.Providers })
            facts.value = facts.value.copy(server = null); settle()
            assertFalse(ui.group!!.rows.any { it.id == SettingsRowId.Providers })
        }
}
