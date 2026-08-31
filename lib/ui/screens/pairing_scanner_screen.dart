import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../platform/camera.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/pairing.dart';

/// Scans the QR that `opencode2 pair` prints and returns the parsed payload.
///
/// Pops with a [PairingPayload] on success and `null` on every other exit, so
/// the caller has exactly one thing to check. The decoded text is fed to the
/// same [parsePairingPayload] the clipboard path uses — a QR is just another
/// way to get the string across, and it deserves no second, laxer parser.
///
/// The raw decoded text is never held in state, never rendered, and never
/// logged: any QR in the world can be pointed at this screen, but the one we
/// are looking for carries the serve password.
class PairingScannerScreen extends StatefulWidget {
  const PairingScannerScreen({super.key});

  @override
  State<PairingScannerScreen> createState() => _PairingScannerScreenState();
}

/// What the scanner is doing, so the screen renders one honest state rather
/// than a preview with an error painted over it.
enum _ScanStage {
  /// Asking the platform for the camera.
  starting,

  /// Preview is up and looking for a code.
  scanning,

  /// The user said no, and can be asked again.
  denied,

  /// The user said no twice, or ticked "don't ask again". Only app settings
  /// can undo this.
  permanentlyDenied,

  /// There is no camera on this device.
  noCamera,

  /// Something else went wrong opening the camera.
  failed,
}

class _PairingScannerScreenState extends State<PairingScannerScreen> {
  MobileScannerController? _controller;
  _ScanStage _stage = _ScanStage.starting;

  /// Why the last decode was rejected. Shown under the preview so the user
  /// can tell "that QR is not a pairing code" from "the camera is broken",
  /// while scanning continues.
  String? _rejected;

  /// Set the instant a valid payload is found, so a second frame decoding the
  /// same code cannot pop the route twice.
  bool _handled = false;

  @override
  void initState() {
    super.initState();
    unawaited(_start());
  }

  Future<void> _start() async {
    // Backstop: the affordance that opens this screen is gated, but a route
    // is reachable by other means and a desktop build has no camera code to
    // run at all.
    if (!platformCapabilities.supportsQrPairing) {
      if (mounted) setState(() => _stage = _ScanStage.noCamera);
      return;
    }
    if (!await cameraPlatform.hasCamera()) {
      if (mounted) setState(() => _stage = _ScanStage.noCamera);
      return;
    }
    final permission = await cameraPlatform.requestCameraPermission();
    if (!mounted) return;
    switch (permission) {
      case CameraPermission.denied:
        setState(() => _stage = _ScanStage.denied);
        return;
      case CameraPermission.permanentlyDenied:
        setState(() => _stage = _ScanStage.permanentlyDenied);
        return;
      case CameraPermission.granted:
        break;
    }
    final controller = MobileScannerController(
      // Only QR carries pairing codes; narrowing the formats keeps the
      // decoder from spending frames on barcodes we would reject anyway.
      formats: const [BarcodeFormat.qrCode],
      detectionSpeed: DetectionSpeed.noDuplicates,
    );
    try {
      await controller.start();
    } catch (error) {
      await controller.dispose();
      if (!mounted) return;
      setState(() {
        _stage = _ScanStage.failed;
        // A camera failure message is about the device, not the payload, so
        // it is safe to show — and it is the only clue the user has.
        _rejected = error is MobileScannerException
            ? error.errorDetails?.message ?? error.errorCode.name
            : '$error';
      });
      return;
    }
    if (!mounted) {
      await controller.dispose();
      return;
    }
    setState(() {
      _controller = controller;
      _stage = _ScanStage.scanning;
    });
  }

  void _onDetect(BarcodeCapture capture) {
    if (_handled) return;
    for (final barcode in capture.barcodes) {
      final raw = barcode.rawValue;
      if (raw == null || raw.isEmpty) continue;
      final parsed = parsePairingPayload(raw);
      if (parsed.ok) {
        _handled = true;
        // Stop before popping so no further frame is decoded behind the
        // closing route.
        unawaited(_controller?.stop());
        Navigator.of(context).pop(parsed.payload);
        return;
      }
      // Not a pairing code. Say so and keep scanning — the user has very
      // likely just pointed the camera at the wrong QR.
      if (_rejected != parsed.error) {
        setState(() => _rejected = parsed.error);
      }
    }
  }

