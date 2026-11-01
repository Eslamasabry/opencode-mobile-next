import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../l10n/app_localizations.dart';
import '../../../update/app_update_controller.dart';
import '../../app_theme.dart';
import '../../kit/kit.dart';
import '../../widgets/external_link.dart';

/// Settings › This app › Updates: this build, whether a newer one waits,
/// what it brings, and the switch that lets the app look.
class UpdatesScreen extends ConsumerStatefulWidget {
  const UpdatesScreen({super.key, this.controller});

  /// Tests; the app reads [appUpdateProvider].
  final AppUpdateController? controller;

  @override
  ConsumerState<UpdatesScreen> createState() => _UpdatesScreenState();
}

class _UpdatesScreenState extends ConsumerState<UpdatesScreen> {
  late final AppUpdateController _updates =
      widget.controller ?? ref.read(appUpdateProvider);

  @override
  void initState() {
    super.initState();
    // This page is the one place the update shows: the app-wide status line
    // steps aside while it is open.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _updates.setPageOpen(true);
    });
  }

  @override
  void dispose() {
    final updates = _updates;
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => updates.setPageOpen(false),
    );
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitScreen(
      topBar: KitTopBar(title: l10n.appUpdatesTitle),
      width: KitScreenWidth.reading,
      body: ListenableBuilder(
        listenable: _updates,
        builder: (context, _) => _UpdatesBody(updates: _updates),
      ),
    );
  }
}

class _UpdatesBody extends StatelessWidget {
  const _UpdatesBody({required this.updates});

  final AppUpdateController updates;

  String _size(AppLocalizations l10n, Locale locale, int bytes) =>
      l10n.appUpdateSizeMb(
        NumberFormat.decimalPattern(
          locale.toString(),
        ).format((bytes / 1048576).ceil()),
      );

  String _checked(AppLocalizations l10n, DateTime at) {
    final d = DateTime.now().difference(at);
    if (d.inMinutes < 1) return l10n.appUpdateCheckedJustNow;
    final when = d.inMinutes < 60
        ? l10n.e7WorkspaceMinutesAgo(d.inMinutes)
        : d.inHours < 24
        ? l10n.e7WorkspaceHoursAgo(d.inHours)
        : l10n.e7WorkspaceDaysAgo(d.inDays);
    return l10n.appUpdateUpToDate(when);
  }

  String _failureWords(AppLocalizations l10n) => switch (updates.failure) {
    AppUpdateFailure.network || null => l10n.appUpdateFailNetwork,
    AppUpdateFailure.noFile => l10n.appUpdateFailNoFile,
    AppUpdateFailure.checksum => l10n.appUpdateFailChecksum,
    AppUpdateFailure.signer => l10n.appUpdateFailSigner,
    AppUpdateFailure.package => l10n.appUpdateFailPackage,
    AppUpdateFailure.version => l10n.appUpdateFailVersion,
    AppUpdateFailure.storage => l10n.appUpdateFailStorage,
    AppUpdateFailure.installer => l10n.appUpdateFailInstaller,
  };

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final locale = Localizations.localeOf(context);
    final tokens = KitTokens.of(context);
    final release = updates.release;
    final app = updates.installedApp;
    final phase = updates.phase;
    final size = updates.downloadSize;

