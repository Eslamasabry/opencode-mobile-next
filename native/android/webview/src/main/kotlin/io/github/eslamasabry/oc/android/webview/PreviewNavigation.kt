package io.github.eslamasabry.oc.android.webview

/**
 * The preview's navigation decisions, pure so they are tested without a WebView (B-SEC-058, plan 16 §1.8.5).
 */
public object PreviewNavigation {
    /** What a top-level or frame navigation to an address does. */
    public enum class Navigate {
        /** The preview's own origin: it loads here. */
        LoadHere,

        /** Another address, asked by a person's tap on the page: it goes to the external link policy. */
        AskToLeave,

        /** Another address asked by a script, a frame or a redirect: it does not load, and nothing is shown. */
        Refuse,
    }

    public fun navigate(sameOrigin: Boolean, mainFrame: Boolean, gesture: Boolean): Navigate =
        when {
            sameOrigin -> Navigate.LoadHere
            mainFrame && gesture -> Navigate.AskToLeave
            else -> Navigate.Refuse
        }

    /**
     * After the document fetch followed redirects within the origin from [start] to [final]: null serves the body for
     * [start] as it is; an address means the page must load there instead, so its relative links and the shown
     * address resolve against where it really lives (`/` → `/app/` loads `bundle.js` from `/app/`).
     */
    public fun reloadAt(start: String, final: String): String? = final.takeIf { it != start }
}
