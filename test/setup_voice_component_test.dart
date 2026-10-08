// Voice typing as a phone setup component (feat/voice-in-setup, 2026-09-27):
// the app-side component kind in the setup engine, and the voice model
// component that fills it from the voice settings' own manager and
// downloader. Fakes only: no network, no files, no native job runner.

import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/component_removal.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/voice_component.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/download_size.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';

import 'setup_engine_test.dart' show FakeLinux, en, pumpUntil, spec;
import 'support/voice_setup_fakes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('the voice model component', () {
    late FakeVoiceDownloader downloader;

    setUp(() => downloader = FakeVoiceDownloader());

    Future<(VoiceSetupComponent, VoiceModelManager)> component({
      int? totalMemoryMb = 8000,
      int? memoryClassMb = 256,
      int? availableStorageBytes = 20 * 1000 * 1000 * 1000,
      bool captureSupported = true,
      Map<String, Object> preferences = const {},
    }) async {
      final manager = await fakeVoiceManager(
        downloader,
        device: voicePhone(
          totalMemoryMb: totalMemoryMb,
          memoryClassMb: memoryClassMb,
          availableStorageBytes: availableStorageBytes,
          captureSupported: captureSupported,
        ),
        preferences: preferences,
      );
      addTearDown(manager.dispose);
      return (VoiceSetupComponent(manager: () async => manager), manager);
    }

    test('offers the pack first-mic setup would use: High accuracy on a phone '
        'with room, at its real size, not installed', () async {
      final (voice, _) = await component();
      final offer = await voice.offer();
      expect(offer, isNotNull);
      expect(offer!.installed, isFalse);
      expect(offer.downloadBytes, voiceModelPack('small').downloadBytes);
      expect(offer.downloadSize.kind, DownloadSizeKind.exact);
      expect(offer.downloadSize.bytes, voiceModelPack('small').downloadBytes);
      expect(voice.lastOfferBytes, voiceModelPack('small').downloadBytes);
      expect((await voice.check()).ok, isFalse);
    });

    test('gates on total RAM, never the Java heap class', () async {
      // 8 GB phone whose per-app heap class is 256 MB: still offered.
      final (roomy, _) = await component(memoryClassMb: 128);
      expect(await roomy.offer(), isNotNull);

      // 1.2 GB: Balanced does not fit, Compact does.
      final (small, _) = await component(totalMemoryMb: 1200);
      expect(
        (await small.offer())!.downloadBytes,
        voiceModelPack('tiny').downloadBytes,
      );

      // Below every pack: not offered at all.
      final (tooSmall, _) = await component(totalMemoryMb: 800);
      expect(await tooSmall.offer(), isNull);
    });

    test(
      'capture and known physical RAM are required for an automatic offer',
      () async {
        final (noCapture, _) = await component(captureSupported: false);
        expect(await noCapture.offer(), isNull);
        final (unknown, _) = await component(totalMemoryMb: null);
        expect(await unknown.offer(), isNull);
      },
    );

    test('low free space still offers it: setup\'s pre-flight says how '
        'much to free', () async {
      final (voice, _) = await component(availableStorageBytes: 1000);
      expect(await voice.offer(), isNotNull);
    });

    test('an uninstalled preference cannot override the RAM choice', () async {
      final (voice, _) = await component(
        preferences: const {'voice.selected_pack': 'tiny'},
      );
      final offer = await voice.offer();
      expect(offer!.downloadBytes, voiceModelPack('small').downloadBytes);
      expect(offer.downloadSize.kind, DownloadSizeKind.exact);
      expect(offer.downloadSize.bytes, voiceModelPack('small').downloadBytes);
    });

    test('retains a supported installed selection', () async {
      downloader.installed.add('tiny');
      final (voice, manager) = await component(
        preferences: const {'voice.selected_pack': 'tiny'},
      );
      expect(VoiceSetupComponent.packFor(manager)?.id, 'tiny');
      expect((await voice.offer())!.installed, isTrue);
      await voice.install(onProgress: (_) {});
      expect(manager.selectedPack.id, 'tiny');
      expect(downloader.starts, isEmpty);
    });

    test('an installed selection above RAM is not reported ready', () async {
      downloader.installed.add('small');
      final (voice, manager) = await component(
        totalMemoryMb: 1536,
        preferences: const {'voice.selected_pack': 'small'},
      );
      expect(VoiceSetupComponent.packFor(manager)?.id, 'base');
      expect((await voice.offer())!.installed, isFalse);
      expect((await voice.check()).ok, isFalse);
      await voice.install(onProgress: (_) {});
      expect(manager.selectedPack.id, 'base');
      expect((await voice.check()).ok, isTrue);
    });

    test('install downloads and verifies through the voice manager, reports '
        'bytes then the check, and leaves it in use', () async {
      final (voice, manager) = await component();
      final progress = <SetupAppProgress>[];
      await voice.install(onProgress: progress.add);

      expect(downloader.installed, {'small'});
      expect(downloader.replaceFlags, [false]);
      expect(progress.first.stage, SetupAppStage.downloading);
      expect(progress.first.bytesTotal, voiceModelPack('small').downloadBytes);
      expect(progress.map((p) => p.bytesDone ?? 0), contains(greaterThan(0)));
      expect(progress.map((p) => p.stage), contains(SetupAppStage.verifying));
      expect(manager.selectedPack.id, 'small');
      expect(manager.isReady, isTrue);
      expect((await voice.check()).ok, isTrue);
      expect((await voice.offer())!.installed, isTrue);
    });

    test(
      'install is idempotent: an installed model downloads nothing',
      () async {
        downloader.installed.add('base');
        final (voice, _) = await component();
        expect((await voice.check()).ok, isTrue);
        final offer = await voice.offer();
        expect(offer!.downloadSize.kind, DownloadSizeKind.exact);
        expect(offer.downloadSize.bytes, 0);
        await voice.install(onProgress: (_) {});
        expect(downloader.starts, isEmpty);
      },
    );

    test('a stopped install keeps its partial download and the next one '
        'resumes it', () async {
      final (voice, _) = await component();
      downloader.gate = Completer<void>();
      final first = voice.install(onProgress: (_) {});
      await pumpUntil(() => downloader.partial.isNotEmpty);
      voice.cancel();
      await expectLater(
        first,
        throwsA(
          isA<SetupAppFailure>().having(
            (f) => f.kind,
            'kind',
            SetupAppFailureKind.cancelled,
          ),
        ),
      );
      expect(downloader.installed, isEmpty);
      final kept = downloader.partial['small'];
      expect(kept, greaterThan(0));

      // The app comes back (or Continue): the same pack, from where it was.
      downloader.gate = null;
      await voice.install(onProgress: (_) {});
      expect(downloader.starts, [0, kept]);
      expect(downloader.replaceFlags, [false, false]);
      expect((await voice.check()).ok, isTrue);
    });

    test('failures come back as kinds, never exception text', () async {
      final (voice, _) = await component();
      downloader.failWith = const VoiceDownloadException(
        'Model server timed out at https://example/secret',
        failure: VoiceDownloadFailure.timeout,
      );
      await expectLater(
        voice.install(onProgress: (_) {}),
        throwsA(
          isA<SetupAppFailure>().having(
            (f) => f.kind,
            'kind',
            SetupAppFailureKind.offline,
          ),
        ),
      );
      expect(
        VoiceSetupComponent.failureOf(
          const VoiceDownloadException(
            'bad',
            failure: VoiceDownloadFailure.checksum,
          ),
        ),
        SetupAppFailureKind.checksum,
      );
    });

    test('too little space fails the install as no space', () async {
      final (voice, _) = await component(availableStorageBytes: 1000);
      await expectLater(
        voice.install(onProgress: (_) {}),
        throwsA(
          isA<SetupAppFailure>().having(
            (f) => f.kind,
            'kind',
            SetupAppFailureKind.noSpace,
          ),
        ),
      );
    });

    test('remove deletes every pack, partial downloads included, and '
        'confirms it is gone', () async {
      downloader.installed.add('base');
      downloader.partial['tiny'] = 10;
      final (voice, manager) = await component();
      await voice.remove();
      expect(downloader.deleted, containsAll(['base', 'small', 'tiny']));
      expect(downloader.partial, isEmpty);
      expect(manager.isInstalled(voiceModelPack('base')), isFalse);
      expect((await voice.check()).ok, isFalse);
      expect((await voice.offer())!.installed, isFalse);
    });

    test('remove refuses while a download runs', () async {
      final (voice, _) = await component();
      downloader.gate = Completer<void>();
      final install = voice.install(onProgress: (_) {});
      await pumpUntil(() => downloader.partial.isNotEmpty);
      await expectLater(
        voice.remove(),
        throwsA(
          isA<SetupAppFailure>().having(
            (f) => f.kind,
            'kind',
            SetupAppFailureKind.busy,
          ),
        ),
      );
      downloader.gate!.complete();
      await install;
    });

    test('a manager that cannot start (no platform) is simply not '
        'offered and not installed', () async {
      final voice = VoiceSetupComponent(
        manager: () async => throw StateError('no path_provider'),
      );
      expect(await voice.offer(), isNull);
      expect((await voice.check()).ok, isFalse);
    });

    test('the registry declares it optional, off by default, app-side, '
        'with nothing to run inside Linux, after the start', () {
      final registry = setupComponents(en);
      final voice = registry.singleWhere((c) => c.id == 'voice');
      expect(voice.title, 'Voice typing');
      expect(voice.summary, 'Speak instead of typing, even offline');
      expect(voice.required, isFalse);
      expect(voice.defaultOn, isFalse);
      expect(voice.dependsOn, isEmpty);
      expect(voice.app, isA<VoiceSetupComponent>());
      expect(voice.checkScript, isEmpty);
      expect(voice.installScript, isEmpty);
      expect(voice.downloadBytes, greaterThan(0));
      final ids = registry.map((c) => c.id).toList();
      expect(ids.indexOf('voice'), greaterThan(ids.indexOf('start')));
    });
  });

  group('app-side components in the engine', () {
    late FakeLinux linux;
    late FakeSetupApp app;
    late ChannelSetupEngine engine;

    List<SetupComponent> withVoice(
      AppLocalizations l10n,
      Map<String, Map<String, String>> params,
    ) => [
      ...setupComponents(l10n, params: params).where((c) => c.id != 'voice'),
      voiceSetupItem(app),
    ];

    ChannelSetupEngine makeEngine() => ChannelSetupEngine(
      linux: linux,
      strings: () => en,
      components: withVoice,
      pollInterval: const Duration(milliseconds: 5),
      finisher: (_) async => null,
    );

    setUp(() {
      linux = FakeLinux();
      linux.checks = {
        for (final id in ['linux', 'essentials', 'node', 'opencode'])
          id: (true, '1'),
      };
      app = FakeSetupApp();
      engine = makeEngine();
    });

    tearDown(() => engine.dispose());

    List<String> jobIds() => [
      for (final c in linux.started.last['components']! as List) c['id'],
    ];

    test('left out, it changes nothing: not checked, not in the job', () async {
      await engine.run(const {});
      expect(jobIds(), isNot(contains('voice')));
      expect(app.checks, 0);
      expect(app.installs, 0);
    });

    test(
      'chosen, it is checked by the app (not in Linux) and runs after '
      'the start with storage admission and a bounded download wait',
      () async {
        await engine.run({'voice'});
        expect(jobIds().last, 'voice');
        expect(jobIds().indexOf('start'), jobIds().length - 2);
        expect(app.checks, 1);
        expect(linux.runs.single, isNot(contains('voice')));
        final step = spec(linux, 'voice');
        expect(step['step'], isTrue);
        expect(step['script'], isNull);
        expect(step['data'], {
          'requiredFreeBytes': '320000000',
          'waitMinutes': '$appStepWaitMinutes',
        });
      },
    );

    test('already installed, it is skipped', () async {
      app.installed = true;
      await engine.run({'voice'});
      expect(spec(linux, 'voice')['skipped'], isTrue);
    });

    test('it is checked even before Linux is there', () async {
      linux.installed = false;
      app.installed = true;
      await engine.run({'voice'});
      expect(linux.runs, isEmpty);
      expect(spec(linux, 'voice')['skipped'], isTrue);
    });

    test('when the job reaches it, the app installs it once, its bytes show '
        'on the row, and the job hears it is done', () async {
      app.gate = Completer<void>();
      await engine.run({'voice'});
      linux.advance('voice', {'stage': en.setupAppStageDownloading});
      void listener() {}
      engine.progress.addListener(listener);
      addTearDown(() => engine.progress.removeListener(listener));
      await pumpUntil(() => app.installs == 1);
      await pumpUntil(
        () => engine.progress.value.components.last.bytesDone == 80,
      );
      final row = engine.progress.value.components.last;
      expect(row.id, 'voice');
      expect(row.bytesTotal, 160);
      expect(row.stage, en.setupAppStageDownloading);
      // More polls while it downloads never start a second install.
      await Future<void>.delayed(const Duration(milliseconds: 40));
      expect(app.installs, 1);

      app.gate!.complete();
      await pumpUntil(() => linux.steps.isNotEmpty);
      expect(linux.steps.single, containsPair('id', 'voice'));
      expect(linux.steps.single, containsPair('ok', true));
      await pumpUntil(() => engine.progress.value.state == SetupState.done);
      expect(app.installed, isTrue);
    });

    test('a failed download fails its row in plain words; Continue tries '
        'again', () async {
      app.failWith = SetupAppFailureKind.offline;
      await engine.run({'voice'});
      linux.advance('voice', {});
      void listener() {}
      engine.progress.addListener(listener);
      addTearDown(() => engine.progress.removeListener(listener));
      await pumpUntil(() => engine.progress.value.state == SetupState.failed);
      expect(linux.steps.single, containsPair('ok', false));
      final row = engine.progress.value.components.last;
      expect(row.error, en.phoneSetupErrorOffline('Voice typing'));
      expect(engine.progress.value.error, en.phoneSetupErrorNoInternet);
      expect(engine.progress.value.canContinue, isTrue);

      app.failWith = null;
      await engine.resume();
      expect(linux.started, hasLength(2));
      expect(jobIds(), contains('voice'));
      expect(spec(linux, 'voice')['step'], isTrue);
    });

    test('cancel stops the download and reports nothing', () async {
      app.gate = Completer<void>();
      await engine.run({'voice'});
      linux.advance('voice', {});
      void listener() {}
      engine.progress.addListener(listener);
      addTearDown(() => engine.progress.removeListener(listener));
      await pumpUntil(() => app.installs == 1);
      await engine.cancel();
      expect(app.cancels, greaterThan(0));
      await Future<void>.delayed(const Duration(milliseconds: 30));
      expect(linux.steps, isEmpty);
      expect(app.installed, isFalse);
    });

    test('the app killed mid-download: a new engine finds the job waiting '
        'on it and starts the install again (the download resumes)', () async {
      app.gate = Completer<void>();
      await engine.run({'voice'});
      linux.advance('voice', {});
      await engine.restore();
      await pumpUntil(() => app.installs == 1);
      engine.dispose();

      // A new process: the Termux runner kept waiting on the step.
      app.gate = null;
      final restarted = makeEngine();
      addTearDown(restarted.dispose);
      await restarted.restore();
      await pumpUntil(() => linux.steps.isNotEmpty);
      expect(app.installs, 2);
      expect(linux.steps.single, containsPair('ok', true));
    });

    test('an interrupted job continues through it (built-in runner)', () async {
      await engine.run({'voice'});
      linux.job!['state'] = 'interrupted';
      ((linux.job!['components'] as Map)['voice'] as Map)['state'] = 'pending';
      await engine.resume();
      expect(linux.started, hasLength(2));
      expect(jobIds(), contains('voice'));
      expect(spec(linux, 'voice')['step'], isTrue);
    });

    test('installedOptional counts it even when Linux cannot answer', () async {
      app.installed = true;
      expect(await engine.installedOptional(), contains('voice'));
      linux.installed = false;
      expect(await engine.installedOptional(), {'voice'});
    });

    test(
      'the removal service removes it through the app, not a script',
      () async {
        app.installed = true;
        final service = ComponentRemovalService(
          linux: linux,
          registry: withVoice(en, const {}),
          team: BuiltinTeam(linux: linux),
        );
        final entry = (await service.inventory()).singleWhere(
          (e) => e.component.id == 'voice',
        );
        expect(entry.presence, ComponentPresence.installed);
        expect(entry.canRemove, isTrue);
        await service.remove('voice');
        expect(app.removes, 1);
        expect(app.installed, isFalse);
        expect(linux.runs.where((s) => s.contains('voice')), isEmpty);
      },
    );
  });

  test('app-side failure reasons map to the setup failure words', () {
    String words(SetupAppFailureKind kind) => describeSetupFailure(
      SetupJobComponent(
        id: 'voice',
        state: 'failed',
        error: appFailureReason(kind),
      ),
      '',
      'Voice typing',
      en,
    ).component;
    expect(
      words(SetupAppFailureKind.offline),
      en.phoneSetupErrorOffline('Voice typing'),
    );
    expect(
      words(SetupAppFailureKind.noSpace),
      en.phoneSetupErrorNoSpace('Voice typing'),
    );
    expect(
      words(SetupAppFailureKind.checksum),
      en.phoneSetupErrorChecksum('Voice typing'),
    );
    expect(
      words(SetupAppFailureKind.failed),
      en.phoneSetupErrorInstall('Voice typing'),
    );
  });
}
