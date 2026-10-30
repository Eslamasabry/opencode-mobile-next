// KitProgressRow (docs/ux-system/kit-api/KitProgressRow.md): a row holding a
// measured amount — a quota window, one item downloading, the context used,
// a budget. A title, a determinate bar, the amount in words and, when the
// data is old, its age. [KitProgressRow.segments] shows a stacked bar with a
// worded legend, for example what fills a conversation's context.
import 'dart:ui' show lerpDouble;

import 'package:flutter/foundation.dart' show listEquals;
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../../l10n/app_localizations_en.dart';
import '../app_theme.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_row_parts.dart' show KitChevron;
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_time.dart';
import 'kit_tokens.dart';

/// One named share of a [KitProgressRow.segments] stacked bar.
class KitProgressSegment {
  const KitProgressSegment({
    required this.label,
    required this.value,
    this.valueLabel,
  });

  /// "Conversation".
  final String label;

  /// Share of the whole, 0..1; the segments sum to <= 1 (asserted in
  /// debug, scaled to fit in release — KitProgressRow.md "Data safety").
  final double value;

  /// "41k tokens".
  final String? valueLabel;
}

/// One slice actually drawn and listed: either a caller [KitProgressSegment]
/// unchanged, or the folded "Other" bucket (KitProgressRow.md "Purpose": at
/// most 4 named segments, the rest summed into one).
class _Slice {
  const _Slice({required this.label, required this.value, this.valueLabel});
  final String label;
  final double value;
  final String? valueLabel;
}

/// A row holding a measured amount (KitProgressRow.md), built from
/// `KitProgress`'s bar tokens: a quota window, one item downloading, the
/// context used, a budget. The row has a title, a determinate bar, the
/// amount in words and, when the data is old, its age.
///
/// States: loading (`value == null`), loaded (under 80 %), near limit
/// (80–99 %, automatic), at limit (100 %, automatic: bar and word in the
/// strong neutral), stale (`asOf` set),
/// segments, empty. Error and disabled are not in this part: the host
/// carries an error on a `KitNotice` on its section, and a `null` [onTap]
/// is simply not tappable.
class KitProgressRow extends StatelessWidget {
  const KitProgressRow({
    super.key,
    required this.title,
    required this.value,
    this.valueLabel,
    this.leading,
    this.tone,
    this.asOf,
    this.onTap,
    this.titleKey,
    this.valueKey,
  }) : assert(
         tone != AppStatusTone.attention,
         'KitProgressRow: tone must not be attention — STANDARDS LOOK-4 '
         'reserves it for "needs you"; near and at the limit are carried by '
         'words, not colour.',
       ),
       segments = const [],
       _isSegments = false;

  /// A stacked bar with a worded legend under it: what fills a conversation's
  /// context, a budget by provider. At most 4 named segments; the rest are
  /// summed into one "Other" segment.
  const KitProgressRow.segments({
    super.key,
    required this.title,
    required this.segments,
    this.valueLabel,
    this.leading,
    this.asOf,
    this.onTap,
    this.titleKey,
    this.valueKey,
  }) : value = null,
       tone = null,
       _isSegments = true;

  /// "Claude · 5-hour window".
  final String title;

  /// 0..1 (clamped for drawing); null while unknown: a skeleton bar, never
  /// a spinner.
  final double? value;

  /// "62 % · resets in 3 h".
  final String? valueLabel;
  final Widget? leading;

  /// Null is automatic (near/at-limit words from [value]); never
  /// [AppStatusTone.attention] (asserted).
  final AppStatusTone? tone;

  /// Last-known data: shown as "as of 10:42" (offline or stale). A row
  /// without it must be live data — that is the caller's contract.
  final DateTime? asOf;

  /// Opens the detail; adds a [KitChevron].
  final VoidCallback? onTap;
  final List<KitProgressSegment> segments;
  final Key? titleKey;

  /// The key of the value/legend text, for tests.
  final Key? valueKey;

  /// True for [KitProgressRow.segments], where [segments] carries the bar
  /// instead of [value].
  final bool _isSegments;

