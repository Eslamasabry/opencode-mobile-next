import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

const contractHttpMethods = <String>{
  'get',
  'put',
  'post',
  'delete',
  'options',
  'head',
  'patch',
  'trace',
};

String canonicalJson(Object? value) {
  Object? sort(Object? item) {
    if (item is List) return item.map(sort).toList(growable: false);
    if (item is Map) {
      final keys = item.keys.cast<String>().toList()..sort();
      return <String, Object?>{for (final key in keys) key: sort(item[key])};
    }
    return item;
  }

  return jsonEncode(sort(value));
}

String canonicalHash(Object? value) =>
    sha256Hex(utf8.encode(canonicalJson(value)));

String sha256Hex(List<int> input) {
  const initial = <int>[
    0x6a09e667,
    0xbb67ae85,
    0x3c6ef372,
    0xa54ff53a,
    0x510e527f,
    0x9b05688c,
    0x1f83d9ab,
    0x5be0cd19,
  ];
  const constants = <int>[
    0x428a2f98,
    0x71374491,
    0xb5c0fbcf,
    0xe9b5dba5,
    0x3956c25b,
    0x59f111f1,
    0x923f82a4,
    0xab1c5ed5,
    0xd807aa98,
    0x12835b01,
    0x243185be,
    0x550c7dc3,
    0x72be5d74,
    0x80deb1fe,
    0x9bdc06a7,
    0xc19bf174,
    0xe49b69c1,
    0xefbe4786,
    0x0fc19dc6,
    0x240ca1cc,
    0x2de92c6f,
    0x4a7484aa,
    0x5cb0a9dc,
    0x76f988da,
    0x983e5152,
    0xa831c66d,
    0xb00327c8,
    0xbf597fc7,
    0xc6e00bf3,
    0xd5a79147,
    0x06ca6351,
    0x14292967,
    0x27b70a85,
    0x2e1b2138,
    0x4d2c6dfc,
    0x53380d13,
    0x650a7354,
    0x766a0abb,
    0x81c2c92e,
    0x92722c85,
    0xa2bfe8a1,
    0xa81a664b,
    0xc24b8b70,
    0xc76c51a3,
    0xd192e819,
    0xd6990624,
    0xf40e3585,
    0x106aa070,
    0x19a4c116,
    0x1e376c08,
    0x2748774c,
    0x34b0bcb5,
    0x391c0cb3,
    0x4ed8aa4a,
    0x5b9cca4f,
    0x682e6ff3,
    0x748f82ee,
    0x78a5636f,
    0x84c87814,
    0x8cc70208,
    0x90befffa,
    0xa4506ceb,
    0xbef9a3f7,
    0xc67178f2,
  ];
  final bytes = BytesBuilder(copy: false)..add(input);
  final bitLength = input.length * 8;
  bytes.addByte(0x80);
  while (bytes.length % 64 != 56) {
    bytes.addByte(0);
  }
  final lengthBytes = Uint8List(8);
  for (var index = 0; index < 8; index++) {
    lengthBytes[7 - index] = (bitLength >> (index * 8)) & 0xff;
  }
  bytes.add(lengthBytes);
  final padded = bytes.takeBytes();
  final hash = List<int>.from(initial);
  final words = List<int>.filled(64, 0);
  int rotate(int value, int count) =>
      ((value >>> count) | (value << (32 - count))) & 0xffffffff;

  for (var offset = 0; offset < padded.length; offset += 64) {
    for (var index = 0; index < 16; index++) {
      final start = offset + index * 4;
      words[index] =
          (padded[start] << 24) |
          (padded[start + 1] << 16) |
          (padded[start + 2] << 8) |
          padded[start + 3];
    }
    for (var index = 16; index < 64; index++) {
      final s0 =
          rotate(words[index - 15], 7) ^
          rotate(words[index - 15], 18) ^
          (words[index - 15] >>> 3);
      final s1 =
          rotate(words[index - 2], 17) ^
          rotate(words[index - 2], 19) ^
          (words[index - 2] >>> 10);
      words[index] =
          (words[index - 16] + s0 + words[index - 7] + s1) & 0xffffffff;
    }
    var a = hash[0];
    var b = hash[1];
    var c = hash[2];
    var d = hash[3];
    var e = hash[4];
    var f = hash[5];
    var g = hash[6];
    var h = hash[7];
    for (var index = 0; index < 64; index++) {
      final sum1 = rotate(e, 6) ^ rotate(e, 11) ^ rotate(e, 25);
      final choice = (e & f) ^ ((~e) & g);
      final temp1 =
          (h + sum1 + choice + constants[index] + words[index]) & 0xffffffff;
      final sum0 = rotate(a, 2) ^ rotate(a, 13) ^ rotate(a, 22);
      final majority = (a & b) ^ (a & c) ^ (b & c);
      final temp2 = (sum0 + majority) & 0xffffffff;
      h = g;
      g = f;
      f = e;
      e = (d + temp1) & 0xffffffff;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) & 0xffffffff;
    }
    hash[0] = (hash[0] + a) & 0xffffffff;
    hash[1] = (hash[1] + b) & 0xffffffff;
    hash[2] = (hash[2] + c) & 0xffffffff;
    hash[3] = (hash[3] + d) & 0xffffffff;
    hash[4] = (hash[4] + e) & 0xffffffff;
    hash[5] = (hash[5] + f) & 0xffffffff;
    hash[6] = (hash[6] + g) & 0xffffffff;
    hash[7] = (hash[7] + h) & 0xffffffff;
  }
  return hash.map((word) => word.toRadixString(16).padLeft(8, '0')).join();
}

