// KitStepTimeline: a turn's work as one quiet line that opens in place to a
// timeline (owner request, 2026-10-09: "do something similar to the Claude
// app's grouping sheet, but without bottom sheets"). Closed it is an icon,
// plain words and a chevron, no frame; open, the steps hang off a thin rail
// under it, in the order they happened: a small grey dot for a sentence, a
// small tile with the step's glyph for a tool call, the live mark for the
// step in progress, and a compact preview of the change a file write or edit
// made. docs/ux-system/kit-api/KitStepTimeline.md.
//
// The steps are the host's widgets ([KitToolRow], [KitMessage.thought], any
// other). Each draws its own node on the rail through
// [KitStepTimeline.node], which does nothing outside a timeline, so the same
// rows keep their plain look anywhere else. A step that draws no node (plain
// text) still sits beside the rail; only its node is missing.
//
// No glass, no shadow, no size animation (MOT-5): the opened steps appear at
// once and the host may fade them in. The rail mirrors under Arabic.
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_diff_view.dart';
import '../kit_surface.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../motion/kit_motion_parts.dart';

/// A few lines of what a file write or edit changed, for the timeline's
/// preview card: at most [maxLines] lines with their numbers, and whether
/// more follow (the card then fades its last line). Built lazily by the host
/// from the call's own text; the kit never sees lib/api types.
@immutable
class KitStepPreview {
  const KitStepPreview(this.lines, {this.more = false});

  /// A unified patch: its first change, with one line of context before it
  /// (hunk headers and the file preamble are skipped).
  factory KitStepPreview.fromPatch(String patch, {int maxLines = maxShown}) {
    final lines = <KitDiffLine>[];
    KitDiffLine? lead;
    var changed = false;
    var inHunk = false;
    var oldNo = 0;
    var newNo = 0;
    var more = false;
    var at = 0;
    while (at <= patch.length) {
      var end = patch.indexOf('\n', at);
      if (end < 0) end = patch.length;
      final line = patch.substring(at, end);
      at = end + 1;
      if (line.startsWith('@@')) {
        final match = _hunkHeader.firstMatch(line);
        if (match != null) {
          oldNo = int.parse(match.group(1)!);
          newNo = int.parse(match.group(2)!);
        }
        inHunk = true;
        continue;
      }
      if (!inHunk || line.startsWith('\\')) continue;
      KitDiffLine next;
      if (line.startsWith('+')) {
        next = KitDiffLine(
          line.substring(1),
          KitDiffLineKind.added,
          newNo: newNo++,
        );
        changed = true;
      } else if (line.startsWith('-')) {
        next = KitDiffLine(
          line.substring(1),
          KitDiffLineKind.removed,
          oldNo: oldNo++,
        );
        changed = true;
      } else {
        if (line.isEmpty) continue;
        next = KitDiffLine(
          line.startsWith(' ') ? line.substring(1) : line,
          KitDiffLineKind.context,
          oldNo: oldNo++,
          newNo: newNo++,
        );
        if (!changed) {
          lead = next;
          continue;
        }
      }
      if (lead != null && lines.isEmpty) lines.add(lead);
      if (lines.length >= maxLines) {
        more = true;
        break;
      }
      lines.add(next);
    }
    return KitStepPreview(lines, more: more);
  }

  /// A whole new file: every line added, numbered from 1.
  factory KitStepPreview.fromText(String text, {int maxLines = maxShown}) {
    final lines = <KitDiffLine>[];
    var more = false;
    var at = 0;
    var number = 1;
    while (at < text.length) {
      var end = text.indexOf('\n', at);
      if (end < 0) end = text.length;
      if (lines.length >= maxLines) {
        more = true;
        break;
      }
      lines.add(
        KitDiffLine(
          text.substring(at, end),
          KitDiffLineKind.added,
          newNo: number++,
        ),
      );
      at = end + 1;
    }
    return KitStepPreview(lines, more: more);
  }

