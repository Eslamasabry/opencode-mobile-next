package io.github.eslamasabry.oc.engine.analytics.health

/*
 * B-190: the fixed words and ranges of the health and diagnostics telemetry, word for word as Flutter 2.1.5
 * (`lib/telemetry/health_schema.dart`; the contract is docs/telemetry/health-and-diagnostics.md, shared by both apps).
 * Nothing here is free text: every value is one listed word, one listed range, or a number matched by a listed
 * pattern. [productSchema][io.github.eslamasabry.oc.engine.analytics.product.productSchema] puts these into the event
 * list; [TelemetryService] drops anything that is not listed.
 */

/** Range buckets. Each `*Of` function answers one of its list's words, so a call site cannot invent a value. */
public object HealthRanges {
    private fun pick(value: Double, cuts: List<Double>, words: List<String>): String {
        for (i in cuts.indices) if (value < cuts[i]) return words[i]
        return words.last()
    }

    /** `conn_attempt.ms_range`. */
    public val connMs: List<String> = listOf("<500", "500-2000", "2000-10000", ">10000")

    public fun connMsOf(elapsedMs: Long): String = pick(elapsedMs.toDouble(), listOf(500.0, 2000.0, 10000.0), connMs)

    /** `send_result.first_reply_range`. */
    public val firstReply: List<String> = listOf("<2s", "2-10s", "10-30s", ">30s", "none")

    public fun firstReplyOf(elapsedMs: Long?): String =
        if (elapsedMs == null) "none" else pick(elapsedMs.toDouble(), listOf(2000.0, 10000.0, 30000.0), firstReply)

    /** `turn_stall.minutes_range`. */
    public val stallMinutes: List<String> = listOf("<1", "1-5", "5-15", "15-60", ">60")

    public fun stallMinutesOf(elapsedMs: Long): String =
        pick((elapsedMs / 1000) / 60.0, listOf(1.0, 5.0, 15.0, 60.0), stallMinutes)

    /** `queue_flush.sent` and `.failed`: a count range. */
    public val counts: List<String> = listOf("0", "1", "2-5", "6-20", ">20")

    public fun countOf(count: Int): String =
        when {
            count <= 0 -> "0"
            count == 1 -> "1"
            count <= 5 -> "2-5"
            count <= 20 -> "6-20"
            else -> ">20"
        }

    /** `linux_setup_step.ms_range`. */
    public val setupMs: List<String> = listOf("<10000", "10000-60000", "60000-300000", ">300000")

    public fun setupMsOf(elapsedMs: Long): String =
        pick(elapsedMs.toDouble(), listOf(10000.0, 60000.0, 300000.0), setupMs)

    /** `app_start.ms_range`. */
    public val appStartMs: List<String> = listOf("<500", "500-1000", "1000-2000", "2000-5000", ">5000")

    public fun appStartMsOf(elapsedMs: Long): String =
        pick(elapsedMs.toDouble(), listOf(500.0, 1000.0, 2000.0, 5000.0), appStartMs)

    /** `jank.janky_pct_range`. */
    public val jankPct: List<String> = listOf("<1", "1-5", "5-15", ">15")

    public fun jankPctOf(percent: Double): String = pick(percent, listOf(1.0, 5.0, 15.0), jankPct)

    /** `jank.refresh_hz`. */
    public val refreshHz: List<String> = listOf("60", "90", "120", "other")

    public fun refreshHzOf(hz: Double): String =
        when (kotlin.math.round(hz).toInt()) {
            in 58..62 -> "60"
            in 88..92 -> "90"
            in 118..122 -> "120"
            else -> "other"
        }

    /** `ram_range` (GB). */
    public val ram: List<String> = listOf("<3", "3-4", "4-6", "6-8", "8-12", ">12")

    public fun ramOf(gigabytes: Double): String = pick(gigabytes, listOf(3.0, 4.0, 6.0, 8.0, 12.0), ram)

    /** `free_storage_range` and `linux_setup_step.free_space_range` (GB). */
    public val storage: List<String> = listOf("<2", "2-8", "8-32", ">32")

    public fun storageOf(gigabytes: Double): String = pick(gigabytes, listOf(2.0, 8.0, 32.0), storage)

    /** The custom key `connections`. */
    public val connections: List<String> = listOf("0", "1", "2-3", "4+")

    public fun connectionsOf(count: Int): String =
        when {
            count <= 0 -> "0"
            count == 1 -> "1"
            count <= 3 -> "2-3"
            else -> "4+"
        }
}

/** The words several events share. */
public object HealthWords {
    /** `conn_attempt.fail_kind`; `send_result` adds [sendOnlyFailKinds]. */
    public val failKinds: Set<String> =
        setOf("dns", "tls", "auth", "timeout", "refused", "server_5xx", "protocol", "offline", "other")
    public val sendOnlyFailKinds: Set<String> = setOf("rejected", "too_large")
    public val serverKinds: Set<String> = setOf("phone", "computer")
    public val okFail: Set<String> = setOf("ok", "fail")
    public val dropReasons: Set<String> = setOf("network_change", "server_closed", "idle_timeout", "background", "other")
    public val yesNo: Set<String> = setOf("yes", "no")
    public val stallStages: Set<String> = setOf("waiting_tool", "waiting_approval", "streaming", "other")

