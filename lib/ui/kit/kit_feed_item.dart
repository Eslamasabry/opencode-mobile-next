import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_effects.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_since.dart';
import 'kit_motion.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'chat/kit_background_work_line.dart' show KitBackgroundWorkLine;
import 'style/kit_signal.dart';

/// What a running conversation does now, for its row: since when its turn
/// runs, the shell [command] it runs (its first line), else a [line] of
/// words (the task in progress, "Running a command…").
@immutable
class KitFeedRunning {
  const KitFeedRunning({required this.since, this.command, this.line});

  final DateTime since;
  final String? command;
  final String? line;
}

/// Which state a [KitFeedItem] names in its meta line.
enum KitFeedStateTone {
  /// "Needs you": amber, the only amber on the list.
  needsYou,

  /// "Working": the accent colour.
  running,

  /// "Done": a reply finished since the person last opened it. Quiet green,
  /// news to read; "Needs you" always wins over it.
  done,

  /// "Failed", "Can't reopen": plain secondary text, never red.
  quiet,
}

/// One conversation in a feed (Chats home), two lines at most: the title on
/// one line, then one meta line in the small size. The meta line holds, in
/// order, the agent's name (only when it is not the default), the state word
/// (only when it matters), the project and the time:
/// "Needs you - shopfront - 2 h ago".
///
/// No preview, no Git mark, no pills: those live inside the conversation.
/// The whole row is one target. Its semantics read as one sentence: title,
/// agent, state, project, time. At large text the title and the meta line
/// each take a second line instead of being cut.
///
/// States: disabled.
///
/// A conversation that exists; press, hover and focus come
/// from [KitTappable], and a missing state, project or time is only its
/// content. Read-only ([onTap] null: a title remembered from last time while
/// the server opens) is the same row, untappable, at the same size.
class KitFeedItem extends StatelessWidget {
  const KitFeedItem({
    super.key,
    required this.project,
    required this.title,
    required this.onTap,
    this.agent,
    this.agentMark,
    this.state,
    this.stateTone = KitFeedStateTone.quiet,
    this.time,
    this.semanticsLabel,
    this.tappableKey,
    this.request,
    this.running,
  });

  /// The project's name; a name the server chose, so it is isolated for
  /// bidirectional text here. Empty when it is not known yet.
  final String project;

  final String title;

  /// The agent's name ('Claude Code'), first in the meta line; null for the
  /// default agent.
  final String? agent;

  /// A bundled brand mark (`assets/marks/<slug>.svg`) drawn just before
  /// [agent], the agent's logo at the meta line's text size. Null: none.
  final String? agentMark;

  /// The state word ("Needs you", "Working", "Failed"); null when nothing
  /// needs saying.
  final String? state;
  final KitFeedStateTone stateTone;

  /// The relative time ("2 h ago").
  final String? time;

  final VoidCallback? onTap;

  /// Overrides the spoken sentence; null builds it from the parts.
  final String? semanticsLabel;
  final Key? tappableKey;

  /// What the conversation asks, under its row (its request card), inset
  /// to the page gutter; outside the row's one target. Null: the row alone.
  final Widget? request;

  /// While it runs, what it does now. Under Signal the time becomes the
  /// segment timer and a line under the meta says what runs ("Running
  /// `flutter test`"); other styles keep their row.
  final KitFeedRunning? running;

  /// Whether the conversation is live: it needs the person or runs.
  bool get _live =>
      stateTone == KitFeedStateTone.needsYou ||
      stateTone == KitFeedStateTone.running;

