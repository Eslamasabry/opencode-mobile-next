// KitComposerChips: the small things that ride with a message before it is
// sent (docs/ux-system/kit-api/KitComposerChips.md, cut review C16): the
// model chip (which model answers, how full its context is, a quick way to
// switch), the attachments and references a message carries, and the
// inline suggestions that appear when the person types `/` or `@`. The same
// attachment chips, read-only, show what a sent prompt carried.
//
// Three named forms of one part (NAME-1): [KitComposerChips.model],
// [KitComposerChips.attachments] and [KitComposerChips.suggestions]. The
// line of standing facts above the composer is [KitComposerStatusStrip].
//
// Seam (STANDARDS §0.4 "Every builder"): the spec builds the model chip,
// the attachment bodies and the suggestion rows on kit-KitTappable, which
// has not merged into the integration branch. Until it does, each tap zone
// here is the nearest existing kit pattern — KitChip's transparent InkWell
// zone that tells the surface it is hovered, pressed or focused — and the
// hover tooltip is a manual-trigger Tooltip as KitChip's is. The QA record
// lists this under NOT proven; swap the zones for KitTappable once it lands.
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart' show CustomSemanticsAction;
import 'package:flutter/services.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_buttons.dart';
import '../kit_chip.dart' show KitChipWrap;
import '../kit_copy.dart';
import '../kit_divider.dart';
import '../kit_image.dart';
import '../kit_layout.dart';
import '../kit_menu.dart';
import '../kit_motion.dart';
import '../kit_text.dart';
import '../kit_tappable.dart' show KitPressTracker;
import '../kit_tokens.dart';
import '../motion/kit_motion_parts.dart';

export 'kit_composer_status_strip.dart';

/// Which model answers, as far as the composer can say.
enum KitModelChipState {
  /// A model the person picked: its presented name.
  chosen,

  /// No pick: the server's default, by name when known (P6.6).
  serverDefault,

  /// No model is signed in: "Sign in to a model" opens the provider row
  /// (P7.7).
  signInNeeded,

  /// Models exist, none picked and no default: "Choose a model".
  chooseNeeded,
}

/// What a [KitAttachment] is, for its glyph and its read-only words.
enum KitAttachmentKind { image, file, folder, reference }

/// One thing a message carries. A value, so the kit never sees a
/// PromptAttachment or a ReviewReference (ARCH-1).
@immutable
class KitAttachment {
  const KitAttachment({
    required this.id,
    required this.label,
    required this.kind,
    this.detail,
    this.thumbnail,
    this.onOpen,
    this.chipKey,
    this.thumbnailKey,
  });

  /// Stable identity for keys and removal.
  final Object id;

  /// A file name, "@src/app", "main.dart:12–30" (the host isolates
  /// technical text).
  final String label;
  final KitAttachmentKind kind;

  /// "Recovered", "Not saved with your draft", "2.1 MB".
  final String? detail;

  /// Images: a small preview.
  final KitImageSource? thumbnail;

  /// Preview; null: not openable (a folder reference).
  final VoidCallback? onOpen;

  /// For example `Key('composer-reference-<id>')`.
  final Key? chipKey;

  /// For example `Key('attachment-thumbnail')`.
  final Key? thumbnailKey;
}

/// What a [KitSuggestion] would insert.
enum KitSuggestionKind { command, agent }

/// One row of [KitComposerChips.suggestions].
@immutable
class KitSuggestion {
  const KitSuggestion({
    required this.id,
    required this.label,
    required this.kind,
    this.description,
    this.key,
  });

  final Object id;

  /// "/compact" (mono, left to right) or an agent's name.
  final String label;
  final KitSuggestionKind kind;

  /// Up to two lines, wrapped at words.
  final String? description;

  /// For example `Key('inline-command-compact')`.
  final Key? key;
}

/// The composer's chips (C16). Named forms of one part (NAME-1):
/// [KitComposerChips.model], [KitComposerChips.attachments] and
/// [KitComposerChips.suggestions].
///
/// Declared (KitComposerChips.md "States"): model chosen / server default /
/// sign in needed / choose needed / context warning / narrow (glyph only);
/// attachments editable / read-only / empty; suggestions list / empty /
/// note (one line saying why nothing is offered). There
/// is no loading (the host has the data), no error of its own and no
/// disabled chip (STATE-8: a chip that cannot act is not shown).
///
/// States: empty.
class KitComposerChips extends StatelessWidget {
  /// The model chip. Tap opens the host's model picker (or, for
  /// signInNeeded, the provider row). Long-press, right-click and the
  /// semantic custom actions open [menu] (next, previous, favourites).
  const KitComposerChips.model({
    super.key,
    required String this.label,
    required VoidCallback this.onPressed,
    this.state = KitModelChipState.chosen,
    this.contextUsed,
    this.menu = const <KitMenuItem>[],
    this.chipKey,
    this.contextKey,
  }) : items = null,
       onRemove = null,
       suggestions = null,
       onSelected = null,
       onShowAll = null,
       note = null,
       stripKey = null,
       listKey = null;

