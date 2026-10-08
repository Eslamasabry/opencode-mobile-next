package io.github.eslamasabry.opencode_mobile

import android.app.Notification
import android.app.NotificationChannel
import android.content.Context

private const val PHONE_ID = 4097

fun main(args: Array<String>) {
    when (args.single()) {
        "phone-fallback" -> phoneFallback()
        "supplied-title" -> suppliedTitle()
        "channel-policy" -> channelPolicy()
        "empty-surfaces" -> emptySurfaces()
        else -> error("unknown scenario")
    }
    println("PASS ${args.single()}")
}

private fun phone(context: Context, title: String = "OpenCode is running on this phone"): Notification {
    val notification = Notification().apply {
        extras.putCharSequence(Notification.EXTRA_TITLE, title)
        extras.putCharSequence(Notification.EXTRA_TEXT, "Your agent keeps working while you use other apps.")
        extras.putCharSequence("route", "unchanged route")
        actions = arrayOf(Notification.Action("Stop", Any()))
    }
    context.notifications.posted[PHONE_ID] = notification
    return notification
}

private fun phoneFallback() {
    val context = Context()
    val original = phone(context)
    val stopToken = original.actions!!.single().intentToken
    context.preferences.choice = "ar"
    NativeNotificationLocale.refresh(context)
    val arabic = context.notifications.posted.getValue(PHONE_ID)
    check(arabic.extras.getCharSequence(Notification.EXTRA_TITLE) == "OpenCode يعمل على هذا الهاتف")
    check(arabic.extras.getCharSequence(Notification.EXTRA_TEXT) == "يواصل وكيلك العمل بينما تستخدم تطبيقات أخرى.")
    check(arabic.actions!!.single().title == "إيقاف")
    check(arabic.actions!!.single().intentToken === stopToken)
    check(arabic.extras.getCharSequence("route") == "unchanged route")
    check(arabic.ongoing && arabic.channelId == original.channelId && arabic.visibility == original.visibility)

    context.preferences.choice = "en"
    NativeNotificationLocale.refresh(context)
    val english = context.notifications.posted.getValue(PHONE_ID)
    check(english.extras.getCharSequence(Notification.EXTRA_TITLE) == "OpenCode is running on this phone")
    check(english.extras.getCharSequence(Notification.EXTRA_TEXT) == "Your agent keeps working while you use other apps.")
    check(english.actions!!.single().title == "Stop")
    check(english.actions!!.single().intentToken === stopToken)
    check(BackgroundConnectionService.refreshPayloads == listOf("unchanged session counts", "unchanged session counts"))
    check(SessionsWidgetProvider.refreshes == 2)
    check(context.serviceStarts == 0)
}

private fun suppliedTitle() {
    val context = Context()
    val original = phone(context, "Authored server task")
    val unrelated = Notification.Action("Authored task action", Any())
    original.actions = original.actions!! + unrelated
    context.preferences.choice = "ar"
    NativeNotificationLocale.refresh(context)
    val refreshed = context.notifications.posted.getValue(PHONE_ID)
    check(refreshed.extras.getCharSequence(Notification.EXTRA_TITLE) == "Authored server task")
    check(refreshed.extras.getCharSequence(Notification.EXTRA_TEXT) == "يواصل وكيلك العمل بينما تستخدم تطبيقات أخرى.")
    check(refreshed.actions!![0].title == "إيقاف")
    check(refreshed.actions!![1].title == unrelated.title)
    check(refreshed.actions!![1].intentToken === unrelated.intentToken)
}

private fun channelPolicy() {
    val context = Context()
    val ids = listOf(
        "opencode_live_connection", "opencode_coding_action", "opencode_coding_status",
        "opencode_team_progress", "opencode_builtin_server", "opencode_voice_setup",
    )
    ids.forEachIndexed { index, id ->
        context.notifications.channels[id] = NotificationChannel(id, "old English copy", index).apply {
            description = "old English description"
            sound = if (index % 2 == 0) null else "user selected sound"
        }
    }
    val originals = context.notifications.channels.toMap()
    val policy = originals.mapValues { (_, channel) -> channel.importance to channel.sound }
    context.preferences.choice = "ar"
    NativeNotificationLocale.refresh(context)
    check(context.notifications.channels.keys.toList() == ids)
    for ((id, channel) in context.notifications.channels) {
        check(channel === originals[id])
        check(channel.importance to channel.sound == policy[id])
        check(channel.name != "old English copy")
        if (id != "opencode_voice_setup") check(channel.description != "old English description")
    }
    check(context.notifications.channels.getValue("opencode_builtin_server").name == "OpenCode على هذا الهاتف")
    check(context.notifications.channels.getValue("opencode_voice_setup").name == "إعداد الصوت")
    check(context.notifications.channels.getValue("opencode_voice_setup").description == "old English description")
    check(context.notifications.posted.isEmpty() && context.serviceStarts == 0)
}

private fun emptySurfaces() {
    val context = Context().apply { preferences.choice = "ar" }
    NativeNotificationLocale.refresh(context)
    check(context.notifications.channels.isEmpty())
    check(context.notifications.posted.isEmpty())
    check(context.notifications.updates.isEmpty())
    check(context.serviceStarts == 0)
    check(BackgroundConnectionService.refreshPayloads == listOf("unchanged session counts"))
    check(SessionsWidgetProvider.refreshes == 1)
}
