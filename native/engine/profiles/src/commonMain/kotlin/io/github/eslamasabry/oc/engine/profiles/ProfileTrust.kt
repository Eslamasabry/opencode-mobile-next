package io.github.eslamasabry.oc.engine.profiles

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.data.dart.DartJson
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.security.AddressRules

/** `oc.certificatePin.<id>` = `{"origin":"https://h:443","sha256":"<64 lower hex>"}` (Flutter, exact). */
public data class CertificatePinRecord(val origin: String, val leafSha256: String)

/** `oc.cleartextOk.<id>` = the exact origin string the person confirmed plain HTTP for. */
public data class CleartextOkRecord(val origin: String)

/** Per-profile trust (plan 03 §10.3). Each half names the origin it was given for. */
public data class ProfileTrustRecord(val pin: CertificatePinRecord?, val cleartext: CleartextOkRecord?) {
    /** Only the halves that name [origin] apply; an address edit makes both inert. */
    public fun forOrigin(origin: String?): ProfileTrustRecord =
        ProfileTrustRecord(pin?.takeIf { it.origin == origin }, cleartext?.takeIf { it.origin == origin })

    public companion object {
        public val None: ProfileTrustRecord = ProfileTrustRecord(null, null)
    }
}

/**
 * What an editor asks for. Null halves keep what is stored (an editor that rebuilds a profile can't erase trust by
 * accident, Flutter `_syncCleartextConfirmation`/`_syncCertificatePin`); a half that does not name the profile's new
 * origin is dropped in the same commit.
 */
public data class TrustInput(val pin: CertificatePinRecord? = null, val cleartextOrigin: String? = null)

/** Origin rules shared by the store and the deletion code. */
public object ProfileOrigins {
    /** Dart `cleartextOriginOf` / `serverOrigin`: `scheme://host:port`, or null when [url] is not a usable address. */
    public fun trustOrigin(url: String): String? = AddressRules.origin(url.trim()).getOrNull()?.toString()

    /** Dart `serverUrlNeedsCleartextConfirmation`: plain HTTP to anything but this device. */
    public fun needsCleartextConsent(url: String): Boolean =
        AddressRules.origin(url.trim()).getOrNull()?.let(AddressRules::needsCleartextConsent) == true

    /** The connection origin, ws/wss included for agent sockets (null when unusable). */
    public fun serverOrigin(url: String): ServerOrigin? {
        val raw = url.trim()
        val scheme = raw.substringBefore("://", "").lowercase()
        return when (scheme) {
            "http", "https" -> AddressRules.origin(raw).getOrNull()
            "ws", "wss" -> {
                val http = if (scheme == "ws") "http" else "https"
                (AddressRules.origin(http + raw.substring(scheme.length)) as? Outcome.Ok)?.value?.let { parsed ->
                    val port = if (raw.substringAfter("://").substringBefore('/').let(::hasExplicitPort)) {
                        parsed.port
                    } else if (scheme == "ws") 80 else 443
                    ServerOrigin(scheme, parsed.host, port)
                }
            }
            else -> null
        }
    }

    private fun hasExplicitPort(authority: String): Boolean =
        if (authority.startsWith("[")) authority.substringAfter("]").startsWith(":") else ':' in authority
}

internal object TrustCodec {
    private val PIN = Regex("^[0-9a-fA-F]{64}$")

    fun validPin(sha256: String): Boolean = PIN.matches(sha256)

    /** Dart `_storedCertificatePin`: a malformed record is no pin. */
    fun decodePin(raw: String?): CertificatePinRecord? {
        if (raw == null) return null
        val value = DartJson.decodeOrNull(raw) as? Map<*, *> ?: return null
        val origin = value["origin"] as? String ?: return null
        val sha = value["sha256"] as? String ?: return null
        if (!origin.startsWith("https://") || ProfileOrigins.trustOrigin(origin) != origin || !validPin(sha)) return null
        return CertificatePinRecord(origin, sha.lowercase())
    }

    fun encodePin(pin: CertificatePinRecord): String = DartJson.encode(linkedMapOf("origin" to pin.origin, "sha256" to pin.leafSha256))

    /** The trust a profile at [profile]'s address may carry, from what the editor asked and what is stored. */
    fun next(profile: StoredProfile, input: TrustInput, stored: ProfileTrustRecord): ProfileTrustRecord {
        val origin = ProfileOrigins.trustOrigin(profile.baseUrl)
        val wantedConsent = input.cleartextOrigin ?: stored.cleartext?.origin
        val consent = if (!profile.usesAgentSocket && ProfileOrigins.needsCleartextConsent(profile.baseUrl) &&
            wantedConsent != null && wantedConsent == origin
        ) {
            CleartextOkRecord(wantedConsent)
        } else {
            null
        }
        val wantedPin = input.pin ?: stored.pin
        val pin = if (!profile.usesAgentSocket && origin != null && origin.startsWith("https://") &&
            wantedPin != null && wantedPin.origin == origin
        ) {
            CertificatePinRecord(origin, wantedPin.leafSha256.lowercase())
        } else {
            null
        }
        return ProfileTrustRecord(pin, consent)
    }

    /** What load restores: a pin only for the profile's current HTTPS origin (Flutter `_restoreCertificatePin`). */
    fun restored(profile: StoredProfile, pinRaw: String?, cleartextRaw: String?): ProfileTrustRecord {
        val origin = ProfileOrigins.trustOrigin(profile.baseUrl)
        val pin = decodePin(pinRaw)?.takeIf { !profile.usesAgentSocket && it.origin == origin }
        return ProfileTrustRecord(pin, cleartextRaw?.let(::CleartextOkRecord))
    }
}