  bool get _tappable => onTap != null;
  bool get _automatic => !_isSegments && tone == null;
  bool get _nearLimit =>
      _automatic && value != null && value! >= 0.8 && value! < 1.0;
  bool get _atLimit => _automatic && value != null && value! >= 1.0;

  /// At most 4 named segments, then one "Other" folding in the rest.
  List<_Slice> _slices(AppLocalizations l10n) {
    final head = segments.length <= 4 ? segments : segments.take(4);
    final slices = [
      for (final s in head)
        _Slice(label: s.label, value: s.value, valueLabel: s.valueLabel),
    ];
    if (segments.length > 4) {
      final other = segments.skip(4).fold<double>(0, (sum, s) => sum + s.value);
      slices.add(_Slice(label: l10n.kitProgressRowOther, value: other));
    }
    return slices;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final theme = Theme.of(context);
    final roles = AppTheme.rolesOf(theme);
    final tokens = KitTokens.of(context);
    final l10n = _kitProgressWords(context);
    final reduceMotion = KitMotion.reduced(context);
    final isLoading = !_isSegments && value == null;

    final slices = _isSegments ? _slices(l10n) : const <_Slice>[];
    final rawSum = slices.fold<double>(0, (sum, s) => sum + s.value);
    assert(
      rawSum <= 1 + 1e-9,
      'KitProgressRow.segments: segments sum to $rawSum, must be at most 1 '
      '(KitProgressRow.md "Data safety and honest state")',
    );
    final scale = rawSum > 1 ? 1 / rawSum : 1.0;

    Color colorFor(int index) =>
        index < 4 ? tokens.segmentFills[index] : roles.surface3;

    final wordsSpans = isLoading
        ? const <InlineSpan>[]
        : _wordsSpans(context, l10n, roles);
    final topLine = wordsSpans.isEmpty
        ? null
        : KitText.rich(
            TextSpan(children: wordsSpans),
            key: valueKey,
            role: KitTextRole.secondary,
            tabular: true,
          );

    // One paint-only renderer for every state (MOT-5): the loading
    // skeleton is the track with no fill, a scalar bar one fill, a stacked
    // bar one fill per slice.
    final List<_BarFill> fills;
    if (isLoading) {
      fills = const [];
    } else if (_isSegments) {
      fills = [
        for (var i = 0; i < slices.length; i++)
          _BarFill(
            fraction: (slices[i].value * scale).clamp(0.0, 1.0),
            color: colorFor(i),
            edged: colorFor(i) == roles.surface3,
          ),
      ];
    } else {
      final color = switch (tone) {
        AppStatusTone.neutral => roles.text3,
        AppStatusTone.ok => roles.success,
        // A full or exceeded limit is a failure: the strong neutral, never
        // amber (that is "needs you" only) and never the text colour.
        AppStatusTone.failure => roles.text1,
        AppStatusTone.progress => roles.accent,
        _ => _atLimit ? roles.text1 : roles.accent,
      };
      fills = [
        _BarFill(
          fraction: (value ?? 0).clamp(0.0, 1.0),
          color: color,
          edged: false,
        ),
      ];
    }
    // A stacked bar's 4th segment and "Other" fill in `surface3`, so its
    // unfilled track is a role no fill uses: the panel's own `surface1`
    // inside a 1 physical px `hairline` outline (R8). 76 % used never reads
    // as the first three fills' 62 %.
    final bar = _AnimatedBar(
      fills: fills,
      track: _isSegments ? roles.surface1 : roles.surface3,
      hairline: roles.hairline,
      outlined: _isSegments,
      reduceMotion: reduceMotion,
    );

    final line = topLine == null
        ? null
        : _CrossFadeLine(
            reduceMotion: reduceMotion,
            contentKey: ValueKey((_nearLimit, _atLimit)),
            child: topLine,
          );
    // KitProgressRow.md "Adaptive": compact and medium always stack the
    // value line under the title; only an expanded or large window may
    // move it to the title's line, and only when both fit whole.
    final mayTrail =
        !_isSegments && line != null && KitLayout.windowOf(context).isWide;
    final words = LayoutBuilder(
      builder: (context, constraints) {
        final trailingFits =
            mayTrail &&
            _fitsTrailing(context, constraints.maxWidth, wordsSpans, tokens);
        final titleText = KitText(
          title,
          key: titleKey,
          role: KitTextRole.rowTitle,
          maxLines: trailingFits ? 1 : 2,
          overflow: TextOverflow.ellipsis,
        );
        if (trailingFits) {
          return Row(
            children: [
              Expanded(child: titleText),
              SizedBox(width: tokens.space2),
              line,
            ],
          );
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            titleText,
            if (line != null) ...[SizedBox(height: tokens.space1), line],
          ],
        );
      },
    );

