import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:path_provider/path_provider.dart';

import '../domain/file_share_gateway.dart';

/// A tiny read-only download server for one file, bound to the phone's
/// Wi-Fi IPv4 address only (never 0.0.0.0, never mobile data).
///
/// Security shape: the only reachable URL is `/<token>/<name>` with a
/// 192-bit token from [Random.secure]; every other path is 404; only GET
/// and HEAD answer; the file streams; the share ends after [lifetime], on
/// [stop], on a new share, and when the app is detached.
class WifiFileShare with WidgetsBindingObserver implements FileShareGateway {
  WifiFileShare({
    Future<InternetAddress?> Function()? findWifiAddress,
    Future<List<String>> Function()? privateRoots,
    Future<List<String>> Function()? allowedRoots,
    this.lifetime = const Duration(minutes: 15),
    DateTime Function()? now,
    Random? random,
  }) : _findWifi = findWifiAddress ?? wifiAddress,
       _privateRoots = privateRoots ?? _appPrivateRoots,
       _allowedRoots = allowedRoots ?? _builtinRoots,
       _now = now ?? DateTime.now,
       _random = random ?? Random.secure();

  final Future<InternetAddress?> Function() _findWifi;
  final Future<List<String>> Function() _privateRoots;
  final Future<List<String>> Function() _allowedRoots;
  final DateTime Function() _now;
  final Random _random;
  final Duration lifetime;

  final ValueNotifier<FileShareState?> _state = ValueNotifier(null);
  HttpServer? _server;
  Timer? _expiry;
  int _downloads = 0;
  bool _observing = false;

  @override
  bool get supported => !kIsWeb;

  @override
  FileShareState? get value => _state.value;

  @override
  void addListener(VoidCallback listener) => _state.addListener(listener);

  @override
  void removeListener(VoidCallback listener) => _state.removeListener(listener);

  /// The phone's current Wi-Fi (or hotspot) IPv4 address, or null. Mobile
  /// data interfaces (rmnet*, ccmni*) and VPNs never qualify; the address
  /// must be a private LAN one.
  static Future<InternetAddress?> wifiAddress() async {
    try {
      final all = await NetworkInterface.list(
        type: InternetAddressType.IPv4,
        includeLoopback: false,
      );
      for (final nic in all) {
        final name = nic.name.toLowerCase();
        final wifi =
            name.startsWith('wlan') ||
            name.startsWith('swlan') ||
            name.startsWith('ap');
        if (!wifi) continue;
        for (final address in nic.addresses) {
          if (isPrivateLan(address)) return address;
        }
      }
    } on Object {
      return null;
    }
    return null;
  }

  @visibleForTesting
  static bool isPrivateLan(InternetAddress address) {
    final b = address.rawAddress;
    if (b.length != 4) return false;
    return b[0] == 10 ||
        (b[0] == 172 && b[1] >= 16 && b[1] <= 31) ||
        (b[0] == 192 && b[1] == 168);
  }

  static Future<List<String>> _appPrivateRoots() async {
    final roots = <String>[];
    for (final lookup in [
      getApplicationSupportDirectory,
      getApplicationDocumentsDirectory,
    ]) {
      try {
        roots.add((await lookup()).path);
      } on Object {
        // Not available on this platform: nothing to block there.
      }
    }
    return roots;
  }

  /// Whether [path] sits inside the app's private storage.
  Future<bool> isPrivatePath(String path) async {
    final resolved = await _resolve(path);
    return _insideAny(resolved, await _privateRoots());
  }

  Future<bool> _insideAny(String resolved, List<String> roots) async {
    for (final root in roots) {
      final base = await _resolve(root);
      if (resolved == base || resolved.startsWith('$base/')) return true;
    }
    return false;
  }

  Future<String> _resolve(String path) async {
    try {
      return await File(path).resolveSymbolicLinks();
    } on Object {
      try {
        return await Directory(path).resolveSymbolicLinks();
      } on Object {
        return path;
      }
    }
  }

  static Future<List<String>> _builtinRoots() async {
    try {
      final support = (await getApplicationSupportDirectory()).path;
      return ['$support/linux/ubuntu', '$support/projects'];
    } on Object {
      return const [];
    }
  }

