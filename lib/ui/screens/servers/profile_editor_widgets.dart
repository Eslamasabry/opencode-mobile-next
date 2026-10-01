part of '../servers_screen.dart';

/// The one command that starts the chosen agent, with a copy button, and the
/// rest of the setup guide's commands behind "Show the commands" (UX plan
/// 5.9). First run is the moment the person is at their computer with a
/// terminal open; the guide screen stays in Settings → Help for later.
class _ComputerCommand extends StatelessWidget {
  const _ComputerCommand({super.key, required this.backend, this.below});

  final ServerBackend backend;

  /// What to do with what the command prints (OpenCode's pairing buttons),
  /// between the command and the rarer commands.
  final Widget? below;

  @override
  Widget build(BuildContext context) {
    final copy = _connectionL10n(context);
    final tokens = KitTokens.of(context);
    Widget caption(String text) => Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitText(
        text,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
      ),
    );
    Widget command(String text, {Key? key, String? caption}) => Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space2),
      child: KitCodeBlock(
        key: key,
        text: text,
        kind: KitCodeKind.command,
        caption: caption,
        copyLabel: copy.handoffCopyCommand,
      ),
    );
    return Column(
      key: const ValueKey('connect-computer-command'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _Rails(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // The block's header says what to do with it, beside its copy
              // button.
              command(
                SetupCommands.startFor(backend),
                key: const ValueKey('connect-command'),
                caption: copy.firstRunRunOnComputer,
              ),
              if (below case final below?) ...[
                SizedBox(height: tokens.space3),
                below,
              ],
            ],
          ),
        ),
        KitExpandRow(
          key: const ValueKey('connect-show-commands'),
          title: copy.firstRunShowCommands,
          children: [
            _Rails(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: switch (backend) {
                  ServerBackend.openCode => [
                    caption(copy.e7SharedServersStartedWithOpencodeServeDoNot),
                    command(SetupCommands.legacyServe),
                    caption(copy.e7SharedThenAddTheServerManuallyWithUsername),
                  ],
                  ServerBackend.paseo => [
                    caption(copy.firstRunCommandsPaseoNetwork),
                    command(SetupCommands.paseoStartPrivateNetwork),
                  ],
                  ServerBackend.codex => [
                    caption(copy.firstRunCommandsCodexToken),
                    command(SetupCommands.codexToken),
                    caption(copy.firstRunCommandsCodexUsb),
                    command(SetupCommands.codexUsb),
                  ],
                },
              ),
            ),
          ],
        ),
      ],
    );
  }
}

/// Pairing, the main way to add an OpenCode server: the command on the
/// computer prints a code (a QR and a line to copy) carrying the address,
/// the username and the per-run password together, so scanning or pasting
/// it is strictly less work than typing them.
///
/// On a new server it is two equal buttons, Scan code and Paste code (just
/// Paste pairing code where there is no camera), under the line that says
/// the next move. Editing a saved server, it is two text buttons under the
/// fields ([compact]): pairing again after the password rotated.
class _PairingActions extends StatelessWidget {
  const _PairingActions({
    this.instructions,
    this.compact = false,
    required this.busy,
    required this.notice,
    required this.failure,
    required this.onPaste,
    required this.onScan,
  });

  /// The next move, under the command shown above with its copy button.
  final String? instructions;
  final bool compact;
  final bool busy;
  final String? notice;
  final String? failure;
  final VoidCallback? onPaste;

