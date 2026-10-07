import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_details_fold.dart';
import 'kit_icon.dart';
import 'kit_receipt.dart';
import 'kit_request_card.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Which look an [KitAgentCard] has.
enum KitAgentCardMode {
  /// The whole card: eyebrow, title, body and (when the agent asks for
  /// something) the ask. A card with an ask wears the needs-you tone; a
  /// report (no ask) is a neutral surface.
  full,

  /// Answered: one row with the act ("Sent · Undo"), no frame. With
  /// [KitAgentCard.expandLabel] the row opens the body read-only.
  receipt,

  /// The agent moved on before the person answered: one quiet line, the body
  /// read-only behind its tap when [KitAgentCard.expandLabel] is given.
  passedOver,

  /// The card could not be read: one line and a Details fold.
  unreadable,
}

/// The frame for a card an agent describes (docs/design/genui-plan-2026-10-07.md
/// "UI (kit)"): an [eyebrow] ("Claude Code asks"), a [title], the [body]
/// parts the agent chose (all from the kit, handed in as widgets) and, when
/// the agent asks for something, the [ask] slot. It is drawn with
/// [KitRequestCard]'s one shape (FC2, docs/design/FC2-one-card-shape.md):
/// the same frame (the needs-you ring and surface while it waits for an
/// answer, a plain `surface1` card with a hairline for a report), the same
/// heading (the agent's glyph in the tile) and the same answered row.
///
/// Four modes ([KitAgentCardMode]): `full`; `receipt` (what was sent, with
/// Undo inside its window, or tap to read it again); `passedOver` ("Not
/// answered", read-only); `unreadable` (one line, the technical text under
/// Details). With [inList] the card sits under a list row: no reading-width
/// centring and no page gutter (the list owns both); the caller passes
/// secondary buttons in [ask], since the screen keeps its one primary.
///
/// The words come from the caller (its ARB): this part draws no copy of its
/// own. One live region announces [announcement] when it is given.
///
/// States: answered, disabled.
///
/// Answered is the `receipt` mode; disabled is a collapsed card with nothing
/// to press (the receipt's Undo shows only while [onUndo] is given, and
/// `passedOver` is read-only).
class KitAgentCard extends StatefulWidget {
  const KitAgentCard({
    super.key,
    required this.eyebrow,
    required this.title,
    this.body = const [],
    this.ask,
    this.mode = KitAgentCardMode.full,
    this.inList = false,
    this.icon,
    this.announcement,
    this.receiptLabel,
    this.receiptAt,
    this.onUndo,
    this.expandLabel,
    this.passedOverLabel,
    this.unreadableLabel,
    this.detailsLabel,
    this.details,
    this.cardKey,
  });

  /// Who is speaking and why: "Claude Code asks", "Claude Code reports".
  final String eyebrow;
  final String title;

  /// The card's parts, in order, drawn from the kit by the caller.
  final List<Widget> body;

  /// What the person answers with; null for a report.
  final Widget? ask;

  final KitAgentCardMode mode;
  final bool inList;

  /// The eyebrow's glyph; the app's agent mark by default.
  final IconData? icon;

  /// Said once when the card appears and when its words change.
  final String? announcement;

  /// [KitAgentCardMode.receipt]: the act in the person's words ("Sent: 2
  /// photos", "Allowed once").
  final String? receiptLabel;
  final DateTime? receiptAt;

  /// "Undo" on a receipt, only while the host passes it (the window is the
  /// receipt's own).
  final VoidCallback? onUndo;

  /// Names the tap that opens a collapsed card's body read-only ("Show the
  /// card"). Null: the row has nothing to open.
  final String? expandLabel;

  /// [KitAgentCardMode.passedOver]: "Not answered".
  final String? passedOverLabel;

  /// [KitAgentCardMode.unreadable]: "The agent's card could not be shown".
  final String? unreadableLabel;

  /// The fold's name, "Details".
  final String? detailsLabel;

  /// The technical text under the fold; shown redacted, mono, left to right.
  final String? details;

  final Key? cardKey;

  /// Whether the card asks for something.
  bool get asks => ask != null;

