@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.SurfaceRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.android.camera.access.AndroidCameraHardware
import io.github.eslamasabry.oc.android.camera.camerax.CameraXScanner
import io.github.eslamasabry.oc.android.webview.KitWebPreview
import io.github.eslamasabry.oc.android.webview.PreviewEvents
import io.github.eslamasabry.oc.android.webview.PreviewGuard
import io.github.eslamasabry.oc.android.webview.PreviewPhase
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.GatewayFactory
import io.github.eslamasabry.oc.core.domain.ProbeTarget
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.security.AddressRules
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.feature.preview.presentation.BrowserTicket
import io.github.eslamasabry.oc.feature.preview.presentation.NoPreviewBrowser
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewBrowser
import io.github.eslamasabry.oc.feature.preview.presentation.PagePhase
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewEgress
import io.github.eslamasabry.oc.feature.preview.ui.NoWebPreviewSlot
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewEvents
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewHandle
import io.github.eslamasabry.oc.feature.preview.ui.WebPreviewSlot
import io.github.eslamasabry.oc.feature.servers.pairing.NoQrScannerSlot
import io.github.eslamasabry.oc.feature.servers.pairing.PairingAnswer
import io.github.eslamasabry.oc.feature.servers.pairing.PairingProbe
import io.github.eslamasabry.oc.feature.servers.pairing.QrScannerHandle
import io.github.eslamasabry.oc.feature.servers.pairing.QrScannerSlot
import io.github.eslamasabry.oc.feature.servers.pairing.ScanStage
import java.util.concurrent.Executor
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Unit PREVIEW-QR: the Android halves of the dev preview and the pairing scanner, and the one Android
 * ExternalLinkOpener (AGENTS invariant 8: ExternalLinkPolicy → ExternalLinkOpener; nothing else launches a URL).
 */

/** Opens an approved link in the person's browser. Only `ExternalLinkPolicy` makes an [ApprovedLink]. */
@ContributesBinding(AppScope::class)
@Inject
class AndroidExternalLinkOpener(private val application: Application) : ExternalLinkOpener {
    override fun open(link: ApprovedLink): Outcome<Unit> {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(link.uri))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            application.startActivity(intent)
            Outcome.Ok(Unit)
        } catch (_: ActivityNotFoundException) {
            Outcome.Failed(Failure.Device())
        } catch (_: SecurityException) {
            Outcome.Failed(Failure.Device())
        }
    }
}

/** The preview's way to the browser: ExternalLinkPolicy decides, ExternalLinkOpener opens. */
@ContributesBinding(AppScope::class, replaces = [NoPreviewBrowser::class])
@Inject
class PolicyPreviewBrowser(private val policy: ExternalLinkPolicy, private val opener: ExternalLinkOpener) : PreviewBrowser {
    override fun check(url: String, appAuthored: Boolean): BrowserTicket =
        when (val decision = policy.check(url, if (appAuthored) LinkOrigin.AppAuthored else LinkOrigin.ServerContent)) {
            is LinkDecision.Open -> BrowserTicket.Open(decision.link)
            is LinkDecision.AskFirst -> BrowserTicket.AskFirst(decision.shownHost, decision.link)
            is LinkDecision.Refuse -> BrowserTicket.Refused
        }

    override fun open(token: Any): Boolean = (token as? ApprovedLink)?.let { opener.open(it) is Outcome.Ok } ?: false
}

/** The dev preview's page: `:android:webview` [KitWebPreview] under the screen's [PreviewEgress]. */
@ContributesBinding(AppScope::class, replaces = [NoWebPreviewSlot::class])
@Inject
class AndroidWebPreviewSlot : WebPreviewSlot {
    override val supported: Boolean = true

