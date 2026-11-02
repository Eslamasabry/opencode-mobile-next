package io.github.eslamasabry.oc.android.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** What the page is doing (Flutter `KitWebPreviewPhase`). */
public enum class PreviewPhase { Loading, Ready, Failed }

/**
 * The egress rules, decided outside this module (`:feature:preview` `PreviewEgress`, handed over by the app wiring).
 * Every request and navigation the page makes is asked here first.
 */
public interface PreviewGuard {
    /** True when [url] is the preview's own origin over http(s) without credentials: it may load here. */
    public fun mayLoad(url: String): Boolean

    /** True for the request methods the preview lets through (GET and HEAD); a form POST never leaves. */
    public fun methodAllowed(method: String): Boolean

    /** The `Content-Security-Policy` set on every page document: no frames, no websockets, no other origins. */
    public val documentPolicy: String
}

/** What the screen hears from the page. Always called on the main thread. */
public interface PreviewEvents {
    public fun phase(phase: PreviewPhase)

    public fun address(url: String)

    public fun canGoBack(can: Boolean)

    /** A person's navigation to another address, or a redirect there: it did not load here. */
    public fun leave(url: String)
}

/**
 * A web page from a dev server, inside the app — deliberately not a browser (Flutter `KitWebPreview`, B-SEC-058):
 *
 * - it loads [origin] and nothing else: a request for any other address, scheme or a URL with credentials is answered
 *   here with an empty refusal and never reaches the network; a person's tap on such a link goes to [PreviewEvents.leave]
 *   (the screen sends it through the external link policy);
 * - only GET and HEAD leave the phone, so a form cannot POST anywhere;
 * - every page document is fetched here and served with [PreviewGuard.documentPolicy], so frames, websockets, workers
 *   and other-origin connections are refused by the page's own engine; a redirect to another origin is not followed;
 * - no JavaScript bridge is ever added; file and content access, geolocation, camera and microphone are off; pop-ups
 *   and mixed content are refused;
 * - cookies, cache and storage are cleared when the preview opens and when it closes, and third-party cookies refused.
 */
