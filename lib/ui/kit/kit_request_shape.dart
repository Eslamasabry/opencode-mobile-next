part of 'kit_request_card.dart';

// ── The one shape (FC2) ──────────────────────────────────────────────────
//
// Everything an agent asks the person is drawn with these: [KitRequestCard]
// (permissions, questions, forms, choices, gates, replies) and
// [KitAgentCard] (an agent's own card). One frame, one heading, one answered
// row, one placement (docs/design/FC2-one-card-shape.md).

/// Where a request card sits: in a conversation, centred at the reading
/// width with its border on the page gutter (the ring adds its own width
/// around it) and an entrance; [inList], exactly as the list places it.
Widget kitRequestPlacement(
  BuildContext context, {
  required bool inList,
  required Widget child,
}) {
  if (inList) return child;
  final tokens = KitTokens.of(context);
  return KitEntrance(
    child: Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: tokens.gutter - KitTokens.needsYouRingWidth,
            vertical: tokens.space1,
          ),
          child: child,
        ),
      ),
    ),
  );
}

/// The request card's frame: the needs-you look when [attention] (the
/// attention surface and line, and outside them the one 4 dp ring LOOK-20
/// allows), else a plain `surface1` card with a hairline; the accent border
/// while [focused]. [wrapCard] wraps the bordered card before the ring (a
/// [Focus]). With [capHeight], at 2.0 text the card never takes more than
/// 45 % of the window (its words scroll inside). With [edge] (the question
/// look) the attention card is calm: `surface1` under the attention line,
/// and an attention edge of [KitTokens.needsYouEdgeWidth] at its start.
///
/// The style decides how loud "needs you" is ([KitStyleTokens.needsYou]):
/// whether the full card takes the attention tint, whether it is ringed
/// (the attention line around it and the wash ring outside), and whether
/// the start edge marks it. By default a style whose glow has a halo tints
/// and rings it, and one without ([KitStyleGlow.halo] off: Brutal and
/// Paper) draws it calm, with the start edge alone; the face is the style's
/// own. The question look is never tinted or ringed. [endsWithTap]: the
/// card's last line is a quiet act whose own target gives the air under
/// it, so a frame that pads its bottom (Signal's slip) pads it less.
Widget kitRequestFrame(
  BuildContext context, {
  required bool attention,
  required Widget child,
  Key? key,
  bool focused = false,
  bool capHeight = true,
  bool edge = false,
  bool endsWithTap = false,
  Widget Function(Widget card)? wrapCard,
}) {
  if (KitTrack.on(context)) {
    return _kitSignalFrame(
      context,
      key: key,
      focused: focused,
      capHeight: capHeight,
      endsWithTap: endsWithTap,
      wrapCard: wrapCard,
      child: child,
    );
  }
  final tokens = KitTokens.of(context);
  final roles = tokens.roles;
  final side = focused
      ? BorderSide(
          color: roles.accent,
          width: KitTokens.focusRingWidth(context),
        )
      : BorderSide(
          color: edge && attention
              ? roles.attention.withValues(alpha: KitTokens.needsYouLineAlpha)
              : attention
              ? roles.attentionLine
              : roles.hairline,
          width: KitTokens.hairlineWidth(context),
        );
  final large = MediaQuery.textScalerOf(context).scale(10) >= 20;
  final look = kitRequestNeedsYou(context, question: edge);
  final ringed = attention && look.ring;
  final marked = attention && look.startEdge;
  final radius = BorderRadius.circular(tokens.cardRadius);
  final tinted = attention && look.tint
      ? Color.alphaBlend(roles.attentionSurface, roles.surface1)
      : null;
  final solid = ShapeDecoration(
    color: tinted ?? roles.surface1,
    shape: RoundedRectangleBorder(borderRadius: radius, side: side),
  );
  // A plain DecoratedBox, not a kitSurfaceBox: [key] names the card's own
  // decorated box (tests and the agent card read it).
  final face = KitSurfaceLook.decorate(
    context,
    solid: solid,
    borderRadius: radius,
    color: tinted,
  );
  // A style's own face drops the hairline, but the focus border and the
  // attention line still say "focused" and "needs you": drawn over the
  // face. Unringed, the start edge (or the tag) alone says "needs you".
  // The card's own face moved only by a Fine-tune knob (Solid) keeps its
  // own border, or none under Edges › Off: only the focus border comes
  // back, so a knob changes no more than it names.
  final own = face is! KitSurfaceDecoration;
  final keepsLine =
      !identical(face, solid) &&
      (own
          ? focused && _kitLostSide(face)
          : focused || (attention && look.line));
  // Whether the face casts a shadow the start edge's clip must not cut: a
  // style's own (hook H12) or Depth › Raised on the card's own face.
  final casts = face is KitSurfaceDecoration
      ? face.casts
      : face is ShapeDecoration && (face.shadows?.isNotEmpty ?? false);
  Widget card = DecoratedBox(
    key: key,
    decoration: face,
    // Hook H9: what sits in the card (its answers panel) casts nothing of
    // its own under a style that keeps one shadow per stack.
    child: KitSurfaceLook.castScope(
      context,
      decoration: face,
      child: DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: keepsLine
            ? ShapeDecoration(
                shape: RoundedRectangleBorder(borderRadius: radius, side: side),
              )
            : const BoxDecoration(),
        child: Padding(
          padding: marked
              ? EdgeInsetsDirectional.fromSTEB(
                  tokens.space4 + KitTokens.needsYouEdgeWidth,
                  tokens.space4,
                  tokens.space4,
                  tokens.space4,
                )
              : EdgeInsets.all(tokens.space4),
          child: child,
        ),
      ),
    ),
  );
  if (marked) {
    final edge = ExcludeSemantics(child: ColoredBox(color: roles.attention));
    if (casts) {
      // Hook H12: a card that casts a shadow (Soft, Brutal, or
      // Depth › Raised) keeps it; only the attention edge is clipped to the
      // card's corners.
      card = Stack(
        children: [
          card,
          Positioned.fill(
            child: IgnorePointer(
              child: ClipRRect(
                borderRadius: radius,
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: SizedBox(
                    width: KitTokens.needsYouEdgeWidth,
                    height: double.infinity,
                    child: edge,
                  ),
                ),
              ),
            ),
          ),
        ],
      );
    } else {
      // The edge rides inside the card's corners, under its border.
      card = ClipRRect(
        borderRadius: radius,
        child: Stack(
          children: [
            card,
            PositionedDirectional(
              start: 0,
              top: 0,
              bottom: 0,
              width: KitTokens.needsYouEdgeWidth,
              child: edge,
            ),
          ],
        ),
      );
    }
  }
  if (capHeight && large) {
    final largest = MediaQuery.textScalerOf(context).scale(10) >= 25;
    card = ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight:
            MediaQuery.sizeOf(context).height *
            (largest
                ? KitTokens.requestMaxHeightShareLarge
                : KitTokens.requestMaxHeightShare),
      ),
      child: card,
    );
  }
  if (wrapCard != null) card = wrapCard(card);
  // The calm card's edge and line already say it: no ring around it, its
  // place kept so the card sits where every request card sits.
  if (!ringed) {
    return Padding(
      padding: const EdgeInsetsDirectional.symmetric(
        horizontal: KitTokens.needsYouRingWidth,
      ),
      child: card,
    );
  }
  return DecoratedBox(
    decoration: ShapeDecoration(
      color: roles.attention.withValues(alpha: KitTokens.needsYouRingAlpha),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(
          tokens.cardRadius + KitTokens.needsYouRingWidth,
        ),
      ),
    ),
    child: Padding(
      padding: const EdgeInsets.all(KitTokens.needsYouRingWidth),
      child: card,
    ),
  );
}

