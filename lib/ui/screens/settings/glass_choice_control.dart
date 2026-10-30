import 'package:flutter/widgets.dart';

import '../../../l10n/app_localizations.dart';
import '../../kit/kit_frost.dart';
import '../../kit/kit_segmented.dart';
import '../../kit/kit_text.dart';
import '../../kit/kit_tokens.dart';

/// Settings › Appearance › Glass: Off, Weak, Balanced or Strong for the
/// chat's message box and header ([KitFrost]). A device choice
/// ([GlassChoiceStore]): it applies at once to an open chat. Under Saver,
/// reduced motion, high contrast or a style without glass the note says why
/// both stay solid; the choice is kept for when that ends.
///
/// Kit only: KitSegmented and KitText on the page gutter.
class GlassChoiceControl extends StatelessWidget {
  const GlassChoiceControl({super.key});

  /// Why glass is held here, or null when the choice applies.
  static String? heldReason(BuildContext context, AppLocalizations copy) =>
      switch (KitFrost.heldBy(context)) {
        KitFrostHold.saver => copy.frostGlassHeldSaver,
        KitFrostHold.motion => copy.frostGlassHeldMotion,
        KitFrostHold.contrast => copy.frostGlassHeldContrast,
        KitFrostHold.style => copy.frostGlassHeldStyle,
        null => null,
      };

  @override
  Widget build(BuildContext context) {
    final copy = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    KitFrost.unawaitedLoad();
    return ValueListenableBuilder<KitGlassChoice>(
      valueListenable: GlassChoiceStore.choice,
      builder: (context, chosen, _) {
        final held = heldReason(context, copy);
        return Padding(
          key: const ValueKey('frost-glass-choice'),
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              KitSegmented<KitGlassChoice>(
                semanticsLabel: copy.frostGlassQuestion,
                selected: chosen,
                fitLabels: true,
                segments: [
                  for (final choice in KitGlassChoice.values)
                    KitSegment(
                      key: ValueKey('frost-glass-${choice.name}'),
                      value: choice,
                      label: _label(copy, choice),
                    ),
                ],
                onChanged: (choice) => GlassChoiceStore.save(choice).ignore(),
              ),
              SizedBox(height: tokens.space3),
              KitText(
                held ?? copy.frostGlassHint,
                key: const ValueKey('frost-glass-note'),
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            ],
          ),
        );
      },
    );
  }

  static String _label(AppLocalizations copy, KitGlassChoice choice) =>
      switch (choice) {
        KitGlassChoice.off => copy.frostGlassOff,
        KitGlassChoice.weak => copy.frostGlassWeak,
        KitGlassChoice.balanced => copy.frostGlassBalanced,
        KitGlassChoice.strong => copy.frostGlassStrong,
      };
}
