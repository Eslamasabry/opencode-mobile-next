import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';

import '../../../l10n/app_localizations.dart';
import '../../../scheduling/controller.dart';
import '../../../scheduling/models.dart';
import '../../../scheduling/recurrence.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import 'schedule_copy.dart';

/// The rules the sheet offers, in the order of its segments.
enum ScheduleSheetRule { once, daily, weekdays, custom }

/// What the sheet hands back besides saving: the person asked to delete the
/// schedule, or to open its last run's conversation.
enum ScheduleSheetExit { saved, delete, openRun }

/// Opens the one sheet that makes a scheduled prompt or changes one
/// (vision pick "B sheet", 2026-10-20): the prompt first, then when it runs
/// (Once / Every day / Weekdays / Custom on a wheel), then where, and one
/// main action that names the moment ("Schedule for 9:00 AM").
///
/// [seed] carries the target (server, project, conversation, agent, model)
/// for a new schedule; [existing] is the schedule being changed. [where]
/// is "neon-breaker · Laptop". [lastRun] is the existing schedule's last
/// run, shown with a way to its conversation when [canOpenRun] is true.
Future<ScheduleSheetExit?> showScheduleSheet(
  BuildContext context, {
  required ScheduledPromptsController controller,
  required ScheduledPrompt seed,
  required String where,
  ScheduledPrompt? existing,
  ScheduledPromptRun? lastRun,
  bool canOpenRun = false,
  @visibleForTesting DateTime? debugNow,
}) async {
  final l = AppLocalizations.of(context);
  final draft = _ScheduleDraft(
    controller: controller,
    seed: seed,
    existing: existing,
    now: debugNow ?? controller.now(),
  );
  final primary = ValueNotifier<KitAction?>(null);
  final step = ValueNotifier<KitSheetStep?>(null);
  void refresh() {
    primary.value = KitAction(
      key: const ValueKey('schedule-save'),
      label: draft.actionLabel(l),
      working: draft.saving,
      onPressed: () => unawaited(draft.save(context, l)),
    );
  }

  // The typed prompt survives a swipe, back or a restart until the
  // schedule is saved (DATA-2, G10); kept per server profile.
  final profileId = (existing ?? seed).profileId;
  final textDraft = profileId.isEmpty
      ? null
      : KitDraft(
          target: 'schedule.${existing?.id ?? 'new'}',
          profileId: profileId,
          controller: draft.prompt,
        );
  draft.addListener(refresh);
  refresh();
  try {
    final exit = await showKitSheet<ScheduleSheetExit>(
      context,
      title: existing == null ? l.scheduleNew : l.scheduleEdit,
      primaryListenable: primary,
      stepListenable: step,
      draft: textDraft,
      tertiary: [
        if (existing != null)
          KitAction(
            label: l.scheduleDelete,
            onPressed: () => Navigator.of(
              draft.sheetContext ?? context,
            ).pop(ScheduleSheetExit.delete),
          ),
      ],
      body: (sheetContext) {
        draft.sheetContext = sheetContext;
        return _ScheduleForm(
          draft: draft,
          step: step,
          where: where,
          lastRun: lastRun,
          canOpenRun: canOpenRun,
        );
      },
    );
    if (exit == ScheduleSheetExit.saved || exit == ScheduleSheetExit.delete) {
      if (textDraft != null) unawaited(textDraft.clear());
    }
    return exit;
  } finally {
    draft.removeListener(refresh);
    // The sheet's route has finished its exit: nothing reads these now.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      draft.dispose();
      primary.dispose();
      step.dispose();
    });
  }
}