    final content = Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space2,
        _tappable ? tokens.space1 : tokens.gutter,
        tokens.space2,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              if (leading case final leading?) ...[
                leading,
                SizedBox(width: tokens.space3),
              ],
              Expanded(child: words),
              if (_tappable) const KitChevron(),
            ],
          ),
          SizedBox(height: tokens.space2),
          bar,
          if (_isSegments) ...[
            SizedBox(height: tokens.space2),
            _Legend(
              slices: slices,
              colorFor: colorFor,
              tokens: tokens,
              roles: roles,
            ),
          ],
        ],
      ),
    );

    // Flat lists (Solid): a row on the page like KitRow, no box of its own.
    final flat = KitSurfaceLook.flatLists(context);
    final corners = BorderRadius.circular(flat ? 0 : tokens.panelCornerRadius);
    final panel = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.rowHeightTwoLine),
      child: kitSurfaceBox(
        style: KitSurfaceLook.styleOf(context),
        decoration: flat
            ? KitSurfaceLook.bare
            : KitSurfaceLook.decorate(
                context,
                solid: BoxDecoration(
                  color: roles.surface1,
                  borderRadius: corners,
                ),
              ),
        child: _tappable
            ? InkWell(onTap: onTap, borderRadius: corners, child: content)
            : content,
      ),
    );

    return Semantics(
      container: true,
      button: _tappable,
      onTap: onTap,
      label: _mergedLabel(context, l10n, isLoading, slices),
      child: ExcludeSemantics(child: panel),
    );
  }

  /// KitStatusLine's `_stacks` measurement, applied to the title and the
  /// whole value line (value label, limit word and as-of, each in its own
  /// style) instead: whether both fit whole in [width], the space between
  /// the leading and the chevron (KitProgressRow.md "Adaptive"). Never at
  /// 1.3x text and above (Accessibility: the trailing layout falls back to
  /// stacked).
  bool _fitsTrailing(
    BuildContext context,
    double width,
    List<InlineSpan> wordsSpans,
    KitTokens tokens,
  ) {
    final scaler = MediaQuery.textScalerOf(context);
    if (scaler.scale(100) / 100 >= 1.3) return false;
    double measure(InlineSpan span) {
      final painter = TextPainter(
        text: span,
        textDirection: Directionality.of(context),
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final result = painter.width;
      painter.dispose();
      return result;
    }

    const tabular = [FontFeature.tabularFigures()];
    final titleWidth = measure(
      TextSpan(
        text: title,
        style: KitText.styleOf(context, KitTextRole.rowTitle),
      ),
    );
    final lineWidth = measure(
      TextSpan(
        style: KitText.styleOf(
          context,
          KitTextRole.secondary,
        ).copyWith(fontFeatures: tabular),
        children: wordsSpans,
      ),
    );
    return titleWidth + tokens.space2 + lineWidth <= width;
  }

  List<InlineSpan> _wordsSpans(
    BuildContext context,
    AppLocalizations l10n,
    ThemeRoles roles,
  ) {
    final words = <(String, TextStyle?)>[];
    if (valueLabel case final v? when v.isNotEmpty) words.add((v, null));
    if (_nearLimit) {
      words.add((
        l10n.kitProgressRowNearLimit,
        KitText.styleFor(KitTextRole.label).copyWith(color: roles.text1),
      ));
    }
    if (_atLimit) {
      words.add((
        l10n.kitProgressRowAtLimit,
        KitText.styleFor(KitTextRole.label).copyWith(color: roles.text1),
      ));
    }
    if (asOf case final at?) {
      words.add((
        l10n.kitProgressRowAsOf(_formatAsOf(context, at)),
        KitText.styleFor(KitTextRole.caption).copyWith(color: roles.text3),
      ));
    }
    return [
      for (var i = 0; i < words.length; i++)
        TextSpan(text: (i == 0 ? '' : ' · ') + words[i].$1, style: words[i].$2),
    ];
  }

  String _formatAsOf(BuildContext context, DateTime at) =>
      KitTime.clock(context, at);

  /// The one merged semantics node (KitProgressRow.md "Accessibility"):
  /// "{title}, {percent} percent, {valueLabel}[, near limit][, as of
  /// 10:42]"; for segments: "{title}, {valueLabel}; {label} {valueLabel};
  /// …".
  String _mergedLabel(
    BuildContext context,
    AppLocalizations l10n,
    bool isLoading,
    List<_Slice> slices,
  ) {
    if (isLoading) return '$title, ${l10n.kitProgressRowLoading}';
    if (_isSegments) {
      final parts = <String>[
        if (valueLabel case final v? when v.isNotEmpty) '$title, $v' else title,
        for (final s in slices) [s.label, ?s.valueLabel].join(' '),
      ];
      if (asOf case final at?) {
        parts.add(l10n.kitProgressRowAsOf(_formatAsOf(context, at)));
      }
      return parts.join('; ');
    }
    final percent = ((value ?? 0) * 100).round();
    final parts = <String>[
      title,
      l10n.kitProgressRowPercent(percent),
      if (valueLabel case final v? when v.isNotEmpty) v,
      if (_nearLimit) l10n.kitProgressRowNearLimit,
      if (_atLimit) l10n.kitProgressRowAtLimit,
      if (asOf case final at?)
        l10n.kitProgressRowAsOf(_formatAsOf(context, at)),
    ];
    return parts.join(', ');
  }
}

