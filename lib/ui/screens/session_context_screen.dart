import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../api/provider_presentation.dart';
import '../../state/connection.dart';
import '../../domain/session_history.dart';
import '../widgets/product_states.dart';
import '../app_theme.dart';
import 'active_context_screen.dart';

enum SessionContextBreakdownKind { user, assistant, tool, other }

class SessionContextBreakdownSegment {
  final SessionContextBreakdownKind kind;
  final int tokens;
  final double percent;

  const SessionContextBreakdownSegment({
    required this.kind,
    required this.tokens,
    required this.percent,
  });
}

class SessionContextMetrics {
  final MessageInfo? currentMessage;
  final CatalogModel? model;
  final int userMessages;
  final int assistantMessages;
  final double sessionCost;
  final List<SessionContextBreakdownSegment> breakdown;

  /// Session-level cost/tokens as the server reported them; null when the
  /// server sent none and the client-side sums stand in.
  final double? serverCost;
  final Tokens? serverTokens;

  const SessionContextMetrics({
    required this.currentMessage,
    required this.model,
    required this.userMessages,
    required this.assistantMessages,
    required this.sessionCost,
    required this.breakdown,
    this.serverCost,
    this.serverTokens,
  });

  /// True when the totals below come from the server rather than a sum over
  /// the loaded messages.
  bool get reportedByServer => serverCost != null || serverTokens != null;

  /// Accumulated cost: the server's figure when it sent one, else the sum of
  /// message costs.
  double get totalCost => serverCost ?? sessionCost;

  Tokens get tokens => currentMessage?.tokens ?? Tokens();
  int get contextLimit => model?.contextLimit ?? 0;
  int get contextTokens => tokens.total;
  double? get usage => contextLimit > 0 ? contextTokens / contextLimit : null;
}

@visibleForTesting
SessionContextMetrics calculateSessionContextMetrics(
  List<MessageWithParts> messages,
  CatalogSnapshot? catalog, {
  Session? session,
}) {
  MessageWithParts? current;
  for (final message in messages.reversed) {
    if (message.info.role == 'assistant' && message.info.tokens.total > 0) {
      current = message;
      break;
    }
  }

  CatalogModel? model;
  final providerID = current?.info.providerID;
  final modelID = current?.info.modelID;
  if (providerID != null && modelID != null) {
    for (final candidate in catalog?.models ?? const <CatalogModel>[]) {
      if (candidate.providerID == providerID && candidate.id == modelID) {
        model = candidate;
        break;
      }
    }
  }

  var userMessages = 0;
  var assistantMessages = 0;
  var sessionCost = 0.0;
  for (final message in messages) {
    if (message.info.role == 'user') userMessages += 1;
    if (message.info.role == 'assistant') {
      assistantMessages += 1;
      sessionCost += message.info.cost;
    }
  }

  return SessionContextMetrics(
    currentMessage: current?.info,
    model: model,
    userMessages: userMessages,
    assistantMessages: assistantMessages,
    sessionCost: sessionCost,
    breakdown: _estimateBreakdown(messages, current?.info.tokens.input ?? 0),
    serverCost: session?.cost,
    serverTokens: session?.tokens,
  );
}

List<SessionContextBreakdownSegment> _estimateBreakdown(
  List<MessageWithParts> messages,
  int inputTokens,
) {
  if (inputTokens <= 0) return const [];
  var userChars = 0;
  var assistantChars = 0;
  var toolChars = 0;

  for (final message in messages) {
    if (message.info.role == 'user') {
      for (final part in message.parts) {
        if (part.type == 'text') userChars += part.text.length;
        if (part.type == 'file') {
          userChars += (part.filename ?? part.url ?? '').length;
        }
      }
      continue;
    }
    if (message.info.role != 'assistant') continue;
    for (final part in message.parts) {
      if (part.type == 'text' || part.type == 'reasoning') {
        assistantChars += part.text.length;
      } else if (part.type == 'tool') {
        toolChars += part.toolState.inputJson?.length ?? 0;
        toolChars += part.toolState.output?.length ?? 0;
      }
    }
  }

  int estimatedTokens(int chars) => (chars / 4).ceil();
  var user = estimatedTokens(userChars);
  var assistant = estimatedTokens(assistantChars);
  var tool = estimatedTokens(toolChars);
  final estimated = user + assistant + tool;
  if (estimated > inputTokens && estimated > 0) {
    final scale = inputTokens / estimated;
    user = (user * scale).floor();
    assistant = (assistant * scale).floor();
    tool = (tool * scale).floor();
  }
  final other = math.max(0, inputTokens - user - assistant - tool);
  final values = <SessionContextBreakdownKind, int>{
    SessionContextBreakdownKind.user: user,
    SessionContextBreakdownKind.assistant: assistant,
    SessionContextBreakdownKind.tool: tool,
    SessionContextBreakdownKind.other: other,
  };
  return [
    for (final entry in values.entries)
      if (entry.value > 0)
        SessionContextBreakdownSegment(
          kind: entry.key,
          tokens: entry.value,
          percent: entry.value / inputTokens * 100,
        ),
  ];
}

