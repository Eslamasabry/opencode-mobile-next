// KitToolRow (docs/ux-system/kit-api/KitToolRow.md, kit-v2.md §9.2): one
// step of the agent's work as one line of the reply — what it did, where,
// and how it went — opening to its note, output or diff. Its `.agent` form
// is a sub-agent or an AI Team worker: who, on what, in what state and for
// how long, opening that agent's own conversation.
//
// A line of the reply (STATE-16, KIT-41): no frame, no fill, no radius, no
// shadow and no glass. Only what the step produced, once opened, is a block,
// and those blocks are the host's kit parts (KitCodeBlock, KitDiffView,
// KitImage, KitNotice) passed in `body`.
//
// States (KIT-12): notRun, pending, running, waitingForYou, done, failed,
// stopped, background; each folded or open where it has a note or body.
// Agent form: running (ticking), done, failed, waitingForYou, not tappable.
// A step may instead open elsewhere (onOpen): a forward chevron, no fold.
import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_buttons.dart';
import '../kit_motion.dart';
import '../kit_since.dart';
import '../kit_status_mark.dart';
import '../kit_tappable.dart';
import '../kit_task_mark.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../motion/kit_motion_parts.dart';
import 'kit_markdown.dart';
import 'kit_step_timeline.dart';

/// What kind of step it was. It picks the glyph only (one glyph per verb,
/// COPY-18); the words are the host's [KitToolRow.title].
enum KitToolKind {
  read,
  list,
  search,
  shell,
  edit,
  web,
  agent,
  todo,
  question,
  skill,
  other,
}

/// How the step went. The host maps the server's status; the row says it.
enum KitToolStatus {
  /// The server never ran it.
  notRun,

  /// Accepted, not started.
  pending,

  running,

  /// Blocked on a request (permission, question): never a spinner
  /// (AUTO-15).
  waitingForYou,

  done,

  failed,

  /// Stopped by the person or the server.
  stopped,

  /// Started and handed to the background (a background sub-agent).
  background,
}

/// From this text scale the title may take two lines and the path or detail
/// moves under it (A11Y-8).
const double _kWrapTextScale = 1.3;

/// One step of a turn's work, drawn as a line of the reply (STANDARDS
/// STATE-16, KIT-41): no frame and no fill; only what it printed, once
/// opened, is a block.
///
/// States: notRun, pending, running, waitingForYou, done, failed, stopped,
/// background; folded or open (KIT-12).
class KitToolRow extends StatefulWidget {
  const KitToolRow({
    super.key,
    required this.kind,
    required this.title,
    required this.status,
    this.detail,
    this.path,
    this.pathCut = KitMonoCut.middle,
    this.added,
    this.removed,
    this.duration,
    this.note,
    this.body = const <Widget>[],
    this.preview,
    this.expanded,
    this.onExpansionChanged,
    this.rowKey,
    this.onOpen,
    this.openLabel,
    this.onRetry,
  }) : assert(
         onOpen == null || note == null,
         'KitToolRow: a step either opens elsewhere (onOpen) or folds its '
         'note, not both',
       ),
       task = null,
       startedAt = null,
       liveMark = true,
       _agent = false;

  /// A sub-agent the turn started, or an AI Team worker or reviewer. One
  /// line: "[title] · [status word]", the task under it, the elapsed time
  /// while it runs. A tap opens its conversation (watching mode).
  const KitToolRow.agent({
    super.key,
    required this.title,
    required this.status,
    this.task,
    this.startedAt,
    this.onOpen,
    this.openLabel,
    this.rowKey,
    this.liveMark = true,
  }) : kind = KitToolKind.agent,
       detail = null,
       path = null,
       pathCut = KitMonoCut.middle,
       added = null,
       removed = null,
       duration = null,
       note = null,
       body = const <Widget>[],
       preview = null,
       expanded = null,
       onExpansionChanged = null,
       onRetry = null,
       _agent = true;

  /// The glyph; `.agent` is always [KitToolKind.agent].
  final KitToolKind kind;

  /// Host words: "Read main.dart", the agent's own heading for the step,
  /// "Delegated to explore", "furiosa · Worker".
  final String title;

  final KitToolStatus status;

  /// Host words: "3 matches", "Exit code 1 · failed", the tool's name under
  /// a heading. Shown when there is no [path].
  final String? detail;

