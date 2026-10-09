import 'dart:math' as math;
// One piece of a transcript that is words (docs/ux-system/kit-api/
// KitMessage.md; STATE-16, KIT-41, LOOK-26, LOOK-5, KIT-28, A11Y-5, COPY-2,
// KIT-32): the person's prompt as an end-aligned bubble, the agent's reply
// as plain prose, folded reasoning, a notice that nobody typed, and a quiet
// marker for a switch. It draws the words; the turn around it (KitTurn)
// draws the controls.
import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter/services.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../theme_roles.dart' show ThemeRoles;
import '../kit_bidi.dart';
import '../kit_buttons.dart';
import '../kit_copy.dart';
import '../kit_divider.dart';
import '../kit_image.dart';
import '../kit_layout.dart';
import '../kit_menu.dart';
import '../kit_motion.dart';
import '../kit_status_mark.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import '../motion/kit_motion_parts.dart';
import 'kit_composer_chips.dart';
import 'kit_markdown.dart';
import 'kit_step_timeline.dart';

/// Which piece of the transcript a [KitMessage] is.
enum KitMessageKind { prompt, reply, thought, notice, marker }

/// At this text scale and above a notice's action drops under its words and
/// a technical value sits under the text (A11Y: "at 200 % text the notice
/// action drops under the words"). The same threshold as KitToolRow.
const double _kWrapTextScale = 1.3;

/// How wide a [KitMessage.prompt] bubble may grow. [auto] (the default) and
/// [compact] hug the words and stop at [KitLayout.bubbleMaxShare] (85 %) of
/// the column (owner decision 04A); [full] fills the width minus
/// [KitLayout.bubbleStartInset].
enum KitBubbleWidth { auto, compact, full }

/// A transcript piece that is words (STANDARDS STATE-16, KIT-41): a prompt
/// has no control row and opens its menu on long-press; a reply has no
/// frame; step boundaries are never drawn.
///
/// States: prompt (with or without attachments), reply, thought (working,
/// folded, open), notice (quiet, failed, with action, open), marker
/// (still, working) (KIT-12).
class KitMessage extends StatelessWidget {
  /// The person's words: an end-aligned surface2 bubble, radii 20/20/6/20
  /// (the small corner at the bottom end). No control row: long-press,
  /// right-click, Shift+F10 and the semantic custom actions open [menu].
  const KitMessage.prompt({
    super.key,
    required KitMarkdown this.body, // the host passes selectable: false
    this.attachments = const <KitAttachment>[],
    this.time,
    this.menu = const <KitMenuItem>[],
    this.bubbleKey,
    this.bubbleWidth = KitBubbleWidth.auto,
  }) : kind = KitMessageKind.prompt,
       text = null,
       heading = null,
       took = null,
       expanded = null,
       onExpansionChanged = null,
       icon = null,
       technical = null,
       detail = null,
       action = null,
       failed = false,
       working = false,
       bodyKey = null,
       thoughtKey = null,
       noticeKey = null,
       markerKey = null;

  /// The agent's prose: plain body text on the prose's start edge.
  const KitMessage.reply({
    super.key,
    required KitMarkdown this.body,
    this.bodyKey,
  }) : kind = KitMessageKind.reply,
       bubbleWidth = KitBubbleWidth.auto,
       attachments = const <KitAttachment>[],
       time = null,
       menu = const <KitMenuItem>[],
       text = null,
       heading = null,
       took = null,
       expanded = null,
       onExpansionChanged = null,
       icon = null,
       technical = null,
       detail = null,
       action = null,
       failed = false,
       working = false,
       bubbleKey = null,
       thoughtKey = null,
       noticeKey = null,
       markerKey = null;

  /// Folded reasoning. Title: [heading] when the agent wrote one, else
  /// "Thinking…" while [working], "Thought for {took}" or "Thought".
  const KitMessage.thought({
    super.key,
    required KitMarkdown this.body, // role: secondary
    this.heading,
    this.working = false,
    this.took,
    this.expanded,
    this.onExpansionChanged,
    this.thoughtKey,
  }) : kind = KitMessageKind.thought,
       bubbleWidth = KitBubbleWidth.auto,
       attachments = const <KitAttachment>[],
       time = null,
       menu = const <KitMenuItem>[],
       text = null,
       icon = null,
       technical = null,
       detail = null,
       action = null,
       failed = false,
       bubbleKey = null,
       bodyKey = null,
       noticeKey = null,
       markerKey = null;