    final failed = phase == AppUpdatePhase.failed;
    // The same file would be refused again: send people to the release.
    final retryHelps = switch (updates.failure) {
      AppUpdateFailure.signer ||
      AppUpdateFailure.package ||
      AppUpdateFailure.version => false,
      _ => true,
    };
    final String line;
    KitAction? action;
    switch (phase) {
      case AppUpdatePhase.checking:
        line = l10n.appUpdateChecking;
      case AppUpdatePhase.available || AppUpdatePhase.failed:
        line = release == null
            ? l10n.appUpdateNeverChecked
            : failed
            ? l10n.appUpdateNotInstalled(release.version)
            : l10n.appUpdateAvailableLine(
                release.version,
                size == null ? '' : _size(l10n, locale, size),
              );
        if (release != null && (retryHelps || release.pageUrl.isNotEmpty)) {
          action = KitAction(
            key: const ValueKey('updates-update'),
            label: !failed
                ? l10n.appUpdateUpdate
                : retryHelps
                ? l10n.appUpdateTryAgain
                : l10n.appUpdateOpenRelease,
            onPressed: failed && !retryHelps
                ? () => unawaited(openExternalLink(context, release.pageUrl))
                : () => unawaited(updates.updateNow()),
          );
        }
      case AppUpdatePhase.downloading:
        line = l10n.appUpdateDownloadingTitle(release?.version ?? '');
        action = KitAction(
          key: const ValueKey('updates-cancel'),
          label: l10n.appUpdateCancel,
          onPressed: updates.cancel,
        );
      case AppUpdatePhase.verifying:
        line = l10n.appUpdateVerifying;
      case AppUpdatePhase.installing:
        line = l10n.appUpdateInstalling;
      case AppUpdatePhase.readyToInstall:
        line = l10n.appUpdateInstallReadyTitle(release?.version ?? '');
        action = KitAction(
          key: const ValueKey('updates-install'),
          label: l10n.appUpdateInstall,
          onPressed: () => unawaited(updates.updateNow()),
        );
      case AppUpdatePhase.restartToUpdate:
        line = l10n.shorebirdUpdateReadyTitle;
      case AppUpdatePhase.none:
        final at = updates.checkedAt;
        line = at == null
            ? updates.enabled
                  ? l10n.appUpdateNeverChecked
                  : l10n.appUpdateCheckingOff
            : _checked(l10n, at);
    }
    final version = app == null
        ? l10n.appTitle
        : l10n.appUpdateCurrentVersion(app.version);
    final progress = updates.progress;

    return ListView(
      key: const ValueKey('updates-page'),
      padding: EdgeInsetsDirectional.only(
        top: tokens.space4,
        bottom: KitScreen.endPadding(context),
      ),
      children: [
        KitRowGroup(
          children: [
            KitRow(
              key: const ValueKey('updates-state'),
              leading: KitRow.icon(context, AppIconography.systemDownload),
              title: version,
              titleMaxLines: 2,
              supporting: TextSpan(text: line),
              supportingMaxLines: 3,
              action: action,
            ),
          ],
        ),
        if (phase == AppUpdatePhase.downloading)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space3,
            ),
            child: KitProgressView(
              progress: progress == null
                  ? const KitProgress.waiting()
                  : KitProgress.known(
                      progress,
                      caption: l10n.appUpdatePercent(
                        (progress * 100).floor().toString(),
                      ),
                    ),
            ),
          ),
        if (phase == AppUpdatePhase.failed) ...[
          SizedBox(height: tokens.space3),
          Padding(
            padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
            child: KitNotice(
              key: const ValueKey('updates-failure'),
              tone: AppStatusTone.failure,
              message: _failureWords(l10n),
            ),
          ),
        ],
        if (app != null)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space2,
            ),
            child: KitDetailsFold(
              foldKey: const ValueKey('updates-build-details'),
              values: [
                KitTechnicalValue(
                  l10n.appUpdateBuildLabel,
                  '${app.version}+${app.build}',
                ),
              ],
              text: failed ? updates.detail : null,
            ),
          ),
        if (phase == AppUpdatePhase.readyToInstall && updates.needsPermission)
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: tokens.gutter,
              end: tokens.gutter,
              top: tokens.space3,
            ),
            child: KitNotice(message: l10n.appUpdatePermissionNeeded),
          ),
        SizedBox(height: tokens.sectionGap),
        KitRowGroup(
          children: [
            KitSwitchRow(
              leading: KitRow.icon(context, AppIconography.systemDownload),
              switchKey: const ValueKey('updates-check-switch'),
              title: l10n.appUpdateCheckSwitch,
              supporting: l10n.appUpdateCheckSwitchBody,
              supportingMaxLines: 4,
              value: updates.enabled,
              onChanged: (v) => unawaited(updates.setEnabled(v)),
            ),
            KitRow(
              key: const ValueKey('updates-check-now'),
              leading: KitRow.icon(context, AppIconography.sync),
              title: l10n.appUpdateCheckNow,
              enabled: !updates.busy,
              onTap: () => unawaited(updates.checkNow()),
            ),
          ],
        ),
        if (release != null && release.notes.trim().isNotEmpty) ...[
          SizedBox(height: tokens.sectionGap),
          KitSectionLabel(l10n.appUpdateWhatsNew),
          Padding(
            padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
            child: KitMarkdown(
              release.notes,
              key: const ValueKey('updates-notes'),
            ),
          ),
        ],
      ],
    );
  }
}
