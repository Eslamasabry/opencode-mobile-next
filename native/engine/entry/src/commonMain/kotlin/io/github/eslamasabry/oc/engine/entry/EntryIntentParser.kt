package io.github.eslamasabry.oc.engine.entry

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.time.Instant

/**
 * The frozen extra names and actions of 07 §7.13 (rows received by `.MainActivity` and `.InternalEntryActivity`).
 * Values copied from the Flutter-era Kotlin: `MainActivity.kt:627–629`, `BackgroundConnectionService.kt:820–826`,
 * `SessionWidgetIntents.kt:19`, `ScheduledPromptChannel.kt:79–81`.
 */
public object EntryExtras {
    public const val SHORTCUT: String = "oc.shortcut"
    public const val SHORTCUT_PROFILE: String = "oc.shortcut.profile"
    public const val SHORTCUT_SESSION: String = "oc.shortcut.session"
    public const val ALERT_KIND: String = "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_KIND"
    public const val ALERT_SESSION_ID: String = "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_SESSION_ID"
    public const val ALERT_PROFILE_ID: String = "io.github.eslamasabry.opencode_mobile.extra.CODING_ALERT_PROFILE_ID"
    public const val MONITOR_TOKEN: String = "io.github.eslamasabry.opencode_mobile.MONITOR_TOKEN"
    public const val WIDGET_REPLY: String = "oc.widgetReply"
    public const val SCHEDULED_PROFILE: String = "scheduledProfileId"
    public const val SCHEDULED_PROMPT: String = "scheduledPromptId"
    public const val SCHEDULED_SESSION: String = "scheduledSessionId"
    public const val TEXT: String = "android.intent.extra.TEXT"
    public const val SUBJECT: String = "android.intent.extra.SUBJECT"
    public const val COMPONENT_NAME: String = "android.intent.extra.COMPONENT_NAME"

    public const val ACTION_MAIN: String = "android.intent.action.MAIN"
    public const val ACTION_VIEW: String = "android.intent.action.VIEW"
    public const val ACTION_SEND: String = "android.intent.action.SEND"
    public const val ACTION_TILE_PREFERENCES: String = "android.service.quicksettings.action.QS_TILE_PREFERENCES"

    public const val APP_PACKAGE: String = "io.github.eslamasabry.opencode_mobile"
    public const val ATTENTION_TILE: String = "$APP_PACKAGE.AttentionTileService"
    public const val PAUSE_TILE: String = "$APP_PACKAGE.PauseTileService"

    /** Every string extra the reader removes after reading (rejected values are consumed too, 07 §7.1). */
    public val owned: List<String> =
        listOf(
            SHORTCUT, SHORTCUT_PROFILE, SHORTCUT_SESSION, ALERT_KIND, ALERT_SESSION_ID, ALERT_PROFILE_ID, MONITOR_TOKEN,
            WIDGET_REPLY, SCHEDULED_PROFILE, SCHEDULED_PROMPT, SCHEDULED_SESSION, TEXT, SUBJECT, COMPONENT_NAME,
        )
}

/**
 * What the Android reader copies out of an `Intent` before stripping it: plain values only, so the whitelist and every
 * bound is common code and table-tested on the desktop. [strings] holds only [EntryExtras.owned] string extras;
 * [component] is `Intent.EXTRA_COMPONENT_NAME` as (package, class).
 */
public data class IntentFields(
    val action: String? = null,
    val type: String? = null,
    val data: String? = null,
    val strings: Map<String, String?> = emptyMap(),
    val widgetReply: Boolean = false,
    val component: Pair<String, String>? = null,
) {
    override fun toString(): String = "IntentFields(action=$action, type=$type, extras=${strings.keys})" // no values
}

/**
 * 07 §7.1 `EntryIntentReader.read`, platform-neutral: whitelists and bounds every field and returns the requests the
 * intent carries (zero or more; each channel at most once). Anything that fails a check is dropped, never thrown, and
 * never logged with its value. [origin] is [EntryOrigin.Internal] only for the non-exported `.InternalEntryActivity`
 * (Astra M2); links, shares and tile long-presses are untrusted wherever they arrive.
 */
public object EntryIntentParser {
    public fun parse(fields: IntentFields, origin: EntryOrigin, now: Instant): List<EntryRequest> =
        listOfNotNull(
            share(fields, now),
            link(fields, now),
            tile(fields, now),
            launch(fields, origin, now),
            pinned(fields, origin, now),
            alert(fields, origin, now),
            scheduled(fields, origin, now),
        )

    private fun string(fields: IntentFields, key: String): String = fields.strings[key]?.trim().orEmpty()

