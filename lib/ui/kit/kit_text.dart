import 'package:flutter/foundation.dart' show ValueListenable;
import 'package:flutter/material.dart';

import '../../state/effects.dart' show KitTextSize;
import '../theme_roles.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_text_halo.dart';
import 'style/kit_style.dart';

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
  /// Wraps at any character: a code line, a long command. With a
  /// `maxLines` (and not selectable) the last line ends in "…".
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
    this.pageTitle = false,
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
      pageTitle = false,
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
       pageTitle = false,
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
       pageTitle = false,
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
       pageTitle = false,
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
       pageTitle = false,
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

  /// The page's own title in the top bar ([KitTextRole.headline] only): a
  /// style's [KitStyleType.titleSize] sets it larger than a card's
  /// headline (Paper's big bold name). Solid's is the role's own.
  final bool pageTitle;

  /// [KitText.mono] only: what happens when the value does not fit.
  final KitMonoCut cut;

  final bool _monoSelectable;
  final _KitTextKind _kind;

  /// The app-wide text scale (A11Y-8: only kit parts clamp text scale): the
  /// person's own [scaler] passes through untouched below [max], including
  /// scales under 1.0, which people pick deliberately; only the extreme top
  /// end is capped, so a runaway scale cannot break the shell.
  ///
  /// [factor] is the app's own text size (Settings > Appearance > Text
  /// size); it multiplies the phone's scale before the cap.
  static TextScaler appScaler(
    TextScaler scaler, {
    required double max,
    double factor = 1.0,
  }) {
    final scale = scaler.scale(1) * factor;
    return TextScaler.linear(scale > max ? max : scale);
  }

  /// The app root's text scale: [appScaler] with the person's own text size
  /// ([size], Settings > Appearance > Text size) easing in on a change.
  static Widget appScale({
    required ValueListenable<KitTextSize> size,
    required double max,
    required Widget child,
  }) => _AppTextScale(size: size, max: max, child: child);

  /// Stops [child]'s text growing past [max] times its size, for a page
  /// title that must stay whole (A11Y-8: only kit parts clamp text scale).
  static Widget capScale({required double max, required Widget child}) =>
      MediaQuery.withClampedTextScaling(maxScaleFactor: max, child: child);

  /// [words] in sentence case: the first letter capitalised, nothing else
  /// changed (LOOK-15: never all capitals).
  static String sentenceCase(String words) =>
      words.isEmpty ? words : words[0].toUpperCase() + words.substring(1);

  /// The base style of [role]: size, line height, weight, tracking and (for
  /// mono) the family. No colour: [styleOf] adds it.
  static TextStyle styleFor(KitTextRole role) => switch (role) {
    // Hierarchy from size and colour, not weight (unslop 2026-10-30): the
    // titles and the small roles at 500, the secondary line at 400. A
    // Style with type of its own keeps the firmer table ([_firm]).
    KitTextRole.largeTitle => const TextStyle(
      fontSize: 28,
      height: 34 / 28,
      fontWeight: FontWeight.w500,
      letterSpacing: -0.28,
    ),
    KitTextRole.title => const TextStyle(
      fontSize: 22,
      height: 28 / 22,
      fontWeight: FontWeight.w500,
      letterSpacing: 0,
    ),
    KitTextRole.headline => const TextStyle(
      fontSize: 16,
      height: 22 / 16,
      fontWeight: FontWeight.w500,
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
      fontSize: 13,
      height: 18 / 13,
      fontWeight: FontWeight.w400,
      letterSpacing: 0,
    ),
    KitTextRole.label => const TextStyle(
      fontSize: 13,
      height: 18 / 13,
      fontWeight: FontWeight.w500,
      letterSpacing: 0,
    ),
    KitTextRole.caption => const TextStyle(
      fontSize: 13,
      height: 18 / 13,
      fontWeight: FontWeight.w500,
      letterSpacing: 0,
    ),
    KitTextRole.button => const TextStyle(
      fontSize: 16,
      height: 20 / 16,
      fontWeight: FontWeight.w500,
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
    KitTextTone.success => roles.success,
  };

  /// The resolved style of [role] in [tone] here: the theme's face (so an
  /// Arabic locale keeps its system face) with the role's metrics.
  static TextStyle styleOf(
    BuildContext context,
    KitTextRole role, {
    KitTextTone? tone,
    bool pageTitle = false,
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
    style = styled(context, role, style, arabic: arabic, pageTitle: pageTitle);
    final resolved = tone ?? defaultTone(role);
    // On a bright live background the quieter tones keep a halo of the
    // ground around their letters (KitTextHalo).
    final halo =
        resolved == KitTextTone.secondary || resolved == KitTextTone.tertiary
        ? KitTextHalo.of(context)
        : null;
    return style.copyWith(color: toneColor(roles, resolved), shadows: halo);
  }

  /// [style] under the Style's type tweaks (hook H1,
  /// [KitStyleTokens.type]): display weight and tracking for the large
  /// title, title and headline; the large title's and the [pageTitle]'s
  /// size; the body weight; mono section labels and captions. Under an Arabic locale only the weights change
  /// (the face and zero tracking stay). Solid has none, so its text is
  /// exactly as before.
  static TextStyle styled(
    BuildContext context,
    KitTextRole role,
    TextStyle style, {
    bool arabic = false,
    bool pageTitle = false,
  }) {
    final type = kitStyleSpec(KitStyles.styleOf(context)).tokens.type;
    return applyType(
      role,
      style,
      type,
      arabic: arabic,
      pageTitle: pageTitle,
      sectionLabel:
          role == KitTextRole.label &&
          (type.monoLabels || type.engravedLabels) &&
          !arabic &&
          _inHeading(context),
    );
  }

  /// The weights (and the titles' tracking) a Style with type of its own
  /// keeps ([KitStyleType.firmWeights]): the table before the 2026-10-30
  /// unslop pass, so Signal, Soft, Brutal and Paper read as they did.
  static TextStyle _firm(
    KitTextRole role,
    TextStyle style, {
    bool arabic = false,
  }) {
    final (int weight, double? tracking) = switch (role) {
      KitTextRole.largeTitle => (650, -0.8),
      KitTextRole.title => (650, -0.48),
      KitTextRole.headline || KitTextRole.label => (600, null),
      KitTextRole.caption || KitTextRole.button => (600, null),
      KitTextRole.secondary => (500, null),
      _ => (style.fontWeight?.value ?? 400, null),
    };
    return style.copyWith(
      fontWeight: FontWeight(weight),
      letterSpacing: tracking == null || arabic ? null : tracking,
    );
  }

  /// Whether the Style here keeps the firm weights
  /// ([KitStyleType.firmWeights]); Solid sets its display text lighter.
  static bool firmType(BuildContext context) =>
      kitStyleSpec(KitStyles.styleOf(context)).tokens.type.firmWeights;

  /// Whether [context] sits under a heading ([Semantics] with `header`):
  /// a section label is one, a segment's or a tab's words never are.
  static bool _inHeading(BuildContext context) {
    var heading = false;
    context.visitAncestorElements((element) {
      final widget = element.widget;
      if (widget is Semantics && widget.properties.header != null) {
        heading = widget.properties.header!;
        return false;
      }
      return true;
    });
    return heading;
  }

  /// [styled] without a context (KitTokens' own text styles use it). The
  /// label role is taken as a [sectionLabel] unless told otherwise (the
  /// tokens' one label style is the section label's).
  static TextStyle applyType(
    KitTextRole role,
    TextStyle style,
    KitStyleType type, {
    bool arabic = false,
    bool pageTitle = false,
    bool sectionLabel = true,
  }) {
    if (type.isNone || role == KitTextRole.mono) return style;
    if (type.firmWeights) style = _firm(role, style, arabic: arabic);
    final display =
        role == KitTextRole.largeTitle ||
        role == KitTextRole.title ||
        role == KitTextRole.headline;
    var weight = style.fontWeight?.value.toDouble() ?? 400;
    if (display && type.displayWeight != null) weight = type.displayWeight!;
    if (role == KitTextRole.largeTitle && type.largeTitleWeight != null) {
      weight = type.largeTitleWeight!;
    }
    if ((role == KitTextRole.body || role == KitTextRole.rowTitle) &&
        type.bodyWeight != null) {
      weight = type.bodyWeight!;
    }
    weight = weight.clamp(100, 900);
    var next = style.copyWith(fontWeight: FontWeight(weight.round()));
    if (role == KitTextRole.largeTitle && type.largeTitleSize != null) {
      final size = type.largeTitleSize!;
      final ratio = size / (style.fontSize ?? size);
      next = next.copyWith(
        fontSize: size,
        letterSpacing: (style.letterSpacing ?? 0) * ratio,
      );
    }
    if (pageTitle && role == KitTextRole.headline && type.titleSize != null) {
      // The title role's leading (28 on 22): a big name stays one block.
      final size = type.titleSize!;
      final ratio = size / (style.fontSize ?? size);
      next = next.copyWith(
        fontSize: size,
        height: (size + 6) / size,
        letterSpacing: (style.letterSpacing ?? 0) * ratio,
      );
    }
    if (arabic) return next;
    if (display &&
        role != KitTextRole.headline &&
        type.displayTracking != null) {
      next = next.copyWith(
        letterSpacing: type.displayTracking! * (next.fontSize ?? 16),
      );
    }
    if (type.monoLabels &&
        ((role == KitTextRole.label && sectionLabel) ||
            role == KitTextRole.caption)) {
      next = next.copyWith(fontFamily: 'AppMono', letterSpacing: 0);
    }
    if (type.engravedLabels && role == KitTextRole.label && sectionLabel) {
      // Engraved: 12 dp capitals tracked wide, on the label's own leading.
      next = next.copyWith(
        fontSize: 12,
        height: 18 / 12,
        fontWeight: FontWeight.w600,
        letterSpacing: 12 * .12,
      );
    }
    return next;
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

  static const _monoFamily = 'AppMono';
  static final _monoGap = RegExp('[ \u00A0]+');

  /// [text] in runs: the words in the paragraph's (mono) style, each run
  /// of spaces in the sans face.
  static TextSpan _narrowSpaces(String text) {
    final children = <InlineSpan>[];
    var at = 0;
    for (final gap in _monoGap.allMatches(text)) {
      if (gap.start > at) {
        children.add(TextSpan(text: text.substring(at, gap.start)));
      }
      children.add(
        TextSpan(
          text: gap.group(0),
          style: const TextStyle(fontFamily: 'AppSans'),
        ),
      );
      at = gap.end;
    }
    if (at < text.length) children.add(TextSpan(text: text.substring(at)));
    return TextSpan(children: children);
  }

  @override
  Widget build(BuildContext context) => KitMaxWidth.reading(
    // A value that ends its row, or words centred on a page, keep their
    // edge when the box is wider than the reading measure.
    alignment: switch (textAlign) {
      TextAlign.end => AlignmentDirectional.centerEnd,
      TextAlign.center => AlignmentDirectional.center,
      _ => AlignmentDirectional.centerStart,
    },
    child: _buildContent(context),
  );

  Widget _buildContent(BuildContext context) {
    final style = styleOf(context, role, tone: tone, pageTitle: pageTitle);
    // Words cut by a line limit say so: a limit with no overflow named ends
    // in an ellipsis, never in a silent cut (kit-v2 §8.6).
    final shownOverflow =
        overflow ?? (maxLines == null ? null : TextOverflow.ellipsis);
    switch (_kind) {
      case _KitTextKind.plain:
      case _KitTextKind.rich:
        // A caller may still pass role: mono to the plain constructors
        // (unchanged pre-v2 behaviour): the paragraph stays left to right.
        final mono = role == KitTextRole.mono;
        final resolved = tabular
            ? style.copyWith(fontFeatures: const [FontFeature.tabularFigures()])
            : style;
        // A style that sets labels in mono (Brutal, Paper; hook H1): the
        // mono face's space is a full 0.6 em cell, so the spaces between
        // the words take the sans width ("19 s", not "19  s").
        if (_kind == _KitTextKind.plain &&
            !mono &&
            resolved.fontFamily == _monoFamily &&
            text.contains(_monoGap)) {
          return Text.rich(
            _narrowSpaces(text),
            style: resolved,
            maxLines: maxLines,
            overflow: shownOverflow,
            textAlign: textAlign,
            softWrap: softWrap,
            semanticsLabel: semanticsLabel,
          );
        }
        return _kind == _KitTextKind.plain
            ? Text(
                text,
                style: resolved,
                maxLines: maxLines,
                overflow: shownOverflow,
                textAlign: textAlign,
                softWrap: softWrap,
                semanticsLabel: semanticsLabel,
                textDirection: mono ? TextDirection.ltr : null,
              )
            : Text.rich(
                span!,
                style: resolved,
                maxLines: maxLines,
                overflow: shownOverflow,
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
      // Cut at [maxLines] with "…", never a line clipped mid-glyph.
      return Text(
        text,
        style: style,
        maxLines: maxLines,
        overflow: maxLines == null ? null : TextOverflow.ellipsis,
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
          // The cut above measured this string; words that still do not fit
          // (text changed after it, a larger scale) end in an ellipsis.
          overflow: TextOverflow.ellipsis,
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

/// Applies the app's own text size (Settings > Appearance > Text size) once
/// at the app root, on top of the phone's font scale and under the [max] cap.
/// A change eases in over a short beat; the system's "remove animations"
/// makes it instant.
class _AppTextScale extends StatelessWidget {
  const _AppTextScale({
    required this.size,
    required this.max,
    required this.child,
  });

  final ValueListenable<KitTextSize> size;
  final double max;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<KitTextSize>(
      valueListenable: size,
      child: child,
      builder: (context, textSize, child) => TweenAnimationBuilder<double>(
        tween: Tween<double>(end: textSize.factor),
        duration: (MediaQuery.maybeDisableAnimationsOf(context) ?? false)
            ? Duration.zero
            : KitMotion.standard,
        curve: KitMotion.enter,
        child: child,
        builder: (context, factor, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: KitText.appScaler(
              MediaQuery.textScalerOf(context),
              max: max,
              factor: factor,
            ),
          ),
          child: child!,
        ),
      ),
    );
  }
}
