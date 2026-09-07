import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../domain/provider_quota.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/provider_quota_monitor.dart';

String _sourceOrigin(String value, String fallback) {
  try {
    return Uri.parse(value).origin;
  } catch (_) {
    return fallback;
  }
}

String quotaProviderLabel(AppLocalizations l10n, QuotaProvider provider) =>
    switch (provider) {
      QuotaProvider.codex => l10n.quotaCodex,
      QuotaProvider.claude => l10n.quotaClaude,
      QuotaProvider.minimax => l10n.quotaMiniMax,
      QuotaProvider.glm => l10n.quotaGlm,
    };

/// Review collector observations without connecting or switching OpenCode servers.
class QuotaMonitorScreen extends StatelessWidget {
  final ConnectionController controller;
  const QuotaMonitorScreen({super.key, required this.controller});
  @override
  Widget build(BuildContext context) {
    final monitor = controller.quotaMonitor;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Scaffold(
      appBar: AppBar(title: Text(l10n.quotaMonitorTitle)),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: monitor,
          builder: (context, _) => ListView(
            padding: const EdgeInsets.all(20),
            children: [
              Text(l10n.quotaMonitorRuntime),
              const SizedBox(height: 12),
              if (monitor.sources.isEmpty) Text(l10n.quotaMonitorEmpty),
              for (final target in monitor.sources) ...[
                const Divider(height: 32),
                _Source(controller: controller, target: target),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

class _Source extends StatefulWidget {
  final ConnectionController controller;
  final QuotaMonitorTarget target;
  const _Source({required this.controller, required this.target});
  @override
  State<_Source> createState() => _SourceState();
}

class _SourceState extends State<_Source> {
  bool saving = false;
  Future<void> _change(Future<bool> Function() action) async {
    setState(() => saving = true);
    final success = await action();
    if (!mounted) {
      return;
    }
    setState(() => saving = false);
    if (!success) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            lookupAppLocalizations(
              Localizations.localeOf(context),
            ).quotaMonitorSaveFailed,
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final monitor = widget.controller.quotaMonitor, target = widget.target;
    final rules = monitor.rulesFor(target.profileID, target.provider);
    final profile = widget.controller.store.profiles
        .where((p) => p.id == target.profileID)
        .firstOrNull;
    if (rules == null || profile == null) {
      return const SizedBox.shrink();
    }
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final observation = monitor.observationFor(
      target.profileID,
      target.provider,
    );
    final snapshot = observation.snapshot;
    final quiet = rules.quietStart != null;
    Future<bool> policy({
      bool? notifications,
      bool? wifiOnly,
      bool? quietHours,
      double? threshold,
    }) => monitor.setPolicy(
      target.profileID,
      target.provider,
      notifications: notifications ?? rules.notifications,
      threshold: threshold ?? rules.threshold,
      wifiOnly: wifiOnly ?? rules.wifiOnly,
      quietStart: (quietHours ?? quiet) ? 22 * 60 : null,
      quietEnd: (quietHours ?? quiet) ? 8 * 60 : null,
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.quotaSourceTitle(
            profile.name,
            quotaProviderLabel(l10n, target.provider),
          ),
          style: Theme.of(context).textTheme.titleMedium,
        ),
        Text(_sourceOrigin(profile.baseUrl, l10n.quotaUnknownSource)),
        const SizedBox(height: 8),
        Text(switch (observation.status) {
          QuotaMonitorStatus.disabled => l10n.quotaMonitorDisabled,
          QuotaMonitorStatus.waiting => l10n.quotaMonitorWaiting,
          QuotaMonitorStatus.checking => l10n.quotaMonitorChecking,
          QuotaMonitorStatus.current => l10n.quotaMonitorCurrent,
          QuotaMonitorStatus.paused => l10n.quotaMonitorPaused,
          QuotaMonitorStatus.wifiRequired => l10n.quotaMonitorWifiRequired,
          QuotaMonitorStatus.unavailable => l10n.quotaUnavailable,
          QuotaMonitorStatus.sourceChanged => l10n.quotaMonitorSourceChanged,
        }),
        if (snapshot != null) ...[
          Text(
            l10n.quotaChecked(
              DateFormat.yMMMd().add_jm().format(snapshot.fetchedAt.toLocal()),
            ),
          ),
          for (var i = 0; i < snapshot.windows.length; i++) ...[
            const SizedBox(height: 8),
            Text(
              l10n.quotaOtherWindow(i + 1),
              style: Theme.of(context).textTheme.titleSmall,
            ),
            Text(
              snapshot.windows[i].remainingPercent == null
                  ? l10n.quotaNotReported
                  : l10n.quotaRemaining(
                      '${snapshot.windows[i].remainingPercent!.toStringAsFixed(1)}%',
                    ),
            ),
            Text(
              snapshot.windows[i].resetsAt == null
                  ? l10n.quotaResetUnknown
                  : l10n.quotaResetAt(
                      DateFormat.yMMMd().add_jm().format(
                        snapshot.windows[i].resetsAt!.toLocal(),
                      ),
                    ),
            ),
          ],
        ],
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.quotaMonitorNotifications),
          value: rules.notifications,
          onChanged: saving
              ? null
              : (value) => _change(() => policy(notifications: value)),
        ),
        DropdownButton<double>(
          value: rules.threshold,
          isExpanded: true,
          items: [
            for (final value in {50.0, 75.0, 90.0, 100.0, rules.threshold})
              DropdownMenuItem(
                value: value,
                child: Text(l10n.quotaBudgetPercent(value.toInt().toString())),
              ),
          ],
          onChanged: saving
              ? null
              : (value) => _change(() => policy(threshold: value)),
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.quotaMonitorWifi),
          value: rules.wifiOnly,
          onChanged: saving
              ? null
              : (value) => _change(() => policy(wifiOnly: value)),
        ),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(l10n.quotaMonitorQuiet),
          value: quiet,
          onChanged: saving
              ? null
              : (value) => _change(() => policy(quietHours: value)),
        ),
        Wrap(
          spacing: 12,
          children: [
            TextButton(
              onPressed: monitor.refreshing || !monitor.runningAllowed
                  ? null
                  : () => monitor.refreshSource(target),
              child: Text(l10n.quotaRefresh),
            ),
            TextButton(
              onPressed: saving
                  ? null
                  : () => _change(
                      () => monitor.disable(target.profileID, target.provider),
                    ),
              child: Text(l10n.quotaMonitorDisable),
            ),
          ],
        ),
      ],
    );
  }
}
