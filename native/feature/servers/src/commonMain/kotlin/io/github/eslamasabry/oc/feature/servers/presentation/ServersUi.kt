package io.github.eslamasabry.oc.feature.servers.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/*
 * Screen contracts (rule 3: XUi + XActions). Plain data: no copy, no Compose, no platform types. Every action names
 * its target (rule 15), so titles and buttons can say "Disconnect from Laptop".
 */

// ---- Servers (07 §5 E01)

public data class ServerRowUi(
    val id: ProfileId,
    val name: String,
    /** Always `scheme://host:port`, so two servers on one host are told apart by port (B-NAV-040). */
    val address: String,
    val status: ServerStatus,
    val active: Boolean,
    val onPhone: Boolean,
    /** A failed connect shows inline, in the verdict style (E01). */
    val failure: ConnectionFailureUi?,
)

public data class ServersUi(
    val rows: ImmutableList<ServerRowUi> = persistentListOf(),
    val connecting: ProfileId? = null,
    /** The forget confirmation, naming the server. */
    val forgetting: ServerRowUi? = null,
) {
    /** Nothing saved: the welcome. */
    val welcome: Boolean get() = rows.isEmpty()
}

public interface ServersActions {
    public fun addServer()

    public fun openServer(id: ProfileId)

    public fun connect(id: ProfileId)

    public fun fix(id: ProfileId, action: FailureAction)

    public fun forget(id: ProfileId)

    public fun confirmForget()

    public fun cancelForget()
}

// ---- Editor (07 §5 E02)

public enum class EntryKind { PairingCode, Address }

/** Why the typed address was refused (plan 04 §4.3 `UrlRejection`, the cases `AddressRules` reports). */
public enum class AddressProblem { Empty, Invalid, CredentialsInAddress, PinNeedsHttps }

/** K11: a stored secret is never shown; "Saved · Replace" until the person chooses Replace. */
public enum class SecretPresence { Empty, Saved, Editing }

public sealed interface EditorVerdict {
    public data class Answered(val origin: ServerOrigin, val serverVersion: String?) : EditorVerdict

    public data class Failed(val failure: ConnectionFailureUi) : EditorVerdict

    /** No pairing address answered: one plain reason per address (B-SEC-108). */
    public data class NoAddressAnswered(val outcomes: ImmutableList<PairingAddressOutcome>) : EditorVerdict
}

/** The plain-HTTP choice for one origin (plan 04 §4.3). [public] picks the stronger sentence (N4-D4). */
public data class CleartextConsentUi(val origin: ServerOrigin, val public: Boolean)

/** A certificate to compare before trusting it (plan 04 §4.5; kit `KitServerTrust`). */
public data class PinReviewUi(
    val origin: ServerOrigin,
    val fingerprint: String,
    val notBeforeMs: Long,
    val notAfterMs: Long,
    /** The pin this replaces, when the certificate changed (B-NAV-008). */
    val previousFingerprint: String?,
)

public data class EditorUi(
    val mode: EditorMode = EditorMode.Add,
    /** The server's name in Edit and Credentials, so the title names it. */
    val serverName: String? = null,
    val entry: EntryKind = EntryKind.Address,
    val address: String = "",
    val name: String = "",
    val username: String = "",
    val password: SecretPresence = SecretPresence.Empty,
    val code: String = "",
    val focusPassword: Boolean = false,
    val addressProblem: AddressProblem? = null,
    val codeProblem: PairingRejection? = null,
    val checking: Boolean = false,
    val verdict: EditorVerdict? = null,
    val consent: CleartextConsentUi? = null,
    /** Plain HTTP the person already allowed for the current address (shown as a standing notice). */
    val cleartextAllowed: Boolean = false,
    val pinReview: PinReviewUi? = null,
    val pinned: Boolean = false,
    val certificateUnreadable: Boolean = false,
    val saveFailed: Failure? = null,
) {
    /** Saving never connects without a successful test (E02). */
    val canSave: Boolean get() = verdict is EditorVerdict.Answered && !checking

    /** A pairing code carries the server password, so printing the state never shows it (B-SEC-107). */
    override fun toString(): String =
        "EditorUi(mode=$mode, entry=$entry, address=$address, name=$name, password=$password, " +
            "code=${if (code.isEmpty()) "" else "<redacted>"}, addressProblem=$addressProblem, codeProblem=$codeProblem, " +
            "checking=$checking, verdict=$verdict, consent=$consent, pinReview=$pinReview, pinned=$pinned)"
}

public interface EditorActions {
    public fun useEntry(kind: EntryKind)

    public fun setAddress(text: String)

    public fun setName(text: String)

    public fun setUsername(text: String)

    public fun setPassword(text: String)

    public fun replacePassword()

    public fun setCode(text: String)

    public fun test()

    public fun allowHttp()

    public fun declineHttp()

    public fun reviewCertificate()

    public fun trustCertificate()

    public fun cancelCertificateReview()

    public fun saveAndConnect()

    public fun back()
}

// ---- Server page and connection details (07 §5 E04, §2.3 ConnectionDetails)

/** How this server is reached, for the Connection section and E2E-04. */
public enum class TransportSecurity { Https, HttpsPinned, HttpAllowed, HttpNotAllowed, ThisDevice }

public data class ServerPageUi(
    val id: ProfileId,
    val name: String,
    val address: String,
    val backend: BackendKind,
    val status: ServerStatus,
    val active: Boolean,
    val security: TransportSecurity,
    val pinFingerprint: String?,
    val failure: ConnectionFailureUi?,
    val pinReview: PinReviewUi? = null,
    val certificateUnreadable: Boolean = false,
    val forgetting: Boolean = false,
    val busy: Boolean = false,
)

public interface ServerPageActions {
    public fun connect()

    public fun disconnect()

    public fun edit()

    public fun fix(action: FailureAction)

    public fun reviewCertificate()

    public fun trustCertificate()

    public fun cancelCertificateReview()

    public fun forget()

    public fun confirmForget()

    public fun cancelForget()

    public fun back()
}

// ---- Switcher (07 §2.3 ServerSwitcher, A08)

public data class SwitcherRowUi(
    val id: ProfileId,
    val name: String,
    val host: String,
    /** Set only when another saved server shares this host (B-NAV-040/041). */
    val port: Int?,
    val status: ServerStatus,
    val current: Boolean,
)

public data class ServerSwitcherUi(val rows: ImmutableList<SwitcherRowUi> = persistentListOf())

public interface ServerSwitcherActions {
    public fun choose(id: ProfileId)

    public fun manage(id: ProfileId)

    public fun addServer()
}
