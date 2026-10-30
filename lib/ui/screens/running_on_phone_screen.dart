/// This phone › Running on this phone (in-app Linux): what the app runs on
/// the phone, grouped under what started it, with what heats the phone
/// marked and a one-tap Stop for what is stuck or was left behind.
///
/// Groups, in this order: Left behind (programs a dead Linux left running:
/// a Stop on each and one "Stop all left behind"), OpenCode, each Claude
/// Code conversation (titled with the conversation when the app knows it),
/// the agent helper, AI Team, terminals, setup, helpers, this app, other.
/// Inside a group the parent → child relation is the indentation.
///
/// Each row: a plain name ("Flutter test", "Command"; the command line is
/// under Details), how hard it works in a word and a % of one core, memory
/// and how long it has run. "Heavy" needs readings two minutes apart, all
/// over 80%; "May be stuck" is about 100% for over 10 minutes with no
/// conversation above it. The page reads every 5 seconds while open, and
/// never otherwise. From 28 processes it warns that Android may stop the
/// servers (it stops an app's oldest children above 32).
///
/// The root of a server, the agent helper, a terminal or a setup step is
/// never offered a Stop (the phone refuses it; This phone stops servers).
/// Every stop asks first, names what it stops, sends exactly those process
/// ids, and says plainly what did not stop.
///
/// Kit parts only: [KitScreen], [KitSkeletonRows], [KitStateView],
/// [KitRowGroup], [KitAnimatedRows], [KitRow], [KitNotice], [showKitSheet],
/// [showKitConfirm].
library;

import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../builtin/phone_process_watch.dart';
import '../../builtin/phone_processes.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import '../widgets/phone_server_card.dart' show formatPhoneStorage;
import '../widgets/product_states.dart' show productErrorDetails;
import '../widgets/running_on_phone_text.dart';
import 'termux_processes_screen.dart' show formatTermuxDuration;

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

class RunningOnPhoneScreen extends ConsumerStatefulWidget {
  const RunningOnPhoneScreen({
    super.key,
    this.interval = const Duration(seconds: 5),
    this.conversationTitle,
  });

  /// How often the page reads again while it is open.
  final Duration interval;

  /// The conversation title for an agent session; the app asks the
  /// connection (null when it cannot map it). Tests pass their own.
  final String? Function(String session)? conversationTitle;

  @override
  ConsumerState<RunningOnPhoneScreen> createState() =>
      _RunningOnPhoneScreenState();
}

/// What the last stop did, said on the page.
class _Outcome {
  const _Outcome(this.line, this.reasons, {required this.complete});

  final String line;
  final List<String> reasons;
  final bool complete;
}

class _Failure {
  const _Failure(this.message, this.details);

  final String message;
  final String? details;
}

class _RunningOnPhoneScreenState extends ConsumerState<RunningOnPhoneScreen> {
  PhoneProcessReading? _reading;
  _Failure? _failure;

  /// A stop that did not reach the phone; kept across readings until the
  /// next stop.
  _Failure? _stopFailure;
  bool _loading = true;
  bool _busy = false;
  _Outcome? _outcome;
  Timer? _timer;

  PhoneProcessWatch get _watch => ref.read(phoneProcessWatchProvider);

