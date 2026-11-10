// Backing up the in-app server's projects before they can be lost (slice
// clear-storage-guard, 2026-09-28): Android's "Clear storage" deletes
// <filesDir> whole, which is the in-app Ubuntu, its OpenCode data and every
// project in <filesDir>/projects (bound at /root/projects).
//
// Dart decides what goes into an export (this file, unit-tested); the
// native half (ProjectExportBridge.kt, channel `oc/project_export`) only
// streams the planned files into a zip at a place the person chose through
// the Storage Access Framework, in the foreground, with progress.
//
// The rules follow the Termux migration exporter's: links and special files
// are never followed or copied, and sign-ins and other credentials leave
// only in an export the person opted into and that says it is private.
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// One file going into the zip: its name inside the zip and where it is.
class ProjectExportEntry {
  const ProjectExportEntry({
    required this.name,
    required this.source,
    required this.bytes,
    this.private = false,
  });

  final String name;
  final String source;
  final int bytes;

  /// A credential or sign-in: included only in a private export.
  final bool private;
}

/// A project folder (or a loose file) under the projects folder.
class ProjectFacts {
  const ProjectFacts({
    required this.name,
    required this.bytes,
    required this.files,
    required this.privateFiles,
  });

  final String name;

  /// Every regular file's logical size, private ones included.
  final int bytes;
  final int files;

  /// Credential files a plain export leaves out.
  final int privateFiles;
}

/// What "Clear storage" would delete, measured from the files.
class AppStorageFacts {
  const AppStorageFacts({
    required this.projects,
    required this.serverInstalled,
    required this.serverBytes,
    required this.privateDataBytes,
    this.savedServers = 0,
  });

  static const empty = AppStorageFacts(
    projects: [],
    serverInstalled: false,
    serverBytes: 0,
    privateDataBytes: 0,
  );

  final List<ProjectFacts> projects;

  /// The in-app Ubuntu is unpacked (`linux/ubuntu.ready`).
  final bool serverInstalled;

  /// Ubuntu and OpenCode, projects not counted.
  final int serverBytes;

  /// OpenCode's sign-ins, settings and conversations inside Ubuntu: what a
  /// private export adds besides the projects' credential files.
  final int privateDataBytes;
  final int savedServers;

  int get projectBytes => projects.fold(0, (sum, p) => sum + p.bytes);
  int get privateFiles => projects.fold(0, (sum, p) => sum + p.privateFiles);

  AppStorageFacts withSavedServers(int count) => AppStorageFacts(
    projects: projects,
    serverInstalled: serverInstalled,
    serverBytes: serverBytes,
    privateDataBytes: privateDataBytes,
    savedServers: count,
  );
}

/// Which files are credentials. Deliberately broad: a false positive only
/// moves a file into the private export.
abstract final class ProjectExportPolicy {
  /// File names that hold sign-ins or keys wherever they are.
  static const privateNames = {
    'auth.json',
    '.git-credentials',
    '.netrc',
    '_netrc',
    '.npmrc',
    '.pypirc',
    '.pgpass',
    '.dockercfg',
    'credentials',
    'credentials.json',
    'id_rsa',
    'id_dsa',
    'id_ecdsa',
    'id_ed25519',
  };

  /// Folders whose whole content is private.
  static const privateFolders = {'.ssh', '.gnupg', '.aws', '.docker'};

  static const privateExtensions = {
    '.pem',
    '.key',
    '.p12',
    '.pfx',
    '.jks',
    '.keystore',
  };

  /// `.env` files that are templates, not secrets.
  static const _envTemplates = {
    '.env.example',
    '.env.sample',
    '.env.template',
    '.env.dist',
  };

  /// A remote URL with a password or token in it (`https://u:t@host`).
  static final _urlCredential = RegExp(r'://[^/\s:@]+:[^/\s@]+@');

  /// Whether [relative] (a `/` path inside a project) is private. [readText]
  /// reads a small file's text, for `.git/config`.
  static bool isPrivate(String relative, {String? Function()? readText}) {
    final parts = relative.split('/');
    final name = parts.last;
    if (parts.any(privateFolders.contains)) return true;
    if (privateNames.contains(name)) return true;
    final lower = name.toLowerCase();
    if (lower == '.env' ||
        (lower.startsWith('.env.') && !_envTemplates.contains(lower))) {
      return true;
    }
    final dot = lower.lastIndexOf('.');
    if (dot > 0 && privateExtensions.contains(lower.substring(dot))) {
      return true;
    }
    if (relative == '.git/config' || relative.endsWith('/.git/config')) {
      final text = readText?.call();
      if (text != null && _urlCredential.hasMatch(text)) return true;
    }
    return false;
  }

