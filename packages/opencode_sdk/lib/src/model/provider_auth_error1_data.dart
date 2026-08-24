//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_auth_error1_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAuthError1Data {
  /// Returns a new [ProviderAuthError1Data] instance.
  ProviderAuthError1Data({
    this.providerID,

    this.field,

    this.message,

    this.kind,
  });

  @JsonKey(name: r'providerID', required: false, includeIfNull: false)
  final String? providerID;

  @JsonKey(name: r'field', required: false, includeIfNull: false)
  final String? field;

  @JsonKey(name: r'message', required: false, includeIfNull: false)
  final String? message;

  @JsonKey(name: r'kind', required: false, includeIfNull: false)
  final String? kind;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAuthError1Data &&
            runtimeType == other.runtimeType &&
            equals(
              [providerID, field, message, kind],
              [other.providerID, other.field, other.message, other.kind],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([providerID, field, message, kind]);

  factory ProviderAuthError1Data.fromJson(Map<String, dynamic> json) =>
      _$ProviderAuthError1DataFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAuthError1DataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
