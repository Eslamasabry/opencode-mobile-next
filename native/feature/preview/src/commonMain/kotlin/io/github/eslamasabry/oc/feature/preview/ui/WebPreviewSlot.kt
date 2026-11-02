package io.github.eslamasabry.oc.feature.preview.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.feature.preview.presentation.PagePhase
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewEgress

/** What the page tells the screen. */
public interface WebPreviewEvents {
    public fun phase(phase: PagePhase)

    public fun address(url: String)

    public fun canGoBack(can: Boolean)

    public fun leave(url: String)
}

/** The screen's handle on the page: the slot fills in [reload] and [goBack] while it is shown. */
public class WebPreviewHandle {
    public var reload: () -> Unit = {}
    public var goBack: () -> Boolean = { false }
}

/**
 * The platform web view that shows one origin under [PreviewEgress]'s rules. Android binds `:android:webview` in the app
 * wiring; where there is none (desktop) [supported] is false and the screen explains it instead.
 */
public interface WebPreviewSlot {
    public val supported: Boolean

    @Composable
    public fun Page(origin: String, egress: PreviewEgress, handle: WebPreviewHandle, events: WebPreviewEvents, modifier: Modifier)
}

/** The default: no web view on this platform. */
@ContributesBinding(AppScope::class)
@Inject
public class NoWebPreviewSlot : WebPreviewSlot {
    override val supported: Boolean = false

    @Composable
    override fun Page(origin: String, egress: PreviewEgress, handle: WebPreviewHandle, events: WebPreviewEvents, modifier: Modifier) {
    }
}
