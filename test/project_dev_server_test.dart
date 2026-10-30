// ProjectDevServer: the Terminal header's dev-server signal. Only a saved
// service that runs (at its own saved port) or a program listening inside
// the project counts; a failed look keeps what was known.
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/dev_preview.dart';
import 'package:opencode_mobile/domain/managed_shell.dart';
import 'package:opencode_mobile/state/detected_dev_servers.dart';
import 'package:opencode_mobile/state/project_dev_server.dart';

// The phone's listing (ListeningPorts' script output): vite on 5173 run
// from /root/p, and a python server on 8080 run from /other.
const _phone = '''
#T4
   0: 0100007F:1435 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 5001 1 0000000000000000 100 0 0 10 0
   1: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000  1000        0 5002 1 0000000000000000 100 0 0 10 0
#S
lrwx------ 1 root root 64 Oct 16 10:00 /proc/41/fd/3 -> socket:[5001]
lrwx------ 1 root root 64 Oct 16 10:00 /proc/42/fd/3 -> socket:[5002]
#C
41\tnode vite --port 5173 \t/root/p\t/root/p/dev.log
42\tpython3 -m http.server 8080 \t/other\t/dev/pts/0
#P
''';

DetectedDevServers _listening(String directory) => DetectedDevServers(
  directory: directory,
  isCurrent: () => true,
  resolveGateway: () async => null as ManagedShellGateway?,
  runOnPhone: (_) async => (exitCode: 0, output: _phone),
);

void main() {
  test('nothing known: no line', () async {
    expect(
      await ProjectDevServer.lookFor(directory: '/root/projects/neon'),
      isNull,
    );
  });

  test(
    'a look that finds one says so once, and a failed look keeps it',
    () async {
      var answer = const DevPreviewLink(port: 5173) as DevPreviewLink?;
      var fail = false;
      final watch = ProjectDevServer(
        look: () async {
          if (fail) throw StateError('phone busy');
          return answer;
        },
      );
      addTearDown(watch.dispose);
      var notified = 0;
      watch.addListener(() => notified++);
      await watch.refresh();
      expect(watch.running?.port, 5173);
      await watch.refresh();
      expect(notified, 1, reason: 'the same server says nothing new');
      fail = true;
      await watch.refresh();
      expect(watch.running?.port, 5173);
      fail = false;
      answer = null;
      await watch.refresh();
      expect(watch.running, isNull);
      expect(notified, 2);
    },
  );

  test('a program listening inside the project is its dev server', () async {
    final phone = _listening('/root/p');
    addTearDown(phone.dispose);
    final link = await ProjectDevServer.lookFor(
      directory: '/root/p',
      listening: phone,
    );
    expect(link?.port, 5173);
  });

  test(
    'programs listening outside the project are not this project\'s',
    () async {
      final phone = _listening('/root/q');
      addTearDown(phone.dispose);
      expect(
        await ProjectDevServer.lookFor(directory: '/root/q', listening: phone),
        isNull,
      );
    },
  );
}
