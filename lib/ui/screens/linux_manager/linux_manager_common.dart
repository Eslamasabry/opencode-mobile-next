/// Parts the Linux manager's pages share: errors in plain words, sizes, the
/// names of what runs, and the panel that follows the one package job.
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../builtin/linux_manager/linux_packages.dart';
import '../../../builtin/linux_manager/linux_processes.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/phone_server_card.dart' show formatPhoneStorage;

/// A problem in plain words. The phone's own text never appears here.
String linuxProblemText(AppLocalizations l10n, Object error) {
  if (error is! LinuxManagerException) return l10n.linuxMgrProblemFailed;
  return switch (error.problem) {
    LinuxProblem.busy => l10n.linuxMgrProblemBusy,
    LinuxProblem.setupRunning => l10n.linuxMgrProblemSetup,
    LinuxProblem.jobRunning => l10n.linuxMgrProblemJob,
    LinuxProblem.unavailable => l10n.linuxMgrProblemUnavailable,
    LinuxProblem.failed => l10n.linuxMgrProblemFailed,
  };
}

/// The technical text behind a problem, redacted, for a Details fold.
String? linuxProblemDetails(Object error) {
  if (error is LinuxManagerException && error.details != null) {
    return KitRedact.text(error.details!);
  }
  return null;
}

/// Sizes as the rest of This phone shows them.
String linuxSize(int bytes) =>
    bytes < 1024 ? '0 KB' : formatPhoneStorage(bytes);

String linuxKindLabel(AppLocalizations l10n, LinuxProcessKind kind) =>
    switch (kind) {
      LinuxProcessKind.openCodeServer => l10n.linuxMgrKindServer,
      LinuxProcessKind.paseo => l10n.linuxMgrKindPaseo,
      LinuxProcessKind.agent => l10n.linuxMgrKindAgent,
      LinuxProcessKind.devServer => l10n.linuxMgrKindDev,
      LinuxProcessKind.other => l10n.linuxMgrKindOther,
    };

IconData linuxKindIcon(LinuxProcessKind kind) => switch (kind) {
  LinuxProcessKind.openCodeServer => AppIconography.server,
  LinuxProcessKind.paseo => AppIconography.agent,
  LinuxProcessKind.agent => AppIconography.code,
  LinuxProcessKind.devServer => AppIconography.tools,
  LinuxProcessKind.other => AppIconography.processor,
};

/// What a row calls a process: the server and Paseo by their names, the
/// rest by the program a person would recognise.
String linuxProcessTitle(AppLocalizations l10n, LinuxProcess process) =>
    switch (process.kind) {
      LinuxProcessKind.openCodeServer => l10n.linuxMgrKindServer,
      LinuxProcessKind.paseo => l10n.linuxMgrKindPaseo,
      _ => process.programName,
    };

/// A page's list inside the gutters the kit pages use.
EdgeInsetsDirectional linuxRails(BuildContext context) {
  final tokens = KitTokens.of(context);
  return EdgeInsetsDirectional.only(
    start: tokens.gutter,
    end: tokens.gutter,
    bottom: tokens.sectionGap,
  );
}

/// The one package job, wherever the person is: running with its progress
/// and a named Stop, or how it ended. Nothing when there is no job to show.
/// The job outlives pages, so every page that can start one shows this.
class LinuxJobPanel extends ConsumerWidget {
  const LinuxJobPanel({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final controller = ref.watch(linuxJobProvider);
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) =>
          _JobBody(state: controller.state, controller: controller),
    );
  }
}

class _JobBody extends StatelessWidget {
  const _JobBody({required this.state, required this.controller});

  final LinuxJobState state;
  final LinuxJobController controller;

  String _names(LinuxJobState s) => s.names.join(', ');

  String _title(AppLocalizations l10n) => switch (state.kind) {
    LinuxJobKind.install => l10n.linuxMgrJobInstalling(_names(state)),
    LinuxJobKind.remove => l10n.linuxMgrJobRemoving(_names(state)),
    _ => l10n.linuxMgrJobUpdating,
  };

