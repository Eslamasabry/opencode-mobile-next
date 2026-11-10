// KitComposerStatusStrip: the line above the composer's field (docs/ux-system/
// kit-api/KitComposer.md). Split from kit_composer_chips.dart to keep that
// file under the size limit; it is exported from there.
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../app_iconography.dart';
import '../kit_chip.dart';
import '../kit_frost.dart';
import '../kit_max_width.dart';
import '../kit_menu.dart' show KitMenuItem;
import '../motion/kit_motion_parts.dart';
import '../kit_scroll_fade.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tip.dart';
import '../kit_tokens.dart';
import 'kit_composer_chips.dart';
import 'kit_composer.dart' show KitComposerTip;

/// The line above the composer's field: standing facts about a
/// conversation's run as chips (automatic approvals, context waiting for the
/// agent's next step, "Background"), and the model chip at the end (owner
/// decision 2 Oct, 14A: the field gets its full width). One line while it
/// fits; at a narrow width or a large text size the line scrolls sideways
/// inside itself, never wrapping and never overlapping. Draws
/// nothing when there are neither [chips] nor a [model].
///
/// Stop, at the trailing end of the strip while a turn runs (owner, 30 Oct
/// 2026: always visible above the input bar, whether or not the field has
/// text). One control: it stops this session and keeps the draft. [stuck]
/// turns it amber.
class KitComposerStop {
  const KitComposerStop({
    required this.label,
    required this.spoken,
    required this.onPressed,
    this.stuck = false,
    this.working = false,
    this.chipKey,
    this.menu = const [],
    this.tip,
  });

  /// The word on the chip ("Stop").
  final String label;

  /// What a screen reader says ("Stop Claude Code").
  final String spoken;
  final VoidCallback onPressed;

  /// The step looks stuck: the attention tone.
  final bool stuck;

  /// The stop is on its way: taps do nothing.
  final bool working;
  final Key? chipKey;

  /// A hold (or right-click) on Stop opens these ("Stop after this step"),
  /// where the agent can. A tap still stops at once. Empty: no menu.
  final List<KitMenuItem> menu;

  /// The teaching tip over Stop, shown once and dismissed by the host.
  final KitComposerTip? tip;
}

/// States: empty (nothing drawn); stop shown (the trailing Stop chip).
class KitComposerStatusStrip extends StatelessWidget {
  const KitComposerStatusStrip({
    super.key,
    required this.chips,
    this.model,
    this.stop,
    this.stripKey,
  });

  /// Kit chips ([KitChip] and its forms), in the host's order.
  final List<Widget> chips;

  /// [KitComposerChips.model], at the end of the line.
  final Widget? model;

  /// The Stop at the line's trailing end, fading in and out ([KitSwap]).
  /// It never shrinks or scrolls away: the chips and the model chip give
  /// their room up first. Null: no Stop.
  final KitComposerStop? stop;

