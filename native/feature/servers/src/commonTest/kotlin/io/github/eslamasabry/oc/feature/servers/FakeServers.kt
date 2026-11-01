package io.github.eslamasabry.oc.feature.servers

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ProfileTrust
import io.github.eslamasabry.oc.core.model.ServerOrigin
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.feature.servers.presentation.CertificateSummary
import io.github.eslamasabry.oc.feature.servers.presentation.EditorMode
import io.github.eslamasabry.oc.feature.servers.presentation.ProbeAnswer
import io.github.eslamasabry.oc.feature.servers.presentation.ProbeTarget
import io.github.eslamasabry.oc.feature.servers.presentation.SavedServer
import io.github.eslamasabry.oc.feature.servers.presentation.ServerDraft
import io.github.eslamasabry.oc.feature.servers.presentation.ServerStatus
import io.github.eslamasabry.oc.feature.servers.presentation.ServersNavigator
import io.github.eslamasabry.oc.feature.servers.presentation.ServersPort
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A profile for tests. Names are fake; addresses are documentation ranges or loopback. */
fun profile(
    id: String,
    name: String,
    origin: ServerOrigin,
    pin: String? = null,
    consentAtMs: Long? = null,
    onPhone: Boolean = false,
    backend: BackendKind = BackendKind.OpenCode2,
): ServerProfile =
    ServerProfile(ProfileId(id), name, backend, origin, ProfileTrust(pin, consentAtMs), onPhone, "opencode")

/**
 * An in-memory [ServersPort] that records every probe (so "never probed" is checkable) and answers from a table.
 * Failures from connect are kept on the row with an attempt count, as the connection controller does.
 */
class FakeServersPort(initial: List<SavedServer> = emptyList()) : ServersPort {
    private val mutable = MutableStateFlow<ImmutableList<SavedServer>>(initial.toImmutableList())
    override val servers: StateFlow<ImmutableList<SavedServer>> = mutable

    val probes = mutableListOf<ProbeTarget>()
    val saved = mutableListOf<ServerDraft>()
    val connects = mutableListOf<ProfileId>()
    val inspected = mutableListOf<ServerOrigin>()
    val trusted = mutableListOf<Pair<ProfileId, String>>()
    var probeAnswer: (ProbeTarget) -> Outcome<ProbeAnswer> = { Outcome.Ok(ProbeAnswer("2.1.1")) }
    var connectAnswer: (ProfileId) -> Outcome<Unit> = { Outcome.Ok(Unit) }
    var certificate: Outcome<CertificateSummary> = Outcome.Ok(CERT)
    private var nextId = 100

    override suspend fun probe(target: ProbeTarget): Outcome<ProbeAnswer> {
        probes += target
        return probeAnswer(target)
    }

    override suspend fun save(draft: ServerDraft): Outcome<ProfileId> {
        saved += draft
        val id = draft.editing ?: ProfileId("p${nextId++}")
        val p =
            ServerProfile(
                id, draft.name, draft.backend, draft.origin,
                ProfileTrust(draft.pinnedSha256, if (draft.cleartextConsent) 1L else null), false, draft.username,
            )
        mutable.update { list -> (list.filterNot { it.profile.id == id } + SavedServer(p, ServerStatus.NotConnected, false)).toImmutableList() }
        return Outcome.Ok(id)
    }

    override suspend fun connect(id: ProfileId): Outcome<Unit> {
        connects += id
        val r = connectAnswer(id)
        mutable.update { list ->
            list.map { s ->
                when {
                    s.profile.id == id && r is Outcome.Ok -> s.copy(status = ServerStatus.Connected, active = true, failure = null, attempts = 0)
                    s.profile.id == id && r is Outcome.Failed ->
                        s.copy(status = ServerStatus.Failed, failure = r.failure, attempts = s.attempts + 1)
                    r is Outcome.Ok -> s.copy(active = false, status = if (s.active) ServerStatus.NotConnected else s.status)
                    else -> s
                }
            }.toImmutableList()
        }
        return r
    }

    override suspend fun disconnect(id: ProfileId) {
        mutable.update { list ->
            list.map { if (it.profile.id == id) it.copy(status = ServerStatus.NotConnected, active = false) else it }.toImmutableList()
        }
    }

    override suspend fun forget(id: ProfileId): Outcome<Unit> {
        mutable.update { list -> list.filterNot { it.profile.id == id }.toImmutableList() }
        return Outcome.Ok(Unit)
    }

    override suspend fun inspectCertificate(origin: ServerOrigin): Outcome<CertificateSummary> {
        inspected += origin
        return certificate
    }

    override suspend fun trustCertificate(id: ProfileId, sha256Hex: String): Outcome<Unit> {
        trusted += id to sha256Hex
        mutable.update { list ->
            list.map {
                if (it.profile.id == id) it.copy(profile = it.profile.copy(trust = it.profile.trust.copy(pinnedLeafSha256 = sha256Hex))) else it
            }.toImmutableList()
        }
        return Outcome.Ok(Unit)
    }

    fun fail(id: ProfileId, failure: Failure, attempts: Int = 1) {
        mutable.update { list ->
            list.map { if (it.profile.id == id) it.copy(status = ServerStatus.Failed, failure = failure, attempts = attempts) else it }
                .toImmutableList()
        }
    }

    companion object {
        const val SHA = "ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12cd34ef56ab12"
        const val SHA2 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val CERT = CertificateSummary(SHA, "CN=desk", "CN=desk", 1_700_000_000_000, 1_800_000_000_000)
    }
}

class RecordingServersNavigator : ServersNavigator {
    val calls = mutableListOf<String>()

    override fun openEditor(mode: EditorMode, focusPassword: Boolean) {
        calls += "editor $mode focus=$focusPassword"
    }

    override fun openServerPage(id: ProfileId) {
        calls += "page ${id.raw}"
    }

    override fun land() {
        calls += "land"
    }

    override fun resetToServers() {
        calls += "resetToServers"
    }

    override fun back() {
        calls += "back"
    }
}

val NO_SERVERS: List<SavedServer> = persistentListOf()