  /// Something that happened during a turn that nobody typed ("Context
  /// added", "Instructions updated", a compaction summary). It does not end
  /// the turn. One line; opens to [detail] when given.
  const KitMessage.notice({
    super.key,
    required String this.text,
    this.icon,
    this.technical,
    this.detail,
    this.action,
    this.failed = false,
    this.expanded,
    this.onExpansionChanged,
    this.noticeKey,
  }) : kind = KitMessageKind.notice,
       bubbleWidth = KitBubbleWidth.auto,
       body = null,
       attachments = const <KitAttachment>[],
       time = null,
       menu = const <KitMenuItem>[],
       heading = null,
       took = null,
       working = false,
       bubbleKey = null,
       bodyKey = null,
       thoughtKey = null,
       markerKey = null;

  /// A quiet divider row for a switch (model, agent, project) or a running
  /// compaction: hairline, centred words, hairline.
  const KitMessage.marker({
    super.key,
    required String this.text,
    this.icon,
    this.working = false,
    this.markerKey,
  }) : kind = KitMessageKind.marker,
       bubbleWidth = KitBubbleWidth.auto,
       body = null,
       attachments = const <KitAttachment>[],
       time = null,
       menu = const <KitMenuItem>[],
       heading = null,
       took = null,
       expanded = null,
       onExpansionChanged = null,
       technical = null,
       detail = null,
       action = null,
       failed = false,
       bubbleKey = null,
       bodyKey = null,
       thoughtKey = null,
       noticeKey = null;

  final KitMessageKind kind;

  /// prompt, reply, thought: the words, as the host's KitMarkdown.
  final KitMarkdown? body;

  /// [KitMessage.prompt]: how wide the bubble may grow ([KitBubbleWidth]).
  final KitBubbleWidth bubbleWidth;

  /// prompt: read-only chips under the text (KitComposerChips).
  final List<KitAttachment> attachments;

  /// prompt: shown as a caption under the bubble when non-null.
  final DateTime? time;

  /// prompt: Copy message, Edit and resend, Revert to here… Empty makes the
  /// bubble inert.
  final List<KitMenuItem> menu;

  /// notice, marker: the one line of words.
  final String? text;

  /// thought: the title the agent wrote, if any.
  final String? heading;

  /// thought: how long the reasoning took.
  final Duration? took;

  /// thought, notice: non-null makes the fold controlled.
  final bool? expanded;
  final ValueChanged<bool>? onExpansionChanged;

  /// notice, marker: an AppIconography glyph; null takes the info glyph
  /// (notice) or none (marker).
  final IconData? icon;

  /// notice: appended in mono, LTR-isolated (a skill name).
  final String? technical;

  /// notice: shown when opened; makes the line a fold.
  final KitMarkdown? detail;

  /// notice: one tertiary action on the line ("Compact again").
  final KitAction? action;

  /// notice: text1 and the neutral error glyph, never danger (LOOK-5).
  final bool failed;

  /// thought: still thinking. marker: a working mark before the words.
  final bool working;

  final Key? bubbleKey, bodyKey, thoughtKey, noticeKey, markerKey;

  @override
  Widget build(BuildContext context) => switch (kind) {
    KitMessageKind.prompt => _Prompt(message: this),
    KitMessageKind.reply => KeyedSubtree(
      key: bodyKey,
      child: Align(alignment: AlignmentDirectional.topStart, child: body),
    ),
    KitMessageKind.thought => _Thought(message: this),
    KitMessageKind.notice => _Notice(message: this),
    KitMessageKind.marker => _Marker(message: this),
  };
}

// ── Prompt ────────────────────────────────────────────────────────────────

/// The prompt bubble's fill. Dark: `surface2`. Light: `surface2` is white,
/// the page's own colour, so the bubble would vanish; it is `text1` at 7 %
/// over the ground instead, which stays visible on the ground and on white.
/// `text1` words keep far more than 7:1 on it in both.
Color kitPromptBubbleFill(ThemeRoles roles) => roles.isDark
    ? roles.surface2
    : Color.alphaBlend(roles.text1.withValues(alpha: .07), roles.ground);

