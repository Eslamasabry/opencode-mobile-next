import 'package:flutter/material.dart';

import 'kit_buttons.dart';
import 'kit_max_width.dart';
import 'kit_tokens.dart';

/// A disabled action's reason (STATE-8, KitAction.md): one muted line,
/// found in tests by its text first (TEST-5). The key sits on the inner
/// [Text], the only child of this widget, so every line has the same key
/// without two keyed siblings. Kept identical to `KitActionBlock`'s copy in
/// kit_buttons.dart (each file is its own library; a shared public widget
/// would be a new kit part, KIT-3).
class _KitActionReason extends StatelessWidget {
  const _KitActionReason(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Text(
    text,
    key: const ValueKey('kit-action-reason'),
    style: KitTokens.of(context).note,
  );
}

/// A block of actions where every rare path gets its own line (design
/// standard §2): the primary, then the secondary, full width, then each
/// tertiary text button under the other, aligned to the start. Used where
/// the tertiary actions must never sit side by side (a server's Update and
/// its destructive Stop), so a thumb aimed at one cannot land on the other.
/// At most two tertiary actions, as in [KitActionBlock]. A disabled action's
/// [KitAction.disabledReason] shows as a muted line under its button
/// (STATE-8, kit-KitAction-v2); every gap is [KitTokens.space2] (8 dp), so a
/// destructive action's 48 dp target stays clear of every other (LAY-9).
class KitActionStack extends StatelessWidget {
  const KitActionStack({
    super.key,
    this.primary,
    this.secondary,
    this.tertiary = const [],
  }) : assert(tertiary.length <= 2);

  final KitAction? primary;
  final KitAction? secondary;
  final List<KitAction> tertiary;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final gap = SizedBox(height: tokens.space2);
    final children = <Widget>[];

    final reasonGap = SizedBox(height: tokens.space1);

    if (primary case final primary?) {
      children.add(KitButton.fromAction(primary, role: KitButtonRole.primary));
      if (primary.disabledReason case final reason?) {
        children.add(reasonGap);
        children.add(_KitActionReason(reason));
      }
    }
    if (secondary case final secondary?) {
      if (children.isNotEmpty) children.add(gap);
      children.add(
        KitButton.fromAction(secondary, role: KitButtonRole.secondary),
      );
      if (secondary.disabledReason case final reason?) {
        children.add(reasonGap);
        children.add(_KitActionReason(reason));
      }
    }
    for (final action in tertiary) {
      if (children.isNotEmpty) children.add(gap);
      children.add(
        KitInset(
          child: KitButton.fromAction(action, role: KitButtonRole.tertiary),
        ),
      );
      if (action.disabledReason case final reason?) {
        children.add(reasonGap);
        children.add(_KitActionReason(reason));
      }
    }

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: children,
    );
  }
}