  /// On the scrolling line, for tests (TEST-5).
  final Key? stripKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final stop = this.stop;
    final body = _line(context);
    if (stop == null && body == null) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    // One tree whether or not a Stop shows: the line keeps its place while
    // the Stop fades in and out ([KitSwap]) at the trailing edge. The Stop
    // is a fixed-width sibling, so the chips and the model chip give their
    // room up first and the Stop never shrinks or scrolls away.
    return Row(
      key: const ValueKey('kit-composer-status-strip-row'),
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(child: body ?? const SizedBox.shrink()),
        KitSwap(
          child: stop == null
              ? SizedBox(
                  key: const ValueKey('kit-composer-no-stop'),
                  width: tokens.gutter,
                )
              : Padding(
                  key: const ValueKey('kit-composer-stop-chip'),
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.space2,
                    end: tokens.gutter,
                  ),
                  child: KitTip(
                    key: const ValueKey('kit-composer-stop-tip'),
                    tipKey: const ValueKey('kit-composer-stop-tip-bubble'),
                    message: stop.tip?.message ?? '',
                    visible: stop.tip != null,
                    onDismissed: () => stop.tip?.onDismissed(),
                    child: KitTappable(
                      tappableKey: stop.chipKey,
                      shape: KitShape.button,
                      label: stop.spoken,
                      tooltip: stop.spoken,
                      disabledReason: stop.working ? stop.spoken : null,
                      menu: stop.working ? const [] : stop.menu,
                      onTap: stop.working ? null : stop.onPressed,
                      // The chip is the look; the tappable is the 48 dp
                      // target, the hold menu and the screen reader's name.
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          minWidth: tokens.minTarget,
                          minHeight: tokens.minTarget,
                        ),
                        child: Center(
                          child: ExcludeSemantics(
                            child: KitChip(
                              label: stop.label,
                              icon: AppIconography.stop,
                              tone: stop.stuck
                                  ? KitChipTone.attention
                                  : KitChipTone.neutral,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
        ),
      ],
    );
  }

  /// The chips and the model chip as one line; null when there are none.
  /// The trailing gutter belongs to the Stop slot beside it.
  Widget? _line(BuildContext context) {
    final model = this.model;
    if (chips.isEmpty && model == null) return null;
    final tokens = KitTokens.of(context);
    // On the composer's own edges, so the chips line up with the box under
    // them.
    final pad = EdgeInsetsDirectional.only(start: tokens.gutter);
    // One line, always: when the chips do not fit (large text, narrow
    // screen) the line scrolls sideways inside itself instead of wrapping.
    Widget line(
      List<Widget> kids, {
      Widget? end,
      required Key? key,
    }) => LayoutBuilder(
      builder: (context, box) {
        // No chip is wider than the line; a longer label ends in an
        // ellipsis inside its own chip.
        Widget held(Widget chip) => ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: math.max(0, box.maxWidth - tokens.gutter),
          ),
          child: chip,
        );
        final start = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < kids.length; i++) ...[
              if (i > 0) SizedBox(width: tokens.space2),
              held(kids[i]),
            ],
          ],
        );
        if (end != null) {
          // The chips keep their own width while the model chip still
          // has room for its name (_modelChipLabelledMin),
          // and at least half the line; more scrolls sideways inside it.
          // The model chip takes what is left and ends in an ellipsis,
          // so "Ask first · Auto Accept" stays whole beside a long
          // model name. Nothing is cut at the screen's edge.
          return Padding(
            padding: pad,
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                ConstrainedBox(
                  constraints: BoxConstraints(
                    maxWidth: math.max(
                      (box.maxWidth - tokens.gutter) * 0.5,
                      box.maxWidth -
                          tokens.gutter -
                          tokens.space2 -
                          _modelChipLabelledMin(context),
                    ),
                  ),
                  // A chip running past the line's end fades there,
                  // never a hard cut mid-word ("Auto Ac" at 1.3x text,
                  // impeccable audit 2026-10-28 #6).
                  child: KitScrollFade(
                    child: SingleChildScrollView(
                      key: key,
                      scrollDirection: Axis.horizontal,
                      child: start,
                    ),
                  ),
                ),
                SizedBox(width: tokens.space2),
                Flexible(child: end),
              ],
            ),
          );
        }
        // On the chat composer's glass pane the line starts (and fades)
        // at the pane's inner edge, never across it
        // (KitFrost.lineInsetOf); the chips still start on the gutter.
        final inset = KitFrost.lineInsetOf(context).clamp(0.0, tokens.gutter);
        return Padding(
          padding: EdgeInsetsDirectional.only(start: inset),
          child: KitScrollFade(
            width: KitScrollFade.defaultWidth(tokens) + tokens.gutter - inset,
            child: SingleChildScrollView(
              key: key,
              scrollDirection: Axis.horizontal,
              // The padding scrolls with the chips, so a chip cut by the
              // edge is cut at the line's edge, not inside the margin.
              padding: EdgeInsetsDirectional.only(start: tokens.gutter - inset),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minWidth: math.max(0, box.maxWidth - tokens.gutter),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [start],
                ),
              ),
            ),
          ),
        );
      },
    );
    if (model == null) {
      return line(chips, key: stripKey);
    }
    if (chips.isEmpty) {
      return Padding(
        key: stripKey,
        padding: pad,
        child: Align(alignment: AlignmentDirectional.centerEnd, child: model),
      );
    }
    // The chips first, the model chip at the end of the same line.
    return line(chips, end: model, key: stripKey);
  }
}

/// The narrowest the model chip can be and still show its name: six
/// characters and an ellipsis beside its two glyphs. Narrower, it stands
/// as a glyph alone (the "Adaptive" rule of KitComposerChips' model chip;
/// keep the two in step). The line leaves the model chip at least this.
double _modelChipLabelledMin(BuildContext context) {
  final tokens = KitTokens.of(context);
  final glyphSize = tokens.iconSize(context, tokens.smallIconSize);
  final painter = TextPainter(
    text: TextSpan(
      text: '000000…',
      style: KitText.styleOf(
        context,
        KitTextRole.secondary,
        tone: KitTextTone.primary,
      ),
    ),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
    maxLines: 1,
  )..layout();
  final label = painter.width;
  painter.dispose();
  return 2 * tokens.space3 +
      2 * glyphSize +
      tokens.space2 +
      tokens.space1 +
      label.ceilToDouble() +
      1;
}
