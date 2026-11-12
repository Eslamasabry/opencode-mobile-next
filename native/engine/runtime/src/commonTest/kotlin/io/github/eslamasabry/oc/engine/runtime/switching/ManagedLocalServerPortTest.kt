package io.github.eslamasabry.oc.engine.runtime.switching

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 3 conn: the phase-0 gate, the waits outside the connection lock and the healing recognise This phone's server by
 * ManagedLocalServer.isBuiltin, which reads the port the app selected (OcApplication: PhoneServerScripts.configurePort
 * { BuiltinLinux.selectedServerPort }): 4098 for the same-package build, 4100 for the preview package, and the port the
 * app moved to (4100..4199) when its own was busy.
 */
class ManagedLocalServerPortTest {
    @Test
    fun `W3-CONN-020 each build's own port is the phone's server, another loopback port is not`() {
        try {
            for (port in listOf(4098, 4100, 4102, 4117)) {
                ManagedLocalServer.configureNativePort { port }
                assertTrue(ManagedLocalServer.isBuiltin("http://127.0.0.1:$port"), "port $port")
                assertTrue(
                    ManagedLocalServer.isManagedLocal(LocalProfile("p", "This phone", "http://127.0.0.1:$port", ServerBackendKind.OpenCode)),
                )
                for (other in listOf(4096, 4097, 4098, 4100, 4102).filter { it != port })
                    assertFalse(ManagedLocalServer.isBuiltin("http://127.0.0.1:$other"), "port $other on a $port build")
            }
        } finally {
            ManagedLocalServer.configureNativePort { 4098 }
        }
    }
}
