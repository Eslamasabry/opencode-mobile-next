// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_shell_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionShellRequest _$SessionShellRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionShellRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['agent', 'command']);
      final val = SessionShellRequest(
        messageID: $checkedConvert('messageID', (v) => v as String?),
        agent: $checkedConvert('agent', (v) => v as String),
        model: $checkedConvert(
          'model',
          (v) => v == null
              ? null
              : SessionPromptAsyncRequestModel.fromJson(
                  v as Map<String, dynamic>,
                ),
        ),
        command: $checkedConvert('command', (v) => v as String),
      );
      return val;
    });

Map<String, dynamic> _$SessionShellRequestToJson(
  SessionShellRequest instance,
) => <String, dynamic>{
  'messageID': ?instance.messageID,
  'agent': instance.agent,
  'model': ?instance.model?.toJson(),
  'command': instance.command,
};
