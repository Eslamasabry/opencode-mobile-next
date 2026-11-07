// The in-app repair of a project whose Git objects are proot link2symlink
// links Git can't follow (owner's phone, 2026-10-18: 973 links into
// Termux's Ubuntu, `bad object HEAD`). The app no longer asks Termux for
// anything: links into its private storage count as missing. Real
// repositories, real links and the real git on this machine.
import 'dart:io';
import 'dart:isolate';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/git_link_repair.dart';

const _store =
    '/data/data/com.termux/files/usr/var/lib/proot-distro/containers/'
    'opencode-ubuntu/rootfs/.l2s';

void main() {
  late Directory root;
  late Directory projects;
  late Directory termuxSide;

  ProcessResult git(String repo, List<String> args) => Process.runSync(
    'git',
    ['-C', repo, ...args],
    environment: const {
      'GIT_AUTHOR_NAME': 't',
      'GIT_AUTHOR_EMAIL': 't@t',
      'GIT_COMMITTER_NAME': 't',
      'GIT_COMMITTER_EMAIL': 't@t',
    },
  );

  /// A repository with two commits, all loose objects.
  String repository(String name) {
    final repo = '${projects.path}/$name';
    Directory(repo).createSync(recursive: true);
    git(repo, ['init', '-q']);
    File('$repo/a.txt').writeAsStringSync('one\n');
    git(repo, ['add', '.']);
    git(repo, ['commit', '-qm', 'one']);
    File('$repo/b.txt').writeAsStringSync('two\n');
    git(repo, ['add', '.']);
    git(repo, ['commit', '-qm', 'two']);
    return repo;
  }

  List<File> looseObjects(String repo) =>
      Directory('$repo/.git/objects')
          .listSync(recursive: true)
          .whereType<File>()
          .where(
            (f) =>
                RegExp(r'/objects/[0-9a-f]{2}/[0-9a-f]{38}$').hasMatch(f.path),
          )
          .toList()
        ..sort((a, b) => a.path.compareTo(b.path));

  /// What the app's own proot leaves after the folder moved: each object a
  /// link to `.l2s.` names under the folder's old path.
  void linkLikeAMovedFolder(String repo, String oldRepo) {
    for (final (i, object) in looseObjects(repo).indexed) {
      final folder = object.parent.path;
      final oldFolder = folder.replaceFirst(repo, oldRepo);
      final stored = '$folder/.l2s.tmp_obj_$i.0001.0001';
      object.renameSync(stored);
      Link(
        '$folder/.l2s.tmp_obj_$i.0001',
      ).createSync('$oldFolder/.l2s.tmp_obj_$i.0001.0001');
      Link(object.path).createSync('$oldFolder/.l2s.tmp_obj_$i.0001');
    }
  }

  /// What a project brought from Termux's Ubuntu holds: each object a link
  /// into Termux's private `.l2s` store. The bytes stay on "Termux's side".
  Map<String, String> linkIntoTermux(String repo, {int? only}) {
    final bytes = <String, String>{};
    for (final (i, object) in looseObjects(repo).indexed) {
      if (only != null && i >= only) break;
      final kept = '${termuxSide.path}/$i';
      object.copySync(kept);
      object.deleteSync();
      final target = '$_store/.l2s.tmp_obj_X$i.0001';
      Link(object.path).createSync(target);
      bytes[target] = kept;
    }
    return bytes;
  }

  GitLinkRepair repairWith() => GitLinkRepair(
    projects: projects,
    check: (ubuntu) async {
      final host =
          '${projects.path}${ubuntu.substring('/root/projects'.length)}';
      final fsck = git(host, ['fsck', '--connectivity-only', '--no-progress']);
      final status = git(host, ['status', '--porcelain']);
      return GitCheck(
        ok: fsck.exitCode == 0 && status.exitCode == 0,
        output: '${fsck.stderr}${status.stderr}',
      );
    },
  );

  bool readable(String repo) =>
      git(repo, ['status', '--porcelain']).exitCode == 0;

  int links(String repo) => Directory('$repo/.git/objects')
      .listSync(recursive: true, followLinks: false)
      .whereType<Link>()
      .where((l) => !l.path.split('/').last.startsWith('.l2s.'))
      .length;

  setUp(() {
    root = Directory.systemTemp.createTempSync('oc-git-repair-');
    projects = Directory('${root.path}/projects')..createSync();
    termuxSide = Directory('${root.path}/termux')..createSync();
  });

  tearDown(() => root.deleteSync(recursive: true));

  test('a project whose folder moved is repaired from its own files', () async {
    final repo = repository('app');
    linkLikeAMovedFolder(repo, '${projects.path}/app-before');
    expect(readable(repo), isFalse); // `bad object HEAD`

    final stages = <GitRepairStage>{};
    final outcome = await repairWith().repair(
      '/root/projects/app',
      progress: (p) => stages.add(p.stage),
    );

    expect(outcome.result, GitRepairResult.repaired);
    expect(outcome.restored, 6); // 2 commits, 2 trees, 2 blobs
    expect(outcome.missing, isEmpty);
    expect(readable(repo), isTrue);
    expect(links(repo), 0); // Moving it again can't break it.
    expect(
      stages,
      containsAll([GitRepairStage.looking, GitRepairStage.checking]),
    );
  });

  test('a progress callback that can\'t cross isolates is fine', () async {
    // The sheet's callback holds its State; on the emulator the walk's
    // isolate refused it (ArgumentError) and every repair failed.
    final repo = repository('app');
    linkLikeAMovedFolder(repo, '${projects.path}/app-before');
    final port = ReceivePort();
    addTearDown(port.close);
    final outcome = await repairWith().repair(
      '/root/projects/app',
      progress: (p) => port.sendPort.send(p.stage.name),
    );
    expect(outcome.result, GitRepairResult.repaired);
  });

  test('objects whose bytes are in Termux are named as missing, and the '
      'app asks Termux for nothing', () async {
    final repo = repository('app');
    linkIntoTermux(repo);
    final outcome = await repairWith().repair('/root/projects/app');
    expect(outcome.result, GitRepairResult.cannotRepair);
    expect(outcome.missing, hasLength(6));
    expect(outcome.restored, 0);
    expect(links(repo), 6);
  });

  test('a healthy project needs nothing', () async {
    repository('app');
    final outcome = await repairWith().repair('/root/projects/app');
    expect(outcome.result, GitRepairResult.healthy);
  });

  test('only the projects folder is reached', () async {
    final repair = repairWith();
    for (final path in [
      '/root',
      '/root/projects',
      '/etc/x',
      '/root/projects/../x',
    ]) {
      expect(
        (await repair.repair(path)).result,
        GitRepairResult.notRepository,
        reason: path,
      );
    }
  });

  test('pack files are checked by their trailing checksum', () async {
    final body = List<int>.generate(100, (i) => i);
    final good = File('${root.path}/good.pack')
      ..writeAsBytesSync([...body, ...sha1.convert(body).bytes]);
    final bad = File('${root.path}/bad.pack')
      ..writeAsBytesSync([...body, ...List<int>.filled(20, 0)]);
    const name =
        'objects/pack/pack-0123456789012345678901234567890123456789.pack';
    expect(await GitObjectLinks.verify(name, good), isTrue);
    expect(await GitObjectLinks.verify(name, bad), isFalse);
    expect(await GitObjectLinks.verify('config', good), isFalse);
  });
}
