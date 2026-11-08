import 'dart:async';
import 'dart:isolate';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';

import 'kit_qr_decoder.dart';
import 'kit_scanner.dart';

KitScannerCamera qrCamera() => _CameraQrCamera();

/// The back camera through the Flutter team's `camera` plugin (CameraX), with
/// frames decoded by [decodeQrFromLuma] in a short-lived isolate. One frame is
/// decoded at a time; frames that arrive meanwhile are dropped, which keeps the
/// preview smooth on a slow phone. The controller is made in [start], so
/// building the part on a machine without a camera touches no platform code.
class _CameraQrCamera implements KitScannerCamera {
  _CameraQrCamera();

  CameraController? _controller;
  final _codes = StreamController<String>.broadcast();
  bool _decoding = false;
  String? _last;

  static bool get _hasCameraCode =>
      !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  @override
  Future<void> start() async {
    if (!_hasCameraCode) throw const KitScannerFailure();
    try {
      final cameras = await availableCameras();
      if (cameras.isEmpty) throw const KitScannerFailure();
      final back = cameras.firstWhere(
        (c) => c.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );
      final controller = CameraController(
        back,
        ResolutionPreset.medium,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.yuv420,
      );
      _controller = controller;
      await controller.initialize();
      await controller.startImageStream(_onFrame);
    } on CameraException catch (error) {
      throw KitScannerFailure(error.description ?? error.code);
    }
  }

  void _onFrame(CameraImage image) {
    if (_decoding || _codes.isClosed) return;
    _decoding = true;
    final plane = image.planes.first;
    final luma = Uint8List.fromList(plane.bytes);
    final width = image.width;
    final height = image.height;
    final stride = plane.bytesPerRow;
    unawaited(
      Isolate.run(
            () => decodeQrFromLuma(luma, width, height, rowStride: stride),
          )
          .then((raw) {
            // Each sighting once: the same code in a row is not repeated.
            if (raw != null &&
                raw.isNotEmpty &&
                raw != _last &&
                !_codes.isClosed) {
              _codes.add(raw);
            }
            _last = raw;
          })
          .catchError((Object _) {})
          .whenComplete(() => _decoding = false),
    );
  }

  @override
  Future<void> stop() async {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) return;
    try {
      if (controller.value.isStreamingImages) {
        await controller.stopImageStream();
      }
    } on CameraException {
      // Already stopped: nothing to release.
    }
  }

  @override
  Future<void> dispose() async {
    final controller = _controller;
    _controller = null;
    await controller?.dispose();
    await _codes.close();
  }

  @override
  Stream<String> get codes => _codes.stream;

  @override
  Widget preview(BuildContext context) {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      return const SizedBox.shrink();
    }
    return FittedBox(
      fit: BoxFit.cover,
      clipBehavior: Clip.hardEdge,
      child: SizedBox(
        width: controller.value.previewSize?.height ?? 1,
        height: controller.value.previewSize?.width ?? 1,
        child: CameraPreview(controller),
      ),
    );
  }
}
