// KitPhraseChips: one row of the person's own common short phrases above the
// message box ("Continue", "Run the tests"). A tap sends the phrase; a
// long-press (or the screen reader's long-press action) opens a small menu:
// Edit puts the phrase in the box without sending, Hide stops suggesting it.
//
// The host decides which phrases (the moment, what was learned) and what a
// tap does; this part only draws the row. At most a few chips, so the row is
// one line that scrolls sideways when the window is narrow or the text is
// large, starting at the reading edge in both directions. Each chip is a
// KitChip.action: a 48 dp target around a 32 dp pill, named "Send: <words>"
// for a screen reader.
import 'package:flutter/material.dart';

import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../kit_chip.dart';
import '../kit_filter_chips.dart';
import '../kit_frost.dart';
import '../kit_max_width.dart';
import '../kit_menu.dart';
import '../kit_tokens.dart';

/// No phrases draws nothing.
///
/// States: empty.
class KitPhraseChips extends StatelessWidget {
  const KitPhraseChips({
    super.key,
    required this.phrases,
    required this.onChoose,
    required this.onEdit,
    required this.onHide,
  });

  /// The words of each chip, in order. Empty draws nothing.
  final List<String> phrases;

  /// A tap: the host sends these words (this part keeps no sending state;
  /// the host removes the row while a message is on its way).
  final ValueChanged<String> onChoose;

  /// Menu › Edit: put these words in the message box.
  final ValueChanged<String> onEdit;

  /// Menu › Hide: never suggest these words again.
  final ValueChanged<String> onHide;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    if (phrases.isEmpty) return const SizedBox.shrink();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    // space2 under the row: its 48 dp targets never touch the status chips'
    // targets under it, so a tap meant for one never lands on the other
    // (impeccable audit 2026-10-28: 0 dp between the two rows' targets).
    // On the chat composer's glass pane the rows sit at the composer's own
    // rhythm: space1, the targets still apart (coordinator review
    // 2026-10-30: the pane read as empty space between the rows).
    final tokens = KitTokens.of(context);
    final onPane = KitFrost.lineInsetOf(context) > 0;
    return Padding(
      padding: EdgeInsetsDirectional.only(
        bottom: onPane ? tokens.space1 : tokens.space2,
      ),
      child: Semantics(
        container: true,
        label: l10n.phraseChipsRowLabel,
        // One line that scrolls sideways; an edge it runs past fades, never a
        // hard cut mid-word (KitFilterChips).
        child: KitFilterChips(
          chips: [
            for (final phrase in phrases)
              Builder(
                builder: (chipContext) => KitChip.action(
                  key: ValueKey('phrase-chip-$phrase'),
                  label: phrase,
                  spoken: l10n.phraseChipsSend(phrase),
                  onPressed: () => onChoose(phrase),
                  onLongPress: () => _menu(chipContext, phrase),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _menu(BuildContext chipContext, String phrase) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(chipContext));
    return showKitMenu(
      chipContext,
      semanticsLabel: l10n.phraseChipsMenuLabel,
      items: [
        KitMenuItem(
          key: const ValueKey('phrase-chip-edit'),
          label: l10n.phraseChipsEdit,
          icon: AppIconography.edit,
          onSelected: () => onEdit(phrase),
        ),
        KitMenuItem(
          key: const ValueKey('phrase-chip-hide'),
          label: l10n.phraseChipsHide,
          icon: AppIconography.hidden,
          onSelected: () => onHide(phrase),
        ),
      ],
    );
  }
}
