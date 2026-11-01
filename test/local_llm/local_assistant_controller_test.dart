import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/local_llm/local_llm_controller.dart';
import 'package:opencode_mobile/local_llm/local_llm_pack.dart';
import 'package:opencode_mobile/local_llm/local_llm_platform.dart';

import 'local_llm_fakes.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('gate', () {
    test('a 64-bit phone with enough memory is offered the assistant', () {
      expect(localAssistantBlock(phone(totalMemoryMb: 8000)), isNull);
      expect(localAssistantBlock(phone(totalMemoryMb: 5929)), isNull);
      expect(localAssistantBlock(phone(abis: ['x86_64'])), isNull);
    });

    test('a 4 GB phone is not, and an unreadable amount is not trusted', () {
      expect(
        localAssistantBlock(phone(totalMemoryMb: 3900)),
        LocalAssistantBlock.memory,
      );
      expect(
        localAssistantBlock(phone(totalMemoryMb: null)),
        LocalAssistantBlock.memory,
      );
    });

    test('32-bit phones are not offered it', () {
      expect(
        localAssistantBlock(phone(abis: ['armeabi-v7a'])),
        LocalAssistantBlock.abi,
      );
      expect(localAssistantBlock(phone(abis: [])), LocalAssistantBlock.abi);
    });

    test('the controller reports the block and refuses to download', () async {
      final rig = await Rig.create(info: phone(totalMemoryMb: 3000));
      expect(rig.controller.available, isFalse);
      await rig.controller.download();
      expect(rig.downloader.downloads, 0);
    });
  });

  group('download', () {
    test('never starts by itself', () async {
      final rig = await Rig.create();
      expect(rig.downloader.downloads, 0);
      expect(rig.controller.installed, isFalse);
    });

    test('off Wi-Fi it waits and says why; mobile data is a choice', () async {
      final rig = await Rig.create(network: mobile);
      await rig.controller.download();
      expect(rig.controller.phase, LocalAssistantPhase.needsWifi);
      expect(rig.downloader.downloads, 0);
      await rig.controller.download(allowMobileData: true);
      expect(rig.downloader.downloads, 1);
      expect(rig.controller.installed, isTrue);
    });

    test('on Wi-Fi it downloads, shows progress and installs', () async {
      final rig = await Rig.create();
      final gate = rig.downloader.gate = Completer<void>();
      final done = rig.controller.download();
      await pumpEventQueue();
      expect(rig.controller.phase, LocalAssistantPhase.downloading);
      expect(rig.controller.progress!.fraction, closeTo(0.5, 0.01));
      gate.complete();
      await done;
      expect(rig.controller.installed, isTrue);
      expect(rig.controller.phase, LocalAssistantPhase.idle);
    });

    test('too little free space stops it before any byte is fetched', () async {
      final rig = await Rig.create(
        info: phone(freeBytes: 2 * 1000 * 1000 * 1000),
      );
      await rig.controller.download();
      expect(rig.controller.phase, LocalAssistantPhase.noSpace);
      expect(rig.downloader.downloads, 0);
    });

    test('cancel leaves it not installed and ready to start again', () async {
      final rig = await Rig.create();
      rig.downloader.gate = Completer<void>();
      final done = rig.controller.download();
      await pumpEventQueue();
      rig.controller.cancelDownload();
      await done;
      expect(rig.controller.installed, isFalse);
      expect(rig.controller.phase, LocalAssistantPhase.idle);
    });

    test('delete removes the file and unloads the model first', () async {
      final rig = await Rig.create(installed: true, enabled: true);
      await rig.controller.ensureLoaded();
      await rig.controller.deleteModel();
      expect(rig.controller.installed, isFalse);
      expect(rig.llm.calls, containsAllInOrder(['load:gpu', 'unload']));
      expect(rig.controller.offersSummary, isFalse);
    });

    test(
      'a model file of the wrong size is not counted as installed',
      () async {
        final rig = await Rig.create(installed: true);
        expect(rig.controller.installed, isTrue);
        rig.downloader.files.lengths[rig.controller.modelPath] = 10;
        await rig.controller.initialize();
        expect(rig.controller.installed, isFalse);
      },
    );
  });

  group('the menu offer', () {
    test(
      'needs the switch on, the model downloaded and a capable phone',
      () async {
        final off = await Rig.create(installed: true);
        expect(off.controller.offersSummary, isFalse);
        final on = await Rig.create(installed: true, enabled: true);
        expect(on.controller.offersSummary, isTrue);
        final missing = await Rig.create(enabled: true);
        expect(missing.controller.offersSummary, isFalse);
        final weak = await Rig.create(
          installed: true,
          enabled: true,
          info: phone(totalMemoryMb: 3000),
        );
        expect(weak.controller.offersSummary, isFalse);
      },
    );

    test('the switch is remembered', () async {
      final rig = await Rig.create(installed: true);
      await rig.controller.setEnabled(true);
      await rig.controller.initialize();
      expect(rig.controller.enabled, isTrue);
    });
  });

  group('summary', () {
    Future<Rig> ready() => Rig.create(installed: true, enabled: true);

    test('loads GPU first, streams the words and ends done', () async {
      final rig = await ready();
      final run = rig.controller.summarize('User: hi\n\nAssistant: hello');
      await pumpEventQueue();
      expect(rig.llm.calls, ['load:gpu', 'generate:summary-1']);
      expect(rig.llm.lastPrompt, contains('User: hi'));
      expect(rig.llm.lastMaxTokens, localSummaryMaxTokens);
      rig.llm.emit(const LocalLlmToken('summary-1', 'They '));
      rig.llm.emit(const LocalLlmToken('summary-1', 'said hello.'));
      await pumpEventQueue();
      expect(run.text, 'They said hello.');
      expect(run.status, LocalSummaryStatus.running);
      rig.llm.emit(
        const LocalLlmDone(
          'summary-1',
          firstTokenMs: 500,
          totalMs: 1500,
          chunks: 3,
        ),
      );
      await pumpEventQueue();
      expect(run.status, LocalSummaryStatus.done);
      expect(run.wordsPerSecond, closeTo(3.0, 0.01));
    });

    test('a second summary reuses the loaded model', () async {
      final rig = await ready();
      rig.controller.summarize('one');
      await pumpEventQueue();
      rig.llm.emit(
        const LocalLlmDone('summary-1', firstTokenMs: 1, totalMs: 2, chunks: 1),
      );
      await pumpEventQueue();
      rig.controller.summarize('two');
      await pumpEventQueue();
      expect(rig.llm.calls.where((c) => c.startsWith('load')).length, 1);
    });

    test('falling back to the CPU is reported', () async {
      final rig = await ready();
      rig.llm
        ..backendToUse = 'cpu'
        ..fellBack = true;
      await rig.controller.ensureLoaded();
      expect(rig.controller.backend, 'cpu');
      expect(rig.controller.fellBackToCpu, isTrue);
    });

    test('Stop cancels on the device and keeps the words so far', () async {
      final rig = await ready();
      final run = rig.controller.summarize('x');
      await pumpEventQueue();
      rig.llm.emit(const LocalLlmToken('summary-1', 'Partial'));
      await pumpEventQueue();
      await run.stop();
      expect(rig.llm.calls, contains('cancel'));
      expect(run.status, LocalSummaryStatus.stopped);
      expect(run.text, 'Partial');
      // Late words after Stop do not reopen it.
      rig.llm.emit(const LocalLlmToken('summary-1', ' more'));
      await pumpEventQueue();
      expect(run.text, 'Partial');
    });

    test(
      'Stop while the model is still loading never starts generating',
      () async {
        final rig = await ready();
        final gate = rig.llm.loadGate = Completer<void>();
        final run = rig.controller.summarize('x');
        await pumpEventQueue();
        await run.stop();
        gate.complete();
        await pumpEventQueue();
        expect(rig.llm.calls.where((c) => c.startsWith('generate')), isEmpty);
      },
    );

    test('words from an older summary never land in a newer one', () async {
      final rig = await ready();
      rig.controller.summarize('one');
      await pumpEventQueue();
      final second = rig.controller.summarize('two');
      await pumpEventQueue();
      rig.llm.emit(const LocalLlmToken('summary-1', 'stale'));
      rig.llm.emit(const LocalLlmToken('summary-2', 'fresh'));
      await pumpEventQueue();
      expect(second.text, 'fresh');
    });

    test('the system freeing the model fails the run and unloads', () async {
      final rig = await ready();
      final run = rig.controller.summarize('x');
      await pumpEventQueue();
      rig.llm.emit(const LocalLlmUnloaded('memory'));
      await pumpEventQueue();
      expect(run.status, LocalSummaryStatus.failed);
      expect(rig.controller.loaded, isFalse);
      // The next summary loads it again.
      rig.controller.summarize('y');
      await pumpEventQueue();
      expect(rig.llm.calls.where((c) => c.startsWith('load')).length, 2);
    });

    test('a load failure ends the run as failed', () async {
      final rig = await ready();
      rig.llm.loadError = const LocalLlmException('load_failed');
      final run = rig.controller.summarize('x');
      await pumpEventQueue();
      expect(run.status, LocalSummaryStatus.failed);
      expect(rig.controller.phase, LocalAssistantPhase.error);
    });
  });

  group('prompt', () {
    test('a short conversation goes in whole', () {
      final prompt = buildLocalSummaryPrompt('User: hi');
      expect(prompt, endsWith('User: hi'));
    });

    test('a long one keeps its opening and its end, not the middle', () {
      final long = 'START${'m' * 20000}END';
      final prompt = buildLocalSummaryPrompt(long, maxChars: 1000);
      expect(prompt, contains('START'));
      expect(prompt, endsWith('END'));
      expect(prompt, contains('[...]'));
      expect(prompt.length, lessThan(1500));
    });
  });

  group('events', () {
    test('parse every native event', () {
      expect(
        LocalLlmEvent.parse({'type': 'token', 'id': 'a', 'text': 'x'}),
        isA<LocalLlmToken>(),
      );
      expect(
        LocalLlmEvent.parse({'type': 'unloaded', 'reason': 'memory'}),
        isA<LocalLlmUnloaded>(),
      );
      expect(LocalLlmEvent.parse({'type': 'nonsense'}), isNull);
      expect(LocalLlmEvent.parse('x'), isNull);
    });
  });
}
