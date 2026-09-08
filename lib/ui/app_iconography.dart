import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// Product verbs and destinations, independent of the underlying icon package.
///
/// Regular is the action weight. Selected navigation keeps its silhouette and
/// adds a quiet duotone fill; More stays as dots. Static references let Flutter
/// remove unused font glyphs when building a release. Artwork is Phosphor 2.1.0
/// (MIT); native IconData keeps compatibility with Flutter 3.47.2.
@staticIconProvider
abstract final class AppIconography {
  static const navigationSize = 24.0;
  static const actionSize = 24.0;
  static const inlineSize = 20.0;

  static const workspace = IconData(
    0xe17e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const workspaceSelected = IconData(
    0xe17f,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  );
  static const files = IconData(
    0xe25a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const filesSelected = IconData(
    0xe25b,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  );
  static const activity = IconData(
    0xe0d0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const activitySelected = IconData(
    0xe0d1,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  );
  static const more = IconData(
    0xe1fe,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const menu = IconData(
    0xe208,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const add = IconData(
    0xe3d4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const send = IconData(
    0xe08e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const stop = IconData(
    0xe46c,
    fontFamily: 'AppPhosphorFill',
    matchTextDirection: false,
  );
  static const settings = IconData(
    0xe434,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const search = IconData(
    0xe30c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const branch = IconData(
    0xe278,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const terminal = IconData(
    0xeae8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const copy = IconData(
    0xe1cc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const expand = IconData(
    0xe0a6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const close = IconData(
    0xe4f6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const back = IconData(
    0xe058,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const retry = IconData(
    0xe036,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const externalLink = IconData(
    0xe5de,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const review = IconData(
    0xe27c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const chevronRight = IconData(
    0xe13a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const chevronDown = IconData(
    0xe136,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const chevronUp = IconData(
    0xe13c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const check = IconData(
    0xe182,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const info = IconData(
    0xe2ce,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const warning = IconData(
    0xe4e0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const error = IconData(
    0xe4f8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cloud = IconData(
    0xe1aa,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cloudOff = IconData(
    0xe1b6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const server = IconData(
    0xe2a0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const model = IconData(
    0xe74e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const agent = IconData(
    0xe762,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const tools = IconData(
    0xe5d4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const extensions = IconData(
    0xe596,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const keyboard = IconData(
    0xe2d8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const guide = IconData(
    0xe0e6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const bug = IconData(
    0xe5f4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const appearance = IconData(
    0xe6c8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const privacy = IconData(
    0xe40c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const diagnostics = IconData(
    0xe000,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const usage = IconData(
    0xe150,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const link = IconData(
    0xe2e2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const unlink = IconData(
    0xe2e4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const attach = IconData(
    0xe39a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const download = IconData(
    0xe20c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const upload = IconData(
    0xe4c0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const file = IconData(
    0xe230,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const code = IconData(
    0xe1bc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const archive = IconData(
    0xe00c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const delete = IconData(
    0xe4a6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const edit = IconData(
    0xe3b4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const pin = IconData(
    0xe3e2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const unpin = IconData(
    0xe3e4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const computer = IconData(
    0xe560,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const folderAdd = IconData(
    0xe25e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const question = IconData(
    0xe3e8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const permissions = IconData(
    0xe2d6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const clock = IconData(
    0xe19a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const star = IconData(
    0xe46a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const starFilled = IconData(
    0xe46a,
    fontFamily: 'AppPhosphorFill',
    matchTextDirection: false,
  );
  static const mic = IconData(
    0xe326,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const image = IconData(
    0xe2ca,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const camera = IconData(
    0xe10e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
}

// Static background glyphs preserve release font tree shaking.
const _duotoneBackgrounds = <int, IconData>{
  0xe17f: IconData(
    0xe17e,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  ),
  0xe25b: IconData(
    0xe25a,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  ),
  0xe0d1: IconData(
    0xe0d0,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  ),
};

/// Renders regular and duotone glyphs with one optional accessibility label.
///
/// This is decoration, not a tap target: place it inside an IconButton or other
/// accessible control. Let that control's tooltip or visible text name the
/// action; use [semanticLabel] only for a standalone informative glyph.
class AppGlyph extends StatelessWidget {
  const AppGlyph(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.semanticLabel,
    this.textDirection,
  });

  final IconData icon;
  final double? size;
  final Color? color;
  final String? semanticLabel;

  /// Pass an explicit direction for technical marks that must not mirror.
  /// Navigation arrows follow the surrounding direction by default; technical
  /// glyph data preserves its orientation.
  final TextDirection? textDirection;

  @override
  Widget build(BuildContext context) {
    final foreground = Icon(
      icon,
      size: size,
      color: color,
      textDirection: textDirection,
    );
    final secondary = icon.fontFamily == 'AppPhosphorDuotone'
        ? _duotoneBackgrounds[icon.codePoint]
        : null;
    final glyph = ExcludeSemantics(
      child: secondary == null || MediaQuery.highContrastOf(context)
          ? foreground
          : Stack(
              alignment: Alignment.center,
              children: [
                Opacity(
                  opacity: .2,
                  child: Icon(
                    secondary,
                    size: size,
                    color: color,
                    textDirection: textDirection,
                  ),
                ),
                foreground,
              ],
            ),
    );
    final label = semanticLabel;
    return label == null
        ? glyph
        : Semantics(label: label, image: true, child: glyph);
  }
}

/// The open portal identity without a launcher background or shadow.
///
/// Decorative by default. A standalone mark may supply [semanticLabel]; a
/// neighboring app title already communicates the identity and needs no label.
class AppBrandMark extends StatelessWidget {
  const AppBrandMark({super.key, this.size = 32, this.semanticLabel});

  final double size;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final mark = SvgPicture.asset(
      'assets/branding/open-portal/mark.svg',
      width: size,
      height: size,
      colorFilter: ColorFilter.mode(
        Theme.of(context).colorScheme.primary,
        BlendMode.srcIn,
      ),
      excludeFromSemantics: true,
    );
    final label = semanticLabel;
    return label == null
        ? mark
        : Semantics(label: label, image: true, child: mark);
  }
}
