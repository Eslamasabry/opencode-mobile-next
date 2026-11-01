import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image_picker/image_picker.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/state/prompt_photos.dart';
import 'package:opencode_mobile/state/prompt_shelf.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A payload store with no platform behind it, so the vault's own rules —
/// identity, bounds, owner isolation, repair, the order of writes — can be
/// asserted without a filesystem or a browser.
class _MemoryStorage implements DraftAttachmentStorage {
  final entries = <String, Uint8List>{};
  final partial = <String, int>{};
  DraftAttachmentStorageException? refuseWrite;
  int writes = 0;

  @override
  Future<List<DraftPayloadEntry>> list({String? ownerKey}) async => [
    for (final entry in entries.entries)
      if (ownerKey == null || entry.key.startsWith('$ownerKey/'))
        DraftPayloadEntry(
          ownerKey: entry.key.split('/').first,
          id: entry.key.split('/').last,
          bytes: entry.value.length,
        ),
    for (final entry in partial.entries)
      if (ownerKey == null || entry.key.startsWith('$ownerKey/'))
        DraftPayloadEntry(
          ownerKey: entry.key.split('/').first,
          id: entry.key.split('/').last,
          bytes: entry.value,
          committed: false,
        ),
  ];

  @override
  Future<int?> length(String ownerKey, String id) async =>
      entries['$ownerKey/$id']?.length;

  @override
  Future<Uint8List> read(String ownerKey, String id, int expectedBytes) async {
    final bytes = entries['$ownerKey/$id'];
    if (bytes == null) throw const FormatException('Attachment missing');
    if (bytes.length != expectedBytes) {
      throw const FormatException('Attachment size changed');
    }
    return bytes;
  }

  @override
  Future<void> write(String ownerKey, String id, Uint8List bytes) async {
    writes++;
    final refusal = refuseWrite;
    if (refusal != null) throw refusal;
    entries['$ownerKey/$id'] = Uint8List.fromList(bytes);
  }

  @override
  Future<void> delete(String ownerKey, String id) async {
    entries.remove('$ownerKey/$id');
    partial.remove('$ownerKey/$id');
  }
}

class _Picker extends ImagePicker {
  Future<XFile?> Function()? choose;
  @override
  Future<XFile?> pickImage({
    required ImageSource source,
    double? maxWidth,
    double? maxHeight,
    int? imageQuality,
    CameraDevice preferredCameraDevice = CameraDevice.rear,
    bool requestFullMetadata = true,
  }) async => choose?.call();

  @override
  Future<LostDataResponse> retrieveLostData() async => LostDataResponse.empty();
}

