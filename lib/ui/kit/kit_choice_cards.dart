import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_choice_list.dart' show KitChoice;
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

/// Two or three large answers to one question, a card each, stacked: an
/// icon, the answer in title size and one calm line of what it costs or
/// means ("About 4 minutes · 208 MB"). Built for a first page's one
/// question ("Where should your agent run?"), where the answer is chosen
/// first and the page's one main button acts on it: a tap chooses, it does
/// not go anywhere.
///
/// The chosen card has its icon in mint, a filled mint check and the
/// style's chosen surface (Solid: a mint ring and a breath of mint); the
/// others a muted icon and an empty ring, so the choice reads by shape as
/// well as colour (never a solid mint card). The surface changes over the
/// style's state fade and the check lands over [KitMotion.standard];
/// reduced motion changes at once. A card whose line changes (a setup that
/// moved on) brings the new line in with a short rise.
///
/// [KitChoice.leading] is the card's icon: a [KitIcon], drawn large and
/// tinted by the card. Use [KitChoiceTiles] for a sheet's few recommended
/// choices that apply on tap, and [KitChoiceList] for a list of equals.
///
/// States: disabled, working.
///
/// Disabled: the reason first on the line, muted, no tap. Working: [busy]
/// shows a small progress mark where the check goes and every card waits.
/// Titles and lines wrap (200 % text, 320 dp), never truncate.
class KitChoiceCards<T> extends StatelessWidget {
  const KitChoiceCards({
    super.key,
    required this.choices,
    required this.selected,
    required this.onSelected,
    this.busy,
    this.semanticsLabel,
  });

  final List<KitChoice<T>> choices;

  /// The chosen value; its card has the ring and the check.
  final T? selected;
  final ValueChanged<T> onSelected;

  /// The value whose action is under way: its card shows progress, every
  /// card waits.
  final T? busy;

  /// The question the cards answer, for screen readers.
  final String? semanticsLabel;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final waiting = busy != null;
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: semanticsLabel,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < choices.length; i++) ...[
            if (i > 0) SizedBox(height: tokens.space4),
            _KitChoiceCard<T>(
              key: choices[i].key,
              choice: choices[i],
              selected: choices[i].value == selected,
              busy: waiting && choices[i].value == busy,
              onTap: !choices[i].enabled || waiting
                  ? null
                  : () => onSelected(choices[i].value),
            ),
          ],
        ],
      ),
    );
  }
}

class _KitChoiceCard<T> extends StatelessWidget {
  const _KitChoiceCard({
    super.key,
    required this.choice,
    required this.selected,
    required this.busy,
    required this.onTap,
  });

  final KitChoice<T> choice;
  final bool selected;
  final bool busy;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final still = KitMotion.reduced(context);
    final glide = still ? Duration.zero : KitMotion.quick;
    final enabled = choice.enabled;
    final large = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final line = [
      if (!enabled) ?choice.disabledReason,
      if (choice.supporting case final text? when text.isNotEmpty) text,
    ].join(' · ');
    final markSize = tokens.iconTileSize - tokens.space1;
    // Hook H3: on the chosen card the words take the colour the
    // style says reads on its chosen face, so a style that sets it in
    // reverse keeps them legible; elsewhere the part's own tones.
    // Resolved inside the card, so the words keep its plate (hook H10).
    Widget text(String text, KitTextRole role, KitTextTone tone) => selected
        ? Builder(
            builder: (context) {
              final own = KitText.styleOf(context, role, tone: tone);
              final ink = KitSurfaceLook.wordsOn(
                context,
                fallback: own.color!,
                role: KitSurfaceRole.content,
                level: KitSurfaceLevel.surface2,
                selected: true,
              );
              return Text(text, style: own.copyWith(color: ink));
            },
          )
        : KitText(text, role: role, tone: tone);

