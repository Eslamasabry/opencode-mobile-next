//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_auth_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderAuthErrorData {
  /// Returns a new [ProviderAuthErrorData] instance.
  ProviderAuthErrorData({required this.providerID, required this.message});

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderAuthErrorData &&
            runtimeType == other.runtimeType &&
            equals([providerID, message], [other.providerID, other.message]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([providerID, message]);

  factory ProviderAuthErrorData.fromJson(Map<String, dynamic> json) =>
      _$ProviderAuthErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderAuthErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}
