//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/connection_info.dart';
import 'package:opencode_sdk/src/model/integration_method.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationInfo {
  /// Returns a new [IntegrationInfo] instance.
  IntegrationInfo({
    required this.id,

    required this.name,

    required this.methods,

    required this.connections,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'methods', required: true, includeIfNull: false)
  final List<IntegrationMethod> methods;

  @JsonKey(name: r'connections', required: true, includeIfNull: false)
  final List<ConnectionInfo> connections;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationInfo &&
            runtimeType == other.runtimeType &&
            equals(
              [id, name, methods, connections],
              [other.id, other.name, other.methods, other.connections],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, name, methods, connections]);

  factory IntegrationInfo.fromJson(Map<String, dynamic> json) =>
      _$IntegrationInfoFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}
