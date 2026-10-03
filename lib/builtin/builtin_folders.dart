import 'dart:io';

import 'package:path_provider/path_provider.dart';

import 'builtin_linux.dart';
import 'builtin_server.dart' show BuiltinProjectFolders;

/// One folder in a listing: its name, its full path inside Ubuntu, and
/// whether it is a git repository (it has a `.git` of its own).
class FolderEntry {
  const FolderEntry({
    required this.name,
    required this.path,
    this.isGit = false,
    this.inside,
    this.more = 0,
  });

  final String name;
  final String path;
  final bool isGit;

  /// A few of the names inside (visible ones), when the lister looked: the
  /// browser's one-line hint of what is in the folder. Empty means the
  /// folder is empty; null means nobody looked.
  final List<String>? inside;

  /// How many more names there are beyond [inside].
  final int more;
}

/// Why a folder could not be listed.
enum FolderListProblem {
  /// Ubuntu is not unpacked on this phone (or its files cannot be found).
  notInstalled,

  /// The folder is not there (any more).
  missing,

  /// The app may not read it.
  denied,

  /// It is a link; its target is only right inside Ubuntu, so the app does
  /// not follow it from outside.
  linked,

  /// The path is not a plain absolute path.
  invalid,

  /// No answer in time (Termux, where each listing is a round trip).
  timedOut,

  /// Anything else the filesystem said.
  failed,
}

class FolderListException implements Exception {
  const FolderListException(this.problem, [this.detail = '']);

  final FolderListProblem problem;

  /// The filesystem's own words, for Details.
  final String detail;

  @override
  String toString() => detail.isEmpty ? problem.name : detail;
}

/// Lists folders inside the app's own Ubuntu straight from its files on the
/// phone, without running anything in Ubuntu and without OpenCode: it works
/// while the server is stopped and answers in milliseconds.
///
/// Ubuntu's root is `<app files>/linux/ubuntu` (BuiltinLinux.kt: `home =
/// File(context.filesDir, "linux")`, `rootfs = File(home, "ubuntu")`, marked
/// installed by `linux/ubuntu.ready`); `path_provider`'s support directory
/// is that same `filesDir` on Android. Projects live at `<app files>/projects`
/// and proot binds them at `/root/projects`. Before native migration, the
/// legacy rootfs folder is used instead. Retained projects can be browsed even
/// after removing Ubuntu. `/dev`, `/proc` and `/sys` are runtime bindings and
/// are left out of the listing of `/`.
///
/// Links are never followed: an absolute link target means a path inside
/// Ubuntu, which read from outside would point somewhere else on the phone.
class BuiltinRootfsFolders {
  BuiltinRootfsFolders({
    Future<Directory?> Function()? locate,
    Future<Directory?> Function()? locateProjects,
  }) : _locate = locate ?? locateRootfs,
       _locateProjects =
           locateProjects ?? (locate == null ? locateProjectStorage : _absent);

  final Future<Directory?> Function() _locate;
  final Future<Directory?> Function() _locateProjects;

  static Future<Directory?> _absent() async => null;

  /// Folders bound from Android when Ubuntu runs: empty in the files.
  static const _boundAtRoot = {'dev', 'proc', 'sys'};

  /// Ubuntu's root folder on this phone, or null when it is not installed.
  static Future<Directory?> locateRootfs() async {
    if (!BuiltinLinux.supported) return null;
    final Directory support;
    try {
      support = await getApplicationSupportDirectory();
    } catch (_) {
      return null;
    }
    final home = '${support.path}/linux';
    if (!await File('$home/ubuntu.ready').exists()) return null;
    final rootfs = Directory('$home/ubuntu');
    return await rootfs.exists() ? rootfs : null;
  }

  /// Android's retained project storage, independently of Ubuntu installation.
  /// A supplied [BuiltinRootfsFolders] `locate` isolates custom filesystems;
  /// supply `locateProjects` too to model the external project binding.
  static Future<Directory?> locateProjectStorage() async {
    if (!BuiltinLinux.supported) return null;
    try {
      final support = await getApplicationSupportDirectory();
      return Directory('${support.path}/projects');
    } catch (_) {
      return null;
    }
  }

  /// [path] as a plain absolute Ubuntu path (`/root/projects`), or null
  /// when it is relative or climbs with `..`.
  static String? normalize(String path) {
    final value = path.trim();
    if (!value.startsWith('/') || value.contains('\x00')) return null;
    final segments = <String>[];
    for (final segment in value.split('/')) {
      if (segment.isEmpty || segment == '.') continue;
      if (segment == '..') return null;
      segments.add(segment);
    }
    return '/${segments.join('/')}';
  }

  /// The folder [path] holds, or null at `/`.
  static String? parentOf(String path) {
    final value = normalize(path);
    if (value == null || value == '/') return null;
    final cut = value.lastIndexOf('/');
    return cut <= 0 ? '/' : value.substring(0, cut);
  }

  /// The folders directly inside [path] (hidden ones left out), sorted by
  /// name. The projects folder that does not exist yet (a fresh install
  /// before OpenCode first started) lists as empty.
  Future<List<FolderEntry>> list(String path) async {
    final ubuntuPath = normalize(path);
    if (ubuntuPath == null) {
      throw FolderListException(FolderListProblem.invalid, path);
    }
    try {
      return await _list(ubuntuPath);
    } on FileSystemException catch (error) {
      throw FolderListException(_problemOf(error), error.toString());
    }
  }

