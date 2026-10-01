import '../../l10n/app_localizations.dart';

/// Translates app-authored validation and manager messages at the presentation
/// boundary. Unknown server/platform detail and copied raw diagnostics remain
/// unchanged. Keep longest matches first so a short label cannot split prose.
String setupUiMessage(AppLocalizations l10n, String message) {
  final whole = _wholeMessage(l10n, message);
  if (whole != null) return whole;
  var result = message;
  for (final entry in _messages(l10n).entries) {
    if (entry.key.length >= 24) {
      result = result.replaceAll(entry.key, entry.value);
    }
  }
  return result;
}

/// The person's words for [message] when the whole of it is one the app
/// wrote (a manager or bridge message it translates, or already one of
/// those translations); null for anything else — a script's own error,
/// native or server text — which belongs under Details, never in a row's
/// words (no raw errors).
String? setupPlainMessage(AppLocalizations l10n, String message) {
  final text = message.trim();
  // Carries the transport's own words after the colon.
  if (text.startsWith('Connection test failed: ')) return null;
  final whole = _wholeMessage(l10n, text);
  if (whole != null) return whole;
  return _messages(l10n).containsValue(text) ? text : null;
}

/// [message] translated only when it is one known message as a whole.
String? _wholeMessage(AppLocalizations l10n, String message) {
  {
    final match = RegExp(
      r'^The address responded, but not like an OpenCode server \(HTTP (\d+)\)\. Check that the URL points at opencode serve\.$',
    ).firstMatch(message);
    if (match != null) return l10n.e7SetupProbeHttp(match.group(1)!);
  }
  {
    final match = RegExp(r'^Connection test failed: (.*)$').firstMatch(message);
    if (match != null) return l10n.e7SetupProbeError(match.group(1)!);
  }
  {
    final match = RegExp(
      r'^OpenCode server exited \(code (\d+)\)$',
    ).firstMatch(message);
    if (match != null) return l10n.e7SetupServerExit(match.group(1)!);
  }
  return _messages(l10n)[message];
}