/// The bubble's corners: 20 everywhere but the bottom end (6), mirrored
/// under RTL through the directional radius (VL §5 "20/20/6/20").
const BorderRadiusDirectional _bubbleRadius = BorderRadiusDirectional.only(
  topStart: Radius.circular(KitTokens.bubbleRadius),
  topEnd: Radius.circular(KitTokens.bubbleRadius),
  bottomStart: Radius.circular(KitTokens.bubbleRadius),
  bottomEnd: Radius.circular(KitTokens.bubbleTailRadius),
);

class _Prompt extends StatelessWidget {
  const _Prompt({required this.message});

  final KitMessage message;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final time = message.time;
    String? timeWords;
    if (time != null) {
      final material = MaterialLocalizations.of(context);
      timeWords = material.formatTimeOfDay(
        TimeOfDay.fromDateTime(time),
        alwaysUse24HourFormat: MediaQuery.alwaysUse24HourFormatOf(context),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : KitLayout.paneDetailMaxWidth;
        // One rule for every prompt (auto): the bubble hugs its words and
        // may use the whole width, so short and long prompts look the same
        // and a long one never wraps in a narrow column. The time stays at
        // the end edge.
        final stretch = message.bubbleWidth == KitBubbleWidth.full;
        // 85 % of the column (owner decision 04A); replies stay full width.
        final maxWidth = message.bubbleWidth == KitBubbleWidth.full
            ? math.max(0.0, width - KitLayout.bubbleStartInset)
            : (width * KitLayout.bubbleMaxShare).floorToDouble();
        return Align(
          alignment: AlignmentDirectional.centerEnd,
          child: ConstrainedBox(
            constraints: BoxConstraints(maxWidth: maxWidth),
            child: Column(
              crossAxisAlignment: stretch
                  ? CrossAxisAlignment.stretch
                  : CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                _Bubble(message: message),
                if (timeWords != null)
                  Padding(
                    padding: EdgeInsetsDirectional.only(
                      top: tokens.space1,
                      end: tokens.space2,
                    ),
                    child: Align(
                      alignment: AlignmentDirectional.centerEnd,
                      child: KitText(
                        timeWords,
                        role: KitTextRole.caption,
                        tone: KitTextTone.tertiary,
                      ),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _Bubble extends StatefulWidget {
  const _Bubble({required this.message});

  final KitMessage message;

  @override
  State<_Bubble> createState() => _BubbleState();
}

class _BubbleState extends State<_Bubble> {
  final FocusNode _focusNode = FocusNode(debugLabel: 'KitMessage prompt');
  bool _focusRingVisible = false;
  bool _menuOpen = false;

  List<KitMenuItem> get _menu => widget.message.menu;

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(_syncRing);
    FocusManager.instance.addHighlightModeListener(_onHighlightMode);
  }

  @override
  void dispose() {
    FocusManager.instance.removeHighlightModeListener(_onHighlightMode);
    _focusNode.removeListener(_syncRing);
    _focusNode.dispose();
    super.dispose();
  }

  void _onHighlightMode(FocusHighlightMode mode) => _syncRing();

  void _syncRing() {
    final visible =
        _focusNode.hasFocus &&
        FocusManager.instance.highlightMode == FocusHighlightMode.traditional;
    if (visible != _focusRingVisible && mounted) {
      setState(() => _focusRingVisible = visible);
    }
  }

  Future<void> _openMenu(Offset? position) async {
    if (_menu.isEmpty || _menuOpen) return;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    _menuOpen = true;
    try {
      await showKitMenu(
        context,
        items: _menu,
        position: position,
        semanticsLabel: l10n.kitMessageActions,
      );
    } finally {
      _menuOpen = false;
    }
  }

  void _invoke(KitMenuItem item) {
    final copyText = item.copyText;
    if (copyText != null) {
      KitCopy.copy(context, copyText());
    } else {
      item.onSelected();
    }
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    if (!node.hasPrimaryFocus || event is! KeyDownEvent) {
      return KeyEventResult.ignored;
    }
    final key = event.logicalKey;
    final isMenuKey =
        key == LogicalKeyboardKey.contextMenu ||
        (key == LogicalKeyboardKey.f10 &&
            HardwareKeyboard.instance.isShiftPressed);
    if (!isMenuKey) return KeyEventResult.ignored;
    _openMenu(null);
    return KeyEventResult.handled;
  }

  @override
  Widget build(BuildContext context) {
    final message = widget.message;
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final hasMenu = _menu.isNotEmpty;
    final attachments = message.attachments;
    // Read-only chips announce themselves only when one of them opens a
    // preview; otherwise their names join the bubble's one label.
    final chipsSpeak = attachments.any((item) => item.onOpen != null);
    final photos = [
      for (final item in attachments)
        if (_isPhoto(item)) item,
    ];
    final chips = [
      for (final item in attachments)
        if (!_isPhoto(item)) item,
    ];
    final words = message.body?.data.trim() ?? '';

    final label = [
      l10n.kitMessageYou,
      if (words.isNotEmpty) KitBidi.auto(words),
      if (!chipsSpeak)
        for (final item in attachments) item.label,
    ].join(', ');

    final content = Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.space3,
        vertical: KitLayout.bubblePaddingVertical,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (message.body case final body?) ExcludeSemantics(child: body),
          // Sent photos show as pictures, not file names; the rest stay
          // read-only chips.
          if (photos.isNotEmpty) ...[
            SizedBox(height: tokens.space2),
            ExcludeSemantics(
              excluding: !chipsSpeak,
              child: Wrap(
                spacing: tokens.space2,
                runSpacing: tokens.space2,
                children: [
                  for (final photo in photos) _PromptPhoto(attachment: photo),
                ],
              ),
            ),
          ],
          if (chips.isNotEmpty) ...[
            SizedBox(height: tokens.space2),
            ExcludeSemantics(
              excluding: !chipsSpeak,
              child: KitComposerChips.attachments(items: chips),
            ),
          ],
        ],
      ),
    );

    // "Never narrower than its content needs up to that": plain prose hugs
    // its longest line; anything with blocks or attachments takes the full
    // share.
    final body = message.body;
    final Widget sized = attachments.isNotEmpty || body == null
        ? content
        : LayoutBuilder(
            builder: (context, constraints) {
              final inner = constraints.maxWidth - 2 * tokens.space3;
              final hug = _hugWidth(context, body, inner);
              if (hug == null) return content;
              return SizedBox(width: hug + 2 * tokens.space3, child: content);
            },
          );

    final ringWidth = KitTokens.focusRingWidth(context);
    Widget bubble = DecoratedBox(
      key: message.bubbleKey,
      decoration: BoxDecoration(
        color: kitPromptBubbleFill(roles),
        borderRadius: _bubbleRadius,
      ),
      position: DecorationPosition.background,
      child: CustomPaint(
        foregroundPainter: _focusRingVisible
            ? _BubbleRingPainter(
                radius: _bubbleRadius.resolve(Directionality.of(context)),
                color: roles.accent,
                width: ringWidth,
              )
            : null,
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: hasMenu ? tokens.minTarget : 0,
          ),
          child: sized,
        ),
      ),
    );

    if (hasMenu) {
      bubble = Focus(
        focusNode: _focusNode,
        onKeyEvent: _onKey,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          excludeFromSemantics: true,
          onLongPressStart: (details) => _openMenu(details.globalPosition),
          onSecondaryTapUp: (details) => _openMenu(details.globalPosition),
          child: bubble,
        ),
      );
    }

    return Semantics(
      container: true,
      label: label,
      onLongPress: hasMenu ? () => _openMenu(null) : null,
      onLongPressHint: hasMenu ? l10n.kitTappableShowActions : null,
      customSemanticsActions: hasMenu
          ? <CustomSemanticsAction, VoidCallback>{
              for (final item in _menu.where((item) => item.enabled))
                CustomSemanticsAction(label: item.label): () => _invoke(item),
            }
          : null,
      child: bubble,
    );
  }
}

/// Lines that start a Markdown block (a heading, a list, a quote, a table,
/// code): such a prompt takes the bubble's full share.
final RegExp _blockLine = RegExp(
  r'^(\s{4}|\s*(#|>|\||```|~~~|[-*+] |\d+[.)] ))',
);

/// The width plain prose needs, up to [maxWidth]: its longest line laid out
/// in the body role at the current text scale. Null when [body] holds block
/// Markdown or there is no room to measure (the bubble then takes its full
/// share).
double? _hugWidth(BuildContext context, KitMarkdown body, double maxWidth) {
  if (!maxWidth.isFinite || maxWidth <= 0) return null;
  final data = body.data.trim();
  if (data.isEmpty) return null;
  if (data.split('\n').any(_blockLine.hasMatch)) return null;
  final painter = TextPainter(
    text: TextSpan(
      text: data,
      style: KitText.styleOf(context, body.role, tone: body.tone),
    ),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: maxWidth);
  final width = painter.width;
  painter.dispose();
  // One step of slack for rounding and inline marks (a code chip's inset).
  final tokens = KitTokens.of(context);
  return (width + tokens.space1).ceilToDouble().clamp(0.0, maxWidth);
}

/// The keyboard focus ring on the bubble's own shape: [width] in `accent`,
/// deflated to stay inside the painted area.
class _BubbleRingPainter extends CustomPainter {
  const _BubbleRingPainter({
    required this.radius,
    required this.color,
    required this.width,
  });