  /// Null wherever there is no camera path — desktop, and anywhere else
  /// `supportsQrPairing` says no. The button is then not built at all, so no
  /// camera code is reachable and nothing offers what it cannot do.
  final VoidCallback? onScan;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final notice = this.notice;
    final failure = this.failure;
    final scan = onScan;
    final Widget buttons;
    if (compact) {
      buttons = KitInset(
        child: Wrap(
          spacing: tokens.space1,
          children: [
            KitButton.tertiary(
              key: const ValueKey('server-pairing-paste'),
              onPressed: busy ? null : onPaste,
              icon: AppIconography.paste,
              label: busy ? copy.e7SetupPairing : copy.e7SetupPastePairing,
            ),
            if (scan != null)
              KitButton.tertiary(
                key: const ValueKey('server-pairing-scan'),
                onPressed: busy ? null : scan,
                icon: AppIconography.qrCode,
                label: copy.addServerScan,
              ),
          ],
        ),
      );
    } else {
      // The spinner is only the code being checked; the drawing above and
      // the notice below say what was found.
      final paste = KitButton.secondary(
        key: const ValueKey('server-pairing-paste'),
        onPressed: onPaste,
        working: busy,
        icon: AppIconography.paste,
        maxLines: 1,
        label: scan == null ? copy.e7SetupPastePairing : copy.addServerPaste,
      );
      buttons = scan == null
          ? paste
          : Row(
              children: [
                Expanded(
                  child: KitButton.secondary(
                    key: const ValueKey('server-pairing-scan'),
                    onPressed: busy ? null : scan,
                    icon: AppIconography.qrCode,
                    maxLines: 1,
                    label: copy.addServerScan,
                  ),
                ),
                SizedBox(width: tokens.space3),
                Expanded(child: paste),
              ],
            );
    }
    return Column(
      key: const ValueKey('server-pairing-actions'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (instructions case final line?) ...[
          KitText(
            line,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
          SizedBox(height: tokens.space3),
        ],
        buttons,
        // What the code said unfolds under the buttons and folds away when
        // the next try starts (design standard §10).
        KitReveal(
          child: notice == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space2),
                  child: KitNotice(
                    key: const ValueKey('server-pairing-notice'),
                    tone: AppStatusTone.ok,
                    message: notice,
                  ),
                ),
        ),
        KitReveal(
          child: failure == null
              ? null
              : Padding(
                  padding: EdgeInsetsDirectional.only(top: tokens.space2),
                  child: KitNotice(
                    key: const ValueKey('server-pairing-failure'),
                    tone: AppStatusTone.failure,
                    message: setupUiMessage(copy, failure),
                  ),
                ),
        ),
      ],
    );
  }
}

/// What a connection check found (design standard §3, a notice on the form's
/// rails, never a filled block): which OpenCode answered and what to do next,
/// or why it did not, with the setup guide when nothing seems to be there
/// and, after Save & connect, "Save anyway".
class _ProbeVerdict extends StatelessWidget {
  const _ProbeVerdict({required this.result, this.saveAnyway});

  final ServerProbeResult result;
  final KitAction? saveAnyway;

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    if (result.ok) {
      final version = result.version ?? copy.e7SetupUnknownVersion;
      return KitNotice(
        key: const ValueKey('server-test-success'),
        tone: AppStatusTone.ok,
        title: result.flavor == ServerFlavor.v2
            ? copy.e7SetupProbeV2(version)
            : copy.e7SetupProbeV1(version),
        message: copy.e7SetupSaveToFinish,
        notes: [if (result.flavor == ServerFlavor.v1) copy.e7SetupV1Limited],
      );
    }
    // A check that failed before the server could answer carries the raw
    // error (a socket or TLS message): plain words lead, the error waits
    // under Details.
    final raw = _rawCheckError(result.message!);
    return _WithDetails(
      details: raw,
      detailsKey: const ValueKey('server-test-failure-details'),
      child: KitNotice(
        key: const ValueKey('server-test-failure'),
        tone: AppStatusTone.failure,
        // A missing password answers 401 on OpenCode 1 and 2 alike; which
        // one it is shows once the password is in.
        title: result.flavor == ServerFlavor.v2 && !result.needsPassword
            ? copy.e7SetupIsV2
            : null,
        message: raw != null
            ? copy.addServerCheckFailedPlain
            : setupUiMessage(copy, result.message!),
        notes: [if (result.suggestsMissingServer) copy.e7SetupNoServerGuide],
        actions: [
          if (result.suggestsMissingServer)
            KitAction(
              key: const ValueKey('server-test-guide'),
              label: copy.e7SetupOpenSetupGuide,
              onPressed: () => Navigator.pushNamed(context, '/guide'),
            ),
          ?saveAnyway,
        ],
      ),
    );
  }
}

/// The raw error inside a probe's "Connection test failed: …" message, or
/// null for the probe's own plain verdicts.
String? _rawCheckError(String message) {
  const prefix = 'Connection test failed: ';
  if (!message.startsWith(prefix)) return null;
  final raw = message.substring(prefix.length).trim();
  return raw.isEmpty ? null : raw;
}

/// A verdict with its technical text folded under Details right below it
/// (no raw errors as copy: the words lead, the text waits, redacted).
class _WithDetails extends StatelessWidget {
  const _WithDetails({
    required this.child,
    required this.details,
    required this.detailsKey,
  });

  final Widget child;
  final String? details;
  final Key detailsKey;

  @override
  Widget build(BuildContext context) {
    final text = details;
    if (text == null || text.isEmpty) return child;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        child,
        KitDetailsFold(key: detailsKey, text: text),
      ],
    );
  }
}
