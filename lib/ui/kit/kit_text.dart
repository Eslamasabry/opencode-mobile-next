import 'package:flutter/material.dart';

import '../theme_roles.dart';
import 'kit_layout.dart';

/// The type roles of the visual language (docs/design/visual-language-2026-09-26.md
/// §2, rounded to whole pixels per §7).
enum KitTextRole {
  /// 32/38, 650: the screen's own name (Settings, a project).
  largeTitle,

  /// 24/30, 650: a sheet's or a dialog's question.
  title,

  /// 17/22, 600: the top bar, card titles.
  headline,

  /// 16/24, 400: the transcript, paragraphs.
  body,

  /// 16/22, 500: a row's first line.
  rowTitle,

  /// 14/20, 400: a row's second line, meta.
  secondary,

  /// 13/18, 600: section labels (never uppercase).
  label,

  /// 12/16, 600, +0.02 em: "Needs you · 40 s ago".
  caption,

  /// 16/20, 600: buttons (LOOK-12).
  button,

  /// 13/19, Geist Mono: code, commands, paths (isolated left to right).
  mono,
}

/// Which colour role a [KitText] paints in. Always an opaque role (§7: no
/// text at partial opacity).
enum KitTextTone {
  primary,
  secondary,
  tertiary,
  accent,
  onAccent,
  attention,
  danger,
  success,
}

/// How a [KitSelectable] region turns on (KitText v2).
enum KitSelectMode {
  /// Long-press on touch, drag with a mouse: About, diagnostics, a file
  /// preview.
  always,

  /// Only while a fine pointer is in use ([KitLayout.finePointer]): the
  /// transcript, where long-press on touch belongs to the row menu.
  /// Replaces `DesktopSelectionArea`.
  finePointer,
}

/// What [KitText.mono] does when it does not fit (KitText v2, A11Y-8).
enum KitMonoCut {
  /// Wraps at any character: a code line, a long command.
  wrap,

  /// One line, "…" at the end: an id, a host.
  end,

  /// One line, "…" in the middle: a path keeps its root and its file name.
  middle,
}

/// The kind of content a [KitText] instance carries; routes [KitText.build]
/// to the right widget without adding a public API (R11: the existing
/// constructors and fields are unchanged).
enum _KitTextKind { plain, rich, selectable, selectableRich, mono }

/// Text in one of the kit's type roles (kit v2 §9.2, the first P9.7 part):
/// the role sets size, line height, weight and tracking; the [tone] sets
/// the colour from the theme's roles. Screens name the role, never a size.
///
/// Mono text is laid out left to right whatever the reading direction, so a
/// path or a command never reorders inside Arabic.
///
/// States: none — text shows the words it is given.
class KitText extends StatelessWidget {
  const KitText(
    this.text, {
    super.key,
    this.role = KitTextRole.body,
    this.tone,
    this.maxLines,
    this.overflow,
    this.textAlign,
    this.softWrap,
    this.semanticsLabel,
    this.tabular = false,
  }) : span = null,
       cut = KitMonoCut.wrap,
       _monoSelectable = false,
       _kind = _KitTextKind.plain;

  /// A quiet inline link: the secondary role in the accent tone ("In
  /// /root/projects · Change"). Wrap it in a [KitTappable] for the tap.
  const KitText.link(this.text, {super.key, this.maxLines, this.semanticsLabel})
    : role = KitTextRole.secondary,
      tone = KitTextTone.accent,
      overflow = null,
      textAlign = null,
      softWrap = null,
      tabular = false,
      span = null,
      cut = KitMonoCut.wrap,
      _monoSelectable = false,
      _kind = _KitTextKind.plain;

  /// Spans in one role; a span may carry its own tone colour
  /// ([KitText.toneColor]) or weight.
  const KitText.rich(
    InlineSpan this.span, {
    super.key,
    this.role = KitTextRole.body,
    this.tone,
    this.maxLines,
    this.overflow,
    this.textAlign,
    this.softWrap,
    this.semanticsLabel,
    this.tabular = false,
  }) : text = '',
       cut = KitMonoCut.wrap,
       _monoSelectable = false,
       _kind = _KitTextKind.rich;

  /// Text the person can select and copy (KitText v2). Long-press on touch,
  /// drag with a mouse. The toolbar offers Copy and Select all, and nothing
  /// else. Not a Tab stop.
  const KitText.selectable(
    this.text, {
    super.key,
    this.role = KitTextRole.body,
    this.tone,
    this.maxLines,
    this.textAlign,
    this.semanticsLabel,
    this.tabular = false,
  }) : span = null,
       overflow = null,
       softWrap = null,
       cut = KitMonoCut.wrap,
       _monoSelectable = false,
       _kind = _KitTextKind.selectable;

