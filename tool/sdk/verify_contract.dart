import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

const _httpMethods = {
  'get',
  'put',
  'post',
  'delete',
  'options',
  'head',
  'patch',
  'trace',
};

int _countKeys(Object? value, String key) {
  if (value is List) {
    return value.fold(0, (count, item) => count + _countKeys(item, key));
  }
  if (value is! Map) return 0;
  var count = value.containsKey(key) ? 1 : 0;
  for (final child in value.values) {
    count += _countKeys(child, key);
  }
  return count;
}

const _sha256Constants = <int>[
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

int _rotateRight(int value, int count) =>
    ((value >> count) | (value << (32 - count))) & 0xffffffff;

String _sha256Bytes(List<int> input) {
  final bitLength = input.length * 8;
  final paddedLength = ((input.length + 9 + 63) ~/ 64) * 64;
  final bytes = Uint8List(paddedLength)..setRange(0, input.length, input);
  bytes[input.length] = 0x80;
  for (var index = 0; index < 8; index++) {
    bytes[paddedLength - 1 - index] = (bitLength >> (index * 8)) & 0xff;
  }

  final hash = <int>[
    0x6a09e667,
    0xbb67ae85,
    0x3c6ef372,
    0xa54ff53a,
    0x510e527f,
    0x9b05688c,
    0x1f83d9ab,
    0x5be0cd19,
  ];
  final words = Uint32List(64);
  for (var offset = 0; offset < bytes.length; offset += 64) {
    for (var index = 0; index < 16; index++) {
      final byte = offset + index * 4;
      words[index] =
          (bytes[byte] << 24) |
          (bytes[byte + 1] << 16) |
          (bytes[byte + 2] << 8) |
          bytes[byte + 3];
    }
    for (var index = 16; index < 64; index++) {
      final s0 =
          _rotateRight(words[index - 15], 7) ^
          _rotateRight(words[index - 15], 18) ^
          (words[index - 15] >> 3);
      final s1 =
          _rotateRight(words[index - 2], 17) ^
          _rotateRight(words[index - 2], 19) ^
          (words[index - 2] >> 10);
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
      final sum1 =
          _rotateRight(e, 6) ^ _rotateRight(e, 11) ^ _rotateRight(e, 25);
      final choice = (e & f) ^ ((~e) & g);
      final temp1 =
          (h + sum1 + choice + _sha256Constants[index] + words[index]) &
          0xffffffff;
      final sum0 =
          _rotateRight(a, 2) ^ _rotateRight(a, 13) ^ _rotateRight(a, 22);
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

Future<String> _sha256(String path) async =>
    _sha256Bytes(await File(path).readAsBytes());

Future<String> _treeSha256(String path) async {
  final root = Directory(path).absolute;
  final records = <String>[];

  Future<void> visit(Directory directory, String prefix) async {
    final entities = await directory.list(followLinks: false).toList();
    entities.sort((left, right) => left.path.compareTo(right.path));
    for (final entity in entities) {
      final name = entity.path.substring(directory.path.length + 1);
      final relative = prefix.isEmpty ? name : '$prefix/$name';
      if (prefix.isEmpty && (name == '.dart_tool' || name == 'build')) {
        continue;
      }
      if (entity is Directory) {
        records.add('directory\u0000$relative');
        await visit(entity, relative);
      } else if (entity is File) {
        records.add(
          'file\u0000$relative\u0000${await entity.length()}\u0000${await _sha256(entity.path)}',
        );
      } else if (entity is Link) {
        records.add('link\u0000$relative\u0000${await entity.target()}');
      }
    }
  }

  await visit(root, '');
  return _sha256Bytes(utf8.encode(records.join('\n')));
}

Future<void> main(List<String> arguments) async {
  if (arguments.length == 3 && arguments[0] == '--verify-sha256') {
    final actual = await _sha256(arguments[1]);
    if (actual != arguments[2].toLowerCase()) {
      stderr.writeln(
        '${arguments[1]}: expected SHA-256 ${arguments[2]}, found $actual',
      );
      exitCode = 1;
      return;
    }
    stdout.writeln(actual);
    return;
  }
  if (arguments.length == 2 && arguments[0] == '--tree-sha256') {
    stdout.writeln(await _treeSha256(arguments[1]));
    return;
  }
  if (arguments.length < 2 || arguments.length > 3) {
    stderr.writeln(
      'Usage: dart run tool/sdk/verify_contract.dart '
      '<openapi> <manifest> [before|after]',
    );
    exitCode = 64;
    return;
  }

  final mode = arguments.length == 3 ? arguments[2] : 'before';
  if (mode != 'before' && mode != 'after') {
    throw ArgumentError.value(mode, 'mode', 'must be before or after');
  }
  final document =
      jsonDecode(await File(arguments[0]).readAsString())
          as Map<String, dynamic>;
  final manifest =
      jsonDecode(await File(arguments[1]).readAsString())
          as Map<String, dynamic>;
  final normalization = manifest['normalization'] as Map<String, dynamic>;
  final inventory = normalization[mode] as Map<String, dynamic>;
  final expected = inventory['counts'] as Map<String, dynamic>;
  final paths = document['paths'] as Map<String, dynamic>;
  final schemas =
      (document['components'] as Map<String, dynamic>)['schemas']
          as Map<String, dynamic>;
  final operationIds = <String>[];
  final operations = <Map<String, dynamic>>[];

  for (final path in paths.values.cast<Map<String, dynamic>>()) {
    for (final entry in path.entries) {
      if (!_httpMethods.contains(entry.key)) continue;
      final operation = entry.value as Map<String, dynamic>;
      final operationId = operation['operationId'];
      if (operationId is! String || operationId.isEmpty) {
        throw FormatException('An HTTP operation has no operationId.');
      }
      operationIds.add(operationId);
      operations.add(operation);
    }
  }

  final duplicates = <String>{};
  final seen = <String>{};
  for (final operationId in operationIds) {
    if (!seen.add(operationId)) duplicates.add(operationId);
  }
  if (duplicates.isNotEmpty) {
    throw FormatException('Duplicate operationIds: ${duplicates.join(', ')}');
  }

  var parameterCount = 0;
  var deepObjectLocations = 0;
  var bracketedLocations = 0;
  var requestBodies = 0;
  var requiredRequestBodies = 0;
  for (final operation in operations) {
    final parameters = operation['parameters'];
    if (parameters is List) {
      parameterCount += parameters.length;
      for (final parameter in parameters.whereType<Map>()) {
        if (parameter['name'] == 'location' &&
            parameter['style'] == 'deepObject') {
          deepObjectLocations++;
        }
        if (parameter['name'] == 'location[directory]' ||
            parameter['name'] == 'location[workspace]') {
          bracketedLocations++;
        }
      }
    }
    final requestBody = operation['requestBody'];
    if (requestBody is Map) {
      requestBodies++;
      if (requestBody['required'] == true) requiredRequestBodies++;
    }
  }

  var unionSchemas = 0;
  var unionBranches = 0;
  void countUnions(Object? value) {
    if (value is List) {
      for (final item in value) {
        countUnions(item);
      }
      return;
    }
    if (value is! Map) return;
    var isUnion = false;
    for (final keyword in const ['anyOf', 'oneOf']) {
      final branches = value[keyword];
      if (branches is List) {
        isUnion = true;
        unionBranches += branches.length;
      }
    }
    if (isUnion) unionSchemas++;
    for (final child in value.values) {
      countUnions(child);
    }
  }

  countUnions(document);
  final actual = <String, int>{
    'paths': paths.length,
    'operations': operationIds.length,
    'schemas': schemas.length,
    'parameters': parameterCount,
    'rootTags': (document['tags'] as List).length,
    'unionSchemas': unionSchemas,
    'unionBranches': unionBranches,
    'deepObjectLocationParameters': deepObjectLocations,
    'bracketedLocationParameters': bracketedLocations,
    'patternProperties': _countKeys(document, 'patternProperties'),
    'nullableMarkers': _countKeys(document, 'nullable'),
    'xEffectStreamExtensions': _countKeys(document, 'x-effect-stream'),
    'requestBodies': requestBodies,
    'requiredRequestBodies': requiredRequestBodies,
  };
  for (final entry in actual.entries) {
    if (entry.value != expected[entry.key]) {
      throw StateError(
        '$mode ${entry.key}: expected ${expected[entry.key]}, '
        'found ${entry.value}',
      );
    }
  }

  final actualHash = await _sha256(arguments[0]);
  if (actualHash != inventory['sha256']) {
    throw StateError(
      '$mode sha256: expected ${inventory['sha256']}, found $actualHash',
    );
  }
  if (mode == 'before' && actualHash != (manifest['source'] as Map)['sha256']) {
    throw StateError(
      'Canonical source hash does not match normalization input.',
    );
  }

  stdout.writeln(jsonEncode({'mode': mode, 'sha256': actualHash, ...actual}));
}
