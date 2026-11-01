package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Failure.Net
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Why a pairing code was refused. Words in `:ui:words`; the raw code never reaches them (B-SEC-107). */
public enum class PairingRejection {
    Empty, TooLong, NotJson, NotAnObject, NoUrls, UrlsNotAList, TooManyUrls, UrlNotText, UrlTooLong, NoAddress,
    UsernameNotText, NoPassword, PasswordNotText,
}

public sealed interface PairingParse {
    public data class Ok(val payload: PairingPayload) : PairingParse

    public data class Rejected(val reason: PairingRejection) : PairingParse
}

/**
 * A parsed `opencode2 pair` code (Dart `PairingPayload`). It holds the password carefully: printing redacts it and
 * strips credentials and query secrets from the addresses, a non-default username is not echoed, [consume] drops the
 * reference, and [urls] cannot be mutated (B-SEC-107). Memory only, never a route argument (07 §5 E03).
 */
public class PairingPayload internal constructor(
    public val urls: ImmutableList<String>,
    public val username: String,
    password: String,
) {
    private var held: SecretText? = SecretText(password)

    public val isConsumed: Boolean get() = held == null

    /** Null once consumed: parse the code again. */
    public fun password(): SecretText? = held

    public fun consume() {
        held = null
    }

    override fun toString(): String =
        "PairingPayload(urls: ${urls.map(PairingAddress::display)}, " +
            "username: ${if (username == PairingCode.DEFAULT_USERNAME) username else "<redacted>"}, " +
            "password: ${if (isConsumed) "<consumed>" else "<redacted>"})"
}

public object PairingCode {
    public const val MAX_LENGTH: Int = 8192
    public const val MAX_URLS: Int = 32
    public const val DEFAULT_USERNAME: String = "opencode"
    private const val MAX_URL_LENGTH = 2048

    /** Claims anything shaped like a code, malformed ones too, so the editor can explain instead of staying silent. */
    public fun looksLike(raw: String): Boolean {
        val t = raw.trim()
        if (t.length < 2 || t.length > MAX_LENGTH) return false
        return t.startsWith("{") && t.endsWith("}") && ("\"urls\"" in t || "'urls'" in t)
    }

    public fun parse(raw: String): PairingParse {
        val t = raw.trim()
        if (t.isEmpty()) return reject(PairingRejection.Empty)
        if (t.length > MAX_LENGTH) return reject(PairingRejection.TooLong)
        val decoded = decode(t) ?: return reject(PairingRejection.NotJson)
        val obj = decoded as? JsonObject ?: return reject(PairingRejection.NotAnObject)
        val rawUrls = obj["urls"] ?: return reject(PairingRejection.NoUrls)
        val list = rawUrls as? JsonArray ?: return reject(PairingRejection.UrlsNotAList)
        if (list.size > MAX_URLS) return reject(PairingRejection.TooManyUrls)
        val urls = mutableListOf<String>()
        for (entry in list) {
            val url = entry.textOrNull()?.trim() ?: return reject(PairingRejection.UrlNotText)
            if (url.isEmpty()) continue
            if (url.length > MAX_URL_LENGTH) return reject(PairingRejection.UrlTooLong)
            if (url !in urls) urls += url
        }
        if (urls.isEmpty()) return reject(PairingRejection.NoAddress)
        val rawUser = obj["username"]
        val username =
            when {
                rawUser == null || rawUser is JsonNull -> null
                else -> rawUser.textOrNull()?.trim() ?: return reject(PairingRejection.UsernameNotText)
            }
        val rawPassword = obj["password"] ?: return reject(PairingRejection.NoPassword)
        val password = rawPassword.textOrNull() ?: return reject(PairingRejection.PasswordNotText)
        return PairingParse.Ok(
            PairingPayload(urls.toImmutableList(), username?.takeIf { it.isNotEmpty() } ?: DEFAULT_USERNAME, password),
        )
    }

    private fun reject(reason: PairingRejection): PairingParse = PairingParse.Rejected(reason)

    private val json = Json { isLenient = false }

    /** kotlinx's parser is the boundary; its exception text (which can quote the input) is dropped here. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun decode(text: String): JsonElement? =
        try {
            json.parseToJsonElement(text)
        } catch (e: IllegalArgumentException) { // SerializationException is an IllegalArgumentException
            null
        }

    private fun JsonElement.textOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
}

/** Display-only address handling for pairing: never decides trust (that is [AddressPolicy]). */
internal object PairingAddress {
    private const val INVALID = "<invalid address>"

    /** `scheme://host[:port]` with user info, path, query and fragment dropped; IPv6 stays bracketed. */
    fun display(raw: String): String {
        val s = raw.trim()
        if (s.isEmpty() || s.length > 2048) return INVALID
        val schemeEnd = s.indexOf("://")
        if (schemeEnd <= 0) return INVALID
        val scheme = s.substring(0, schemeEnd).lowercase()
        if (!scheme.all { it.isLetterOrDigit() || it in "+.-" }) return INVALID
        val rest = s.substring(schemeEnd + 3)
        val authority = rest.takeWhile { it !in "/?#" }
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isEmpty() || hostPort.any { it.isWhitespace() }) return INVALID
        return "$scheme://${hostPort.lowercase()}"
    }
}