class SessionContextScreen extends StatefulWidget {
  final ConnectionController controller;
  final String sessionID;
  final List<MessageWithParts> initialMessages;
  final bool initialHasOlder;

  const SessionContextScreen({
    super.key,
    required this.controller,
    required this.sessionID,
    this.initialMessages = const [],
    this.initialHasOlder = false,
  });

  @override
  State<SessionContextScreen> createState() => _SessionContextScreenState();
}

class _SessionContextScreenState extends State<SessionContextScreen> {
  late List<MessageWithParts> _messages;
  Object? _error;
  bool _loading = false;
  int _generation = 0;
  late int _refreshRevision;
  late final int _locationRevision;
  late int _historyRevision;
  late bool _wasBusy;
  String? _olderCursor;
  bool _hasOlder = false;
  bool _failedOlder = false;
  bool _olderNeedsReload = false;
  final Set<String> _usedCursors = {};
  bool get _sameLocation =>
      widget.controller.locationRevision == _locationRevision;

  @override
  void initState() {
    super.initState();
    _messages = List.of(widget.initialMessages);
    _hasOlder = widget.initialHasOlder;
    _refreshRevision = widget.controller.dataRefreshRevision;
    _locationRevision = widget.controller.locationRevision;
    _historyRevision = widget.controller.sessionHistoryRevision(
      widget.sessionID,
    );
    _wasBusy = widget.controller.busySessions.contains(widget.sessionID);
    widget.controller.addListener(_handleControllerChange);
    unawaited(_load());
  }

  @override
  void dispose() {
    widget.controller.removeListener(_handleControllerChange);
    super.dispose();
  }

  void _handleControllerChange() {
    if (!_sameLocation) {
      _generation++;
      setState(() {
        _messages = [];
        _loading = false;
        _error = null;
      });
      return;
    }
    final refreshChanged =
        widget.controller.dataRefreshRevision != _refreshRevision;
    final busy = widget.controller.busySessions.contains(widget.sessionID);
    final historyRevision = widget.controller.sessionHistoryRevision(
      widget.sessionID,
    );
    final historyChanged = _historyRevision != historyRevision;
    _historyRevision = historyRevision;
    final completed = _wasBusy && !busy;
    _refreshRevision = widget.controller.dataRefreshRevision;
    _wasBusy = busy;
    if (refreshChanged || completed || historyChanged) {
      if (historyChanged &&
          widget.controller.sessionsById[widget.sessionID]?.stagedRevert ==
              null) {
        _messages = [];
      }
      unawaited(_load());
    }
  }