/// The sheet's live input. Nothing is saved until the main action.
class _ScheduleDraft extends ChangeNotifier {
  _ScheduleDraft({
    required this.controller,
    required this.seed,
    required this.existing,
    required this.now,
  }) {
    final s = existing ?? seed;
    final r = s.recurrence;
    prompt = TextEditingController(text: s.prompt);
    rule = switch (r.kind) {
      ScheduleRecurrenceKind.once => ScheduleSheetRule.once,
      ScheduleRecurrenceKind.daily => ScheduleSheetRule.daily,
      ScheduleRecurrenceKind.weekdays => ScheduleSheetRule.weekdays,
      ScheduleRecurrenceKind.interval => ScheduleSheetRule.custom,
    };
    final local = now.toLocal();
    final today = DateTime(local.year, local.month, local.day);
    if (existing == null) {
      // A new schedule starts at 9:00, today when that is still ahead.
      minutes = 9 * 60;
      day = local.isBefore(DateTime(today.year, today.month, today.day, 9))
          ? today
          : DateTime(today.year, today.month, today.day + 1);
    } else if (r.kind == ScheduleRecurrenceKind.once) {
      final at = r.at!.toLocal();
      minutes = at.hour * 60 + at.minute;
      final d = DateTime(at.year, at.month, at.day);
      day = d.isBefore(today) ? today : d;
    } else {
      minutes = r.hour * 60 + r.minute;
      day = today;
    }
    hours = r.kind == ScheduleRecurrenceKind.interval ? r.hours : 3;
    thisConversation = s.sessionId != null;
    prompt.addListener(_typed);
  }

  final ScheduledPromptsController controller;
  final ScheduledPrompt seed;
  final ScheduledPrompt? existing;
  final DateTime now;
  late final TextEditingController prompt;
  late ScheduleSheetRule rule;

  /// Minutes after midnight.
  late int minutes;

  /// The once day, at midnight local time.
  late DateTime day;
  late int hours;
  late bool thisConversation;
  String? error;
  bool saving = false;
  BuildContext? sheetContext;

  bool get canChooseConversation =>
      seed.sessionId != null || existing?.sessionId != null;

  void _typed() {
    if (error != null) {
      error = null;
      notifyListeners();
    }
  }

  void setRule(ScheduleSheetRule value) {
    rule = value;
    error = null;
    notifyListeners();
  }

  void setMinutes(int value) {
    minutes = value;
    error = null;
    notifyListeners();
  }

  void setDay(DateTime value) {
    day = value;
    error = null;
    notifyListeners();
  }

  void setHours(int value) {
    hours = value;
    notifyListeners();
  }

  void setThisConversation(bool value) {
    thisConversation = value;
    notifyListeners();
  }

  DateTime get onceAt =>
      DateTime(day.year, day.month, day.day, minutes ~/ 60, minutes % 60);

  /// The main action names the moment it sets.
  String actionLabel(AppLocalizations l) {
    final clock = scheduleClock(l, minutes ~/ 60, minutes % 60);
    switch (rule) {
      case ScheduleSheetRule.daily:
      case ScheduleSheetRule.weekdays:
        return l.scheduleForTime(clock);
      case ScheduleSheetRule.custom:
        return l.scheduleForHours(hours);
      case ScheduleSheetRule.once:
        final days = _daysBetween(now.toLocal(), day);
        if (days == 0) return l.scheduleForToday(clock);
        if (days == 1) return l.scheduleForTomorrow(clock);
        return l.scheduleForWhen(scheduleWhen(l, onceAt, now));
    }
  }

