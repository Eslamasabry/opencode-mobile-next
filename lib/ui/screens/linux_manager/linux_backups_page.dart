/// Linux › Backups (S3, docs/design/linux-manager-2026-10-31/3-ui.md §2.6):
/// choose what to back up (setup, setup and projects, or projects only),
/// one primary at the thumb, and a visible "Restore from a backup" row.
/// The job's progress is the shared [LinuxJobPanel]; saving the zip shows
/// its own bar. How it ended is one notice: saved (size and name), restored
/// (what came back), or a problem in plain words with the technical text
/// under Details.
///
/// "Projects only" is the former This phone › Export projects (owner,
/// 2026-10-31), opened as it was.
///
/// Kit parts only.
library;

import 'dart:async';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:path_provider/path_provider.dart';

import '../../../builtin/builtin_server.dart' show builtinLinuxProvider;
import '../../../builtin/linux_backup/linux_backup.dart';
import '../../../builtin/linux_backup/linux_backup_plan.dart';
import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../builtin/linux_manager/linux_packages.dart';
import '../../../builtin/linux_services/linux_services.dart';
import '../../../builtin/project_export.dart';
import '../../../builtin/project_export_controller.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../manage_space_screen.dart' show ProjectExportScreen;
import 'linux_manager_common.dart';

/// What the page offers to back up.
enum LinuxBackupChoice { setup, setupAndProjects, projectsOnly }

Future<void> openLinuxBackups(BuildContext context) => pushKitPage<void>(
  context,
  (_) => const LinuxBackupsPage(),
  settings: const RouteSettings(name: 'linux-backups'),
);

class LinuxBackupsPage extends ConsumerStatefulWidget {
  const LinuxBackupsPage({super.key, this.controller, this.openProjects});

  /// A test's controller; the real one otherwise.
  final LinuxBackupController? controller;

  /// Opens "Projects only" (a test's stand-in; Export projects otherwise).
  final Future<void> Function(BuildContext context)? openProjects;

  @override
  ConsumerState<LinuxBackupsPage> createState() => _LinuxBackupsPageState();
}

class _LinuxBackupsPageState extends ConsumerState<LinuxBackupsPage> {
  late final LinuxBackupController _c;
  late final bool _owns;
  LinuxBackupChoice _choice = LinuxBackupChoice.setupAndProjects;
  AppLocalizations? _l10n;

  @override
  void initState() {
    super.initState();
    _owns = widget.controller == null;
    _c = widget.controller ?? _real();
  }

  LinuxBackupController _real() {
    final linux = ref.read(builtinLinuxProvider);
    return LinuxBackupController(
      jobs: ref.read(linuxJobProvider),
      exporter: MethodChannelProjectExport(),
      prefs: ref.read(connProvider).store.prefs,
      notice: (kind) {
        final l10n = _l10n;
        if (l10n == null) return '';
        return kind == LinuxJobKind.restore
            ? l10n.linuxMgrJobRestoring
            : l10n.linuxMgrJobBackingUp;
      },
      pickBackupFile: () async {
        final file = await FilePicker.pickFile(
          type: FileType.custom,
          allowedExtensions: const ['zip'],
        );
        return file?.path;
      },
      afterRestore: () =>
          ref.read(linuxServicesProvider).ensureSupervisor().catchError((_) {}),
      appVersion: () async => (await PackageInfo.fromPlatform()).version,
      abi: () async => (await linux.status()).abi,
      tempDir: getTemporaryDirectory,
    );
  }

  @override
  void dispose() {
    if (_owns) _c.dispose();
    super.dispose();
  }

  Future<void> _go() async {
    switch (_choice) {
      case LinuxBackupChoice.projectsOnly:
        final open = widget.openProjects;
        if (open != null) {
          await open(context);
        } else {
          await pushKitPage<void>(
            context,
            (_) => ProjectExportScreen(
              controller: ProjectExportController(
                platform: MethodChannelProjectExport(),
              ),
            ),
          );
        }
      case LinuxBackupChoice.setup:
        await _c.backUp(LinuxBackupKind.setup);
      case LinuxBackupChoice.setupAndProjects:
        await _c.backUp(LinuxBackupKind.setupAndProjects);
    }
  }

