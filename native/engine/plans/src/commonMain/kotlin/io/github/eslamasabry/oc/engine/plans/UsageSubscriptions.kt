package io.github.eslamasabry.oc.engine.plans

// The subscriptions model behind Usage, its alerts and the home widget (port of lib/domain/usage_subscriptions.dart).
//
// A subscription is a plan on a provider account. It can be signed in on several connections and is read by a
// reader. Nothing here touches the network, storage or the platform; the controller feeds it readings and
// [mergeUsageSubscriptions] turns them into one list. Times are epoch milliseconds (UTC).

/** The provider ids the app can offer under "Add a subscription". */
public val usagePlanProviders: List<String> = listOf("codex", "claude", "glm", "minimax")

/**
 * The providers whose usage the app's reader can read from an OpenCode sign-in (Codex, GLM, MiniMax). Any other
 * provider connected on an OpenCode server is not a plan card.
 */
public val usageReadableFromOpenCode: Set<String> = setOf("codex", "glm", "minimax")

/** Providers whose sign-in is a key, not an account sign-in. Their fix is "Add key". */
private val keyProviders = setOf("glm", "minimax")

/** Maps OpenCode's (and Paseo's) provider ids to the ids Usage uses. Null for providers that are not subscriptions. */
public fun canonicalUsageProviderId(id: String): String? =
    when (id) {
        "codex", "openai" -> "codex"
        "claude", "anthropic" -> "claude"
        "glm", "zai-coding-plan", "zhipuai-coding-plan" -> "glm"
        "minimax", "minimax-coding-plan" -> "minimax"
        "copilot", "github-copilot" -> "copilot"
        "kimi", "kimi-for-coding" -> "kimi"
        else -> null
    }

public enum class UsageConnectionKind { InAppUbuntu, PhoneAgents, OpenCodeServer, PaseoServer, CodexServer }

/** A place usage is read from. Equality is by [id] only. */
public class UsageConnection(
    public val id: String,
    public val name: String,
    public val kind: UsageConnectionKind,
) {
    override fun equals(other: Any?): Boolean = other is UsageConnection && other.id == id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "UsageConnection($id)"

    public companion object {
        /** This phone's agent host. Its id is not a saved profile id. */
        public const val PHONE_ID: String = "phone"
    }
}

public enum class SignInState { SignedIn, Expired, NotSignedIn }

/** How a provider is signed in. [Plan] is an account sign-in; [ApiKey] is a pay-as-you-go key with no plan limits. */
public enum class SignInMethod { Plan, ApiKey, Unknown }

public data class SignedInOn(
    val connection: UsageConnection,
    val state: SignInState,
    val method: SignInMethod = SignInMethod.Unknown,
    /** The numbers on the card came from this connection. */
    val readsFromHere: Boolean = false,
    val online: Boolean = true,
)

public enum class UsageWindowKind { FiveHour, Weekly, Monthly, Other }

/** One limit window. [usedPercent] is clamped to 0..100. */
public class UsageWindow(
    public val id: String,
    usedPercent: Double,
    public val durationSeconds: Int? = null,
    public val resetsAt: Long? = null,
    public val label: String = "",
) {
    public val usedPercent: Double = usedPercent.coerceIn(0.0, 100.0)
    public val kind: UsageWindowKind = kindOf(id, durationSeconds)
    public val leftPercent: Double get() = 100 - usedPercent

    public fun resetPassed(now: Long): Boolean = resetsAt != null && now >= resetsAt

    override fun equals(other: Any?): Boolean =
        other is UsageWindow && other.id == id && other.usedPercent == usedPercent &&
            other.durationSeconds == durationSeconds && other.resetsAt == resetsAt && other.label == label

    override fun hashCode(): Int = listOf(id, usedPercent, durationSeconds, resetsAt, label).hashCode()

    override fun toString(): String = "UsageWindow($id, $usedPercent)"

    private companion object {
        fun kindOf(id: String, seconds: Int?): UsageWindowKind {
            if (seconds == 18000) return UsageWindowKind.FiveHour
            if (seconds == 604800) return UsageWindowKind.Weekly
            if (seconds != null && seconds >= 28 * 86400 && seconds <= 31 * 86400) return UsageWindowKind.Monthly
            return when (id) {
                "5h", "five_hour" -> UsageWindowKind.FiveHour
                "weekly", "seven_day", "week" -> UsageWindowKind.Weekly
                "monthly", "month" -> UsageWindowKind.Monthly
                else -> UsageWindowKind.Other
            }
        }
    }
}

