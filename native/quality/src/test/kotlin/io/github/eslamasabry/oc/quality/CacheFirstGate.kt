package io.github.eslamasabry.oc.quality

/**
 * Cache-first (owner rule, 2026-11-09: "why not cache something to load fast? This applies everywhere"; audit in
 * docs/native/qa/cache-first-2026-11-09): a screen that reads a server draws its real layout at once from the last
 * data it saw, then refreshes in place. Pure functions over (path, text), as [BuildRestGates].
 *
 * - [alternateLayout]: no composable named like an offline, cached, fallback or "saved titles" layout in a feature.
 * - [bodySwaps]: every loading branch that replaces a feature screen's body (a skeleton, a loading state view, a blank
 *   body) is reviewed in [REVIEWED]. [Kind.Pending] entries are screens not yet cache-first; their number only shrinks
 *   ([PENDING_CEILING]). A screen made cache-first moves to [Kind.Cold] (its skeleton is only for a first-ever load,
 *   proven by the named test); a screen that never reads a server is [Kind.Local].
 */
internal object CacheFirstGate {
    data class Hit(val path: String, val rule: String, val line: Int)

    sealed interface Kind

    /** Reads only this phone (or runs an action): nothing to keep from a server. */
    data object Local : Kind

    /** Not cache-first yet: shows this instead of its rows while its server data loads. */
    data object Pending : Kind

    /** Cache-first: the branch shows only when nothing was ever kept; [proof] names the test. */
    data class Cold(val proof: String) : Kind

    data class Review(val branches: Int, val kind: Kind)

