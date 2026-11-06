package io.github.eslamasabry.oc.engine.runtime.install

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The engine's shell text is the released Flutter app's (2.1.4), byte for byte, so both apps run the same scripts on
 * the phone. The ports follow the release (9c1e42325 took the 2.1.4 prelude), not this branch's older lib/ snapshot.
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
            OpenCodeUbuntuSetup.SCRIPT,
        )
    }
}
