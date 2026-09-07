import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../api2/transport.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/usage_overview.dart';
import '../../state/usage_budgets.dart';
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
  late final UsageBudgets _budgets;
  @override
  void initState() {
    super.initState();
    final profile = widget.controller.profile;
    final id = profile?.id ?? '';
    final origin = profile?.baseUrl;
    final username = profile?.username;
    _budgets = UsageBudgets(
      preferences: widget.controller.store.prefs,
      profileId: id,
      serverOrigin: '$origin\n$username',
      isCurrent: () =>
          !_overview.detached &&
          widget.controller.profile?.id == id &&
          widget.controller.profile?.baseUrl == origin &&
          widget.controller.profile?.username == username &&
          widget.controller.isProfileReadable(id),
      isProfilePresent: () => widget.controller.isProfileReadable(id),
    );
    unawaited(_overview.refresh());
  }

  @override
  void dispose() {
    if (widget.overview == null) _overview.dispose();
    _budgets.dispose();
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
    listenable: Listenable.merge([_overview, _budgets]),
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
                      if (snapshot != null) Text(l10n.usagePreviousResult),
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
                    if (snapshot != null && _budgets.available) ...[
                      _UsageBudgetControls(
                        budgets: _budgets,
                        snapshot: snapshot,
                        enabled:
                            available &&
                            !_overview.loading &&
                            _overview.error == null,
                      ),
                      const SizedBox(height: 20),
                    ],
                    if (snapshot != null)
                      _UsageReport(snapshot: snapshot, overview: _overview),
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

class _UsageBudgetControls extends StatelessWidget {
  final UsageBudgets budgets;
  final UsageSnapshot snapshot;
  final bool enabled;
  const _UsageBudgetControls({
    required this.budgets,
    required this.snapshot,
    required this.enabled,
  });

  Future<void> _edit(BuildContext context, UsageBudgetUnit unit) async {
    final l10n = _strings(context);
    final controller = TextEditingController(
      text: budgets.limit(snapshot, unit)?.toString() ?? '',
    );
    final form = GlobalKey<FormState>();
    final route = DialogRoute<num>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          unit == UsageBudgetUnit.usd
              ? l10n.usageBudgetUsd
              : l10n.usageBudgetTokens,
        ),
        content: SingleChildScrollView(
          child: Form(
            key: form,
            child: TextFormField(
              controller: controller,
              autofocus: true,
              keyboardType: TextInputType.numberWithOptions(
                decimal: unit == UsageBudgetUnit.usd,
              ),
              decoration: InputDecoration(labelText: l10n.usageBudgetAmount),
              validator: (value) =>
                  UsageBudgets.validLimit(
                    num.tryParse(value?.trim() ?? ''),
                    unit,
                  )
                  ? null
                  : l10n.usageBudgetInvalid,
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, -1),
            child: Text(l10n.usageBudgetRemove),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.workCancel),
          ),
          FilledButton(
            onPressed: () {
              if (form.currentState!.validate()) {
                Navigator.pop(context, num.parse(controller.text.trim()));
              }
            },
            child: Text(l10n.fileSave),
          ),
        ],
      ),
    );
    final result = await Navigator.of(context).push(route);
    await route.completed;
    controller.dispose();
    if (result != null) {
      await budgets.save(snapshot, unit, result == -1 ? null : result);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.usageBudgetTitle,
          style: Theme.of(context).textTheme.titleMedium,
        ),
        Text(l10n.usageBudgetDescription),
        if (budgets.failed)
          Text(
            l10n.quotaBudgetSaveFailed,
            style: TextStyle(color: Theme.of(context).colorScheme.error),
          ),
        for (final unit in UsageBudgetUnit.values) ...[
          const SizedBox(height: 8),
          Builder(
            builder: (context) {
              final limit = budgets.limit(snapshot, unit);
              final tokens = snapshot.statistics.tokens;
              final tokenTotal =
                  [
                    tokens.input,
                    tokens.output,
                    tokens.reasoning,
                    tokens.cacheRead,
                    tokens.cacheWrite,
                  ].fold<BigInt>(
                    BigInt.zero,
                    (sum, value) => sum + BigInt.from(value),
                  );
              final format = NumberFormat.decimalPattern(
                Localizations.localeOf(context).toLanguageTag(),
              );
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (limit != null)
                    Text(
                      l10n.usageBudgetProgress(
                        unit == UsageBudgetUnit.usd
                            ? format.format(snapshot.statistics.cost)
                            : tokenTotal.toString(),
                        format.format(limit),
                        unit == UsageBudgetUnit.usd
                            ? 'USD'
                            : l10n.usageBudgetTokenUnit,
                      ),
                    ),
                  if (limit != null &&
                      (unit == UsageBudgetUnit.usd
                          ? snapshot.statistics.cost >= limit
                          : tokenTotal >= BigInt.from(limit)))
                    Text(
                      enabled
                          ? l10n.usageBudgetReached
                          : l10n.usageBudgetPrevious,
                      style: TextStyle(
                        color: Theme.of(context).colorScheme.error,
                      ),
                    ),
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: TextButton(
                      key: ValueKey('usage-budget-${unit.name}'),
                      onPressed: enabled && !budgets.saving
                          ? () => _edit(context, unit)
                          : null,
                      child: Text(
                        unit == UsageBudgetUnit.usd
                            ? l10n.usageBudgetUsd
                            : l10n.usageBudgetTokens,
                      ),
                    ),
                  ),
                ],
              );
            },
          ),
        ],
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: TextButton(
            onPressed: budgets.saving
                ? null
                : () async {
                    final clear = await showDialog<bool>(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: Text(l10n.usageBudgetClearAll),
                        content: Text(l10n.usageBudgetClearDescription),
                        actions: [
                          TextButton(
                            onPressed: () => Navigator.pop(context, false),
                            child: Text(l10n.workCancel),
                          ),
                          FilledButton(
                            onPressed: () => Navigator.pop(context, true),
                            child: Text(l10n.usageBudgetClearAll),
                          ),
                        ],
                      ),
                    );
                    if (clear == true) await budgets.clearAll();
                  },
            child: Text(l10n.usageBudgetClearAll),
          ),
        ),
      ],
    );
  }
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
  final UsageOverview overview;
  const _UsageReport({required this.snapshot, required this.overview});

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
    final providers = stats.providers;
    final models = overview.matchingModels;
    final matchingProviderIDs = models.map((model) => model.providerID).toSet();
    final subtotal = models.fold<double>(0, (sum, model) => sum + model.cost);
    // BigInt avoids overflowing aggregate record counts on native platforms.
    final matchingSteps = models.fold<BigInt>(
      BigInt.zero,
      (sum, model) => sum + BigInt.from(model.steps),
    );
    final matchingTokens = models.fold<BigInt>(BigInt.zero, (sum, model) {
      final tokens = model.tokens;
      return sum +
          BigInt.from(tokens.input) +
          BigInt.from(tokens.output) +
          BigInt.from(tokens.reasoning) +
          BigInt.from(tokens.cacheRead) +
          BigInt.from(tokens.cacheWrite);
    });
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
        Text(l10n.usageScopedTotals, style: theme.textTheme.titleMedium),
        const SizedBox(height: 8),
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
        Text(l10n.usageProviders, style: theme.textTheme.titleLarge),
        const SizedBox(height: 8),
        Text(l10n.usageProviderScope, style: theme.textTheme.bodySmall),
        const SizedBox(height: 8),
        Text(l10n.usageInspectionDisclosure, style: theme.textTheme.bodySmall),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(
          key: ValueKey((overview.filterRevision, overview.providerFilter)),
          initialValue: overview.providerFilter ?? '',
          isExpanded: true,
          decoration: InputDecoration(
            labelText: l10n.usageProviderFilter,
            border: const OutlineInputBorder(),
          ),
          items: [
            DropdownMenuItem(value: '', child: Text(l10n.usageAllProviders)),
            for (final id in {
              ...providers.map((provider) => provider.providerID),
              if (overview.providerFilter != null) overview.providerFilter!,
            }.toList()..sort())
              DropdownMenuItem(
                value: id,
                child: Text(id, overflow: TextOverflow.ellipsis),
              ),
          ],
          onChanged: (value) =>
              overview.setProviderFilter(value == '' ? null : value),
        ),
        const SizedBox(height: 12),
        TextFormField(
          key: ValueKey('usage-search-${overview.filterRevision}'),
          initialValue: overview.modelSearch,
          decoration: InputDecoration(
            labelText: l10n.usageSearchRecords,
            prefixIcon: const Icon(Icons.search),
            border: const OutlineInputBorder(),
          ),
          onChanged: overview.setModelSearch,
        ),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: TextButton.icon(
            onPressed: overview.hasInspectionFilters
                ? overview.clearInspectionFilters
                : null,
            icon: const Icon(Icons.filter_alt_off_outlined),
            label: Text(l10n.usageClearFilters),
          ),
        ),
        Text(l10n.usageScopedProviderTotals, style: theme.textTheme.bodySmall),
        const SizedBox(height: 12),
        if (providers.isEmpty) Text(l10n.usageNoModels),
        for (final provider in providers)
          if (matchingProviderIDs.contains(provider.providerID))
            Card(
              key: ValueKey('usage-provider-${provider.providerID}'),
              margin: const EdgeInsets.only(bottom: 10),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      provider.providerID,
                      style: theme.textTheme.titleMedium,
                    ),
                    Text(l10n.usageProviderModelCount(provider.modelCount)),
                    const SizedBox(height: 8),
                    Text(
                      provider.cost == null
                          ? l10n.usageProviderCostUnavailable
                          : _money(context, provider.cost!),
                      style: theme.textTheme.titleLarge,
                    ),
                    if (provider.cost != null &&
                        stats.cost > 0 &&
                        provider.cost! <= stats.cost)
                      Text(
                        l10n.usageProviderCostShare(
                          NumberFormat.percentPattern(
                            locale,
                          ).format(provider.cost! / stats.cost),
                        ),
                      ),
                  ],
                ),
              ),
            ),
        const SizedBox(height: 24),
        Text(l10n.usageModels, style: theme.textTheme.titleLarge),
        const SizedBox(height: 12),
        Text(l10n.usageMatchingSubtotal, style: theme.textTheme.titleMedium),
        Text(l10n.usageMatchingRecords(number.format(models.length))),
        Wrap(
          spacing: 16,
          runSpacing: 8,
          children: [
            Text(
              subtotal.isFinite
                  ? _money(context, subtotal)
                  : l10n.usageProviderCostUnavailable,
            ),
            Text(l10n.usageModelSteps(matchingSteps.toString())),
            Text(l10n.usageModelTokens(matchingTokens.toString())),
          ],
        ),
        const SizedBox(height: 12),
        if (models.isEmpty)
          Text(
            overview.hasInspectionFilters
                ? l10n.usageNoMatchingRecords
                : l10n.usageNoModels,
          ),
        for (final model in models)
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
                  if (stats.cost > 0 && model.cost <= stats.cost) ...[
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