  final BorderRadius radius;
  final Color color;
  final double width;

  @override
  void paint(Canvas canvas, Size size) {
    final rrect = radius.toRRect(Offset.zero & size).deflate(width / 2);
    canvas.drawRRect(
      rrect,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = width
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_BubbleRingPainter old) =>
      old.radius != radius || old.color != color || old.width != width;
}

// ── Folds (thought, notice) ───────────────────────────────────────────────

/// A line that opens to a body: the tappable header with the kit's one
/// chevron, and the body under it behind the work line's hairline stroke.
/// The body appears at once and fades in over [KitMotion.quick]; nothing
/// animates its size (MOT-5).
class _Fold extends StatefulWidget {
  const _Fold({
    required this.line,
    required this.label,
    required this.body,
    required this.expanded,
    required this.onExpansionChanged,
    required this.foldKey,
    this.trailing,
    this.railIcon,
    this.railMark,
  });

  /// The header's words and glyph, without the chevron. On a
  /// [KitStepTimeline] the glyph is the rail's node instead: the caller
  /// leaves it out and names it in [railIcon] or [railMark] (neither: a dot).
  final Widget line;

  /// On a timeline: the step's glyph as a tile on the rail.
  final IconData? railIcon;

  /// On a timeline: a state mark in the node's place (the live mark).
  final Widget? railMark;

