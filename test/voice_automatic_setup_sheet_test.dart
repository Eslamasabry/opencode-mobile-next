// P10.4 Automatic voice setup: the first mic tap's sheet, end to end with
// the real setup controller, model manager and composer over fakes (no
// network, no microphone). It picks the pack by total RAM, says its size in
// plain words, asks before downloading (in so many words on mobile data),
// shows progress, and hands a started recording to the composer.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/voice/automatic_setup_platform.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/read_aloud.dart';
import 'package:opencode_mobile/voice/voice_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'revamp/screen_voice_1_fixtures.dart';
import 'revamp/voice_auto_setup_fixtures.dart';
import 'voice_controller_test.dart' show FakeVoiceRecognizer, FakeVoiceRecorder;

void main() {
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.android();
    addTearDown(() => debugPlatformCapabilities = null);
  });

  testWidgets('the first mic tap picks the pack by total RAM, says its size '
      'in plain words, downloads with progress and starts listening', (
    tester,
  ) async {
    // 8 GB of RAM behind a 128 MB Java heap class: the heap class is never
    // the measure, so the best pack is picked.
    final rig = await openAutoSetup(tester);
    final small = voiceModelPack('small');
    expect(
      find.text("High accuracy speech model, picked for this phone's memory"),
      findsOneWidget,
    );
    expect(
      find.text('Download ${plainMb(small.downloadBytes)}'),
      findsOneWidget,
    );
    // No model jargon or MiB above Details.
    expect(find.textContaining('MiB'), findsNothing);
    expect(find.textContaining('Whisper'), findsNothing);
    expect(find.textContaining('int8'), findsNothing);
    expect(rig.downloader.downloads, isEmpty, reason: 'asks first');

    rig.downloader.gate = Completer<void>();
    await autoSetupTap(
      tester,
      find.byKey(const Key('voice-auto-setup-download')),
    );
    expect(rig.downloader.downloads, ['small']);
    rig.downloader.progress!(
      VoiceDownloadProgress(
        received: 150000000,
        total: small.downloadBytes,
        fileName: small.encoder.name,
      ),
    );
    await pumpSheet(tester);
    expect(
      find.text('150 MB of ${plainMb(small.downloadBytes)}'),
      findsOneWidget,
    );
    expect(
      find.text(
        "Progress also shows in your notifications. Listening starts when it's done.",
      ),
      findsOneWidget,
    );
    expect(
      rig.platform.phases,
      contains(VoiceSetupNotificationPhase.downloading),
    );

    rig.downloader.gate!.complete();
    await pumpSheet(tester);
    await pumpSheet(tester);
    expect(rig.result, isTrue);
    expect(find.byKey(const Key('voice-auto-setup')), findsNothing);
    expect(rig.composer.state, VoiceComposerState.listening);
    expect(rig.recorder.startCalls, 1);
    expect(rig.platform.phases, contains(VoiceSetupNotificationPhase.complete));
    await stopAutoSetupRecording(tester, rig);
  });

  testWidgets('a low-RAM phone gets the compact pack whatever its heap class', (
    tester,
  ) async {
    await openAutoSetup(
      tester,
      info: autoSetupInfo(memory: 1200, memoryClass: 16384),
    );
    expect(
      find.text(
        "Compact fallback speech model, picked for this phone's memory",
      ),
      findsOneWidget,
    );
  });

  testWidgets('on mobile data it says so and the button names it', (
    tester,
  ) async {
    SharedPreferences.setMockInitialValues({});
    final rig = await openAutoSetup(tester, info: autoSetupInfo(memory: 2048));
    // The offer was read on Wi-Fi; switch before asking again.
    rig.platform.current = VoiceSetupNetwork.metered;
    await autoSetupTap(
      tester,
      find.byKey(const Key('voice-auto-setup-download')),
    );
    final size = plainMb(voiceModelPack('base').downloadBytes);
    expect(
      find.text(
        "You're on mobile data. This download counts against your data plan.",
      ),
      findsOneWidget,
    );
    expect(rig.downloader.downloads, isEmpty, reason: 'Wi-Fi consent only');
    rig.downloader.gate = Completer<void>();
    await autoSetupTap(tester, find.text('Download $size on mobile data'));
    expect(rig.downloader.downloads, ['base']);
    rig.downloader.gate!.complete();
    await pumpSheet(tester);
    await pumpSheet(tester);
    await stopAutoSetupRecording(tester, rig);
  });

  testWidgets(
    'on a network it cannot read it is honest and not "mobile data"',
    (tester) async {
      SharedPreferences.setMockInitialValues({});
      final rig = await openAutoSetup(
        tester,
        info: autoSetupInfo(memory: 2048),
      );
      rig.platform.current = VoiceSetupNetwork.unknown;
      await autoSetupTap(
        tester,
        find.byKey(const Key('voice-auto-setup-download')),
      );
      final size = plainMb(voiceModelPack('base').downloadBytes);
      expect(
        find.text("This phone can't tell whether this connection costs data."),
        findsOneWidget,
      );
      expect(find.textContaining('mobile data'), findsNothing);
      expect(find.text('Download $size'), findsOneWidget);
      expect(rig.downloader.downloads, isEmpty);
      rig.downloader.gate = Completer<void>();
      await autoSetupTap(tester, find.text('Download $size'));
      expect(rig.downloader.downloads, ['base']);
      rig.downloader.gate!.complete();
      await pumpSheet(tester);
      await pumpSheet(tester);
      await stopAutoSetupRecording(tester, rig);
    },
  );

  testWidgets('Not now downloads nothing and records nothing', (tester) async {
    final rig = await openAutoSetup(tester);
    await autoSetupTap(tester, find.text('Not now'));
    expect(rig.result, isFalse);
    expect(rig.downloader.downloads, isEmpty);
    expect(rig.recorder.startCalls, 0);
  });

  testWidgets('Cancel download stops it and closes', (tester) async {
    final rig = await openAutoSetup(tester);
    rig.downloader.gate = Completer<void>();
    await autoSetupTap(
      tester,
      find.byKey(const Key('voice-auto-setup-download')),
    );
    await autoSetupTap(tester, find.text('Cancel download'));
    expect(rig.downloader.cancellation!.isCancelled, isTrue);
    rig.downloader.gate!.complete();
    await pumpSheet(tester);
    expect(rig.result, isFalse);
    expect(rig.recorder.startCalls, 0);
  });

  testWidgets('offline: plain words and Try again', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final downloader = AutoSetupDownloader();
    final models = VoiceModelManager(
      root: '/synthetic-voice-models',
      preferences: await SharedPreferences.getInstance(),
      downloader: downloader,
      devicePlatform: AutoSetupDevice(autoSetupInfo()),
    );
    autoSetupPhone(tester);
    final platform = AutoSetupPlatform()..current = VoiceSetupNetwork.offline;
    final composer = VoiceComposerController(
      models: models,
      recorder: FakeVoiceRecorder(),
      recognizer: FakeVoiceRecognizer(),
    );
    addTearDown(() {
      composer.dispose();
      models.dispose();
    });
    await tester.pumpWidget(
      voiceHost(
        home: voiceLauncher(
          (context) => showVoiceAutomaticSetupSheet(
            context,
            composer,
            platform: platform,
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open'));
    await pumpSheet(tester);
    await pumpSheet(tester);
    expect(
      find.text('No internet connection. Connect, then try again.'),
      findsOneWidget,
    );
    platform.current = VoiceSetupNetwork.unmetered;
    await autoSetupTap(tester, find.text('Try again'));
    expect(find.byKey(const Key('voice-auto-setup-download')), findsOneWidget);
    expect(downloader.downloads, isEmpty);
  });

  testWidgets('unknown total RAM picks nothing and offers the manual choice', (
    tester,
  ) async {
    final rig = await openAutoSetup(tester, info: autoSetupInfo(memory: null));
    expect(
      find.text(
        "This phone didn't say how much memory it has, so no speech model was picked.",
      ),
      findsOneWidget,
    );
    expect(find.text('Choose a speech model'), findsOneWidget);
    expect(rig.downloader.downloads, isEmpty);
  });

  testWidgets('a failed download says what failed; the raw text is only '
      'under Details', (tester) async {
    final rig = await openAutoSetup(tester);
    rig.downloader.failure = const VoiceDownloadException(
      'GET https://huggingface.co/x/small-encoder.int8.onnx timed out',
      failure: VoiceDownloadFailure.timeout,
    );
    await autoSetupTap(
      tester,
      find.byKey(const Key('voice-auto-setup-download')),
    );
    await pumpSheet(tester);
    expect(find.byKey(const Key('voice-auto-setup-problem')), findsOneWidget);
    expect(find.textContaining('int8'), findsNothing);
    expect(find.text('Technical details'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
    expect(rig.recorder.startCalls, 0);
    expect(rig.platform.phases, contains(VoiceSetupNotificationPhase.failed));
  });

  testWidgets('a download that finishes while the app is away waits for the '
      'person before listening', (tester) async {
    final rig = await openAutoSetup(tester);
    rig.downloader.gate = Completer<void>();
    await autoSetupTap(
      tester,
      find.byKey(const Key('voice-auto-setup-download')),
    );
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    rig.downloader.gate!.complete();
    await pumpSheet(tester);
    expect(rig.recorder.startCalls, 0);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await pumpSheet(tester);
    expect(find.text('The speech model is on this phone.'), findsOneWidget);
    await autoSetupTap(tester, find.text('Start listening'));
    await pumpSheet(tester);
    expect(rig.result, isTrue);
    expect(rig.composer.state, VoiceComposerState.listening);
    await stopAutoSetupRecording(tester, rig);
  });

  testWidgets('Settings › Voice shows sizes in plain MB, never MiB', (
    tester,
  ) async {
    autoSetupPhone(tester);
    final models = await ScriptedVoiceModels.create(installed: {});
    addTearDown(models.dispose);
    await tester.pumpWidget(
      voiceHost(
        home: voiceLauncher((c) => showVoiceModelSetupSheet(c, models)),
      ),
    );
    await tester.tap(find.text('Open'));
    await pumpSheet(tester);
    final base = voiceModelPack('base');
    expect(
      find.text('Download Balanced (${plainMb(base.downloadBytes)})'),
      findsOneWidget,
    );
    expect(find.textContaining('MiB', skipOffstage: false), findsNothing);
  });

  group('the reading voice by locale', () {
    const voices = [
      ReadAloudVoice(id: 'ar', label: 'ar', locale: 'ar-EG'),
      ReadAloudVoice(id: 'gb', label: 'gb', locale: 'en-GB'),
      ReadAloudVoice(id: 'us', label: 'us', locale: 'en_us'),
    ];

    test('language and region first, then language, else none', () {
      expect(
        readAloudVoiceForLocale(voices, const Locale('en', 'US'))?.id,
        'us',
      );
      expect(
        readAloudVoiceForLocale(voices, const Locale('en', 'AU'))?.id,
        'gb',
      );
      expect(readAloudVoiceForLocale(voices, const Locale('en'))?.id, 'gb');
      expect(
        readAloudVoiceForLocale(voices, const Locale('ar', 'SA'))?.id,
        'ar',
      );
      expect(readAloudVoiceForLocale(voices, const Locale('fr', 'FR')), isNull);
      expect(readAloudVoiceForLocale(const [], const Locale('en')), isNull);
    });
  });
}
