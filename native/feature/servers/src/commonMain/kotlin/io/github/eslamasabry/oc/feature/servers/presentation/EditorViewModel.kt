package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The server editor (07 §5 E02): typed address or pairing code. Credentials only ever go to an origin the person
 * trusted: an unconfirmed plain-HTTP origin is held and never probed (B-SEC-124, plan 04 §4.3), a certificate is
 * trusted only after its fingerprint was shown (B-NAV-008), a changed host, port or scheme drops the pin and the HTTP
 * choice (B-SEC-154), and saving never happens without a successful test (E02). The password is never echoed.
 */
public class EditorViewModel(
    private val mode: EditorMode,
    focusPassword: Boolean,
    private val port: ServersPort,
    private val policy: AddressPolicy,
    private val env: ServersEnvironment,
    private val nav: ServersNavigator,
    scope: CoroutineScope,
    /** Desktop tries loopback pairing addresses first (Dart `platformCapabilities.isDesktop`). */
    private val preferLoopback: Boolean = false,
) : ViewModel(scope), EditorActions {
    private val editing: ServerProfile? =
        when (mode) {
            is EditorMode.Edit -> port.servers.value.firstOrNull { it.profile.id == mode.profileId }?.profile
            is EditorMode.Credentials -> port.servers.value.firstOrNull { it.profile.id == mode.profileId }?.profile
            EditorMode.Add -> null
        }

    private val mutable =
        MutableStateFlow(
            EditorUi(
                mode = mode,
                serverName = editing?.name,
                entry = if (editing != null) EntryKind.Address else EntryKind.PairingCode,
                address = editing?.origin?.toString().orEmpty(),
                name = editing?.name.orEmpty(),
                username = editing?.username.orEmpty(),
                password = if (editing != null) SecretPresence.Saved else SecretPresence.Empty,
                focusPassword = focusPassword,
                pinned = editing?.trust?.pinnedLeafSha256 != null,
                cleartextAllowed = editing?.trust?.cleartextConsentAtMs != null,
            ),
        )
    public val state: StateFlow<EditorUi> = mutable.asStateFlow()

    // Held outside the UI state: the password and the pin never travel in EditorUi (it is data a test may print).
    private var password: SecretText? = null
    private var pin: String? = editing?.trust?.pinnedLeafSha256
    private val consented: MutableSet<ServerOrigin> =
        listOfNotNull(editing?.origin?.takeIf { editing.trust.cleartextConsentAtMs != null }).toMutableSet()
    private var attempts: Pair<ServerOrigin, Int>? = null
    private var pairing: PairingPayload? = null
    private var chosen: ProbeTarget? = null
    private var reviewed: CertificateSummary? = null

    override fun useEntry(kind: EntryKind): Unit = mutable.update { it.copy(entry = kind, verdict = null, consent = null) }

    override fun setAddress(text: String): Unit =
        mutable.update { it.copy(address = text, addressProblem = null, verdict = null, consent = null) }

    override fun setName(text: String): Unit = mutable.update { it.copy(name = text) }

    override fun setUsername(text: String): Unit = mutable.update { it.copy(username = text, verdict = null) }

    override fun setPassword(text: String) {
        password = SecretText(text)
        mutable.update { it.copy(password = SecretPresence.Editing, verdict = null) }
    }

    override fun replacePassword(): Unit = mutable.update { it.copy(password = SecretPresence.Editing, focusPassword = true) }

    override fun setCode(text: String) {
        pairing?.consume()
        pairing = null
        mutable.update { it.copy(code = text, codeProblem = null, verdict = null, consent = null) }
    }

    override fun test() {
        if (mutable.value.checking) return
        viewModelScope.launch { runTest() }
    }

    override fun saveAndConnect() {
        if (mutable.value.checking) return
        viewModelScope.launch {
            if (!mutable.value.canSave) runTest()
            if (mutable.value.canSave) save()
        }
    }

    override fun allowHttp() {
        val ask = mutable.value.consent ?: return
        consented += ask.origin
        mutable.update { it.copy(consent = null, cleartextAllowed = true) }
        test()
    }

    override fun declineHttp(): Unit = mutable.update { it.copy(consent = null) }

    override fun reviewCertificate() {
        val origin = currentOrigin() ?: return
        viewModelScope.launch {
            mutable.update { it.copy(checking = true, certificateUnreadable = false) }
            when (val r = port.inspectCertificate(origin)) {
                is Outcome.Ok -> {
                    reviewed = r.value
                    mutable.update {
                        it.copy(
                            checking = false,
                            pinReview =
                                PinReviewUi(origin, Fingerprints.display(r.value.sha256Hex), r.value.notBeforeMs, r.value.notAfterMs, pin?.let(Fingerprints::display)),
                        )
                    }
                }
                is Outcome.Failed -> mutable.update { it.copy(checking = false, certificateUnreadable = true) }
            }
        }
    }

    override fun trustCertificate() {
        val review = mutable.value.pinReview ?: return
        val cert = reviewed ?: return
        when (val checked = policy.checkPin(review.origin, cert.sha256Hex)) {
            is Outcome.Ok -> {
                pin = checked.value
                mutable.update { it.copy(pinReview = null, pinned = true) }
                test()
            }
            // A pin can never be attached to plain HTTP (B-SEC-153).
            is Outcome.Failed -> mutable.update { it.copy(pinReview = null, addressProblem = AddressProblem.PinNeedsHttps) }
        }
    }

    override fun cancelCertificateReview() {
        reviewed = null
        mutable.update { it.copy(pinReview = null) } // closing never grants trust (06 KitServerTrust)
    }

    override fun back() {
        pairing?.consume()
        nav.back()
    }

    // ---- the work

    private fun currentOrigin(): ServerOrigin? =
        when (mutable.value.entry) {
            EntryKind.Address -> (policy.normalize(mutable.value.address) as? Outcome.Ok)?.value
            EntryKind.PairingCode -> chosen?.origin
        }

    private suspend fun runTest() {
        when (mutable.value.entry) {
            EntryKind.Address -> testAddress()
            EntryKind.PairingCode -> testCode()
        }
    }

    private suspend fun testAddress() {
        val ui = mutable.value
        if (ui.address.isBlank()) return mutable.update { it.copy(addressProblem = AddressProblem.Empty) }
        val origin =
            when (val n = policy.normalize(ui.address)) {
                is Outcome.Failed -> return mutable.update { it.copy(addressProblem = problemOf(n.failure)) }
                is Outcome.Ok -> n.value
            }
        // B-SEC-154: trust follows the origin. Another host, port or scheme drops the pin and asks about HTTP again.
        if (editing != null && origin != editing.origin) {
            pin = null
            mutable.update { it.copy(pinned = false) }
        }
        if (policy.needsCleartextConsent(origin) && origin !in consented) {
            return mutable.update {
                it.copy(consent = CleartextConsentUi(origin, public = isPublic(origin)), cleartextAllowed = false)
            }
        }
        val target =
            ProbeTarget(
                origin = origin,
                backend = editing?.backend ?: BackendKind.OpenCode2,
                username = ui.username.ifBlank { null },
                password = password,
                pinnedSha256 = pin,
                consentedOrigin = origin.takeIf { it in consented },
            )
        probe(target)
    }

    private suspend fun testCode() {
        val payload =
            pairing
                ?: when (val parsed = PairingCode.parse(mutable.value.code)) {
                    is PairingParse.Rejected -> return mutable.update { it.copy(codeProblem = parsed.reason) }
                    is PairingParse.Ok -> parsed.value()
                }
        pairing = payload
        mutable.update { it.copy(checking = true, verdict = null) }
        val selection = PairingDialer(policy, port::probe, preferLoopback, consented.toSet()).select(payload)
        val held = selection.heldForConsent.firstOrNull()
        chosen = selection.chosenTarget
        val target = selection.chosenTarget
        mutable.update {
            when {
                target != null && selection.outcomes.last().verdict == AddressVerdict.Answered ->
                    it.copy(checking = false, verdict = EditorVerdict.Answered(target.origin, null))
                target != null -> // answered, but the password was refused: ask for it on the typed path
                    it.copy(
                        checking = false,
                        entry = EntryKind.Address,
                        address = target.origin.toString(),
                        username = target.username.orEmpty(),
                        password = SecretPresence.Empty,
                        focusPassword = true,
                        verdict = EditorVerdict.Failed(diagnose(Failure.Net.SignInNeeded(Failure.Net.SignInNeeded.Why.PasswordRejected), target.origin)),
                    )
                held != null -> it.copy(checking = false, consent = CleartextConsentUi(held, public = isPublic(held)))
                else -> it.copy(checking = false, verdict = EditorVerdict.NoAddressAnswered(selection.outcomes))
            }
        }
    }

    private fun PairingParse.Ok.value(): PairingPayload = payload

    private suspend fun probe(target: ProbeTarget) {
        mutable.update { it.copy(checking = true, verdict = null) }
        val result = port.probe(target)
        chosen = target
        mutable.update {
            when (result) {
                is Outcome.Ok -> {
                    attempts = null
                    it.copy(checking = false, verdict = EditorVerdict.Answered(target.origin, result.value.serverVersion))
                }
                is Outcome.Failed -> {
                    val count = if (attempts?.first == target.origin) attempts!!.second + 1 else 1
                    attempts = target.origin to count
                    it.copy(checking = false, verdict = EditorVerdict.Failed(diagnose(result.failure, target.origin, count)))
                }
            }
        }
    }

    private suspend fun save() {
        val target = chosen ?: return
        val ui = mutable.value
        val draft =
            ServerDraft(
                editing = editing?.id,
                name = ui.name.trim().ifEmpty { target.origin.host },
                backend = target.backend,
                origin = target.origin,
                username = target.username,
                password = target.password, // null keeps the saved one (K11: never prefilled)
                pinnedSha256 = pin.takeIf { target.origin.scheme == "https" },
                cleartextConsent = target.origin in consented,
            )
        mutable.update { it.copy(checking = true, saveFailed = null) }
        when (val saved = port.save(draft)) {
            is Outcome.Failed -> mutable.update { it.copy(checking = false, saveFailed = saved.failure) }
            is Outcome.Ok -> {
                pairing?.consume()
                password = null
                mutable.update { it.copy(code = "") }
                when (val connected = port.connect(saved.value)) {
                    is Outcome.Ok -> nav.land() // the editor never stays above the new Conversations root
                    is Outcome.Failed ->
                        mutable.update { it.copy(checking = false, verdict = EditorVerdict.Failed(diagnose(connected.failure, target.origin))) }
                }
            }
        }
    }

    private fun diagnose(failure: Failure, origin: ServerOrigin, count: Int = 1): ConnectionFailureUi =
        ConnectionDiagnosis.diagnose(
            failure,
            origin,
            DiagnosisContext(
                backend = editing?.backend ?: BackendKind.OpenCode2,
                supportsTermux = env.supportsTermux,
                managedPhoneServer = editing?.managedOnPhone == true,
                attempts = count,
            ),
            policy,
        )

    private fun isPublic(origin: ServerOrigin): Boolean =
        !policy.isPrivateNetwork(origin.host) && !policy.isTailnet(origin.host) && !policy.isLoopback(origin.host)

    private fun problemOf(failure: Failure): AddressProblem =
        when ((failure as? Failure.Refused)?.reason) {
            RefusalReason.CredentialsInAddress -> AddressProblem.CredentialsInAddress
            RefusalReason.PinNeedsHttps -> AddressProblem.PinNeedsHttps
            else -> AddressProblem.Invalid
        }

    override fun onCleared() {
        pairing?.consume()
        password = null
    }
}

/** `AA:BB:…` display of a SHA-256 hex fingerprint (KitServerTrust shows it expanded, LTR). */
public object Fingerprints {
    public fun display(hex: String): String = hex.uppercase().chunked(2).joinToString(":")
}

/** `yyyy-MM-dd` (UTC) for certificate validity lines; numbers, not copy (Howard Hinnant's civil-from-days). */
public object CivilDate {
    public fun of(epochMs: Long): String {
        val z = epochMs.floorDiv(86_400_000L) + 719_468
        val era = z.floorDiv(146_097L)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        val y = yoe + era * 400 + if (m <= 2) 1 else 0
        return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
    }
}