  /// A before and after pair with no file around it (an edit that names the
  /// old and the new text): the changed lines with a sign and no number,
  /// since the call does not say where in the file they sit.
  factory KitStepPreview.fromTexts({
    String? before,
    String? after,
    int maxLines = maxShown,
  }) {
    final file = KitDiffFile.fromTexts('', before: before, after: after);
    final all = <KitDiffLine>[
      for (final segment in file.segments)
        if (segment is KitDiffLine && segment.kind != KitDiffLineKind.hunk)
          KitDiffLine(segment.text, segment.kind),
    ];
    final firstChange = all.indexWhere(
      (line) => line.kind != KitDiffLineKind.context,
    );
    final from = firstChange > 0 ? firstChange - 1 : 0;
    final rest = all.sublist(from);
    return KitStepPreview(
      rest.take(maxLines).toList(),
      more: rest.length > maxLines,
    );
  }

  /// The most lines a card shows.
  static const int maxShown = 6;

  final List<KitDiffLine> lines;

  /// More lines follow the last one shown.
  final bool more;

  bool get isEmpty => lines.isEmpty;

  static final _hunkHeader = RegExp(r'^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@');
}

/// A turn's work as one quiet line that opens in place to a timeline.
///
/// The line is [icon], [label] and a chevron (or the live [mark] in the
/// icon's place while the work runs); tapping it calls [onPressed]. Open
/// ([expanded]), [steps] hang off the rail under it, [head] first (the
/// "Show earlier steps" row). The host decides everything else: the words,
/// the glyph, the state, which steps to build, the fade ([fade]).
///
/// States: none — it lays out the line and the steps it is given.
class KitStepTimeline extends StatelessWidget {
  const KitStepTimeline({
    super.key,
    required this.label,
    required this.icon,
    required this.expanded,
    required this.onPressed,
    required this.steps,
    this.spoken,
    this.mark,
    this.head,
    this.fade,
    this.lineKey,
    this.stepsKey,
  });

  /// The line's words ("Read 3 files · ran 8 commands").
  final String label;

  /// The group's glyph, in `text2`.
  final IconData icon;

  /// Whether the steps are shown.
  final bool expanded;

  /// The line was tapped (open or close).
  final VoidCallback onPressed;

  /// The steps, oldest first: [KitToolRow], [KitMessage.thought], any widget.
  final List<Widget> steps;

  /// What a screen reader says for the line; null reads [label].
  final String? spoken;

  /// In the glyph's place: the live mark while the work runs, or the failed
  /// mark when it ended on a failure. It is decoration here; its word is in
  /// [spoken].
  final Widget? mark;

  /// A row above the first step, outside the steps ("Show 12 earlier steps").
  final Widget? head;

  /// Paints the opened steps in (paint only; no size change).
  final Animation<double>? fade;

  /// On the line's tap target (today's `Key('work-group-header')`).
  final Key? lineKey;

  /// On the opened steps (today's `Key('work-group-steps')`).
  final Key? stepsKey;

  /// Whether [context] sits inside a timeline's rail.
  static bool inside(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_RailScope>() != null;

  /// [child] with its node on the rail: a small tile with [icon], the state
  /// [mark] centred in the node's place, or (neither) a quiet dot. Outside a
  /// timeline it is [child] unchanged.
  ///
  /// [lineHeight] is the height of the first line of [child] at 1.0 text
  /// scale (22 for a row title, 20 for a sentence) and [top] is where that
  /// line starts inside [child]; the node centres on it.
  static Widget node(
    BuildContext context, {
    required Widget child,
    double lineHeight = 22,
    double top = 0,
    IconData? icon,
    Widget? mark,
  }) {
    if (!inside(context)) return child;
    return _StepNode(
      lineHeight: lineHeight,
      top: top,
      icon: icon,
      mark: mark,
      child: child,
    );
  }

  /// A sentence between steps (prose the agent said while it worked) with a
  /// dot on the rail. Outside a timeline it is [child] unchanged.
  static Widget sentence(BuildContext context, {required Widget child}) {
    if (!inside(context)) return child;
    final tokens = KitTokens.of(context);
    return _StepNode(
      lineHeight: 24,
      top: tokens.space1,
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
        child: child,
      ),
    );
  }

  /// The compact preview card of a file write or edit: [preview]'s lines in
  /// mono with their numbers, fading when more follow; a tap calls [onOpen]
  /// (the existing full view). [label] names the file for a screen reader.
  static Widget previewCard(
    BuildContext context, {
    required KitStepPreview preview,
    required VoidCallback onOpen,
    required String label,
    Key? key,
  }) => _PreviewCard(key: key, preview: preview, onOpen: onOpen, label: label);