    /** `SEND text/…`: subject joined as the first line unless the body already starts with it; ≤ 8,000 chars. */
    internal fun share(fields: IntentFields, now: Instant): EntryRequest.SharedText? {
        if (fields.action != EntryExtras.ACTION_SEND || fields.type?.startsWith("text/") != true) return null
        val body = string(fields, EntryExtras.TEXT)
        val subject = string(fields, EntryExtras.SUBJECT)
        val text =
            when {
                subject.isEmpty() -> body
                body.isEmpty() -> subject
                body.startsWith(subject) -> body
                else -> "$subject\n$body"
            }.trim().take(EntryLimits.SHARED_TEXT_MAX)
        return if (text.isEmpty()) null else EntryRequest.SharedText(text, now)
    }

    /** `VIEW` with scheme `opencode-mobile` and host `session` or `team`, bounded by `SessionLinkIngress`. */
    internal fun link(fields: IntentFields, now: Instant): EntryRequest.Link? {
        if (fields.action != EntryExtras.ACTION_VIEW) return null
        val data = fields.data ?: return null
        val schemeEnd = data.indexOf("://")
        if (schemeEnd <= 0 || !data.substring(0, schemeEnd).equals("opencode-mobile", ignoreCase = true)) return null
        val host = data.substring(schemeEnd + 3).takeWhile { it != '/' && it != '?' && it != '#' }
        if (!host.equals("session", ignoreCase = true) && !host.equals("team", ignoreCase = true)) return null
        return if (EntryValidators.ingressAccepts(data)) EntryRequest.Link(data, now) else null
    }

    internal fun tile(fields: IntentFields, now: Instant): EntryRequest.TilePreferences? {
        if (fields.action != EntryExtras.ACTION_TILE_PREFERENCES) return null
        val (pkg, cls) = fields.component ?: return null
        if (pkg != EntryExtras.APP_PACKAGE) return null
        val tile =
            when (cls) {
                EntryExtras.ATTENTION_TILE -> TileId.Attention
                EntryExtras.PAUSE_TILE -> TileId.Pause
                else -> return null
            }
        return EntryRequest.TilePreferences(tile, now)
    }

    internal fun launch(fields: IntentFields, origin: EntryOrigin, now: Instant): EntryRequest.Launch? {
        val value = fields.strings[EntryExtras.SHORTCUT] ?: return null
        val action = EntryValidators.launchAction(value) ?: return null
        return EntryRequest.Launch(action, now, origin)
    }

    internal fun pinned(fields: IntentFields, origin: EntryOrigin, now: Instant): EntryRequest.PinnedSession? {
        val session = string(fields, EntryExtras.SHORTCUT_SESSION)
        val profile = string(fields, EntryExtras.SHORTCUT_PROFILE)
        if (!EntryValidators.isSafeIdentifier(session) || !EntryValidators.isSafeIdentifier(profile)) return null
        return EntryRequest.PinnedSession(ProfileId(profile), SessionId(session), now, origin)
    }

    /** Kind and session required; a present `monitorToken` that is not `^[a-f0-9]{64}$` voids the whole alert. */
    internal fun alert(fields: IntentFields, origin: EntryOrigin, now: Instant): EntryRequest.AlertOpen? {
        val kind = AlertKind.fromWire(string(fields, EntryExtras.ALERT_KIND)) ?: return null
        val session = string(fields, EntryExtras.ALERT_SESSION_ID)
        if (!EntryValidators.isSafeIdentifier(session)) return null
        val profile = string(fields, EntryExtras.ALERT_PROFILE_ID)
        if (profile.isNotEmpty() && !EntryValidators.isSafeIdentifier(profile)) return null
        val token = string(fields, EntryExtras.MONITOR_TOKEN)
        if (token.isNotEmpty() && !EntryValidators.isMonitorToken(token)) return null
        return EntryRequest.AlertOpen(
            kind = kind,
            profileId = profile.takeIf { it.isNotEmpty() }?.let(::ProfileId),
            sessionId = SessionId(session),
            monitorToken = token.takeIf { it.isNotEmpty() }?.let(::MonitorToken),
            reply = fields.widgetReply,
            receivedAt = now,
            origin = origin,
        )
    }

    internal fun scheduled(fields: IntentFields, origin: EntryOrigin, now: Instant): EntryRequest.ScheduledOpen? {
        val profile = string(fields, EntryExtras.SCHEDULED_PROFILE)
        val schedule = string(fields, EntryExtras.SCHEDULED_PROMPT)
        val session = string(fields, EntryExtras.SCHEDULED_SESSION)
        if (!EntryValidators.isScheduleId(profile) || !EntryValidators.isScheduleId(schedule)) return null
        if (session.isNotEmpty() && !EntryValidators.isSafeIdentifier(session)) return null
        return EntryRequest.ScheduledOpen(
            ProfileId(profile),
            schedule,
            session.takeIf { it.isNotEmpty() }?.let(::SessionId),
            now,
            origin,
        )
    }
}
