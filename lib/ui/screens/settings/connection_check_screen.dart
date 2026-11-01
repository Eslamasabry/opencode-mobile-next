import 'dart:async';

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../domain/server_gateway.dart' show StreamStatus;
import '../../../state/connection.dart';
import '../../../state/connection_check.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'reach_computer_screen.dart';

/// A server's page › Check connection: five steps, run once when the person
/// opens it and again on "Check again". Each step ends with a plain result
/// and, when it needs fixing, what to do next. No server text is shown.
class ConnectionCheckScreen extends StatefulWidget {
  const ConnectionCheckScreen({
    super.key,
    required this.controller,
    required this.profileId,
  });

  final ConnectionController controller;
  final String profileId;

  @override
  State<ConnectionCheckScreen> createState() => _ConnectionCheckScreenState();
}

class _ConnectionCheckScreenState extends State<ConnectionCheckScreen> {
  ConnectionCheck? _check;

  @override
  void initState() {
    super.initState();
    final profile = widget.controller.store.profiles
        .where((p) => p.id == widget.profileId)
        .firstOrNull;
    if (profile == null) return;
    _check = ConnectionCheck(profile: profile, streamView: _streamView)
      ..addListener(_changed);
    unawaited(_check!.run());
  }

  @override
  void dispose() {
    _check?.removeListener(_changed);
    _check?.dispose();
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  ConnectionStreamView _streamView() {
    final c = widget.controller;
    if (c.profile?.id != widget.profileId) {
      return ConnectionStreamView.notConnected;
    }
    return switch (c.status) {
      StreamStatus.connected => ConnectionStreamView.live,
      StreamStatus.connecting ||
      StreamStatus.reconnecting => ConnectionStreamView.connecting,
      StreamStatus.disconnected => ConnectionStreamView.down,
    };
  }

  KitMarkState _mark(ConnectionCheckState state) => switch (state) {
    ConnectionCheckState.running => KitMarkState.working,
    ConnectionCheckState.pass || ConnectionCheckState.warn => KitMarkState.done,
    ConnectionCheckState.fail => KitMarkState.failed,
    ConnectionCheckState.waiting ||
    ConnectionCheckState.skipped => KitMarkState.waiting,
  };

  String _title(AppLocalizations l10n, ConnectionCheckStep step) =>
      switch (step) {
        ConnectionCheckStep.address => l10n.connCheckStepAddress,
        ConnectionCheckStep.signIn => l10n.connCheckStepSignIn,
        ConnectionCheckStep.version => l10n.connCheckStepVersion,
        ConnectionCheckStep.liveUpdates => l10n.connCheckStepLive,
        ConnectionCheckStep.speed => l10n.connCheckStepSpeed,
      };

  String? _words(
    AppLocalizations l10n,
    ConnectionCheckStep step,
    ConnectionCheckResult r,
  ) {
    switch (r.state) {
      case ConnectionCheckState.waiting:
      case ConnectionCheckState.running:
        return null;
      case ConnectionCheckState.skipped:
        return r.reason == ConnectionCheckReason.streamNotChecked
            ? l10n.connCheckLiveNotChecked
            : l10n.connCheckSkipped;
      case ConnectionCheckState.pass:
      case ConnectionCheckState.warn:
      case ConnectionCheckState.fail:
        break;
    }
    return switch (r.reason) {
      ConnectionCheckReason.ok => switch (step) {
        ConnectionCheckStep.address => l10n.connCheckAddressOk,
        ConnectionCheckStep.signIn => l10n.connCheckSignInOk,
        ConnectionCheckStep.version =>
          r.value is String && (r.value as String).isNotEmpty
              ? l10n.connCheckVersionOk(r.value as String)
              : l10n.connCheckVersionOkUnknown,
        ConnectionCheckStep.liveUpdates => l10n.connCheckLiveOk,
        ConnectionCheckStep.speed => l10n.connCheckSpeedOk(
          (r.value as int?) ?? 0,
        ),
      },
      ConnectionCheckReason.addressUnreachable =>
        l10n.connCheckAddressUnreachable,
      ConnectionCheckReason.addressNotOpenCode =>
        l10n.connCheckAddressNotOpenCode,
      ConnectionCheckReason.needsPassword => l10n.connCheckNeedsPassword,
      ConnectionCheckReason.passwordRejected => l10n.connCheckPasswordRejected,
      ConnectionCheckReason.versionUnsupported =>
        l10n.connCheckVersionUnsupported,
      ConnectionCheckReason.versionNotReady => l10n.connCheckVersionNotReady,
      ConnectionCheckReason.streamNotChecked => l10n.connCheckLiveNotChecked,
      ConnectionCheckReason.streamConnecting => l10n.connCheckLiveConnecting,
      ConnectionCheckReason.streamDown => l10n.connCheckLiveDown,
      ConnectionCheckReason.slow =>
        '${l10n.connCheckSpeedOk((r.value as int?) ?? 0)} '
            '${l10n.connCheckSpeedSlow}',
      ConnectionCheckReason.earlierFailed => l10n.connCheckSkipped,
    };
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final check = _check;
    final running = check?.running ?? false;
    final reachFirst =
        check != null &&
        check.result(ConnectionCheckStep.address).state ==
            ConnectionCheckState.fail;
    return KitScreen(
      topBar: KitTopBar(title: l10n.connCheckTitle),
      width: KitScreenWidth.reading,
      body: Builder(
        builder: (context) => ListView(
          key: const ValueKey('connection-check-page'),
          padding: EdgeInsetsDirectional.only(
            top: tokens.space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
              ),
              child: KitText(l10n.connCheckIntro, role: KitTextRole.secondary),
            ),
            if (check != null)
              KitChecklist(
                checklistKey: const ValueKey('connection-check-steps'),
                steps: [
                  for (final step in ConnectionCheckStep.values)
                    KitStep(
                      key: ValueKey('connection-check-${step.name}'),
                      title: _title(l10n, step),
                      state: _mark(check.result(step).state),
                      supporting: _words(l10n, step, check.result(step)),
                    ),
                ],
              ),
            SizedBox(height: tokens.sectionGap),
            KitRowGroup(
              children: [
                KitRow(
                  key: const ValueKey('connection-check-again'),
                  leading: KitRow.icon(context, AppIconography.networkCheck),
                  title: running ? l10n.connCheckRunning : l10n.connCheckAgain,
                  titleAccent: true,
                  enabled: !running && check != null,
                  onTap: () => unawaited(check?.run()),
                ),
                KitRow(
                  key: const ValueKey('connection-check-reach-help'),
                  leading: KitRow.icon(context, AppIconography.network),
                  title: l10n.connCheckReachHelp,
                  supporting: TextSpan(text: l10n.connCheckReachHelpDetail),
                  titleAccent: reachFirst,
                  trailing: const KitChevron(),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute<void>(
                      builder: (_) => const ReachComputerScreen(),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
