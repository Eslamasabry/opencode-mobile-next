import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

void main() {
  kitMotionStillTests(
    'KitAddAgentCard',
    builds: {
      'default': () => KitAddAgentCard(label: 'Add an agent', onPressed: () {}),
    },
  );
}