  /// The attachments and references a message carries. With [onRemove]
  /// null the chips are read-only (a sent prompt, KitMessage).
  const KitComposerChips.attachments({
    super.key,
    required List<KitAttachment> this.items,
    this.onRemove,
    this.stripKey,
  }) : label = null,
       onPressed = null,
       state = KitModelChipState.chosen,
       contextUsed = null,
       menu = const <KitMenuItem>[],
       suggestions = null,
       onSelected = null,
       onShowAll = null,
       note = null,
       chipKey = null,
       contextKey = null,
       listKey = null;

  /// What `/` or `@` would insert. At most [visibleCount] rows, then
  /// "Show all" when [onShowAll] is given. With no rows, [note] is one plain
  /// line in the same place that says why nothing is offered ("The demo has
  /// no commands — …"), so a typed `/` never meets silence (B12).
  const KitComposerChips.suggestions({
    super.key,
    required List<KitSuggestion> this.suggestions,
    required ValueChanged<KitSuggestion> this.onSelected,
    this.onShowAll,
    this.note,
    this.listKey,
  }) : label = null,
       onPressed = null,
       state = KitModelChipState.chosen,
       contextUsed = null,
       menu = const <KitMenuItem>[],
       items = null,
       onRemove = null,
       chipKey = null,
       contextKey = null,
       stripKey = null;

  /// Behaviour constant, not a look token.
  static const int visibleCount = 5;

  // Fields for the three forms; each is null outside its form.
  final String? label;
  final VoidCallback? onPressed;
  final KitModelChipState state;

  /// 0–1; the chip shows it from 0.7.
  final double? contextUsed;
  final List<KitMenuItem> menu;
  final List<KitAttachment>? items;
  final ValueChanged<KitAttachment>? onRemove;
  final List<KitSuggestion>? suggestions;
  final ValueChanged<KitSuggestion>? onSelected;
  final VoidCallback? onShowAll;

  /// Suggestions form only: the one line shown when [suggestions] is empty.
  final String? note;
  final Key? chipKey, contextKey, stripKey, listKey;

  /// Whether the suggestions form has anything to show: rows, or its
  /// [note] when there are none.
  bool get hasSuggestionContent =>
      (suggestions?.isNotEmpty ?? false) ||
      (suggestions != null && note != null);

  @override
  Widget build(BuildContext context) {
    final label = this.label;
    if (label != null) {
      return _ModelChip(
        chipKey: chipKey,
        label: label,
        onPressed: onPressed!,
        state: state,
        contextUsed: contextUsed,
        menu: menu,
        contextKey: contextKey,
      );
    }
    final items = this.items;
    if (items != null) {
      return _AttachmentStrip(
        stripKey: stripKey,
        items: items,
        onRemove: onRemove,
      );
    }
    final suggestions = this.suggestions!;
    if (suggestions.isEmpty) {
      final note = this.note;
      if (note == null) return const SizedBox.shrink();
      return _SuggestionNote(key: listKey, note: note);
    }
    return _SuggestionPanel(
      key: listKey,
      suggestions: suggestions,
      onSelected: onSelected!,
      onShowAll: onShowAll,
    );
  }
}

// ── Shared look ──────────────────────────────────────────────────────────

/// The chip's surface (KitChip's pill): a [KitShape.pill] stadium in
/// `surface3` (`surface2` while hovered or pressed), at least
/// [KitTokens.chipHeight] tall, with an `accent` focus ring outside it
/// (LOOK-21).
class _Pill extends StatelessWidget {
  const _Pill({
    required this.active,
    required this.focused,
    required this.padding,
    required this.child,
  });

  final bool active;
  final bool focused;
  final EdgeInsetsDirectional padding;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    Widget pill = DecoratedBox(
      decoration: ShapeDecoration(
        color: active ? roles.surface2 : roles.surface3,
        shape: tokens.shapeOf(KitShape.pill),
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: KitTokens.chipHeight),
        child: Padding(padding: padding, child: child),
      ),
    );
    if (focused) {
      pill = DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: ShapeDecoration(
          shape: StadiumBorder(
            side: BorderSide(
              color: roles.accent,
              width: KitTokens.focusRingWidth(context),
              strokeAlign: BorderSide.strokeAlignOutside,
            ),
          ),
        ),
        child: pill,
      );
    }
    return pill;
  }
}

