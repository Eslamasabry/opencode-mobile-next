import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';

void main() {
  late Directory directory;
  late DraftAttachmentVault vault;
  final attachment = PromptAttachment(
    filename: 'ملاحظة.txt',
    mime: 'text/plain',
    url: Uri.dataFromString('Hello مرحبا 🌍', encoding: utf8).toString(),
  );
  setUp(() async {
    directory = await Directory.systemTemp.createTemp('oc-draft-test-');
    vault = DraftAttachmentVault(directory: () async => directory);
  });
  tearDown(() async => directory.delete(recursive: true));

  test(
    'restart restores exact Unicode content without payload in metadata',
    () async {
      final refs = await vault.store('server-a', [attachment]);
      expect(
        jsonEncode(refs.map((r) => r.toJson()).toList()),
        isNot(contains('data:')),
      );
      final restarted = DraftAttachmentVault(directory: () async => directory);
      final recovered = await restarted.restore(
        'server-a',
        refs,
        sameLocation: true,
      );
      expect(recovered.unavailable, isEmpty);
      expect(recovered.attachments.single.url, attachment.url);
      expect(recovered.attachments.single.filename, attachment.filename);
      expect(
        (await restarted.restore(
          'server-b',
          refs,
          sameLocation: true,
        )).unavailable,
        [attachment.filename],
      );
    },
  );

  test(
    'shared bytes remain until last reference and owners clean independently',
    () async {
      final a = await vault.store('a', [attachment]);
      final b = await vault.store('b', [attachment]);
      expect(
        await vault.collect({
          'a': [...a, ...a],
          'b': b,
        }),
        isTrue,
      );
      expect(await vault.collect({'a': a, 'b': b}), isTrue);
      expect(
        (await vault.restore('a', a, sameLocation: true)).attachments,
        hasLength(1),
      );
      expect(await vault.collect({'b': b}, owner: 'a'), isTrue);
      expect(
        (await vault.restore('a', a, sameLocation: true)).unavailable,
        hasLength(1),
      );
      expect(
        (await vault.restore('b', b, sameLocation: true)).attachments,
        hasLength(1),
      );
    },
  );

  test('corrupt bytes are reported and a subsequent save repairs them', () async {
    final refs = await vault.store('a', [attachment]);
    final file = File(
      '${directory.path}/${DraftAttachmentVault.ownerKey('a')}/${refs.single.blob}.blob',
    );
    await file.writeAsBytes(List.filled(refs.single.bytes, 120));
    expect((await vault.restore('a', refs, sameLocation: true)).unavailable, [
      attachment.filename,
    ]);
    await vault.store('a', [attachment]);
    expect(
      (await vault.restore(
        'a',
        refs,
        sameLocation: true,
      )).attachments.single.url,
      attachment.url,
    );
    await file.delete();
    expect((await vault.restore('a', refs, sameLocation: true)).unavailable, [
      attachment.filename,
    ]);
  });

  test(
    'project file references require matching location and are never read locally',
    () async {
      final refs = await vault.store('a', const [
        PromptAttachment(
          filename: 'server.txt',
          mime: 'text/plain',
          url: 'file:///server/project/server.txt',
        ),
        PromptAttachment(
          filename: 'web.txt',
          mime: 'text/plain',
          url: 'https://example.com/web.txt',
        ),
      ]);
      expect(
        (await vault.restore('a', refs, sameLocation: true)).attachments,
        hasLength(2),
      );
      final moved = await vault.restore('a', refs, sameLocation: false);
      expect(moved.unavailable, ['server.txt']);
      expect(moved.attachments.single.filename, 'web.txt');
      expect(await directory.list().isEmpty, isTrue);
    },
  );

  test(
    'invalid identities and temporary references cannot escape the vault',
    () async {
      final recovery = await vault.restore('a', const [
        DraftAttachmentRef(
          filename: 'bad',
          mime: 'text/plain',
          blob: '../../outside',
          bytes: 2,
        ),
      ], sameLocation: true);
      expect(recovery.unavailable, ['bad']);
      await expectLater(
        vault.store('a', const [
          PromptAttachment(
            filename: 'temporary',
            mime: 'text/plain',
            url: 'content://temporary/file',
          ),
        ]),
        throwsFormatException,
      );
      final unrelated = File('${directory.path}/keep.txt');
      await unrelated.writeAsString('keep');
      await vault.collect({});
      expect(await unrelated.readAsString(), 'keep');
    },
  );
}
