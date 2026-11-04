package io.github.eslamasabry.oc.engine.runtime.messages

/**
 * The person's words for a setup or manager message (port of lib/ui/widgets/setup_ui_messages.dart, B-L10N-019).
 *
 * The bundled setup and manager scripts, the Linux bridge and older stored states still speak English sentences. They
 * are translated at the presentation boundary: a message the app wrote, as a whole, becomes one words key (with its
 * arguments, such as an exit code); a longer app sentence inside other text is swapped in place; anything else (a
 * script's own error, server or platform detail) stays exactly as it is, for Details. Keys are names in `:ui:words`
 * (`Res.allStringResources`), so this module needs no copy of its own.
 */
public sealed interface SetupMessage {
    /** The whole message is one the app wrote: show [key] with [args]. */
    public data class Known(val key: String, val args: List<String> = emptyList()) : SetupMessage

    /** Not one message as a whole: [segments] in order, app sentences swapped for keys, the rest kept verbatim. */
    public data class Mixed(val segments: List<SetupSegment>) : SetupMessage
}

/** One piece of a [SetupMessage.Mixed]. */
public sealed interface SetupSegment {
    public data class Text(val text: String) : SetupSegment

    public data class Word(val key: String) : SetupSegment
}

public object SetupMessages {
    /** Shorter sentences are only translated as a whole, so a short label cannot split prose (Dart: length >= 24). */
    private const val EMBEDDED_MIN = 24

    private val probeHttp =
        Regex(
            "^The address responded, but not like an OpenCode server \\(HTTP (\\d+)\\)\\. " +
                "Check that the URL points at opencode serve\\.$",
        )
    private val probeError = Regex("^Connection test failed: (.*)$")
    private val serverExit = Regex("^OpenCode server exited \\(code (\\d+)\\)$")

    /** [message] in the person's words (Dart `setupUiMessage`). */
    public fun classify(message: String): SetupMessage {
        whole(message)?.let { return it }
        var segments: List<SetupSegment> = listOf(SetupSegment.Text(message))
        for ((english, key) in SetupMessageTable.entries) {
            if (english.length < EMBEDDED_MIN) continue
            if (segments.none { it is SetupSegment.Text && english in it.text }) continue
            segments = segments.flatMap { segment -> split(segment, english, key) }
        }
        return SetupMessage.Mixed(segments.filterNot { it is SetupSegment.Text && it.text.isEmpty() })
    }

    /**
     * The words for [message] only when the whole of it is one the app wrote; null for anything else, which belongs
     * under Details and never in a row's words (Dart `setupPlainMessage`).
     */
    public fun plain(message: String): SetupMessage.Known? {
        val text = message.trim()
        // Carries the transport's own words after the colon.
        if (text.startsWith("Connection test failed: ")) return null
        return whole(text)
    }

    private fun whole(message: String): SetupMessage.Known? {
        probeHttp.matchEntire(message)?.let { return SetupMessage.Known("e7SetupProbeHttp", listOf(it.groupValues[1])) }
        probeError.matchEntire(message)?.let { return SetupMessage.Known("e7SetupProbeError", listOf(it.groupValues[1])) }
        serverExit.matchEntire(message)?.let { return SetupMessage.Known("e7SetupServerExit", listOf(it.groupValues[1])) }
        return SetupMessageTable.byEnglish[message]?.let { SetupMessage.Known(it) }
    }

    private fun split(segment: SetupSegment, english: String, key: String): List<SetupSegment> {
        if (segment !is SetupSegment.Text || english !in segment.text) return listOf(segment)
        val pieces = segment.text.split(english)
        return buildList {
            pieces.forEachIndexed { i, piece ->
                if (i > 0) add(SetupSegment.Word(key))
                add(SetupSegment.Text(piece))
            }
        }
    }
}

