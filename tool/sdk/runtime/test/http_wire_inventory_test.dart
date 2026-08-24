import 'dart:convert';
import 'dart:io' as io;

import 'package:opencode_sdk/opencode_sdk.dart';
import 'package:test/test.dart';

void main() {
  test(
    'contract-derived wire inventory covers every audited definition',
    () async {
      final inventory =
          jsonDecode(
                await io.File('tool/http_wire_inventory.json').readAsString(),
              )
              as Map<String, dynamic>;
      final counts = inventory['counts'] as Map<String, dynamic>;

      expect(counts['optionalRequestBodies'], 46);
      expect(counts['pathParameters'], 99);
      expect(counts['scalarUnionQueryParameters'], 3);
      expect(counts['nullableOmittedQueryParameters'], 1);
      expect(counts['errorResponses'], 332);
      expect(counts['operationsWithErrors'], 186);
      expect(counts['errorDecoders'], 332);
      expect(
        (counts['generatedModelErrorDecoders'] as int) +
            (counts['generatedUnionWrapperErrorDecoders'] as int) +
            (counts['losslessDescriptorErrorDecoders'] as int),
        332,
      );
      expect(counts['losslessDescriptorErrorDecoders'], 0);
      expect(openCodeErrorContracts, hasLength(332));
      expect(
        openCodeErrorContracts.values.every(
          (contract) => contract.payloadType != 'Object',
        ),
        isTrue,
      );
      expect(
        openCodeErrorContracts.keys.map((key) => key.operationId).toSet(),
        hasLength(186),
      );

      final inventoryKeys = (inventory['errorResponses'] as List)
          .cast<Map<String, dynamic>>()
          .map(
            (item) => OpenCodeErrorContractKey(
              operationId: item['operationId'] as String,
              status: item['status'] as int,
              mediaType: item['mediaType'] as String,
              schemaJson: item['schemaJson'] as String,
            ),
          )
          .toSet();
      expect(openCodeErrorContracts.keys.toSet(), inventoryKeys);
      final decoderContracts = (inventory['errorDecoderContracts'] as List)
          .cast<Map<String, dynamic>>();
      expect(decoderContracts, hasLength(332));
      expect(
        decoderContracts.every(
          (item) =>
              item['payloadType'] != 'Object' &&
              item['decoderKind'] != 'losslessDescriptorWrapper',
        ),
        isTrue,
      );
    },
  );

  test('generated API sources contain complete wire fixes', () async {
    final sources = await io.Directory('lib/src/api')
        .list()
        .where(
          (entity) => entity is io.File && entity.path.endsWith('_api.dart'),
        )
        .cast<io.File>()
        .asyncMap((file) => file.readAsString())
        .toList();
    final source = sources.join('\n');

    expect('const _operationId ='.allMatches(source), hasLength(188));
    expect('encodeOpenCodePathSegment('.allMatches(source), hasLength(99));
    expect('Uri.encodeComponent('.allMatches(source), isEmpty);
    expect(
      RegExp(r'includeBody: [^\n]+ != null').allMatches(source),
      hasLength(46),
    );
    final wireSource = await io.File('lib/src/http/wire.dart').readAsString();
    expect(wireSource, contains('requestOptions.contentType = null;'));
  });
}
