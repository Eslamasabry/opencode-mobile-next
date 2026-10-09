import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../api2/transport.dart';
import '../../domain/quota_answers.dart';
import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/usage_budgets.dart';
import '../../state/usage_overview.dart';
import '../app_theme.dart';
import '../kit/kit_bidi.dart';
import '../kit/kit_buttons.dart';
import '../kit/kit_capability_explainer.dart';
import '../kit/kit_choice_list.dart';
import '../kit/kit_sheet.dart';
import '../kit/kit_details_fold.dart';
import '../kit/kit_dialog.dart';
import '../kit/kit_field.dart';
import '../kit/kit_menu.dart';
import '../kit/kit_notice.dart';
import '../kit/kit_progress_row.dart';
import '../kit/kit_row.dart';
import '../kit/kit_screen.dart';
import '../kit/kit_search_field.dart';
import '../kit/kit_state_view.dart';
import '../kit/kit_surface.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import '../kit/kit_top_bar.dart';
import '../kit/motion/kit_refresh.dart';
import '../widgets/product_states.dart';
import 'usage_refresh_slot.dart';

AppLocalizations _strings(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// Spent: what the connected OpenCode 2 server reports it used, for a date
/// range and a project scope, with the person's own budgets.
///
/// Order (map `usage`, owner verdict "Rethink"): the total first, then the
/// range and scope that shape it, then the budgets, then the breakdowns
/// (activity, tokens, providers, models, tools). The long explanations of
/// what the numbers are and are not fold under one Details at the end; the
/// one sentence that this is not the provider's bill stays visible.
/// The total's label names the days the server actually covered
/// (slice-P5.4).
///
/// A server without usage statistics is explained in place
/// (KitCapabilityExplainer, `server.oc2`) instead of an error line.
class UsageScreen extends StatefulWidget {
  final ConnectionController controller;
  final UsageOverview? overview;

  /// True as the "Spent" section of the Usage screen, which already supplies
  /// the app bar.
  final bool embedded;

  /// Inside Usage, where this section's Refresh goes: the Usage top bar
  /// holds one Refresh for the active tab.
  final UsageRefreshSlot? refreshSlot;
  const UsageScreen({
    super.key,
    required this.controller,
    this.overview,
    this.embedded = false,
    this.refreshSlot,
  });
  @override
  State<UsageScreen> createState() => _UsageScreenState();
}

class _UsageScreenState extends State<UsageScreen> {
  late final UsageOverview _overview =
      widget.overview ?? UsageOverview(widget.controller);
  late final UsageBudgets _budgets;
  late final TextEditingController _search = TextEditingController(
    text: _overview.modelSearch,
  );
  late int _filterRevision = _overview.filterRevision;

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
    _overview.addListener(_syncSearch);
    unawaited(_overview.refresh());
  }

  /// A new range or scope resets the inspection filters; the field follows.
  void _syncSearch() {
    if (_overview.filterRevision == _filterRevision) return;
    _filterRevision = _overview.filterRevision;
    if (_search.text != _overview.modelSearch) {
      _search.text = _overview.modelSearch;
    }
  }

  @override
  void dispose() {
    _overview.removeListener(_syncSearch);
    if (widget.overview == null) _overview.dispose();
    _budgets.dispose();
    _search.dispose();
    super.dispose();
  }

  String _error(Object error, AppLocalizations l10n) => switch (error) {
    UsageUnsupported() => l10n.usageUnsupported,
    UsageProjectUnavailable() => l10n.usageProjectUnavailable,
    UsageTimezoneUnavailable() => l10n.usageTimezoneUnavailable,
    UsageRefreshInterrupted() => l10n.usageRefreshInterrupted,
    FormatException() => l10n.usageInvalidResponse,
    Api2Error(statusCode: 401 || 403) => l10n.usageAuthorization,
    _ => productErrorText(error, l10n: l10n),
  };

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([_overview, _budgets]),
    builder: (context, _) {
      final l10n = _strings(context);
      final tokens = KitTokens.of(context);
      final snapshot = _overview.snapshot;
      final unsupported = _overview.error is UsageUnsupported;
      final available = !_overview.detached && !unsupported;
      final canRefresh = available && !_overview.loading;
      // Inside Usage the one top bar holds Refresh for the active tab.
      widget.refreshSlot?.offer(
        visible: !unsupported,
        onRefresh: canRefresh ? _overview.refresh : null,
        disabledReason: canRefresh ? null : l10n.usageLoading,
      );
      Widget gap([double? height]) => SizedBox(height: height ?? tokens.space4);
      final body = KitRefresh(
        onRefresh: _overview.refresh,
        child: ListView(
          key: const ValueKey('usage-content'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: KitScreen.padding(context),
          children: [
            SizedBox(height: tokens.space3),
            KitText(l10n.usageDescription, role: KitTextRole.secondary),
            if (_overview.detached) ...[
              gap(),
              KitNotice(
                icon: AppIconography.swap,
                message: l10n.usageLocationChanged,
              ),
            ],
            if (!unsupported)
              if (_overview.error case final error?) ...[
                gap(),
                KitNotice.error(
                  message: _error(error, l10n),
                  error: error,
                  details: productErrorDetails(error),
                  reportSource: 'usage',
                  retry: available
                      ? KitAction(
                          label: l10n.usageRefresh,
                          icon: AppIconography.retry,
                          onPressed: _overview.loading
                              ? null
                              : _overview.refresh,
                        )
                      : null,
                ),
              ],
            if (snapshot != null &&
                (_overview.loading || _overview.error != null)) ...[
              SizedBox(height: tokens.space2),
              KitText(l10n.usagePreviousResult, role: KitTextRole.secondary),
            ],
            if (snapshot != null) ...[gap(), _UsageTotal(snapshot: snapshot)],
            if (available) ...[
              SizedBox(height: tokens.sectionGap),
              _UsageFilters(overview: _overview),
            ],
            if (snapshot != null && _budgets.available) ...[
              SizedBox(height: tokens.sectionGap),
              _UsageBudgetControls(
                budgets: _budgets,
                snapshot: snapshot,
                enabled:
                    available && !_overview.loading && _overview.error == null,
              ),
            ],
            if (snapshot != null && !snapshot.statistics.isEmpty)
              _UsageReport(
                snapshot: snapshot,
                overview: _overview,
                search: _search,
              ),
            if (snapshot != null) ...[
              SizedBox(height: tokens.sectionGap),
              KitText(
                l10n.usageUpdated(
                  DateFormat.Hm(
                    Localizations.localeOf(context).toLanguageTag(),
                  ).format(snapshot.fetchedAt.toLocal()),
                ),
                role: KitTextRole.caption,
              ),
              SizedBox(height: tokens.space2),
              KitText(l10n.usageCostDisclosure, role: KitTextRole.secondary),
              gap(),
              KitDetailsFold(
                label: l10n.usageAboutNumbers,
                notes: [
                  l10n.usageProviderScope,
                  l10n.usageScopedProviderTotals,
                  l10n.usageInspectionDisclosure,
                  l10n.usageBudgetDescription,
                ],
              ),
            ],
          ],
        ),
      );
      return KitScreen(
        topBar: widget.embedded
            ? null
            : KitTopBar(
                title: l10n.usageTitle,
                actions: [
                  if (!unsupported)
                    KitAction(
                      key: const ValueKey('refresh-usage'),
                      label: l10n.usageRefresh,
                      icon: AppIconography.retry,
                      onPressed: canRefresh ? _overview.refresh : null,
                      disabledReason: canRefresh ? null : l10n.usageLoading,
                    ),
                ],
              ),
        width: KitScreenWidth.reading,
        loading: _overview.loading,
        loadingLabel: l10n.usageLoading,
        // The whole page is the missing feature: say why, and offer the
        // switch where this phone's server can make it.
        body: unsupported
            ? KitCapabilityExplainer.state(
                key: const ValueKey('usage-unsupported'),
                capability: 'server.oc2',
                serverName: widget.controller.profile?.name,
                size: KitStateSize.page,
                source: 'usage',
              )
            : body,
      );
    },
  );
}