  /// The file or folder touched: mono, LTR-isolated, middle ellipsis; the
  /// full value in semantics and the tooltip.
  final String? path;

  /// Where [path] gives way when it does not fit: the middle for a path (it
  /// keeps its root and file name), the end for a shell command (it reads
  /// from its first word).
  final KitMonoCut pathCut;

  /// An edit's "+n".
  final int? added;

  /// An edit's "−n".
  final int? removed;

  /// How long it took; shown once finished.
  final Duration? duration;

  /// Why, in the agent's words (secondary Markdown); shown first when open.
  final KitMarkdown? note;

  /// What it produced, shown in order when open: KitCodeBlock output
  /// (capped by the host), KitDiffView, KitImage or file rows, KitNotice.
  final List<Widget> body;

  /// A file write or edit: a few lines of what it changed. Drawn only in a
  /// [KitStepTimeline], as a card under the step while it is closed; a tap
  /// opens the step (its [body] is the full view). Null draws none.
  final KitStepPreview? preview;

  /// Non-null: controlled (the host's expansion store); a tap only reports
  /// through [onExpansionChanged].
  final bool? expanded;

  /// Called with the requested state on every open and close.
  final ValueChanged<bool>? onExpansionChanged;

  /// `.agent`: what it works on, one or two lines.
  final String? task;

  /// `.agent` while running: "for 3 min", ticking by the minute (KitSince).
  final DateTime? startedAt;

  /// `.agent`: false when the page already shows one live status elsewhere
  /// (the Now line): a running or waiting row then draws no spinner; a
  /// failed or stopped mark still shows.
  final bool liveMark;

  /// `.agent`: opens its conversation. Null: not tappable (no session).
  /// A step: opens its details elsewhere (an AI Team step's Work sheet);
  /// such a step has no note or body to fold.
  final VoidCallback? onOpen;

  /// The semantics hint of [onOpen]; null reads "Open its conversation"
  /// (`.agent`) or "Open its details" (a step).
  final String? openLabel;

  /// A failed step: "Retry", a quiet neutral text button under the line
  /// (owner decision 10A: still, no red, no shake). Null shows none; it is
  /// shown only while [status] is [KitToolStatus.failed].
  final VoidCallback? onRetry;

  /// On the line's tap target or text (today's `Key('embedded-tool-row')`,
  /// `ValueKey('team-conversation-agent-<id>')`).
  final Key? rowKey;

  final bool _agent;

  /// The kit's word for [status]: Not run · Waiting · Running · Waiting for
  /// you · Done · Failed · Stopped · Started in the background.
  static String wordFor(BuildContext context, KitToolStatus status) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return switch (status) {
      KitToolStatus.notRun => l10n.kitToolNotRun,
      KitToolStatus.pending => l10n.kitToolWaiting,
      KitToolStatus.running => l10n.kitToolRunning,
      KitToolStatus.waitingForYou => l10n.kitToolWaitingForYou,
      KitToolStatus.done => l10n.kitToolDone,
      KitToolStatus.failed => l10n.kitToolFailed,
      KitToolStatus.stopped => l10n.kitToolStopped,
      KitToolStatus.background => l10n.kitToolBackground,
    };
  }

  @override
  State<KitToolRow> createState() => _KitToolRowState();
}

IconData _glyphFor(KitToolKind kind) => switch (kind) {
  KitToolKind.read => AppIconography.fileText,
  KitToolKind.list => AppIconography.folderOpen,
  KitToolKind.search => AppIconography.search,
  KitToolKind.shell => AppIconography.terminal,
  KitToolKind.edit => AppIconography.editNote,
  KitToolKind.web => AppIconography.globe,
  KitToolKind.agent => AppIconography.agent,
  KitToolKind.todo => AppIconography.checklist,
  KitToolKind.question => AppIconography.question,
  KitToolKind.skill || KitToolKind.other => AppIconography.tools,
};

/// "3 seconds" / "2 minutes"; null under a second (nothing worth saying).
String? _durationWords(AppLocalizations l10n, Duration? duration) {
  if (duration == null) return null;
  if (duration.inMinutes >= 1) {
    return l10n.kitToolTookMinutes(duration.inMinutes);
  }
  if (duration.inSeconds >= 1) {
    return l10n.kitToolTookSeconds(duration.inSeconds);
  }
  return null;
}

bool _finished(KitToolStatus status) =>
    status == KitToolStatus.done ||
    status == KitToolStatus.failed ||
    status == KitToolStatus.stopped;

