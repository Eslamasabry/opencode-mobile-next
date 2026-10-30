import 'package:flutter/widgets.dart';
import 'package:flutter/material.dart' show Theme;

import '../../l10n/app_localizations.dart';
import '../theme_roles.dart';
import 'kit_layout.dart';
import 'kit_live_background.dart';
import 'kit_max_width.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// What a part of the app shows in place of itself when it fails to build
/// (`ErrorWidget.builder`, lib/diagnostics/app_diagnostics.dart): one plain
/// sentence on the theme's ground and, where there is a page to leave and
/// room for it, Back. The technical text never shows here; it goes to App
/// diagnostics.
///
/// It may be drawn anywhere: inside a page, or above every theme,
/// Localizations, Directionality, Material and Navigator. So it looks
/// nothing up that can be missing: the theme's roles (derived from the
/// default theme when the app's is not there yet) on the page's still
/// ground, its words in a complete text
/// style of their own (never the bare-text fallback that shows yellow,
/// double-underlined monospace outside a Material), the copy in English when
/// no localizations are there, and Back only under a navigator that can go
/// back.
///
/// Back shows only where a page can be left and there is room for it.
///
/// States: error.
class KitErrorFallback extends StatelessWidget {
  const KitErrorFallback({super.key});

  /// The words when no localizations are in force.
  static const fallbackMessage =
      'This part of OpenCode hit an error. Go back and try again, or open '
      'App diagnostics for details.';
  static const fallbackBack = 'Back';

  /// The smallest box Back is offered in: smaller, the part is a small
  /// piece of a page, and the page's own Back is right there.
  static const roomForBack = Size(200, 160);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // The app's roles, or ones derived from the default theme above it:
    // either way a ground and words that read on it.
    final roles = ThemeRoles.resolve(theme);
    final ink = roles.text1;
    final accent = roles.accent;
    final l10n = Localizations.of<AppLocalizations>(context, AppLocalizations);
    final navigator = Navigator.maybeOf(context);
    // A complete style of its own: nothing inherited from a missing
    // Material (the yellow, double-underlined fallback).
    final face = theme.textTheme.bodyLarge;
    final body = KitText.styleFor(KitTextRole.body).copyWith(
      inherit: false,
      color: ink,
      fontFamily: face?.fontFamily,
      fontFamilyFallback: face?.fontFamilyFallback,
      decoration: TextDecoration.none,
    );
    return Directionality(
      textDirection: Directionality.maybeOf(context) ?? TextDirection.ltr,
      // The page's own ground, held still.
      child: KitLiveBackground(
        roles: roles,
        still: true,
        child: LayoutBuilder(
          builder: (context, box) {
            final roomy =
                box.maxWidth >= roomForBack.width &&
                box.maxHeight >= roomForBack.height;
            final back = roomy && (navigator?.canPop() ?? false);
            return ClipRect(
              child: SingleChildScrollView(
                physics: const NeverScrollableScrollPhysics(),
                padding: const EdgeInsets.all(KitTokens.panelPadding),
                // The ground above is the page's and fills it; the words
                // stay in a state's column (kit-v2 §8.6).
                child: KitMaxWidth.cap(
                  maxWidth: KitLayout.stateMaxWidth,
                  alignment: AlignmentDirectional.centerStart,
                  child: DefaultTextStyle(
                    style: body,
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Semantics(
                          liveRegion: true,
                          child: Text(
                            l10n?.kitErrorFallbackMessage ?? fallbackMessage,
                            key: const ValueKey('kit-error-fallback'),
                            style: body,
                          ),
                        ),
                        if (back)
                          Semantics(
                            button: true,
                            child: GestureDetector(
                              key: const ValueKey('kit-error-fallback-back'),
                              behavior: HitTestBehavior.opaque,
                              onTap: () => navigator?.maybePop(),
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  minHeight: 48,
                                  minWidth: 48,
                                ),
                                child: Align(
                                  alignment: AlignmentDirectional.centerStart,
                                  widthFactor: 1,
                                  child: Text(
                                    l10n?.kitTopBarBack ?? fallbackBack,
                                    style: body.copyWith(
                                      color: accent,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
