part of 'kit_top_bar.dart';

/// [KitTopBar.centred]: the title in title size at the true centre, the exit
/// at its side and the same room kept on the other, so the words never
/// shift with the button.
class _KitCentredBar extends StatelessWidget {
  const _KitCentredBar({
    required this.title,
    required this.titleKey,
    required this.back,
    required this.close,
  });

  final String title;
  final Key? titleKey;

  /// The exit buttons; null where the route has none.
  final Widget? back;
  final Widget? close;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final slot = tokens.minTarget + tokens.space1;
    Widget side(Widget? button, AlignmentDirectional alignment) => SizedBox(
      width: slot,
      child: button == null ? null : Align(alignment: alignment, child: button),
    );
    final large = MediaQuery.textScalerOf(context).scale(1) >= 1.3;
    return ConstrainedBox(
      constraints: BoxConstraints(
        minHeight: tokens.minTarget + 2 * tokens.space1 + tokens.space2,
      ),
      child: Padding(
        padding: EdgeInsets.symmetric(vertical: tokens.space2),
        child: Row(
          children: [
            // Each button sits at the screen's edge of its slot, so its
            // glyph's ink lands on the gutter like the other bars' (the
            // close's 20.4 -> 16.5 dp; impeccable layout pass 2026-10-28).
            side(back, AlignmentDirectional.centerStart),
            Expanded(
              child: Semantics(
                header: true,
                namesRoute: true,
                child: KitText(
                  title,
                  key: titleKey,
                  role: large ? KitTextRole.headline : KitTextRole.title,
                  textAlign: TextAlign.center,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
            side(close, AlignmentDirectional.centerEnd),
          ],
        ),
      ),
    );
  }
}
