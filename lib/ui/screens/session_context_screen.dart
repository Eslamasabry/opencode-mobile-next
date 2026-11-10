import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../../domain/models.dart';
import '../../domain/product_repository.dart';
import '../../domain/provider_presentation.dart';
import '../../domain/server_gateway.dart' show StreamStatus;
import '../../domain/session_history.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import '../widgets/product_states.dart'
    show productErrorDetails, productErrorText;
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

/// From this share of the model's limit up, the page says the conversation
/// is near its limit and offers what to do (map: session-context).
const _nearLimit = .8;

/// Under this share, the verdict adds "plenty left".
const _plentyLeft = .5;

/// How full a conversation's context is (map: session-context): a plain
/// verdict first, the model's bar, what to do near the limit, what fills
/// the input, the conversation's totals, and the raw request figures under
/// Details.
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
  bool _compactStarted = false;
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
      if (completed) _compactStarted = false;
      unawaited(_load());
    } else {
      setState(() {});
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
        throw ProductException(
          _sharedCopy(context).e7SharedOpenCodeIsReconnectingTryAgain,
        );
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
        throw ProductException(_sharedCopy(context).historyCursorExpired);
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

  /// Why compacting cannot start now, or null when it can.
  String? _compactBlocked(AppLocalizations l10n) {
    final conn = widget.controller;
    if (conn.status != StreamStatus.connected) return l10n.workDisconnected;
    if (conn.busySessions.contains(widget.sessionID)) {
      return l10n.sessionContextCompactBusy;
    }
    if (conn.modelForSession(widget.sessionID) == null &&
        !conn.serverOwnsSessionSelection) {
      return l10n.chatUiSelectAModelBeforeCompactingThisSession;
    }
    return null;
  }

  bool get _canOfferCompact =>
      !widget.controller.isIsolated &&
      widget.controller.capabilities.sessionCompact;

  /// Asked first (it changes what the model sees from now on); the request
  /// runs inside the question, so a failure keeps it open with Try again.
  Future<void> _compact() async {
    final l10n = _sharedCopy(context);
    final conn = widget.controller;
    final started = await showKitConfirm(
      context,
      title: l10n.sessionContextCompactTitle,
      body: l10n.sessionContextCompactBody,
      confirmLabel: l10n.sessionContextCompactConfirm,
      icon: AppIconography.collapse,
      confirmKey: const Key('session-context-compact-confirm'),
      consequenceItems: [
        KitConsequence(
          l10n.sessionContextCompactKept,
          mark: KitConsequenceMark.kept,
        ),
      ],
      action: () async {
        final repository = await conn.prepareActionRepository();
        if (repository == null || !_sameLocation) {
          throw ProductException(l10n.e7SharedOpenCodeIsReconnectingTryAgain);
        }
        final model = conn.modelForSession(widget.sessionID);
        await repository.compactSession(
          widget.sessionID,
          providerID: model?.providerID ?? '',
          modelID: model?.modelID ?? '',
        );
      },
    );
    if (started && mounted) setState(() => _compactStarted = true);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _sharedCopy(context);
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
    final blocked = _compactBlocked(l10n);
    final canRefresh = !_loading && _sameLocation;
    // Near the limit the page's notice carries "Compact this conversation";
    // the bar's menu does not offer it a second time (owner rule
    // 2026-09-27, nothing twice on one page).
    final noticeOffersCompact =
        _nearLimitNoticeShown(metrics) && _canOfferCompact;
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.e7SharedSessionContext,
        actions: [
          KitAction(
            key: const Key('session-context-refresh'),
            label: l10n.e7SharedRefreshContext,
            icon: AppIconography.retry,
            onPressed: canRefresh ? _load : null,
            disabledReason: canRefresh
                ? null
                : _sameLocation
                ? l10n.sessionContextLoading
                : l10n.activeContextChanged,
          ),
        ],
        menu: [
          if (_canOfferCompact && _sameLocation && !noticeOffersCompact)
            KitMenuItem(
              key: const Key('session-context-compact-menu'),
              label: l10n.sessionContextCompactAction,
              icon: AppIconography.collapse,
              enabled: blocked == null && !_compactStarted,
              disabledReason: _compactStarted
                  ? l10n.sessionContextCompactStarted
                  : blocked,
              onSelected: () => unawaited(_compact()),
            ),
        ],
      ),
      width: KitScreenWidth.reading,
      loading: _loading && _messages.isNotEmpty,
      loadingLabel: l10n.sessionContextLoading,
      body: _buildBody(l10n, metrics, blocked),
    );
  }

  /// Whether the body shows the near-the-limit notice (with its Compact).
  bool _nearLimitNoticeShown(SessionContextMetrics metrics) =>
      _sameLocation &&
      !_compactStarted &&
      !(_loading && _messages.isEmpty) &&
      !(_error != null && _messages.isEmpty) &&
      metrics.currentMessage != null &&
      (metrics.usage ?? 0) >= _nearLimit;

  Widget _buildBody(
    AppLocalizations l10n,
    SessionContextMetrics metrics,
    String? compactBlocked,
  ) {
    if (!_sameLocation) {
      return KitStateView(
        icon: AppIconography.swap,
        title: l10n.sessionContextMovedTitle,
        body: l10n.activeContextChanged,
      );
    }
    if (_loading && _messages.isEmpty) {
      return ListView(
        padding: KitScreen.padding(context),
        children: const [KitSkeletonRows(count: 6)],
      );
    }
    if (_error != null && _messages.isEmpty) {
      return KitStateView.error(
        title: l10n.sessionContextLoadFailed,
        body: productErrorText(_error!),
        error: _error,
        details: productErrorDetails(_error!),
        retry: KitAction(label: l10n.isolatedTaskRetryOpen, onPressed: _load),
      );
    }
    if (metrics.currentMessage == null) {
      final facts = _sessionFacts(l10n, withCost: true);
      final empty = KitStateView(
        icon: AppIconography.usageRing,
        title: l10n.e7SharedNoContextUsageYet,
        body: _error != null
            ? productErrorText(_error!)
            : _hasOlder
            ? l10n.historyLoadedOnly
            : l10n.e7SharedSendAPromptAndWaitForAn,
        primary: KitAction(
          label: _olderNeedsReload
              ? l10n.historyReload
              : _olderCursor != null
              ? l10n.historyLoadOlder
              : l10n.globalSessionsRefresh,
          onPressed: _loading
              ? null
              : _olderCursor != null
              ? () => _load(older: true)
              : _load,
        ),
      );
      if (facts.isEmpty) return empty;
      // Nothing to measure yet, but where the conversation lives still
      // shows, folded under the state.
      return ListView(
        key: const ValueKey('session-context-list'),
        padding: KitScreen.padding(context),
        children: [
          empty,
          SizedBox(height: KitTokens.of(context).space4),
          KitDetailsFold(
            key: const ValueKey('session-context-details'),
            values: facts,
          ),
        ],
      );
    }

    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space4);
    final usage = metrics.usage;
    final near = usage != null && usage >= _nearLimit;
    final activeContext =
        widget.controller.repository is ActiveContextGateway &&
        (widget.controller.repository as ActiveContextGateway)
            .activeContextSupported;
    final model = _modelLabels(l10n, metrics);

    return KitRefresh(
      onRefresh: _load,
      child: ListView(
        key: const ValueKey('session-context-list'),
        physics: const AlwaysScrollableScrollPhysics(),
        padding: KitScreen.padding(context),
        children: [
          if (_error != null) ...[
            KitNotice.error(
              key: const ValueKey('session-context-inline-error'),
              message: l10n.sessionContextRefreshFailed,
              error: _error,
              details: productErrorDetails(_error!),
              retry: KitAction(
                label: l10n.isolatedTaskRetryOpen,
                onPressed: () => _load(older: _failedOlder),
              ),
            ),
            gap,
          ],
          if (_hasOlder) ...[
            KitNotice(
              icon: AppIconography.history,
              message: l10n.historyLoadedOnly,
              actions: [
                KitAction(
                  label: _olderNeedsReload
                      ? l10n.historyReload
                      : l10n.historyLoadOlder,
                  onPressed: _loading ? null : () => _load(older: true),
                ),
              ],
            ),
            gap,
          ],
          // The verdict first, in plain words (map infoMissing).
          KitText(
            _verdict(l10n, usage),
            key: const ValueKey('session-context-verdict'),
            role: KitTextRole.title,
          ),
          SizedBox(height: tokens.space3),
          KitRowGroup(
            margin: EdgeInsets.zero,
            children: [
              if (usage == null)
                // Without a limit there is no fraction to draw, but the
                // amount is known: a bar with no fill would read as still
                // loading (KitProgressRow `value: null`) and hide it.
                KitRow(
                  key: const ValueKey('session-context-gauge'),
                  title: model.name,
                  supportingKey: const ValueKey(
                    'session-context-token-summary',
                  ),
                  supporting: TextSpan(
                    text: l10n.e7SharedDetail386(
                      _formatNumber(metrics.contextTokens),
                    ),
                  ),
                )
              else
                KitProgressRow(
                  key: const ValueKey('session-context-gauge'),
                  title: model.name,
                  value: usage,
                  // Near the limit, the verdict and the notice below say so;
                  // an explicit tone keeps the bar's own "Near limit" word
                  // off (the same colours as the automatic bar).
                  tone: near
                      ? usage >= 1
                            ? AppStatusTone.failure
                            : AppStatusTone.progress
                      : null,
                  valueKey: const ValueKey('session-context-token-summary'),
                  valueLabel: l10n.e7SharedDetail385(
                    _formatNumber(metrics.contextTokens),
                    _formatNumber(metrics.contextLimit),
                  ),
                ),
              if (activeContext)
                KitRow(
                  key: const ValueKey('open-active-context'),
                  leading: const KitRowIcon(AppIconography.text),
                  title: l10n.activeContextTitle,
                  supporting: TextSpan(text: l10n.activeContextSubtitle),
                  trailing: const KitChevron(),
                  onTap: () => pushKitPage<void>(
                    context,
                    (_) => ActiveContextScreen(
                      controller: widget.controller,
                      sessionID: widget.sessionID,
                    ),
                  ),
                ),
            ],
          ),
          // Near the limit: what it means and what to do (map
          // statesMissing, actionsMissing "compact now").
          if (_compactStarted) ...[
            gap,
            KitNotice(
              key: const ValueKey('session-context-compact-started'),
              tone: AppStatusTone.ok,
              message: l10n.sessionContextCompactStarted,
            ),
          ] else if (near) ...[
            gap,
            KitNotice(
              key: const ValueKey('session-context-near-limit'),
              icon: AppIconography.warning,
              title: l10n.sessionContextNearLimitTitle,
              message: l10n.sessionContextNearLimitBody,
              actions: [
                if (_canOfferCompact)
                  KitAction(
                    key: const Key('session-context-compact'),
                    label: l10n.sessionContextCompactAction,
                    icon: AppIconography.collapse,
                    onPressed: compactBlocked == null ? _compact : null,
                    disabledReason: compactBlocked,
                  ),
              ],
            ),
          ],
          if (metrics.breakdown.isNotEmpty) ...[
            gap,
            KitRowGroup(
              label: l10n.e7SharedEstimatedInputMakeup,
              margin: EdgeInsets.zero,
              children: [
                KitProgressRow.segments(
                  key: const ValueKey('session-context-breakdown'),
                  title: l10n.sessionContextMakeupTitle,
                  valueLabel: l10n.sessionContextTokens(
                    _formatNumber(metrics.tokens.input),
                  ),
                  segments: [
                    for (final segment in metrics.breakdown)
                      KitProgressSegment(
                        label: _breakdownLabel(l10n, segment.kind),
                        value: segment.percent / 100,
                        // Short, so the legend fits at large text; the
                        // counts are in the bar's own label.
                        valueLabel: l10n.sessionContextPercent(
                          segment.percent.round().toString(),
                        ),
                      ),
                  ],
                ),
              ],
            ),
          ],
          gap,
          _SessionTotals(metrics: metrics, partial: _hasOlder),
          gap,
          // The raw figures of the latest request, the model's id and how
          // the numbers are made: one fold, last and closed (KIT-33).
          KitDetailsFold(
            key: const ValueKey('session-context-details'),
            values: [
              ..._sessionFacts(l10n),
              if (model.wire != model.name)
                KitTechnicalValue(l10n.sessionContextModelId, model.wire),
              KitTechnicalValue(
                l10n.usageInput,
                _formatNumber(metrics.tokens.input),
                copyable: false,
              ),
              KitTechnicalValue(
                l10n.usageOutput,
                _formatNumber(metrics.tokens.output),
                copyable: false,
              ),
              KitTechnicalValue(
                l10n.transcriptFindReasoning,
                _formatNumber(metrics.tokens.reasoning),
                copyable: false,
              ),
              KitTechnicalValue(
                l10n.usageCacheRead,
                _formatNumber(metrics.tokens.cacheRead),
                copyable: false,
              ),
              KitTechnicalValue(
                l10n.usageCacheWrite,
                _formatNumber(metrics.tokens.cacheWrite),
                copyable: false,
              ),
              KitTechnicalValue(
                l10n.e7SharedContextLimit,
                metrics.contextLimit > 0
                    ? _formatNumber(metrics.contextLimit)
                    : l10n.e7SharedUnavailable,
                copyable: false,
              ),
            ],
            notes: [
              l10n.e7SharedLatestAssistantRequestIncludingCacheActivity,
              l10n.e7SharedUsageComesFromTheLatestCompletedAssistant,
            ],
          ),
        ],
      ),
    );
  }

  /// Where the conversation lives and its public link, copyable, and how
  /// much it changed: the facts the Work row's old details sheet held (map
  /// `workspace-session-details-sheet`, merged here in slice-P3.11a).
  /// [withCost] adds the server's cost while nothing is measured yet; once
  /// usage loads, the totals row carries it (said once).
  List<KitTechnicalValue> _sessionFacts(
    AppLocalizations l10n, {
    bool withCost = false,
  }) {
    final session = widget.controller.sessionsById[widget.sessionID];
    final directory = session?.directory;
    final share = session?.shareUrl;
    final cost = session?.cost;
    final summary = session?.summary;
    return [
      if (withCost && cost != null && cost >= 0.005)
        KitTechnicalValue(
          l10n.e7SharedAccumulatedCostReportedByServer,
          '\$${cost.toStringAsFixed(2)}',
          key: const ValueKey('session-context-server-cost'),
          copyable: false,
        ),
      if (summary != null && (summary.additions > 0 || summary.deletions > 0))
        KitTechnicalValue(
          l10n.chatUiChanges,
          [
            '+${summary.additions} −${summary.deletions}',
            if (summary.files > 0) l10n.e7WorkspaceFileCount(summary.files),
          ].join(' · '),
          key: const ValueKey('session-context-diff'),
          copyable: false,
        ),
      if (directory != null && directory.isNotEmpty)
        KitTechnicalValue(
          l10n.workspaceContextFolder,
          directory,
          key: const ValueKey('session-context-folder'),
        ),
      if (share != null && share.isNotEmpty)
        KitTechnicalValue(
          l10n.workspaceSessionSharedLink,
          share,
          key: const ValueKey('session-context-share'),
        ),
    ];
  }

  String _verdict(AppLocalizations l10n, double? usage) {
    if (usage == null) return l10n.e7SharedContextLimitUnavailable;
    final percent = (usage * 100).round().toString();
    if (usage >= 1) return l10n.sessionContextVerdictFull(percent);
    if (usage >= _nearLimit) return l10n.sessionContextVerdictNear(percent);
    if (usage < _plentyLeft) return l10n.sessionContextVerdictPlenty(percent);
    return l10n.sessionContextVerdictUsed(percent);
  }
}