  /// The glyph to lead a work line with, by what the work mostly did.
  /// [read], [searched]…: the host's counts.
  static IconData iconFor({
    int read = 0,
    int searched = 0,
    int listed = 0,
    int edited = 0,
    int ran = 0,
    int fetched = 0,
    int delegated = 0,
    int other = 0,
  }) {
    final counts = <(int, IconData)>[
      (edited, AppIconography.editNote),
      (ran, AppIconography.terminal),
      (fetched, AppIconography.globe),
      (delegated, AppIconography.agent),
      (searched, AppIconography.search),
      (read, AppIconography.fileText),
      (listed, AppIconography.folderOpen),
      (other, AppIconography.tools),
    ];
    var best = counts.first;
    for (final entry in counts) {
      if (entry.$1 > best.$1) best = entry;
    }
    return best.$1 == 0 ? AppIconography.tools : best.$2;
  }

  @override
  Widget build(BuildContext context) {
    final hasSteps = steps.isNotEmpty || head != null;
    final line = _StepLine(
      label: label,
      icon: icon,
      mark: mark,
      expanded: expanded,
      railBelow: expanded && hasSteps,
      onPressed: onPressed,
      spoken: spoken ?? label,
      lineKey: lineKey,
    );
    if (!expanded || !hasSteps) return line;
    final count = steps.length + (head == null ? 0 : 1);
    Widget rail = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (head != null) _RailSlot(last: count == 1, child: head!),
        for (var i = 0; i < steps.length; i++)
          _RailSlot(last: i == steps.length - 1, child: steps[i]),
      ],
    );
    if (fade != null) rail = FadeTransition(opacity: fade!, child: rail);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        line,
        KeyedSubtree(key: stepsKey, child: rail),
      ],
    );
  }
}

// ── Geometry ──────────────────────────────────────────────────────────────

/// The rail's measures, all from the kit's tokens and the person's text size:
/// the node column is one glyph wide plus a gap, the rail runs down the
/// middle of the glyph, and the text of the line and of every step starts
/// where the column ends.
class _Rail {
  _Rail(BuildContext context)
    : tokens = KitTokens.of(context),
      scaler = MediaQuery.textScalerOf(context),
      dpr = MediaQuery.devicePixelRatioOf(context) {
    glyph = tokens.iconSize(context, tokens.smallIconSize).roundToDouble();
  }

  final KitTokens tokens;
  final TextScaler scaler;
  final double dpr;
  late final double glyph;

  /// Where the words start.
  double get column => glyph + tokens.space2;

  /// The rail's distance from the start edge.
  double get centre => glyph / 2;

  /// Two physical pixels, pixel-aligned.
  double get stroke => dpr > 0 ? 2 / dpr : 1;

  /// The rail's colour: `text3` at half strength, over the ground or any
  /// surface. The hairline role is too faint for a line the eye follows.
  Color get color => tokens.roles.text3.withValues(alpha: .5);

  /// A first line's height at the person's text size.
  double line(double height) => scaler.scale(height);

  /// Where the rail ends in the last slot: the middle of a title line, under
  /// the row's own padding (the node sits there).
  double get lastEnd => tokens.space1 + line(22) / 2;
}

double _snap(double value, double dpr) =>
    dpr > 0 ? (value * dpr).roundToDouble() / dpr : value;

class _RailPainter extends CustomPainter {
  _RailPainter({
    required this.color,
    required this.stroke,
    required this.centre,
    required this.dpr,
    required this.rtl,
    this.top = 0,
    this.bottom,
  });

  final Color color;
  final double stroke;
  final double centre;
  final double dpr;
  final bool rtl;

  /// Where the stroke starts; the slot's top by default.
  final double top;

  /// Where it ends; the bottom by default.
  final double? bottom;

  @override
  void paint(Canvas canvas, Size size) {
    final end = math.min(bottom ?? size.height, size.height);
    if (end <= top) return;
    final x = rtl ? size.width - centre : centre;
    final left = _snap(x - stroke / 2, dpr);
    canvas.drawRect(
      Rect.fromLTRB(left, top, left + stroke, end),
      Paint()..color = color,
    );
  }

  @override
  bool shouldRepaint(_RailPainter old) =>
      old.color != color ||
      old.stroke != stroke ||
      old.centre != centre ||
      old.dpr != dpr ||
      old.rtl != rtl ||
      old.top != top ||
      old.bottom != bottom;
}

