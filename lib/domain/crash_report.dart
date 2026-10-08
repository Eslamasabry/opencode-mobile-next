import 'diagnostics_error.dart';

/// The immutable text the person previews and may explicitly share.
final class CrashReportPreview {
  const CrashReportPreview({
    required this.text,
    required this.byteCount,
    required this.recordCount,
  });

  final String text;
  final int byteCount;
  final int recordCount;
}

final class CrashReportResult {
  const CrashReportResult({this.preview, this.error});

  final CrashReportPreview? preview;
  final DiagnosticsError? error;
}

/// [opened] means the system chooser opened, never that delivery completed.
final class CrashShareResult {
  const CrashShareResult({required this.opened, this.error});

  final bool opened;
  final DiagnosticsError? error;
}
