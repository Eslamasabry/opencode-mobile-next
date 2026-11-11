package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.engine.settings.consent.ConsentChoice
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentRow
import io.github.eslamasabry.oc.feature.settings.presentation.ConfigurationSources
import io.github.eslamasabry.oc.feature.settings.presentation.NeedsYouAlerts
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.followingLive
import io.github.eslamasabry.oc.feature.settings.presentation.needsYouAlerts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** w4-settings-truth (sweep-b 2026-11-11): two Settings pages never disagree, and no page is developer content. */
class SettingsTruthTest {
    private val needsYou = InFlowConsentKind.NeedsYouNotifications

    @Test
    fun `N1 the needs-you state derives from the Notifications facts and the background connection`() {
        val on = NotificationFacts()
        assertEquals(NeedsYouAlerts.On, needsYouAlerts(on, backgroundOn = true))
        assertEquals(NeedsYouAlerts.OnlyWhileOpen, needsYouAlerts(on, backgroundOn = false))
        assertEquals(NeedsYouAlerts.QuestionsOff, needsYouAlerts(on.copy(questions = false), true))
        assertEquals(NeedsYouAlerts.AndroidBlocks, needsYouAlerts(on.copy(blocked = true), true))
        assertEquals(NeedsYouAlerts.AndroidBlocks, needsYouAlerts(on.copy(questionsAllowed = false), true))
    }

    @Test
    fun `N1 a declined first ask reads On once questions reach the person, and an old yes reads Off when they do not`() {
        val declined = InFlowConsentRow(needsYou, ConsentChoice.Denied)
        val allowed = InFlowConsentRow(needsYou, ConsentChoice.Accepted)
        // The sweep's phone: "Questions for you" on, yet the row said Off from the answer given at first start.
        assertEquals(ConsentChoice.Accepted, declined.followingLive(NeedsYouAlerts.On).choice)
        assertEquals(ConsentChoice.Denied, allowed.followingLive(NeedsYouAlerts.QuestionsOff).choice)
        assertEquals(ConsentChoice.Denied, allowed.followingLive(NeedsYouAlerts.OnlyWhileOpen).choice)
        // An unfinished ask stays unfinished; an unasked one stays hidden; an unread state keeps the record.
        val offered = InFlowConsentRow(needsYou, ConsentChoice.Offered)
        assertEquals(ConsentChoice.Offered, offered.followingLive(NeedsYouAlerts.QuestionsOff).choice)
        assertEquals(ConsentChoice.Unseen, InFlowConsentRow(needsYou, ConsentChoice.Unseen).followingLive(NeedsYouAlerts.On).choice)
        assertEquals(declined, declined.followingLive(null))
        // Other answers are not notifications and keep what was answered.
        val battery = InFlowConsentRow(InFlowConsentKind.BatteryExemption, ConsentChoice.Denied)
        assertEquals(battery, battery.followingLive(NeedsYouAlerts.On))
    }

    @Test
    fun `N1 the row and the Notifications page can never contradict each other`() {
        for (blocked in listOf(false, true)) for (questions in listOf(false, true)) for (allowed in listOf(false, true))
            for (background in listOf(false, true)) for (stored in listOf(ConsentChoice.Accepted, ConsentChoice.Denied)) {
                val facts = NotificationFacts(blocked = blocked, questions = questions, questionsAllowed = allowed)
                val row = InFlowConsentRow(needsYou, stored).followingLive(needsYouAlerts(facts, background))
                val rowOn = row.choice == ConsentChoice.Accepted
                // The page shows "Questions for you" switched on and Android letting it through.
                val pageOn = !blocked && questions && allowed
                if (rowOn) assertTrue(pageOn && background, "row On while the page says otherwise: $facts")
                if (pageOn && background) assertTrue(rowOn, "row Off while every switch is on: $facts")
            }
    }

    @Test
    fun `S4 a server with no configuration file lists only the folders it looks in`() {
        val ready =
            ConfigurationSources.ready(
                """{"sources":[{"type":"directory","path":"/srv/a/.config/opencode"},{"type":"directory","path":"/srv/a/project"}]}"""
            )
        assertEquals(emptyList(), ready.files)
        assertEquals(listOf("/srv/a/.config/opencode", "/srv/a/project"), ready.folders)
    }

    @Test
    fun `S4 a document entry is a file, and its contents never reach the lists`() {
        val secret = "sk-ant-api03-SECRETVALUE0123456789"
        val text =
            """{"sources":[{"type":"directory","path":"/srv/a/.config/opencode"},""" +
                """{"type":"document","path":"/srv/a/.config/opencode/opencode.json",""" +
                """"info":{"provider":{"anthropic":{"options":{"apiKey":"$secret"}}}}}]}"""
        val ready = ConfigurationSources.ready(text)
        assertEquals(listOf("/srv/a/.config/opencode/opencode.json"), ready.files)
        assertEquals(listOf("/srv/a/.config/opencode"), ready.folders)
        assertFalse((ready.files + ready.folders).any { secret in it })
    }

    @Test
    fun `S4 an answer that is not the expected shape lists nothing rather than failing`() {
        val ready = ConfigurationSources.ready("not json")
        assertEquals(emptyList(), ready.files)
        assertEquals(emptyList(), ready.folders)
    }
}
