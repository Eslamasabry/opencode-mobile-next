import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../domain/provider_quota.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/provider_quota_overview.dart';
import '../../state/provider_quota_budgets.dart';
import '../app_theme.dart';
import 'quota_monitor_screen.dart';

class ProviderQuotaScreen extends StatefulWidget {
  final ConnectionController controller;
  final ProviderQuotaOverview? overview;

  const ProviderQuotaScreen({
    super.key,
    required this.controller,
    this.overview,
  });

  @override
  State<ProviderQuotaScreen> createState() => _ProviderQuotaScreenState();
}

class _ProviderQuotaScreenState extends State<ProviderQuotaScreen> {
  late ProviderQuotaOverview _overview;
  bool _trusted = false;

  @override
  void initState() {
    super.initState();
    _overview = widget.overview ?? ProviderQuotaOverview(widget.controller);
  }

  @override
  void didUpdateWidget(ProviderQuotaScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller == widget.controller &&
        oldWidget.overview == widget.overview) {
      return;
    }
    if (oldWidget.overview == null) {
      _overview.dispose();
    }
    _overview = widget.overview ?? ProviderQuotaOverview(widget.controller);
    _trusted = false;
  }

  @override
  void dispose() {
    if (widget.overview == null) {
      _overview.dispose();
    }
    super.dispose();
  }

  String _source(AppLocalizations l10n) {
    final uri = Uri.tryParse(widget.controller.profile?.baseUrl ?? '');
    if (uri == null ||
        !['http', 'https'].contains(uri.scheme) ||
        uri.host.isEmpty) {
      return l10n.quotaUnknownSource;
    }
    return uri.origin;
  }

  String _failure(ProviderQuotaFailure failure, AppLocalizations l10n) =>
      switch (failure.kind) {
        QuotaFailureKind.collectorAuth => l10n.quotaCollectorAuth,
        QuotaFailureKind.unsupported => l10n.quotaCollectorMissing,
        QuotaFailureKind.unavailable => l10n.quotaUnavailable,
        QuotaFailureKind.invalidResponse => l10n.quotaInvalidResponse,
      };

  String _status(ProviderQuotaStatus status, AppLocalizations l10n) =>
      switch (status) {
        ProviderQuotaStatus.ok => l10n.quotaAccountUnverified,
        ProviderQuotaStatus.unconfigured => l10n.quotaUnconfigured,
        ProviderQuotaStatus.unsupported => l10n.quotaProviderUnsupported,
        ProviderQuotaStatus.authRequired => l10n.quotaProviderAuth,
        ProviderQuotaStatus.rateLimited => l10n.quotaRateLimited,
        ProviderQuotaStatus.unavailable => l10n.quotaUnavailable,
        ProviderQuotaStatus.invalidResponse => l10n.quotaInvalidResponse,
      };

  Future<void> _enroll() async {
    final snapshot = _overview.snapshot;
    final profileID = widget.controller.profile?.id;
    if (snapshot == null ||
        profileID == null ||
        _overview.snapshotIsStale ||
        _overview.detached) {
      return;
    }
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    var notifications = false, wifiOnly = false, quiet = false;
    var threshold = 100.0;
    final accepted = await showDialog<bool>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, update) => AlertDialog(
          title: Text(l10n.quotaMonitorConsentTitle),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(l10n.quotaMonitorConsent),
                Text(_source(l10n)),
                DropdownButton<double>(
                  value: threshold,
                  isExpanded: true,
                  items: [
                    for (final value in [50.0, 75.0, 90.0, 100.0])
                      DropdownMenuItem(
                        value: value,
                        child: Text(
                          l10n.quotaBudgetPercent(value.toInt().toString()),
                        ),
                      ),
                  ],
                  onChanged: (value) => update(() => threshold = value ?? 100),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.quotaMonitorNotifications),
                  value: notifications,
                  onChanged: (value) => update(() => notifications = value),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.quotaMonitorWifi),
                  value: wifiOnly,
                  onChanged: (value) => update(() => wifiOnly = value),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(l10n.quotaMonitorQuiet),
                  value: quiet,
                  onChanged: (value) => update(() => quiet = value),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(l10n.workCancel),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(l10n.quotaMonitorEnable),
            ),
          ],
        ),
      ),
    );
    if (!mounted || accepted != true) {
      return;
    }
    final valid =
        !_overview.detached &&
        !_overview.snapshotIsStale &&
        identical(snapshot, _overview.snapshot);
    final saved =
        valid &&
        await widget.controller.quotaMonitor.enroll(
          profileID,
          snapshot,
          notifications: notifications,
          threshold: threshold,
          wifiOnly: wifiOnly,
          quietStart: quiet ? 22 * 60 : null,
          quietEnd: quiet ? 8 * 60 : null,
        );
    if (!mounted) {
      return;
    }
    if (!saved) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.quotaMonitorSaveFailed)));
      return;
    }
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => QuotaMonitorScreen(controller: widget.controller),
      ),
    );
  }

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _overview,
    builder: (context, _) {
      final l10n = lookupAppLocalizations(Localizations.localeOf(context));
      final theme = Theme.of(context);
      final snapshot = _overview.snapshot;
      final canRefresh = _overview.canRead && !_overview.loading;
      final detached = _overview.detached;
      return Scaffold(
        appBar: AppBar(
          title: Text(l10n.quotaTitle),
          actions: [
            IconButton(
              tooltip: l10n.quotaMonitorTitle,
              icon: const Icon(Icons.notifications_none_rounded),
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) =>
                      QuotaMonitorScreen(controller: widget.controller),
                ),
              ),
            ),
            if (_overview.consented)
              IconButton(
                tooltip: l10n.quotaRefresh,
                onPressed: canRefresh
                    ? () => unawaited(_overview.refresh())
                    : null,
                icon: const Icon(Icons.refresh_rounded),
              ),
          ],
        ),
        body: SafeArea(
          child: Align(
            alignment: Alignment.topCenter,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 860),
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
                children: [
                  Text(l10n.quotaDescription),
                  const SizedBox(height: 16),
                  if (detached)
                    _Notice(text: l10n.quotaSourceChanged)
                  else ...[
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (final provider in QuotaProvider.values)
                          ChoiceChip(
                            label: Text(switch (provider) {
                              QuotaProvider.codex => l10n.quotaCodex,
                              QuotaProvider.claude => l10n.quotaClaude,
                              QuotaProvider.minimax => l10n.quotaMiniMax,
                              QuotaProvider.glm => l10n.quotaGlm,
                            }),
                            selected: _overview.provider == provider,
                            onSelected: (_) {
                              if (_overview.provider == provider) {
                                return;
                              }
                              setState(() => _trusted = false);
                              _overview.selectProvider(provider);
                            },
                          ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Text(l10n.quotaSource, style: theme.textTheme.titleSmall),
                    const SizedBox(height: 4),
                    Text(_source(l10n), style: theme.textTheme.bodyMedium),
                    const SizedBox(height: 16),
                    if (!_overview.providerSupported)
                      _Notice(text: l10n.quotaClaudeUnavailable)
                    else if (!_overview.consented) ...[
                      Text(
                        l10n.quotaSetupTitle,
                        style: theme.textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      Text(l10n.quotaSetupDescription),
                      const SizedBox(height: 12),
                      SelectableText(
                        quotaPathFor(_overview.provider),
                        style: theme.textTheme.bodyMedium?.copyWith(
                          fontFamily: AppTheme.monoFamily,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(l10n.quotaSetupGuide),
                      if (_overview.setupNeeded) ...[
                        const SizedBox(height: 12),
                        _Notice(text: l10n.quotaSetupNeeded),
                      ],
                      CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        controlAffinity: ListTileControlAffinity.leading,
                        title: Text(l10n.quotaConsent),
                        value: _trusted,
                        onChanged: _overview.setupNeeded
                            ? null
                            : (value) =>
                                  setState(() => _trusted = value ?? false),
                      ),
                      FilledButton.icon(
                        onPressed: !_trusted || _overview.setupNeeded
                            ? null
                            : () => unawaited(_overview.allowAndRefresh()),
                        icon: const Icon(Icons.speed_rounded),
                        label: Text(l10n.quotaRead),
                      ),
                    ] else ...[
                      if (_overview.loading) ...[
                        LinearProgressIndicator(
                          semanticsLabel: l10n.quotaLoading,
                        ),
                        const SizedBox(height: 12),
                      ],
                      if (_overview.failure case final failure?) ...[
                        _Notice(text: _failure(failure, l10n), error: true),
                        Align(
                          alignment: AlignmentDirectional.centerStart,
                          child: TextButton(
                            onPressed: canRefresh
                                ? () => unawaited(_overview.refresh())
                                : null,
                            child: Text(l10n.quotaRefresh),
                          ),
                        ),
                      ],
                      if (snapshot != null) ...[
                        if (snapshot.canShowWindows)
                          Align(
                            alignment: AlignmentDirectional.centerStart,
                            child: TextButton(
                              onPressed: _overview.snapshotIsStale
                                  ? null
                                  : _enroll,
                              child: Text(l10n.quotaMonitorEnable),
                            ),
                          ),
                        if (_overview.budgets.failed)
                          _Notice(
                            text: l10n.quotaBudgetSaveFailed,
                            error: true,
                          ),
                        if (_overview.budgets.attentionVisible &&
                            !_overview.snapshotIsStale)
                          _Notice(text: l10n.quotaBudgetAttention),
                        if (!snapshot.canShowWindows)
                          _Notice(text: _status(snapshot.status, l10n))
                        else
                          _QuotaReport(
                            snapshot: snapshot,
                            stale: _overview.snapshotIsStale,
                            now: _overview.clock(),
                            budgets: _overview.budgets,
                          ),
                      ],
                      const SizedBox(height: 16),
                      TextButton(
                        onPressed: _overview.budgets.saving
                            ? null
                            : () async {
                                final clear = await showDialog<bool>(
                                  context: context,
                                  builder: (context) => AlertDialog(
                                    title: Text(l10n.quotaBudgetClearAll),
                                    content: Text(
                                      l10n.quotaBudgetClearDescription,
                                    ),
                                    actions: [
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(context, false),
                                        child: Text(l10n.workCancel),
                                      ),
                                      FilledButton(
                                        onPressed: () =>
                                            Navigator.pop(context, true),
                                        child: Text(l10n.quotaBudgetClearAll),
                                      ),
                                    ],
                                  ),
                                );
                                if (clear == true) {
                                  await _overview.budgets.clearAll();
                                }
                              },
                        child: Text(l10n.quotaBudgetClearAll),
                      ),
                      TextButton(
                        onPressed: () {
                          setState(() => _trusted = false);
                          _overview.disable();
                        },
                        child: Text(l10n.quotaForgetConsent),
                      ),
                    ],
                  ],
                ],
              ),
            ),
          ),
        ),
      );
    },
  );
}

