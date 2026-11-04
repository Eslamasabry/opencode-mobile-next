package io.github.eslamasabry.oc.android.background

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.lane.DefaultWriterLanes
import io.github.eslamasabry.oc.core.data.migration.StateMigrationGate
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowActivityManager.ApplicationExitInfoBuilder

// The Android halves on Robolectric: the receipt file and the Flutter opt-in key on disk, the service port's
// permission/start/acknowledgement contract, and the copy in English and Arabic.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBackgroundTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        for (file in listOf("FlutterSharedPreferences", SharedPreferencesReceiptFile.FILE)) {
            context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private class Permissions(var status: PermissionStatus, var answer: PermissionStatus = status) : PermissionGate {
        var requests = 0

        override suspend fun status(permission: AppPermission): PermissionStatus = status

        override suspend fun request(permission: AppPermission): PermissionStatus {
            requests++
            return answer
        }

        override fun openSettings(permission: AppPermission) = Unit
    }

    private class Starter(val onStart: () -> Unit) : LiveServiceStarter {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
            onStart()
        }

        override fun stop() {
            stops++
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.stores(): Triple<PauseReceiptStore, KeyValueLiveOptIn, () -> Any?> {
        val prefs = AndroidDataStores.flutterPrefs(context, StateMigrationGate.alwaysOpen(), UnconfinedTestDispatcher(testScheduler))
        prefs.warm()
        val optIn = KeyValueLiveOptIn(prefs, DefaultWriterLanes().app)
        val receipts = PauseReceiptStore(SharedPreferencesReceiptFile(context), AndroidPauseFacts(context, fixedClock), optIn)
        val physical = { context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE).all["flutter.oc.keepLiveInBackground"] }
        return Triple(receipts, optIn, physical)
    }

    @Test
    fun `B-ANDR-015 a time-limit pause survives a restart on disk and turns the Flutter opt-in key off`() = runTest {
        val (receipts, optIn, physical) = stores()
        assertTrue(receipts.confirmedStarted())
        assertEquals(true, physical(), "the Flutter key, verbatim, so a migrated install keeps its choice")
        assertTrue(receipts.timeout())
        assertEquals(false, physical())
        assertEquals(false, optIn.read())
        val (reopened, _, _) = stores()
        val state = reopened.status(active = false)
        assertTrue(state.paused)
        assertEquals(PauseReason.TimeLimit, state.reason)
        assertEquals(fixedClock.now().toEpochMilliseconds(), state.atMs)
        // A restarted controller over the reopened stores stays paused despite any old opt-in.
        val controller =
            LiveModeController(
                FakeLivePort().apply { onPause = { ok(reopened.status(active = false)) } },
                optIn, MutableSupport(), backgroundScope, fixedClock,
            )
        controller.restore()
        assertFalse(controller.state.value.enabled)
        assertEquals(PauseReason.TimeLimit, controller.state.value.pause.reason)
    }

    @Test
    fun `B-ANDR-017 a damaged receipt file reads as unreadable`() = runTest {
        context
            .getSharedPreferences(SharedPreferencesReceiptFile.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString("phase", "running")
            .putString("reason", "none")
            .putString("at", "soon")
            .commit()
        val (receipts, _, _) = stores()
        assertFalse(receipts.status(active = false).supported)
    }

    @Test
    fun `B-ANDR-017 only this process's own exit record is evidence, never a helper process`() {
        val manager = context.getSystemService(ActivityManager::class.java)
        shadowOf(manager).addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setProcessName(context.packageName + ":helper")
                .setReason(ApplicationExitInfo.REASON_USER_REQUESTED)
                .setTimestamp(9000L)
                .build(),
        )
        shadowOf(manager).addApplicationExitInfo(
            ApplicationExitInfoBuilder.newBuilder()
                .setProcessName(context.packageName)
                .setReason(ApplicationExitInfo.REASON_USER_REQUESTED)
                .setTimestamp(2000L)
                .build(),
        )
        assertEquals(PauseExit(ApplicationExitInfo.REASON_USER_REQUESTED, 2000L), AndroidPauseFacts(context, fixedClock).latestExit())
    }

    @Test
    fun `B-ANDR-012 a denied notification permission refuses the start before any service is asked`() = runTest {
        val (receipts, _, _) = stores()
        val signals = LiveServiceSignals()
        val starter = Starter { signals.active = true }
        val permissions = Permissions(PermissionStatus.Denied)
        val port = AndroidLiveServicePort(context, signals, receipts, permissions, starter)
        val reply = assertIs<ServiceReply.Refused>(port.enable())
        assertEquals(ServiceRefusal.NOTIFICATION_DENIED, reply.refusal.code)
        assertEquals(1, permissions.requests)
        assertEquals(0, starter.starts)
    }

    @Test
    fun `B-ANDR-012 live mode turns on only once Android confirms the foreground start`() = runTest {
        val (receipts, optIn, _) = stores()
        val signals = LiveServiceSignals()
        val body = LiveServiceBody(signals, receipts) { _, _ -> }
        var promised = true
        // The shell answers on its own thread after the start request; the port polls for it.
        val starter = Starter { backgroundScope.launch { body.onForegroundResult(promised) } }
        val port = AndroidLiveServicePort(context, signals, receipts, Permissions(PermissionStatus.Granted), starter)
        val controller = LiveModeController(port, optIn, MutableSupport(), backgroundScope, fixedClock)
        assertTrue(controller.setEnabled(true))
        assertTrue(controller.state.value.active)
        assertTrue(controller.state.value.pause.active)
        assertEquals(true, optIn.read())

        // The shell's startForeground refused (Android 15's spent budget): a named refusal, never a crash or an on.
        controller.setEnabled(false)
        promised = false
        val refused = assertIs<ServiceReply.Ok<ServiceStatus>>(port.enable())
        assertFalse(refused.value.active)
        assertEquals(LiveServiceSignals.START_REFUSED, refused.value.startFailure)
        assertFalse(controller.setEnabled(true))
        assertEquals(false, optIn.read())
    }

    @Test
    fun `B-ANDR-012 an unanswered start reports ackTimeout and a throwing start reports foreground_service_failed`() = runTest {
        val (receipts, _, _) = stores()
        val signals = LiveServiceSignals()
        val silent = AndroidLiveServicePort(context, signals, receipts, Permissions(PermissionStatus.Granted), Starter {})
        val reply = assertIs<ServiceReply.Ok<ServiceStatus>>(silent.enable())
        assertEquals(LiveServiceSignals.START_ACK_TIMEOUT, reply.value.startFailure)
        val throwing =
            AndroidLiveServicePort(
                context, signals, receipts, Permissions(PermissionStatus.Granted),
                Starter { throw IllegalStateException("not allowed") },
            )
        assertEquals(
            ServiceRefusal.FOREGROUND_SERVICE_FAILED,
            assertIs<ServiceReply.Refused>(throwing.enable()).refusal.code,
        )
    }

    @Test
    fun `B-ANDR-014 the service timeout saves the pause before telling the app`() = runTest {
        val (receipts, optIn, physical) = stores()
        val signals = LiveServiceSignals()
        val told = mutableListOf<Pair<String, PauseState?>>()
        val body = LiveServiceBody(signals, receipts) { reason, pause -> told += reason to pause }
        assertTrue(body.onForegroundResult(promised = true))
        assertTrue(signals.active)
        body.onTimeout()
        assertFalse(signals.active)
        assertEquals(false, physical())
        assertEquals(LiveModeController.REASON_SYSTEM_TIMEOUT, told.single().first)
        assertEquals(PauseReason.TimeLimit, told.single().second?.reason)
        assertEquals(false, optIn.read())
    }

    @Test
    fun `B-ANDR-018 the pause notice words in English`() {
        assertEquals(
            "Background connection paused at 3:10 PM to save battery.",
            context.getString(BackgroundWords.message(PauseMessage.TimeLimit), "at 3:10 PM"),
        )
        assertEquals("Resume", context.getString(BackgroundWords.action(PauseAction.Resume)))
        assertEquals("Resume background connection", context.getString(BackgroundWords.actionSemantics(PauseAction.Resume)))
        assertEquals("Notification access is required.", context.getString(BackgroundWords.error(LiveModeError.NotificationsRequired)))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `B-ANDR-018 the pause notice says it in Arabic`() {
        assertEquals("توقف الاتصال في الخلفية لسبب غير معروف.", context.getString(BackgroundWords.message(PauseMessage.Interrupted)))
        assertEquals("استئناف", context.getString(BackgroundWords.action(PauseAction.Resume)))
    }
}