  /// OpenCode's data inside Ubuntu (relative to its /root), exported only
  /// privately: sign-ins, settings and conversations.
  static const privateDataRoots = [
    '.local/share/opencode',
    '.config/opencode',
    '.oc-opencode2/config',
    '.oc-opencode2/data',
  ];

  /// Regenerable parts of those roots that are left out even then.
  static const privateDataSkipped = {'bin', 'log'};
}

/// The places under the app's files folder.
class AppStorageLayout {
  const AppStorageLayout(this.filesDir);

  final String filesDir;

  String get projects => '$filesDir/projects';
  String get linux => '$filesDir/linux';
  String get rootfs => '$filesDir/linux/ubuntu';
  String get root => '$rootfs/root';

  /// Projects before the native migration moved them out of Ubuntu.
  String get legacyProjects => '$root/projects';
  String get ready => '$filesDir/linux/ubuntu.ready';
}

/// Walks the files without following links and without reading contents
/// (except a `.git/config`'s few bytes). Synchronous on purpose: run it in
/// an isolate ([ProjectStorageScanner.scanInBackground]).
abstract final class ProjectStorageScanner {
  static AppStorageFacts scan(String filesDir) {
    final layout = AppStorageLayout(filesDir);
    final projects = <ProjectFacts>[];
    for (final top in _projectTops(layout)) {
      var bytes = 0, files = 0, privateFiles = 0;
      _walk(top.path, top.name, gitLinksUnder: filesDir, (
        relative,
        path,
        size,
      ) {
        bytes += size;
        files++;
        final inProject = relative.contains('/')
            ? relative.substring(relative.indexOf('/') + 1)
            : relative;
        if (ProjectExportPolicy.isPrivate(
          inProject,
          readText: () => _smallText(path),
        )) {
          privateFiles++;
        }
      });
      projects.add(
        ProjectFacts(
          name: top.name,
          bytes: bytes,
          files: files,
          privateFiles: privateFiles,
        ),
      );
    }
    projects.sort(
      (a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
    );
    final installed = _kind(layout.ready) == FileSystemEntityType.file;
    var linuxBytes = 0;
    _walk(layout.linux, 'linux', (_, _, size) => linuxBytes += size);
    var legacyBytes = 0;
    _walk(layout.legacyProjects, 'p', (_, _, size) => legacyBytes += size);
    var privateBytes = 0;
    for (final entry in _privateData(layout)) {
      privateBytes += entry.bytes;
    }
    return AppStorageFacts(
      projects: projects,
      serverInstalled: installed,
      serverBytes: (linuxBytes - legacyBytes).clamp(0, linuxBytes),
      privateDataBytes: privateBytes,
    );
  }

  static Future<AppStorageFacts> scanInBackground(String filesDir) =>
      Isolate.run(() => scan(filesDir));

  /// Every file an export writes. A plain export leaves out every private
  /// file; [includePrivate] adds them and OpenCode's own data.
  static List<ProjectExportEntry> plan(
    String filesDir, {
    bool includePrivate = false,
  }) {
    final layout = AppStorageLayout(filesDir);
    final entries = <ProjectExportEntry>[];
    for (final top in _projectTops(layout)) {
      _walk(top.path, 'projects/${top.name}', gitLinksUnder: filesDir, (
        name,
        path,
        size,
      ) {
        final inProject = name.substring('projects/${top.name}'.length);
        final relative = inProject.startsWith('/')
            ? inProject.substring(1)
            : top.name;
        final private = ProjectExportPolicy.isPrivate(
          relative,
          readText: () => _smallText(path),
        );
        if (private && !includePrivate) return;
        entries.add(
          ProjectExportEntry(
            name: name,
            source: path,
            bytes: size,
            private: private,
          ),
        );
      });
    }
    if (includePrivate) entries.addAll(_privateData(layout));
    return entries;
  }

  static Future<List<ProjectExportEntry>> planInBackground(
    String filesDir, {
    bool includePrivate = false,
  }) => Isolate.run(() => plan(filesDir, includePrivate: includePrivate));

  static List<ProjectExportEntry> _privateData(AppStorageLayout layout) {
    final entries = <ProjectExportEntry>[];
    for (final root in ProjectExportPolicy.privateDataRoots) {
      _walk(
        '${layout.root}/$root',
        'opencode/$root',
        (name, path, size) => entries.add(
          ProjectExportEntry(
            name: name,
            source: path,
            bytes: size,
            private: true,
          ),
        ),
        skip: ProjectExportPolicy.privateDataSkipped,
      );
    }
    return entries;
  }

  static Iterable<({String name, String path})> _projectTops(
    AppStorageLayout layout,
  ) sync* {
    final seen = <String>{};
    for (final folder in [layout.projects, layout.legacyProjects]) {
      if (_kind(folder) != FileSystemEntityType.directory) continue;
      final List<FileSystemEntity> children;
      try {
        children = Directory(folder).listSync(followLinks: false);
      } on FileSystemException {
        continue;
      }
      for (final child in children) {
        final name = child.path.substring(child.path.lastIndexOf('/') + 1);
        final kind = _kind(child.path);
        if (kind != FileSystemEntityType.directory &&
            kind != FileSystemEntityType.file) {
          continue;
        }
        if (!seen.add(name)) continue;
        yield (name: name, path: child.path);
      }
    }
  }

  /// Calls [visit] for every regular file under [path] (or [path] itself),
  /// named `prefix/…`. Links and special files are skipped, unreadable
  /// folders too (they are counted as nothing, never followed).
  ///
  /// With [gitLinksUnder] (a project), proot's link2symlink links are the
  /// one exception. Ubuntu's proot turns each hard link (every Git object
  /// Git ever stored, for one) into a link to a `.l2s.` file holding the
  /// bytes. Skipping those left an exported repository without its objects
  /// (`bad object HEAD` once unzipped). Such a link goes in as a regular file
  /// under its own name with the bytes it leads to, when that is a `.l2s.`
  /// file inside [gitLinksUnder]; the `.l2s.` files themselves stay out.
  static void _walk(
    String path,
    String prefix,
    void Function(String name, String path, int bytes) visit, {
    Set<String> skip = const {},
    String? gitLinksUnder,
  }) {
    final kind = _kind(path);
    if (kind == FileSystemEntityType.file) {
      visit(prefix, path, _size(path));
      return;
    }
    if (kind == FileSystemEntityType.link && gitLinksUnder != null) {
      final bytes = linkedObjectFile(path, gitLinksUnder);
      if (bytes != null) visit(prefix, bytes, _size(bytes));
      return;
    }
    if (kind != FileSystemEntityType.directory) return;
    final List<FileSystemEntity> children;
    try {
      children = Directory(path).listSync(followLinks: false);
    } on FileSystemException {
      return;
    }
    for (final child in children) {
      final name = child.path.substring(child.path.lastIndexOf('/') + 1);
      if (skip.contains(name)) continue;
      if (gitLinksUnder != null && name.startsWith(l2sPrefix)) continue;
      _walk(child.path, '$prefix/$name', visit, gitLinksUnder: gitLinksUnder);
    }
  }

  /// The name prefix proot's link2symlink gives the files behind its links.
  static const l2sPrefix = '.l2s.';

  /// The regular `.l2s.` file a link2symlink link at [link] leads to, inside
  /// [root]; null for any other link, a dangling one or one leading out.
  /// A hop whose absolute target is gone (the folder moved since) is looked
  /// for under the same name next to the link, where proot made it.
  static String? linkedObjectFile(String link, String root) {
    final String top;
    try {
      top = Directory(root).resolveSymbolicLinksSync();
    } on FileSystemException {
      return null;
    }
    var current = link;
    for (var hop = 0; hop < 8; hop++) {
      final String target;
      try {
        target = Link(current).targetSync();
      } on FileSystemException {
        return null;
      }
      final name = target.substring(target.lastIndexOf('/') + 1);
      if (!name.startsWith(l2sPrefix)) return null;
      final folder = current.substring(0, current.lastIndexOf('/'));
      var next = target.startsWith('/') ? target : '$folder/$target';
      if (_kind(next) == FileSystemEntityType.notFound) next = '$folder/$name';
      switch (_kind(next)) {
        case FileSystemEntityType.link:
          current = next;
        case FileSystemEntityType.file:
          try {
            final real = File(next).resolveSymbolicLinksSync();
            return real.startsWith('$top/') ? real : null;
          } on FileSystemException {
            return null;
          }
        default:
          return null;
      }
    }
    return null;
  }

  static FileSystemEntityType _kind(String path) =>
      FileSystemEntity.typeSync(path, followLinks: false);

  static int _size(String path) {
    try {
      return File(path).lengthSync();
    } on FileSystemException {
      return 0;
    }
  }

  static String? _smallText(String path) {
    try {
      final file = File(path);
      if (file.lengthSync() > 64 * 1024) return null;
      return file.readAsStringSync();
    } catch (_) {
      return null;
    }
  }
}

/// Why an export did not finish, in the words the page uses.
enum ProjectExportFailure {
  /// The chosen place said no or went away (card removed, Drive offline).
  destination,

