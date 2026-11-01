package io.github.eslamasabry.oc.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager

/** Which build features a channel belongs to; `ensure` creates only the channels of features this build ships. */
public enum class ChannelFeature {
    /** Shipped by every build at the switch. */
    Core,

    /** AI Team progress (D6). Never created before D6; an existing one is kept, never deleted. */
    TeamProgress,

    /** QA builds only; never created in `stable` or `preview` release builds. */
    QaProducer,
}

/**
 * The notification channels (plan 11 §10.1; ids frozen by plan 05 §7.9). The person's per-channel choices (sound,
 * importance, lock-screen visibility, off) survive the Flutter → native update **only** on identical ids, so the ids
 * and importances below are copied verbatim from the Flutter app and are never changed, deleted or recreated.
 */
public enum class NotifyChannel(
    public val id: String,
    public val importance: Int,
    public val nameRes: Int,
    public val descriptionRes: Int?,
    public val showBadge: Boolean,
    public val feature: ChannelFeature,
    /** Coding channels hide their words on the lock screen; the alerts carry a count-only public version. */
    public val privateOnLockScreen: Boolean,
) {
    LiveConnection(
        "opencode_live_connection", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_live_channel, R.string.notify_live_description, false, ChannelFeature.Core, false,
    ),
    CodingAction(
        "opencode_coding_action", NotificationManager.IMPORTANCE_HIGH,
        R.string.notify_coding_requests_channel, R.string.notify_coding_requests_description, true, ChannelFeature.Core, true,
    ),
    CodingActionQuiet(
        "opencode_coding_action_quiet", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_coding_requests_quiet_channel, R.string.notify_coding_requests_quiet_description, true,
        ChannelFeature.Core, true,
    ),
    CodingStatus(
        "opencode_coding_status", NotificationManager.IMPORTANCE_DEFAULT,
        R.string.notify_coding_status_channel, R.string.notify_coding_status_description, true, ChannelFeature.Core, true,
    ),
    TeamProgress(
        "opencode_team_progress", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_team_progress_channel, R.string.notify_team_progress_description, false,
        ChannelFeature.TeamProgress, true,
    ),
    PhoneSetup(
        "opencode_phone_setup", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_setup_channel, null, false, ChannelFeature.Core, false,
    ),
    BuiltinServer(
        "opencode_builtin_server", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_phone_server_channel, R.string.notify_phone_server_description, false, ChannelFeature.Core, false,
    ),
    ScheduledPrompts(
        "opencode_scheduled_prompts", NotificationManager.IMPORTANCE_DEFAULT,
        R.string.notify_schedule_channel, null, true, ChannelFeature.Core, false,
    ),
    VoiceSetup(
        "opencode_voice_setup", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_voice_setup, null, true, ChannelFeature.Core, false,
    ),

    /** QA builds only. Its name is not translated (QA tooling); it reuses the setup name. */
    QaProducer(
        "bb9_qa_producer", NotificationManager.IMPORTANCE_LOW,
        R.string.notify_setup_channel, null, false, ChannelFeature.QaProducer, false,
    ),
    ;

    public companion object {
        public fun byId(id: String): NotifyChannel? = entries.firstOrNull { it.id == id }
    }
}

/** What `ensure` did: the ids it created, and the ids that already existed (left untouched). */
public data class ChannelsReport(val created: List<String>, val kept: List<String>)

/**
 * Creates missing channels and renames existing ones in place (plan 11 §10.1, §10.7; first-launch hook H3, 05 §7.9).
 *
 * - [ensure] creates only ids that do not exist yet, for the features this build ships. An existing channel is never
 *   re-created, so its importance and the person's choices stay exactly as they are.
 * - [relocalize] rewrites name and description of the existing channels through the same channel object, which keeps
 *   every user choice (port of `NativeNotificationLocale.refresh`).
 */
public class ChannelRegistry(private val manager: NotificationManager, private val words: NotifyWords) {
    public fun ensure(ships: Set<ChannelFeature> = setOf(ChannelFeature.Core)): ChannelsReport {
        val created = mutableListOf<String>()
        val kept = mutableListOf<String>()
        val toCreate = mutableListOf<NotificationChannel>()
        for (spec in NotifyChannel.entries) {
            if (manager.getNotificationChannel(spec.id) != null) {
                kept += spec.id
                continue
            }
            if (spec.feature !in ships) continue
            toCreate += build(spec)
            created += spec.id
        }
        if (toCreate.isNotEmpty()) manager.createNotificationChannels(toCreate)
        return ChannelsReport(created, kept)
    }

    /** Renames every existing channel of ours in the current app language; returns how many were renamed. */
    public fun relocalize(): Int {
        var renamed = 0
        for (spec in NotifyChannel.entries) {
            if (spec == NotifyChannel.QaProducer) continue
            val channel = manager.getNotificationChannel(spec.id) ?: continue
            channel.name = words.text(spec.nameRes)
            spec.descriptionRes?.let { channel.description = words.text(it) }
            // Reusing the existing object keeps importance, sound and the person's other choices.
            manager.createNotificationChannel(channel)
            renamed++
        }
        return renamed
    }

    /** True when the person turned the channel off (or it does not exist yet and so cannot be posted to). */
    public fun isOff(channel: NotifyChannel): Boolean =
        manager.getNotificationChannel(channel.id)?.importance?.let { it == NotificationManager.IMPORTANCE_NONE } ?: true

    private fun build(spec: NotifyChannel): NotificationChannel =
        NotificationChannel(spec.id, words.text(spec.nameRes), spec.importance).apply {
            spec.descriptionRes?.let { description = words.text(it) }
            setShowBadge(spec.showBadge)
            if (spec.privateOnLockScreen) lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
}
