import 'dart:convert';

import '../domain/crash_report.dart';
import '../domain/diagnostics_error.dart';
import '../platform/share_intent.dart';
import 'crash_diagnostics.dart';

/// Builds only from BD7's validated categories, sources and times. Previewing
/// changes neither consent nor storage; sharing uses the exact issued text.
class CrashReportBuilder {
  CrashReportBuilder({
    required this.loadController,
    Future<bool> Function(String text)? shareText,
  }) : _shareText = shareText;

  static const maxBytes = 16 * 1024;
  static const maxEntries = 20;
  static const _header =
      'OpenCode Mobile crash report\n'
      'Captured error categories and times only.\n'
      'No messages, stacks or conversations are included.\n\n';

  final Future<CrashDiagnosticsController?> Function() loadController;
  final Future<bool> Function(String text)? _shareText;
  final _issued = Expando<_PreviewEvidence>();
  bool _sharing = false;

  bool get shareSupported => _shareText != null || ShareOut.supported;

  Future<CrashReportResult> preview() async {
    final CrashDiagnosticsController? controller;
    try {
      controller = await loadController();
    } catch (_) {
      return const CrashReportResult(error: DiagnosticsError.unavailable);
    }
    if (controller == null) {
      return const CrashReportResult(error: DiagnosticsError.unavailable);
    }
    final snapshot = controller.snapshot;
    if (!snapshot.available) {
      return const CrashReportResult(error: DiagnosticsError.unavailable);
    }
    if (snapshot.storageFailed) {
      return const CrashReportResult(error: DiagnosticsError.storageFailed);
    }
    if (!snapshot.enabled) {
      return const CrashReportResult(error: DiagnosticsError.captureDisabled);
    }
    if (snapshot.records.isEmpty) {
      return const CrashReportResult(error: DiagnosticsError.noCapture);
    }
    final text = StringBuffer(_header);
    var bytes = utf8.encode(_header).length;
    var count = 0;
    for (final record in snapshot.records.take(maxEntries)) {
      final line =
          '${record.at.toIso8601String()} | '
          '${record.source} | ${record.category}\n';
      final lineBytes = utf8.encode(line).length;
      if (bytes + lineBytes > maxBytes) break;
      text.write(line);
      bytes += lineBytes;
      count++;
    }
    if (count == 0) {
      return const CrashReportResult(error: DiagnosticsError.noCapture);
    }
    final preview = CrashReportPreview(
      text: text.toString(),
      byteCount: bytes,
      recordCount: count,
    );
    _issued[preview] = _PreviewEvidence(
      controller,
      snapshot.consentEpoch,
      snapshot.revision,
    );
    return CrashReportResult(preview: preview);
  }

  Future<CrashShareResult> share(CrashReportPreview preview) async {
    final evidence = _issued[preview];
    if (evidence == null) {
      return const CrashShareResult(
        opened: false,
        error: DiagnosticsError.stalePreview,
      );
    }
    if (_sharing) {
      return const CrashShareResult(
        opened: false,
        error: DiagnosticsError.busy,
      );
    }
    _sharing = true;
    try {
      final CrashDiagnosticsController? controller;
      try {
        controller = await loadController();
      } catch (_) {
        return const CrashShareResult(
          opened: false,
          error: DiagnosticsError.stalePreview,
        );
      }
      if (!identical(controller, evidence.controller)) {
        return const CrashShareResult(
          opened: false,
          error: DiagnosticsError.stalePreview,
        );
      }
      final snapshot = controller!.snapshot;
      if (!snapshot.available ||
          !snapshot.enabled ||
          snapshot.storageFailed ||
          snapshot.consentEpoch != evidence.consentEpoch ||
          snapshot.revision != evidence.revision) {
        return const CrashShareResult(
          opened: false,
          error: DiagnosticsError.stalePreview,
        );
      }
      if (!shareSupported) {
        return const CrashShareResult(
          opened: false,
          error: DiagnosticsError.shareUnavailable,
        );
      }
      final shareText = _shareText;
      final opened = shareText == null
          ? await ShareOut.text(preview.text)
          : await shareText(preview.text);
      return CrashShareResult(
        opened: opened,
        error: opened ? null : DiagnosticsError.shareUnavailable,
      );
    } catch (_) {
      return const CrashShareResult(
        opened: false,
        error: DiagnosticsError.shareUnavailable,
      );
    } finally {
      _sharing = false;
    }
  }
}

final class _PreviewEvidence {
  const _PreviewEvidence(this.controller, this.consentEpoch, this.revision);

  final CrashDiagnosticsController controller;
  final int consentEpoch;
  final int revision;
}