/// A tap zone that paints nothing itself: it fills the area it is given and
/// reports hover and keyboard focus to the surface under it; its press is
/// [press] (KitPressTracker), so the fill shows on the next frame of a touch
/// instead of after the InkWell's tap-or-scroll wait, and a quick tap is
/// still seen (KitChip's zone).
Widget _zone({
  required BuildContext context,
  required KitPressTracker press,
  required VoidCallback onTap,
  required ValueChanged<bool> onHover,
  required ValueChanged<bool> onFocus,
  VoidCallback? onLongPress,
  GestureTapUpCallback? onSecondaryTapUp,
}) => press.listen(
  context: context,
  enabled: true,
  child: Material(
    type: MaterialType.transparency,
    child: InkWell(
      onTap: () {
        press.confirm();
        onTap();
      },
      onTapCancel: press.cancel,
      onLongPress: onLongPress,
      onSecondaryTapUp: onSecondaryTapUp,
      onHover: onHover,
      onFocusChange: onFocus,
      enableFeedback: false,
      splashFactory: NoSplash.splashFactory,
      overlayColor: const WidgetStatePropertyAll(Colors.transparent),
      child: const SizedBox.expand(),
    ),
  ),
);

/// [child] as a [Flexible] when the row has a width to share, as itself in
/// an unbounded row (a host's scrolling strip), where a flex would throw.
Widget _flexibleWhenBounded(BoxConstraints constraints, Widget child) =>
    constraints.hasBoundedWidth ? Flexible(child: child) : child;

// ── Model chip ───────────────────────────────────────────────────────────

/// Whether the context meter is shown, and how.
enum _ContextLevel { none, percent, full }

class _ModelChip extends StatefulWidget {
  const _ModelChip({
    required this.label,
    required this.onPressed,
    required this.state,
    required this.contextUsed,
    required this.menu,
    required this.contextKey,
    required this.chipKey,
  });

  final String label;
  final VoidCallback onPressed;
  final KitModelChipState state;
  final double? contextUsed;
  final List<KitMenuItem> menu;
  final Key? contextKey;
  final Key? chipKey;

  @override
  State<_ModelChip> createState() => _ModelChipState();
}

class _ModelChipState extends State<_ModelChip> {
  bool _hovered = false;
  bool _focused = false;
  late final _press = KitPressTracker(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _press.dispose();
    super.dispose();
  }

  void _set(void Function() change) => setState(change);

  bool get _hasMenu => widget.menu.any((item) => item.enabled);

  Future<void> _openMenu({Offset? position}) async {
    if (!_hasMenu) return;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    await showKitMenu(
      context,
      items: widget.menu,
      position: position,
      semanticsLabel: l10n.kitModelActions,
    );
  }

  void _runItem(KitMenuItem item) {
    final copy = item.copyText;
    if (copy != null) {
      KitCopy.copy(context, copy());
    } else {
      item.onSelected();
    }
  }

  /// The chip's words by state (KitComposerChips.md "Model chip").
  String _words(AppLocalizations l10n) => switch (widget.state) {
    KitModelChipState.chosen => widget.label,
    KitModelChipState.serverDefault =>
      widget.label.trim().isEmpty ? l10n.kitModelServerDefault : widget.label,
    KitModelChipState.signInNeeded => l10n.kitModelSignIn,
    KitModelChipState.chooseNeeded => l10n.kitModelChoose,
  };

  _ContextLevel get _level {
    final used = widget.contextUsed;
    if (used == null || used < 0.7) return _ContextLevel.none;
    if (used >= 0.95) return _ContextLevel.full;
    return _ContextLevel.percent;
  }

  /// Whether [text] in [style] fits on one line in [width].
  static bool _fits(
    BuildContext context,
    String text,
    TextStyle style,
    double width,
  ) {
    if (width <= 0) return false;
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout(maxWidth: width);
    final fits = !painter.didExceedMaxLines;
    painter.dispose();
    return fits;
  }

  static double _width(BuildContext context, String text, TextStyle style) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final words = _words(l10n);
    final level = _level;
    String? contextWords;
    String? contextSpoken;
    switch (level) {
      case _ContextLevel.none:
        break;
      case _ContextLevel.full:
        contextWords = l10n.kitModelContextFull;
        contextSpoken = l10n.kitModelContextFull;
      case _ContextLevel.percent:
        // Floor, so "95 %" never shows below the "almost full" line; the
        // epsilon absorbs binary fractions (0.7 * 100 = 70.000…01).
        final percent = NumberFormat.decimalPattern(
          Localizations.localeOf(context).toString(),
        ).format((widget.contextUsed! * 100 + 1e-9).floor());
        contextWords = l10n.kitModelContext(percent);
        contextSpoken = l10n.kitModelContextLabel(percent);
    }
    final shownContext = contextWords == null ? null : '· $contextWords';
    final spoken = contextSpoken == null ? words : '$words, $contextSpoken';
    final tip = shownContext == null ? words : '$words $shownContext';

