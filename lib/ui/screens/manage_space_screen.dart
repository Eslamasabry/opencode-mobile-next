// "Clear storage" guard (slice clear-storage-guard, 2026-09-28).
//
// Android's App info › Storage button opens ManageSpaceScreen first
// (android:manageSpaceActivity → ManageSpaceActivity → manageSpaceMain):
// what clearing deletes, measured from the files, what stays, and three
// acts — Export projects first, Clear the app's cache only, and Delete
// everything after a confirm that names the counts. This phone reaches the
// export part alone through ProjectExportScreen, to back up any time.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show SystemNavigator;

import '../../builtin/project_export.dart';
import '../../builtin/project_export_controller.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import '../widgets/phone_server_card.dart' show formatPhoneStorage;

class ManageSpaceScreen extends StatefulWidget {
  const ManageSpaceScreen({super.key, required this.controller, this.onClose});

  final ProjectExportController controller;

  /// Leaves the page (the activity ends); SystemNavigator.pop by default.
  final VoidCallback? onClose;

  @override
  State<ManageSpaceScreen> createState() => _ManageSpaceScreenState();
}

class _ManageSpaceScreenState extends State<ManageSpaceScreen> {
  ProjectExportController get _c => widget.controller;

  @override
  void initState() {
    super.initState();
    unawaited(_c.load());
  }

  Future<void> _confirmDelete() async {
    final l10n = AppLocalizations.of(context);
    final facts = _c.facts ?? AppStorageFacts.empty;
    await showKitConfirm(
      context,
      title: l10n.manageSpaceDeleteTitle,
      body: l10n.manageSpaceDeleteBody,
      confirmLabel: l10n.manageSpaceDeleteAll,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.delete,
      consequenceItems: [
        if (facts.serverInstalled)
          KitConsequence(
            l10n.manageSpaceLostServer,
            mark: KitConsequenceMark.lost,
          ),
        if (facts.projects.isNotEmpty)
          KitConsequence(
            l10n.manageSpaceLostProjects(
              facts.projects.length,
              formatPhoneStorage(facts.projectBytes),
            ),
            mark: KitConsequenceMark.lost,
          ),
        KitConsequence(
          l10n.manageSpaceLostSettings(facts.savedServers),
          mark: KitConsequenceMark.lost,
        ),
        KitConsequence(l10n.manageSpaceKeptAll, mark: KitConsequenceMark.kept),
      ],
      action: () async {
        // Android ends the app when it accepts; false is a refusal.
        if (!await _c.deleteEverything()) {
          throw StateError('clearApplicationUserData refused');
        }
      },
      sheetKey: const ValueKey('manage-space-delete-sheet'),
      confirmKey: const ValueKey('manage-space-delete-confirm'),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    return ListenableBuilder(
      listenable: _c,
      builder: (context, _) {
        final facts = _c.facts;
        return KitScreen(
          topBar: KitTopBar(
            title: l10n.manageSpaceTitle,
            exit: KitTopBarExit.close,
            onExit: widget.onClose ?? () => unawaited(SystemNavigator.pop()),
          ),
          width: KitScreenWidth.reading,
          loading: facts == null,
          loadingLabel: l10n.manageSpaceMeasuring,
          body: ListView(
            key: const ValueKey('manage-space'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              if (facts == null)
                const KitSkeletonRows()
              else
                Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Notices sit on the page's rails, like the groups.
                    for (final notice in [
                      KitNotice(
                        key: const ValueKey('manage-space-intro'),
                        icon: AppIconography.warning,
                        // It sends to Export only when the row is there.
                        message: canExportProjects(_c)
                            ? l10n.manageSpaceIntro
                            : l10n.manageSpaceIntroNothingToExport,
                      ),
                      ...projectExportOutcome(context, _c),
                      ..._cacheOutcome(l10n),
                    ])
                      _onRails(context, notice),
                    SizedBox(height: tokens.sectionGap),
                    _actions(context, l10n, facts),
                    SizedBox(height: tokens.sectionGap),
                    _deleted(context, l10n, facts),
                    SizedBox(height: tokens.sectionGap),
                    _kept(context, l10n),
                  ],
                ),
            ],
          ),
        );
      },
    );
  }

  List<Widget> _cacheOutcome(AppLocalizations l10n) => switch (_c.cachePhase) {
    CacheClearPhase.cleared => [
      KitNotice(
        key: const ValueKey('manage-space-cache-cleared'),
        tone: AppStatusTone.ok,
        message: l10n.manageSpaceCacheCleared(
          formatPhoneStorage(_c.cacheFreed),
        ),
      ),
    ],
    CacheClearPhase.failed => [
      KitNotice.error(
        key: const ValueKey('manage-space-cache-failed'),
        message: l10n.manageSpaceCacheFailed,
        retry: KitAction(
          label: l10n.manageSpaceTryAgain,
          onPressed: () => unawaited(_c.clearCache()),
        ),
      ),
    ],
    _ => const [],
  };

  Widget _actions(
    BuildContext context,
    AppLocalizations l10n,
    AppStorageFacts facts,
  ) {
    Widget icon(IconData data) => KitRow.icon(context, data);
    final cache = _c.cacheBytes;
    final busy = _c.exporting;
    return KitRowGroup(
      children: [
        ...projectExportRows(context, _c, title: l10n.manageSpaceExportFirst),
        KitRow(
          key: const ValueKey('manage-space-clear-cache'),
          leading: icon(AppIconography.clearAll),
          title: l10n.manageSpaceClearCache,
          supporting: TextSpan(
            text: cache != null && cache > 0
                ? l10n.manageSpaceClearCacheDetail(formatPhoneStorage(cache))
                : l10n.manageSpaceClearCacheKeeps,
          ),
          supportingMaxLines: 2,
          trailing: _c.cachePhase == CacheClearPhase.clearing
              ? const KitStatusMark(state: KitMarkState.working)
              : null,
          enabled: !busy && _c.cachePhase != CacheClearPhase.clearing,
          disabledReason: busy ? l10n.manageSpaceWaitForExport : null,
          onTap: () => unawaited(_c.clearCache()),
        ),
        KitRow(
          key: const ValueKey('manage-space-delete'),
          leading: icon(AppIconography.delete),
          title: l10n.manageSpaceDeleteAll,
          supporting: TextSpan(text: l10n.manageSpaceDeleteAllDetail),
          destructive: true,
          enabled: !busy,
          disabledReason: busy ? l10n.manageSpaceWaitForExport : null,
          onTap: () => unawaited(_confirmDelete()),
        ),
      ],
    );
  }

  Widget _deleted(
    BuildContext context,
    AppLocalizations l10n,
    AppStorageFacts facts,
  ) {
    Widget icon(IconData data) => KitRow.icon(context, data);
    return KitRowGroup(
      key: const ValueKey('manage-space-deleted'),
      label: l10n.manageSpaceDeletedLabel,
      children: [
        if (facts.serverInstalled)
          KitRow(
            key: const ValueKey('manage-space-server'),
            leading: icon(AppIconography.server),
            title: l10n.manageSpaceServer,
            supporting: TextSpan(text: l10n.manageSpaceServerDetail),
            supportingMaxLines: 2,
            trailing: facts.serverBytes > 0
                ? KitRowValue(
                    formatPhoneStorage(facts.serverBytes),
                    chevron: false,
                  )
                : null,
          ),
        for (final project in facts.projects)
          KitRow(
            key: ValueKey('manage-space-project-${project.name}'),
            leading: icon(AppIconography.projects),
            title: project.name,
            titleIsFileName: true,
            trailing: KitRowValue(
              formatPhoneStorage(project.bytes),
              chevron: false,
            ),
          ),
        KitRow(
          key: const ValueKey('manage-space-settings'),
          leading: icon(AppIconography.settings),
          title: l10n.manageSpaceSettings,
          supporting: TextSpan(
            text: l10n.manageSpaceSavedServers(facts.savedServers),
          ),
        ),
      ],
    );
  }

  Widget _kept(BuildContext context, AppLocalizations l10n) {
    Widget icon(IconData data) => KitRow.icon(context, data);
    return KitRowGroup(
      key: const ValueKey('manage-space-kept'),
      label: l10n.manageSpaceKeptLabel,
      children: [
        KitRow(
          leading: icon(AppIconography.computer),
          title: l10n.manageSpaceKeptComputers,
        ),
        KitRow(
          leading: icon(AppIconography.branch),
          title: l10n.manageSpaceKeptGit,
        ),
      ],
    );
  }
}

