// New conversation's chooser (revamp unit slice-P4.5): the Work tab's one
// New conversation button asks Solo · Team · In a separate copy · On a
// cloud machine where the server supports each, remembers the answer per
// server, and every start ends in a conversation (or, for a team that is
// off, the team's off state). The separate copy is reached only from here.
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/screens/new_conversation_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show SeededProfileStore;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });
  tearDown(() => debugPlatformCapabilities = null);

  group('the remembered choice', () {
    test('round-trips every way, reads the old Solo · Team values, and is '
        'swept with the server', () async {
      for (final choice in const [
        NewConversationChoice.solo(),
        NewConversationChoice.team(),
        NewConversationChoice.separateCopy(),
        NewConversationChoice.cloud('ws-1'),
      ]) {
        expect(NewConversationChoice.parse(choice.stored), choice);
      }
      expect(NewConversationChoice.parse('cloud:'), isNull);
      expect(NewConversationChoice.parse('other'), isNull);

      SharedPreferences.setMockInitialValues({
        'oc.newConversationMode.phone': 'team',
      });
      final prefs = await SharedPreferences.getInstance();
      expect(
        NewConversationMemory.read(prefs, 'phone'),
        const NewConversationChoice.team(),
      );
      await NewConversationMemory.remember(
        prefs,
        'phone',
        const NewConversationChoice.cloud('ws-1'),
      );
      expect(prefs.getString('oc.newConversationMode.phone'), 'cloud:ws-1');
      final store = SeededProfileStore(prefs: prefs, seeded: const []);
      expect(
        store.profileScopedPreferenceKeys('phone'),
        contains(NewConversationMemory.key('phone')),
      );
    });

    test('only Solo skips the chooser', () {
      expect(const NewConversationOptions(project: 'shop').onlySolo, isTrue);
      expect(
        const NewConversationOptions(separateCopy: true).onlySolo,
        isTrue,
        reason: 'no project, no copy',
      );
      expect(const NewConversationOptions(team: true).onlySolo, isFalse);
      expect(
        const NewConversationOptions(
          clouds: [NewConversationCloud(id: 'a', name: 'a')],
        ).onlySolo,
        isFalse,
      );
    });
  });
}
