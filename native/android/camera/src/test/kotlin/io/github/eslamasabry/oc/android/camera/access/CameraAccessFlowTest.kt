package io.github.eslamasabry.oc.android.camera.access

import io.github.eslamasabry.oc.android.camera.scan.FakeClock
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.domain.platform.AppPermission
import io.github.eslamasabry.oc.core.domain.platform.PermissionGate
import io.github.eslamasabry.oc.core.domain.platform.PermissionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private class FakeGate(
    var status: PermissionStatus = PermissionStatus.Denied,
    var answer: PermissionStatus = PermissionStatus.Denied,
    var throwOnRequest: Boolean = false,
) : PermissionGate {
    val asked = mutableListOf<AppPermission>()
    val settings = mutableListOf<AppPermission>()

    override suspend fun status(permission: AppPermission): PermissionStatus = status

    override suspend fun request(permission: AppPermission): PermissionStatus {
        asked += permission
        if (throwOnRequest) throw IllegalStateException("no activity")
        status = answer
        return answer
    }

    override fun openSettings(permission: AppPermission) {
        settings += permission
    }
}

private class FakeHardware(var camera: Boolean = true, var broken: Boolean = false) : CameraHardware {
    override fun hasCamera(): Boolean = if (broken) error("probe failed") else camera
}

class CameraAccessFlowTest {
    @Test
    fun `B-ANDR-049 no camera says so and never asks for permission`() = runTest {
        val gate = FakeGate()
        val flow = CameraAccessFlow(gate, FakeHardware(camera = false))
        assertEquals(ScannerStage.NoCamera, flow.open())
        assertEquals(ScannerStage.NoCamera, flow.recheck())
        assertTrue(gate.asked.isEmpty())
        assertEquals(listOf(ScannerAction.PasteInstead), ScannerStage.NoCamera.actions)
        assertEquals(MediaProblem.NoCamera, ScannerStage.NoCamera.problem)
    }

    @Test
    fun `B-ANDR-049 a camera probe that fails reports no camera, not a false yes`() = runTest {
        val gate = FakeGate(status = PermissionStatus.Granted)
        assertEquals(ScannerStage.NoCamera, CameraAccessFlow(gate, FakeHardware(broken = true)).open())
        assertTrue(gate.asked.isEmpty())
    }

    @Test
    fun `B-ANDR-049 a platform without the permission (desktop) reaches no camera code`() = runTest {
        val gate = FakeGate(status = PermissionStatus.NotApplicable, answer = PermissionStatus.NotApplicable)
        assertEquals(ScannerStage.NoCamera, CameraAccessFlow(gate, FakeHardware()).open())
        assertTrue(gate.asked.isEmpty())
    }

    @Test
    fun `granted already starts the camera without a prompt`() = runTest {
        val gate = FakeGate(status = PermissionStatus.Granted)
        assertEquals(ScannerStage.Starting, CameraAccessFlow(gate, FakeHardware()).open())
        assertTrue(gate.asked.isEmpty())
    }

    @Test
    fun `B-ANDR-049 a refusable denial explains, offers Allow camera and paste, and opens no settings`() = runTest {
        val gate = FakeGate(answer = PermissionStatus.Denied)
        val flow = CameraAccessFlow(gate, FakeHardware())
        assertEquals(ScannerStage.Denied, flow.open())
        assertEquals(listOf(AppPermission.Camera), gate.asked)
        assertEquals(listOf(ScannerAction.AllowCamera, ScannerAction.PasteInstead), ScannerStage.Denied.actions)
        assertEquals(MediaProblem.CameraDenied, ScannerStage.Denied.problem)
        assertTrue(gate.settings.isEmpty())
    }

    @Test
    fun `B-ANDR-049 retrying asks the platform again and can succeed or become permanent`() = runTest {
        val gate = FakeGate(answer = PermissionStatus.Denied)
        val flow = CameraAccessFlow(gate, FakeHardware())
        flow.open()
        gate.answer = PermissionStatus.PermanentlyDenied
        assertEquals(ScannerStage.PermanentlyDenied, flow.allowCamera())
        assertEquals(2, gate.asked.size)
        gate.answer = PermissionStatus.Granted
        assertEquals(ScannerStage.Starting, flow.allowCamera())
        assertEquals(3, gate.asked.size)
    }

