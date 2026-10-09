import 'dart:convert';
import 'dart:io';

import 'package:opencode_mobile/builtin/agents/gen_ui_server.dart';

// Exact installed bytes: do not append a newline or duplicate the JS template.
void main() {
  stdout.add(
    utf8.encode(
      genUiServerScript(enabledMarkerPath: '/root/.oc-genui/openCode2/enabled'),
    ),
  );
}