public enum class SubscriptionState {
    Ok, NearLimit, Exhausted, CantReadYet, SignInExpired, NotSignedIn, ReaderBroken, OfflineStale, NoLimits, ApiKeyOnly,
}

public enum class UsageFix { None, SignIn, AddKey, SetUp, TryAgain }

public data class UsageSubscription(
    val key: String,
    val providerId: String,
    val displayName: String,
    val state: SubscriptionState,
    val planLabel: String? = null,
    val accountRef: String? = null,
    val windows: List<UsageWindow> = emptyList(),
    val fetchedAt: Long? = null,
    val checkedAt: Long? = null,
    val readFrom: UsageConnection? = null,
    val signedInOn: List<SignedInOn> = emptyList(),
    val fix: UsageFix = UsageFix.None,
    val fixConnection: UsageConnection? = null,
    val resetSinceLastReading: Boolean = false,
    val showConnectionName: Boolean = false,
    val alertThreshold: Double = 80.0,
    val hasAlertOverride: Boolean = false,
    val notifyOnReset: Boolean = false,
) {
    val fiveHour: UsageWindow? get() = windows.firstOrNull { it.kind == UsageWindowKind.FiveHour }
    val weekly: UsageWindow? get() = windows.firstOrNull { it.kind == UsageWindowKind.Weekly }
    val mostUsedPercent: Double? get() = windows.maxOfOrNull { it.usedPercent }
    val leastLeftPercent: Double? get() = mostUsedPercent?.let { 100 - it }

    override fun toString(): String = "UsageSubscription($key, $state)"
}

public data class ApiKeyAccount(
    val providerId: String,
    val displayName: String,
    val connection: UsageConnection,
    /** Seen through an OpenCode server: its plan limits cannot be read from there (Claude), so a Details row only. */
    val viaOpenCode: Boolean = false,
)

public data class UsageSnapshot(
    val subscriptions: List<UsageSubscription> = emptyList(),
    val apiKeyAccounts: List<ApiKeyAccount> = emptyList(),
    val addable: List<String> = emptyList(),
    val connections: List<UsageConnection> = emptyList(),
    val refreshedAt: Long? = null,
    val loaded: Boolean = false,
    /** Connections the last refresh could not reach: their state is unknown. */
    val offlineConnectionIds: Set<String> = emptySet(),
    /** Connections that answered at least once. A connection in neither set was never asked. */
    val answeredConnectionIds: Set<String> = emptySet(),
) {
    public fun isOffline(connection: UsageConnection): Boolean = connection.id in offlineConnectionIds

    /** True when [connection] gave an answer, so "not signed in" there is a fact and not a guess. */
    public fun hasAnswered(connection: UsageConnection): Boolean =
        !isOffline(connection) && connection.id in answeredConnectionIds

    val hasSeveralConnections: Boolean get() = connections.size > 1
}

// ---------------------------------------------------------------------------------------------------------------------
// Source-side types: what the readers (another module) hand over through [UsageReadingsPort].

public enum class UsageReadingStatus { Ok, NoLimits, AuthRequired, NotSignedIn }

/** Why the last numbers could not be refreshed. */
public enum class UsageProblem { RateLimited, Broken, Offline }

/** One provider as one connection reports it. Display numbers only. */
public data class UsageReading(
    val providerId: String,
    val displayName: String,
    val connectionId: String,
    val status: UsageReadingStatus,
    val fetchedAt: Long,
    val windows: List<UsageWindow> = emptyList(),
    /** Opaque account hash from the reader; never an email or token. */
    val accountRef: String? = null,
    val plan: String? = null,
    val method: SignInMethod = SignInMethod.Unknown,
    val checkedAt: Long? = null,
    val problem: UsageProblem? = null,
) {
    val cacheKey: String get() = "$connectionId|$providerId"

    val hasNumbers: Boolean
        get() = (status == UsageReadingStatus.Ok || status == UsageReadingStatus.NoLimits) &&
            problem != UsageProblem.Broken
}