  Future<void> _restore(AppLocalizations l10n) async {
    await _c.chooseBackup();
    final read = _c.manifest;
    if (!mounted || _c.phase != LinuxBackupPhase.ready || read == null) return;
    final what = read.hasProjects
        ? l10n.linuxBackupSetupProjects
        : l10n.linuxBackupSetup;
    final yes = await showKitConfirm(
      context,
      title: l10n.linuxRestoreTitle,
      body: l10n.linuxRestoreBody(
        what,
        MaterialLocalizations.of(context).formatMediumDate(read.created),
      ),
      confirmLabel: l10n.linuxRestoreConfirm,
      icon: AppIconography.restore,
      consequenceItems: [
        if (read.hasProjects)
          KitConsequence(
            l10n.linuxRestoreKeptProjects,
            mark: KitConsequenceMark.kept,
          ),
        KitConsequence(
          l10n.linuxRestoreReplacedSettings,
          mark: KitConsequenceMark.info,
        ),
        KitConsequence(
          l10n.linuxRestoreAddedTools,
          mark: KitConsequenceMark.info,
        ),
      ],
      sheetKey: const ValueKey('linux-restore-sheet'),
      confirmKey: const ValueKey('linux-restore-confirm'),
    );
    if (!mounted) return;
    if (yes) {
      await _c.restore();
    } else {
      _c.reset();
    }
  }

  String _problem(AppLocalizations l10n, LinuxBackupProblem? problem) =>
      switch (problem) {
        LinuxBackupProblem.busy => l10n.linuxBackupProblemBusy,
        LinuxBackupProblem.linuxMissing => l10n.linuxBackupProblemMissing,
        LinuxBackupProblem.saveFailed => l10n.linuxBackupProblemSave,
        LinuxBackupProblem.notABackup => l10n.linuxBackupProblemNotBackup,
        LinuxBackupProblem.newerBackup => l10n.linuxBackupProblemNewer,
        LinuxBackupProblem.jobFailed || null => l10n.linuxBackupProblemJob,
      };

