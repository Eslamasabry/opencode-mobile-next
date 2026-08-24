//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'permission_request_tool.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PermissionRequestTool {
  /// Returns a new [PermissionRequestTool] instance.
  PermissionRequestTool({required this.messageID, required this.callID});

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'callID', required: true, includeIfNull: false)
  final String callID;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PermissionRequestTool &&
            runtimeType == other.runtimeType &&
            equals([messageID, callID], [other.messageID, other.callID]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([messageID, callID]);

  factory PermissionRequestTool.fromJson(Map<String, dynamic> json) =>
      _$PermissionRequestToolFromJson(json);

  Map<String, dynamic> toJson() => _$PermissionRequestToolToJson(this);

  String toString() {
    return toJson().toString();
  }
}
