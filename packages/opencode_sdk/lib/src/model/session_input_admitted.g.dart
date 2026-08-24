// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_input_admitted.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionInputAdmitted _$SessionInputAdmittedFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('SessionInputAdmitted', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const [
      'admittedSeq',
      'id',
      'sessionID',
      'prompt',
      'delivery',
      'timeCreated',
    ],
  );
  final val = SessionInputAdmitted(
    admittedSeq: $checkedConvert('admittedSeq', (v) => (v as num).toInt()),
    id: $checkedConvert('id', (v) => v as String),
    sessionID: $checkedConvert('sessionID', (v) => v as String),
    prompt: $checkedConvert(
      'prompt',
      (v) => Prompt.fromJson(v as Map<String, dynamic>),
    ),
    delivery: $checkedConvert(
      'delivery',
      (v) => $enumDecode(
        _$SessionInputAdmittedDeliveryEnumEnumMap,
        v,
        unknownValue: SessionInputAdmittedDeliveryEnum.unknownDefaultOpenApi,
      ),
    ),
    timeCreated: $checkedConvert('timeCreated', (v) => v as num),
    promotedSeq: $checkedConvert('promotedSeq', (v) => (v as num?)?.toInt()),
  );
  return val;
});

Map<String, dynamic> _$SessionInputAdmittedToJson(
  SessionInputAdmitted instance,
) => <String, dynamic>{
  'admittedSeq': instance.admittedSeq,
  'id': instance.id,
  'sessionID': instance.sessionID,
  'prompt': instance.prompt.toJson(),
  'delivery': _$SessionInputAdmittedDeliveryEnumEnumMap[instance.delivery]!,
  'timeCreated': instance.timeCreated,
  'promotedSeq': ?instance.promotedSeq,
};

const _$SessionInputAdmittedDeliveryEnumEnumMap = {
  SessionInputAdmittedDeliveryEnum.steer: 'steer',
  SessionInputAdmittedDeliveryEnum.queue: 'queue',
  SessionInputAdmittedDeliveryEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};