/// Marks the subtree as sitting on a rail (what [KitStepTimeline.inside]
/// reads). It carries nothing: the measures come from the tokens.
class _RailScope extends InheritedWidget {
  const _RailScope({required super.child});

  @override
  bool updateShouldNotify(_RailScope old) => false;
}

/// One step's place on the rail: the rail's stroke behind it and the node
/// column kept free at the start. Every slot but the last runs to its bottom
/// so the strokes join; the last ends at its node.
class _RailSlot extends StatelessWidget {
  const _RailSlot({required this.last, required this.child});

  final bool last;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final rail = _Rail(context);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return _RailScope(
      child: CustomPaint(
        painter: _RailPainter(
          color: rail.color,
          stroke: rail.stroke,
          centre: rail.centre,
          dpr: rail.dpr,
          rtl: rtl,
          bottom: last ? rail.lastEnd : null,
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.only(start: rail.column),
          child: child,
        ),
      ),
    );
  }
}

// ── The line ──────────────────────────────────────────────────────────────

class _StepLine extends StatelessWidget {
  const _StepLine({
    required this.label,
    required this.icon,
    required this.mark,
    required this.expanded,
    required this.railBelow,
    required this.onPressed,
    required this.spoken,
    required this.lineKey,
  });

  final String label;
  final IconData icon;
  final Widget? mark;
  final bool expanded;

  /// The rail starts under the glyph (the steps are shown).
  final bool railBelow;
  final VoidCallback onPressed;
  final String spoken;
  final Key? lineKey;

  @override
  Widget build(BuildContext context) {
    final rail = _Rail(context);
    final tokens = rail.tokens;
    final roles = tokens.roles;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final first = rail.line(20);
    final wide = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    // The glyph sits in the middle of the 48 dp target whatever the words
    // do; the target grows downward when they wrap.
    final pad = math.max(tokens.space1, (tokens.minTarget - first) / 2);

    final Widget lead = SizedBox(
      width: rail.glyph,
      height: first,
      child: Center(
        child: mark ?? Icon(icon, size: rail.glyph, color: roles.text2),
      ),
    );

    final Widget content = Padding(
      padding: EdgeInsetsDirectional.symmetric(vertical: pad),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          ExcludeSemantics(child: lead),
          SizedBox(width: tokens.space2),
          Flexible(
            child: KitText(
              label,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
              // Two lines, then the end is cut; from 1.3x text the words
              // wrap in full (A11Y-8).
              maxLines: wide ? null : 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          SizedBox(width: tokens.space1),
          ExcludeSemantics(
            child: SizedBox(
              height: first,
              child: Center(
                child: SizedBox.square(
                  dimension: tokens.smallIconSize,
                  child: KitSpin.chevron(expanded: expanded),
                ),
              ),
            ),
          ),
        ],
      ),
    );

    final Widget tappable = Align(
      alignment: AlignmentDirectional.centerStart,
      child: Semantics(
        container: true,
        button: true,
        expanded: expanded,
        label: spoken,
        onTap: onPressed,
        excludeSemantics: true,
        child: KitTappable(
          tappableKey: lineKey,
          onTap: onPressed,
          label: spoken,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: content,
        ),
      ),
    );

    if (!railBelow) return tappable;
    // The rail leaves the glyph and runs on into the first step.
    final startY = pad + first / 2 + rail.glyph / 2 + tokens.space1;
    return CustomPaint(
      painter: _RailPainter(
        color: rail.color,
        stroke: rail.stroke,
        centre: rail.centre,
        dpr: rail.dpr,
        rtl: rtl,
        top: startY,
      ),
      child: tappable,
    );
  }
}

// ── Nodes ─────────────────────────────────────────────────────────────────

/// Draws a node in the free column at the start of its slot, beside the
/// first line of [child]. The node sits outside [child]'s own box (it is
/// decoration: no semantics, no taps), so [child] keeps its full width.
class _StepNode extends StatelessWidget {
  const _StepNode({
    required this.child,
    required this.lineHeight,
    required this.top,
    this.icon,
    this.mark,
  });

  final Widget child;
  final double lineHeight;
  final double top;
  final IconData? icon;
  final Widget? mark;

