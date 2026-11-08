package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.math.roundToInt
import kotlin.time.Instant

// Native twin of Flutter lib/quota/antigravity_quota_client.dart (PR #125) and its call site in
// lib/state/connection/phone_agents_usage.dart (_paReadUsage, _readAntigravityUsage); the signed-in account from
// PR #136 (`ACCOUNT_EMAIL:` line, parseProbeAccount).

/** One shell run inside the phone's built-in Linux: exit code and output. */
public data class LinuxRunResult(val exitCode: Int, val output: String)

/** Runs one shell script inside the built-in Linux (BuiltinLinux.run on Android). */
public fun interface AntigravityLinuxRunner {
    public suspend fun run(script: String, timeoutMs: Long): LinuxRunResult
}

/**
 * Reads Google Antigravity plan usage by asking the Antigravity CLI itself (`agy -p /usage --output-format json`)
 * inside the built-in Linux, the same command a person runs in a terminal. The app never opens the CLI's credential
 * file and never calls Google on the CLI's behalf: the CLI stays the only client that uses its sign-in. The probe run
 * inside the Linux prints at most one `ACCOUNT_EMAIL:` line (an email-shaped value, never the file), which
 * [parseProbeAccount] checks again before it names the plan's account.
 */
public class AntigravityQuotaClient(
    /** Null when the built-in Linux is not available: then there is nothing to read. */
    private val runner: AntigravityLinuxRunner?,
    private val clock: () -> Instant,
) {
    /** The last good readings (shown while a new read is under way). */
    public var cachedReadings: List<ProviderUsageReading>? = null
        private set

    /**
     * The CLI's readings, or an empty list when the CLI is missing, not signed in, too slow, or answers something
     * unreadable. Never invents numbers: an empty list means "this provider reports no limits now".
     */
    public suspend fun fetchAllUsage(profileId: String? = null, now: Instant? = null): List<ProviderUsageReading> {
        val run = runner ?: return emptyList()
        val result = attempt({ Failure.Device() }) { run.run(cliScript(profileId), TIMEOUT_MS) }
        val output = (result as? Outcome.Ok)?.value?.takeIf { it.exitCode == 0 }?.output
        val readings =
            output
                ?.let(::jsonObjectIn)
                ?.let { parseCliJson(it, now ?: clock(), accountEmail = parseProbeAccount(output)) }
                .orEmpty()
        if (readings.isNotEmpty()) cachedReadings = readings
        return readings
    }

    public companion object {
        public const val TIMEOUT_MS: Long = 20_000
        private val SAFE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
        private const val FIVE_HOURS = 18_000L
        private const val WEEK = 604_800L
        private const val PERCENT = 100.0
        private const val MAX_TIME_TEXT = 64
        private const val GROUP_NAME_LENGTH = 48
        private const val LABEL_LENGTH = 24
        private const val PROVIDER_ID_LENGTH = 32
        private const val SHARED_CLI = "/home/oc/.local/bin/agy"
        private const val PROFILES = "/home/oc/.oc-profiles"
        private const val TOKEN = ".gemini/antigravity-cli/antigravity-oauth-token"
        private const val DEFAULT_PLAN = "Google AI Pro"

        /** The longest account email the app accepts from the probe. */
        public const val MAX_ACCOUNT_LENGTH: Int = 64
        private val ACCOUNT_LINE = Regex("^ACCOUNT_EMAIL:(.*)$", RegexOption.MULTILINE)
        private val EMAIL_SHAPE = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

        /**
         * The account email the probe printed as `ACCOUNT_EMAIL:<email>`, or null. Only a plain email-shaped value of
         * at most [MAX_ACCOUNT_LENGTH] characters is accepted; anything else in the output is ignored.
         */
        public fun parseProbeAccount(output: String): String? =
            ACCOUNT_LINE.findAll(output)
                .map { it.groupValues[1].trim() }
                .firstOrNull { it.length <= MAX_ACCOUNT_LENGTH && EMAIL_SHAPE.matches(it) }

        /** Where the CLI can live: the profile's own home first (agents installed per profile), then the shared one. */
        public fun candidateCliPaths(profileId: String? = null): List<String> =
            buildList {
                if (profileId != null && SAFE_ID.matches(profileId)) {
                    add("/home/oc/.oc-profiles/$profileId/.local/bin/agy")
                }
                add(SHARED_CLI)
            }

        /**
         * The script run inside the built-in Linux (Flutter `cliScript`, PR #136): the first CLI that exists runs with
         * the profile's home (or the home that holds a sign-in), so it finds its own sign-in; 127 when none is
         * installed. Before the CLI runs, the account email is picked out inside the Linux and printed as one
         * `ACCOUNT_EMAIL:` line only when it is made of plain email characters and at most [MAX_ACCOUNT_LENGTH] long;
         * nothing else from the sign-in leaves the Linux. Paths are fixed strings (the profile id is checked above).
         */
        public fun cliScript(profileId: String? = null): String {
            val candidates = candidateCliPaths(profileId).joinToString(" ")
            val id = profileId?.takeIf { SAFE_ID.matches(it) }.orEmpty()
            val pick = { field: String ->
                "\$(grep -o '\"$field\"[[:space:]]*:[[:space:]]*\"[^\"]*\"' \"\$f\" 2>/dev/null | head -n 1 | cut -d'\"' -f4)"
            }
            return "for b in $candidates; do " +
                "if [ -x \"\$b\" ]; then " +
                "h=\"\${b%/.local/bin/agy}\"; " +
                // Flutter writes `[ -n "<id>" ] && …` even without an id; with none the branch is left out.
                (if (id.isEmpty()) "" else "if [ -d \"$PROFILES/$id\" ]; then h=\"$PROFILES/$id\"; el") +
                "if [ ! -f \"\$h/$TOKEN\" ]; then " +
                "for o in $PROFILES/*/$TOKEN; do " +
                "if [ -f \"\$o\" ]; then h=\"\${o%/$TOKEN}\"; break; fi; " +
                "done; fi; " +
                "export HOME=\"\$h\"; " +
                "f=\"\$HOME/$TOKEN\"; " +
                "e=${pick("id_token_email")}; " +
                "if [ -z \"\$e\" ]; then e=${pick("email")}; fi; " +
                "case \"\$e\" in *[!A-Za-z0-9.@_+-]*) e=\"\";; esac; " +
                "if [ \"\${#e}\" -gt $MAX_ACCOUNT_LENGTH ]; then e=\"\"; fi; " +
                "if [ -n \"\$e\" ]; then echo \"ACCOUNT_EMAIL:\$e\"; fi; " +
                "exec \"\$b\" -p /usage --output-format json; " +
                "fi; " +
                "done; exit 127"
        }

        /** The JSON object in the CLI's output (it may print a banner first). */
        private fun jsonObjectIn(output: String): Map<*, *>? {
            val start = output.indexOf('{')
            val end = output.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return DartJson.decodeOrNull(output.substring(start, end + 1)) as? Map<*, *>
        }

        private fun time(value: Any?): Instant? =
            (value as? String)?.takeIf { it.length <= MAX_TIME_TEXT }?.let(Instant::parseOrNull)

        private fun priority(w: ProviderUsageWindowReading): Int =
            when (w.durationSeconds) {
                FIVE_HOURS -> 0
                WEEK -> 1
                else -> 2
            }

        /** Provider id and display name for one CLI group (Flutter's Gemini / Claude and GPT / other split). */
        private fun identity(groupName: String, manyGroups: Boolean): Pair<String, String> {
            val lower = groupName.lowercase()
            return when {
                "gemini" in lower -> "antigravity" to if (manyGroups) "Antigravity (Gemini Models)" else "Antigravity"
                "claude" in lower || "gpt" in lower || "3p" in lower ->
                    "antigravity_claude" to "Antigravity (Claude and GPT models)"
                else ->
                    ("antigravity_" + lower.replace(Regex("[^a-z0-9]"), "_")).take(PROVIDER_ID_LENGTH) to
                        "Antigravity ($groupName)"
            }
        }

        /**
         * One bucket, or null without a finite fraction. A disabled bucket (for example the 5-hour limit while the
         * weekly one is used up) has nothing left: 0%, never its stale fraction.
         */
        private fun window(bucket: Any?): ProviderUsageWindowReading? {
            val b = bucket as? Map<*, *>
            val fraction =
                ((b?.get("remaining_fraction") ?: b?.get("remainingFraction")) as? Number)?.toDouble()?.takeIf {
                    it.isFinite()
                }
            if (b == null || fraction == null) return null
            val remaining = if (b["disabled"] == true) 0 else (fraction * PERCENT).coerceIn(0.0, PERCENT).roundToInt()
            val (seconds, label) =
                when (b["window"]) {
                    "5h" -> FIVE_HOURS to "5-hour"
                    "weekly" -> WEEK to "Weekly"
                    else -> null to (cleanUsageText(b["name"], LABEL_LENGTH) ?: "Quota")
                }
            return ProviderUsageWindowReading(seconds, label, remaining, time(b["reset_time"] ?: b["resetTime"]))
        }

        /**
         * The CLI's `command.data.groups`: Gemini models read as `antigravity`, Claude and GPT models as
         * `antigravity_claude`, any other group by its own name; a group without a readable bucket is left out. Every
         * reading carries [planLabel] (Google AI Pro unless named) and the account: [accountEmail] from the probe, or an
         * account field the CLI's own JSON names.
         */
        public fun parseCliJson(
            data: Map<*, *>,
            now: Instant,
            planLabel: String? = null,
            accountEmail: String? = null,
        ): List<ProviderUsageReading> {
            val command = data["command"] as? Map<*, *>
            val cmdData = command?.get("data") as? Map<*, *>
            val groups = cmdData?.get("groups") as? List<*> ?: return emptyList()
            val account =
                accountEmail
                    ?: cleanUsageText(
                        cmdData["account"]
                            ?: cmdData["email"]
                            ?: cmdData["user"]
                            ?: command["account"]
                            ?: command["email"]
                            ?: data["account"]
                            ?: data["email"],
                        MAX_ACCOUNT_LENGTH,
                    )
            return groups
                .filterIsInstance<Map<*, *>>()
                .mapNotNull { group ->
                    val windows = (group["buckets"] as? List<*>).orEmpty().mapNotNull(::window).sortedBy(::priority)
                    if (windows.isEmpty()) return@mapNotNull null
                    val name = cleanUsageText(group["name"], GROUP_NAME_LENGTH).orEmpty()
                    val (providerId, displayName) = identity(name, groups.size > 1)
                    ProviderUsageReading(
                        providerId,
                        displayName,
                        now,
                        windows,
                        planLabel = planLabel ?: DEFAULT_PLAN,
                        accountRef = account,
                    )
                }
                .sortedBy { if (it.providerId == "antigravity") 0 else 1 }
        }
    }
}

