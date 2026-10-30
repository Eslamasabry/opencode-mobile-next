package io.github.eslamasabry.opencode_mobile

/** Validated routing and presentation inputs for a coding notification. */
internal data class CodingAlertRequest(
    val kind: String,
    val sessionID: String,
    val key: String,
    val requestID: String = "",
    val profileID: String = "",
    val allowActions: Boolean = true,
    val monitorToken: String = "",
    val subtext: String = "",
    val title: String = "",
    val text: String = "",
    val agentName: String = "",
    val chatTitle: String = "",
    val detail: String = "",
    val reason: String = "",
    val options: List<String> = emptyList(),
    val silent: Boolean = false,
    /** What a permission asks to do: run, edit, read, folder, or empty. */
    val askAction: String = "",
    /** The short thing it acts on ("npm install", "settings.dart"), masked by Dart. */
    val askTarget: String = ""
)
