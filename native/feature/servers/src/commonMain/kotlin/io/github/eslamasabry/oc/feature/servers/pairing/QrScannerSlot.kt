package io.github.eslamasabry.oc.feature.servers.pairing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject

/** Where the camera is (plan 10 §14.2 stages; `:android:camera` `ScannerStage`). */
public enum class ScanStage {
    Starting,
    Scanning,
    /** Android will ask again: "Allow camera" is honest. */
    Denied,
    /** Android will not ask again: only app settings can change it. */
    Blocked,
    NoCamera,
    Failed,
}

/**
 * The screen's line to the platform scanner. The scanner reports [stage]; the screen bumps a counter for each act the
 * person takes (Allow camera, Open app settings, Try again), and the scanner acts on it once.
 */
@Stable
public class QrScannerHandle {
    public var stage: ScanStage by mutableStateOf(ScanStage.Starting)
    public var allowRequests: Int by mutableIntStateOf(0)
    public var settingsRequests: Int by mutableIntStateOf(0)
    public var retries: Int by mutableIntStateOf(0)
}

/**
 * The camera viewfinder that reads pairing QR codes (CameraX + zxing-cpp on Android, bound in the app wiring; no ML
 * Kit). [accept] says whether decoded text is a pairing code (it runs off the main thread and keeps nothing); the first
 * accepted code stops the camera and reaches [onCode]; a code that is not one reaches [onRejected] once per sighting.
 * Where there is no scanner (desktop), [supported] is false and nothing is drawn.
 */
public interface QrScannerSlot {
    public val supported: Boolean

    @Composable
    public fun Viewfinder(
        handle: QrScannerHandle,
        accept: (String) -> Boolean,
        onCode: (String) -> Unit,
        onRejected: () -> Unit,
        modifier: Modifier,
    )
}

/** The default: nothing to scan with on this platform (B-ANDR-049: never a fake camera answer). */
@ContributesBinding(AppScope::class)
@Inject
public class NoQrScannerSlot : QrScannerSlot {
    override val supported: Boolean = false

    @Composable
    override fun Viewfinder(
        handle: QrScannerHandle,
        accept: (String) -> Boolean,
        onCode: (String) -> Unit,
        onRejected: () -> Unit,
        modifier: Modifier,
    ) {
    }
}
