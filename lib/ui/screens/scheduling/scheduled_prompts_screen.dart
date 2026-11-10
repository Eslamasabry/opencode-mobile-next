import 'dart:async';

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../../scheduling/controller.dart';
import '../../../scheduling/models.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'schedule_copy.dart';
import 'schedule_sheet.dart';

/// Scheduled prompts (vision pick "A list + B sheet", 2026-10-20): one calm
/// row per schedule (its words, one state line, a quiet switch) and one
/// mint "New scheduled prompt" at the thumb. A row opens the same sheet
/// that creates one.
class ScheduledPromptsScreen extends StatefulWidget {
  const ScheduledPromptsScreen({
    super.key,
    required this.controller,
    required this.target,
    this.serverName,
    this.onOpenRun,
  });
  final ScheduledPromptsController controller;

  /// What a new schedule is for: the server, project and conversation the
  /// screen was opened from.
  final ScheduledPrompt target;

  /// The saved server's name as the app shows it ("Laptop", "This phone").
  final String? serverName;
  final Future<void> Function(ScheduledPromptRun)? onOpenRun;
  @override
  State<ScheduledPromptsScreen> createState() => _ScheduledPromptsScreenState();
}

class _ScheduledPromptsScreenState extends State<ScheduledPromptsScreen>
    with WidgetsBindingObserver {
  ScheduledPromptsController get c => widget.controller;
  bool _loaded = false;

  @override
  void initState() {
    super.initState();
    c.addListener(_changed);
    WidgetsBinding.instance.addObserver(this);
    unawaited(_load());
  }

  Future<void> _load() async {
    await c.load();
    if (mounted) setState(() => _loaded = true);
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(c.load());
  }

  @override
  void dispose() {
    c.removeListener(_changed);
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  Future<void> _act(Future<void> Function() action) async {
    try {
      await action();
    } catch (_) {
      /* Controller exposes failure. */
    }
  }

  String get _project => scheduleProjectName(widget.target.projectPath);

  String _where(ScheduledPrompt s) =>
      [scheduleProjectName(s.projectPath), ?widget.serverName].join(' · ');

  /// The schedule's latest run, if it has one.
  ScheduledPromptRun? _lastRun(ScheduledPrompt s) {
    ScheduledPromptRun? last;
    for (final run in c.runs) {
      if (run.scheduleId != s.id) continue;
      if (last == null || run.scheduledAt.isAfter(last.scheduledAt)) {
        last = run;
      }
    }
    return last;
  }

  /// A one-time schedule whose moment has passed is finished, not paused.
  bool _spent(ScheduledPrompt s) =>
      s.recurrence.kind == ScheduleRecurrenceKind.once &&
      !s.recurrence.at!.isAfter(c.now());

  bool _finished(ScheduledPrompt s) =>
      _spent(s) || (s.enabled && s.nextRunAt == null);

  /// Soonest first, then paused, then finished.
  List<ScheduledPrompt> get _sorted {
    int rank(ScheduledPrompt s) => _finished(s)
        ? 2
        : s.enabled
        ? 0
        : 1;
    final far = DateTime.utc(9999);
    return [...c.schedules]..sort((a, b) {
      final byRank = rank(a).compareTo(rank(b));
      if (byRank != 0) return byRank;
      return (a.nextRunAt ?? far).compareTo(b.nextRunAt ?? far);
    });
  }

  /// The row's one state line: "Every weekday at 9:00 AM · Sent yesterday".
  String _line(ScheduledPrompt s, AppLocalizations l) {
    final now = c.now();
    final run = _lastRun(s);
    if (_finished(s)) {
      return run == null ? l.scheduleFinished : scheduleLastResult(l, run, now);
    }
    if (run?.status == ScheduledPromptRunStatus.running) {
      return l.scheduleSendingNow;
    }
    final rule = s.recurrence.kind == ScheduleRecurrenceKind.once
        ? scheduleWhen(l, s.recurrence.at!, now)
        : scheduleRule(l, s.recurrence, now);
    if (!s.enabled) return '${l.schedulePaused} · $rule';
    final project = scheduleProjectName(s.projectPath);
    return [
      rule,
      if (project != _project) project,
      if (run != null) scheduleLastResult(l, run, now),
    ].join(' · ');
  }

  Future<void> _open([ScheduledPrompt? schedule]) async {
    final run = schedule == null ? null : _lastRun(schedule);
    final exit = await showScheduleSheet(
      context,
      controller: c,
      seed: ScheduledPrompt(
        id: DateTime.now().microsecondsSinceEpoch.toString(),
        profileId: widget.target.profileId,
        profileFingerprint: widget.target.profileFingerprint,
        prompt: '',
        projectPath: widget.target.projectPath,
        workspace: widget.target.workspace,
        sessionId: widget.target.sessionId,
        agent: widget.target.agent,
        modelProviderId: widget.target.modelProviderId,
        modelId: widget.target.modelId,
        recurrence: const ScheduleRecurrence.weekdays(hour: 9),
        createdAt: c.now(),
      ),
      existing: schedule,
      where: _where(schedule ?? widget.target),
      lastRun: run,
      canOpenRun: run?.sessionId != null && widget.onOpenRun != null,
    );
    if (!mounted || schedule == null) return;
    switch (exit) {
      case ScheduleSheetExit.delete:
        await _delete(schedule);
      case ScheduleSheetExit.openRun:
        if (run != null) await _act(() => widget.onOpenRun!(run));
      case ScheduleSheetExit.saved || null:
        break;
    }
  }

  Future<void> _delete(ScheduledPrompt s) {
    final l = AppLocalizations.of(context);
    final name = scheduleName(l, s);
    return showKitConfirm(
      context,
      title: l.scheduleDeleteNamedTitle(
        name.length <= 40 ? name : '${name.substring(0, 40).trimRight()}…',
      ),
      body: l.scheduleDeleteBody,
      confirmLabel: l.scheduleDelete,
      kind: KitConfirmKind.neutral,
      icon: AppIconography.delete,
      action: () => c.delete(s.id),
    );
  }

  Future<void> _pickDigestTime() async {
    final l = AppLocalizations.of(context);
    final value = await showKitTimePicker(
      context,
      title: l.scheduleDigest,
      initial: TimeOfDay(hour: c.digest.hour, minute: c.digest.minute),
    );
    if (value == null) return;
    await _act(
      () => c.setDigest(
        ScheduledPromptDigest(
          enabled: true,
          hour: value.hour,
          minute: value.minute,
          lastDeliveredAt: c.digest.lastDeliveredAt,
        ),
      ),
    );
  }

  Widget _row(ScheduledPrompt s, AppLocalizations l) {
    final title = scheduleName(l, s);
    final line = _line(s, l);
    if (_finished(s)) {
      return KitRow(
        key: ValueKey('schedule-${s.id}'),
        title: title,
        supporting: TextSpan(text: line),
        onTap: () => unawaited(_open(s)),
        trailing: KitIconButton(
          icon: AppIconography.delete,
          tooltip: l.scheduleDelete,
          onPressed: () => unawaited(_delete(s)),
        ),
      );
    }
    return KitSwitchRow(
      key: ValueKey('schedule-${s.id}'),
      title: title,
      supporting: line,
      supportingMaxLines: 1,
      value: s.enabled,
      openLabel: l.scheduleEdit,
      onOpen: () => unawaited(_open(s)),
      onChanged: (on) => unawaited(_act(() => c.setEnabled(s.id, on))),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final supported = c.platformStatus.supported;
    final topBar = KitTopBar(
      title: l.scheduleTitle,
      subtitle: [_project, ?widget.serverName].join(' · '),
    );
    if (_loaded && !supported) {
      return KitScreen(
        topBar: topBar,
        body: KitStateView(
          icon: AppIconography.clock,
          title: l.scheduleUnsupported,
        ),
      );
    }
    final failed = c.errorCode != null && c.errorCode != 'unsupported';
    final schedules = _sorted;
    final Widget body;
    if (!_loaded && schedules.isEmpty) {
      body = const SizedBox.shrink();
    } else if (schedules.isEmpty) {
      // Calm (vision 2026-10-20): the big line, one plain next step; the
      // pinned New scheduled prompt is the action.
      body = KitStateView.calm(
        key: const ValueKey('schedule-empty'),
        icon: AppIconography.clock,
        title: failed ? l.scheduleSaveFailed : l.scheduleEmpty,
        body: failed ? null : l.scheduleEmptyBody,
      );
    } else {
      body = ListView(
        padding: EdgeInsetsDirectional.only(
          top: tokens.space2,
          bottom: tokens.space4 + KitScreen.endPadding(context),
        ),
        children: [
          if (failed) KitGroupNote(message: l.scheduleSaveFailed),
          KitRowGroup(
            leadingIcons: false,
            children: [for (final s in schedules) _row(s, l)],
          ),
          KitRowGroup(
            leadingIcons: false,
            gapBefore: tokens.space5,
            children: [
              KitSwitchRow(
                key: const ValueKey('schedule-digest'),
                title: l.scheduleDigest,
                supporting: c.digest.enabled
                    ? l.scheduleRuleDaily(
                        scheduleClock(l, c.digest.hour, c.digest.minute),
                      )
                    : l.scheduleDigestShort,
                supportingMaxLines: 1,
                value: c.digest.enabled,
                openLabel: l.scheduleTime,
                onOpen: c.digest.enabled
                    ? () => unawaited(_pickDigestTime())
                    : null,
                onChanged: (v) => unawaited(
                  _act(
                    () => c.setDigest(
                      ScheduledPromptDigest(
                        enabled: v,
                        hour: c.digest.hour,
                        minute: c.digest.minute,
                        lastDeliveredAt: v && !c.digest.enabled
                            ? c.now()
                            : c.digest.lastDeliveredAt,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          if (!c.platformStatus.exactAllowed)
            KitGroupNote(
              message: l.scheduleExactNote,
              action: KitAction(
                label: l.scheduleExactAllow,
                onPressed: () => unawaited(_act(c.requestExactAlarm)),
              ),
            )
          else if (!c.platformStatus.notificationsAllowed)
            KitGroupNote(message: l.scheduleNotifyOff),
        ],
      );
    }
    return KitScreen(
      topBar: topBar,
      loading: c.loading && !_loaded,
      loadingLabel: l.scheduleTitle,
      body: body,
      bottom: _loaded && supported
          ? KitActionBlock(
              primary: KitAction(
                key: const ValueKey('schedule-new'),
                label: l.scheduleNew,
                // At large text the words need the whole button.
                icon: MediaQuery.textScalerOf(context).scale(1) >= 1.6
                    ? null
                    : AppIconography.add,
                onPressed: () => unawaited(_open()),
              ),
            )
          : null,
    );
  }
}
