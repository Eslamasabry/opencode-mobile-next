import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/platform/phone_storage_folders.dart';

void main() {
  test('/sdcard reads as the internal storage; nothing outside it does', () {
    expect(PhoneStorageFolders.normalize('/sdcard'), PhoneStorageFolders.root);
    expect(
      PhoneStorageFolders.normalize('/sdcard/Code/'),
      '${PhoneStorageFolders.root}/Code',
    );
    expect(PhoneStorageFolders.normalize('/data/data'), isNull);
    expect(
      PhoneStorageFolders.normalize('${PhoneStorageFolders.root}/../x'),
      isNull,
    );
  });

  test('the parent stops at the internal storage', () {
    expect(PhoneStorageFolders.parentOf(PhoneStorageFolders.root), isNull);
    expect(
      PhoneStorageFolders.parentOf('${PhoneStorageFolders.root}/a/b'),
      '${PhoneStorageFolders.root}/a',
    );
  });

  test('a path outside is invalid, a missing folder is said so', () async {
    final folders = PhoneStorageFolders();
    await expectLater(
      folders.list('/etc'),
      throwsA(
        isA<FolderListException>().having(
          (e) => e.problem,
          'problem',
          FolderListProblem.invalid,
        ),
      ),
    );
    await expectLater(
      folders.list('${PhoneStorageFolders.root}/definitely-not-here'),
      throwsA(isA<FolderListException>()),
    );
  });
}