/// [kitRequestFrame] under Signal: the printed slip ([kitSignalSlip]),
/// height-capped at large text like every request card, in the ring's
/// place so it sits where every request card sits.
Widget _kitSignalFrame(
  BuildContext context, {
  required Widget child,
  required bool focused,
  required bool capHeight,
  bool endsWithTap = false,
  Key? key,
  Widget Function(Widget card)? wrapCard,
}) {
  var card = kitSignalSlip(
    context,
    key: key,
    focused: focused,
    endsWithTap: endsWithTap,
    child: child,
  );
  final scale = MediaQuery.textScalerOf(context).scale(10);
  if (capHeight && scale >= 20) {
    card = ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight:
            MediaQuery.sizeOf(context).height *
            (scale >= 25
                ? KitTokens.requestMaxHeightShareLarge
                : KitTokens.requestMaxHeightShare),
      ),
      child: card,
    );
  }
  if (wrapCard != null) card = wrapCard(card);
  return Padding(
    padding: const EdgeInsetsDirectional.symmetric(
      horizontal: KitTokens.needsYouRingWidth,
    ),
    child: card,
  );
}

/// Whether [face], the card's own shape, lost its border (Edges › Off).
bool _kitLostSide(Decoration face) {
  if (face is! ShapeDecoration) return false;
  final shape = face.shape;
  return shape is OutlinedBorder && shape.side == BorderSide.none;
}

