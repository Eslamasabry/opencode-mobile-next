//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/permission_v2_effect.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'v2_session_permission_create200_response_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class V2SessionPermissionCreate200ResponseData {
  /// Returns a new [V2SessionPermissionCreate200ResponseData] instance.
  V2SessionPermissionCreate200ResponseData({
    required this.id,

    required this.effect,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'effect',
    required: true,
    includeIfNull: false,
    unknownEnumValue: PermissionV2Effect.unknownDefaultOpenApi,
  )
  final PermissionV2Effect effect;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is V2SessionPermissionCreate200ResponseData &&
            runtimeType == other.runtimeType &&
            equals([id, effect], [other.id, other.effect]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([id, effect]);

  factory V2SessionPermissionCreate200ResponseData.fromJson(
    Map<String, dynamic> json,
  ) => _$V2SessionPermissionCreate200ResponseDataFromJson(json);

  Map<String, dynamic> toJson() =>
      _$V2SessionPermissionCreate200ResponseDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
