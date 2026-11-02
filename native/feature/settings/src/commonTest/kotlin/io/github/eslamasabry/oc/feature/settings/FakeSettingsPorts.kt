package io.github.eslamasabry.oc.feature.settings

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.analytics.AnalyticsSink
import io.github.eslamasabry.oc.engine.analytics.ConsentState
import io.github.eslamasabry.oc.engine.analytics.DetailedConsent
import io.github.eslamasabry.oc.engine.analytics.NoOpSink
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollect
import io.github.eslamasabry.oc.engine.analytics.WhatWeCollectSource
import io.github.eslamasabry.oc.feature.settings.presentation.AnalyticsChoices
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationChange
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationFacts
import io.github.eslamasabry.oc.feature.settings.presentation.NotificationSettings
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChange
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalChoices
import io.github.eslamasabry.oc.feature.settings.presentation.PersonalPrefs
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOpeners
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateFacts
import io.github.eslamasabry.oc.feature.settings.presentation.UpdateSettings
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceFacts
import io.github.eslamasabry.oc.feature.settings.presentation.VoiceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val android: PlatformCapabilities = PlatformCapabilities.android()

val desktop: PlatformCapabilities =
    PlatformCapabilities.android().copy(
        builtinLinux = false, termuxMigration = false, voice = false, readAloud = false, backgroundService = false,
        notifications = false, homeWidgets = false, launchShortcuts = false, quickSettingsTiles = false,
        inAppUpdater = false, desktopReleaseCheck = false,
    )

fun phoneFacts(server: ServerLine? = ServerLine("laptop", onPhone = false)): SettingsFacts =
    SettingsFacts(platform = android, server = server, dynamicColour = true)

/** Acknowledged values only: a refused write leaves [value] as it was (the engine's contract, B-STATE-005). */
class FakeChoices(initial: PersonalPrefs = PersonalPrefs()) : PersonalChoices {
    private val flow = MutableStateFlow(initial)
    override val value: StateFlow<PersonalPrefs> = flow
    val writes = mutableListOf<PersonalChange>()
    var refuseNext = false

    override suspend fun set(change: PersonalChange): Outcome<PersonalPrefs> {
        writes += change
        if (refuseNext) {
            refuseNext = false
            return Outcome.Failed(Failure.StorageRefused())
        }
        val v = flow.value
        flow.value =
            when (change) {
                is PersonalChange.Mode -> v.copy(mode = change.mode)
                is PersonalChange.Pack -> v.copy(pack = change.pack)
                is PersonalChange.Style -> v.copy(style = change.style, fineTuned = false)
                is PersonalChange.Motion -> v.copy(motion = change.motion)
                is PersonalChange.Reasoning -> v.copy(reasoning = change.on)
                is PersonalChange.Timestamps -> v.copy(timestamps = change.on)
                is PersonalChange.TextSize -> v.copy(textSize = change.size)
                is PersonalChange.Language -> v.copy(language = change.language)
            }
        return Outcome.Ok(flow.value)
    }
}

class FakeAnalytics(sink: AnalyticsSink = NoOpSink) : AnalyticsChoices {
    private val theSink = sink
    private val flow = MutableStateFlow(WhatWeCollectSource.build(ConsentState(), sink))
    override val whatWeCollect: StateFlow<WhatWeCollect> = flow
    val asked = mutableListOf<Boolean>()
    var refuseNext = false

    override suspend fun setDetailedConsent(granted: Boolean): Outcome<Unit> {
        asked += granted
        if (refuseNext) {
            refuseNext = false
            return Outcome.Failed(Failure.StorageRefused())
        }
        val consent = ConsentState(if (granted) DetailedConsent.Granted else DetailedConsent.Declined)
        flow.value = WhatWeCollectSource.build(consent, theSink)
        return Outcome.Ok(Unit)
    }
}

class FakeVoice(initial: VoiceFacts = VoiceFacts()) : VoiceSettings {
    val flow = MutableStateFlow(initial)
    override val facts: StateFlow<VoiceFacts> = flow

    override suspend fun setReadAloud(on: Boolean): Outcome<Unit> {
        flow.value = flow.value.copy(readAloudOn = on)
        return Outcome.Ok(Unit)
    }
}

class FakeNotifications(initial: NotificationFacts = NotificationFacts()) : NotificationSettings {
    val flow = MutableStateFlow(initial)
    override val facts: StateFlow<NotificationFacts> = flow
    var tests = 0
    var refuseTest = false

    override suspend fun set(change: NotificationChange): Outcome<Unit> {
        val v = flow.value
        flow.value =
            when (change) {
                is NotificationChange.FinishedWork -> v.copy(finishedWork = change.on)
                is NotificationChange.Questions -> v.copy(questions = change.on)
                is NotificationChange.WifiOnly -> v.copy(wifiOnly = change.on)
            }
        return Outcome.Ok(Unit)
    }

    override suspend fun sendTest(): Outcome<Unit> {
        tests++
        return if (refuseTest) Outcome.Failed(Failure.Device()) else Outcome.Ok(Unit)
    }
}

class FakeUpdates(initial: UpdateFacts) : UpdateSettings {
    val flow = MutableStateFlow(initial)
    override val facts: StateFlow<UpdateFacts> = flow
    val calls = mutableListOf<String>()

    override suspend fun checkNow(): Outcome<Unit> {
        calls += "check"
        return Outcome.Ok(Unit)
    }

    override suspend fun setChecks(on: Boolean): Outcome<Unit> {
        calls += "checks:$on"
        flow.value = flow.value.copy(checksOn = on)
        return Outcome.Ok(Unit)
    }

    override suspend fun act(): Outcome<Unit> {
        calls += "act"
        return Outcome.Ok(Unit)
    }
}

class RecordingOpeners : SettingsOpeners {
    val opened = mutableListOf<SettingsOutside>()

    override fun open(place: SettingsOutside) {
        opened += place
    }
}
