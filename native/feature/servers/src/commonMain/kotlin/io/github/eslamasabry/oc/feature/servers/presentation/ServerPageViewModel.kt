package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One server's page with its connection details (07 §5 E04): pinned to its profile, every action names the server,
 * Disconnect → `resetTo(Servers)`, and a changed certificate is reviewed here before it is trusted (B-NAV-008).
 * [state] is null once the profile is gone (N12 then removes the page).
 */
public class ServerPageViewModel(
    private val id: ProfileId,
    private val port: ServersPort,
    private val policy: AddressPolicy,
    private val env: ServersEnvironment,
    private val nav: ServersNavigator,
    scope: CoroutineScope,
) : ViewModel(scope), ServerPageActions {
    private data class Local(
        val review: PinReviewUi? = null,
        val reviewedHex: String? = null,
        val unreadable: Boolean = false,
        val forgetting: Boolean = false,
        val busy: Boolean = false,
    )

    private val local = MutableStateFlow(Local())

    public val state: StateFlow<ServerPageUi?> =
        combine(port.servers, local) { all, l ->
            val s = all.firstOrNull { it.profile.id == id } ?: return@combine null
            val p = s.profile
            ServerPageUi(
                id = p.id,
                name = p.name,
                address = p.origin.toString(),
                backend = p.backend,
                status = s.status,
                active = s.active,
                security =
                    when {
                        policy.isLoopback(p.origin.host) -> TransportSecurity.ThisDevice
                        p.origin.scheme == "https" && p.trust.pinnedLeafSha256 != null -> TransportSecurity.HttpsPinned
                        p.origin.scheme == "https" -> TransportSecurity.Https
                        p.trust.cleartextConsentAtMs != null -> TransportSecurity.HttpAllowed
                        else -> TransportSecurity.HttpNotAllowed
                    },
                pinFingerprint = p.trust.pinnedLeafSha256?.let(Fingerprints::display),
                failure = s.failureUi(env, policy),
                pinReview = l.review,
                certificateUnreadable = l.unreadable,
                forgetting = l.forgetting,
                busy = l.busy || s.status == ServerStatus.Connecting,
            )
        }.stateIn(scope, SharingStarted.Eagerly, null)

    override fun connect() {
        if (local.value.busy) return
        local.update { it.copy(busy = true) }
        viewModelScope.launch {
            val r = port.connect(id)
            local.update { it.copy(busy = false) }
            if (r is Outcome.Ok) nav.land()
        }
    }

    override fun disconnect() {
        viewModelScope.launch {
            port.disconnect(id)
            nav.resetToServers()
        }
    }

    override fun edit(): Unit = nav.openEditor(EditorMode.Edit(id))

    override fun fix(action: FailureAction) {
        when (action) {
            FailureAction.Retry, FailureAction.StartPhoneServer -> connect()
            FailureAction.UpdatePassword, FailureAction.UpdateToken -> nav.openEditor(EditorMode.Credentials(id), focusPassword = true)
            FailureAction.ReviewCertificate -> reviewCertificate()
            FailureAction.EditServer, FailureAction.ChangeServer -> edit()
        }
    }

    override fun reviewCertificate() {
        val profile = port.servers.value.firstOrNull { it.profile.id == id }?.profile ?: return
        viewModelScope.launch {
            local.update { it.copy(busy = true, unreadable = false) }
            when (val r = port.inspectCertificate(profile.origin)) {
                is Outcome.Ok ->
                    local.update {
                        it.copy(
                            busy = false,
                            reviewedHex = r.value.sha256Hex,
                            review =
                                PinReviewUi(
                                    origin = profile.origin,
                                    fingerprint = Fingerprints.display(r.value.sha256Hex),
                                    notBeforeMs = r.value.notBeforeMs,
                                    notAfterMs = r.value.notAfterMs,
                                    previousFingerprint = profile.trust.pinnedLeafSha256?.let(Fingerprints::display),
                                ),
                        )
                    }
                is Outcome.Failed -> local.update { it.copy(busy = false, unreadable = true) }
            }
        }
    }

    override fun trustCertificate() {
        val review = local.value.review ?: return
        val hex = local.value.reviewedHex ?: return
        val checked = policy.checkPin(review.origin, hex) as? Outcome.Ok ?: return cancelCertificateReview()
        local.update { it.copy(review = null, reviewedHex = null, busy = true) }
        viewModelScope.launch {
            val trusted = port.trustCertificate(id, checked.value)
            local.update { it.copy(busy = false) }
            if (trusted is Outcome.Ok) connect()
        }
    }

    override fun cancelCertificateReview(): Unit = local.update { it.copy(review = null, reviewedHex = null) }

    override fun forget(): Unit = local.update { it.copy(forgetting = true) }

    override fun cancelForget(): Unit = local.update { it.copy(forgetting = false) }

    override fun confirmForget() {
        local.update { it.copy(forgetting = false) }
        viewModelScope.launch {
            if (port.forget(id) is Outcome.Ok) nav.resetToServers()
        }
    }

    override fun back(): Unit = nav.back()
}

/**
 * The server switcher (07 §2.3, A08): only chooses, and acts after it closed (N2 is the shell's). Same-host servers
 * are told apart by port (B-NAV-040/041). Choosing the current server does nothing but close.
 */
public class ServerSwitcherViewModel(
    private val port: ServersPort,
    private val nav: ServersNavigator,
    scope: CoroutineScope,
) : ViewModel(scope), ServerSwitcherActions {
    public val state: StateFlow<ServerSwitcherUi> =
        port.servers
            .map { all ->
                val hosts = all.groupingBy { it.profile.origin.host }.eachCount()
                ServerSwitcherUi(
                    all
                        .sortedWith(compareByDescending<SavedServer> { it.active }.thenBy { it.profile.name.lowercase() })
                        .map { s ->
                            SwitcherRowUi(
                                id = s.profile.id,
                                name = s.profile.name,
                                host = s.profile.origin.host,
                                port = s.profile.origin.port.takeIf { (hosts[s.profile.origin.host] ?: 0) > 1 },
                                status = s.status,
                                current = s.active,
                            )
                        }.toImmutableList(),
                )
            }.stateIn(scope, SharingStarted.Eagerly, ServerSwitcherUi())

    override fun choose(id: ProfileId) {
        val current = port.servers.value.firstOrNull { it.active }?.profile?.id
        nav.back() // close the switcher first; the shell waits for the exit (N2)
        if (id == current) return
        viewModelScope.launch {
            when (port.connect(id)) {
                is Outcome.Ok -> nav.land()
                is Outcome.Failed -> nav.resetToServers() // the failure shows inline on its row
            }
        }
    }

    override fun manage(id: ProfileId) {
        nav.back()
        nav.openServerPage(id)
    }

    override fun addServer() {
        nav.back()
        nav.openEditor(EditorMode.Add)
    }
}
