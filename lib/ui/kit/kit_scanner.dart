import 'dart:async';
import 'dart:math' as math;

import 'package:clock/clock.dart';
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_buttons.dart';
import 'kit_layout.dart';
import 'kit_motion.dart';
import 'kit_notice.dart';
import 'kit_progress.dart';
import 'kit_scanner_camera.dart';
import 'kit_since.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Where the frames come from (KitScanner.md). The default is the device
/// camera through the `camera` plugin, QR only; tests and galleries pass a fake.
/// The plugin's types never leave kit_scanner_camera.dart.
abstract class KitScannerCamera {
  /// The back camera, QR codes only, each code reported once per sighting
  /// (the same code in a row is not repeated).
  factory KitScannerCamera.qr() => qrCamera();

  /// Opens the camera. Throws [KitScannerFailure] when it cannot.
  Future<void> start();
  Future<void> stop();
  Future<void> dispose();

  /// Decoded values, raw. Only [KitScanner] listens.
  Stream<String> get codes;

  /// The live preview, filling its box.
  Widget preview(BuildContext context);
}

/// The camera could not open. [deviceMessage] is about the device (it
/// never carries a decoded value), safe for the host's Details.
class KitScannerFailure implements Exception {
  const KitScannerFailure([this.deviceMessage]);
  final String? deviceMessage;

  @override
  String toString() => 'KitScannerFailure(${deviceMessage ?? ''})';
}

/// The camera frame that finds a pairing QR (KitScanner.md): a live preview
/// with a square window, one instruction line, and an in-place line when
/// the code seen is not the one wanted. It hands each decoded value to the
/// host through [onCode] and never keeps it: the value is not stored in
/// state, rendered, put into semantics or logged (SEC-2). Permission, the
/// recovery states and parsing stay with the host.
///
/// Once [onCode] returns true the part latches, stops the camera and
/// ignores every later value, even ones already queued. The camera also
/// stops while the app is inactive or in the background, restarting on
/// return, and when the part is disposed.
///
/// States: loading, error.
///
/// Loading is starting, and slow once the camera has taken longer than
/// [KitMotion.escalateAfter]; error is the rejected line (a camera that
/// will not open is reported through [onFailed], never drawn); paused shows
/// while the app is away.
class KitScanner extends StatefulWidget {
  const KitScanner({
    super.key,
    required this.onCode,
    required this.onFailed,
    required this.instruction,
    this.rejected,
    this.onSlow = const [],
    this.camera,
    this.previewKey,
    this.rejectedKey,
  }) : assert(
         onSlow.length <= 2,
         'KitScanner.onSlow: at most two ways out (KitScanner.md).',
       );

  /// Called with each decoded value, raw. True means accepted: the part
  /// stops the camera and delivers nothing more. False keeps scanning; the
  /// host then says why in [rejected].
  final bool Function(String raw) onCode;

  /// The camera could not open: called once per start attempt, and the
  /// host replaces the part with its own state view.
  final ValueChanged<KitScannerFailure> onFailed;

  /// What to point the camera at, in the host's words.
  final String instruction;

  /// Why the last code was refused, in words; null hides the line.
  final String? rejected;

  /// At most two ways out, offered when the camera takes longer than
  /// [KitMotion.escalateAfter] to open ("Paste the code instead").
  final List<KitAction> onSlow;

  /// Null means [KitScannerCamera.qr], owned and disposed by the part. A
  /// camera passed in is started and stopped by the part and disposed by
  /// its owner.
  final KitScannerCamera? camera;

  /// Defaults to `ValueKey('kit-scanner-preview')`.
  final Key? previewKey;

  /// The key of the rejected line.
  final Key? rejectedKey;

  @override
  State<KitScanner> createState() => _KitScannerState();
}

enum _Phase { starting, scanning, paused, failed }

/// KitScanner.md, Accessibility: at 200 % text the text block scrolls
/// rather than shrink the window below this side.
const double _minWindow = 160;

class _KitScannerState extends State<KitScanner> with WidgetsBindingObserver {
  late final KitScannerCamera _camera;
  late final bool _ownsCamera;
  StreamSubscription<String>? _codes;
  _Phase _phase = _Phase.starting;

  /// When the current start attempt began; KitSince escalates from here.
  DateTime _startedAt = clock.now();

  /// Counts start attempts, so a start that finishes after a pause or a
  /// newer attempt does not change what is shown.
  int _attempt = 0;