  /// Selectable spans (the Markdown part's paragraphs; KitText v2).
  const KitText.selectableRich(
    InlineSpan this.span, {
    super.key,
    this.role = KitTextRole.body,
    this.tone,
    this.maxLines,
    this.textAlign,
    this.semanticsLabel,
    this.tabular = false,
  }) : text = '',
       overflow = null,
       softWrap = null,
       cut = KitMonoCut.wrap,
       _monoSelectable = false,
       _kind = _KitTextKind.selectableRich;

  /// A technical value the app did not write: a path, command, host, id,
  /// branch, URL or version (KitText v2). It uses the mono role and is laid
  /// out left to right whatever the reading direction (LOOK-16, KIT-32). It
  /// is aligned to the START of the ambient direction, so under Arabic it
  /// sits at the row's right edge. When [cut] is not [KitMonoCut.wrap], the
  /// semantics carry the full value.
  ///
  /// [KitMonoCut.middle] (and [KitMonoCut.end] with [selectable]) measures
  /// the value against the width it is given, so it needs a bounded width
  /// and answers no intrinsic-size query: it cannot sit under
  /// `IntrinsicWidth`, `IntrinsicHeight` or an intrinsically sized table
  /// column. Give it a fixed or flexible width instead (a row's `Expanded`).
  const KitText.mono(
    this.text, {
    super.key,
    this.tone,
    this.cut = KitMonoCut.wrap,
    this.maxLines,
    bool selectable = false,
    this.semanticsLabel,
  }) : span = null,
       role = KitTextRole.mono,
       overflow = null,
       textAlign = null,
       softWrap = null,
       tabular = false,
       _monoSelectable = selectable,
       _kind = _KitTextKind.mono;

  final String text;
  final InlineSpan? span;
  final KitTextRole role;

  /// Null takes the role's own tone: [KitTextTone.secondary] for
  /// [KitTextRole.secondary], [KitTextRole.label] and [KitTextRole.caption],
  /// [KitTextTone.primary] for the rest.
  final KitTextTone? tone;
  final int? maxLines;
  final TextOverflow? overflow;
  final TextAlign? textAlign;
  final bool? softWrap;
  final String? semanticsLabel;

  /// Tabular figures ([FontFeature.tabularFigures]) for numbers that line
  /// up (LOOK-18; KitText v2). Ignored by [KitText.mono].
  final bool tabular;

  /// [KitText.mono] only: what happens when the value does not fit.
  final KitMonoCut cut;

  final bool _monoSelectable;
  final _KitTextKind _kind;

  /// The app-wide text scale (A11Y-8: only kit parts clamp text scale): the
  /// person's own [scaler] passes through untouched below [max], including
  /// scales under 1.0, which people pick deliberately; only the extreme top
  /// end is capped, so a runaway scale cannot break the shell.
  static TextScaler appScaler(TextScaler scaler, {required double max}) {
    final scale = scaler.scale(1);
    return TextScaler.linear(scale > max ? max : scale);
  }

  /// [words] in sentence case: the first letter capitalised, nothing else
  /// changed (LOOK-15: never all capitals).
  static String sentenceCase(String words) =>
      words.isEmpty ? words : words[0].toUpperCase() + words.substring(1);

