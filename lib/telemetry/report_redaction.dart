import '../ui/kit/kit_redact.dart';

/// The redaction every "Send a problem report" text goes through before the
/// person sees it (docs/telemetry/health-and-diagnostics.md section E).
///
/// It starts from [KitRedact] (provider keys, tokens, Bearer values, secret
/// assignments, user-info, JWTs, registered secrets) and then blanks what a
/// log line can still carry about the person's setup:
///
/// - URLs, whole: `https://host:port/path?query` becomes `[url]`;
/// - e-mail addresses;
/// - IPv4 and IPv6 addresses;
/// - file paths: absolute (`/data/user/0/...`), home (`~/...`) and Windows
///   (`C:\...`);
/// - host names: a dotted name ending in a common top-level domain, or in
///   `.local`/`.lan`/`.internal`, or any `*.ts.net` name;
/// - long opaque tokens (32 or more letters and digits in one run).
///
/// Code locations (`package:` and `dart:` libraries with line numbers) are
/// kept: they are what a crash is read by.
abstract final class ReportRedaction {
  static const String url = '[url]';
  static const String path = '[path]';
  static const String host = '[host]';
  static const String address = '[address]';
  static const String email = '[email]';

  static final RegExp _codeLocation = RegExp(
    r'(?:package|dart):[A-Za-z0-9_./\-]+(?::\d+){0,2}',
  );
  static final RegExp _url = RegExp(
    r'''\b[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s"'<>)\]]+''',
  );
  static final RegExp _email = RegExp(
    r'\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)+\b',
  );
  static final RegExp _ipv4 = RegExp(
    r'\b\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?\b',
  );
  static final RegExp _ipv6 = RegExp(
    r'(?<![\w:])(?:[0-9A-Fa-f]{1,4}:){2,7}[0-9A-Fa-f]{1,4}(?![\w:])',
  );
  static final RegExp _windowsPath = RegExp(r'''\b[A-Za-z]:\\[^\s"'<>|]*''');
  static final RegExp _unixPath = RegExp(
    r'''(?<![\w:/.\-])(?:~|\.{1,2})?/[\w.\-@+%~=,]+(?:/[\w.\-@+%~=,]*)*''',
  );
  static final RegExp _host = RegExp(
    r'\b(?:[A-Za-z0-9\-]+\.)+(?:ts\.net|com|net|org|io|dev|app|xyz|local|lan|internal|home|me|co|ai|cloud|info|tech|online|site|us|uk|de|fr|eu)\b',
    caseSensitive: false,
  );
  static final RegExp _opaque = RegExp(r'\b[A-Za-z0-9_-]{32,}\b');

  /// [text] with everything listed above blanked. Idempotent.
  static String redact(String text) {
    // Park the code locations so the host and path rules cannot touch them.
    final parked = <String>[];
    var safe = text.replaceAllMapped(_codeLocation, (m) {
      parked.add(m.group(0)!);
      return '\u0000${parked.length - 1}\u0000';
    });
    safe = KitRedact.text(safe);
    safe = safe.replaceAll(_url, url);
    safe = safe.replaceAll(_email, email);
    safe = safe.replaceAll(_ipv4, address);
    safe = safe.replaceAll(_ipv6, address);
    safe = safe.replaceAll(_windowsPath, path);
    safe = safe.replaceAll(_unixPath, path);
    safe = safe.replaceAll(_host, host);
    safe = safe.replaceAll(_opaque, KitRedact.mask);
    return safe.replaceAllMapped(
      RegExp('\u0000(\\d+)\u0000'),
      (m) => parked[int.parse(m.group(1)!)],
    );
  }
}