  Future<void> _load({bool older = false}) async {
    if (!_sameLocation) return;
    older = older && !_olderNeedsReload;
    final cursor = older ? _olderCursor : null;
    if (older && (_loading || cursor == null)) return;
    final generation = ++_generation;
    if (mounted) {
      setState(() {
        _loading = true;
        _error = null;
        _failedOlder = older;
      });
    }
    try {
      final api = await widget.controller.prepareActionTransport();
      if (!mounted || generation != _generation || !_sameLocation) return;
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final page = await readHistoryAtStagedBoundary(
        api,
        widget.sessionID,
        cursor: cursor,
        boundary: widget.controller.supportsStagedRevert
            ? widget
                  .controller
                  .sessionsById[widget.sessionID]
                  ?.stagedRevert
                  ?.messageID
            : null,
        isCurrent: () => mounted && generation == _generation,
      );
      if (!mounted || generation != _generation) return;
      if (older &&
          page.hasMore &&
          (page.nextCursor == cursor ||
              _usedCursors.contains(page.nextCursor))) {
        _failedOlder = false;
        _olderNeedsReload = true;
        throw ProductException(
          lookupAppLocalizations(
            Localizations.localeOf(context),
          ).historyCursorExpired,
        );
      }
      setState(() {
        if (older) {
          final existing = _messages.map((message) => message.info.id).toSet();
          _messages = [
            ...page.items.where((message) => existing.add(message.info.id)),
            ..._messages,
          ];
          _usedCursors.add(cursor!);
        } else {
          _messages = page.items;
          _usedCursors.clear();
        }
        _olderCursor = page.nextCursor;
        _hasOlder = page.hasMore;
        _olderNeedsReload = false;
      });
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() {
        _error = error;
        if (older &&
            error is ApiException &&
            (error.statusCode == 400 || error.statusCode == 410)) {
          _olderNeedsReload = true;
          _failedOlder = false;
        }
      });
    } finally {
      if (mounted && generation == _generation) {
        setState(() => _loading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final boundary = widget.controller.supportsStagedRevert
        ? widget
              .controller
              .sessionsById[widget.sessionID]
              ?.stagedRevert
              ?.messageID
        : null;
    final metrics = calculateSessionContextMetrics(
      boundary == null
          ? _messages
          : _messages
                .where((message) => message.info.id.compareTo(boundary) < 0)
                .toList(),
      widget.controller.catalog,
      session: widget.controller.sessionsById[widget.sessionID],
    );
    return Scaffold(
      appBar: AppBar(
        title: const Text('Session context'),
        actions: [
          IconButton(
            tooltip: 'Refresh context',
            onPressed: _loading || !_sameLocation ? null : _load,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: !_sameLocation
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(AppLocalizations.of(context).activeContextChanged),
              ),
            )
          : Column(
              children: [
                if (widget.controller.repository is ActiveContextGateway &&
                    (widget.controller.repository as ActiveContextGateway)
                        .activeContextSupported)
                  ListTile(
                    key: const ValueKey('open-active-context'),
                    leading: const Icon(Icons.subject_rounded),
                    title: Text(
                      AppLocalizations.of(context).activeContextTitle,
                    ),
                    subtitle: Text(
                      AppLocalizations.of(context).activeContextSubtitle,
                    ),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => ActiveContextScreen(
                          controller: widget.controller,
                          sessionID: widget.sessionID,
                        ),
                      ),
                    ),
                  ),
                Expanded(child: _buildBody(metrics)),
              ],
            ),
    );
  }

  Widget _buildBody(SessionContextMetrics metrics) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    if (_loading && _messages.isEmpty) return const LoadingList(rows: 7);
    if (_error != null && _messages.isEmpty) {
      return ProductErrorState(
        message: productErrorText(_error!),
        onRetry: _load,
      );
    }
    if (metrics.currentMessage == null) {
      return ProductEmptyState(
        icon: Icons.donut_large_outlined,
        title: 'No context usage yet',
        message: _error != null
            ? productErrorText(_error!)
            : _hasOlder
            ? l10n.historyLoadedOnly
            : 'Send a prompt and wait for an assistant response. OpenCode will then report token usage for this session.',
        actionLabel: _olderNeedsReload
            ? l10n.historyReload
            : _olderCursor != null
            ? l10n.historyLoadOlder
            : 'Refresh',
        onAction: _loading
            ? null
            : _olderCursor != null
            ? () => _load(older: true)
            : _load,
      );
    }

    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        key: const ValueKey('session-context-list'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 32),
        children: [
          if (_error != null)
            _InlineContextError(
              error: _error!,
              onRetry: () => _load(older: _failedOlder),
            ),
          if (_hasOlder)
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(l10n.historyLoadedOnly),
                  TextButton(
                    onPressed: _loading ? null : () => _load(older: true),
                    child: Text(
                      _olderNeedsReload
                          ? l10n.historyReload
                          : l10n.historyLoadOlder,
                    ),
                  ),
                ],
              ),
            ),
          _ContextHero(metrics: metrics),
          const SectionLabel('Current model request'),
          _MetricGrid(metrics: metrics),
          if (metrics.breakdown.isNotEmpty) ...[
            const SectionLabel('Estimated input makeup'),
            _ContextBreakdown(segments: metrics.breakdown),
          ],
          SectionLabel(_hasOlder ? l10n.historyLoadedTotals : 'Session totals'),
          _SessionTotals(metrics: metrics, partial: _hasOlder),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 20, 16, 0),
            child: Text(
              'Usage comes from the latest completed assistant message. '
              'The makeup is an estimate from visible prompt, response, and tool text; Other includes system instructions, tool definitions, and provider overhead.',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: AppTheme.mutedOf(Theme.of(context)),
                height: 1.45,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ContextHero extends StatelessWidget {
  final SessionContextMetrics metrics;

  const _ContextHero({required this.metrics});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final usage = metrics.usage;
    final percent = usage == null ? null : usage * 100;
    final progress = usage?.clamp(0.0, 1.0) ?? 0.0;
    final model = metrics.model;
    final info = metrics.currentMessage!;
    final providerID = info.providerID ?? '';
    final modelID = info.modelID ?? '';
    final wireLabel = modelID.isEmpty
        ? 'Model unavailable'
        : providerID.isEmpty
        ? modelID
        : presentedModelLabel(providerID, modelID);
    final modelLabel = model?.name.trim().isNotEmpty == true
        ? model!.name
        : wireLabel;

    final gauge = Semantics(
      label: percent == null
          ? 'Context limit unavailable'
          : '${percent.toStringAsFixed(1)} percent context used',
      child: SizedBox.square(
        dimension: 86,
        child: Stack(
          alignment: Alignment.center,
          children: [
            SizedBox.square(
              dimension: 72,
              child: CircularProgressIndicator(
                key: const ValueKey('session-context-gauge'),
                value: progress,
                strokeWidth: 6,
                backgroundColor: theme.colorScheme.surfaceContainerHighest,
                strokeCap: StrokeCap.round,
              ),
            ),
            Text(
              percent == null ? '—' : '${percent.toStringAsFixed(0)}%',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    );
    final details = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(modelLabel, style: theme.textTheme.titleMedium),
        if (modelLabel != wireLabel) ...[
          const SizedBox(height: 2),
          Text(
            wireLabel,
            style: theme.textTheme.bodySmall?.copyWith(
              color: AppTheme.mutedOf(theme),
            ),
          ),
        ],
        const SizedBox(height: 8),
        Text(
          metrics.contextLimit > 0
              ? '${_formatNumber(metrics.contextTokens)} of ${_formatNumber(metrics.contextLimit)} tokens'
              : '${_formatNumber(metrics.contextTokens)} tokens · limit unavailable',
          key: const ValueKey('session-context-token-summary'),
          style: theme.textTheme.bodyMedium?.copyWith(
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Latest assistant request, including cache activity',
          style: theme.textTheme.bodySmall?.copyWith(
            color: AppTheme.mutedOf(theme),
          ),
        ),
      ],
    );

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 18, 16, 20),
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: AppTheme.hairline(theme))),
      ),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final stacked =
              constraints.maxWidth < 360 ||
              MediaQuery.textScalerOf(context).scale(1) >= 1.5;
          if (stacked) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [gauge, const SizedBox(height: 12), details],
            );
          }
          return Row(
            children: [
              gauge,
              const SizedBox(width: 18),
              Expanded(child: details),
            ],
          );
        },
      ),
    );
  }
}

