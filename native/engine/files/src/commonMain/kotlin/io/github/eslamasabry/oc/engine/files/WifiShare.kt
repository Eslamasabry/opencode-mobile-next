package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A running share: the one link that works, until [expiresAtMs]. */
public data class WifiShareState(
    val url: String,
    val name: String,
    val expiresAtMs: Long,
    val downloads: Int = 0,
    /** Which start made this share: only its owner may stop it ([WifiShare.stop] with this id). */
    val id: Long = 0,
) {
    public fun remainingMs(nowMs: Long): Long = (expiresAtMs - nowMs).coerceAtLeast(0)

    override fun toString(): String = "WifiShareState(downloads=$downloads)" // the link holds the secret token
}

/** What the download server answers one request (pure, B-MEDIA-034). */
public sealed interface ShareReply {
    /** 404: any path but `/<token>/<name>`. */
    public data object NotFound : ShareReply

    /** 405 with `Allow: GET, HEAD`. */
    public data object MethodNotAllowed : ShareReply

    /** 200 with [headers]; [body] is not sent for HEAD. */
    public class Serve(
        public val headers: List<Pair<String, String>>,
        public val body: ByteArray,
        public val headOnly: Boolean,
    ) : ShareReply
}

/** One request at a time from the platform's socket. [served] counts a finished GET download. */
public interface ShareHandler {
    public fun reply(method: String, rawPath: String): ShareReply

    public fun served()
}

public interface ShareServer {
    public val port: Int

    public suspend fun close()
}

/** The platform half (Android: `FilesWiring.kt`): the Wi-Fi address, the listening socket and secure randomness. */
public interface ShareHost {
    /** The phone's Wi-Fi or hotspot IPv4 address (a private LAN one), or null. Mobile data and VPNs never count. */
    public suspend fun wifiAddress(): String?

    /** Binds exactly [address] (never 0.0.0.0) on a free port. */
    public suspend fun open(address: String, handler: ShareHandler): Outcome<ShareServer>

    /** Cryptographically secure random bytes. */
    public fun secureBytes(count: Int): ByteArray
}

/** Ports of the pure parts of `WifiFileShare` (lib/platform/wifi_file_share.dart). */
public object WifiShareRules {
    public const val LIFETIME_MS: Long = 15 * 60 * 1000L
    public const val TOKEN_BYTES: Int = 24

    /** 10/8, 172.16/12 and 192.168/16 only. */
    public fun isPrivateLan(address: String): Boolean {
        val parts = address.split('.')
        if (parts.size != 4) return false
        val b = parts.map { it.toIntOrNull()?.takeIf { v -> v in 0..255 } ?: return false }
        return b[0] == 10 || (b[0] == 172 && b[1] in 16..31) || (b[0] == 192 && b[1] == 168)
    }

    /** Wi-Fi and hotspot interface names (`wlan*`, `swlan*`, `ap*`); mobile data (`rmnet*`, `ccmni*`) never. */
    public fun isWifiInterface(name: String): Boolean {
        val n = name.lowercase()
        return n.startsWith("wlan") || n.startsWith("swlan") || n.startsWith("ap")
    }

    /** A base name the downloader may see: no folders, no control characters or quotes. */
    public fun safeName(raw: String): String {
        val base = raw.split('/', '\\').last()
        val clean = buildString { for (c in base) append(if (c.code < 0x20 || c.code == 0x7F || c == '"') '_' else c) }
        return clean.ifEmpty { "file" }
    }

    public fun token(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes).trimEnd('=')

    public fun url(address: String, port: Int, token: String, name: String): String =
        "http://$address:$port/$token/${encode(name)}"

    public fun reply(method: String, rawPath: String, token: String, name: String, data: FileData): ShareReply {
        val path = rawPath.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        val good = segments.size == 2 && sameText(segments[0], token) && decode(segments[1]) == name
        if (!good) return ShareReply.NotFound
        if (method != "GET" && method != "HEAD") return ShareReply.MethodNotAllowed
        val ascii = buildString { for (c in name) append(if (c.code in 0x20..0x7E) c else '_') }
        return ShareReply.Serve(
            listOf(
                "Content-Type" to (data.mime ?: "application/octet-stream"),
                "Content-Disposition" to "attachment; filename=\"$ascii\"; filename*=UTF-8''${encode(name)}",
                "X-Content-Type-Options" to "nosniff",
                "Cache-Control" to "no-store",
                "Content-Length" to data.bytes.size.toString(),
            ),
            data.bytes,
            headOnly = method == "HEAD",
        )
    }