  @override
  void initState() {
    super.initState();
    _reading = _watch.last;
    unawaited(_refresh());
    _timer = Timer.periodic(widget.interval, (_) {
      // Only while the page is open and the app is in front.
      final state = WidgetsBinding.instance.lifecycleState;
      final inFront = state == null || state == AppLifecycleState.resumed;
      if (mounted && !_busy && inFront) unawaited(_refresh());
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final reading = await _watch.sample();
      if (!mounted) return;
      setState(() {
        _reading = reading;
        _failure = null;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      final l10n = _copy(context);
      setState(() {
        _failure = _Failure(
          _reading == null
              ? l10n.runningPhoneLoadFailedBody
              : l10n.runningPhoneRefreshFailed,
          productErrorDetails(error),
        );
        _loading = false;
      });
    }
  }

  String? _title(String? session) {
    if (session == null) return null;
    try {
      final lookup = widget.conversationTitle;
      if (lookup != null) return lookup(session);
      return ref.read(connProvider).agentChatTitleForRuntimeSession(session);
    } catch (_) {
      return null;
    }
  }

  // --- Stopping --------------------------------------------------------------

  Future<void> _stopOne(PhoneProcessNode node) async {
    final l10n = _copy(context);
    final name = runningProcessName(l10n, node.process);
    final confirmed = await showKitConfirm(
      context,
      title: l10n.runningPhoneStopTitle(name),
      body: l10n.runningPhoneStopBody,
      confirmLabel: l10n.runningPhoneStopNamed(name),
      icon: AppIcons.stop,
      kind: KitConfirmKind.stop,
      consequenceItems: [KitConsequence(l10n.termuxProcsNoRestart)],
      sheetKey: const Key('running-confirm'),
      confirmKey: const Key('running-confirm-stop'),
    );
    if (!confirmed || !mounted) return;
    await _runStop([node]);
  }

  /// One question for everything left behind, naming each; only the ones
  /// it named are sent.
  Future<void> _stopLeftBehind() async {
    final targets = _reading?.leftBehind ?? const <PhoneProcessNode>[];
    if (targets.isEmpty) return;
    final l10n = _copy(context);
    final count = targets.length;
    final confirmed = await showKitConfirm(
      context,
      title: l10n.runningPhoneStopAllTitle,
      body: l10n.runningPhoneStopAllBody(
        targets.map((n) => runningProcessName(l10n, n.process)).join(', '),
      ),
      confirmLabel: l10n.runningPhoneStopAllLeftBehind(count),
      icon: AppIcons.stop,
      kind: KitConfirmKind.stop,
      consequenceItems: [KitConsequence(l10n.termuxProcsNoRestart)],
      sheetKey: const Key('running-confirm'),
      confirmKey: const Key('running-confirm-stop'),
    );
    if (!confirmed || !mounted) return;
    await _runStop(targets);
  }

  Future<void> _runStop(List<PhoneProcessNode> nodes) async {
    final l10n = _copy(context);
    final names = {
      for (final n in nodes) n.pid: runningProcessName(l10n, n.process),
    };
    setState(() {
      _busy = true;
      _outcome = null;
      _stopFailure = null;
    });
    try {
      final result = await _watch.stop([for (final n in nodes) n.pid]);
      if (!mounted) return;
      var ended = 0;
      final reasons = <String>[];
      result.refused.forEach((pid, reason) {
        final name = names[pid] ?? '$pid';
        switch (reason) {
          case PhoneProcessStopRefusal.notFound ||
              PhoneProcessStopRefusal.invalid:
            ended++;
          case PhoneProcessStopRefusal.app:
            reasons.add(l10n.runningPhoneRefusedApp(name));
          case PhoneProcessStopRefusal.serverRoot:
            reasons.add(l10n.runningPhoneRefusedServer(name));
          case PhoneProcessStopRefusal.stillRunning:
            reasons.add(l10n.runningPhoneRefusedStillRunning(name));
        }
      });
      final line = [
        l10n.runningPhoneStopped(result.stopped.length),
        if (ended > 0) l10n.runningPhoneAlreadyEnded(ended),
      ].join(' · ');
      setState(
        () => _outcome = _Outcome(line, reasons, complete: reasons.isEmpty),
      );
    } catch (error) {
      if (mounted) {
        setState(
          () => _stopFailure = _Failure(
            l10n.runningPhoneStopFailed,
            productErrorDetails(error),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
    if (mounted) await _refresh();
  }

  // --- Details ---------------------------------------------------------------

  Future<void> _showDetails(PhoneProcessNode node) async {
    final reading = _reading;
    if (reading == null) return;
    final l10n = _copy(context);
    final p = node.process;
    final name = runningProcessName(l10n, p);
    final kind = runningGroupOf(reading, node);
    BuildContext? inside;
    await showKitSheet<void>(
      context,
      title: name,
      subtitle: runningGroupLabel(l10n, kind, title: _title(node.session)),
      icon: runningGroupIcon(kind),
      secondary: !node.stoppable
          ? null
          : KitAction(
              key: const Key('running-details-stop'),
              label: l10n.runningPhoneStopNamed(name),
              icon: AppIcons.stop,
              destructive: true,
              onPressed: _busy
                  ? null
                  : () {
                      final sheet = inside;
                      if (sheet != null) KitSheet.close<void>(sheet);
                      unawaited(_stopOne(node));
                    },
              disabledReason: _busy ? l10n.termuxProcsStopping : null,
            ),
      tertiary: [
        KitAction.copy(
          key: const Key('running-details-copy'),
          label: l10n.termuxProcsCopyCommand,
          text: () => p.command,
        ),
      ],
      body: (sheetContext) {
        inside = sheetContext;
        return _Details(
          node: node,
          kind: kind,
          conversation: _title(node.session),
        );
      },
    );
  }

  // --- The page --------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final reading = _reading;
    final failure = _failure;
    final Widget body;
    if (reading == null && _loading) {
      body = ListView(
        key: const ValueKey('running-loading'),
        padding: KitScreen.padding(context),
        children: const [KitSkeletonRows()],
      );
    } else if (reading == null) {
      body = KitStateView.error(
        key: const ValueKey('running-load-error'),
        title: l10n.runningPhoneLoadFailedTitle,
        body: failure?.message ?? l10n.runningPhoneLoadFailedBody,
        details: failure?.details,
        retry: KitAction(label: l10n.commonRetry, onPressed: _refresh),
      );
    } else {
      body = KitRefresh(
        onRefresh: _refresh,
        child: ListView(
          key: const ValueKey('running-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: EdgeInsetsDirectional.only(
            top: KitTokens.of(context).space2,
            bottom: KitScreen.endPadding(context),
          ),
          children: [_content(context, l10n, reading)],
        ),
      );
    }
    return KitScreen(
      topBar: KitTopBar(
        title: l10n.runningPhoneTitle,
        exitKey: const ValueKey('running-back'),
      ),
      width: KitScreenWidth.reading,
      loading: _busy,
      loadingLabel: l10n.termuxProcsStopping,
      body: body,
    );
  }

  Widget _content(
    BuildContext context,
    AppLocalizations l10n,
    PhoneProcessReading reading,
  ) {
    final tokens = KitTokens.of(context);
    final rails = EdgeInsetsDirectional.only(
      start: tokens.gutter,
      end: tokens.gutter,
      bottom: tokens.space4,
    );
    final failure = _failure;
    final stopFailure = _stopFailure;
    final outcome = _outcome;
    final count = reading.snapshot.processes.length;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (count > 0)
          Padding(
            padding: rails,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                KitText(
                  l10n.runningPhoneSummary(
                    count,
                    formatPhoneStorage(reading.rssBytes),
                  ),
                  key: const Key('running-summary'),
                  role: KitTextRole.headline,
                  tabular: true,
                ),
                SizedBox(height: tokens.space1),
                KitText(
                  l10n.runningPhoneAutoRefresh,
                  role: KitTextRole.caption,
                ),
              ],
            ),
          ),
        // Near Android's limit: said in words, with what to do.
        if (reading.nearLimit)
          Padding(
            padding: rails,
            child: KitNotice(
              key: const ValueKey('running-near-limit'),
              // Heat and limits are neutral with a warning glyph: amber
              // means "needs you" only (G17).
              tone: AppStatusTone.neutral,
              icon: AppIconography.warning,
              title: l10n.runningPhoneLimitTitle,
              message: l10n.runningPhoneLimitBody(
                reading.childCount,
                PhoneProcessWatch.androidLimit,
              ),
              liveRegion: false,
            ),
          ),
        if (failure != null)
          Padding(
            padding: rails,
            child: KitNotice.error(
              message: failure.message,
              details: failure.details,
              messageKey: const Key('running-error'),
              retry: KitAction(label: l10n.commonRetry, onPressed: _refresh),
            ),
          ),
        if (stopFailure != null)
          Padding(
            padding: rails,
            child: KitNotice.error(
              message: stopFailure.message,
              details: stopFailure.details,
              messageKey: const Key('running-stop-error'),
            ),
          ),
        if (outcome != null)
          Padding(
            padding: rails,
            child: KitNotice(
              key: const ValueKey('running-outcome'),
              tone: outcome.complete ? AppStatusTone.ok : AppStatusTone.failure,
              icon: outcome.complete
                  ? AppIconography.check
                  : AppIconography.error,
              title: outcome.complete ? null : l10n.runningPhoneNotStoppedTitle,
              message: outcome.line,
              notes: outcome.reasons,
              messageKey: const Key('running-result'),
            ),
          ),
        if (count == 0)
          Padding(
            padding: rails,
            child: KitStateView(
              key: const Key('running-empty'),
              icon: AppIconography.processor,
              title: l10n.runningPhoneEmpty,
              body: l10n.runningPhoneEmptyBody,
              size: KitStateSize.inline,
              padding: EdgeInsets.zero,
            ),
          ),
        // Groups come and go gently, as do the rows inside them.
        KitAnimatedRows(
          children: [
            for (final group in reading.groups)
              KeyedSubtree(
                key: ValueKey('running-group-${group.id}'),
                child: _group(context, l10n, reading, group),
              ),
          ],
        ),
        SizedBox(height: tokens.sectionGap),
      ],
    );
  }

  Widget _group(
    BuildContext context,
    AppLocalizations l10n,
    PhoneProcessReading reading,
    PhoneProcessGroup group,
  ) {
    final leftBehind = group.kind == PhoneProcessGroupKind.leftBehind
        ? reading.leftBehind.length
        : 0;
    return KitRowGroup(
      key: ValueKey('running-panel-${group.id}'),
      label: runningGroupLabel(l10n, group.kind, title: _title(group.session)),
      labelTrailing: _headingFacts(context, l10n, group, warn: leftBehind > 0),
      children: [
        KitAnimatedRows(
          children: [
            for (final (i, node) in group.nodes.indexed)
              KeyedSubtree(
                key: ValueKey('running-node-${node.pid}'),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (i > 0)
                      KitDivider(
                        inset: group.kind == PhoneProcessGroupKind.leftBehind
                            ? KitDividerInset.gutter
                            : KitDividerInset.text,
                      ),
                    _row(context, l10n, group, node),
                  ],
                ),
              ),
          ],
        ),
        // The one bulk stop, last, while something is left behind.
        if (leftBehind > 0)
          KitRow(
            key: const Key('running-stop-left-behind'),
            title: l10n.runningPhoneStopAllLeftBehind(leftBehind),
            titleMaxLines: 2,
            supporting: TextSpan(text: l10n.runningPhoneStopAllDetail),
            supportingMaxLines: 2,
            destructive: true,
            enabled: !_busy,
            disabledReason: _busy ? l10n.termuxProcsStopping : null,
            onTap: () => unawaited(_stopLeftBehind()),
          ),
      ],
    );
  }