class _MetricGrid extends StatelessWidget {
  final SessionContextMetrics metrics;

  const _MetricGrid({required this.metrics});

  @override
  Widget build(BuildContext context) {
    final tokens = metrics.tokens;
    final items = <({String label, String value})>[
      (label: 'Input', value: _formatNumber(tokens.input)),
      (label: 'Output', value: _formatNumber(tokens.output)),
      (label: 'Reasoning', value: _formatNumber(tokens.reasoning)),
      (label: 'Cache read', value: _formatNumber(tokens.cacheRead)),
      (label: 'Cache write', value: _formatNumber(tokens.cacheWrite)),
      (
        label: 'Context limit',
        value: metrics.contextLimit > 0
            ? _formatNumber(metrics.contextLimit)
            : 'Unavailable',
      ),
    ];
    return LayoutBuilder(
      builder: (context, constraints) {
        final columns =
            constraints.maxWidth >= 430 &&
                MediaQuery.textScalerOf(context).scale(1) < 1.5
            ? 2
            : 1;
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Wrap(
            children: [
              for (var index = 0; index < items.length; index++)
                SizedBox(
                  width: columns == 1
                      ? constraints.maxWidth - 32
                      : (constraints.maxWidth - 32) / 2,
                  child: _MetricRow(
                    label: items[index].label,
                    value: items[index].value,
                  ),
                ),
            ],
          ),
        );
      },
    );
  }
}

