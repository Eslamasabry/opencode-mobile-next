import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_iconography.dart';
import 'package:opencode_mobile/ui/app_theme.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    for (final entry in {
      'PhosphorRegular': 'Phosphor.ttf',
      'PhosphorDuotone': 'Phosphor-Duotone.ttf',
      'PhosphorFill': 'Phosphor-Fill.ttf',
    }.entries) {
      final loader = FontLoader('App${entry.key}');
      loader.addFont(rootBundle.load('assets/fonts/phosphor/${entry.value}'));
      await loader.load();
    }
    final roboto = FontLoader('Roboto');
    roboto.addFont(
      Future.value(
        ByteData.sublistView(
          File('tool/capture/fonts/Roboto-Regular.ttf').readAsBytesSync(),
        ),
      ),
    );
    await roboto.load();
  });

  for (final dark in [false, true]) {
    testWidgets('Phosphor glyphs render in ${dark ? 'dark' : 'light'} theme', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final boundary = GlobalKey();
      final theme = dark ? AppTheme.dark() : AppTheme.light();
      await tester.pumpWidget(
        MaterialApp(
          theme: theme,
          home: RepaintBoundary(
            key: boundary,
            child: Scaffold(
              body: SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Row(
                        children: [
                          AppBrandMark(),
                          SizedBox(width: 12),
                          Text(
                            'Clear iconography',
                            style: TextStyle(fontSize: 24, height: 1.3),
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Actual Flutter font render · 24px glyphs',
                        style: theme.textTheme.bodySmall,
                      ),
                      const SizedBox(height: 24),
                      for (final row in [
                        (
                          'Workspace',
                          AppIconography.workspace,
                          AppIconography.workspaceSelected,
                        ),
                        (
                          'Files',
                          AppIconography.files,
                          AppIconography.filesSelected,
                        ),
                        (
                          'Activity',
                          AppIconography.activity,
                          AppIconography.activitySelected,
                        ),
                        ('More', AppIconography.more, AppIconography.more),
                      ])
                        SizedBox(
                          height: 64,
                          child: Row(
                            children: [
                              Expanded(child: Text(row.$1)),
                              AppGlyph(row.$2, size: 24),
                              const SizedBox(width: 56),
                              AppGlyph(
                                row.$3,
                                size: 24,
                                color: theme.colorScheme.primary,
                              ),
                            ],
                          ),
                        ),
                      const Divider(height: 32),
                      const Text('Actions · 48dp touch targets'),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final item in [
                            ('New session', AppIconography.add),
                            ('Search', AppIconography.search),
                            ('Settings', AppIconography.settings),
                            ('Branch', AppIconography.branch),
                            ('Terminal', AppIconography.terminal),
                            ('Copy', AppIconography.copy),
                            ('Expand', AppIconography.expand),
                            ('Close', AppIconography.close),
                          ])
                            IconButton(
                              tooltip: item.$1,
                              onPressed: () {},
                              icon: AppGlyph(item.$2),
                            ),
                        ],
                      ),
                      const SizedBox(height: 24),
                      Row(
                        children: [
                          IconButton.filled(
                            tooltip: 'Send',
                            onPressed: () {},
                            icon: const AppGlyph(AppIconography.send),
                          ),
                          const SizedBox(width: 12),
                          IconButton.filledTonal(
                            tooltip: 'Stop',
                            onPressed: () {},
                            icon: AppGlyph(
                              AppIconography.stop,
                              color: theme.colorScheme.error,
                            ),
                          ),
                          const SizedBox(width: 12),
                          const IconButton(
                            tooltip: 'Send unavailable',
                            onPressed: null,
                            icon: AppGlyph(AppIconography.send),
                          ),
                        ],
                      ),
                      const Spacer(),
                      Text(
                        'Component fixture, not an installed app screen.',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      final render =
          boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      await tester.runAsync(() async {
        final image = await render.toImage(pixelRatio: 2);
        final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
        image.dispose();
        final file = File(
          'docs/qa/clear-iconography/glyphs-${dark ? 'dark' : 'light'}.png',
        );
        file.parent.createSync(recursive: true);
        file.writeAsBytesSync(bytes!.buffer.asUint8List());
      });
    });
  }
}