const _note = PromptAttachment(
  filename: 'note.txt',
  mime: 'text/plain',
  url: 'data:text/plain;base64,aGVsbG8=',
);
const _other = PromptAttachment(
  filename: 'other.txt',
  mime: 'text/plain',
  url: 'data:text/plain;base64,d29ybGQ=',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('vault over an injected store', () {
    late _MemoryStorage storage;
    late DraftAttachmentVault vault;
    setUp(() {
      storage = _MemoryStorage();
      vault = DraftAttachmentVault(storage: storage);
    });

    test(
      'payloads survive a new vault over the same store and stay per owner',
      () async {
        final refs = await vault.store('server-a', [_note]);
        expect(jsonEncode(refs.single.toJson()), isNot(contains('data:')));
        expect(
          storage.entries.keys.single,
          startsWith('${DraftAttachmentVault.ownerKey('server-a')}/'),
        );

        final restarted = DraftAttachmentVault(storage: storage);
        final recovered = await restarted.restore(
          'server-a',
          refs,
          sameLocation: true,
        );
        expect(recovered.unavailable, isEmpty);
        expect(recovered.attachments.single.url, _note.url);
        expect(
          (await restarted.restore(
            'server-b',
            refs,
            sameLocation: true,
          )).unavailable,
          ['note.txt'],
        );
        // Saving again finds the intact payload and writes nothing new.
        await restarted.store('server-a', [_note]);
        expect(storage.writes, 1);
      },
    );

    test(
      'a payload whose bytes changed is unavailable until the next save repairs it',
      () async {
        final refs = await vault.store('a', [_note]);
        final key = storage.entries.keys.single;
        storage.entries[key] = Uint8List.fromList(
          List.filled(refs.single.bytes, 120),
        );
        expect(
          (await vault.restore('a', refs, sameLocation: true)).unavailable,
          ['note.txt'],
        );
        await vault.store('a', [_note]);
        expect(
          (await vault.restore(
            'a',
            refs,
            sameLocation: true,
          )).attachments.single.url,
          _note.url,
        );
        storage.entries.remove(key);
        expect(
          (await vault.restore('a', refs, sameLocation: true)).unavailable,
          ['note.txt'],
        );
      },
    );

    test(
      'collect keeps referenced payloads per owner and drops partial ones',
      () async {
        final a = await vault.store('a', [_note, _other]);
        final b = await vault.store('b', [_note]);
        storage.partial['${DraftAttachmentVault.ownerKey('a')}/${'0' * 64}'] =
            9;
        expect(
          await vault.collect({
            'a': [a.first],
            'b': b,
          }),
          isTrue,
        );
        expect(storage.partial, isEmpty);
        expect((await vault.restore('a', a, sameLocation: true)).unavailable, [
          'other.txt',
        ]);
        expect(await vault.collect({'b': b}, owner: 'a'), isTrue);
        expect((await vault.restore('a', a, sameLocation: true)).unavailable, [
          'note.txt',
          'other.txt',
        ]);
        expect(
          (await vault.restore('b', b, sameLocation: true)).attachments,
          hasLength(1),
        );
      },
    );

    test(
      'a refused write reports its actionable message and publishes nothing',
      () async {
        storage.refuseWrite = const DraftAttachmentStorageException(
          DraftAttachmentStorageFailure.quota,
          'The browser has no storage space left for this page.',
        );
        await expectLater(
          vault.store('a', [_note]),
          throwsA(
            isA<DraftAttachmentStorageException>()
                .having(
                  (e) => e.failure,
                  'failure',
                  DraftAttachmentStorageFailure.quota,
                )
                .having((e) => e.message, 'message', contains('no storage')),
          ),
        );
        expect(storage.entries, isEmpty);
        storage.refuseWrite = null;
        final refs = await vault.store('a', [_note]);
        expect(
          (await vault.restore('a', refs, sameLocation: true)).attachments,
          hasLength(1),
        );
      },
    );

    test('the root budget refuses a payload before writing it', () async {
      storage.partial['${'f' * 64}/${'e' * 64}'] =
          DraftAttachmentVault.maxDiskBytes;
      await expectLater(
        vault.store('a', [_note]),
        throwsA(
          isA<DraftAttachmentStorageException>()
              .having(
                (e) => e.failure,
                'failure',
                DraftAttachmentStorageFailure.limit,
              )
              .having(
                (e) => e.message,
                'message',
                DraftAttachmentStorageException.rootLimitMessage,
              ),
        ),
      );
      expect(storage.writes, 0);
    });

    test('invalid identities never reach the store', () async {
      final recovery = await vault.restore('a', const [
        DraftAttachmentRef(
          filename: 'bad',
          mime: 'text/plain',
          blob: '../../outside',
          bytes: 2,
        ),
      ], sameLocation: true);
      expect(recovery.unavailable, ['bad']);
      expect(storage.entries, isEmpty);
    });
  });

  group('native default roots', () {
    late Directory support;
    var pathLookups = 0;
    final png = base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
    );

    setUp(() async {
      support = await Directory.systemTemp.createTemp('oc-support-');
      pathLookups = 0;
      const channel = MethodChannel('plugins.flutter.io/path_provider');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            if (call.method == 'getApplicationSupportDirectory') {
              pathLookups++;
              return support.path;
            }
            return null;
          });
      addTearDown(() async {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null);
        await support.delete(recursive: true);
      });
      SharedPreferences.setMockInitialValues({});
    });

    File payloadFile(String root, String owner, DraftAttachmentRef ref) => File(
      '${support.path}/$root/${DraftAttachmentVault.ownerKey(owner)}/${ref.blob}.blob',
    );

    test(
      'a named root resolves under app support only once bytes are needed',
      () async {
        final vault = DraftAttachmentVault(root: 'custom-root-v1');
        expect(pathLookups, 0);
        expect(await vault.collect({}), isTrue);
        final refs = await vault.store('a', [_note]);
        expect(pathLookups, greaterThan(0));
        final file = payloadFile('custom-root-v1', 'a', refs.single);
        expect(await file.exists(), isTrue);
        expect(await file.readAsString(), _note.url);
        expect(
          (await DraftAttachmentVault(
            root: 'custom-root-v1',
          ).restore('a', refs, sameLocation: true)).attachments.single.url,
          _note.url,
        );
        expect(await vault.collect({}, owner: 'a'), isTrue);
        expect(await file.exists(), isFalse);
      },
    );

    test(
      'the stash shelf publishes to its own root and clears it with the profile',
      () async {
        final prefs = await SharedPreferences.getInstance();
        final shelf = PromptShelfStore.withAttachmentFiles(prefs);
        await shelf.stash(
          'a',
          const StashedPrompt(
            id: 'first',
            text: 'keep',
            createdAt: 1,
            attachments: [_note],
          ),
        );
        final ref = shelf.stashes('a').single.attachmentRefs.single;
        final file = payloadFile(
          PromptShelfStore.attachmentDirectoryName,
          'a',
          ref,
        );
        expect(await file.exists(), isTrue);
        final restarted = PromptShelfStore.withAttachmentFiles(prefs);
        final recovery = await restarted.restoreAttachments(
          'a',
          'first',
          sameLocation: true,
        );
        expect(recovery.attachments.single.url, _note.url);
        expect(await restarted.clearForProfile('a'), isTrue);
        expect(await file.exists(), isFalse);
        expect(prefs.getKeys().where((k) => k.contains('.a')), isEmpty);
      },
    );

    test(
      'photo recovery publishes to its own root and clears it with the profile',
      () async {
        final source = File('${support.path}/source.png');
        await source.writeAsBytes(png);
        final picker = _Picker()..choose = () async => XFile(source.path);
        final prefs = await SharedPreferences.getInstance();
        final photos = PromptPhotoStore(prefs, picker: picker);
        final pending = await photos.pick(
          profileID: 'a',
          sessionID: 's',
          directory: '/project',
          workspace: 'w',
          source: ImageSource.gallery,
        );
        final file = payloadFile(
          PromptPhotoStore.attachmentRoot,
          'a',
          pending!.ref!,
        );
        expect(await file.exists(), isTrue);
        expect(
          (await PromptPhotoStore(
            prefs,
            picker: picker,
          ).readPending(pending.id)).mime,
          'image/png',
        );
        expect(await photos.clearForProfile('a'), isTrue);
        expect(await file.exists(), isFalse);
        expect(prefs.containsKey(PromptPhotoStore.key), isFalse);
      },
    );

    test(
      'a filesystem failure surfaces as a storage exception, not a raw error',
      () async {
        final blocker = File('${support.path}/blocker');
        await blocker.writeAsString('not a directory');
        final vault = DraftAttachmentVault(
          directory: () async => Directory('${blocker.path}/vault'),
        );
        await expectLater(
          vault.store('a', [_note]),
          throwsA(
            isA<DraftAttachmentStorageException>()
                .having(
                  (e) => e.failure,
                  'failure',
                  DraftAttachmentStorageFailure.unavailable,
                )
                .having((e) => e.message, 'message', isNot(contains('/'))),
          ),
        );
      },
    );
  });
}