class _Notice extends StatelessWidget {
  final String text;
  final bool error;
  const _Notice({required this.text, this.error = false});

  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Text(
      text,
      style: TextStyle(
        color: error ? Theme.of(context).colorScheme.error : null,
      ),
    ),
  );
}

class _QuotaReport extends StatelessWidget {
  final ProviderQuotaSnapshot snapshot;
  final bool stale;
  final DateTime now;
  final ProviderQuotaBudgets budgets;
  const _QuotaReport({
    required this.snapshot,
    required this.stale,
    required this.now,
    required this.budgets,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final locale = Localizations.localeOf(context).toLanguageTag();
    final date = DateFormat.yMMMd(locale).add_jm();
    final percent = NumberFormat.percentPattern(locale)
      ..maximumFractionDigits = 1;
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(switch (snapshot.provider) {
          QuotaProvider.codex => l10n.quotaCodexAccount,
          QuotaProvider.claude => l10n.quotaClaudeAccount,
          QuotaProvider.minimax => l10n.quotaMiniMaxAccount,
          QuotaProvider.glm => l10n.quotaGlmAccount,
        }, style: theme.textTheme.titleLarge),
        if (snapshot.account.status == QuotaAccountStatus.sourceBound) ...[
          const SizedBox(height: 8),
          Text(
            snapshot.provider == QuotaProvider.minimax
                ? l10n.quotaMiniMaxSourceBound
                : l10n.quotaSourceBound,
          ),
        ],
        if (snapshot.account.plan case final plan?) Text(l10n.quotaPlan(plan)),
        const SizedBox(height: 8),
        Text(l10n.quotaChecked(date.format(snapshot.fetchedAt.toLocal()))),
        if (stale) ...[
          const SizedBox(height: 8),
          _Notice(text: l10n.quotaStale),
        ],
        if (snapshot.ordinaryUsageAllowed == false) ...[
          const SizedBox(height: 8),
          _Notice(text: l10n.quotaUseBlocked),
        ],
        if (snapshot.windows.isEmpty) ...[
          const SizedBox(height: 16),
          Text(l10n.quotaNotReported),
        ],
        for (var index = 0; index < snapshot.windows.length; index++) ...[
          const SizedBox(height: 16),
          Builder(
            builder: (context) {
              final window = snapshot.windows[index];
              final title = switch (window.id) {
                'tokens' when snapshot.provider == QuotaProvider.glm =>
                  l10n.quotaGlmTokenWindow,
                'mcp' when snapshot.provider == QuotaProvider.glm =>
                  l10n.quotaGlmMcpWindow,
                'primary' => l10n.quotaPrimaryWindow,
                'secondary' => l10n.quotaSecondaryWindow,
                _ => l10n.quotaOtherWindow(index + 1),
              };
              final duration = window.durationSeconds;
              final remaining = window.remainingPercent;
              final reset = window.resetsAt;
              return Card(
                margin: EdgeInsets.zero,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(title, style: theme.textTheme.titleMedium),
                      if (duration != null)
                        Text(
                          duration % 86400 == 0
                              ? l10n.quotaDays(duration ~/ 86400)
                              : duration % 3600 == 0
                              ? l10n.quotaHours(duration ~/ 3600)
                              : l10n.quotaSeconds(duration),
                        ),
                      const SizedBox(height: 12),
                      if (remaining == null)
                        Text(l10n.quotaNotReported)
                      else ...[
                        Text(
                          l10n.quotaRemaining(percent.format(remaining / 100)),
                          style: theme.textTheme.headlineSmall,
                        ),
                        Text(
                          l10n.quotaUsed(
                            percent.format(window.usedPercent! / 100),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Semantics(
                          container: true,
                          child: LinearProgressIndicator(
                            value: remaining / 100,
                            semanticsLabel: l10n.quotaWindowRemainingLabel(
                              title,
                            ),
                            // Progress-bar semantics require a numeric value.
                            // Localized prose belongs in the label/text, never
                            // the machine-readable range value.
                            semanticsValue: NumberFormat(
                              '0.#',
                              'en',
                            ).format(remaining),
                          ),
                        ),
                      ],
                      const SizedBox(height: 12),
                      Text(
                        reset == null
                            ? l10n.quotaResetUnknown
                            : l10n.quotaResetAt(date.format(reset.toLocal())),
                      ),
                      if (reset != null && !now.isBefore(reset))
                        Text(l10n.quotaResetPassed),
                      if (budgets.usable(snapshot, window)) ...[
                        const SizedBox(height: 16),
                        _BudgetControls(
                          budgets: budgets,
                          snapshot: snapshot,
                          window: window,
                          stale: stale,
                        ),
                      ],
                    ],
                  ),
                ),
              );
            },
          ),
        ],
        const SizedBox(height: 16),
        Text(l10n.quotaSourceDisclosure, style: theme.textTheme.bodySmall),
      ],
    );
  }
}