Map<String, String> _messages(AppLocalizations l10n) => <String, String>{
  'Termux command failed.': l10n.e7SetupCommandFailed,
  'Termux returned an unexpected bridge response.':
      l10n.e7SetupUnexpectedBridge,
  'Setup queued': l10n.e7SetupSetupQueued,
  'No setup has been started': l10n.e7SetupNoSetup,
  'Setup manager is missing after launch':
      l10n.e7SetupManagerMissingAfterLaunch,
  'Bootstrap state cleared': l10n.e7SetupBootstrapCleared,
  'Installing OpenCode 2 beta': l10n.e7SetupInstallingBeta,
  'Authentication failed': l10n.e7SetupAuthenticationFailed,

  'A server bound to its own 127.0.0.1 is not reachable from this phone until you bridge it — `adb reverse tcp:PORT tcp:PORT` over USB, or an SSH forward. To reach it over the network instead, put it behind HTTPS.':
      l10n.e7SetupPairingPhoneHint,
  'The camera is used only to read the QR that opencode2 pair prints, and only while this screen is open. You can paste the code instead — it does exactly the same thing.':
      l10n.e7SetupCameraPrivacy,
  'Android will not ask again, so this has to be changed in app settings: turn on Camera, then come back. Pasting the code needs no permission at all and works right now.':
      l10n.e7SetupCameraSettingsDetail,
  'Password rejected. Copy the current "server password" line from the server output — it changes on every restart unless OPENCODE_PASSWORD is set.':
      l10n.e7SetupPasswordRejected,
  'These commands run on the computer that hosts this server — the app cannot run them for you. Copy each one into a terminal on that machine.':
      l10n.e7SetupHostInstructions,
  'This version of Termux is too old for the app to control it. Install the current F-Droid or GitHub build of Termux, then check again.':
      l10n.e7SetupTermuxOutdated,
  'There is nothing to scan with. Run opencode2 pair on the server, copy the code it prints, and paste it into the server editor.':
      l10n.e7SetupNoCameraDetail,
  'That pairing code carries no server address. Check that the server is actually listening, then run `opencode2 pair` again.':
      l10n.e7SetupPairAddressMissing,
  'It drives Termux, which has no desktop equivalent. On this machine, run `opencode serve` yourself and add it as a server.':
      l10n.e7SetupUnsupportedSetup,
  'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires a password.':
      l10n.e7SetupMissingPasswordLong,
  'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires one.':
      l10n.e7SetupMissingPasswordShort,
  'That pairing code lists more addresses than this app will try. Bind the server to one interface and pair again.':
      l10n.e7SetupPairTooMany,
  'When the server reports an update, Settings offers the native upgrade first; this is the host-side equivalent.':
      l10n.e7SetupUpdateHostDetail,
  'The saved profile credential differs from this runtime; restore its original saved credential before returning':
      l10n.e7SetupCredentialMismatch,
  'That is far too long to be a pairing code. Copy only the line `opencode2 pair` prints, or scan its QR code.':
      l10n.e7SetupPairLong,
  'That pairing code is the wrong shape — it should be a JSON object with `urls`, `username`, and `password`.':
      l10n.e7SetupPairShape,
  'A local server exists, but its saved credential is unavailable. Run setup again to replace it safely.':
      l10n.e7SetupExistingMissingCredential,
  'Termux opened but the setup did not start. Retry once; if it happens again, copy the failure report.':
      l10n.e7SetupSetupNotStarted,
  'That pairing code has no `password` field. It may have been truncated — scan or copy the whole code.':
      l10n.e7SetupPairPasswordMissing,
  'The local server is ready, but its observed version could not be saved. Refresh setup to try again.':
      l10n.e7SetupObservedVersionSaveFailed,
  'First-time setup can take 10–15 minutes. You can leave this screen and return; setup keeps running.':
      l10n.e7SetupFirstSetupDuration,
  'The saved credential for this managed server is unavailable. Run setup again to replace it safely.':
      l10n.e7SetupMissingCredential,
  'There is no pairing code here. Run `opencode2 pair` on the server and scan or copy what it prints.':
      l10n.e7SetupPairNone,
  'Check that the server is running, and that the address it printed is one this machine can reach.':
      l10n.e7SetupPairingDesktopHint,
  'Interactive terminal. Use the accessibility button for a readable transcript and labeled input.':
      l10n.e7SetupTerminalSemantics,
  'That is not a pairing code. Run `opencode2 pair` on the server and scan or copy what it prints.':
      l10n.e7SetupPairInvalid,
  'Printed by opencode2 serve at startup ("server password …"). Optional for servers without one.':
      l10n.e7SetupPasswordStartupHint,
  'HTTP is allowed only for localhost, 127.0.0.1, [::1], or a private network address. Use HTTPS or Tailscale for other servers.':
      l10n.e7SetupLocalHttp,
  'The connection timed out. Check the address, and that the server is reachable from this phone.':
      l10n.e7SetupTimeout,
  'Official installer plus a systemd user service that survives closed terminals and reboots.':
      l10n.e7SetupInstallServiceDetail,
  'The local server restarted, but the active server changed. Reconnect when you are ready.':
      l10n.e7SetupRestartActiveChanged,
  'The clipboard is empty. Run `opencode2 pair` on the server and copy the code it prints.':
      l10n.e7SetupEmptyPairClipboard,
  'HTTPS is required outside this device. Basic credentials must never be sent over HTTP.':
      l10n.e7SetupRequireHttps,
  'Opens a browser. If the redirect cannot reach OpenCode, paste the callback URL here.':
      l10n.e7SetupBrowserHint,
  'The connection test failed before the server could be checked. Try another address.':
      l10n.e7SetupPairTestFailed,
  'The server responded but reported itself unhealthy. Check its logs, then try again.':
      l10n.e7SetupUnhealthy,
  'The server will be briefly unavailable. Active generation should be stopped first.':
      l10n.e7SetupUpdateInterruption,
  'The address did not answer as an OpenCode server. Check the address and try again.':
      l10n.e7SetupNotOpenCode,
  'Another app may be holding the camera. Pasting the pairing code works either way.':
      l10n.e7SetupCameraFailedDetail,
  'Termux did not answer. Open Termux once, run the unlock line, then verify again.':
      l10n.e7SetupTermuxNoAnswer,
  'The server did not answer. Check that opencode serve is running on that address.':
      l10n.e7SetupNoServerAnswer,
  'The server’s TLS certificate was rejected. Use a certificate this phone trusts.':
      l10n.e7SetupCertificate,
  'On your computer run `opencode2 pair`, then paste or scan the code it prints.':
      l10n.e7SetupPairingInstructions,
  'That pairing code has no `urls` field, so there is no address to connect to.':
      l10n.e7SetupPairNoUrls,
  'The connection was refused. Is opencode serve running on that host and port?':
      l10n.e7SetupRefused,
  'Use HTTPS for remote machines. HTTP is limited to localhost or 127.0.0.1.':
      l10n.e7SetupHttpsHint,
  'This app targets OpenCode 2; some features are unavailable on v1 servers.':
      l10n.e7SetupV1Limited,
  'This OpenCode 2 installation has no separate OpenCode 1 data to return to':
      l10n.e7SetupNoReturnData,
  'OpenCode server did not become authenticated and ready within 30 seconds':
      l10n.e7SetupReadinessTimeout,
  'Include https://. Plain http:// works only on this device or a private network address.':
      l10n.e7SetupIncludeScheme,
  'Could not confirm this restart. Refresh its progress before retrying.':
      l10n.e7SetupRestartUnconfirmed,
  'The server refused the credentials. Check the username and password.':
      l10n.e7SetupCredentialsRefused,
  'An existing Ubuntu container is not usable; setup will not delete it':
      l10n.e7SetupUbuntuUnusable,
  'That pairing code lists an address far too long to be a server URL.':
      l10n.e7SetupPairAddressLong,
  'Could not refresh packages.termux.dev; check the network and retry':
      l10n.e7SetupRepositoryRefreshFailed,
  'Enter a complete server URL, such as https://server.example:4096.':
      l10n.e7SetupCompleteUrl,
  'The local server port is still in use; no replacement was started':
      l10n.e7SetupPortBusy,
  'The running process and its child processes will be terminated.':
      l10n.e7SetupStopTerminalDetail,
  'Codex server URLs must use wss://, or ws:// for a local server.':
      l10n.e7SetupCodexScheme,
  'Termux dependencies are still unusable after the package repair':
      l10n.e7SetupDependenciesUnusable,
  'Install the current F-Droid build of Termux, then return here.':
      l10n.e7SetupInstallTermuxDetail,
  'That host name could not be found. Check the address spelling.':
      l10n.e7SetupDns,
  'OpenCode updated, but its model catalog could not be refreshed':
      l10n.e7SetupModelsRefreshFailed,
  'Server URLs must use https://, or http:// for a local server.':
      l10n.e7SetupUrlScheme,
  'The local server restarted, but the app could not reconnect.':
      l10n.e7SetupRestartReconnectFailed,
  'Waiting for Termux to respond. This can take a little while.':
      l10n.e7SetupWaitingTermux,
  'No server there yet? The setup guide shows how to start one.':
      l10n.e7SetupNoServerGuide,
  'That pairing code\'s `urls` field is not a list of addresses.':
      l10n.e7SetupPairUrlsType,
  'Server URLs must use https://, or http:// for local Termux.':
      l10n.e7SetupTermuxUrlScheme,
  'Remove the path from the server URL. Enter only its origin.':
      l10n.e7SetupUrlPath,
  'Could not repair the interrupted Termux package transaction':
      l10n.e7SetupRepairFailed,
  'Point the camera at the QR code printed by opencode2 pair.':
      l10n.e7SetupScanInstruction,
  'Leave empty only if this server no longer uses a password.':
      l10n.e7SetupEmptyPasswordHint,
  'The local server is stopped. Its installed files are kept.':
      l10n.e7SetupLocalStopped,
  'Remove query parameters and fragments from the server URL.':
      l10n.e7SetupUrlQuery,
  'Remove query parameters and fragments from the Codex URL.':
      l10n.e7SetupCodexQuery,
  'Plain WebSocket is allowed only for a local Codex server.':
      l10n.e7SetupCodexPlain,
  'Could not remove the interrupted app-owned Ubuntu install':
      l10n.e7SetupRemoveInterruptedFailed,
  'Switch manager did not start in an isolated process group':
      l10n.e7SetupSwitchGroupFailed,
  'Managed server did not start in an isolated process group':
      l10n.e7SetupServerGroupFailed,
  'Password rejected. Check the pairing code and try again.':
      l10n.e7SetupPairPasswordRejected,
  'Do not put credentials in the URL. Use the fields below.':
      l10n.e7SetupUrlCredentials,
  'Ubuntu Base extraction did not create a usable container':
      l10n.e7SetupExtractionFailed,
  'Setup manager did not start in an isolated process group':
      l10n.e7SetupSetupGroupFailed,
  'Setup stopped unexpectedly; see live output for details':
      l10n.e7SetupSetupInterrupted,
  'Could not select the official Termux package repository':
      l10n.e7SetupRepositoryFailed,
  'That pairing code contains an unusable server address.':
      l10n.e7SetupPairAddressUnusable,
  'The tracked process is not the managed OpenCode server':
      l10n.e7SetupIdentityMismatch,
  'That pairing code lists an address that is not text.':
      l10n.e7SetupPairAddressType,
  'Could not record the managed server process identity':
      l10n.e7SetupRecordIdentityFailed,
  'Terminal control keys. Swipe horizontally for more.':
      l10n.e7SetupControlKeys,
  'Ubuntu is installed. OpenCode is not installed yet.': l10n.e7SetupUbuntuOnly,
  'Could not complete the safe Termux package upgrade':
      l10n.e7SetupUpgradeFailed,
  'Could not save connection guidance. Retry saving.':
      l10n.e7SetupGuidanceSaveFailed,
  'That pairing code\'s `username` field is not text.':
      l10n.e7SetupPairUsernameType,
  'That pairing code\'s `password` field is not text.':
      l10n.e7SetupPairPasswordType,
  'The OpenCode 2 data location record is unreadable':
      l10n.e7SetupUnreadableData,
  'Stop active generation before updating OpenCode.':
      l10n.e7SetupStopBeforeUpdate,
  'OpenCode installed but did not report a version': l10n.e7SetupVersionMissing,
  'Unavailable while the terminal is disconnected': l10n.e7SetupKeyUnavailable,
  'The server is starting. Try again in a moment.': l10n.e7SetupServerStarting,
  'The local OpenCode server stopped unexpectedly': l10n.e7SetupUnexpectedStop,
  'The phone restarted, so the local server stopped':
      l10n.setupStoppedByRestart,
  'Could not check available storage before setup':
      l10n.e7SetupCheckStorageFailed,
  'The server started but authentication failed.': l10n.e7SetupAuthFailed,
  'The managed Ubuntu environment is unavailable':
      l10n.e7SetupUbuntuUnavailable,
  'Could not read available storage before setup':
      l10n.e7SetupReadStorageFailed,
  'Setup manager could not claim its launch lock': l10n.e7SetupLockFailed,
  'The selected OpenCode command is unavailable':
      l10n.e7SetupRuntimeUnavailable,
  'Could not check the installed environment.': l10n.e7SetupCheckInstallFailed,
  'Remove the path from the Codex server URL.': l10n.e7SetupCodexPath,
  'Enter an absolute Codex project directory.': l10n.e7SetupCodexDirectory,
  'Lost track of the setup running in Termux': l10n.e7SetupSetupLost,
  'Uses a one-time code. Works from a phone.': l10n.e7SetupDeviceCodeHint,
  'The previous runtime record is unreadable': l10n.e7SetupUnreadablePrevious,
  'Could not install the Termux dependencies': l10n.e7SetupDependenciesFailed,
  'Install OpenCode as a background service': l10n.e7SetupInstallService,
  'Input is unavailable while disconnected.': l10n.e7SetupInputDisconnected,
  'Copied. Run it on the server\'s computer.': l10n.e7SetupHostCopied,
  'This server requires its serve password.': l10n.e7SetupPasswordNeeded,
  'Do not put credentials in the Codex URL.': l10n.e7SetupCodexCredentials,
  'Checking the local server before restart': l10n.e7SetupCheckRestart,
  'First-time setup — run on your computer': l10n.e7SetupHostFirstSetup,
  'Preparing the selected OpenCode runtime': l10n.e7SetupPrepareRuntime,
  'Start a shell in the active workspace.': l10n.e7SetupNewTerminalDetail,
  'The server profile has not been saved.': l10n.e7SetupUnsavedProfile,
  'The server transport is reconnecting.': l10n.e7SetupTransportReconnecting,
  'This terminal record will be removed.': l10n.e7SetupRemoveTerminalDetail,
  'Read the server password for this app': l10n.e7SetupReadPassword,
  'No managed Ubuntu installation found.': l10n.e7SetupNoUbuntu,
  'Copy terminal selection or transcript': l10n.e7SetupCopyTerminal,
  'Enter a valid Codex connection token.': l10n.e7SetupCodexToken,
  'Refreshing the OpenCode model catalog': l10n.e7SetupRefreshModels,
  'OpenCode server exited during startup': l10n.e7SetupStartupExited,
  'Retry — resumes where setup left off': l10n.e7SetupResumeSetup,
  'The local server password is missing': l10n.e7SetupPasswordMissing,
  'Use accessible transcript and input': l10n.e7SetupAccessibleTerminal,
  'Full walkthrough (opens in browser)': l10n.e7SetupFullWalkthrough,
  'Could not read setup manager status': l10n.e7SetupReadManagerFailed,
  'Connection token re-entry required': l10n.e7SetupTokenRequired,
  'Termux bridge verification failed.': l10n.e7SetupVerifyTermuxFailed,
  'OpenCode is running on this phone.': l10n.e7SetupRunningOnPhone,
  'Enter a complete Codex server URL.': l10n.e7SetupCodexCompleteUrl,
  'Switching the managed local server': l10n.e7SetupSwitchLocal,
  'Checking installed environment...': l10n.e7SetupCheckingInstall,
  'Android could not inspect Termux.': l10n.e7SetupInspectTermuxFailed,
  'Reach it from this phone over USB': l10n.e7SetupUsbAccess,
  'The runtime switch did not start.': l10n.e7SetupSwitchNotStarted,
  'Day-to-day — run on your computer': l10n.e7SetupHostDaily,
  'Repairing the Termux package set': l10n.e7SetupRepairPackages,
  'Camera access is needed to scan': l10n.e7SetupCameraNeeded,
  'On-device setup is Android only': l10n.e7SetupAndroidOnly,
  'Automatic recovery was disabled': l10n.e7SetupRecoveryWasDisabled,
  'The camera could not be opened': l10n.e7SetupCameraFailed,
  'Sends this key to the terminal': l10n.e7SetupSendKey,
  'Installing Termux dependencies': l10n.e7SetupInstallDependencies,
  'This is an OpenCode 2 server.': l10n.e7SetupIsV2,
  'About and open source notices': l10n.e7SetupAboutNotices,
  'Installing Ubuntu environment': l10n.e7SetupInstallUbuntu,
  'Keep it running after logout': l10n.e7SetupKeepAfterLogout,
  'Server operation in progress': l10n.e7SetupServerOperation,
  'Saving local server settings': l10n.e7SetupSavingLocal,
  'The server is not connected.': l10n.e7SetupServerDisconnected,
  'Update OpenCode on the host': l10n.e7SetupUpdateHost,
  'Connected — save to finish.': l10n.e7SetupSaveToFinish,
  'Restarting the local server': l10n.e7SetupRestartingLocal,
  'Camera access is turned off': l10n.e7SetupCameraDisabled,
  'The server did not connect.': l10n.e7SetupDidNotConnect,
  'Automatic recovery disabled': l10n.e7SetupRecoveryDisabled,
  'Password re-entry required': l10n.e7SetupPasswordRequired,
  'Checking Termux connection': l10n.e7SetupCheckingTermux,
  'Terminal input unavailable': l10n.e7SetupInputUnavailable,
  'Refreshing Termux packages': l10n.e7SetupRefreshPackages,
  'Sign in with your account': l10n.e7SetupAccountHint,
  'Start installed OpenCode?': l10n.e7SetupStartInstalled,
  'This device has no camera': l10n.e7SetupNoCamera,
  'Enter a Codex server URL.': l10n.e7SetupCodexUrl,
  'Starting the local server': l10n.e7SetupStartLocalServer,
  'Stopping the local server': l10n.e7SetupStoppingLocal,
  'Update managed OpenCode?': l10n.e7SetupConfirmUpdate,
  'Use interactive terminal': l10n.e7SetupInteractiveTerminal,
  'Starting setup in Termux': l10n.e7SetupStartingSetup,
  'Send command to terminal': l10n.e7SetupSendCommand,
  'Setup manager is missing': l10n.e7SetupMissingManager,
  'No terminal output yet.': l10n.e7SetupNoOutput,
  'End of input, Control D': l10n.e7SetupEndInputKey,
  'Discard server changes?': l10n.e7SetupDiscardChanges,
  'Restarting local server': l10n.e7SetupRestartingLocalStage,
  'Terminal command input': l10n.e7SetupCommandInput,
  'Choose how to continue': l10n.e7SetupChooseContinue,
  'Run as a Linux service': l10n.e7SetupLinuxService,
  'Failure report copied.': l10n.e7SetupReportCopied,
  'Reading setup progress': l10n.e7SetupReadingProgress,
  'Follow the server log': l10n.e7SetupFollowLog,
  'No terminal processes': l10n.e7SetupNoTerminals,
  'Paste server password': l10n.e7SetupPastePassword,
  'Starting local server': l10n.e7SetupStartingLocal,
  'Show server password': l10n.e7SetupShowPassword,
  'Open the setup guide': l10n.e7SetupOpenSetupGuide,
  'Hide server password': l10n.e7SetupHidePassword,
  'Interrupt, Control C': l10n.e7SetupInterruptKey,
  'This device (Termux)': l10n.e7SetupThisDevice,
  'Getting models ready': l10n.e7SetupPreparingModels,
  'Setup output copied.': l10n.e7SetupOutputCopied,
  'Local server stopped': l10n.e7SetupStoppedLocal,
  'Installing OpenCode': l10n.e7SetupInstallingOpenCode,
  'Username (optional)': l10n.e7SetupUsername,
  'Copy failure report': l10n.e7SetupCopyFailureReport,
  'Terminal transcript': l10n.e7SetupTranscript,
  'Enter a server URL.': l10n.e7SetupEnterUrl,
  'Unknown setup state': l10n.e7SetupUnknownSetup,
  'Copy & open Termux': l10n.e7SetupCopyOpenTermux,
  'Restart the server': l10n.e7SetupRestartServer,
  'Paste pairing code': l10n.e7SetupPastePairing,
  'Checking Termux...': l10n.e7SetupCheckingTermuxShort,
  'Connection failed.': l10n.e7SetupConnectionFailed,
  'Verify & continue': l10n.e7SetupVerifyContinue,
  'Setting up Ubuntu': l10n.e7SetupInstallingUbuntu,
  'not yet available': l10n.e7SetupStepUnavailable,
  'Scan pairing code': l10n.e7SetupScanPairing,
  'Connection closed': l10n.e7SetupConnectionClosed,
  'Close the scanner': l10n.e7SetupCloseScanner,
  'Re-enter password': l10n.e7SetupReenterPassword,
  'Open app settings': l10n.e7SetupOpenAppSettings,
  'Stop local server': l10n.e7SetupStopLocal,
  '<invalid address>': l10n.e7SetupInvalidAddress,
  'OpenCode is ready': l10n.e7SetupOpenCodeReady,
  'Paste it instead': l10n.e7SetupPasteInstead,
  'Paste an API key': l10n.e7SetupApiKeyHint,
  'Resume live view': l10n.e7SetupResumeLive,
  'Terminal actions': l10n.e7SetupTerminalActions,
  'Remove terminal?': l10n.e7SetupRemoveTerminal,
  'Preparing Termux': l10n.e7SetupPrepareTermux,
};
