import 'package:flutter/painting.dart';

import '../kit_effects.dart';
import '../kit_surface_look.dart';
import 'kit_style.dart';

/// Solid (docs/design/design-schools/schools/solid.md): today's calm matte
/// look. At its defaults it returns the part's own decoration untouched,
/// so a solid screen is pixel-identical to the app before styles existed.
/// Every role keeps the part's own look (a field its edge, code its fill, a
/// divider its hairline); only content takes Depth › Raised, and content
/// and sheets lose their hairline under Edges › Off.
///
/// Lists are flat ([KitStyleTokens.flatLists]): rows on the page, parted
/// by inset hairlines, with no panel around a group and no tile behind a
/// row's icon.
class KitStyleSolid extends KitStyleSpec {
  const KitStyleSolid();

  @override
  KitStyleTokens get tokens => const KitStyleTokens(
    defaults: KitKnobs(),
    keepsNestedSurfaces: true,
    flatLists: true,
  );

  @override
  Decoration resolve(KitSurfaceRequest r) {
    final raise =
        r.knobs.depth == KitDepth.raised && r.role == KitSurfaceRole.content;
    final strip =
        r.knobs.edges == KitEdges.none &&
        (r.role == KitSurfaceRole.content || r.role == KitSurfaceRole.sheet);
    if (!raise && !strip) return r.solid;
    // Depth › Raised lifts content with the floating layer's one tight
    // drop; Edges › Off drops a panel's hairline (fields keep theirs).
    final lift = [
      BoxShadow(
        color: r.roles.elevationShadow,
        blurRadius: 16,
        spreadRadius: -6,
        offset: const Offset(0, 6),
      ),
    ];
    final solid = r.solid;
    if (solid is BoxDecoration) {
      return BoxDecoration(
        color: solid.color,
        image: solid.image,
        border: strip ? null : solid.border,
        borderRadius: solid.borderRadius,
        boxShadow: raise ? [...?solid.boxShadow, ...lift] : solid.boxShadow,
        gradient: solid.gradient,
        backgroundBlendMode: solid.backgroundBlendMode,
        shape: solid.shape,
      );
    }
    if (solid is ShapeDecoration) {
      final shape = solid.shape;
      return ShapeDecoration(
        color: solid.color,
        image: solid.image,
        gradient: solid.gradient,
        shadows: raise ? [...?solid.shadows, ...lift] : solid.shadows,
        shape: strip && shape is OutlinedBorder
            ? shape.copyWith(side: BorderSide.none)
            : shape,
      );
    }
    return solid;
  }
}
