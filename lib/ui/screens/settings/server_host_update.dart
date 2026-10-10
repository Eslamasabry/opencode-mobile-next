part of '../settings_screen.dart';

// ---- OD1 agent lane: update Paseo on the computer ------------------------

extension _ServerHostUpdate on _ServerSettingsScreenState {
  /// The gateway that can update its computer's helper, or null.
  HostDaemonUpdateGateway? get _hostUpdater {
    final api = widget.controller.api;
    return api is HostDaemonUpdateGateway &&
            (api as HostDaemonUpdateGateway).hostUpdateSupported
        ? api as HostDaemonUpdateGateway
        : null;
  }

  String _phaseWords(AppLocalizations copy, HostUpdatePhase? phase) =>
      switch (phase) {
        null || HostUpdatePhase.starting => copy.hostUpdateStarting,
        HostUpdatePhase.downloading => copy.hostUpdateDownloading,
        HostUpdatePhase.installing => copy.hostUpdateInstalling,
        HostUpdatePhase.complete => copy.hostUpdateFinishing,
      };

  /// What the row says under its title.
  String _hostUpdateSupporting(AppLocalizations copy, String computer) {
    if (_hostUpdating) return _phaseWords(copy, _hostPhase);
    if (_hostUpdateLine != null) return _hostUpdateLine!;
    final version = _runningVersion;
    return version == null || version.isEmpty
        ? copy.hostUpdateRowUnknown
        : copy.hostUpdateRowRunning(version);
  }

  Future<void> _confirmHostUpdate(String computer) async {
    final updater = _hostUpdater;
    if (_hostUpdating || updater == null) return;
    final copy = _settingsCopy(context);
    final confirmed = await showKitConfirm(
      context,
      title: copy.hostUpdateConfirmTitle,
      body: copy.hostUpdateConfirmBody(computer),
      confirmLabel: copy.hostUpdateConfirmAction,
      icon: AppIconography.systemDownload,
      consequenceItems: [
        KitConsequence(
          copy.hostUpdateConfirmPauses(computer),
          mark: KitConsequenceMark.lost,
        ),
        KitConsequence(
          copy.hostUpdateConfirmKept,
          mark: KitConsequenceMark.kept,
        ),
      ],
      confirmKey: const Key('confirm-host-update'),
    );
    if (confirmed && mounted) await _runHostUpdate(updater, computer);
  }

  Future<void> _runHostUpdate(
    HostDaemonUpdateGateway updater,
    String computer,
  ) async {
    final copy = _settingsCopy(context);
    final profileId = widget.controller.profile?.id;
    _setState(() {
      _hostUpdating = true;
      _hostPhase = HostUpdatePhase.starting;
      _hostUpdateLine = null;
      _hostUpdateReason = null;
      _hostUpdateFailed = false;
    });
    var restarting = false;
    try {
      final result = await updater.updateHostDaemon(
        onProgress: (phase) {
          if (mounted) _setState(() => _hostPhase = phase);
        },
      );
      if (!mounted) return;
      restarting = result.success && !result.alreadyUpToDate;
      _setState(() {
        _hostUpdateFailed = !result.success;
        _hostUpdateReason = result.reason;
        _hostUpdateLine = !result.success
            ? copy.hostUpdateFailed
            : result.alreadyUpToDate
            ? copy.hostUpdateCurrent(computer)
            : result.newVersion != null
            ? copy.hostUpdateDone(result.newVersion!)
            : copy.hostUpdateDoneNoVersion;
      });
    } catch (_) {
      if (!mounted) return;
      // The helper restarts itself, so a dropped connection can still be a
      // finished update: say so and look again once it is back.
      restarting = true;
      _setState(() {
        _hostUpdateFailed = false;
        _hostUpdateLine = copy.hostUpdateDropped;
      });
    } finally {
      if (mounted) _setState(() => _hostUpdating = false);
    }
    if (restarting) await _refreshAfterRestart(profileId);
  }

  /// The helper is back a few seconds after it restarts: the health check
  /// reconnects to it and shows the version it now runs.
  Future<void> _refreshAfterRestart(String? profileId) async {
    for (var attempt = 0; attempt < 4; attempt++) {
      await Future<void>.delayed(const Duration(seconds: 3));
      if (!mounted || widget.controller.profile?.id != profileId) return;
      await _checkHealth();
      if (_healthError == null) return;
    }
  }

  void _showHostUpdateDetails(String computer) {
    final copy = _settingsCopy(context);
    final reason = _hostUpdateReason;
    if (reason == null) return;
    unawaited(
      showKitTechnicalDetails(
        context,
        title: copy.hostUpdateDetailsTitle,
        text: '',
        sheetKey: const ValueKey('host-update-details-sheet'),
        values: [KitTechnicalValue(copy.hostUpdateDetailsMessage, reason)],
      ),
    );
  }

  List<Widget> _hostUpdateRows(
    BuildContext context,
    AppLocalizations copy,
    String computer,
  ) {
    if (_hostUpdater == null) return const [];
    return [
      KitRow(
        key: const Key('server-host-update'),
        leading: KitRow.icon(context, AppIconography.systemDownload),
        title: copy.hostUpdateRow(computer),
        titleMaxLines: 2,
        supporting: TextSpan(text: _hostUpdateSupporting(copy, computer)),
        supportingMaxLines: 3,
        enabled: !_hostUpdating,
        onTap: () => unawaited(_confirmHostUpdate(computer)),
      ),
      if (_hostUpdateFailed && _hostUpdateReason != null)
        KitRow(
          key: const Key('server-host-update-details'),
          leading: KitRow.icon(context, AppIconography.info),
          title: copy.hostUpdateDetailsRow,
          trailing: const _RowMark(AppIconography.chevronRight),
          onTap: () => _showHostUpdateDetails(computer),
        ),
    ];
  }
}