  /// The header's semantic name.
  final String label;

  /// Null: a plain line that does not open.
  final Widget? body;
  final bool? expanded;
  final ValueChanged<bool>? onExpansionChanged;
  final Key? foldKey;

  /// An action at the end of the line, outside the fold's tap region; it
  /// drops under the line at large text.
  final Widget? trailing;

  @override
  State<_Fold> createState() => _FoldState();
}

class _FoldState extends State<_Fold> with SingleTickerProviderStateMixin {
  bool _own = false;

  late final AnimationController _fade = AnimationController(
    vsync: this,
    duration: KitMotion.quick,
    value: 1,
  );
  late final CurvedAnimation _fadeCurve = CurvedAnimation(
    parent: _fade,
    curve: KitMotion.enter,
  );

  bool get _opens => widget.body != null;

  bool get _open => _opens && (widget.expanded ?? _own);

  @override
  void didUpdateWidget(covariant _Fold old) {
    super.didUpdateWidget(old);
    final wasOpen = old.body != null && (old.expanded ?? _own);
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
    final tokens = KitTokens.of(context);
    final open = _open;
    final onRail = KitStepTimeline.inside(context);

    // On a timeline the first line stays at the top of the 48 dp target, so
    // the node and the rail can be placed from the tokens alone.
    Widget lineBox(Widget child) => ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Align(
        alignment: onRail
            ? AlignmentDirectional.topStart
            : AlignmentDirectional.centerStart,
        heightFactor: onRail ? null : 1,
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space1),
          child: child,
        ),
      ),
    );