/** What a connection says about one provider's sign-in (OpenCode's connected providers). Says nothing about numbers. */
public data class UsageSignIn(
    val providerId: String,
    val connectionId: String,
    val state: SignInState = SignInState.SignedIn,
    val method: SignInMethod = SignInMethod.Unknown,
    val displayName: String = "",
)

/** A reader tried and failed; no text from the failure is kept. */
public data class UsageFailure(val connectionId: String, val providerId: String, val problem: UsageProblem)

public data class UsageAlertOverride(val threshold: Double? = null, val notifyOnReset: Boolean? = null) {
    val isEmpty: Boolean get() = threshold == null && notifyOnReset == null
}

// ---------------------------------------------------------------------------------------------------------------------
// The merge.

public fun usageSubscriptionKey(providerId: String, accountRef: String?, connectionId: String): String =
    "$providerId|${accountRef ?: "conn:$connectionId"}"

private val stateRank =
    mapOf(
        SubscriptionState.Exhausted to 0,
        SubscriptionState.SignInExpired to 1,
        SubscriptionState.NearLimit to 2,
        SubscriptionState.ReaderBroken to 3,
        SubscriptionState.CantReadYet to 4,
        SubscriptionState.NotSignedIn to 5,
        SubscriptionState.OfflineStale to 6,
        SubscriptionState.Ok to 7,
        SubscriptionState.NoLimits to 8,
        SubscriptionState.ApiKeyOnly to 9,
    )

/**
 * Turns every connection's readings and sign-ins into one list.
 *
 * [readings] is one entry per connection and provider. [authoritativeSignIns] names the connections whose [signIns]
 * answer was complete, so a provider missing from it is signed out there.
 */
