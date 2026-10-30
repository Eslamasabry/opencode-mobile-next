// KitBackgroundWorkLine: the quiet line under a finished turn while work the
// agent started goes on in the background
// (docs/ux-system/kit-api/KitBackgroundWorkLine.md). "Still running in the
// background · Build the owner APK · 2 min" with the step light on its glyph;
// with several tasks "2 tasks still running in the background". Either opens
// in place to one row per task: its name, how long it has run and the command
// itself. A tap on a row takes the person to the step that started it. It folds into the space under the last turn: no bar, no
// card (no-new-UI-levels rule).
import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_max_width.dart';
import '../kit_motion.dart';
import '../kit_redact.dart';
import '../kit_since.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_step_timeline.dart';

/// One piece of work running in the background, as the line words it.
@immutable
class KitBackgroundTask {
  const KitBackgroundTask({
    required this.name,
    required this.since,
    this.command = '',
  });

  /// Short plain words for the work ("Build the owner APK"). Never a path
  /// or an id: the host words it.
  final String name;

  /// When the work started.
  final DateTime since;

  /// The command as it was run, shown as technical text on the opened row
  /// (at most three lines, redacted); empty shows none.
  final String command;
}

/// The line under a finished turn while background work runs.
///
/// [tasks] oldest first. With none the line fades out (in
/// [KitMotion.stepFade], at once under reduced motion) and takes no room.
/// A tap on the line opens the list in place (one row for one task), and a
/// tap on a row calls [onOpenTask] with its index.
///
/// States: none — the host decides what runs.
class KitBackgroundWorkLine extends StatefulWidget {
  const KitBackgroundWorkLine({
    super.key,
    required this.tasks,
    required this.onOpenTask,
    this.lineKey,
    this.afterStop = false,
  });

  final List<KitBackgroundTask> tasks;

  /// Go to the step that started task [index].
  final ValueChanged<int> onOpenTask;

  /// On the line's tap target.
  final Key? lineKey;

  /// The turn above ended on a failure or a stop: the line says the work
  /// is "still running after the turn stopped", so it never reads as the
  /// agent itself still working.
  final bool afterStop;

  /// "2 min", "1 h 5 min", "under a minute": how long [elapsed] has run.
  static String elapsedText(AppLocalizations l10n, Duration elapsed) {
    final minutes = elapsed.isNegative ? 0 : elapsed.inMinutes;
    return minutes < 60
        ? l10n.kitBackgroundWorkMinutes(minutes)
        : l10n.kitBackgroundWorkHours(minutes ~/ 60, minutes % 60);
  }

  @override
  State<KitBackgroundWorkLine> createState() => _KitBackgroundWorkLineState();
}

class _KitBackgroundWorkLineState extends State<KitBackgroundWorkLine> {
  bool _open = false;

  @override
  void didUpdateWidget(KitBackgroundWorkLine oldWidget) {
    super.didUpdateWidget(oldWidget);
    // No task left: there is no list to hold open.
    if (widget.tasks.isEmpty) _open = false;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final reduced = KitMotion.reduced(context);
    final tasks = widget.tasks;
    return AnimatedSwitcher(
      duration: reduced ? Duration.zero : KitMotion.stepFade,
      switchInCurve: KitMotion.enter,
      switchOutCurve: KitMotion.exit,
      transitionBuilder: (child, animation) => FadeTransition(
        opacity: animation,
        child: SizeTransition(
          sizeFactor: animation,
          alignment: AlignmentDirectional.topStart,
          child: child,
        ),
      ),
      child: tasks.isEmpty
          ? const SizedBox.shrink(key: ValueKey('kit-background-work-none'))
          : KeyedSubtree(
              key: const ValueKey('kit-background-work-line'),
              child: tasks.length == 1
                  ? _one(context, tasks.single)
                  : _many(context, tasks),
            ),
    );
  }

  Widget _one(BuildContext context, KitBackgroundTask task) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return KitSince(
      since: task.since,
      ticks: KitSinceTicks.minutes,
      builder: (context, status) {
        final time = KitBackgroundWorkLine.elapsedText(l10n, status.elapsed);
        final label = widget.afterStop
            ? l10n.kitBackgroundWorkOneAfterStop(task.name, time)
            : l10n.kitBackgroundWorkOne(task.name, time);
        return KitStepTimeline(
          label: label,
          icon: AppIconography.terminal,
          live: !_open,
          expanded: _open,
          onPressed: _toggle,
          steps: [_TaskRow(task: task, onOpen: () => widget.onOpenTask(0))],
          lineKey: widget.lineKey,
        );
      },
    );
  }

  void _toggle() => setState(() => _open = !_open);

  Widget _many(BuildContext context, List<KitBackgroundTask> tasks) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final label = widget.afterStop
        ? l10n.kitBackgroundWorkManyAfterStop(tasks.length)
        : l10n.kitBackgroundWorkMany(tasks.length);
    return KitStepTimeline(
      label: label,
      icon: AppIconography.terminal,
      live: !_open,
      expanded: _open,
      onPressed: _toggle,
      lineKey: widget.lineKey,
      steps: [
        for (var i = 0; i < tasks.length; i++)
          _TaskRow(task: tasks[i], onOpen: () => widget.onOpenTask(i)),
      ],
    );
  }
}

/// One task on the opened list: its node with the calm background light,
/// its words and how long it has run, the command under them in mono (at
/// most three lines), and a forward chevron: a tap goes to its step.
class _TaskRow extends StatelessWidget {
  const _TaskRow({required this.task, required this.onOpen});

  final KitBackgroundTask task;
  final VoidCallback onOpen;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final command = KitRedact.text(task.command.trim());
    return KitStepTimeline.node(
      context,
      icon: AppIconography.terminal,
      state: KitStepNodeState.background,
      lineHeight: KitStepTimeline.stepLineHeight(context),
      child: KitSince(
        since: task.since,
        ticks: KitSinceTicks.minutes,
        builder: (context, status) {
          final words = l10n.kitBackgroundWorkTask(
            task.name,
            KitBackgroundWorkLine.elapsedText(l10n, status.elapsed),
          );
          return KitTappable(
            onTap: onOpen,
            label: '$words. ${l10n.kitBackgroundWorkGoToStep}',
            shape: KitShape.tile,
            surface: KitSurfaceLevel.ground,
            child: ConstrainedBox(
              constraints: BoxConstraints(
                minHeight: KitStepTimeline.stepLineHeight(context),
              ),
              child: Padding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.space1,
                  vertical: tokens.space1,
                ),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          KitText(
                            words,
                            role: KitTextRole.secondary,
                            tone: KitTextTone.secondary,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          if (command.isNotEmpty)
                            KitText.mono(
                              command,
                              tone: KitTextTone.tertiary,
                              maxLines: 3,
                            ),
                        ],
                      ),
                    ),
                    SizedBox(width: tokens.space1),
                    Icon(
                      AppIconography.chevronRight,
                      size: tokens.smallIconSize,
                      color: tokens.roles.text2,
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}