    /** `linux_setup_step.step`. */
    public val linuxSteps: Set<String> =
        linkedSetOf("download", "unpack", "essentials", "node", "opencode", "paseo", "agents", "done")
    public val exitKinds: Set<String> = setOf("0", "1", "126", "127", "signal", "other")
    public val signals: Set<String> = setOf("SIGILL", "SIGSEGV", "SIGBUS", "SIGKILL", "other", "none")
    public val compatModes: Set<String> = setOf("none", "armcap", "threads", "both")

    /** `agent_start.agent`. */
    public val agents: Set<String> = setOf("claude_code", "codex", "gemini", "antigravity", "opencode", "other")
    public val agentFailKinds: Set<String> = setOf("not_installed", "needs_sign_in", "crashed", "timeout", "other")
    public val bgReasons: Set<String> = setOf("os_limit", "user_stopped", "idle", "crashed", "other")
    public val updateKinds: Set<String> = setOf("in_app", "patch")
    public val updateResults: Set<String> = setOf("installed", "failed", "declined")
    public val updateFailKinds: Set<String> = setOf("download", "checksum", "signer", "version", "installer", "other")
    public val startKinds: Set<String> = setOf("cold", "warm")
    public val memoryLevels: Set<String> = setOf("moderate", "low", "critical")

    /** Android's ApplicationExitInfo reasons, as the contract names them. */
    public val exitReasons: Set<String> =
        setOf(
            "crash", "crash_native", "anr", "low_memory", "excessive_resource", "signaled", "user_requested",
            "dependency_died", "other",
        )
    public val buildKinds: Set<String> = setOf("store", "sideload", "ci", "local")
    public val keyServerKinds: Set<String> = setOf("phone", "computer", "none")
    public val engineStates: Set<String> = setOf("stopped", "starting", "ready", "failing")
    public val abis: Set<String> = setOf("arm64", "arm32", "x86_64")
    public val userServerKinds: Set<String> = setOf("phone", "computer", "both")
}

/** Numbers and versions that cannot be listed word by word. Each matches digits and dots only, so none carries text. */
public object HealthPatterns {
    public val appVersion: Regex = Regex("^\\d{1,2}\\.\\d{1,2}\\.\\d{1,3}$")
    public val majorMinor: Regex = Regex("^\\d{1,2}\\.\\d{1,2}$")
    public val number: Regex = Regex("^\\d{1,3}$")
    public val reportCode: Regex = Regex("^[0-9a-f]{8}-\\d{1,5}$")
}

/** The Crashlytics custom keys (section A) and the words each may hold; [healthKeyPatterns] the numeric ones. */
public val healthKeyWords: Map<String, Set<String>> =
    mapOf(
        "build_kind" to HealthWords.buildKinds,
        "server_kind" to HealthWords.keyServerKinds,
        "engine_state" to HealthWords.engineStates,
        "connections" to HealthRanges.connections.toSet(),
        "exit_reason" to HealthWords.exitReasons,
    )

public val healthKeyPatterns: Map<String, Regex> =
    mapOf(
        "app_version" to HealthPatterns.appVersion,
        "oc_server_version" to HealthPatterns.majorMinor,
        "report_code" to HealthPatterns.reportCode,
    )

/** The user properties (section D). */
public val healthPropertyWords: Map<String, Set<String>> =
    mapOf(
        "ram_range" to HealthRanges.ram.toSet(),
        "free_storage_range" to HealthRanges.storage.toSet(),
        "abi" to HealthWords.abis,
        "notif_allowed" to HealthWords.yesNo,
        "server_kind" to HealthWords.userServerKinds,
    )

public val healthPropertyPatterns: Map<String, Regex> =
    mapOf("webview_major" to HealthPatterns.number, "oc_server_version" to HealthPatterns.majorMinor)

/** The breadcrumb words (section F). A crumb is `area.action`, or `area.action:word` for the four areas that name one. */
public object BreadcrumbWords {
    public val plain: Set<String> =
        setOf(
            "app.start", "app.foreground", "app.background", "chat.send", "chat.stop", "chat.approve", "chat.deny",
            "chat.tool_wait", "conn.connect", "conn.drop", "conn.reconnect", "linux.fail", "agent.fail", "update.offer",
            "update.install", "update.fail",
        )

    /** `settings.change:<setting group word>`: the Settings hub's groups. */
    public val settingGroups: Set<String> =
        setOf("connections", "ai", "chat", "this_app", "help", "privacy", "appearance", "notifications", "other")

    /** `area.action:` prefixes and the words allowed after each. */
    public val named: Map<String, Set<String>> =
        mapOf(
            "nav.open" to
                setOf(
                    "conversations", "chat", "new_chat", "servers", "this_phone", "setup", "privacy", "appearance",
                    "integrations", "settings", "files", "git", "review", "team", "usage", "agents", "about", "other",
                ),
            "linux.step" to HealthWords.linuxSteps,
            "agent.start" to HealthWords.agents,
            "settings.change" to settingGroups,
        )

    /** Whether [crumb] is one of the listed breadcrumbs. */
    public fun isListed(crumb: String): Boolean {
        if (crumb in plain) return true
        val colon = crumb.indexOf(':')
        if (colon <= 0) return false
        return named[crumb.substring(0, colon)]?.contains(crumb.substring(colon + 1)) ?: false
    }
}