  /// The chosen place is full.
  space,

  /// A project file could not be read.
  source,

  /// The person stopped it.
  cancelled,

  /// Anything else (the native half is missing, the app was closing).
  failed,
}

class ProjectExportResult {
  const ProjectExportResult.done({required this.bytes, required this.files})
    : failure = null,
      detail = '';

  const ProjectExportResult.failed(this.failure, [this.detail = ''])
    : bytes = 0,
      files = 0;

  final ProjectExportFailure? failure;
  final int bytes;
  final int files;

  /// Technical text for Details only; never shown as copy.
  final String detail;

  bool get ok => failure == null;
}

/// The native half. Everything it does happens while the page is open.
abstract class ProjectExportPlatform {
  /// The system "Save to" picker; null when the person backed out.
  Future<String?> pickDestination(String suggestedName);

  /// Streams [planPath]'s files into the zip at [destination]. On any
  /// failure the partial file is deleted.
  Future<ProjectExportResult> export({
    required String destination,
    required String planPath,
    required void Function(int bytesDone) onProgress,
  });

  Future<void> cancel();

  /// Deletes the app's cache only; returns the bytes freed.
  Future<int> clearCache();

  /// Measures the cache.
  Future<int> cacheBytes();

  /// ActivityManager.clearApplicationUserData: the app ends.
  Future<bool> clearAllData();

