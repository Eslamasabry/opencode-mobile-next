package io.github.eslamasabry.oc.engine.runtime.install

import kotlin.test.Test
import kotlin.test.assertEquals

/** The engine's shell text is the Flutter app's, byte for byte, so both apps run the same scripts on the phone. */
class VerbatimScriptsTest {
    @Test
    fun `B-LINUX-111 the install preludes and the shared npm text are the Flutter constants byte for byte`() {
        assertEquals(Sh.dartRaw("lib/builtin/setup/setup_scripts.dart", "setupPrelude"), InstallPrelude.SETUP)
        assertEquals(
            Sh.dartRaw("lib/builtin/setup/component_updates.dart", "componentUpdatePrelude"),
            InstallPrelude.COMPONENT_UPDATE,
        )
        assertEquals(
            Sh.dartRaw("lib/termux/opencode_ubuntu_setup.dart", "openCodeUbuntuSetupScript"),
            OpenCodeUbuntuSetup.SCRIPT,
        )
    }
}
