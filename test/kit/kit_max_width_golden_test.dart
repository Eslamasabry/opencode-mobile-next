// The chat column at 1,600 dp (docs/ux-system/kit-v2.md §8.6): a prompt, a
// reply, its work line and the composer share one 700 dp column in the
// middle of the window instead of stretching across it.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/kit/kit_max_width_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_markdown.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_message.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;

const _reply =
    'The checkout test was flaky because it read the clock twice. I froze '
    'the clock in the test setup and the three reruns pass. The change is '
    'two lines in checkout_test.dart; nothing else moved.';

void main() {
  setUpAll(loadKitGalleryFonts);
  for (final dark in [true, false]) {
    testWidgets('chat column at 1,600 dp · ${dark ? 'dark' : 'light'}', (
      tester,
    ) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      tester.view.physicalSize = const Size(1600, 900);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final controller = TextEditingController();
      final focus = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focus.dispose);
      await tester.pumpWidget(
        RepaintBoundary(
          key: const ValueKey('shot'),
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: dark ? AppTheme.dark() : AppTheme.light(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: ListView(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      children: [
                        const KitMessage.prompt(
                          body: KitMarkdown(
                            'Fix the flaky checkout test.',
                            selectable: false,
                          ),
                        ),
                        KitWorkLine(
                          counts: const KitWorkCounts(read: 3, edited: 1),
                          state: KitWorkState.done,
                          steps: [
                            const KitText('Read the checkout test'),
                            const KitText('Froze the clock in the setup'),
                          ],
                        ),
                        const KitMessage.reply(
                          body: KitMarkdown(_reply, selectable: false),
                        ),
                      ],
                    ),
                  ),
                  KitComposer(
                    controller: controller,
                    focusNode: focus,
                    hint: 'Ask OpenCode…',
                    onSend: () {},
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 500));
      await expectLater(
        find.byKey(const ValueKey('shot')),
        matchesGoldenFile(
          'goldens/kit_max_width_chat_1600_${dark ? 'dark' : 'light'}.png',
        ),
      );
      debugDefaultTargetPlatformOverride = null;
    });
  }
}
