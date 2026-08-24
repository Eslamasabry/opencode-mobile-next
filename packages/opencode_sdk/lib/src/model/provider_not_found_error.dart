//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderNotFoundError {
  /// Returns a new [ProviderNotFoundError] instance.
  ProviderNotFoundError({
    required this.tag,

    required this.providerID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final ProviderNotFoundErrorTagEnum tag;

  @JsonKey(name: r'providerID', required: true, includeIfNull: false)
  final String providerID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, providerID, message],
              [other.tag, other.providerID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, providerID, message]);

  factory ProviderNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$ProviderNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderNotFoundErrorTagEnum {
  @JsonValue(r'ProviderNotFoundError')
  providerNotFoundError(r'ProviderNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
