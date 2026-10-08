// Shared fixtures for screen-voice-1's behaviour tests and goldens: a voice
// model manager whose installed packs, state and progress the test sets,
// and a voice composer controller that walks listening → draft on command
// without a microphone or a recognizer.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/voice/controller.dart';
import 'package:opencode_mobile/voice/model_download.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/notices.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
import '../voice_controller_test.dart'
    show FakeVoiceRecognizer, FakeVoiceRecorder;

class _NoStore implements VoiceFileStore {
  @override
  Future<void> createDirectory(String path) async {}
  @override
  Future<void> delete(String path) async {}
  @override
  Future<bool> exists(String path) async => false;
  @override
  Future<int> length(String path) async => 0;
  @override
  Future<void> move(String from, String to) async {}
  @override
  Future<VoiceByteSink> openWrite(String path, {required bool append}) =>
      throw UnimplementedError();
  @override
  Stream<List<int>> read(String path) => const Stream.empty();
  @override
  Future<Uint8List> readBytes(String path) async => Uint8List(0);
  @override
  Future<void> writeAtomic(String path, List<int> bytes) async {}
}

class _NoHttp implements VoiceHttpTransport {
  @override
  void close() {}
  @override
  Future<VoiceHttpResponse> get(
    Uri uri, {
    Map<String, String> headers = const {},
    VoiceCancellationToken? cancellation,
  }) => throw UnimplementedError();
}

/// A manager the test drives: which packs are on the phone, the state, the
/// progress and the error are fields; every act is recorded, never run.
class ScriptedVoiceModels extends VoiceModelManager {
  ScriptedVoiceModels(
    SharedPreferences preferences, {
    Set<String> installed = const {'base'},
  }) : installedIds = {...installed},
       super(
         root: '/private/models',
         preferences: preferences,
         downloader: VoiceModelDownloader(store: _NoStore(), http: _NoHttp()),
       ) {
    state = installedIds.contains(selectedPack.id)
        ? VoiceModelState.ready
        : VoiceModelState.required;
  }

  final Set<String> installedIds;
  final List<String> acts = [];

  static Future<ScriptedVoiceModels> create({
    Set<String> installed = const {'base'},
  }) async {
    SharedPreferences.setMockInitialValues({});
    return ScriptedVoiceModels(
      await SharedPreferences.getInstance(),
      installed: installed,
    );
  }

  @override
  bool isInstalled(VoiceModelPack pack) => installedIds.contains(pack.id);

  @override
  bool get isReady =>
      state == VoiceModelState.ready && isInstalled(selectedPack);

  @override
  Future<void> downloadSelected({bool replaceExisting = false}) async {
    acts.add('download:${selectedPack.id}');
  }

  @override
  Future<void> redownloadPack(VoiceModelPack pack) async {
    acts.add('redownload:${pack.id}');
  }

  @override
  void cancelDownload() => acts.add('cancel');

  @override
  Future<void> deletePack(VoiceModelPack pack) async {
    acts.add('delete:${pack.id}');
    installedIds.remove(pack.id);
    if (selectedPack.id == pack.id) state = VoiceModelState.required;
    emit();
  }

  /// Shows a download of [pack] at [received] of its size.
  void downloading(VoiceModelPack pack, int received) {
    selectedPack = pack;
    state = VoiceModelState.downloading;
    progress = VoiceDownloadProgress(
      received: received,
      total: pack.downloadBytes,
      fileName: pack.encoder.name,
    );
    emit();
  }

  void emit() => notifyListeners();
}

/// A composer controller that listens and drafts on command: startListening
/// goes to listening, stopListening to a draft of [said].
class ScriptedVoiceComposer extends VoiceComposerController {
  ScriptedVoiceComposer({required super.models, this.said = ''})
    : super(recorder: FakeVoiceRecorder(), recognizer: FakeVoiceRecognizer());

  final String said;
  int cancels = 0;

  @override
  Future<void> startListening() async {
    state = VoiceComposerState.listening;
    elapsed = const Duration(seconds: 7);
    level = .55;
    notifyListeners();
  }

  @override
  Future<void> stopListening() async {
    draft = said;
    state = VoiceComposerState.draft;
    notifyListeners();
  }

  @override
  Future<void> cancel({String? reason, bool clearError = false}) async {
    cancels++;
  }

  /// Puts the controller in [next] with [failure].
  void show(VoiceComposerState next, {Object? failure}) {
    state = next;
    error = failure;
    notifyListeners();
  }
}

/// The app's theme and localisation around [home], animations off.
Widget voiceHost({
  required Widget home,
  bool light = false,
  Key? boundary,
  double textScale = 1,
}) => RepaintBoundary(
  key: boundary,
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: captureTheme(light: light),
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(
        disableAnimations: true,
        textScaler: TextScaler.linear(textScale),
      ),
      child: child!,
    ),
    home: home,
  ),
);

/// The Voice licenses group as Settings › About shows it, on a bare page (the
/// group has no page of its own since FG6).
const noticesHost = Scaffold(
  body: SafeArea(child: SingleChildScrollView(child: VoiceNoticesView())),
);

/// A page with one button that runs [open] with its context.
Widget voiceLauncher(Future<Object?> Function(BuildContext) open) => Builder(
  builder: (context) => Material(
    child: Center(
      child: TextButton(
        onPressed: () => unawaited(open(context)),
        child: const Text('Open'),
      ),
    ),
  ),
);

/// Voice sheets animate (the level meter); tests pump instead of settling.
Future<void> pumpSheet(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
  await tester.pump();
}

@visibleForTesting
const voicePhone = Size(412, 915);
@visibleForTesting
const voiceWide = Size(1280, 800);