String pointerPart(String value) =>
    value.replaceAll('~', '~0').replaceAll('/', '~1');

String dartTypeNameForContract(String source) {
  final result = StringBuffer();
  for (final word in source.split(RegExp(r'[^A-Za-z0-9]+'))) {
    if (word.isEmpty) continue;
    result
      ..write(word[0].toUpperCase())
      ..write(word.substring(1));
  }
  final value = result.toString();
  if (value == 'Part') return 'ModelPart';
  return value.isEmpty ? 'Model' : value;
}

String dartFileNameForContract(String className) => className
    .replaceAll(RegExp(r'[^A-Za-z0-9]+'), '_')
    .replaceAllMapped(
      RegExp(r'([A-Z]+)([A-Z][a-z])'),
      (match) => '${match[1]}_${match[2]}',
    )
    .replaceAllMapped(
      RegExp(r'([a-z0-9])([A-Z])'),
      (match) => '${match[1]}_${match[2]}',
    )
    .toLowerCase();

class GeneratedOperationLink {
  const GeneratedOperationLink({
    required this.operationId,
    required this.file,
    required this.className,
    required this.methodName,
    required this.path,
    required this.httpMethod,
    required this.dartDeprecated,
  });

  final String operationId;
  final String file;
  final String className;
  final String methodName;
  final String path;
  final String httpMethod;
  final bool dartDeprecated;

  Map<String, Object?> toJson() => {
    'file': file,
    'class': className,
    'method': methodName,
    'operationIdMetadata': operationId,
    'methodPath': path,
    'httpMethod': httpMethod,
    'dartDeprecated': dartDeprecated,
  };
}

Future<Map<String, GeneratedOperationLink>> parseGeneratedOperationLinks(
  Directory package,
) async {
  final apiDirectory = Directory('${package.path}/lib/src/api');
  final files = await apiDirectory
      .list()
      .where((entity) => entity is File && entity.path.endsWith('_api.dart'))
      .cast<File>()
      .toList();
  files.sort((left, right) => left.path.compareTo(right.path));
  final result = <String, GeneratedOperationLink>{};
  final methodPattern = RegExp(
    r'Future<Response<[^\r\n]+>>\s+([A-Za-z0-9_]+)\s*\(',
  );
  final operationPattern = RegExp(r"const _operationId = r?'([^']+)';");
  for (final file in files) {
    final source = await file.readAsString();
    final classMatch = RegExp(
      r'class\s+([A-Za-z0-9_]+Api)\s*\{',
    ).firstMatch(source);
    if (classMatch == null) throw StateError('No API class in ${file.path}.');
    final methods = methodPattern.allMatches(source).toList();
    final operations = operationPattern.allMatches(source).toList();
    for (var index = 0; index < operations.length; index++) {
      final operation = operations[index];
      final candidates = methods.where(
        (method) => method.start < operation.start,
      );
      if (candidates.isEmpty) {
        throw StateError('No Dart method for ${operation.group(1)}.');
      }
      final method = candidates.last;
      final end = index + 1 < operations.length
          ? operations[index + 1].start
          : source.length;
      final body = source.substring(operation.start, end);
      final path = RegExp(
        r"(?:final|const) _path = r?'([^']+)'",
      ).firstMatch(body);
      final httpMethod = RegExp(r"method: r?'([A-Z]+)'").firstMatch(body);
      if (path == null || httpMethod == null) {
        throw StateError(
          'Incomplete generated linkage for ${operation.group(1)}.',
        );
      }
      final annotationStart = method.start > 240 ? method.start - 240 : 0;
      final annotation = source.substring(annotationStart, method.start);
      final operationId = operation.group(1)!;
      if (result.containsKey(operationId)) {
        throw StateError('Duplicate generated operationId $operationId.');
      }
      result[operationId] = GeneratedOperationLink(
        operationId: operationId,
        file: 'lib/src/api/${file.uri.pathSegments.last}',
        className: classMatch.group(1)!,
        methodName: method.group(1)!,
        path: path.group(1)!,
        httpMethod: httpMethod.group(1)!,
        dartDeprecated: annotation.contains('@Deprecated('),
      );
    }
  }
  return result;
}