    @Test
    fun `B-ANDR-049 a permanent denial offers app settings and no retry`() = runTest {
        val gate = FakeGate(answer = PermissionStatus.PermanentlyDenied)
        val flow = CameraAccessFlow(gate, FakeHardware())
        assertEquals(ScannerStage.PermanentlyDenied, flow.open())
        val actions = ScannerStage.PermanentlyDenied.actions
        assertFalse(ScannerAction.AllowCamera in actions)
        assertEquals(listOf(ScannerAction.OpenSettings, ScannerAction.PasteInstead), actions)
        assertEquals(MediaProblem.CameraDeniedForever, ScannerStage.PermanentlyDenied.problem)
        flow.openSettings()
        assertEquals(listOf(AppPermission.Camera), gate.settings)
    }

    @Test
    fun `coming back from settings re-reads the status without a prompt`() = runTest {
        val gate = FakeGate(answer = PermissionStatus.PermanentlyDenied)
        val flow = CameraAccessFlow(gate, FakeHardware())
        flow.open()
        gate.status = PermissionStatus.Granted // the person turned Camera on in app settings
        assertEquals(ScannerStage.Starting, flow.recheck())
        assertEquals(1, gate.asked.size)
        gate.status = PermissionStatus.Denied
        assertEquals(ScannerStage.Denied, flow.recheck())
        assertEquals(1, gate.asked.size)
    }

    @Test
    fun `B-ANDR-049 a permission request that fails never invents a grant`() = runTest {
        val gate = FakeGate(throwOnRequest = true)
        assertEquals(ScannerStage.Denied, CameraAccessFlow(gate, FakeHardware()).open())
    }

    @Test
    fun `a camera failure offers Try again and paste`() {
        assertEquals(listOf(ScannerAction.TryAgain, ScannerAction.PasteInstead), ScannerStage.Failed.actions)
        assertEquals(MediaProblem.CameraFailed, ScannerStage.Failed.problem)
        assertEquals(listOf(ScannerAction.PasteInstead), ScannerStage.Starting.actions)
        assertEquals(listOf(ScannerAction.PasteInstead), ScannerStage.Scanning.actions)
    }

    @Test
    fun `B-ANDR-049 camera errors become plain failures that never carry the exception message`() {
        val denied = CameraFailures.classify(SecurityException("camera service said /data/secret"))
        assertEquals(MediaProblem.CameraDenied, (denied as Failure.Media).problem)
        val failed = CameraFailures.classify(IllegalStateException("hal error at /dev/video0"))
        assertEquals(MediaProblem.CameraFailed, (failed as Failure.Media).problem)
        listOf(denied, failed).forEach { f ->
            val text = f.details?.text.orEmpty()
            assertFalse("/data" in text || "/dev" in text, text)
        }
        assertEquals(ScannerStage.Denied, CameraFailures.stageFor(denied))
        assertEquals(ScannerStage.Failed, CameraFailures.stageFor(failed))
        assertEquals(ScannerStage.NoCamera, CameraFailures.stageFor(Failure.Media(MediaProblem.NoCamera)))
        assertEquals(ScannerStage.PermanentlyDenied, CameraFailures.stageFor(Failure.Media(MediaProblem.CameraDeniedForever)))
        assertEquals(ScannerStage.Failed, CameraFailures.stageFor(Failure.Unexpected()))
    }

    @Test
    fun `starting says it is taking longer than usual after 8 s`() {
        val clock = FakeClock()
        val notice = StartingNotice(clock)
        assertFalse(notice.isSlow())
        clock.advance(7.99.seconds)
        assertFalse(notice.isSlow())
        clock.advance(0.01.seconds)
        assertTrue(notice.isSlow())
        assertEquals(StartingNotice.SLOW_AFTER, 8.seconds)
    }
}
