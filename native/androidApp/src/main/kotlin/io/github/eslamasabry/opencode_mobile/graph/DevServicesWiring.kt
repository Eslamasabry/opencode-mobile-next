package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScan
import io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScriptResult
import io.github.eslamasabry.oc.engine.runtime.devservers.PhoneScriptRunner
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceSettings
import io.github.eslamasabry.oc.engine.runtime.services.commands.ManagedShell
import io.github.eslamasabry.oc.engine.runtime.services.commands.ManagedShellList
import io.github.eslamasabry.oc.engine.runtime.services.commands.ManagedShellOutput
import io.github.eslamasabry.oc.engine.runtime.services.commands.ManagedShellPort
import io.github.eslamasabry.oc.engine.runtime.services.commands.ManagedShellStatus
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesContext
import io.github.eslamasabry.oc.feature.devservices.presentation.DevServicesHost
import io.github.eslamasabry.oc.feature.devservices.presentation.NoDevServicesHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import java.security.SecureRandom
import kotlin.time.Clock
import io.github.eslamasabry.oc.core.model.ManagedShell as WireShell

/*
 * BUILD-RUNTIME-A wiring: Development services (:feature:devservices) over the live connection's managed shells, the
 * Flutter prefs key `oc.developmentServices.<profileId>` (so migrated services read as-is; the profile sweep's
 * `oc.<what>.<id>` rule removes it), and the in-app Linux for the phone scan when the server is this phone's own.
 */

/** Phone scans and stops get the same 25 s the Flutter channel gave them. */
private const val PHONE_SCRIPT_SECONDS = 25L

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoDevServicesHost::class])
@Inject
class LiveDevServicesHost(
    private val app: Application,
    private val registry: ConnectionRegistry,
    private val stores: PreviewStores,
    private val platform: PlatformCapabilities,
    private val linux: LinuxHost,
    private val clock: Clock,
) : DevServicesHost {
    private val random = SecureRandom()

    override fun open(profile: ProfileId, directory: String): DevServicesContext? {
        val graph = registry.live.value[profile] ?: return null
        val loaded = stores.profiles.profile(profile) ?: return null
        val operations = graph.session.server.facet<ServerOperationsGateway>()
        val lane = stores.lanes.forProfile(profile)
        val settings =
            object : DevelopmentServiceSettings {
                override suspend fun read(key: String): String? = (stores.prefs.get(key) as? PrefValue.Str)?.value

                override suspend fun write(key: String, value: String): Outcome<Unit> =
                    stores.prefs.commit(lane) { putString(key, value) }

                override suspend fun remove(key: String): Outcome<Unit> = stores.prefs.commit(lane) { remove(key) }
            }
        val phone =
            PhoneScan.runnerFor(
                serverAddress = loaded.profile.baseUrl,
                linuxSupported = platform.builtinLinux,
                linux = PhoneScriptRunner { script ->
                    when (val run = linux.run(script, PHONE_SCRIPT_SECONDS)) {
                        is Outcome.Ok -> Outcome.Ok(PhoneScriptResult(run.value.exitCode, run.value.output))
                        is Outcome.Failed -> run
                    }
                },
            )
        return DevServicesContext(
            settings = settings,
            canWrite = { stores.profiles.profile(profile) != null },
            workspace = null,
            supported = { graph.session.capabilities.value.developmentServices },
            shells = { operations?.let(::GatewayShells) },
            phone = phone,
            isCurrent = { registry.live.value[profile] === graph && stores.profiles.profile(profile) != null },
            newId = ::newId,
            openLink = ::openLink,
            clock = clock,
        )
    }

    /** 24 secure random bytes as hex (Flutter `DevelopmentServices.newID`). */
    private fun newId(): String {
        val bytes = ByteArray(24).also(random::nextBytes)
        return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    /** A saved preview address was typed by the person, so it goes through the link policy like server content. */
    private fun openLink(url: String): Boolean =
        when (val decision = ExternalLinkPolicy().check(url, LinkOrigin.ServerContent)) {
            is LinkDecision.Open -> view(decision.link.uri)
            is LinkDecision.AskFirst, is LinkDecision.Refuse -> false
        }

    private fun view(uri: String): Boolean =
        try {
            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
}

/** The engine's shell port over the connection's ManagedShellGateway (field for field the same contract). */
private class GatewayShells(private val gateway: ServerOperationsGateway) : ManagedShellPort {
    override suspend fun start(command: String, directory: String, ownerToken: String): Outcome<ManagedShell> =
        gateway.startManagedShell(command, directory, ownerToken).map(::shell)

    override suspend fun running(): Outcome<ManagedShellList> =
        gateway.runningShells().map { ManagedShellList(it.supported, it.shells.map(::shell)) }

    override suspend fun get(id: String): Outcome<ManagedShell?> = gateway.managedShell(id).map { it?.let(::shell) }

    override suspend fun output(id: String, cursor: Long, limit: Int): Outcome<ManagedShellOutput> =
        gateway.managedShellOutput(id, cursor, limit).map { ManagedShellOutput(it.text, it.cursor, it.size, it.truncated) }

    override suspend fun stop(id: String): Outcome<Unit> = gateway.stopManagedShell(id)

    private fun shell(wire: WireShell): ManagedShell =
        ManagedShell(
            id = wire.id,
            command = wire.command,
            status = ManagedShellStatus.valueOf(wire.status.name),
            startedAtMillis = wire.startedAtMs,
            completedAtMillis = wire.completedAtMs,
            exitCode = wire.exitCode,
            sessionId = wire.sessionId,
            directory = wire.directory,
            ownerToken = wire.ownerToken,
        )

    private inline fun <T, R> Outcome<T>.map(f: (T) -> R): Outcome<R> =
        when (this) {
            is Outcome.Ok -> Outcome.Ok(f(value))
            is Outcome.Failed -> this
        }
}
