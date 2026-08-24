//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'config_enterprise.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ConfigEnterprise {
  /// Returns a new [ConfigEnterprise] instance.
  ConfigEnterprise({this.url});

  @JsonKey(name: r'url', required: false, includeIfNull: false)
  final String? url;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ConfigEnterprise &&
            runtimeType == other.runtimeType &&
            equals([url], [other.url]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([url]);

  factory ConfigEnterprise.fromJson(Map<String, dynamic> json) =>
      _$ConfigEnterpriseFromJson(json);

  Map<String, dynamic> toJson() => _$ConfigEnterpriseToJson(this);

  String toString() {
    return toJson().toString();
  }
}
