package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.engine.agents.host.AuthProjection
import io.github.eslamasabry.oc.engine.agents.host.AuthState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the last read of the phone's agents found, kept across app starts (`oc.phoneAgentFacts.<owner>`) so the Agents
 * page's tiles show their last known state at once while the slow checks run again (a sign-in check starts the agent's
 * own program in the phone's Linux: up to 65 s for Claude Code). Only what a tile needs: whether a copy runs, its
 * version and whether the agent said it was signed in. Never an account name, never a credential. A kept fact is never
 * taken as fresh: every read checks again, and a person's step on an agent forgets that agent's facts.
 */
internal object PhoneAgentFacts {
    /** [installed] null: the version was not read. [signedIn] null: the sign-in was not read (or could not be). */
    data class Fact(val installed: Boolean?, val found: String?, val signedIn: Boolean?)

    fun key(owner: String): String = "oc.phoneAgentFacts.$owner"

    /** The version answer a tile is built from (`PhoneAgentRules.runs` and `foundVersion` read these two). */
    fun version(fact: Fact?): Map<String, Any?>? =
        fact?.installed?.let { mapOf("installed" to it, "foundVersion" to fact.found) }

    fun signIn(fact: Fact?): AuthProjection? =
        fact?.signedIn?.let { AuthProjection(if (it) AuthState.SignedIn else AuthState.SignedOut) }

    /** A fact from a version answer and a sign-in answer; an error or a missing answer is not kept. */
    fun of(version: Map<String, Any?>?, signIn: AuthProjection?): Fact =
        Fact(
            installed = version?.let { it["installed"] == true },
            found = version?.get("foundVersion") as? String,
            signedIn =
                when (signIn?.state) {
                    AuthState.SignedIn -> true
                    AuthState.SignedOut -> false
                    AuthState.Error,
                    null -> null
                },
        )

    /** [kept] with this read's [fresh] answers; an agent the read could not answer for keeps its kept fact. */
    fun merge(kept: Map<String, Fact>, fresh: Map<String, Fact>): Map<String, Fact> =
        kept + fresh.filterValues { it.installed != null || it.signedIn != null }

    /** A step on [agent] drops what it may have changed ([version], [signIn]); the other agents keep theirs. */
    fun forget(kept: Map<String, Fact>, agent: String, version: Boolean, signIn: Boolean): Map<String, Fact> {
        val fact = kept[agent] ?: return kept
        return kept +
            (agent to
                fact.copy(
                    installed = fact.installed.takeUnless { version },
                    found = fact.found.takeUnless { version },
                    signedIn = fact.signedIn.takeUnless { signIn },
                ))
    }

    fun encode(facts: Map<String, Fact>): String =
        JsonObject(
                facts
                    .filterValues { it.installed != null || it.signedIn != null }
                    .mapValues { (_, fact) ->
                        JsonObject(
                            buildMap {
                                fact.installed?.let { put("installed", JsonPrimitive(it)) }
                                fact.found?.let { put("found", JsonPrimitive(it)) }
                                fact.signedIn?.let { put("signedIn", JsonPrimitive(it)) }
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
                    Fact(
                        installed = fields["installed"]?.jsonPrimitive?.booleanOrNull,
                        found = fields["found"]?.jsonPrimitive?.contentOrNull?.takeIf { VERSION.matches(it) },
                        signedIn = fields["signedIn"]?.jsonPrimitive?.booleanOrNull,
                    )
                }
        } catch (_: Exception) {
            emptyMap()
        }

    private val VERSION = Regex("^[0-9][A-Za-z0-9.+_-]{0,63}$")
}
