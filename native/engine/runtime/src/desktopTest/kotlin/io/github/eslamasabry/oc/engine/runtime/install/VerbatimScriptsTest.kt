package io.github.eslamasabry.oc.engine.runtime.install

import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinScriptTexts
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The engine's shell text is the released Flutter app's (2.1.4+62), byte for byte, so both apps run the same scripts on
 * the phone. Read from git at the release: this branch's lib/ is an older Flutter snapshot (2.1.0+57) without the
 * crashing-curl and crashing-tar fallbacks (#117) the engine already ports.
 */
class VerbatimScriptsTest {
    @Test
    fun `B-LINUX-111 the install preludes and the shared npm text are the Flutter constants byte for byte`() {
        assertEquals(Sh.releasedDartRaw("lib/builtin/setup/setup_scripts.dart", "setupPrelude"), InstallPrelude.SETUP)
        assertEquals(
            Sh.releasedDartRaw("lib/builtin/setup/component_updates.dart", "componentUpdatePrelude"),
            InstallPrelude.COMPONENT_UPDATE,
        )
        assertEquals(
            Sh.releasedDartRaw("lib/termux/opencode_ubuntu_setup.dart", "openCodeUbuntuSetupScript"),
            BuiltinScriptTexts.UBUNTU_SETUP,
        )
    }
}
