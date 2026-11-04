package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.serialization.Serializable

/**
 * The in-app preview of a dev server (unit PREVIEW-QR; plan 07 `ProjectRoute.DevPreview`). Keys
 * hold the link's port, scheme, path and query (Flutter carries the whole `DevPreviewLink`,
 * B-CHAT-025), never a host: the preview resolves the host from the saved server. Their text forms
 * leave the path out, so a query never reaches a log.
 */
@Serializable
public sealed interface PreviewRoute : AppRoute {
    /**
     * "Preview a running app": the ports [ref]'s conversation mentioned, newest first, plus a typed
     * port.
     */
    @Serializable
    public data class Ports(
        val ref: ConversationRef?,
        val mentioned: List<PreviewPort> = emptyList(),
    ) : PreviewRoute {
        override val motion: RouteMotion
            get() = RouteMotion.SlideUp

        public companion object {
            /** The route for a conversation whose texts (oldest first) are [texts]. */
            public fun from(ref: ConversationRef?, texts: Iterable<String>): Ports =
                Ports(
                    ref,
                    PreviewLinks.recent(texts).map {
                        PreviewPort(it.port, it.https, it.pathAndQuery)
                    },
                )
        }
    }

    /**
     * The running page on [port] at [path] (path and query), on the server of [ref] (or the
     * connected one when null).
     */
    @Serializable
    public data class Page(
        val port: Int,
        val https: Boolean = false,
        val ref: ConversationRef? = null,
        val path: String = "/",
        val profile: io.github.eslamasabry.oc.core.common.ids.ProfileId? = null,
    ) : PreviewRoute {
        override fun toString(): String = "Page(port=$port, https=$https)"

        public companion object {
            /**
             * A tapped link the preview claims (Flutter: dev links in a reply open in the preview,
             * resolved against the conversation's server): a local link with a port opens here on
             * [ref]'s server; anything else is null and goes on through the external link policy.
             * Never the phone's browser, whose localhost is not the server's.
             */
            public fun claim(url: String, ref: ConversationRef?): Page? =
                PreviewLinks.parse(url)?.let { Page(it.port, it.https, ref, it.pathAndQuery) }
        }
    }
}

/**
 * A mentioned dev server: its port and scheme, and the [path] (with its query) the link pointed at.
 */
@Serializable
public data class PreviewPort(val port: Int, val https: Boolean = false, val path: String = "/") {
    override fun toString(): String = "PreviewPort(port=$port, https=$https)"
}
