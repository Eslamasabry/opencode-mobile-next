/// A user-reviewed link, not fetched page content or a server reference ID.
/// Integrators may add this as ordinary prompt text after review. Never map an
/// HTTP URL to a directory reference or fetch it as a remote file attachment.
class WebSourceSelection {
  static const maxTitleLength = 200;
  static const maxUrlLength = 2048;
  static const maxExcerptLength = 2000;

  final String title;
  final String url;

  /// Optional user-pasted text. This is unverified, untrusted source material.
  final String? excerpt;

  const WebSourceSelection._(this.title, this.url, this.excerpt);

  factory WebSourceSelection({
    required String title,
    required String url,
    String? excerpt,
  }) {
    final normalized = url.trim();
    final uri = Uri.tryParse(normalized);
    if (normalized.length > maxUrlLength ||
        normalized.contains(RegExp(r'[\s\x00-\x1f\x7f\\]')) ||
        uri == null ||
        (uri.scheme != 'https' && uri.scheme != 'http') ||
        uri.userInfo.isNotEmpty ||
        !_publicDnsHost(uri.host)) {
      throw const FormatException(
        'Enter a public HTTP or HTTPS URL without credentials. '
        'Local addresses and IP literals are not supported.',
      );
    }
    final label = title.trim().isEmpty ? uri.host : title.trim();
    final text = excerpt?.trim();
    if (label.length > maxTitleLength ||
        label.contains(RegExp(r'[\x00-\x1f\x7f]'))) {
      throw const FormatException('Use a title of at most 200 characters.');
    }
    if (text != null && text.length > maxExcerptLength) {
      throw const FormatException('Use an excerpt of at most 2000 characters.');
    }
    return WebSourceSelection._(
      label,
      uri.toString(),
      text == null || text.isEmpty ? null : text,
    );
  }

  // Conservative, offline input policy, not DNS resolution or an SSRF guard.
  // No consumer is authorized to fetch a page by receiving this value.
  static bool _publicDnsHost(String host) {
    final name = host.toLowerCase().replaceFirst(RegExp(r'\.$'), '');
    final labels = name.split('.');
    if (labels.length < 2 || name.length > 253) return false;
    if (const [
      'localhost',
      'local',
      'internal',
      'lan',
      'home',
      'test',
      'invalid',
    ].contains(labels.last)) {
      return false;
    }
    if (name == 'home.arpa' || name.endsWith('.home.arpa')) return false;
    if (!RegExp(r'^[a-z]{2,63}$').hasMatch(labels.last)) return false;
    return labels.every(
      (label) =>
          RegExp(r'^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$').hasMatch(label),
    );
  }
}
