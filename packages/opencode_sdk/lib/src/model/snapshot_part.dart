//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'snapshot_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SnapshotPart {
  /// Returns a new [SnapshotPart] instance.
  SnapshotPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.snapshot,
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
    unknownEnumValue: SnapshotPartTypeEnum.unknownDefaultOpenApi,
  )
  final SnapshotPartTypeEnum type;

  @JsonKey(name: r'snapshot', required: true, includeIfNull: false)
  final String snapshot;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SnapshotPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, snapshot],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.snapshot,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, sessionID, messageID, type, snapshot]);

  factory SnapshotPart.fromJson(Map<String, dynamic> json) =>
      _$SnapshotPartFromJson(json);

  Map<String, dynamic> toJson() => _$SnapshotPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SnapshotPartTypeEnum {
  @JsonValue(r'snapshot')
  snapshot(r'snapshot'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SnapshotPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
