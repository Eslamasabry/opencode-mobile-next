package oc.buildlogic

/**
 * ADR §6.1 + §6.2 as data. A violation fails configuration, before any compile. Only the coordinator edits this file.
 *
 * Audit BLOCKER 3 (signatures needing forbidden edges) is resolved here, not by the signatures:
 * - `:core:security` may use `:core:model` (AddressRules.normalize returns ServerOrigin; model depends only on common).
 * - `:ui:kit-content` may use `:core:security` (WebPreview takes an ApprovedLink).
 * - `:core:net` still may not use `:core:data`; credentials arrive already resolved in `GatewaySpec.credentials`
 *   (`ConnectionCredentials`, :core:domain), built by :engine:connection's ProfileSource (plan 03 §10.7; no reader seam).
 * - Plan 03 §0.3 wants ServerOrigin/ApprovedLink in :core:common and the two edges above deleted (open, INTEGRATE-W1).
 */
internal object ModuleRules {
    /** ADR §6.2. An engine may use only engines listed above it. */
    val engineOrder =
        listOf(
            "settings", "analytics", "diagnostics", "profiles", "connection", "transcript", "requests", "delivery", "composer",
            "runtime", "agents", "attention", "schedule", "media", "project", "integrations", "entry", "team",
        )

    private val core =
        setOf(
            ":core:common", ":core:security", ":core:model", ":core:domain",
            ":core:data", ":core:db", ":core:net", ":core:markdown",
        )
    private val kits = setOf(":ui:kit", ":ui:kit-chat", ":ui:kit-content")
    private val testOnly = setOf(":core:testing", ":ui:testing")

    /** null = allowed. [inTest] is true for any configuration whose name contains "test" (case-insensitive). */
    fun violation(from: String, to: String, inTest: Boolean): String? {
        if (to == from) return null // a test compilation depending on its own module
        if (to in testOnly) return if (inTest) null else "$to is test-only; use it from a test source set."
        val ok =
            when {
                from == ":androidApp" || from == ":quality" -> true
                from == ":desktopApp" -> !to.startsWith(":android:") && to != ":androidApp"
                from == ":core:common" || from == ":core:net-okhttp" || from.startsWith(":tools:") -> false
                from == ":core:model" -> to == ":core:common"
                from == ":core:security" -> to in setOf(":core:common", ":core:model")
                from == ":core:domain" -> to in setOf(":core:common", ":core:model", ":core:security")
                from == ":core:data" -> to in setOf(":core:common", ":core:security", ":core:model")
                from == ":core:db" -> to in setOf(":core:common", ":core:security", ":core:model", ":core:data")
                from == ":core:net" -> to in setOf(":core:common", ":core:security", ":core:model", ":core:net-okhttp")
                from == ":core:markdown" -> to in setOf(":core:common", ":core:model")
                from == ":core:testing" -> to in core || to == ":core:net-okhttp"
                from.startsWith(":protocol:") ->
                    to in setOf(":core:common", ":core:model", ":core:domain", ":core:net", ":core:security")
                from.startsWith(":engine:") ->
                    (to in core) || (to.startsWith(":engine:") && engineIsAbove(to, from))
                from == ":ui:design" -> to == ":core:common"
                from == ":ui:words" || from == ":ui:routes" -> to in setOf(":core:common", ":core:model")
                from == ":ui:kit" ->
                    to in setOf(":ui:design", ":ui:words", ":core:common", ":core:security", ":core:model")
                from == ":ui:kit-chat" ->
                    to in
                        setOf(
                            ":ui:kit", ":ui:design", ":ui:words", ":core:markdown",
                            ":core:model", ":core:common", ":core:security",
                        )
                from == ":ui:kit-content" ->
                    to in setOf(":ui:kit", ":ui:design", ":ui:words", ":core:model", ":core:common", ":core:security")
                from == ":ui:shell" ->
                    to in kits ||
                        to in
                        setOf(
                            ":ui:design", ":ui:words", ":ui:routes", ":engine:entry", ":engine:attention",
                            ":engine:settings", ":core:domain", ":core:model", ":core:common",
                        )
                from == ":ui:testing" ->
                    to in kits || to in setOf(":ui:design", ":ui:words", ":ui:routes", ":core:common", ":core:model")
                from.startsWith(":feature:") ->
                    to in kits ||
                        to.startsWith(":engine:") ||
                        to in setOf(":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common")
                from == ":android:migration" ->
                    to in setOf(":core:common", ":core:security", ":core:data", ":core:model", ":core:db")
                from.startsWith(":android:") ->
                    to.startsWith(":core:") || to.startsWith(":engine:") || to == ":ui:words" || to == ":ui:design"
                else -> false
            }
        return if (ok) {
            null
        } else {
            "Module rule: $from may not depend on $to (ADR §6.1/§6.2). " +
                "Move the shared piece down (into a lower engine or :core:domain) or ask the coordinator."
        }
    }

    private fun engineIsAbove(to: String, from: String): Boolean =
        engineOrder.indexOf(to.substringAfterLast(':')) in 0 until engineOrder.indexOf(from.substringAfterLast(':'))

    data class Layer(val explicitApi: Boolean, val hasPlatformSourceSets: Boolean, val isTestOnly: Boolean)

    /** ADR §7.2: the only KMP modules that may have androidMain/desktopMain code (Konsist K10 checks the folders). */
    val platformSourceSets =
        setOf(
            ":core:common", ":core:data", ":core:net", ":core:db",
            ":ui:kit", ":ui:kit-chat", ":ui:kit-content", ":ui:shell", ":ui:testing",
        )

    fun layerOf(path: String): Layer =
        Layer(
            explicitApi =
                path.startsWith(":core:") || path.startsWith(":protocol:") || path.startsWith(":engine:") ||
                    path in setOf(":ui:design", ":ui:routes") || path in kits,
            hasPlatformSourceSets = path in platformSourceSets,
            isTestOnly = path in testOnly,
        )
}
