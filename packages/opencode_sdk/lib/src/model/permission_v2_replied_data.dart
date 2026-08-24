//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_reply.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_v2_replied_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionV2RepliedData {
  /// Returns a new [PermissionV2RepliedData] instance.
  PermissionV2RepliedData({
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
    unknownEnumValue: PermissionV2Reply.unknownDefaultOpenApi,
  )
  final PermissionV2Reply reply;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionV2RepliedData &&
            runtimeType == other.runtimeType &&
            equals(
              [sessionID, requestID, reply],
              [other.sessionID, other.requestID, other.reply],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([sessionID, requestID, reply]);

  factory PermissionV2RepliedData.fromJson(Map<String, dynamic> json) =>
      _$PermissionV2RepliedDataFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionV2RepliedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
