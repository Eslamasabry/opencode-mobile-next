import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_code_block.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_reveal.dart';

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// The conversation record, ready to read before it is sent: its size, how
/// much was left out, whether anything was hidden, the file it will be saved
/// as, and the Markdown itself behind "Show preview" (which unfolds in place
/// over [KitMotion.standard]; instant under reduced motion).
///
/// The preview shows at most [previewLimit] characters; the record sent is
/// whole. It is a read-only review: it never edits the record.
///
/// States: none — the preview is folded until asked.
class KitHandoffRecord extends StatefulWidget {
  const KitHandoffRecord({
    super.key,
    required this.sizeBytes,
    required this.omittedMessages,
    required this.redacted,
    required this.markdown,
    required this.path,
    this.initiallyExpanded = false,
  });

  /// Most characters of the Markdown the preview draws.
  static const int previewLimit = 12000;

  final int sizeBytes;

  /// Messages the record leaves out to stay small.
  final int omittedMessages;

  /// Something that looked like a secret was hidden.
  final bool redacted;
  final String markdown;

  /// Where the record will be saved, relative to the project.
  final String path;
  final bool initiallyExpanded;

  @override
  State<KitHandoffRecord> createState() => _KitHandoffRecordState();
}

class _KitHandoffRecordState extends State<KitHandoffRecord> {
  late bool _open = widget.initiallyExpanded;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final reduced = KitMotion.reduced(context);
    final kb = (widget.sizeBytes / 1024).ceil().clamp(1, 1 << 20);
    final markdown = widget.markdown.length > KitHandoffRecord.previewLimit
        ? '${widget.markdown.substring(0, KitHandoffRecord.previewLimit)}\n…'
        : widget.markdown;
    final gap = SizedBox(height: tokens.space2);
    return Column(
      key: const ValueKey('handoff-record'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        KitText(
          [
            l10n.continueWithRecordSize(kb),
            if (widget.omittedMessages > 0)
              l10n.continueWithOmittedCount(widget.omittedMessages),
          ].join(' · '),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
        if (widget.omittedMessages > 0) ...[
          gap,
          KitText(
            l10n.continueWithOlderLeftOut,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
        ],
        gap,
        KitText(
          widget.redacted
              ? '${l10n.continueWithRedacted} ${l10n.continueWithReadPreview}'
              : l10n.continueWithReadPreview,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
        gap,
        KitText(
          l10n.continueWithRecordFile(widget.path),
          role: KitTextRole.mono,
          tone: KitTextTone.secondary,
        ),
        gap,
        KitTappable(
          tappableKey: const ValueKey('handoff-record-toggle'),
          label: _open
              ? l10n.continueWithHidePreview
              : l10n.continueWithShowPreview,
          selected: _open,
          onTap: () => setState(() => _open = !_open),
          child: Padding(
            padding: EdgeInsets.symmetric(vertical: tokens.space2),
            child: Row(
              children: [
                Expanded(
                  child: KitText(
                    _open
                        ? l10n.continueWithHidePreview
                        : l10n.continueWithShowPreview,
                    role: KitTextRole.label,
                    tone: KitTextTone.accent,
                  ),
                ),
                AnimatedRotation(
                  turns: _open ? 0.5 : 0,
                  duration: reduced ? Duration.zero : KitMotion.quick,
                  curve: KitMotion.enter,
                  child: const KitIcon(
                    AppIconography.chevronDown,
                    size: KitIconSize.small,
                  ),
                ),
              ],
            ),
          ),
        ),
        KitReveal(
          child: _open
              ? Padding(
                  padding: EdgeInsets.only(top: tokens.space2),
                  child: KitCodeBlock(
                    key: const ValueKey('handoff-record-preview'),
                    text: markdown,
                    language: 'markdown',
                    maxLines: 14,
                    copyable: false,
                    highlight: false,
                    wrap: true,
                    showWrapToggle: false,
                  ),
                )
              : null,
        ),
      ],
    );
  }
}