/// The export rows both pages share: the act (or its progress and Stop),
/// then whether sign-ins go too. Nothing when there is nothing to export.
List<Widget> projectExportRows(
  BuildContext context,
  ProjectExportController c, {
  required String title,
}) {
  final l10n = AppLocalizations.of(context);
  final facts = c.facts;
  if (facts == null || !canExportProjects(c)) return const [];
  Widget icon(IconData data) => KitRow.icon(context, data);
  final exporting = c.exporting;
  return [
    if (exporting) ...[
      KitProgressRow(
        key: const ValueKey('project-export-progress'),
        leading: icon(AppIconography.zip),
        title: l10n.projectExportRunning,
        tone: AppStatusTone.progress,
        value: c.phase == ProjectExportPhase.running && c.bytesTotal > 0
            ? c.bytesDone / c.bytesTotal
            : null,
        valueLabel: c.phase == ProjectExportPhase.running
            ? l10n.projectExportProgress(
                formatPhoneStorage(c.bytesDone),
                formatPhoneStorage(c.bytesTotal),
              )
            : l10n.projectExportPreparing,
      ),
      KitRow(
        key: const ValueKey('project-export-stop'),
        leading: icon(AppIconography.stop),
        title: l10n.projectExportStop,
        supporting: TextSpan(text: l10n.projectExportStopDetail),
        onTap: () => unawaited(c.cancel()),
      ),
    ] else
      KitRow(
        key: const ValueKey('project-export'),
        leading: icon(AppIconography.zip),
        title: title,
        supporting: TextSpan(
          text: facts.projects.isEmpty
              ? l10n.projectExportNoProjects
              : l10n.projectExportDetail(
                  facts.projects.length,
                  formatPhoneStorage(facts.projectBytes),
                ),
        ),
        supportingMaxLines: 2,
        trailing: const KitChevron(),
        onTap: () => unawaited(c.export()),
      ),
    KitSwitchRow(
      key: const ValueKey('project-export-private'),
      leading: icon(AppIconography.locked),
      title: l10n.projectExportPrivate,
      supporting: l10n.projectExportPrivateDetail,
      value: c.includePrivate,
      onChanged: exporting ? null : c.setIncludePrivate,
      disabledReason: exporting ? l10n.manageSpaceWaitForExport : null,
    ),
  ];
}