/** Per-address verdicts: a fixed set, so no probe text or URL secret can reach the screen (B-SEC-108). */
public enum class AddressVerdict {
    Answered, Refused, TimedOut, HostNotFound, CertificateRejected, PasswordRejected, Unhealthy, Starting,
    NotOpenCode, ProbeFailed, InvalidAddress, CredentialsInAddress, NeedsCleartextConsent,
}

public data class PairingAddressOutcome(
    val display: String,
    val origin: ServerOrigin?,
    val probed: Boolean,
    val verdict: AddressVerdict,
)

public data class PairingSelection(
    val outcomes: ImmutableList<PairingAddressOutcome>,
    val chosen: ServerOrigin?,
    /** What to save and connect with; its password prints redacted. */
    val chosenTarget: ProbeTarget?,
) {
    public val ok: Boolean get() = chosen != null

    /** Plain-HTTP addresses that were held, never dialed: the editor asks about each origin (B-SEC-124). */
    public val heldForConsent: List<ServerOrigin>
        get() = outcomes.filter { it.verdict == AddressVerdict.NeedsCleartextConsent }.mapNotNull { it.origin }
}

/**
 * Chooses the pairing address (Dart `selectPairingUrl`). Refused and held addresses are never dialed; candidates are
 * tried routable-first (loopback first on desktop) and the first that answers wins. A server that answered but
 * refused the password is still chosen when nothing else answered, so the editor can ask for the password.
 */
public class PairingDialer(
    private val policy: AddressPolicy,
    private val probe: suspend (ProbeTarget) -> Outcome<ProbeAnswer>,
    private val preferLoopback: Boolean,
    private val confirmedCleartext: Set<ServerOrigin>,
) {
    public suspend fun select(payload: PairingPayload): PairingSelection {
        val password = payload.password()
        val outcomes = mutableListOf<PairingAddressOutcome>()
        val candidates = mutableListOf<ServerOrigin>()
        for (raw in payload.urls) {
            val display = PairingAddress.display(raw)
            when (val o = policy.origin(raw.trim())) {
                is Outcome.Failed -> {
                    val verdict =
                        if ((o.failure as? Failure.Refused)?.reason == RefusalReason.CredentialsInAddress) {
                            AddressVerdict.CredentialsInAddress
                        } else {
                            AddressVerdict.InvalidAddress
                        }
                    outcomes += PairingAddressOutcome(display, null, probed = false, verdict = verdict)
                }
                is Outcome.Ok -> {
                    val origin = o.value
                    if (policy.needsCleartextConsent(origin) && origin !in confirmedCleartext) {
                        outcomes +=
                            PairingAddressOutcome(origin.toString(), origin, probed = false, verdict = AddressVerdict.NeedsCleartextConsent)
                    } else if (origin !in candidates) {
                        candidates += origin
                    }
                }
            }
        }
        val (local, routable) = candidates.partition { policy.isLoopback(it.host) }
        val ordered = if (preferLoopback) local + routable else routable + local
        var answeredButRefused: Pair<ServerOrigin, ProbeTarget>? = null
        for (origin in ordered) {
            val target =
                ProbeTarget(
                    origin = origin,
                    backend = BackendKind.OpenCode2,
                    username = payload.username,
                    password = password,
                    pinnedSha256 = null,
                    consentedOrigin = origin.takeIf { policy.needsCleartextConsent(it) },
                )
            when (val r = probe(target)) {
                is Outcome.Ok -> {
                    outcomes += PairingAddressOutcome(origin.toString(), origin, probed = true, verdict = AddressVerdict.Answered)
                    return PairingSelection(outcomes.toImmutableList(), origin, target)
                }
                is Outcome.Failed -> {
                    val verdict = verdictOf(r.failure)
                    if (verdict == AddressVerdict.PasswordRejected && answeredButRefused == null) {
                        answeredButRefused = origin to target
                    }
                    outcomes += PairingAddressOutcome(origin.toString(), origin, probed = true, verdict = verdict)
                }
            }
        }
        val fallback = answeredButRefused
        return PairingSelection(outcomes.toImmutableList(), fallback?.first, fallback?.second)
    }

    private fun verdictOf(failure: Failure): AddressVerdict =
        when (failure) {
            is Net.Unreachable ->
                when (failure.why) {
                    Net.Unreachable.Why.Timeout -> AddressVerdict.TimedOut
                    Net.Unreachable.Why.Dns -> AddressVerdict.HostNotFound
                    else -> AddressVerdict.Refused
                }
            is Net.Offline -> AddressVerdict.Refused
            is Net.TrustRefused -> AddressVerdict.CertificateRejected
            is Net.SignInNeeded -> AddressVerdict.PasswordRejected
            is Net.ServerBusy ->
                if (failure.kind == Net.ServerBusy.Kind.Starting) AddressVerdict.Starting else AddressVerdict.Unhealthy
            is Net.ServerFailed -> AddressVerdict.Unhealthy
            is Net.Incompatible, is Net.UnreadableReply -> AddressVerdict.NotOpenCode
            else -> AddressVerdict.ProbeFailed
        }
}