  Future<void> save(BuildContext context, AppLocalizations l) async {
    if (saving) return;
    final current = controller.now();
    if (prompt.text.trim().isEmpty) {
      error = l.scheduleNeedPrompt;
      notifyListeners();
      return;
    }
    if (rule == ScheduleSheetRule.once && !onceAt.isAfter(current)) {
      error = l.scheduleNeedFuture;
      notifyListeners();
      return;
    }
    saving = true;
    error = null;
    notifyListeners();
    final hour = minutes ~/ 60;
    final minute = minutes % 60;
    final recurrence = switch (rule) {
      ScheduleSheetRule.once => ScheduleRecurrence.once(onceAt),
      ScheduleSheetRule.daily => ScheduleRecurrence.daily(
        hour: hour,
        minute: minute,
      ),
      ScheduleSheetRule.weekdays => ScheduleRecurrence.weekdays(
        hour: hour,
        minute: minute,
      ),
      ScheduleSheetRule.custom => ScheduleRecurrence.interval(
        hours: hours,
        anchor: current.add(Duration(hours: hours)),
      ),
    };
    final base = existing ?? seed;
    try {
      await controller.save(
        ScheduledPrompt(
          id: base.id,
          profileId: base.profileId,
          profileFingerprint: seed.profileFingerprint,
          prompt: prompt.text.trim(),
          projectPath: base.projectPath,
          workspace: base.workspace,
          sessionId: thisConversation
              ? (base.sessionId ?? seed.sessionId)
              : null,
          agent: base.agent,
          modelProviderId: base.modelProviderId,
          modelId: base.modelId,
          recurrence: recurrence,
          createdAt: base.createdAt,
          missedPolicy: base.missedPolicy,
          enabled: true,
          nextRunAt: nextScheduledOccurrence(recurrence, current),
          revision: existing == null ? 1 : base.revision + 1,
        ),
      );
      final sheet = sheetContext;
      if (sheet != null && sheet.mounted) {
        Navigator.of(sheet).pop(ScheduleSheetExit.saved);
      }
    } catch (_) {
      error = l.scheduleSaveFailed;
    } finally {
      saving = false;
      notifyListeners();
    }
  }

  @override
  void dispose() {
    prompt.removeListener(_typed);
    prompt.dispose();
    super.dispose();
  }
}

/// Calendar days from [from]'s date to [to]'s, whatever the clock does in
/// between (a daylight-saving day is 23 or 25 hours long).
int _daysBetween(DateTime from, DateTime to) => DateTime.utc(
  to.year,
  to.month,
  to.day,
).difference(DateTime.utc(from.year, from.month, from.day)).inDays;

/// The step that chooses which conversation a run goes to.
const _whereStep = 'where';

class _ScheduleForm extends StatelessWidget {
  const _ScheduleForm({
    required this.draft,
    required this.step,
    required this.where,
    required this.lastRun,
    required this.canOpenRun,
  });

