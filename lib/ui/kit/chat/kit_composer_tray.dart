part of 'kit_composer.dart';

/// The tray "+" opens (plus-menu concept C, owner 20 Oct 2026): the box
/// grows upward by one compact row of round matte buttons with a word under
/// each, and a hairline between it and the field. No sheet, no glass. It
/// comes and goes with KitReveal's fold and fade (instant with reduced
/// motion); "+" turns into "×" meanwhile.
class _ComposerTray extends StatelessWidget {
  const _ComposerTray({required this.actions, required this.onPicked});

  final List<KitComposerTrayAction> actions;

  /// The tray closes first; then the action runs.
  final ValueChanged<KitComposerTrayAction> onPicked;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Column(
      key: const ValueKey('kit-composer-tray'),
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: EdgeInsetsDirectional.only(
            top: tokens.space3,
            bottom: tokens.space2,
          ),
          // At a large text size on a narrow phone the words shrink, all
          // by the same amount, until the longest fits its quarter of the
          // row: whole words in one size, never "Ph…".
          child: LayoutBuilder(
            builder: (context, box) {
              final media = MediaQuery.of(context);
              final style = KitText.styleOf(context, KitTextRole.secondary);
              var widest = 0.0;
              for (final action in actions) {
                final painter = TextPainter(
                  text: TextSpan(text: action.label, style: style),
                  textDirection: Directionality.of(context),
                  textScaler: media.textScaler,
                  maxLines: 1,
                )..layout();
                widest = math.max(widest, painter.width);
                painter.dispose();
              }
              final room = box.maxWidth / actions.length - 2 * tokens.space1;
              final fit = widest <= room || widest == 0 ? 1.0 : room / widest;
              final labels = TextScaler.linear(media.textScaler.scale(1) * fit);
              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  for (final action in actions)
                    Expanded(
                      child: _TrayButton(
                        action: action,
                        labelScaler: labels,
                        onTap: () => onPicked(action),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
        Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space3),
          child: const KitDivider(),
        ),
      ],
    );
  }
}

class _TrayButton extends StatelessWidget {
  const _TrayButton({
    required this.action,
    required this.labelScaler,
    required this.onTap,
  });

  final KitComposerTrayAction action;

  /// The one size every label of the row shares ([_ComposerTray]).
  final TextScaler labelScaler;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final enabled = action.disabledReason == null;
    return Center(
      child: KitTappable(
        tappableKey: action.buttonKey,
        shape: KitShape.button,
        surface: KitSurfaceLevel.surface2,
        label: action.label,
        disabledReason: action.disabledReason,
        onTap: enabled ? onTap : null,
        child: Padding(
          padding: EdgeInsets.all(tokens.space1),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              SizedBox.square(
                dimension: tokens.minTarget,
                // A round control in the style's own face (raised in
                // Soft, an ink ring in Brutal and Paper); Solid keeps its
                // matte disc.
                child: kitSurfaceBox(
                  style: KitSurfaceLook.styleOf(context),
                  decoration: KitSurfaceLook.decorate(
                    context,
                    solid: BoxDecoration(
                      color: roles.surface3,
                      shape: BoxShape.circle,
                    ),
                    role: KitSurfaceRole.control,
                    level: KitSurfaceLevel.surface3,
                    circle: true,
                  ),
                  child: Icon(
                    action.icon,
                    size: tokens.iconSize(context, 22),
                    color: enabled ? roles.text1 : roles.text3,
                  ),
                ),
              ),
              SizedBox(height: tokens.space1),
              // The button's spoken name is its full word.
              ExcludeSemantics(
                child: MediaQuery(
                  data: MediaQuery.of(
                    context,
                  ).copyWith(textScaler: labelScaler),
                  child: KitText(
                    action.label,
                    role: KitTextRole.secondary,
                    tone: enabled ? KitTextTone.primary : KitTextTone.tertiary,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