class _KitToolRowState extends State<KitToolRow>
    with SingleTickerProviderStateMixin {
  bool _own = false;

  /// The opened body fades in over [KitMotion.quick]; closing is instant.
  /// No size animation (MOT-5).
  late final AnimationController _fade = AnimationController(
    vsync: this,
    duration: KitMotion.quick,
    value: 1,
  );
  late final CurvedAnimation _fadeCurve = CurvedAnimation(
    parent: _fade,
    curve: KitMotion.enter,
  );

  bool get _opens => widget.note != null || widget.body.isNotEmpty;

  bool get _open => _opens && (widget.expanded ?? _own);

  @override
  void didUpdateWidget(covariant KitToolRow old) {
    super.didUpdateWidget(old);
    final wasOpen = old.note != null || old.body.isNotEmpty
        ? (old.expanded ?? _own)
        : false;
    if (!wasOpen && _open) _playFade();
  }

  void _playFade() {
    if (KitMotion.reduced(context)) {
      _fade.value = 1;
    } else {
      _fade.forward(from: 0);
    }
  }

  void _toggle() {
    final next = !_open;
    if (widget.expanded == null) {
      setState(() => _own = next);
      if (next) _playFade();
    }
    widget.onExpansionChanged?.call(next);
  }

  @override
  void dispose() {
    _fadeCurve.dispose();
    _fade.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final inTimeline = KitStepTimeline.inside(context);
    if (widget._agent) return _buildAgent(context, inTimeline);
    return inTimeline ? _buildTimelineStep(context) : _buildStep(context);
  }

  // ── The step on a timeline ───────────────────────────────────────────────

  /// The step inside a [KitStepTimeline]: its node on the rail (a tile with
  /// the step's glyph, or the state's mark in its place), the title in the
  /// row's first-line role with the file or detail muted under it, the
  /// counts and state words at the end, and, for a file write or edit, the
  /// preview card under it until the step is opened (then the full body).
  Widget _buildTimelineStep(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final row = widget;
    final status = row.status;
    final word = KitToolRow.wordFor(context, status);
    final durationWords = _finished(status)
        ? _durationWords(l10n, row.duration)
        : null;
    final hasCounts = row.added != null || row.removed != null;
    final wide = MediaQuery.textScalerOf(context).scale(1) >= _kWrapTextScale;
    final open = _open;
    final path = row.path;
    final detail = row.detail;
    final label = _labelOf(l10n, row, word, durationWords);

    final titleText = KitText(
      row.title,
      role: KitTextRole.rowTitle,
      tone: status == KitToolStatus.notRun
          ? KitTextTone.tertiary
          : KitTextTone.primary,
      maxLines: wide ? 3 : 1,
      overflow: TextOverflow.ellipsis,
    );

    Widget? second;
    if (path != null && path.isNotEmpty) {
      second = KitText.mono(
        path,
        cut: row.pathCut,
        tone: KitTextTone.secondary,
        maxLines: 1,
      );
      if (!_opens) {
        second = Tooltip(
          message: path,
          excludeFromSemantics: true,
          child: second,
        );
      }
    } else if (detail != null && detail.isNotEmpty) {
      second = KitText(
        detail,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
        maxLines: wide ? 3 : 2,
        overflow: TextOverflow.ellipsis,
      );
    }

    final counts = hasCounts ? _Counts(row.added, row.removed) : null;
    final words = _stepWords(
      status,
      word,
      durationWords,
      maxLines: wide ? 2 : 1,
    );
    final Widget? chevron = _opens
        ? SizedBox.square(
            dimension: tokens.smallIconSize,
            child: KitSpin.chevron(expanded: open),
          )
        : row.onOpen != null
        ? const _ForwardChevron()
        : null;

    final Widget middle = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        titleText,
        ?second,
        if (wide) ...[?counts, ?words],
      ],
    );
    final Widget titleLine = LayoutBuilder(
      builder: (context, constraints) {
        final Widget? tail = wide
            ? null
            : (counts == null && words == null)
            ? null
            : ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: (constraints.maxWidth * 0.45).floorToDouble(),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    ?counts,
                    if (counts != null && words != null)
                      SizedBox(width: tokens.space2),
                    if (words != null) Flexible(child: words),
                  ],
                ),
              );
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(child: middle),
            if (tail != null) ...[
              SizedBox(width: tokens.space2),
              _FirstLine(height: 22, child: tail),
            ],
            if (chevron != null) ...[
              SizedBox(width: tokens.space1),
              _FirstLine(height: 22, child: chevron),
            ],
          ],
        );
      },
    );

    // The 48 dp target is the top of the box: the first line stays at the
    // same place whatever the box adds below it, so the node and the rail
    // can be placed from the tokens alone.
    final Widget headerBox = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Align(
        alignment: AlignmentDirectional.topStart,
        child: Padding(
          padding: EdgeInsetsDirectional.only(
            top: tokens.space1,
            bottom: tokens.space2,
          ),
          child: titleLine,
        ),
      ),
    );

    final Widget header;
    if (_opens) {
      header = Semantics(
        expanded: open,
        child: KitTappable(
          tappableKey: row.rowKey,
          onTap: _toggle,
          label: label,
          tooltip: path != null && path.isNotEmpty ? path : null,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: headerBox,
        ),
      );
    } else if (row.onOpen case final onOpen?) {
      header = Semantics(
        hint: row.openLabel ?? l10n.kitToolOpenDetails,
        child: KitTappable(
          tappableKey: row.rowKey,
          onTap: onOpen,
          label: label,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: headerBox,
        ),
      );
    } else {
      header = Semantics(
        key: row.rowKey,
        container: true,
        label: label,
        excludeSemantics: true,
        child: headerBox,
      );
    }

    final onRetry = status == KitToolStatus.failed ? row.onRetry : null;
    final Widget? retry = onRetry == null
        ? null
        : Align(
            alignment: AlignmentDirectional.centerStart,
            child: KitButton.tertiary(
              key: const ValueKey('kit-tool-retry'),
              label: l10n.kitToolRetry,
              onPressed: onRetry,
            ),
          );

    final preview = row.preview;
    final Widget? previewCard =
        !open &&
            status == KitToolStatus.done &&
            preview != null &&
            !preview.isEmpty
        ? KitStepTimeline.previewCard(
            context,
            preview: preview,
            onOpen: _toggle,
            label: path ?? row.title,
            key: const ValueKey('kit-step-preview'),
          )
        : null;

    final Widget column = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        header,
        ?retry,
        ?previewCard,
        if (open)
          FadeTransition(
            opacity: _fadeCurve,
            child: _Body(children: [?row.note, ...row.body]),
          ),
      ],
    );

    final mark = _stepMark(status);
    return KitStepTimeline.node(
      context,
      lineHeight: 22,
      top: tokens.space1,
      icon: mark == null ? _glyphFor(row.kind) : null,
      mark: mark,
      child: column,
    );
  }

  /// The row's one spoken line: title, where, counts, state and time.
  String _labelOf(
    AppLocalizations l10n,
    KitToolRow row,
    String word,
    String? durationWords,
  ) {
    final path = row.path;
    final detail = row.detail;
    return [
      row.title,
      if (path != null && path.isNotEmpty)
        path
      else if (detail != null && detail.isNotEmpty)
        detail,
      if (row.added != null || row.removed != null)
        l10n.kitCodeChanges(row.added ?? 0, row.removed ?? 0),
      word,
      ?durationWords,
    ].join(', ');
  }

  // ── The step line ────────────────────────────────────────────────────────

  Widget _buildStep(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final row = widget;
    final status = row.status;
    final word = KitToolRow.wordFor(context, status);
    final durationWords = _finished(status)
        ? _durationWords(l10n, row.duration)
        : null;
    final hasCounts = row.added != null || row.removed != null;
    final wide = MediaQuery.textScalerOf(context).scale(1) >= _kWrapTextScale;
    final open = _open;
    final path = row.path;
    final detail = row.detail;

    final label = [
      row.title,
      if (path != null && path.isNotEmpty)
        path
      else if (detail != null && detail.isNotEmpty)
        detail,
      if (hasCounts) l10n.kitCodeChanges(row.added ?? 0, row.removed ?? 0),
      word,
      ?durationWords,
    ].join(', ');

    final titleText = KitText(
      row.title,
      role: KitTextRole.secondary,
      tone: status == KitToolStatus.notRun
          ? KitTextTone.tertiary
          : KitTextTone.primary,
      maxLines: wide ? 2 : 1,
      overflow: TextOverflow.ellipsis,
    );

    Widget? second;
    if (path != null && path.isNotEmpty) {
      second = KitText.mono(path, cut: row.pathCut, maxLines: 1);
      if (!_opens) {
        // A row that opens shows the full path through its KitTappable
        // tooltip; a plain row carries it on the value itself.
        second = Tooltip(
          message: path,
          excludeFromSemantics: true,
          child: second,
        );
      }
    } else if (detail != null && detail.isNotEmpty) {
      second = KitText(
        detail,
        role: KitTextRole.secondary,
        tone: KitTextTone.secondary,
        maxLines: wide ? 2 : 1,
        overflow: TextOverflow.ellipsis,
      );
    }

    final counts = hasCounts ? _Counts(row.added, row.removed) : null;
    final mark = _stepMark(status);
    final words = _stepWords(
      status,
      word,
      durationWords,
      maxLines: wide ? 2 : 1,
    );

    final Widget middle;
    if (wide) {
      middle = Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [titleText, ?second, ?counts, ?words],
      );
    } else {
      middle = LayoutBuilder(
        builder: (context, constraints) {
          final line = second;
          if (line == null) {
            return Row(
              children: [
                Flexible(child: titleText),
                if (counts != null) ...[SizedBox(width: tokens.space2), counts],
              ],
            );
          }
          // The title keeps its own width up to 60 % of the line; the path
          // or detail takes the rest and cuts in the middle (or the end).
          final titleMax = (constraints.maxWidth * 0.6).floorToDouble();
          return Row(
            children: [
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: titleMax),
                child: titleText,
              ),
              SizedBox(width: tokens.space2),
              Expanded(child: line),
              if (counts != null) ...[SizedBox(width: tokens.space2), counts],
            ],
          );
        },
      );
    }

    final glyph = _Glyph(_glyphFor(row.kind));

    final Widget? chevron = _opens
        ? SizedBox.square(
            dimension: tokens.smallIconSize,
            child: KitSpin.chevron(expanded: open),
          )
        : row.onOpen != null
        ? const _ForwardChevron()
        : null;

    final line = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        heightFactor: 1,
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
          child: LayoutBuilder(
            builder: (context, constraints) {
              // At large text the words sit under the title and only the mark
              // stays at the end; on one line they share the end, never more
              // than 45 % of the line.
              final Widget? end;
              if (wide) {
                end = mark;
              } else if (mark == null && words == null) {
                end = null;
              } else {
                end = ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: (constraints.maxWidth * 0.45).floorToDouble(),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      ?mark,
                      if (mark != null && words != null)
                        SizedBox(width: tokens.space1),
                      if (words != null) Flexible(child: words),
                    ],
                  ),
                );
              }
              return Row(
                crossAxisAlignment: wide
                    ? CrossAxisAlignment.start
                    : CrossAxisAlignment.center,
                children: [
                  glyph,
                  SizedBox(width: tokens.space2),
                  Expanded(child: middle),
                  if (end != null) ...[
                    SizedBox(width: tokens.space2),
                    _FirstLine(child: end),
                  ],
                  if (chevron != null) ...[
                    SizedBox(width: tokens.space1),
                    _FirstLine(child: chevron),
                  ],
                ],
              );
            },
          ),
        ),
      ),
    );

    final Widget header;
    if (_opens) {
      header = Semantics(
        expanded: open,
        child: KitTappable(
          tappableKey: row.rowKey,
          onTap: _toggle,
          label: label,
          tooltip: path != null && path.isNotEmpty ? path : null,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: line,
        ),
      );
    } else if (row.onOpen case final onOpen?) {
      header = Semantics(
        hint: row.openLabel ?? l10n.kitToolOpenDetails,
        child: KitTappable(
          tappableKey: row.rowKey,
          onTap: onOpen,
          label: label,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: line,
        ),
      );
    } else {
      header = Semantics(
        key: row.rowKey,
        container: true,
        label: label,
        excludeSemantics: true,
        child: line,
      );
    }

    // A failed step offers Retry on its own line: a quiet neutral button,
    // no frame, no motion (10A).
    final onRetry = status == KitToolStatus.failed ? row.onRetry : null;
    final Widget? retry = onRetry == null
        ? null
        : Align(
            alignment: AlignmentDirectional.centerStart,
            child: Padding(
              padding: EdgeInsetsDirectional.only(start: tokens.space4),
              child: KitButton.tertiary(
                key: const ValueKey('kit-tool-retry'),
                label: l10n.kitToolRetry,
                onPressed: onRetry,
              ),
            ),
          );

    if (!open) {
      if (retry == null) return header;
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [header, retry],
      );
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        header,
        ?retry,
        FadeTransition(
          opacity: _fadeCurve,
          child: _Body(children: [?row.note, ...row.body]),
        ),
      ],
    );
  }

  // ── The agent line ───────────────────────────────────────────────────────

  Widget _buildAgent(BuildContext context, bool inTimeline) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final row = widget;
    final word = KitToolRow.wordFor(context, row.status);
    final running = row.status == KitToolStatus.running;
    final task = row.task;
    final separator = l10n.kitWorkSeparator;

    Widget content(String? elapsed) {
      final label = [
        row.title,
        if (task != null && task.isNotEmpty) task,
        word,
        ?elapsed,
      ].join(', ');
      final lineText = elapsed == null
          ? '${row.title}$separator$word'
          : '${row.title}$separator$word $elapsed';
      final mark =
          !row.liveMark &&
              (row.status == KitToolStatus.running ||
                  row.status == KitToolStatus.background ||
                  row.status == KitToolStatus.pending)
          ? null
          : _stepMark(row.status);
      final Widget line;
      if (inTimeline) {
        // On a timeline the agent is a step like the others: its node on the
        // rail (the state's mark, or the agent's glyph), its words, and the
        // forward chevron when it opens its conversation.
        final box = ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: Align(
            alignment: AlignmentDirectional.topStart,
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        KitText(
                          lineText,
                          role: KitTextRole.rowTitle,
                          tone: KitTextTone.primary,
                        ),
                        if (task != null && task.isNotEmpty)
                          KitText(
                            task,
                            role: KitTextRole.secondary,
                            tone: KitTextTone.secondary,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                      ],
                    ),
                  ),
                  if (row.onOpen != null)
                    const _FirstLine(height: 22, child: _ForwardChevron()),
                ],
              ),
            ),
          ),
        );
        line = box;
      } else {
        line = ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            heightFactor: 1,
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const _Glyph(AppIconography.agent),
                  SizedBox(width: tokens.space2),
                  Expanded(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        KitText(
                          lineText,
                          role: KitTextRole.secondary,
                          tone: KitTextTone.primary,
                        ),
                        if (task != null && task.isNotEmpty)
                          KitText(
                            task,
                            role: KitTextRole.secondary,
                            tone: KitTextTone.secondary,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                      ],
                    ),
                  ),
                  if (mark != null) ...[
                    SizedBox(width: tokens.space2),
                    _FirstLine(child: mark),
                  ],
                  if (row.onOpen != null)
                    const _FirstLine(child: _ForwardChevron()),
                ],
              ),
            ),
          ),
        );
      }
      // The node goes around the whole row, outside the tap region, which
      // clips what it draws.
      Widget withNode(Widget child) => !inTimeline
          ? child
          : KitStepTimeline.node(
              context,
              lineHeight: 22,
              top: tokens.space1,
              icon: mark == null ? AppIconography.agent : null,
              mark: mark,
              child: child,
            );
      final onOpen = row.onOpen;
      if (onOpen == null) {
        return withNode(
          Semantics(
            key: row.rowKey,
            container: true,
            label: label,
            excludeSemantics: true,
            child: line,
          ),
        );
      }
      return withNode(
        Semantics(
          hint: row.openLabel ?? l10n.kitToolOpenConversation,
          child: KitTappable(
            tappableKey: row.rowKey,
            onTap: onOpen,
            label: label,
            shape: KitShape.tile,
            surface: KitSurfaceLevel.ground,
            child: line,
          ),
        ),
      );
    }

    final startedAt = row.startedAt;
    if (!running || startedAt == null) return content(null);
    // Ticks once a minute; never a live region (A11Y-3).
    return KitSince(
      since: startedAt,
      ticks: KitSinceTicks.minutes,
      builder: (context, since) =>
          content(l10n.kitToolFor(KitSince.durationWords(l10n, since.elapsed))),
    );
  }
}

