//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/provider_config_models_value.dart';
import 'package:opencode_sdk/src/model/provider_config_options.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'provider_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ProviderConfig {
  /// Returns a new [ProviderConfig] instance.
  ProviderConfig({
    this.api,

    this.name,

    this.env,

    this.id,

    this.npm,

    this.whitelist,

    this.blacklist,

    this.options,

    this.models,
  });

  @JsonKey(name: r'api', required: false, includeIfNull: false)
  final String? api;

  @JsonKey(name: r'name', required: false, includeIfNull: false)
  final String? name;

  @JsonKey(name: r'env', required: false, includeIfNull: false)
  final List<String>? env;

  @JsonKey(name: r'id', required: false, includeIfNull: false)
  final String? id;

  @JsonKey(name: r'npm', required: false, includeIfNull: false)
  final String? npm;

  @JsonKey(name: r'whitelist', required: false, includeIfNull: false)
  final List<String>? whitelist;

  @JsonKey(name: r'blacklist', required: false, includeIfNull: false)
  final List<String>? blacklist;

  @JsonKey(name: r'options', required: false, includeIfNull: false)
  final ProviderConfigOptions? options;

  @JsonKey(name: r'models', required: false, includeIfNull: false)
  final Map<String, ProviderConfigModelsValue>? models;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ProviderConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [api, name, env, id, npm, whitelist, blacklist, options, models],
              [
                other.api,
                other.name,
                other.env,
                other.id,
                other.npm,
                other.whitelist,
                other.blacklist,
                other.options,
                other.models,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        api,
        name,
        env,
        id,
        npm,
        whitelist,
        blacklist,
        options,
        models,
      ]);

  factory ProviderConfig.fromJson(Map<String, dynamic> json) =>
      _$ProviderConfigFromJson(json);

  Map<String, dynamic> toJson() => _$ProviderConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}
