import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/file_share_gateway.dart';
import 'package:opencode_mobile/platform/wifi_file_share.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  WifiFileShare make({bool wifi = true}) => WifiFileShare(
    findWifiAddress: () async => wifi ? InternetAddress.loopbackIPv4 : null,
    privateRoots: () async => const [],
  );

  test('no Wi-Fi refuses to start', () async {
    final share = make(wifi: false);
    expect(
      () => share.start(FileShareSource.bytes('a.txt', Uint8List(1))),
      throwsA(
        isA<FileShareException>().having(
          (e) => e.failure,
          'failure',
          FileShareFailure.noWifi,
        ),
      ),
    );
  });

  test(
    'serves the one file by token, counts downloads, 404s the rest',
    () async {
      final share = make();
      final state = await share.start(
        FileShareSource.bytes(
          'hello world.txt',
          Uint8List.fromList(utf8.encode('hi')),
        ),
      );
      final url = Uri.parse(state.url);
      final client = HttpClient();
      addTearDown(() async {
        client.close(force: true);
        await share.stop();
      });

      final ok = await (await client.getUrl(url)).close();
      expect(ok.statusCode, 200);
      expect(await utf8.decodeStream(ok), 'hi');
      expect(ok.headers.value('content-disposition'), contains('attachment'));
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(share.value?.downloads, 1);

      final other = url.replace(path: '/wrong/hello%20world.txt');
      expect((await (await client.getUrl(other)).close()).statusCode, 404);
      final root = url.replace(path: '/');
      expect((await (await client.getUrl(root)).close()).statusCode, 404);
      expect((await (await client.postUrl(url)).close()).statusCode, 405);
    },
  );

  test('stop closes the server and clears the state', () async {
    final share = make();
    final state = await share.start(
      FileShareSource.bytes('a.txt', Uint8List(3)),
    );
    await share.stop();
    expect(share.value, isNull);
    final client = HttpClient();
    await expectLater(
      client.getUrl(Uri.parse(state.url)).then((r) => r.close()),
      throwsA(isA<SocketException>()),
    );
    client.close(force: true);
  });

  test('only private LAN addresses count as Wi-Fi', () {
    expect(WifiFileShare.isPrivateLan(InternetAddress('192.168.1.5')), isTrue);
    expect(WifiFileShare.isPrivateLan(InternetAddress('10.0.0.2')), isTrue);
    expect(WifiFileShare.isPrivateLan(InternetAddress('8.8.8.8')), isFalse);
    expect(WifiFileShare.isPrivateLan(InternetAddress('0.0.0.0')), isFalse);
  });

  group('what may be shared by path', () {
    late Directory root;
    setUp(() => root = Directory.systemTemp.createTempSync('share-guard'));
    tearDown(() => root.deleteSync(recursive: true));

    WifiFileShare guarded() => WifiFileShare(
      findWifiAddress: () async => InternetAddress.loopbackIPv4,
      privateRoots: () async => [root.path],
      allowedRoots: () async => ['${root.path}/linux/ubuntu'],
    );

    File put(String relative, [String body = 'x']) =>
        File('${root.path}/$relative')
          ..createSync(recursive: true)
          ..writeAsStringSync(body);

    Future<FileShareFailure?> failureOf(WifiFileShare s, String path) async {
      try {
        await s.fileSource(path);
        return null;
      } on FileShareException catch (e) {
        return e.failure;
      }
    }

    test('a plain file in the built-in Linux streams', () async {
      final f = put('linux/ubuntu/root/projects/app/out.txt', 'hello');
      final source = await guarded().fileSource(f.path);
      expect(source.name, 'out.txt');
      expect(source.length, 5);
    });

    test('app storage and key files are refused', () async {
      final s = guarded();
      for (final p in [
        'shared_prefs/a.xml',
        'databases/x.db',
        'app_flutter/y.txt',
        'linux/ubuntu/root/.claude/notes.txt',
        'linux/ubuntu/root/.ssh/config',
        'linux/ubuntu/root/.config/opencode/auth.json',
        'linux/ubuntu/root/projects/app/.env.local',
        'linux/ubuntu/root/projects/app/server.pem',
        'linux/ubuntu/root/projects/app/id_rsa.pub',
        'linux/ubuntu/root/projects/app/gcp-credentials.json',
        'linux/ubuntu/root/.oc-builtin/server.password',
        'linux/ubuntu/home/oc/.oc-profiles/me/claude/settings.json',
        'linux/ubuntu/root/projects/app/api_token.txt',
      ]) {
        expect(
          await failureOf(s, put(p).path),
          FileShareFailure.privateFile,
          reason: p,
        );
      }
    });

    test('a link leading out of the allowed area is refused', () async {
      final secret = put('shared_prefs/secret.txt');
      final dir = Directory('${root.path}/linux/ubuntu/root')
        ..createSync(recursive: true);
      final link = Link('${dir.path}/innocent.txt')..createSync(secret.path);
      expect(
        await failureOf(guarded(), link.path),
        FileShareFailure.privateFile,
      );
    });
  });
}