  @override
  void dispose() {
    unawaited(_controller?.dispose());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      key: const ValueKey('pairing-scanner-screen'),
      appBar: AppBar(
        leading: IconButton(
          tooltip: 'Close the scanner',
          onPressed: () => Navigator.of(context).pop(),
          icon: const Icon(Icons.close_rounded),
        ),
        title: const Text('Scan pairing code'),
      ),
      body: SafeArea(
        child: switch (_stage) {
          _ScanStage.starting => const Center(
            key: ValueKey('pairing-scanner-starting'),
            child: CircularProgressIndicator(),
          ),
          _ScanStage.scanning => _preview(theme),
          _ScanStage.denied => _Recovery(
            key: const ValueKey('pairing-scanner-denied'),
            icon: Icons.photo_camera_outlined,
            title: 'Camera access is needed to scan',
            body:
                'The camera is used only to read the QR that opencode2 pair '
                'prints, and only while this screen is open. You can paste '
                'the code instead — it does exactly the same thing.',
            primaryLabel: 'Try again',
            onPrimary: () {
              setState(() => _stage = _ScanStage.starting);
              unawaited(_start());
            },
            secondaryLabel: 'Paste it instead',
            onSecondary: () => Navigator.of(context).pop(),
          ),
          _ScanStage.permanentlyDenied => _Recovery(
            key: const ValueKey('pairing-scanner-blocked'),
            icon: Icons.no_photography_outlined,
            title: 'Camera access is turned off',
            body:
                'Android will not ask again, so this has to be changed in '
                'app settings: turn on Camera, then come back. Pasting the '
                'code needs no permission at all and works right now.',
            primaryLabel: 'Open app settings',
            onPrimary: () => unawaited(cameraPlatform.openAppSettings()),
            secondaryLabel: 'Paste it instead',
            onSecondary: () => Navigator.of(context).pop(),
          ),
          _ScanStage.noCamera => _Recovery(
            key: const ValueKey('pairing-scanner-no-camera'),
            icon: Icons.no_photography_outlined,
            title: 'This device has no camera',
            body:
                'There is nothing to scan with. Run opencode2 pair on the '
                'server, copy the code it prints, and paste it into the '
                'server editor.',
            primaryLabel: 'Paste it instead',
            onPrimary: () => Navigator.of(context).pop(),
          ),
          _ScanStage.failed => _Recovery(
            key: const ValueKey('pairing-scanner-failed'),
            icon: Icons.error_outline_rounded,
            title: 'The camera could not be opened',
            body: [
              ?_rejected,
              'Another app may be holding the camera. Pasting the pairing '
                  'code works either way.',
            ].join('\n\n'),
            primaryLabel: 'Try again',
            onPrimary: () {
              setState(() {
                _stage = _ScanStage.starting;
                _rejected = null;
              });
              unawaited(_start());
            },
            secondaryLabel: 'Paste it instead',
            onSecondary: () => Navigator.of(context).pop(),
          ),
        },
      ),
    );
  }

  Widget _preview(ThemeData theme) {
    final controller = _controller;
    if (controller == null) {
      return const Center(child: CircularProgressIndicator());
    }
    final rejected = _rejected;
    return Column(
      key: const ValueKey('pairing-scanner-preview'),
      children: [
        Expanded(
          child: MobileScanner(controller: controller, onDetect: _onDetect),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                'Point the camera at the QR code printed by opencode2 pair.',
                textAlign: TextAlign.center,
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              if (rejected != null) ...[
                const SizedBox(height: 12),
                Semantics(
                  key: const ValueKey('pairing-scanner-rejected'),
                  container: true,
                  liveRegion: true,
                  child: Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.errorContainer,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Text(
                      rejected,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onErrorContainer,
                        height: 1.35,
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

/// A full-screen "this did not work, here is what to do" state.
class _Recovery extends StatelessWidget {
  const _Recovery({
    super.key,
    required this.icon,
    required this.title,
    required this.body,
    required this.primaryLabel,
    required this.onPrimary,
    this.secondaryLabel,
    this.onSecondary,
  });

  final IconData icon;
  final String title;
  final String body;
  final String primaryLabel;
  final VoidCallback onPrimary;
  final String? secondaryLabel;
  final VoidCallback? onSecondary;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 48, 24, 24),
      children: [
        Icon(icon, size: 48, color: theme.colorScheme.onSurfaceVariant),
        const SizedBox(height: 20),
        Text(
          title,
          textAlign: TextAlign.center,
          style: theme.textTheme.titleMedium,
        ),
        const SizedBox(height: 10),
        Text(
          body,
          textAlign: TextAlign.center,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            height: 1.4,
          ),
        ),
        const SizedBox(height: 24),
        FilledButton(
          key: const ValueKey('pairing-scanner-primary'),
          onPressed: onPrimary,
          child: Text(primaryLabel),
        ),
        if (secondaryLabel case final label?) ...[
          const SizedBox(height: 10),
          TextButton(
            key: const ValueKey('pairing-scanner-secondary'),
            onPressed: onSecondary,
            child: Text(label),
          ),
        ],
      ],
    );
  }
}