/// A notice on the page's rails (the screen gutter), where the row groups
/// sit, never against the window's edge.
Widget _onRails(BuildContext context, Widget notice) {
  final tokens = KitTokens.of(context);
  return Padding(
    padding: EdgeInsetsDirectional.symmetric(
      horizontal: tokens.gutter,
      vertical: tokens.space1,
    ),
    child: notice,
  );
}

/// Whether there is anything to export (projects, or the in-app server
/// that holds them): the export rows show only then.
bool canExportProjects(ProjectExportController c) {
  final facts = c.facts;
  return facts != null && (facts.projects.isNotEmpty || facts.serverInstalled);
}

/// How the last export ended, in words, with the way forward.
List<Widget> projectExportOutcome(
  BuildContext context,
  ProjectExportController c,
) {
  final l10n = AppLocalizations.of(context);
  final result = c.result;
  if (result == null || c.exporting) return const [];
  if (result.ok) {
    final left = c.facts?.privateFiles ?? 0;
    return [
      KitNotice(
        key: const ValueKey('project-export-done'),
        tone: AppStatusTone.ok,
        message: l10n.projectExportDone(
          formatPhoneStorage(result.bytes),
          result.files,
        ),
        notes: [
          if (c.exportedPrivate)
            l10n.projectExportDonePrivate
          else if (left > 0)
            l10n.projectExportDoneLeftOut(left),
        ],
      ),
    ];
  }
  if (result.failure == ProjectExportFailure.cancelled) {
    return [
      KitNotice(
        key: const ValueKey('project-export-stopped'),
        message: l10n.projectExportStopped,
      ),
    ];
  }
  final message = switch (result.failure) {
    ProjectExportFailure.destination => l10n.projectExportFailedDestination,
    ProjectExportFailure.space => l10n.projectExportFailedSpace,
    ProjectExportFailure.source => l10n.projectExportFailedSource,
    _ => l10n.projectExportFailed,
  };
  return [
    KitNotice.error(
      key: const ValueKey('project-export-failed'),
      message: message,
      details: result.detail.isEmpty ? null : result.detail,
      retry: KitAction(
        label: l10n.manageSpaceTryAgain,
        onPressed: () => unawaited(c.export()),
      ),
    ),
  ];
}

/// This phone › Export projects: the same export, any time.
class ProjectExportScreen extends StatefulWidget {
  const ProjectExportScreen({super.key, required this.controller});

  final ProjectExportController controller;

  @override
  State<ProjectExportScreen> createState() => _ProjectExportScreenState();
}

class _ProjectExportScreenState extends State<ProjectExportScreen> {
  @override
  void initState() {
    super.initState();
    unawaited(widget.controller.load());
  }

  @override
  void dispose() {
    widget.controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final c = widget.controller;
    return ListenableBuilder(
      listenable: c,
      builder: (context, _) {
        final facts = c.facts;
        return KitScreen(
          topBar: KitTopBar(title: l10n.thisPhoneExportProjects),
          width: KitScreenWidth.reading,
          loading: facts == null,
          loadingLabel: l10n.manageSpaceMeasuring,
          body: ListView(
            key: const ValueKey('project-export-page'),
            padding: EdgeInsetsDirectional.only(
              top: tokens.space2,
              bottom: KitScreen.endPadding(context),
            ),
            children: [
              if (facts == null)
                const KitSkeletonRows()
              else
                Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (final notice in projectExportOutcome(context, c))
                      _onRails(context, notice),
                    KitRowGroup(
                      children: [
                        ...projectExportRows(
                          context,
                          c,
                          title: l10n.projectExportSave,
                        ),
                        if (facts.projects.isEmpty && !facts.serverInstalled)
                          KitRow(
                            leading: KitRow.icon(
                              context,
                              AppIconography.projects,
                            ),
                            title: l10n.projectExportNoProjects,
                          ),
                      ],
                    ),
                    if (facts.projects.isNotEmpty) ...[
                      SizedBox(height: tokens.sectionGap),
                      KitRowGroup(
                        label: l10n.projectExportProjectsLabel,
                        children: [
                          for (final project in facts.projects)
                            KitRow(
                              leading: KitRow.icon(
                                context,
                                AppIconography.projects,
                              ),
                              title: project.name,
                              titleIsFileName: true,
                              trailing: KitRowValue(
                                formatPhoneStorage(project.bytes),
                                chevron: false,
                              ),
                            ),
                        ],
                      ),
                    ],
                  ],
                ),
            ],
          ),
        );
      },
    );
  }
}
