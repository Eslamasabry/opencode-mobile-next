package io.github.eslamasabry.oc.android.voice

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.system.ErrnoException
import android.system.OsConstants
import org.robolectric.RuntimeEnvironment
import io.github.eslamasabry.oc.android.voice.audio.AndroidAudioSession
import io.github.eslamasabry.oc.android.voice.device.AndroidDeviceProbe
import io.github.eslamasabry.oc.android.voice.files.AndroidModelFiles
import io.github.eslamasabry.oc.android.voice.files.PlatformDigests
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.voice.audio.AudioInterruption
import io.github.eslamasabry.oc.engine.voice.audio.PlaybackLease
import io.github.eslamasabry.oc.engine.voice.models.DeviceProbeResult
import io.github.eslamasabry.oc.engine.voice.models.PureSha256
import io.github.eslamasabry.oc.engine.voice.models.StorageFullException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private class StepClock(var now: Instant = Instant.fromEpochMilliseconds(1_000_000)) : Clock {
    override fun now(): Instant = now
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // Robolectric 4.17 has no SDK 37 image; 35 is what the other modules use
class AndroidPlatformTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `N5 A1 A2 playback is refused for 10 s after any microphone start, then allowed`() =
        runTest {
            val clock = StepClock()
            var stops = 0
            val session = AndroidAudioSession(context, clock) { stops++; true }
            assertTrue(session.beforeCapture())
            assertEquals(1, stops)
            assertEquals(Failure.Media(MediaProblem.ReadAloudBusy).failed(), session.acquirePlayback())
            session.endCapture()
            clock.now += 10.seconds
            assertIs<Outcome.Ok<PlaybackLease>>(session.acquirePlayback())
        }

    @Test
    fun `N5 A3 playback asks transient focus for speech and any change but a gain stops the reader`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = AndroidAudioSession(context, StepClock())
            val lease = assertIs<Outcome.Ok<PlaybackLease>>(session.acquirePlayback()).value
            val request = session.playbackFocusRequest!!
            assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request.focusGain)
            assertEquals(AudioAttributes.USAGE_MEDIA, request.audioAttributes.usage)
            assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, request.audioAttributes.contentType)
            assertTrue(request.willPauseWhenDucked())
            val seen = mutableListOf<AudioInterruption>()
            val watch = launch { session.interruptions.collect { seen += it } }
            session.onPlaybackFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            session.onPlaybackFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            session.onHostPaused()
            assertEquals(listOf(AudioInterruption.FocusLost, AudioInterruption.AppLeftForeground), seen)
            lease.release()
            assertNull(session.playbackFocusRequest)
            session.onPlaybackFocusChange(AudioManager.AUDIOFOCUS_LOSS) // no lease: nothing to stop
            assertEquals(2, seen.size)
            watch.cancel()
        }

    @Test
    fun `N5 A8 capture asks full focus with delayed gain, abandons it, and a loss interrupts the capture`() =
        runTest {
            val session = AndroidAudioSession(context, StepClock())
            session.beforeCapture()
            val request = session.captureFocusRequest!!
            assertEquals(AudioManager.AUDIOFOCUS_GAIN, request.focusGain)
            assertTrue(request.acceptsDelayedFocusGain())
            assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, request.audioAttributes.contentType)
            var lost = 0
            val watcher = session.watchCaptureFocus { lost++ }
            session.onCaptureFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            session.onCaptureFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            session.onCaptureFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            session.onCaptureFocusChange(AudioManager.AUDIOFOCUS_GAIN)
            assertEquals(3, lost)
            watcher.close()
            session.endCapture()
            assertNull(session.captureFocusRequest)
        }

    @Test
    fun `B-MEDIA-032 on Android the probe reports total RAM, never the heap class, and the microphone feature`() =
        runBlocking {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            shadowOf(activityManager).setMemoryInfo(ActivityManager.MemoryInfo().apply { totalMem = 3_400L * 1024 * 1024; availMem = 900L * 1024 * 1024; threshold = 200L * 1024 * 1024 })
            shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_MICROPHONE, true)
            val probe = AndroidDeviceProbe(context, Dispatchers.IO)
            val known = assertIs<DeviceProbeResult.Known>(probe.probe())
            assertEquals(3_400L, known.totalRamMb)
            assertTrue(known.hasMicrophone)
            assertTrue(known.cpuCount >= 1)
            assertEquals(900L, probe.memoryNow()!!.availMb)
            assertEquals(200L, probe.memoryNow()!!.thresholdMb)
        }

    @Test
    fun `B-MEDIA-013 a full disk is no space, decided by errno, and reads seek`() {
        val files = AndroidModelFiles()
        val dir = File(context.filesDir, "voice_models/base-x").path
        files.createDirectories(dir)
        val path = "$dir/f.bin"
        files.openAppend(path).apply { write(ByteArray(3_000) { it.toByte() }, 0, 3_000); flush(); close() }
        val reader = files.openRead(path, offset = 2_990)
        val buffer = ByteArray(64)
        assertEquals(10, reader.read(buffer))
        assertEquals((2_990 % 256).toByte(), buffer[0])
        reader.close()
        files.move(path, "$dir/g.bin")
        assertEquals(3_000L, files.size("$dir/g.bin"))
        assertNull(files.size(path))
        val full = IOException("write failed", ErrnoException("write", OsConstants.ENOSPC))
        assertTrue(AndroidModelFiles.isNoSpace(full))
        assertFalse(AndroidModelFiles.isNoSpace(IOException("No space left on device"))) // never by message
        assertIs<StorageFullException>(runCatching { throw StorageFullException() }.exceptionOrNull())
    }

    @Test
    fun `B-MEDIA-025 the platform digest equals the portable one`() {
        val bytes = ByteArray(200_000) { (it * 31).toByte() }
        val platform = PlatformDigests.sha256().apply { update(bytes, 0, bytes.size) }.digestHex()
        val portable = PureSha256().apply { update(bytes) }.digestHex()
        assertEquals(portable, platform)
    }
}