  Future<List<FolderEntry>> _list(String ubuntuPath) async {
    // Resolve each time: native migration/removal can happen while this
    // browser remains alive. Never retain a stale path into the old rootfs.
    final projects = await _locateProjects();
    final external = projects == null
        ? null
        : await _hostPath(projects.path, '/');
    final rootfs = await _locate();
    const projectsPath = BuiltinLinux.projectsDir;
    final inProjects =
        ubuntuPath == projectsPath || ubuntuPath.startsWith('$projectsPath/');
    final virtualParent =
        external != null && (ubuntuPath == '/' || ubuntuPath == '/root');
    if (rootfs == null && !(external != null && inProjects) && !virtualParent) {
      throw const FolderListException(FolderListProblem.notInstalled);
    }
    final String? host;
    if (external != null && inProjects) {
      host = await _hostPath(
        external,
        ubuntuPath.substring(projectsPath.length),
      );
    } else {
      host = rootfs == null ? null : await _hostPath(rootfs.path, ubuntuPath);
    }
    if (host == null && !virtualParent) {
      if (ubuntuPath == projectsPath) return const [];
      throw FolderListException(FolderListProblem.missing, ubuntuPath);
    }
    final entries = <FolderEntry>[];
    if (host != null) {
      await for (final entity in Directory(host).list(followLinks: false)) {
        if (entity is! Directory) continue; // Files and links are not shown.
        final name = entity.path.substring(entity.path.lastIndexOf('/') + 1);
        if (name.isEmpty || name.startsWith('.')) continue;
        if (ubuntuPath == '/' && _boundAtRoot.contains(name)) continue;
        if (external != null && ubuntuPath == '/root' && name == 'projects') {
          continue; // Show the binding, not the disposable mountpoint.
        }
        entries.add(
          FolderEntry(
            name: name,
            path: ubuntuPath == '/' ? '/$name' : '$ubuntuPath/$name',
            isGit: await _hasGit(entity.path),
          ),
        );
      }
    }
    if (external != null && ubuntuPath == '/root') {
      entries.add(
        FolderEntry(
          name: 'projects',
          path: projectsPath,
          isGit: await _hasGit(external),
        ),
      );
    } else if (virtualParent &&
        ubuntuPath == '/' &&
        !entries.any((entry) => entry.name == 'root')) {
      entries.add(const FolderEntry(name: 'root', path: '/root'));
    }
    entries.sort(
      (a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()),
    );
    return entries;
  }

  /// The phone path of [ubuntuPath], checking every folder on the way is a
  /// real folder (not a link). Null when a part of it is missing.
  static Future<String?> _hostPath(String rootfs, String ubuntuPath) async {
    var host = rootfs;
    final segments = ubuntuPath.split('/').where((s) => s.isNotEmpty);
    for (final segment in ['', ...segments]) {
      if (segment.isNotEmpty) host = '$host/$segment';
      final type = await FileSystemEntity.type(host, followLinks: false);
      if (type == FileSystemEntityType.notFound) return null;
      if (type == FileSystemEntityType.link) {
        throw FolderListException(FolderListProblem.linked, ubuntuPath);
      }
      if (type != FileSystemEntityType.directory) {
        throw FolderListException(FolderListProblem.missing, ubuntuPath);
      }
    }
    return host;
  }

  static Future<bool> _hasGit(String host) async =>
      await FileSystemEntity.type('$host/.git', followLinks: false) !=
      FileSystemEntityType.notFound;

  static FolderListProblem _problemOf(FileSystemException error) =>
      switch (error.osError?.errorCode) {
        2 => FolderListProblem.missing, // ENOENT
        1 || 13 => FolderListProblem.denied, // EPERM, EACCES
        _ => FolderListProblem.failed,
      };
}

/// The folders of OpenCode inside the app, for the folder browser: read
/// from Ubuntu's files ([BuiltinRootfsFolders]). Should the app not find
/// those files, Ubuntu itself is asked for the one folder it keeps a script
/// for, the projects folder ([BuiltinProjectFolders.list]); any other folder
/// then says it cannot be shown, and a path can still be entered.
class BuiltinFolders {
  BuiltinFolders(this.linux, {BuiltinRootfsFolders? rootfs})
    : rootfs = rootfs ?? BuiltinRootfsFolders();

  /// Only through Ubuntu: what the app does when it cannot find Ubuntu's
  /// files, and what a widget test uses (its fake Ubuntu has no files, and
  /// real file access does not run under a widget test's fake clock).
  BuiltinFolders.throughUbuntu(this.linux)
    : rootfs = BuiltinRootfsFolders(locate: () async => null);

  final BuiltinLinux linux;
  final BuiltinRootfsFolders rootfs;

  Future<List<FolderEntry>> list(String path) async {
    try {
      return await rootfs.list(path);
    } on FolderListException catch (error) {
      if (error.problem != FolderListProblem.notInstalled) rethrow;
    }
    if (BuiltinRootfsFolders.normalize(path) != BuiltinLinux.projectsDir) {
      throw FolderListException(
        FolderListProblem.failed,
        'Ubuntu’s files were not found; only ${BuiltinLinux.projectsDir} '
        'can be listed.',
      );
    }
    final List<String> names;
    try {
      names = await BuiltinProjectFolders(linux).list();
    } on BuiltinLinuxException catch (error) {
      throw FolderListException(FolderListProblem.failed, error.message);
    }
    return [
      for (final name in names)
        FolderEntry(name: name, path: BuiltinProjectFolders.pathFor(name)),
    ];
  }
}