    @Composable
    override fun Page(origin: String, egress: PreviewEgress, handle: WebPreviewHandle, events: WebPreviewEvents, modifier: Modifier) {
        val latest by rememberUpdatedState(events)
        key(origin) {
            val holder = remember { arrayOfNulls<KitWebPreview>(1) }
            DisposableEffect(Unit) {
                onDispose {
                    holder[0]?.destroy()
                    holder[0] = null
                    handle.reload = {}
                    handle.goBack = { false }
                }
            }
            AndroidView(
                factory = { context ->
                    val guard =
                        object : PreviewGuard {
                            override fun mayLoad(url: String): Boolean = egress.mayLoad(url)

                            override fun methodAllowed(method: String): Boolean = egress.methodAllowed(method)

                            override val documentPolicy: String = egress.documentPolicy
                        }
                    val sink =
                        object : PreviewEvents {
                            override fun phase(phase: PreviewPhase) =
                                latest.phase(
                                    when (phase) {
                                        PreviewPhase.Loading -> PagePhase.Loading
                                        PreviewPhase.Ready -> PagePhase.Ready
                                        PreviewPhase.Failed -> PagePhase.Failed
                                    },
                                )

                            override fun address(url: String) = latest.address(url)

                            override fun canGoBack(can: Boolean) = latest.canGoBack(can)

                            override fun leave(url: String) = latest.leave(url)
                        }
                    val preview = KitWebPreview(context, origin, guard, sink)
                    holder[0] = preview
                    handle.reload = preview::reload
                    handle.goBack = preview::goBack
                    preview.view
                },
                modifier = modifier,
            )
        }
    }
}

/**
 * The pairing scanner: `:android:camera` [CameraXScanner] (CameraX + zxing-cpp, no ML Kit) drawn into a TextureView,
 * with the camera permission in three states (B-ANDR-049): asked on opening and on "Allow camera"; refused for good →
 * app settings, rechecked on return; no camera → the screen says so and nothing is asked.
 */
@ContributesBinding(AppScope::class, replaces = [NoQrScannerSlot::class])
@Inject
class AndroidQrScannerSlot(private val application: Application, private val clock: Clock) : QrScannerSlot {
    override val supported: Boolean
        get() = AndroidCameraHardware(application).hasCamera()

    @Composable
    override fun Viewfinder(
        handle: QrScannerHandle,
        accept: (String) -> Boolean,
        onCode: (String) -> Unit,
        onRejected: () -> Unit,
        modifier: Modifier,
    ) {
        val context = LocalContext.current
        val activity = remember(context) { context.findComponentActivity() }
        if (activity == null) {
            LaunchedEffect(Unit) { handle.stage = ScanStage.Failed }
            return
        }
        fun allowed() = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        var granted by remember { mutableStateOf(allowed()) }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                granted = ok
                if (!ok) {
                    handle.stage =
                        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) ScanStage.Denied
                        else ScanStage.Blocked
                }
            }
        LaunchedEffect(handle.allowRequests) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
        LaunchedEffect(handle.settingsRequests) {
            if (handle.settingsRequests > 0) {
                val settings =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
                try {
                    activity.startActivity(settings)
                } catch (_: ActivityNotFoundException) {
                    handle.stage = ScanStage.Failed
                }
            }
        }
        // Back from app settings: recheck without asking again.
        DisposableEffect(activity) {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME && !granted && allowed()) granted = true
                }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
        if (!granted) return
        key(handle.retries) { Camera(activity, handle, accept, onCode, onRejected, modifier) }
    }

    @Composable
    private fun Camera(
        activity: ComponentActivity,
        handle: QrScannerHandle,
        accept: (String) -> Boolean,
        onCode: (String) -> Unit,
        onRejected: () -> Unit,
        modifier: Modifier,
    ) {
        val accepts by rememberUpdatedState(accept)
        val code by rememberUpdatedState(onCode)
        val rejected by rememberUpdatedState(onRejected)
        val owner = remember { ScopedLifecycle(activity.lifecycle) }
        val scanner =
            remember(owner) {
                CameraXScanner(
                    context = activity,
                    owner = owner,
                    clock = clock,
                    parse = { text: String -> text.takeIf { accepts(it) } },
                    onLatched = { text -> code(text) },
                    onRejected = { rejected() },
                    onFailure = { handle.stage = ScanStage.Failed },
                )
            }
        DisposableEffect(owner) {
            handle.stage = ScanStage.Starting
            owner.start()
            onDispose { owner.destroy() }
        }
        val request by scanner.surfaceRequests.collectAsState()
        LaunchedEffect(request) { if (request != null) handle.stage = ScanStage.Scanning }
        AndroidView(
            factory = { context -> TextureView(context) },
            update = { view -> request?.let { attach(view, it) } },
            modifier = modifier,
        )
    }
}

