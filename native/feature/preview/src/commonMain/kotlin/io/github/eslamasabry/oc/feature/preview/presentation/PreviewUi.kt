package io.github.eslamasabry.oc.feature.preview.presentation

import io.github.eslamasabry.oc.ui.routes.PreviewPort
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What the running page is doing (mirrors the platform web view's phase). */
public enum class PagePhase {
    Loading,
    Ready,
    Failed,
}

/** One-shot words for the screen after an act. */
public enum class PreviewNotice {
    OpenFailed,
    LinkBlocked,
    AskFailed,
    Sent,
    Joined,
    AfterReply,
    Restarted,
}

/**
 * The preview page (Flutter `DevPreviewScreen`). [target] is null while blocked; [blocked] then
 * says why. [shown] is the address the page is on now. [leaveHost] asks "Open this link in your
 * browser?" for a link to elsewhere.
 */
public data class DevPreviewUi(
    val port: Int,
    val target: DevPreviewTarget? = null,
    val blocked: PreviewBlock? = null,
    val phase: PagePhase = PagePhase.Loading,
    val shown: String? = null,
    val canGoBack: Boolean = false,
    val leaveHost: String? = null,
    val details: Boolean = false,
    val menu: Boolean = false,
    val notice: PreviewNotice? = null,
    val canAsk: Boolean = false,
    val delivery: PreviewSent = PreviewSent.Now,
    val asking: Boolean = false,
    val askClear: Long = 0,
    val logsAvailable: Boolean = false,
    val logsOpen: Boolean = false,
    val logs: String = "",
    val logsFailed: Boolean = false,
    val copied: Long? = null,
    val copyText: String? = null,
) {
    /** The address the page was opened for. */
    public val origin: String?
        get() =
            when (val t = target) {
                is DevPreviewTarget.Phone -> t.url
                is DevPreviewTarget.Remote -> t.url
                else -> null
            }

    /** The host named in the "listen on 0.0.0.0" note, for a remote server only. */
    public val remoteHost: String?
        get() = (target as? DevPreviewTarget.Remote)?.host
}

public interface DevPreviewActions {
    public fun back()

    public fun openInBrowser()

    public fun menu(open: Boolean) {}

    public fun showDetails(open: Boolean)

    public fun pagePhase(phase: PagePhase)

    public fun pageAddress(url: String)

    public fun pageCanGoBack(can: Boolean)

    /** A person's navigation to another address: it did not load in the preview. */
    public fun pageLeave(url: String)

    public fun confirmLeave()

    public fun cancelLeave()

    public fun noticeShown()

    public fun ask(text: String) {}

    public fun copyAddress() {}

    public fun copied(id: Long) {}

    public fun showLogs(open: Boolean) {}

    public fun refreshLogs() {}
}

/** One mentioned port, with where it opens. [host] null = on this phone. */
public data class PortRow(
    val port: Int,
    val https: Boolean,
    val host: String?,
    val path: String = "/",
)

/** "Preview a running app" (Flutter chat dev-preview sheet). */
public data class PortsUi(
    val rows: ImmutableList<PortRow> = persistentListOf(),
    val blocked: PreviewBlock? = null,
    val invalidPort: Boolean = false,
)

public interface PortsActions {
    public fun back()

    public fun open(port: PreviewPort)

    /** The typed port: 1–65535, digits only. */
    public fun openTyped(text: String)
}