/// A step's status mark (KitToolRow.md "Status marks"); none for done and
/// notRun.
Widget? _stepMark(KitToolStatus status) => switch (status) {
  KitToolStatus.running ||
  KitToolStatus.background => const KitStatusMark(state: KitMarkState.working),
  KitToolStatus.pending || KitToolStatus.waitingForYou => const KitStatusMark(
    state: KitMarkState.waiting,
  ),
  KitToolStatus.failed => const KitStatusMark(state: KitMarkState.failed),
  KitToolStatus.stopped => const KitTaskMark(state: KitTaskState.stopped),
  KitToolStatus.done || KitToolStatus.notRun => null,
};

/// The visible words beside the mark: the status word ("Waiting for you" is
/// always visible, AUTO-15), or the duration for a finished step. Running
/// shows its mark alone.
Widget? _stepWords(
  KitToolStatus status,
  String word,
  String? durationWords, {
  required int maxLines,
}) {
  Widget text(String value, KitTextTone tone, {KitTextRole? role}) => KitText(
    value,
    role: role ?? KitTextRole.secondary,
    tone: tone,
    maxLines: maxLines,
    overflow: TextOverflow.ellipsis,
  );
  final took = durationWords == null
      ? null
      : text(durationWords, KitTextTone.tertiary, role: KitTextRole.caption);
  return switch (status) {
    KitToolStatus.running => null,
    // LOOK-5 interim: the neutral failed glyph and the word in text1; no
    // danger role.
    KitToolStatus.failed => text(word, KitTextTone.primary),
    KitToolStatus.done => took,
    KitToolStatus.background ||
    KitToolStatus.pending ||
    KitToolStatus.waitingForYou ||
    KitToolStatus.stopped ||
    KitToolStatus.notRun => text(word, KitTextTone.secondary),
  };
}