  /// Whether [path] (a real path, symlinks resolved) may be handed out. The
  /// app's own storage is refused, except regular files inside the built-in
  /// Linux and its projects; keys and credentials are refused everywhere.
  @visibleForTesting
  Future<bool> mayShare(String resolved) async {
    if (looksSecret(resolved)) return false;
    if (!await _insideAny(resolved, await _privateRoots())) return true;
    return _insideAny(resolved, await _allowedRoots());
  }

  /// Names and folders that may hold keys, passwords or logins. Matched on
  /// the real path, so a link cannot dodge it.
  static bool looksSecret(String path) {
    final parts = path.split(RegExp(r'[\\/]')).where((p) => p.isNotEmpty);
    final names = parts.map((p) => p.toLowerCase()).toList();
    if (names.isEmpty) return true;
    const folders = {
      'shared_prefs',
      'databases',
      'no_backup',
      'app_flutter',
      'code_cache',
      '.ssh',
      '.gnupg',
      '.codex',
      '.claude',
      '.aws',
      '.kube',
      '.docker',
      '.gcloud',
    };
    for (final folder in names.take(names.length - 1)) {
      // .oc-builtin holds the phone server's password, .oc-profiles each
      // agent's sign-ins: every app-owned .oc-* folder stays private.
      if (folders.contains(folder) || folder.startsWith('.oc-')) return true;
    }
    for (var i = 0; i < names.length - 1; i++) {
      // ~/.config/opencode, ~/.local/share/opencode, ~/.config/gh, /etc/ssh
      if (names[i] == '.config' &&
          (names[i + 1] == 'opencode' || names[i + 1] == 'gh')) {
        return true;
      }
      if (names[i] == '.local' &&
          i + 2 < names.length &&
          names[i + 1] == 'share' &&
          names[i + 2] == 'opencode') {
        return true;
      }
      if (names[i] == 'etc' && names[i + 1] == 'ssh') return true;
    }
    final name = names.last;
    if (name.startsWith('.env') ||
        name.startsWith('id_rsa') ||
        name.startsWith('id_ed25519') ||
        name.startsWith('id_ecdsa') ||
        name == 'auth.json' ||
        name == 'shadow' ||
        name == '.netrc' ||
        name == '.npmrc' ||
        name == '.git-credentials' ||
        name.endsWith('.pem') ||
        name.endsWith('.key') ||
        name.endsWith('.p12') ||
        name.endsWith('.pfx') ||
        name.endsWith('.keystore') ||
        name.endsWith('.jks')) {
      return true;
    }
    if (name.contains('password') ||
        name.contains('secret') ||
        name.contains('token')) {
      return true;
    }
    return name.contains('credentials') && name.endsWith('.json');
  }

  /// A source for a file on disk the person opened, streamed from disk
  /// (never read whole into memory). Refuses keys and credentials, links
  /// that lead out of where the file seems to be, and the app's private
  /// storage outside the built-in Linux ([FileShareFailure.privateFile]).
  Future<FileShareSource> fileSource(String path, {String? mimeType}) async {
    final String resolved;
    try {
      resolved = await File(path).resolveSymbolicLinks();
    } on Object {
      throw const FileShareException(FileShareFailure.unavailable);
    }
    // Both the name given and the real file must pass.
    if (looksSecret(path) || !await mayShare(resolved)) {
      throw const FileShareException(FileShareFailure.privateFile);
    }
    // A link inside the allowed area must not lead out of it.
    final cut = path.lastIndexOf('/');
    if (cut > 0) {
      final folder = await _resolve(path.substring(0, cut));
      final allowed = await _allowedRoots();
      if (await _insideAny(folder, allowed) &&
          !await _insideAny(resolved, allowed)) {
        throw const FileShareException(FileShareFailure.privateFile);
      }
    }
    final file = File(resolved);
    final int length;
    try {
      if (await FileSystemEntity.type(resolved, followLinks: false) !=
          FileSystemEntityType.file) {
        throw const FileShareException(FileShareFailure.unavailable);
      }
      length = await file.length();
    } on FileShareException {
      rethrow;
    } on Object {
      throw const FileShareException(FileShareFailure.unavailable);
    }
    return FileShareSource(
      name: path.split(RegExp(r'[\\/]')).last,
      length: length,
      mimeType: mimeType,
      open: file.openRead,
    );
  }

