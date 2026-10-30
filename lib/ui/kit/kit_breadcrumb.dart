// The folder trail (docs/ux-system/kit-api/KitBreadcrumb.md): the root, the
// folders below it and the current folder last, each ancestor one tap back.
// When the trail does not fit, the middle folders fold into one "…" crumb
// that opens a `showKitMenu` of them. Replaces Files' horizontal scroll of
// `ActionChip`s and a `Chip` (files_screen.dart, adopted by its wave-2
// unit). The part never builds paths: the host joins
// `segments.take(i + 1)` in [KitBreadcrumb.onSelected].
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_menu.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// The one ellipsis the part draws: the "…" crumb and a middle cut.
const _kitBreadcrumbEllipsis = '…';

/// A folder trail. States: default, collapsed, root only, truncated.
///
/// - default: root › a › b › current. Ancestors are tappable crumbs in
///   `text2`; the current folder is last, in `text1` semibold, and is not
///   tappable (a tap never triggers a pointless reload).
/// - collapsed: root › … › parent › current. The root, the parent of the
///   current folder and the current folder are always shown; the folders in
///   between are hidden from the one next to the root inwards until the
///   trail fits its width, and "…" opens a menu of them in trail order.
/// - root only: [segments] empty, the root alone as the current crumb.
/// - truncated: an ancestor wider than [KitTokens.crumbMaxWidth] keeps its
///   start and end with "…" in the middle (A11Y-8); its full name is in its
///   semantics and tooltip. The current folder uses the room left, wraps to
///   two lines, and only then is cut in the middle (its semantics keep the
///   full name).
///
/// One 48 dp row that grows with the text; no motion, no haptics. The part
/// draws edge to edge within the host's rails.
///
/// States: none — every crumb but the current one always opens its place.
class KitBreadcrumb extends StatelessWidget {
  const KitBreadcrumb({
    super.key,
    required this.rootLabel,
    required this.segments,
    required this.onSelected,
    this.breadcrumbKey,
    this.crumbKey,
  });

  /// "Project root", or the project's name, shown as written.
  final String rootLabel;

  /// Folder names below the root, in order; the last is the current folder.
  final List<String> segments;

  /// -1 is the root, i is `segments[i]`. Never called for the current
  /// folder.
  final ValueChanged<int> onSelected;

  /// A test handle on the trail's container node.
  final Key? breadcrumbKey;

  /// A test handle per crumb: index -1 is the root, i is `segments[i]`.
  final Key Function(int index)? crumbKey;

