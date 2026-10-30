import 'package:flutter/material.dart';

import 'kit_field.dart' show kitFieldFrame;
import 'kit_icon_button.dart';
import 'kit_max_width.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A one-time code the person has to type somewhere else (a device code
/// such as "VPFI-KC2ZK"): shown on its own, large and monospaced, left to
/// right whatever the reading direction, with the kit's Copy control at its
/// end. The copy follows the one copy contract ([KitIconButton.copy]: a
/// light tick, one "Copied" announcement, no SnackBar). The code is copied
/// as shown, not redacted: it is on screen already and only works once.
///
/// It is drawn as the style's field (`KitSurfaceRole.field`, from
/// [kitFieldFrame]: Solid's inset fill, a well under Soft, an ink box under
/// Brutal and Paper), so it reads as the value to type, never as a pressed
/// control.
///
/// States: none — one short text with a copy control.
class KitOneTimeCode extends StatelessWidget {
  const KitOneTimeCode({
    super.key,
    required this.code,
    this.label,
    this.copyLabel,
    this.copyKey,
  });

  /// The code exactly as the server wrote it.
  final String code;

  /// A short line above the code ("Your code").
  final String? label;

  /// The copy control's name ("Copy code"); null reads "Copy".
  final String? copyLabel;

  /// On the copy control (tests find it by it).
  final Key? copyKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.control(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    // The largest role, in the mono family: a code is read letter by
    // letter, left to right.
    final style = KitText.styleOf(context, KitTextRole.largeTitle).copyWith(
      fontFamily: KitText.styleOf(context, KitTextRole.mono).fontFamily,
      fontFamilyFallback: KitText.styleOf(
        context,
        KitTextRole.mono,
      ).fontFamilyFallback,
    );
    final radius = BorderRadius.circular(tokens.panelCornerRadius);
    final look = KitSurfaceLook.decorate(
      context,
      solid: kitFieldFrame(context, fill: tokens.insetSurface, radius: radius),
      role: KitSurfaceRole.field,
      borderRadius: radius,
    );
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (label != null) ...[
          KitText(label!, role: KitTextRole.secondary),
          SizedBox(height: tokens.space2),
        ],
        kitSurfaceBox(
          style: KitSurfaceLook.styleOf(context),
          decoration: look,
          child: Padding(
            padding: EdgeInsetsDirectional.all(tokens.space3).add(look.padding),
            child: Row(
              children: [
                Expanded(
                  child: Directionality(
                    textDirection: TextDirection.ltr,
                    child: Semantics(
                      label: code.split('').join(' '),
                      excludeSemantics: true,
                      child: FittedBox(
                        fit: BoxFit.scaleDown,
                        alignment: AlignmentDirectional.centerStart,
                        child: Text(
                          code,
                          key: const ValueKey('kit-one-time-code'),
                          style: style,
                        ),
                      ),
                    ),
                  ),
                ),
                SizedBox(width: tokens.space2),
                KitIconButton.copy(
                  key: copyKey ?? const ValueKey('kit-one-time-code-copy'),
                  text: () => code,
                  tooltip: copyLabel,
                  redact: false,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