  String _newToken() {
    final bytes = Uint8List.fromList(
      List<int>.generate(24, (_) => _random.nextInt(256)),
    );
    return base64Url.encode(bytes).replaceAll('=', '');
  }

  @override
  Future<FileShareState> start(FileShareSource source) async {
    await stop();
    final address = await _findWifi();
    if (address == null) {
      throw const FileShareException(FileShareFailure.noWifi);
    }
    final HttpServer server;
    try {
      server = await HttpServer.bind(address, 0);
    } on Object {
      throw const FileShareException(FileShareFailure.unavailable);
    }
    final token = _newToken();
    final name = _safeName(source.name);
    final path = '/$token/${Uri.encodeComponent(name)}';
    _server = server;
    _downloads = 0;
    final state = FileShareState(
      url: Uri(
        scheme: 'http',
        host: address.address,
        port: server.port,
        path: path,
      ).toString(),
      name: name,
      expiresAt: _now().add(lifetime),
    );
    _state.value = state;
    server.listen(
      (request) => unawaited(_serve(request, server, token, name, source)),
      onError: (Object _) {},
      cancelOnError: false,
    );
    _expiry = Timer(lifetime, () => unawaited(stop()));
    if (!_observing) {
      _observing = true;
      WidgetsBinding.instance.addObserver(this);
    }
    return state;
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.detached) unawaited(stop());
  }

  static String _safeName(String raw) {
    final base = raw
        .split(RegExp(r'[\\/]'))
        .last
        .replaceAll(RegExp(r'[\x00-\x1F\x7F"]'), '_');
    return base.isEmpty ? 'file' : base;
  }

  Future<void> _serve(
    HttpRequest request,
    HttpServer server,
    String token,
    String name,
    FileShareSource source,
  ) async {
    final response = request.response;
    try {
      final segments = request.uri.pathSegments;
      final good =
          segments.length == 2 &&
          _sameText(segments[0], token) &&
          segments[1] == name;
      if (!good) {
        response.statusCode = HttpStatus.notFound;
        await response.close();
        return;
      }
      final method = request.method;
      if (method != 'GET' && method != 'HEAD') {
        response.statusCode = HttpStatus.methodNotAllowed;
        response.headers.set(HttpHeaders.allowHeader, 'GET, HEAD');
        await response.close();
        return;
      }
      response.headers
        ..set(
          HttpHeaders.contentTypeHeader,
          source.mimeType ?? 'application/octet-stream',
        )
        ..set(
          'content-disposition',
          'attachment; filename="${name.replaceAll(RegExp(r'[^\x20-\x7E]'), '_')}"; '
              "filename*=UTF-8''${Uri.encodeComponent(name)}",
        )
        ..set('x-content-type-options', 'nosniff')
        ..set(HttpHeaders.cacheControlHeader, 'no-store');
      response.contentLength = source.length;
      if (method == 'HEAD') {
        await response.close();
        return;
      }
      await response.addStream(source.open());
      await response.close();
      if (identical(_server, server)) {
        _downloads++;
        final current = _state.value;
        if (current != null) _state.value = current.withDownloads(_downloads);
      }
    } on Object {
      // The downloader went away or the file could not be read: end this
      // response only.
      unawaited(response.close().then((_) {}, onError: (Object _) {}));
    }
  }

  static bool _sameText(String a, String b) {
    if (a.length != b.length) return false;
    var diff = 0;
    for (var i = 0; i < a.length; i++) {
      diff |= a.codeUnitAt(i) ^ b.codeUnitAt(i);
    }
    return diff == 0;
  }

  @override
  Future<void> stop() async {
    _expiry?.cancel();
    _expiry = null;
    final server = _server;
    _server = null;
    _state.value = null;
    if (_observing) {
      _observing = false;
      WidgetsBinding.instance.removeObserver(this);
    }
    if (server != null) {
      try {
        await server.close(force: true);
      } on Object {
        // Closing twice is harmless.
      }
    }
  }
}
