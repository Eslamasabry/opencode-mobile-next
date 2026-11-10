import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/team_directories.dart';

// Folders seen on the Android 15 emulator on 2026-09-24, after the in-app
// AI Team ran one task in the project my-app.
const _polecat =
    '/root/aiteam/city/.gc/worktrees/my-app/polecats/'
    'gastown.furiosa';
const _refinery = '/root/aiteam/city/.gc/worktrees/my-app/refinery';

void main() {
  group('isAiTeamDirectory', () {
    test('the in-app team home and everything in it is the team\'s', () {
      for (final path in [
        _polecat,
        _refinery,
        '/root/aiteam',
        '/root/aiteam/',
        BuiltinTeam.cityDir,
        '${BuiltinTeam.originsDir}/my-app.git',
        '/root/./aiteam/city',
        '/root/projects/../aiteam/city',
      ]) {
        expect(isAiTeamDirectory(path), isTrue, reason: path);
      }
    });

    test('a Gas City work folder is the team\'s wherever the city lives', () {
      for (final path in [
        // Termux-native team (hybrid layout): ~/.oc/aiteam/city.
        '/data/data/com.termux/files/home/.oc/aiteam/city/.gc/worktrees/'
            'my-app/polecats/gastown.furiosa',
        '/data/data/com.termux/files/home/.oc/aiteam/city',
        // A city on a PC.
        '/home/eslam/gascity/city2/.gc/worktrees/oc-bg-proof/refinery',
      ]) {
        expect(isAiTeamDirectory(path), isTrue, reason: path);
      }
    });

    test('the person\'s own folders are not, the team\'s rig included', () {
      for (final path in [
        '/root/projects/my-app',
        '/root/projects/my-app/lib',
        '/root/projects/aiteam-spike',
        '/root/projects',
        '/root/aiteam-notes',
        '/root/aiteamwork/app',
        '/home/eslam/Storage/Code/oc_app',
        '/root/projects/gc-notes',
        null,
        '',
        '   ',
      ]) {
        expect(isAiTeamDirectory(path), isFalse, reason: '$path');
      }
    });

    test('the built-in team home and the rule are one constant', () {
      expect(BuiltinTeam.home, aiTeamHome);
      expect(isAiTeamDirectory(BuiltinTeam.home), isTrue);
    });
  });

  group('isAiTeamConversation', () {
    GlobalSessionResult result(String? directory, String? project) =>
        GlobalSessionResult(
          session: Session(id: 's', title: 't', directory: directory),
          projectDirectory: project,
        );

    test('judged by the conversation\'s own folder', () {
      expect(
        isAiTeamConversation(result(_polecat, '/root/projects/my-app')),
        isTrue,
      );
      // A person's conversation in the rig stays theirs even if the server
      // reports the project under a team folder.
      expect(
        isAiTeamConversation(result('/root/projects/my-app', _refinery)),
        isFalse,
      );
    });

    test('the project folder decides only when the conversation has none', () {
      expect(isAiTeamConversation(result(null, _refinery)), isTrue);
      expect(
        isAiTeamConversation(result(null, '/root/projects/my-app')),
        isFalse,
      );
    });
  });
}
