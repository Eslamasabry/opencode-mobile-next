//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_saved_info.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_permission_saved_list200_response.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2PermissionSavedList200Response {
  /// Returns a new [V2PermissionSavedList200Response] instance.
  V2PermissionSavedList200Response({required this.data});

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final List<PermissionSavedInfo> data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2PermissionSavedList200Response &&
            runtimeType == other.runtimeType &&
            equals([data], [other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([data]);

  factory V2PermissionSavedList200Response.fromJson(
    Map<String, dynamic> json,
  ) => _$V2PermissionSavedList200ResponseFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2PermissionSavedList200ResponseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
