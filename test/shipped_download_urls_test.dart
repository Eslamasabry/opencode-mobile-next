// Issue #87: the AI Team manifest shipped in a public release with the
// owner's Tailscale address (http://100.101.102.103:8876/aiteam/) as its
// download server, so the download could never work for anyone else. These
// checks read every text file under assets/ and fail the build when a URL
// points at an address only one machine or one private network can reach,
// or when a download manifest uses plain http.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/team_runtime.dart';

final _url = RegExp(r'''https?://[^\s"'<>()\[\]]+''');

const _textExtensions = {'.json', '.md', '.txt', '.xml', '.svg', '.yaml'};

/// Why [host] is not reachable from an arbitrary phone on the internet, or
/// null when it is a public name or address.
String? privateHostProblem(String host) {
  final name = host.toLowerCase();
  if (name == 'localhost' || name.endsWith('.localhost')) return 'loopback';
  if (name.endsWith('.local') || name.endsWith('.lan')) return 'LAN name';
  if (name.endsWith('.ts.net')) return 'Tailscale name';
  if (name.endsWith('.internal') || name.endsWith('.home.arpa')) {
    return 'private name';
  }
  final address = InternetAddress.tryParse(
    name.replaceAll(RegExp(r'^\[|\]$'), ''),
  );
  if (address == null) return null;
  if (address.isLoopback) return 'loopback';
  if (address.isLinkLocal) return 'link-local';
  final b = address.rawAddress;
  if (address.type == InternetAddressType.IPv4) {
    if (b[0] == 0) return 'unspecified';
    if (b[0] == 10) return 'private (10/8)';
    if (b[0] == 172 && b[1] >= 16 && b[1] < 32) return 'private (172.16/12)';
    if (b[0] == 192 && b[1] == 168) return 'private (192.168/16)';
    // 100.64.0.0/10: carrier-grade NAT, and every Tailscale address.
    if (b[0] == 100 && b[1] >= 64 && b[1] < 128) return 'Tailscale/CGNAT';
    return null;
  }
  // IPv6 unique local (fc00::/7), which Tailscale also hands out.
  if ((b[0] & 0xfe) == 0xfc) return 'unique local IPv6';
  if (address.isMulticast) return 'multicast';
  return null;
}

List<File> _assetTextFiles() =>
    Directory('assets')
        .listSync(recursive: true)
        .whereType<File>()
        .where((f) => _textExtensions.any(f.path.endsWith))
        .toList()
      ..sort((a, b) => a.path.compareTo(b.path));

void main() {
  test('the private-host check catches the address that shipped in #87', () {
    expect(privateHostProblem('100.101.102.103'), 'Tailscale/CGNAT');
    expect(privateHostProblem('127.0.0.1'), 'loopback');
    expect(privateHostProblem('[::1]'), 'loopback');
    expect(privateHostProblem('169.254.1.2'), 'link-local');
    expect(privateHostProblem('192.168.1.20'), isNotNull);
    expect(privateHostProblem('10.0.0.5'), isNotNull);
    expect(privateHostProblem('172.20.0.1'), isNotNull);
    expect(privateHostProblem('dev-pc.tail1234.ts.net'), isNotNull);
    expect(privateHostProblem('fd7a:115c:a1e0::1'), isNotNull);
    expect(privateHostProblem('github.com'), isNull);
    expect(privateHostProblem('100.128.0.1'), isNull);
    expect(privateHostProblem('172.32.0.1'), isNull);
  });

  test('no URL in assets/ points at a private address', () {
    final problems = <String>[];
    for (final file in _assetTextFiles()) {
      final text = file.readAsStringSync();
      for (final match in _url.allMatches(text)) {
        final uri = Uri.tryParse(match.group(0)!);
        if (uri == null || uri.host.isEmpty) continue;
        final problem = privateHostProblem(uri.host);
        if (problem != null) {
          problems.add('${file.path}: ${match.group(0)} ($problem)');
        }
      }
    }
    expect(problems, isEmpty);
  });

  test('every download manifest in assets/ is https and public', () {
    final manifests = _assetTextFiles().where((f) => f.path.endsWith('.json'));
    expect(
      manifests.map((f) => f.path),
      containsAll([
        TermuxTeamRuntime.manifestAsset,
        TermuxTeamRuntime.manifestAssetX86,
      ]),
    );
    for (final file in manifests) {
      final raw = file.readAsStringSync();
      // Any URL in a shipped JSON file is something the app may fetch.
      for (final match in _url.allMatches(raw)) {
        expect(
          match.group(0),
          startsWith('https://'),
          reason: '${file.path} downloads over plain http',
        );
      }
      final json = jsonDecode(raw);
      if (json is! Map || !json.containsKey('base_url')) continue;
      final manifest = TeamRuntimeManifest.parse(raw);
      expect(manifest, isNotNull, reason: file.path);
      final base = Uri.parse(manifest!.baseUrl);
      expect(base.scheme, 'https', reason: file.path);
      expect(base.hasPort, isFalse, reason: '${file.path}: odd port');
      expect(privateHostProblem(base.host), isNull, reason: file.path);
      expect(manifest.baseUrl, endsWith('/'), reason: file.path);
      for (final entry in (json['files'] as Map).values) {
        final sha = (entry as Map)['sha256'] as String;
        expect(sha, matches(RegExp(r'^[0-9a-f]{64}$')), reason: file.path);
        expect(entry['bytes'], isA<int>(), reason: file.path);
      }
    }
  });
}