/// Cross-fades [child] on [KitMotion.quick] when [contentKey] changes (a
/// newly crossed limit word — KitProgressRow.md "Motion and haptics").
/// Instant under reduced motion.
class _CrossFadeLine extends StatelessWidget {
  const _CrossFadeLine({
    required this.reduceMotion,
    required this.contentKey,
    required this.child,
  });

  final bool reduceMotion;
  final Key contentKey;
  final Widget child;

  @override
  Widget build(BuildContext context) => AnimatedSwitcher(
    duration: reduceMotion ? Duration.zero : KitMotion.quick,
    switchInCurve: KitMotion.enter,
    switchOutCurve: KitMotion.exit,
    child: KeyedSubtree(key: contentKey, child: child),
  );
}

/// One fill of a bar: a share of the track, from the start, in [color].
/// An [edged] fill (`surface3`, the 4th named segment or "Other") carries
/// a 1 physical px hairline on the edges it shares with the rest of the bar,
/// so its end is marked against the unfilled track (`_new-tokens.md`,
/// STATE-9/18: the bar never reads short).
@immutable
class _BarFill {
  const _BarFill({
    required this.fraction,
    required this.color,
    required this.edged,
  });

  final double fraction;
  final Color color;
  final bool edged;

  _BarFill withFraction(double f) =>
      _BarFill(fraction: f, color: color, edged: edged);

  @override
  bool operator ==(Object other) =>
      other is _BarFill &&
      other.fraction == fraction &&
      other.color == color &&
      other.edged == edged;

  @override
  int get hashCode => Object.hash(fraction, color, edged);
}

/// The fills of one bar, compared by value so an unchanged bar does not
/// restart its tween on every rebuild.
@immutable
class _BarFills {
  const _BarFills(this.fills);

  final List<_BarFill> fills;

  @override
  bool operator ==(Object other) =>
      other is _BarFills && listEquals(other.fills, fills);

  @override
  int get hashCode => Object.hashAll(fills);
}

/// Tweens every fill's share at once; a fill that appears grows from zero
/// and one that goes shrinks to zero. Colours are the target's.
class _BarFillsTween extends Tween<_BarFills> {
  _BarFillsTween({super.end});

  @override
  _BarFills lerp(double t) {
    final from = begin?.fills ?? const <_BarFill>[];
    final to = end?.fills ?? const <_BarFill>[];
    final count = to.length > from.length ? to.length : from.length;
    return _BarFills([
      for (var i = 0; i < count; i++)
        (i < to.length ? to[i] : from[i]).withFraction(
          lerpDouble(
            i < from.length ? from[i].fraction : 0,
            i < to.length ? to[i].fraction : 0,
            t,
          )!,
        ),
    ]);
  }
}