    final labelStyle = KitText.styleOf(
      context,
      KitTextRole.label,
      tone: KitTextTone.primary,
    );
    final contextStyle = labelStyle.copyWith(
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    final glyphSize = tokens.iconSize(context, tokens.smallIconSize);
    final twoLines = MediaQuery.textScalerOf(context).scale(1) >= 1.3;

    Widget body = LayoutBuilder(
      builder: (context, constraints) {
        final contextWidth = shownContext == null
            ? 0.0
            : tokens.space1 + _width(context, shownContext, contextStyle);
        final available =
            constraints.maxWidth -
            2 * tokens.space3 -
            2 * glyphSize -
            tokens.space2 -
            tokens.space1 -
            contextWidth;
        // Glyph only when the label would show fewer than six characters
        // (never a width literal, KitComposerChips.md "Adaptive").
        final chars = words.characters;
        final narrow =
            constraints.hasBoundedWidth &&
            !_fits(context, words, labelStyle, available) &&
            (chars.length <= 6 ||
                !_fits(context, '${chars.take(6)}…', labelStyle, available));

        final glyph = Icon(
          AppIconography.model,
          size: glyphSize,
          color: roles.text1,
        );
        final chevron = Icon(
          AppIconography.chevronDown,
          size: glyphSize,
          color: roles.text2,
        );
        final Widget content;
        final EdgeInsetsDirectional padding;
        if (narrow) {
          padding = EdgeInsetsDirectional.symmetric(horizontal: tokens.space1);
          // The icons grow with the text size; where the pair no longer
          // fits the 48 dp target, the model glyph stands alone (the chip
          // still opens the same choice, and says so to a screen reader).
          final pairFits =
              !constraints.hasBoundedWidth ||
              2 * glyphSize + 2 * tokens.space1 <= constraints.maxWidth;
          content = Row(
            mainAxisSize: MainAxisSize.min,
            children: [glyph, if (pairFits) chevron],
          );
        } else {
          padding = EdgeInsetsDirectional.symmetric(horizontal: tokens.space3);
          content = Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              glyph,
              SizedBox(width: tokens.space2),
              _flexibleWhenBounded(
                constraints,
                Text(
                  words,
                  style: labelStyle,
                  maxLines: twoLines ? 2 : 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              KitSwap(
                alignment: AlignmentDirectional.centerStart,
                child: shownContext == null
                    ? SizedBox.shrink(key: ValueKey(level))
                    : Padding(
                        key: ValueKey(level),
                        padding: EdgeInsetsDirectional.only(
                          start: tokens.space1,
                        ),
                        child: Text(
                          shownContext,
                          key: widget.contextKey,
                          style: contextStyle,
                          maxLines: 1,
                          softWrap: false,
                        ),
                      ),
              ),
              SizedBox(width: tokens.space1),
              chevron,
            ],
          );
        }

        final hasMenu = _hasMenu;
        Widget chip = ConstrainedBox(
          constraints: BoxConstraints(
            minWidth: tokens.minTarget,
            minHeight: tokens.minTarget,
          ),
          child: Stack(
            alignment: AlignmentDirectional.center,
            children: [
              ExcludeSemantics(
                child: _Pill(
                  active: _hovered || _press.shown,
                  focused: _focused,
                  padding: padding,
                  child: content,
                ),
              ),
              PositionedDirectional(
                start: 0,
                end: 0,
                top: 0,
                bottom: 0,
                child: Tooltip(
                  message: tip,
                  // A fine pointer's hover only (LAY-11): long-press on
                  // touch opens the model menu instead.
                  triggerMode: TooltipTriggerMode.manual,
                  excludeFromSemantics: true,
                  enableFeedback: false,
                  child: _zone(
                    context: context,
                    press: _press,
                    onTap: widget.onPressed,
                    onLongPress: hasMenu ? _openMenu : null,
                    onSecondaryTapUp: hasMenu
                        ? (details) =>
                              _openMenu(position: details.globalPosition)
                        : null,
                    onHover: (on) => _set(() => _hovered = on),
                    onFocus: (on) => _set(() => _focused = on),
                  ),
                ),
              ),
            ],
          ),
        );
        return chip;
      },
    );
    if (_hasMenu) {
      // Shift+F10 and the context-menu key open the menu from the
      // keyboard (KitMenu.md "Keyboard").
      body = Focus(
        canRequestFocus: false,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent) return KeyEventResult.ignored;
          final key = event.logicalKey;
          final shiftF10 =
              key == LogicalKeyboardKey.f10 &&
              HardwareKeyboard.instance.isShiftPressed;
          if (key == LogicalKeyboardKey.contextMenu || shiftF10) {
            _openMenu();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: body,
      );
    }
    return Semantics(
      key: widget.chipKey,
      container: true,
      button: true,
      label: spoken,
      hint: l10n.kitModelChange,
      onTap: widget.onPressed,
      customSemanticsActions: {
        for (final item in widget.menu)
          if (item.enabled)
            CustomSemanticsAction(label: item.label): () => _runItem(item),
      },
      excludeSemantics: true,
      child: body,
    );
  }
}

// ── Attachments ──────────────────────────────────────────────────────────

/// One attachment in the strip, with its fade.
class _StripEntry {
  _StripEntry(this.item, this.controller);

  KitAttachment item;
  AnimationController? controller;
  bool leaving = false;
}