public class KitWebPreview(
    context: Context,
    private val origin: String,
    private val guard: PreviewGuard,
    private val events: PreviewEvents,
) {
    private val main = Handler(Looper.getMainLooper())
    private var failed = false

    public val view: WebView = WebView(context)

    init {
        clean()
        harden(view)
        view.webViewClient = Client()
        view.webChromeClient = Chrome()
        view.setDownloadListener { _, _, _, _, _ -> } // downloads never start from a preview
        events.phase(PreviewPhase.Loading)
        events.address(origin)
        view.loadUrl(origin)
    }

    public fun reload() {
        failed = false
        events.phase(PreviewPhase.Loading)
        view.reload()
    }

    /** Steps back in the page's own history; false when there is none. */
    public fun goBack(): Boolean {
        if (!view.canGoBack()) return false
        view.goBack()
        return true
    }

    /** Stops the page, clears what it stored and frees the view. */
    public fun destroy() {
        main.removeCallbacksAndMessages(null)
        view.stopLoading()
        view.webChromeClient = null
        clean()
        view.destroy()
    }

    private fun clean() {
        runCatchingPlatform { view.clearCache(true) }
        runCatchingPlatform { WebStorage.getInstance().deleteAllData() }
        runCatchingPlatform { CookieManager.getInstance().removeAllCookies(null) }
    }

    // Dev servers are JavaScript apps; the page still gets no bridge, files, location, camera or microphone.
    @SuppressLint("SetJavaScriptEnabled")
    private fun harden(web: WebView) {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (guard.mayLoad(url)) return false
            // Only a person's top-level navigation leaves; a frame or a script asking for another address just doesn't load.
            if (request.isForMainFrame) events.leave(url)
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            if (!guard.mayLoad(url)) return refusal(403, "Forbidden")
            if (!guard.methodAllowed(request.method)) return refusal(405, "Method Not Allowed")
            if (!request.isForMainFrame || !request.method.equals("GET", ignoreCase = true)) return null
            return document(url, request.requestHeaders.orEmpty())
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (!failed) events.phase(PreviewPhase.Loading)
            url?.let(events::address)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            if (!failed) events.phase(PreviewPhase.Ready)
            events.canGoBack(view.canGoBack())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                failed = true
                events.phase(PreviewPhase.Failed)
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            url?.let(events::address)
            events.canGoBack(view.canGoBack())
        }
    }

    private class Chrome : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
            callback?.invoke(origin, false, false)
        }
    }

    /**
     * Fetches a page document so its answer carries [PreviewGuard.documentPolicy]. Redirects are followed only within
     * the origin (at most [MAX_REDIRECTS]); one to elsewhere is handed to [PreviewEvents.leave]. Runs on WebView's
     * request thread.
     */
    private fun document(start: String, requestHeaders: Map<String, String>): WebResourceResponse {
        var url = start
        repeat(MAX_REDIRECTS + 1) {
            val connection =
                try {
                    URL(url).openConnection() as HttpURLConnection
                } catch (_: IOException) {
                    return unreachable()
                } catch (_: IllegalArgumentException) {
                    return refusal(400, "Bad Request")
                }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.useCaches = false
                for ((name, value) in requestHeaders) {
                    if (name.lowercase() !in DROPPED_REQUEST_HEADERS) connection.setRequestProperty(name, value)
                }
                runCatchingPlatform { CookieManager.getInstance().getCookie(url) }?.let { connection.setRequestProperty("Cookie", it) }
                val code = connection.responseCode
                connection.headerFields["Set-Cookie"]?.forEach { cookie ->
                    runCatchingPlatform { CookieManager.getInstance().setCookie(url, cookie) }
                }
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location")?.let { runCatchingPlatform { URL(URL(url), it).toString() } }
                    connection.disconnect()
                    if (next == null) return refusal(502, "Bad Gateway")
                    if (!guard.mayLoad(next)) {
                        onMain { events.leave(next) }
                        return refusal(403, "Forbidden")
                    }
                    url = next
                    return@repeat
                }
                val body: InputStream = (if (code >= 400) connection.errorStream else connection.inputStream) ?: empty()
                val type = connection.contentType.orEmpty()
                val mime = type.substringBefore(';').trim().ifEmpty { "text/html" }
                val charset =
                    type.split(';').map { it.trim() }.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
                        ?.substringAfter('=')?.trim('"', ' ')
                val headers = HashMap<String, String>()
                for ((name, values) in connection.headerFields) {
                    if (name == null || name.lowercase() in DROPPED_RESPONSE_HEADERS) continue
                    headers[name] = values.joinToString(", ")
                }
                headers["Content-Security-Policy"] = guard.documentPolicy
                val reason = connection.responseMessage?.takeIf { it.isNotBlank() } ?: "OK"
                return WebResourceResponse(mime, charset, code.coerceIn(200, 599), reason, headers, body)
            } catch (_: IOException) {
                connection.disconnect()
                return unreachable()
            }
        }
        return refusal(508, "Loop Detected")
    }

    private fun unreachable(): WebResourceResponse {
        onMain {
            failed = true
            events.phase(PreviewPhase.Failed)
        }
        return refusal(502, "Bad Gateway")
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000

        // Compression and length belong to this hop: the stream handed back is already decoded.
        val DROPPED_REQUEST_HEADERS = setOf("accept-encoding", "cookie", "host", "connection")
        val DROPPED_RESPONSE_HEADERS =
            setOf("content-security-policy", "content-security-policy-report-only", "set-cookie", "content-length",
                "content-encoding", "transfer-encoding", "connection")

        fun empty(): InputStream = ByteArrayInputStream(ByteArray(0))

        fun refusal(code: Int, reason: String): WebResourceResponse =
            WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), empty())

        /** WebView's static managers throw on devices whose WebView package is missing or updating. */
        inline fun <T> runCatchingPlatform(block: () -> T): T? =
            try {
                block()
            } catch (_: RuntimeException) {
                null
            }
    }
}