/// The bar of every state (KitProgressRow.md): a `surface3` track and its
/// fills, drawn by one painter at [KitTokens.progressBarHeight]. A value
/// change tweens the shares on [KitMotion.standard] / [KitMotion.enter] and
/// only repaints (MOT-5); under reduced motion it jumps straight there.
class _AnimatedBar extends StatelessWidget {
  const _AnimatedBar({
    required this.fills,
    required this.track,
    required this.hairline,
    required this.reduceMotion,
    this.outlined = false,
  });

  final List<_BarFill> fills;
  final Color track;
  final Color hairline;
  final bool reduceMotion;

  /// Draws a hairline outline round the whole track (a segments bar, whose
  /// track is the panel's colour).
  final bool outlined;

  @override
  Widget build(BuildContext context) {
    final direction = Directionality.of(context);
    final hairlineWidth = KitTokens.hairlineWidth(context);
    final pixelRatio = MediaQuery.devicePixelRatioOf(context);
    Widget paint(_BarFills shown) => CustomPaint(
      size: const Size(double.infinity, KitTokens.progressBarHeight),
      painter: _BarPainter(
        fills: shown,
        track: track,
        hairline: hairline,
        outlined: outlined,
        hairlineWidth: hairlineWidth,
        pixelRatio: pixelRatio,
        direction: direction,
      ),
    );
    final target = _BarFills(fills);
    return SizedBox(
      width: double.infinity,
      height: KitTokens.progressBarHeight,
      child: reduceMotion
          ? paint(target)
          : TweenAnimationBuilder<_BarFills>(
              tween: _BarFillsTween(end: target),
              duration: KitMotion.standard,
              curve: KitMotion.enter,
              builder: (context, shown, _) => paint(shown),
            ),
    );
  }
}

class _BarPainter extends CustomPainter {
  const _BarPainter({
    required this.fills,
    required this.track,
    required this.hairline,
    required this.outlined,
    required this.hairlineWidth,
    required this.pixelRatio,
    required this.direction,
  });

  final _BarFills fills;
  final Color track;
  final Color hairline;
  final bool outlined;
  final double hairlineWidth;
  final double pixelRatio;
  final TextDirection direction;

  @override
  void paint(Canvas canvas, Size size) {
    final whole = Offset.zero & size;
    final rtl = direction == TextDirection.rtl;
    // From the start (it mirrors): [from, to) measured along the bar.
    Rect span(double from, double to) => rtl
        ? Rect.fromLTRB(size.width - to, 0, size.width - from, size.height)
        : Rect.fromLTRB(from, 0, to, size.height);

    canvas.save();
    canvas.clipRRect(
      RRect.fromRectAndRadius(
        whole,
        const Radius.circular(KitTokens.progressBarRadius),
      ),
    );
    canvas.drawRect(whole, Paint()..color = track);
    final edges = <double>{};
    var at = 0.0;
    for (final fill in fills.fills) {
      final width = fill.fraction * size.width;
      if (width <= 0) continue;
      final to = (at + width).clamp(0.0, size.width);
      canvas.drawRect(span(at, to), Paint()..color = fill.color);
      if (fill.edged) edges.addAll([at, to]);
      at = to;
    }
    // The hairline edges, snapped to whole physical pixels; the bar's own
    // two ends need none.
    final line = Paint()..color = hairline;
    for (final edge in edges) {
      if (edge <= 0 || edge >= size.width) continue;
      final snapped = (edge * pixelRatio).roundToDouble() / pixelRatio;
      final from = (snapped - hairlineWidth / 2).clamp(
        0.0,
        size.width - hairlineWidth,
      );
      canvas.drawRect(span(from, from + hairlineWidth), line);
    }
    if (outlined) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          whole.deflate(hairlineWidth / 2),
          const Radius.circular(KitTokens.progressBarRadius),
        ),
        Paint()
          ..color = hairline
          ..style = PaintingStyle.stroke
          ..strokeWidth = hairlineWidth,
      );
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(_BarPainter old) =>
      old.fills != fills ||
      old.track != track ||
      old.hairline != hairline ||
      old.outlined != outlined ||
      old.hairlineWidth != hairlineWidth ||
      old.pixelRatio != pixelRatio ||
      old.direction != direction;
}

