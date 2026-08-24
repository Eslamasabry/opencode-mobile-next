//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_status_needs_auth.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MCPStatusNeedsAuth {
  /// Returns a new [MCPStatusNeedsAuth] instance.
  MCPStatusNeedsAuth({required this.status});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MCPStatusNeedsAuthStatusEnum.unknownDefaultOpenApi,
  )
  final MCPStatusNeedsAuthStatusEnum status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MCPStatusNeedsAuth &&
            runtimeType == other.runtimeType &&
            equals([status], [other.status]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([status]);

  factory MCPStatusNeedsAuth.fromJson(Map<String, dynamic> json) =>
      _$MCPStatusNeedsAuthFromJson(json);

  Map<String, dynamic> toJson() => _$MCPStatusNeedsAuthToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MCPStatusNeedsAuthStatusEnum {
  @JsonValue(r'needs_auth')
  needsAuth(r'needs_auth'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MCPStatusNeedsAuthStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
