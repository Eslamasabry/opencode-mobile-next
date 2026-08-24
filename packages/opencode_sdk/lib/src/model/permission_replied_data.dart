//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_replied_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionRepliedData {
  /// Returns a new [PermissionRepliedData] instance.
  PermissionRepliedData({
    required this.sessionID,

    required this.requestID,

    required this.reply,
  });

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'requestID', required: true, includeIfNull: false)
  final String requestID;

  @JsonKey(
    name: r'reply',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionRepliedDataReplyEnum.unknownDefaultOpenApi,
  )
  final PermissionRepliedDataReplyEnum reply;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionRepliedData &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, requestID, reply],
              [other.sessionID, other.requestID, other.reply],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, requestID, reply]);

  factory PermissionRepliedData.fromJson(Map<String, dynamic> json) =>
      _$PermissionRepliedDataFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionRepliedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum PermissionRepliedDataReplyEnum {
  @JsonValue(r'once')
  once(r'once'),
  @JsonValue(r'always')
  always(r'always'),
  @JsonValue(r'reject')
  reject(r'reject'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const PermissionRepliedDataReplyEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