  /// Set once [KitScanner.onCode] accepts a value: nothing more is
  /// delivered, and the camera stays stopped.
  bool _handled = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final passed = widget.camera;
    _ownsCamera = passed == null;
    _camera = passed ?? KitScannerCamera.qr();
    _codes = _camera.codes.listen(_onCode);
    unawaited(_start());
  }

  Future<void> _start() async {
    final attempt = ++_attempt;
    _startedAt = clock.now();
    if (mounted && _phase != _Phase.starting) {
      setState(() => _phase = _Phase.starting);
    } else {
      _phase = _Phase.starting;
    }
    KitScannerFailure? failure;
    try {
      await _camera.start();
    } on KitScannerFailure catch (error) {
      failure = error;
    } catch (error) {
      // A platform error from the camera is about the device, never about
      // a decoded value (start runs before any frame is read).
      failure = KitScannerFailure('$error');
    }
    if (!mounted || attempt != _attempt) return;
    if (failure != null) {
      setState(() => _phase = _Phase.failed);
      widget.onFailed(failure);
      return;
    }
    if (_phase == _Phase.paused || _handled) {
      // The app went away (or a code was accepted) while the camera opened.
      unawaited(_camera.stop());
      return;
    }
    setState(() => _phase = _Phase.scanning);
  }

  void _onCode(String raw) {
    if (_handled || _phase != _Phase.scanning || raw.isEmpty) return;
    if (widget.onCode(raw)) {
      _handled = true;
      // Stopped before the host pops, so no frame is decoded behind a
      // closing route.
      unawaited(_camera.stop());
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.inactive:
      case AppLifecycleState.hidden:
      case AppLifecycleState.paused:
        if (_phase == _Phase.scanning || _phase == _Phase.starting) {
          setState(() => _phase = _Phase.paused);
          if (!_handled) unawaited(_camera.stop());
        }
      case AppLifecycleState.resumed:
        if (_phase == _Phase.paused && !_handled) unawaited(_start());
      case AppLifecycleState.detached:
        break;
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _attempt++;
    unawaited(_codes?.cancel());
    unawaited(_release());
    super.dispose();
  }

  Future<void> _release() async {
    await _camera.stop();
    if (_ownsCamera) await _camera.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_phase == _Phase.failed) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    final short = KitLayout.isShort(context);
    final frame = _frame(context, tokens);
    final text = _textBlock(tokens);
    return LayoutBuilder(
      builder: (context, constraints) {
        if (short) {
          return Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Expanded(child: frame),
              Expanded(
                child: Center(child: SingleChildScrollView(child: text)),
              ),
            ],
          );
        }
        // The text block scrolls rather than push the window below its
        // smallest side (160) plus its margins.
        final height = constraints.maxHeight;
        final minFrame = _minWindow + 2 * tokens.space6;
        final textMax = height.isFinite
            ? math.max(height - minFrame, height / 2)
            : double.infinity;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: frame),
            ConstrainedBox(
              constraints: BoxConstraints(maxHeight: textMax),
              child: SingleChildScrollView(child: text),
            ),
          ],
        );
      },
    );
  }

  Widget _frame(BuildContext context, KitTokens tokens) {
    final roles = tokens.roles;
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final scanning = _phase == _Phase.scanning;
    final Widget? centre = switch (_phase) {
      _Phase.starting => KitSince(
        since: _startedAt,
        builder: (context, status) => _starting(tokens, l10n, status.isSlow),
      ),
      _Phase.paused => KitText(
        l10n.kitScannerPaused,
        key: const ValueKey('kit-scanner-paused'),
        role: KitTextRole.label,
        tone: KitTextTone.secondary,
        textAlign: TextAlign.center,
      ),
      _ => null,
    };
    final body = Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(color: roles.ground),
        if (scanning)
          ExcludeSemantics(
            child: KeyedSubtree(
              key: const ValueKey('kit-scanner-camera'),
              child: _camera.preview(context),
            ),
          ),
        CustomPaint(
          painter: _Viewfinder(
            scrim: scanning ? roles.scrim : null,
            bracket: scanning ? roles.accent : roles.text3,
            radius: tokens.panelCornerRadius,
            thickness: tokens.space1,
            gutter: tokens.gutter,
            margin: tokens.space6,
          ),
        ),
        if (centre != null)
          Center(
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
              child: SingleChildScrollView(child: centre),
            ),
          ),
      ],
    );
    return Semantics(
      key: widget.previewKey ?? const ValueKey('kit-scanner-preview'),
      container: true,
      image: scanning,
      liveRegion: scanning,
      label: scanning ? l10n.kitScannerPreview : null,
      hint: scanning ? widget.instruction : null,
      child: ClipRect(child: body),
    );
  }

  Widget _starting(KitTokens tokens, AppLocalizations l10n, bool slow) {
    return Column(
      key: const ValueKey('kit-scanner-starting'),
      mainAxisSize: MainAxisSize.min,
      children: [
        SizedBox(
          width: KitTokens.scannerWindow - 2 * tokens.space4,
          child: Semantics(
            container: true,
            liveRegion: true,
            child: KitProgressView(
              progress: KitProgress.waiting(
                key: const ValueKey('kit-scanner-progress'),
                caption: slow ? l10n.kitScannerSlow : l10n.kitScannerStarting,
              ),
            ),
          ),
        ),
        if (slow && widget.onSlow.isNotEmpty) ...[
          SizedBox(height: tokens.space3),
          KeyedSubtree(
            key: const ValueKey('kit-scanner-on-slow'),
            // Centred under the words, as tertiary buttons (48 dp targets).
            child: Wrap(
              alignment: WrapAlignment.center,
              spacing: tokens.space1,
              children: [
                for (final action in widget.onSlow)
                  KitButton.fromAction(action, role: KitButtonRole.tertiary),
              ],
            ),
          ),
        ],
      ],
    );
  }

  Widget _textBlock(KitTokens tokens) {
    final rejected = widget.rejected;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: KitLayout.readingWidth),
        child: Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.gutter,
            tokens.space4,
            tokens.gutter,
            tokens.space6,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [
              KitText(
                widget.instruction,
                key: const ValueKey('kit-scanner-instruction'),
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
              if (rejected != null) ...[
                SizedBox(height: tokens.space3),
                KitNotice(
                  key:
                      widget.rejectedKey ??
                      const ValueKey('kit-scanner-rejected'),
                  message: rejected,
                  // LOOK-4, LOOK-5: the failure tone's glyph is the error
                  // mark in text1, never danger or attention.
                  tone: AppStatusTone.failure,
                  icon: AppIconography.error,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// The dim outside the square window and the four corner brackets
/// (filled shapes, [thickness] thick, [KitTokens.scannerBracket] long).
class _Viewfinder extends CustomPainter {
  const _Viewfinder({
    required this.scrim,
    required this.bracket,
    required this.radius,
    required this.thickness,
    required this.gutter,
    required this.margin,
  });

  final Color? scrim;
  final Color bracket;
  final double radius;
  final double thickness;
  final double gutter;
  final double margin;

  /// The window's side: [KitTokens.scannerWindow], capped by the box's
  /// width less the gutters and its height less the margins, in whole dp.
  static double side(
    Size size, {
    required double gutter,
    required double margin,
  }) => math
      .max(
        0,
        math.min(
          KitTokens.scannerWindow,
          math.min(size.width - 2 * gutter, size.height - 2 * margin),
        ),
      )
      .floorToDouble();

  @override
  void paint(Canvas canvas, Size size) {
    final s = side(size, gutter: gutter, margin: margin);
    if (s <= 0) return;
    final window = Rect.fromCenter(
      center: size.center(Offset.zero),
      width: s,
      height: s,
    );
    final corner = math.min(radius, s / 2);
    final outer = RRect.fromRectAndRadius(window, Radius.circular(corner));
    if (scrim case final scrim?) {
      final dim = Path()
        ..fillType = PathFillType.evenOdd
        ..addRect(Offset.zero & size)
        ..addRRect(outer);
      canvas.drawPath(dim, Paint()..color = scrim);
    }
    final inner = RRect.fromRectAndRadius(
      window.deflate(thickness),
      Radius.circular(math.max(0, corner - thickness)),
    );
    final ring = Path.combine(
      PathOperation.difference,
      Path()..addRRect(outer),
      Path()..addRRect(inner),
    );
    final arm = math.min(KitTokens.scannerBracket, s / 2);
    final boxes = Path()
      ..addRect(Rect.fromLTWH(window.left, window.top, arm, arm))
      ..addRect(Rect.fromLTWH(window.right - arm, window.top, arm, arm))
      ..addRect(Rect.fromLTWH(window.left, window.bottom - arm, arm, arm))
      ..addRect(
        Rect.fromLTWH(window.right - arm, window.bottom - arm, arm, arm),
      );
    canvas.drawPath(
      Path.combine(PathOperation.intersect, ring, boxes),
      Paint()..color = bracket,
    );
  }

  @override
  bool shouldRepaint(_Viewfinder old) =>
      old.scrim != scrim ||
      old.bracket != bracket ||
      old.radius != radius ||
      old.thickness != thickness ||
      old.gutter != gutter ||
      old.margin != margin;
}
