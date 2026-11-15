package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * This phone's agent registry (`oc.phoneAgentFacts.<owner>`): what the last reads of the phone's agents found, kept
 * across app starts, app updates and phone restarts, so every place that offers or names an agent (the Agents page,
 * New conversation, the home, the widget: all read [io.github.eslamasabry.oc.feature.agents.hub.PhoneAgents.state],
 * which is built from this record first) shows the last known state at once while the slow checks run again (a sign-in
 * check starts the agent's own program in the phone's Linux: up to 65 s for Claude Code).
 *
 * Per agent: whether the pinned copy is installed and the version that runs, whether the agent said it was signed in
 * and when that was read, and the version last proven able to start a conversation with what it was checked against
 * (the helper's pin and the app build), and whether the app has seen it set up ([Fact.setUp]). Never an account name,
 * never a credential. A kept answer is never taken as fresh: every read checks again, and a person's step on an agent
 * forgets that agent's answers. The proof is the one
 * fact a check does not re-derive: an app update that moves an agent's pin makes the copy that runs "behind", and that
 * copy stays usable while the automatic update brings the pinned one ([provenRuns]), instead of the agent dropping
 * out of every list until then.
 *
 * Only AppPhoneAgents writes it, through one path (`keepFacts`): its reads, a person's steps on an agent, and
 * `noteSetUp` (the set-up terminal ended, or a conversation got an answer).
 */
internal object PhoneAgentFacts {
    /**
     * [installed] null: the version was not read. [signedIn] null: the sign-in was not read (or could not be).
     * [signedInAt]: wall-clock millis when [signedIn] was read. [proven]: the version last seen past every check up to
     * the sign-in (Ready, or would be once signed in), [provenHelper] the helper pin and [provenBuild] the app build it
     * was checked under. [setUp]: this app has seen the agent set up (its set-up terminal ended, or a conversation with
     * it got an answer); an agent whose sign-in no check can read leads with its set-up until then. Unlike the proof it
     * is about the agent's own settings, not a version: an update keeps it, a removal, a sign-out or a reset of the
     * saved sign-ins drops it.
     */
    data class Fact(
        val installed: Boolean?,
        val found: String?,
        val signedIn: Boolean?,
        val signedInAt: Long? = null,
        val proven: String? = null,
        val provenHelper: String? = null,
        val provenBuild: Long? = null,
        val setUp: Boolean = false,
    ) {
        /** Something worth keeping. */
        val known: Boolean
            get() = installed != null || signedIn != null || proven != null || setUp
    }

    fun key(owner: String): String = AgentKeys.phoneAgentFacts(ProfileId(owner))

    /** The version answer a tile is built from (`PhoneAgentRules.runs` and `foundVersion` read these two). */
    fun version(fact: Fact?): Map<String, Any?>? =
        fact?.installed?.let { mapOf("installed" to it, "foundVersion" to fact.found) }

    fun signIn(fact: Fact?): AuthProjection? =
        fact?.signedIn?.let { AuthProjection(if (it) AuthState.SignedIn else AuthState.SignedOut) }

    /**
     * A fact from a version answer and a sign-in answer; an error or a missing answer is not kept. A pinned copy that
     * runs (`installed`) is proven by its install's own check, under [helper] and [build].
     */
    fun of(
        version: Map<String, Any?>?,
        signIn: AuthProjection?,
        helper: String? = null,
        build: Long? = null,
        signedInAt: Long? = null,
    ): Fact {
        val installed = version?.let { it["installed"] == true }
        val found = version?.get("foundVersion") as? String
        val signedIn =
            when (signIn?.state) {
                AuthState.SignedIn -> true
                AuthState.SignedOut -> false
                AuthState.Error,
                null -> null
            }
        val pinned = found?.takeIf { installed == true }
        return Fact(
            installed = installed,
            found = found,
            signedIn = signedIn,
            signedInAt = signedInAt?.takeIf { signedIn != null },
            proven = pinned,
            provenHelper = helper?.takeIf { pinned != null },
            provenBuild = build?.takeIf { pinned != null },
        )
    }

    /** [kept] with [agent] seen set up ([Fact.setUp]); the rest of what is known about it stays. */
    fun seenSetUp(kept: Map<String, Fact>, agent: String): Map<String, Fact> {
        val fact = kept[agent] ?: Fact(installed = null, found = null, signedIn = null)
        return if (fact.setUp) kept else kept + (agent to fact.copy(setUp = true))
    }

    /** The agents of [kept] the app has seen set up. */
    fun setUp(kept: Map<String, Fact>): Set<String> = kept.filterValues { it.setUp }.keys

    /**
     * [kept] with the set-up list earlier builds kept on its own (`oc.agentSetUp.<owner>`, the agent ids whose
     * set-up terminal ended) read in as [Fact.setUp], so a set-up done before this record held it is not asked again.
     * That list is removed the next time the record is written.
     */
    fun withEarlierSetUp(kept: Map<String, Fact>, earlier: List<String>?): Map<String, Fact> =
        earlier.orEmpty().filter { ID.matches(it) }.fold(kept, ::seenSetUp)

    /** [fact] with [version] proven able to start a conversation under [helper] and [build]. */
    fun proven(fact: Fact, version: String, helper: String, build: Long): Fact =
        fact.copy(proven = version, provenHelper = helper, provenBuild = build)

    /**
     * Whether the copy that runs ([found]) is one this phone proved able to start a conversation, under a helper this
     * app still trusts with it (the same one, or both tested: evidence carries across tested helpers, as the phone
     * check's own proof does). A record kept before the proof was written down counts: "the pinned copy is installed"
     * there means the build that wrote it had checked that very copy at its install.
     */
    fun provenRuns(fact: Fact?, found: String?, pins: AgentPins = AgentPins.builtIn): Boolean {
        if (fact == null) return false
        val proven = fact.proven ?: fact.found?.takeIf { fact.installed == true } ?: return false
        if (found == null || proven != found) return false
        val helper = fact.provenHelper ?: return true
        val pinned = pins.paseo.version
        return helper == pinned || (helper in pins.paseoTestedVersions && pinned in pins.paseoTestedVersions)
    }

    /**
     * [kept] with this read's [fresh] answers; an agent the read could not answer for keeps its kept fact. A fresh
     * answer keeps the kept proof when it brings none (a proof is about a version, not about this read), including
     * the proof an older record carries only as "the pinned copy was installed"; and keeps when the sign-in was read
     * while the answer is unchanged. A read never brings [Fact.setUp]: the kept one stays.
     */
    fun merge(kept: Map<String, Fact>, fresh: Map<String, Fact>): Map<String, Fact> =
        kept +
            fresh
                .filterValues { it.known }
                .mapValues { (agent, now) ->
                    val before = kept[agent] ?: return@mapValues now
                    val proof =
                        if (now.proven != null) now
                        else if (before.proven != null) before
                        else before.copy(proven = before.found?.takeIf { before.installed == true })
                    now.copy(
                        signedInAt =
                            now.signedInAt
                                ?: before.signedInAt?.takeIf {
                                    now.signedIn != null && now.signedIn == before.signedIn
                                },
                        proven = proof.proven,
                        provenHelper = proof.provenHelper,
                        provenBuild = proof.provenBuild,
                        setUp = now.setUp || before.setUp,
                    )
                }

    /**
     * A step on [agent] drops what it may have changed ([version], [signIn]); the other agents keep theirs. [proof]
     * also drops the agent's proof (a removal, or a phone check that starts again from nothing); [setUp] drops that the
     * app saw it set up (a removal, a sign-out). An install or an update keeps both.
     */
    fun forget(
        kept: Map<String, Fact>,
        agent: String,
        version: Boolean,
        signIn: Boolean,
        proof: Boolean = false,
        setUp: Boolean = false,
    ): Map<String, Fact> {
        val fact = kept[agent] ?: return kept
        return kept +
            (agent to
                fact.copy(
                    installed = fact.installed.takeUnless { version },
                    found = fact.found.takeUnless { version },
                    signedIn = fact.signedIn.takeUnless { signIn },
                    signedInAt = fact.signedInAt.takeUnless { signIn },
                    proven = fact.proven.takeUnless { proof },
                    provenHelper = fact.provenHelper.takeUnless { proof },
                    provenBuild = fact.provenBuild.takeUnless { proof },
                    setUp = fact.setUp && !setUp,
                ))
    }

    /**
     * A reset of the saved sign-ins: every kept "signed in" or "signed out", and that any agent was seen set up (the
     * reset deletes the agents' own settings with their sign-ins). Versions and proofs stay.
     */
    fun forgetSignIns(kept: Map<String, Fact>): Map<String, Fact> =
        kept.mapValues { (_, fact) -> fact.copy(signedIn = null, signedInAt = null, setUp = false) }

    fun encode(facts: Map<String, Fact>): String =
        JsonObject(
                facts
                    .filterValues { it.known }
                    .mapValues { (_, fact) ->
                        JsonObject(
                            buildMap {
                                fact.installed?.let { put("installed", JsonPrimitive(it)) }
                                fact.found?.let { put("found", JsonPrimitive(it)) }
                                fact.signedIn?.let { put("signedIn", JsonPrimitive(it)) }
                                fact.signedInAt?.let { put("signedInAt", JsonPrimitive(it)) }
                                fact.proven?.let { put("proven", JsonPrimitive(it)) }
                                fact.provenHelper?.let { put("provenHelper", JsonPrimitive(it)) }
                                fact.provenBuild?.let { put("provenBuild", JsonPrimitive(it)) }
                                if (fact.setUp) put("setUp", JsonPrimitive(true))
                            }
                        )
                    }
            )
            .toString()

    /** Unreadable or foreign text is no facts. */
    fun decode(text: String?): Map<String, Fact> =
        try {
            if (text.isNullOrEmpty()) emptyMap()
            else
                Json.parseToJsonElement(text).jsonObject.mapValues { (_, value) ->
                    val fields = value.jsonObject
                    fun version(name: String) =
                        fields[name]?.jsonPrimitive?.contentOrNull?.takeIf { VERSION.matches(it) }
                    Fact(
                        installed = fields["installed"]?.jsonPrimitive?.booleanOrNull,
                        found = version("found"),
                        signedIn = fields["signedIn"]?.jsonPrimitive?.booleanOrNull,
                        signedInAt = fields["signedInAt"]?.jsonPrimitive?.longOrNull,
                        proven = version("proven"),
                        provenHelper = version("provenHelper"),
                        provenBuild = fields["provenBuild"]?.jsonPrimitive?.longOrNull,
                        setUp = fields["setUp"]?.jsonPrimitive?.booleanOrNull == true,
                    )
                }
        } catch (_: Exception) {
            emptyMap()
        }

    private val VERSION = Regex("^[0-9][A-Za-z0-9.+_-]{0,63}$")

    /** An agent id as the catalog writes it. */
    private val ID = Regex("^[a-z0-9-]{1,40}$")
}
