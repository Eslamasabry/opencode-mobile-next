// Renders the shell command block from a real Claude Code chat (owner phone,
// 2026-10-07): a heredoc script whose lines are not separate commands.
//
//   flutter test --concurrency=1 tool/capture/command_block_test.dart
//
// Output: docs/qa/command-block-2026-10-07/<prefix>-<scene>.png
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_code_block.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

import 'fixtures.dart';

const _prefix = String.fromEnvironment(
  'COMMAND_BLOCK_CAPTURE',
  defaultValue: 'after',
);

const heredocCommand = r'''cat > /tmp/probe_st.py <<'EOF'
import json, os, time, urllib.request, urllib.error, random, re
H=os.environ['P_HOST']; U=os.environ['P_USER']; P=os.environ['P_PASS']
ids=json.load(open('/tmp/probe_ids.json'))
def probe(url, ua, n=65536, timeout=12):
    req=urllib.request.Request(url, headers={'User-Agent': ua})
    t=time.time()
    try:
        r=urllib.request.urlopen(req, timeout=timeout); d=r.read(n)
        ctype=r.headers.get('Content-Type'); r.close()
        return f"{r.status} {len(d)}B {ctype} {time.time()-t:.1f}s"
    except urllib.error.HTTPError as e: return f"HTTP {e.code}"
    except Exception as e: return type(e).__name__
UAS={'ours':'Mozilla/5.0 (Linux; Android 14)', 'vlc':'VLC/3.0.20 LibVLC/3.0.20'}
random.seed(4)
for sid in random.sample(ids['live'][:400], 3):
    for ext in ['ts','m3u8']:
        url=f"{H}/live/{U}/{P}/{sid}.{ext}"
        print('live',ext, probe(url, UAS['ours']))
sid=ids['live'][7]
for name,ua in UAS.items():
    print('ua',name, probe(f"{H}/live/{U}/{P}/{sid}.ts", ua))
vid,ext=ids['vod'][3]
print('movie',ext, probe(f"{H}/movie/{U}/{P}/{vid}.{ext}", UAS['ours']))
# m3u8 manifest content shape
req=urllib.request.Request(f"{H}/live/{U}/{P}/{sid}.m3u8", headers={'User-Agent': UAS['ours']})
try:
    r=urllib.request.urlopen(req,timeout=12)
    print('m3u8 first lines:', [l for l in r.read(4096).decode().splitlines()[:6]])
except Exception as e: print('m3u8 manifest', type(e).__name__)
EOF
read -r P_HOST P_USER P_PASS <<< "http://s1.example.test:8080 demo demo"
P_HOST=$P_HOST P_USER=$P_USER python3 /tmp/probe_st.py''';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final wrap in [false, true]) {
    final name = wrap ? 'wrapped' : 'sideways';
    testWidgets('$_prefix $name', (tester) async {
      tester.view.physicalSize = const Size(390, 1100);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  KitCodeBlock(
                    text: heredocCommand,
                    kind: KitCodeKind.command,
                    copyLabel: 'Copy command',
                    wrap: wrap,
                  ),
                ],
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final showAll = find.byKey(const ValueKey('kit-code-show-all'));
      if (showAll.evaluate().isNotEmpty) {
        await tester.tap(showAll);
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/command-block-2026-10-07/$_prefix-$name.png',
        await capturePng(tester, boundary, pixelRatio: 1),
      );
    });
  }

  for (final status in ['running', 'completed']) {
    testWidgets('$_prefix tool card $status', (tester) async {
      tester.view.physicalSize = const Size(412, 1400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: ToolCard(
                  toolName: 'bash',
                  state: ToolState.fromJson({
                    'status': status,
                    'input': {'command': heredocCommand},
                    if (status == 'completed') 'output': 'live ts 200 65536B',
                    'metadata': {if (status == 'completed') 'exit': 0},
                  }, toolName: 'bash'),
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pump(const Duration(milliseconds: 600));
      final header = find.text('Shell');
      if (header.evaluate().isNotEmpty) {
        await tester.tap(header.first);
        await tester.pump(const Duration(milliseconds: 600));
      await tester.pump(const Duration(milliseconds: 600));
      }
      final showAll = find.byKey(const ValueKey('kit-code-show-all'));
      if (showAll.evaluate().isNotEmpty) {
        await tester.tap(showAll);
        await tester.pump(const Duration(milliseconds: 600));
      await tester.pump(const Duration(milliseconds: 600));
      }
      await writePng(
        'docs/qa/command-block-2026-10-07/$_prefix-tool-card-$status.png',
        await capturePng(tester, boundary, pixelRatio: 1),
      );
    });
  }
}