/// A section's name above its content (sentence case, a header for
/// assistive technology): the label KitRowGroup draws, for content that is
/// not a row panel.
class _Label extends StatelessWidget {
  const _Label(this.text);
  final String text;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.space1,
        end: tokens.space1,
        bottom: tokens.labelGap,
      ),
      child: Semantics(
        header: true,
        child: KitText(text, role: KitTextRole.label),
      ),
    );
  }
}

/// The answer first: what was spent and over which days. The label names
/// the days the server actually covered: when they differ from the range
/// asked for (a 30-day request answered for Sep 2 – 6) it gives those
/// dates instead of claiming the range (slice-P5.4).
class _UsageTotal extends StatelessWidget {
  const _UsageTotal({required this.snapshot});
  final UsageSnapshot snapshot;

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final tokens = KitTokens.of(context);
    final stats = snapshot.statistics;
    final covered = SpentPeriod.fromUsage(
      query: snapshot.query,
      statistics: stats,
      requestedRange: snapshot.range,
    );
    final days = spentDays(
      covered,
      Localizations.localeOf(context).toLanguageTag(),
      l10n,
    );
    final spent = covered.matchesRequestedRange
        ? switch (snapshot.range) {
            UsageRange.today => l10n.usageSpentToday,
            UsageRange.thirtyDays => l10n.usageSpentThirtyDays,
            UsageRange.year => l10n.usageSpentYear,
            UsageRange.allTime => l10n.usageSpentAllTime,
          }
        : l10n.usageSpentPeriod(days);
    return KitSurface.panel(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          KitText(
            spent,
            key: const ValueKey('usage-total-period'),
            role: KitTextRole.label,
          ),
          SizedBox(height: tokens.space1),
          KitText(
            _money(context, stats.cost),
            key: const ValueKey('usage-total-cost'),
            role: KitTextRole.largeTitle,
            tabular: true,
          ),
          SizedBox(height: tokens.space2),
          // The dates show once: in the label when they are not the range
          // asked for, otherwise here beside the project.
          KitText(
            [
              if (snapshot.projectName case final name?) KitBidi.auto(name),
              if (covered.matchesRequestedRange) days,
            ].join(' · '),
            role: KitTextRole.secondary,
          ),
          KitText(
            l10n.usageTimezone(snapshot.query.timezone),
            role: KitTextRole.caption,
          ),
          if (stats.isEmpty) ...[
            SizedBox(height: tokens.space3),
            KitText(l10n.usageEmpty),
          ],
        ],
      ),
    );
  }
}

