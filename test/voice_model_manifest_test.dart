import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';

void main() {
  test('voice dependencies and Android permission are pinned', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final manifest = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final activity = File(
      'android/app/src/main/kotlin/ai/opencode/opencode_mobile/MainActivity.kt',
    ).readAsStringSync();
    final audio = File('lib/voice/audio.dart').readAsStringSync();

    expect(pubspec, contains('sherpa_onnx: 1.13.6'));
    expect(pubspec, contains('record: 7.1.1'));
    expect(manifest, contains('android.permission.RECORD_AUDIO'));
    expect(manifest, contains('android.permission.POST_NOTIFICATIONS'));
    expect(
      manifest,
      contains('android.hardware.microphone" android:required="false"'),
    );
    expect(manifest, isNot(contains('android.permission.BLUETOOTH_CONNECT')));
    expect(
      manifest,
      isNot(contains('android.permission.MODIFY_AUDIO_SETTINGS')),
    );
    expect(audio, contains('manageBluetooth: false'));
    expect(audio, contains('audioSource: AndroidAudioSource.mic'));
    expect(activity, contains('StatFs(filesDir.absolutePath)'));
    expect(activity, contains('activityManager.memoryClass'));
    expect(activity, contains('Build.SUPPORTED_ABIS'));
    expect(activity, contains('permanentlyDenied'));
    expect(manifest, isNot(contains('android.speech.RecognitionService')));
    expect(pubspec, isNot(contains('speech_to_text')));
  });

  test('all immutable model values and totals are exact', () {
    expect(voiceModelPacks.map((pack) => pack.id), ['base', 'small', 'tiny']);
    expect(voiceModelPacks.map((pack) => pack.downloadBytes), [
      160609290,
      375485327,
      103609903,
    ]);
    expect(voiceModelPacks.map((pack) => pack.revision), [
      'bb53ee204431c90d314c1cc08d28d23e5b7927cc',
      '8f3c18b358db4d1f2fc1eae49d75cd20989e4309',
      '65176e2deb88badc814a94058666cadccc29b61c',
    ]);
    expect(
      voiceModelPacks.expand((pack) => pack.files).map((file) => file.length),
      [
        29120534,
        130672026,
        816730,
        112442483,
        262226114,
        816730,
        12937772,
        89855401,
        816730,
      ],
    );
    expect(
      voiceModelPacks.expand((pack) => pack.files).map((file) => file.sha256),
      [
        '0b8fb1304b6109976038efff5ace81720e00386f3ff6b54ee8c75291ca0a1e11',
        '9759d217388a01b3a4c7c15533201067b48ae819c4daafc8624e64b9409dc02d',
        'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126',
        '4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9',
        'acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee',
        'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126',
        'd24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434',
        'd2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925',
        'b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126',
      ],
    );
    expect(voiceModelPack('missing').id, 'base');
    for (final pack in voiceModelPacks) {
      for (final file in pack.files) {
        final url = pack.urlFor(file);
        expect(url.scheme, 'https');
        expect(url.host, 'huggingface.co');
        expect(url.path, contains('/resolve/${pack.revision}/'));
        expect(file.sha256, hasLength(64));
      }
    }
  });

  test('voice runtime and model notices are complete and bundled', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final notices = File('THIRD_PARTY_NOTICES.md').readAsStringSync();

    expect(pubspec, contains('- THIRD_PARTY_NOTICES.md'));
    expect(pubspec, contains('- LICENSES/'));
    expect(notices, contains('sherpa-onnx'));
    expect(notices, contains('ONNX Runtime'));
    expect(notices, contains('1.27.1'));
    expect(notices, contains('OpenAI Whisper'));
    for (final pack in voiceModelPacks) {
      expect(notices, contains(pack.repository));
      expect(notices, contains(pack.revision));
    }
    expect(File('LICENSES/Apache-2.0.txt').existsSync(), isTrue);
    expect(File('LICENSES/BSD-3-Clause-record.txt').existsSync(), isTrue);
    expect(File('LICENSES/MIT-ONNX-Runtime.txt').existsSync(), isTrue);
    expect(File('LICENSES/MIT-OpenAI-Whisper.txt').existsSync(), isTrue);
    expect(
      File('lib/voice/notices.dart').readAsStringSync(),
      contains('VoiceNoticesView'),
    );
  });
}
