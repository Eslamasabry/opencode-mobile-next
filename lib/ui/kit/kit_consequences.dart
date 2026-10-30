// The visual language's consequences panel (docs/design/visual-language-
// 2026-09-26.md §5 "Sheets"): counted facts that go with an act, on one
// `surface1` (or, in light, `ground`) panel with hairlines inset to the
// words.
//
// A part of the kit_sheet.dart library (KitSheet.md "Public API",
// KitConfirmSheet.md: "the same library"), so the confirm part
// (kit_confirm_sheet.dart) names KitConsequences with no import, while the
// class still lives in its own NAME-1 file, kit_consequences.dart.
part of 'kit_sheet.dart';

/// What a fact in a [KitConsequences] panel says about the thing (§5
/// Sheets).
enum KitConsequenceMark {
  /// A fact: `text2` info glyph.
  info,

  /// Goes with the act (loses data or ends work): strong neutral glyph (never red).
  lost,

  /// Survives the act (DATA-8): `text2` check.
  kept,

  /// A plain fact with no verdict: a small `text2` dot. Give every item of
  /// a list this one mark when the facts are neither good nor bad news, so
  /// the panel does not pair a check against an info glyph for no reason.
  neutral,
}

/// One counted fact a [KitConsequences] panel shows: "3 queued prompts will
/// be deleted".
@immutable
class KitConsequence {
  const KitConsequence(
    this.text, {
    this.mark = KitConsequenceMark.info,
    this.icon,
    this.key,
  });

  final String text;
  final KitConsequenceMark mark;

  /// Overrides [mark]'s glyph ("the test run" → terminal).
  final IconData? icon;
  final Key? key;
}

/// The visual language's consequences panel (§5 Sheets): a `surface1` (or,
/// in light, `ground`) panel of one-line facts, with hairlines inset to the
/// words. A sheet body places it where the facts belong, and
/// `showKitConfirm` draws its `consequences` and `consequenceItems` with it.
///
/// States: none — a static list of the facts the caller gives it.
class KitConsequences extends StatelessWidget {
  const KitConsequences({super.key, required this.items})
    : assert(items.length > 0);

  final List<KitConsequence> items;

  static IconData _iconFor(KitConsequenceMark mark) => switch (mark) {
    KitConsequenceMark.info => AppIconography.info,
    KitConsequenceMark.lost => AppIconography.warning,
    KitConsequenceMark.kept => AppIconography.check,
    KitConsequenceMark.neutral => AppIconography.statusDot,
  };

  /// The row's leading mark: the mark's glyph (or the item's own), or the
  /// neutral dot, which is drawn small so it reads as a bullet.
  static Widget _mark(KitConsequence item, KitTokens tokens) {
    final roles = tokens.roles;
    final size = tokens.smallIconSize;
    if (item.icon == null && item.mark == KitConsequenceMark.neutral) {
      return SizedBox.square(
        key: const ValueKey('kit-consequence-dot'),
        dimension: size,
        child: Center(
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: roles.text2,
              shape: BoxShape.circle,
            ),
            child: SizedBox.square(dimension: tokens.space2),
          ),
        ),
      );
    }
    return Icon(
      item.icon ?? _iconFor(item.mark),
      size: size,
      color: item.mark == KitConsequenceMark.lost ? roles.text1 : roles.text2,
    );
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return KitSurface.inset(
      key: const ValueKey('kit-consequences'),
      padding: KitSurfacePadding.none,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0)
              Divider(
                height: 0,
                thickness: KitTokens.hairlineWidth(context),
                indent: tokens.space4 + tokens.smallIconSize + tokens.space3,
                color: roles.hairline,
              ),
            Padding(
              key: items[i].key,
              padding: EdgeInsets.symmetric(
                horizontal: tokens.space4,
                vertical: tokens.space3,
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ExcludeSemantics(child: _mark(items[i], tokens)),
                  SizedBox(width: tokens.space3),
                  Expanded(
                    child: KitText(items[i].text, role: KitTextRole.rowTitle),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
