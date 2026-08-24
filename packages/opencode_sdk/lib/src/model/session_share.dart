//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_share.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionShare {
  /// Returns a new [SessionShare] instance.
  SessionShare({required this.url});

  @JsonKey(name: r'url', required: true, includeIfNull: false)
  final String url;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionShare &&
            runtimeType == other.runtimeType &&
            equals([url], [other.url]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([url]);

  factory SessionShare.fromJson(Map<String, dynamic> json) =>
      _$SessionShareFromJson(json);

  Map<String, dynamic> toJson() => _$SessionShareToJson(this);

  String toString() {
    return toJson().toString();
  }
}