/** The app-authored English sentences and their words keys, longest first as in the Dart table (order matters). */
internal object SetupMessageTable {
    val entries: List<Pair<String, String>> =
        listOf(
            "Setup stopped before it finished. Continue setup to try again. Completed steps will be kept." to "phoneSetupErrorInterrupted",
            "OpenCode is installed but is not answering. Continue setup to try again. Details show what stopped it." to "phoneSetupErrorCannotStart",
            "OpenCode was downloaded, but its program was not in the download. Continue to fetch it again." to "phoneSetupErrorOpenCodeNoProgram",
            "OpenCode was downloaded, but its program does not run on this phone. Details show what it said." to "phoneSetupErrorOpenCodeWontRun",
            "OpenCode was installed, but it did not start. Continue to try again; Details show what it said." to "phoneSetupErrorOpenCodeNoStart",
            "Include https:// or http:// in the server address." to "e7SetupIncludeScheme",
            "Server URLs must use https:// or http://." to "e7SetupUrlScheme",
            "Enter a server port between 1 and 65535." to "serverTrustInvalidPort",
            "This server uses plain HTTP, and you have not confirmed that yet. Edit the server and choose Use it anyway, or use HTTPS." to "serverTrustHttpUnconfirmed",
            "The server certificate is expired or not yet valid." to "serverTrustCertificateValidityError",
            "The server certificate changed." to "serverTrustCertificateChangedTitle",
            "The server certificate is not trusted. Review its fingerprint." to "e7SetupCertificate",
            "Password rejected. Check the server password and try again." to "serverTrustPasswordRejected",
            "The server could not be checked. Check the address and try again." to "serverTrustProbeFailed",
            "Some Linux packages need repair. Continue setup to try again. Details show what stopped it." to "phoneSetupErrorPackageRepair",
            "Termux command failed." to "e7SetupCommandFailed",
            "Termux returned an unexpected bridge response." to "e7SetupUnexpectedBridge",
            "Setup queued" to "e7SetupSetupQueued",
            "No setup has been started" to "e7SetupNoSetup",
            "Setup manager is missing after launch" to "e7SetupManagerMissingAfterLaunch",
            "Bootstrap state cleared" to "e7SetupBootstrapCleared",
            "Installing OpenCode 2 beta" to "e7SetupInstallingBeta",
            "Authentication failed" to "e7SetupAuthenticationFailed",
            "A server bound to its own 127.0.0.1 is not reachable from this phone until you bridge it — `adb reverse tcp:PORT tcp:PORT` over USB, or an SSH forward. To reach it over the network instead, put it behind HTTPS." to "e7SetupPairingPhoneHint",
            "The camera is used only to read the QR that opencode2 pair prints, and only while this screen is open. You can paste the code instead — it does exactly the same thing." to "e7SetupCameraPrivacy",
            "Android will not ask again, so this has to be changed in app settings: turn on Camera, then come back. Pasting the code needs no permission at all and works right now." to "e7SetupCameraSettingsDetail",
            "Password rejected. Copy the current \"server password\" line from the server output — it changes on every restart unless OPENCODE_PASSWORD is set." to "e7SetupPasswordRejected",
            "These commands run on the computer that hosts this server — the app cannot run them for you. Copy each one into a terminal on that machine." to "e7SetupHostInstructions",
            "This version of Termux is too old for the app to control it. Install the current F-Droid or GitHub build of Termux, then check again." to "e7SetupTermuxOutdated",
            "There is nothing to scan with. Run opencode2 pair on the server, copy the code it prints, and paste it into the server editor." to "e7SetupNoCameraDetail",
            "That pairing code carries no server address. Check that the server is actually listening, then run `opencode2 pair` again." to "e7SetupPairAddressMissing",
            "It drives Termux, which has no desktop equivalent. On this machine, run `opencode serve` yourself and add it as a server." to "e7SetupUnsupportedSetup",
            "The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires a password." to "e7SetupMissingPasswordLong",
            "The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires one." to "e7SetupMissingPasswordShort",
            "That pairing code lists more addresses than this app will try. Bind the server to one interface and pair again." to "e7SetupPairTooMany",
            "When the server reports an update, Settings offers the native upgrade first; this is the host-side equivalent." to "e7SetupUpdateHostDetail",
            "The saved profile credential differs from this runtime; restore its original saved credential before returning" to "e7SetupCredentialMismatch",
            "That is far too long to be a pairing code. Copy only the line `opencode2 pair` prints, or scan its QR code." to "e7SetupPairLong",
            "That pairing code is the wrong shape — it should be a JSON object with `urls`, `username`, and `password`." to "e7SetupPairShape",
            "A local server exists, but its saved credential is unavailable. Run setup again to replace it safely." to "e7SetupExistingMissingCredential",
            "Termux opened but the setup did not start. Retry once; if it happens again, copy the failure report." to "e7SetupSetupNotStarted",
            "That pairing code has no `password` field. It may have been truncated — scan or copy the whole code." to "e7SetupPairPasswordMissing",
            "The local server is ready, but its observed version could not be saved. Refresh setup to try again." to "e7SetupObservedVersionSaveFailed",
            "First-time setup can take 10–15 minutes. You can leave this screen and return; setup keeps running." to "e7SetupFirstSetupDuration",
            "The saved credential for this managed server is unavailable. Run setup again to replace it safely." to "e7SetupMissingCredential",
            "There is no pairing code here. Run `opencode2 pair` on the server and scan or copy what it prints." to "e7SetupPairNone",
            "Check that the server is running, and that the address it printed is one this machine can reach." to "e7SetupPairingDesktopHint",
            "Interactive terminal. Use the accessibility button for a readable transcript and labeled input." to "e7SetupTerminalSemantics",
            "That is not a pairing code. Run `opencode2 pair` on the server and scan or copy what it prints." to "e7SetupPairInvalid",
            "Printed by opencode2 serve at startup (\"server password …\"). Optional for servers without one." to "e7SetupPasswordStartupHint",
            "HTTP is allowed only for localhost, 127.0.0.1, [::1], or a private network address. Use HTTPS or Tailscale for other servers." to "e7SetupLocalHttp",
            "The connection timed out. Check the address, and that the server is reachable from this phone." to "e7SetupTimeout",
            "The browser could not reach the server. Check the address and that the server is running. A server on a different origin must allow the origin of this page (CORS), and a page loaded over https cannot use an http:// or ws:// server." to "browserTransportFailure",
            "Official installer plus a systemd user service that survives closed terminals and reboots." to "e7SetupInstallServiceDetail",
            "The local server restarted, but the active server changed. Reconnect when you are ready." to "e7SetupRestartActiveChanged",
            "The clipboard is empty. Run `opencode2 pair` on the server and copy the code it prints." to "e7SetupEmptyPairClipboard",
            "HTTPS is required outside this device. Basic credentials must never be sent over HTTP." to "e7SetupRequireHttps",
            "Opens a browser. If the redirect cannot reach OpenCode, paste the callback URL here." to "e7SetupBrowserHint",
            "The connection test failed before the server could be checked. Try another address." to "e7SetupPairTestFailed",
            "The server responded but reported itself unhealthy. Check its logs, then try again." to "e7SetupUnhealthy",
            "The server will be briefly unavailable. Active generation should be stopped first." to "e7SetupUpdateInterruption",
            "The address did not answer as an OpenCode server. Check the address and try again." to "e7SetupNotOpenCode",
            "Another app may be holding the camera. Pasting the pairing code works either way." to "e7SetupCameraFailedDetail",
            "Termux did not answer. Open Termux once, run the unlock line, then verify again." to "e7SetupTermuxNoAnswer",
            "The server did not answer. Check that opencode serve is running on that address." to "e7SetupNoServerAnswer",
            "The server’s TLS certificate was rejected. Use a certificate this phone trusts." to "e7SetupCertificate",
            "On your computer run `opencode2 pair`, then paste or scan the code it prints." to "e7SetupPairingInstructions",
            "That pairing code has no `urls` field, so there is no address to connect to." to "e7SetupPairNoUrls",
            "The connection was refused. Is opencode serve running on that host and port?" to "e7SetupRefused",
            "Use HTTPS for remote machines. HTTP is limited to localhost or 127.0.0.1." to "e7SetupHttpsHint",
            "OpenCode server did not become authenticated and ready within 30 seconds" to "e7SetupReadinessTimeout",
            "Include https://. Plain http:// works only on this device or a private network address." to "e7SetupIncludeScheme",
            "Could not confirm this restart. Refresh its progress before retrying." to "e7SetupRestartUnconfirmed",
            "The server refused the credentials. Check the username and password." to "e7SetupCredentialsRefused",
            "An existing Ubuntu container is not usable; setup will not delete it" to "e7SetupUbuntuUnusable",
            "That pairing code lists an address far too long to be a server URL." to "e7SetupPairAddressLong",
            "Could not refresh packages.termux.dev; check the network and retry" to "e7SetupRepositoryRefreshFailed",
            "Enter a complete server URL, such as https://server.example:4096." to "e7SetupCompleteUrl",
            "The local server port is still in use; no replacement was started" to "e7SetupPortBusy",
            "The running process and its child processes will be terminated." to "e7SetupStopTerminalDetail",
            "Codex server URLs must use wss://, or ws:// for a local server." to "e7SetupCodexScheme",
            "Termux dependencies are still unusable after the package repair" to "e7SetupDependenciesUnusable",
            "Install the current F-Droid build of Termux, then return here." to "e7SetupInstallTermuxDetail",
            "That host name could not be found. Check the address spelling." to "e7SetupDns",
            "OpenCode updated, but its model catalog could not be refreshed" to "e7SetupModelsRefreshFailed",
            "Server URLs must use https://, or http:// for a local server." to "e7SetupUrlScheme",
            "The local server restarted, but the app could not reconnect." to "e7SetupRestartReconnectFailed",
            "Waiting for Termux to respond. This can take a little while." to "e7SetupWaitingTermux",
            "No server there yet? The setup guide shows how to start one." to "e7SetupNoServerGuide",
            "That pairing code's `urls` field is not a list of addresses." to "e7SetupPairUrlsType",
            "Server URLs must use https://, or http:// for local Termux." to "e7SetupTermuxUrlScheme",
            "Remove the path from the server URL. Enter only its origin." to "e7SetupUrlPath",
            "Could not repair the interrupted Termux package transaction" to "e7SetupRepairFailed",
            "Point the camera at the QR code printed by opencode2 pair." to "e7SetupScanInstruction",
            "Leave empty only if this server no longer uses a password." to "e7SetupEmptyPasswordHint",
            "The local server is stopped. Its installed files are kept." to "e7SetupLocalStopped",
            "Remove query parameters and fragments from the server URL." to "e7SetupUrlQuery",
            "Remove query parameters and fragments from the Codex URL." to "e7SetupCodexQuery",
            "Plain WebSocket is allowed only for a local Codex server." to "e7SetupCodexPlain",
            "Could not remove the interrupted app-owned Ubuntu install" to "e7SetupRemoveInterruptedFailed",
            "Switch manager did not start in an isolated process group" to "e7SetupSwitchGroupFailed",
            "Managed server did not start in an isolated process group" to "e7SetupServerGroupFailed",
            "Password rejected. Check the pairing code and try again." to "e7SetupPairPasswordRejected",
            "Do not put credentials in the URL. Use the fields below." to "e7SetupUrlCredentials",
            "Ubuntu Base extraction did not create a usable container" to "e7SetupExtractionFailed",
            "Setup manager did not start in an isolated process group" to "e7SetupSetupGroupFailed",
            "Setup stopped unexpectedly; see live output for details" to "e7SetupSetupInterrupted",
            "Could not select the official Termux package repository" to "e7SetupRepositoryFailed",
            "That pairing code contains an unusable server address." to "e7SetupPairAddressUnusable",
            "The tracked process is not the managed OpenCode server" to "e7SetupIdentityMismatch",
            "That pairing code lists an address that is not text." to "e7SetupPairAddressType",
            "Could not record the managed server process identity" to "e7SetupRecordIdentityFailed",
            "Terminal control keys. Swipe horizontally for more." to "e7SetupControlKeys",
            "Ubuntu is installed. OpenCode is not installed yet." to "e7SetupUbuntuOnly",
            "Could not complete the safe Termux package upgrade" to "e7SetupUpgradeFailed",
            "Could not save connection guidance. Retry saving." to "e7SetupGuidanceSaveFailed",
            "That pairing code's `username` field is not text." to "e7SetupPairUsernameType",
            "That pairing code's `password` field is not text." to "e7SetupPairPasswordType",
            "The OpenCode 2 data location record is unreadable" to "e7SetupUnreadableData",
            "Stop active generation before updating OpenCode." to "e7SetupStopBeforeUpdate",
            "OpenCode installed but did not report a version" to "e7SetupVersionMissing",
            "Unavailable while the terminal is disconnected" to "e7SetupKeyUnavailable",
            "The server is starting. Try again in a moment." to "e7SetupServerStarting",
            "The local OpenCode server stopped unexpectedly" to "e7SetupUnexpectedStop",
            "The phone restarted, so the local server stopped" to "setupStoppedByRestart",
            "Could not check available storage before setup" to "e7SetupCheckStorageFailed",
            "The server started but authentication failed." to "e7SetupAuthFailed",
            "The managed Ubuntu environment is unavailable" to "e7SetupUbuntuUnavailable",
            "Could not read available storage before setup" to "e7SetupReadStorageFailed",
            "Setup manager could not claim its launch lock" to "e7SetupLockFailed",
            "The selected OpenCode command is unavailable" to "e7SetupRuntimeUnavailable",
            "Could not check the installed environment." to "e7SetupCheckInstallFailed",
            "Remove the path from the Codex server URL." to "e7SetupCodexPath",
            "Enter an absolute Codex project directory." to "e7SetupCodexDirectory",
            "Lost track of the setup running in Termux" to "e7SetupSetupLost",
            "Uses a one-time code. Works from a phone." to "e7SetupDeviceCodeHint",
            "The previous runtime record is unreadable" to "e7SetupUnreadablePrevious",
            "Could not install the Termux dependencies" to "e7SetupDependenciesFailed",
            "Install OpenCode as a background service" to "e7SetupInstallService",
            "Input is unavailable while disconnected." to "e7SetupInputDisconnected",
            "Copied. Run it on the server's computer." to "e7SetupHostCopied",
            "This server requires its serve password." to "e7SetupPasswordNeeded",
            "Do not put credentials in the Codex URL." to "e7SetupCodexCredentials",
            "Checking the local server before restart" to "e7SetupCheckRestart",
            "First-time setup — run on your computer" to "e7SetupHostFirstSetup",
            "Preparing the selected OpenCode runtime" to "e7SetupPrepareRuntime",
            "Start a shell in the active workspace." to "e7SetupNewTerminalDetail",
            "The server profile has not been saved." to "e7SetupUnsavedProfile",
            "The server transport is reconnecting." to "e7SetupTransportReconnecting",
            "This terminal record will be removed." to "e7SetupRemoveTerminalDetail",
            "Read the server password for this app" to "e7SetupReadPassword",
            "No managed Ubuntu installation found." to "e7SetupNoUbuntu",
            "Copy terminal selection or transcript" to "e7SetupCopyTerminal",
            "Enter a valid Codex connection token." to "e7SetupCodexToken",
            "Refreshing the OpenCode model catalog" to "e7SetupRefreshModels",
            "OpenCode server exited during startup" to "e7SetupStartupExited",
            "Retry — resumes where setup left off" to "e7SetupResumeSetup",
            "The local server password is missing" to "e7SetupPasswordMissing",
            "Use accessible transcript and input" to "e7SetupAccessibleTerminal",
            "Full walkthrough (opens in browser)" to "e7SetupFullWalkthrough",
            "Could not read setup manager status" to "e7SetupReadManagerFailed",
            "Connection token re-entry required" to "e7SetupTokenRequired",
            "Termux bridge verification failed." to "e7SetupVerifyTermuxFailed",
            "OpenCode is running on this phone." to "e7SetupRunningOnPhone",
            "Enter a complete Codex server URL." to "e7SetupCodexCompleteUrl",
            "Switching the managed local server" to "e7SetupSwitchLocal",
            "Checking installed environment..." to "e7SetupCheckingInstall",
            "Android could not inspect Termux." to "e7SetupInspectTermuxFailed",
            "Reach it from this phone over USB" to "e7SetupUsbAccess",
            "The runtime switch did not start." to "e7SetupSwitchNotStarted",
            "Day-to-day — run on your computer" to "e7SetupHostDaily",
            "Repairing the Termux package set" to "e7SetupRepairPackages",
            "Camera access is needed to scan" to "e7SetupCameraNeeded",
            "On-device setup is Android only" to "e7SetupAndroidOnly",
            "Automatic recovery was disabled" to "e7SetupRecoveryWasDisabled",
            "The camera could not be opened" to "e7SetupCameraFailed",
            "Sends this key to the terminal" to "e7SetupSendKey",
            "Installing Termux dependencies" to "e7SetupInstallDependencies",
            "This is an OpenCode 2 server." to "e7SetupIsV2",
            "About and open source notices" to "e7SetupAboutNotices",
            "Installing Ubuntu environment" to "e7SetupInstallUbuntu",
            "Keep it running after logout" to "e7SetupKeepAfterLogout",
            "Server operation in progress" to "e7SetupServerOperation",
            "Saving local server settings" to "e7SetupSavingLocal",
            "The server is not connected." to "e7SetupServerDisconnected",
            "Update OpenCode on the host" to "e7SetupUpdateHost",
            "Connected — save to finish." to "e7SetupSaveToFinish",
            "Restarting the local server" to "e7SetupRestartingLocal",
            "Camera access is turned off" to "e7SetupCameraDisabled",
            "The server did not connect." to "e7SetupDidNotConnect",
            "Automatic recovery disabled" to "e7SetupRecoveryDisabled",
            "Password re-entry required" to "e7SetupPasswordRequired",
            "Checking Termux connection" to "e7SetupCheckingTermux",
            "Terminal input unavailable" to "e7SetupInputUnavailable",
            "Refreshing Termux packages" to "e7SetupRefreshPackages",
            "Sign in with your account" to "e7SetupAccountHint",
            "Start installed OpenCode?" to "e7SetupStartInstalled",
            "This device has no camera" to "e7SetupNoCamera",
            "Enter a Codex server URL." to "e7SetupCodexUrl",
            "Starting the local server" to "e7SetupStartLocalServer",
            "Stopping the local server" to "e7SetupStoppingLocal",
            "Update managed OpenCode?" to "e7SetupConfirmUpdate",
            "Use interactive terminal" to "e7SetupInteractiveTerminal",
            "Starting setup in Termux" to "e7SetupStartingSetup",
            "Send command to terminal" to "e7SetupSendCommand",
            "Setup manager is missing" to "e7SetupMissingManager",
            "No terminal output yet." to "e7SetupNoOutput",
            "End of input, Control D" to "e7SetupEndInputKey",
            "Discard server changes?" to "e7SetupDiscardChanges",
            "Restarting local server" to "e7SetupRestartingLocalStage",
            "Terminal command input" to "e7SetupCommandInput",
            "Choose how to continue" to "e7SetupChooseContinue",
            "Run as a Linux service" to "e7SetupLinuxService",
            "Failure report copied." to "e7SetupReportCopied",
            "Reading setup progress" to "e7SetupReadingProgress",
            "Follow the server log" to "e7SetupFollowLog",
            "No terminal processes" to "e7SetupNoTerminals",
            "Paste server password" to "e7SetupPastePassword",
            "Starting local server" to "e7SetupStartingLocal",
            "Show server password" to "e7SetupShowPassword",
            "Open the setup guide" to "e7SetupOpenSetupGuide",
            "Hide server password" to "e7SetupHidePassword",
            "Interrupt, Control C" to "e7SetupInterruptKey",
            "This device (Termux)" to "e7SetupThisDevice",
            "Getting models ready" to "e7SetupPreparingModels",
            "Setup output copied." to "e7SetupOutputCopied",
            "Local server stopped" to "e7SetupStoppedLocal",
            "Installing OpenCode" to "e7SetupInstallingOpenCode",
            "Username (optional)" to "e7SetupUsername",
            "Copy failure report" to "e7SetupCopyFailureReport",
            "Terminal transcript" to "e7SetupTranscript",
            "Enter a server URL." to "e7SetupEnterUrl",
            "Unknown setup state" to "e7SetupUnknownSetup",
            "Copy & open Termux" to "e7SetupCopyOpenTermux",
            "Restart the server" to "e7SetupRestartServer",
            "Paste pairing code" to "e7SetupPastePairing",
            "Checking Termux..." to "e7SetupCheckingTermuxShort",
            "Connection failed." to "e7SetupConnectionFailed",
            "Verify & continue" to "e7SetupVerifyContinue",
            "Setting up Ubuntu" to "e7SetupInstallingUbuntu",
            "not yet available" to "e7SetupStepUnavailable",
            "Scan pairing code" to "e7SetupScanPairing",
            "Connection closed" to "e7SetupConnectionClosed",
            "Close the scanner" to "e7SetupCloseScanner",
            "Re-enter password" to "e7SetupReenterPassword",
            "Open app settings" to "e7SetupOpenAppSettings",
            "Stop local server" to "e7SetupStopLocal",
            "<invalid address>" to "e7SetupInvalidAddress",
            "OpenCode is ready" to "e7SetupOpenCodeReady",
            "Paste it instead" to "e7SetupPasteInstead",
            "Paste an API key" to "e7SetupApiKeyHint",
            "Resume live view" to "e7SetupResumeLive",
            "Terminal actions" to "e7SetupTerminalActions",
            "Remove terminal?" to "e7SetupRemoveTerminal",
            "Preparing Termux" to "e7SetupPrepareTermux",
        )

    val byEnglish: Map<String, String> = entries.toMap()
}