    final Widget header;
    if (_opens) {
      header = Semantics(
        expanded: open,
        child: KitTappable(
          tappableKey: widget.foldKey,
          onTap: _toggle,
          label: widget.label,
          shape: KitShape.tile,
          surface: KitSurfaceLevel.ground,
          child: lineBox(
            Row(
              children: [
                Expanded(child: widget.line),
                SizedBox(width: tokens.space1),
                _FirstLine(
                  child: ExcludeSemantics(
                    child: SizedBox.square(
                      dimension: tokens.smallIconSize,
                      child: KitSpin.chevron(expanded: open),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
    } else {
      header = Semantics(
        key: widget.foldKey,
        container: true,
        label: widget.label,
        excludeSemantics: true,
        child: lineBox(widget.line),
      );
    }

    Widget top = header;
    final trailing = widget.trailing;
    if (trailing != null) {
      final wide = MediaQuery.textScalerOf(context).scale(1) >= _kWrapTextScale;
      top = wide
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                header,
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: trailing,
                ),
              ],
            )
          : Row(
              children: [
                Expanded(child: header),
                SizedBox(width: tokens.space2),
                trailing,
              ],
            );
    }

    // One shape open or shut, so the header (and its focus) never remounts.
    final body = widget.body;
    final column = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        top,
        if (open && body != null)
          FadeTransition(
            opacity: _fadeCurve,
            child: _Body(child: body),
          ),
      ],
    );
    // The node goes around the whole step, outside the tap region (which
    // clips what it draws).
    return KitStepTimeline.node(
      context,
      lineHeight: 20,
      top: tokens.space1,
      icon: widget.railIcon,
      mark: widget.railMark,
      child: column,
    );
  }
}

/// The opened body: on the transcript's gutter, no stroke and no indent.
class _Body extends StatelessWidget {
  const _Body({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Padding(
      padding: EdgeInsetsDirectional.only(
        top: tokens.space1,
        bottom: tokens.space2,
      ),
      child: child,
    );
  }
}

/// Centres a small part (glyph, chevron) on the first `secondary` line, so
/// it stays beside the words when they wrap.
class _FirstLine extends StatelessWidget {
  const _FirstLine({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    // KitTextRole.secondary is 14/20: one line is 20 dp at 1.0 text.
    final lineHeight = MediaQuery.textScalerOf(context).scale(20);
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: lineHeight),
      child: Align(widthFactor: 1, heightFactor: 1, child: child),
    );
  }
}

/// A 20 dp glyph in `text2` on the first line, growing with text up to the
/// kit's icon cap.
class _Glyph extends StatelessWidget {
  const _Glyph(this.icon);

  final IconData icon;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final size = tokens.iconSize(context, tokens.smallIconSize).roundToDouble();
    return _FirstLine(
      child: ExcludeSemantics(
        child: SizedBox(
          width: size,
          child: Icon(icon, size: size, color: tokens.roles.text2),
        ),
      ),
    );
  }
}

// ── Thought ───────────────────────────────────────────────────────────────

class _Thought extends StatelessWidget {
  const _Thought({required this.message});

  final KitMessage message;

  static String titleFor(AppLocalizations l10n, KitMessage message) {
    final heading = message.heading?.trim();
    if (heading != null && heading.isNotEmpty) return heading;
    if (message.working) return l10n.kitMessageThinking;
    final took = message.took;
    if (took == null) return l10n.kitMessageThought;
    if (took.inSeconds < 60) {
      return l10n.kitMessageThoughtForSeconds(
        took.inSeconds < 1 ? 1 : took.inSeconds,
      );
    }
    return l10n.kitMessageThoughtForMinutes(took.inMinutes);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final title = titleFor(l10n, message);
    final onRail = KitStepTimeline.inside(context);
    final words = KitText(
      title,
      role: KitTextRole.secondary,
      tone: KitTextTone.secondary,
    );
    return _Fold(
      foldKey: message.thoughtKey,
      label: title,
      expanded: message.expanded,
      onExpansionChanged: message.onExpansionChanged,
      body: message.body,
      // On a timeline the thought is a sentence: a dot on the rail (the live
      // mark while it is still thinking) and its words.
      railMark: onRail && message.working
          ? const KitStatusMark(state: KitMarkState.working)
          : null,
      line: onRail
          ? words
          : Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const _Glyph(AppIconography.idea),
                SizedBox(width: tokens.space2),
                Expanded(child: words),
              ],
            ),
    );
  }
}

// ── Notice ────────────────────────────────────────────────────────────────

class _Notice extends StatelessWidget {
  const _Notice({required this.message});

