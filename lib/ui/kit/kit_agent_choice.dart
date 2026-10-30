import 'package:flutter/material.dart';

import '../../domain/agent_persona.dart';
import '../../l10n/app_localizations.dart';
import '../agents/agent_persona_text.dart';
import '../app_iconography.dart';
import 'kit_agent_grid_span.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Where an agent stands on its card.
enum KitAgentChoiceState {
  /// Can be started now, not the chosen one: matte surface, hairline.
  available,

  /// The chosen one: a filled mint check badge in the corner and the
  /// style's chosen surface (Solid: a mint ring and a breath of mint; never
  /// solid mint).
  selected,

  /// Known but not set up: dimmed, with "Set up" in place of the line; a tap
  /// opens its setup.
  needsSetup,
}

/// One agent as a large card in the start screen's grid: its big
/// monochrome mark, its name in title size and a calm line (up to two) of
/// what it is good at, on a matte solid surface with a hairline. The mark,
/// name and line come only from the [persona]
/// (docs/design/agent-personas.md); an agent with no persona of its own
/// ([AgentPersonaId.other]) shows [otherName] and no line. Spanning a whole
/// row ([KitAgentGridSpan]) it lays out sideways, the mark beside the words.
/// The surface comes from the style resolver: Solid's mint ring and tint,
/// each other style's own chosen look; it changes over the style's state
/// fade and the check lands over [KitMotion.standard] (instantly under
/// reduced motion). The card is a
/// mutually exclusive choice with one tap target of at least 48 dp.
///
/// States: none — selected, available and needs setup ([KitAgentChoiceState])
/// are looks of one card, not states a screen waits in.
class KitAgentChoice extends StatelessWidget {
  const KitAgentChoice({
    super.key,
    required this.persona,
    required this.state,
    required this.onPressed,
    this.otherName,
    this.tappableKey,
  });

  final AgentPersona persona;
  final KitAgentChoiceState state;
  final VoidCallback onPressed;