class _BudgetControls extends StatelessWidget {
  final ProviderQuotaBudgets budgets;
  final ProviderQuotaSnapshot snapshot;
  final ProviderQuotaWindow window;
  final bool stale;
  const _BudgetControls({
    required this.budgets,
    required this.snapshot,
    required this.window,
    required this.stale,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final rule = budgets.rule(snapshot, window);
    final enabled = !stale && !budgets.saving;
    Future<void> save(QuotaBudget? value) async {
      if (await budgets.save(snapshot, window, value)) {
        await budgets.observe(snapshot, stale: stale);
      }
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.quotaBudgetTitle,
          style: Theme.of(context).textTheme.titleSmall,
        ),
        Text(l10n.quotaBudgetDescription),
        DropdownButton<double>(
          key: ValueKey('quota-threshold-${window.id}'),
          isExpanded: true,
          value: rule?.percent ?? 0,
          items: [
            DropdownMenuItem(value: 0, child: Text(l10n.quotaBudgetOff)),
            for (final value in {
              50.0,
              75.0,
              90.0,
              100.0,
              if (rule != null) rule.percent,
            })
              DropdownMenuItem(
                value: value,
                child: Text(l10n.quotaBudgetPercent(value.toStringAsFixed(0))),
              ),
          ],
          onChanged: enabled
              ? (value) => unawaited(
                  save(
                    value == null || value == 0
                        ? null
                        : QuotaBudget(
                            value,
                            attention: rule?.attention ?? false,
                          ),
                  ),
                )
              : null,
        ),
        if (rule != null)
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.quotaBudgetOptIn),
            subtitle: Text(l10n.quotaBudgetAttentionScope),
            value: rule.attention,
            onChanged: enabled
                ? (value) => unawaited(
                    save(QuotaBudget(rule.percent, attention: value)),
                  )
                : null,
          ),
      ],
    );
  }
}
