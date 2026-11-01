import 'desktop_release_check.dart' show buildNumberFromTag;

/// One downloadable file of a release.
class AppReleaseAsset {
  const AppReleaseAsset({
    required this.name,
    required this.url,
    required this.size,
  });

  final String name;
  final String url;
  final int size;
}

/// A published release of the app, as GitHub's releases API describes it.
///
/// Everything here is data from the network: the tag decides nothing but
/// "newer or not", and the files are checked (checksum, signer, package,
/// version) before anything installs.
class AppRelease {
  const AppRelease({
    required this.tag,
    required this.version,
    required this.build,
    required this.notes,
    required this.pageUrl,
    required this.assets,
    required this.json,
  });

  /// `v2.1.0+57`
  final String tag;

  /// `2.1.0`
  final String version;

  /// 57, the versionCode.
  final int build;

  /// The release notes (Markdown).
  final String notes;
  final String pageUrl;
  final List<AppReleaseAsset> assets;

  /// The source JSON, kept so the finding survives a restart.
  final Map<String, dynamic> json;

  static final _tag = RegExp(r'^v(\d+\.\d+\.\d+)\+\d+');

  /// Null for a draft, a pre-release or a tag that is not `v<x.y.z>+<n>`.
  static AppRelease? fromJson(Map<dynamic, dynamic> raw) {
    final json = Map<String, dynamic>.from(raw);
    if (json['draft'] == true || json['prerelease'] == true) return null;
    final tag = json['tag_name'];
    if (tag is! String) return null;
    final build = buildNumberFromTag(tag);
    final match = _tag.firstMatch(tag);
    if (build == null || match == null) return null;
    final assets = <AppReleaseAsset>[];
    for (final entry in (json['assets'] as List<dynamic>? ?? const [])) {
      if (entry is! Map) continue;
      final name = entry['name'];
      final url = entry['browser_download_url'];
      final size = entry['size'];
      if (name is String && url is String && size is int) {
        assets.add(AppReleaseAsset(name: name, url: url, size: size));
      }
    }
    final page = json['html_url'];
    return AppRelease(
      tag: tag,
      version: match.group(1)!,
      build: build,
      notes: json['body'] is String ? json['body'] as String : '',
      pageUrl: page is String ? page : '',
      assets: assets,
      json: json,
    );
  }

  AppReleaseAsset? get checksums =>
      assets.where((a) => a.name == 'SHA256SUMS').firstOrNull;

  /// The file for this phone: the first of its [abis] a release file exists
  /// for (arm64-v8a, armeabi-v7a), else the universal one.
  AppReleaseAsset? apkFor(List<String> abis) {
    final apks = assets.where((a) => a.name.endsWith('.apk')).toList();
    for (final abi in abis) {
      final hit = apks.where((a) => a.name.endsWith('-$abi.apk')).firstOrNull;
      if (hit != null) return hit;
    }
    return apks
        .where((a) => !RegExp(r'-[a-z0-9_]+-v\d+a\.apk$').hasMatch(a.name))
        .firstOrNull;
  }

  /// The first sentence of the notes' first paragraph, without Markdown
  /// marks; empty when the notes say nothing.
  String get summary {
    for (final block in notes.split(RegExp(r'\n\s*\n'))) {
      final text = block.trim();
      if (text.isEmpty ||
          text.startsWith('#') ||
          text.startsWith('-') ||
          text.startsWith('|') ||
          text.startsWith('>') ||
          text.startsWith('```')) {
        continue;
      }
      final plain = text
          .replaceAll(RegExp(r'[*_`]'), '')
          .replaceAllMapped(RegExp(r'\[([^\]]*)\]\([^)]*\)'), (m) => m[1]!)
          .replaceAll(RegExp(r'\s+'), ' ');
      final end = RegExp(r'[.!?](\s|$)').firstMatch(plain);
      return end == null ? plain : plain.substring(0, end.start + 1);
    }
    return '';
  }
}

/// `SHA256SUMS`: `<64 hex>  <file name>` per line (a `*` before the name
/// marks binary mode). Lower-cased hex by file name.
Map<String, String> parseSha256Sums(String text) {
  final out = <String, String>{};
  for (final line in text.split('\n')) {
    final m = RegExp(
      r'^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$',
    ).firstMatch(line.trim());
    if (m != null) out[m.group(2)!] = m.group(1)!.toLowerCase();
  }
  return out;
}
