// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_command_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionCommandRequest _$SessionCommandRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionCommandRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['arguments', 'command']);
  final val = SessionCommandRequest(
    messageID: $checkedConvert('messageID', (v) => v as String?),
    agent: $checkedConvert('agent', (v) => v as String?),
    model: $checkedConvert('model', (v) => v as String?),
    arguments: $checkedConvert('arguments', (v) => v as String),
    command: $checkedConvert('command', (v) => v as String),
    variant: $checkedConvert('variant', (v) => v as String?),
    parts: $checkedConvert(
      'parts',
      (v) => (v as List<dynamic>?)
          ?.map(
            (e) => SessionCommandRequestPartsInner.fromJson(
              e as Map<String, dynamic>,
            ),
          )
          .toList(),
    ),
  );
  return val;
});

Map<String, dynamic> _$SessionCommandRequestToJson(
  SessionCommandRequest instance,
) => <String, dynamic>{
  'messageID': ?instance.messageID,
  'agent': ?instance.agent,
  'model': ?instance.model,
  'arguments': instance.arguments,
  'command': instance.command,
  'variant': ?instance.variant,
  'parts': ?instance.parts?.map((e) => e.toJson()).toList(),
};
