import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../termux/bridge.dart';

/// An explicit, bounded status check. Never starts, installs or restarts a server.
class ManagedServerHealth extends StatefulWidget {
  const ManagedServerHealth({super.key, required this.onManage});

  final VoidCallback onManage;

  @override
  State<ManagedServerHealth> createState() => _ManagedServerHealthState();
}

class _ManagedServerHealthState extends State<ManagedServerHealth> {
  TermuxSetupStatus? _status;
  DateTime? _checkedAt;
  bool _checking = false;
  bool _failed = false;

  Future<void> _check() async {
    if (_checking || !platformCapabilities.supportsTermux) return;
    setState(() {
      _checking = true;
      _failed = false;
      _status = null;
      _checkedAt = null;
    });
    try {
      final status = await TermuxBridge.status();
      if (!mounted) return;
      setState(() {
        _status = status;
        _checkedAt = DateTime.now();
      });
    } catch (_) {
      if (mounted) setState(() => _failed = true);
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  String _label(AppLocalizations l10n) {
    if (_checking) return l10n.managedHealthChecking;
    if (_failed) return l10n.managedHealthFailed;
    final status = _status;
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
    final status = _status;
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
