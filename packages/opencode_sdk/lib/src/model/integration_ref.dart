//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'integration_ref.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class IntegrationRef {
  /// Returns a new [IntegrationRef] instance.
  IntegrationRef({required this.id, required this.name});

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is IntegrationRef &&
            runtimeType == other.runtimeType &&
            equals([id, name], [other.id, other.name]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([id, name]);

  factory IntegrationRef.fromJson(Map<String, dynamic> json) =>
      _$IntegrationRefFromJson(json);

  Map<String, dynamic> toJson() => _$IntegrationRefToJson(this);

  String toString() {
    return toJson().toString();
  }
}
