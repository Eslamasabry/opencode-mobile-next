//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class Provider {
  /// Returns a new [Provider] instance.
  Provider({
    required this.id,

    required this.name,

    required this.source_,

    required this.env,

    this.key,

    required this.options,

    required this.models,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(
    name: r'source',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ProviderSource_Enum.unknownDefaultOpenApi,
  )
  final ProviderSource_Enum source_;

  @JsonKey(name: r'env', required: true, includeIfNull: false)
  final List<String> env;

  @JsonKey(name: r'key', required: false, includeIfNull: false)
  final String? key;

  @JsonKey(name: r'options', required: true, includeIfNull: false)
  final Object options;

  @JsonKey(name: r'models', required: true, includeIfNull: false)
  final Map<String, Model> models;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is Provider &&
            runtimeType == other.runtimeType &&
            equals(
              [id, name, source_, env, key, options, models],
              [
                other.id,
                other.name,
                other.source_,
                other.env,
                other.key,
                other.options,
                other.models,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, name, source_, env, key, options, models]);

  factory Provider.fromJson(Map<String, dynamic> json) =>
      _$ProviderFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ProviderSource_Enum {
  @JsonValue(r'env')
  env(r'env'),
  @JsonValue(r'config')
  config(r'config'),
  @JsonValue(r'custom')
  custom(r'custom'),
  @JsonValue(r'api')
  api(r'api'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ProviderSource_Enum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}