  /// The base style of [role]: size, line height, weight, tracking and (for
  /// mono) the family. No colour: [styleOf] adds it.
  static TextStyle styleFor(KitTextRole role) => switch (role) {
    KitTextRole.largeTitle => const TextStyle(
      fontSize: 32,
      height: 38 / 32,
      fontWeight: FontWeight(650),
      letterSpacing: -0.8,
    ),
    KitTextRole.title => const TextStyle(
      fontSize: 24,
      height: 30 / 24,
      fontWeight: FontWeight(650),
      letterSpacing: -0.48,
    ),
    KitTextRole.headline => const TextStyle(
      fontSize: 17,
      height: 22 / 17,
      fontWeight: FontWeight.w600,
      letterSpacing: -0.17,
    ),
    KitTextRole.body => const TextStyle(
      fontSize: 16,
      height: 24 / 16,
      fontWeight: FontWeight.w400,
      letterSpacing: 0,
    ),
    KitTextRole.rowTitle => const TextStyle(
      fontSize: 16,
      height: 22 / 16,
      fontWeight: FontWeight.w500,
      letterSpacing: 0,
    ),
    KitTextRole.secondary => const TextStyle(
      fontSize: 14,
      height: 20 / 14,
      fontWeight: FontWeight.w400,
      letterSpacing: 0,
    ),
    KitTextRole.label => const TextStyle(
      fontSize: 13,
      height: 18 / 13,
      fontWeight: FontWeight.w600,
      letterSpacing: 0,
    ),
    KitTextRole.caption => const TextStyle(
      fontSize: 12,
      height: 16 / 12,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.24,
    ),
    KitTextRole.button => const TextStyle(
      fontSize: 16,
      height: 20 / 16,
      fontWeight: FontWeight.w600,
      letterSpacing: 0,
    ),
    KitTextRole.mono => const TextStyle(
      fontFamily: 'AppMono',
      fontSize: 13,
      height: 19 / 13,
      fontWeight: FontWeight.w400,
      letterSpacing: 0,
    ),
  };

  static KitTextTone defaultTone(KitTextRole role) => switch (role) {
    KitTextRole.secondary ||
    KitTextRole.label ||
    KitTextRole.caption => KitTextTone.secondary,
    _ => KitTextTone.primary,
  };

  static Color toneColor(ThemeRoles roles, KitTextTone tone) => switch (tone) {
    KitTextTone.primary => roles.text1,
    KitTextTone.secondary => roles.text2,
    KitTextTone.tertiary => roles.text3,
    KitTextTone.accent => roles.accent,
    KitTextTone.onAccent => roles.onAccent,
    KitTextTone.attention => roles.attention,
    KitTextTone.danger => roles.danger,
    KitTextTone.success => roles.success,
  };

  /// The resolved style of [role] in [tone] here: the theme's face (so an
  /// Arabic locale keeps its system face) with the role's metrics.
  static TextStyle styleOf(
    BuildContext context,
    KitTextRole role, {
    KitTextTone? tone,
  }) {
    final theme = Theme.of(context);
    final roles = ThemeRoles.resolve(theme);
    final face = theme.textTheme.bodyLarge;
    // Arabic (the system face) keeps zero tracking for connected shaping.
    final arabic = face?.fontFamily == 'sans-serif';
    var style = styleFor(role);
    if (role != KitTextRole.mono) {
      style = style.copyWith(
        fontFamily: face?.fontFamily,
        fontFamilyFallback: face?.fontFamilyFallback,
        letterSpacing: arabic ? 0 : style.letterSpacing,
      );
    } else if (arabic) {
      // Mono keeps AppMono for the Latin of a technical value, but an
      // Arabic file or branch name inside a path falls back to the same
      // Arabic faces as the prose roles, never to whatever the engine picks.
      style = style.copyWith(
        fontFamilyFallback: face?.fontFamilyFallback,
        letterSpacing: 0,
      );
    }
    return style.copyWith(color: toneColor(roles, tone ?? defaultTone(role)));
  }

  /// Material's type scale said in the kit's roles (LOOK-12, LOOK-17):
  /// every slot is exactly one role, so no text in the app, kit or not,
  /// takes a size outside the role table.
  ///
  /// | Slot | Role |
  /// |---|---|
  /// | displayLarge, displayMedium, displaySmall, headlineLarge | [KitTextRole.largeTitle] 32 |
  /// | headlineMedium, headlineSmall, titleLarge | [KitTextRole.title] 24 |
  /// | titleMedium | [KitTextRole.rowTitle] 16 |
  /// | titleSmall, labelLarge, labelMedium | [KitTextRole.label] 13 |
  /// | bodyLarge | [KitTextRole.body] 16 |
  /// | bodyMedium, bodySmall | [KitTextRole.secondary] 14 |
  /// | labelSmall | [KitTextRole.caption] 12 |
  ///
  /// bodyMedium is the ambient text of every Material widget, so the
  /// screens not yet on [KitText] read at the secondary role's 14/20; a
  /// button's text is [KitTextRole.button], set by the button themes.
  /// Every colour is the opaque `text1`.
  static TextTheme textTheme(TextTheme base, ThemeRoles roles) {
    TextStyle? role(TextStyle? slot, KitTextRole role) => slot
        ?.merge(styleFor(role))
        .copyWith(color: roles.text1, decorationColor: roles.text1);
    return base.copyWith(
      displayLarge: role(base.displayLarge, KitTextRole.largeTitle),
      displayMedium: role(base.displayMedium, KitTextRole.largeTitle),
      displaySmall: role(base.displaySmall, KitTextRole.largeTitle),
      headlineLarge: role(base.headlineLarge, KitTextRole.largeTitle),
      headlineMedium: role(base.headlineMedium, KitTextRole.title),
      headlineSmall: role(base.headlineSmall, KitTextRole.title),
      titleLarge: role(base.titleLarge, KitTextRole.title),
      titleMedium: role(base.titleMedium, KitTextRole.rowTitle),
      titleSmall: role(base.titleSmall, KitTextRole.label),
      bodyLarge: role(base.bodyLarge, KitTextRole.body),
      bodyMedium: role(base.bodyMedium, KitTextRole.secondary),
      bodySmall: role(base.bodySmall, KitTextRole.secondary),
      labelLarge: role(base.labelLarge, KitTextRole.label),
      labelMedium: role(base.labelMedium, KitTextRole.label),
      labelSmall: role(base.labelSmall, KitTextRole.caption),
    );
  }

