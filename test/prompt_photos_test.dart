import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/prompt_photos.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _Picker extends ImagePicker {
  Future<XFile?> Function()? choose;
  LostDataResponse lost = LostDataResponse.empty();
  int calls = 0;
  ImageSource? selectedSource;
  @override
  Future<XFile?> pickImage({
    required ImageSource source,
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
    bool requestFullMetadata = true,
  }) async {
    calls++;
    selectedSource = source;
    expect(requestFullMetadata, isFalse);
    return choose?.call();
  }

  @override
  Future<LostDataResponse> retrieveLostData() async => lost;
}

class _RefusedPrefs extends InMemorySharedPreferencesStore {
  _RefusedPrefs() : super.withData({});
  @override
  Future<bool> setValue(String type, String key, Object value) async => false;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory directory;
  late SharedPreferences prefs;
  late _Picker picker;
  late DraftAttachmentVault vault;
  late PromptPhotoStore store;
  final png = base64Decode(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
  );
  Future<XFile> photo() async {
    final file = File('${directory.path}/source.png');
    await file.writeAsBytes(png);
    return XFile(file.path);
  }

  Future<PendingPromptPhoto?> pick() => store.pick(
    profileID: 'a',
    sessionID: 's',
    directory: '/project',
    workspace: 'w',
    source: ImageSource.camera,
  );
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    directory = await Directory.systemTemp.createTemp('oc-photo-test-');
    picker = _Picker();
    vault = DraftAttachmentVault(
      directory: () async => Directory('${directory.path}/vault'),
    );
    store = PromptPhotoStore(prefs, picker: picker, vault: vault);
  });
  tearDown(() async {
    store.dispose();
    await directory.delete(recursive: true);
  });

  test(
    'origin precedes picker launch; selected bytes survive source deletion',
    () async {
      final file = await photo();
      picker.choose = () async {
        expect(store.pending!.profileID, 'a');
        expect(store.pending!.directory, '/project');
        return file;
      };
      final result = (await pick())!;
      expect(picker.selectedSource, ImageSource.camera);
      expect(result.ref, isNotNull);
      expect(
        prefs.getString(PromptPhotoStore.key),
        isNot(contains('data:image')),
      );
      await File(file.path).delete();
      final restarted = PromptPhotoStore(prefs, picker: picker, vault: vault);
      final restored = await restarted.readPending(result.id);
      expect(UriData.parse(restored.url).contentAsBytes(), png);
      await restarted.discard(result.id);
      expect(restarted.pending, isNull);
      expect(
        await Directory(
          '${directory.path}/vault',
        ).list(recursive: true).where((e) => e is File).isEmpty,
        isTrue,
      );
      restarted.dispose();
    },
  );

  test('cancel clears request without manufacturing an attachment', () async {
    expect(await pick(), isNull);
    expect(store.pending, isNull);
  });

  test(
    'permission denial does not leave a nonexistent photo blocking retry',
    () async {
      picker.choose = () async =>
          throw PlatformException(code: 'camera_access_denied');
      await expectLater(pick(), throwsA(isA<PlatformException>()));
      expect(store.pending, isNull);
      picker.choose = () async => null;
      expect(await pick(), isNull);
    },
  );

  test('refused request persistence never launches camera', () async {
    SharedPreferencesStorePlatform.instance = _RefusedPrefs();
    SharedPreferences.resetStatic();
    final failed = PromptPhotoStore(
      await SharedPreferences.getInstance(),
      picker: picker,
      vault: vault,
    );
    await expectLater(
      failed.pick(
        profileID: 'a',
        sessionID: 's',
        directory: null,
        workspace: null,
        source: ImageSource.camera,
      ),
      throwsA(isA<PromptPhotoException>()),
    );
    expect(picker.calls, 0);
    failed.dispose();
  });

  test('lost native result retains original destination at restart', () async {
    const request = PendingPromptPhoto(
      id: 'lost',
      profileID: 'a',
      sessionID: 'old-session',
      directory: '/old',
    );
    await prefs.setString(PromptPhotoStore.key, jsonEncode(request.toJson()));
    picker.lost = LostDataResponse(files: [await photo()]);
    await store.recoverLostData();
    expect(store.pending!.sessionID, 'old-session');
    expect(store.pending!.directory, '/old');
    expect(store.pending!.ref, isNotNull);
    expect((await store.readPending('lost')).mime, 'image/png');
  });

  test('late picker result cannot resurrect a removed profile photo', () async {
    final gate = Completer<XFile?>();
    picker.choose = () => gate.future;
    final pending = pick();
    await Future<void>.delayed(Duration.zero);
    expect(await store.clearForProfile('other'), isTrue);
    expect(store.pending, isNotNull);
    expect(await store.clearForProfile('a'), isTrue);
    gate.complete(await photo());
    expect(await pending, isNull);
    expect(store.pending, isNull);
  });

  test('a pending photo prevents another picker overwriting it', () async {
    picker.choose = photo;
    final original = (await pick())!;
    await expectLater(pick(), throwsA(isA<PromptPhotoException>()));
    expect(store.pending!.id, original.id);
    expect(picker.calls, 1);
  });

  test(
    'image validation rejects fake extensions and oversized input',
    () async {
      await expectLater(
        PromptPhotoStore.readPhoto(
          XFile.fromData(Uint8List.fromList([1, 2, 3]), name: 'fake.png'),
        ),
        throwsA(isA<PromptPhotoException>()),
      );
      await expectLater(
        PromptPhotoStore.readPhoto(
          XFile.fromData(
            Uint8List(PromptPhotoStore.maxBytes + 1),
            name: 'large.png',
          ),
        ),
        throwsA(isA<PromptPhotoException>()),
      );
    },
  );
}