  final _ScheduleDraft draft;
  final ValueNotifier<KitSheetStep?> step;
  final String where;
  final ScheduledPromptRun? lastRun;
  final bool canOpenRun;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: Listenable.merge([draft, step]),
    builder: (context, _) {
      final l = AppLocalizations.of(context);
      if (step.value?.id == _whereStep) return _conversationStep(context, l);
      return _form(context, l);
    },
  );

  Widget _conversationStep(BuildContext context, AppLocalizations l) {
    void choose(bool value) {
      draft.setThisConversation(value);
      step.value = null;
    }

    return KitChoiceList<bool>.single(
      semanticsLabel: l.scheduleTarget,
      selected: draft.thisConversation,
      onSelected: choose,
      choices: [
        KitChoice(value: true, title: l.scheduleThisConversation),
        KitChoice(value: false, title: l.scheduleNewConversation),
      ],
    );
  }

  Widget _form(BuildContext context, AppLocalizations l) {
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space4);
    final run = lastRun;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitField(
          fieldKey: const ValueKey('schedule-prompt'),
          label: l.scheduleAsk,
          hint: l.scheduleAskHint,
          controller: draft.prompt,
          kind: KitFieldKind.multiline,
          minLines: 1,
          maxLines: 6,
          autofocus: draft.existing == null,
          inputFormatters: [LengthLimitingTextInputFormatter(32000)],
          error: draft.error == l.scheduleNeedPrompt ? draft.error : null,
        ),
        gap,
        KitSegmented<ScheduleSheetRule>(
          semanticsLabel: l.scheduleRepeat,
          fitLabels: true,
          selected: draft.rule,
          onChanged: draft.setRule,
          segments: [
            KitSegment(value: ScheduleSheetRule.once, label: l.scheduleOnce),
            KitSegment(
              value: ScheduleSheetRule.daily,
              label: l.scheduleEveryDay,
            ),
            KitSegment(
              value: ScheduleSheetRule.weekdays,
              label: l.scheduleWeekdaysShort,
            ),
            KitSegment(
              value: ScheduleSheetRule.custom,
              label: l.scheduleCustom,
            ),
          ],
        ),
        SizedBox(height: tokens.space3),
        KitWheel(columns: _columns(context, l)),
        if (draft.error != null && draft.error != l.scheduleNeedPrompt)
          Padding(
            padding: EdgeInsets.only(top: tokens.space2),
            child: KitText(
              draft.error!,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          ),
        SizedBox(height: tokens.space3),
        KitRow(
          key: const ValueKey('schedule-where'),
          padding: EdgeInsets.zero,
          leading: KitRow.icon(context, AppIconography.folderOpen),
          title: where,
          supporting: TextSpan(
            text: draft.thisConversation
                ? l.scheduleInThisConversation
                : l.scheduleNewConversation,
          ),
          trailing: draft.canChooseConversation ? const KitChevron() : null,
          onTap: draft.canChooseConversation
              ? () => step.value = KitSheetStep(
                  id: _whereStep,
                  title: l.scheduleTarget,
                  back: KitAction(
                    label: MaterialLocalizations.of(context).backButtonTooltip,
                    onPressed: () => step.value = null,
                  ),
                )
              : null,
        ),
        if (run != null)
          KitRow(
            key: const ValueKey('schedule-last-run'),
            padding: EdgeInsets.zero,
            leading: KitRow.icon(context, AppIconography.clock),
            title: scheduleLastResult(l, run, draft.now),
            supporting: TextSpan(
              text: run.errorCode != null
                  ? scheduleErrorCopy(l, run.errorCode)
                  : canOpenRun
                  ? l.scheduleOpenRun
                  : null,
            ),
            supportingMaxLines: 3,
            trailing: canOpenRun ? const KitChevron() : null,
            onTap: canOpenRun
                ? () => Navigator.of(
                    draft.sheetContext ?? context,
                  ).pop(ScheduleSheetExit.openRun)
                : null,
          ),
        SizedBox(height: tokens.space3),
        KitText(
          l.scheduleClosedNote,
          role: KitTextRole.secondary,
          tone: KitTextTone.tertiary,
        ),
      ],
    );
  }

  List<KitWheelColumn<dynamic>> _columns(
    BuildContext context,
    AppLocalizations l,
  ) {
    if (draft.rule == ScheduleSheetRule.custom) {
      final values = {for (var h = 1; h <= 24; h++) h, draft.hours}.toList()
        ..sort();
      return [
        KitWheelColumn<int>(
          key: const ValueKey('schedule-wheel-hours'),
          values: values,
          selected: draft.hours,
          label: l.scheduleRuleHours,
          onChanged: draft.setHours,
          semanticsLabel: l.scheduleWheelHowOften,
        ),
      ];
    }
    // Quarter hours, plus the saved minute when it is not on one.
    final times = {
      for (var m = 0; m < 24 * 60; m += 15) m,
      draft.minutes,
    }.toList()..sort();
    KitWheelColumn<int> time({int flex = 1}) => KitWheelColumn<int>(
      key: const ValueKey('schedule-wheel-time'),
      values: times,
      selected: draft.minutes,
      label: (m) => scheduleClock(l, m ~/ 60, m % 60),
      onChanged: draft.setMinutes,
      semanticsLabel: l.scheduleTime,
      flex: flex,
    );
    if (draft.rule != ScheduleSheetRule.once) return [time()];
    final local = draft.now.toLocal();
    final today = DateTime(local.year, local.month, local.day);
    final days = [
      for (var i = 0; i < 60; i++)
        DateTime(today.year, today.month, today.day + i),
    ];
    if (!days.contains(draft.day)) days.add(draft.day);
    return [
      KitWheelColumn<DateTime>(
        key: const ValueKey('schedule-wheel-day'),
        values: days,
        selected: draft.day,
        flex: 3,
        label: (d) {
          final n = _daysBetween(today, d);
          if (n == 0) return l.scheduleWheelToday;
          if (n == 1) return l.scheduleWheelTomorrow;
          return DateFormat.MMMEd(l.localeName).format(d);
        },
        onChanged: draft.setDay,
        semanticsLabel: l.scheduleWheelDay,
      ),
      time(flex: 2),
    ];
  }
}