/** Follows the Activity's lifecycle while the scanner is on screen, then ends, so the camera never outlives the page. */
private class ScopedLifecycle(private val parent: Lifecycle) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = registry

    private val mirror = LifecycleEventObserver { _, event ->
        if (event != Lifecycle.Event.ON_DESTROY) registry.handleLifecycleEvent(event)
    }

    fun start() = parent.addObserver(mirror)

    fun destroy() {
        parent.removeObserver(mirror)
        if (registry.currentState != Lifecycle.State.INITIALIZED) registry.currentState = Lifecycle.State.DESTROYED
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is Activity -> null
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

/** Hands the camera a surface on [view] once per [request], filling the view without stretching. */
private fun attach(view: TextureView, request: SurfaceRequest) {
    if (view.tag === request) return
    view.tag = request
    val size = request.resolution
    val onView = Executor { view.post(it) }
    fun provide(texture: SurfaceTexture) {
        texture.setDefaultBufferSize(size.width, size.height)
        val surface = Surface(texture)
        request.provideSurface(surface, onView) { surface.release() }
        fit(view, size)
    }
    view.surfaceTextureListener =
        object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                if (view.tag === request) provide(texture)
            }

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fit(view, size)

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
        }
    view.surfaceTexture?.let(::provide)
}

/** Centre-crop: the camera frame (sensor landscape, shown upright) scaled to cover the view. */
private fun fit(view: TextureView, size: Size) {
    val width = view.width.toFloat()
    val height = view.height.toFloat()
    if (width <= 0f || height <= 0f) return
    val rotation = view.display?.rotation ?: Surface.ROTATION_0
    val portrait = rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180
    val frameW = (if (portrait) size.height else size.width).toFloat()
    val frameH = (if (portrait) size.width else size.height).toFloat()
    val scale = maxOf(width / frameW, height / frameH)
    view.setTransform(Matrix().apply { setScale(frameW * scale / width, frameH * scale / height, width / 2f, height / 2f) })
}

/**
 * P12-FIX-PREVIEWQR: a pairing code's addresses are asked in turn with the protocol's own health/version probe, which
 * never sends a credential to an origin that is not trusted yet (B-SEC-124); the form then fills the one that works,
 * or that at least answered (B-PROTO-068). A probe that does not finish in time counts as no answer.
 */
@ContributesBinding(AppScope::class)
@Inject
class GatewayPairingProbe(private val factories: Set<GatewayFactory>) : PairingProbe {
    override suspend fun check(address: String): PairingAnswer {
        val origin = (AddressRules.normalize(address) as? Outcome.Ok)?.value ?: return PairingAnswer.NoAnswer
        val factory =
            factories.firstOrNull { it.backend == BackendKind.OpenCode2 } ?: factories.firstOrNull() ?: return PairingAnswer.NoAnswer
        val result = withTimeoutOrNull(PROBE_TIMEOUT) { factory.probe(ProbeTarget(origin, credentials = null)) }
        return when (result) {
            null -> PairingAnswer.NoAnswer
            is Outcome.Ok -> PairingAnswer.Works
            is Outcome.Failed ->
                when (result.failure) {
                    is Failure.SignIn, is Failure.Server, is Failure.Incompatible, is Failure.Rejected, is Failure.NotFound ->
                        PairingAnswer.Answers
                    else -> PairingAnswer.NoAnswer
                }
        }
    }

    private companion object {
        val PROBE_TIMEOUT = 8.seconds
    }
}