Map<String, Object?>? transportReplacement(String operationId) {
  const replacements = <String, List<String>>{
    'global.event': [
      'sse',
      'lib/src/sse/event_streams.dart',
      'OpencodeSdkEventStreams',
      'globalEventStream',
    ],
    'event.subscribe': [
      'sse',
      'lib/src/sse/event_streams.dart',
      'OpencodeSdkEventStreams',
      'eventSubscribeStream',
    ],
    'v2.event.subscribe': [
      'sse',
      'lib/src/sse/event_streams.dart',
      'OpencodeSdkEventStreams',
      'v2EventSubscribeStream',
    ],
    'v2.session.events': [
      'sse',
      'lib/src/sse/event_streams.dart',
      'OpencodeSdkEventStreams',
      'v2SessionEventsStream',
    ],
    'v2.fs.read': [
      'wildcard-path',
      'lib/src/http/filesystem.dart',
      'OpencodeSdkFilesystem',
      'v2FsReadPath',
    ],
  };
  final replacement = replacements[operationId];
  if (replacement == null) return null;
  return {
    'kind': replacement[0],
    'file': replacement[1],
    'declaration': replacement[2],
    'method': replacement[3],
  };
}

String markdownForMatrix(Map<String, dynamic> matrix) {
  final totals = matrix['totals'] as Map<String, dynamic>;
  final provenance = matrix['provenance'] as Map<String, dynamic>;
  final operations = (matrix['operations'] as List)
      .cast<Map<String, dynamic>>();
  final byFile = <String, List<Map<String, dynamic>>>{};
  for (final operation in operations) {
    final generated = operation['generated'] as Map<String, dynamic>;
    byFile.putIfAbsent(generated['file'] as String, () => []).add(operation);
  }
  final output = StringBuffer()
    ..writeln('# OpenCode Dart SDK contract matrix')
    ..writeln()
    ..writeln(
      'Generated from `${provenance['canonicalFile']}` at upstream commit '
      '`${provenance['upstreamCommit']}` (SHA-256 `${provenance['canonicalSha256']}`).',
    )
    ..writeln()
    ..writeln(
      '**Totals:** ${totals['paths']} paths, ${totals['operations']} operations '
      '(${totals['GET']} GET, ${totals['POST']} POST, ${totals['DELETE']} DELETE, '
      '${totals['PATCH']} PATCH, ${totals['PUT']} PUT), ${totals['parameters']} '
      'effective parameters, ${totals['requestSchemaSlots']} request schema slots, '
      '${totals['responseObjects']} response objects, '
      '${totals['responseSchemaSlots']} response schema slots, ${totals['schemas']} '
      'component schemas, ${totals['eventVariants']} Event variants, and '
      '${totals['enumSites']} enum sites / ${totals['enumEntries']} entries.',
    )
    ..writeln()
    ..writeln(
      'Machine-readable component/schema hashes, exact parameters, request and '
      'response media, Event discriminators, enums, and runtime replacement links '
      'are in `contracts/opencode-sdk-matrix.json`.',
    )
    ..writeln()
    ..writeln(
      'Schema hashes are SHA-256 over canonical JSON with recursively sorted '
      'object keys; array order and JSON scalar types are preserved.',
    )
    ..writeln()
    ..writeln('## Explicit canonical override')
    ..writeln()
    ..writeln(
      '`v2.session.history` declares `limit` and `after` as strings in the '
      'canonical OpenAPI, while the upstream generated JS SDK build patches '
      'both to numbers. The Dart parity target is the upstream generated SDK, '
      'so Dart emits nullable `num` query parameters. Both descriptors and '
      'their hashes are retained in the machine-readable matrix; they are not '
      'claimed to be identical.',
    );
  final files = byFile.keys.toList()..sort();
  for (final file in files) {
    output
      ..writeln()
      ..writeln('## `$file`')
      ..writeln()
      ..writeln(
        '| Dart class | Function | operationId | HTTP contract | Tags | Deprecated | Transport |',
      )
      ..writeln('|---|---|---|---|---|---:|---|');
    for (final operation in byFile[file]!) {
      final generated = operation['generated'] as Map<String, dynamic>;
      final replacement =
          operation['transportReplacement'] as Map<String, dynamic>?;
      final transport = replacement == null
          ? 'Dio'
          : '${replacement['kind']}: `${replacement['method']}`';
      output.writeln(
        '| `${generated['class']}` | `${generated['method']}()` | '
        '`${operation['operationId']}` | `${operation['method']} '
        '${operation['path']}` | ${(operation['tags'] as List).join(', ')} | '
        '${operation['deprecated'] == true ? 'yes' : 'no'} | $transport |',
      );
    }
  }
  return output.toString();
}
