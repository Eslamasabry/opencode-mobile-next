import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/update/app_release.dart';
import 'package:opencode_mobile/update/app_update_controller.dart';
import 'package:opencode_mobile/update/app_update_platform.dart';
import 'package:opencode_mobile/update/app_update_source.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/app_update_fakes.dart';

void main() {
  late Directory dir;
  late FakeUpdateSource source;
  late FakeUpdatePlatform platform;
  late DateTime clock;

  setUp(() async {
    dir = await Directory.systemTemp.createTemp('app_update_test');
    source = FakeUpdateSource()
      ..latest = AppRelease.fromJson(updateReleaseJson());
    platform = FakeUpdatePlatform();
    clock = DateTime(2026, 10, 31, 12);
    SharedPreferences.setMockInitialValues({});
  });
  tearDown(() => dir.delete(recursive: true));

  AppUpdateController make({int installedBuild = 57, bool supported = true}) =>
      AppUpdateController(
        source: source,
        platform: platform,
        supported: supported,
        prefs: SharedPreferences.getInstance,
        installed: () async => InstalledApp(
          packageName: 'io.github.eslamasabry.opencode_mobile',
          version: '2.1.0',
          build: installedBuild,
        ),
        cacheDir: () async => dir,
        now: () => clock,
      );

  group('finding a release', () {
    test('a newer build is available, an equal or older one is not', () async {
      final c = make();
      await c.start();
      expect(c.phase, AppUpdatePhase.available);
      expect(c.release!.version, '2.2.0');
      expect(c.hasPendingRelease, isTrue);

      final same = make(installedBuild: 60);
      await same.start();
      expect(same.phase, AppUpdatePhase.none);
      expect(same.hasPendingRelease, isFalse);
    });

    test('a finding survives a restart without asking again', () async {
      final first = make();
      await first.start();
      source.fetches = 0;
      final again = make();
      await again.start();
      expect(again.phase, AppUpdatePhase.available);
      expect(source.fetches, 0, reason: 'checked less than 6 hours ago');
    });

    test('once the new build is installed the finding is dropped', () async {
      await make().start();
      final updated = make(installedBuild: 60);
      await updated.start();
      expect(updated.hasPendingRelease, isFalse);
    });

    test('checks happen at most once every 6 hours', () async {
      final c = make();
      await c.start();
      expect(source.fetches, 1);
      await c.checkIfDue();
      clock = clock.add(const Duration(hours: 5, minutes: 59));
      await c.checkIfDue();
      expect(source.fetches, 1);
      clock = clock.add(const Duration(minutes: 2));
      await c.checkIfDue();
      expect(source.fetches, 2);
    });

    test('Check now asks at once; the switch off stops the clock', () async {
      final c = make();
      await c.start();
      await c.checkNow();
      expect(source.fetches, 2);
      await c.setEnabled(false);
      clock = clock.add(const Duration(days: 1));
      await c.checkIfDue();
      expect(source.fetches, 2);
    });

    test(
      'a failed check is retried after 30 minutes, not on every resume',
      () async {
        source.fetchError = const SocketException('offline');
        final c = make();
        await c.start();
        expect(c.phase, AppUpdatePhase.none);
        await c.checkIfDue();
        expect(source.fetches, 1);
        clock = clock.add(const Duration(minutes: 31));
        await c.checkIfDue();
        expect(source.fetches, 2);
      },
    );

    test('an unsupported platform never asks', () async {
      final c = make(supported: false);
      await c.start();
      await c.checkNow();
      expect(source.fetches, 0);
    });
  });

  group('the offer', () {
    test(
      'Not now hides the notice for that version but keeps the badge',
      () async {
        final c = make();
        await c.start();
        expect(c.noticeVisible, isTrue);
        c.notNow();
        expect(c.noticeVisible, isFalse);
        expect(c.hasPendingRelease, isTrue);
        // The next app start offers it again.
        final next = make();
        await next.start();
        expect(next.noticeVisible, isTrue);
      },
    );

    test('a newer release brings the offer back after Not now', () async {
      final c = make();
      await c.start();
      c.notNow();
      source.latest = AppRelease.fromJson(updateReleaseJson(build: 61));
      await c.checkNow();
      expect(c.noticeVisible, isTrue);
    });

    test('the release wins over a ready patch', () async {
      final c = make();
      c.patchReady();
      expect(c.phase, AppUpdatePhase.restartToUpdate);
      await c.start();
      expect(c.phase, AppUpdatePhase.available);
      expect(c.noticeVisible, isTrue);
      expect(c.hasPendingRelease, isTrue);
    });

    test('a patch alone says restart, with no gear badge', () async {
      source.latest = null;
      final c = make();
      await c.start();
      c.patchReady();
      expect(c.phase, AppUpdatePhase.restartToUpdate);
      expect(c.hasPendingRelease, isFalse);
    });
  });

  group('installing', () {
    test(
      'downloads the file for this phone, checks it, and installs it',
      () async {
        final c = make();
        await c.start();
        await c.updateNow();
        expect(source.downloadedUrl, contains('arm64-v8a.apk'));
        expect(platform.installs, hasLength(1));
        expect(platform.installs.single, endsWith('arm64-v8a.apk'));
        expect(c.phase, AppUpdatePhase.installing);
      },
    );

    test(
      'a phone with only armeabi-v7a gets the v7a file; others the universal one',
      () async {
        platform.abis = ['armeabi-v7a'];
        final c = make();
        await c.start();
        await c.updateNow();
        expect(source.downloadedUrl, contains('armeabi-v7a.apk'));
      },
    );

    test('a checksum mismatch never installs and deletes the file', () async {
      source.sumsHash = 'a' * 64;
      final c = make();
      await c.start();
      await c.updateNow();
      expect(platform.installs, isEmpty);
      expect(c.phase, AppUpdatePhase.failed);
      expect(c.failure, AppUpdateFailure.checksum);
      final left = Directory(
        '${dir.path}/app-update',
      ).listSync().where((e) => e.path.endsWith('.apk'));
      expect(left, isEmpty);
    });

    test('a signer mismatch never installs', () async {
      platform.signer = 'BB22';
      final c = make();
      await c.start();
      await c.updateNow();
      expect(platform.installs, isEmpty);
      expect(c.failure, AppUpdateFailure.signer);
      expect(c.detail, contains('BB22'));
    });

    test(
      'another package, or a version that is not newer, never installs',
      () async {
        platform.package = 'com.example.other';
        final c = make();
        await c.start();
        await c.updateNow();
        expect(platform.installs, isEmpty);
        expect(c.failure, AppUpdateFailure.package);

        platform.package = 'io.github.eslamasabry.opencode_mobile';
        platform.versionCode = 57;
        await c.updateNow();
        expect(platform.installs, isEmpty);
        expect(c.failure, AppUpdateFailure.version);
      },
    );

    test(
      'without permission to install it asks once and continues on return',
      () async {
        platform.canInstallNow = false;
        final c = make();
        await c.start();
        await c.updateNow();
        expect(platform.permissionOpened, 1);
        expect(platform.installs, isEmpty);
        expect(c.phase, AppUpdatePhase.readyToInstall);
        expect(c.needsPermission, isTrue);

        platform.canInstallNow = true;
        await c.onResumed();
        expect(platform.installs, hasLength(1));
        expect(source.downloads, 1, reason: 'the checked file is reused');
      },
    );

    test('cancelling a download goes back to available', () async {
      source.hold = Completer<void>();
      final c = make();
      await c.start();
      final run = c.updateNow();
      while (c.progress == null) {
        await Future<void>.delayed(const Duration(milliseconds: 5));
      }
      expect(c.phase, AppUpdatePhase.downloading);
      expect(c.progress, 0.5);
      c.cancel();
      await run;
      expect(c.phase, AppUpdatePhase.available);
      expect(platform.installs, isEmpty);
    });

    test(
      'Android confirm dialog cancelled returns to ready; Try again reuses the file',
      () async {
        platform.events = [
          const AppInstallEvent(AppInstallState.pendingUser),
          const AppInstallEvent(AppInstallState.aborted),
        ];
        final c = make();
        await c.start();
        await c.updateNow();
        expect(c.phase, AppUpdatePhase.readyToInstall);
        expect(c.needsPermission, isFalse);
        await c.updateNow();
        expect(platform.installs, hasLength(2));
        expect(source.downloads, 1);
      },
    );

    test(
      'an installer failure is a failure with Android words in the detail',
      () async {
        platform.events = [
          const AppInstallEvent(AppInstallState.failure, 'INSTALL_FAILED_X'),
        ];
        final c = make();
        await c.start();
        await c.updateNow();
        expect(c.failure, AppUpdateFailure.installer);
        expect(c.detail, 'INSTALL_FAILED_X');
      },
    );
  });

  group('release data', () {
    test('the override is empty in every build the scripts make', () {
      expect(appUpdateFeedOverride, isEmpty);
      for (final root in ['scripts', 'tool', '.github']) {
        for (final f in Directory(
          root,
        ).listSync(recursive: true).whereType<File>()) {
          if (f.path.endsWith('.png') || f.path.endsWith('.jar')) continue;
          String text;
          try {
            text = f.readAsStringSync();
          } on FileSystemException {
            continue;
          }
          expect(text, isNot(contains('OC_APP_UPDATE_FEED')), reason: f.path);
        }
      }
    });

    test('only GitHub release downloads of this project are trusted', () {
      const ok =
          'https://github.com/Eslamasabry/opencode-mobile-next/releases/download/v1/a.apk';
      expect(isTrustedReleaseFileUrl(ok), isTrue);
      expect(
        isTrustedReleaseFileUrl(ok.replaceFirst('https', 'http')),
        isFalse,
      );
      expect(
        isTrustedReleaseFileUrl('https://evil.example/releases/a.apk'),
        isFalse,
      );
      expect(
        isTrustedReleaseFileUrl(
          'https://github.com/other/repo/releases/download/v1/a.apk',
        ),
        isFalse,
      );
      expect(
        isTrustedReleaseFileUrl(
          'http://10.0.2.2:8099/a.apk',
          feedOverride: 'http://10.0.2.2:8099/latest.json',
        ),
        isTrue,
      );
    });

    test('parses sums, picks files by ABI and reads the first sentence', () {
      final r = AppRelease.fromJson(updateReleaseJson())!;
      expect(r.build, 60);
      expect(r.apkFor(['arm64-v8a'])!.name, endsWith('arm64-v8a.apk'));
      expect(r.apkFor(['x86_64'])!.name, 'opencode-mobile-2.2.0+60.apk');
      expect(r.summary, 'This release is faster.');
      expect(
        AppRelease.fromJson({...updateReleaseJson(), 'draft': true}),
        isNull,
      );
      expect(parseSha256Sums('${'A' * 64}  *x.apk\nnonsense'), {
        'x.apk': 'a' * 64,
      });
    });
  });

  test(
    'a download resumes from the partial file with a Range request',
    () async {
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
      addTearDown(() => server.close(force: true));
      final full = List<int>.generate(1000, (i) => i % 251);
      String? range;
      server.listen((req) async {
        range = req.headers.value('range');
        final from = range == null
            ? 0
            : int.parse(range!.split('=')[1].split('-')[0]);
        req.response.statusCode = range == null ? 200 : 206;
        req.response.headers.contentLength = full.length - from;
        req.response.add(full.sublist(from));
        await req.response.close();
      });
      final feed = 'http://127.0.0.1:${server.port}/latest.json';
      final src = DioAppUpdateSource(feedOverride: feed);
      final dest = File('${dir.path}/x/a.apk');
      await dest.parent.create(recursive: true);
      await File('${dest.path}.part').writeAsBytes(full.sublist(0, 400));
      final seen = <int>[];
      await src.download(
        'http://127.0.0.1:${server.port}/a.apk',
        dest,
        onProgress: (r, t) => seen.add(r),
        cancel: DownloadCancel(),
      );
      expect(range, 'bytes=400-');
      expect(await dest.readAsBytes(), full);
      expect(seen.last, 1000);
    },
  );
}