/** The phone's Antigravity agent row as the usage read sees it (Flutter `PhoneAgentStatus` ready / signedOut). */
public enum class AntigravityAgentState {
    /** Installed and signed in: ask its CLI. */
    Ready,

    /** Installed, not signed in: show "needs sign-in", never numbers. */
    SignedOut,

    /** No row for it: ask its CLI anyway, keep only real numbers. */
    Absent,

    /** Any other state (checking, stopped, not installed…): add nothing. */
    Other,
}

/**
 * The phone helper's usage list plus Antigravity's own (Flutter `_paReadUsage`). The helper's own Antigravity reading
 * wins; otherwise a ready agent adds what its CLI reports (nothing when it reports nothing), a signed-out agent adds
 * one "needs sign-in" reading, and with no agent row the CLI is still asked.
 */
public suspend fun withAntigravityUsage(
    base: List<ProviderUsageReading>,
    agent: AntigravityAgentState,
    now: Instant,
    readCli: suspend () -> List<ProviderUsageReading>?,
): List<ProviderUsageReading> {
    if (base.any { it.providerId == "antigravity" }) return base
    return when (agent) {
        AntigravityAgentState.Ready -> base + readCli().orEmpty()
        AntigravityAgentState.SignedOut ->
            base + ProviderUsageReading("antigravity", "Antigravity", now, emptyList(), needsSignIn = true)
        AntigravityAgentState.Absent -> base + readCli().orEmpty().filterNot { it.needsSignIn }
        AntigravityAgentState.Other -> base
    }
}
