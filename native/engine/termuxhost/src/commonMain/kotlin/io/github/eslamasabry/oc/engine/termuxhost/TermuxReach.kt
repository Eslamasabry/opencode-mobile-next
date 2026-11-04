package io.github.eslamasabry.oc.engine.termuxhost

/**
 * Port of lib/termux/termux_reach.dart, lib/ui/widgets/termux_problem_fix.dart's words and
 * lib/state/termux_running_server.dart's detection (B-LINUX-229, B-LINUX-228): one typed cause per failure, read
 * from what Android and Termux answered, with plain words and one named fix.
 */
public enum class TermuxProblem {
    NotInstalled,
    Outdated,
    AccessNeeded,
    AccessBlocked,
    OtherAppsOff,
    Asleep,
    NotAnswering,
    Unknown,
    ;

    /** Whether a plain "Try again" can help: for every other cause retrying changes nothing until the fix. */
    public val retryHelps: Boolean get() = this == Unknown

    /** Whether the cause keeps the app from running anything in Termux. */
    public val blocksTermux: Boolean get() = this != NotAnswering

    public companion object {
        /** The cause behind a failed Termux command, from the bridge's code and Termux's own (never shown) message. */
        public fun ofError(error: TermuxBridgeError): TermuxProblem {
            if ("allow-external-apps" in error.message.lowercase()) return OtherAppsOff
            return when (error.code) {
                "permission_denied" -> AccessNeeded
                "service_unavailable" -> Outdated
                "termux_missing" -> NotInstalled
                "command_timeout", "missing_result" -> Asleep
                else -> Unknown
            }
        }

        /** The cause the capabilities alone show, or null when commands may run. */
        public fun ofCapabilities(capabilities: TermuxCapabilities, accessBlocked: Boolean): TermuxProblem? =
            when {
                !capabilities.platformSupported -> null
                !capabilities.installed -> NotInstalled
                !capabilities.serviceAvailable || !capabilities.protocolSupported -> Outdated
                !capabilities.permissionGranted -> if (accessBlocked) AccessBlocked else AccessNeeded
                else -> null
            }
    }
}

public enum class TermuxAccessAnswer { Granted, Denied, PermanentlyDenied, Missing }

/** Asking Android for Termux access, remembering (for this run of the app) that Android stopped asking. */
public class TermuxAccess(private val bridge: TermuxBridge) {
    /** Android answered "permanently denied" in this run of the app. */
    public var blocked: Boolean = false
        private set

    public suspend fun request(): TermuxAccessAnswer {
        if (!bridge.supported) return TermuxAccessAnswer.Missing
        val answer =
            when (val reply = bridge.requestRunCommandAccess()) {
                is ChannelReply.Value ->
                    when (reply.value) {
                        "granted" -> TermuxAccessAnswer.Granted
                        "permanentlyDenied" -> TermuxAccessAnswer.PermanentlyDenied
                        "missing" -> TermuxAccessAnswer.Missing
                        else -> TermuxAccessAnswer.Denied
                    }
                // An older runner: the yes/no request.
                ChannelReply.MissingHandler ->
                    if (bridge.requestPermission()) TermuxAccessAnswer.Granted else TermuxAccessAnswer.Denied
                is ChannelReply.PlatformError -> TermuxAccessAnswer.Denied
            }
        blocked = answer == TermuxAccessAnswer.PermanentlyDenied
        return answer
    }
}

/** A cause in words: one plain [line] and the one [action] that fixes it. */
public data class TermuxProblemWords(val line: String, val action: String)

public enum class TermuxWordsLocale { English, Arabic }