  /// The heading's figures; on Left behind they carry the page's one
  /// warning glyph, so the rows under it need none.
  Widget _headingFacts(
    BuildContext context,
    AppLocalizations l10n,
    PhoneProcessGroup group, {
    required bool warn,
  }) {
    final facts = KitText(
      '${runningCpuText(l10n, group.cpuPercent)} · '
      '${runningMemoryText(l10n, group.rssBytes)}',
      role: KitTextRole.caption,
      tabular: true,
    );
    if (!warn) return facts;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const KitIcon(AppIconography.warning, size: KitIconSize.small),
        SizedBox(width: KitTokens.of(context).space1),
        Flexible(child: facts),
      ],
    );
  }

  Widget _row(
    BuildContext context,
    AppLocalizations l10n,
    PhoneProcessGroup group,
    PhoneProcessNode node,
  ) {
    final tokens = KitTokens.of(context);
    final p = node.process;
    final name = runningProcessName(l10n, p);
    final inLeftBehind = group.kind == PhoneProcessGroupKind.leftBehind;
    final marks = [if (node.stuck && !inLeftBehind) l10n.runningPhoneMarkStuck];
    // The mark in words at label weight (never amber: that is "needs
    // you" only); the row's warning glyph goes with it.
    final markStyle = KitText.styleOf(context, KitTextRole.label);
    // Stop is in reach where it is the likely act: left behind, stuck,
    // heavy. Elsewhere it is in the row's menu and its details.
    final offerStop =
        node.stoppable && (node.leftBehind || node.stuck || node.heavy);
    // Said once, on the top of what was left behind: the rows under it
    // came from the same place.
    final saysFrom = inLeftBehind && node.depth == 0 && node.session != null;
    final chat = saysFrom ? _title(node.session) : null;
    final from = !saysFrom
        ? null
        : chat == null
        ? l10n.runningPhoneFromClaude
        : l10n.runningPhoneFromChat(chat);
    final depth = math.min(node.depth, 3);
    return KitRow(
      key: Key('running-proc-${p.pid}'),
      // Left behind rows carry no glyph: the heading warns once, the
      // indent shows the tree.
      leading: inLeftBehind
          ? null
          : KitRow.icon(
              context,
              node.stuck
                  ? AppIconography.warning
                  : node.heavy
                  ? AppIconography.speed
                  : runningGroupIcon(group.kind),
            ),
      title: name,
      titleKey: Key('running-proc-title-${p.pid}'),
      supporting: TextSpan(
        children: [
          for (final mark in marks)
            TextSpan(text: '$mark · ', style: markStyle),
          TextSpan(text: runningFacts(l10n, p)),
        ],
      ),
      supportingKey: Key('running-proc-line-${p.pid}'),
      supportingMaxLines: 2,
      below: from == null
          ? null
          : KitText(
              from,
              key: Key('running-proc-from-${p.pid}'),
              role: KitTextRole.secondary,
            ),
      // The tree: each level a step in from its parent.
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.space4 + depth * tokens.space5,
        tokens.space2,
        tokens.space4,
        tokens.space2,
      ),
      // Stop ends at the row's end edge like any trailing action; the
      // chip column would leave a gap after it in a tree of mixed rows.
      trailing: offerStop
          ? Semantics(
              container: true,
              button: true,
              label: l10n.runningPhoneStopNamed(name),
              onTap: () => unawaited(_stopOne(node)),
              excludeSemantics: true,
              child: KitChip.action(
                key: Key('running-proc-stop-${p.pid}'),
                label: l10n.runningPhoneStop,
                onPressed: () => unawaited(_stopOne(node)),
              ),
            )
          : const KitChevron(),
      onTap: () => unawaited(_showDetails(node)),
      menu: [
        KitMenuItem(
          label: l10n.kitDetails,
          icon: AppIconography.info,
          onSelected: () => unawaited(_showDetails(node)),
        ),
        KitMenuItem.copy(
          label: l10n.termuxProcsCopyCommand,
          text: () => p.command,
        ),
        if (node.stoppable)
          KitMenuItem(
            key: Key('running-proc-menu-stop-${p.pid}'),
            label: l10n.runningPhoneStopNamed(name),
            icon: AppIcons.stop,
            destructive: true,
            enabled: !_busy,
            disabledReason: _busy ? l10n.termuxProcsStopping : null,
            onSelected: () => unawaited(_stopOne(node)),
          ),
      ],
    );
  }
}

