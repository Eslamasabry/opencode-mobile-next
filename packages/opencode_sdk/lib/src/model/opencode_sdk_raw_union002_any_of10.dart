//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_part_removed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'opencode_sdk_raw_union002_any_of10.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class OpencodeSdkRawUnion002AnyOf10 {
  /// Returns a new [OpencodeSdkRawUnion002AnyOf10] instance.
  OpencodeSdkRawUnion002AnyOf10({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        OpencodeSdkRawUnion002AnyOf10TypeEnum.unknownDefaultOpenApi,
  )
  final OpencodeSdkRawUnion002AnyOf10TypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventMessagePartRemovedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is OpencodeSdkRawUnion002AnyOf10 &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory OpencodeSdkRawUnion002AnyOf10.fromJson(Map<String, dynamic> json) =>
      _$OpencodeSdkRawUnion002AnyOf10FromJson(json);

  Map<String, dynamic> toJson() => _$OpencodeSdkRawUnion002AnyOf10ToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum OpencodeSdkRawUnion002AnyOf10TypeEnum {
  @JsonValue(r'message.part.removed')
  messagePeriodPartPeriodRemoved(r'message.part.removed'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const OpencodeSdkRawUnion002AnyOf10TypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