/// The legend under a segments bar: one row per segment (mark swatch,
/// label, value label), flowing in two columns at width and count
/// (KitProgressRow.md "Adaptive": >= 3 segments, and a column of at least half the
/// reading cap less the panel's two gutters, which a part held to the reading
/// cap still reaches).
class _Legend extends StatelessWidget {
  const _Legend({
    required this.slices,
    required this.colorFor,
    required this.tokens,
    required this.roles,
  });

  final List<_Slice> slices;
  final Color Function(int index) colorFor;
  final KitTokens tokens;
  final ThemeRoles roles;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final rows = <Widget>[
          for (var i = 0; i < slices.length; i++)
            _row(context, slices[i], colorFor(i), constraints.maxWidth),
        ];
        final columnWidth = (constraints.maxWidth - tokens.space3) / 2;
        // At large text (1.3x and above) the legend stacks one entry per
        // line whatever the width (R8), as the value line does.
        final twoColumns =
            !_largeText(context) &&
            rows.length >= 3 &&
            columnWidth >= KitLayout.readingWidth / 2 - 2 * tokens.gutter;
        if (!twoColumns) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              for (var i = 0; i < rows.length; i++) ...[
                if (i > 0) SizedBox(height: tokens.space1),
                rows[i],
              ],
            ],
          );
        }
        return Wrap(
          spacing: tokens.space3,
          runSpacing: tokens.space1,
          children: [
            for (final r in rows) SizedBox(width: columnWidth, child: r),
          ],
        );
      },
    );
  }

  static bool _largeText(BuildContext context) =>
      MediaQuery.textScalerOf(context).scale(100) / 100 >= 1.3;

  /// One legend entry. At large text the label wraps instead of clipping
  /// and the value label takes at most half the line, wrapping too, so the
  /// entry never overflows at 2.0 text (R8).
  Widget _row(
    BuildContext context,
    _Slice slice,
    Color color,
    double lineWidth,
  ) {
    final large = _largeText(context);
    final value = slice.valueLabel;
    return Row(
      children: [
        DecoratedBox(
          // A `surface3` swatch gets the bar's hairline edge, or it vanishes
          // on the `surface1` panel.
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: color == roles.surface3
                ? Border.all(
                    color: roles.hairline,
                    width: KitTokens.hairlineWidth(context),
                  )
                : null,
          ),
          child: const SizedBox.square(dimension: KitTokens.swatchDot),
        ),
        SizedBox(width: tokens.space2),
        Expanded(
          child: KitText(
            slice.label,
            role: KitTextRole.secondary,
            maxLines: large ? null : 1,
            overflow: large ? null : TextOverflow.ellipsis,
          ),
        ),
        if (value != null) ...[
          SizedBox(width: tokens.space2),
          if (large)
            ConstrainedBox(
              constraints: BoxConstraints(maxWidth: lineWidth / 2),
              child: KitText(
                value,
                role: KitTextRole.secondary,
                tabular: true,
                textAlign: TextAlign.end,
              ),
            )
          else
            // A value never takes more than half the line: a long one is
            // cut with an ellipsis, not run past the legend's edge.
            ConstrainedBox(
              constraints: BoxConstraints(maxWidth: lineWidth / 2),
              child: KitText(
                value,
                role: KitTextRole.secondary,
                tabular: true,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                textAlign: TextAlign.end,
              ),
            ),
        ],
      ],
    );
  }
}

/// The kit's words: the app's bound [AppLocalizations], else the locale's
/// lookup, else English, so the part never throws in a bare harness with no
/// localization delegates (R8).
AppLocalizations _kitProgressWords(BuildContext context) {
  final bound = Localizations.of<AppLocalizations>(context, AppLocalizations);
  if (bound != null) return bound;
  final locale = Localizations.maybeLocaleOf(context);
  if (locale != null && AppLocalizations.delegate.isSupported(locale)) {
    return lookupAppLocalizations(locale);
  }
  return AppLocalizationsEn();
}
