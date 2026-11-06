package io.github.eslamasabry.opencode_mobile

/** App-authored failure fact; never contains server output or credentials. */
class ServerPortBusy(val port: Int) :
    IllegalStateException("Another OpenCode server is already using port $port on this phone.")
