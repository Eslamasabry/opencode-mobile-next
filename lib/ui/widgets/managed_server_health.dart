import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../termux/bridge.dart';
import '../../termux/managed_server_recovery.dart';

/// Explicit health checks plus a separately opted-in foreground recovery policy.
class ManagedServerHealth extends StatefulWidget {
  const ManagedServerHealth({
    super.key,
    required this.onManage,
    this.prefs,
    this.profileID,
  });

  final VoidCallback onManage;
  final SharedPreferences? prefs;
  final String? profileID;

  @override
  State<ManagedServerHealth> createState() => _ManagedServerHealthState();
}

class _ManagedServerHealthState extends State<ManagedServerHealth> {
  TermuxSetupStatus? _status;
  TermuxSetupStatus? _recoveryStatusAtLastCheck;
  DateTime? _checkedAt;
  bool _checking = false;
  bool _failed = false;
  TermuxStorageSnapshot? _storage;
  bool _storageFailed = false;
  ManagedServerRecovery? _recovery;
  bool? _policyError;

  @override
  void initState() {
    super.initState();
    _bindRecovery();
  }

  @override
  void didUpdateWidget(ManagedServerHealth oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.prefs != widget.prefs ||
        oldWidget.profileID != widget.profileID) {
      _recovery?.removeListener(_changed);
      _bindRecovery();
    }
  }

  void _bindRecovery() {
    final prefs = widget.prefs;
    final id = widget.profileID;
    _recovery =
        prefs != null && id != null && platformCapabilities.supportsTermux
        ? ManagedServerRecovery.forProfile(prefs, id)
        : null;
    _recovery?.addListener(_changed);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  Future<void> _setRecovery(bool enabled) async {
    setState(() => _policyError = null);
    try {
      await _recovery?.setEnabled(enabled);
    } catch (_) {
      if (mounted) setState(() => _policyError = true);
    }
  }

  @override
  void dispose() {
    _recovery?.removeListener(_changed);
    super.dispose();
  }

  Future<void> _check() async {
    if (_checking || !platformCapabilities.supportsTermux) return;
    setState(() {
      _checking = true;
      _failed = false;
      _status = null;
      _checkedAt = null;
      _storage = null;
      _storageFailed = false;
    });
    try {
      final status = await TermuxBridge.status();
      if (!mounted) return;
      setState(() {
        _status = status;
        _recoveryStatusAtLastCheck = _recovery?.enabled == true
            ? _recovery?.status
            : null;
        _checkedAt = DateTime.now();
      });
      try {
        final storage = await TermuxBridge.storage();
        if (mounted) setState(() => _storage = storage);
      } catch (_) {
        if (mounted) setState(() => _storageFailed = true);
      }
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  TermuxSetupStatus? get _displayedStatus {
    if (_checking || _failed) return null;
    final recoveryStatus = _recovery?.enabled == true
        ? _recovery?.status
        : null;
    // A manual check supersedes the recovery observation that existed when
    // it completed. A later recovery poll supplies a new observation object.
    if (_status != null &&
        identical(recoveryStatus, _recoveryStatusAtLastCheck)) {
      return _status;
    }
    return recoveryStatus ?? _status;
  }

  String _label(AppLocalizations l10n) {
    if (_checking) return l10n.managedHealthChecking;
    if (_failed) return l10n.managedHealthFailed;
    final status = _displayedStatus;
    if (status == null) return l10n.managedHealthUnchecked;
    if (status.isReady) return l10n.managedHealthReady;
    if (status.isRunning) return l10n.managedHealthWorking;
    return switch (status.phase) {
      'stopped' => l10n.managedHealthStopped,
      'failed' => l10n.managedHealthNeedsSetup,
      'idle' => l10n.managedHealthAbsent,
      _ => l10n.managedHealthUnknown,
    };
  }

  @override
  Widget build(BuildContext context) {
    if (!platformCapabilities.supportsTermux) return const SizedBox.shrink();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final theme = Theme.of(context);
    final status = _displayedStatus;
    final version = status?.version ?? '';
    final safeVersion = RegExp(
      r'^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$',
    ).hasMatch(version);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Divider(),
          const SizedBox(height: 12),
          Text(l10n.managedHealthTitle, style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          Semantics(liveRegion: true, child: Text(_label(l10n))),
          if (safeVersion) Text(l10n.managedHealthVersion(version)),
          if (status?.runner == 'proot') Text(l10n.managedHealthUbuntu),
          if (_storage case final storage?)
            Text(
              l10n.managedStorageSummary(
                (storage.availableBytes / 1073741824).toStringAsFixed(1),
                (storage.totalBytes / 1073741824).toStringAsFixed(1),
              ),
            ),
          if (_storageFailed) Text(l10n.managedStorageFailed),
          if (_checkedAt case final checkedAt?) ...[
            const SizedBox(height: 8),
            Text(
              l10n.managedHealthObserved(
                MaterialLocalizations.of(context).formatTimeOfDay(
                  TimeOfDay.fromDateTime(checkedAt),
                  alwaysUse24HourFormat: MediaQuery.alwaysUse24HourFormatOf(
                    context,
                  ),
                ),
              ),
              style: theme.textTheme.bodySmall,
            ),
          ],
          const SizedBox(height: 8),
          Text(l10n.managedHealthLifetime, style: theme.textTheme.bodySmall),
          if (_recovery case final recovery?) ...[
            const SizedBox(height: 12),
            SwitchListTile.adaptive(
              contentPadding: EdgeInsets.zero,
              title: Text(l10n.managedRecoveryTitle),
              subtitle: Text(l10n.managedRecoveryPolicy),
              value: recovery.enabled,
              onChanged: recovery.busy && !recovery.enabled
                  ? null
                  : _setRecovery,
            ),
            Text(l10n.managedRecoveryAttempts(recovery.attempts)),
            if (recovery.exhausted) Text(l10n.managedRecoveryExhausted),
            if (recovery.enabled && recovery.paused && recovery.error == null)
              Text(l10n.managedRecoveryBackground),
            if (recovery.busy) Text(l10n.managedRecoveryChecking),
            if (recovery.nextAttemptAt case final next?)
              if (recovery.enabled && !recovery.exhausted)
                Text(
                  l10n.managedRecoveryNext(
                    MaterialLocalizations.of(
                      context,
                    ).formatTimeOfDay(TimeOfDay.fromDateTime(next)),
                  ),
                ),
            if (recovery.error case final error?)
              Text(switch (error) {
                ManagedRecoveryError.settingsUnreadable =>
                  l10n.managedRecoverySettingsUnreadable,
                ManagedRecoveryError.enableFailed =>
                  l10n.managedRecoveryEnableFailed,
                ManagedRecoveryError.ownershipChanged =>
                  l10n.managedRecoveryOwnershipChanged,
                ManagedRecoveryError.uncertainResult =>
                  l10n.managedRecoveryUncertain,
              }),
            if (_policyError case final revoke?)
              Text(
                revoke
                    ? l10n.managedRecoveryRevokeFailed
                    : l10n.managedRecoverySaveFailed,
              ),
            if (_policyError == true)
              TextButton(
                onPressed: () => _setRecovery(false),
                child: Text(l10n.managedRecoveryRetryDisable),
              ),
            if (recovery.enabled && recovery.error != null)
              TextButton(
                onPressed: recovery.busy
                    ? null
                    : () async {
                        try {
                          await recovery.retryCheck();
                        } catch (_) {
                          if (mounted) setState(() => _policyError = false);
                        }
                      },
                child: Text(l10n.managedRecoveryCheck),
              ),
            if (recovery.exhausted)
              TextButton(
                onPressed: recovery.busy ? null : () => _setRecovery(true),
                child: Text(l10n.managedRecoveryReset),
              ),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              OutlinedButton.icon(
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size(48, 48),
                ),
                onPressed: _checking ? null : _check,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(l10n.managedHealthCheck),
              ),
              TextButton(
                style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: widget.onManage,
                child: Text(l10n.managedHealthManage),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
