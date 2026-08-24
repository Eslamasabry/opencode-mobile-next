//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'server_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ServerConfig {
  /// Returns a new [ServerConfig] instance.
  ServerConfig({
    this.port,

    this.hostname,

    this.mdns,

    this.mdnsDomain,

    this.cors,
  });

  @JsonKey(name: r'port', required: false, includeIfNull: false)
  final int? port;

  @JsonKey(name: r'hostname', required: false, includeIfNull: false)
  final String? hostname;

  @JsonKey(name: r'mdns', required: false, includeIfNull: false)
  final bool? mdns;

  @JsonKey(name: r'mdnsDomain', required: false, includeIfNull: false)
  final String? mdnsDomain;

  @JsonKey(name: r'cors', required: false, includeIfNull: false)
  final List<String>? cors;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ServerConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [port, hostname, mdns, mdnsDomain, cors],
              [
                other.port,
                other.hostname,
                other.mdns,
                other.mdnsDomain,
                other.cors,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([port, hostname, mdns, mdnsDomain, cors]);

  factory ServerConfig.fromJson(Map<String, dynamic> json) =>
      _$ServerConfigFromJson(json);

  Map<String, dynamic> toJson() => _$ServerConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}
