import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/workspace_paths.dart';

void main() {
  group('isProtectedWorkspaceDirectory', () {
    test('rejects roots, home folders and the server default', () {
      for (final path in [
        null,
        '',
        '   ',
        '/',
        '~',
        '/root',
        '/root/',
        '/home',
        '/home/eslam',
        '/home/eslam/',
        '/Users/eslam',
        '/data/data/com.termux/files/home',
        r'C:\',
        'C:/',
        r'C:\Users\eslam',
      ]) {
        expect(isProtectedWorkspaceDirectory(path), isTrue, reason: '$path');
      }
    });

    test('accepts project folders, including ones inside a home', () {
      for (final path in [
        '/root/projects',
        '/root/projects/app',
        '/home/eslam/Storage/Code/oc_app',
        '/Users/eslam/work/acme',
        '/srv/app',
        '/data/data/com.termux/files/home/projects',
        r'C:\Users\eslam\code',
        '/home/eslam/My Project',
      ]) {
        expect(isProtectedWorkspaceDirectory(path), isFalse, reason: path);
      }
    });
  });

  group('workspaceDirectoryProblem', () {
    test('explains empty, relative and protected paths', () {
      expect(workspaceDirectoryProblem(''), contains('full path'));
      expect(workspaceDirectoryProblem('projects/app'), contains('absolute'));
      expect(workspaceDirectoryProblem('/root'), contains('home folder'));
      expect(workspaceDirectoryProblem('/'), contains('filesystem root'));
      expect(workspaceDirectoryProblem('/tmp/a\nb'), contains('characters'));
    });

    test('accepts real folders', () {
      expect(workspaceDirectoryProblem('/root/projects/app'), isNull);
      expect(workspaceDirectoryProblem('/home/eslam/My Project/'), isNull);
      expect(workspaceDirectoryProblem(r'D:\work\acme'), isNull);
    });
  });

  group('projectFolderNameProblem', () {
    test('accepts one safe segment', () {
      expect(projectFolderNameProblem('my-app'), isNull);
      expect(projectFolderNameProblem('App_2.0'), isNull);
      expect(projectFolderNameProblem(' app '), isNull);
    });

    test('rejects paths, hidden names and odd characters', () {
      expect(projectFolderNameProblem(''), contains('Enter'));
      expect(projectFolderNameProblem('..'), contains('single'));
      expect(projectFolderNameProblem('a/b'), contains('single'));
      expect(projectFolderNameProblem('.git'), contains('letters'));
      expect(projectFolderNameProblem('my app'), contains('letters'));
      expect(projectFolderNameProblem('x' * 65), contains('letters'));
    });
  });
}