/// The details sheet's body: what it is in words, why it is marked, what
/// it is doing, then the technical values once under one Details fold.
class _Details extends StatelessWidget {
  const _Details({required this.node, required this.kind, this.conversation});

  final PhoneProcessNode node;
  final PhoneProcessGroupKind kind;
  final String? conversation;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tokens = KitTokens.of(context);
    final p = node.process;
    final heavyFor = node.heavyFor;
    final lines = [
      if (node.session != null)
        conversation == null
            ? l10n.runningPhoneFromClaude
            : l10n.runningPhoneFromChat(conversation!),
      if (heavyFor != null)
        l10n.runningPhoneHeavyFor(
          formatTermuxDuration(l10n, heavyFor.inSeconds),
        ),
      if (node.stuck && !node.leftBehind)
        l10n.runningPhoneStuckWhy(
          formatTermuxDuration(l10n, p.elapsed.inSeconds),
        ),
      if (node.root) l10n.runningPhoneServerRoot,
    ];
    return Column(
      key: const Key('running-details'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitText(
          l10n.runningPhoneAbout(kind.name),
          key: const Key('running-details-about'),
        ),
        for (final line in lines) ...[
          SizedBox(height: tokens.space2),
          KitText(line, role: KitTextRole.secondary),
        ],
        SizedBox(height: tokens.space2),
        KitText(
          runningFacts(l10n, p),
          key: const Key('running-details-facts'),
          role: KitTextRole.secondary,
          tabular: true,
        ),
        SizedBox(height: tokens.space4),
        KitDetailsFold(
          values: [
            KitTechnicalValue(
              l10n.termuxProcsCommand,
              p.command,
              key: const Key('running-details-command'),
            ),
            KitTechnicalValue(l10n.termuxProcsProcessId, '${p.pid}'),
            KitTechnicalValue(l10n.termuxProcsParentId, '${p.ppid}'),
            KitTechnicalValue(l10n.runningPhoneOwner, p.owner.wire),
            if (node.session case final session?)
              KitTechnicalValue(l10n.runningPhoneConversationId, session),
          ],
        ),
      ],
    );
  }
}
