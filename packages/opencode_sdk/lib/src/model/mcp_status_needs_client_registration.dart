//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'mcp_status_needs_client_registration.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MCPStatusNeedsClientRegistration {
  /// Returns a new [MCPStatusNeedsClientRegistration] instance.
  MCPStatusNeedsClientRegistration({required this.status, required this.error});

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        MCPStatusNeedsClientRegistrationStatusEnum.unknownDefaultOpenApi,
  )
  final MCPStatusNeedsClientRegistrationStatusEnum status;

  @JsonKey(name: r'error', required: true, includeIfNull: false)
  final String error;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MCPStatusNeedsClientRegistration &&
            runtimeType == other.runtimeType &&
            equals([status, error], [other.status, other.error]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([status, error]);

  factory MCPStatusNeedsClientRegistration.fromJson(
    Map<String, dynamic> json,
  ) => _$MCPStatusNeedsClientRegistrationFromJson(json);

  Map<String, dynamic> toJson() =>
      _$MCPStatusNeedsClientRegistrationToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MCPStatusNeedsClientRegistrationStatusEnum {
  @JsonValue(r'needs_client_registration')
  needsClientRegistration(r'needs_client_registration'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MCPStatusNeedsClientRegistrationStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