  /// The agent's own name, used only when the persona is [AgentPersonaId.other].
  final String? otherName;
  final Key? tappableKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final selected = state == KitAgentChoiceState.selected;
    final dimmed = state == KitAgentChoiceState.needsSetup;
    final still = KitMotion.reduced(context);
    final glide = still ? Duration.zero : KitMotion.quick;
    final large = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final wide = KitAgentGridSpan.wideOf(context);
    final name = persona.id == AgentPersonaId.other
        ? (otherName ?? persona.name(l10n))
        : persona.name(l10n);
    final line = dimmed ? l10n.chatsNewSetUp : persona.strength(l10n);
    // Hook H3: on the chosen card the words take the colour the
    // style says reads on its chosen face, so a style that sets it in
    // reverse keeps them legible; elsewhere the part's own tones.
    // Resolved inside the card, so the words keep its plate (hook H10).
    Widget text(
      String text,
      KitTextRole role,
      KitTextTone tone, {
      int? maxLines,
      TextOverflow? overflow,
    }) => selected
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
              return Text(
                text,
                style: own.copyWith(color: ink),
                maxLines: maxLines,
                overflow: overflow,
              );
            },
          )
        : KitText(
            text,
            role: role,
            tone: tone,
            maxLines: maxLines,
            overflow: overflow,
          );

    // The mark is the card's hero: the 24 dp designed mark drawn as a vector
    // a little larger than a mark tile, monochrome in the text colour.
    final markBox = tokens.markSize + tokens.space2;
    final mark = SizedBox.square(
      dimension: wide ? tokens.markSize : markBox,
      child: FittedBox(
        child: IconTheme.merge(
          data: IconThemeData(color: dimmed ? roles.text3 : roles.text1),
          child: persona.hasMark
              ? KitIcon.mark(persona.markSlug, size: KitIconSize.large)
              : const KitIcon(AppIconography.terminal, size: KitIconSize.large),
        ),
      ),
    );
    // A filled mint badge with the check, which fades in from slightly
    // smaller when the card is chosen and fades out when another is.
    final badge = AnimatedScale(
      scale: selected ? 1 : KitMotion.markFrom,
      duration: still ? Duration.zero : KitMotion.standard,
      curve: KitMotion.enter,
      child: AnimatedOpacity(
        opacity: selected ? 1 : 0,
        duration: glide,
        curve: KitMotion.enter,
        child: Container(
          width: tokens.iconTileSize - tokens.space1,
          height: tokens.iconTileSize - tokens.space1,
          decoration: BoxDecoration(
            color: roles.accent,
            shape: BoxShape.circle,
          ),
          alignment: Alignment.center,
          child: IconTheme.merge(
            data: IconThemeData(color: roles.onAccent),
            child: const KitIcon(AppIconography.check, size: KitIconSize.small),
          ),
        ),
      ),
    );
    final lineStyle = KitText.styleOf(context, KitTextRole.rowTitle);
    final twoLines =
        2 *
        MediaQuery.textScalerOf(context).scale(lineStyle.fontSize ?? 16) *
        (lineStyle.height ?? 1.4);
    final words = <Widget>[
      text(
        name,
        // Title size; at large text the headline, so a name wraps at its
        // words and never inside one.
        large ? KitTextRole.headline : KitTextRole.title,
        dimmed ? KitTextTone.secondary : KitTextTone.primary,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      if (line.isNotEmpty) ...[
        SizedBox(height: tokens.space1),
        // The stacked card keeps room for two lines, so a pair of cards
        // stands equally tall whichever line is shorter.
        ConstrainedBox(
          constraints: BoxConstraints(minHeight: wide || large ? 0 : twoLines),
          child: text(
            line,
            KitTextRole.rowTitle,
            dimmed ? KitTextTone.tertiary : KitTextTone.secondary,
            maxLines: 3,
          ),
        ),
      ],
    ];
    // Solid: matte, every card the same surface and hairline; the chosen
    // one is marked one way only, by its check (a shape, so never by colour
    // alone). Every other style shows the choice its own way (the
    // resolver's `selected`).
    final radius = BorderRadius.circular(tokens.cardRadius);
    final decoration = KitSurfaceLook.decorate(
      context,
      solid: BoxDecoration(
        color: roles.surface1,
        borderRadius: radius,
        border: Border.all(
          color: roles.hairline,
          width: KitTokens.hairlineWidth(context),
        ),
      ),
      // A card on its own, a step up from the page, so every style gives
      // it a boundary (Paper sets a resting panel on the page unboxed).
      level: KitSurfaceLevel.surface2,
      borderRadius: radius,
      selected: selected,
    );
    final body = wide
        ? Row(
            children: [
              mark,
              SizedBox(width: tokens.space4),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: words,
                ),
              ),
              SizedBox(width: tokens.space2),
              badge,
            ],
          )
        : Stack(
            clipBehavior: Clip.none,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: mark,
                  ),
                  SizedBox(height: tokens.space5),
                  ...words,
                ],
              ),
              PositionedDirectional(
                top: -tokens.space1,
                end: -tokens.space1,
                child: badge,
              ),
            ],
          );
    return KitTappable(
      tappableKey: tappableKey,
      onTap: onPressed,
      label: line.isEmpty ? name : '$name. $line',
      selected: selected,
      shape: KitShape.card,
      child: Semantics(
        inMutuallyExclusiveGroup: true,
        child: ExcludeSemantics(
          child: ConstrainedBox(
            constraints: BoxConstraints(
              minHeight: wide || large
                  ? tokens.minTarget * 2
                  : tokens.minTarget * 3.5,
            ),
            child: kitSurfaceBox(
              style: KitSurfaceLook.styleOf(context),
              decoration: decoration,
              child: Padding(
                // A drawn border takes its room inside the card, as a
                // container's does; a style's edge sits over the face.
                padding: EdgeInsets.all(tokens.space5).add(decoration.padding),
                child: body,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