  final KitMessage message;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final failed = message.failed;
    final text = message.text ?? '';
    final technical = message.technical?.trim();
    final hasTechnical = technical != null && technical.isNotEmpty;
    final tone = failed ? KitTextTone.primary : KitTextTone.secondary;
    final wide = MediaQuery.textScalerOf(context).scale(1) >= _kWrapTextScale;

    final label = [
      if (failed) l10n.kitMessageNoticeFailed,
      text,
      if (hasTechnical) KitBidi.ltr(technical),
    ].join(', ');

    final words = KitText(text, role: KitTextRole.secondary, tone: tone);
    final Widget wordsLine;
    if (!hasTechnical) {
      wordsLine = words;
    } else {
      final mono = KitText.mono(
        technical,
        tone: tone,
        cut: wide ? KitMonoCut.wrap : KitMonoCut.middle,
        maxLines: wide ? null : 1,
      );
      wordsLine = wide
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [words, mono],
            )
          : Row(
              children: [
                Flexible(child: words),
                SizedBox(width: tokens.space2),
                Flexible(child: mono),
              ],
            );
    }

    final glyph = failed
        ? AppIconography.error
        : (message.icon ?? AppIconography.info);

    final action = message.action;
    final onRail = KitStepTimeline.inside(context);
    return _Fold(
      foldKey: message.noticeKey,
      label: label,
      expanded: message.expanded,
      onExpansionChanged: message.onExpansionChanged,
      body: message.detail,
      railIcon: onRail ? glyph : null,
      trailing: action == null
          ? null
          : KitButton.fromAction(action, role: KitButtonRole.tertiary),
      line: onRail
          ? wordsLine
          : Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _Glyph(glyph),
                SizedBox(width: tokens.space2),
                Expanded(child: wordsLine),
              ],
            ),
    );
  }
}

// ── Marker ────────────────────────────────────────────────────────────────

class _Marker extends StatelessWidget {
  const _Marker({required this.message});

  final KitMessage message;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final text = message.text ?? '';
    final icon = message.icon;
    final Widget? lead = message.working
        ? const KitStatusMark(state: KitMarkState.working)
        : icon == null
        ? null
        : Icon(
            icon,
            size: tokens
                .iconSize(context, tokens.smallIconSize)
                .roundToDouble(),
            color: tokens.roles.text2,
          );
    return Semantics(
      key: message.markerKey,
      container: true,
      label: text,
      excludeSemantics: true,
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space2),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : KitLayout.paneDetailMaxWidth;
            return Row(
              children: [
                const Expanded(child: KitDivider()),
                SizedBox(width: tokens.space2),
                ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: (width * 0.75).floorToDouble(),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (lead != null) ...[
                        lead,
                        SizedBox(width: tokens.space1),
                      ],
                      Flexible(
                        child: KitText(
                          text,
                          role: KitTextRole.caption,
                          tone: KitTextTone.secondary,
                          textAlign: TextAlign.center,
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(width: tokens.space2),
                const Expanded(child: KitDivider()),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// A sent picture with pixels to show: drawn as a thumbnail, not a chip.
bool _isPhoto(KitAttachment item) =>
    item.kind == KitAttachmentKind.image && item.thumbnail != null;

/// One sent photo in a prompt bubble: a square thumbnail
/// ([KitLayout.promptPhotoSize]) that opens the host's full view.
class _PromptPhoto extends StatelessWidget {
  const _PromptPhoto({required this.attachment});

  final KitAttachment attachment;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final image = KitImage(
      key: attachment.thumbnailKey,
      source: attachment.thumbnail!,
      semanticsLabel: null,
      fit: KitImageFit.cover,
      shape: KitShape.tile,
      width: KitLayout.promptPhotoSize,
      height: KitLayout.promptPhotoSize,
    );
    final onOpen = attachment.onOpen;
    if (onOpen == null) {
      return Semantics(
        container: true,
        image: true,
        label: l10n.kitAttachmentImage(attachment.label),
        child: ExcludeSemantics(child: image),
      );
    }
    // Its own node, like an openable chip: the bubble's label stays the
    // words, the photo is a button of its own.
    return Semantics(
      container: true,
      child: KitTappable(
        tappableKey: attachment.chipKey,
        onTap: onOpen,
        label: l10n.kitAttachmentOpen(attachment.label),
        shape: KitShape.tile,
        child: ExcludeSemantics(child: image),
      ),
    );
  }
}