/// The wrapping row of attachment chips. Chips appear and leave with a
/// paint fade on `KitMotion.quick`, never a size animation (a leaving chip
/// keeps its place, ignores touches and is skipped by screen readers until
/// it has faded). The first build and reduced motion show changes at once.
class _AttachmentStrip extends StatefulWidget {
  const _AttachmentStrip({
    required this.stripKey,
    required this.items,
    required this.onRemove,
  });

  final Key? stripKey;
  final List<KitAttachment> items;
  final ValueChanged<KitAttachment>? onRemove;

  @override
  State<_AttachmentStrip> createState() => _AttachmentStripState();
}

class _AttachmentStripState extends State<_AttachmentStrip>
    with TickerProviderStateMixin {
  late List<_StripEntry> _entries = [
    for (final item in widget.items) _StripEntry(item, null),
  ];

  AnimationController _controller() =>
      AnimationController(vsync: this, duration: KitMotion.quick);

  @override
  void didUpdateWidget(covariant _AttachmentStrip old) {
    super.didUpdateWidget(old);
    final reduced = KitMotion.reduced(context);
    final ids = {for (final item in widget.items) item.id};
    final byId = {for (final entry in _entries) entry.item.id: entry};
    final next = <_StripEntry>[];

    void leave(_StripEntry entry) {
      if (reduced) {
        entry.controller?.dispose();
        return;
      }
      if (!entry.leaving) {
        entry.leaving = true;
        final controller = entry.controller ??= _controller()..value = 1;
        controller.reverse().whenCompleteOrCancel(() {
          if (!mounted || !entry.leaving) return;
          setState(() => _entries.remove(entry));
          controller.dispose();
          entry.controller = null;
        });
      }
      next.add(entry);
    }

    var cursor = 0;
    for (final item in widget.items) {
      final existing = byId[item.id];
      if (existing == null) {
        final controller = reduced ? null : (_controller()..forward());
        next.add(_StripEntry(item, controller));
        continue;
      }
      final at = _entries.indexOf(existing);
      for (; cursor < at; cursor++) {
        final gone = _entries[cursor];
        if (!ids.contains(gone.item.id)) leave(gone);
      }
      if (cursor <= at) cursor = at + 1;
      existing.item = item;
      if (existing.leaving) {
        existing.leaving = false;
        if (reduced) {
          existing.controller?.value = 1;
        } else {
          existing.controller?.forward();
        }
      }
      next.add(existing);
    }
    for (; cursor < _entries.length; cursor++) {
      final gone = _entries[cursor];
      if (!ids.contains(gone.item.id)) leave(gone);
    }
    _entries = next;
  }

  @override
  void dispose() {
    for (final entry in _entries) {
      entry.controller?.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_entries.isEmpty) return SizedBox.shrink(key: widget.stripKey);
    final onRemove = widget.onRemove;
    return KitChipWrap(
      key: widget.stripKey,
      children: [
        for (final entry in _entries)
          _fade(
            entry,
            _AttachmentChip(
              key: entry.item.chipKey,
              attachment: entry.item,
              onRemove: onRemove == null ? null : () => onRemove(entry.item),
            ),
          ),
      ],
    );
  }

  Widget _fade(_StripEntry entry, Widget chip) {
    final controller = entry.controller;
    return KeyedSubtree(
      key: ValueKey<Object>(('kit-composer-chips-attachment', entry.item.id)),
      child: IgnorePointer(
        ignoring: entry.leaving,
        child: ExcludeSemantics(
          excluding: entry.leaving,
          child: controller == null
              ? chip
              : FadeTransition(
                  opacity: CurvedAnimation(
                    parent: controller,
                    curve: KitMotion.enter,
                    reverseCurve: KitMotion.exit.flipped,
                  ),
                  child: chip,
                ),
        ),
      ),
    );
  }
}

enum _Zone { body, remove }

class _AttachmentChip extends StatefulWidget {
  const _AttachmentChip({
    super.key,
    required this.attachment,
    required this.onRemove,
  });

  final KitAttachment attachment;
  final VoidCallback? onRemove;

  @override
  State<_AttachmentChip> createState() => _AttachmentChipState();
}

class _AttachmentChipState extends State<_AttachmentChip> {
  final _hovered = <_Zone>{};
  final _focused = <_Zone>{};
  late final _press = {
    for (final zone in _Zone.values)
      zone: KitPressTracker(() {
        if (mounted) setState(() {});
      }),
  };

  @override
  void dispose() {
    for (final press in _press.values) {
      press.dispose();
    }
    super.dispose();
  }

  void _mark(Set<_Zone> set, _Zone zone, bool on) {
    final changed = on ? set.add(zone) : set.remove(zone);
    if (changed) setState(() {});
  }

  static IconData _glyph(KitAttachmentKind kind) => switch (kind) {
    KitAttachmentKind.image => AppIconography.image,
    KitAttachmentKind.file => AppIconography.file,
    KitAttachmentKind.folder => AppIconography.folders,
    KitAttachmentKind.reference => AppIconography.bookmark,
  };

