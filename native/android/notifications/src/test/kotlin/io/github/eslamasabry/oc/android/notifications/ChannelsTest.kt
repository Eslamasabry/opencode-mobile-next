package io.github.eslamasabry.oc.android.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Channels (plan 11 §10.1, ids frozen by plan 05 §7.9) and the seven languages (§10.7, D11-4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelsTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private var tag: String? = "en"
    private val words = NotifyWords(app) { tag }
    private val registry = ChannelRegistry(manager, words)

    @Test
    fun `plan 05 7_9 channel ids and importances are the Flutter app's, verbatim`() {
        assertEquals(
            listOf(
                "opencode_live_connection" to NotificationManager.IMPORTANCE_LOW,
                "opencode_coding_action" to NotificationManager.IMPORTANCE_HIGH,
                "opencode_coding_action_quiet" to NotificationManager.IMPORTANCE_LOW,
                "opencode_coding_status" to NotificationManager.IMPORTANCE_DEFAULT,
                "opencode_team_progress" to NotificationManager.IMPORTANCE_LOW,
                "opencode_phone_setup" to NotificationManager.IMPORTANCE_LOW,
                "opencode_builtin_server" to NotificationManager.IMPORTANCE_LOW,
                "opencode_scheduled_prompts" to NotificationManager.IMPORTANCE_DEFAULT,
                "opencode_voice_setup" to NotificationManager.IMPORTANCE_LOW,
                "bb9_qa_producer" to NotificationManager.IMPORTANCE_LOW,
            ),
            NotifyChannel.entries.map { it.id to it.importance },
        )
    }

    @Test
    fun `H3 ensure creates only missing ids of shipped features and never team progress or QA`() {
        val report = registry.ensure()
        assertEquals(8, report.created.size)
        assertNull(manager.getNotificationChannel("opencode_team_progress"))
        assertNull(manager.getNotificationChannel("bb9_qa_producer"))
        val live = manager.getNotificationChannel("opencode_live_connection")
        assertEquals("Live coding connection", live.name)
        assertFalse(live.canShowBadge())
        assertEquals(Notification.VISIBILITY_PRIVATE, manager.getNotificationChannel("opencode_coding_action").lockscreenVisibility)
        // A second run creates nothing.
        assertEquals(emptyList(), registry.ensure().created)
    }

    @Test
    fun `H3 an existing channel keeps the person's choices and an existing team channel is left alone`() {
        // The Flutter app created these; the person turned requests down to silent.
        manager.createNotificationChannel(NotificationChannel("opencode_coding_action", "Coding requests", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("opencode_team_progress", "AI Team progress", NotificationManager.IMPORTANCE_LOW))
        manager.getNotificationChannel("opencode_coding_action").let {
            it.importance = NotificationManager.IMPORTANCE_LOW
            manager.createNotificationChannel(it)
        }
        val report = registry.ensure()
        assertTrue("opencode_coding_action" in report.kept)
        assertTrue("opencode_team_progress" in report.kept)
        assertFalse("opencode_coding_action" in report.created)
        assertEquals(NotificationManager.IMPORTANCE_LOW, manager.getNotificationChannel("opencode_coding_action").importance)
    }

    @Test
    fun `B-L10N-002 relocalize renames channels in place in the app language and keeps the person's settings`() {
        registry.ensure()
        manager.getNotificationChannel("opencode_coding_status").let {
            it.importance = NotificationManager.IMPORTANCE_NONE
            manager.createNotificationChannel(it)
        }
        tag = "ar"
        assertEquals(8, registry.relocalize())
        val status = manager.getNotificationChannel("opencode_coding_status")
        assertEquals(arabic(R.string.notify_coding_status_channel), status.name)
        assertEquals(NotificationManager.IMPORTANCE_NONE, status.importance)
        assertTrue(registry.isOff(NotifyChannel.CodingStatus))
    }

    @Test
    fun `B-L10N-013 every channel name and alert word exists in all seven app languages`() {
        val english = NotifyWords(app) { "en" }
        val ids =
            NotifyChannel.entries.map { it.nameRes } + NotifyChannel.entries.mapNotNull { it.descriptionRes } +
                listOf(R.string.notify_allow_once, R.string.notify_reject, R.string.notify_answer_in_app, R.string.notify_public_needs_you)
        for (lang in listOf("ar", "es", "ja", "pt", "ru", "zh")) {
            val other = NotifyWords(app) { lang }
            for (id in ids.distinct()) {
                assertTrue(other.text(id).isNotBlank(), "$lang ${app.resources.getResourceEntryName(id)}")
                if (lang != "en") {
                    kotlin.test.assertNotEquals(english.text(id), other.text(id), "$lang ${app.resources.getResourceEntryName(id)}")
                }
            }
            // Arabic formats the count with Arabic-Indic digits.
            val chip = other.plural(R.plurals.notify_chip_running, 3, 3)
            assertTrue("3" in chip || "٣" in chip, "$lang $chip")
        }
        assertEquals("セットアップ", NotifyWords(app) { "ja" }.text(R.string.notify_setup_channel))
        assertEquals("Permitir una vez", NotifyWords(app) { "es" }.text(R.string.notify_allow_once))
        assertEquals("Разрешить один раз", NotifyWords(app) { "ru" }.text(R.string.notify_allow_once))
        assertEquals("仅允许一次", NotifyWords(app) { "zh-Hans-CN" }.text(R.string.notify_allow_once))
        assertEquals("Permitir uma vez", NotifyWords(app) { "pt-BR" }.text(R.string.notify_allow_once))
    }

    @Test
    fun `B-L10N-013 an unsupported or missing app language falls back to the system language`() {
        assertEquals("Allow once", NotifyWords(app) { "de" }.text(R.string.notify_allow_once))
        assertEquals("Allow once", NotifyWords(app) { null }.text(R.string.notify_allow_once))
        assertEquals("Allow once", NotifyWords(app) { "" }.text(R.string.notify_allow_once))
    }

    private fun arabic(id: Int) = NotifyWords(app) { "ar" }.text(id)
}