  @override
  Widget build(BuildContext context) {
    final track = KitTrack.on(context);
    final tokens = KitTokens.of(context);
    final row = KitMaxWidth.list(
      child: track ? _buildSignal(context) : _buildContent(context),
    );
    final request = this.request;
    final Widget entry = request == null
        ? row
        : Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              row,
              Padding(
                padding: EdgeInsetsDirectional.only(
                  // Under Signal the slip hangs beside the track, on the
                  // row's words (its slot reaches into the ring's place).
                  start: track ? KitTrack.slipStart(tokens) : tokens.gutter,
                  end: tokens.gutter,
                  bottom: tokens.space3,
                ),
                child: request,
              ),
            ],
          );
    if (!track || !_live) return entry;
    // The live track runs down the whole entry, over the list's siding.
    return CustomPaint(
      painter: KitTrackPainter(
        colors: kitSignalColors(context),
        centre: KitTrack.rowCentre(tokens),
        rtl: Directionality.of(context) == TextDirection.rtl,
      ),
      child: entry,
    );
  }

  /// The row under Signal: its signal on the track (a red head while it
  /// needs you, a green one breathing while it runs, a hollow ring
  /// otherwise), the title and the time on one line, the agent, the state
  /// word in ink and the project under it. The lamp is the only colour.
  Widget _buildSignal(BuildContext context) {
    final tokens = KitTokens.of(context);
    final large = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final titleStyle = KitText.styleOf(context, KitTextRole.rowTitle);
    final firstLine = MediaQuery.textScalerOf(
      context,
    ).scale((titleStyle.fontSize ?? 16) * (titleStyle.height ?? 1.4));
    final agent = this.agent;
    final running = stateTone == KitFeedStateTone.running ? this.running : null;
    final doing = running == null ? null : _doing(context, running);
    // The line of what runs says it works: no "Working" beside it.
    final state = doing == null ? this.state : null;
    final time = this.time;
    final dot = '\u00a0\u00b7 ';
    final words =
        semanticsLabel ??
        [
          title,
          ?agent,
          ?this.state,
          if (project.isNotEmpty) project,
          if (doing != null) doing.spoken,
          ?time,
        ].join(', ');
    final meta = [
      if (agent != null) KitBidi.auto(agent),
      if (project.isNotEmpty) KitBidi.auto(project),
    ].join(dot);
    final Widget signal = switch (stateTone) {
      KitFeedStateTone.needsYou => kitSignalHead(aspect: KitSignalAspect.stop),
      KitFeedStateTone.running => kitSignalHead(
        aspect: KitSignalAspect.go,
        breathing: true,
      ),
      _ => kitSignalRing(),
    };
    final headHeight = _live ? 26.0 : 12.0;
    final body = ExcludeSemantics(
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Padding(
            padding: EdgeInsetsDirectional.only(
              start: KitTrack.rowText(tokens),
              end: tokens.gutter,
              top: tokens.space3,
              bottom: tokens.space3,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              spacing: tokens.space1,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.baseline,
                  textBaseline: TextBaseline.alphabetic,
                  children: [
                    Expanded(
                      // The concept's title: the row's size, set firmer.
                      child: KitText.rich(
                        TextSpan(
                          text: KitBidi.auto(title),
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                        role: KitTextRole.rowTitle,
                        maxLines: large ? 2 : 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (running != null) ...[
                      SizedBox(width: tokens.space3),
                      // The running time, on the title line where the
                      // done rows keep theirs.
                      KitSince(
                        since: running.since,
                        ticks: KitSinceTicks.seconds,
                        builder: (context, since) => kitSegmentTimer(
                          _elapsed(context, since.elapsed),
                          key: const ValueKey('kit-feed-timer'),
                        ),
                      ),
                    ] else if (time != null) ...[
                      SizedBox(width: tokens.space3),
                      KitText(
                        time.replaceAll(' ', '\u00a0'),
                        role: KitTextRole.secondary,
                        tone: KitTextTone.secondary,
                        tabular: true,
                        maxLines: 1,
                      ),
                    ],
                  ],
                ),
                if (state != null || meta.isNotEmpty)
                  KitText.rich(
                    TextSpan(
                      children: [
                        if (state != null) ...[
                          TextSpan(
                            text: state,
                            style: TextStyle(
                              color: tokens.roles.text1,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          if (meta.isNotEmpty) TextSpan(text: dot),
                        ],
                        TextSpan(text: meta),
                      ],
                    ),
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: large ? null : 1,
                    overflow: large ? null : TextOverflow.ellipsis,
                  ),
                if (doing != null)
                  KitText.rich(
                    doing.span,
                    key: const ValueKey('kit-feed-running'),
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: large ? 2 : 1,
                    overflow: TextOverflow.ellipsis,
                  ),
              ],
            ),
          ),
          PositionedDirectional(
            start: KitTrack.rowCentre(tokens) - 8,
            width: 16,
            top: tokens.space3 + (firstLine - headHeight) / 2,
            height: headHeight,
            child: Center(child: signal),
          ),
        ],
      ),
    );
    final onTap = this.onTap;
    if (onTap == null) {
      return Semantics(container: true, label: words, child: body);
    }
    return KitTappable(
      tappableKey: tappableKey,
      onTap: onTap,
      label: words,
      child: body,
    );
  }

  /// "3 min", "42 s": the segment timer's words.
  static String _elapsed(BuildContext context, Duration elapsed) {
    final l10n = AppLocalizations.of(context);
    return elapsed.inMinutes < 1
        ? l10n.kitTurnLiveSeconds(elapsed.isNegative ? 0 : elapsed.inSeconds)
        : KitBackgroundWorkLine.elapsedText(l10n, elapsed);
  }

  /// What runs, as the row's last line: "Running `flutter test`" with the
  /// command in mono (read left to right in any language), else the words
  /// of [KitFeedRunning.line]. Null when nothing is known.
  static ({InlineSpan span, String spoken})? _doing(
    BuildContext context,
    KitFeedRunning running,
  ) {
    final command = running.command;
    if (command != null && command.isNotEmpty) {
      final l10n = AppLocalizations.of(context);
      const mark = '\u0000';
      final words = l10n.signalRowRunningCommand(mark);
      final at = words.indexOf(mark);
      // The mono face at the line's own size, in the ink.
      final mono = TextStyle(
        fontFamily: KitText.styleFor(KitTextRole.mono).fontFamily,
        fontWeight: FontWeight.w600,
        color: KitTokens.of(context).roles.text1,
      );
      final isolated = '\u2066$command\u2069';
      return (
        span: TextSpan(
          children: [
            if (at > 0) TextSpan(text: words.substring(0, at)),
            TextSpan(text: isolated, style: mono),
            if (at >= 0 && at + mark.length < words.length)
              TextSpan(text: words.substring(at + mark.length)),
          ],
        ),
        spoken: l10n.signalRowRunningCommand(command),
      );
    }
    final line = running.line;
    if (line == null || line.isEmpty) return null;
    return (span: TextSpan(text: line), spoken: line);
  }

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final agent = this.agent;
    final state = this.state;
    final time = this.time;
    // From 1.3x text the lines take a second line, so nothing is cut.
    final large = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final base = KitText.styleOf(
      context,
      KitTextRole.secondary,
      tone: KitTextTone.secondary,
    );
    final stateColor = switch (stateTone) {
      KitFeedStateTone.needsYou => roles.attention,
      KitFeedStateTone.running => roles.accent,
      // Done is finished, not live: muted, told from Failed by its check,
      // so only Running and Needs you carry colour in the list.
      KitFeedStateTone.done || KitFeedStateTone.quiet => roles.text2,
    };
    // The space before the dot does not break, so a wrapped line never
    // starts with a lone "·": it stays with the word before it.
    final dot = '\u00a0\u00b7 ';
    final words =
        semanticsLabel ??
        [
          title,
          ?agent,
          ?state,
          if (project.isNotEmpty) project,
          ?time,
        ].join(', ');
    final meta = TextSpan(
      style: base,
      children: [
        if (agent != null && agentMark != null)
          WidgetSpan(
            alignment: PlaceholderAlignment.middle,
            child: Padding(
              padding: EdgeInsetsDirectional.only(end: tokens.space1),
              // The 20 dp mark scaled down to the meta line's text size.
              child: SizedBox.square(
                dimension: (base.fontSize ?? 13) + 2,
                child: FittedBox(
                  child: IconTheme(
                    data: IconThemeData(color: roles.text2),
                    child: KitIcon.mark(agentMark!),
                  ),
                ),
              ),
            ),
          ),
        if (agent != null) TextSpan(text: '${KitBidi.auto(agent)}$dot'),
        if (state != null) ...[
          if (stateTone == KitFeedStateTone.done)
            // The shape that tells Done from Working in a still image.
            WidgetSpan(
              alignment: PlaceholderAlignment.middle,
              child: Padding(
                padding: EdgeInsetsDirectional.only(end: tokens.space1),
                child: Icon(
                  AppIconography.check,
                  size: (base.fontSize ?? 13) + 1,
                  color: stateColor,
                ),
              ),
            ),
          if (stateTone == KitFeedStateTone.running)
            WidgetSpan(
              alignment: PlaceholderAlignment.baseline,
              baseline: TextBaseline.alphabetic,
              // A widget span is already scaled with the line's text, so the
              // word inside it is not scaled a second time (it read 4x at
              // 2x text): one size for the whole meta line.
              child: MediaQuery.withNoTextScaling(
                child: _StateSweep(
                  color: stateColor,
                  light: roles.text1,
                  child: Text(
                    state,
                    // No halo: the sweep's mask would paint it in the
                    // accent, and the accent reads on the aurora bare.
                    style: base.copyWith(
                      color: stateColor,
                      fontWeight: FontWeight.w600,
                      shadows: const [],
                    ),
                  ),
                ),
              ),
            )
          else
            TextSpan(
              text: state,
              style: TextStyle(
                color: stateColor,
                fontWeight: stateTone == KitFeedStateTone.needsYou
                    ? FontWeight.w600
                    : null,
              ),
            ),
          TextSpan(text: dot),
        ],
        if (project.isNotEmpty) TextSpan(text: KitBidi.auto(project)),
        // "2 min ago" is one piece: it never breaks between the number and
        // its unit.
        if (time != null)
          TextSpan(
            text:
                '${project.isEmpty ? '' : dot}'
                '${time.replaceAll(' ', '\u00a0')}',
          ),
      ],
    );
    final onTap = this.onTap;
    final body = ExcludeSemantics(
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: tokens.gutter,
          vertical: tokens.space3,
        ),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                spacing: tokens.space1,
                children: [
                  KitText(
                    KitBidi.auto(title),
                    role: KitTextRole.rowTitle,
                    maxLines: large ? 2 : 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  KitText.rich(
                    meta,
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: large ? null : 1,
                    overflow: large ? null : TextOverflow.ellipsis,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
    if (onTap == null) {
      return Semantics(container: true, label: words, child: body);
    }
    return KitTappable(
      tappableKey: tappableKey,
      onTap: onTap,
      label: words,
      child: body,
    );
  }
}

/// The live word's light: a soft highlight sweeping over "Working" the way
/// a running step's name does (2.2 s a pass); a still word when motion is
/// off, reduced, or loops are paused.
class _StateSweep extends StatefulWidget {
  const _StateSweep({
    required this.color,
    required this.light,
    required this.child,
  });

  final Color color;
  final Color light;
  final Widget child;

  @override
  State<_StateSweep> createState() => _StateSweepState();
}

class _StateSweepState extends State<_StateSweep>
    with SingleTickerProviderStateMixin {
  late final AnimationController _sweep = AnimationController(
    vsync: this,
    duration: KitMotion.stepSweep,
  );
  bool _moving = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _moving =
        KitMotion.loops &&
        !KitMotion.reduced(context) &&
        KitEffects.of(context).motion != KitMotionLevel.off;
    if (!_moving) {
      _sweep.stop();
    } else if (!_sweep.isAnimating) {
      _sweep.repeat();
    }
  }

  @override
  void dispose() {
    _sweep.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!_moving) return widget.child;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return AnimatedBuilder(
      animation: _sweep,
      child: widget.child,
      builder: (context, child) {
        final t = _sweep.value;
        final centre = -0.3 + 1.6 * (rtl ? 1 - t : t);
        return ShaderMask(
          key: const ValueKey('kit-feed-state-sweep'),
          blendMode: BlendMode.srcIn,
          shaderCallback: (bounds) => LinearGradient(
            colors: [widget.color, widget.light, widget.color],
            stops: [
              (centre - 0.18).clamp(0.0, 1.0),
              centre.clamp(0.0, 1.0),
              (centre + 0.18).clamp(0.0, 1.0),
            ],
          ).createShader(bounds),
          child: child,
        );
      },
    );
  }
}
