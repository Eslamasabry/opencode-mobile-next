package io.github.eslamasabry.opencode_mobile

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL

/** The authored loopback connection rule used by the app's private HTTP helpers. */
public object DirectLoopbackHttp {
    public fun open(port: Int, path: String): HttpURLConnection {
        require(port in 1..65535)
        require(path.startsWith("/") && !path.startsWith("//") && path.none { it == '\r' || it == '\n' })
        return (URL("http", "127.0.0.1", port, path).openConnection(Proxy.NO_PROXY) as HttpURLConnection)
            .apply { instanceFollowRedirects = false }
    }
}
