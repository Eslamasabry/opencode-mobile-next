//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'compaction_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class CompactionPart {
  /// Returns a new [CompactionPart] instance.
  CompactionPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.auto,

    this.overflow,

    this.tailStartId,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: CompactionPartTypeEnum.unknownDefaultOpenApi,
  )
  final CompactionPartTypeEnum type;

  @JsonKey(name: r'auto', required: true, includeIfNull: false)
  final bool auto;

  @JsonKey(name: r'overflow', required: false, includeIfNull: false)
  final bool? overflow;

  @JsonKey(name: r'tail_start_id', required: false, includeIfNull: false)
  final String? tailStartId;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is CompactionPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, auto, overflow, tailStartId],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.auto,
                other.overflow,
                other.tailStartId,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        messageID,
        type,
        auto,
        overflow,
        tailStartId,
      ]);

  factory CompactionPart.fromJson(Map<String, dynamic> json) =>
      _$CompactionPartFromJson(json);

  Map<String, dynamic> toJson() => _$CompactionPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum CompactionPartTypeEnum {
  @JsonValue(r'compaction')
  compaction(r'compaction'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const CompactionPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