  @override
  Widget build(BuildContext context) {
    final style = styleOf(context, role, tone: tone);
    switch (_kind) {
      case _KitTextKind.plain:
      case _KitTextKind.rich:
        // A caller may still pass role: mono to the plain constructors
        // (unchanged pre-v2 behaviour): the paragraph stays left to right.
        final mono = role == KitTextRole.mono;
        final resolved = tabular
            ? style.copyWith(fontFeatures: const [FontFeature.tabularFigures()])
            : style;
        return _kind == _KitTextKind.plain
            ? Text(
                text,
                style: resolved,
                maxLines: maxLines,
                overflow: overflow,
                textAlign: textAlign,
                softWrap: softWrap,
                semanticsLabel: semanticsLabel,
                textDirection: mono ? TextDirection.ltr : null,
              )
            : Text.rich(
                span!,
                style: resolved,
                maxLines: maxLines,
                overflow: overflow,
                textAlign: textAlign,
                softWrap: softWrap,
                semanticsLabel: semanticsLabel,
                textDirection: mono ? TextDirection.ltr : null,
              );
      case _KitTextKind.selectable:
      case _KitTextKind.selectableRich:
        final resolved = tabular
            ? style.copyWith(fontFeatures: const [FontFeature.tabularFigures()])
            : style;
        return _KitSelectableText(
          text: _kind == _KitTextKind.selectable ? text : null,
          span: _kind == _KitTextKind.selectableRich ? span : null,
          style: resolved,
          maxLines: maxLines,
          textAlign: textAlign,
          semanticsLabel: semanticsLabel,
        );
      case _KitTextKind.mono:
        return _KitMonoText(
          text: text,
          style: style,
          cut: cut,
          maxLines: maxLines,
          selectable: _monoSelectable,
          semanticsLabel: semanticsLabel,
        );
    }
  }
}

/// A selectable region around several texts (`SelectionArea`'s job;
/// KitText v2). The toolbar offers Copy and Select all, and nothing else
/// (the same rule as [KitText.selectable]).
///
/// States: none — the enclosed text is idle or selected, never this region.
class KitSelectable extends StatelessWidget {
  const KitSelectable({
    super.key,
    required this.child,
    this.mode = KitSelectMode.always,
  }) : _excluded = false;

  /// Content inside a selectable region that must not be selected, such as
  /// a preview's line numbers (`SelectionContainer.disabled`'s job).
  const KitSelectable.excluded({super.key, required this.child})
    : mode = KitSelectMode.always,
      _excluded = true;

  final Widget child;
  final KitSelectMode mode;
  final bool _excluded;

  @override
  Widget build(BuildContext context) {
    if (_excluded) return SelectionContainer.disabled(child: child);
    if (mode == KitSelectMode.finePointer && !KitLayout.finePointer(context)) {
      // Off, so the gesture reaches an ancestor unchanged (a row's own
      // long-press menu, on touch, where the transcript's own selection
      // does not apply).
      return child;
    }
    return SelectionArea(
      contextMenuBuilder: _kitSelectionAreaMenu,
      child: child,
    );
  }
}

