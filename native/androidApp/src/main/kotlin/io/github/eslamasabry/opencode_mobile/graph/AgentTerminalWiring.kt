package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import dev.zacsweers.metro.*
import io.github.eslamasabry.oc.android.background.ForegroundFailure
import io.github.eslamasabry.oc.android.background.ForegroundLease
import io.github.eslamasabry.oc.android.background.LeaseAdmission
import io.github.eslamasabry.oc.android.terminal.NativeTerminalEvent
import io.github.eslamasabry.oc.android.terminal.NativeTerminalManager
import io.github.eslamasabry.oc.android.terminal.NativeTerminalProgram
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.probe.AuthScriptAction
import io.github.eslamasabry.oc.engine.agents.signin.SignInOutputReader
import io.github.eslamasabry.oc.engine.agents.signin.SignInRow
import io.github.eslamasabry.oc.engine.profiles.ProfileCloser
import io.github.eslamasabry.oc.feature.agents.signin.*
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.LocalTerminal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The app owns the cross-module binding from Linux launch recipes to platform PTYs. */
@SingleIn(AppScope::class)
@Inject
class LinuxTerminalBridge(
    private val app: Application,
    private val manager: NativeTerminalManager,
    private val dispatchers: AppDispatchers,
) : LocalTerminal.Driver {
    private val linux by lazy { BuiltinLinux.get(app) }

    init {
        install()
    }

    fun install() {
        LocalTerminal.get(app).bind(this)
    }

    private fun openNative(
        rows: Int,
        cols: Int,
        owner: String?,
        program: List<String>?,
    ): NativeTerminalManager.NativeSession {
        val recipe =
            synchronized(linux) {
                check(linux.installed)
                val signIn = owner?.let {
                    linux.agentSignInCommand(
                        it,
                        program ?: listOf("claude", "auth", "login", "--claudeai"),
                    )
                }
                val argv = signIn?.first ?: linux.prootCommand(listOf("/bin/bash", "-l"))
                NativeTerminalProgram(
                    linux.prootLaunchPath,
                    app.filesDir.absolutePath,
                    argv,
                    (System.getenv() + linux.prootEnvironment()).map { (key, value) ->
                        "$key=$value"
                    },
                    owner,
                    linux.prootIsConfined,
                    signIn?.second,
                    { pid, exited -> BuiltinLinux.stopPidTree(pid, exited) },
                )
            }
        return manager.start(recipe, rows, cols)
    }

    suspend fun open(owner: String, program: List<String>): NativeTerminalManager.NativeSession =
        withContext(dispatchers.io) { openNative(24, 80, owner, program) }

    private fun inventory(session: NativeTerminalManager.NativeSession): LocalTerminal.Session =
        LocalTerminal.Session(
            session.id,
            session.pid,
            session.confined,
            object : LocalTerminal.Control {
                override val running: Boolean
                    get() = session.running

                override val exitCode: Int?
                    get() = session.exitCode

                override fun stop() {
                    session.stop()
                }
            },
            session.owner != null,
        )

    override fun start(
        rows: Int,
        cols: Int,
        signInProfile: String?,
        signInProgram: List<String>?,
    ): LocalTerminal.Session = inventory(openNative(rows, cols, signInProfile, signInProgram))

    override fun list(): List<LocalTerminal.Session> = manager.list().map(::inventory)

    override fun remove(id: Int) {
        manager.remove(id)
    }
}