/// "+4 −1" in the diff roles, laid out left to right whatever the reading
/// direction (STATE-9). The row's label reads it as "4 added, 1 removed".
class _Counts extends StatelessWidget {
  const _Counts(this.added, this.removed);

  final int? added;
  final int? removed;

  @override
  Widget build(BuildContext context) {
    final roles = KitTokens.of(context).roles;
    final added = this.added;
    final removed = this.removed;
    return KitLtr(
      child: KitText.rich(
        TextSpan(
          children: [
            if (added != null)
              TextSpan(
                text: '+$added',
                style: TextStyle(color: roles.codeAdded),
              ),
            if (added != null && removed != null) const TextSpan(text: ' '),
            if (removed != null)
              TextSpan(
                text: '−$removed',
                style: TextStyle(color: roles.codeRemoved),
              ),
          ],
        ),
        role: KitTextRole.secondary,
        tabular: true,
        maxLines: 1,
      ),
    );
  }
}

/// The opened note and body: on the transcript's gutter, no stroke and no
/// indent, whatever the row's depth.
class _Body extends StatelessWidget {
  const _Body({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        top: tokens.space1,
        bottom: tokens.space2,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) SizedBox(height: tokens.space2),
            children[i],
          ],
        ],
      ),
    );
  }
}

/// Centres a small part (glyph, mark, chevron) on the first text line, so
/// it stays beside the title when the title or the task wraps.
class _FirstLine extends StatelessWidget {
  const _FirstLine({required this.child, this.height = 20});

