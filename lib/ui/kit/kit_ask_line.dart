import 'package:flutter/material.dart';

import 'kit_buttons.dart';
import 'kit_max_width.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A one-time question on an otherwise working screen, with its two
/// answers in view (design standard §2, §5): a neutral glyph, the
/// question, then [decline] and [accept] as tertiary [KitButton]s at the
/// end of the line. When the question and both answers do not fit on one
/// line (long words, large text) the answers move under the question,
/// start-aligned.
///
/// It is not a status line (§5 is for conditions): it asks, once, and
/// leaves when answered. It sits where the question belongs (in the chat,
/// just above the composer), not under the top bar with the status line.
///
/// States: inline, stacked.
class KitAskLine extends StatelessWidget {
  const KitAskLine({
    super.key,
    required this.icon,
    required this.question,
    required this.accept,
    required this.decline,
    this.semanticsLabel,
  });

  final IconData icon;
  final String question;
  final KitAction accept;
  final KitAction decline;

  /// The fuller explanation a screen reader gives instead of [question].
  final String? semanticsLabel;

  bool _stacks(BuildContext context, double width) {
    final tokens = KitTokens.of(context);
    final buttonStyle = KitText.styleOf(context, KitTextRole.button);
    final questionStyle = KitText.styleOf(context, KitTextRole.body);
    final scaler = MediaQuery.textScalerOf(context);
    double measure(String text, TextStyle style) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: Directionality.of(context),
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final result = painter.width;
      painter.dispose();
      return result;
    }

    final buttons =
        measure(accept.label, buttonStyle) +
        measure(decline.label, buttonStyle) +
        4 * KitButton.tertiaryInset;
    // Room for the question on at most two lines beside the answers.
    final question = measure(this.question, questionStyle) / 2;
    final reserved =
        tokens.gutter + tokens.smallIconSize + tokens.space3 + tokens.space2;
    return reserved + question + buttons > width || scaler.scale(1) > 1.5;
  }

  /// The height of one line of the question at the current text scale.
  double _lineHeight(BuildContext context) {
    final painter = TextPainter(
      text: TextSpan(
        text: question,
        style: KitText.styleOf(context, KitTextRole.body),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final result = painter.preferredLineHeight;
    painter.dispose();
    return result;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final answers = [
      KitButton.fromAction(decline, role: KitButtonRole.tertiary),
      KitButton.fromAction(accept, role: KitButtonRole.tertiary),
    ];
    final verticalInset = tokens.space1 / 2;
    return Semantics(
      container: true,
      label: semanticsLabel,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final stacked = _stacks(context, constraints.maxWidth);
          // Two lines at most, even at large text: the question never takes
          // the screen from what it sits on; [semanticsLabel] says it all.
          final text = KitText(
            question,
            role: KitTextRole.body,
            tone: KitTextTone.primary,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          );
          final glyph = Icon(
            icon,
            size: tokens.smallIconSize,
            color: roles.text2,
          );
          return ConstrainedBox(
            constraints: const BoxConstraints(
              minHeight: KitTokens.statusLineMinHeight,
            ),
            child: Padding(
              // Stacked, the block keeps a space3 top inset so the question
              // does not hug the line above it.
              padding: EdgeInsetsDirectional.fromSTEB(
                tokens.gutter,
                stacked ? tokens.space3 : verticalInset,
                tokens.space2,
                verticalInset,
              ),
              child: Row(
                crossAxisAlignment: stacked
                    ? CrossAxisAlignment.start
                    : CrossAxisAlignment.center,
                children: [
                  // Stacked, the glyph is centred on the question's first
                  // line (one line of the scaled secondary style), not on
                  // the top of the block.
                  if (stacked)
                    SizedBox(
                      height: _lineHeight(context),
                      child: Center(child: glyph),
                    )
                  else
                    glyph,
                  SizedBox(width: tokens.space3),
                  Expanded(
                    child: stacked
                        ? Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              text,
                              KitInset(
                                child: Wrap(
                                  spacing: tokens.space1,
                                  children: answers.reversed.toList(),
                                ),
                              ),
                            ],
                          )
                        : text,
                  ),
                  if (!stacked) ...answers,
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