/// A block of technical content that is not one text: a table of values, a
/// terminal strip, a key/value grid (KitText v2). It is forced left to
/// right. Never used for prose, a server's description or a whole
/// mixed-content card (the same rule the old `TechnicalDirection` followed).
/// A placeholder inside a sentence is isolated with `KitBidi.ltr` /
/// `KitBidi.auto` (the pre-wave seam, COPY-30), not with this.
///
/// States: none — it only sets a reading direction.
class KitLtr extends StatelessWidget {
  const KitLtr({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) =>
      Directionality(textDirection: TextDirection.ltr, child: child);
}

/// The restricted context menu [KitText.selectable], [KitText.selectableRich]
/// and [KitSelectable] all share: Copy and Select all, nothing else (no Cut,
/// Paste or Share — the content is read-only and never a secret; see
/// KitText.md "Data safety").
Widget _kitCopyMenu(
  List<ContextMenuButtonItem> offered,
  TextSelectionToolbarAnchors anchors,
) {
  final items = offered
      .where(
        (item) =>
            item.type == ContextMenuButtonType.copy ||
            item.type == ContextMenuButtonType.selectAll,
      )
      .toList();
  if (items.isEmpty) return const SizedBox.shrink();
  return AdaptiveTextSelectionToolbar.buttonItems(
    anchors: anchors,
    buttonItems: items,
  );
}

Widget _kitTextContextMenu(BuildContext context, EditableTextState state) =>
    _kitCopyMenu(state.contextMenuButtonItems, state.contextMenuAnchors);

Widget _kitSelectionAreaMenu(
  BuildContext context,
  SelectableRegionState state,
) => _kitCopyMenu(state.contextMenuButtonItems, state.contextMenuAnchors);

/// [SelectableText] / [SelectableText.rich] with a long-lived [FocusNode]
/// that is skipped by keyboard Tab traversal ([KitText.selectable] is never
/// a Tab stop) but can still take focus on tap or long-press, which
/// selection needs.
class _KitSelectableText extends StatefulWidget {
  const _KitSelectableText({
    this.text,
    this.span,
    required this.style,
    this.maxLines,
    this.textAlign,
    this.textDirection,
    this.semanticsLabel,
  }) : assert((text == null) != (span == null), 'exactly one of text/span');

  final String? text;
  final InlineSpan? span;
  final TextStyle style;
  final int? maxLines;
  final TextAlign? textAlign;
  final TextDirection? textDirection;
  final String? semanticsLabel;

  @override
  State<_KitSelectableText> createState() => _KitSelectableTextState();
}

class _KitSelectableTextState extends State<_KitSelectableText> {
  final _focusNode = FocusNode(
    skipTraversal: true,
    debugLabel: 'KitText.selectable',
  );

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final span = widget.span;
    if (span != null) {
      // SelectableText.rich takes a TextSpan root (unlike Text.rich, which
      // takes any InlineSpan): any other root, such as a WidgetSpan, is
      // wrapped as the only child of an unstyled TextSpan.
      return SelectableText.rich(
        span is TextSpan ? span : TextSpan(children: [span]),
        focusNode: _focusNode,
        style: widget.style,
        maxLines: widget.maxLines,
        textAlign: widget.textAlign,
        textDirection: widget.textDirection,
        semanticsLabel: widget.semanticsLabel,
        contextMenuBuilder: _kitTextContextMenu,
      );
    }
    return SelectableText(
      widget.text!,
      focusNode: _focusNode,
      style: widget.style,
      maxLines: widget.maxLines,
      textAlign: widget.textAlign,
      textDirection: widget.textDirection,
      semanticsLabel: widget.semanticsLabel,
      contextMenuBuilder: _kitTextContextMenu,
    );
  }
}

/// [KitText.mono]'s body: always left to right, aligned to the start of the
/// ambient direction, with [KitMonoCut] deciding what happens when the
/// value does not fit.
class _KitMonoText extends StatelessWidget {
  const _KitMonoText({
    required this.text,
    required this.style,
    required this.cut,
    required this.maxLines,
    required this.selectable,
    required this.semanticsLabel,
  });

  final String text;
  final TextStyle style;
  final KitMonoCut cut;
  final int? maxLines;
  final bool selectable;
  final String? semanticsLabel;

