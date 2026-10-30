import 'package:flutter/widgets.dart';

import 'kit_buttons.dart';
import 'kit_max_width.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One muted line under a [KitRowGroup] that says what the group leaves out
/// and offers one way to learn why (target-ia §1.3): "2 settings aren't
/// available on this server · Why". Rows a server cannot serve stay off the
/// panel; this line is the one place that says so, instead of dimmed rows.
///
/// It sits on the group's label rail (the gutter, as the section label's
/// words do), so
/// it reads as part of the group above it. The [action] is a tertiary
/// button at the end of the line; when the words and the action do not fit
/// on one line (long words, large text) the action wraps under the words,
/// start-aligned.
///
/// States: none — a passive line; its one action has no state of its own.
class KitGroupNote extends StatelessWidget {
  const KitGroupNote({super.key, required this.message, this.action});

  /// What the group leaves out, in one sentence.
  final String message;

  /// Where the reason is ("Why"); null shows the words alone.
  final KitAction? action;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final action = this.action;
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.gutter,
        end: tokens.gutter,
        top: tokens.labelGap,
      ),
      child: Wrap(
        crossAxisAlignment: WrapCrossAlignment.center,
        spacing: tokens.space2,
        children: [
          KitText(
            message,
            role: KitTextRole.secondary,
            tone: KitTextTone.secondary,
          ),
          if (action != null)
            KitButton.fromAction(
              action,
              role: KitButtonRole.tertiary,
              expand: false,
            ),
        ],
      ),
    );
  }
}