/** The copy of lib/l10n/app_en.arb / app_ar.arb for each cause (termuxProblem* and termuxFix*). */
public object TermuxProblemCopy {
    public fun words(
        problem: TermuxProblem,
        heard: Boolean = false,
        runtime: String? = null,
        locale: TermuxWordsLocale = TermuxWordsLocale.English,
    ): TermuxProblemWords {
        val name = runtime ?: "OpenCode"
        val ar = locale == TermuxWordsLocale.Arabic
        return when (problem) {
            TermuxProblem.AccessNeeded ->
                TermuxProblemWords(
                    line =
                        when {
                            heard && ar -> "يعمل OpenCode في Termux، لكن هذا التطبيق لا يستطيع الوصول إلى Termux بعد. اسمح بالوصول وسيتصل."
                            heard -> "OpenCode is running in Termux, but this app can't reach Termux yet. Allow access and it connects."
                            ar -> "لا يستطيع هذا التطبيق الوصول إلى Termux بعد. اسمح بالوصول ليتمكن من العثور على OpenCode هناك والاتصال به."
                            else -> "This app can't reach Termux yet. Allow access so it can find OpenCode there and connect."
                        },
                    action = if (ar) "السماح بالوصول إلى Termux" else "Allow access to Termux",
                )
            TermuxProblem.AccessBlocked ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "حظر Android وصول هذا التطبيق إلى Termux. في أذونات هذا التطبيق، فعّل «Run commands in Termux environment»."
                        } else {
                            "Android blocked Termux access for this app. In this app's permissions, turn on “Run commands in Termux environment”."
                        },
                    action = if (ar) "فتح أذونات هذا التطبيق" else "Open this app's permissions",
                )
            TermuxProblem.OtherAppsOff ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "لا يقبل Termux أوامر من تطبيقات أخرى بعد. يتيح سطر واحد في Termux ذلك."
                        } else {
                            "Termux doesn't take commands from other apps yet. One line in Termux allows it."
                        },
                    action = if (ar) "السماح لتطبيقات أخرى في Termux" else "Allow other apps in Termux",
                )
            TermuxProblem.Asleep ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "لم يردّ Termux. ربما وضعه Android في حالة سكون. افتح Termux لتنشيطه."
                        } else {
                            "Termux didn't answer. Android may have put it to sleep. Open Termux to wake it."
                        },
                    action = if (ar) "فتح Termux" else "Open Termux",
                )
            TermuxProblem.NotAnswering ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "أُعدّ $name في Termux، لكنه لا يردّ. عادةً تعيده إعادة التشغيل إلى العمل."
                        } else {
                            "$name is set up in Termux but isn't answering. A restart usually brings it back."
                        },
                    action = if (ar) "إعادة تشغيل $name في Termux" else "Restart $name in Termux",
                )
            TermuxProblem.NotInstalled ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "Termux غير موجود على هذا الهاتف. ثبّته مجددًا أو أعدّ «على هذا الهاتف» بدلًا منه."
                        } else {
                            "Termux isn't on this phone. Install it again, or set up On this phone instead."
                        },
                    action = if (ar) "تنزيل Termux" else "Get Termux",
                )
            TermuxProblem.Outdated ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "إصدار Termux هذا قديم جدًا ولا يمكن للتطبيق استخدامه. ثبّت أحدث إصدار من Termux من F-Droid."
                        } else {
                            "This Termux is too old for the app to use. Install the current Termux from F-Droid."
                        },
                    action = if (ar) "تنزيل أحدث إصدار من Termux" else "Get the current Termux",
                )
            TermuxProblem.Unknown ->
                TermuxProblemWords(
                    line =
                        if (ar) {
                            "تعذّر على هذا الهاتف التحقّق من $name في Termux. حاول مجددًا بعد قليل."
                        } else {
                            "This phone couldn't check on $name in Termux. Try again in a moment."
                        },
                    action = if (ar) "إعادة المحاولة" else RETRY,
                )
        }
    }

    public const val RETRY: String = "Try again"
}

public enum class TermuxRunningServerState { Unsupported, Absent, Denied, Unavailable, Stopped, Running }