  String _stopLabel(AppLocalizations l10n) => switch (state.kind) {
    LinuxJobKind.install => l10n.linuxMgrJobStopInstall(_names(state)),
    LinuxJobKind.remove => l10n.linuxMgrJobStopRemove(_names(state)),
    _ => l10n.linuxMgrJobStopUpdate,
  };

  Future<void> _stop(BuildContext context, AppLocalizations l10n) async {
    final names = _names(state);
    final yes = await showKitConfirm(
      context,
      title: state.kind == LinuxJobKind.install
          ? l10n.linuxMgrJobStopInstallTitle(names)
          : state.kind == LinuxJobKind.remove
          ? l10n.linuxMgrJobStopRemoveTitle(names)
          : l10n.linuxMgrJobStopUpdateTitle,
      body: l10n.linuxMgrJobStopBody,
      confirmLabel: state.kind == LinuxJobKind.install
          ? l10n.linuxMgrJobStopInstall(names)
          : state.kind == LinuxJobKind.remove
          ? l10n.linuxMgrJobStopRemove(names)
          : l10n.linuxMgrJobStopUpdate,
      kind: KitConfirmKind.stop,
      icon: AppIcons.stop,
      sheetKey: const Key('linux-job-stop-sheet'),
      confirmKey: const Key('linux-job-stop-confirm'),
    );
    if (yes) await controller.stop();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final rails = linuxRails(context);
    final tokens = KitTokens.of(context);
    final status = state.status;
    if (status == LinuxJobStatus.none) return const SizedBox.shrink();
    if (status == LinuxJobStatus.running) {
      final percent = state.percent;
      return Padding(
        key: const Key('linux-job-running'),
        padding: rails,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(_title(l10n), role: KitTextRole.headline),
            SizedBox(height: tokens.space1),
            KitProgressView(
              progress: percent == null
                  ? const KitProgress.waiting()
                  : KitProgress.known(percent / 100, caption: '$percent%'),
            ),
            SizedBox(height: tokens.space1),
            KitText(l10n.linuxMgrJobKeepsGoing, role: KitTextRole.caption),
            SizedBox(height: tokens.space2),
            KitButton.secondary(
              key: const Key('linux-job-stop'),
              label: _stopLabel(l10n),
              icon: AppIcons.stop,
              onPressed: () => unawaited(_stop(context, l10n)),
            ),
          ],
        ),
      );
    }
    final done = status == LinuxJobStatus.done;
    final message = switch (status) {
      LinuxJobStatus.done => switch (state.kind) {
        LinuxJobKind.install => l10n.linuxMgrJobDoneInstall(_names(state)),
        LinuxJobKind.remove => l10n.linuxMgrJobDoneRemove(_names(state)),
        _ => l10n.linuxMgrJobDoneUpdate,
      },
      LinuxJobStatus.timedOut => l10n.linuxMgrJobTimedOut,
      LinuxJobStatus.interrupted => l10n.linuxMgrJobInterrupted,
      _ => state.wasBusy ? l10n.linuxMgrProblemBusy : l10n.linuxMgrJobFailed,
    };
    final technical = [
      ?state.stage,
      if (state.tail.isNotEmpty) state.tail,
    ].join('\n');
    return Padding(
      key: const Key('linux-job-outcome'),
      padding: rails,
      child: KitNotice(
        tone: done ? AppStatusTone.ok : AppStatusTone.failure,
        icon: done ? AppIconography.check : AppIconography.error,
        title: _title(l10n),
        message: message,
        messageKey: const Key('linux-job-message'),
        notes: const [],
        actions: [
          KitAction(
            key: const Key('linux-job-dismiss'),
            label: l10n.linuxMgrJobDismiss,
            onPressed: controller.dismiss,
          ),
          if (!done && technical.isNotEmpty)
            KitAction.copy(label: l10n.kitCopyDetails, text: () => technical),
        ],
      ),
    );
  }
}
