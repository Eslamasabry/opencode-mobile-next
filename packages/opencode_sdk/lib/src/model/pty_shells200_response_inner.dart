//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_shells200_response_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyShells200ResponseInner {
  /// Returns a new [PtyShells200ResponseInner] instance.
  PtyShells200ResponseInner({
    required this.path,

    required this.name,

    required this.acceptable,
  });

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'acceptable', required: true, includeIfNull: false)
  final bool acceptable;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyShells200ResponseInner &&
            runtimeType == other.runtimeType &&
            equals(
              [path, name, acceptable],
              [other.path, other.name, other.acceptable],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([path, name, acceptable]);

  factory PtyShells200ResponseInner.fromJson(Map<String, dynamic> json) =>
      _$PtyShells200ResponseInnerFromJson(json);

  Map<String, dynamic> toJson() => _$PtyShells200ResponseInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}
