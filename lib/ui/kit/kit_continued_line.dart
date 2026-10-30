import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// "Continued in Claude Code" on the conversation that was continued, and
/// "Continued from <title>" on the one that continues it: one quiet line
/// that opens the other conversation. [gone] words a conversation that no
/// longer exists and turns the line off. It folds into a row or notice the
/// host already has; it adds no level of its own.
///
/// States: disabled.
class KitContinuedLine extends StatelessWidget {
  const KitContinuedLine({
    super.key,
    required this.text,
    required this.onOpen,
    this.gone = false,
  });

  final String text;
  final VoidCallback onOpen;
  final bool gone;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final words = gone ? l10n.continuedGone : text;
    return KitReveal(
      child: KitTappable(
        tappableKey: const ValueKey('continued-line'),
        label: words,
        onTap: gone ? null : onOpen,
        disabledReason: gone ? l10n.continuedGone : null,
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: tokens.space2,
            vertical: tokens.space2,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const KitIcon(AppIconography.link, size: KitIconSize.small),
              SizedBox(width: tokens.space2),
              Flexible(
                child: KitText(
                  words,
                  role: KitTextRole.secondary,
                  tone: gone ? KitTextTone.tertiary : KitTextTone.accent,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              if (!gone) ...[
                SizedBox(width: tokens.space1),
                KitIcon(
                  rtl
                      ? AppIconography.chevronLeft
                      : AppIconography.chevronRight,
                  size: KitIconSize.small,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
