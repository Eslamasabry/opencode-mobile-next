import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/desktop/desktop_interaction.dart';
import 'package:opencode_mobile/ui/screens/files_screen.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _FilesApi extends OpenCodeApi {
  _FilesApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => [
    FileNode(name: 'lib', path: 'lib', isDir: true),
    FileNode(name: 'main.dart', path: 'main.dart', isDir: false),
  ];
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: prefs))
    ..api = _FilesApi()
    ..status = StreamStatus.connected;
}

/// A widget test that runs with the platform reported as Linux desktop. The
/// override must be cleared inside the body: flutter_test asserts no
/// foundation debug variable outlives the test, so tearDown runs too late.
void desktopTest(
  String description,
  Future<void> Function(WidgetTester tester) body,
) {
  testWidgets(description, (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.linux;
    try {
      await body(tester);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}

Widget _linkHost() => MaterialApp(
  scrollBehavior: const AppScrollBehavior(),
  home: Scaffold(
    body: MarkdownFileLinks(
      validate: (_) async => true,
      open: (_) {},
      child: const MarkdownText('See `lib/a/b.dart` for details.'),
    ),
  ),
);

Future<MouseCursor?> _cursorOver(WidgetTester tester, Finder target) async {
  final gesture = await tester.createGesture(kind: PointerDeviceKind.mouse);
  await gesture.addPointer(location: Offset.zero);
  addTearDown(gesture.removePointer);
  await tester.pump();
  await gesture.moveTo(tester.getCenter(target));
  await tester.pumpAndSettle();
  return RendererBinding.instance.mouseTracker.debugDeviceActiveCursor(1);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  desktopTest('a live file link takes the click cursor', (tester) async {
    await tester.pumpWidget(_linkHost());
    await tester.pumpAndSettle();

    final cursor = await _cursorOver(
      tester,
      find.byKey(const Key('path-link-lib/a/b.dart')),
    );
    expect(cursor, SystemMouseCursors.click);
  });

  testWidgets('android leaves the file link cursor alone', (tester) async {
    await tester.pumpWidget(_linkHost());
    await tester.pumpAndSettle();

    final cursor = await _cursorOver(
      tester,
      find.byKey(const Key('path-link-lib/a/b.dart')),
    );
    expect(cursor, isNot(SystemMouseCursors.click));
  });

  desktopTest('an editable field still reports the text cursor', (
    tester,
  ) async {
    final controller = TextEditingController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        scrollBehavior: const AppScrollBehavior(),
        home: Scaffold(body: TextField(controller: controller)),
      ),
    );
    await tester.pumpAndSettle();

    final cursor = await _cursorOver(tester, find.byType(TextField));
    expect(cursor, SystemMouseCursors.text);
  });

  desktopTest('a ListTile row reports the click cursor and hovers', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        scrollBehavior: const AppScrollBehavior(),
        home: Scaffold(
          body: ListTile(title: const Text('row'), onTap: () {}),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final cursor = await _cursorOver(tester, find.byType(ListTile));
    expect(cursor, SystemMouseCursors.click);
    // InkWell paints a hover overlay while the pointer is inside it.
    expect(find.byType(InkWell), findsOneWidget);
  });

  desktopTest('the scroll wheel scrolls a list', (tester) async {
    final controller = ScrollController();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(
        scrollBehavior: const AppScrollBehavior(),
        home: Scaffold(
          body: ListView.builder(
            controller: controller,
            itemCount: 200,
            itemBuilder: (context, index) =>
                SizedBox(height: 40, child: Text('row $index')),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(controller.offset, 0);

    final pointer = TestPointer(1, PointerDeviceKind.mouse);
    pointer.hover(tester.getCenter(find.byType(ListView)));
    await tester.sendEventToBinding(pointer.scroll(const Offset(0, 320)));
    await tester.pumpAndSettle();

    expect(controller.offset, greaterThan(0));
  });

  desktopTest('the Files split divider resizes and shows a resize cursor', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final connection = await _controller();
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      MaterialApp(
        scrollBehavior: const AppScrollBehavior(),
        home: Scaffold(body: FilesScreen(controller: connection)),
      ),
    );
    await tester.pumpAndSettle();

    final handle = find.byKey(const ValueKey('files-split-handle'));
    expect(handle, findsOneWidget);
    expect(await _cursorOver(tester, handle), SystemMouseCursors.resizeColumn);

    final before = tester.getRect(handle).left;
    await tester.drag(handle, const Offset(120, 0));
    await tester.pumpAndSettle();
    expect(tester.getRect(handle).left, greaterThan(before + 50));
  });

  testWidgets('android keeps the plain Files hairline divider', (tester) async {
    tester.view.physicalSize = const Size(1200, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final connection = await _controller();
    addTearDown(connection.dispose);

    await tester.pumpWidget(
      MaterialApp(home: Scaffold(body: FilesScreen(controller: connection))),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('files-split-handle')), findsNothing);
    expect(find.byType(VerticalDivider), findsOneWidget);
  });

  desktopTest('mouse drag selects text rather than scrolling the list', (
    tester,
  ) async {
    // Mouse is deliberately absent from dragDevices: drag-to-scroll would
    // take the gesture desktop users select transcript text with.
    expect(
      const AppScrollBehavior().dragDevices.contains(PointerDeviceKind.mouse),
      isFalse,
    );
    await tester.pumpWidget(const SizedBox());
  });
}