/** The native sign-in PTY is opened only after the real foreground claim was admitted. */
@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<TerminalHost>())
class AgentTerminalHost(
    private val terminals: NativeTerminalManager,
    private val bridge: LinuxTerminalBridge,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    private val payloads: PhonePayloadGuard,
) : TerminalHost {
    override suspend fun open(target: TerminalTarget): Outcome<TerminalModel> {
        val command = (target as TerminalTarget.SignIn).command
        val owner =
            Regex("^/home/oc/[.]oc-profiles/([A-Za-z0-9_-]{1,80})$")
                .matchEntire(command.home)
                ?.groupValues
                ?.get(1) ?: return Failure.Refused(RefusalReason.StaleScope).failed()
        val expected = (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
        if (expected != owner || stores.profiles.profile(ProfileId(owner)) == null)
            return Failure.Refused(RefusalReason.ProfileDeleted).failed()
        // Synchronous reservation precedes any await; the exact native removal releases it.
        val reader =
            when (val claim = payloads.reserveNativeSignIn(ProfileId(owner))) {
                is Outcome.Failed -> return claim
                is Outcome.Ok -> claim.value
            }
        var transferred = false
        try {
            when (val admitted = reader.awaitReady()) {
                is Outcome.Failed -> return admitted
                is Outcome.Ok -> Unit
            }
            val opened =
                attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                    bridge.open(owner, listOf(command.program) + command.args)
                }
            val session =
                when (opened) {
                    is Outcome.Failed -> return opened
                    is Outcome.Ok -> opened.value
                }
            session.onRemoved(reader::release)
            transferred = true
            if (
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value != owner ||
                    stores.profiles.profile(ProfileId(owner)) == null
            ) {
                // Keep the exact handle available for retry if this drain could not be confirmed.
                withContext(dispatchers.io) {
                    attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                        terminals.remove(session.id)
                    }
                }
            }
            return object : TerminalModel {
                    override val view =
                        object : PlatformViewHandle {
                            override val native: Any = session.platformView
                        }
                    override val title = session.title
                    override val transcript = session.transcript
                    override val events: Flow<TerminalEvent> =
                        session.events.map { event ->
                            when (event) {
                                is NativeTerminalEvent.Exited -> TerminalEvent.Exited(event.code)
                                is NativeTerminalEvent.OpenUrl -> TerminalEvent.OpenUrl(event.url)
                            }
                        }

                    override suspend fun write(bytes: ByteArray) {
                        session.write(bytes)
                    }

                    override suspend fun resize(cols: Int, rows: Int) {
                        session.resize(cols, rows)
                    }

                    override suspend fun close(): Outcome<Unit> =
                        withContext(dispatchers.io) {
                            attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                                terminals.remove(session.id)
                            }
                        }
                }
                .ok()
        } finally {
            if (!transferred) reader.release()
        }
    }
}

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<SignInAgents>())
class NativeSignInAgents(
    private val app: Application,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
) : SignInAgents {
    override fun plan(agent: AgentRef): SignInPlan {
        val descriptor = AgentCatalog.builtIn.byId(AgentId(agent.id))
        // This entry serves the phone Claude account card; other agents retain their own setup
        // paths.
        return if (agent.id == "claude" && descriptor?.recipe != null)
            SignInPlan.Terminal(descriptor.name, true)
        else SignInPlan.NotAvailable(descriptor?.name.orEmpty())
    }

    override fun command(owner: ProfileId, agent: AgentRef): Outcome<SignInCommand> {
        if (
            plan(agent) !is SignInPlan.Terminal ||
                stores.profiles.profile(owner) == null ||
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value != owner.raw
        )
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val recipe = checkNotNull(AgentCatalog.builtIn.byId(AgentId(agent.id))?.recipe)
        return SignInCommand(
                recipe.executable,
                recipe.signInArgs,
                home = "/home/oc/.oc-profiles/${owner.raw}",
            )
            .ok()
    }

    override suspend fun confirmSignedIn(owner: ProfileId, agent: AgentRef): Boolean =
        withContext(dispatchers.io) {
            if (
                stores.profiles.profile(owner) == null ||
                    (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value !=
                        owner.raw
            )
                return@withContext false
            val descriptor =
                AgentCatalog.builtIn.byId(AgentId(agent.id)) ?: return@withContext false
            val script =
                AgentAuthScripts.script(descriptor, AuthScriptAction.Probe)
                    ?: return@withContext false
            val result =
                attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                    BuiltinLinux.get(app)
                        .agentAuthProbe(
                            mapOf(
                                "profileId" to owner.raw,
                                "agentId" to agent.id,
                                "action" to "probe",
                                "script" to AgentAuthScripts.forProfile(owner, script),
                                "timeoutSeconds" to if (agent.id == "claude") 65 else 10,
                            )
                        )
                }
            result is Outcome.Ok &&
                result.value["state"] == "signedIn" &&
                stores.profiles.profile(owner) != null &&
                (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value == owner.raw
        }

    override fun read(transcript: String): SignInFound {
        val output =
            SignInOutputReader.read(transcript.lineSequence().map { SignInRow(it) }.asIterable())
        return SignInFound(output.page?.url, output.code)
    }
}

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<SignInForeground>())
class NativeSignInForeground(
    private val hub: BackgroundHub,
    private val dispatchers: AppDispatchers,
) : SignInForeground {
    override fun reserve(): SignInForegroundLease {
        val ready = CompletableDeferred<ForegroundLease?>()
        var released = false
        var confirmed = false
        var held: ForegroundLease? = null
        hub.launchOnMain {
            val controller = hub.controller()
            if (released) ready.complete(null)
            else {
                held = controller.reserve()
                ready.complete(held)
            }
        }
        return object : SignInForegroundLease {
            override suspend fun admitted(): SignInAdmission {
                val actual = ready.await() ?: return SignInAdmission.Refused("cancelled")
                return when (val answer = actual.ready()) {
                    LeaseAdmission.Ready -> SignInAdmission.Ready
                    is LeaseAdmission.Refused ->
                        if (answer.loss.reason == ForegroundFailure.Permission)
                            SignInAdmission.NotificationsBlocked
                        else SignInAdmission.Refused(answer.loss.code ?: "service-refused")
                }
            }

            override val active: Boolean
                get() = !released && held?.active == true

            override val lost: Flow<String> = flow {
                ready.await()?.lost?.collect { emit(it.code ?: "service-gone") }
            }
            override val releaseConfirmed: Boolean
                get() = confirmed

            override suspend fun release() =
                withContext(dispatchers.main) {
                    released = true
                    val actual = ready.await()
                    confirmed = actual == null || actual.release() is Outcome.Ok
                }
        }
    }
}

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<SignInLinks>())
class NativeAgentSignInLinks(
    private val opener: ExternalLinkOpener,
    private val policy: ExternalLinkPolicy,
) : SignInLinks {
    override fun check(url: String): SignInLinkCheck =
        when (val decision = policy.checkSignInPage(url)) {
            is LinkDecision.Open -> SignInLinkCheck.Direct
            is LinkDecision.AskFirst -> SignInLinkCheck.Ask(decision.shownHost, decision.insecure)
            is LinkDecision.Refuse -> SignInLinkCheck.Refused
        }

    override suspend fun openConfirmed(url: String): Outcome<Unit> =
        when (val decision = policy.checkSignInPage(url)) {
            is LinkDecision.Open -> opener.open(decision.link)
            is LinkDecision.AskFirst -> opener.open(decision.link)
            is LinkDecision.Refuse -> Failure.Refused(RefusalReason.InvalidAddress).failed()
        }

    override suspend fun open(url: String): Outcome<Unit> =
        when (val decision = policy.checkSignInPage(url)) {
            is LinkDecision.Open -> opener.open(decision.link)
            is LinkDecision.AskFirst,
            is LinkDecision.Refuse -> Failure.Refused(RefusalReason.InvalidAddress).failed()
        }
}

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<SignInClipboard>())
class AgentSignInClipboard(private val app: Application) : SignInClipboard {
    override fun copySensitive(text: String) {
        val clip = ClipData.newPlainText("", text)
        clip.description.extras =
            PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        app.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}

@SingleIn(AppScope::class)
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class AgentTerminalProfileCloser(
    private val terminals: NativeTerminalManager,
    bridge: LinuxTerminalBridge,
    private val cleanup: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : ProfileCloser {
    init {
        bridge.install()
    }

    override val owner = ":android:terminal"

    override fun stopAdmission(profile: ProfileId) {
        terminals.stopAdmission(profile.raw)
        // Start draining during the all-owners admission fence so an earlier payload closer
        // can observe its reader being released instead of waiting for this closer's turn.
        cleanup.launch(dispatchers.io) {
            attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                terminals.closeProfile(profile.raw)
            }
        }
    }

    override suspend fun drain(profile: ProfileId): Outcome<Unit> =
        attempt({ Failure.Runtime(RuntimeProblem.Generic) }) { terminals.closeProfile(profile.raw) }

    override fun reopen(profile: ProfileId) {
        terminals.reopen(profile.raw)
    }
}