  @override
  Widget build(BuildContext context) {
    final rail = _Rail(context);
    final roles = rail.tokens.roles;
    final Widget node;
    if (mark != null) {
      node = mark!;
    } else if (icon != null) {
      final size = rail.glyph;
      node = SizedBox.square(
        dimension: size,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: roles.surface3,
            borderRadius: BorderRadius.circular(size * .3),
          ),
          child: Icon(icon, size: size * .7, color: roles.text2),
        ),
      );
    } else {
      node = SizedBox.square(
        dimension: _dot,
        child: DecoratedBox(
          decoration: BoxDecoration(color: roles.text3, shape: BoxShape.circle),
        ),
      );
    }
    return Stack(
      clipBehavior: Clip.none,
      fit: StackFit.passthrough,
      children: [
        child,
        PositionedDirectional(
          start: -rail.column,
          top: top,
          width: rail.glyph,
          height: rail.line(lineHeight),
          child: ExcludeSemantics(child: Center(child: node)),
        ),
      ],
    );
  }

  static const double _dot = 6;
}

// ── The preview card ──────────────────────────────────────────────────────

class _PreviewCard extends StatelessWidget {
  const _PreviewCard({
    super.key,
    required this.preview,
    required this.onOpen,
    required this.label,
  });

  final KitStepPreview preview;
  final VoidCallback onOpen;
  final String label;

  static String _gutter(KitDiffLine line) => switch (line.kind) {
    KitDiffLineKind.added => '+${line.newNo ?? ''}',
    KitDiffLineKind.removed => '−${line.oldNo ?? ''}',
    _ => '${line.newNo ?? line.oldNo ?? ''}',
  };

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final scale = MediaQuery.textScalerOf(context).scale(1);
    final gutters = [for (final line in preview.lines) _gutter(line)];
    final width = gutters.fold<int>(0, (a, g) => math.max(a, g.length));
    // One mono digit is about 0.62 em of the 13 dp mono role.
    final gutterWidth = (width * 8.1 * scale).ceilToDouble();

    final rows = <Widget>[
      for (var i = 0; i < preview.lines.length; i++)
        _PreviewRow(
          line: preview.lines[i],
          gutter: gutters[i],
          gutterWidth: gutterWidth,
        ),
    ];
    Widget body = KitLtr(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: rows,
      ),
    );
    if (preview.more) {
      // The last lines melt into the card: the preview says there is more
      // without a word (the tap opens it).
      body = ShaderMask(
        blendMode: BlendMode.dstIn,
        shaderCallback: (bounds) => LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          // Only the alpha matters to the mask.
          colors: [roles.text1, roles.text1, roles.text1.withValues(alpha: 0)],
          stops: const [0, .45, 1],
        ).createShader(bounds),
        child: body,
      );
    }
    return Padding(
      padding: EdgeInsetsDirectional.only(
        top: tokens.space1,
        bottom: tokens.space1,
      ),
      child: Semantics(
        container: true,
        button: true,
        label: label,
        hint: l10n.kitToolOpenDetails,
        onTap: onOpen,
        excludeSemantics: true,
        child: KitTappable(
          onTap: onOpen,
          label: label,
          shape: KitShape.code,
          surface: KitSurfaceLevel.surface1,
          child: KitSurface(
            level: KitSurfaceLevel.surface1,
            shape: KitShape.code,
            padding: KitSurfacePadding.none,
            child: Padding(
              padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space2),
              child: body,
            ),
          ),
        ),
      ),
    );
  }
}

class _PreviewRow extends StatelessWidget {
  const _PreviewRow({
    required this.line,
    required this.gutter,
    required this.gutterWidth,
  });

  final KitDiffLine line;
  final String gutter;
  final double gutterWidth;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final (Color? tint, KitTextTone numberTone) = switch (line.kind) {
      KitDiffLineKind.added => (roles.codeAddedSurface, KitTextTone.success),
      KitDiffLineKind.removed => (roles.codeRemovedSurface, KitTextTone.danger),
      _ => (null, KitTextTone.tertiary),
    };
    final row = Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: gutterWidth,
            child: KitText(
              gutter,
              role: KitTextRole.mono,
              tone: numberTone,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.clip,
              textAlign: TextAlign.end,
            ),
          ),
          SizedBox(width: tokens.space2),
          Expanded(
            child: KitText(
              line.text,
              role: KitTextRole.mono,
              tone: KitTextTone.primary,
              maxLines: 1,
              softWrap: false,
              overflow: TextOverflow.clip,
            ),
          ),
        ],
      ),
    );
    return tint == null ? row : ColoredBox(color: tint, child: row);
  }
}