  @override
  Widget build(BuildContext context) {
    final align = Directionality.of(context) == TextDirection.rtl
        ? TextAlign.end
        : TextAlign.start;
    // The framework's Text keeps the full string as its semantics data even
    // when painted with an ellipsis, but a display string this widget
    // truncates itself (KitMonoCut.middle, or `end` while selectable) must
    // carry the full value explicitly.
    final fullValueLabel = semanticsLabel ?? text;

    if (cut == KitMonoCut.wrap) {
      if (selectable) {
        return _KitSelectableText(
          text: text,
          style: style,
          maxLines: maxLines,
          textAlign: align,
          textDirection: TextDirection.ltr,
          semanticsLabel: semanticsLabel,
        );
      }
      return Text(
        text,
        style: style,
        maxLines: maxLines,
        textAlign: align,
        textDirection: TextDirection.ltr,
        semanticsLabel: semanticsLabel,
      );
    }

    if (!selectable && cut == KitMonoCut.end) {
      return Text(
        text,
        style: style,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        textAlign: align,
        textDirection: TextDirection.ltr,
        semanticsLabel: fullValueLabel,
      );
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final display = constraints.maxWidth.isFinite
            ? _kitMonoTruncate(
                text,
                _kitMonoPaintedStyle(context, style),
                constraints.maxWidth,
                keepTail: cut == KitMonoCut.middle,
                textScaler: MediaQuery.textScalerOf(context),
                locale: Localizations.maybeLocaleOf(context),
              )
            : text;
        if (selectable) {
          return _KitSelectableText(
            text: display,
            style: style,
            maxLines: 1,
            textAlign: align,
            textDirection: TextDirection.ltr,
            semanticsLabel: fullValueLabel,
          );
        }
        return Text(
          display,
          style: style,
          maxLines: 1,
          softWrap: false,
          textAlign: align,
          textDirection: TextDirection.ltr,
          semanticsLabel: fullValueLabel,
        );
      },
    );
  }
}

const _kitMonoEllipsis = '…';

/// The style the [Text] (or [SelectableText]) below paints [style] in: the
/// ambient [DefaultTextStyle] under it and, when the platform asks for bold
/// text, the bold weight, exactly as those widgets resolve it. The cut must
/// measure what is painted, or the chosen string overflows the box.
TextStyle _kitMonoPaintedStyle(BuildContext context, TextStyle style) {
  var painted = DefaultTextStyle.of(context).style.merge(style);
  if (MediaQuery.boldTextOf(context)) {
    painted = painted.merge(const TextStyle(fontWeight: FontWeight.bold));
  }
  return painted;
}

/// The single line of [text] in [style] that fits [maxWidth], with one
/// [_kitMonoEllipsis] cutting the end (an id, a host) or the middle
/// ([keepTail]: a path keeps its root and its file name). A binary search
/// over how many characters to keep, measuring the candidate string each
/// time with the [textScaler] and [locale] the paragraph paints with: even
/// a monospace face varies glyph width with ligatures and wide punctuation,
/// so a plain character count cannot stand in for it.
///
/// The middle cut favours the tail up to the last path segment (the file
/// name with its separator), so `/home/…/main.dart` keeps `main.dart` for
/// as long as one head character still fits beside it; a value with no
/// separator is cut evenly.
String _kitMonoTruncate(
  String text,
  TextStyle style,
  double maxWidth, {
  required bool keepTail,
  required TextScaler textScaler,
  Locale? locale,
}) {
  double widthOf(String value) {
    final painter = TextPainter(
      text: TextSpan(text: value, style: style, locale: locale),
      textDirection: TextDirection.ltr,
      textScaler: textScaler,
      locale: locale,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }

  if (widthOf(text) <= maxWidth) return text;

  if (!keepTail) {
    var low = 0;
    var high = text.length;
    var best = _kitMonoEllipsis;
    while (low <= high) {
      final mid = (low + high) ~/ 2;
      final candidate = '${text.substring(0, mid)}$_kitMonoEllipsis';
      if (widthOf(candidate) <= maxWidth) {
        best = candidate;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return best;
  }

  // The last segment with its separator ('/main.dart'), or nothing when
  // the value has no separator.
  final lastSeparator = text.lastIndexOf(RegExp(r'[/\\]'));
  final fileName = lastSeparator > 0 ? text.length - lastSeparator : 0;
  String candidateOf(int kept) {
    final even = kept - kept ~/ 2;
    final tail = kept <= 1
        ? 0
        : (fileName < kept ? fileName : kept - 1).clamp(even, kept - 1);
    final head = kept - tail;
    return '${text.substring(0, head)}$_kitMonoEllipsis'
        '${text.substring(text.length - tail)}';
  }

  var low = 1;
  var high = text.length - 1;
  var best = _kitMonoEllipsis;
  while (low <= high) {
    final mid = (low + high) ~/ 2;
    final candidate = candidateOf(mid);
    if (widthOf(candidate) <= maxWidth) {
      best = candidate;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return best;
}