  static String _readOnly(AppLocalizations l10n, KitAttachment a) =>
      switch (a.kind) {
        KitAttachmentKind.image => l10n.kitAttachmentImage(a.label),
        KitAttachmentKind.file => l10n.kitAttachmentFile(a.label),
        KitAttachmentKind.folder => l10n.kitAttachmentFolder(a.label),
        KitAttachmentKind.reference => l10n.kitAttachmentReference(a.label),
      };

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final a = widget.attachment;
    final onOpen = a.onOpen;
    final onRemove = widget.onRemove;
    final glyphSize = tokens.iconSize(context, tokens.smallIconSize);

    final thumbnail = a.thumbnail;
    final Widget leading = thumbnail != null
        ? KitImage(
            key: a.thumbnailKey,
            source: thumbnail,
            semanticsLabel: null,
            fit: KitImageFit.cover,
            shape: KitShape.tile,
            width: tokens.iconTileSize,
            height: tokens.iconTileSize,
          )
        : Icon(_glyph(a.kind), size: glyphSize, color: roles.text1);
    final detail = a.detail;
    final content = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        leading,
        SizedBox(width: tokens.space2),
        Flexible(
          child: detail == null
              ? KitText.mono(a.label, cut: KitMonoCut.middle)
              : _LabelAndDetail(label: a.label, detail: detail),
        ),
        if (onRemove != null) ...[
          // The gap that keeps the body's target 8 dp from the ×'s (LAY-9),
          // then the × centred in its own 48 dp target.
          SizedBox(width: tokens.space2),
          SizedBox(
            width: tokens.minTarget,
            child: Center(
              child: Icon(
                AppIconography.close,
                size: glyphSize,
                color: roles.text2,
              ),
            ),
          ),
        ],
      ],
    );
    // A removable chip ends in the ×'s own 48 dp slot, so no end padding.
    final start = thumbnail != null ? tokens.space1 : tokens.space3;
    final padding = onRemove != null
        ? EdgeInsetsDirectional.only(start: start)
        : EdgeInsetsDirectional.only(start: start, end: tokens.space3);

    final bodySemantics = Semantics(
      container: true,
      button: onOpen != null,
      label: onOpen != null
          ? l10n.kitAttachmentOpen(a.label)
          : _readOnly(l10n, a),
      value: detail,
      onTap: onOpen,
      excludeSemantics: true,
      child: onOpen == null
          ? const SizedBox.expand()
          : _zone(
              context: context,
              press: _press[_Zone.body]!,
              onTap: onOpen,
              onHover: (on) => _mark(_hovered, _Zone.body, on),
              onFocus: (on) => _mark(_focused, _Zone.body, on),
            ),
    );
    final removeLabel = l10n.kitChipRemove(a.label);

    Widget chip = ConstrainedBox(
      constraints: BoxConstraints(
        // The body's own target stays 48 dp wide beside the ×'s (LAY-9).
        minWidth: onRemove != null
            ? 2 * tokens.minTarget + tokens.space2
            : tokens.minTarget,
        minHeight: tokens.minTarget,
      ),
      child: Stack(
        alignment: AlignmentDirectional.centerStart,
        children: [
          ExcludeSemantics(
            child: _Pill(
              active:
                  _hovered.isNotEmpty ||
                  _press.values.any((press) => press.shown),
              focused: _focused.isNotEmpty,
              padding: padding,
              child: content,
            ),
          ),
          PositionedDirectional(
            start: 0,
            top: 0,
            bottom: 0,
            end: onRemove != null ? tokens.minTarget + tokens.space2 : 0,
            child: bodySemantics,
          ),
          if (onRemove != null)
            PositionedDirectional(
              end: 0,
              top: 0,
              bottom: 0,
              width: tokens.minTarget,
              child: Semantics(
                container: true,
                button: true,
                label: removeLabel,
                onTap: onRemove,
                excludeSemantics: true,
                child: _zone(
                  context: context,
                  press: _press[_Zone.remove]!,
                  onTap: onRemove,
                  onHover: (on) => _mark(_hovered, _Zone.remove, on),
                  onFocus: (on) => _mark(_focused, _Zone.remove, on),
                ),
              ),
            ),
        ],
      ),
    );
    if (onRemove != null) {
      // Delete or Backspace on a focused attachment (body or ×) removes it.
      // canRequestFocus: false keeps this node out of Tab order.
      chip = Focus(
        canRequestFocus: false,
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent) return KeyEventResult.ignored;
          final key = event.logicalKey;
          if (key == LogicalKeyboardKey.delete ||
              key == LogicalKeyboardKey.backspace) {
            onRemove();
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: chip,
      );
    }
    return chip;
  }
}

// ── Suggestions ──────────────────────────────────────────────────────────

