//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_create_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyCreateRequest {
  /// Returns a new [PtyCreateRequest] instance.
  PtyCreateRequest({this.command, this.args, this.cwd, this.title, this.env});

  @JsonKey(name: r'command', required: false, includeIfNull: false)
  final String? command;

  @JsonKey(name: r'args', required: false, includeIfNull: false)
  final List<String>? args;

  @JsonKey(name: r'cwd', required: false, includeIfNull: false)
  final String? cwd;

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'env', required: false, includeIfNull: false)
  final Map<String, String>? env;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyCreateRequest &&
            runtimeType == other.runtimeType &&
            equals(
              [command, args, cwd, title, env],
              [other.command, other.args, other.cwd, other.title, other.env],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([command, args, cwd, title, env]);

  factory PtyCreateRequest.fromJson(Map<String, dynamic> json) =>
      _$PtyCreateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$PtyCreateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}
