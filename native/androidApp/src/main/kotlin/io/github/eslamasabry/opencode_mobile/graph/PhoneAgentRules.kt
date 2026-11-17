package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.catalog.AgentSignInMethod
import io.github.eslamasabry.oc.engine.agents.gate.PhoneGate
import io.github.eslamasabry.oc.engine.agents.host.AuthProbeError
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import io.github.eslamasabry.oc.engine.agents.probe.AgentAuthScripts
import io.github.eslamasabry.oc.engine.agents.readiness.AgentHiddenReason
import io.github.eslamasabry.oc.engine.agents.readiness.AgentReadiness
import io.github.eslamasabry.oc.engine.agents.readiness.AgentSignInPhase
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentRuntime
import io.github.eslamasabry.oc.engine.agents.signin.SignInCommandPolicy
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallPhase
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentInstallation
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentRow
import io.github.eslamasabry.oc.feature.agents.hub.PhoneAgentStatus
import io.github.eslamasabry.oc.feature.agents.hub.installed
import kotlinx.serialization.json.JsonObject
import io.github.eslamasabry.oc.engine.agents.readiness.PhoneAgentStatus as ReadinessStatus

/**
 * The Agents page's rules for what is installed and what its row says, taken from the Flutter app
 * (`phone_agents_host.dart` runtime facts, `buildAgentRow`). Pure: the phone is read elsewhere.
 */
internal object PhoneAgentRules {
    /**
     * Flutter: `installed: version['installed'] == true || version['foundVersion'] is String`. Any copy that runs counts,
     * so a version other than the pin is an update on offer, not an absence.
     */
    fun runs(version: Map<String, Any?>?): Boolean =
        version?.get("installed") == true || version?.get("foundVersion") is String

    /**
     * Whether the agent is on this phone. [payload] is the shared inventory's answer (null when storage could not be
     * read safely); a copy that runs is installed whatever the inventory says, and the inventory only speaks for agents
     * it has a folder for.
     */
    fun present(payload: Boolean?, version: Map<String, Any?>?): Boolean? =
        if (runs(version)) true else payload

    /** Claude Code is the agent the inventory has no folder for, so only its version answers. */
    fun needsVersionProbe(agent: AgentId, payload: Boolean?): Boolean =
        agent == AgentId.CLAUDE || payload == true

    /**
     * The processor the phone runs, from the FIRST (primary) ABI as Flutter did. An x86_64 emulator also lists
     * arm64-v8a (translation); taking that one would never match the proof Flutter stored for x64.
     */
    fun architectureOf(abis: List<String>): AgentArchitecture? =
        when (abis.firstOrNull()) {
            "arm64-v8a" -> AgentArchitecture.Arm64
            "x86_64" -> AgentArchitecture.X64
            else -> null
        }

    /** The copy that runs, as a version string. */
    fun foundVersion(version: Map<String, Any?>?): String? = version?.get("foundVersion") as? String

    /**
     * A sign-in probe starts a process in the phone's Linux, and Android stops an app's child processes past a limit,
     * so none starts while an install runs (the install is the thing those processes could kill).
     */
    fun mayProbeSignIn(installRunning: Boolean): Boolean = !installRunning

    /**
     * Whether a read may reuse the sign-in answer the agent gave [ageMillis] ago instead of starting its check again
     * (each one starts the agent's own program; Claude Code's may take a minute on a cold start). Only "signed in",
     * and only for [SIGN_IN_REUSE_MS]: a person who signs in on the sign-in page comes back to a tile that still said
     * "Sign in", and that answer is always checked again. A step on the agent forgets its answer.
     */
    fun reuseSignIn(answer: AuthProjection?, ageMillis: Long?): Boolean =
        answer?.state == AuthState.SignedIn && ageMillis != null && ageMillis in 0 until SIGN_IN_REUSE_MS

    const val SIGN_IN_REUSE_MS = 60_000L

    /**
     * This phone's own install of the pinned version is verified by its install step, so it needs no earlier proof; a
     * version proved by the Flutter app counts while its helper is a tested one; and so does a version this phone's
     * registry ([record]) saw able to start a conversation, after an app update moved the agent's pin past it (the
     * automatic update brings the pinned copy; until then the copy that ran yesterday still runs, as the engine's
     * `GateInspection.behindPin` says). Before, every such agent read "Phone check needed" from the first frame after
     * the update and was absent from New conversation until the update or a check ended.
     */
    // The facts one qualification weighs: the agent, the phone, the gate read, what was found, the pins, the record.
    @Suppress("LongParameterList")
    fun qualified(
        agent: AgentDescriptor,
        architecture: AgentArchitecture,
        gate: JsonObject?,
        found: String?,
        pins: AgentPins = AgentPins.builtIn,
        record: PhoneAgentFacts.Fact? = null,
    ): Boolean =
        found != null &&
            (found == agent.recipe?.version ||
                PhoneGate.qualifies(gate, agent, architecture, pins, found) ||
                PhoneAgentFacts.provenRuns(record, found, pins))