/// [text] cut at the last word that still fits [maxLines] lines of
/// [maxWidth] with an end ellipsis (never mid-word). A single word longer
/// than the space falls back to the framework's character ellipsis.
String _cutAtWord(
  String text,
  TextStyle style,
  double maxWidth,
  int maxLines,
  TextScaler scaler,
  TextDirection direction,
) {
  bool fits(String candidate) {
    final painter = TextPainter(
      text: TextSpan(text: candidate, style: style),
      textDirection: direction,
      textScaler: scaler,
      maxLines: maxLines,
    )..layout(maxWidth: maxWidth);
    final ok = !painter.didExceedMaxLines;
    painter.dispose();
    return ok;
  }

  if (!maxWidth.isFinite || fits(text)) return text;
  final cuts = [
    for (final match in RegExp(r'\s+').allMatches(text))
      if (match.start > 0) match.start,
  ];
  var low = 0;
  var high = cuts.length - 1;
  String? best;
  while (low <= high) {
    final mid = (low + high) >> 1;
    final candidate = '${text.substring(0, cuts[mid]).trimRight()}…';
    if (fits(candidate)) {
      best = candidate;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }
  return best ?? text;
}

/// The suggestions form's one plain line when nothing can be offered: the
/// panel's surface and inset, no tap target, read out when it appears.
class _SuggestionNote extends StatelessWidget {
  const _SuggestionNote({super.key, required this.note});

  final String note;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Semantics(
      container: true,
      liveRegion: true,
      child: DecoratedBox(
        decoration: ShapeDecoration(
          color: tokens.roles.surface2,
          shape: tokens.shapeOf(KitShape.panel),
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: tokens.minTarget),
          child: Padding(
            padding: EdgeInsetsDirectional.symmetric(
              horizontal: tokens.space4,
              vertical: tokens.space2,
            ),
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              widthFactor: 1,
              heightFactor: 1,
              child: KitText(note, role: KitTextRole.secondary),
            ),
          ),
        ),
      ),
    );
  }
}

class _SuggestionPanel extends StatelessWidget {
  const _SuggestionPanel({
    super.key,
    required this.suggestions,
    required this.onSelected,
    required this.onShowAll,
  });