class _MetricRow extends StatelessWidget {
  final String label;
  final String value;

  const _MetricRow({super.key, required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10),
      decoration: BoxDecoration(
        border: Border(bottom: BorderSide(color: AppTheme.hairline(theme))),
      ),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: theme.textTheme.bodySmall?.copyWith(
                color: AppTheme.mutedOf(theme),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Text(
            value,
            style: theme.textTheme.bodyMedium?.copyWith(
              fontFeatures: const [FontFeature.tabularFigures()],
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}

class _ContextBreakdown extends StatelessWidget {
  final List<SessionContextBreakdownSegment> segments;

  const _ContextBreakdown({required this.segments});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final base = theme.colorScheme.primary;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: SizedBox(
              key: const ValueKey('session-context-breakdown'),
              height: 8,
              child: Row(
                children: [
                  for (var index = 0; index < segments.length; index++)
                    Expanded(
                      flex: math.max(1, (segments[index].percent * 10).round()),
                      child: ColoredBox(
                        color: base.withValues(
                          alpha: .28 + (index * .16).clamp(0, .58),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 10),
          for (var index = 0; index < segments.length; index++)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: Row(
                children: [
                  Container(
                    width: 9,
                    height: 9,
                    decoration: BoxDecoration(
                      color: base.withValues(
                        alpha: .28 + (index * .16).clamp(0, .58),
                      ),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(child: Text(_breakdownLabel(segments[index].kind))),
                  Text(
                    '${segments[index].percent.toStringAsFixed(1)}% · ${_formatNumber(segments[index].tokens)}',
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: AppTheme.mutedOf(theme),
                      fontFeatures: const [FontFeature.tabularFigures()],
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _SessionTotals extends StatelessWidget {
  final SessionContextMetrics metrics;
  final bool partial;

  const _SessionTotals({required this.metrics, this.partial = false});

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        children: [
          _MetricRow(
            label: partial ? l10n.historyLoadedMessages : 'Messages',
            value: _formatNumber(
              metrics.userMessages + metrics.assistantMessages,
            ),
          ),
          _MetricRow(
            label: 'User / assistant',
            value:
                '${_formatNumber(metrics.userMessages)} / ${_formatNumber(metrics.assistantMessages)}',
          ),
          _MetricRow(
            key: const ValueKey('session-context-cost'),
            label: metrics.serverCost != null
                ? 'Accumulated cost · reported by server'
                : partial
                ? l10n.historyLoadedCost
                : 'Accumulated cost',
            value: '\$${metrics.totalCost.toStringAsFixed(4)}',
          ),
          if (metrics.serverTokens case final tokens?)
            _MetricRow(
              key: const ValueKey('session-context-server-tokens'),
              label: 'Session tokens · reported by server',
              value: _formatNumber(tokens.total),
            ),
          if (metrics.reportedByServer)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                l10n.historyServerTotalsNote,
                key: const ValueKey('session-context-reported-by-server'),
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: AppTheme.mutedOf(Theme.of(context)),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _InlineContextError extends StatelessWidget {
  final Object error;
  final Future<void> Function() onRetry;

  const _InlineContextError({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      key: const ValueKey('session-context-inline-error'),
      padding: const EdgeInsets.fromLTRB(16, 10, 8, 10),
      color: theme.colorScheme.errorContainer.withValues(alpha: .38),
      child: Row(
        children: [
          Icon(
            Icons.sync_problem_rounded,
            size: 18,
            color: theme.colorScheme.error,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'Could not refresh: ${productErrorText(error)}',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall,
            ),
          ),
          TextButton(onPressed: onRetry, child: const Text('Try again')),
        ],
      ),
    );
  }
}

String _breakdownLabel(SessionContextBreakdownKind kind) => switch (kind) {
  SessionContextBreakdownKind.user => 'User prompts',
  SessionContextBreakdownKind.assistant => 'Assistant text',
  SessionContextBreakdownKind.tool => 'Tool calls and results',
  SessionContextBreakdownKind.other => 'Other context',
};

String _formatNumber(int value) {
  final digits = value.abs().toString();
  final buffer = StringBuffer();
  for (var index = 0; index < digits.length; index++) {
    if (index > 0 && (digits.length - index) % 3 == 0) buffer.write(',');
    buffer.write(digits[index]);
  }
  return value < 0 ? '-$buffer' : buffer.toString();
}