    /**
     * A row past the phone check: it can start a conversation, or would once signed in, resumed or past its limit.
     * The version it runs is then proven ([PhoneAgentFacts.Fact.proven]).
     */
    fun pastCheck(status: PhoneAgentStatus): Boolean =
        status == PhoneAgentStatus.Ready ||
            status == PhoneAgentStatus.SetUpAtFirstUse ||
            status == PhoneAgentStatus.SignInNeeded ||
            status == PhoneAgentStatus.Stopped ||
            status == PhoneAgentStatus.Limit

    /**
     * Whether saved server [id] is the helper's own "Phone agents" server, whose helper the app starts when the server
     * is needed: its id is `<owner>-agents` and the owner it was saved for (pref `oc.phoneAgentOwner.<id>`) is that
     * same owner. Any other server is not the helper's.
     */
    fun isHelperProfile(id: String, savedOwner: String?): Boolean =
        savedOwner != null && Regex("[A-Za-z0-9_-]{1,80}").matches(savedOwner) && id == "$savedOwner-agents"

    /**
     * Whether saved server [id] is a remote Paseo server rather than this phone's own helper profile (`<owner>-agents`),
     * which `PhoneUsageReader` (`PHONE_ID`) already represents in Usage.
     */
    fun isRemotePaseoProfile(id: String, backend: BackendKind, savedOwner: String?): Boolean =
        backend == BackendKind.Paseo && !isHelperProfile(id, savedOwner)

    /**
     * The saved "This phone" server that helper [id] belongs to, or null when [id] is not the helper or no such phone is
     * saved. As in Flutter, the helper is part of the phone's profile (`<phone>.agents`, never a server of its own):
     * anything that pointed at the helper as a server (the home's server, a deletion) means the phone. The phone is
     * the app's own OpenCode ([builtinOwner], pref `oc.builtinServerOwner`), else the owner the helper was saved for.
     */
    fun phoneOf(id: String, savedOwner: String?, builtinOwner: String?, saved: Set<String>): String? =
        if (!isHelperProfile(id, savedOwner)) null
        else builtinOwner?.takeIf { it != id && it in saved } ?: savedOwner?.takeIf { it in saved }

    /**
     * Whose phone a payload lease on the agent helper is taken for: [phone] is the app's own OpenCode profile the lease
     * is checked against (pref `oc.builtinServerOwner`), [helper] the owner the helper runs under, whose secret
     * (`oc.agentHostSecret.<helper>`) and status the lease reads.
     */
    data class PayloadOwner(val phone: String, val helper: String)

    /**
     * The payload owner of a connection to the helper's port by [alias]: the transient `<phone>.agents` alias by its
     * native ownership proof ([transientOwner]); the saved helper `<owner>-agents` (the home's This phone agent rows,
     * "one This phone") by its phone ([phoneOf]) and the owner it was saved for ([savedOwner], whose secret
     * AppLocalAgentHostStarter writes and whose password the saved profile carries); any other saved profile as
     * itself. Null: no owner, the lease is refused.
     */
    fun payloadOwner(
        alias: String,
        transientOwner: String?,
        savedOwner: String?,
        builtinOwner: String?,
        saved: Set<String>,
    ): PayloadOwner? =
        when {
            transientOwner != null -> PayloadOwner(transientOwner, transientOwner)
            savedOwner != null && isHelperProfile(alias, savedOwner) ->
                phoneOf(alias, savedOwner, builtinOwner, saved)?.let { PayloadOwner(it, savedOwner) }
            alias in saved -> PayloadOwner(alias, alias)
            else -> null
        }

    /** The owner part of the lease's check: an owner whose phone is still saved and still the app's own OpenCode. */
    fun payloadOwnerHolds(owner: PayloadOwner?, builtinOwner: String?, saved: Set<String>): Boolean =
        owner != null && owner.phone in saved && builtinOwner == owner.phone

    /** The secret part: the helper's stored secret is the one the connection presents. */
    fun payloadSecretMatches(stored: String?, presented: String?): Boolean = !stored.isNullOrEmpty() && stored == presented

    /**
     * Whether a source's list counts as reaching every project. F: phone_agents.dart:1207-1210: the phone agents' list
     * is read folder by folder and never makes the whole list look single-project; OpenCode's own answer
     * ([globalSessionSearch]) decides.
     */
    fun listsAcrossProjects(globalSessionSearch: Boolean, helper: Boolean): Boolean = globalSessionSearch || helper