public fun mergeUsageSubscriptions(
    now: Long,
    connections: List<UsageConnection>,
    readings: List<UsageReading>,
    signIns: List<UsageSignIn> = emptyList(),
    authoritativeSignIns: Set<String> = emptySet(),
    defaultThreshold: Double = 80.0,
    overrides: Map<String, UsageAlertOverride> = emptyMap(),
    notifyOnResetDefault: Boolean = false,
    refreshedAt: Long? = null,
    loaded: Boolean = true,
    offlineConnections: Set<String> = emptySet(),
): UsageSnapshot {
    val byId = connections.associateBy { it.id }

    // A provider the connection no longer lists has been signed out there.
    val effective = mutableListOf<UsageReading>()
    for (r in readings) {
        if (byId[r.connectionId] == null) continue
        var reading = r
        if (reading.hasNumbers && r.connectionId in authoritativeSignIns &&
            signIns.none {
                it.providerId == r.providerId && it.connectionId == r.connectionId &&
                    it.state != SignInState.NotSignedIn
            }
        ) {
            reading = reading.copy(status = UsageReadingStatus.NotSignedIn, windows = emptyList(), problem = null)
        }
        effective += reading
    }

    val groups = LinkedHashMap<String, MutableList<UsageReading>>()
    for (r in effective) {
        groups.getOrPut(usageSubscriptionKey(r.providerId, r.accountRef, r.connectionId)) { mutableListOf() } += r
    }

    val built = mutableListOf<UsageSubscription>()
    val apiKeys = LinkedHashMap<String, ApiKeyAccount>()
    val covered = effective.map { it.cacheKey }.toSet()

    fun build(
        key: String,
        providerId: String,
        displayName: String,
        state: SubscriptionState,
        signedInOn: List<SignedInOn>,
        primary: UsageReading? = null,
        fix: UsageFix = UsageFix.None,
        fixConnection: UsageConnection? = null,
        resetSince: Boolean = false,
    ): UsageSubscription {
        val override = overrides[key]
        val read = wasRead(primary)
        return UsageSubscription(
            key = key,
            providerId = providerId,
            displayName = displayName,
            state = state,
            planLabel = primary?.plan,
            accountRef = primary?.accountRef,
            windows = primary?.let { sortedWindows(it.windows) } ?: emptyList(),
            // A record with no numbers was not read, so it carries no "read" time.
            fetchedAt = if (read) primary!!.fetchedAt else null,
            checkedAt = if (read) primary!!.checkedAt ?: primary.fetchedAt else null,
            readFrom = if (primary != null && primary.hasNumbers) byId[primary.connectionId] else null,
            signedInOn = signedInOn,
            fix = fix,
            fixConnection = fixConnection,
            resetSinceLastReading = resetSince,
            alertThreshold = override?.threshold ?: defaultThreshold,
            hasAlertOverride = override?.threshold != null,
            notifyOnReset = override?.notifyOnReset ?: notifyOnResetDefault,
        )
    }

    fun signInFix(providerId: String) = if (providerId in keyProviders) UsageFix.AddKey else UsageFix.SignIn

    for ((key, list) in groups) {
        // Freshest usable reading wins; otherwise the most recent attempt.
        var found: UsageReading? = null
        for (r in list) {
            val usable = r.hasNumbers && r.problem != UsageProblem.Offline
            if (!usable) continue
            if (found == null || r.fetchedAt > found.fetchedAt) found = r
        }
        val primary = found ?: list.sortedByDescending { it.fetchedAt }.first()
        val threshold = overrides[key]?.threshold ?: defaultThreshold

        val signedIn = mutableListOf<SignedInOn>()
        val ordered = listOf(primary) + list.filter { it !== primary }
        for (r in ordered) {
            val c = byId[r.connectionId] ?: continue
            signedIn +=
                SignedInOn(
                    connection = c,
                    state =
                        when (r.status) {
                            UsageReadingStatus.AuthRequired -> SignInState.Expired
                            UsageReadingStatus.NotSignedIn -> SignInState.NotSignedIn
                            else -> SignInState.SignedIn
                        },
                    method = r.method,
                    readsFromHere = r === primary && r.hasNumbers,
                    online = r.problem != UsageProblem.Offline && r.connectionId !in offlineConnections,
                )
        }

        val resetSince = primary.windows.any { it.resetPassed(now) && primary.fetchedAt < it.resetsAt!! }
        val primaryConnection = byId.getValue(primary.connectionId)
        var fix = UsageFix.None
        var fixConnection: UsageConnection? = null
        val state: SubscriptionState
        when (primary.status) {
            UsageReadingStatus.AuthRequired -> {
                state = SubscriptionState.SignInExpired
                fix = signInFix(primary.providerId)
                fixConnection = primaryConnection
            }
            UsageReadingStatus.NotSignedIn -> {
                state = SubscriptionState.NotSignedIn
                fix = signInFix(primary.providerId)
                fixConnection = primaryConnection
            }
            UsageReadingStatus.NoLimits, UsageReadingStatus.Ok -> {
                if (primary.problem == UsageProblem.Offline) {
                    state = SubscriptionState.OfflineStale
                } else if (primary.problem == UsageProblem.Broken) {
                    state = SubscriptionState.ReaderBroken
                    fix = UsageFix.TryAgain
                    fixConnection = primaryConnection
                } else if (primary.status == UsageReadingStatus.NoLimits || primary.windows.isEmpty()) {
                    state = SubscriptionState.NoLimits
                } else {
                    val used = primary.windows.maxOf { it.usedPercent }
                    state =
                        when {
                            used >= 100 -> SubscriptionState.Exhausted
                            used >= threshold -> SubscriptionState.NearLimit
                            else -> SubscriptionState.Ok
                        }
                }
            }
        }
        built +=
            build(
                key = key,
                providerId = primary.providerId,
                displayName = primary.displayName,
                state = state,
                signedInOn = signedIn,
                primary = primary,
                fix = fix,
                fixConnection = fixConnection,
                resetSince = resetSince,
            )
    }

    // Sign-ins that no reader speaks for.
    for (s in signIns) {
        if (s.state == SignInState.NotSignedIn) continue
        val c = byId[s.connectionId] ?: continue
        if ("${s.connectionId}|${s.providerId}" in covered) continue
        val name = s.displayName.ifEmpty { s.providerId }
        val viaOpenCode = c.kind == UsageConnectionKind.OpenCodeServer || c.kind == UsageConnectionKind.InAppUbuntu
        if (s.method == SignInMethod.ApiKey || (s.providerId == "claude" && viaOpenCode)) {
            apiKeys["${s.providerId}|${s.connectionId}"] =
                ApiKeyAccount(s.providerId, name, c, viaOpenCode = viaOpenCode && s.providerId == "claude")
            continue
        }
        // Providers the app's reader cannot read from an OpenCode sign-in are not plan cards.
        if (viaOpenCode && s.providerId !in usageReadableFromOpenCode) continue
        val expired = s.state == SignInState.Expired
        built +=
            build(
                key = usageSubscriptionKey(s.providerId, null, s.connectionId),
                providerId = s.providerId,
                displayName = name,
                state = if (expired) SubscriptionState.SignInExpired else SubscriptionState.CantReadYet,
                signedInOn =
                    listOf(SignedInOn(c, s.state, s.method, online = c.id !in offlineConnections)),
                fix = if (expired) signInFix(s.providerId) else UsageFix.SetUp,
                fixConnection = c,
            )
    }

    collapseNotSignedIn(built)

    // Order: urgency, then least left, then name.
    built.sortWith(
        compareBy<UsageSubscription> { stateRank.getValue(it.state) }
            .thenBy { it.leastLeftPercent ?: 101.0 }
            .thenBy { it.providerId }
            .thenBy { it.key },
    )

    val counts = built.groupingBy { it.providerId }.eachCount()
    val subscriptions = built.map { if (counts.getValue(it.providerId) > 1) it.copy(showConnectionName = true) else it }
    val seenProviders =
        subscriptions.map { it.providerId }.toSet() +
            apiKeys.values.filter { !it.viaOpenCode }.map { it.providerId }
    return UsageSnapshot(
        subscriptions = subscriptions,
        apiKeyAccounts = apiKeys.values.toList(),
        addable = usagePlanProviders.filter { it !in seenProviders },
        connections = connections.toList(),
        refreshedAt = refreshedAt,
        loaded = loaded,
        offlineConnectionIds = offlineConnections.toSet(),
        answeredConnectionIds =
            authoritativeSignIns + effective.filter { it.problem != UsageProblem.Offline }.map { it.connectionId },
    )
}

