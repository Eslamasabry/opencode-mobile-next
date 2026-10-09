import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_search_publish.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_status.dart';

void main() {
  late Directory support;
  late BuiltinGenUiSearchPublisher publisher;
  setUp(() async {
    support = await Directory.systemTemp.createTemp('search-publish-');
    publisher = BuiltinGenUiSearchPublisher(
      supportDirectory: () async => support,
    );
  });
  tearDown(() => support.delete(recursive: true));
  Future<Directory> managed(String relative) async {
    final directory = await Directory(
      '${support.path}/linux/ubuntu/$relative',
    ).create(recursive: true);
    await Process.run('chmod', ['700', directory.path]);
    return directory;
  }

  Future<bool> publish({
    String profile = 'profile',
    GenUiAgent? agent,
    Uri? endpoint,
    String? bearer,
  }) => publisher.publish(
    profileId: profile,
    agent: agent ?? GenUiAgent.claude,
    endpoint: endpoint ?? Uri.parse('http://127.0.0.1:4321/find-connectors'),
    bearer: bearer ?? 'a' * 64,
  );

  test(
    'publishes private descriptor only in existing app-owned rootfs',
    () async {
      final directory = await managed('home/oc/.oc-profiles/profile/.oc-genui');
      expect(await publish(), isTrue);
      final file = File('${directory.path}/enabled.search.json');
      expect(jsonDecode(await file.readAsString()), {
        'endpoint': 'http://127.0.0.1:4321/find-connectors',
        'bearer': 'a' * 64,
      });
      expect((await file.stat()).mode & 0x1ff, 0x180);
      expect(await publish(bearer: 'b' * 64), isTrue);
      expect(
        (jsonDecode(await file.readAsString()) as Map)['bearer'],
        'b' * 64,
      );
      expect(await directory.list().length, 1);
      final root = await managed('root/.oc-genui/openCode1');
      expect(await publish(agent: GenUiAgent.openCode1), isTrue);
      expect(await File('${root.path}/enabled.search.json').exists(), isTrue);
    },
  );
  test(
    'refuses invalid input, missing rootfs, symlinks and public directory',
    () async {
      expect(await publish(), isFalse);
      final directory = await managed('home/oc/.oc-profiles/profile/.oc-genui');
      for (final url in [
        'https://example.com/find-connectors',
        'http://127.0.0.1:4321/other',
        'http://user@127.0.0.1:4321/find-connectors',
      ]) {
        expect(await publish(endpoint: Uri.parse(url)), isFalse);
      }
      expect(await publish(profile: '../escape'), isFalse);
      expect(await publish(bearer: 'invalid'), isFalse);
      final outside = File('${support.path}/outside');
      await outside.writeAsString('unchanged');
      final link = Link('${directory.path}/enabled.search.json');
      await link.create(outside.path);
      expect(await publish(), isFalse);
      expect(await outside.readAsString(), 'unchanged');
      await link.delete();
      await Process.run('chmod', ['777', directory.path]);
      expect(await publish(), isFalse);
    },
  );
}
