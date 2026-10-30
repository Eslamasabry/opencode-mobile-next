import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:qr/qr.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_max_width.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A QR code another device can scan (docs/ux-system/kit-api/KitQr.md,
/// kit-v2.md §5 "the handoff QR" and §9.2 Surfaces): dark modules on a
/// light card with a four-module quiet zone, whatever the theme, because a
/// scanner reads contrast, not palette. Every module is snapped to whole
/// physical pixels, so the code is as sharp as the screen allows (LOOK-21).
///
/// [data] is never shown as text, logged or put into semantics by this
/// part: a session link can carry identifiers (SEC-2). Only
/// [semanticsLabel] is spoken; the module pattern itself is excluded.
///
/// The code takes the width it is given, up to [KitTokens.qrMaxSize], and
/// is square; there is no `size:` parameter. Error correction is fixed at
/// M and is not a parameter.
///
/// States: none — a link too long to encode shows a notice in words (the
/// gallery's too_long shot); the caller chose the data, so it is not a
/// failure state.
class KitQr extends StatelessWidget {
  const KitQr({
    super.key,
    required this.data,
    required this.semanticsLabel,
    this.qrKey,
  }) : assert(data != '', 'KitQr.data must not be empty');

  /// The text to encode.
  final String data;

  /// What the code is for, e.g. "QR code to open this conversation on your
  /// phone". The host's own words; this part adds none beside it.
  final String semanticsLabel;

  /// Kept by the host on the code's own box across a rebuild, e.g.
  /// `ValueKey('session-link-qr')` (TEST-5). There is no box to key when
  /// [data] is too long.
  final Key? qrKey;

  /// Whether [data] fits a code at the kit's error correction (M). The
  /// host can decide before showing the part, for example to hide "Scan"
  /// words.
  static bool fits(String data) => _encode(data) != null;

  static QrImage? _encode(String data) {
    try {
      return QrImage(
        QrCode.fromData(data: data, errorCorrectLevel: QrErrorCorrectLevel.M),
      );
    } on InputTooLongException {
      return null;
    }
  }

  /// The card's side: at most [KitTokens.qrMaxSize] and never more than
  /// [maxWidth], snapped down so every module (plus the quiet zone) lands
  /// on a whole physical pixel (LOOK-21, VL §7).
  ///
  /// In a window too narrow for even one physical pixel per module, the
  /// code draws at the width it has, with fractional modules, rather than
  /// growing past its box. The side never exceeds [maxWidth], so a module
  /// can fall under the spec's 2-physical-px floor; the host keeps the
  /// copy-link path (see the QA record's contract problem on "Short
  /// windows").
  static double _sideFor({
    required double maxWidth,
    required int moduleCount,
    required double dpr,
  }) {
    final totalModules = moduleCount + KitTokens.qrQuietModules * 2;
    final idealSide = math.max(0.0, math.min(maxWidth, KitTokens.qrMaxSize));
    final ratio = dpr > 0 ? dpr : 1.0;
    final physicalModule = (idealSide * ratio / totalModules).floor();
    if (physicalModule < 1) return idealSide;
    return physicalModule * totalModules / ratio;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.control(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final image = _encode(data);
    if (image == null) {
      final l10n = lookupAppLocalizations(Localizations.localeOf(context));
      return Semantics(
        container: true,
        liveRegion: true,
        child: Row(
          children: [
            Icon(
              AppIconography.error,
              size: tokens.smallIconSize,
              color: tokens.roles.text1,
            ),
            SizedBox(width: tokens.space2),
            Expanded(
              child: KitText(l10n.kitQrTooLong, role: KitTextRole.secondary),
            ),
          ],
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final dpr = MediaQuery.devicePixelRatioOf(context);
        final maxWidth = constraints.hasBoundedWidth
            ? constraints.maxWidth
            : KitTokens.qrMaxSize;
        final side = _sideFor(
          maxWidth: maxWidth,
          moduleCount: image.moduleCount,
          dpr: dpr,
        );
        final totalModules = image.moduleCount + KitTokens.qrQuietModules * 2;
        return Semantics(
          image: true,
          label: semanticsLabel,
          child: ExcludeSemantics(
            child: Container(
              key: qrKey,
              width: side,
              height: side,
              decoration: BoxDecoration(
                color: KitTokens.qrPaper,
                borderRadius: BorderRadius.circular(tokens.codeRadius),
                border: Border.all(
                  color: tokens.roles.hairline,
                  width: KitTokens.hairlineWidth(context),
                ),
              ),
              child: CustomPaint(
                painter: _KitQrPainter(
                  image: image,
                  moduleLogical: side / totalModules,
                  ink: KitTokens.qrInk,
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Paints [image]'s dark modules inside the quiet zone, in [ink]. Module
/// order is always left to right, top to bottom, whatever [Directionality]
/// says: a mirrored QR code does not scan.
class _KitQrPainter extends CustomPainter {
  const _KitQrPainter({
    required this.image,
    required this.moduleLogical,
    required this.ink,
  });

  final QrImage image;
  final double moduleLogical;
  final Color ink;

  @override
  void paint(Canvas canvas, Size size) {
    if (moduleLogical <= 0) return;
    final paint = Paint()
      ..color = ink
      ..style = PaintingStyle.fill
      ..isAntiAlias = false;
    final quiet = moduleLogical * KitTokens.qrQuietModules;
    for (var row = 0; row < image.moduleCount; row++) {
      for (var col = 0; col < image.moduleCount; col++) {
        if (!image.isDark(row, col)) continue;
        canvas.drawRect(
          Rect.fromLTWH(
            quiet + col * moduleLogical,
            quiet + row * moduleLogical,
            moduleLogical,
            moduleLogical,
          ),
          paint,
        );
      }
    }
  }

  @override
  bool shouldRepaint(_KitQrPainter oldDelegate) =>
      oldDelegate.image != image ||
      oldDelegate.moduleLogical != moduleLogical ||
      oldDelegate.ink != ink;
}
