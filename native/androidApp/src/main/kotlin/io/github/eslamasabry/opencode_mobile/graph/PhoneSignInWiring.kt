package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.background.ForegroundLease
import io.github.eslamasabry.oc.android.background.LeaseAdmission
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.core.domain.facet
import io.github.eslamasabry.oc.core.security.*
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentAvailability
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.probe.AuthScriptAction
import io.github.eslamasabry.oc.engine.agents.signin.SignInCommandPolicy
import io.github.eslamasabry.oc.engine.agents.signin.SignInOutputReader
import io.github.eslamasabry.oc.engine.agents.signin.SignInRow
import io.github.eslamasabry.oc.engine.connection.ConnectionGraph
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.feature.agents.presentation.*
import io.github.eslamasabry.oc.feature.agents.signin.*
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.ui.kit.content.terminal.AndroidRemoteTerminalView
import io.github.eslamasabry.oc.ui.kit.content.terminal.TerminalTranscript
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phone sign-in is a separate, explicitly created PTY on the real app-owned loopback server. It
 * uses the native profile home and catalog command, never types executable text into an existing
 * shell, and drains before the connection owner during profile deletion. Provider output is
 * transient and never logged or persisted.
 */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(
    AppScope::class,
    binding = binding<PhoneSignInFactory>(),
    replaces = [UnavailablePhoneSignIn::class],
)
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class NativePhoneSignIn(
    private val app: Application,
    private val stores: PreviewStores,
    private val runtime: BuiltinRuntime,
    private val server: Lazy<PhoneServerSource>,
    private val connections: Lazy<ServerConnections>,
    private val registry: ConnectionRegistry,
    private val background: BackgroundHub,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
    private val policy: ExternalLinkPolicy,
    private val opener: ExternalLinkOpener,
) : PhoneSignInFactory, ProfileCloser {
    override val owner: String = "phone-sign-in"
    override val drainOrder: Int = -100
    private val blocked = ConcurrentHashMap.newKeySet<ProfileId>()
    private val controllers = LinkedHashMap<Pair<ProfileId, String>, LivePhoneSignIn>()
    private val linux by lazy { BuiltinLinux.get(app) }
    private val catalog = AgentCatalog.builtIn
    private val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)

    override fun create(
        owner: ProfileId,
        agent: String,
        onLeave: () -> Unit,
    ): PhoneSignInController {
        val key = owner to agent
        return controllers.getOrPut(key) {
            val scope =
                CoroutineScope(main.coroutineContext + SupervisorJob(main.coroutineContext[Job]))
            LivePhoneSignIn(owner, agent, scope, onLeave) { controller ->
                main.launch {
                    if (controller.drain() is Outcome.Ok) {
                        if (controllers[key] === controller) controllers.remove(key)
                        scope.cancel()
                    }
                }
            }
        }
    }

    override fun stopAdmission(profile: ProfileId) {
        blocked.add(profile)
    }

    override fun reopen(profile: ProfileId) {
        blocked.remove(profile)
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> =
        withContext(dispatchers.main) {
            val owned = controllers.filterKeys { it.first == profile }.toMap()
            for ((key, controller) in owned) {
                when (val result = controller.drain()) {
                    is Outcome.Failed -> return@withContext result
                    is Outcome.Ok -> {
                        controllers.remove(key)
                        controller.scope.cancel()
                    }
                }
            }
            Unit.ok()
        }

    private fun owns(profile: ProfileId): Boolean =
        profile !in blocked &&
            stores.prefs.string("oc.builtinServerOwner") == profile.raw &&
            stores.profiles.profile(profile) != null

    private val auth =
        object : SignInAgents {
            override fun plan(agent: AgentRef): SignInPlan {
                val descriptor =
                    catalog.byId(AgentId(agent.id)) ?: return SignInPlan.NotAvailable(agent.id)
                if (
                    !descriptor.runsOnPhoneHost ||
                        descriptor.availability == AgentAvailability.Hidden
                )
                    return SignInPlan.NotAvailable(descriptor.name)
                val recipe = descriptor.recipe ?: return SignInPlan.NotAvailable(descriptor.name)
                return when (descriptor.signInMethod) {
                    AgentSignInMethod.ApiKeyHost -> SignInPlan.ProviderKeyOnHost(descriptor.name)
                    AgentSignInMethod.None -> SignInPlan.NotAvailable(descriptor.name)
                    AgentSignInMethod.BrowserOAuthHost ->
                        if (SignInCommandPolicy.allows(recipe.executable, recipe.signInArgs))
                            SignInPlan.Terminal(descriptor.name, agent.id == "claude")
                        else SignInPlan.NotAvailable(descriptor.name)
                }
            }

            override fun command(owner: ProfileId, agent: AgentRef): Outcome<SignInCommand> {
                if (!owns(owner)) return Failure.Refused(RefusalReason.StaleScope).failed()
                val recipe =
                    catalog.byId(AgentId(agent.id))?.recipe
                        ?: return Failure.Unavailable(FeatureId("phoneSignIn")).failed()
                if (!SignInCommandPolicy.allows(recipe.executable, recipe.signInArgs))
                    return Failure.Refused(RefusalReason.StaleScope).failed()
                return SignInCommand(recipe.executable, recipe.signInArgs, home = owner.raw).ok()
            }

            override suspend fun confirmSignedIn(owner: ProfileId, agent: AgentRef): Boolean {
                if (!owns(owner)) return false
                val descriptor = catalog.byId(AgentId(agent.id)) ?: return false
                val script =
                    AgentAuthScripts.script(descriptor, AuthScriptAction.Probe) ?: return false
                val result =
                    withContext(dispatchers.io) {
                        attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                            linux.agentAuthProbe(
                                mapOf(
                                    "profileId" to owner.raw,
                                    "agentId" to agent.id,
                                    "action" to "probe",
                                    "script" to AgentAuthScripts.forProfile(owner, script),
                                    "timeoutSeconds" to if (agent.id == "claude") 65 else 10,
                                )
                            )
                        }
                    }
                return owns(owner) && (result as? Outcome.Ok)?.value?.get("state") == "signedIn"
            }

            override fun read(transcript: String): SignInFound =
                SignInOutputReader.read(transcript.lines().map(::SignInRow)).let {
                    SignInFound(it.page?.url, it.code)
                }
        }

    private suspend fun open(
        owner: ProfileId,
        target: TerminalTarget,
        scope: CoroutineScope,
    ): Outcome<TerminalModel> {
        if (!owns(owner)) return Failure.Refused(RefusalReason.StaleScope).failed()
        val command = (target as TerminalTarget.SignIn).command
        if (
            command.home != owner.raw ||
                command.env.isNotEmpty() ||
                !SignInCommandPolicy.allows(command.program, command.args)
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val prepared =
            withContext(dispatchers.io) {
                attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                    linux
                        .agentSignInCommand(owner.raw, listOf(command.program) + command.args)
                        .second
                }
            }
        if (prepared is Outcome.Failed) return prepared
        if (!owns(owner)) return Failure.Refused(RefusalReason.StaleScope).failed()
        when (val started = server.value.start()) {
            is Outcome.Failed -> return started
            is Outcome.Ok -> Unit
        }
        if (!owns(owner)) return Failure.Refused(RefusalReason.StaleScope).failed()
        when (val connected = connections.value.connect(owner)) {
            is Outcome.Failed -> return connected
            is Outcome.Ok -> Unit
        }
        val graph =
            registry.live.value[owner] ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val status = runtime.runtimeStatus()
        if (status is Outcome.Failed) return status
        val native = (status as Outcome.Ok).value
        val profile =
            stores.profiles.profile(owner)?.toServerProfile(managedOnPhone = true)
                ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        if (
            !owns(owner) ||
                !AddressRules.isLoopback(profile.origin.host) ||
                profile.origin.port != native.serverPort ||
                !native.serverRunning ||
                !graph.session.capabilities.value.terminal
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val operations =
            graph.session.server.facet<ServerOperationsGateway>()
                ?: return Failure.Unavailable(FeatureId("terminal")).failed()
        val home = "/home/oc/.oc-profiles/${owner.raw}"
        val args =
            listOf(
                "-C",
                home,
                "HOME=$home",
                "CLAUDE_CONFIG_DIR=$home/claude",
                "CLAUDE_HOME=$home/claude",
                "CODEX_HOME=$home/codex",
                "BROWSER=$home/.oc-bin/open-url",
                "PATH=$home/.oc-bin:/home/oc/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "DISABLE_AUTOUPDATER=1",
                "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1",
                command.program,
            ) + command.args
        val created =
            operations.createCommandTerminal(
                "/usr/bin/env",
                args.toImmutableList(),
                "OpenCode Mobile agent sign-in",
            )
        if (created is Outcome.Failed) return created
        val row = (created as Outcome.Ok).value
        if (!owns(owner) || registry.live.value[owner] !== graph) {
            operations.removeTerminal(row.id)
            return Failure.Refused(RefusalReason.StaleScope).failed()
        }
        val channel = operations.connectTerminal(row.id)
        if (channel is Outcome.Failed) {
            operations.removeTerminal(row.id)
            return channel
        }
        return OwnedTerminal(
                owner,
                graph,
                operations,
                row.id,
                (channel as Outcome.Ok).value,
                (prepared as Outcome.Ok).value,
                scope,
                command.program,
            )
            .ok()
    }

    private inner class OwnedTerminal(
        private val ownerId: ProfileId,
        private val graph: ConnectionGraph,
        private val operations: ServerOperationsGateway,
        private val id: String,
        private val channel: io.github.eslamasabry.oc.core.model.TerminalChannel,
        private val openRequest: File,
        private val scope: CoroutineScope,
        program: String,
    ) : TerminalModel {
        private val output = MutableStateFlow("")
        private val eventsChannel = Channel<TerminalEvent>(64)
        private val transcriptBuilder = TerminalTranscript()
        private val lane = Mutex()
        private val writes = Channel<ByteArray>(Channel.UNLIMITED)
        private var closed = false
        private val terminal =
            AndroidRemoteTerminalView(
                app,
                { writes.trySend(it.encodeToByteArray()) },
                { rows, cols ->
                    scope.launch { if (!closed) operations.resizeTerminal(id, rows, cols) }
                },
            )
        override val view: PlatformViewHandle =
            object : PlatformViewHandle {
                override val native: Any = terminal
            }
        override val title: StateFlow<String> = MutableStateFlow(program)
        override val transcript: StateFlow<String> = output.asStateFlow()
        override val events: Flow<TerminalEvent> = eventsChannel.receiveAsFlow()
        private val writer = scope.launch {
            for (bytes in writes) {
                if (closed || registry.live.value[ownerId] !== graph || !owns(ownerId)) continue
                attempt({ Failure.Network() }) { channel.write(bytes.decodeToString()) }
            }
        }
        private val reader = scope.launch {
            terminal.enableInput(true)
            terminal.publishSize()
            attempt({ Failure.Network() }) {
                channel.output.collect { text ->
                    if (!closed) {
                        transcriptBuilder.add(text)
                        terminal.append(text)
                        output.value = transcriptBuilder.text
                    }
                }
            }
            if (!closed) {
                terminal.enableInput(false)
                val result = operations.listTerminals()
                val exit =
                    (result as? Outcome.Ok)?.value?.firstOrNull { it.id == id }?.exitCode ?: -1
                eventsChannel.trySend(TerminalEvent.Exited(exit))
            }
        }
        private val requests = scope.launch {
            while (isActive && !closed) {
                val url =
                    withContext(dispatchers.io) {
                        attempt({ Failure.StorageRefused() }) {
                                if (
                                    !Files.isSymbolicLink(openRequest.toPath()) &&
                                        openRequest.isFile &&
                                        openRequest.length() <= 8192
                                ) {
                                    val value = openRequest.readText().trim()
                                    openRequest.delete()
                                    value.takeIf { it.isNotEmpty() }
                                } else null
                            }
                            .getOrNull()
                    }
                if (url != null && !closed) eventsChannel.trySend(TerminalEvent.OpenUrl(url))
                delay(300)
            }
        }

        override suspend fun write(bytes: ByteArray) {
            if (!closed) writes.send(bytes.copyOf())
        }

        override suspend fun resize(cols: Int, rows: Int) {
            if (!closed) operations.resizeTerminal(id, rows, cols)
        }

        override suspend fun close(): Outcome<Unit> = lane.withLock {
            if (closed) return@withLock Unit.ok()
            // Cleanup is admitted after stopAdmission too; it uses the captured exact connection
            // and PTY id.
            if (registry.live.value[ownerId] !== graph)
                return@withLock Failure.Refused(RefusalReason.StaleScope).failed()
            when (val removed = operations.removeTerminal(id)) {
                is Outcome.Failed -> removed
                is Outcome.Ok -> {
                    closed = true
                    terminal.enableInput(false)
                    writes.close()
                    writer.cancel()
                    requests.cancel()
                    reader.cancel()
                    attempt({ Failure.Network() }) { channel.close() }
                    eventsChannel.close()
                    Unit.ok()
                }
            }
        }
    }

    private inner class LivePhoneSignIn(
        private val ownerId: ProfileId,
        agent: String,
        val scope: CoroutineScope,
        onLeave: () -> Unit,
        private val disposeAction: (LivePhoneSignIn) -> Unit,
    ) : PhoneSignInController {
        private val foreground = PhoneForeground(scope)
        private val pending = MutableStateFlow<ApprovedLink?>(null)
        private val host = MutableStateFlow<String?>(null)
        override val pageHost: StateFlow<String?> = host.asStateFlow()
        private val links = SignInLinks { url ->
            if (!foreground.active) Failure.Refused(RefusalReason.StaleScope).failed()
            else
                when (val decision = policy.checkSignInPage(url)) {
                    is LinkDecision.Open -> opener.open(decision.link)
                    is LinkDecision.AskFirst -> {
                        pending.value = decision.link
                        host.value = decision.shownHost
                        Unit.ok()
                    }
                    is LinkDecision.Refuse -> Failure.Refused(RefusalReason.StaleScope).failed()
                }
        }
        private val clipboard = SignInClipboard { text ->
            val clip = ClipData.newPlainText("", text)
            clip.description.extras =
                PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                clip
            )
        }
        private val presenter =
            SignInTerminalPresenter(
                scope,
                ownerId,
                AgentRef(agent),
                auth,
                object : TerminalHost {
                    override suspend fun open(target: TerminalTarget): Outcome<TerminalModel> =
                        this@NativePhoneSignIn.open(ownerId, target, scope)
                },
                foreground,
                links,
                clipboard,
                onLeave,
            )
        override val state: StateFlow<SignInTerminalUi> = presenter.state

        init {
            scope.launch { if (stores.warm() is Outcome.Ok) presenter.begin() }
        }

        override fun approvePage() {
            val link = pending.value ?: return
            dismissPage()
            if (foreground.active && owns(ownerId)) opener.open(link)
        }

        override fun dismissPage() {
            pending.value = null
            host.value = null
        }

        override fun dispose() {
            dismissPage()
            disposeAction(this)
        }

        suspend fun drain(): Outcome<Unit> {
            dismissPage()
            val result = presenter.drain()
            return if (result is Outcome.Failed) result else foreground.retryRelease()
        }

        override fun openSignInPage(): Unit = presenter.openSignInPage()

        override fun copyCode(): Unit = presenter.copyCode()

        override fun send(line: String): Unit = presenter.send(line)

        override fun setAccessibleMode(on: Boolean): Unit = presenter.setAccessibleMode(on)

        override fun finish(): Unit = presenter.finish()

        override fun startAgain(): Unit = presenter.startAgain()

        override fun leave(): Unit = presenter.leave()
    }

    private inner class PhoneForeground(private val scope: CoroutineScope) : SignInForeground {
        private val held = mutableListOf<Lease>()
        val active: Boolean
            get() = held.any { it.active }

        override fun reserve(): SignInForegroundLease = Lease().also { held += it }

        suspend fun retryRelease(): Outcome<Unit> {
            for (lease in held.toList()) {
                val result = lease.releaseNative()
                if (result is Outcome.Failed) return result
                held.remove(lease)
            }
            return Unit.ok()
        }

        private inner class Lease : SignInForegroundLease {
            private val ready = CompletableDeferred<ForegroundLease?>()
            private val losses = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)
            private var current: ForegroundLease? = null
            private var released = false
            private val opening = scope.launch {
                val controller = background.controller()
                if (released) {
                    ready.complete(null)
                    return@launch
                }
                val lease = controller.reserve()
                current = lease
                ready.complete(lease)
                lease.lost.collect { loss -> losses.emit(loss.code ?: "service-gone") }
            }
            override val active: Boolean
                get() = !released && current?.active == true

            override val lost: Flow<String> = losses.asSharedFlow()

            override suspend fun admitted(): SignInAdmission {
                val lease = ready.await() ?: return SignInAdmission.Refused("cancelled")
                return when (val admission = lease.ready()) {
                    LeaseAdmission.Ready -> SignInAdmission.Ready
                    is LeaseAdmission.Refused ->
                        if (admission.loss.code == "notifications-blocked")
                            SignInAdmission.NotificationsBlocked
                        else SignInAdmission.Refused(admission.loss.code ?: "service-refused")
                }
            }

            override suspend fun release() {
                releaseNative()
            }

            suspend fun releaseNative(): Outcome<Unit> {
                released = true
                val lease = current
                if (lease == null) {
                    opening.cancel()
                    if (!ready.isCompleted) ready.complete(null)
                    return Unit.ok()
                }
                val result = lease.release()
                if (result is Outcome.Ok) opening.cancel()
                return result
            }
        }
    }
}
