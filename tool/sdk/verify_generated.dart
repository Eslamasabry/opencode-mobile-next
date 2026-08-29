import 'dart:convert';
import 'dart:io';

Future<void> main(List<String> arguments) async {
  if (arguments.length != 2) {
    stderr.writeln(
      'Usage: dart run tool/sdk/verify_generated.dart <package> <manifest>',
    );
    exitCode = 64;
    return;
  }

  final package = Directory(arguments[0]);
  final manifest =
      jsonDecode(await File(arguments[1]).readAsString())
          as Map<String, dynamic>;
  final expected =
      (manifest['counts'] as Map<String, dynamic>)['operations'] as int;
  final counts = manifest['counts'] as Map<String, dynamic>;
  final generator = manifest['generator'] as Map<String, dynamic>;
  final httpWireAudit = manifest['httpWireAudit'] as Map<String, dynamic>;
  final apiDirectory = Directory('${package.path}/lib/src/api');
  final modelDirectory = Directory('${package.path}/lib/src/model');
  var operations = 0;
  var apiFiles = 0;
  var modelSourceFiles = 0;
  var serializerFiles = 0;
  var locationDirectoryWireKeys = 0;
  var locationWorkspaceWireKeys = 0;
  var encodedPathParameters = 0;
  var operationMetadata = 0;
  var optionalBodyOmissions = 0;
  final apiSources = <String>[];

  await for (final entity in apiDirectory.list()) {
    if (entity is! File || !entity.path.endsWith('_api.dart')) continue;
    apiFiles++;
    final source = await entity.readAsString();
    apiSources.add(source);
    operations += 'Future<Response<'.allMatches(source).length;
    locationDirectoryWireKeys += r"r'location[directory]'"
        .allMatches(source)
        .length;
    locationWorkspaceWireKeys += r"r'location[workspace]'"
        .allMatches(source)
        .length;
    encodedPathParameters += 'encodeOpenCodePathSegment('
        .allMatches(source)
        .length;
    operationMetadata += 'const _operationId ='.allMatches(source).length;
    optionalBodyOmissions += RegExp(
      r'includeBody: [^\n]+ != null',
    ).allMatches(source).length;
  }

  if (operations != expected) {
    throw StateError(
      'Generated operation count: expected $expected, found $operations',
    );
  }
  if (locationDirectoryWireKeys != 32 || locationWorkspaceWireKeys != 32) {
    throw StateError(
      'Generated location wire keys: expected 32 each, found '
      '$locationDirectoryWireKeys directory and $locationWorkspaceWireKeys workspace',
    );
  }
  if (encodedPathParameters != 99 ||
      operationMetadata != expected ||
      optionalBodyOmissions != 46) {
    throw StateError(
      'Generated HTTP wire inventory mismatch: $encodedPathParameters encoded '
      'paths, $operationMetadata operation IDs, $optionalBodyOmissions optional '
      'body omissions.',
    );
  }

  await for (final entity in modelDirectory.list()) {
    if (entity is! File || !entity.path.endsWith('.dart')) continue;
    if (entity.path.endsWith('.g.dart')) {
      serializerFiles++;
    } else {
      modelSourceFiles++;
    }
  }

  final actual = {
    'operations': operations,
    'generatedApiFiles': apiFiles,
    'generatedModelSourceFiles': modelSourceFiles,
    'generatedSerializerFiles': serializerFiles,
  };
  for (final entry in actual.entries) {
    if (entry.value != counts[entry.key]) {
      throw StateError(
        '${entry.key}: expected ${counts[entry.key]}, found ${entry.value}',
      );
    }
  }

  final sessionsApi = await File(
    '${apiDirectory.path}/sessions_api.dart',
  ).readAsString();
  if (!RegExp(
    r'v2SessionHistory\(\{[\s\S]*?required String sessionID,\s*num\? limit,\s*num\? after,',
  ).hasMatch(sessionsApi)) {
    throw StateError(
      'v2.session.history did not generate numeric limit/after.',
    );
  }

  final expectedCollisionModels = {
    'model_v2_capabilities.dart': 'ModelV2Capabilities',
    'event_tui_command_execute_schema2.dart': 'EventTuiCommandExecuteSchema2',
    'event_tui_prompt_append_schema2.dart': 'EventTuiPromptAppendSchema2',
    'event_tui_session_select_schema2.dart': 'EventTuiSessionSelectSchema2',
    'event_tui_toast_show_schema2.dart': 'EventTuiToastShowSchema2',
    'question_rejected_schema2.dart': 'QuestionRejectedSchema2',
    'question_replied_schema2.dart': 'QuestionRepliedSchema2',
    'session_status_schema2.dart': 'SessionStatusSchema2',
    'provider_api_model.dart': 'ProviderApiModel',
  };
  for (final entry in expectedCollisionModels.entries) {
    final source = await File(
      '${modelDirectory.path}/${entry.key}',
    ).readAsString();
    if (!source.contains('class ${entry.value} ')) {
      throw StateError('Missing deterministic collision model ${entry.value}.');
    }
  }

  final unionInventory =
      jsonDecode(
            await File(
              '${package.path}/tool/union_inventory.json',
            ).readAsString(),
          )
          as Map<String, dynamic>;
  final wrappers = (unionInventory['losslessRawJsonUnions'] as List)
      .cast<String>();
  final residual = (unionInventory['residualLossyUnions'] as List)
      .cast<String>();
  if (wrappers.length != generator['losslessRawJsonUnionComponents'] ||
      residual.isNotEmpty) {
    throw StateError(
      'Union coverage mismatch: ${wrappers.length} wrappers, '
      '${residual.length} residual lossy unions.',
    );
  }
  const requiredWrappers = {
    'ToolState',
    'Event',
    'V2Event',
    'SessionDurableEvent',
    'Auth',
    'ModelPart',
    'Message',
    'MCPStatus',
    'SessionStatus',
  };
  if (!wrappers.toSet().containsAll(requiredWrappers)) {
    throw StateError('Required union wrappers are missing.');
  }

  final additionalPropertiesInventory =
      jsonDecode(
            await File(
              '${package.path}/tool/additional_properties_inventory.json',
            ).readAsString(),
          )
          as Map<String, dynamic>;
  final mixedAdditionalProperties =
      (additionalPropertiesInventory['losslessMixedAdditionalProperties']
              as List)
          .cast<Map<String, dynamic>>();
  final residualAdditionalProperties =
      (additionalPropertiesInventory['residualLossyAdditionalProperties']
              as List)
          .cast<String>();
  if (mixedAdditionalProperties.length !=
          generator['losslessMixedAdditionalPropertiesSites'] ||
      residualAdditionalProperties.isNotEmpty ||
      generator['residualLossyAdditionalPropertiesSites'] != 0) {
    throw StateError(
      'Mixed additionalProperties coverage mismatch: '
      '${mixedAdditionalProperties.length} lossless sites, '
      '${residualAdditionalProperties.length} residual lossy sites.',
    );
  }
  const expectedMixedAdditionalProperties = <String, String>{
    '#/components/schemas/AgentConfig': 'agent_config.dart',
    '#/components/schemas/ProviderConfig/properties/options':
        'provider_config_options.dart',
    '#/components/schemas/ProviderConfig/properties/models/additionalProperties/properties/variants/additionalProperties':
        'provider_config_models_value_variants_value.dart',
    '#/components/schemas/Config/properties/mode': 'config_mode.dart',
    '#/components/schemas/Config/properties/agent': 'config_agent.dart',
  };
  final inventoriedLocations = {
    for (final item in mixedAdditionalProperties) item['location'] as String,
  };
  if (!inventoriedLocations.containsAll(
        expectedMixedAdditionalProperties.keys,
      ) ||
      !inventoriedLocations.contains(
        '#/components/schemas/PermissionConfig/anyOf/1',
      )) {
    throw StateError('Required mixed additionalProperties sites are missing.');
  }
  for (final entry in expectedMixedAdditionalProperties.entries) {
    final source = await File(
      '${modelDirectory.path}/${entry.value}',
    ).readAsString();
    if (!source.contains(
          'get additionalProperties => _additionalProperties;',
        ) ||
        !source.contains('if (!knownKeys.contains(entry.key))') ||
        !source.contains('..._\$') ||
        !source.contains('ToJson(this)')) {
      throw StateError(
        '${entry.key} no longer preserves mixed additionalProperties.',
      );
    }
  }
  final permissionSource = await File(
    '${modelDirectory.path}/permission_config.dart',
  ).readAsString();
  if (!permissionSource.contains('class PermissionConfig implements') ||
      !permissionSource.contains(r'\"additionalProperties\":{\"\$ref\"')) {
    throw StateError(
      'PermissionConfig no longer preserves its mixed additionalProperties branch.',
    );
  }
  final publicLibrary = await File(
    '${package.path}/lib/opencode_sdk.dart',
  ).readAsString();
  if (!publicLibrary.contains("src/sse/event_streams.dart")) {
    throw StateError('The regeneration-safe SSE runtime is not exported.');
  }
  if (!publicLibrary.contains("src/http/errors.dart") ||
      !publicLibrary.contains("src/http/filesystem.dart")) {
    throw StateError('The regeneration-safe HTTP runtime is not exported.');
  }

  final wireInventory =
      jsonDecode(
            await File(
              '${package.path}/tool/http_wire_inventory.json',
            ).readAsString(),
          )
          as Map<String, dynamic>;
  final wireCounts = wireInventory['counts'] as Map<String, dynamic>;
  const expectedWireCounts = <String, int>{
    'optionalRequestBodies': 46,
    'pathParameters': 99,
    'scalarUnionQueryParameters': 3,
    'nullableOmittedQueryParameters': 1,
    'errorResponses': 332,
    'operationsWithErrors': 186,
  };
  for (final entry in expectedWireCounts.entries) {
    if (wireCounts[entry.key] != entry.value) {
      throw StateError(
        '${entry.key}: expected ${entry.value}, found ${wireCounts[entry.key]}',
      );
    }
  }
  if (wireCounts['optionalRequestBodies'] !=
          httpWireAudit['optionalRequestBodiesOmittedWhenNull'] ||
      wireCounts['optionalRequestBodies'] !=
          httpWireAudit['optionalRequestBodyContentTypesRemovedWhenOmitted'] ||
      wireCounts['pathParameters'] !=
          httpWireAudit['rfc3986PathSegmentSubstitutions'] ||
      wireCounts['errorResponses'] !=
          httpWireAudit['declaredNon2xxResponses'] ||
      wireCounts['operationsWithErrors'] !=
          httpWireAudit['operationsWithDeclaredNon2xxResponses']) {
    throw StateError('The manifest HTTP wire audit does not match generation.');
  }
  final manifestErrorRegistry =
      httpWireAudit['errorDecoderRegistry'] as Map<String, dynamic>;
  if (wireCounts['errorDecoders'] != manifestErrorRegistry['entries'] ||
      wireCounts['generatedModelErrorDecoders'] !=
          manifestErrorRegistry['generatedModelDecoders'] ||
      wireCounts['generatedUnionWrapperErrorDecoders'] !=
          manifestErrorRegistry['generatedLosslessUnionWrapperDecoders'] ||
      wireCounts['losslessDescriptorErrorDecoders'] !=
          manifestErrorRegistry['losslessDescriptorWrapperDecoders'] ||
      manifestErrorRegistry['plainObjectDecoders'] != 0) {
    throw StateError(
      'The manifest error decoder audit does not match generation.',
    );
  }
  final allApiSource = apiSources.join('\n');
  for (final item
      in (wireInventory['scalarUnionQueryParameters'] as List).cast<Map>()) {
    final name = item['name'] as String;
    if (!RegExp(
      "r'${RegExp.escape(name)}': serializeOpenCodeQueryParameter\\(",
    ).hasMatch(allApiSource)) {
      throw StateError('Union query parameter $name is not scalar-serialized.');
    }
  }
  if (!RegExp(
    r"if \(workspace != null\)\s+r'workspace': serializeOpenCodeQueryParameter\(workspace\)",
  ).hasMatch(sessionsApi)) {
    throw StateError('v2.session.list still sends a null workspace query.');
  }
  final contractsSource = await File(
    '${package.path}/lib/src/http/error_contracts.g.dart',
  ).readAsString();
  if ('OpenCodeErrorContractKey('.allMatches(contractsSource).length != 664) {
    throw StateError('The exact 332-entry error contract registry is missing.');
  }
  if ('payloadType:'.allMatches(contractsSource).length != 332 ||
      !contractsSource.contains('openCodeErrorFallbackOperations')) {
    throw StateError(
      'The complete generated error decoder registry is missing.',
    );
  }
  if (wireCounts['errorDecoders'] != 332 ||
      wireCounts['losslessDescriptorErrorDecoders'] != 0) {
    throw StateError(
      'Declared error decoders are incomplete or use avoidable descriptor fallbacks.',
    );
  }
  const streamingReplacements = <String, String>{
    'global_api.dart': 'globalEvent',
    'event_api.dart': 'eventSubscribe',
    'events_api.dart': 'v2EventSubscribe',
    'sessions_api.dart': 'v2SessionEvents',
  };
  for (final entry in streamingReplacements.entries) {
    final source = await File(
      '${apiDirectory.path}/${entry.key}',
    ).readAsString();
    if (!RegExp(
      "@Deprecated\\('[^']+Stream\\(\\)[^']*'\\)\\s+"
      'Future<Response<[^>]+>>\\s+${entry.value}\\(',
    ).hasMatch(source)) {
      throw StateError('${entry.value} is not marked as an unsafe SSE method.');
    }
  }
  final filesystemSource = await File(
    '${apiDirectory.path}/filesystem_api.dart',
  ).readAsString();
  if (!filesystemSource.contains(
        'The generated wildcard route sends a literal *. '
        'Use OpencodeSdk.v2FsReadPath() instead.',
      ) ||
      !filesystemSource.contains('Future<Response<Uint8List>> v2FsRead(')) {
    throw StateError('v2FsRead is not deprecated in favor of v2FsReadPath.');
  }
  final deserializeSource = await File(
    '${package.path}/lib/src/deserialize.dart',
  ).readAsString();
  for (final item in (unionInventory['covered'] as List).cast<Map>()) {
    final className = item['className'] as String;
    final source = await File('${package.path}/${item['file']}').readAsString();
    if (!source.contains('class $className') ||
        !source.contains('factory $className.fromJson(Object? json)') ||
        !source.contains('static const String openApiSchemaJson')) {
      throw StateError(
        '$className is not a lossless descriptor-backed wrapper.',
      );
    }
    if (RegExp(
      '${RegExp.escape(className)}\\.fromJson\\('
      r'\s*value as Map<String, dynamic>',
    ).hasMatch(deserializeSource)) {
      throw StateError(
        '$className endpoint dispatch still narrows JSON to Map.',
      );
    }
  }

  final activeModel = await File(
    '${modelDirectory.path}/v2_session_active200_response.dart',
  ).readAsString();
  if (!activeModel.contains('final Map<String, SessionActive> data;')) {
    throw StateError('v2.session.active did not generate a typed map.');
  }
  final warpModel = await File(
    '${modelDirectory.path}/experimental_workspace_warp_request.dart',
  ).readAsString();
  final globalSessionModel = await File(
    '${modelDirectory.path}/global_session.dart',
  ).readAsString();
  if (!warpModel.contains("name: r'id', required: true, includeIfNull: true") ||
      !warpModel.contains('final String? id;') ||
      !globalSessionModel.contains(
        "name: r'project', required: true, includeIfNull: true",
      ) ||
      !globalSessionModel.contains('final ProjectSummary? project;')) {
    throw StateError('Required explicit-null unions lost null acceptance.');
  }
  stdout.writeln(jsonEncode(actual));
}