  Widget? _outcome(BuildContext context, AppLocalizations l10n) {
    final c = _c;
    switch (c.phase) {
      case LinuxBackupPhase.saving:
        final total = c.totalBytes;
        return Column(
          key: const ValueKey('linux-backup-saving'),
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(l10n.linuxMgrJobBackingUp, role: KitTextRole.headline),
            SizedBox(height: KitTokens.of(context).space1),
            KitProgressView(
              progress: total <= 0
                  ? const KitProgress.waiting()
                  : KitProgress.known(
                      (c.savedBytes / total).clamp(0, 1).toDouble(),
                      caption: l10n.linuxBackupSaving(
                        linuxSize(c.savedBytes),
                        linuxSize(total),
                      ),
                    ),
            ),
          ],
        );
      case LinuxBackupPhase.checking:
        return KitProgressView(
          key: const ValueKey('linux-restore-checking'),
          progress: KitProgress.waiting(caption: l10n.linuxRestoreChecking),
        );
      case LinuxBackupPhase.saved:
        return KitNotice(
          key: const ValueKey('linux-backup-saved'),
          tone: AppStatusTone.ok,
          icon: AppIconography.check,
          message: l10n.linuxBackupSaved(
            linuxSize(c.totalBytes),
            c.fileName ?? '',
          ),
          actions: [
            KitAction(label: l10n.linuxMgrJobDismiss, onPressed: c.reset),
          ],
        );
      case LinuxBackupPhase.restored:
        final s = c.summary ?? const LinuxRestoreSummary();
        int n(String key) => s.counts[key] ?? 0;
        return KitNotice(
          key: const ValueKey('linux-restore-done'),
          tone: AppStatusTone.ok,
          icon: AppIconography.check,
          title: l10n.linuxRestoreDone,
          message: l10n.linuxRestoreCounts(
            '${n('projects')}',
            '${n('services')}',
            '${n('settings')}',
            '${n('packages')}',
          ),
          notes: [
            if (s.skipped.isNotEmpty)
              l10n.linuxRestoreSkipped(s.skipped.join(', ')),
          ],
          actions: [
            KitAction(label: l10n.linuxMgrJobDismiss, onPressed: c.reset),
          ],
        );
      case LinuxBackupPhase.failed:
        final restoring =
            c.problem == LinuxBackupProblem.notABackup ||
            c.problem == LinuxBackupProblem.newerBackup ||
            c.manifest != null;
        final technical = c.details;
        return KitNotice(
          key: const ValueKey('linux-backup-failed'),
          tone: AppStatusTone.failure,
          icon: AppIconography.error,
          title: restoring
              ? l10n.linuxRestoreFailedTitle
              : l10n.linuxBackupFailedTitle,
          message:
              c.manifest != null && c.problem == LinuxBackupProblem.jobFailed
              ? l10n.linuxBackupProblemRestore
              : _problem(l10n, c.problem),
          actions: [
            KitAction(label: l10n.linuxMgrJobDismiss, onPressed: c.reset),
            if (technical != null && technical.isNotEmpty)
              KitAction.copy(label: l10n.kitCopyDetails, text: () => technical),
          ],
        );
      case LinuxBackupPhase.idle ||
          LinuxBackupPhase.preparing ||
          LinuxBackupPhase.ready ||
          LinuxBackupPhase.restoring:
        return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    _l10n = l10n;
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: _c,
      builder: (context, _) {
        final busy = _c.busy;
        final outcome = _outcome(context, l10n);
        final label = switch (_choice) {
          LinuxBackupChoice.setup => l10n.linuxBackupNowSetup,
          LinuxBackupChoice.setupAndProjects =>
            l10n.linuxBackupNowSetupProjects,
          LinuxBackupChoice.projectsOnly => l10n.linuxBackupNowProjects,
        };
        return KitScreen(
          topBar: KitTopBar(title: l10n.linuxBackupRow),
          width: KitScreenWidth.reading,
          bottom: KitButton.primary(
            key: const ValueKey('linux-backup-go'),
            label: label,
            hero: true,
            working:
                _c.phase == LinuxBackupPhase.preparing ||
                _c.phase == LinuxBackupPhase.saving,
            onPressed: busy ? null : () => unawaited(_go()),
          ),
          body: ListView(
            key: const ValueKey('linux-backups'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              const LinuxJobPanel(),
              if (outcome != null)
                Padding(padding: linuxRails(context), child: outcome),
              KitSectionLabel(l10n.linuxBackupWhat),
              KitChoiceList<LinuxBackupChoice>.single(
                key: const ValueKey('linux-backup-choice'),
                actsOnTap: false,
                selected: _choice,
                onSelected: (value) {
                  if (!busy) setState(() => _choice = value);
                },
                choices: [
                  KitChoice(
                    value: LinuxBackupChoice.setup,
                    title: l10n.linuxBackupSetup,
                    supporting: l10n.linuxBackupSetupDetail,
                  ),
                  KitChoice(
                    value: LinuxBackupChoice.setupAndProjects,
                    title: l10n.linuxBackupSetupProjects,
                    supporting: l10n.linuxBackupSetupProjectsDetail,
                  ),
                  KitChoice(
                    value: LinuxBackupChoice.projectsOnly,
                    title: l10n.linuxBackupProjectsOnly,
                    supporting: l10n.linuxBackupProjectsOnlyDetail,
                  ),
                ],
              ),
              SizedBox(height: tokens.sectionGap),
              KitRowGroup(
                children: [
                  KitRow(
                    key: const ValueKey('linux-restore-row'),
                    leading: KitRow.icon(context, AppIconography.restore),
                    title: l10n.linuxRestoreRow,
                    supporting: TextSpan(text: l10n.linuxRestoreRowDetail),
                    supportingMaxLines: 2,
                    trailing: const KitChevron(),
                    enabled: !busy,
                    onTap: () => unawaited(_restore(l10n)),
                  ),
                ],
              ),
              SizedBox(height: tokens.space3),
              Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                ),
                child: KitText(
                  l10n.linuxBackupPrivacy,
                  role: KitTextRole.caption,
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