    private fun code(text: String): List<Pair<Int, String>> =
        text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("*") || line.startsWith("/*")) null else (i + 1) to line
        }

    private val ALT_NAME =
        Regex("""\bfun\s+(\w*(Offline|Cached|Fallback|SavedTitles)\w*|\w*Opening(Row|Rows|List|Body))\s*\(""")

    /** A composable (or any function) in a feature named like an alternate layout. */
    fun alternateLayout(path: String, text: String): List<Hit> {
        if (!path.startsWith("feature/")) return emptyList()
        val lines = code(text)
        return lines.mapIndexedNotNull { i, (n, l) ->
            val composable = lines.subList(maxOf(0, i - 2), i + 1).any { "@Composable" in it.second }
            if (composable && ALT_NAME.containsMatchIn(l)) Hit(path, "alternate layout", n) else null
        }
    }

    private val BODY_SWAP = Regex("""KitSkeleton\w*\(|\b(Loading|Opening)\b\s*->\s*(Unit\b|KitStateView\(|\w*LoadingBody\()""")

    /** Loading branches in a feature that replace the body (skeleton, loading state view, blank). */
    fun bodySwaps(path: String, text: String): List<Hit> {
        if (!path.startsWith("feature/")) return emptyList()
        return code(text).flatMap { (n, l) -> BODY_SWAP.findAll(l).map { Hit(path, "body swap while loading", n) } }
    }

    /** Pending screens (not cache-first yet). Lower it with every screen made cache-first; never raise it. */
    const val PENDING_CEILING: Int = 35

    private fun f(module: String, rest: String): String =
        "feature/$module/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/${module.replace("-", "")}/$rest"

    /** The reviewed loading branches per file (the audit table's live half). */
    val REVIEWED: Map<String, Review> =
        mapOf(
            f("agenttools", "ui/AgentToolsScreen.kt") to Review(1, Pending),
            f("chat", "agentfeatures/ui/AgentFeaturesScreen.kt") to Review(1, Pending),
            f("chat", "context/ActiveContextSheet.kt") to Review(1, Pending),
            f("chat", "forms/ConnectionFormScreen.kt") to Review(1, Local),
            f("chat", "ui/ChatScreen.kt") to Review(1, Cold("CF-12")),
            f("chat", "ui/ChatStates.kt") to Review(1, Cold("CF-12")),
            f("chats", "attention/AttentionScreen.kt") to Review(1, Pending),
            f("chats", "ui/ChatsAllScreen.kt") to Review(1, Pending),
            f("chats", "ui/ChatsScreen.kt") to Review(2, Pending),
            f("chats", "ui/NewConversationScreen.kt") to Review(1, Pending),
            f("factsbook", "ui/AboutYouScreen.kt") to Review(1, Pending),
            f("factsbook", "ui/MemorySection.kt") to Review(1, Pending),
            f("factsbook", "ui/NeedsYouScreen.kt") to Review(1, Pending),
            f("factsbook", "ui/PageScreens.kt") to Review(3, Pending),
            f("factsbook", "ui/RemindersScreens.kt") to Review(2, Pending),
            f("factsbook", "ui/StatusScreens.kt") to Review(2, Pending),
            f("factsbook", "ui/TriggersScreens.kt") to Review(2, Pending),
            f("files", "ui/FileScreen.kt") to Review(3, Pending),
            f("files", "ui/FileShareSheet.kt") to Review(1, Local),
            f("git", "ui/GitScreen.kt") to Review(1, Pending),
            f("handoff", "ui/AgentHandoffScreen.kt") to Review(1, Local),
            f("linux", "ui/KeptServicesScreen.kt") to Review(1, Local),
            f("linux", "ui/LinuxPackagesScreen.kt") to Review(1, Local),
            f("linux", "ui/LinuxPortsScreen.kt") to Review(1, Local),
            f("linux", "ui/LinuxStorageScreen.kt") to Review(1, Local),
            f("manage-space", "ui/ManageSpaceScreen.kt") to Review(1, Local),
            f("phone", "ui/RunningScreen.kt") to Review(1, Local),
            f("plans", "ui/PlansScreen.kt") to Review(1, Pending),
            f("projects", "ui/IsolatedTaskSheet.kt") to Review(1, Local),
            f("projects", "ui/OpenProjectScreen.kt") to Review(3, Cold("CF-26")),
            f("projects", "ui/ProjectPageScreen.kt") to Review(2, Cold("CF-23")),
            f("projects", "ui/ProjectWorkspaceSheet.kt") to Review(1, Pending),
            f("projects", "ui/ProjectsPickerScreen.kt") to Review(2, Cold("CF-20")),
            f("projects", "ui/WorktreeInspectionPane.kt") to Review(1, Pending),
            f("prompts", "ui/KeptPromptsScreen.kt") to Review(1, Local),
            f("prompts", "ui/SavedPromptsScreen.kt") to Review(1, Local),
            f("prompts", "ui/ServerListsScreens.kt") to Review(3, Pending),
            f("review", "ui/ReviewScreens.kt") to Review(3, Pending),
            f("schedules", "ui/ScheduleEditScreen.kt") to Review(1, Pending),
            f("schedules", "ui/SchedulesScreen.kt") to Review(1, Pending),
            f("servers", "account/ui/AgentAccountScreen.kt") to Review(2, Pending),
            f("settings", "ui/LibraryRegistrySheet.kt") to Review(2, Pending),
            f("settings", "ui/LibraryScreen.kt") to Review(1, Pending),
            f("settings", "ui/ModelDefaultsScreen.kt") to Review(1, Pending),
            f("settings", "ui/PermissionSettingsScreen.kt") to Review(1, Local),
            f("settings", "ui/VoiceLicensesView.kt") to Review(1, Local),
            f("setup", "ui/SetupScreen.kt") to Review(1, Local),
            f("setupassistant", "ui/SetupScreen.kt") to Review(3, Local),
            f("team", "ui/TeamBoardScreen.kt") to Review(1, Pending),
            f("team", "ui/TeamEditorScreen.kt") to Review(1, Pending),
            f("team", "ui/TeamHomeScreen.kt") to Review(1, Pending),
            f("team", "ui/TeamProjectScreen.kt") to Review(1, Pending),
            f("team", "ui/TeamTaskScreen.kt") to Review(1, Pending),
            f("terminal", "ui/TerminalScreen.kt") to Review(1, Pending),
            f("usage", "ui/LimitsContent.kt") to Review(1, Pending),
            f("usage", "ui/UsageScreen.kt") to Review(1, Pending),
            f("voice", "ui/TeachingScreen.kt") to Review(1, Local),
        )

    /**
     * Alternate layouts still in the code (path → why). Shrinks only. The home's saved-titles list goes when the
     * home draws its real rows from the saved feed (native/home-connecting).
     */
    val ALTERNATE_LAYOUTS: Map<String, String> =
        mapOf(
            f("chats", "ui/ChatsHomeSheets.kt") to "OpeningRow: the home's saved-titles list (removed by home-connecting)",
        )
}