/// The model's display name, and the id it goes by on the wire.
({String name, String wire}) _modelLabels(
  AppLocalizations l10n,
  SessionContextMetrics metrics,
) {
  final info = metrics.currentMessage!;
  final providerID = info.providerID ?? '';
  final modelID = info.modelID ?? '';
  final wire = modelID.isEmpty
      ? l10n.e7SharedModelUnavailable
      : providerID.isEmpty
      ? modelID
      : presentedModelLabel(providerID, modelID);
  final name = metrics.model?.name.trim().isNotEmpty == true
      ? metrics.model!.name
      : wire;
  return (name: name, wire: wire);
}

class _SessionTotals extends StatelessWidget {
  final SessionContextMetrics metrics;
  final bool partial;

  const _SessionTotals({required this.metrics, this.partial = false});

  @override
  Widget build(BuildContext context) {
    final l10n = _sharedCopy(context);
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitRowGroup(
          label: partial
              ? l10n.historyLoadedTotals
              : l10n.e7SharedSessionTotals,
          leadingIcons: false,
          margin: EdgeInsets.zero,
          children: [
            // One row for the count and who wrote them: "2 (1 yours,
            // 1 agent)", not the same total twice.
            KitRow(
              key: const ValueKey('session-context-messages'),
              title: partial
                  ? l10n.historyLoadedMessages
                  : l10n.e7SharedMessages,
              trailing: KitRowValue(
                l10n.sessionContextMessagesSplit(
                  _formatNumber(
                    metrics.userMessages + metrics.assistantMessages,
                  ),
                  _formatNumber(metrics.userMessages),
                  _formatNumber(metrics.assistantMessages),
                ),
                chevron: false,
              ),
            ),
            KitRow(
              key: const ValueKey('session-context-cost'),
              title: metrics.serverCost != null
                  ? l10n.e7SharedAccumulatedCostReportedByServer
                  : partial
                  ? l10n.historyLoadedCost
                  : l10n.e7SharedAccumulatedCost,
              titleMaxLines: 2,
              trailing: KitRowValue(
                '\$${metrics.totalCost.toStringAsFixed(4)}',
                chevron: false,
              ),
            ),
            if (metrics.serverTokens case final serverTokens?)
              KitRow(
                key: const ValueKey('session-context-server-tokens'),
                title: l10n.e7SharedSessionTokensReportedByServer,
                titleMaxLines: 2,
                trailing: KitRowValue(
                  _formatNumber(serverTokens.total),
                  chevron: false,
                ),
              ),
          ],
        ),
        if (metrics.reportedByServer) ...[
          SizedBox(height: tokens.space2),
          KitText(
            l10n.historyServerTotalsNote,
            key: const ValueKey('session-context-reported-by-server'),
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
        ],
      ],
    );
  }
}

String _breakdownLabel(
  AppLocalizations l10n,
  SessionContextBreakdownKind kind,
) => switch (kind) {
  SessionContextBreakdownKind.user => l10n.e7SharedUserPrompts,
  SessionContextBreakdownKind.assistant => l10n.e7SharedAssistantText,
  SessionContextBreakdownKind.tool => l10n.e7SharedToolCallsAndResults,
  SessionContextBreakdownKind.other => l10n.e7SharedOtherContext,
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

AppLocalizations _sharedCopy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));