  String _nameOf(int index) => index < 0 ? rootLabel : segments[index];

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return Semantics(
      key: breadcrumbKey,
      container: true,
      explicitChildNodes: true,
      label: l10n.kitBreadcrumb,
      child: FocusTraversalGroup(
        child: LayoutBuilder(
          builder: (context, constraints) =>
              _trail(context, l10n, constraints.maxWidth),
        ),
      ),
    );
  }

  Widget _trail(BuildContext context, AppLocalizations l10n, double maxWidth) {
    final tokens = KitTokens.of(context);
    final measure = _KitCrumbMeasure.of(context);
    final padding = tokens.space2 * 2;
    final separatorWidth = tokens.smallIconSize + tokens.space1 * 2;

    if (segments.isEmpty) {
      return _row(tokens, [
        Flexible(
          child: _current(
            context,
            l10n,
            index: -1,
            textWidth: maxWidth.isFinite ? maxWidth - padding : null,
            measure: measure,
          ),
        ),
      ]);
    }

    final count = segments.length;
    final currentIndex = count - 1;
    final parentIndex = count - 2; // -1 (the root) when there is one segment.
    // The folders between the root and the parent: segments[0 .. count-3].
    final middle = count >= 3 ? count - 2 : 0;

    // An ancestor's text is never wider than crumbMaxWidth before it cuts.
    final naturalText = <int, double>{
      for (var i = -1; i <= parentIndex; i++)
        i: _min(measure.natural(_nameOf(i)), KitTokens.crumbMaxWidth),
    };
    double crumbWidth(double text) => _max(tokens.minTarget, text + padding);
    final moreWidth = crumbWidth(measure.natural(_kitBreadcrumbEllipsis));
    final currentNatural =
        measure.natural(segments[currentIndex], current: true) + padding;

    List<int> shownFor(int hidden) => [
      -1,
      for (var i = hidden; i < middle; i++) i,
      if (parentIndex >= 0) parentIndex,
    ];
    double fixedWidth(List<int> shown, int hidden, Map<int, double> text) {
      final crumbs = shown.length + (hidden > 0 ? 1 : 0);
      var width = separatorWidth * crumbs; // one separator before current.
      for (final i in shown) {
        width += crumbWidth(text[i]!);
      }
      return width + (hidden > 0 ? moreWidth : 0);
    }

    var hidden = 0;
    if (maxWidth.isFinite) {
      hidden = middle;
      for (var h = 0; h <= middle; h++) {
        if (fixedWidth(shownFor(h), h, naturalText) + currentNatural <=
            maxWidth) {
          hidden = h;
          break;
        }
      }
    }
    final shown = shownFor(hidden);

    // Still too narrow for the current folder (a narrow window, large
    // text): the kept ancestors share what is left evenly, never below a
    // 48 dp target, so the current folder keeps a usable width.
    final textWidth = Map<int, double>.of(naturalText);
    if (maxWidth.isFinite) {
      final minCurrent = _min(currentNatural, tokens.minTarget * 2);
      if (fixedWidth(shown, hidden, textWidth) + minCurrent > maxWidth) {
        final crumbs = shown.length + (hidden > 0 ? 1 : 0);
        final budget =
            maxWidth -
            minCurrent -
            separatorWidth * crumbs -
            (hidden > 0 ? moreWidth : 0);
        final each = _max(
          tokens.minTarget - padding,
          (budget / shown.length - padding).floorToDouble(),
        );
        for (final i in shown) {
          textWidth[i] = _min(textWidth[i]!, each);
        }
      }
    }

    final separator = ExcludeSemantics(
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space1),
        child: const KitIcon(
          AppIconography.chevronRight,
          size: KitIconSize.small,
          tone: KitTextTone.tertiary,
        ),
      ),
    );

    final children = <Widget>[];
    void add(Widget crumb) {
      if (children.isNotEmpty) children.add(separator);
      children.add(crumb);
    }

    for (final i in shown) {
      add(
        _ancestor(
          context,
          l10n,
          index: i,
          textWidth: textWidth[i]!,
          measure: measure,
        ),
      );
      // The "…" sits right after the root, where the hidden folders were.
      if (i < 0 && hidden > 0) {
        add(
          _KitMoreCrumb(
            hidden: [for (var h = 0; h < hidden; h++) h],
            segments: segments,
            onSelected: onSelected,
            label: l10n.kitBreadcrumbMore(hidden),
          ),
        );
      }
    }

    final used = fixedWidth(shown, hidden, textWidth);
    add(
      Flexible(
        child: _current(
          context,
          l10n,
          index: currentIndex,
          textWidth: maxWidth.isFinite
              ? _max(0, maxWidth - used - padding)
              : null,
          measure: measure,
        ),
      ),
    );
    return _row(tokens, children);
  }

  Widget _row(KitTokens tokens, List<Widget> children) => ConstrainedBox(
    constraints: BoxConstraints(minHeight: tokens.minTarget),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: children,
    ),
  );

  Widget _ancestor(
    BuildContext context,
    AppLocalizations l10n, {
    required int index,
    required double textWidth,
    required _KitCrumbMeasure measure,
  }) {
    final tokens = KitTokens.of(context);
    final name = _nameOf(index);
    final display = measure.middleCut(name, textWidth, maxLines: 1);
    final cut = display != name;
    return KitTappable(
      tappableKey: crumbKey?.call(index),
      onTap: () => onSelected(index),
      label: index < 0
          ? l10n.kitBreadcrumbOpenRoot(name)
          : l10n.kitBreadcrumbOpen(name),
      // LAY-11: only a cut name needs its full form on hover and focus.
      tooltip: cut ? name : null,
      shape: KitShape.button,
      surface: KitSurfaceLevel.ground,
      // No wider than the width the trail was fitted with: words that grew
      // after the cut (a larger scale) end in an ellipsis, never past the row.
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: textWidth + 2 * tokens.space2),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
          child: KitText(
            KitBidi.auto(display),
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
            maxLines: 1,
            softWrap: false,
          ),
        ),
      ),
    );
  }

  Widget _current(
    BuildContext context,
    AppLocalizations l10n, {
    required int index,
    required double? textWidth,
    required _KitCrumbMeasure measure,
  }) {
    final tokens = KitTokens.of(context);
    final name = _nameOf(index);
    final display = textWidth == null
        ? name
        : measure.middleCut(name, textWidth, maxLines: 2, current: true);
    return Semantics(
      key: crumbKey?.call(index),
      container: true,
      selected: true,
      label: l10n.kitBreadcrumbCurrent(name),
      excludeSemantics: true,
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.minTarget),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            widthFactor: 1,
            heightFactor: 1,
            child: KitText.rich(
              TextSpan(
                text: KitBidi.auto(display),
                style: const TextStyle(fontWeight: FontWeight.w600),
              ),
              role: KitTextRole.secondary,
              tone: KitTextTone.primary,
              maxLines: 2,
            ),
          ),
        ),
      ),
    );
  }
}