    /**
     * The server the home opens on at start, from the saved choice [chosen]. A saved choice of the phone agents'
     * helper (earlier builds listed it as a server) means the phone it belongs to ([phoneOf]); with no such phone,
     * the first saved server that is not a helper; with none, null (nothing to open). Any other choice stays.
     */
    fun homeServer(
        chosen: String?,
        saved: List<String>,
        isHelper: (String) -> Boolean,
        phoneOf: (String) -> String?,
    ): String? =
        if (chosen != null && !isHelper(chosen)) chosen
        else chosen?.let(phoneOf) ?: saved.firstOrNull { !isHelper(it) }

    /**
     * The saved helpers that go when [removed] goes: those whose phone ([phoneOf]) it is. The helper is part of This
     * phone and has no row of its own to be removed from (Flutter: `<phone>.agents` dies with the phone's profile).
     */
    fun removedWith(removed: String, saved: List<String>, phoneOf: (String) -> String?): List<String> =
        saved.filter { it != removed && phoneOf(it) == removed }

    /**
     * The name each saved helper must carry so that every place that names a connection (a chat's header, an alert,
     * All conversations, a widget) calls its conversations This phone's: its phone's own name. [saved] is (id, name);
     * the result lists only the helpers whose name differs, as (id, phone's name). A helper with no phone keeps its
     * name.
     */
    fun helperNames(saved: List<Pair<String, String>>, phoneOf: (String) -> String?): List<Pair<String, String>> {
        val names = saved.toMap()
        return saved.mapNotNull { (id, name) ->
            val phone = phoneOf(id)?.let(names::get)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (phone == name) null else id to phone
        }
    }

    /**
     * A kept "interrupted" or "failed" install result for an agent whose installed-check now passes is out of date (an
     * earlier run was cut short but the agent is there): it is dropped, not shown. A running or finished-well
     * install, and a result for an agent still missing, stay.
     */
    fun staleResult(installation: PhoneAgentInstallation?, agents: List<PhoneAgentRow>): Boolean {
        val ended =
            installation?.phase == PhoneAgentInstallPhase.Failed ||
                installation?.phase == PhoneAgentInstallPhase.Interrupted
        return ended && agents.any { it.id == installation?.id && it.status.installed }
    }

    /** What a row says, from Flutter's `buildAgentRow` over the facts read. */
    @Suppress("LongParameterList") // the facts one row reads; most have defaults and every caller names them
    fun status(
        agent: AgentDescriptor,
        architecture: AgentArchitecture,
        installed: Boolean,
        payloadPresent: Boolean,
        qualified: Boolean,
        signIn: AgentSignInPhase?,
        /** Whether the agent helper runs now; null when it was not read (then nothing says it stopped). */
        helperRunning: Boolean? = null,
        /** Its set-up has to come first ([setUpFirst]): it reads set up at first use even while the helper is down. */
        setUpFirst: Boolean = false,
    ): PhoneAgentStatus {
        val row =
            AgentReadiness.row(
                agent,
                architecture,
                ServerCapabilities.Unknown,
                PhoneAgentRuntime(
                    agentId = agent.id,
                    installed = installed,
                    payloadPresent = payloadPresent,
                    // The page reads status without the helper; the helper has its own line on the page.
                    hostAvailable = true,
                    architectureQualified = qualified,
                    signInPhase = signIn,
                    // Flutter `inspect`: an agent the phone already proved, whose helper is not running.
                    stoppedInBackground = qualified && helperRunning == false,
                ),
            )
        return when (row.status) {
            ReadinessStatus.Ready -> PhoneAgentStatus.Ready
            // The helper is not running yet (after every app start until something starts it), but the set-up runs in
            // its own terminal without it: an agent never seen set up says Set up, not Resume (which would then offer
            // it as a choice that fails its first answer).
            ReadinessStatus.StoppedInBackground ->
                if (setUpFirst) PhoneAgentStatus.SetUpAtFirstUse else PhoneAgentStatus.Stopped
            ReadinessStatus.NeedsInstall -> PhoneAgentStatus.NotInstalled
            ReadinessStatus.NeedsQualification -> PhoneAgentStatus.PhoneCheck
            ReadinessStatus.SignedOut -> PhoneAgentStatus.SignInNeeded
            ReadinessStatus.LimitReached -> PhoneAgentStatus.Limit
            ReadinessStatus.Unavailable -> unavailableStatus(agent, row.hiddenReason)
        }
    }

