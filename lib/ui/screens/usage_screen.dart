import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../api2/transport.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/usage_overview.dart';
import '../widgets/product_states.dart';

AppLocalizations _strings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

class UsageScreen extends StatefulWidget {
  final ConnectionController controller;
  final UsageOverview? overview;
  const UsageScreen({super.key, required this.controller, this.overview});
  @override
  State<UsageScreen> createState() => _UsageScreenState();
}

class _UsageScreenState extends State<UsageScreen> {
  late final UsageOverview _overview =
      widget.overview ?? UsageOverview(widget.controller);
  @override
  void initState() {
    super.initState();
    unawaited(_overview.refresh());
  }

  @override
  void dispose() {
    if (widget.overview == null) _overview.dispose();
    super.dispose();
  }

  String _error(Object error, AppLocalizations l10n) => switch (error) {
    UsageUnsupported() => l10n.usageUnsupported,
    UsageProjectUnavailable() => l10n.usageProjectUnavailable,
    UsageTimezoneUnavailable() => l10n.usageTimezoneUnavailable,
    UsageRefreshInterrupted() => l10n.usageRefreshInterrupted,
    FormatException() => l10n.usageInvalidResponse,
    Api2Error(statusCode: 401 || 403) => l10n.usageAuthorization,
    Api2Error() => error.message,
    _ => productErrorText(error),
  };

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: _overview,
    builder: (context, _) {
      final l10n = _strings(context);
      final snapshot = _overview.snapshot;
      final unsupported = _overview.error is UsageUnsupported;
      final available = !_overview.detached && !unsupported;
      return Scaffold(
        appBar: AppBar(
          title: Text(l10n.usageTitle),
          actions: [
            IconButton(
              key: const ValueKey('refresh-usage'),
              tooltip: l10n.usageRefresh,
              onPressed: available && !_overview.loading
                  ? _overview.refresh
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
              child: RefreshIndicator(
                onRefresh: _overview.refresh,
                child: ListView(
                  key: const ValueKey('usage-content'),
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(20, 12, 20, 32),
                  children: [
                    Text(
                      l10n.usageDescription,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 16),
                    if (available) ...[
                      Wrap(
                        spacing: 8,
                        runSpacing: 4,
                        children: [
                          for (final range in UsageRange.values)
                            ChoiceChip(
                              key: ValueKey('usage-range-${range.name}'),
                              showCheckmark: false,
                              padding: const EdgeInsets.symmetric(
                                horizontal: 4,
                              ),
                              labelPadding: const EdgeInsets.symmetric(
                                horizontal: 4,
                              ),
                              label: Text(switch (range) {
                                UsageRange.today => l10n.usageToday,
                                UsageRange.thirtyDays => l10n.usageThirtyDays,
                                UsageRange.year => l10n.usageYear,
                                UsageRange.allTime => l10n.usageAllTime,
                              }),
                              selected: _overview.range == range,
                              onSelected: (_) =>
                                  unawaited(_overview.setRange(range)),
                            ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<UsageScope>(
                        key: ValueKey('usage-scope-${_overview.scope.name}'),
                        initialValue: _overview.scope,
                        isExpanded: true,
                        decoration: InputDecoration(
                          labelText: l10n.usageScope,
                          border: const OutlineInputBorder(),
                        ),
                        items: [
                          DropdownMenuItem(
                            value: UsageScope.allProjects,
                            child: Text(l10n.usageAllProjects),
                          ),
                          DropdownMenuItem(
                            value: UsageScope.currentProject,
                            child: Text(l10n.usageCurrentProject),
                          ),
                        ],
                        onChanged: (value) {
                          if (value != null) {
                            unawaited(_overview.setScope(value));
                          }
                        },
                      ),
                      const SizedBox(height: 20),
                    ],
                    if (_overview.loading) ...[
                      LinearProgressIndicator(
                        semanticsLabel: l10n.usageLoading,
                      ),
                      const SizedBox(height: 12),
                    ],
                    if (_overview.detached) Text(l10n.usageLocationChanged),
                    if (_overview.error case final error?) ...[
                      Text(
                        _error(error, l10n),
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                        ),
                      ),
                      if (snapshot != null) Text(l10n.usagePreviousResult),
                      if (available)
                        Align(
                          alignment: AlignmentDirectional.centerStart,
                          child: TextButton(
                            onPressed: _overview.loading
                                ? null
                                : _overview.refresh,
                            child: Text(l10n.usageRefresh),
                          ),
                        ),
                      const SizedBox(height: 12),
                    ],
                    if (snapshot != null) _UsageReport(snapshot: snapshot),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    },
  );
}

String _money(BuildContext context, double value) {
  final locale = Localizations.localeOf(context).toLanguageTag();
  if (value > 0 && value < 0.000001) return _strings(context).usageTinyCost;
  return NumberFormat.currency(
    locale: locale,
    name: 'USD',
    symbol: r'$',
    decimalDigits: value > 0 && value < 0.01 ? 6 : 2,
  ).format(value);
}

class _UsageReport extends StatelessWidget {
  final UsageSnapshot snapshot;
  const _UsageReport({required this.snapshot});

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final stats = snapshot.statistics;
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toLanguageTag();
    final number = NumberFormat.decimalPattern(locale);
    final date = DateFormat.yMMMd(locale);
    final time = DateFormat.Hm(locale);
    final tools = stats.tools;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (snapshot.projectName case final name?) ...[
          Text(name, style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
        ],
        Text(
          l10n.usagePeriod(
            date.format(DateTime.fromMillisecondsSinceEpoch(stats.from)),
            date.format(
              DateTime.fromMillisecondsSinceEpoch(
                stats.to > stats.from ? stats.to - 1 : stats.to,
              ),
            ),
          ),
        ),
        Text(
          l10n.usageTimezone(snapshot.query.timezone),
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: 16),
        Card(
          margin: EdgeInsets.zero,
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(l10n.usageReportedCost, style: theme.textTheme.titleSmall),
                const SizedBox(height: 8),
                Text(
                  _money(context, stats.cost),
                  key: const ValueKey('usage-total-cost'),
                  style: theme.textTheme.headlineLarge,
                ),
                const SizedBox(height: 16),
                _MetricGrid(
                  values: [
                    (l10n.usageSessions, number.format(stats.sessions)),
                    (l10n.usagePrompts, number.format(stats.prompts)),
                    (l10n.usageSteps, number.format(stats.steps)),
                    (l10n.usageSubagents, number.format(stats.subagents)),
                    (l10n.usageActiveDays, number.format(stats.activeDays)),
                    (l10n.usageStreak, number.format(stats.streak)),
                  ],
                ),
              ],
            ),
          ),
        ),
        if (stats.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 20),
            child: Text(l10n.usageEmpty),
          ),
        const SizedBox(height: 24),
        Text(l10n.usageTokens, style: theme.textTheme.titleLarge),
        const SizedBox(height: 12),
        _MetricGrid(
          values: [
            (l10n.usageTotalTokens, number.format(stats.tokens.total)),
            (l10n.usageInput, number.format(stats.tokens.input)),
            (l10n.usageOutput, number.format(stats.tokens.output)),
            (l10n.usageReasoning, number.format(stats.tokens.reasoning)),
            (l10n.usageCacheRead, number.format(stats.tokens.cacheRead)),
            (l10n.usageCacheWrite, number.format(stats.tokens.cacheWrite)),
          ],
        ),
        const SizedBox(height: 24),
        Text(l10n.usageModels, style: theme.textTheme.titleLarge),
        const SizedBox(height: 12),
        if (stats.models.isEmpty) Text(l10n.usageNoModels),
        for (final model in stats.models)
          Card(
            margin: const EdgeInsets.only(bottom: 10),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(model.modelID, style: theme.textTheme.titleMedium),
                  Text(
                    [
                      model.providerID,
                      if (model.variant?.isNotEmpty == true) model.variant!,
                    ].join(' · '),
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 16,
                    runSpacing: 8,
                    children: [
                      Text(_money(context, model.cost)),
                      Text(l10n.usageModelSteps(number.format(model.steps))),
                      Text(
                        l10n.usageModelTokens(
                          number.format(model.tokens.total),
                        ),
                      ),
                    ],
                  ),
                  if (stats.cost > 0) ...[
                    const SizedBox(height: 12),
                    LinearProgressIndicator(
                      value: (model.cost / stats.cost).clamp(0, 1),
                      semanticsLabel: l10n.usageCostShare,
                      semanticsValue: NumberFormat.percentPattern(
                        locale,
                      ).format(model.cost / stats.cost),
                    ),
                  ],
                ],
              ),
            ),
          ),
        const SizedBox(height: 24),
        Text(l10n.usageToolReliability, style: theme.textTheme.titleLarge),
        const SizedBox(height: 12),
        if (tools == null)
          Text(l10n.usageToolsUnavailable)
        else if (tools.calls == 0)
          Text(l10n.usageNoTools)
        else ...[
          Text(
            tools.successRate == null
                ? l10n.usageNoFinishedTools
                : l10n.usageSuccessRate(
                    NumberFormat.percentPattern(
                      locale,
                    ).format(tools.successRate),
                  ),
            style: theme.textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          _MetricGrid(
            values: [
              (l10n.usageToolCalls, number.format(tools.calls)),
              (l10n.usageSucceeded, number.format(tools.succeeded)),
              (l10n.usageFailed, number.format(tools.failed)),
              (l10n.usageUnfinished, number.format(tools.unfinished)),
            ],
          ),
        ],
        const SizedBox(height: 24),
        Text(
          l10n.usageUpdated(time.format(snapshot.fetchedAt.toLocal())),
          style: theme.textTheme.bodySmall,
        ),
        const SizedBox(height: 8),
        Text(l10n.usageCostDisclosure, style: theme.textTheme.bodySmall),
      ],
    );
  }
}

class _MetricGrid extends StatelessWidget {
  final List<(String, String)> values;
  const _MetricGrid({required this.values});
  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final columns =
          constraints.maxWidth < 300 ||
              MediaQuery.textScalerOf(context).scale(16) > 24
          ? 1
          : 2;
      final width = (constraints.maxWidth - (columns - 1) * 16) / columns;
      return Wrap(
        spacing: 16,
        runSpacing: 16,
        children: [
          for (final (label, value) in values)
            SizedBox(
              width: width,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: 4),
                  Text(value, style: Theme.of(context).textTheme.titleMedium),
                ],
              ),
            ),
        ],
      );
    },
  );
}