/// The days [period] covers, compact: "Sep 2 – 6", "Aug 30 – Sep 3",
/// "Sep 2", or with years when they differ. The end is the last included
/// instant (the interval is half-open). The query's timezone is this
/// device's own (UsageOverview reads it from the device), so local time is
/// the display timezone.
String spentDays(SpentPeriod period, String locale, AppLocalizations l10n) {
  final from = period.from.toLocal();
  final last =
      (period.to.isAfter(period.from)
              ? period.to.subtract(const Duration(milliseconds: 1))
              : period.to)
          .toLocal();
  final sameDay =
      from.year == last.year &&
      from.month == last.month &&
      from.day == last.day;
  if (sameDay) return DateFormat.MMMd(locale).format(from);
  if (from.year != last.year) {
    final full = DateFormat.yMMMd(locale);
    return l10n.usagePeriod(full.format(from), full.format(last));
  }
  final start = DateFormat.MMMd(locale).format(from);
  final end = from.month == last.month
      ? DateFormat.d(locale).format(last)
      : DateFormat.MMMd(locale).format(last);
  return l10n.usagePeriod(start, end);
}

/// The range and the project scope: what the total above covers.
class _UsageFilters extends StatelessWidget {
  const _UsageFilters({required this.overview});
  final UsageOverview overview;

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    String rangeLabel(UsageRange range) => switch (range) {
      UsageRange.today => l10n.usageToday,
      UsageRange.thirtyDays => l10n.usageThirtyDays,
      UsageRange.year => l10n.usageYear,
      UsageRange.allTime => l10n.usageAllTime,
    };
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitRowGroup(
          margin: EdgeInsetsDirectional.zero,
          leadingIcons: true,
          children: [
            // A picker, not a segmented control: four ranges do not fit
            // one line at phone width until KitSegmented stacks (contract
            // problem in the QA record).
            KitPickerRow<UsageRange>(
              rowKey: ValueKey('usage-range-${overview.range.name}'),
              leading: KitRow.icon(context, AppIconography.calendar),
              title: l10n.usageRangeLabel,
              selected: overview.range,
              onSelected: (range) => unawaited(overview.setRange(range)),
              choices: [
                for (final range in UsageRange.values)
                  KitChoice(
                    key: ValueKey('usage-range-choice-${range.name}'),
                    value: range,
                    title: rangeLabel(range),
                  ),
              ],
            ),
            KitPickerRow<UsageScope>(
              rowKey: ValueKey('usage-scope-${overview.scope.name}'),
              leading: KitRow.icon(context, AppIconography.projects),
              title: l10n.usageScope,
              selected: overview.scope,
              onSelected: (scope) => unawaited(overview.setScope(scope)),
              choices: [
                KitChoice(
                  value: UsageScope.allProjects,
                  title: l10n.usageAllProjects,
                ),
                KitChoice(
                  value: UsageScope.currentProject,
                  title: l10n.usageCurrentProject,
                ),
              ],
            ),
          ],
        ),
      ],
    );
  }
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

  BigInt get _tokenTotal {
    final tokens = snapshot.statistics.tokens;
    return [
      tokens.input,
      tokens.output,
      tokens.reasoning,
      tokens.cacheRead,
      tokens.cacheWrite,
    ].fold<BigInt>(BigInt.zero, (sum, value) => sum + BigInt.from(value));
  }

  bool _reached(UsageBudgetUnit unit, num limit) => unit == UsageBudgetUnit.usd
      ? snapshot.statistics.cost >= limit
      : _tokenTotal >= BigInt.from(limit);

  /// The budget amount as one short text entry. Remove is offered only
  /// when a budget exists; it can be set again, so it is not destructive.
  Future<void> _edit(BuildContext context, UsageBudgetUnit unit) async {
    final l10n = _strings(context);
    final current = budgets.limit(snapshot, unit);
    final usd = unit == UsageBudgetUnit.usd;
    final text = await showKitInputDialog(
      context,
      title: usd ? l10n.usageBudgetUsd : l10n.usageBudgetTokens,
      label: l10n.usageBudgetAmount,
      confirmLabel: usd ? l10n.usageBudgetSaveUsd : l10n.usageBudgetSaveTokens,
      cancelLabel: l10n.workCancel,
      // showKitInputDialog has no `decimal` switch yet, and the number
      // kind is digits only: a dollar amount like 2.50 needs the text kind
      // (contract problem in docs/qa/revamp-screen-usage-1).
      kind: usd ? KitFieldKind.text : KitFieldKind.number,
      initial: current?.toString(),
      helper: usd ? l10n.usageBudgetHelperUsd : l10n.usageBudgetHelperTokens,
      // The kit shows the reason only after the first edit (or a submit
      // attempt), so an empty dialog opens without an error.
      validate: (value) =>
          UsageBudgets.validLimit(num.tryParse(value.trim()), unit)
          ? null
          : usd
          ? l10n.usageBudgetInvalidUsd
          : l10n.usageBudgetInvalidTokens,
      alternative: current == null
          ? null
          : KitAction(
              key: ValueKey('usage-budget-remove-${unit.name}'),
              label: l10n.usageBudgetRemove,
              icon: AppIconography.removeCircle,
              onPressed: () => unawaited(budgets.save(snapshot, unit, null)),
            ),
      fieldKey: const ValueKey('usage-budget-amount'),
    );
    if (text == null) return;
    final value = num.tryParse(text.trim());
    if (value != null) await budgets.save(snapshot, unit, value);
  }

  Future<void> _clear(BuildContext context) async {
    final l10n = _strings(context);
    final clear = await showKitConfirm(
      context,
      title: l10n.usageBudgetClearTitle,
      body: l10n.usageBudgetClearDescription,
      confirmLabel: l10n.usageBudgetClearConfirm,
      icon: AppIconography.delete,
      kind: KitConfirmKind.destructive,
    );
    if (clear) await budgets.clearAll();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final tokens = KitTokens.of(context);
    final format = NumberFormat.decimalPattern(
      Localizations.localeOf(context).toLanguageTag(),
    );
    final canEdit = enabled && !budgets.saving;
    var reached = false;
    final rows = <Widget>[];
    for (final unit in UsageBudgetUnit.values) {
      final usd = unit == UsageBudgetUnit.usd;
      final limit = budgets.limit(snapshot, unit);
      final key = ValueKey('usage-budget-${unit.name}');
      if (limit == null) {
        rows.add(
          KitRow(
            key: key,
            leading: KitRow.icon(
              context,
              usd ? AppIconography.usage : AppIconography.dataObject,
            ),
            title: usd ? l10n.usageBudgetUsd : l10n.usageBudgetTokens,
            trailing: KitRowValue(l10n.usageBudgetNotSet),
            enabled: canEdit,
            disabledReason: canEdit ? null : l10n.usageBudgetWaitReason,
            onTap: canEdit ? () => _edit(context, unit) : null,
          ),
        );
        continue;
      }
      final used = usd ? snapshot.statistics.cost : _tokenTotal.toDouble();
      reached = reached || _reached(unit, limit);
      rows.add(
        KitProgressRow(
          key: key,
          leading: KitRow.icon(
            context,
            usd ? AppIconography.usage : AppIconography.dataObject,
          ),
          title: usd ? l10n.usageBudgetUsdTitle : l10n.usageBudgetTokensTitle,
          value: limit > 0 ? used / limit : 1,
          valueLabel: l10n.usageBudgetProgress(
            usd
                ? format.format(snapshot.statistics.cost)
                : _count(format, _tokenTotal),
            format.format(limit),
            usd ? 'USD' : l10n.usageBudgetTokenUnit,
          ),
          onTap: canEdit ? () => _edit(context, unit) : null,
        ),
      );
    }
    // Clearing is offered only when there is something to clear: a budget
    // for this scope, or one saved for another range or project.
    final anySet = UsageBudgetUnit.values.any(
      (unit) => budgets.limit(snapshot, unit) != null,
    );
    if (anySet || budgets.hasSaved) {
      rows.add(
        KitRow(
          key: const ValueKey('usage-budget-clear'),
          leading: KitRow.icon(context, AppIconography.delete),
          title: l10n.usageBudgetClearAll,
          destructive: true,
          enabled: !budgets.saving,
          disabledReason: budgets.saving ? l10n.usageBudgetWaitReason : null,
          onTap: budgets.saving ? null : () => _clear(context),
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        if (budgets.failed) ...[
          KitNotice.error(message: l10n.quotaBudgetSaveFailed),
          SizedBox(height: tokens.space3),
        ],
        KitRowGroup(
          margin: EdgeInsetsDirectional.zero,
          label: l10n.usageBudgetTitle,
          children: rows,
        ),
        if (reached) ...[
          SizedBox(height: tokens.space3),
          KitNotice(
            key: const ValueKey('usage-budget-reached'),
            icon: AppIconography.warning,
            message: enabled
                ? l10n.usageBudgetReached
                : l10n.usageBudgetPrevious,
          ),
        ],
      ],
    );
  }
}