    // The empty ring of an answer not chosen, under the check that lands
    // on it when it is.
    final ring = AnimatedOpacity(
      opacity: selected || busy ? 0 : 1,
      duration: glide,
      child: Container(
        width: markSize,
        height: markSize,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          border: Border.all(
            color: roles.text3,
            width: KitTokens.focusRingWidth(context),
          ),
        ),
      ),
    );
    final Widget check = busy
        ? SizedBox.square(
            key: const ValueKey('kit-choice-card-busy'),
            dimension: markSize,
            child: Padding(
              padding: EdgeInsets.all(tokens.space1),
              // The kit's small spinner; still under reduced motion (MOT-7).
              child: CircularProgressIndicator(
                strokeWidth: KitTokens.spinnerStroke,
                value: still ? .75 : null,
                color: roles.accent,
                backgroundColor: Colors.transparent,
              ),
            ),
          )
        : AnimatedScale(
            scale: selected ? 1 : KitMotion.markFrom,
            duration: still ? Duration.zero : KitMotion.standard,
            curve: KitMotion.enter,
            child: AnimatedOpacity(
              opacity: selected ? 1 : 0,
              duration: glide,
              curve: KitMotion.enter,
              child: Container(
                width: markSize,
                height: markSize,
                decoration: BoxDecoration(
                  color: roles.accent,
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: IconTheme.merge(
                  data: IconThemeData(color: roles.onAccent),
                  child: const KitIcon(
                    AppIconography.check,
                    size: KitIconSize.small,
                  ),
                ),
              ),
            ),
          );
    final iconColor = !enabled
        ? roles.text3
        : selected
        ? roles.accent
        : roles.text2;
    final icon = choice.leading;
    final top = Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (icon != null)
          TweenAnimationBuilder<Color?>(
            tween: ColorTween(end: iconColor),
            duration: glide,
            builder: (context, color, child) => IconTheme.merge(
              data: IconThemeData(color: color, size: tokens.markSize),
              child: child!,
            ),
            child: SizedBox.square(
              dimension: tokens.markSize + tokens.space2,
              child: FittedBox(
                alignment: AlignmentDirectional.centerStart,
                child: icon,
              ),
            ),
          ),
        const Spacer(),
        Stack(alignment: Alignment.center, children: [ring, check]),
      ],
    );
    final words = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        text(
          choice.title,
          // Title size; at large text the headline, so the answer wraps at
          // its words and the line stays near it.
          large ? KitTextRole.headline : KitTextRole.title,
          enabled ? KitTextTone.primary : KitTextTone.secondary,
        ),
        if (line.isNotEmpty) ...[
          SizedBox(height: tokens.space1),
          KitEntrance(
            trigger: line,
            onMount: false,
            child: text(
              line,
              KitTextRole.body,
              enabled ? KitTextTone.secondary : KitTextTone.tertiary,
            ),
          ),
        ],
      ],
    );
    // Solid: matte, the chosen card takes a breath of mint into its
    // surface and a mint ring; the others a hairline. Every other style
    // shows the choice its own way (the resolver's `selected`).
    final chosenRing = 2 * KitTokens.focusRingWidth(context);
    final fill = selected
        ? Color.alphaBlend(roles.accent.withValues(alpha: .07), roles.surface1)
        : roles.surface1;
    final radius = BorderRadius.circular(tokens.cardRadius);
    final decoration = KitSurfaceLook.decorate(
      context,
      solid: BoxDecoration(
        color: fill,
        borderRadius: radius,
        border: Border.all(
          // A hairline that still reads on the matte surface.
          color: selected
              ? roles.accent
              : Color.lerp(roles.hairline, roles.text3, .4)!,
          // The ring twice the focus ring, so it reads as chosen; the
          // hairline otherwise.
          width: selected ? chosenRing : KitTokens.hairlineWidth(context),
        ),
      ),
      // A card on its own, a step up from the page, so every style gives
      // it a boundary (Paper sets a resting panel on the page unboxed).
      level: KitSurfaceLevel.surface2,
      borderRadius: radius,
      selected: selected,
    );
    // One merged node: the answer, chosen or not, in its exclusive group.
    return MergeSemantics(
      child: Semantics(
        inMutuallyExclusiveGroup: true,
        child: KitTappable(
          onTap: onTap,
          disabledReason: choice.disabledReason ?? '',
          label: line.isEmpty ? choice.title : '${choice.title}. $line',
          selected: selected,
          shape: KitShape.card,
          child: ExcludeSemantics(
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: tokens.minTarget * 3),
              child: kitSurfaceBox(
                style: KitSurfaceLook.styleOf(context),
                decoration: decoration,
                // The padding keeps room for the thickest ring, drawn inside
                // the card, so choosing never moves the words.
                child: Padding(
                  padding: EdgeInsets.all(tokens.space5 + chosenRing),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      top,
                      SizedBox(height: tokens.space5),
                      words,
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