  @override
  State<KitAgentCard> createState() => _KitAgentCardState();
}

class _KitAgentCardState extends State<KitAgentCard> {
  bool _open = false;

  @override
  void didUpdateWidget(covariant KitAgentCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.mode != widget.mode) _open = false;
  }

  @override
  Widget build(BuildContext context) {
    final content = switch (widget.mode) {
      KitAgentCardMode.full => _card(context),
      KitAgentCardMode.receipt || KitAgentCardMode.passedOver => _row(context),
      KitAgentCardMode.unreadable => _unreadable(context),
    };
    return kitRequestPlacement(context, inList: widget.inList, child: content);
  }

  // ── The frame ──────────────────────────────────────────────────────────

  Widget _card(BuildContext context) {
    final tokens = KitTokens.of(context);
    final attention = widget.asks;
    final spaced = <Widget>[];
    for (final piece in widget.body) {
      spaced
        ..add(SizedBox(height: tokens.space3))
        ..add(piece);
    }
    final words = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _heading(context, attention: attention),
        ...spaced,
        if (widget.ask != null) ...[
          SizedBox(height: tokens.space4),
          widget.ask!,
        ],
      ],
    );
    // The request card's frame (FC2): the needs-you look while it asks, a
    // plain card for a report. Its body scrolls with the page, so no
    // height cap of its own.
    return kitRequestFrame(
      context,
      key: widget.cardKey,
      attention: attention,
      capHeight: false,
      child: words,
    );
  }

  /// The request card's heading (FC2): the agent's glyph in the tile, who
  /// speaks and why as the caption, the title as the headline.
  Widget _heading(BuildContext context, {required bool attention}) {
    Widget heading = kitRequestHeading(
      context,
      icon: widget.icon ?? AppIconography.agent,
      attention: attention,
      caption: widget.eyebrow,
      captionTone: attention ? KitTextTone.attention : KitTextTone.secondary,
      title: widget.title,
    );
    final said = widget.announcement;
    if (said != null) {
      heading = Semantics(
        container: true,
        liveRegion: true,
        label: said,
        excludeSemantics: true,
        child: heading,
      );
    }
    return heading;
  }

  // ── The collapsed row ──────────────────────────────────────────────────

  /// The request card's answered row (FC2): the act and its receipt, or
  /// "Not answered"; the title line opens the body read-only.
  Widget _row(BuildContext context) {
    final received = widget.mode == KitAgentCardMode.receipt;
    final canOpen = widget.expandLabel != null && widget.body.isNotEmpty;
    final Widget outcome = received
        ? KitReceipt(
            state: KitReceiptState.confirmed,
            label: widget.receiptLabel,
            at: widget.receiptAt,
            onUndo: widget.onUndo,
          )
        : Semantics(
            container: true,
            liveRegion: true,
            child: KitText(
              widget.passedOverLabel ?? '',
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
          );
    return kitAnsweredRow(
      context,
      key: widget.cardKey,
      icon: widget.icon ?? AppIconography.agent,
      title: widget.title,
      outcome: outcome,
      open: _open,
      onToggle: canOpen ? () => setState(() => _open = !_open) : null,
      toggleLabel: widget.expandLabel,
      body: widget.body,
    );
  }

  // ── Unreadable ─────────────────────────────────────────────────────────

  Widget _unreadable(BuildContext context) {
    final tokens = KitTokens.of(context);
    final details = widget.details;
    return Padding(
      padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space4),
      child: Column(
        key: widget.cardKey,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.rowHeight),
            child: Row(
              children: [
                ExcludeSemantics(
                  child: KitIcon(
                    AppIconography.info,
                    size: KitIconSize.small,
                    tone: KitTextTone.secondary,
                  ),
                ),
                SizedBox(width: tokens.space3),
                Expanded(
                  child: Semantics(
                    container: true,
                    liveRegion: true,
                    child: KitText(
                      widget.unreadableLabel ?? widget.title,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (details != null && details.isNotEmpty)
            KitDetailsFold(label: widget.detailsLabel, text: details),
        ],
      ),
    );
  }
}
