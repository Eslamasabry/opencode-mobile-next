import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../domain/genui/gen_ui.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart' show connProvider;
import '../../kit/kit.dart';

/// The connection's agent-card surface, or null where it has none (a test
/// without a connection): the Cards row then draws nothing.
final genUiControllerProvider = Provider<GenUiController?>((ref) {
  try {
    return ref.watch(connProvider);
  } catch (_) {
    return null;
  }
});

/// The names of the agents a status covers, once each, in the app's words.
String _agentNames(AppLocalizations l10n, List<GenUiAgent> agents) {
  final names = <String>[];
  for (final agent in agents) {
    // Product names come from each agent's adapter (one per agent).
    final name = agent.displayName;
    if (!names.contains(name)) names.add(name);
  }
  return names.join(l10n.localeName == 'ar' ? '، ' : ', ');
}

/// Why a setup step stopped, in plain words (never a command or a path).
/// A problem about particular agents ([affected]) names them.
String genUiProblemText(
  AppLocalizations l10n,
  GenUiSetupProblem problem, {
  List<GenUiAgent> affected = const [],
}) {
  final names = affected.isEmpty ? null : _agentNames(l10n, affected);
  return switch (problem) {
    GenUiSetupProblem.unsupportedHost => l10n.cardsProblemUnsupportedHost,
    GenUiSetupProblem.runtimeMissing => l10n.cardsProblemRuntimeMissing,
    GenUiSetupProblem.notQualified =>
      names == null
          ? l10n.cardsProblemNotQualified
          : l10n.cardsProblemNotQualifiedFor(names),
    GenUiSetupProblem.permissionDenied => l10n.cardsProblemPermissionDenied,
    GenUiSetupProblem.conflict => l10n.cardsProblemConflict,
    GenUiSetupProblem.installationFailed => l10n.cardsProblemInstallationFailed,
    GenUiSetupProblem.registrationFailed =>
      names == null
          ? l10n.cardsProblemRegistrationFailed
          : l10n.cardsProblemRegistrationFailedFor(names),
    GenUiSetupProblem.verificationFailed =>
      names == null
          ? l10n.cardsProblemVerificationFailed
          : l10n.cardsProblemVerificationFailedFor(names),
    GenUiSetupProblem.removalFailed =>
      names == null
          ? l10n.cardsProblemRemovalFailed
          : l10n.cardsProblemRemovalFailedFor(names),
    GenUiSetupProblem.storageFailed => l10n.cardsProblemStorageFailed,
    GenUiSetupProblem.busy => l10n.cardsProblemBusy,
  };
}

/// The status line under the switch: what is true now, per agent.
String genUiStatusText(AppLocalizations l10n, GenUiSetupStatus status) =>
    switch (status) {
      GenUiSetupOff() => l10n.cardsStatusOff,
      GenUiSetupInstalling() => l10n.cardsStatusChecking,
      GenUiSetupOn() => l10n.cardsStatusOn(_agentNames(l10n, status.agents)),
      GenUiSetupPartial() => l10n.cardsStatusPartial(
        _agentNames(l10n, status.agents),
        genUiProblemText(l10n, status.reason, affected: status.affected),
      ),
      GenUiSetupRestartRequired() => l10n.cardsStatusRestart(
        _agentNames(l10n, status.agents),
      ),
      GenUiSetupUnavailable() => l10n.cardsStatusUnavailable(
        genUiProblemText(l10n, status.reason, affected: status.affected),
      ),
      GenUiSetupFailed() => l10n.cardsStatusFailed(
        genUiProblemText(l10n, status.reason, affected: status.affected),
      ),
    };

/// Settings › Agents › Cards from agents: the switch and what it did, per
/// agent. Off blocks card actions at once; the status says what is still to
/// do (a restart) rather than claiming more than is verified.
class CardsFromAgentsRow extends ConsumerStatefulWidget {
  const CardsFromAgentsRow({super.key});

  @override
  ConsumerState<CardsFromAgentsRow> createState() => _CardsFromAgentsRowState();
}

class _CardsFromAgentsRowState extends ConsumerState<CardsFromAgentsRow> {
  bool _pending = false;
  bool _failed = false;

  Future<void> _set(GenUiController gen, bool on) async {
    setState(() {
      _pending = true;
      _failed = false;
    });
    try {
      await gen.setGenUiEnabled(on);
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    } finally {
      if (mounted) setState(() => _pending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final gen = ref.watch(genUiControllerProvider);
    if (gen == null) return const SizedBox.shrink();
    final listenable = gen is Listenable ? gen as Listenable : null;
    return ListenableBuilder(
      listenable: listenable ?? ValueNotifier<int>(0),
      builder: (context, _) {
        final l10n = AppLocalizations.of(context);
        final tokens = KitTokens.of(context);
        final status = _failed
            ? l10n.cardsSettingFailed
            : genUiStatusText(l10n, gen.genUiStatus);
        return KitRowGroup(
          key: const Key('cards-from-agents'),
          leadingIcons: false,
          gapBefore: tokens.space4,
          children: [
            KitSwitchRow(
              title: l10n.cardsFromAgentsTitle,
              supporting: l10n.cardsFromAgentsSupporting,
              value: gen.genUiEnabled,
              switchKey: const Key('cards-from-agents-switch'),
              onChanged: _pending ? null : (on) => unawaited(_set(gen, on)),
              disabledReason: _pending ? l10n.cardsStatusChecking : null,
              below: KitText(
                status,
                key: const Key('cards-from-agents-status'),
                role: KitTextRole.secondary,
                tone: _failed ? KitTextTone.danger : KitTextTone.secondary,
              ),
            ),
          ],
        );
      },
    );
  }
}