/// A count grouped like the other figures; a sum too large for an int
/// (never seen in practice) falls back to plain digits.
String _count(NumberFormat number, BigInt value) =>
    value.isValidInt ? number.format(value.toInt()) : value.toString();

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

/// A day the server names as `YYYY-MM-DD`, as the person's calendar writes
/// it; a value that is not a date is shown as the server sent it.
String _dayLabel(String date, String locale) {
  final parsed = DateTime.tryParse(date);
  return parsed == null ? date : DateFormat.MMMd(locale).format(parsed);
}

/// The breakdowns under the total: activity, tokens, providers, models and
/// tool reliability.
class _UsageReport extends StatelessWidget {
  final UsageSnapshot snapshot;
  final UsageOverview overview;
  final TextEditingController search;
  const _UsageReport({
    required this.snapshot,
    required this.overview,
    required this.search,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = _strings(context);
    final tokens = KitTokens.of(context);
    final stats = snapshot.statistics;
    final locale = Localizations.localeOf(context).toLanguageTag();
    final number = NumberFormat.decimalPattern(locale);
    final percent = NumberFormat.percentPattern(locale);
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
    final providerIds = {
      ...providers.map((provider) => provider.providerID),
      ?overview.providerFilter,
    }.toList()..sort();
    Widget section() => SizedBox(height: tokens.sectionGap);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        section(),
        _Label(l10n.usageScopedTotals),
        KitSurface.panel(
          child: _Metrics(
            values: [
              (l10n.usageSessions, number.format(stats.sessions)),
              (l10n.usagePrompts, number.format(stats.prompts)),
              (l10n.usageSteps, number.format(stats.steps)),
              (l10n.usageSubagents, number.format(stats.subagents)),
              (l10n.usageActiveDays, number.format(stats.activeDays)),
              (l10n.usageStreak, number.format(stats.streak)),
            ],
          ),
        ),
        if (stats.activity.isNotEmpty) ...[
          section(),
          KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            label: l10n.usageBusiestDays,
            leadingIcons: false,
            children: [
              for (final day in ([
                ...stats.activity,
              ]..sort((a, b) => b.steps.compareTo(a.steps))).take(5))
                KitRow(
                  key: ValueKey('usage-day-${day.date}'),
                  title: _dayLabel(day.date, locale),
                  trailing: KitRowValue(
                    l10n.usageDaySteps(day.steps),
                    chevron: false,
                  ),
                ),
            ],
          ),
        ],
        section(),
        _Label(l10n.usageTokens),
        KitSurface.panel(
          child: _Metrics(
            values: [
              (l10n.usageTotalTokens, number.format(stats.tokens.total)),
              (l10n.usageInput, number.format(stats.tokens.input)),
              (l10n.usageOutput, number.format(stats.tokens.output)),
              (l10n.usageReasoning, number.format(stats.tokens.reasoning)),
              (l10n.usageCacheRead, number.format(stats.tokens.cacheRead)),
              (l10n.usageCacheWrite, number.format(stats.tokens.cacheWrite)),
            ],
          ),
        ),
        section(),
        _Label(l10n.usageModels),
        // Provider is the search field's filter: one place to narrow the
        // provider and model lists, with the active filter in words.
        KitSearchField(
          fieldKey: const ValueKey('usage-search'),
          label: l10n.usageSearchRecords,
          controller: search,
          onChanged: overview.setModelSearch,
          resultCount: overview.hasInspectionFilters ? models.length : null,
          filters: [
            for (final id in providerIds)
              KitMenuItem(
                key: ValueKey('usage-filter-$id'),
                label: id,
                checked: overview.providerFilter == id,
                onSelected: () => overview.setProviderFilter(
                  overview.providerFilter == id ? null : id,
                ),
              ),
          ],
          activeFilter: overview.providerFilter,
          onClearFilter: () => overview.setProviderFilter(null),
        ),
        SizedBox(height: tokens.space3),
        if (providers.isEmpty)
          KitText(l10n.usageNoModels, role: KitTextRole.secondary)
        else if (matchingProviderIDs.isNotEmpty)
          KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            label: l10n.usageProviders,
            leadingIcons: false,
            children: [
              for (final provider in providers)
                if (matchingProviderIDs.contains(provider.providerID))
                  KitRow(
                    key: ValueKey('usage-provider-${provider.providerID}'),
                    title: provider.providerID,
                    supporting: TextSpan(
                      text: [
                        l10n.usageProviderModelCount(provider.modelCount),
                        if (provider.cost != null &&
                            stats.cost > 0 &&
                            provider.cost! <= stats.cost)
                          l10n.usageProviderCostShare(
                            percent.format(provider.cost! / stats.cost),
                          ),
                      ].join(' · '),
                    ),
                    trailing: KitRowValue(
                      provider.cost == null
                          ? l10n.usageProviderCostUnavailable
                          : _money(context, provider.cost!),
                      chevron: false,
                    ),
                  ),
            ],
          ),
        SizedBox(height: tokens.space4),
        if (models.isEmpty)
          overview.hasInspectionFilters
              ? KitSearchNoMatch(
                  query: overview.modelSearch.isEmpty
                      ? (overview.providerFilter ?? '')
                      : overview.modelSearch,
                  what: l10n.usageModels,
                  onClear: overview.clearInspectionFilters,
                )
              : KitText(l10n.usageNoModels, role: KitTextRole.secondary)
        else ...[
          _Label(
            [
              l10n.usageMatchingRecords(number.format(models.length)),
              subtotal.isFinite
                  ? _money(context, subtotal)
                  : l10n.usageProviderCostUnavailable,
              l10n.usageModelSteps(_count(number, matchingSteps)),
              l10n.usageModelTokens(_count(number, matchingTokens)),
            ].join(' · '),
          ),
          KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            leadingIcons: false,
            children: [
              for (final model in models)
                KitProgressRow(
                  title: model.modelID,
                  // Only a share of the total is a bar; a record the server
                  // priced above the total has no honest share.
                  value: stats.cost > 0 && model.cost <= stats.cost
                      ? (model.cost / stats.cost).clamp(0, 1).toDouble()
                      : 0,
                  tone: AppStatusTone.progress,
                  valueLabel: [
                    model.providerID,
                    if (model.variant?.isNotEmpty == true) model.variant!,
                    _money(context, model.cost),
                    l10n.usageModelSteps(number.format(model.steps)),
                    l10n.usageModelTokens(number.format(model.tokens.total)),
                  ].join(' · '),
                ),
            ],
          ),
        ],
        section(),
        _Label(l10n.usageToolReliability),
        if (tools == null)
          KitText(l10n.usageToolsUnavailable, role: KitTextRole.secondary)
        else if (tools.calls == 0)
          KitText(l10n.usageNoTools, role: KitTextRole.secondary)
        else
          KitSurface.panel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                KitText(
                  tools.successRate == null
                      ? l10n.usageNoFinishedTools
                      : l10n.usageSuccessRate(
                          percent.format(tools.successRate),
                        ),
                  role: KitTextRole.headline,
                ),
                SizedBox(height: tokens.space3),
                _Metrics(
                  values: [
                    (l10n.usageToolCalls, number.format(tools.calls)),
                    (l10n.usageSucceeded, number.format(tools.succeeded)),
                    (l10n.usageFailed, number.format(tools.failed)),
                    (l10n.usageUnfinished, number.format(tools.unfinished)),
                  ],
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// Labelled figures in two columns (one at large text), inside a panel.
class _Metrics extends StatelessWidget {
  const _Metrics({required this.values});
  final List<(String, String)> values;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final columns = MediaQuery.textScalerOf(context).scale(1) >= 1.5 ? 1 : 2;
    return LayoutBuilder(
      builder: (context, constraints) {
        final width =
            (constraints.maxWidth - (columns - 1) * tokens.space4) / columns;
        return Wrap(
          spacing: tokens.space4,
          runSpacing: tokens.space4,
          children: [
            for (final (label, value) in values)
              SizedBox(
                width: width,
                child: MergeSemantics(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      KitText(label, role: KitTextRole.secondary),
                      SizedBox(height: tokens.space1),
                      KitText(value, role: KitTextRole.headline, tabular: true),
                    ],
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}