    /** Constant-time comparison of the token. */
    public fun sameText(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    /** RFC 3986 percent-encoding of one path segment (UTF-8). */
    public fun encode(text: String): String = buildString {
        for (byte in text.encodeToByteArray()) {
            val c = byte.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-._~") append(ch)
            else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }

    /** Percent-decoding; null when the escape is malformed or the bytes are not UTF-8. */
    public fun decode(segment: String): String? {
        val out = ArrayList<Byte>(segment.length)
        var i = 0
        while (i < segment.length) {
            val ch = segment[i]
            if (ch == '%') {
                if (i + 2 >= segment.length) return null
                val hi = segment[i + 1].digitToIntOrNull(16) ?: return null
                val lo = segment[i + 2].digitToIntOrNull(16) ?: return null
                out += ((hi shl 4) or lo).toByte()
                i += 3
            } else {
                for (b in ch.toString().encodeToByteArray()) out += b
                i++
            }
        }
        return try {
            out.toByteArray().decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private const val HEX = "0123456789ABCDEF"
}

/**
 * Shares one opened file on the local Wi-Fi, read-only, for 15 minutes (port of `WifiFileShare`). One share at a
 * time: starting another stops the first. The share ends on [stop], on expiry and when the app's scope ends. A path
 * that [ShareGuard] calls secret is refused before anything listens.
 */
public class WifiShare(
    private val host: ShareHost?,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val lifetimeMs: Long = WifiShareRules.LIFETIME_MS,
) {
    private val lane = Mutex()
    private val mutableState = MutableStateFlow<WifiShareState?>(null)
    private var server: ShareServer? = null
    private var expiry: Job? = null
    private val generation = AtomicLong(0L)

    /** Null when nothing is shared. */
    public val state: StateFlow<WifiShareState?> = mutableState.asStateFlow()

    /** Milliseconds left on the running share, once a second; null when nothing is shared. */
    @OptIn(ExperimentalCoroutinesApi::class)
    public val remainingMs: Flow<Long?> =
        mutableState.flatMapLatest { share ->
            if (share == null) flowOf(null)
            else
                flow {
                    while (true) {
                        val left = share.remainingMs(clock.now().toEpochMilliseconds())
                        emit(left)
                        if (left == 0L) break
                        delay(1_000)
                    }
                }
        }

    /** Ends the share from a place that cannot suspend (a screen being destroyed). */
    public fun stopLater() {
        scope.launch { stop() }
    }

    /** [stopLater] for one owner: a newer share started by someone else keeps running. */
    public fun stopLater(id: Long) {
        scope.launch { stop(id) }
    }

    /** False where the platform cannot run the download server. */
    public val supported: Boolean
        get() = host != null

    public suspend fun start(data: FileData): Outcome<WifiShareState> = lane.withLock {
        stopLocked()
        val host = host ?: return@withLock Outcome.Failed(Failure.Media(MediaProblem.ShareUnavailable))
        if (ShareGuard.looksSecret(data.path)) {
            return@withLock Outcome.Failed(Failure.Media(MediaProblem.WifiSharePrivateFile))
        }
        val address =
            host.wifiAddress()?.takeIf(WifiShareRules::isPrivateLan)
                ?: return@withLock Outcome.Failed(Failure.Media(MediaProblem.WifiShareNoWifi))
        val token = WifiShareRules.token(host.secureBytes(WifiShareRules.TOKEN_BYTES))
        val name = WifiShareRules.safeName(data.name)
        val mine = generation.incrementAndFetch()
        val handler =
            object : ShareHandler {
                override fun reply(method: String, rawPath: String): ShareReply =
                    if (generation.load() != mine) ShareReply.NotFound
                    else WifiShareRules.reply(method, rawPath, token, name, data)

                override fun served() {
                    if (generation.load() == mine) mutableState.update { it?.copy(downloads = it.downloads + 1) }
                }
            }
        val opened =
            when (val bound = host.open(address, handler)) {
                is Outcome.Failed -> return@withLock Outcome.Failed(Failure.Media(MediaProblem.WifiShareUnavailable))
                is Outcome.Ok -> bound.value
            }
        server = opened
        val started =
            WifiShareState(
                WifiShareRules.url(address, opened.port, token, name),
                name,
                clock.now().toEpochMilliseconds() + lifetimeMs,
                id = mine,
            )
        mutableState.value = started
        expiry =
            scope.launch {
                delay(lifetimeMs)
                lane.withLock { if (generation.load() == mine) stopLocked() }
            }
        Outcome.Ok(started)
    }

    /** Ends the share now. Safe when nothing is shared. */
    public suspend fun stop(): Unit = lane.withLock { stopLocked() }

    /** Ends the share only while it is still the one [id] started (two viewers never stop each other's share). */
    public suspend fun stop(id: Long): Unit = lane.withLock { if (generation.load() == id && server != null) stopLocked() }

    private suspend fun stopLocked() {
        generation.incrementAndFetch()
        val job = expiry
        expiry = null
        mutableState.value = null
        val closing = server
        server = null
        closing?.close()
        job?.cancel()
    }
}