/// The "…" crumb: the hidden middle folders' menu, in trail order.
class _KitMoreCrumb extends StatelessWidget {
  const _KitMoreCrumb({
    required this.hidden,
    required this.segments,
    required this.onSelected,
    required this.label,
  });

  final List<int> hidden;
  final List<String> segments;
  final ValueChanged<int> onSelected;
  final String label;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return KitTappable(
      onTap: () => showKitMenu(
        context,
        semanticsLabel: label,
        items: [
          for (final i in hidden)
            KitMenuItem(label: segments[i], onSelected: () => onSelected(i)),
        ],
      ),
      label: label,
      tooltip: label,
      shape: KitShape.button,
      surface: KitSurfaceLevel.ground,
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space2),
        child: const KitText(
          _kitBreadcrumbEllipsis,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
          maxLines: 1,
          softWrap: false,
        ),
      ),
    );
  }
}

/// Measures crumb text exactly as [KitText] paints it (the ambient
/// [DefaultTextStyle], bold text, the text scaler and the reading
/// direction), so the collapse rule and the middle cut never overflow.
class _KitCrumbMeasure {
  _KitCrumbMeasure._(
    this._style,
    this._currentStyle,
    this._scaler,
    this._direction,
    this._locale,
  );

  factory _KitCrumbMeasure.of(BuildContext context) {
    var style = DefaultTextStyle.of(
      context,
    ).style.merge(KitText.styleOf(context, KitTextRole.secondary));
    if (MediaQuery.boldTextOf(context)) {
      style = style.merge(const TextStyle(fontWeight: FontWeight.bold));
    }
    final current = MediaQuery.boldTextOf(context)
        ? style
        : style.merge(const TextStyle(fontWeight: FontWeight.w600));
    return _KitCrumbMeasure._(
      style,
      current,
      MediaQuery.textScalerOf(context),
      Directionality.of(context),
      Localizations.maybeLocaleOf(context),
    );
  }

  final TextStyle _style;
  final TextStyle _currentStyle;
  final TextScaler _scaler;
  final TextDirection _direction;
  final Locale? _locale;

  TextPainter _painter(String value, {required bool current, int? maxLines}) =>
      TextPainter(
        text: TextSpan(
          text: KitBidi.auto(value),
          style: current ? _currentStyle : _style,
          locale: _locale,
        ),
        textDirection: _direction,
        textScaler: _scaler,
        maxLines: maxLines,
        locale: _locale,
      );

  /// The one-line width of [value], rounded up to a whole logical pixel.
  double natural(String value, {bool current = false}) {
    final painter = _painter(value, current: current)..layout();
    final width = painter.width.ceilToDouble();
    painter.dispose();
    return width;
  }

  bool _fits(String value, double maxWidth, int maxLines, bool current) {
    final painter = _painter(value, current: current, maxLines: maxLines)
      ..layout(maxWidth: maxWidth);
    final fits = !painter.didExceedMaxLines && painter.width <= maxWidth;
    painter.dispose();
    return fits;
  }

  /// [name] as it fits in [maxWidth] and [maxLines]: whole, or its start
  /// and end with one "…" in the middle (A11Y-8). A binary search over how
  /// many characters (grapheme clusters) to keep.
  String middleCut(
    String name,
    double maxWidth, {
    required int maxLines,
    bool current = false,
  }) {
    if (_fits(name, maxWidth, maxLines, current)) return name;
    final chars = name.characters.toList();
    var low = 1;
    var high = chars.length - 1;
    var best = _kitBreadcrumbEllipsis;
    while (low <= high) {
      final kept = (low + high) ~/ 2;
      final head = kept - kept ~/ 2;
      final tail = kept ~/ 2;
      final candidate =
          '${chars.take(head).join()}$_kitBreadcrumbEllipsis'
          '${chars.skip(chars.length - tail).join()}';
      if (_fits(candidate, maxWidth, maxLines, current)) {
        best = candidate;
        low = kept + 1;
      } else {
        high = kept - 1;
      }
    }
    return best;
  }
}

double _min(double a, double b) => a < b ? a : b;
double _max(double a, double b) => a > b ? a : b;
