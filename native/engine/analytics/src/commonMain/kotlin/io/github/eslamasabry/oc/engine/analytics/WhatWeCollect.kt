package io.github.eslamasabry.oc.engine.analytics

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/*
 * The data source for the future "What we collect" screen (plan 16 §5.8: Settings › This app › Privacy). Keys and
 * values only; the words live in `:ui:words`. Built from the schema registry, so the screen cannot drift from what
 * the code can send.
 */

/** Things the app never collects, whatever the tier (plan 16 §5.4). */
public enum class NeverCollected {
    ChatContent, FileNamesAndPaths, ServerAddresses, ServerNames, ProfileIds, ConversationIds, ModelOrProviderNames,
    ErrorText, CrashStacks, Credentials, DeviceModel, ScreenSize, TimeZone, Location, AdvertisingId, AndroidId,
    ContactsOrAccounts,
}

/** Facts every event carries besides its own properties. */
public enum class EnvelopeField {
    AppVersion, AppBuild, Channel, OsFamily, OsMajor, DeviceClass, Language, IsDebug, HourUtc, InstallId, SessionId,
}

/** Whether anything leaves the phone in this build. */
public enum class SendingState {
    /** `sender=none`: nothing is sent (the default until the owner names a destination). */
    NotSentInThisVersion,

    /** Debug or `qa` build: batches are written on the phone only. */
    LocalDebugOnly,

    /** A remote sink is bound. */
    Sending,
}

public data class CollectedProp(val key: String, val kind: PropKind, val values: ImmutableList<String>)

public data class CollectedEvent(
    val name: String,
    val tier: AnalyticsTier,
    val trigger: EventTrigger,
    val props: ImmutableList<CollectedProp>,
    /** False for DETAILED events while the person has not opted in. */
    val active: Boolean,
)

public data class WhatWeCollect(
    val sending: SendingState,
    val destinationHost: String?,
    val detailed: DetailedConsent,
    /** Hidden on the screen when nothing is sent: there is nothing to opt into (plan 16 §5.8). */
    val detailedSwitchShown: Boolean,
    val basicEvents: ImmutableList<CollectedEvent>,
    val detailedEvents: ImmutableList<CollectedEvent>,
    val envelope: ImmutableList<EnvelopeField>,
    val never: ImmutableList<NeverCollected>,
    val installIdRotationDays: Int,
    val sessionIdleMinutes: Int,
    val queued: Int,
)

public object WhatWeCollectSource {
    public fun build(consent: ConsentState, sink: AnalyticsSink, queued: Int = 0): WhatWeCollect {
        val sending =
            when (sink.kind) {
                SinkKind.None -> SendingState.NotSentInThisVersion
                SinkKind.LocalDebug -> SendingState.LocalDebugOnly
                SinkKind.Remote -> SendingState.Sending
            }

        fun events(tier: AnalyticsTier) =
            AnalyticsSchema.tier(tier).map { spec ->
                CollectedEvent(
                    name = spec.name,
                    tier = spec.tier,
                    trigger = spec.trigger,
                    props = spec.props.map { CollectedProp(it.key, it.kind, it.allowed.toImmutableList()) }.toImmutableList(),
                    active = consent.permits(spec.tier),
                )
            }.toImmutableList()

        return WhatWeCollect(
            sending = sending,
            destinationHost = sink.host.takeIf { sink.kind == SinkKind.Remote },
            detailed = consent.detailed,
            detailedSwitchShown = sending != SendingState.NotSentInThisVersion,
            basicEvents = events(AnalyticsTier.Basic),
            detailedEvents = events(AnalyticsTier.Detailed),
            envelope = EnvelopeField.entries.toImmutableList(),
            never = NeverCollected.entries.toImmutableList(),
            installIdRotationDays = InstallIdPolicy.ROTATION_DAYS,
            sessionIdleMinutes = (SessionTracker.SESSION_IDLE_MS / 60_000L).toInt(),
            queued = queued,
        )
    }
}