  final Widget child;

  /// The first line's height at 1.0 text: 20 for the secondary role, 22 for
  /// a row title.
  final double height;

  @override
  Widget build(BuildContext context) {
    // KitTextRole.secondary is 14/20: one line is 20 dp at 1.0 text.
    final lineHeight = MediaQuery.textScalerOf(context).scale(height);
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: lineHeight),
      child: Align(widthFactor: 1, heightFactor: 1, child: child),
    );
  }
}

/// The kind glyph: [KitTokens.smallIconSize] in `text2`, growing with text
/// up to [KitTokens.maxIconScale], on the first line.
class _Glyph extends StatelessWidget {
  const _Glyph(this.icon);

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final size = tokens.iconSize(context, tokens.smallIconSize).roundToDouble();
    return _FirstLine(
      child: SizedBox(
        width: size,
        child: Icon(icon, size: size, color: tokens.roles.text2),
      ),
    );
  }
}

/// The agent row's forward chevron: 20 dp, mirrored in RTL (LAY-8).
class _ForwardChevron extends StatelessWidget {
  const _ForwardChevron();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return SizedBox.square(
      dimension: tokens.smallIconSize,
      child: Icon(
        AppIconography.chevronRight,
        size: tokens.smallIconSize,
        color: tokens.roles.text2,
      ),
    );
  }
}