/// How "needs you" looks on a request card here: the style's
/// [KitStyleTokens.needsYou] decided against its glow; [question] for the
/// question look, which is never tinted or ringed.
({bool tint, bool line, bool ring, bool startEdge, bool pill})
kitRequestNeedsYou(BuildContext context, {bool question = false}) {
  final tokens = kitStyleSpec(KitSurfaceLook.styleOf(context)).tokens;
  return tokens.needsYou.decide(halo: tokens.glow.halo, question: question);
}

/// The words "Needs you" ([text]) on a request card: the attention caption
/// in [role] while it [waiting], else the secondary one; or, where the
/// style sets it as a pill ([KitNeedsYouTag.pill]) and it waits, a pill in
/// the attention fill with its own words, drawn as the style's control
/// (Brutal: an ink edge). [leading] (the attention dot) goes before the
/// caption, never inside the pill, which is the mark itself.
Widget kitRequestNeedsYouTag(
  BuildContext context, {
  required String text,
  required bool waiting,
  KitTextRole role = KitTextRole.caption,
  bool tabular = false,
  bool question = false,
  Widget? leading,
}) {
  final tokens = KitTokens.of(context);
  final roles = tokens.roles;
  if (KitTrack.on(context)) {
    // The red lamp says it, in place of the attention dot.
    final words = kitSignalNeedsYou(context, text, waiting: waiting);
    if (!waiting) return words;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        kitSignalHead(aspect: KitSignalAspect.stop, height: 16),
        SizedBox(width: tokens.space2),
        Flexible(child: words),
      ],
    );
  }
  if (!waiting || !kitRequestNeedsYou(context, question: question).pill) {
    final words = KitText(
      text,
      role: role,
      tone: waiting ? KitTextTone.attention : KitTextTone.secondary,
      tabular: tabular,
    );
    if (leading == null) return words;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (waiting) ...[leading, SizedBox(width: tokens.space2)],
        Flexible(child: words),
      ],
    );
  }
  return Align(
    alignment: AlignmentDirectional.centerStart,
    widthFactor: 1,
    heightFactor: 1,
    child: kitSurfaceBox(
      style: KitSurfaceLook.styleOf(context),
      decoration: KitSurfaceLook.decorate(
        context,
        // A stadium, squared by Corners › Sharp like every pill (hook H4).
        solid: KitSurfaceDecoration(
          borderRadius: KitSurfaceLook.stadium,
          fill: roles.attentionFill,
        ),
        role: KitSurfaceRole.control,
        color: roles.attentionFill,
      ),
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: tokens.space2,
          vertical: tokens.space1,
        ),
        child: Text(
          text,
          // On the opaque fill: no live-background halo around the words.
          style: KitText.styleOf(context, role).copyWith(
            color: roles.onAttentionFill,
            shadows: const <Shadow>[],
            fontFeatures: tabular ? const [FontFeature.tabularFigures()] : null,
          ),
        ),
      ),
    ),
  );
}

