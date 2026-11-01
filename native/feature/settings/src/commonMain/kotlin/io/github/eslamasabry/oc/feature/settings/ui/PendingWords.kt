package io.github.eslamasabry.oc.feature.settings.ui

import io.github.eslamasabry.oc.engine.analytics.EnvelopeField
import io.github.eslamasabry.oc.engine.analytics.NeverCollected

/**
 * TODO(words): copy that `:ui:words` does not have yet (plan 18 §6 puts it in `strings_privacy.xml`, 7 languages; this
 * unit may not edit `:ui:words`). English only, taken from plan 16 §5.8 and plan 18 §2 where they give the words. The
 * l10n owner moves every line here into `:ui:words` and this file is deleted; the report lists them (§6). Nothing else
 * in this module holds copy.
 */
internal object PendingWords {
    const val WHAT_WE_COLLECT = "What we collect"
    const val NOT_SENT_YET = "Not sent yet. Basic reports wait on this phone, at most 90 days, until a service is chosen."
    const val LOCAL_ONLY = "This is a test build: reports are written on this phone only."
    const val SENT_TO = "Sent to %1\$s"
    const val DETAILED_TITLE = "Share detailed usage"
    const val DETAILED_BODY =
        "Also send which screens and features you use, and how fast the app starts. Helps decide what to improve."
    const val BASIC_LABEL = "Always sent"
    const val BASIC_NOTE =
        "Always on. Sends the app version, Android version and whether updates and connections worked. " +
            "Never your chats, files, server addresses or passwords."
    const val DETAILED_LABEL = "Only if you share detailed usage"
    const val ENVELOPE_LABEL = "Sent with every report"
    const val NEVER_LABEL = "Never collected"
    const val IDS_NOTE = "The random id changes every %1\$d days. A visit ends after %2\$d minutes without use."
    const val TEST_NOT_SENT = "The test notification wasn't sent. Check that Android lets this app notify you."
    const val MATERIAL_YOU = "Material You"

    /** One plain line per event the schema can send (B-ANLY-011: an event without words fails the test). */
    val events: Map<String, String> =
        mapOf(
            "app_start" to "The app opened, and how fast",
            "app_updated" to "The app was updated, and from which version",
            "migration_done" to "Moving your data from the old app finished, and how it went",
            "backend_connected" to "A kind of server connected, and whether it is on this phone, your network or the internet",
            "update_result" to "An in-app update did not finish, and why",
            "crash_seen" to "The app crashed (only the kind of crash)",
            "freeze_seen" to "The app froze, and for how long",
            "error_seen" to "An error was shown (only its kind)",
            "daily_use" to "How often the app was used today, in ranges",
            "server_kinds" to "How many servers of each kind you have, in ranges",
            "agent_kinds" to "How many agents of each kind you have, in ranges",
            "screen_open" to "Which screen opened",
            "feature_used" to "Which feature was used",
            "flow_step" to "How far a setup step got",
            "action_timed" to "How long an action took, in ranges",
            "setting_changed" to "Which setting changed, never its value",
        )

    fun envelope(field: EnvelopeField): String =
        when (field) {
            EnvelopeField.AppVersion -> "App version"
            EnvelopeField.AppBuild -> "Build number"
            EnvelopeField.Channel -> "Release channel"
            EnvelopeField.OsFamily -> "Operating system"
            EnvelopeField.OsMajor -> "Its main version"
            EnvelopeField.DeviceClass -> "Phone, tablet or desktop"
            EnvelopeField.Language -> "App language"
            EnvelopeField.IsDebug -> "Whether it is a test build"
            EnvelopeField.HourUtc -> "The hour it happened"
            EnvelopeField.InstallId -> "A random id that changes every month"
            EnvelopeField.SessionId -> "A random id for this visit"
        }

    fun never(item: NeverCollected): String =
        when (item) {
            NeverCollected.ChatContent -> "What you type or read"
            NeverCollected.FileNamesAndPaths -> "File names and paths"
            NeverCollected.ServerAddresses -> "Server addresses"
            NeverCollected.ServerNames -> "Server names"
            NeverCollected.ProfileIds -> "Saved server ids"
            NeverCollected.ConversationIds -> "Conversation ids"
            NeverCollected.ModelOrProviderNames -> "Model and provider names"
            NeverCollected.ErrorText -> "Error text"
            NeverCollected.CrashStacks -> "Crash details"
            NeverCollected.Credentials -> "Passwords, keys and tokens"
            NeverCollected.DeviceModel -> "Your phone's model"
            NeverCollected.ScreenSize -> "Screen size"
            NeverCollected.TimeZone -> "Time zone"
            NeverCollected.Location -> "Location"
            NeverCollected.AdvertisingId -> "Advertising id"
            NeverCollected.AndroidId -> "Android id"
            NeverCollected.ContactsOrAccounts -> "Contacts and accounts"
        }
}
