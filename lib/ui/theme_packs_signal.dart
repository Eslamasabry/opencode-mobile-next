// Signal's palette (Settings › Appearance › Style › Signal, owner
// 2026-10-30): the colours of a railway signal box, the same whatever the
// theme pack. A theme file (it holds colour literals, kit ratchet G17); the
// lamps' red is the one red the app shows, approved for this style only
// (test/no_red_scan_test.dart allows this file).
import 'package:flutter/foundation.dart' show immutable;
import 'package:flutter/painting.dart';

import 'theme_roles.dart';

/// Signal's fixed colours, light (cream enamel, ink) or dark (a night
/// signal box: warm near-black, a dim brass track, glowing lamps). Never
/// the theme's: a signal box looks the same whatever the theme pack.
@immutable
class SignalPalette {
  const SignalPalette._({
    required this.dark,
    required this.ground,
    required this.slip,
    required this.slipEdge,
    required this.rule,
    required this.track,
    required this.siding,
    required this.head,
    required this.lampOff,
    required this.lcd,
    required this.lcdGhost,
    required this.lcdBezel,
    required this.key,
    required this.keyEdge,
    required this.stopKey,
    required this.stopEdge,
    required this.alertInk,
    required this.inkKey,
    required this.inkKeyEdge,
    required this.inkKeyWords,
  });

  static const light = SignalPalette._(
    dark: false,
    ground: Color(0xFFEFE9DA),
    slip: Color(0xFFFBF9F2),
    slipEdge: Color(0xFFCDC3A9),
    rule: Color(0xFFC9BFA6),
    track: Color(0xFF191815),
    siding: Color(0xFF6F6858),
    head: Color(0xFF15140F),
    lampOff: Color(0xFF4A4740),
    lcd: Color(0xFF1B1D1A),
    lcdGhost: Color(0xFF2C302A),
    lcdBezel: Color(0x00000000),
    key: Color(0xFFF7F3E8),
    keyEdge: Color(0xFF8F887A),
    stopKey: Color(0xFFB3261E),
    stopEdge: Color(0xFF6E130E),
    alertInk: Color(0xFFA8231C),
    inkKey: Color(0xFF22211D),
    inkKeyEdge: Color(0xFF000000),
    inkKeyWords: Color(0xFFF4EFE2),
  );

  static const night = SignalPalette._(
    dark: true,
    ground: Color(0xFF12110E),
    slip: Color(0xFF221F19),
    slipEdge: Color(0xFF3F3A30),
    rule: Color(0xFF2E2B24),
    track: Color(0xFF8E8673),
    siding: Color(0xFF6A6353),
    head: Color(0xFF000000),
    lampOff: Color(0xFF3A372F),
    lcd: Color(0xFF070806),
    lcdGhost: Color(0xFF1A1D18),
    lcdBezel: Color(0xFF34302A),
    key: Color(0xFF26241F),
    keyEdge: Color(0xFF0A0908),
    stopKey: Color(0xFFC0332A),
    stopEdge: Color(0xFF5A0F0A),
    alertInk: Color(0xFFFF8A7F),
    inkKey: Color(0xFF3A372F),
    inkKeyEdge: Color(0xFF000000),
    inkKeyWords: Color(0xFFF4EFE2),
  );

  /// The lamps glow the same by day and night.
  static const Color lampRed = Color(0xFFFF3B2F);
  static const Color lampGreen = Color(0xFF3BE070);

  /// The segment timer's lit digits.
  static const Color lcdOn = Color(0xFF9FE870);

  final bool dark;
  final Color ground;
  final Color slip;
  final Color slipEdge;
  final Color rule;
  final Color track;
  final Color siding;
  final Color head;
  final Color lampOff;
  final Color lcd;
  final Color lcdGhost;
  final Color lcdBezel;

  /// A quiet raised key (Reject on a slip) and its edge.
  final Color key;
  final Color keyEdge;

  /// The red hardware Stop key and its edge.
  final Color stopKey;
  final Color stopEdge;

  /// "Needs you", engraved in the signal's red ink (AA on the ground).
  final Color alertInk;

  /// The dark ink key a screen's floating action is ("+ New
  /// conversation"), its edge and lip, and its words. By night a dim key,
  /// no glare.
  final Color inkKey;
  final Color inkKeyEdge;
  final Color inkKeyWords;

  static SignalPalette forRoles(ThemeRoles roles) =>
      roles.isDark ? night : light;
}