  /// Where the app's files are (Android `filesDir`).
  Future<String?> filesDir();

  /// How many servers are saved (read from settings, no secrets).
  Future<int> savedServers();
}

class MethodChannelProjectExport implements ProjectExportPlatform {
  MethodChannelProjectExport({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel('oc/project_export') {
    _channel.setMethodCallHandler(_onCall);
  }

  final MethodChannel _channel;
  void Function(int)? _progress;

  Future<dynamic> _onCall(MethodCall call) async {
    if (call.method == 'progress') {
      final bytes = call.arguments;
      if (bytes is int) _progress?.call(bytes);
    }
  }

  @override
  Future<String?> pickDestination(String suggestedName) =>
      _channel.invokeMethod<String>('pickDestination', {'name': suggestedName});

  @override
  Future<ProjectExportResult> export({
    required String destination,
    required String planPath,
    required void Function(int bytesDone) onProgress,
  }) async {
    _progress = onProgress;
    try {
      final result = await _channel.invokeMapMethod<String, Object?>('export', {
        'uri': destination,
        'plan': planPath,
      });
      if (result == null) {
        return const ProjectExportResult.failed(ProjectExportFailure.failed);
      }
      if (result['ok'] == true) {
        return ProjectExportResult.done(
          bytes: (result['bytes'] as num?)?.toInt() ?? 0,
          files: (result['files'] as num?)?.toInt() ?? 0,
        );
      }
      final reason = ProjectExportFailure.values
          .where((f) => f.name == result['reason'])
          .firstOrNull;
      return ProjectExportResult.failed(
        reason ?? ProjectExportFailure.failed,
        '${result['detail'] ?? ''}',
      );
    } on PlatformException catch (e) {
      return ProjectExportResult.failed(ProjectExportFailure.failed, e.code);
    } on MissingPluginException {
      return const ProjectExportResult.failed(ProjectExportFailure.failed);
    } finally {
      _progress = null;
    }
  }

  @override
  Future<void> cancel() => _channel.invokeMethod<void>('cancel');

  @override
  Future<int> clearCache() async =>
      await _channel.invokeMethod<int>('clearCache') ?? 0;

  @override
  Future<int> cacheBytes() async =>
      await _channel.invokeMethod<int>('cacheBytes') ?? 0;

  @override
  Future<bool> clearAllData() async =>
      await _channel.invokeMethod<bool>('clearAllData') ?? false;

  @override
  Future<String?> filesDir() async {
    if (!Platform.isAndroid) return null;
    try {
      return (await getApplicationSupportDirectory()).path;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<int> savedServers() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString('oc.profiles');
      if (raw == null) return 0;
      final list = jsonDecode(raw);
      return list is List ? list.length : 0;
    } catch (_) {
      return 0;
    }
  }
}

/// Writes the plan the native half reads: one `name\tsource` per line. It
/// sits in the app's cache and is deleted after the export.
Future<File> writeExportPlan(
  Directory dir,
  List<ProjectExportEntry> entries,
) async {
  final file = File(
    '${dir.path}/project-export-${DateTime.now().microsecondsSinceEpoch}.plan',
  );
  final sink = file.openWrite();
  for (final e in entries) {
    // Names and paths with a tab or newline cannot be framed: left out
    // rather than mis-framed.
    if (e.name.contains(RegExp('[\t\n\r]')) ||
        e.source.contains(RegExp('[\t\n\r]'))) {
      continue;
    }
    sink.write('${e.name}\t${e.source}\n');
  }
  await sink.close();
  return file;
}

/// "opencode-projects-2026-09-28.zip" (or "-private" when it holds
/// sign-ins, so the file itself says so).
String suggestedExportName(DateTime now, {required bool private}) {
  String two(int n) => n.toString().padLeft(2, '0');
  final day = '${now.year}-${two(now.month)}-${two(now.day)}';
  return 'opencode-projects${private ? '-private' : ''}-$day.zip';
}