private fun wasRead(r: UsageReading?): Boolean = r != null && (r.hasNumbers || r.windows.isNotEmpty())

/**
 * "Not signed in anywhere" is only true when no connection has the provider. A provider with no account identity keeps
 * one card; leftover not-signed-in records fold into the signed-in card (or into one another).
 */
private fun collapseNotSignedIn(built: MutableList<UsageSubscription>) {
    val byProvider = LinkedHashMap<String, MutableList<UsageSubscription>>()
    for (s in built) byProvider.getOrPut(s.providerId) { mutableListOf() } += s
    for (list in byProvider.values) {
        val leftovers = list.filter { it.state == SubscriptionState.NotSignedIn && it.accountRef == null }
        if (leftovers.isEmpty()) continue
        val signed = list.filter { it.state != SubscriptionState.NotSignedIn }
        val keeper: UsageSubscription
        val drop: List<UsageSubscription>
        if (signed.isNotEmpty()) {
            keeper = signed.first()
            drop = leftovers
        } else {
            keeper = leftovers.first()
            drop = leftovers.drop(1)
        }
        if (drop.isEmpty()) continue
        val extra =
            drop.flatMap { it.signedInOn }.filter { on -> keeper.signedInOn.none { it.connection == on.connection } }
        val index = built.indexOfFirst { it === keeper }
        built[index] = keeper.copy(signedInOn = keeper.signedInOn + extra)
        for (d in drop) built.removeAll { it === d }
    }
}

private fun sortedWindows(windows: List<UsageWindow>): List<UsageWindow> = windows.sortedBy { it.kind.ordinal }
