package io.github.eslamasabry.opencode_mobile

import java.net.HttpURLConnection
import java.net.URL

/** The authored loopback connection rule used by the app's private HTTP helpers. */
public object DirectLoopbackHttp {
    public fun open(port: Int, path: String): HttpURLConnection =
        URL("http", "127.0.0.1", port, path).openConnection() as HttpURLConnection
}
