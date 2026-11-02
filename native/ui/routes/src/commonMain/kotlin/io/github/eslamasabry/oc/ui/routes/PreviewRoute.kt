package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * The in-app preview of a dev server (unit PREVIEW-QR; plan 07 `ProjectRoute.DevPreview`). Keys hold only a port and its
 * scheme, never a host, a path or text from the conversation: the preview resolves the host from the saved server.
 */
@Serializable
public sealed interface PreviewRoute : AppRoute {
    /** "Preview a running app": the ports [ref]'s conversation mentioned, newest first, plus a typed port. */
    @Serializable
    public data class Ports(val ref: ConversationRef?, val mentioned: List<PreviewPort> = emptyList()) : PreviewRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp

        public companion object {
            /** The route for a conversation whose texts (oldest first) are [texts]. */
            public fun from(ref: ConversationRef?, texts: Iterable<String>): Ports =
                Ports(ref, PreviewLinks.recent(texts).map { PreviewPort(it.port, it.https) })
        }
    }

    /** The running page on [port], on the server of [ref] (or the connected server when null). */
    @Serializable
    public data class Page(val port: Int, val https: Boolean = false, val ref: ConversationRef? = null) : PreviewRoute
}

@Serializable public data class PreviewPort(val port: Int, val https: Boolean = false)
