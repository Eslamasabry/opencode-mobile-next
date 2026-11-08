package io.github.eslamasabry.oc.engine.team

/**
 * The phone engine's `GET /v1/health` (Dart `PhoneEngineHealth`): who it answers for, which commands it implements
 * and whether it may run work now. Parsed strictly: a wrong schema, profile or shape reads as no engine.
 */
public data class TeamHealth(
    val engineVersion: String,
    val execution: Boolean,
    val boundary: Boolean,
    val oc2: Boolean,
    val oc2Verified: Boolean,
    val commandActions: Set<String>,
    /** Signed confinement kind: `none` (unreported), `landlock` or `proot`. */
    val boundaryTier: String = "none",
) {
    /** Dart `protocolVerified`. */
    public val protocolVerified: Boolean
        get() = oc2 && oc2Verified

    /** Dart `canExecute`: protocol identity alone never proves the permission boundary. */
    public val canExecute: Boolean
        get() = execution && boundary && protocolVerified

    /** What the engine can do right now (Dart `PhoneEngineGateway.capabilities`). */
    public val capabilities: TeamCapabilities
        get() {
            fun all(vararg actions: String) = actions.all { it in commandActions }
            return TeamCapabilities(
                lifecycle = all(TeamAction.CREATE_PROJECT, TeamAction.CREATE_QUICK_TASK, TeamAction.DELETE_PROJECT),
                livingSpec = all(TeamAction.SAVE_SPEC_DRAFT, TeamAction.APPROVE_SPEC),
                lanes = canExecute && all(TeamAction.APPROVE_PLAN, TeamAction.PAUSE_TASK, TeamAction.STOP_TASK),
                placement = canExecute && all(TeamAction.MOVE_TASK, TeamAction.UPDATE_SERVER),
                verification = canExecute && all(TeamAction.VERIFY_TASK, TeamAction.RECHECK_TASK),
                mergeQueue = canExecute && all(TeamAction.PROCESS_MERGE_QUEUE),
                promotion = canExecute && all(TeamAction.PROMOTE),
                resume = canExecute && all(TeamAction.RESUME_PROJECT, TeamAction.RESUME_TASK),
                budgets = all(TeamAction.UPDATE_SETTINGS),
                digest = all(TeamAction.ACKNOWLEDGE_DIGEST),
                boundaryTier = boundaryTier,
            )
        }

    /**
     * Why [command] may not be sent (Dart `executeProject`'s checks), or null when it may: an action the engine does
     * not list, or one that runs work while the engine cannot execute.
     */
    public fun refusal(command: TeamCommand): String? =
        when {
            command.action !in commandActions -> "unsupportedCommand"
            command.runsWork && !canExecute ->
                when {
                    !boundary -> "boundaryUnverified"
                    !protocolVerified -> "protocolUnverified"
                    else -> "engineUnavailable"
                }
            else -> null
        }

    public companion object {
        private val TIERS = setOf("none", "landlock", "proot")

        /** Dart `PhoneEngineHealth.fromJson`; null where Dart throws. */
        public fun parse(value: Any?, expectedProfile: String): TeamHealth? {
            val map = value as? Map<*, *> ?: return null
            val schema = map["schemaVersion"] as? Number ?: return null
            if (schema.toDouble() != 1.0 || map["profileId"] != expectedProfile) return null
            val flags = map["capabilities"] as? Map<*, *> ?: return null
            val actions = map["commandActions"] as? List<*> ?: return null
            val version = map["engineVersion"] as? String ?: return null
            if (actions.any { it !is String } || version.isEmpty()) return null
            if (listOf("execution", "boundary", "oc2").any { flags[it] !is Boolean }) return null
            if (flags.containsKey("oc2Verified") && flags["oc2Verified"] !is Boolean) return null
            val top = map["boundaryTier"]
            val inner = flags["boundaryTier"]
            if (map.containsKey("boundaryTier") && top !in TIERS) return null
            if (flags.containsKey("boundaryTier") && inner !in TIERS) return null
            if (map.containsKey("boundaryTier") && flags.containsKey("boundaryTier") && top != inner) return null
            return TeamHealth(
                engineVersion = version,
                execution = flags["execution"] as Boolean,
                boundary = flags["boundary"] as Boolean,
                oc2 = flags["oc2"] as Boolean,
                oc2Verified = flags["oc2Verified"] == true,
                commandActions = actions.filterIsInstance<String>().toSet(),
                boundaryTier = (top ?: inner ?: "none") as String,
            )
        }
    }
}

/**
 * Which project actions the engine can run now (Dart `OrchestrationCapabilities` project flags). Pages draw an action
 * only when its flag is on; while the engine cannot run work they say why once, with the fix, instead of dead buttons.
 */
public data class TeamCapabilities(
    val lifecycle: Boolean = false,
    val livingSpec: Boolean = false,
    val lanes: Boolean = false,
    val placement: Boolean = false,
    val verification: Boolean = false,
    val mergeQueue: Boolean = false,
    val promotion: Boolean = false,
    val resume: Boolean = false,
    val budgets: Boolean = false,
    val digest: Boolean = false,
    val boundaryTier: String = "none",
) {
    public companion object {
        /** The demo simulates every action, so it is never gated (Dart `TeamExecutionGate.allows`). */
        public val ALL: TeamCapabilities =
            TeamCapabilities(true, true, true, true, true, true, true, true, true, true)
    }
}

/** One read of the engine: its health and the workspace. */
public data class TeamRead(val health: TeamHealth, val workspace: TeamWorkspace)