    private fun unavailableStatus(agent: AgentDescriptor, reason: AgentHiddenReason?): PhoneAgentStatus =
        when (reason) {
            AgentHiddenReason.RuntimeUnknown -> PhoneAgentStatus.Checking
            AgentHiddenReason.UnsupportedArchitecture,
            AgentHiddenReason.UnverifiedDownload -> PhoneAgentStatus.NotInstalled
            // The sign-in check did not answer (Flutter `buildAgentRow`: "Sign-in could not be checked. Try
            // signing in again." with Sign in). Before, this read "Not available on this phone yet" with no
            // reason and no act.
            // An agent whose sign-in no check can read (Qwen Code, Goose, omp) is usable once installed: set
            // up at first use, its truth learnt from its own answer (never "Ready", never signed in or out).
            AgentHiddenReason.SignedOut ->
                if (signInUnchecked(agent)) PhoneAgentStatus.SetUpAtFirstUse
                else PhoneAgentStatus.SignInNeeded
            else -> PhoneAgentStatus.Unavailable
        }

    /**
     * An agent that needs a sign-in the app's probe cannot read (Qwen Code, Goose, omp, an installed Gemini CLI): its
     * check answers "unsupported" every time, so it is set up at first use (engine `AgentFirstUse`).
     */
    fun signInUnchecked(agent: AgentDescriptor): Boolean =
        agent.signInMethod != AgentSignInMethod.None && !AgentAuthScripts.checksSignIn(agent.id)

    /**
     * Whether [agent]'s set-up comes before its first conversation ([PhoneAgentRow.needsSetUp]): its sign-in no check
     * can read, the app has not [seen] it set up (`PhoneAgentFacts.Fact.setUp`), and the app can open its set-up (its
     * own set-up command passes the sign-in terminal's policy, as `NativePhoneSignIn`'s plan asks). An agent with no
     * set-up the app could open stays startable: its first answer is then the only way to learn.
     */
    fun setUpFirst(agent: AgentDescriptor, seen: Boolean): Boolean {
        val recipe = agent.recipe ?: return false
        return signInUnchecked(agent) && !seen && SignInCommandPolicy.allows(recipe.executable, recipe.signInArgs)
    }

    /**
     * The sign-in check of an agent the probe can read did not run to an answer this time (timed out, the phone's
     * Linux or helper did not answer, an unreadable reply) and no earlier answer stands in. Its row is
     * [PhoneAgentStatus.SignInNeeded] for Flutter's Sign in act, but nothing is known about the account: the card says
     * the check could not be done (Flutter "Sign-in could not be checked. Try signing in again."), never "Sign in
     * needed", and the home names no signed-out agent for it. A check that ran and found no (or an expired) sign-in
     * is [AgentSignInPhase.SignedOut], not this.
     */
    fun signInCheckFailed(agent: AgentDescriptor, signIn: AgentSignInPhase?): Boolean =
        signIn == AgentSignInPhase.Failed && agent.signInMethod != AgentSignInMethod.None && !signInUnchecked(agent)

    /** A check that gave no answer: it timed out, the phone's Linux did not answer, or Claude Code was busy. */
    val NO_ANSWER: Set<AuthProbeError> =
        setOf(AuthProbeError.HostUnavailable, AuthProbeError.TimedOut, AuthProbeError.AgentBusy)

    /**
     * signin-truth-2: the answer that stands when the check gave [fresh] and it is no answer ([NO_ANSWER]): this run's
     * [previous] answer, else the registry's [kept] one (the last answer before an app start); null when [fresh] is an
     * answer, or nothing is known (then the check's failure is kept and the sign-in reads "not confirmed"). Before,
     * after every app start a Claude Code check that could not run read "Sign-in not confirmed" while the registry
     * knew the last answer.
     */
    fun standIn(fresh: AuthProjection, previous: AuthProjection?, kept: AuthProjection?): AuthProjection? {
        if (fresh.error !in NO_ANSWER) return null
        return previous?.takeIf { it.state != AuthState.Error } ?: kept?.takeIf { it.state != AuthState.Error }
    }

    /** The sign-in phase of the probe's [answer]: an expired sign-in is a check that ran and found none to use. */
    fun phaseOf(answer: AuthProjection): AgentSignInPhase =
        when (answer.state) {
            AuthState.SignedOut -> AgentSignInPhase.SignedOut
            AuthState.SignedIn -> AgentSignInPhase.SignedIn
            AuthState.Error ->
                if (answer.error == AuthProbeError.SignInExpired) AgentSignInPhase.SignedOut
                else AgentSignInPhase.Failed
        }

    /**
     * The install steps whose own check already passes. Flutter ran each component's check before its install; the
     * Node download and Paseo `npm ci` are the long ones, so a step that is already in place is not run again.
     * [check] runs one step's check script and answers its exit code (null when it could not run).
     */
    fun satisfiedSteps(steps: Map<String, String>, check: (String) -> Int?): Set<String> =
        steps.filterValues { script -> check(script) == 0 }.keys
}
