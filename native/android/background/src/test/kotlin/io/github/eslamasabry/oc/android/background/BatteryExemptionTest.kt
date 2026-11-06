package io.github.eslamasabry.oc.android.background

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// E07-S01: live mode's "Allow unrestricted battery" (B-ANDR-012, the live-mode native contract). Android only shows
// its question to an app that declares REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; when it refuses anyway, the battery
// settings page opens instead and the screen says so.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class BatteryExemptionTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    private object Granted : PermissionGate {
        override suspend fun status(permission: AppPermission) = PermissionStatus.Granted

        override suspend fun request(permission: AppPermission) = PermissionStatus.Granted

        override fun openSettings(permission: AppPermission) = Unit
    }

    private object NoStarter : LiveServiceStarter {
        override fun start() = Unit

        override fun stop() = Unit
    }

    private fun port(open: (Intent) -> Unit): AndroidLiveServicePort =
        AndroidLiveServicePort(
            context,
            LiveServiceSignals(),
            PauseReceiptStore(SharedPreferencesReceiptFile(context), AndroidPauseFacts(context, fixedClock), FakeOptIn()),
            Granted,
            NoStarter,
            openActivity = open,
        )

    @Test
    fun `B-ANDR-012 the merged manifest declares the battery exemption permission`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        assertTrue(
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" in requested,
            "requested permissions: $requested",
        )
    }

    @Test
    fun `B-ANDR-012 Allow unrestricted battery opens Android's own question for this app`() = runTest {
        val opened = mutableListOf<Intent>()
        val reply = assertIs<ServiceReply.Ok<ServiceStatus>>(port { opened += it }.requestBatteryOptimizationExemption())
        assertFalse(reply.value.batterySettingsInstead)
        val question = opened.single()
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, question.action)
        assertEquals("package:${context.packageName}", question.dataString)
        assertTrue(question.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `B-ANDR-012 a refused battery question opens the battery settings page instead and says so`() = runTest {
        val opened = mutableListOf<String?>()
        val reply =
            assertIs<ServiceReply.Ok<ServiceStatus>>(
                port {
                        if (it.action == Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            throw ActivityNotFoundException()
                        opened += it.action
                    }
                    .requestBatteryOptimizationExemption()
            )
        assertTrue(reply.value.batterySettingsInstead)
        assertEquals(listOf<String?>(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), opened)
    }

    @Test
    fun `B-ANDR-012 without a battery page the app's own details page opens, and nothing at all is a refusal`() =
        runTest {
            val opened = mutableListOf<String?>()
            val details =
                assertIs<ServiceReply.Ok<ServiceStatus>>(
                    port {
                            if (it.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) throw SecurityException()
                            opened += it.dataString
                        }
                        .requestBatteryOptimizationExemption()
                )
            assertTrue(details.value.batterySettingsInstead)
            assertEquals(listOf<String?>("package:${context.packageName}"), opened)

            val nothing = port { throw ActivityNotFoundException() }.requestBatteryOptimizationExemption()
            assertEquals(ServiceRefusal.UNKNOWN, assertIs<ServiceReply.Refused>(nothing).refusal.code)
        }

    @Test
    fun `B-ANDR-012 the controller says the settings page opened instead until battery use is unrestricted`() =
        runTest {
            val port = FakeLivePort()
            port.onBattery = { ok(status(true).copy(batterySettingsInstead = true)) }
            val c = liveMode(backgroundScope, port, FakeOptIn(true))
            assertTrue(c.requestBatteryOptimizationExemption())
            assertTrue(c.state.value.batterySettingsOpenedInstead)

            // A plain status read keeps the note; the person coming back with it allowed clears it.
            port.onStatus = { ok(status(true)) }
            c.refreshStatus()
            assertTrue(c.state.value.batterySettingsOpenedInstead)
            port.onStatus = { ok(status(true).copy(batteryOptimizationIgnored = true)) }
            c.refreshStatus()
            assertFalse(c.state.value.batterySettingsOpenedInstead)

            // The question showing normally never carries the note.
            port.onBattery = { ok(status(true)) }
            c.requestBatteryOptimizationExemption()
            assertFalse(c.state.value.batterySettingsOpenedInstead)
        }
}