/// The request's glyph in its tinted tile: the attention tint while it
/// needs the person, else `surface3`.
Widget kitRequestTile(
  BuildContext context, {
  required IconData icon,
  required bool attention,
}) {
  final tokens = KitTokens.of(context);
  final roles = tokens.roles;
  final tint = attention
      ? roles.attention.withValues(alpha: tokens.markTintAlpha)
      : roles.surface3;
  return ExcludeSemantics(
    child: SizedBox.square(
      dimension: KitTokens.requestTileSize,
      child: kitSurfaceBox(
        style: KitSurfaceLook.styleOf(context),
        decoration: KitSurfaceLook.decorate(
          context,
          solid: ShapeDecoration(
            color: tint,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(KitTokens.requestTileRadius),
            ),
          ),
          level: KitSurfaceLevel.surface3,
          role: KitSurfaceRole.control,
          // The attention tint keeps its colour, made opaque on the card.
          color: attention
              ? Color.alphaBlend(
                  tint,
                  Color.alphaBlend(roles.attentionSurface, roles.surface1),
                )
              : null,
        ),
        child: Center(
          child: Icon(
            icon,
            size: tokens.smallIconSize,
            color: attention ? roles.attention : roles.text1,
          ),
        ),
      ),
    ),
  );
}

/// The card's heading: the tile ([kitRequestTile]), then the [caption] line
/// (who asks and why), the [title] as the headline, and [lines] under it
/// (each brings its own top gap).
Widget kitRequestHeading(
  BuildContext context, {
  required IconData icon,
  required bool attention,
  required String caption,
  required KitTextTone captionTone,
  required String title,
  Key? titleKey,
  List<Widget> lines = const [],
}) {
  final tokens = KitTokens.of(context);
  return Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      kitRequestTile(context, icon: icon, attention: attention),
      SizedBox(width: tokens.space3),
      Expanded(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            KitText(caption, role: KitTextRole.caption, tone: captionTone),
            SizedBox(height: tokens.space1),
            KitText(
              title,
              key: titleKey,
              role: KitTextRole.headline,
              tone: KitTextTone.primary,
            ),
            ...lines,
          ],
        ),
      ),
    ],
  );
}

/// The one answered row: a request that no longer needs the person
/// (LOOK-4): no card, border, ring or attention colour. The glyph, the
/// [title], and under it the [outcome] (a [KitReceipt]; "Not answered";
/// "Stopped waiting"). With [onToggle] the title line opens [body] read-only
/// under the row ([open]; [toggleLabel] names the tap); nothing in the body
/// takes a tap.
Widget kitAnsweredRow(
  BuildContext context, {
  Key? key,
  required IconData icon,
  required String title,
  Key? titleKey,
  required Widget outcome,
  bool open = false,
  VoidCallback? onToggle,
  String? toggleLabel,
  List<Widget> body = const [],
}) {
  final tokens = KitTokens.of(context);
  final roles = tokens.roles;
  final twoLines = MediaQuery.textScalerOf(context).scale(10) >= 13;
  final canOpen = onToggle != null && body.isNotEmpty;
  Widget titleLine = KitText(
    title,
    key: titleKey,
    role: KitTextRole.secondary,
    tone: KitTextTone.secondary,
    maxLines: twoLines ? 2 : 1,
    overflow: TextOverflow.ellipsis,
  );
  if (canOpen) {
    titleLine = KitTappable(
      onTap: onToggle,
      label: toggleLabel,
      selected: open,
      child: Row(
        children: [
          Expanded(child: titleLine),
          ExcludeSemantics(
            child: Icon(
              open ? AppIconography.chevronUp : AppIconography.chevronDown,
              size: tokens.smallIconSize,
              color: roles.text2,
            ),
          ),
        ],
      ),
    );
  }
  final row = ConstrainedBox(
    key: canOpen ? null : key,
    constraints: BoxConstraints(minHeight: tokens.rowHeight),
    child: Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space4),
      child: Row(
        children: [
          ExcludeSemantics(
            child: Icon(icon, size: tokens.smallIconSize, color: roles.text2),
          ),
          SizedBox(width: tokens.space3),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [titleLine, outcome],
            ),
          ),
        ],
      ),
    ),
  );
  if (!canOpen) return row;
  return Column(
    key: key,
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      row,
      KitReveal(
        child: open
            ? Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.space4 + tokens.smallIconSize + tokens.space3,
                  top: tokens.space2,
                  end: tokens.space4,
                ),
                // The body is a record now: nothing in it takes a tap.
                child: IgnorePointer(
                  child: ExcludeFocus(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        for (final piece in body) ...[
                          piece,
                          SizedBox(height: tokens.space3),
                        ],
                      ],
                    ),
                  ),
                ),
              )
            : null,
      ),
    ],
  );
}