/** What the phone's Termux says about the app-managed server. */
public data class TermuxRunningServer(
    val state: TermuxRunningServerState,
    val runtime: TermuxRuntime? = null,
    val version: String = "",
    val phase: String = "",
    val needsCredentials: Boolean = false,
    val problem: TermuxProblem? = null,
    val heardOnPhone: Boolean = false,
) {
    val isRunning: Boolean get() = state == TermuxRunningServerState.Running
    val isStopped: Boolean get() = state == TermuxRunningServerState.Stopped
}

/** One look at the app-authored loopback address. Null when nothing answered. */
public data class TermuxHealth(val ok: Boolean, val needsPassword: Boolean)

public fun interface TermuxHealthProbe {
    public suspend fun probe(baseUrl: String, username: String?, password: String?): TermuxHealth?
}

/**
 * Detects the app-managed server (lib/state/termux_running_server.dart detectTermuxRunningServer). Web, iOS and
 * desktop ([TermuxBridge.supported] false) never touch the bridge or the probe. Timeouts are the caller's: a
 * `command_timeout` from the bridge reads as [TermuxProblem.Asleep].
 */
public class TermuxRunningServerDetector(
    private val bridge: TermuxBridge,
    private val access: TermuxAccess,
    private val probe: TermuxHealthProbe,
) {
    private val version = Regex("^\\d+\\.\\d+\\.\\d+(?:[-+][A-Za-z0-9.-]+)?$")

    public suspend fun detect(username: String? = null, password: String? = null): TermuxRunningServer {
        if (!bridge.supported) return TermuxRunningServer(TermuxRunningServerState.Unsupported)
        val capabilities = bridge.capabilities()
        if (!capabilities.platformSupported) return TermuxRunningServer(TermuxRunningServerState.Absent)
        if (!capabilities.installed) {
            return TermuxRunningServer(TermuxRunningServerState.Absent, problem = TermuxProblem.NotInstalled)
        }
        val blocked = TermuxProblem.ofCapabilities(capabilities, access.blocked)
        if (blocked != null) {
            val health = probe.probe(TermuxScripts.MANAGED_SERVER_URL, null, null)
            val heard = health != null && (health.ok || health.needsPassword)
            return TermuxRunningServer(TermuxRunningServerState.Denied, problem = blocked, heardOnPhone = heard)
        }
        val status =
            when (val answer = bridge.status()) {
                is TermuxAnswer.Done -> answer.value
                is TermuxAnswer.Failed -> {
                    val problem = TermuxProblem.ofError(answer.error)
                    return if (problem == TermuxProblem.OtherAppsOff || problem == TermuxProblem.AccessNeeded) {
                        TermuxRunningServer(TermuxRunningServerState.Denied, problem = problem)
                    } else {
                        TermuxRunningServer(TermuxRunningServerState.Unavailable, problem = problem)
                    }
                }
            }
        if (status.isReady && !status.switchPending && status.port == TermuxScripts.MANAGED_SERVER_PORT) {
            val shown = status.version.trim().takeIf(version::matches) ?: ""
            val health = probe.probe(TermuxScripts.MANAGED_SERVER_URL, username, password)
            if (health != null && (health.ok || health.needsPassword)) {
                return TermuxRunningServer(
                    TermuxRunningServerState.Running,
                    runtime = status.runtime,
                    version = shown,
                    phase = "ready",
                    needsCredentials = health.needsPassword,
                )
            }
            return TermuxRunningServer(
                TermuxRunningServerState.Unavailable,
                problem = TermuxProblem.NotAnswering,
                runtime = status.runtime,
            )
        }
        if (status.phase == "stopped" && status.runtimeSelected && !status.switchPending) {
            return TermuxRunningServer(TermuxRunningServerState.Stopped, runtime = status.runtime, phase = "stopped")
        }
        return TermuxRunningServer(TermuxRunningServerState.Absent, phase = status.phase)
    }
}
