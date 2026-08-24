// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'config_compaction.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

ConfigCompaction _$ConfigCompactionFromJson(Map<String, dynamic> json) =>
    $checkedCreate(
      'ConfigCompaction',
      json,
      ($checkedConvert) {
        final val = ConfigCompaction(
          auto: $checkedConvert('auto', (v) => v as bool?),
          prune: $checkedConvert('prune', (v) => v as bool?),
          tailTurns: $checkedConvert('tail_turns', (v) => (v as num?)?.toInt()),
          preserveRecentTokens: $checkedConvert(
            'preserve_recent_tokens',
            (v) => (v as num?)?.toInt(),
          ),
          reserved: $checkedConvert('reserved', (v) => (v as num?)?.toInt()),
        );
        return val;
      },
      fieldKeyMap: const {
        'tailTurns': 'tail_turns',
        'preserveRecentTokens': 'preserve_recent_tokens',
      },
    );

Map<String, dynamic> _$ConfigCompactionToJson(ConfigCompaction instance) =>
    <String, dynamic>{
      'auto': ?instance.auto,
      'prune': ?instance.prune,
      'tail_turns': ?instance.tailTurns,
      'preserve_recent_tokens': ?instance.preserveRecentTokens,
      'reserved': ?instance.reserved,
    };
