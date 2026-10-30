import 'package:flutter/widgets.dart';

import 'kit_surface_look.dart';

/// Kit-internal (not exported): a technical surface (a code block, a log, a
/// terminal, a details body, a command box) painted through the style
/// resolver on the `code` role. [fill] with [shape] (or, for a part that
/// paints a plain rounded box, [borderRadius]) is the part's own look, which
/// Solid at its defaults keeps exactly; every other style keeps
/// [fill] opaque and adds its own edge (Soft's well, Brutal's ink box,
/// Paper's rule), so code never sits on translucency.
Widget kitCodeSurface(
  BuildContext context, {
  Key? key,
  required Color fill,
  ShapeBorder? shape,
  BorderRadius? borderRadius,
  required Widget child,
}) => kitSurfaceBox(
  key: key,
  style: KitSurfaceLook.styleOf(context),
  decoration: KitSurfaceLook.decorate(
    context,
    solid: shape != null
        ? ShapeDecoration(color: fill, shape: shape)
        : BoxDecoration(color: fill, borderRadius: borderRadius),
    role: KitSurfaceRole.code,
    color: fill,
  ),
  child: child,
);
