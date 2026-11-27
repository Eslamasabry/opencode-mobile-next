package io.github.eslamasabry.oc.engine.integrations.usage

import io.github.eslamasabry.oc.core.common.dart.DartJson
import kotlin.time.Duration
import kotlin.time.Instant

private const val CONNECTION_ACCOUNT_PREFIX: String = "conn:"
private const val OPAQUE_ACCOUNT_PREFIX: String = "acct:"
private const val OPAQUE_ACCOUNT_DOMAIN: String = "oc.usageHistory.acct.v1"
private const val OPAQUE_ACCOUNT_HEX_LENGTH: Int = 32

internal fun sameResetWindow(
    previous: Instant?,
    current: Instant?,
    resetJitter: Duration,
): Boolean =
    when {
        previous == null && current == null -> true
        previous == null || current == null -> false
        else -> (current - previous).absoluteValue <= resetJitter
    }

internal fun opaqueUsageSubscriptionKey(subscriptionKey: String): String {
    val sep = subscriptionKey.indexOf('|')
    return if (sep <= 0 || sep == subscriptionKey.lastIndex) {
        if (isOpaqueAccountSegment(subscriptionKey)) {
            subscriptionKey
        } else {
            "$OPAQUE_ACCOUNT_PREFIX${hashAccountSegment("", subscriptionKey)}"
        }
    } else {
        val provider = subscriptionKey.substring(0, sep)
        val account = subscriptionKey.substring(sep + 1)
        val safe =
            when {
                isOpaqueAccountSegment(account) -> account
                account.startsWith(CONNECTION_ACCOUNT_PREFIX) && '@' !in account -> account
                else -> "$OPAQUE_ACCOUNT_PREFIX${hashAccountSegment(provider, account)}"
            }
        "$provider|$safe"
    }
}

private fun isOpaqueAccountSegment(segment: String): Boolean =
    segment.length == OPAQUE_ACCOUNT_PREFIX.length + OPAQUE_ACCOUNT_HEX_LENGTH &&
        segment.startsWith(OPAQUE_ACCOUNT_PREFIX) &&
        segment.drop(OPAQUE_ACCOUNT_PREFIX.length).all { it in '0'..'9' || it in 'a'..'f' }

private fun hashAccountSegment(provider: String, account: String): String =
    DartJson.sha256Hex(DartJson.encode(listOf(OPAQUE_ACCOUNT_DOMAIN, provider, account)))
        .take(OPAQUE_ACCOUNT_HEX_LENGTH)

internal fun trimConnectionWindows(
    slot: MutableMap<WindowKey, List<UsageSample>>,
    maxWindows: Int = UsageHistory.MAX_WINDOWS_PER_CONNECTION,
): Boolean {
    if (slot.size <= maxWindows) return false
    val keep =
        slot.entries
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<MutableMap.MutableEntry<WindowKey, List<UsageSample>>>> {
                        it.value.value.lastOrNull()?.fetchedAt ?: Instant.DISTANT_PAST
                    }
                    .thenByDescending { it.index }
            )
            .take(maxWindows)
            .mapTo(HashSet(maxWindows)) { it.value.key }
    return slot.keys.retainAll(keep)
}
