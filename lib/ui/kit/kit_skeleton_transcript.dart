import 'package:flutter/material.dart';

import 'kit_layout.dart';
import 'kit_surface_look.dart';
import 'kit_tokens.dart';

/// Placeholder turns while a conversation loads (design standard §4, the
/// transcript's version of [KitSkeletonRows]): the shape of what is coming,
/// a prompt and a few lines of reply per turn, with no words and no motion,
/// hidden from screen readers (the screen's loading bar says it is
/// loading). It sits at the bottom, where the newest turn will appear, so
/// the first real message lands where the placeholder was. The prompt's
/// block is a `content` surface in the style, as the prompt bubble is; the
/// reply's bars stand for words and keep their quiet fill in every style.
///
/// States: loading (decorative).
class KitSkeletonTranscript extends StatelessWidget {
  const KitSkeletonTranscript({super.key, this.turns = 2});

  final int turns;

  static const _replyWidths = [
    [0.94, 0.88, 0.62],
    [0.9, 0.97, 0.8, 0.46],
  ];

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final promptShape = tokens.shapeOf(KitShape.button);
    final barShape = tokens.shapeOf(KitShape.pill);
    final promptHeight = tokens.space6 + tokens.space3;
    final barHeight = tokens.space2;
    Widget bar(double width) => FractionallySizedBox(
      alignment: AlignmentDirectional.centerStart,
      widthFactor: width,
      child: Container(
        height: barHeight,
        decoration: ShapeDecoration(color: roles.surface3, shape: barShape),
      ),
    );
    final turnsColumn = ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: KitLayout.paneDetailMaxWidth),
      child: Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.gutter,
          tokens.gutter,
          tokens.gutter + tokens.space2,
        ),
        child: Column(
          key: const ValueKey('kit-skeleton-transcript'),
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var turn = 0; turn < turns; turn++) ...[
              if (turn > 0) SizedBox(height: tokens.space4 + tokens.space3),
              // The prompt: one wide block, as prompts are drawn.
              SizedBox(
                height: promptHeight,
                child: kitSurfaceBox(
                  style: KitSurfaceLook.styleOf(context),
                  decoration: KitSurfaceLook.decorate(
                    context,
                    solid: ShapeDecoration(
                      color: roles.surface3,
                      shape: promptShape,
                    ),
                    level: KitSurfaceLevel.surface3,
                  ),
                  child: const SizedBox.expand(),
                ),
              ),
              SizedBox(height: tokens.space4),
              for (final width in _replyWidths[turn % _replyWidths.length])
                Padding(
                  padding: EdgeInsetsDirectional.only(bottom: tokens.space2),
                  child: bar(width),
                ),
            ],
          ],
        ),
      ),
    );
    // In a short space (landscape, large text, keyboard up) the turns
    // nearest the composer show and the rest is cut off at the top, never
    // an overflow.
    return ExcludeSemantics(
      child: LayoutBuilder(
        builder: (context, constraints) => !constraints.hasBoundedHeight
            ? Align(alignment: Alignment.bottomCenter, child: turnsColumn)
            : ClipRect(
                child: OverflowBox(
                  alignment: Alignment.bottomCenter,
                  minHeight: 0,
                  maxHeight: double.infinity,
                  child: turnsColumn,
                ),
              ),
      ),
    );
  }
}
