// Explicit Android proof target. No accounts, server connections or user files.
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:opencode_mobile/platform/local_pdf.dart';

import 'capture/pdf_proof_fixture.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const MaterialApp(home: _Proof()));
}

class _Proof extends StatefulWidget {
  const _Proof();
  @override
  State<_Proof> createState() => _ProofState();
}

class _ProofState extends State<_Proof> {
  Uint8List? _document, _page;
  bool _running = false;
  String _report = 'Synthetic local PDF proof. Native runtime lease required.';
  Future<void> _run(bool deathWindow) async {
    setState(() => _running = true);
    final report = <String, Object>{
      'mode': deathWindow ? 'service-death' : 'normal',
    };
    try {
      final Uint8List document =
          _document ??
          await compute<bool, Uint8List>(makePdfProofFixture, true);
      _document = document;
      report['inputBytes'] = document.length;
      if (deathWindow) {
        var failures = 0;
        for (var i = 0; i < 30; i++) {
          debugPrint('PDF_PROOF_RENDER_WINDOW $i');
          try {
            await LocalPdf.render(document, 1, LocalPdf.requestID());
          } on PlatformException catch (error) {
            if (error.code != 'service_died') rethrow;
            failures++;
          }
        }
        report['interruptedRequests'] = failures;
        if (failures == 0) {
          throw StateError(
            'No service death observed; the external kill fixture did not run',
          );
        }
        final recovered = await LocalPdf.render(
          document,
          0,
          LocalPdf.requestID(),
        );
        _page = recovered.png;
        report['recoveredAfterDeath'] = true;
      } else {
        final first = await LocalPdf.render(document, 0, LocalPdf.requestID());
        final second = await LocalPdf.render(document, 1, LocalPdf.requestID());
        if (first.pageCount != 2 ||
            second.pageCount != 2 ||
            second.png.length <= 1024 * 1024) {
          throw StateError(
            'High-entropy descriptor transport was not exercised',
          );
        }
        _page = first.png;
        report['highEntropyPngBytes'] = second.png.length;
        final id = LocalPdf.requestID();
        final cancelled = LocalPdf.render(document, 1, id).then(
          (_) => false,
          onError: (Object e) =>
              e is PlatformException && e.code == 'cancelled',
        );
        await LocalPdf.cancel(id);
        if (!await cancelled) throw StateError('Cancellation was not observed');
        report['cancelled'] = true;
        await LocalPdf.render(document, 0, LocalPdf.requestID());
        report['recoveredAfterCancel'] = true;
        var malformed = false;
        try {
          await LocalPdf.render(
            Uint8List.fromList('not a PDF'.codeUnits),
            0,
            LocalPdf.requestID(),
          );
        } on PlatformException {
          malformed = true;
        }
        if (!malformed) throw StateError('Malformed input did not fail');
        report['malformedRejected'] = true;
      }
      report['passed'] = true;
    } catch (error) {
      report['passed'] = false;
      report['error'] = error is PlatformException
          ? error.code
          : error.toString();
    }
    if (mounted) {
      setState(() {
        _running = false;
        _report = jsonEncode(report);
      });
    }
    debugPrint('PDF_NATIVE_PROOF ${jsonEncode(report)}');
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Local PDF native proof')),
    body: Column(
      children: [
        Wrap(
          children: [
            TextButton(
              onPressed: _running ? null : () => _run(false),
              child: const Text('Run normal proof'),
            ),
            TextButton(
              onPressed: _running ? null : () => _run(true),
              child: const Text('Open service-death window'),
            ),
          ],
        ),
        Padding(
          padding: const EdgeInsets.all(16),
          child: SelectableText(_report),
        ),
        if (_running) const LinearProgressIndicator(),
        if (_page != null) Expanded(child: Image.memory(_page!)),
      ],
    ),
  );
}