  final List<KitSuggestion> suggestions;
  final ValueChanged<KitSuggestion> onSelected;
  final VoidCallback? onShowAll;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final visible = suggestions.take(KitComposerChips.visibleCount).toList();
    final showAll =
        onShowAll != null && suggestions.length > KitComposerChips.visibleCount;
    final shape = tokens.shapeOf(KitShape.panel);
    return Semantics(
      container: true,
      label: l10n.kitSuggestionsLabel,
      explicitChildNodes: true,
      child: DecoratedBox(
        decoration: ShapeDecoration(color: tokens.roles.surface2, shape: shape),
        child: ClipPath(
          clipper: ShapeBorderClipper(shape: shape),
          child: FocusTraversalGroup(
            child: Shortcuts(
              // Arrow keys move between rows as Tab does; Enter selects
              // (the app's default ActivateIntent).
              shortcuts: const {
                SingleActivator(LogicalKeyboardKey.arrowDown):
                    NextFocusIntent(),
                SingleActivator(LogicalKeyboardKey.arrowUp):
                    PreviousFocusIntent(),
              },
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (var i = 0; i < visible.length; i++) ...[
                    if (i > 0) const KitDivider(),
                    _SuggestionRow(
                      key: visible[i].key,
                      suggestion: visible[i],
                      onSelected: onSelected,
                    ),
                  ],
                  if (showAll) ...[
                    const KitDivider(),
                    Padding(
                      padding: EdgeInsetsDirectional.symmetric(
                        horizontal: tokens.space2,
                      ),
                      child: Align(
                        alignment: AlignmentDirectional.centerStart,
                        child: KitButton.tertiary(
                          label: l10n.kitSuggestionsShowAll,
                          onPressed: onShowAll,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SuggestionRow extends StatefulWidget {
  const _SuggestionRow({
    super.key,
    required this.suggestion,
    required this.onSelected,
  });

  final KitSuggestion suggestion;
  final ValueChanged<KitSuggestion> onSelected;

  @override
  State<_SuggestionRow> createState() => _SuggestionRowState();
}

class _SuggestionRowState extends State<_SuggestionRow> {
  bool _hovered = false;
  bool _focused = false;
  late final _press = KitPressTracker(() {
    if (mounted) setState(() {});
  });

  @override
  void dispose() {
    _press.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final s = widget.suggestion;
    final description = s.description;
    final wide = KitLayout.windowOf(context).isWide;
    final descriptionStyle = KitText.styleOf(context, KitTextRole.secondary);
    final Widget label = s.kind == KitSuggestionKind.command
        ? KitText.mono(s.label, cut: KitMonoCut.end)
        : KitText(
            s.label,
            role: KitTextRole.rowTitle,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          );

    Widget content = label;
    if (description != null) {
      content = LayoutBuilder(
        builder: (context, constraints) {
          final scaler = MediaQuery.textScalerOf(context);
          final direction = Directionality.of(context);
          if (wide) {
            // From expanded, the description sits beside the label when
            // both fit on one line.
            final labelStyle = KitText.styleOf(
              context,
              s.kind == KitSuggestionKind.command
                  ? KitTextRole.mono
                  : KitTextRole.rowTitle,
            );
            double width(String text, TextStyle style) {
              final painter = TextPainter(
                text: TextSpan(text: text, style: style),
                textDirection: direction,
                textScaler: scaler,
                maxLines: 1,
              )..layout();
              final w = painter.width;
              painter.dispose();
              return w;
            }

            final together =
                width(s.label, labelStyle) +
                tokens.space3 +
                width(description, descriptionStyle);
            if (together <= constraints.maxWidth) {
              return Row(
                children: [
                  label,
                  SizedBox(width: tokens.space3),
                  Expanded(
                    child: KitText(
                      description,
                      role: KitTextRole.secondary,
                      maxLines: 1,
                    ),
                  ),
                ],
              );
            }
          }
          final cut = _cutAtWord(
            description,
            descriptionStyle,
            constraints.maxWidth,
            2,
            scaler,
            direction,
          );
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              label,
              KitText(
                cut,
                role: KitTextRole.secondary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          );
        },
      );
    }

    Widget row = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(
          horizontal: tokens.space4,
          vertical: tokens.space2,
        ),
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          widthFactor: 1,
          heightFactor: 1,
          child: content,
        ),
      ),
    );
    row = DecoratedBox(
      decoration: BoxDecoration(
        color: _hovered || _press.shown ? roles.surface3 : null,
      ),
      position: DecorationPosition.background,
      child: row,
    );
    if (_focused) {
      row = DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: BoxDecoration(
          border: Border.all(
            color: roles.accent,
            width: KitTokens.focusRingWidth(context),
          ),
        ),
        child: row,
      );
    }
    return Semantics(
      container: true,
      button: true,
      label: description == null ? s.label : '${s.label}, $description',
      onTap: () => widget.onSelected(s),
      excludeSemantics: true,
      child: Stack(
        children: [
          row,
          PositionedDirectional(
            start: 0,
            end: 0,
            top: 0,
            bottom: 0,
            child: _zone(
              context: context,
              press: _press,
              onTap: () => widget.onSelected(s),
              onHover: (on) => setState(() => _hovered = on),
              onFocus: (on) => setState(() => _focused = on),
            ),
          ),
        ],
      ),
    );
  }
}

/// An attachment's label and detail on one line. The label takes the room
/// it needs first; the detail keeps its own width, and is capped at two
/// fifths of the line only when the pair does not fit. Only then is the
/// label middle-cut (and the detail end-cut), so a file name is never cut
/// while the chip still has room for it (AUTO-4, A11Y-8).
class _LabelAndDetail extends StatelessWidget {
  const _LabelAndDetail({required this.label, required this.detail});

  final String label;
  final String detail;

  /// The width [text] paints at in [role], measured as the paragraph
  /// paints it (the ambient text style, bold text, scale and locale), so a
  /// value given exactly this width is never cut.
  static double _need(
    BuildContext context,
    String text,
    KitTextRole role,
    TextDirection direction,
  ) {
    var style = DefaultTextStyle.of(
      context,
    ).style.merge(KitText.styleOf(context, role));
    if (MediaQuery.boldTextOf(context)) {
      style = style.merge(const TextStyle(fontWeight: FontWeight.bold));
    }
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: direction,
      textScaler: MediaQuery.textScalerOf(context),
      locale: Localizations.maybeLocaleOf(context),
      maxLines: 1,
    )..layout();
    final width = painter.width.ceilToDouble();
    painter.dispose();
    return width;
  }

  @override
  Widget build(BuildContext context) {
    final gap = KitTokens.of(context).space2;
    return LayoutBuilder(
      builder: (context, constraints) {
        // A mono value always paints left to right (KitText.mono).
        final labelNeed = _need(
          context,
          label,
          KitTextRole.mono,
          TextDirection.ltr,
        );
        final detailNeed = _need(
          context,
          detail,
          KitTextRole.secondary,
          Directionality.of(context),
        );
        final room = math.max(0.0, constraints.maxWidth - gap);
        var labelWidth = labelNeed;
        var detailWidth = detailNeed;
        if (labelNeed + detailNeed > room) {
          detailWidth = math.min(
            detailNeed,
            math.max((room * 2 / 5).floorToDouble(), room - labelNeed),
          );
          labelWidth = math.max(0.0, math.min(labelNeed, room - detailWidth));
        }
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(
              width: labelWidth,
              child: KitText.mono(label, cut: KitMonoCut.middle),
            ),
            SizedBox(width: gap),
            SizedBox(
              width: detailWidth,
              child: KitText(
                detail,
                role: KitTextRole.secondary,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                softWrap: false,
              ),
            ),
          ],
        );
      },
    );
  }
}
